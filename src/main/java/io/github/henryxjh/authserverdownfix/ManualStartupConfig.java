package io.github.henryxjh.authserverdownfix;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class ManualStartupConfig {
    public static final String FILE_NAME = "authserverdownfix-unsafe.toml";
    private static final String FORCE_UNSAFE_LOGIN_KEY = "forceUnsafeLoginOnStartup";
    private static final Logger LOGGER = LogUtils.getLogger();

    private ManualStartupConfig() {
    }

    public static boolean forceUnsafeLoginOnStartup() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        if (!Files.isRegularFile(path)) {
            LOGGER.info("Manual unsafe startup config is not present: {}", path);
            return false;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            CommentedConfig config = new TomlParser().parse(reader);
            Object value = config.get(FORCE_UNSAFE_LOGIN_KEY);
            if (value == null) {
                LOGGER.info("Manual unsafe startup config does not contain {}", FORCE_UNSAFE_LOGIN_KEY);
                return false;
            }
            if (value instanceof Boolean enabled) {
                return enabled;
            }

            LOGGER.warn(
                    "Ignoring {} in {} because it is not a boolean",
                    FORCE_UNSAFE_LOGIN_KEY,
                    path
            );
            return false;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to read manual unsafe startup config {}", path, exception);
            return false;
        }
    }
}
