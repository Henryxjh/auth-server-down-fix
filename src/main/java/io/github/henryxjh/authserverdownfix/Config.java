package io.github.henryxjh.authserverdownfix;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue USE_CUSTOM_PROFILE_APIS = BUILDER
            .comment(
                    "Whether to use custom profile lookup APIs instead of the two official profile lookup APIs.",
                    "If true, only customProfileApis will be used for UUID lookup.",
                    "If false, only the official Mojang and Minecraft Services APIs will be used for UUID lookup."
            )
            .define("useCustomProfileApis", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_PROFILE_APIS = BUILDER
            .comment(
                    "Custom profile lookup API URL templates.",
                    "The response must use the same JSON format as the official APIs: {\"id\":\"32-character uuid\",\"name\":\"player name\"}.",
                    "Accepted player name placeholders: {name}, <name>, or %s.",
                    "Examples:",
                    "    https://example.com/profile/{name}",
                    "    https://example.com/profile/<name>",
                    "    https://example.com/profile/%s"
            )
            .defineListAllowEmpty("customProfileApis", List.of(), () -> "", Config::validateCustomProfileApi);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FALLBACK_PROFILES = BUILDER
            .comment(
                    "Name to UUID mappings used when Mojang authentication servers and profile lookup APIs are unavailable.",
                    "Format: name=uuid",
                    "Accepted UUID formats: 32-character undashed UUID, or 36-character dashed UUID.",
                    "Examples: Notch=069a79f444e94726a5befca90e38aaf5 or Notch=069a79f4-44e9-4726-a5be-fca90e38aaf5"
            )
            .defineListAllowEmpty("fallbackProfiles", List.of(), () -> "", Config::validateFallbackProfile);

    public static final ModConfigSpec.IntValue UNSAFE_PLAYER_KICK_DELAY_SECONDS = BUILDER
            .comment(
                    "Seconds to wait after the Mojang authentication server connection recovers before disconnecting players who joined through unsafe UUID lookup.",
                    "Set to 0 to disable automatic disconnection."
            )
            .defineInRange("unsafePlayerKickDelaySeconds", 60, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> UNSAFE_LOGIN_ENABLED_BROADCAST = BUILDER
            .comment("Broadcast message sent when unsafe login mode is enabled.")
            .define(
                    "unsafeLoginEnabledBroadcast",
                    "[Auth Server Down Fix] WARNING: Unsafe login mode is enabled. If Mojang authentication servers are unavailable, "
                            + "players may join without completing live online authentication. Do not grant sensitive "
                            + "permissions while this mode is enabled."
            );

    public static final ModConfigSpec.ConfigValue<String> UNSAFE_LOGIN_DISABLED_BROADCAST = BUILDER
            .comment("Broadcast message sent when unsafe login mode is disabled.")
            .define(
                    "unsafeLoginDisabledBroadcast",
                    "Unsafe login mode is disabled. Players must complete vanilla online authentication."
            );

    public static final ModConfigSpec.ConfigValue<String> AUTHENTICATION_RECOVERED_BROADCAST = BUILDER
            .comment("Broadcast message sent when the connection to the Mojang authentication server recovers.")
            .define(
                    "authenticationRecoveredBroadcast",
                    "Mojang authentication server connection has recovered."
            );

    public static final ModConfigSpec.ConfigValue<String> UNSAFE_PLAYER_KICK_COUNTDOWN_BROADCAST = BUILDER
            .comment(
                    "Broadcast message sent when unsafe-login players are scheduled to be disconnected.",
                    "Use {seconds} for the remaining number of seconds."
            )
            .define(
                    "unsafePlayerKickCountdownBroadcast",
                    "Players who joined through unsafe login will be disconnected in {seconds} seconds."
            );

    public static final ModConfigSpec.ConfigValue<String> UNSAFE_PLAYERS_KICKED_BROADCAST = BUILDER
            .comment(
                    "Broadcast message sent after unsafe-login players are disconnected.",
                    "Use {players} for the comma-separated player name list."
            )
            .define(
                    "unsafePlayersKickedBroadcast",
                    "Disconnected unsafe-login players: {players}"
            );

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static boolean useCustomProfileApis() {
        return USE_CUSTOM_PROFILE_APIS.getAsBoolean();
    }

    public static List<String> getCustomProfileApis() {
        return CUSTOM_PROFILE_APIS.get().stream()
                .map(String::trim)
                .filter(api -> !api.isEmpty())
                .toList();
    }

    public static Optional<GameProfile> getFallbackProfile(String username) {
        return getFallbackProfiles().stream()
                .filter(profile -> profile.name().equalsIgnoreCase(username))
                .findFirst()
                .map(profile -> new GameProfile(profile.uuid(), profile.name()));
    }

    public static List<GameProfile> getFallbackGameProfiles() {
        return getFallbackProfiles().stream()
                .map(profile -> new GameProfile(profile.uuid(), profile.name()))
                .toList();
    }

    public static synchronized boolean addOrReplaceFallbackProfile(GameProfile profile) {
        List<String> entries = new ArrayList<>(getFallbackProfileEntries());
        String newEntry = formatFallbackProfileEntry(profile);
        boolean replaced = false;

        for (int index = 0; index < entries.size(); index++) {
            Optional<FallbackProfile> existingProfile = parseFallbackProfile(entries.get(index));
            if (existingProfile.isPresent() && existingProfile.get().name().equalsIgnoreCase(profile.getName())) {
                entries.set(index, newEntry);
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            entries.add(newEntry);
        }

        FALLBACK_PROFILES.set(entries);
        FALLBACK_PROFILES.save();
        return replaced;
    }

    public static synchronized boolean removeFallbackProfile(String username) {
        List<String> entries = new ArrayList<>(getFallbackProfileEntries());
        boolean removed = entries.removeIf(entry -> parseFallbackProfile(entry)
                .map(profile -> profile.name().equalsIgnoreCase(username))
                .orElse(false));
        if (removed) {
            FALLBACK_PROFILES.set(entries);
            FALLBACK_PROFILES.save();
        }
        return removed;
    }

    public static int getUnsafePlayerKickDelaySeconds() {
        return UNSAFE_PLAYER_KICK_DELAY_SECONDS.getAsInt();
    }

    public static String getUnsafeLoginEnabledBroadcast() {
        return UNSAFE_LOGIN_ENABLED_BROADCAST.get();
    }

    public static String getUnsafeLoginDisabledBroadcast() {
        return UNSAFE_LOGIN_DISABLED_BROADCAST.get();
    }

    public static String getAuthenticationRecoveredBroadcast() {
        return AUTHENTICATION_RECOVERED_BROADCAST.get();
    }

    public static String getUnsafePlayerKickCountdownBroadcast(int seconds) {
        return UNSAFE_PLAYER_KICK_COUNTDOWN_BROADCAST.get().replace("{seconds}", Integer.toString(seconds));
    }

    public static String getUnsafePlayersKickedBroadcast(String players) {
        return UNSAFE_PLAYERS_KICKED_BROADCAST.get().replace("{players}", players);
    }

    private static boolean validateCustomProfileApi(Object value) {
        return value instanceof String api && !api.isBlank();
    }

    private static boolean validateFallbackProfile(Object value) {
        return value instanceof String entry && parseFallbackProfile(entry).isPresent();
    }

    private static List<String> getFallbackProfileEntries() {
        return FALLBACK_PROFILES.get().stream()
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private static List<FallbackProfile> getFallbackProfiles() {
        return getFallbackProfileEntries().stream()
                .map(Config::parseFallbackProfile)
                .flatMap(Optional::stream)
                .toList();
    }

    private static String formatFallbackProfileEntry(GameProfile profile) {
        return profile.getName() + "=" + profile.getId().toString().replace("-", "");
    }

    private static Optional<FallbackProfile> parseFallbackProfile(String entry) {
        int separator = entry.indexOf('=');
        if (separator <= 0 || separator == entry.length() - 1) {
            return Optional.empty();
        }

        String name = entry.substring(0, separator).trim();
        String uuid = entry.substring(separator + 1).trim();
        if (name.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new FallbackProfile(name, parseUuid(uuid)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static UUID parseUuid(String uuid) {
        if (uuid.length() == 36) {
            return UUID.fromString(uuid);
        }

        if (uuid.length() != 32) {
            throw new IllegalArgumentException("Expected UUID of length 32 or 36");
        }

        return UUID.fromString(uuid.substring(0, 8)
                + "-"
                + uuid.substring(8, 12)
                + "-"
                + uuid.substring(12, 16)
                + "-"
                + uuid.substring(16, 20)
                + "-"
                + uuid.substring(20));
    }

    private record FallbackProfile(String name, UUID uuid) {
    }
}
