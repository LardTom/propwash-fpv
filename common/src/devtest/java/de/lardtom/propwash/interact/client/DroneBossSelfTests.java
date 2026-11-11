package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.accessory.client.AccessoryClient;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.Knockback;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.TrainingSelfTests;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.interact.BossHit;
import de.lardtom.propwash.interact.DroneBosses;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.client.BossDarkness;
import de.lardtom.propwash.video.client.BossFeedProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class DroneBossSelfTests {
    public static final String GROUP = "bosses: ";
    private static final int SITE_DX = 40;
    private static final int SITE_DZ = -40;
    private static final int HALF = 10;
    private static final int LIFT = 25;
    private static final int SYNC_TICKS = 120;
    private static final int ARM_TICKS = 120;
    private static final String STAGE = "boss_stage";
    private static final String MARK = "boss_mark";
    private static final String FUTURE = "boss_future";
    private static final String BASE = "boss_base";
    private static final String PUSHES = "boss_pushes";
    private static final String SPEED = "boss_speed";
    private static final String SHOT = "boss_shot";
    private static final String TARGET = "boss_target";
    private static final Quaternionf LEVEL = new Quaternionf();
    private static final AtomicReference<@Nullable Arena> ARENA = new AtomicReference<>();
    private static final Watch WATCH = new Watch();

    private DroneBossSelfTests() {
    }

    public static void register() {
        ServerBus.TICK_END.register(DroneBossSelfTests::sample);
        SelfTest.clientStep(GROUP + "Plattform über dem Boden aufbauen, Regeln und Kreativmodus setzen", 400, DroneBossSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Wärter hört die schwebende Drohne, wird wütend und trifft sie mit dem Schallschlag", 1400,
                DroneBossSelfTests::wardenStep);
        SelfTest.clientStep(GROUP + "Dunkelheit des Wärters stört das FPV-Bild, Welt-Regel aus schaltet sie ab", 900, DroneBossSelfTests::darknessStep);
        SelfTest.clientStep(GROUP + "Enderdrache – Flügelschlag und Turbulenz schleudern die Drohne weg", 600, DroneBossSelfTests::wingStep);
        SelfTest.clientStep(GROUP + "Enderdrache – der Kopf rammt die Drohne", 600, DroneBossSelfTests::ramStep);
        SelfTest.clientStep(GROUP + "Enderdrache – greift eine Drohne in seiner Nähe an", 900, DroneBossSelfTests::chargeStep);
        SelfTest.clientStep(GROUP + "Enderdrache – Drachenatem beschädigt die Drohne", 600, DroneBossSelfTests::breathStep);
        SelfTest.clientStep(GROUP + "Wither – Seitenköpfe schießen Schädel auf die geflogene Drohne", 700, DroneBossSelfTests::witherStep);
        SelfTest.clientStep(GROUP + "Breeze-Windkugel stößt die geflogene Drohne weg", 600, DroneBossSelfTests::windStep);
        SelfTest.clientStep(GROUP + "Welt-Regel „Bosse kämpfen gegen Drohnen“ aus: Wärter bleibt ruhig, Windkugel stößt nicht", 900,
                DroneBossSelfTests::ruleOffStep);
        SelfTest.clientStep(GROUP + "Plattform abbauen, Regeln zurücksetzen", 300, DroneBossSelfTests::cleanupStep);
    }

    private static final class Arena {
        final BlockPos center;
        final LinkedHashMap<BlockPos, BlockState> original = new LinkedHashMap<>();
        final Map<RuleSpec, Integer> rules = new LinkedHashMap<>();
        @Nullable ItemStack head;
        @Nullable ItemStack hand;
        @Nullable GameType mode;
        @Nullable Difficulty difficulty;
        boolean griefing;
        volatile int droneId = -1;
        volatile @Nullable UUID drone;
        volatile @Nullable UUID boss;

        Arena(BlockPos center) {
            this.center = center;
        }

        Vec3 at(double dx, double dy, double dz) {
            return new Vec3(this.center.getX() + 0.5 + dx, this.center.getY() + 1.0 + dy, this.center.getZ() + 0.5 + dz);
        }

        AABB box() {
            return new AABB(this.center).inflate(64.0);
        }
    }

    private static final class Watch {
        volatile int anger;
        volatile float worstProp = 1.0F;
        volatile float video = 1.0F;
        volatile boolean droneGone;
        volatile @Nullable String phase;
        volatile boolean chargeAtDrone;
        volatile double chargeMiss = Double.NaN;
        volatile @Nullable Vec3 head;
        volatile @Nullable Vec3 dragon;
        volatile double skullAim = -2.0;
        volatile String state = "";

        void reset() {
            this.anger = 0;
            this.worstProp = 1.0F;
            this.video = 1.0F;
            this.droneGone = false;
            this.phase = null;
            this.chargeAtDrone = false;
            this.chargeMiss = Double.NaN;
            this.head = null;
            this.dragon = null;
            this.skullAim = -2.0;
        }
    }

    private static void sample(MinecraftServer server) {
        Arena arena = ARENA.get();
        if (arena == null || arena.drone == null) {
            return;
        }
        ServerLevel level = server.overworld();
        Entity found = level.getEntity(arena.drone);
        if (!(found instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            WATCH.droneGone = true;
            return;
        }
        PartWear wear = drone.wear();
        WATCH.worstProp = wear.worstProp();
        WATCH.video = wear.video();
        Entity boss = arena.boss == null ? null : level.getEntity(arena.boss);
        if (boss instanceof Warden warden) {
            WATCH.anger = Math.max(WATCH.anger, DroneBosses.anger(warden, drone));
            WATCH.state = String.format(Locale.ROOT, "Drohne scharf %s, Motoren %.2f, Abstand %.1f, Wärter %s, Zorn jetzt %d, Hörpause %s",
                    drone.isArmed(), drone.motorOutput(), warden.distanceTo(drone), warden.getPose(), DroneBosses.anger(warden, drone),
                    warden.getBrain().hasMemoryValue(MemoryModuleType.VIBRATION_COOLDOWN));
        }
        if (boss instanceof EnderDragon dragon) {
            boolean inWing = false;
            for (EnderDragonPart part : dragon.getSubEntities()) {
                if (part.name.equals("wing") && part.getBoundingBox().inflate(4.0, 2.0, 4.0).move(0.0, -2.0, 0.0).intersects(drone.getBoundingBox())) {
                    inWing = true;
                }
            }
            Vec3 d = drone.position().subtract(dragon.position());
            WATCH.state = String.format(Locale.ROOT, "Drache yRot %.1f, Drohne relativ %.1f/%.1f/%.1f, im Flügelbereich %s, kürzlich verletzt %s",
                    dragon.getYRot(), d.x, d.y, d.z, inWing, dragon.hurtTime);
            WATCH.phase = String.valueOf(dragon.getPhaseManager().getCurrentPhase().getPhase());
            WATCH.head = dragon.head.getBoundingBox().getCenter();
            WATCH.dragon = dragon.position();
            if (dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.CHARGING_PLAYER
                    && drone.getUUID().equals(DroneBosses.chargeTarget(dragon))) {
                Vec3 target = dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).getFlyTargetLocation();
                WATCH.chargeAtDrone = true;
                if (target != null) {
                    WATCH.chargeMiss = target.distanceTo(drone.getBoundingBox().getCenter());
                }
            }
        }
        for (WitherSkull skull : level.getEntitiesOfClass(WitherSkull.class, arena.box())) {
            Vec3 motion = skull.getDeltaMovement();
            Vec3 toDrone = drone.getBoundingBox().getCenter().subtract(skull.position());
            if (motion.lengthSqr() > 1.0E-6 && toDrone.lengthSqr() > 1.0E-6) {
                WATCH.skullAim = Math.max(WATCH.skullAim, motion.normalize().dot(toDrone.normalize()));
            }
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static Arena arena() {
        Arena arena = ARENA.get();
        if (arena == null) {
            throw new IllegalStateException("Plattform fehlt");
        }
        return arena;
    }

    private static void set(ServerLevel level, Arena arena, BlockPos pos, BlockState state) {
        arena.original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void rule(MinecraftServer server, Arena arena, RuleSpec spec, int value) {
        arena.rules.putIfAbsent(spec, WorldRules.get(server, spec));
        WorldRules.set(server, spec, value);
    }

    private static void prepare(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int x0 = player.getBlockX() + SITE_DX;
        int z0 = player.getBlockZ() + SITE_DZ;
        int top = level.getMinY();
        for (int dx = -HALF; dx <= HALF; dx += 5) {
            for (int dz = -HALF; dz <= HALF; dz += 5) {
                top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x0 + dx, z0 + dz));
            }
        }
        Arena arena = new Arena(new BlockPos(x0, top + LIFT, z0));
        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                set(level, arena, arena.center.offset(dx, 0, dz), stone);
            }
        }
        arena.mode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(GameType.CREATIVE);
        arena.difficulty = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.NORMAL, true);
        arena.griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        level.getGameRules().set(GameRules.MOB_GRIEFING, false, server);
        rule(server, arena, WorldRuleSpecs.DRONES_BOSS_INTERACTIONS, 1);
        rule(server, arena, WorldRuleSpecs.DRONES_TRIGGER_SCULK, 1);
        rule(server, arena, WorldRuleSpecs.WIND, 0);
        rule(server, arena, WorldRuleSpecs.TURBULENCE, 0);
        rule(server, arena, WorldRuleSpecs.CRASH_DAMAGE, 1);
        arena.head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        arena.hand = player.getMainHandItem().copy();
        clearBosses(level, arena);
        ARENA.set(arena);
        freshDrone(server);
    }

    private static void clearBosses(ServerLevel level, Arena arena) {
        for (Entity entity : level.getEntities((Entity) null, arena.box(), e -> e instanceof Warden || e instanceof AreaEffectCloud
                || e instanceof WitherSkull || e instanceof BreezeWindCharge)) {
            entity.discard();
        }
        for (EnderDragon dragon : List.copyOf(level.getDragons())) {
            dragon.discard();
        }
        for (WitherBoss wither : level.getEntities(EntityTypes.WITHER, wither -> true)) {
            wither.discard();
        }
        arena.boss = null;
    }

    private static void freshDrone(MinecraftServer server) {
        Arena arena = arena();
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        clearBosses(level, arena);
        if (arena.drone != null && level.getEntity(arena.drone) instanceof QuadcopterEntity old) {
            old.discard();
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 pad = arena.at(0.0, 0.0, 0.0);
        drone.snapTo(pad.x, pad.y, pad.z, 0.0F, 0.0F);
        drone.setAttitude(LEVEL);
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
        arena.droneId = drone.getId();
        arena.drone = drone.getUUID();
        WATCH.reset();
    }

    private static <T extends Entity> T spawn(ServerLevel level, @Nullable T entity, Vec3 at, float yaw) {
        if (entity == null) {
            throw new IllegalStateException("Boss nicht erzeugt");
        }
        entity.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        if (!level.addFreshEntity(entity)) {
            throw new IllegalStateException(entity.getType() + " nicht in die Welt gesetzt");
        }
        arena().boss = entity.getUUID();
        return entity;
    }

    private static void warden(MinecraftServer server, Vec3 at, boolean ai) {
        ServerLevel level = server.overworld();
        Warden warden = spawn(level, EntityTypes.WARDEN.create(level, EntitySpawnReason.COMMAND), at, 180.0F);
        warden.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 12000L);
        warden.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12000, 10, false, false));
        warden.setNoAi(!ai);
        warden.setPersistenceRequired();
    }

    private static EnderDragon dragon(MinecraftServer server, Vec3 at, EnderDragonPhase<?> phase) {
        ServerLevel level = server.overworld();
        EnderDragon dragon = spawn(level, EntityTypes.ENDER_DRAGON.create(level, EntitySpawnReason.COMMAND), at, 0.0F);
        dragon.setFightOrigin(arena().center);
        dragon.getPhaseManager().setPhase(phase);
        return dragon;
    }

    private static int stage(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(STAGE, 0);
    }

    private static void stage(SelfTest.StepContext ctx, int next) {
        ctx.state().put(STAGE, next);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static @Nullable StepResult pending(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            input(0.0F, false);
            return StepResult.fail("Serverauftrag: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        return null;
    }

    private static void server(SelfTest.StepContext ctx, Consumer<MinecraftServer> action) {
        ctx.state().put(FUTURE, ctx.onServer(action));
    }

    private static void input(float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private record Flight(Arena arena, QuadcopterEntity drone, DroneSim sim, Telemetry t) {
    }

    private static @Nullable Flight flight() {
        Arena arena = ARENA.get();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (arena == null || drone == null || sim == null || drone.getId() != arena.droneId || !session.isFpv()) {
            return null;
        }
        return new Flight(arena, drone, sim, sim.telemetry());
    }

    private static void hold(QuadcopterEntity drone, Telemetry t, double targetY) {
        double hover = Math.clamp(t.hoverThrottleStick, 0.15, 0.7);
        double wanted = Math.clamp(1.2 * (targetY - drone.getY()), -2.0, 2.0);
        input((float) Math.clamp(hover + 0.06 * (wanted - t.verticalSpeed), 0.05, 0.9), true);
    }

    private static double speed(DroneSim sim) {
        Vector3d v = new Vector3d();
        sim.getVelocity(v);
        return v.length();
    }

    private static void shot(SelfTest.StepContext ctx, String file) {
        if (Screenshots.enabled() && !ctx.state().containsKey(SHOT + file)) {
            ctx.state().put(SHOT + file, Screenshots.capture(file));
            ctx.log("Bildschirmfoto " + file);
        }
    }

    private static boolean pictured(SelfTest.StepContext ctx, String file) {
        return !Screenshots.enabled() || ctx.state().containsKey(SHOT + file);
    }

    private interface Check {
        @Nullable StepResult test(SelfTest.StepContext ctx, Flight f, int ticks);
    }

    private static StepResult scenario(SelfTest.StepContext ctx, Consumer<MinecraftServer> spawn, Vec3 offset, double yaw, int timeout, Check check) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (stage(ctx)) {
            case 0 -> {
                input(0.0F, false);
                server(ctx, DroneBossSelfTests::freshDrone);
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                Flight f = flight();
                if (f == null) {
                    return since(ctx) > SYNC_TICKS ? StepResult.fail("keine FPV-Sitzung mit der neuen Testdrohne") : StepResult.pending();
                }
                Vec3 start = f.arena().at(offset.x, offset.y, offset.z);
                ctx.state().put(TARGET, start);
                TrainingSelfTests.teleport(start.x, start.y, start.z, yaw, 0.0, 0.0, 0.0);
                input(0.0F, false);
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                Flight f = flight();
                if (f == null) {
                    return StepResult.fail("FPV-Sitzung beim Scharfschalten verloren");
                }
                Vec3 start = (Vec3) ctx.state().get(TARGET);
                TrainingSelfTests.teleport(start.x, start.y, start.z, yaw, 0.0, 0.0, 0.0);
                if (!f.t().armed) {
                    input(0.0F, since(ctx) >= 5 && ctx.tick() % 10 >= 3);
                    return since(ctx) > ARM_TICKS ? StepResult.fail("Drohne lässt sich nicht scharf schalten: "
                            + PilotSession.INSTANCE.armBlockers()) : StepResult.pending();
                }
                hold(f.drone(), f.t(), start.y);
                ctx.state().put(BASE, new int[]{DroneBosses.hits(BossHit.SONIC_BOOM), DroneBosses.hits(BossHit.DRAGON_WING),
                        DroneBosses.hits(BossHit.DRAGON_RAM), DroneBosses.hits(BossHit.DRAGON_BREATH), DroneBosses.skulls(),
                        DroneBosses.dragonCharges(), DroneBosses.sonicCharges(), DroneBosses.turbulencePushes(), Knockback.forwarded(),
                        DroneBosses.wingPushes()});
                ctx.state().put(PUSHES, AccessoryClient.pushesApplied());
                ctx.state().put(SPEED, 0.0);
                server(ctx, spawn);
                stage(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                Flight f = flight();
                int ticks = since(ctx);
                if (Screenshots.enabled()) {
                    Screenshots.clearOverlays(Minecraft.getInstance());
                }
                if (f != null) {
                    Vec3 start = (Vec3) ctx.state().get(TARGET);
                    if (f.t().armed) {
                        hold(f.drone(), f.t(), start.y);
                    }
                    ctx.state().put(SPEED, Math.max((double) ctx.state().get(SPEED), speed(f.sim())));
                }
                StepResult result = f == null ? null : check.test(ctx, f, ticks);
                if (result != null) {
                    return result;
                }
                if (f == null && !WATCH.droneGone) {
                    return StepResult.fail("FPV-Sitzung verloren");
                }
                return ticks > timeout ? StepResult.fail("Zeit abgelaufen nach " + ticks + " Ticks: " + describe(ctx)) : StepResult.pending();
            }
        }
    }

    private static int base(SelfTest.StepContext ctx, int index) {
        return ((int[]) ctx.state().get(BASE))[index];
    }

    private static int pushes(SelfTest.StepContext ctx) {
        return AccessoryClient.pushesApplied() - (int) ctx.state().get(PUSHES);
    }

    private static double maxSpeed(SelfTest.StepContext ctx) {
        return (double) ctx.state().getOrDefault(SPEED, 0.0);
    }

    private static String describe(SelfTest.StepContext ctx) {
        return String.format(Locale.ROOT, "Zorn %d, Schallschläge %d/%d, Flügel %d/%d, Rammen %d, Atem %d, Schädel %d, Angriffe %d, Turbulenz %d, "
                        + "Rückstoß weitergegeben %d, Stöße beim Client %d, Höchsttempo %.1f m/s, schlechtester Propeller %.2f, Video %.2f, Phase %s",
                WATCH.anger, DroneBosses.sonicCharges() - base(ctx, 6), DroneBosses.hits(BossHit.SONIC_BOOM) - base(ctx, 0),
                DroneBosses.wingPushes() - base(ctx, 9), DroneBosses.hits(BossHit.DRAGON_WING) - base(ctx, 1), DroneBosses.hits(BossHit.DRAGON_RAM) - base(ctx, 2),
                DroneBosses.hits(BossHit.DRAGON_BREATH) - base(ctx, 3), DroneBosses.skulls() - base(ctx, 4), DroneBosses.dragonCharges() - base(ctx, 5),
                DroneBosses.turbulencePushes() - base(ctx, 7), Knockback.forwarded() - base(ctx, 8), pushes(ctx), maxSpeed(ctx), WATCH.worstProp,
                WATCH.video, WATCH.phase);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (stage(ctx) == 0) {
            input(0.0F, false);
            server(ctx, DroneBossSelfTests::prepare);
            stage(ctx, 1);
            return StepResult.pending();
        }
        Flight f = flight();
        if (f == null) {
            return since(ctx) > SYNC_TICKS ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        input(0.0F, false);
        ctx.log("Plattform bei " + f.arena().center.toShortString() + ", Welt-Regel drones_boss_interactions an");
        return StepResult.pass();
    }

    private static StepResult wardenStep(SelfTest.StepContext ctx) {
        return scenario(ctx, server -> warden(server, arena().at(0.0, 0.0, 9.0), true), new Vec3(0.0, 1.5, 0.0), 0.0, 1300, (c, f, ticks) -> {
            int charges = DroneBosses.sonicCharges() - base(c, 6);
            if (ticks % 100 == 0) {
                c.log(ticks + " Ticks: " + WATCH.state);
            }
            if (charges > 0 && !c.state().containsKey("boss_charge")) {
                c.state().put("boss_charge", ticks);
                c.log("Wärter lädt den Schallschlag nach " + ticks + " Ticks, Zorn " + WATCH.anger);
            }
            if (c.state().containsKey("boss_charge") && ticks - (int) c.state().get("boss_charge") >= 24) {
                shot(c, "boss_warden_sonic_charge_fpv");
            }
            if (c.state().containsKey("boss_charge") && ticks - (int) c.state().get("boss_charge") >= 37) {
                shot(c, "boss_warden_sonic_boom_fpv");
            }
            int booms = DroneBosses.hits(BossHit.SONIC_BOOM) - base(c, 0);
            if (booms > 0 && WATCH.worstProp <= 0.5F && pushes(c) > 0 && maxSpeed(c) > 8.0 && pictured(c, "boss_warden_sonic_boom_fpv")) {
                c.log(describe(c));
                return StepResult.pass();
            }
            return null;
        });
    }

    private static StepResult darknessStep(SelfTest.StepContext ctx) {
        return scenario(ctx, server -> warden(server, arena().at(0.0, 0.0, 10.0), false), new Vec3(0.0, 3.0, 0.0), 0.0, 800, (c, f, ticks) -> {
            int phase = (int) c.state().getOrDefault("boss_dark", 0);
            float blend = BossDarkness.INSTANCE.blend();
            if (phase == 0) {
                if (blend >= 0.99F && BossDarkness.INSTANCE.near()) {
                    float brightness = BossFeedProbe.brightness();
                    float noise = BossFeedProbe.noise();
                    c.log(String.format(Locale.ROOT, "Dunkelheit voll nach %d Ticks: Bild-Helligkeit %.2f, Rauschen %.2f", ticks, brightness, noise));
                    if (!(brightness < 0.8F) || !(noise > 0.15F)) {
                        return StepResult.fail("FPV-Bild bleibt trotz Wärter klar: Helligkeit " + brightness + ", Rauschen " + noise);
                    }
                    c.state().put("boss_dark", 1);
                    c.state().put("boss_dark_at", ticks);
                }
                return ticks > 120 ? StepResult.fail("keine Dunkelheit neben dem Wärter, Blende " + blend) : null;
            }
            if (phase == 1) {
                int at = (int) c.state().get("boss_dark_at");
                if (ticks - at >= 20 && ticks % 80 == 0) {
                    shot(c, "boss_warden_darkness_fpv");
                }
                if (ticks - at >= 100) {
                    server(c, server -> WorldRules.set(server, WorldRuleSpecs.DRONES_BOSS_INTERACTIONS, 0));
                    c.state().put("boss_dark", 2);
                    c.state().put("boss_dark_at", ticks);
                }
                return null;
            }
            if (phase == 2) {
                int at = (int) c.state().get("boss_dark_at");
                if (blend <= 0.0F) {
                    c.log("Regel aus: Dunkelheit nach " + (ticks - at) + " Ticks verschwunden");
                    server(c, server -> WorldRules.set(server, WorldRuleSpecs.DRONES_BOSS_INTERACTIONS, 1));
                    c.state().put("boss_dark", 3);
                    return null;
                }
                return ticks - at > 300 ? StepResult.fail("Dunkelheit bleibt mit Regel aus, Blende " + blend) : null;
            }
            return StepResult.pass();
        });
    }

    private static StepResult wingStep(SelfTest.StepContext ctx) {
        return scenario(ctx, server -> dragon(server, arena().at(0.0, 4.0, 0.0), EnderDragonPhase.HOVERING), new Vec3(8.0, 4.0, 0.0), 90.0, 500,
                (c, f, ticks) -> {
                    if (ticks >= 4) {
                        shot(c, "boss_dragon_wing_fpv");
                    }
                    if (ticks % 20 == 0 && ticks <= 100) {
                        c.log(ticks + " Ticks: " + WATCH.state + "; " + describe(c));
                    }
                    int wings = DroneBosses.wingPushes() - base(c, 9);
                    int turbulence = DroneBosses.turbulencePushes() - base(c, 7);
                    if (wings > 0 && turbulence > 0 && pushes(c) > 0 && maxSpeed(c) > 4.0 && pictured(c, "boss_dragon_wing_fpv")) {
                        c.log(describe(c));
                        return StepResult.pass();
                    }
                    return null;
                });
    }

    private static StepResult ramStep(SelfTest.StepContext ctx) {
        return scenario(ctx, server -> dragon(server, arena().at(0.0, 4.0, 0.0), EnderDragonPhase.HOVERING), new Vec3(-12.0, 6.0, 0.0), -90.0,
                500, (c, f, ticks) -> {
                    Vec3 head = WATCH.head;
                    int rams = DroneBosses.hits(BossHit.DRAGON_RAM) - base(c, 2);
                    if (rams == 0 && head != null && ticks >= 10 && ticks < 40) {
                        TrainingSelfTests.teleport(head.x, head.y - 0.1, head.z, 90.0, 0.0, 0.0, 0.0);
                    }
                    if (ticks == 8) {
                        shot(c, "boss_dragon_ram_fpv");
                    }
                    if (rams > 0 && WATCH.worstProp < 0.6F && pushes(c) > 0) {
                        c.log(describe(c));
                        return StepResult.pass();
                    }
                    return null;
                });
    }

    private static StepResult chargeStep(SelfTest.StepContext ctx) {
        return scenario(ctx, server -> dragon(server, arena().at(30.0, 10.0, 0.0), EnderDragonPhase.HOLDING_PATTERN), new Vec3(0.0, 5.0, 0.0),
                -90.0, 800, (c, f, ticks) -> {
                    int charges = DroneBosses.dragonCharges() - base(c, 5);
                    if (charges > 0 && WATCH.chargeAtDrone && Double.isFinite(WATCH.chargeMiss) && WATCH.chargeMiss < 3.0) {
                        int seen = (int) c.state().computeIfAbsent("boss_seen", key -> ticks);
                        Vec3 dragon = WATCH.dragon;
                        if (ticks - seen == 10 && dragon != null) {
                            Vec3 at = f.drone().position();
                            double yaw = Math.toDegrees(Math.atan2(-(dragon.x - at.x), dragon.z - at.z));
                            TrainingSelfTests.teleport(at.x, at.y, at.z, yaw, 0.0, 0.0, 0.0);
                        }
                        if (ticks - seen < 13) {
                            return null;
                        }
                        shot(c, "boss_dragon_charge_fpv");
                        c.log(String.format(Locale.ROOT, "Drache greift nach %d Ticks an, Ziel %.2f m neben der Drohne; %s", ticks, WATCH.chargeMiss,
                                describe(c)));
                        return StepResult.pass();
                    }
                    return null;
                });
    }

    private static StepResult breathStep(SelfTest.StepContext ctx) {
        return scenario(ctx, server -> {
            ServerLevel level = server.overworld();
            Vec3 at = arena().at(0.0, 0.0, 0.0);
            AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
            cloud.setRadius(3.0F);
            cloud.setDuration(300);
            cloud.setWaitTime(0);
            cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
            level.addFreshEntity(cloud);
        }, new Vec3(0.0, 1.2, 0.0), 0.0, 500, (c, f, ticks) -> {
            if (ticks >= 25) {
                shot(c, "boss_dragon_breath_fpv");
            }
            int doses = DroneBosses.hits(BossHit.DRAGON_BREATH) - base(c, 3);
            if (doses >= 2 && WATCH.video <= 0.75F && pictured(c, "boss_dragon_breath_fpv")) {
                c.log(describe(c));
                return StepResult.pass();
            }
            return null;
        });
    }

    private static StepResult witherStep(SelfTest.StepContext ctx) {
        return scenario(ctx, server -> {
            ServerLevel level = server.overworld();
            WitherBoss wither = spawn(level, EntityTypes.WITHER.create(level, EntitySpawnReason.COMMAND), arena().at(0.0, 0.0, 12.0), 180.0F);
            wither.setInvulnerableTicks(0);
        }, new Vec3(0.0, 4.0, 0.0), 0.0, 600, (c, f, ticks) -> {
            int skulls = DroneBosses.skulls() - base(c, 4);
            if (skulls > 0 && ticks - (int) c.state().computeIfAbsent("boss_seen", key -> ticks) >= 4) {
                shot(c, "boss_wither_skulls_fpv");
            }
            if (skulls > 0 && WATCH.skullAim > 0.9 && pictured(c, "boss_wither_skulls_fpv")) {
                c.log(String.format(Locale.ROOT, "Schädel auf die Drohne: %d, beste Ausrichtung %.3f; %s", skulls, WATCH.skullAim, describe(c)));
                return StepResult.pass();
            }
            return null;
        });
    }

    private static StepResult windStep(SelfTest.StepContext ctx) {
        return scenario(ctx, DroneBossSelfTests::windCharge, new Vec3(0.0, 3.0, 0.0), 0.0, 400, (c, f, ticks) -> {
            int forwarded = Knockback.forwarded() - base(c, 8);
            if (forwarded > 0 && pushes(c) > 0 && maxSpeed(c) > 3.0) {
                c.log(describe(c));
                return StepResult.pass();
            }
            return null;
        });
    }

    private static void windCharge(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Arena arena = arena();
        if (arena.drone == null || !(level.getEntity(arena.drone) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne fehlt");
        }
        BreezeWindCharge charge = EntityTypes.BREEZE_WIND_CHARGE.create(level, EntitySpawnReason.COMMAND);
        if (charge == null) {
            throw new IllegalStateException("Windkugel nicht erzeugt");
        }
        Vec3 target = drone.getBoundingBox().getCenter();
        Vec3 from = target.add(0.0, 0.0, 4.0);
        charge.snapTo(from.x, from.y, from.z, 0.0F, 0.0F);
        charge.setDeltaMovement(target.subtract(from).normalize().scale(0.8));
        level.addFreshEntity(charge);
    }

    private static StepResult ruleOffStep(SelfTest.StepContext ctx) {
        return scenario(ctx, server -> {
            WorldRules.set(server, WorldRuleSpecs.DRONES_BOSS_INTERACTIONS, 0);
            warden(server, arena().at(0.0, 0.0, 8.0), true);
        }, new Vec3(0.0, 3.0, 0.0), 0.0, 800, (c, f, ticks) -> {
            if (WATCH.anger > 0 || DroneBosses.sonicCharges() > base(c, 6)) {
                return StepResult.fail("Wärter wird trotz Regel aus wütend: " + describe(c));
            }
            if (ticks == 300) {
                server(c, DroneBossSelfTests::windCharge);
            }
            if (ticks >= 400) {
                if (Knockback.forwarded() > base(c, 8) || pushes(c) > 0) {
                    return StepResult.fail("Windkugel stößt trotz Regel aus: " + describe(c));
                }
                server(c, server -> WorldRules.set(server, WorldRuleSpecs.DRONES_BOSS_INTERACTIONS, 1));
                c.log("Regel aus: 400 Ticks ohne Zorn und ohne Stoß; " + describe(c));
                return StepResult.pass();
            }
            return null;
        });
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (stage(ctx) == 0) {
            input(0.0F, false);
            PilotSession.INSTANCE.clearTestInput();
            server(ctx, DroneBossSelfTests::restore);
            stage(ctx, 1);
            return StepResult.pending();
        }
        return PilotSession.INSTANCE.isActive() && since(ctx) < 60 ? StepResult.pending() : StepResult.pass();
    }

    private static void restore(MinecraftServer server) {
        Arena arena = ARENA.getAndSet(null);
        if (arena == null) {
            return;
        }
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        clearBosses(level, arena);
        if (arena.drone != null && level.getEntity(arena.drone) instanceof QuadcopterEntity drone) {
            drone.discard();
        }
        for (Map.Entry<BlockPos, BlockState> entry : arena.original.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
        }
        for (Map.Entry<RuleSpec, Integer> entry : arena.rules.entrySet()) {
            WorldRules.set(server, entry.getKey(), entry.getValue());
        }
        level.getGameRules().set(GameRules.MOB_GRIEFING, arena.griefing, server);
        if (arena.difficulty != null) {
            server.setDifficulty(arena.difficulty, true);
        }
        if (arena.mode != null) {
            player.setGameMode(arena.mode);
        }
        player.setItemSlot(EquipmentSlot.HEAD, arena.head == null ? ItemStack.EMPTY : arena.head);
        player.setItemSlot(EquipmentSlot.MAINHAND, arena.hand == null ? ItemStack.EMPTY : arena.hand);
    }
}
