package de.lardtom.propwash.barrier.client;

import de.lardtom.propwash.barrier.BarrierKind;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleGhost;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvEyeGuard;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.collision.CollisionRules;
import de.lardtom.propwash.drone.collision.ServerCollision;
import de.lardtom.propwash.drone.collision.client.ClientCollision;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.CrashDamage;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class BarrierFlightSelfTests {
    static final int FLIGHT_Z = 5;
    static final int WALL_X = 4;
    static final int WALL_HALF = 2;
    static final int WALL_HEIGHT = 5;
    static final double START_DX = 1.5;
    static final double HOVER = 2.0;
    private static final double PLACED = 0.3;
    private static final double OUT_MARGIN = 0.3;
    private static final double CONTACT = 0.08;
    private static final int ARM_TICKS = 200;
    private static final int RESET_TICKS = 50;
    private static final int PASS_TICKS = 120;
    private static final int SETTLE_TICKS = 10;
    private static final int SERVER_TICKS = 20;
    private static final int AFTER_CONTACT_TICKS = 20;
    private static final int DENIED_TICKS = 60;
    private static final String SCENARIO = "barrier_flight";
    private static final Quaternionf FACING_WALL = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<ItemStack> HEAD = new AtomicReference<>();
    private static final Map<Kind, Outcome> OUTCOMES = new EnumMap<>(Kind.class);

    private BarrierFlightSelfTests() {
    }

    enum Kind {
        THROUGH("Drohne fliegt mit 6 m/s durch 3 Blöcke Drohnen-durchlässige Barriere, Server nimmt an, keine Runde mit Hilfe", true, 3, 6.0),
        BOUNCE_STONE("Gegenprobe: Drohne prallt mit 4 m/s an Stein ab", false, 2, 4.0),
        BOUNCE_BARRIER("Drohne prallt mit 4 m/s an der Drohnen-Sperre ab wie an Stein", false, 2, 4.0),
        CRASH_STONE("Gegenprobe: Drohne stürzt mit 14 m/s an Stein ab", false, 2, 14.0),
        CRASH_BARRIER("Drohne stürzt mit 14 m/s an der Drohnen-Sperre ab wie an Stein", false, 2, 14.0),
        DENIED("Client fliegt per Geist ohne Recht durch die Sperre: Server setzt zurück", false, 3, 6.0);

        final String title;
        final boolean pass;
        final int thick;
        final double speed;

        Kind(String title, boolean pass, int thick, double speed) {
            this.title = title;
            this.pass = pass;
            this.thick = thick;
            this.speed = speed;
        }

        BlockState wall() {
            return switch (this) {
                case THROUGH -> BarrierSite.state(BarrierKind.DRONE_PASS);
                case BOUNCE_STONE, CRASH_STONE -> Blocks.STONE.defaultBlockState();
                default -> BarrierSite.state(BarrierKind.DRONE_BLOCK);
            };
        }

        @Nullable Kind reference() {
            return this == BOUNCE_BARRIER ? BOUNCE_STONE : this == CRASH_BARRIER ? CRASH_STONE : null;
        }
    }

    record Outcome(int crashes, double front, double reboundVx, double serverX, int resets) {
    }

    private static final class Scenario {
        final Kind kind;
        int stage;
        int mark;
        @Nullable CompletableFuture<?> pending;
        int crashes;
        int epoch;
        int denied;
        boolean touched;
        int touchedAt;
        boolean guardSeen;
        boolean assistSeen;
        boolean ghostSeen;
        double front = Double.NEGATIVE_INFINITY;
        double reboundVx = Double.NaN;
        double serverX = Double.NaN;
        String overlay = "";

        Scenario(Kind kind) {
            this.kind = kind;
        }

        void enter(int next, int tick) {
            this.stage = next;
            this.mark = tick;
        }
    }

    static void register() {
        for (Kind kind : Kind.values()) {
            SelfTest.clientStep(BarrierSelfTests.GROUP + kind.title, 900, ctx -> step(ctx, kind));
        }
    }

    private static StepResult step(SelfTest.StepContext ctx, Kind kind) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent(SCENARIO, key -> new Scenario(kind));
        if (s.pending != null) {
            if (s.pending.isCompletedExceptionally()) {
                finish();
                return StepResult.fail("Server-Aktion in Stufe " + s.stage + ": " + s.pending.exceptionNow().getMessage());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        if (kind == Kind.DENIED && !(JoinTestEnv.exampleAddon() && CollisionRules.rules().stream().anyMatch(r -> r.id().equals(ExampleGhost.GHOST)))) {
            ctx.log("ohne Beispiel-Add-on (Geist-Regel) übersprungen");
            return StepResult.pass();
        }
        StepResult result = run(ctx, s);
        if (result.status() != StepResult.Status.PENDING) {
            finish();
        }
        return result;
    }

    static void finish() {
        ExampleGhost.force(false);
        input(0.0F, false);
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        int waited = ctx.tick() - s.mark;
        PilotSession session = PilotSession.INSTANCE;
        if (s.stage == 0) {
            finish();
            Kind kind = s.kind;
            s.pending = ctx.onServer(server -> prepare(server, kind));
            s.enter(1, ctx.tick());
            return StepResult.pending();
        }
        BlockPos o = BarrierSite.originOrNull();
        if (o == null) {
            return StepResult.fail("kein Testplatz");
        }
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null || drone.getId() != DRONE.get() || !session.isFpv()) {
            return waited > BarrierSite.SYNC_TICKS ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        switch (s.stage) {
            case 1 -> {
                input(0.0F, false);
                if (!near(drone, ground(o)) || t.armed) {
                    return waited > BarrierSite.SYNC_TICKS ? StepResult.fail("Drohne nicht aufs Testfeld gesetzt: " + where(drone, sim, o))
                            : StepResult.pending();
                }
                s.enter(2, ctx.tick());
                return StepResult.pending();
            }
            case 2 -> {
                input(0.0F, armSwitch(waited, t.armed, t.grounded || t.sleeping));
                if (!t.armed) {
                    if (waited > ARM_TICKS) {
                        return StepResult.fail("nicht scharf geschaltet, Sperren=" + t.armBlockers + ", " + where(drone, sim, o));
                    }
                    if (waited > 0 && waited % RESET_TICKS == 0) {
                        Vec3 ground = ground(o);
                        s.pending = ctx.onServer(server -> serverDrone(server).resetTo(ground, FACING_WALL));
                    }
                    return StepResult.pending();
                }
                Vec3 air = air(o);
                s.pending = ctx.onServer(server -> serverDrone(server).resetTo(air, FACING_WALL));
                s.enter(3, ctx.tick());
                return StepResult.pending();
            }
            case 3 -> {
                input((float) t.hoverThrottleStick, true);
                if (!near(drone, air(o))) {
                    return waited > BarrierSite.SYNC_TICKS ? StepResult.fail("Drohne nicht vor die Wand gesetzt: " + where(drone, sim, o))
                            : StepResult.pending();
                }
                if (!t.armed) {
                    return StepResult.fail("beim Versetzen entschärft: " + t.lastDisarmReason);
                }
                if (waited < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                s.crashes = session.crashCount();
                s.epoch = drone.resetEpoch();
                s.denied = ServerCollision.deniedCount();
                if (s.kind == Kind.DENIED) {
                    ExampleGhost.force(true);
                }
                push(sim, s.kind.speed);
                s.enter(10, ctx.tick());
                return StepResult.pending();
            }
            case 10 -> {
                input((float) t.hoverThrottleStick, true);
                return switch (s.kind) {
                    case THROUGH -> through(ctx, s, o, drone, sim, waited);
                    case DENIED -> denied(ctx, s, o, drone, waited);
                    default -> impact(ctx, s, o, drone, sim, waited);
                };
            }
            case 11 -> {
                input((float) t.hoverThrottleStick, true);
                return verdict(ctx, s, o, drone, waited);
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + s.stage);
            }
        }
    }

    private static StepResult through(SelfTest.StepContext ctx, Scenario s, BlockPos o, QuadcopterEntity drone, DroneSim sim, int waited) {
        double half = drone.getBbWidth() / 2.0;
        double x = drone.getX();
        double near = wallNear(o);
        double far = near + s.kind.thick;
        if (x + half > near + 0.2 && x - half < far - 0.2) {
            s.guardSeen |= FpvEyeGuard.INSTANCE.moved();
            s.assistSeen |= ClientCollision.INSTANCE.assistRule(drone) != null;
            s.ghostSeen |= ClientCollision.INSTANCE.ghost();
        }
        if (x - half <= far + OUT_MARGIN) {
            return waited > PASS_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Drohne kommt nicht durch den Durchlass: x %.2f, Wand %.0f bis %.0f, %s",
                    x, near, far, where(drone, sim, o))) : StepResult.pending();
        }
        push(sim, 0.0);
        s.enter(11, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult impact(SelfTest.StepContext ctx, Scenario s, BlockPos o, QuadcopterEntity drone, DroneSim sim, int waited) {
        double half = drone.getBbWidth() / 2.0;
        double front = drone.getX() + half;
        s.front = Math.max(s.front, front);
        if (!s.touched) {
            if (front >= wallNear(o) - CONTACT) {
                s.touched = true;
                s.touchedAt = ctx.tick();
            } else if (waited > PASS_TICKS) {
                return StepResult.fail(String.format(Locale.ROOT, "Drohne erreicht die Wand nicht: Front %.2f, Wand %.0f", front, wallNear(o)));
            }
            return StepResult.pending();
        }
        if (ctx.tick() - s.touchedAt < AFTER_CONTACT_TICKS) {
            return StepResult.pending();
        }
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        s.reboundVx = snapshot.vx;
        s.enter(11, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult denied(SelfTest.StepContext ctx, Scenario s, BlockPos o, QuadcopterEntity drone, int waited) {
        String text = overlay();
        if (text.equals(Component.translatable(ServerCollision.DENIED_KEY).getString())) {
            s.overlay = text;
        }
        if (waited < DENIED_TICKS) {
            return StepResult.pending();
        }
        ExampleGhost.force(false);
        s.enter(11, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult verdict(SelfTest.StepContext ctx, Scenario s, BlockPos o, QuadcopterEntity drone, int waited) {
        PilotSession session = PilotSession.INSTANCE;
        if (waited < SERVER_TICKS) {
            return StepResult.pending();
        }
        if (Double.isNaN(s.serverX)) {
            AtomicReference<Double> x = new AtomicReference<>(Double.NaN);
            s.pending = ctx.onServer(server -> x.set(serverDrone(server).getX())).thenRun(() -> s.serverX = x.get());
            return StepResult.pending();
        }
        double half = drone.getBbWidth() / 2.0;
        double near = wallNear(o);
        double far = near + s.kind.thick;
        int resets = drone.resetEpoch() - s.epoch;
        int crashes = session.crashCount() - s.crashes;
        switch (s.kind) {
            case THROUGH -> {
                if (!(s.serverX - half > far)) {
                    return StepResult.fail(String.format(Locale.ROOT, "Server nimmt den Durchflug nicht an: Server-x %.2f, Wand endet bei %.0f", s.serverX, far));
                }
                if (resets != 0 || crashes != 0) {
                    return StepResult.fail("beim Durchflug " + resets + " Rücksetzungen und " + crashes + " Abstürze");
                }
                if (s.guardSeen || s.assistSeen || s.ghostSeen) {
                    return StepResult.fail("im Durchlass: Kamera vom Kamera-Schutz verschoben " + s.guardSeen + ", Runde mit Hilfe " + s.assistSeen + ", Geist " + s.ghostSeen);
                }
                ctx.log(String.format(Locale.ROOT, "durch %d Blöcke Drohnen-durchlässige Barriere mit %.0f m/s, Server-x %+.2f hinter der Wand, keine Rücksetzung, "
                        + "kein Absturz, Kamera nicht verschoben, keine Hilfe und kein Geist", s.kind.thick, s.kind.speed, s.serverX - half - far));
                return StepResult.pass();
            }
            case DENIED -> {
                if (resets <= 0) {
                    return StepResult.fail("Server hat den Flug durch die Sperre ohne Recht nicht zurückgesetzt");
                }
                if (s.serverX + half > near + 0.05) {
                    return StepResult.fail(String.format(Locale.ROOT, "Server-Drohne steckt in der Sperre: x %.2f", s.serverX));
                }
                if (ServerCollision.deniedCount() <= s.denied || s.overlay.isEmpty()) {
                    return StepResult.fail("collision.denied fehlt (Zähler " + (ServerCollision.deniedCount() - s.denied) + ", Meldung \"" + s.overlay + "\")");
                }
                ctx.log(String.format(Locale.ROOT, "Geist ohne Recht: %d Rücksetzungen, Server-Front %+.2f an der Sperre, Meldung \"%s\"", resets, s.serverX + half - near, s.overlay));
                return StepResult.pass();
            }
            default -> {
                if (s.front > near + 0.05 || s.serverX + half > near + 0.05) {
                    return StepResult.fail(String.format(Locale.ROOT, "Drohne steckt in der Wand: Front %.2f, Server-x %.2f, Wand %.0f", s.front, s.serverX, near));
                }
                if (resets != 0) {
                    return StepResult.fail("Server hat beim Aufprall " + resets + "-mal zurückgesetzt");
                }
                boolean fast = s.kind.speed >= DroneBuild.FREESTYLE.frame().impactTolerance;
                if (fast != (crashes > 0)) {
                    return StepResult.fail(String.format(Locale.ROOT, "mit %.0f m/s %d Abstürze (Grenze %.1f m/s)", s.kind.speed, crashes,
                            DroneBuild.FREESTYLE.frame().impactTolerance));
                }
                if (!fast && !(s.reboundVx <= 0.2)) {
                    return StepResult.fail(String.format(Locale.ROOT, "kein Abprall: vx %.2f m/s nach dem Aufprall", s.reboundVx));
                }
                Outcome outcome = new Outcome(crashes, s.front, s.reboundVx, s.serverX, resets);
                OUTCOMES.put(s.kind, outcome);
                Kind reference = s.kind.reference();
                String compare = "";
                if (reference != null) {
                    Outcome stone = OUTCOMES.get(reference);
                    if (stone != null) {
                        if (stone.crashes() > 0 != crashes > 0) {
                            return StepResult.fail("Absturz an der Sperre " + crashes + ", an Stein " + stone.crashes());
                        }
                        if (Math.abs(stone.front() - s.front) > 0.1) {
                            return StepResult.fail(String.format(Locale.ROOT, "Front an der Sperre %.2f, an Stein %.2f", s.front, stone.front()));
                        }
                        compare = String.format(Locale.ROOT, ", Stein: Front %+.2f, vx %.2f, Abstürze %d", stone.front() - near, stone.reboundVx(),
                                stone.crashes());
                    }
                }
                ctx.log(String.format(Locale.ROOT, "%.0f m/s: Front %+.2f an der Wand, Server-x %+.2f, vx danach %.2f m/s, Abstürze %d (Grenze %.1f m/s)%s",
                        s.kind.speed, s.front - near, s.serverX + half - near, s.reboundVx, crashes, CrashDamage.thresholdSpeed(DroneBuild.FREESTYLE),
                        compare));
                return StepResult.pass();
            }
        }
    }

    private static void prepare(MinecraftServer server, Kind kind) {
        BlockPos o = BarrierSite.prepare(server);
        ServerPlayer player = BarrierSite.player(server);
        ServerLevel level = player.level();
        player.setGameMode(GameType.CREATIVE);
        for (int dx = 0; dx < 3; dx++) {
            BlockState state = dx < kind.thick ? kind.wall() : Blocks.AIR.defaultBlockState();
            BarrierSite.fill(level, o.offset(WALL_X + dx, 0, FLIGHT_Z - WALL_HALF), o.offset(WALL_X + dx, WALL_HEIGHT - 1, FLIGHT_Z + WALL_HALF), state);
        }
        if (!(DRONE.get() >= 0 && level.getEntity(DRONE.get()) instanceof QuadcopterEntity)) {
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            Vec3 ground = ground(o);
            drone.snapTo(ground.x, ground.y, ground.z, 0.0F, 0.0F);
            drone.setBuild(DroneBuild.FREESTYLE);
            drone.setBatteryCharge(1.0F);
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("addFreshEntity fehlgeschlagen");
            }
            DRONE.set(drone.getId());
        }
        QuadcopterEntity drone = serverDrone(server);
        if (DronePiloting.pilotedBy(player.getUUID()).filter(piloted -> piloted == drone).isEmpty()) {
            fly(player, drone, o);
        }
        player.removeTag(ExampleGhost.PERMISSION_TAG);
        drone.setWear(PartWear.PRISTINE);
        drone.setBatteryCharge(1.0F);
        drone.resetTo(ground(o), FACING_WALL);
    }

    private static void fly(ServerPlayer player, QuadcopterEntity drone, BlockPos o) {
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        HEAD.compareAndSet(null, player.getItemBySlot(EquipmentSlot.HEAD).copy());
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        BarrierSite.teleport(player, Vec3.atBottomCenterOf(o.offset(-2, 0, FLIGHT_Z - 4)), -60.0F, 10.0F);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    static void discard(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        ServerPlayer player = BarrierSite.player(server);
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = id >= 0 ? level.getEntity(id) : null;
            if (entity instanceof QuadcopterEntity drone) {
                DronePiloting.release(drone);
                drone.discard();
            }
        }
        player.removeTag(ExampleGhost.PERMISSION_TAG);
        ItemStack head = HEAD.getAndSet(null);
        if (head != null) {
            player.setItemSlot(EquipmentSlot.HEAD, head);
        }
        OUTCOMES.clear();
    }

    static boolean armSwitch(int waited, boolean armed, boolean resting) {
        return armed || resting && waited >= 3 && waited % 20 != 0;
    }

    static Vec3 ground(BlockPos o) {
        return new Vec3(o.getX() + START_DX, o.getY(), o.getZ() + FLIGHT_Z + 0.5);
    }

    static Vec3 air(BlockPos o) {
        return ground(o).add(0.0, HOVER, 0.0);
    }

    static double wallNear(BlockPos o) {
        return o.getX() + WALL_X;
    }

    private static void input(float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private static void push(DroneSim sim, double vx) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static boolean near(QuadcopterEntity drone, Vec3 target) {
        return Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED && Math.abs(drone.getY() - target.y) < PLACED;
    }

    private static String overlay() {
        try {
            Field field = Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Object message = field.get(Minecraft.getInstance().gui.hud);
            return message instanceof Component component ? component.getString() : "";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    private static String where(QuadcopterEntity drone, DroneSim sim, BlockPos o) {
        Telemetry t = sim.telemetry();
        return String.format(Locale.ROOT, "x %+.2f, %.2f m über dem Boden, %.2f m/s, am Boden %s, scharf %s", drone.getX() - o.getX(), drone.getY() - o.getY(),
                t.speed, t.grounded, t.armed);
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server) {
        if (!(BarrierSite.player(server).level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne auf dem Server verschwunden");
        }
        return drone;
    }
}
