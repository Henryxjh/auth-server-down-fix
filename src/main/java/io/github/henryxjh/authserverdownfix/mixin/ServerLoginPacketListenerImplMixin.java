package io.github.henryxjh.authserverdownfix.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.logging.LogUtils;
import java.net.InetAddress;
import java.util.Optional;
import io.github.henryxjh.authserverdownfix.AuthRecoveryMonitor;
import io.github.henryxjh.authserverdownfix.ProfileLookupService;
import io.github.henryxjh.authserverdownfix.UnsafeLoginMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.slf4j.Logger;

@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl$1")
public class ServerLoginPacketListenerImplMixin {
    @Unique
    private static final Logger authserverdownfix$LOGGER = LogUtils.getLogger();

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
                AuthRecoveryMonitor.onPlayerAuthenticatedNormally(result.profile());
            }
            return result;
        } catch (AuthenticationUnavailableException exception) {
            AuthRecoveryMonitor.onAuthenticationUnavailable(sessionService);
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
            if (!UnsafeLoginMode.isEnabled()) {
                authserverdownfix$LOGGER.warn(
                        "Unsafe login mode is disabled; keeping vanilla authentication server disconnect for {}",
                        username
                );
                throw exception;
            }

            Optional<GameProfile> profile = ProfileLookupService.fetchProfileForLogin(username);
            if (profile.isPresent()) {
                AuthRecoveryMonitor.markPendingUnsafe(profile.get());
                return new ProfileResult(profile.get());
            }

            authserverdownfix$LOGGER.warn("No fallback UUID was available for {}, keeping vanilla authentication server disconnect", username);
            throw exception;
        }
    }
}
