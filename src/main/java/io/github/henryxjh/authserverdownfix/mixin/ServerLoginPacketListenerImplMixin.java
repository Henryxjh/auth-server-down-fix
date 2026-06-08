package io.github.henryxjh.authserverdownfix.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.logging.LogUtils;
import java.net.InetAddress;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.slf4j.Logger;

@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl$1")
public class ServerLoginPacketListenerImplMixin {
    @Unique
    private static final Logger authserverdownfix$LOGGER = LogUtils.getLogger();
    @Unique
    private static final String authserverdownfix$AUTH_RECOVERY_MONITOR = "io.github.henryxjh.authserverdownfix.AuthRecoveryMonitor";
    @Unique
    private static final String authserverdownfix$PROFILE_LOOKUP_SERVICE = "io.github.henryxjh.authserverdownfix.ProfileLookupService";
    @Unique
    private static final String authserverdownfix$UNSAFE_LOGIN_MODE = "io.github.henryxjh.authserverdownfix.UnsafeLoginMode";

    @Redirect(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;hasJoinedServer(Ljava/lang/String;Ljava/lang/String;Ljava/net/InetAddress;)Lcom/mojang/authlib/yggdrasil/ProfileResult;"
            )
    )
    private ProfileResult authserverdownfix$handleAuthSessionServiceUnavailable(
            MinecraftSessionService sessionService,
            String username,
            String serverId,
            InetAddress address
    ) throws AuthenticationUnavailableException {
        try {
            ProfileResult result = sessionService.hasJoinedServer(username, serverId, address);
            if (result != null) {
                authserverdownfix$onPlayerAuthenticatedNormally(result.profile());
            }
            return result;
        } catch (AuthenticationUnavailableException exception) {
            authserverdownfix$onAuthenticationUnavailable(sessionService);
            authserverdownfix$LOGGER.info(
                    "Authentication session service is unavailable for {}, trying fallback UUID lookup",
                    username
            );
            authserverdownfix$LOGGER.debug(
                    "Session service lookup failed for {} with serverId={} address={}",
                    username,
                    serverId,
                    address,
                    exception
            );
            if (!authserverdownfix$isUnsafeLoginEnabled()) {
                authserverdownfix$LOGGER.warn(
                        "Unsafe login mode is disabled; keeping vanilla authentication server disconnect for {}",
                        username
                );
                throw exception;
            }

            Optional<GameProfile> profile = authserverdownfix$fetchProfileForLogin(username);
            if (profile.isPresent()) {
                authserverdownfix$markPendingUnsafe(profile.get());
                return new ProfileResult(profile.get());
            }

            authserverdownfix$LOGGER.warn("No fallback UUID was available for {}, keeping vanilla authentication server disconnect", username);
            throw exception;
        }
    }

    @Unique
    private static void authserverdownfix$onPlayerAuthenticatedNormally(GameProfile profile) {
        authserverdownfix$invokeStatic(
                authserverdownfix$AUTH_RECOVERY_MONITOR,
                "onPlayerAuthenticatedNormally",
                new Class<?>[]{GameProfile.class},
                profile
        );
    }

    @Unique
    private static void authserverdownfix$onAuthenticationUnavailable(MinecraftSessionService sessionService) {
        authserverdownfix$invokeStatic(
                authserverdownfix$AUTH_RECOVERY_MONITOR,
                "onAuthenticationUnavailable",
                new Class<?>[]{MinecraftSessionService.class},
                sessionService
        );
    }

    @Unique
    private static boolean authserverdownfix$isUnsafeLoginEnabled() {
        return authserverdownfix$invokeStatic(
                authserverdownfix$UNSAFE_LOGIN_MODE,
                "isEnabled",
                new Class<?>[0]
        ).filter(Boolean.class::isInstance).map(Boolean.class::cast).orElse(false);
    }

    @Unique
    private static Optional<GameProfile> authserverdownfix$fetchProfileForLogin(String username) {
        Optional<Object> result = authserverdownfix$invokeStatic(
                authserverdownfix$PROFILE_LOOKUP_SERVICE,
                "fetchProfileForLogin",
                new Class<?>[]{String.class},
                username
        );
        if (result.isEmpty() || !(result.get() instanceof Optional<?> profile)) {
            return Optional.empty();
        }

        return profile.filter(GameProfile.class::isInstance).map(GameProfile.class::cast);
    }

    @Unique
    private static void authserverdownfix$markPendingUnsafe(GameProfile profile) {
        authserverdownfix$invokeStatic(
                authserverdownfix$AUTH_RECOVERY_MONITOR,
                "markPendingUnsafe",
                new Class<?>[]{GameProfile.class},
                profile
        );
    }

    @Unique
    private static Optional<Object> authserverdownfix$invokeStatic(
            String className,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        try {
            Class<?> owner = Class.forName(className);
            Method method = owner.getMethod(methodName, parameterTypes);
            return Optional.ofNullable(method.invoke(null, arguments));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            authserverdownfix$LOGGER.error(
                    "Failed to access {}.{} from login mixin",
                    className,
                    methodName,
                    exception
            );
            return Optional.empty();
        } catch (InvocationTargetException exception) {
            authserverdownfix$LOGGER.error(
                    "Failed to invoke {}.{} from login mixin",
                    className,
                    methodName,
                    exception.getCause()
            );
            return Optional.empty();
        }
    }
}
