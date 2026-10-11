package de.lardtom.propwash.mixin.audio.client;

import de.lardtom.propwash.audio.client.AudioHooks;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "stop(Lnet/minecraft/resources/Identifier;Lnet/minecraft/sounds/SoundSource;)V", at = @At("HEAD"))
    private void propwash$onStopCommand(@Nullable Identifier sound, @Nullable SoundSource source, CallbackInfo ci) {
        AudioHooks.onStopCommand(sound, source);
    }
}
