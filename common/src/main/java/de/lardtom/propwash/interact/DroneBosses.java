package de.lardtom.propwash.interact;

import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.accessory.DronePushes;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.DroneWear;
import de.lardtom.propwash.drone.Knockback;
import de.lardtom.propwash.drone.LoadedDrones;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.mixin.drone.WitherBossAccessor;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonSittingPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class DroneBosses {
    public static final double WITHER_RANGE_XZ = 20.0;
    public static final double WITHER_RANGE_Y = 8.0;
    public static final double WITHER_MAX_DISTANCE = 30.0;
    public static final int WITHER_SCAN_TICKS = 10;
    public static final int WITHER_FIRST_SHOT = 10;
    public static final int WITHER_SHOT_TICKS = 40;
    public static final int WITHER_SHOT_SPREAD = 20;
    public static final double DRAGON_NOTICE = 48.0;
    public static final int DRAGON_CHECK_TICKS = 20;
    public static final int DRAGON_CHARGE_ODDS = 3;
    public static final int DRAGON_CHARGE_TICKS = 200;
    public static final int DRAGON_CHARGE_COOLDOWN = 200;
    public static final double DRAGON_HOMING_MIN = 10.0;
    public static final int WING_PUSH_TICKS = 5;
    public static final int WING_WEAR_TICKS = 20;
    public static final int RAM_TICKS = 10;
    public static final int BREATH_TICKS = 20;
    public static final double BREATH_HEIGHT = 2.0;
    public static final int TURBULENCE_TICKS = 5;
    private static final int SLOT_WING_PUSH = 0;
    private static final int SLOT_WING_WEAR = 1;
    private static final int SLOT_RAM = 2;
    private static final int SLOT_TURBULENCE = 3;
    private static final int SLOTS = 4;

    private static final Map<Warden, WardenGrudge> GRUDGES = new WeakHashMap<>();
    private static final Map<WitherBoss, long[]> WITHER_HEADS = new WeakHashMap<>();
    private static final Map<EnderDragon, Charge> CHARGES = new WeakHashMap<>();
    private static final Map<QuadcopterEntity, long[]> COOLDOWNS = new WeakHashMap<>();
    private static final int[] HITS = new int[BossHit.values().length];
    private static int skulls;
    private static int dragonCharges;
    private static int sonicCharges;
    private static int turbulencePushes;
    private static int wingPushes;

    private DroneBosses() {
    }

    private static final class Charge {
        @Nullable UUID target;
        long until = Long.MIN_VALUE;
        long readyAt = Long.MIN_VALUE;
    }

    public static boolean enabled(ServerLevel level) {
        return WorldRules.on(level.getServer(), WorldRuleSpecs.DRONES_BOSS_INTERACTIONS);
    }

    public static boolean flown(QuadcopterEntity drone) {
        return !drone.isRemoved() && (drone.pilot().isPresent() || AutonomousReturns.flying(drone));
    }

    public static boolean pushingSource(@Nullable Entity source) {
        return source instanceof BreezeWindCharge || source instanceof WitherSkull || source instanceof WitherBoss;
    }

    public static synchronized void reset() {
        GRUDGES.clear();
        WITHER_HEADS.clear();
        CHARGES.clear();
        COOLDOWNS.clear();
    }

    public static synchronized int hits(BossHit hit) {
        return HITS[hit.ordinal()];
    }

    public static synchronized int skulls() {
        return skulls;
    }

    public static synchronized int dragonCharges() {
        return dragonCharges;
    }

    public static synchronized int sonicCharges() {
        return sonicCharges;
    }

    public static synchronized int turbulencePushes() {
        return turbulencePushes;
    }

    public static synchronized int wingPushes() {
        return wingPushes;
    }

    public static boolean perched(EnderDragon dragon) {
        return dragon.getPhaseManager().getCurrentPhase() instanceof AbstractDragonSittingPhase;
    }

    public static synchronized int anger(Warden warden, Entity drone) {
        WardenGrudge grudge = GRUDGES.get(warden);
        return grudge == null ? 0 : grudge.anger(drone.getUUID(), warden.level().getGameTime());
    }

    public static synchronized @Nullable UUID chargeTarget(EnderDragon dragon) {
        Charge charge = CHARGES.get(dragon);
        return charge == null ? null : charge.target;
    }

    public static synchronized void wardenAngered(Warden warden, @Nullable Entity entity, int amount) {
        if (!(entity instanceof QuadcopterEntity drone) || !(warden.level() instanceof ServerLevel level) || warden.isNoAi()
                || drone.isRemoved() || drone.level() != warden.level() || !enabled(level)) {
            return;
        }
        long now = level.getGameTime();
        WardenAi.setDigCooldown(warden);
        WardenGrudge grudge = GRUDGES.computeIfAbsent(warden, w -> new WardenGrudge());
        boolean was = WardenGrudge.angry(grudge.anger(drone.getUUID(), now));
        boolean is = WardenGrudge.angry(grudge.add(drone.getUUID(), amount, now));
        warden.playSound(is ? SoundEvents.WARDEN_LISTENING_ANGRY : SoundEvents.WARDEN_LISTENING, 10.0F, warden.getVoicePitch());
        if (is && !was) {
            warden.playSound(SoundEvents.WARDEN_ANGRY, 10.0F, warden.getVoicePitch());
        }
    }

    public static void dragonWing(EnderDragon dragon, ServerLevel level, List<Entity> entities) {
        if (!enabled(level)) {
            return;
        }
        long now = level.getGameTime();
        boolean sitting = dragon.getPhaseManager().getCurrentPhase().isSitting();
        for (Entity entity : entities) {
            if (!(entity instanceof QuadcopterEntity drone) || !flown(drone) || !ready(drone, SLOT_WING_PUSH, now, WING_PUSH_TICKS)) {
                continue;
            }
            Vec3 from = drone.position().subtract(dragon.position());
            Vec3 change = BossHits.wingVelocity(from);
            if (!sitting && ready(drone, SLOT_WING_WEAR, now, WING_WEAR_TICKS)) {
                hit(level, drone, BossHit.DRAGON_WING, from, change);
            } else {
                push(drone, change);
            }
            synchronized (DroneBosses.class) {
                wingPushes++;
            }
        }
    }

    public static void dragonHead(EnderDragon dragon, ServerLevel level, List<Entity> entities) {
        if (!enabled(level)) {
            return;
        }
        long now = level.getGameTime();
        for (Entity entity : entities) {
            if (!(entity instanceof QuadcopterEntity drone) || drone.isRemoved() || !ready(drone, SLOT_RAM, now, RAM_TICKS)) {
                continue;
            }
            Vec3 from = drone.getBoundingBox().getCenter().subtract(dragon.head.getBoundingBox().getCenter());
            Vec3 change = BossHits.ramVelocity(from, Knockback.metresPerSecond(dragon.getDeltaMovement()));
            hit(level, drone, BossHit.DRAGON_RAM, from, change);
        }
    }

    public static void serverTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!enabled(level)) {
                forget(level);
                continue;
            }
            long now = level.getGameTime();
            tickWardens(level, now);
            List<Entity> drones = LoadedDrones.of(level);
            if (drones.isEmpty()) {
                continue;
            }
            List<QuadcopterEntity> flown = new ArrayList<>();
            for (Entity entity : drones) {
                if (entity instanceof QuadcopterEntity drone && !drone.isRemoved()) {
                    if ((now + drone.getId()) % BREATH_TICKS == 0) {
                        breath(level, drone);
                    }
                    if (flown(drone)) {
                        flown.add(drone);
                    }
                }
            }
            if (flown.isEmpty()) {
                continue;
            }
            tickDragons(level, flown, now);
            for (QuadcopterEntity drone : flown) {
                if ((now + drone.getId()) % WITHER_SCAN_TICKS == 0) {
                    withers(level, drone, now);
                }
            }
        }
    }

    private static synchronized void forget(ServerLevel level) {
        GRUDGES.keySet().removeIf(warden -> warden.level() == level);
        CHARGES.keySet().removeIf(dragon -> dragon.level() == level);
    }

    private static void tickWardens(ServerLevel level, long now) {
        List<Map.Entry<Warden, WardenGrudge>> entries;
        synchronized (DroneBosses.class) {
            if (GRUDGES.isEmpty()) {
                return;
            }
            entries = new ArrayList<>(GRUDGES.entrySet());
        }
        for (Map.Entry<Warden, WardenGrudge> entry : entries) {
            Warden warden = entry.getKey();
            if (warden.level() != level) {
                continue;
            }
            WardenGrudge grudge = entry.getValue();
            if (warden.isRemoved() || warden.isDeadOrDying() || warden.isNoAi()) {
                synchronized (DroneBosses.class) {
                    GRUDGES.remove(warden);
                }
                continue;
            }
            synchronized (DroneBosses.class) {
                tickWarden(level, warden, grudge, now);
                if (grudge.empty()) {
                    GRUDGES.remove(warden);
                }
            }
        }
    }

    private static void tickWarden(ServerLevel level, Warden warden, WardenGrudge grudge, long now) {
        if (grudge.charging()) {
            UUID id = grudge.chargeTarget();
            QuadcopterEntity target = id != null && level.getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
            if (target != null) {
                warden.getLookControl().setLookAt(target.position());
            }
            if (grudge.boomDue(now)) {
                grudge.boomed(now);
                if (target != null && inRange(warden, target)) {
                    sonicBoom(level, warden, target);
                }
            }
            return;
        }
        UUID id = grudge.target(now).orElse(null);
        if (id == null) {
            return;
        }
        if (!(level.getEntity(id) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            grudge.forget(id);
            return;
        }
        if (warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            return;
        }
        if ((now + warden.getId()) % 20 == 0) {
            WardenAi.setDisturbanceLocation(warden, drone.blockPosition());
        }
        if (grudge.mayCharge(now) && inRange(warden, drone) && warden.hasLineOfSight(drone)) {
            grudge.startCharge(drone.getUUID(), now);
            warden.getLookControl().setLookAt(drone.position());
            level.broadcastEntityEvent(warden, (byte) 62);
            warden.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
            sonicCharges++;
        }
    }

    private static boolean inRange(Warden warden, QuadcopterEntity drone) {
        return WardenGrudge.inRange(drone.getX() - warden.getX(), drone.getY() - warden.getY(), drone.getZ() - warden.getZ());
    }

    private static void sonicBoom(ServerLevel level, Warden warden, QuadcopterEntity drone) {
        Vec3 source = warden.position().add(warden.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, warden.getYRot()));
        Vec3 delta = drone.getBoundingBox().getCenter().subtract(source);
        Vec3 direction = delta.normalize();
        int steps = Mth.floor(delta.length()) + 7;
        for (int i = 1; i < steps; i++) {
            Vec3 at = source.add(direction.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        warden.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.0F);
        hit(level, drone, BossHit.SONIC_BOOM, direction, BossHits.velocityChange(BossHit.SONIC_BOOM, direction));
    }

    private static void tickDragons(ServerLevel level, List<QuadcopterEntity> flown, long now) {
        List<? extends EnderDragon> dragons = level.getDragons();
        if (dragons.isEmpty()) {
            return;
        }
        for (EnderDragon dragon : dragons) {
            if (dragon.isRemoved() || dragon.isDeadOrDying() || dragon.isNoAi()) {
                continue;
            }
            turbulence(level, dragon, flown, now);
            charge(level, dragon, flown, now);
        }
    }

    private static void turbulence(ServerLevel level, EnderDragon dragon, List<QuadcopterEntity> flown, long now) {
        if (perched(dragon)) {
            return;
        }
        double speed = Knockback.metresPerSecond(dragon.getDeltaMovement()).length();
        Vec3 body = dragon.position();
        for (QuadcopterEntity drone : flown) {
            Vec3 from = drone.getBoundingBox().getCenter().subtract(body);
            if (from.lengthSqr() >= BossHits.TURBULENCE_RADIUS * BossHits.TURBULENCE_RADIUS || !ready(drone, SLOT_TURBULENCE, now, TURBULENCE_TICKS)) {
                continue;
            }
            Vec3 force = BossHits.turbulence(from, speed, mass(drone), level.getRandom().nextDouble(), level.getRandom().nextDouble(),
                    level.getRandom().nextDouble());
            if (force.lengthSqr() > 0.0 && DronePushes.force(force, false, Vec3.ZERO, BossHits.TURBULENCE_SECONDS)
                    .map(push -> DronePushes.apply(drone, push)).orElse(false)) {
                synchronized (DroneBosses.class) {
                    turbulencePushes++;
                }
            }
        }
    }

    private static void charge(ServerLevel level, EnderDragon dragon, List<QuadcopterEntity> flown, long now) {
        Charge charge;
        synchronized (DroneBosses.class) {
            charge = CHARGES.computeIfAbsent(dragon, d -> new Charge());
        }
        EnderDragonPhase<?> phase = dragon.getPhaseManager().getCurrentPhase().getPhase();
        if (phase == EnderDragonPhase.CHARGING_PLAYER && charge.target != null && now <= charge.until) {
            if (level.getEntity(charge.target) instanceof QuadcopterEntity drone && flown(drone)
                    && drone.distanceToSqr(dragon) > DRAGON_HOMING_MIN * DRAGON_HOMING_MIN) {
                dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(drone.getBoundingBox().getCenter());
            }
            return;
        }
        if (phase != EnderDragonPhase.CHARGING_PLAYER) {
            charge.target = null;
        }
        if (phase != EnderDragonPhase.HOLDING_PATTERN || now < charge.readyAt || (now + dragon.getId()) % DRAGON_CHECK_TICKS != 0) {
            return;
        }
        QuadcopterEntity nearest = null;
        double best = DRAGON_NOTICE * DRAGON_NOTICE;
        for (QuadcopterEntity drone : flown) {
            double d = drone.distanceToSqr(dragon);
            if (drone.isArmed() && d < best) {
                best = d;
                nearest = drone;
            }
        }
        if (nearest == null || dragon.getRandom().nextInt(DRAGON_CHARGE_ODDS) != 0) {
            return;
        }
        dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
        dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(nearest.getBoundingBox().getCenter());
        charge.target = nearest.getUUID();
        charge.until = now + DRAGON_CHARGE_TICKS;
        charge.readyAt = now + DRAGON_CHARGE_TICKS + DRAGON_CHARGE_COOLDOWN;
        synchronized (DroneBosses.class) {
            dragonCharges++;
        }
    }

    private static void breath(ServerLevel level, QuadcopterEntity drone) {
        AABB box = drone.getBoundingBox();
        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, box.inflate(8.0, BREATH_HEIGHT + 1.0, 8.0), DroneBosses::dragonBreath)) {
            double dx = drone.getX() - cloud.getX();
            double dz = drone.getZ() - cloud.getZ();
            float radius = cloud.getRadius();
            double above = drone.getY() - cloud.getY();
            if (dx * dx + dz * dz <= radius * radius && above >= -0.5 && above <= BREATH_HEIGHT) {
                hit(level, drone, BossHit.DRAGON_BREATH, new Vec3(0.0, 1.0, 0.0), null);
                return;
            }
        }
    }

    public static boolean dragonBreath(AreaEffectCloud cloud) {
        return !cloud.isRemoved() && (cloud.getParticle().getType() == ParticleTypes.DRAGON_BREATH || cloud.getOwner() instanceof EnderDragon);
    }

    private static void withers(ServerLevel level, QuadcopterEntity drone, long now) {
        AABB box = drone.getBoundingBox().inflate(WITHER_RANGE_XZ, WITHER_RANGE_Y, WITHER_RANGE_XZ);
        for (WitherBoss wither : level.getEntitiesOfClass(WitherBoss.class, box)) {
            if (wither.isRemoved() || wither.isDeadOrDying() || wither.isNoAi() || wither.getInvulnerableTicks() > 0
                    || wither.distanceToSqr(drone) > WITHER_MAX_DISTANCE * WITHER_MAX_DISTANCE) {
                continue;
            }
            long[] next;
            synchronized (DroneBosses.class) {
                next = WITHER_HEADS.computeIfAbsent(wither, w -> new long[]{now + WITHER_FIRST_SHOT, now + WITHER_FIRST_SHOT + WITHER_SHOT_SPREAD / 2});
            }
            for (int head = 1; head <= 2; head++) {
                if (now < next[head - 1] || wither.getAlternativeTarget(head) != 0 || !wither.hasLineOfSight(drone)) {
                    continue;
                }
                Vec3 at = drone.getBoundingBox().getCenter();
                ((WitherBossAccessor) wither).propwash$performRangedAttack(head + 1, at.x, at.y, at.z, false);
                next[head - 1] = now + WITHER_SHOT_TICKS + wither.getRandom().nextInt(WITHER_SHOT_SPREAD);
                synchronized (DroneBosses.class) {
                    skulls++;
                }
            }
        }
    }

    private static synchronized boolean ready(QuadcopterEntity drone, int slot, long now, int ticks) {
        long[] times = COOLDOWNS.computeIfAbsent(drone, d -> {
            long[] fresh = new long[SLOTS];
            Arrays.fill(fresh, Long.MIN_VALUE);
            return fresh;
        });
        if (times[slot] != Long.MIN_VALUE && now - times[slot] < ticks && now >= times[slot]) {
            return false;
        }
        times[slot] = now;
        return true;
    }

    private static double mass(QuadcopterEntity drone) {
        return Knockback.massKg(AirframeSpec.of(drone.build()).massKg, AccessoryPayload.payloadGrams(drone), drone.cargoGrams());
    }

    private static boolean push(QuadcopterEntity drone, @Nullable Vec3 change) {
        if (change == null || !change.isFinite() || change.lengthSqr() <= 0.0) {
            return false;
        }
        Vec3 impulse = BossHits.impulse(change, mass(drone));
        return impulse.lengthSqr() > 0.0 && DronePushes.impulse(impulse, Vec3.ZERO).map(push -> DronePushes.apply(drone, push)).orElse(false);
    }

    static void hit(ServerLevel level, QuadcopterEntity drone, BossHit hit, Vec3 direction, @Nullable Vec3 change) {
        if (drone.isRemoved()) {
            return;
        }
        float strength = WorldRules.crashDamageStrength(level.getServer());
        PartWear before = drone.wear();
        PartWear after = BossHits.wear(before, drone.build(), hit, direction, strength, level.getRandom().nextLong());
        if (after != null && !after.equals(before)) {
            drone.setWear(after);
            DroneSim sim = AutonomousReturns.sim(drone);
            if (sim != null) {
                sim.setWear(after);
            }
            if (DroneWear.newlyBroken(before, after)) {
                level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.7F, 1.3F);
            }
        }
        push(drone, change);
        if (BossHits.crash(hit, drone.build())) {
            DroneVibrations.crashed(drone);
        }
        synchronized (DroneBosses.class) {
            HITS[hit.ordinal()]++;
        }
    }
}
