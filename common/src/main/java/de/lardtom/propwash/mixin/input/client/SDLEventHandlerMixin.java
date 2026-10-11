package de.lardtom.propwash.mixin.input.client;

import com.mojang.blaze3d.platform.SDLEventHandler;
import de.lardtom.propwash.input.client.MouseCapture;
import de.lardtom.propwash.input.client.TransmitterInput;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_MouseMotionEvent;
import org.lwjgl.sdl.SDL_MouseWheelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SDLEventHandler.class)
public abstract class SDLEventHandlerMixin {
    @Inject(method = "pollEvents", at = @At("TAIL"))
    private void propwash$afterPollEvents(CallbackInfo ci) {
        TransmitterInput.INSTANCE.onFrame();
    }

    @Inject(method = "handleMouseMotionEvent", at = @At("HEAD"))
    private void propwash$mouseMotion(SDL_Event event, CallbackInfo ci) {
        SDL_MouseMotionEvent motion = event.motion();
        MouseCapture.INSTANCE.onMotion(motion.timestamp(), motion.windowID(), motion.which(), motion.xrel(), motion.yrel());
    }

    @Inject(method = "handleMouseWheelEvent", at = @At("HEAD"), cancellable = true)
    private void propwash$mouseWheel(SDL_Event event, CallbackInfo ci) {
        SDL_MouseWheelEvent wheel = event.wheel();
        if (MouseCapture.INSTANCE.onWheel(wheel.windowID(), wheel.y(), wheel.direction() == SDLMouse.SDL_MOUSEWHEEL_FLIPPED)) {
            ci.cancel();
        }
    }
}
