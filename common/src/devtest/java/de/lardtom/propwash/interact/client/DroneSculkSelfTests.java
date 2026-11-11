package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class DroneSculkSelfTests {
    public static final String GROUP = "sculk: ";
    static final String RULE = "drones_trigger_sculk";
    private static final int SITE_DX = -36;
    private static final int SITE_DZ = 24;
    private static final int HALF = 5;
    private static final int HEIGHT = 15;
    private static final int SENSOR_DX = 3;
    private static final int SHRIEKER_DZ = 3;
    private static final double LOW = 1.5;
    private static final double HIGH = 10.5;
    private static final double DROP_SPEED = 25.0;
    private static final double PUSH_HEIGHT = 2.0;
    private static final int SYNC_TICKS = 100;
    private static final int QUIET_TICKS = 60;
    private static final int SWITCH_OFF_TICKS = 10;
    private static final int ARM_TICKS = 60;
    private static final int IDLE_TICKS = 40;
    private static final int CLIMB_TICKS = 80;
    private static final int HOVER_TICKS = 80;
    private static final int CALM_TICKS = 45;
    private static final int CALM_LIMIT = 200;
    private static final int DISARM_TICKS = 4;
    private static final int CRASH_TICKS = 100;
    private static final int AFTER_CRASH_TICKS = 25;
    private static final String STAGE = "sculk_stage";
    private static final String MARK = "sculk_mark";
    private static final String FUTURE = "sculk_future";
    private static final String CRASHES = "sculk_crashes";
    private static final String MOTOR = "sculk_motor";
    private static final String VERDICT = "sculk_verdict";
    private static final Quaternionf LEVEL = new Quaternionf();
    private static final AtomicReference<@Nullable Rig> RIG = new AtomicReference<>();
    private static final Watch WATCH = new Watch();

    private DroneSculkSelfTests() {
    }

    public static void register() {
        ServerBus.TICK_END.register(DroneSculkSelfTests::sample);
        SelfTest.clientStep(GROUP + "Wollkammer mit Sculk-Sensoren, Kreischer und Drohne aufbauen", 400, DroneSculkSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "unbewaffnete Drohne am Boden löst keinen Sensor aus", 300, DroneSculkSelfTests::groundStep);
        SelfTest.clientStep(GROUP + "Sensor neben schwebender Drohne gibt Signal, hinter Wolle nicht, Kreischer schreit für den Piloten", 900,
                DroneSculkSelfTests::hoverStep);
        SelfTest.clientStep(GROUP + "Absturz neben dem Sensor löst HIT_GROUND aus", 900, DroneSculkSelfTests::crashStep);
        SelfTest.clientStep(GROUP + "Welt-Regel „Drohnen lösen Sculk aus“ aus: schwebende Drohne bleibt still", 900, DroneSculkSelfTests::ruleOffStep);
        SelfTest.clientStep(GROUP + "Wollkammer abbauen", 300, DroneSculkSelfTests::cleanupStep);
    }

    private static final class Rig {
        final BlockPos center;
        final int floor;
        final LinkedHashMap<BlockPos, BlockState> original = new LinkedHashMap<>();
        int droneId = -1;
        @Nullable ItemStack head;
        @Nullable ItemStack hand;
        @Nullable Integer ruleBefore;

        Rig(BlockPos center, int floor) {
            this.center = center;
            this.floor = floor;
        }

        BlockPos open() {
            return new BlockPos(this.center.getX() + SENSOR_DX, this.floor + 1, this.center.getZ());
        }

        BlockPos wrapped() {
            return new BlockPos(this.center.getX() - SENSOR_DX, this.floor + 1, this.center.getZ());
        }

        BlockPos shrieker() {
            return new BlockPos(this.center.getX() + SENSOR_DX, this.floor + 1, this.center.getZ() + SHRIEKER_DZ);
        }

        Vec3 pad() {
            return new Vec3(this.center.getX() + 0.5, this.floor + 1.0, this.center.getZ() + 0.5);
        }

        double low() {
            return this.floor + 1.0 + LOW;
        }

        double high() {
            return this.floor + 1.0 + HIGH;
        }
    }

    private static final class Watch {
        volatile int openActive;
        volatile int wrappedActive;
        volatile int frequencies;
        volatile boolean shrieked;
        volatile long openBusy = Long.MIN_VALUE;
        volatile long now;
        volatile boolean openInactive = true;

        void reset() {
            this.openActive = 0;
            this.wrappedActive = 0;
            this.frequencies = 0;
            this.shrieked = false;
        }

        boolean calm(int ticks) {
            return this.openInactive && (this.openBusy == Long.MIN_VALUE || this.now - this.openBusy >= ticks);
        }

        String describe() {
            StringBuilder out = new StringBuilder();
            for (int f = 0; f < 16; f++) {
                if ((this.frequencies & (1 << f)) != 0) {
                    out.append(out.isEmpty() ? "" : ",").append(f);
                }
            }
            return "offener Sensor aktiv " + this.openActive + " Ticks (Frequenzen [" + out + "]), Sensor hinter Wolle aktiv "
                    + this.wrappedActive + " Ticks, Kreischer " + (this.shrieked ? "hat geschrien" : "still");
        }
    }

    private static void sample(MinecraftServer server) {
        Rig rig = RIG.get();
        if (rig == null) {
            return;
        }
        ServerLevel level = server.overworld();
        long tick = server.getTickCount();
        WATCH.now = tick;
        BlockState open = level.getBlockState(rig.open());
        if (open.getBlock() instanceof SculkSensorBlock) {
            SculkSensorPhase phase = SculkSensorBlock.getPhase(open);
            WATCH.openInactive = phase == SculkSensorPhase.INACTIVE;
            if (phase != SculkSensorPhase.INACTIVE) {
                WATCH.openBusy = tick;
            }
            if (phase == SculkSensorPhase.ACTIVE) {
                WATCH.openActive++;
                if (level.getBlockEntity(rig.open()) instanceof SculkSensorBlockEntity sensor) {
                    WATCH.frequencies |= 1 << Math.clamp(sensor.getLastVibrationFrequency(), 0, 15);
                }
            }
        }
        BlockState wrapped = level.getBlockState(rig.wrapped());
        if (wrapped.getBlock() instanceof SculkSensorBlock && SculkSensorBlock.getPhase(wrapped) != SculkSensorPhase.INACTIVE) {
            WATCH.wrappedActive++;
        }
        BlockState shrieker = level.getBlockState(rig.shrieker());
        if (shrieker.getBlock() instanceof SculkShriekerBlock && shrieker.getValue(SculkShriekerBlock.SHRIEKING)) {
            WATCH.shrieked = true;
        }
    }

    static int frequency(Holder<GameEvent> event) {
        return VibrationSystem.getGameEventFrequency(event);
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server, int id) {
        if (!(server.overworld().getEntity(id) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne auf dem Server verschwunden");
        }
        return drone;
    }

    static Optional<RuleSpec> rule() {
        return WorldRuleSpecs.byName(RULE);
    }

    private static void set(ServerLevel level, Rig rig, BlockPos pos, BlockState state) {
        rig.original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void prepare(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int x0 = player.getBlockX() + SITE_DX;
        int z0 = player.getBlockZ() + SITE_DZ;
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
        Rig rig = new Rig(new BlockPos(x0, floor, z0), floor);
        BlockState wool = Blocks.WOOL.white().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                for (int dy = -1; dy <= HEIGHT; dy++) {
                    boolean shell = Math.abs(dx) == HALF || Math.abs(dz) == HALF || dy <= 0 || dy == HEIGHT;
                    BlockState state = shell ? wool : air;
                    if (dy == 0 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        state = Blocks.STONE.defaultBlockState();
                    }
                    set(level, rig, new BlockPos(x0 + dx, floor + dy, z0 + dz), state);
                }
            }
        }
        BlockPos wrapped = rig.wrapped();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    set(level, rig, wrapped.offset(dx, dy, dz), wool);
                }
            }
        }
        AABB box = new AABB(x0 - HALF, floor - 1, z0 - HALF, x0 + HALF + 1, floor + HEIGHT + 1, z0 + HALF + 1);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            mob.discard();
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 pad = rig.pad();
        drone.snapTo(pad.x, pad.y, pad.z, 0.0F, 0.0F);
        drone.setAttitude(LEVEL);
        drone.setBuild(DroneBuild.WHOOP);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        rig.droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        rig.head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        rig.hand = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        rule().ifPresent(spec -> {
            rig.ruleBefore = WorldRules.get(server, spec);
            WorldRules.set(server, spec, 1);
        });
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
        set(level, rig, rig.open(), Blocks.SCULK_SENSOR.defaultBlockState());
        set(level, rig, wrapped, Blocks.SCULK_SENSOR.defaultBlockState());
        set(level, rig, rig.shrieker(), Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, false));
        WATCH.reset();
        WATCH.openBusy = Long.MIN_VALUE;
        RIG.set(rig);
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

    private static void hold(QuadcopterEntity drone, @Nullable Telemetry t, double targetY) {
        double hover = t == null ? 0.4 : Math.clamp(t.hoverThrottleStick, 0.15, 0.7);
        double vs = t == null ? 0.0 : t.verticalSpeed;
        double wanted = Math.clamp(1.2 * (targetY - drone.getY()), -2.0, 2.0);
        input((float) Math.clamp(hover + 0.06 * (wanted - vs), 0.05, 0.9), true);
    }

    private static void push(DroneSim sim, double vy) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = 0.0;
        snapshot.vy = vy;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private record Flight(Rig rig, QuadcopterEntity drone, DroneSim sim, Telemetry t) {
    }

    private static @Nullable Flight flight() {
        Rig rig = RIG.get();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (rig == null || drone == null || sim == null || drone.getId() != rig.droneId || !session.isFpv()) {
            return null;
        }
        return new Flight(rig, drone, sim, sim.telemetry());
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (stage(ctx) == 0) {
            input(0.0F, false);
            server(ctx, DroneSculkSelfTests::prepare);
            stage(ctx, 1);
            return StepResult.pending();
        }
        Flight f = flight();
        if (f == null) {
            return since(ctx) > SYNC_TICKS ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        input(0.0F, false);
        if (!(f.drone().level().getBlockState(f.rig().open()).getBlock() instanceof SculkSensorBlock)) {
            return since(ctx) > SYNC_TICKS ? StepResult.fail("Sculk-Sensor kommt beim Client nicht an") : StepResult.pending();
        }
        ctx.log("Wollkammer bei " + f.rig().center.toShortString() + ", offener Sensor " + f.rig().open().toShortString()
                + ", Sensor in Wolle " + f.rig().wrapped().toShortString() + ", Kreischer " + f.rig().shrieker().toShortString()
                + ", Welt-Regel " + RULE + (rule().isPresent() ? " vorhanden" : " fehlt"));
        return StepResult.pass();
    }

    private static StepResult groundStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        Flight f = flight();
        if (f == null) {
            return StepResult.fail("keine FPV-Sitzung mit der Testdrohne");
        }
        input(0.0F, false);
        switch (stage(ctx)) {
            case 0 -> {
                server(ctx, server -> WATCH.reset());
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (f.t().armed) {
                    return StepResult.fail("Drohne ist scharf, obwohl der Schalter aus ist");
                }
                if (since(ctx) < QUIET_TICKS) {
                    return StepResult.pending();
                }
                ctx.log("unbewaffnet am Boden, " + QUIET_TICKS + " Ticks: " + WATCH.describe());
                return StepResult.check(WATCH.openActive == 0 && WATCH.wrappedActive == 0 && !WATCH.shrieked,
                        "unbewaffnete Drohne am Boden hat ausgelöst: " + WATCH.describe());
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + stage(ctx));
            }
        }
    }

    private static StepResult armOnGround(SelfTest.StepContext ctx, Flight f, int next) {
        if (since(ctx) < SWITCH_OFF_TICKS) {
            input(0.0F, false);
            return StepResult.pending();
        }
        input(0.0F, true);
        if (f.t().armed) {
            stage(ctx, next);
            return StepResult.pending();
        }
        return since(ctx) > SWITCH_OFF_TICKS + ARM_TICKS ? StepResult.fail("Drohne lässt sich am Boden nicht scharf schalten, Sperren=" + f.t().armBlockers)
                : StepResult.pending();
    }

    private static StepResult hoverStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        Flight f = flight();
        if (f == null) {
            return StepResult.fail("keine FPV-Sitzung mit der Testdrohne");
        }
        int ticks = since(ctx);
        switch (stage(ctx)) {
            case 0 -> {
                return armOnGround(ctx, f, 1);
            }
            case 1 -> {
                input(0.0F, true);
                server(ctx, server -> WATCH.reset());
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                input(0.0F, true);
                if (!f.t().armed) {
                    return StepResult.fail("Drohne im Leerlauf entschärft");
                }
                if (ticks < IDLE_TICKS) {
                    return StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "scharf im Leerlauf am Boden, Motoren %.3f: %s", f.drone().motorOutput(), WATCH.describe()));
                if (WATCH.openActive > 0 || WATCH.wrappedActive > 0) {
                    return StepResult.fail("scharfe Drohne ohne Gas hat ausgelöst: " + WATCH.describe());
                }
                stage(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                hold(f.drone(), f.t(), f.rig().low());
                if (f.drone().getY() < f.rig().low() - 0.4) {
                    return ticks > CLIMB_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Drohne steigt nicht auf %.1f m, steht bei y=%.2f",
                            LOW, f.drone().getY())) : StepResult.pending();
                }
                server(ctx, server -> WATCH.reset());
                stage(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                hold(f.drone(), f.t(), f.rig().low());
                if (!f.t().armed) {
                    return StepResult.fail("Drohne beim Schweben entschärft: " + f.t().lastDisarmReason);
                }
                ctx.state().merge(MOTOR, (double) f.drone().motorOutput(), (a, b) -> (double) a + (double) b);
                if (ticks < HOVER_TICKS) {
                    return StepResult.pending();
                }
                double motor = (double) ctx.state().getOrDefault(MOTOR, 0.0) / Math.max(1, ticks);
                int glide = frequency(GameEvent.ELYTRA_GLIDE);
                ctx.log(String.format(Locale.ROOT, "Schweben %.1f m über dem Boden, Motoren im Mittel %.3f, %d Ticks: %s", LOW, motor, ticks,
                        WATCH.describe()));
                if (WATCH.openActive == 0) {
                    return StepResult.fail("offener Sensor neben der schwebenden Drohne bleibt still: " + WATCH.describe());
                }
                if ((WATCH.frequencies & (1 << glide)) == 0) {
                    return StepResult.fail("offener Sensor ohne Flug-Frequenz " + glide + ": " + WATCH.describe());
                }
                if (WATCH.wrappedActive > 0) {
                    return StepResult.fail("Sensor hinter Wolle hat ausgelöst: " + WATCH.describe());
                }
                return StepResult.check(WATCH.shrieked, "Kreischer rechnet die Drohne nicht dem Piloten zu: " + WATCH.describe());
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + stage(ctx));
            }
        }
    }

    private static StepResult crashStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        Flight f = flight();
        if (f == null) {
            return StepResult.fail("keine FPV-Sitzung mit der Testdrohne");
        }
        int ticks = since(ctx);
        Rig rig = f.rig();
        switch (stage(ctx)) {
            case 0 -> {
                if (!f.t().armed) {
                    return StepResult.fail("Drohne ist vor dem Absturztest nicht scharf");
                }
                hold(f.drone(), f.t(), f.drone().getY());
                Vec3 up = new Vec3(rig.pad().x, rig.high(), rig.pad().z);
                server(ctx, server -> serverDrone(server, rig.droneId).resetTo(up, LEVEL));
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                hold(f.drone(), f.t(), rig.high());
                if (!f.t().armed) {
                    return StepResult.fail("Drohne oben entschärft: " + f.t().lastDisarmReason);
                }
                if (ticks < CALM_TICKS || !WATCH.calm(CALM_TICKS)) {
                    return ticks > CALM_LIMIT ? StepResult.fail("offener Sensor kommt oben nicht zur Ruhe: " + WATCH.describe()) : StepResult.pending();
                }
                server(ctx, server -> WATCH.reset());
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                input(0.0F, false);
                if (ticks < DISARM_TICKS) {
                    return StepResult.pending();
                }
                if (f.t().armed) {
                    return StepResult.fail("Drohne lässt sich in der Luft nicht entschärfen");
                }
                ctx.state().put(CRASHES, PilotSession.INSTANCE.crashCount());
                stage(ctx, 3);
                return StepResult.pending();
            }
            case 3 -> {
                input(0.0F, false);
                if (f.drone().getY() > rig.pad().y + PUSH_HEIGHT) {
                    return ticks > CRASH_TICKS ? StepResult.fail(String.format(Locale.ROOT, "entschärfte Drohne fällt nicht, steht bei y=%.2f",
                            f.drone().getY())) : StepResult.pending();
                }
                push(f.sim(), -DROP_SPEED);
                stage(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                input(0.0F, false);
                int before = (int) ctx.state().getOrDefault(CRASHES, 0);
                if (PilotSession.INSTANCE.crashCount() <= before) {
                    return ticks > CRASH_TICKS ? StepResult.fail(String.format(Locale.ROOT, "kein Absturz gezählt, Drohne bei y=%.2f",
                            f.drone().getY())) : StepResult.pending();
                }
                stage(ctx, 5);
                return StepResult.pending();
            }
            case 5 -> {
                input(0.0F, false);
                if (ticks < AFTER_CRASH_TICKS) {
                    return StepResult.pending();
                }
                int hit = frequency(GameEvent.HIT_GROUND);
                ctx.log(String.format(Locale.ROOT, "entschärft aus %.1f m gefallen, %.0f m über dem Boden auf %.0f m/s gestoßen, Drohne bei y=%.2f: %s", HIGH, PUSH_HEIGHT, DROP_SPEED,
                        f.drone().getY() - rig.floor - 1.0, WATCH.describe()));
                if ((WATCH.frequencies & (1 << hit)) == 0) {
                    return StepResult.fail("offener Sensor ohne HIT_GROUND (Frequenz " + hit + ") nach dem Absturz: " + WATCH.describe());
                }
                return StepResult.check(WATCH.wrappedActive == 0, "Sensor hinter Wolle hat den Absturz gehört: " + WATCH.describe());
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + stage(ctx));
            }
        }
    }

    private static StepResult ruleOffStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        Flight f = flight();
        if (f == null) {
            return StepResult.fail("keine FPV-Sitzung mit der Testdrohne");
        }
        Optional<RuleSpec> spec = rule();
        if (spec.isEmpty()) {
            input(0.0F, false);
            return StepResult.fail("Welt-Regel propwash:" + RULE + " fehlt");
        }
        int ticks = since(ctx);
        Rig rig = f.rig();
        switch (stage(ctx)) {
            case 0 -> {
                input(0.0F, false);
                Vec3 pad = rig.pad();
                server(ctx, server -> {
                    WorldRules.set(server, spec.get(), 0);
                    serverDrone(server, rig.droneId).resetTo(pad, LEVEL);
                });
                stage(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                input(0.0F, false);
                if (ticks < CALM_TICKS || !WATCH.calm(CALM_TICKS)) {
                    return ticks > CALM_LIMIT ? StepResult.fail("offener Sensor kommt nicht zur Ruhe: " + WATCH.describe()) : StepResult.pending();
                }
                stage(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                return armOnGround(ctx, f, 3);
            }
            case 3 -> {
                hold(f.drone(), f.t(), rig.low());
                if (f.drone().getY() < rig.low() - 0.4) {
                    return ticks > CLIMB_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Drohne steigt nicht auf %.1f m, steht bei y=%.2f",
                            LOW, f.drone().getY())) : StepResult.pending();
                }
                server(ctx, server -> WATCH.reset());
                stage(ctx, 4);
                return StepResult.pending();
            }
            case 4 -> {
                hold(f.drone(), f.t(), rig.low());
                if (!f.t().armed) {
                    return StepResult.fail("Drohne beim Schweben entschärft: " + f.t().lastDisarmReason);
                }
                if (ticks < HOVER_TICKS) {
                    return StepResult.pending();
                }
                String seen = WATCH.describe();
                ctx.log("Welt-Regel aus, Schweben " + ticks + " Ticks: " + seen);
                ctx.state().put(VERDICT, WATCH.openActive == 0 && WATCH.wrappedActive == 0 && !WATCH.shrieked ? ""
                        : "Welt-Regel aus, trotzdem ausgelöst: " + seen);
                input(0.0F, false);
                server(ctx, server -> WorldRules.set(server, spec.get(), 1));
                stage(ctx, 5);
                return StepResult.pending();
            }
            case 5 -> {
                input(0.0F, false);
                String verdict = (String) ctx.state().getOrDefault(VERDICT, "keine Messung");
                return verdict.isEmpty() ? StepResult.pass() : StepResult.fail(verdict);
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + stage(ctx));
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        StepResult waiting = pending(ctx);
        if (waiting != null) {
            return waiting;
        }
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.pass();
        }
        if (stage(ctx) == 0) {
            PilotSession.INSTANCE.clearTestInput();
            server(ctx, server -> {
                if (rig.droneId >= 0 && server.overworld().getEntity(rig.droneId) instanceof QuadcopterEntity drone) {
                    DronePiloting.release(drone);
                    drone.discard();
                }
                ServerPlayer player = player(server);
                if (rig.head != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, rig.head);
                }
                if (rig.hand != null) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, rig.hand);
                }
                if (rig.ruleBefore != null) {
                    rule().ifPresent(spec -> WorldRules.set(server, spec, rig.ruleBefore));
                }
                RIG.compareAndSet(rig, null);
                ServerLevel level = player.level();
                for (Map.Entry<BlockPos, BlockState> entry : rig.original.reversed().entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
                }
            });
            stage(ctx, 1);
            return StepResult.pending();
        }
        return PilotSession.INSTANCE.isActive() ? (since(ctx) > SYNC_TICKS ? StepResult.fail("Sitzung läuft nach dem Aufräumen weiter")
                : StepResult.pending()) : StepResult.pass();
    }
}
