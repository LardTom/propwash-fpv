package de.lardtom.propwash.mixin.drone;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WitherBoss.class)
public interface WitherBossAccessor {
    @Invoker("performRangedAttack")
    void propwash$performRangedAttack(int head, double x, double y, double z, boolean dangerous);
}
