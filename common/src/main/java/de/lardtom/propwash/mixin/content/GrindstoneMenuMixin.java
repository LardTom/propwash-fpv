package de.lardtom.propwash.mixin.content;

import de.lardtom.propwash.content.PropRepairs;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
    @Shadow
    @Final
    private Container repairSlots;

    @ModifyArg(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/GrindstoneMenu;addSlot(Lnet/minecraft/world/inventory/Slot;)Lnet/minecraft/world/inventory/Slot;"),
            require = 2)
    private Slot propwash$propInputs(Slot slot) {
        return slot.container == this.repairSlots ? PropRepairs.grindstoneSlot(slot) : slot;
    }

    @Inject(method = "computeResult", at = @At("HEAD"), cancellable = true)
    private void propwash$combineProps(ItemStack input, ItemStack additional, CallbackInfoReturnable<ItemStack> cir) {
        if (PropRepairs.isProp(input) || PropRepairs.isProp(additional)) {
            cir.setReturnValue(PropRepairs.grindstone(input, additional));
        }
    }
}
