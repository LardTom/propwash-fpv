package de.lardtom.propwash.mixin.guard;

import de.lardtom.propwash.guard.GuardCrash;
import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SystemReport.class)
public abstract class SystemReportMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void propwash$addonSection(CallbackInfo ci) {
        if (!GuardCrash.viaCallable()) {
            ((SystemReport) (Object) this).setDetail(GuardCrash.TITLE, GuardCrash::section);
        }
    }
}
