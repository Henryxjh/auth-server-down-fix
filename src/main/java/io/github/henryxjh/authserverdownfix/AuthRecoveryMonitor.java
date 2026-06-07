package io.github.henryxjh.authserverdownfix;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class AuthRecoveryMonitor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long PROBE_INTERVAL_MINUTES = 5;
    private static final String PROBE_USERNAME = "AuthFixProbe";
    private static final String PROBE_SERVER_ID = "authserverdownfix-probe";
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(new MonitorThreadFactory());
    private static final Set<UUID> PENDING_UNSAFE_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ONLINE_UNSAFE_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Object MONITOR_LOCK = new Object();
    private static volatile MinecraftServer server;
    private static ScheduledFuture<?> recoveryTask;
    private static ScheduledFuture<?> kickTask;

    private AuthRecoveryMonitor() {
    }

    public static void reset(MinecraftServer server) {
        synchronized (MONITOR_LOCK) {
            cancel(recoveryTask);
            cancel(kickTask);
            recoveryTask = null;
            kickTask = null;
            AuthRecoveryMonitor.server = server;
            PENDING_UNSAFE_PLAYERS.clear();
            ONLINE_UNSAFE_PLAYERS.clear();
        }
    }

    public static void stop() {
        synchronized (MONITOR_LOCK) {
            cancel(recoveryTask);
            cancel(kickTask);
            recoveryTask = null;
            kickTask = null;
            server = null;
            PENDING_UNSAFE_PLAYERS.clear();
            ONLINE_UNSAFE_PLAYERS.clear();
        }
    }

    public static void onAuthenticationUnavailable(MinecraftSessionService sessionService) {
        synchronized (MONITOR_LOCK) {
            if (recoveryTask != null && !recoveryTask.isDone()) {
                return;
            }

            LOGGER.warn(
                    "Authentication server recovery monitor started; checking every {} minutes",
                    PROBE_INTERVAL_MINUTES
            );
            recoveryTask = SCHEDULER.scheduleWithFixedDelay(
                    () -> probeAuthenticationServer(sessionService),
                    PROBE_INTERVAL_MINUTES,
                    PROBE_INTERVAL_MINUTES,
                    TimeUnit.MINUTES
            );
        }
    }

    public static void onPlayerAuthenticatedNormally(GameProfile profile) {
        synchronized (MONITOR_LOCK) {
            if (recoveryTask == null || recoveryTask.isDone()) {
                return;
            }
        }

        LOGGER.info(
                "Player {} ({}) completed normal online authentication; treating authentication server as recovered",
                profile.getName(),
                profile.getId()
        );
        authenticationServerRecovered();
    }

    public static void markPendingUnsafe(GameProfile profile) {
        PENDING_UNSAFE_PLAYERS.add(profile.getId());
        LOGGER.warn("Marked {} ({}) as pending unsafe login", profile.getName(), profile.getId());
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (PENDING_UNSAFE_PLAYERS.remove(uuid)) {
            ONLINE_UNSAFE_PLAYERS.add(uuid);
            LOGGER.warn("{} ({}) joined through unsafe UUID lookup", player.getGameProfile().getName(), uuid);
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PENDING_UNSAFE_PLAYERS.remove(uuid);
        if (ONLINE_UNSAFE_PLAYERS.remove(uuid)) {
            LOGGER.info("Removed unsafe-login player {} ({}) after logout", player.getGameProfile().getName(), uuid);
        }
    }

    public static boolean isUnsafePlayer(UUID uuid) {
        return ONLINE_UNSAFE_PLAYERS.contains(uuid);
    }

    private static void probeAuthenticationServer(MinecraftSessionService sessionService) {
        try {
            sessionService.hasJoinedServer(PROBE_USERNAME, PROBE_SERVER_ID, null);
            authenticationServerRecovered();
        } catch (AuthenticationUnavailableException exception) {
            LOGGER.debug("Authentication server recovery probe failed", exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unexpected error while probing authentication server recovery", exception);
        }
    }

    private static void authenticationServerRecovered() {
        MinecraftServer currentServer;
        synchronized (MONITOR_LOCK) {
            if (recoveryTask == null) {
                return;
            }

            cancel(recoveryTask);
            recoveryTask = null;
            currentServer = server;
        }

        LOGGER.info("Authentication server connection has recovered");
        if (currentServer == null) {
            return;
        }

        currentServer.execute(() -> {
            Component message = Component.literal(Config.getAuthenticationRecoveredBroadcast()).withStyle(ChatFormatting.GREEN);
            currentServer.getPlayerList().broadcastSystemMessage(message, false);
        });

        int kickDelaySeconds = Config.getUnsafePlayerKickDelaySeconds();
        if (kickDelaySeconds <= 0 || ONLINE_UNSAFE_PLAYERS.isEmpty()) {
            return;
        }

        LOGGER.warn(
                "Scheduling {} unsafe-login player(s) to be disconnected in {} seconds",
                ONLINE_UNSAFE_PLAYERS.size(),
                kickDelaySeconds
        );
        currentServer.execute(() -> {
            Component message = Component.literal(
                    Config.getUnsafePlayerKickCountdownBroadcast(kickDelaySeconds)
            ).withStyle(ChatFormatting.YELLOW);
            currentServer.getPlayerList().broadcastSystemMessage(message, false);
            sendKickCountdownSubtitle(currentServer, message, kickDelaySeconds);
        });
        synchronized (MONITOR_LOCK) {
            cancel(kickTask);
            kickTask = SCHEDULER.schedule(
                    () -> currentServer.execute(() -> disconnectUnsafePlayers(currentServer)),
                    kickDelaySeconds,
                    TimeUnit.SECONDS
            );
        }
    }

    private static void sendKickCountdownSubtitle(MinecraftServer server, Component message, int kickDelaySeconds) {
        int stayTicks = (int) Math.min(Integer.MAX_VALUE, Math.max(20L, kickDelaySeconds * 20L));
        ClientboundSetTitlesAnimationPacket animationPacket = new ClientboundSetTitlesAnimationPacket(10, stayTicks, 20);
        ClientboundSetSubtitleTextPacket subtitlePacket = new ClientboundSetSubtitleTextPacket(message);
        ClientboundSetTitleTextPacket emptyTitlePacket = new ClientboundSetTitleTextPacket(CommonComponents.EMPTY);

        for (UUID uuid : Set.copyOf(ONLINE_UNSAFE_PLAYERS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.connection.send(animationPacket);
                player.connection.send(subtitlePacket);
                player.connection.send(emptyTitlePacket);
            }
        }
    }

    private static void disconnectUnsafePlayers(MinecraftServer server) {
        Component reason = Component.literal(
                "Authentication servers recovered. Reconnect to complete online authentication."
        );
        List<String> disconnectedPlayers = new ArrayList<>();
        for (UUID uuid : Set.copyOf(ONLINE_UNSAFE_PLAYERS)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                disconnectedPlayers.add(player.getGameProfile().getName());
                player.connection.disconnect(reason);
            } else {
                ONLINE_UNSAFE_PLAYERS.remove(uuid);
            }
        }

        if (!disconnectedPlayers.isEmpty()) {
            Component message = Component.literal(
                    Config.getUnsafePlayersKickedBroadcast(String.join(", ", disconnectedPlayers))
            ).withStyle(ChatFormatting.YELLOW);
            server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    private static final class MonitorThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Auth Server Down Fix Recovery Monitor");
            thread.setDaemon(true);
            return thread;
        }
    }
}
