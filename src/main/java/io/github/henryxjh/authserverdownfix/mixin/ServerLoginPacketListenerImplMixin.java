package io.github.henryxjh.authserverdownfix.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import io.github.henryxjh.authserverdownfix.AuthRecoveryMonitor;
import io.github.henryxjh.authserverdownfix.Config;
import io.github.henryxjh.authserverdownfix.UnsafeLoginMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.slf4j.Logger;

@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl$1")
public class ServerLoginPacketListenerImplMixin {
    @Unique
    private static final Logger authserverdownfix$LOGGER = LogUtils.getLogger();
    @Unique
    private static final HttpClient authserverdownfix$HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Redirect(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;hasJoinedServer(Ljava/lang/String;Ljava/lang/String;Ljava/net/InetAddress;)Lcom/mojang/authlib/yggdrasil/ProfileResult;"
            )
    )
    private ProfileResult authserverdownfix$handleAuthSessionServiceUnavailable(
            MinecraftSessionService sessionService,
            String username,
            String serverId,
            InetAddress address
    ) throws AuthenticationUnavailableException {
        try {
            ProfileResult result = sessionService.hasJoinedServer(username, serverId, address);
            if (result != null) {
                AuthRecoveryMonitor.onPlayerAuthenticatedNormally(result.profile());
            }
            return result;
        } catch (AuthenticationUnavailableException exception) {
            AuthRecoveryMonitor.onAuthenticationUnavailable(sessionService);
            authserverdownfix$LOGGER.info(
                    "Authentication session service is unavailable for {}, trying fallback UUID lookup",
                    username
            );
            authserverdownfix$LOGGER.debug(
                    "Session service lookup failed for {} with serverId={} address={}",
                    username,
                    serverId,
                    address,
                    exception
            );
            if (!UnsafeLoginMode.isEnabled()) {
                authserverdownfix$LOGGER.warn(
                        "Unsafe login mode is disabled; keeping vanilla authentication server disconnect for {}",
                        username
                );
                throw exception;
            }

            Optional<GameProfile> profile = authserverdownfix$fetchMojangApiProfile(username);
            if (profile.isPresent()) {
                AuthRecoveryMonitor.markPendingUnsafe(profile.get());
                return new ProfileResult(profile.get());
            }

            authserverdownfix$LOGGER.warn("No fallback UUID was available for {}, keeping vanilla authentication server disconnect", username);
            throw exception;
        }
    }

    @Unique
    private static Optional<GameProfile> authserverdownfix$fetchMojangApiProfile(String username) {
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
        authserverdownfix$LOGGER.debug(
                "Starting fallback UUID lookup for {} encoded as {}; custom APIs enabled={}",
                username,
                encodedUsername,
                Config.useCustomProfileApis()
        );
        Optional<GameProfile> apiProfile = Config.useCustomProfileApis()
                ? authserverdownfix$fetchCustomProfileApis(username, encodedUsername)
                : authserverdownfix$fetchOfficialProfileApis(username, encodedUsername);
        if (apiProfile.isPresent()) {
            return apiProfile;
        }

        authserverdownfix$LOGGER.debug("No UUID lookup API returned a profile for {}; checking configured fallback profiles", username);
        Optional<GameProfile> fallbackProfile = Config.getFallbackProfile(username);
        fallbackProfile.ifPresent(profile -> authserverdownfix$LOGGER.info(
                "Using configured fallback UUID of player {}: {}",
                profile.getName(),
                profile.getId()
        ));
        if (fallbackProfile.isEmpty()) {
            authserverdownfix$LOGGER.debug("No configured fallback profile matched {}", username);
        }
        return fallbackProfile;
    }

    @Unique
    private static Optional<GameProfile> authserverdownfix$fetchOfficialProfileApis(String username, String encodedUsername) {
        authserverdownfix$LOGGER.debug("Using official UUID lookup APIs for {}", username);
        Optional<GameProfile> mojangProfile = authserverdownfix$fetchProfileFromApi(
                username,
                "Mojang profile API",
                "https://api.mojang.com/users/profiles/minecraft/" + encodedUsername
        );
        if (mojangProfile.isPresent()) {
            return mojangProfile;
        }

        Optional<GameProfile> minecraftServicesProfile = authserverdownfix$fetchProfileFromApi(
                username,
                "Minecraft Services profile API",
                "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + encodedUsername
        );
        if (minecraftServicesProfile.isPresent()) {
            return minecraftServicesProfile;
        }

        return Optional.empty();
    }

    @Unique
    private static Optional<GameProfile> authserverdownfix$fetchCustomProfileApis(String username, String encodedUsername) {
        var customProfileApis = Config.getCustomProfileApis();
        authserverdownfix$LOGGER.debug("Using {} custom UUID lookup API(s) for {}", customProfileApis.size(), username);
        for (String apiTemplate : customProfileApis) {
            String url = authserverdownfix$formatCustomProfileApi(apiTemplate, encodedUsername);
            authserverdownfix$LOGGER.debug("Formatted custom profile API template '{}' as '{}'", apiTemplate, url);
            Optional<GameProfile> profile = authserverdownfix$fetchProfileFromApi(
                    username,
                    "custom profile API",
                    url
            );
            if (profile.isPresent()) {
                return profile;
            }
        }

        return Optional.empty();
    }

    @Unique
    private static String authserverdownfix$formatCustomProfileApi(String apiTemplate, String encodedUsername) {
        if (apiTemplate.contains("{name}")) {
            return apiTemplate.replace("{name}", encodedUsername);
        }

        if (apiTemplate.contains("<name>")) {
            return apiTemplate.replace("<name>", encodedUsername);
        }

        if (apiTemplate.contains("%s")) {
            return apiTemplate.replace("%s", encodedUsername);
        }

        return apiTemplate;
    }

    @Unique
    private static Optional<GameProfile> authserverdownfix$fetchProfileFromApi(String username, String apiName, String url) {
        authserverdownfix$LOGGER.debug("Requesting profile from {} for {}: {}", apiName, username, url);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = authserverdownfix$HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            authserverdownfix$LOGGER.debug(
                    "Profile response from {} for {} at {}: HTTP {}, body={}",
                    apiName,
                    username,
                    url,
                    response.statusCode(),
                    authserverdownfix$truncate(response.body())
            );
            if (response.statusCode() != 200) {
                authserverdownfix$LOGGER.warn(
                        "Failed to fetch profile from {} for {} at {}: HTTP {}",
                        apiName,
                        username,
                        url,
                        response.statusCode()
                );
                return Optional.empty();
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("id") || !json.get("id").isJsonPrimitive()) {
                authserverdownfix$LOGGER.warn(
                        "{} response for {} at {} did not contain a valid UUID",
                        apiName,
                        username,
                        url
                );
                return Optional.empty();
            }

            String id = json.get("id").getAsString();
            authserverdownfix$LOGGER.debug("{} response for {} contained id='{}'", apiName, username, id);
            UUID uuid = authserverdownfix$parseUndashedUuid(id);
            String profileName = json.has("name") && json.get("name").isJsonPrimitive()
                    ? json.get("name").getAsString()
                    : username;
            authserverdownfix$LOGGER.info("Using {} UUID of player {}: {}", apiName, profileName, uuid);
            return Optional.of(new GameProfile(uuid, profileName));
        } catch (IOException exception) {
            authserverdownfix$LOGGER.warn("Failed to fetch profile from {} for {} at {}", apiName, username, url, exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            authserverdownfix$LOGGER.warn("Interrupted while fetching profile from {} for {} at {}", apiName, username, url, exception);
            return Optional.empty();
        } catch (IllegalArgumentException | JsonSyntaxException | IllegalStateException exception) {
            authserverdownfix$LOGGER.warn("Failed to parse profile response from {} for {} at {}", apiName, username, url, exception);
            return Optional.empty();
        }
    }

    @Unique
    private static String authserverdownfix$truncate(String body) {
        if (body == null) {
            return "<null>";
        }

        String compactBody = body.replace('\n', ' ').replace('\r', ' ');
        return compactBody.length() <= 512 ? compactBody : compactBody.substring(0, 512) + "...";
    }

    @Unique
    private static UUID authserverdownfix$parseUndashedUuid(String id) {
        if (id.length() != 32) {
            throw new IllegalArgumentException("Expected undashed UUID of length 32");
        }

        return UUID.fromString(id.substring(0, 8)
                + "-"
                + id.substring(8, 12)
                + "-"
                + id.substring(12, 16)
                + "-"
                + id.substring(16, 20)
                + "-"
                + id.substring(20));
    }
}
