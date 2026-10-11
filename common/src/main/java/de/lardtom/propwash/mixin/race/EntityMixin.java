package de.lardtom.propwash.mixin.race;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.lardtom.propwash.race.GateCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @WrapOperation(method = "collide",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<VoxelShape> propwash$gateColliders(Level level, @Nullable Entity source, AABB area, Operation<List<VoxelShape>> original) {
        List<VoxelShape> shapes = original.call(level, source, area);
        return GateCollisions.append((Entity) (Object) this, level, area, shapes);
    }
}
