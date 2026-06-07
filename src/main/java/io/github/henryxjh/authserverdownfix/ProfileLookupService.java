package io.github.henryxjh.authserverdownfix;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;

public final class ProfileLookupService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private ProfileLookupService() {
    }

    public static Optional<GameProfile> fetchProfileForLogin(String username) {
        Optional<GameProfile> apiProfile = fetchProfileFromConfiguredApis(username);
        if (apiProfile.isPresent()) {
            return apiProfile;
        }

        LOGGER.debug("No UUID lookup API returned a profile for {}; checking configured fallback profiles", username);
        Optional<GameProfile> fallbackProfile = Config.getFallbackProfile(username);
        fallbackProfile.ifPresent(profile -> LOGGER.info(
                "Using configured fallback UUID of player {}: {}",
                profile.getName(),
                profile.getId()
        ));
        if (fallbackProfile.isEmpty()) {
            LOGGER.debug("No configured fallback profile matched {}", username);
        }
        return fallbackProfile;
    }

    public static Optional<GameProfile> fetchProfileFromConfiguredApis(String username) {
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
        LOGGER.debug(
                "Starting fallback UUID lookup for {} encoded as {}; custom APIs enabled={}",
                username,
                encodedUsername,
                Config.useCustomProfileApis()
        );
        return Config.useCustomProfileApis()
                ? fetchCustomProfileApis(username, encodedUsername)
                : fetchOfficialProfileApis(username, encodedUsername);
    }

    private static Optional<GameProfile> fetchOfficialProfileApis(String username, String encodedUsername) {
        LOGGER.debug("Using official UUID lookup APIs for {}", username);
        Optional<GameProfile> mojangProfile = fetchProfileFromApi(
                username,
                "Mojang profile API",
                "https://api.mojang.com/users/profiles/minecraft/" + encodedUsername
        );
        if (mojangProfile.isPresent()) {
            return mojangProfile;
        }

        Optional<GameProfile> minecraftServicesProfile = fetchProfileFromApi(
                username,
                "Minecraft Services profile API",
                "https://api.minecraftservices.com/minecraft/profile/lookup/name/" + encodedUsername
        );
        if (minecraftServicesProfile.isPresent()) {
            return minecraftServicesProfile;
        }

        return Optional.empty();
    }

    private static Optional<GameProfile> fetchCustomProfileApis(String username, String encodedUsername) {
        var customProfileApis = Config.getCustomProfileApis();
        LOGGER.debug("Using {} custom UUID lookup API(s) for {}", customProfileApis.size(), username);
        for (String apiTemplate : customProfileApis) {
            String url = formatCustomProfileApi(apiTemplate, encodedUsername);
            LOGGER.debug("Formatted custom profile API template '{}' as '{}'", apiTemplate, url);
            Optional<GameProfile> profile = fetchProfileFromApi(
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

    private static String formatCustomProfileApi(String apiTemplate, String encodedUsername) {
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

    private static Optional<GameProfile> fetchProfileFromApi(String username, String apiName, String url) {
        LOGGER.debug("Requesting profile from {} for {}: {}", apiName, username, url);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            LOGGER.debug(
                    "Profile response from {} for {} at {}: HTTP {}, body={}",
                    apiName,
                    username,
                    url,
                    response.statusCode(),
                    truncate(response.body())
            );
            if (response.statusCode() != 200) {
                LOGGER.warn(
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
                LOGGER.warn(
                        "{} response for {} at {} did not contain a valid UUID",
                        apiName,
                        username,
                        url
                );
                return Optional.empty();
            }

            String id = json.get("id").getAsString();
            LOGGER.debug("{} response for {} contained id='{}'", apiName, username, id);
            UUID uuid = parseUndashedUuid(id);
            String profileName = json.has("name") && json.get("name").isJsonPrimitive()
                    ? json.get("name").getAsString()
                    : username;
            LOGGER.info("Using {} UUID of player {}: {}", apiName, profileName, uuid);
            return Optional.of(new GameProfile(uuid, profileName));
        } catch (IOException exception) {
            LOGGER.warn("Failed to fetch profile from {} for {} at {}", apiName, username, url, exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while fetching profile from {} for {} at {}", apiName, username, url, exception);
            return Optional.empty();
        } catch (IllegalArgumentException | JsonSyntaxException | IllegalStateException exception) {
            LOGGER.warn("Failed to parse profile response from {} for {} at {}", apiName, username, url, exception);
            return Optional.empty();
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "<null>";
        }

        String compactBody = body.replace('\n', ' ').replace('\r', ' ');
        return compactBody.length() <= 512 ? compactBody : compactBody.substring(0, 512) + "...";
    }

    private static UUID parseUndashedUuid(String id) {
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
