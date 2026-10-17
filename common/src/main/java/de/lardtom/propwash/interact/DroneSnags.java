package de.lardtom.propwash.interact;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.DroneWear;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.net.SnagWearC2S;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimEnvironment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.WeakHashMap;

public final class DroneSnags {
    public static final double NEAR = 2.0;

    private static final Map<QuadcopterEntity, Long> TIMES = new WeakHashMap<>();

    private DroneSnags() {
    }

    static void reset() {
        TIMES.clear();
    }

    static void onWear(SnagWearC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        if (!(level.getEntity(payload.droneId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.isFlownBy(player.getUUID()) || payload.epoch() != drone.resetEpoch() || payload.empty()) {
            return;
        }
        float strength = WorldRules.crashDamageStrength(level.getServer());
        if (strength <= 0.0F || !SnagBlocks.anyNear(level, drone.getBoundingBox().inflate(NEAR))) {
            return;
        }
        long now = level.getGameTime();
        Long last = TIMES.put(drone, now);
        double seconds = last == null || now < last ? 1.0 : (now - last) / 20.0;
        apply(level, drone, payload.propWear(), SnagMath.wearAllowance(seconds), strength);
    }

    static PartWear apply(ServerLevel level, QuadcopterEntity drone, float[] propWear, float allowance, float strength) {
        PartWear before = drone.wear();
        PartWear after = SnagWear.apply(before, propWear, allowance, strength);
        if (after.equals(before)) {
            return before;
        }
        drone.setWear(after);
        if (DroneWear.newlyBroken(before, after)) {
            level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.7F, 1.3F);
        }
        return after;
    }

    public static final class Flight {
        private final SnagSampler sampler = new SnagSampler();
        private final SnagBlocks blocks = new SnagBlocks();
        private final SnagWear wear = new SnagWear();
        private double seconds;

        public void fill(ServerLevel level, DroneSim sim, SimEnvironment env, double dt) {
            this.sampler.sample(sim, this.blocks.at(level), env.snag);
            this.wear.add(this.sampler, sim.telemetry(), sim.params().omegaMax, dt);
            this.seconds += dt;
            this.blocks.at(null);
        }

        public void applyWear(ServerLevel level, QuadcopterEntity drone, DroneSim sim) {
            if (this.seconds < 1.0) {
                return;
            }
            double span = this.seconds;
            this.seconds = 0.0;
            if (!this.wear.any()) {
                return;
            }
            float[] amounts = this.wear.drain();
            float strength = WorldRules.crashDamageStrength(level.getServer());
            if (strength <= 0.0F) {
                return;
            }
            PartWear before = drone.wear();
            PartWear after = apply(level, drone, amounts, SnagMath.wearAllowance(span), strength);
            if (!after.equals(before)) {
                sim.setWear(after);
            }
        }
    }
}
