package io.github.henryxjh.authserverdownfix;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

@Mod(value = authserverdownfix.MODID, dist = Dist.DEDICATED_SERVER)
public class authserverdownfix {
    public static final String MODID = "authserverdownfix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public authserverdownfix(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Loading Auth Server Down Fix");
        modEventBus.addListener(this::onConfigLoaded);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void onServerStarting(ServerStartingEvent event) {
        UnsafeLoginMode.reset();
        AuthRecoveryMonitor.reset(event.getServer());
        if (ManualStartupConfig.forceUnsafeLoginOnStartup()) {
            LOGGER.warn(
                    "Forcing unsafe login mode on startup because forceUnsafeLoginOnStartup=true in {}",
                    ManualStartupConfig.FILE_NAME
            );
            UnsafeLoginMode.setEnabled(event.getServer(), true);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        AuthRecoveryMonitor.stop();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AuthServerDownFixCommands.register(event.getDispatcher());
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AuthRecoveryMonitor.onPlayerLoggedIn(player);
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AuthRecoveryMonitor.onPlayerLoggedOut(player);
        }
    }

    private void onConfigLoaded(ModConfigEvent event) {
        if (!MODID.equals(event.getConfig().getModId()) || event.getConfig().getType() != ModConfig.Type.COMMON) {
            return;
        }

        if (Config.useCustomProfileApis()) {
            List<String> customProfileApis = Config.getCustomProfileApis();
            if (customProfileApis.isEmpty()) {
                LOGGER.info("Auth Server Down Fix UUID lookup APIs: custom APIs enabled, but customProfileApis is empty");
            } else {
                LOGGER.info("Auth Server Down Fix UUID lookup APIs: customProfileApis={}", customProfileApis);
            }
        } else {
            LOGGER.info(
                    "Auth Server Down Fix UUID lookup APIs: official APIs={}, {}",
                    "https://api.mojang.com/users/profiles/minecraft/{name}",
                    "https://api.minecraftservices.com/minecraft/profile/lookup/name/{name}"
            );
        }
        LOGGER.info(
                "Auth Server Down Fix unsafe-player kick delay after authentication recovery: {} seconds (0 disables)",
                Config.getUnsafePlayerKickDelaySeconds()
        );
    }
}
