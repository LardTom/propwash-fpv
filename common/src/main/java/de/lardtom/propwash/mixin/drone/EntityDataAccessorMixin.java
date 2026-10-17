package de.lardtom.propwash.mixin.drone;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.HomePoints;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.data.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityDataAccessor.class)
public abstract class EntityDataAccessorMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "setData", at = @At("HEAD"))
    private void propwash$checkHomePoint(CompoundTag tag, CallbackInfo ci) throws CommandSyntaxException {
        if (this.entity instanceof QuadcopterEntity drone) {
            HomePoints.prepareEdit(drone, tag);
        }
    }
}
