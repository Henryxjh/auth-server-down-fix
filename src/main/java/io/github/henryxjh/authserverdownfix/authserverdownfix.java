package io.github.henryxjh.authserverdownfix;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(authserverdownfix.MODID)
public class authserverdownfix {
    public static final String MODID = "authserverdownfix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public authserverdownfix(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Loading Auth Server Down Fix");
        modEventBus.addListener(this::onConfigLoaded);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void onServerStarting(ServerStartingEvent event) {
        UnsafeLoginMode.reset();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AuthServerDownFixCommands.register(event.getDispatcher());
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
    }
}
