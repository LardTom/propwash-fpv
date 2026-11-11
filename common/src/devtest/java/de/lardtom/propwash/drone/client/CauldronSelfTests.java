package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.CauldronContent;
import de.lardtom.propwash.drone.DroneFluids;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.WaterSurface;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.interact.SnagBlocks;
import de.lardtom.propwash.interact.SnagMaterial;
import de.lardtom.propwash.interact.client.PilotSnags;
import de.lardtom.propwash.rescue.CauldronServerFlights;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Fluid;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class CauldronSelfTests {
    static final String GROUP = "drone: Kessel";
    private static final int ORIGIN_DX = -26;
    private static final int ORIGIN_DZ = 10;
    private static final int ROW_SPACING = 5;
    private static final int CLEAR_BEFORE = 1;
    private static final int CLEAR_AFTER = 8;
    private static final int CLEAR_HALF = 2;
    private static final int CLEAR_HEIGHT = 5;
    private static final int CAULDRON_DX = 3;
    private static final int REFERENCE_DX = 6;
    static final double FLOOR = 4.0 / 16.0;
    static final double PLACED = 0.3;
    static final int SYNC_TICKS = 80;
    static final int ARM_TICKS = 60;
    static final int SWITCH_OFF_TICKS = 3;
    private static final int OBSERVE_TICKS = 30;
    private static final int LAVA_DISARM_TICKS = 2;
    private static final int WATER_DISARM_TICKS = 15;
    private static final int SERVER_TICKS = 20;
    private static final int FILL_ROW = 4;
    private static final double PROBE = 0.01;
    private static final double SURFACE_PROBE = 0.3;
    private static final double SINK_DROP = 0.5;
    private static final double MAX_SINK_SPEED = 1.5;
    private static final double MIN_SUNK = 0.3;
    private static final String SCENARIO = "cauldron";
    static final Quaternionf LEVEL = new Quaternionf();
    static final AtomicReference<Rig> RIG = new AtomicReference<>();

    private CauldronSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(GROUP + " – Wasserkessel wirkt wie Wasser: scharfe Drohne wird darin wie im Wasserblock entschärft (FLUID), "
                + "Scharfschalten gesperrt, Heimflug am Server ebenso", 900, ctx -> step(ctx, Kind.WATER));
        SelfTest.clientStep(GROUP + " – Lavakessel wirkt wie Lava: scharfe Drohne wird darin wie im Lavablock sofort entschärft (FLUID), "
                + "Scharfschalten gesperrt, Heimflug am Server ebenso", 900, ctx -> step(ctx, Kind.LAVA));
        SelfTest.clientStep(GROUP + " – Pulverschneekessel wirkt wie Pulverschnee: bremst die Drohne bis zur Füllhöhe, sie sinkt langsam ein "
                + "wie im Pulverschneeblock, bleibt scharf (keine Flüssigkeit), Heimflug am Server ebenso", 900, ctx -> step(ctx, Kind.POWDER_SNOW));
        SelfTest.clientStep(GROUP + " – leerer Kessel (Kontrolle): keine Flüssigkeit, keine Bremse, Drohne bleibt scharf", 900,
                ctx -> step(ctx, Kind.EMPTY));
        SelfTest.clientStep(GROUP + " – Füllhöhe: Wasser und Lava nur im Becken bis zur Füllhöhe jedes Füllstands (wie die Vanilla-Form des "
                + "Inhalts), Pulverschnee und leerer Kessel ohne Flüssigkeit, Pulverschnee bremst nur im Becken bis zur Füllhöhe, "
                + "Wasseroberfläche wie im Wasserblock", 300, CauldronSelfTests::fillStep);
        CauldronDriftSelfTests.register();
        SelfTest.clientStep(GROUP + " – Testfeld abbauen", 200, CauldronSelfTests::cleanupStep);
    }

    enum Kind {
        WATER(0, "Wasserkessel", "Wasserblock", true, false),
        LAVA(1, "Lavakessel", "Lavablock", true, false),
        POWDER_SNOW(2, "Pulverschneekessel", "Pulverschneeblock", false, true),
        EMPTY(3, "leerer Kessel", "Luft", false, false);

        final int row;
        final String cauldronName;
        final String referenceName;
        final boolean fluid;
        final boolean brakes;

        Kind(int row, String cauldronName, String referenceName, boolean fluid, boolean brakes) {
            this.row = row;
            this.cauldronName = cauldronName;
            this.referenceName = referenceName;
            this.fluid = fluid;
            this.brakes = brakes;
        }

        double drop() {
            return this.brakes ? SINK_DROP : 0.0;
        }

        BlockState cauldron() {
            return switch (this) {
                case WATER -> Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL);
                case LAVA -> Blocks.LAVA_CAULDRON.defaultBlockState();
                case POWDER_SNOW -> Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,
                        LayeredCauldronBlock.MAX_FILL_LEVEL);
                case EMPTY -> Blocks.CAULDRON.defaultBlockState();
            };
        }

        BlockState reference() {
            return switch (this) {
                case WATER -> Blocks.WATER.defaultBlockState();
                case LAVA -> Blocks.LAVA.defaultBlockState();
                case POWDER_SNOW -> Blocks.POWDER_SNOW.defaultBlockState();
                case EMPTY -> Blocks.AIR.defaultBlockState();
            };
        }
    }

    enum Target {
        REFERENCE,
        CAULDRON
    }

    static final class Rig {
        final int x;
        final int y;
        final int z;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        final Map<Kind, Boolean> built = new LinkedHashMap<>();
        int droneId = -1;
        @Nullable ItemStack head;
        @Nullable ItemStack hand;

        Rig(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        int rowZ(Kind kind) {
            return this.z + ROW_SPACING * kind.row;
        }

        BlockPos cauldron(Kind kind) {
            return new BlockPos(this.x + CAULDRON_DX, this.y, this.rowZ(kind));
        }

        BlockPos reference(Kind kind) {
            return new BlockPos(this.x + REFERENCE_DX, this.y, this.rowZ(kind));
        }

        BlockPos fill(int index) {
            return new BlockPos(this.x + 1 + 2 * index, this.y, this.z + ROW_SPACING * FILL_ROW);
        }

        Vec3 armSpot(Kind kind) {
            return new Vec3(this.x + 0.5, this.y, this.rowZ(kind) + 0.5);
        }

        Vec3 feet(Kind kind, Target target) {
            if (target == Target.CAULDRON) {
                BlockPos pos = this.cauldron(kind);
                return new Vec3(pos.getX() + 0.5, pos.getY() + FLOOR, pos.getZ() + 0.5);
            }
            BlockPos pos = this.reference(kind);
            return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }

        Vec3 start(Kind kind, Target target) {
            return this.feet(kind, target).add(0.0, kind.drop(), 0.0);
        }
    }

    private record Observation(int disarmTick, DisarmReason reason, boolean inFluidBlocker, int blockers, boolean braked, double sunk,
                               double peakSink) {
        boolean fluidDisarm() {
            return this.disarmTick >= 0 && this.reason == DisarmReason.FLUID;
        }

        boolean sinksSlowly() {
            return this.braked && this.sunk >= MIN_SUNK && this.peakSink <= MAX_SINK_SPEED;
        }

        @Override
        public String toString() {
            String armed = this.disarmTick < 0 ? "bleibt scharf" : String.format(Locale.ROOT, "entschärft nach %d Ticks (%s)", this.disarmTick,
                    this.reason);
            return armed + ", Sperre IN_FLUID " + (this.inFluidBlocker ? "ja" : "nein") + " (Sperren=" + this.blockers + "), Bremse "
                    + (this.braked ? "ja" : "nein") + String.format(Locale.ROOT, ", sinkt %.2f m mit höchstens %.2f m/s", this.sunk, this.peakSink);
        }
    }

    private static final class Scenario {
        final Kind kind;
        int stage;
        int mark;
        Target target = Target.REFERENCE;
        @Nullable CompletableFuture<?> pending;
        int crashes;
        @Nullable PartWear wear;
        int disarmTick = -1;
        DisarmReason reason = DisarmReason.NONE;
        double startY = Double.NaN;
        double lowY = Double.POSITIVE_INFINITY;
        double peakSink;
        boolean braked;
        @Nullable Observation reference;
        @Nullable Observation cauldron;
        final AtomicReference<CauldronServerFlights.Result> serverReference = new AtomicReference<>();
        final AtomicReference<CauldronServerFlights.Result> serverCauldron = new AtomicReference<>();

        Scenario(Kind kind) {
            this.kind = kind;
        }

        void enter(int next, int tick) {
            this.stage = next;
            this.mark = tick;
        }
    }

    private static StepResult step(SelfTest.StepContext ctx, Kind kind) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent(SCENARIO, key -> new Scenario(kind));
        if (s.pending != null) {
            if (s.pending.isCompletedExceptionally()) {
                input(false);
                return StepResult.fail("Server-Aktion in Stufe " + s.stage + ": " + s.pending.exceptionNow().getMessage());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        StepResult result = run(ctx, s);
        if (result.status() != StepResult.Status.PENDING) {
            input(false);
        }
        return result;
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        int waited = ctx.tick() - s.mark;
        PilotSession session = PilotSession.INSTANCE;
        if (s.stage == 0) {
            input(false);
            s.pending = ctx.onServer(server -> prepare(server, s));
            s.enter(1, ctx.tick());
            return StepResult.pending();
        }
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.fail("kein Testfeld");
        }
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null || drone.getId() != rig.droneId || !session.isFpv()) {
            return waited > SYNC_TICKS ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        switch (s.stage) {
            case 1 -> {
                input(false);
                Level level = drone.level();
                if (!level.getBlockState(rig.cauldron(s.kind)).equals(s.kind.cauldron())
                        || !level.getBlockState(rig.reference(s.kind)).equals(s.kind.reference())) {
                    return waited > SYNC_TICKS ? StepResult.fail("Testfeld kommt beim Client nicht an: " + level.getBlockState(rig.cauldron(s.kind))
                            + ", " + level.getBlockState(rig.reference(s.kind))) : StepResult.pending();
                }
                s.crashes = session.crashCount();
                s.wear = sim.wear();
                return toArmSpot(ctx, s, rig);
            }
            case 2 -> {
                boolean grounded = t.grounded || t.sleeping;
                boolean there = near(drone, rig.armSpot(s.kind));
                input(t.armed || there && grounded && waited >= SWITCH_OFF_TICKS);
                if (!t.armed) {
                    return waited > SYNC_TICKS + ARM_TICKS ? StepResult.fail("auf dem Stein nicht scharf geschaltet, Sperren=" + t.armBlockers
                            + ", " + where(drone, sim)) : StepResult.pending();
                }
                Vec3 start = rig.start(s.kind, s.target);
                s.disarmTick = -1;
                s.reason = DisarmReason.NONE;
                s.startY = Double.NaN;
                s.lowY = Double.POSITIVE_INFINITY;
                s.peakSink = 0.0;
                s.braked = false;
                s.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(start, LEVEL));
                s.enter(3, ctx.tick());
                return StepResult.pending();
            }
            case 3 -> {
                input(true);
                if (!placed(drone, rig, s)) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne nicht in " + name(s) + " gesetzt: " + where(drone, sim))
                            : StepResult.pending();
                }
                s.enter(4, ctx.tick());
                return observe(ctx, s, rig, drone, sim, 0);
            }
            case 4 -> {
                input(true);
                return observe(ctx, s, rig, drone, sim, waited);
            }
            case 5 -> {
                input(false);
                return evaluate(ctx, s, sim);
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + s.stage);
            }
        }
    }

    private static StepResult toArmSpot(SelfTest.StepContext ctx, Scenario s, Rig rig) {
        Vec3 spot = rig.armSpot(s.kind);
        s.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(spot, LEVEL));
        s.enter(2, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult observe(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, DroneSim sim, int since) {
        Telemetry t = sim.telemetry();
        if (s.disarmTick < 0 && !t.armed) {
            s.disarmTick = since;
            s.reason = t.lastDisarmReason;
        }
        if (Double.isNaN(s.startY)) {
            s.startY = drone.getY();
        }
        s.lowY = Math.min(s.lowY, drone.getY());
        if (Double.isFinite(t.verticalSpeed)) {
            s.peakSink = Math.max(s.peakSink, -t.verticalSpeed);
        }
        s.braked |= PilotSnags.INSTANCE.touching();
        if (since < OBSERVE_TICKS) {
            return StepResult.pending();
        }
        Observation seen = new Observation(s.disarmTick, s.reason, ArmBlocker.IN_FLUID.isSet(t.armBlockers), t.armBlockers, s.braked,
                s.startY - s.lowY, s.peakSink);
        if (s.target == Target.REFERENCE) {
            s.reference = seen;
            s.target = Target.CAULDRON;
            input(false);
            return toArmSpot(ctx, s, rig);
        }
        s.cauldron = seen;
        input(false);
        Vec3 spot = rig.armSpot(s.kind);
        s.pending = ctx.onServer(server -> {
            serverDrone(server, rig.droneId).resetTo(spot, LEVEL);
            ServerLevel level = player(server).level();
            s.serverReference.set(CauldronServerFlights.fly(level, rig.feet(s.kind, Target.REFERENCE), SERVER_TICKS));
            s.serverCauldron.set(CauldronServerFlights.fly(level, rig.feet(s.kind, Target.CAULDRON), SERVER_TICKS));
        });
        s.enter(5, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult evaluate(SelfTest.StepContext ctx, Scenario s, DroneSim sim) {
        Observation reference = s.reference;
        Observation cauldron = s.cauldron;
        CauldronServerFlights.Result serverReference = s.serverReference.get();
        CauldronServerFlights.Result serverCauldron = s.serverCauldron.get();
        if (reference == null || cauldron == null || serverReference == null || serverCauldron == null) {
            return StepResult.fail("Messung unvollständig");
        }
        String state = String.format(Locale.ROOT, "%s: %s; %s: %s; Heimflug am Server – %s: %s, %s: %s", s.kind.referenceName, reference,
                s.kind.cauldronName, cauldron, s.kind.referenceName, serverReference, s.kind.cauldronName, serverCauldron);
        if (s.kind.fluid) {
            int limit = s.kind == Kind.LAVA ? LAVA_DISARM_TICKS : WATER_DISARM_TICKS;
            if (!reference.fluidDisarm() || reference.disarmTick > limit || !reference.inFluidBlocker || !serverReference.fluid()) {
                return StepResult.fail("Kontrolle " + s.kind.referenceName + " wirkt nicht wie erwartet: " + state);
            }
            if (!cauldron.fluidDisarm()) {
                return StepResult.fail(s.kind.cauldronName + " entschärft die scharfe Drohne nicht wie der " + s.kind.referenceName + ": " + state);
            }
            if (cauldron.disarmTick > limit) {
                return StepResult.fail(String.format(Locale.ROOT, "%s entschärft erst nach %d Ticks (höchstens %d wie im %s): %s",
                        s.kind.cauldronName, cauldron.disarmTick, limit, s.kind.referenceName, state));
            }
            if (!cauldron.inFluidBlocker) {
                return StepResult.fail(s.kind.cauldronName + " sperrt das Scharfschalten nicht (IN_FLUID fehlt): " + state);
            }
            if (!serverCauldron.fluid()) {
                return StepResult.fail("Heimflug am Server – " + s.kind.cauldronName + " entschärft nicht wie der " + s.kind.referenceName + ": "
                        + state);
            }
        } else {
            if (reference.disarmTick >= 0 || reference.inFluidBlocker || serverReference.fluid()) {
                return StepResult.fail("Kontrolle " + s.kind.referenceName + " wirkt nicht wie erwartet: " + state);
            }
            if (cauldron.disarmTick >= 0 || cauldron.inFluidBlocker) {
                return StepResult.fail(s.kind.cauldronName + " wirkt wie eine Flüssigkeit: " + state);
            }
            if (serverCauldron.fluid()) {
                return StepResult.fail("Heimflug am Server – " + s.kind.cauldronName + " entschärft wegen Flüssigkeit: " + state);
            }
        }
        if (s.kind.brakes) {
            if (!reference.sinksSlowly() || !serverReference.braked()) {
                return StepResult.fail(String.format(Locale.ROOT, "Kontrolle %s bremst nicht wie Pulverschnee (Bremse, mindestens %.2f m "
                        + "eingesunken, höchstens %.2f m/s, Heimflug mit Bremse): %s", s.kind.referenceName, MIN_SUNK, MAX_SINK_SPEED, state));
            }
            if (!cauldron.sinksSlowly()) {
                return StepResult.fail(String.format(Locale.ROOT, "%s bremst nicht wie der %s (Bremse, mindestens %.2f m eingesunken, "
                        + "höchstens %.2f m/s): %s", s.kind.cauldronName, s.kind.referenceName, MIN_SUNK, MAX_SINK_SPEED, state));
            }
            if (!serverCauldron.braked()) {
                return StepResult.fail("Heimflug am Server – " + s.kind.cauldronName + " bremst nicht wie der " + s.kind.referenceName + ": "
                        + state);
            }
        } else if (reference.braked || cauldron.braked || serverReference.braked() || serverCauldron.braked()) {
            return StepResult.fail("Bremse wie Pulverschnee ohne Pulverschnee: " + state);
        }
        PilotSession session = PilotSession.INSTANCE;
        if (session.crashCount() != s.crashes) {
            return StepResult.fail("Absturz im Test: " + state);
        }
        if (s.wear != null && !sim.wear().equals(s.wear)) {
            return StepResult.fail("Verschleiß im Test verändert: " + state);
        }
        ctx.log(state + "; Abstürze 0, Verschleiß unverändert");
        return StepResult.pass();
    }

    private static void prepare(MinecraftServer server, Scenario s) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = rig(player);
        if (!(rig.droneId >= 0 && level.getEntity(rig.droneId) instanceof QuadcopterEntity)) {
            spawn(player, rig);
        }
        QuadcopterEntity current = serverDrone(server, rig.droneId);
        if (DronePiloting.pilotedBy(player.getUUID()).filter(piloted -> piloted == current).isEmpty()) {
            fly(player, current, rig);
        }
        if (!rig.built.containsKey(s.kind)) {
            build(level, rig, s.kind);
        }
        current.setWaterproof(false);
        current.setWear(PartWear.PRISTINE);
    }

    static Rig rig(ServerPlayer player) {
        Rig rig = RIG.get();
        if (rig == null) {
            rig = new Rig(player.getBlockX() + ORIGIN_DX, player.getBlockY(), player.getBlockZ() + ORIGIN_DZ);
            RIG.set(rig);
        }
        return rig;
    }

    static StepResult fillStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(SCENARIO, ctx.onServer(server -> buildFill(player(server).level(), rig(player(server)))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(SCENARIO);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Testfeld: " + future.exceptionNow().getMessage());
        }
        Rig rig = RIG.get();
        Minecraft mc = Minecraft.getInstance();
        if (!future.isDone() || rig == null || mc.level == null || mc.player == null) {
            return ctx.tick() > SYNC_TICKS ? StepResult.fail("Testfeld nicht gebaut") : StepResult.pending();
        }
        List<BlockState> states = fillStates();
        for (int i = 0; i < states.size(); i++) {
            BlockPos pos = rig.fill(i);
            if (!mc.level.getBlockState(pos).equals(states.get(i))) {
                return ctx.tick() > SYNC_TICKS ? StepResult.fail("Testfeld kommt beim Client nicht an: " + pos.toShortString() + " "
                        + mc.level.getBlockState(pos)) : StepResult.pending();
            }
        }
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < states.size(); i++) {
            String problem = checkFill(mc.level, mc.player, rig.fill(i), states.get(i), seen);
            if (problem != null) {
                return StepResult.fail(problem + "; bisher " + seen);
            }
        }
        ctx.log(states.size() + " Kessel geprüft: " + seen);
        return StepResult.pass();
    }

    private static List<BlockState> fillStates() {
        List<BlockState> states = new ArrayList<>();
        for (int level = LayeredCauldronBlock.MIN_FILL_LEVEL; level <= LayeredCauldronBlock.MAX_FILL_LEVEL; level++) {
            states.add(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level));
        }
        for (int level = LayeredCauldronBlock.MIN_FILL_LEVEL; level <= LayeredCauldronBlock.MAX_FILL_LEVEL; level++) {
            states.add(Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, level));
        }
        states.add(Blocks.LAVA_CAULDRON.defaultBlockState());
        states.add(Blocks.CAULDRON.defaultBlockState());
        return states;
    }

    private static void buildFill(ServerLevel level, Rig rig) {
        List<BlockState> states = fillStates();
        int rz = rig.z + ROW_SPACING * FILL_ROW;
        for (int dx = -CLEAR_BEFORE; dx <= 2 * states.size() + 1; dx++) {
            for (int dz = -CLEAR_HALF; dz <= CLEAR_HALF; dz++) {
                for (int dy = -1; dy < CLEAR_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(rig.x + dx, rig.y + dy, rz + dz);
                    rig.original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                    level.setBlockAndUpdate(pos, dy == -1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int i = 0; i < states.size(); i++) {
            level.setBlockAndUpdate(rig.fill(i), states.get(i));
        }
    }

    private static @Nullable String checkFill(Level level, Entity probe, BlockPos pos, BlockState state, StringBuilder seen) {
        CauldronContent content = DroneFluids.content(state);
        Fluid fluid = content == null ? Fluid.NONE : content.fluid();
        int fill = DroneFluids.fillLevel(state);
        double top = content == null ? FLOOR : content.top(fill);
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        String name = state.getBlock().getName().getString();
        String label = state.hasProperty(LayeredCauldronBlock.LEVEL)
                ? String.format(Locale.ROOT, "%s Stufe %d (Füllhöhe %.0f px)", name, fill, top * 16.0)
                : String.format(Locale.ROOT, "%s (Füllhöhe %.0f px)", name, top * 16.0);
        if (content != null) {
            List<AABB> shape = state.getEntityInsideCollisionShape(level, pos, probe).toAabbs();
            if (!contains(shape, 0.5, top - PROBE, 0.5) || contains(shape, 0.5, top + PROBE, 0.5)) {
                return label + ": Füllhöhe passt nicht zur Vanilla-Form des Inhalts " + shape;
            }
        }
        Fluid floor = DroneFluids.at(level, cx, pos.getY() + FLOOR + PROBE, cz);
        Fluid below = DroneFluids.at(level, cx, pos.getY() + top - PROBE, cz);
        Fluid above = DroneFluids.at(level, cx, pos.getY() + top + PROBE, cz);
        Fluid wall = DroneFluids.at(level, pos.getX() + PROBE, pos.getY() + top - PROBE, cz);
        if (floor != fluid || below != fluid || above != Fluid.NONE || wall != Fluid.NONE) {
            return String.format(Locale.ROOT, "%s: am Boden %s, 1 cm unter der Füllhöhe %s, 1 cm darüber %s, in der Wand %s "
                    + "(erwartet %s, %s, NONE, NONE)", label, floor, below, above, wall, fluid, fluid);
        }
        String snag = checkSnag(level, pos, state, content, top, label);
        if (snag != null) {
            return snag;
        }
        double inside = WaterSurface.relative(level, cx, pos.getY() + SURFACE_PROBE, cz);
        double over = WaterSurface.relative(level, cx, pos.getY() + 1.0 + SURFACE_PROBE, cz);
        if (fluid == Fluid.WATER) {
            if (Math.abs(inside - (top - SURFACE_PROBE)) > 1.0E-9 || Math.abs(over - (top - 1.0 - SURFACE_PROBE)) > 1.0E-9) {
                return String.format(Locale.ROOT, "%s: Wasseroberfläche %.4f bzw. %.4f, erwartet %.4f bzw. %.4f", label, inside, over,
                        top - SURFACE_PROBE, top - 1.0 - SURFACE_PROBE);
            }
        } else if (inside != Double.NEGATIVE_INFINITY || over != Double.NEGATIVE_INFINITY) {
            return String.format(Locale.ROOT, "%s: Wasseroberfläche %.4f bzw. %.4f ohne Wasser", label, inside, over);
        }
        if (!seen.isEmpty()) {
            seen.append(", ");
        }
        seen.append(label).append(" → ").append(fluid);
        if (content != null && content.snag() != null) {
            seen.append(", bremst");
        }
        return null;
    }

    private static @Nullable String checkSnag(Level level, BlockPos pos, BlockState state, @Nullable CauldronContent content, double top,
                                              String label) {
        SnagMaterial expected = content == null ? null : content.snag();
        if (SnagBlocks.material(state) != expected) {
            return label + ": Bremse " + SnagBlocks.material(state) + " statt " + expected;
        }
        if (expected == null) {
            return null;
        }
        List<AABB> shape = SnagBlocks.shape(level, pos, state).toAabbs();
        boolean floor = contains(shape, 0.5, FLOOR + PROBE, 0.5);
        boolean below = contains(shape, 0.5, top - PROBE, 0.5);
        boolean above = contains(shape, 0.5, top + PROBE, 0.5);
        boolean wall = contains(shape, PROBE, top - PROBE, 0.5);
        if (!floor || !below || above || wall) {
            return String.format(Locale.ROOT, "%s: Bremse am Boden %s, 1 cm unter der Füllhöhe %s, 1 cm darüber %s, in der Wand %s "
                    + "(erwartet ja, ja, nein, nein)", label, floor, below, above, wall);
        }
        if (SnagBlocks.look(state).getBlock() != Blocks.POWDER_SNOW) {
            return label + ": Bröckchen und Klang nicht vom Pulverschnee, sondern von " + SnagBlocks.look(state);
        }
        return null;
    }

    private static boolean contains(List<AABB> boxes, double x, double y, double z) {
        for (AABB box : boxes) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    static void build(ServerLevel level, Rig rig, Kind kind) {
        int rz = rig.rowZ(kind);
        BlockPos cauldron = rig.cauldron(kind);
        BlockPos reference = rig.reference(kind);
        for (int dx = -CLEAR_BEFORE; dx <= CLEAR_AFTER; dx++) {
            for (int dz = -CLEAR_HALF; dz <= CLEAR_HALF; dz++) {
                for (int dy = -1; dy < CLEAR_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(rig.x + dx, rig.y + dy, rz + dz);
                    rig.original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                    BlockState block;
                    if (dy == -1) {
                        block = Blocks.STONE.defaultBlockState();
                    } else if (pos.equals(cauldron)) {
                        block = kind.cauldron();
                    } else if (pos.equals(reference)) {
                        block = kind.reference();
                    } else if (dy == 0 && Math.abs(pos.getX() - reference.getX()) <= 1 && Math.abs(dz) <= 1) {
                        block = Blocks.STONE.defaultBlockState();
                    } else {
                        block = Blocks.AIR.defaultBlockState();
                    }
                    level.setBlockAndUpdate(pos, block);
                }
            }
        }
        rig.built.put(kind, true);
    }

    static void spawn(ServerPlayer player, Rig rig) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x, feet.y, feet.z + 3.0, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.WHOOP);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        rig.droneId = drone.getId();
    }

    static void fly(ServerPlayer player, QuadcopterEntity drone, Rig rig) {
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (rig.head == null) {
            rig.head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (rig.hand == null) {
            rig.hand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(SCENARIO, ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = rig.droneId >= 0 ? level.getEntity(rig.droneId) : null;
                    if (entity instanceof QuadcopterEntity drone) {
                        DronePiloting.release(drone);
                        drone.discard();
                    }
                }
                ServerPlayer player = player(server);
                if (rig.head != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, rig.head);
                }
                if (rig.hand != null) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, rig.hand);
                }
                ServerLevel level = player.level();
                for (Map.Entry<BlockPos, BlockState> entry : rig.original.entrySet()) {
                    level.setBlockAndUpdate(entry.getKey(), entry.getValue());
                }
                RIG.compareAndSet(rig, null);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(SCENARIO);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > SYNC_TICKS ? StepResult.fail("Sitzung läuft nach dem Aufräumen weiter")
                : StepResult.pending()) : StepResult.pass();
    }

    private static void input(boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), arm, StabMode.ANGLE);
    }

    private static boolean placed(QuadcopterEntity drone, Rig rig, Scenario s) {
        Vec3 start = rig.start(s.kind, s.target);
        double floor = rig.feet(s.kind, s.target).y;
        return Math.abs(drone.getX() - start.x) < PLACED && Math.abs(drone.getZ() - start.z) < PLACED && drone.getY() > floor - PLACED
                && drone.getY() < start.y + PLACED;
    }

    static boolean near(QuadcopterEntity drone, Vec3 target) {
        return Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED
                && Math.abs(drone.getY() - target.y) < PLACED;
    }

    private static String name(Scenario s) {
        return s.target == Target.CAULDRON ? s.kind.cauldronName : s.kind.referenceName;
    }

    static String where(QuadcopterEntity drone, DroneSim sim) {
        Telemetry t = sim.telemetry();
        return String.format(Locale.ROOT, "bei %.2f %.2f %.2f, %.2f m/s, am Boden %s, ruhend %s, scharf %s, Sperren %d", drone.getX(), drone.getY(),
                drone.getZ(), t.speed, t.grounded, t.sleeping, t.armed, t.armBlockers);
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static QuadcopterEntity serverDrone(MinecraftServer server, int id) {
        if (!(player(server).level().getEntity(id) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne auf dem Server verschwunden");
        }
        return drone;
    }
}
