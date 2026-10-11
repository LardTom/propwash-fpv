package de.lardtom.propwash.mixin.join.client;

import de.lardtom.propwash.join.AddonSession;
import de.lardtom.propwash.join.client.JoinCheckClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.DisconnectionDetails;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
    @Shadow
    @Final
    protected @Nullable ServerData serverData;

    @Shadow
    @Final
    protected @Nullable Screen postDisconnectScreen;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void propwash$endAddonSession(DisconnectionDetails details, CallbackInfo ci) {
        AddonSession.clear();
    }

    @Inject(method = "createDisconnectScreen", at = @At("HEAD"), cancellable = true)
    private void propwash$addonOverview(DisconnectionDetails details, CallbackInfoReturnable<Screen> cir) {
        if (!((Object) this instanceof ClientConfigurationPacketListenerImpl)) {
            return;
        }
        Screen parent = Objects.requireNonNullElseGet(this.postDisconnectScreen,
                () -> this.serverData != null ? new JoinMultiplayerScreen(new TitleScreen()) : new TitleScreen());
        Screen screen = JoinCheckClient.disconnectScreen(this, details, parent);
        if (screen != null) {
            cir.setReturnValue(screen);
        }
    }
}
