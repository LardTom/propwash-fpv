package de.lardtom.propwash.mixin.race.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.lardtom.propwash.core.ExtraCollider;
import de.lardtom.propwash.race.FreeformReach;
import de.lardtom.propwash.race.GateEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {
    @WrapOperation(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB propwash$gatePickBox(Entity entity, Operation<AABB> original, @Local(argsOnly = true) Entity picker,
                                             @Local(argsOnly = true, ordinal = 0) Vec3 from, @Local(argsOnly = true, ordinal = 1) Vec3 to) {
        return entity instanceof GateEntity gate ? gate.pickBox(from, to, picker) : original.call(entity);
    }

    @WrapOperation(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> propwash$largeGates(Level level, @Nullable Entity except, AABB box, Predicate<? super Entity> matching,
                                                    Operation<List<Entity>> original) {
        List<Entity> found = original.call(level, except, box, matching);
        double reach = Math.max(ExtraCollider.reach(), GateEntity.picksFreeform(except) ? FreeformReach.reach() : 0.0);
        if (reach <= ExtraCollider.MAX_HALF_EXTENT) {
            return found;
        }
        List<GateEntity> gates = level.getEntitiesOfClass(GateEntity.class, box.inflate(reach), gate -> gate != except && matching.test(gate));
        List<Entity> merged = null;
        for (GateEntity gate : gates) {
            if (!found.contains(gate)) {
                if (merged == null) {
                    merged = new ArrayList<>(found);
                }
                merged.add(gate);
            }
        }
        return merged == null ? found : merged;
    }
}
