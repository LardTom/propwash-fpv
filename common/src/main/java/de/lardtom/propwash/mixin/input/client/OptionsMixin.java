package de.lardtom.propwash.mixin.input.client;

import de.lardtom.propwash.input.client.KeyDefaults;
import net.minecraft.client.Options;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "dataFix", at = @At("RETURN"))
    private void propwash$renamedKeys(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        KeyDefaults.migrateOptions(cir.getReturnValue());
    }
}
