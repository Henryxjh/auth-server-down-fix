package io.github.henryxjh.authserverdownfix;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class UnsafeLoginMode {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean enabled;

    private UnsafeLoginMode() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean enable(MinecraftServer server) {
        return setEnabled(server, true);
    }

    public static boolean setEnabled(MinecraftServer server, boolean enabled) {
        boolean changed = UnsafeLoginMode.enabled != enabled;
        if (!changed) {
            return false;
        }

        UnsafeLoginMode.enabled = enabled;
        if (enabled) {
            Component warning = Component.literal(
                    "WARNING: Unsafe login mode is enabled. If Mojang authentication servers are unavailable, "
                            + "players may join without completing live online authentication. Do not grant sensitive "
                            + "permissions while this mode is enabled."
            ).withStyle(ChatFormatting.RED);
            server.getPlayerList().broadcastSystemMessage(warning, false);
            LOGGER.warn("Unsafe login mode was enabled for this server session");
        } else {
            Component message = Component.literal(
                    "Unsafe login mode is disabled. Players must complete vanilla online authentication."
            ).withStyle(ChatFormatting.GREEN);
            server.getPlayerList().broadcastSystemMessage(message, false);
            LOGGER.info("Unsafe login mode was disabled for this server session");
        }

        return changed;
    }

    public static void reset() {
        enabled = false;
        LOGGER.info("Unsafe login mode is disabled for this server session");
    }
}
