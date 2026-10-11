package de.lardtom.propwash.mixin.drone.client;

import de.lardtom.propwash.drone.client.PilotView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    @Final
    public Options options;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void propwash$blockWorldInteraction(CallbackInfo ci) {
        if (!PilotView.active()) {
            if (PilotView.useLatched()) {
                propwash$drain(this.options.keyUse);
            }
            return;
        }
        boolean used = false;
        while (this.options.keyUse.consumeClick()) {
            used = true;
        }
        propwash$drain(this.options.keyAttack);
        propwash$drain(this.options.keyUse);
        propwash$drain(this.options.keyPickItem);
        propwash$drain(this.options.keyTogglePerspective);
        propwash$drain(this.options.keySwapOffhand);
        propwash$drain(this.options.keyDrop);
        propwash$drain(this.options.keyInventory);
        for (KeyMapping hotbar : this.options.keyHotbarSlots) {
            propwash$drain(hotbar);
        }
        if (used) {
            PilotView.onUseKeyWhilePiloting();
        }
    }

    @Unique
    private static void propwash$drain(KeyMapping mapping) {
        while (mapping.consumeClick()) {
            mapping.setDown(false);
        }
        mapping.setDown(false);
    }
}
