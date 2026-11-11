package de.lardtom.propwash.video.client;

import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.video.DarknessPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class BossDarkness {
    public static final BossDarkness INSTANCE = new BossDarkness();

    private final DarknessPulse pulse = new DarknessPulse();
    private int ticks;
    private boolean near;

    private BossDarkness() {
    }

    public void tick(@Nullable Entity drone, @Nullable Level level) {
        if (drone == null || level == null) {
            this.reset();
            return;
        }
        this.near = ClientWorldRules.INSTANCE.on(WorldRuleSpecs.DRONES_BOSS_INTERACTIONS) && wardenNear(level, drone);
        this.pulse.tick(this.near);
        this.ticks++;
    }

    public static boolean wardenNear(Level level, Entity drone) {
        for (Warden warden : level.getEntitiesOfClass(Warden.class, drone.getBoundingBox().inflate(DarknessPulse.RADIUS))) {
            if (!warden.isRemoved() && DarknessPulse.near(warden.getX() - drone.getX(), warden.getY() - drone.getY(), warden.getZ() - drone.getZ())) {
                return true;
            }
        }
        return false;
    }

    public boolean near() {
        return this.near;
    }

    public float blend() {
        return this.pulse.blend() * option();
    }

    public void applyLight(LightmapRenderState state, float gamma) {
        if (!this.pulse.active()) {
            return;
        }
        float option = option();
        float blend = this.pulse.blend(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) * option;
        if (blend <= 0.0F) {
            return;
        }
        state.brightness = DarknessPulse.brightness(gamma, blend);
        state.darknessEffectScale = DarknessPulse.lightScale(blend, this.ticks) * option;
    }

    public void reset() {
        this.pulse.reset();
        this.near = false;
        this.ticks = 0;
    }

    private static float option() {
        return Minecraft.getInstance().options.darknessEffectScale().get().floatValue();
    }
}
