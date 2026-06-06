package io.github.henryxjh.authserverdownfix;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

@Mod(value = authserverdownfix.MODID, dist = Dist.DEDICATED_SERVER)
public class authserverdownfix {
    public static final String MODID = "authserverdownfix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public authserverdownfix(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Loading Auth Server Down Fix on dedicated server");
        modEventBus.addListener(this::onConfigLoaded);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
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
