package io.github.henryxjh.authserverdownfix.mixin;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.slf4j.Logger;

@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientHandshakePacketListenerImplMixin {
    private static final Logger authserverdownfix$LOGGER = LogUtils.getLogger();

    @Redirect(
            method = "authenticateServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;joinServer(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;)V"
            )
    )
    private void authserverdownfix$continueWhenClientAuthServersUnavailable(
            MinecraftSessionService sessionService,
            UUID profileId,
            String accessToken,
            String serverHash
    ) throws AuthenticationException {
        try {
            sessionService.joinServer(profileId, accessToken, serverHash);
        } catch (AuthenticationUnavailableException exception) {
            authserverdownfix$LOGGER.warn(
                    "Client authentication servers are unavailable; continuing login so the server can decide whether unsafe login is enabled",
                    exception
            );
        }
    }
}
