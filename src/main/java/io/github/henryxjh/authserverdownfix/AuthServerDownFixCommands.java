package io.github.henryxjh.authserverdownfix;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class AuthServerDownFixCommands {
    private AuthServerDownFixCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(authserverdownfix.MODID)
                .then(Commands.literal("enableUnsafeLogin")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setUnsafeLogin(
                                        context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")
                                ))))
                .then(Commands.literal("fallbackProfiles")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                context.getSource().getServer().getPlayerList().getPlayerNamesArray(),
                                                builder
                                        ))
                                        .executes(context -> addFallbackProfile(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        ))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Config.getFallbackGameProfiles().stream()
                                                        .map(GameProfile::getName),
                                                builder
                                        ))
                                        .executes(context -> removeFallbackProfile(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "name")
                                        ))))
                        .then(Commands.literal("list")
                                .executes(context -> listFallbackProfiles(context.getSource()))))
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource()))));
    }

    private static int setUnsafeLogin(CommandSourceStack source, boolean enabled) {
        boolean changed = UnsafeLoginMode.setEnabled(source.getServer(), enabled);
        if (changed) {
            source.sendSuccess(() -> Component.literal(
                    "Unsafe login mode " + (enabled ? "enabled" : "disabled") + " for this server session."
            ).withStyle(enabled ? ChatFormatting.RED : ChatFormatting.GREEN), true);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "Unsafe login mode is already " + (enabled ? "enabled" : "disabled") + " for this server session."
            ).withStyle(ChatFormatting.YELLOW), false);
        }
        return changed ? 1 : 0;
    }

    private static int addFallbackProfile(CommandSourceStack source, String name) {
        Optional<GameProfile> profile = ProfileLookupService.fetchProfileFromConfiguredApis(name);
        if (profile.isEmpty()) {
            source.sendFailure(Component.literal(
                    "Failed to query UUID for " + name + " from the configured profile lookup API(s)."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean replaced = Config.addOrReplaceFallbackProfile(profile.get());
        source.sendSuccess(() -> Component.literal(
                (replaced ? "Updated" : "Added")
                        + " fallback profile: "
                        + profile.get().getName()
                        + "="
                        + profile.get().getId().toString().replace("-", "")
        ).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeFallbackProfile(CommandSourceStack source, String name) {
        boolean removed = Config.removeFallbackProfile(name);
        if (removed) {
            source.sendSuccess(() -> Component.literal(
                    "Removed fallback profile for " + name + "."
            ).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        source.sendFailure(Component.literal(
                "No fallback profile found for " + name + "."
        ).withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int listFallbackProfiles(CommandSourceStack source) {
        List<GameProfile> profiles = Config.getFallbackGameProfiles();
        if (profiles.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "No fallback profiles are configured."
            ).withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Configured fallback profiles (" + profiles.size() + "):"
        ).withStyle(ChatFormatting.GREEN), false);
        for (GameProfile profile : profiles) {
            source.sendSuccess(() -> Component.literal(
                    profile.getName() + "=" + profile.getId().toString().replace("-", "")
            ).withStyle(ChatFormatting.GRAY), false);
        }
        return profiles.size();
    }

    private static int showStatus(CommandSourceStack source) {
        boolean enabled = UnsafeLoginMode.isEnabled();
        source.sendSuccess(() -> Component.literal(
                "Unsafe login mode is " + (enabled ? "enabled" : "disabled") + "."
        ).withStyle(enabled ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        if (source.getPlayer() != null) {
            boolean unsafePlayer = AuthRecoveryMonitor.isUnsafePlayer(source.getPlayer().getUUID());
            source.sendSuccess(() -> Component.literal(
                    "You " + (unsafePlayer ? "joined through unsafe UUID lookup." : "completed normal online authentication.")
            ).withStyle(unsafePlayer ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        }
        return enabled ? 1 : 0;
    }
}
