package de.lardtom.propwash.drone;

import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.net.HardwareWearC2S;
import de.lardtom.propwash.drone.net.ImpactC2S;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.CrashDamage;
import de.lardtom.propwash.sim.ImpactSample;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class DroneWear {
    public static final int IMPACTS_PER_SECOND = 24;
    public static final int WINDOW_TICKS = 20;
    public static final float HEAT_WEAR_SLACK = 1.5F;
    public static final float MAX_HEAT_WEAR_PER_PACKET = 0.25F;

    private static final Map<QuadcopterEntity, Budget> BUDGETS = new WeakHashMap<>();
    private static final Map<QuadcopterEntity, Long> HEAT_TIMES = new WeakHashMap<>();

    private DroneWear() {
    }

    static void init() {
        PropwashNet.registerC2S(ImpactC2S.TYPE, ImpactC2S.CODEC, DroneWear::onImpacts);
        PropwashNet.registerC2S(HardwareWearC2S.TYPE, HardwareWearC2S.CODEC, DroneWear::onHardware);
    }

    public static PartWear apply(PartWear wear, DroneBuild build, List<ImpactSample> impacts, float strength, long seed) {
        PartWear result = wear;
        long salt = seed;
        for (ImpactSample impact : impacts) {
            if (impact == null) {
                continue;
            }
            ImpactSample clean = impact.sanitized();
            if (!CrashDamage.harmful(build, clean)) {
                continue;
            }
            salt = salt * 6364136223846793005L + 1442695040888963407L;
            result = CrashDamage.apply(result, build, clean, strength, salt);
        }
        return result;
    }

    public static float heatWearAllowance(DroneBuild build, double seconds) {
        double rate = AirframeSpec.of(build).motorHeatWearPerSecond;
        double allowed = rate * Math.max(1.0, seconds) * HEAT_WEAR_SLACK;
        return (float) Math.min(MAX_HEAT_WEAR_PER_PACKET, Math.max(0.0, allowed));
    }

    public static PartWear applyHardware(PartWear wear, DroneBuild build, boolean escBurned, float[] motorHeatWear, float allowance) {
        PartWear result = wear;
        if (escBurned && AirframeSpec.of(build).escOvervoltage) {
            result = result.withStack(0.0F);
        }
        if (motorHeatWear != null) {
            for (int position = 0; position < Math.min(PartWear.POSITIONS, motorHeatWear.length); position++) {
                float amount = motorHeatWear[position];
                if (Float.isFinite(amount) && amount > 0.0F) {
                    float applied = Math.min(amount, allowance);
                    result = result.withMotor(position, Math.max(0.0F, result.motor(position) - applied));
                }
            }
        }
        return result;
    }

    public static boolean newlyBroken(PartWear before, PartWear after) {
        for (int slot = 0; slot < PartWear.SLOTS; slot++) {
            if (!PartWear.broken(before.slot(slot)) && PartWear.broken(after.slot(slot))) {
                return true;
            }
        }
        return false;
    }

    private static void onImpacts(ImpactC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        if (!(level.getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.isFlownBy(player.getUUID()) || payload.epoch() != drone.resetEpoch()) {
            return;
        }
        float strength = WorldRules.crashDamageStrength(level.getServer());
        if (strength <= 0.0F || payload.impacts().isEmpty()) {
            return;
        }
        List<ImpactSample> admitted = BUDGETS.computeIfAbsent(drone, d -> new Budget()).admit(level.getGameTime(), payload.impacts());
        if (admitted.isEmpty()) {
            return;
        }
        PartWear before = drone.wear();
        PartWear after = apply(before, drone.build(), admitted, strength, level.getRandom().nextLong());
        if (after.equals(before)) {
            return;
        }
        drone.setWear(after);
        if (newlyBroken(before, after)) {
            level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.7F, 1.3F);
        }
    }

    private static void onHardware(HardwareWearC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        if (!(level.getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.isFlownBy(player.getUUID()) || payload.epoch() != drone.resetEpoch() || !WorldRules.crashDamage(level.getServer()) || payload.empty()) {
            return;
        }
        long now = level.getGameTime();
        Long last = HEAT_TIMES.put(drone, now);
        double seconds = last == null || now < last ? 1.0 : (now - last) / 20.0;
        PartWear before = drone.wear();
        PartWear after = applyHardware(before, drone.build(), payload.escBurned(), payload.motorHeatWear(),
                heatWearAllowance(drone.build(), seconds));
        if (after.equals(before)) {
            return;
        }
        drone.setWear(after);
        if (newlyBroken(before, after)) {
            level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.7F, 1.3F);
        }
        if (escBurnedNow(payload.escBurned(), before, after)) {
            BuildAccessImpl.escBurned(player, drone.toStack(), drone.build().battery().cells);
        }
    }

    public static boolean escBurnedNow(boolean reported, PartWear before, PartWear after) {
        return reported && !PartWear.broken(before.stack()) && PartWear.broken(after.stack());
    }

    private static final class Budget {
        private long windowStart = Long.MIN_VALUE;
        private int used;

        List<ImpactSample> admit(long gameTime, List<ImpactSample> impacts) {
            if (this.windowStart == Long.MIN_VALUE || gameTime - this.windowStart >= WINDOW_TICKS || gameTime < this.windowStart) {
                this.windowStart = gameTime;
                this.used = 0;
            }
            int free = Math.max(0, IMPACTS_PER_SECOND - this.used);
            int count = Math.min(free, impacts.size());
            this.used += count;
            return impacts.subList(0, count);
        }
    }
}
