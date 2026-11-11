package de.lardtom.propwash.mixin.guard;

import de.lardtom.propwash.apiimpl.NamespaceRules;
import de.lardtom.propwash.guard.RegistryOrigins;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin<T> {
    @Inject(method = "register", at = @At("HEAD"))
    private void propwash$recordRegistrant(ResourceKey<T> key, T value, RegistrationInfo info, CallbackInfoReturnable<Holder.Reference<T>> cir) {
        if (NamespaceRules.isReserved(key.identifier().getNamespace())) {
            RegistryOrigins.record(((MappedRegistry<?>) (Object) this).key().identifier().toString(), key.identifier().toString());
        }
    }
}
