package io.github.henryxjh.authserverdownfix;

import com.mojang.authlib.GameProfile;
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
                    "Examples: 00000000000000000000000000000000 or 00000000-0000-0000-0000-000000000000"
            )
            .defineListAllowEmpty("fallbackProfiles", List.of(), () -> "", Config::validateFallbackProfile);

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
        return FALLBACK_PROFILES.get().stream()
                .map(Config::parseFallbackProfile)
                .flatMap(Optional::stream)
                .filter(profile -> profile.name().equalsIgnoreCase(username))
                .findFirst()
                .map(profile -> new GameProfile(profile.uuid(), profile.name()));
    }

    private static boolean validateCustomProfileApi(Object value) {
        return value instanceof String api && !api.isBlank();
    }

    private static boolean validateFallbackProfile(Object value) {
        return value instanceof String entry && parseFallbackProfile(entry).isPresent();
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
