package de.lardtom.propwash.interact;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.accessory.AddonEvents;
import de.lardtom.propwash.api.event.EntityHit;
import de.lardtom.propwash.core.event.Event;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.interact.net.DroneStrikeC2S;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class DroneStrikes {
    public static final ResourceKey<DamageType> DRONE_STRIKE = ResourceKey.create(Registries.DAMAGE_TYPE, Constants.id("drone_strike"));
    public static final Event<StrikeListener> STRUCK = new Event<>("drone_struck");
    public static final int PEAK_WINDOW_TICKS = 20;

    private static final Map<QuadcopterEntity, Tracker> TRACKERS = new WeakHashMap<>();
    private static boolean missingTypeLogged;

    private DroneStrikes() {
    }

    static void onMoved(QuadcopterEntity drone) {
        if (drone.level() instanceof ServerLevel level) {
            tracker(drone).sample(level.getGameTime(), drone.getDeltaMovement().length() * 20.0);
        }
    }

    static void reset() {
        TRACKERS.clear();
    }

    static double recentPeak(QuadcopterEntity drone, long now) {
        return tracker(drone).peak(now);
    }

    private static Tracker tracker(QuadcopterEntity drone) {
        return TRACKERS.computeIfAbsent(drone, d -> new Tracker());
    }

    static void onStrike(DroneStrikeC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        if (!(level.getEntity(payload.droneId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.isFlownBy(player.getUUID()) || payload.epoch() != drone.resetEpoch()) {
            return;
        }
        Entity entity = level.getEntity(payload.targetId());
        if (!(entity instanceof LivingEntity target) || !StrikeTargets.valid(target)) {
            return;
        }
        long now = level.getGameTime();
        Tracker tracker = tracker(drone);
        StrikeRules.Decision decision = StrikeRules.decide(InteractRules.dronesHitEntities(level.getServer()),
                target.getUUID().equals(player.getUUID()), target instanceof Player, level.isPvpAllowed(),
                tracker.cooledDown(target.getId(), now), tracker.withinBudget(now));
        if (decision != StrikeRules.Decision.HIT) {
            return;
        }
        Vec3 claimed = new Vec3(payload.vx(), payload.vy(), payload.vz());
        double claimedSpeed = claimed.length();
        if (!Double.isFinite(claimedSpeed)) {
            return;
        }
        double maxSpeed = StateValidation.effectiveMaxSpeed(ServerRules.maxDroneSpeed(player));
        double speed = StrikeRules.acceptedSpeed(claimedSpeed, tracker.peak(now), maxSpeed);
        Vec3 velocity = claimedSpeed > 1.0E-6 ? claimed.scale(speed / claimedSpeed) : Vec3.ZERO;
        AABB a = drone.getBoundingBox();
        AABB b = target.getBoundingBox();
        double gap = StrikeRules.gap(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
        if (!StrikeRules.withinReach(gap, speed)) {
            return;
        }
        Vec3 relative = velocity.subtract(target.getKnownMovement().scale(20.0));
        double sign = payload.positive() ? 1.0 : -1.0;
        double nx = payload.axis() == 0 ? sign : 0.0;
        double ny = payload.axis() == 1 ? sign : 0.0;
        double nz = payload.axis() == 2 ? sign : 0.0;
        double closing = StrikeMath.closingSpeed(relative.x, relative.y, relative.z, nx, ny, nz);
        double massKg = AirframeSpec.of(drone.build()).massKg + drone.cargoGrams() / 1000.0 + AccessoryPayload.payloadGrams(drone) / 1000.0;
        StrikeMath.Strike strike = StrikeMath.of(massKg, closing);
        if (AddonEvents.wantsHits()) {
            EntityHit hit = AddonEvents.entityHit(drone, player, target, relative, closing, new Vec3(nx, ny, nz), strike.damage(), strike.knockback());
            strike = new StrikeMath.Strike(strike.momentum(), hit.damage(), hit.knockback());
        }
        if (!strike.hits()) {
            return;
        }
        Optional<DamageSource> source = damageSource(level, drone, player);
        if (source.isEmpty()) {
            return;
        }
        tracker.mark(target.getId(), now);
        boolean hurt = target.hurtServer(level, source.get(), strike.damage());
        if (hurt) {
            double hx = relative.x;
            double hz = relative.z;
            if (hx * hx + hz * hz < 1.0E-4) {
                hx = nx;
                hz = nz;
            }
            target.knockback(strike.knockback(), -hx, -hz, source.get(), strike.damage());
        }
        StrikeMath.Strike landed = strike;
        STRUCK.fire(listener -> listener.onStruck(drone, target, landed, hurt));
    }

    public static Optional<DamageSource> damageSource(ServerLevel level, QuadcopterEntity drone, Entity pilot) {
        Optional<Holder.Reference<DamageType>> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(DRONE_STRIKE);
        if (type.isEmpty()) {
            if (!missingTypeLogged) {
                missingTypeLogged = true;
                Constants.LOG.warn("Schadensart {} fehlt (Datenpaket deaktiviert?) – Drohnen-Treffer ohne Schaden", DRONE_STRIKE.identifier());
            }
            return Optional.empty();
        }
        return Optional.of(new DamageSource(type.get(), drone, pilot));
    }

    @FunctionalInterface
    public interface StrikeListener {
        void onStruck(QuadcopterEntity drone, LivingEntity target, StrikeMath.Strike strike, boolean hurt);
    }

    private static final class Tracker {
        private final Map<Integer, Long> lastHit = new HashMap<>();
        private final double[] peaks = new double[PEAK_WINDOW_TICKS];
        private final long[] ticks = new long[PEAK_WINDOW_TICKS];
        private long windowStart = Long.MIN_VALUE;
        private int used;

        void sample(long now, double speed) {
            if (!Double.isFinite(speed)) {
                return;
            }
            int slot = (int) Math.floorMod(now, PEAK_WINDOW_TICKS);
            if (this.ticks[slot] != now) {
                this.ticks[slot] = now;
                this.peaks[slot] = speed;
            } else {
                this.peaks[slot] = Math.max(this.peaks[slot], speed);
            }
        }

        double peak(long now) {
            double peak = 0.0;
            for (int i = 0; i < PEAK_WINDOW_TICKS; i++) {
                if (now - this.ticks[i] < PEAK_WINDOW_TICKS && now >= this.ticks[i]) {
                    peak = Math.max(peak, this.peaks[i]);
                }
            }
            return peak;
        }

        boolean cooledDown(int target, long now) {
            Long last = this.lastHit.get(target);
            return last == null || now - last >= StrikeRules.TARGET_COOLDOWN_TICKS || now < last;
        }

        boolean withinBudget(long now) {
            if (this.windowStart == Long.MIN_VALUE || now - this.windowStart >= 20 || now < this.windowStart) {
                this.windowStart = now;
                this.used = 0;
            }
            return this.used < StrikeRules.STRIKES_PER_SECOND;
        }

        void mark(int target, long now) {
            this.used++;
            this.lastHit.put(target, now);
            if (this.lastHit.size() > 64) {
                this.lastHit.values().removeIf(tick -> now - tick > StrikeRules.TARGET_COOLDOWN_TICKS);
            }
        }
    }
}
