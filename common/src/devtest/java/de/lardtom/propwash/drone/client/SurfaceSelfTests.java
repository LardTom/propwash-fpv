package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.SurfaceMaterials;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.SimSnapshot;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class SurfaceSelfTests {
    static final String SLIME = "drone: Slime";
    static final String WOOL = "drone: Wolle";
    static final String HONEY = "drone: Honig";
    private static final int FIRST_DZ = 10;
    private static final int SPACING = 8;
    private static final int CLEAR = 3;
    private static final int CLEAR_HEIGHT = 15;
    private static final int PAD = 2;
    private static final int WALL_DX = 3;
    private static final int WALL_HALF = 2;
    private static final int WALL_HEIGHT = 5;
    private static final double START_DX = 0.5;
    private static final double HOVER = 2.0;
    private static final double DROP = 10.0;
    private static final double SLIME_APEX = 3.0;
    private static final double HAY_APEX = 0.6;
    private static final double TAKEOFF = 2.0;
    private static final double SUNK = 0.003;
    private static final double SUNK_REST = 0.001;
    private static final double SLIME_WALL_SPEED = 10.0;
    private static final double WOOL_WALL_SPEED = 15.0;
    private static final double BOUNCED = -3.0;
    private static final double CAUGHT = 3.0;
    private static final double PLACED = 0.3;
    private static final double CLIMB_THROTTLE = 0.25;
    private static final double HONEY_WALL_SPEED = 6.0;
    private static final double REBOUND = -0.3;
    private static final double HONEY_APEX = 0.1;
    private static final double SLIDE_FASTEST = -1.0;
    private static final double SLIDE_SLOWEST = -0.15;
    private static final double SLIDE_STONE = -3.0;
    private static final double LIFTED = 0.05;
    private static final double HONEY_CLIMB_THROTTLE = 0.05;
    private static final int SLIDE_SETTLE_TICKS = 10;
    private static final int SLIDE_TICKS = 30;
    private static final int SHOT_TICKS = 6;
    private static final int CLEAR_TICKS = 3;
    private static final int STUCK_TICKS = 12;
    private static final int RELEASE_TICKS = 40;
    private static final int HONEY_CLIMB_TICKS = 70;
    private static final String SHOT_WALL = "surface_honey_wall";
    private static final String SHOT_TAKEOFF = "surface_honey_takeoff";
    private static final int CEILING_DY = 5;
    private static final double CEILING_START = 2.8;
    private static final double CEILING_SPEED = 8.0;
    private static final double NEAR_FLOOR = 1.0;
    private static final double FLOOR_CONTACT = 0.05;
    private static final int BOUNCE_TICKS = 5;
    private static final int FLOOR_TICKS = 80;
    private static final int SYNC_TICKS = 80;
    private static final int ARM_TICKS = 60;
    private static final int CONTACT_TICKS = 60;
    private static final int AFTER_CONTACT_TICKS = 10;
    private static final int WEAR_TICKS = 20;
    private static final int APEX_TICKS = 160;
    private static final int CLIMB_TICKS = 40;
    private static final int SETTLE_TICKS = 10;
    private static final int WATCH_TICKS = 20;
    private static final int REST_TICKS = 80;
    private static final String SCENARIO = "surface";
    private static final Quaternionf FACING_WALL = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final AtomicReference<Rig> RIG = new AtomicReference<>();

    private SurfaceSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(SLIME + " – Fall aus 10 m federt zurück, ohne Absturz und Verschleiß", 700, ctx -> step(ctx, Kind.SLIME_DROP));
        SelfTest.clientStep(SLIME + " – seitlich gegen eine Slime-Wand prallt die Drohne ab", 600, ctx -> step(ctx, Kind.SLIME_WALL));
        SelfTest.clientStep(SLIME + " – von unten gegen eine Slime-Decke mit 8 m/s: kein Treffer, kein Verschleiß bis zum Boden", 600,
                ctx -> step(ctx, Kind.SLIME_CEILING));
        SelfTest.clientStep(SLIME + " – Testfeld abbauen", 200, SurfaceSelfTests::cleanupStep);
        SelfTest.clientStep(WOOL + " – Wand mit 15 m/s fängt ohne Absturz ab, Props bleiben heil", 600, ctx -> step(ctx, Kind.WOOL_WALL));
        SelfTest.clientStep(WOOL + " – Heuballen fängt einen Fall aus 10 m weich ab", 700, ctx -> step(ctx, Kind.HAY_DROP));
        SelfTest.clientStep(WOOL + " – 3 mm in der Wolle bleibt die Drohne am Boden und kommt zur Ruhe", 400, ctx -> step(ctx, Kind.WOOL_SUNK));
        SelfTest.clientStep(WOOL + " – Start von Wolle", 600, ctx -> step(ctx, Kind.WOOL_TAKEOFF));
        SelfTest.clientStep(WOOL + " – Testfeld abbauen", 200, SurfaceSelfTests::cleanupStep);
        SelfTest.clientStep(HONEY + " – gegen eine Honigwand mit 6 m/s: kein Abprall, kein Absturz, danach rutscht die Drohne langsam herunter "
                + "(Bildschirmfoto)", 700, ctx -> step(ctx, Kind.HONEY_WALL));
        SelfTest.clientStep(HONEY + " – Fall aus 10 m auf Honig: kein Abprall, kein Absturz, kein Verschleiß", 700, ctx -> step(ctx, Kind.HONEY_DROP));
        SelfTest.clientStep(HONEY + " – Start von Honig: klebt etwa 1 s, dann steigt die Drohne (Bildschirmfoto)", 700,
                ctx -> step(ctx, Kind.HONEY_TAKEOFF));
        SelfTest.clientStep(HONEY + " – Testfeld abbauen", 200, SurfaceSelfTests::cleanupStep);
        CauldronSelfTests.register();
    }

    private enum Kind {
        SLIME_DROP(0, Blocks.SLIME_BLOCK, false),
        SLIME_WALL(1, Blocks.SLIME_BLOCK, true),
        SLIME_CEILING(3, Blocks.SLIME_BLOCK, false),
        HAY_DROP(0, Blocks.HAY_BLOCK, false),
        WOOL_WALL(1, Blocks.WOOL.pick(DyeColor.WHITE), true),
        WOOL_TAKEOFF(2, Blocks.WOOL.pick(DyeColor.LIME), false),
        WOOL_SUNK(2, Blocks.WOOL.pick(DyeColor.LIME), false),
        HONEY_WALL(0, Blocks.HONEY_BLOCK, true),
        HONEY_DROP(1, Blocks.HONEY_BLOCK, false),
        HONEY_TAKEOFF(1, Blocks.HONEY_BLOCK, false);

        final int slot;
        final Block block;
        final boolean wall;

        Kind(int slot, Block block, boolean wall) {
            this.slot = slot;
            this.block = block;
            this.wall = wall;
        }
    }

    private static final class Rig {
        final int x;
        final int y;
        final int z;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        final Map<Integer, Block> built = new LinkedHashMap<>();
        int droneId = -1;
        @Nullable ItemStack head;
        @Nullable ItemStack hand;

        Rig(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        int centerZ(int slot) {
            return this.z + FIRST_DZ + SPACING * slot;
        }

        Vec3 ground(Kind kind) {
            if (kind.wall) {
                return new Vec3(this.x + START_DX, this.y, this.centerZ(kind.slot) + 0.5);
            }
            return new Vec3(this.x + 0.5, this.y + 1.0, this.centerZ(kind.slot) + 0.5);
        }

        Vec3 start(Kind kind) {
            Vec3 ground = this.ground(kind);
            return kind == Kind.WOOL_SUNK ? ground.add(0.0, -SUNK, 0.0) : ground;
        }

        BlockPos probe(Kind kind) {
            if (kind == Kind.SLIME_CEILING) {
                return new BlockPos(this.x, this.y + CEILING_DY, this.centerZ(kind.slot));
            }
            return new BlockPos(kind.wall ? this.x + WALL_DX : this.x, this.y, this.centerZ(kind.slot));
        }

        double wallFace(Kind kind) {
            return this.x + WALL_DX;
        }

        double ceiling() {
            return this.y + CEILING_DY;
        }
    }

    private static final class Scenario {
        final Kind kind;
        int stage;
        int mark;
        @Nullable CompletableFuture<?> pending;
        int crashes;
        @Nullable PartWear wear;
        double top;
        double last = Double.NaN;
        double fastest;
        boolean touched;
        double apex = Double.NEGATIVE_INFINITY;
        int touchedAt;
        double after;
        int airborne;
        double lowest = Double.POSITIVE_INFINITY;
        int impacts;
        double approach;
        double gap = Double.POSITIVE_INFINITY;
        boolean below;
        int ceilingImpacts;
        double ceilingImpactSpeed;
        int floorImpacts;
        double floorImpactSpeed;
        boolean crashedAbove;
        boolean wornAbove;
        boolean disarmedAbove;
        boolean floor;
        int floorAt;
        boolean wornInWindow;
        double slideStart;
        double slideFastest = Double.POSITIVE_INFINITY;
        double slideSlowest = Double.NEGATIVE_INFINITY;
        int slideTicks;
        int stickyTicks;
        int liftedAt = -1;
        @Nullable CompletableFuture<String> shot;
        @Nullable String shotSize;

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
                input(0.0F, false);
                return StepResult.fail("Server-Aktion in Stufe " + s.stage + ": " + s.pending.exceptionNow().getMessage());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        StepResult result = run(ctx, s);
        if (result.status() != StepResult.Status.PENDING) {
            input(0.0F, false);
        }
        return result;
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        int waited = ctx.tick() - s.mark;
        PilotSession session = PilotSession.INSTANCE;
        Rig rig = RIG.get();
        if (s.stage == 0) {
            input(0.0F, false);
            s.pending = ctx.onServer(server -> prepare(server, s.kind));
            s.enter(4, ctx.tick());
            return StepResult.pending();
        }
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (rig == null) {
            return StepResult.fail("kein Testfeld");
        }
        if (drone == null || sim == null || drone.getId() != rig.droneId || !session.isFpv()) {
            return waited > SYNC_TICKS ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        Vec3 ground = rig.ground(s.kind);
        switch (s.stage) {
            case 4 -> {
                input(0.0F, false);
                BlockPos probe = rig.probe(s.kind);
                if (!drone.level().getBlockState(probe).is(s.kind.block)) {
                    return waited > SYNC_TICKS ? StepResult.fail("Testfeld aus " + name(s.kind.block) + " kommt beim Client nicht an: "
                            + probe.toShortString()) : StepResult.pending();
                }
                Vec3 start = rig.start(s.kind);
                s.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(start, FACING_WALL));
                s.enter(1, ctx.tick());
                return StepResult.pending();
            }
            case 1 -> {
                input(0.0F, false);
                if (!near(drone, rig.start(s.kind)) || !sim.wear().isNew() || t.armed) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne nicht aufs Testfeld gesetzt: " + where(drone, sim, ground.y))
                            : StepResult.pending();
                }
                if (s.kind == Kind.WOOL_SUNK) {
                    s.enter(40, ctx.tick());
                } else if (s.kind.wall || s.kind == Kind.WOOL_TAKEOFF || s.kind == Kind.HONEY_TAKEOFF || s.kind == Kind.SLIME_CEILING) {
                    s.enter(2, ctx.tick());
                } else {
                    Vec3 up = ground.add(0.0, DROP, 0.0);
                    s.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(up, FACING_WALL));
                    s.enter(10, ctx.tick());
                }
                return StepResult.pending();
            }
            case 2 -> {
                boolean grounded = t.grounded || t.sleeping;
                input(0.0F, t.armed || grounded && waited >= 3);
                if (!t.armed) {
                    return waited > ARM_TICKS ? StepResult.fail("nicht scharf geschaltet, Sperren=" + t.armBlockers + ", "
                            + where(drone, sim, ground.y)) : StepResult.pending();
                }
                if (s.kind == Kind.WOOL_TAKEOFF) {
                    s.top = drone.getY();
                    s.crashes = session.crashCount();
                    s.enter(30, ctx.tick());
                } else if (s.kind == Kind.HONEY_TAKEOFF) {
                    s.top = drone.getY();
                    s.crashes = session.crashCount();
                    s.wear = sim.wear();
                    s.enter(60, ctx.tick());
                } else {
                    Vec3 air = ground.add(0.0, hover(s.kind), 0.0);
                    s.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(air, FACING_WALL));
                    s.enter(20, ctx.tick());
                }
                return StepResult.pending();
            }
            case 10 -> {
                input(0.0F, false);
                if (!near(drone, ground.add(0.0, DROP, 0.0))) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne nicht 10 m über das Testfeld gesetzt: " + where(drone, sim, ground.y))
                            : StepResult.pending();
                }
                s.crashes = session.crashCount();
                s.wear = sim.wear();
                s.top = ground.y;
                s.last = drone.getY();
                s.enter(11, ctx.tick());
                return StepResult.pending();
            }
            case 11 -> {
                input(0.0F, false);
                return drop(ctx, s, drone, sim, waited);
            }
            case 20 -> {
                input((float) t.hoverThrottleStick, true);
                if (!near(drone, ground.add(0.0, hover(s.kind), 0.0))) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne nicht vor der Wand in die Luft gesetzt: " + where(drone, sim, ground.y))
                            : StepResult.pending();
                }
                if (!t.armed) {
                    return StepResult.fail("beim Versetzen entschärft: " + t.lastDisarmReason);
                }
                s.crashes = session.crashCount();
                s.wear = sim.wear();
                s.impacts = t.impactCount;
                if (s.kind == Kind.SLIME_CEILING) {
                    push(sim, 0.0, CEILING_SPEED);
                    s.enter(50, ctx.tick());
                } else {
                    push(sim, wallSpeed(s.kind), 0.0);
                    s.enter(21, ctx.tick());
                }
                return StepResult.pending();
            }
            case 21 -> {
                input((float) t.hoverThrottleStick, true);
                return wall(ctx, s, rig, drone, sim, waited);
            }
            case 22 -> {
                input(0.0F, false);
                return slide(ctx, s, drone, sim, rig);
            }
            case 60 -> {
                input((float) Math.min(1.0, t.hoverThrottleStick + HONEY_CLIMB_THROTTLE), true);
                return honeyTakeoff(ctx, s, drone, sim, waited);
            }
            case 30 -> {
                input((float) Math.min(1.0, t.hoverThrottleStick + CLIMB_THROTTLE), true);
                if (waited < CLIMB_TICKS) {
                    return StepResult.pending();
                }
                double height = drone.getY() - s.top;
                if (height <= TAKEOFF) {
                    return StepResult.fail(String.format(Locale.ROOT, "nach 2 s nur %.2f m über der Wolle (%s)", height,
                            where(drone, sim, s.top)));
                }
                if (session.crashCount() != s.crashes) {
                    return StepResult.fail("Absturz beim Start von der Wolle");
                }
                ctx.log(String.format(Locale.ROOT, "von %s gestartet, nach 2 s %.2f m über der Wolle", name(s.kind.block), height));
                return StepResult.pass();
            }
            case 40 -> {
                input(0.0F, false);
                return sunk(ctx, s, drone, sim, ground.y, waited);
            }
            case 50 -> {
                input((float) t.hoverThrottleStick, true);
                return ceiling(ctx, s, rig, drone, sim, ground.y, waited);
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + s.stage);
            }
        }
    }

    private static StepResult drop(SelfTest.StepContext ctx, Scenario s, QuadcopterEntity drone, DroneSim sim, int waited) {
        double y = drone.getY();
        double height = y - s.top;
        double speed = Double.isFinite(s.last) ? (s.last - y) * 20.0 : 0.0;
        s.last = y;
        if (!s.touched) {
            s.fastest = Math.max(s.fastest, speed);
            if (speed < -0.05 && s.fastest > 1.0) {
                s.touched = true;
                s.touchedAt = ctx.tick();
            } else if (waited > APEX_TICKS) {
                return StepResult.fail("Drohne kommt nicht wieder hoch oder fällt nicht: " + where(drone, sim, s.top));
            }
            if ((s.kind == Kind.HAY_DROP || s.kind == Kind.HONEY_DROP) && height < 0.05 && sim.telemetry().grounded && s.fastest > 1.0) {
                s.touched = true;
                s.touchedAt = ctx.tick();
            }
            return StepResult.pending();
        }
        s.apex = Math.max(s.apex, height);
        int since = ctx.tick() - s.touchedAt;
        boolean falling = height < s.apex - 0.05;
        boolean resting = since > WEAR_TICKS && (sim.telemetry().grounded || sim.telemetry().sleeping) && height < 0.05;
        if (!(falling && since > 2) && !resting) {
            return since > APEX_TICKS ? StepResult.fail("kein Gipfel nach dem Aufprall: " + where(drone, sim, s.top)) : StepResult.pending();
        }
        if (since < WEAR_TICKS) {
            return StepResult.pending();
        }
        PilotSession session = PilotSession.INSTANCE;
        if (session.crashCount() != s.crashes) {
            return StepResult.fail(String.format(Locale.ROOT, "Absturz auf %s erkannt (Fallgeschwindigkeit %.1f m/s)", name(s.kind.block),
                    s.fastest));
        }
        if (!sim.wear().equals(s.wear) || !drone.wear().equals(s.wear)) {
            return StepResult.fail("Verschleiß nach dem Fall auf " + name(s.kind.block) + " verändert");
        }
        if (s.kind == Kind.SLIME_DROP && s.apex <= SLIME_APEX) {
            return StepResult.fail(String.format(Locale.ROOT, "zweiter Gipfel nur %.2f m über dem Slime (gefordert > %.1f m, Fall %.1f m/s)",
                    s.apex, SLIME_APEX, s.fastest));
        }
        if (s.kind == Kind.HAY_DROP && s.apex >= HAY_APEX) {
            return StepResult.fail(String.format(Locale.ROOT, "Heuballen federt %.2f m hoch (erlaubt < %.1f m)", s.apex, HAY_APEX));
        }
        if (s.kind == Kind.HONEY_DROP && s.apex >= HONEY_APEX) {
            return StepResult.fail(String.format(Locale.ROOT, "Honig federt %.2f m hoch (erlaubt < %.1f m)", s.apex, HONEY_APEX));
        }
        ctx.log(String.format(Locale.ROOT, "Fall aus %.0f m auf %s mit %.1f m/s, zweiter Gipfel %.2f m, Abstürze 0, Verschleiß unverändert",
                DROP, name(s.kind.block), s.fastest, Math.max(0.0, s.apex)));
        return StepResult.pass();
    }

    private static StepResult sunk(SelfTest.StepContext ctx, Scenario s, QuadcopterEntity drone, DroneSim sim, double top, int waited) {
        Telemetry t = sim.telemetry();
        if (waited <= SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (!t.grounded && !t.sleeping) {
            s.airborne++;
        }
        double depth = top - drone.getY();
        String state = String.format(Locale.ROOT, "%.4f m in der Wolle, %d von %d Ticks nicht am Boden, ruhend %s", depth, s.airborne,
                waited - SETTLE_TICKS, t.sleeping);
        if (!t.sleeping || waited < SETTLE_TICKS + WATCH_TICKS) {
            return waited > REST_TICKS ? StepResult.fail("kommt in der Wolle nicht zur Ruhe: " + state) : StepResult.pending();
        }
        if (s.airborne > 0) {
            return StepResult.fail("in der Wolle zeitweise nicht am Boden: " + state);
        }
        if (Math.abs(depth) > SUNK_REST) {
            return StepResult.fail("ruht nicht an der Oberkante der Wolle: " + state);
        }
        ctx.log("auf " + name(s.kind.block) + " zur Ruhe gekommen: " + state);
        return StepResult.pass();
    }

    private static StepResult wall(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, DroneSim sim, int waited) {
        double vx = velocityX(sim);
        double front = drone.getX() + drone.getBbWidth() / 2.0;
        s.lowest = Math.min(s.lowest, vx);
        if (!s.touched) {
            if (vx < 0.0 || front >= rig.wallFace(s.kind) - 0.02) {
                s.touched = true;
                s.touchedAt = ctx.tick();
                return StepResult.pending();
            }
            return waited > CONTACT_TICKS ? StepResult.fail("Wand nicht erreicht: " + where(drone, sim, rig.y)) : StepResult.pending();
        }
        int since = ctx.tick() - s.touchedAt;
        if (since == AFTER_CONTACT_TICKS) {
            s.after = vx;
        }
        if (s.kind == Kind.HONEY_WALL) {
            return since < AFTER_CONTACT_TICKS ? StepResult.pending() : honeyWall(ctx, s, drone, sim, rig);
        }
        if (since < WEAR_TICKS) {
            return StepResult.pending();
        }
        PilotSession session = PilotSession.INSTANCE;
        String surface = name(s.kind.block);
        if (session.crashCount() != s.crashes) {
            return StepResult.fail("Absturz an der Wand aus " + surface);
        }
        if (!sim.telemetry().armed) {
            return StepResult.fail("an der Wand aus " + surface + " entschärft: " + sim.telemetry().lastDisarmReason);
        }
        if (!sim.wear().equals(s.wear) || !drone.wear().equals(s.wear)) {
            return StepResult.fail("Verschleiß an der Wand aus " + surface + " verändert (Props)");
        }
        if (s.kind == Kind.SLIME_WALL && s.after > BOUNCED) {
            return StepResult.fail(String.format(Locale.ROOT, "kein Abprall von der Slime-Wand: vx %.2f m/s", s.after));
        }
        if (s.kind == Kind.WOOL_WALL && Math.abs(s.after) > CAUGHT) {
            return StepResult.fail(String.format(Locale.ROOT, "Wolle fängt nicht ab: vx %.2f m/s", s.after));
        }
        ctx.log(String.format(Locale.ROOT, "gegen %s mit %.0f m/s, danach vx %.2f m/s, Abstürze 0, Props unverändert",
                surface, wallSpeed(s.kind), s.after));
        return StepResult.pass();
    }

    private static StepResult ceiling(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, DroneSim sim, double floorY, int waited) {
        Telemetry t = sim.telemetry();
        PilotSession session = PilotSession.INSTANCE;
        double vy = velocityY(sim);
        double height = drone.getY() - floorY;
        s.gap = Math.min(s.gap, rig.ceiling() - drone.getY() - drone.getBbHeight());
        s.below |= s.touched && height < NEAR_FLOOR;
        int fresh = t.impactCount - s.impacts;
        s.impacts = t.impactCount;
        if (fresh > 0 && s.below) {
            s.floorImpacts += fresh;
            s.floorImpactSpeed = Math.max(s.floorImpactSpeed, t.lastImpactSpeed);
        } else if (fresh > 0) {
            s.ceilingImpacts += fresh;
            s.ceilingImpactSpeed = Math.max(s.ceilingImpactSpeed, t.lastImpactSpeed);
        }
        boolean worn = !sim.wear().equals(s.wear) || !drone.wear().equals(s.wear);
        boolean crashed = session.crashCount() != s.crashes;
        if (!s.touched) {
            s.fastest = Math.max(s.fastest, vy);
            if (vy < 0.0 && s.fastest > 1.0) {
                s.touched = true;
                s.touchedAt = ctx.tick();
            } else {
                s.approach = vy;
                return waited > CONTACT_TICKS ? StepResult.fail("Slime-Decke nicht erreicht: " + where(drone, sim, floorY)) : StepResult.pending();
            }
        }
        int since = ctx.tick() - s.touchedAt;
        if (since == BOUNCE_TICKS) {
            s.after = vy;
        }
        if (since == WEAR_TICKS) {
            s.wornInWindow = worn;
        }
        if (!s.floor && (s.below && (height < FLOOR_CONTACT || t.grounded) || s.floorImpacts > 0)) {
            s.floor = true;
            s.floorAt = ctx.tick();
        }
        if (!s.floor) {
            s.crashedAbove = crashed;
            s.wornAbove = worn;
            s.disarmedAbove |= !t.armed;
        }
        boolean done = s.floor ? ctx.tick() - s.floorAt >= WEAR_TICKS && since >= WEAR_TICKS : since >= FLOOR_TICKS;
        if (!done) {
            return StepResult.pending();
        }
        String floor = s.floor ? String.format(Locale.ROOT, "Steinboden 4 m tiefer nach %d Ticks: %d Treffer mit bis zu %.1f m/s, danach Verschleiß %s, "
                        + "Absturz %s; 20 Ticks nach der Decke Verschleiß %s", s.floorAt - s.touchedAt, s.floorImpacts, s.floorImpactSpeed,
                worn ? "ja" : "nein", crashed ? "ja" : "nein", s.wornInWindow ? "ja" : "nein") : "Steinboden nicht erreicht";
        String state = String.format(Locale.ROOT, "gegen die Slime-Decke mit %.1f m/s (Abstand zuletzt %.3f m), nach %d Ticks vy %.2f m/s", s.approach,
                Math.max(0.0, s.gap), BOUNCE_TICKS, s.after);
        if (s.crashedAbove) {
            return StepResult.fail("Absturz an der Slime-Decke: " + state + "; " + floor);
        }
        if (s.ceilingImpacts > 0) {
            return StepResult.fail(String.format(Locale.ROOT, "Treffer an der Slime-Decke gezählt: %d mit bis zu %.1f m/s; %s; %s", s.ceilingImpacts,
                    s.ceilingImpactSpeed, state, floor));
        }
        if (s.wornAbove) {
            return StepResult.fail("Verschleiß an der Slime-Decke verändert, bevor die Drohne den Boden erreicht: vorher " + s.wear + ", nachher "
                    + sim.wear() + "; " + state);
        }
        if (s.disarmedAbove) {
            return StepResult.fail("an der Slime-Decke entschärft: " + t.lastDisarmReason + "; " + state);
        }
        if (!(s.after <= BOUNCED)) {
            return StepResult.fail("Slime-Decke federt nicht nach unten zurück: " + state);
        }
        ctx.log(state + ", an der Decke 0 Treffer, kein Absturz, Verschleiß bis zum Boden unverändert; " + floor);
        return StepResult.pass();
    }

    private static StepResult honeyWall(SelfTest.StepContext ctx, Scenario s, QuadcopterEntity drone, DroneSim sim, Rig rig) {
        String state = String.format(Locale.ROOT, "kleinstes vx nach dem Treffer %.2f m/s, %s", s.lowest, where(drone, sim, rig.y));
        if (s.lowest < REBOUND) {
            return StepResult.fail("Drohne prallt von der Honigwand ab: " + state);
        }
        StepResult harm = unharmed(s, drone, sim, "an der Honigwand");
        if (harm != null) {
            return harm;
        }
        s.after = s.lowest;
        s.slideStart = drone.getY();
        s.enter(22, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult slide(SelfTest.StepContext ctx, Scenario s, QuadcopterEntity drone, DroneSim sim, Rig rig) {
        int since = ctx.tick() - s.mark;
        double vy = velocityY(sim);
        boolean wall = (sim.stickySides() & (MoveReport.stickyBit(0, false) | MoveReport.stickyBit(0, true))) != 0;
        if (since > SLIDE_SETTLE_TICKS && since <= SLIDE_SETTLE_TICKS + SLIDE_TICKS) {
            s.slideTicks++;
            s.stickyTicks += wall ? 1 : 0;
            s.slideFastest = Math.min(s.slideFastest, vy);
            s.slideSlowest = Math.max(s.slideSlowest, vy);
        }
        StepResult shot = shoot(s, since - SLIDE_SETTLE_TICKS - SHOT_TICKS, SHOT_WALL);
        if (shot != null) {
            return shot;
        }
        if (since <= SLIDE_SETTLE_TICKS + SLIDE_TICKS || s.shot != null && s.shotSize == null) {
            return StepResult.pending();
        }
        double fallen = s.slideStart - drone.getY();
        String state = String.format(Locale.ROOT, "vy beim Rutschen %.2f bis %.2f m/s, %.2f m in %.1f s, %d von %d Ticks an der Honigwand, %s",
                s.slideFastest, s.slideSlowest, fallen, (SLIDE_SETTLE_TICKS + SLIDE_TICKS) / 20.0, s.stickyTicks, s.slideTicks, where(drone, sim, rig.y));
        if (s.slideFastest < SLIDE_STONE) {
            return StepResult.fail("Drohne fällt an der Honigwand frei herunter: " + state);
        }
        if (s.slideFastest < SLIDE_FASTEST || s.slideSlowest > SLIDE_SLOWEST) {
            return StepResult.fail("Drohne rutscht nicht langsam an der Honigwand herunter: " + state);
        }
        if (s.stickyTicks * 10 < s.slideTicks * 9) {
            return StepResult.fail("Drohne löst sich beim Rutschen von der Honigwand: " + state);
        }
        StepResult harm = unharmed(s, drone, sim, "beim Rutschen an der Honigwand");
        if (harm != null) {
            return harm;
        }
        ctx.log(String.format(Locale.ROOT, "gegen eine Honigwand mit %.0f m/s, kleinstes vx danach %.2f m/s; %s; Abstürze 0, Verschleiß unverändert, "
                + "Bildschirmfoto %s (%s)", HONEY_WALL_SPEED, s.after, state, shotPath(SHOT_WALL), s.shotSize));
        return StepResult.pass();
    }

    private static StepResult honeyTakeoff(SelfTest.StepContext ctx, Scenario s, QuadcopterEntity drone, DroneSim sim, int waited) {
        double height = drone.getY() - s.top;
        if (s.liftedAt < 0 && height > LIFTED) {
            s.liftedAt = waited;
        }
        if (waited == 1 && (sim.stickySides() & MoveReport.STICKY_FLOOR) == 0) {
            return StepResult.fail("Honigboden unter der Drohne nicht erkannt: " + where(drone, sim, s.top));
        }
        StepResult shot = shoot(s, waited - SHOT_TICKS, SHOT_TAKEOFF);
        if (shot != null) {
            return shot;
        }
        if (s.liftedAt >= 0 && s.liftedAt < STUCK_TICKS) {
            return StepResult.fail(String.format(Locale.ROOT, "Drohne löst sich schon nach %d Ticks vom Honig (gefordert mindestens %d): %s", s.liftedAt,
                    STUCK_TICKS, where(drone, sim, s.top)));
        }
        if (s.liftedAt < 0 && waited > RELEASE_TICKS) {
            return StepResult.fail(String.format(Locale.ROOT, "Drohne klebt nach %d Ticks noch am Honig: %s", waited, where(drone, sim, s.top)));
        }
        if (waited < HONEY_CLIMB_TICKS || s.shot != null && s.shotSize == null) {
            return StepResult.pending();
        }
        if (height <= TAKEOFF / 4.0) {
            return StepResult.fail(String.format(Locale.ROOT, "nach %.1f s nur %.2f m über dem Honig (%s)", HONEY_CLIMB_TICKS / 20.0, height,
                    where(drone, sim, s.top)));
        }
        StepResult harm = unharmed(s, drone, sim, "beim Start von Honig");
        if (harm != null) {
            return harm;
        }
        ctx.log(String.format(Locale.ROOT, "von %s mit Gas Schwebe + %.2f gestartet: klebt %d Ticks (%.2f s), nach %.1f s %.2f m über dem Honig, "
                        + "Abstürze 0, Bildschirmfoto %s (%s)", name(s.kind.block), HONEY_CLIMB_THROTTLE, s.liftedAt, s.liftedAt / 20.0,
                HONEY_CLIMB_TICKS / 20.0, height, shotPath(SHOT_TAKEOFF), s.shotSize));
        return StepResult.pass();
    }

    private static @Nullable StepResult unharmed(Scenario s, QuadcopterEntity drone, DroneSim sim, String where) {
        if (PilotSession.INSTANCE.crashCount() != s.crashes) {
            return StepResult.fail("Absturz " + where);
        }
        if (s.wear != null && (!sim.wear().equals(s.wear) || !drone.wear().equals(s.wear))) {
            return StepResult.fail("Verschleiß " + where + " verändert");
        }
        return null;
    }

    private static @Nullable StepResult shoot(Scenario s, int fromShot, String name) {
        if (fromShot == -CLEAR_TICKS) {
            Screenshots.clearOverlays(Minecraft.getInstance());
        }
        if (fromShot == 0 && s.shot == null) {
            s.shot = Screenshots.capture(shotPath(name));
        }
        if (s.shot == null || !s.shot.isDone() || s.shotSize != null) {
            return null;
        }
        if (s.shot.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto " + name + ": " + s.shot.exceptionNow());
        }
        s.shotSize = s.shot.join();
        return null;
    }

    private static String shotPath(String name) {
        return Screenshots.enabled() ? name : "screenshots/" + name;
    }

    private static double hover(Kind kind) {
        return kind == Kind.SLIME_CEILING ? CEILING_START : HOVER;
    }

    private static double wallSpeed(Kind kind) {
        return switch (kind) {
            case SLIME_WALL -> SLIME_WALL_SPEED;
            case HONEY_WALL -> HONEY_WALL_SPEED;
            default -> WOOL_WALL_SPEED;
        };
    }

    private static void prepare(MinecraftServer server, Kind kind) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        if (rig == null) {
            rig = new Rig(player.getBlockX(), player.getBlockY(), player.getBlockZ());
            RIG.set(rig);
        }
        if (!(rig.droneId >= 0 && level.getEntity(rig.droneId) instanceof QuadcopterEntity)) {
            spawn(player, rig);
        }
        QuadcopterEntity current = serverDrone(server, rig.droneId);
        if (DronePiloting.pilotedBy(player.getUUID()).filter(piloted -> piloted == current).isEmpty()) {
            fly(player, current, rig);
        }
        Block built = rig.built.get(kind.slot);
        if (built != kind.block) {
            build(level, rig, kind);
        }
        serverDrone(server, rig.droneId).setWear(PartWear.PRISTINE);
    }

    private static void spawn(ServerPlayer player, Rig rig) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x, feet.y, feet.z + 3.0, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        rig.droneId = drone.getId();
    }

    private static void fly(ServerPlayer player, QuadcopterEntity drone, Rig rig) {
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

    private static void build(ServerLevel level, Rig rig, Kind kind) {
        int cz = rig.centerZ(kind.slot);
        for (int dx = -CLEAR; dx <= WALL_DX + 1; dx++) {
            for (int dz = -CLEAR; dz <= CLEAR; dz++) {
                BlockPos floor = new BlockPos(rig.x + dx, rig.y - 1, cz + dz);
                if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
                    throw new IllegalStateException("kein fester Boden unter dem Testfeld bei " + floor.toShortString());
                }
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(rig.x + dx, rig.y + dy, cz + dz);
                    boolean wall = kind.wall && dx == WALL_DX && Math.abs(dz) <= WALL_HALF && dy < WALL_HEIGHT;
                    boolean pad = !kind.wall && dy == 0 && Math.abs(dx) <= PAD && Math.abs(dz) <= PAD;
                    boolean ceiling = kind == Kind.SLIME_CEILING && dy == CEILING_DY && Math.abs(dx) <= PAD && Math.abs(dz) <= PAD;
                    rig.original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                    BlockState block = kind == Kind.SLIME_CEILING && pad ? Blocks.STONE.defaultBlockState()
                            : wall || pad || ceiling ? kind.block.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    level.setBlockAndUpdate(pos, block);
                }
            }
        }
        BlockState placed = kind.block.defaultBlockState();
        boolean tagged = placed.is(SurfaceMaterials.BOUNCY) || placed.is(SurfaceMaterials.CUSHION) || placed.is(SurfaceMaterials.STICKY);
        if (!tagged) {
            throw new IllegalStateException(name(kind.block) + " steht in keinem der Tags drone_bouncy, drone_cushion oder drone_sticky");
        }
        rig.built.put(kind.slot, kind.block);
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

    private static void input(float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private static void push(DroneSim sim, double vx, double vy) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = vy;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static double velocityX(DroneSim sim) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        return snapshot.vx;
    }

    private static double velocityY(DroneSim sim) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        return snapshot.vy;
    }

    private static boolean near(QuadcopterEntity drone, Vec3 target) {
        return Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED
                && Math.abs(drone.getY() - target.y) < PLACED;
    }

    private static String name(Block block) {
        return block.getName().getString();
    }

    private static String where(QuadcopterEntity drone, DroneSim sim, double floor) {
        Telemetry t = sim.telemetry();
        return String.format(Locale.ROOT, "%.2f m über dem Boden, %.2f m/s, am Boden %s, ruhend %s, scharf %s", drone.getY() - floor, t.speed,
                t.grounded, t.sleeping, t.armed);
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server, int id) {
        if (!(player(server).level().getEntity(id) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne auf dem Server verschwunden");
        }
        return drone;
    }
}
