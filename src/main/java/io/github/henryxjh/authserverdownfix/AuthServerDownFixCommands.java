package io.github.henryxjh.authserverdownfix;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class AuthServerDownFixCommands {
    private AuthServerDownFixCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(authserverdownfix.MODID)
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("enableUnsafeLogin")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setUnsafeLogin(
                                        context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")
                                ))))
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

    private static int showStatus(CommandSourceStack source) {
        boolean enabled = UnsafeLoginMode.isEnabled();
        source.sendSuccess(() -> Component.literal(
                "Unsafe login mode is " + (enabled ? "enabled" : "disabled") + "."
        ).withStyle(enabled ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        return enabled ? 1 : 0;
    }
}
