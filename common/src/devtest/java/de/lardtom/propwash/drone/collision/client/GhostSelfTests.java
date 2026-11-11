package de.lardtom.propwash.drone.collision.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleGhost;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.SurfaceMaterials;
import de.lardtom.propwash.drone.client.CameraClip;
import de.lardtom.propwash.drone.client.FpvEyeGuard;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.collision.CollisionRules;
import de.lardtom.propwash.drone.collision.ServerCollision;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.addon.AddonSwitchServer;
import de.lardtom.propwash.input.addon.client.AddonServerFeatures;
import de.lardtom.propwash.input.net.AddonFeaturesS2C;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.client.AssistText;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.SurfaceResponse;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class GhostSelfTests {
    public static final String GROUP = "addon-api-17: A3";
    private static final int RIG_DZ = -14;
    private static final int CLEAR_BACK = 3;
    private static final int CLEAR_FRONT = 12;
    private static final int CLEAR_SIDE = 3;
    private static final int CLEAR_HEIGHT = 15;
    private static final int WALL_DX = 4;
    private static final int WALL_THICK = 3;
    private static final int WALL_HALF = 2;
    private static final int WALL_HEIGHT = 5;
    private static final int PAD_DX = 10;
    private static final double START_DX = 1.5;
    private static final double HOVER = 2.0;
    private static final double DROP = 10.0;
    private static final double FAST = 6.0;
    private static final double SLOW = 3.0;
    private static final double PLACED = 0.3;
    private static final double STONE_APEX = 2.0;
    private static final double OUT_MARGIN = 0.3;
    private static final double STILL_RPM = 50.0;
    private static final int SYNC_TICKS = 80;
    private static final int ARM_TICKS = 200;
    private static final int RESET_TICKS = 50;
    private static final int PASS_TICKS = 120;
    private static final int SETTLE_TICKS = 10;
    private static final int SERVER_TICKS = 20;
    private static final int DENIED_TICKS = 60;
    private static final int SOLID_TICKS = 20;
    private static final int APEX_TICKS = 160;
    private static final int WEAR_TICKS = 20;
    private static final String SCENARIO = "ghost";
    private static final Quaternionf FACING_WALL = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final AtomicReference<Rig> RIG = new AtomicReference<>();

    private GhostSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + " – Antworten der Oberflächen-Tags ohne Regel unverändert, Beispiel-Regel macht Stein federnd", 200,
                GhostSelfTests::responsesStep);
        SelfTest.clientStep(GROUP + " – Fall aus 10 m auf Stein federt mit der Beispiel-Regel zurück", 900, ctx -> step(ctx, Kind.BOUNCE));
        SelfTest.clientStep(GROUP + " – Geist mit Recht fliegt durch eine 3 Blöcke dicke Wand, Server nimmt an, Runde mit Hilfe", 900,
                ctx -> step(ctx, Kind.THROUGH));
        SelfTest.clientStep(GROUP + " – Schalter in der Wand aus: IM BLOCK, nach dem Herausfliegen fest", 900, ctx -> step(ctx, Kind.INSIDE));
        SelfTest.clientStep(GROUP + " – Geist ohne Recht: Server setzt zurück und meldet collision.denied", 900, ctx -> step(ctx, Kind.DENIED));
        SelfTest.clientStep(GROUP + " – Regel, die der Server nicht kennt: beim Client fest, keine Rücksetzung", 900, ctx -> step(ctx, Kind.UNKNOWN));
        SelfTest.clientStep(GROUP + " – Testfeld abbauen", 200, GhostSelfTests::cleanupStep);
    }

    private enum Kind {
        THROUGH,
        INSIDE,
        DENIED,
        UNKNOWN,
        BOUNCE
    }

    private static final class Rig {
        final int x;
        final int y;
        final int z;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        boolean built;
        int droneId = -1;
        @Nullable ItemStack head;
        @Nullable ItemStack hand;

        Rig(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 ground() {
            return new Vec3(this.x + START_DX, this.y, this.z + 0.5);
        }

        Vec3 air() {
            return this.ground().add(0.0, HOVER, 0.0);
        }

        Vec3 drop() {
            return new Vec3(this.x + PAD_DX + 0.5, this.y + 1.0 + DROP, this.z + 0.5);
        }

        double wallNear() {
            return this.x + WALL_DX;
        }

        double wallFar() {
            return this.x + WALL_DX + WALL_THICK;
        }
    }

    private static final class Scenario {
        final Kind kind;
        int stage;
        int mark;
        @Nullable CompletableFuture<?> pending;
        int crashes;
        int epoch;
        int denied;
        @Nullable PartWear wear;
        boolean sawClip;
        boolean sawGuardIdle;
        boolean sawAssist;
        boolean sawInside;
        boolean sawWarning;
        boolean touched;
        boolean sawGhost;
        int touchedAt;
        double furthest = Double.NEGATIVE_INFINITY;
        double fastest;
        double last = Double.NaN;
        double apex = Double.NEGATIVE_INFINITY;
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

    private static StepResult responsesStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return StepResult.fail("keine Welt");
        }
        if (CollisionRules.rules().size() < 2) {
            return StepResult.fail("Beispiel-Regeln nicht angemeldet: " + CollisionRules.rules().size());
        }
        Entity probe = mc.player;
        BlockPos pos = mc.player.blockPosition().below();
        List<BlockState> states = List.of(Blocks.SLIME_BLOCK.defaultBlockState(), Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState(),
                Blocks.HAY_BLOCK.defaultBlockState(), Blocks.HONEY_BLOCK.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState());
        double restitution = AirframeSpec.of(DroneBuild.FREESTYLE).restitution;
        List<String> errors = new ArrayList<>();
        try {
            ExampleGhost.bouncyStone(false);
            for (BlockState state : states) {
                SurfaceResponse base = SurfaceMaterials.response(state);
                SurfaceResponse out = CollisionRules.contact(CollisionRules.rules(), probe,
                        new CollisionRules.Found(pos, state, Direction.UP, new Vec3(0.0, 1.0, 0.0)), 5.0, base, restitution);
                if (out != base) {
                    errors.add(state.getBlock().getName().getString() + " ohne Regel verändert: " + base + " -> " + out);
                }
            }
            ExampleGhost.bouncyStone(true);
            for (BlockState state : states) {
                SurfaceResponse base = SurfaceMaterials.response(state);
                SurfaceResponse out = CollisionRules.contact(CollisionRules.rules(), probe,
                        new CollisionRules.Found(pos, state, Direction.UP, new Vec3(0.0, 1.0, 0.0)), 5.0, base, restitution);
                boolean stone = state.is(Blocks.STONE);
                if (stone && (out.restitution() != ExampleGhost.STONE_RESTITUTION || out.impactScale() != 0.0 || out.grip() != base.grip())) {
                    errors.add("Stein mit Regel nicht federnd: " + out);
                } else if (!stone && out != base) {
                    errors.add(state.getBlock().getName().getString() + " von der Stein-Regel verändert: " + out);
                }
            }
        } finally {
            ExampleGhost.bouncyStone(false);
        }
        if (!errors.isEmpty()) {
            return StepResult.fail(String.join("; ", errors));
        }
        ctx.log(states.size() + " Flächen: ohne aktive Regel unverändert, Stein mit Beispiel-Regel Rückprall "
                + ExampleGhost.STONE_RESTITUTION + " und Aufprall 0");
        return StepResult.pass();
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
        StepResult result = run(ctx, s);
        if (result.status() != StepResult.Status.PENDING) {
            finish();
            if (kind == Kind.UNKNOWN) {
                ctx.onServer(server -> announce(server, null));
            }
        }
        return result;
    }

    private static void finish() {
        ExampleGhost.force(false);
        ExampleGhost.bouncyStone(false);
        input(0.0F, false);
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        int waited = ctx.tick() - s.mark;
        PilotSession session = PilotSession.INSTANCE;
        if (s.stage == 0) {
            ExampleGhost.force(false);
            input(0.0F, false);
            boolean allowed = s.kind != Kind.DENIED && s.kind != Kind.UNKNOWN;
            boolean hide = s.kind == Kind.UNKNOWN;
            s.pending = ctx.onServer(server -> {
                prepare(server, allowed);
                if (hide) {
                    announce(server, ExampleGhost.GHOST);
                }
            });
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
                input(0.0F, false);
                if (s.kind == Kind.UNKNOWN && AddonServerFeatures.knowsCollisionRule(ExampleGhost.GHOST)) {
                    return waited > SYNC_TICKS ? StepResult.fail("Server-Kennung ohne " + ExampleGhost.GHOST + " kommt beim Client nicht an")
                            : StepResult.pending();
                }
                if (!near(drone, rig.ground()) || t.armed) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne nicht aufs Testfeld gesetzt: " + where(drone, sim, rig.y))
                            : StepResult.pending();
                }
                if (s.kind == Kind.BOUNCE) {
                    if (spinning(t)) {
                        return waited > SYNC_TICKS ? StepResult.fail("Props drehen nach dem Entschärfen weiter") : StepResult.pending();
                    }
                    Vec3 up = rig.drop();
                    s.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(up, FACING_WALL));
                    s.enter(29, ctx.tick());
                    return StepResult.pending();
                }
                s.enter(2, ctx.tick());
                return StepResult.pending();
            }
            case 29 -> {
                input(0.0F, false);
                if (!near(drone, rig.drop())) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne nicht 10 m über den Stein gesetzt: " + where(drone, sim, rig.y))
                            : StepResult.pending();
                }
                ExampleGhost.bouncyStone(true);
                s.crashes = session.crashCount();
                s.wear = sim.wear();
                s.last = drone.getY();
                s.enter(30, ctx.tick());
                return StepResult.pending();
            }
            case 2 -> {
                input(0.0F, RigArming.armSwitch(waited, t.armed, t.grounded || t.sleeping));
                if (!t.armed) {
                    if (waited > ARM_TICKS) {
                        return StepResult.fail("nicht scharf geschaltet, Sperren=" + t.armBlockers + ", " + where(drone, sim, rig.y));
                    }
                    if (waited > 0 && waited % RESET_TICKS == 0) {
                        Vec3 ground = rig.ground();
                        s.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(ground, FACING_WALL));
                    }
                    return StepResult.pending();
                }
                Vec3 air = rig.air();
                s.pending = ctx.onServer(server -> serverDrone(server, rig.droneId).resetTo(air, FACING_WALL));
                s.enter(3, ctx.tick());
                return StepResult.pending();
            }
            case 3 -> {
                input((float) t.hoverThrottleStick, true);
                if (!near(drone, rig.air())) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne nicht vor der Wand in die Luft gesetzt: " + where(drone, sim, rig.y))
                            : StepResult.pending();
                }
                if (!t.armed) {
                    return StepResult.fail("beim Versetzen entschärft: " + t.lastDisarmReason);
                }
                if (waited < SETTLE_TICKS) {
                    return StepResult.pending();
                }
                s.crashes = session.crashCount();
                s.wear = sim.wear();
                s.epoch = drone.resetEpoch();
                s.denied = ServerCollision.deniedCount();
                ExampleGhost.force(true);
                push(sim, s.kind == Kind.INSIDE ? SLOW : FAST);
                s.enter(switch (s.kind) {
                    case INSIDE -> 20;
                    case DENIED -> 25;
                    case UNKNOWN -> 40;
                    default -> 10;
                }, ctx.tick());
                return StepResult.pending();
            }
            case 10 -> {
                input((float) t.hoverThrottleStick, true);
                return through(ctx, s, rig, drone, sim, waited);
            }
            case 11 -> {
                input((float) t.hoverThrottleStick, true);
                return throughServer(ctx, s, rig, drone, waited);
            }
            case 20, 21, 22 -> {
                input((float) t.hoverThrottleStick, true);
                return inside(ctx, s, rig, drone, sim, waited);
            }
            case 25 -> {
                input((float) t.hoverThrottleStick, true);
                return denied(ctx, s, rig, drone, sim, waited);
            }
            case 40 -> {
                input((float) t.hoverThrottleStick, true);
                return unknown(ctx, s, rig, drone, sim, waited);
            }
            case 30 -> {
                input(0.0F, false);
                return bounce(ctx, s, rig, drone, sim, waited);
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + s.stage);
            }
        }
    }

    private static StepResult through(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, DroneSim sim, int waited) {
        PilotSession session = PilotSession.INSTANCE;
        double half = drone.getBbWidth() / 2.0;
        double x = drone.getX();
        if (x + half > rig.wallNear() + 0.2 && x - half < rig.wallFar() - 0.2) {
            s.sawClip |= CameraClip.active();
            s.sawGuardIdle |= !FpvEyeGuard.INSTANCE.active();
            AssistReasons reasons = AssistText.evaluate(session, drone);
            for (AssistDetail detail : reasons.details(LapAssist.OTHER)) {
                s.sawAssist |= detail.subject().equals(ExampleGhost.GHOST.toString());
            }
        }
        if (x - half <= rig.wallFar() + OUT_MARGIN) {
            return waited > PASS_TICKS ? StepResult.fail("Drohne kommt nicht durch die Wand: " + where(drone, sim, rig.y)
                    + String.format(Locale.ROOT, ", x %.2f, Wand %.0f bis %.0f, Geist %s", x, rig.wallNear(), rig.wallFar(),
                    ClientCollision.INSTANCE.ghost())) : StepResult.pending();
        }
        stop(sim);
        s.enter(11, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult throughServer(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, int waited) {
        PilotSession session = PilotSession.INSTANCE;
        if (waited < SERVER_TICKS) {
            return StepResult.pending();
        }
        if (Double.isNaN(s.serverX)) {
            AtomicReference<Double> x = new AtomicReference<>(Double.NaN);
            s.pending = ctx.onServer(server -> x.set(serverDrone(server, rig.droneId).getX()));
            s.pending = s.pending.thenRun(() -> s.serverX = x.get());
            return StepResult.pending();
        }
        ExampleGhost.force(false);
        double half = drone.getBbWidth() / 2.0;
        if (!(s.serverX - half > rig.wallFar())) {
            return StepResult.fail(String.format(Locale.ROOT, "Server nimmt den Durchflug nicht an: Server-x %.2f, Wand endet bei %.0f",
                    s.serverX, rig.wallFar()));
        }
        if (drone.resetEpoch() != s.epoch) {
            return StepResult.fail("Server hat beim Durchflug zurückgesetzt");
        }
        if (session.crashCount() != s.crashes) {
            return StepResult.fail("Absturz beim Durchflug");
        }
        if (!s.sawClip || !s.sawGuardIdle) {
            return StepResult.fail("Kamera in der Wand nicht frei: CameraClip " + s.sawClip + ", Kamera-Schutz aus " + s.sawGuardIdle);
        }
        if (!s.sawAssist) {
            return StepResult.fail("Runde mit Geist nicht als Runde mit Hilfe markiert (Grund Sonstiges, " + ExampleGhost.GHOST + ")");
        }
        ctx.log(String.format(Locale.ROOT, "durch %d Blöcke Stein mit %.0f m/s, Server-x %.2f hinter der Wand, keine Rücksetzung, Abstürze 0, "
                + "Kamera in Blöcken frei, Runde mit Hilfe (%s)", WALL_THICK, FAST, s.serverX, ExampleGhost.GHOST));
        return StepResult.pass();
    }

    private static StepResult inside(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, DroneSim sim, int waited) {
        double half = drone.getBbWidth() / 2.0;
        double x = drone.getX();
        if (s.stage == 20) {
            push(sim, SLOW);
            if (x - half > rig.wallNear() + 0.5) {
                ExampleGhost.force(false);
                s.enter(21, ctx.tick());
                return StepResult.pending();
            }
            return waited > PASS_TICKS ? StepResult.fail("Drohne kommt nicht in die Wand: " + where(drone, sim, rig.y)) : StepResult.pending();
        }
        if (s.stage == 21) {
            push(sim, SLOW);
            s.sawInside |= ClientCollision.INSTANCE.insideBlock();
            OsdData data = OsdHud.sampler().data();
            List<OsdWarnings.Warning> warnings = new ArrayList<>();
            OsdWarnings.collect(data, warnings);
            s.sawWarning |= data.insideBlock && warnings.contains(OsdWarnings.Warning.INSIDE_BLOCK);
            if (x - half <= rig.wallFar() + OUT_MARGIN) {
                return waited > PASS_TICKS ? StepResult.fail("Drohne bleibt in der Wand stecken: " + where(drone, sim, rig.y))
                        : StepResult.pending();
            }
            stop(sim);
            s.enter(22, ctx.tick());
            return StepResult.pending();
        }
        if (waited < SOLID_TICKS / 2) {
            stop(sim);
            return StepResult.pending();
        }
        push(sim, -SLOW);
        if (waited < SOLID_TICKS + SOLID_TICKS / 2) {
            return StepResult.pending();
        }
        if (!s.sawInside || !s.sawWarning) {
            return StepResult.fail("nach dem Ausschalten in der Wand kein IM BLOCK: Zustand " + s.sawInside + ", OSD-Warnung " + s.sawWarning);
        }
        if (ClientCollision.INSTANCE.ghost() || ClientCollision.INSTANCE.insideBlock()) {
            return StepResult.fail("nach dem Herausfliegen noch durchlässig");
        }
        if (x - half < rig.wallFar() - 0.05) {
            return StepResult.fail(String.format(Locale.ROOT, "Drohne fliegt nach dem Herausfliegen wieder in die Wand: x %.2f, Wand endet bei %.0f",
                    x, rig.wallFar()));
        }
        if (drone.resetEpoch() != s.epoch) {
            return StepResult.fail("Server hat beim Herausfliegen zurückgesetzt");
        }
        ctx.log(String.format(Locale.ROOT, "Schalter in der Wand aus: IM BLOCK im OSD, nach dem Herausfliegen fest (x %.2f an der Wand %.0f), "
                + "keine Rücksetzung", x, rig.wallFar()));
        return StepResult.pass();
    }

    private static StepResult denied(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, DroneSim sim, int waited) {
        String text = overlay();
        String expected = Component.translatable(ServerCollision.DENIED_KEY).getString();
        if (text.equals(expected)) {
            s.overlay = text;
        }
        if (waited < DENIED_TICKS) {
            return StepResult.pending();
        }
        if (Double.isNaN(s.serverX)) {
            ExampleGhost.force(false);
            AtomicReference<Double> x = new AtomicReference<>(Double.NaN);
            s.pending = ctx.onServer(server -> x.set(serverDrone(server, rig.droneId).getX()));
            s.pending = s.pending.thenRun(() -> s.serverX = x.get());
            return StepResult.pending();
        }
        double half = drone.getBbWidth() / 2.0;
        if (drone.resetEpoch() == s.epoch) {
            return StepResult.fail("Server hat den Flug durch die Wand ohne Recht nicht zurückgesetzt");
        }
        if (s.serverX + half > rig.wallNear() + 0.05) {
            return StepResult.fail(String.format(Locale.ROOT, "Server-Drohne steckt ohne Recht in der Wand: x %.2f", s.serverX));
        }
        if (ServerCollision.deniedCount() <= s.denied) {
            return StepResult.fail("collision.denied nicht gemeldet");
        }
        if (!s.overlay.equals(expected)) {
            return StepResult.fail("Meldung fehlt in der Aktionsleiste: \"" + text + "\" statt \"" + expected + "\"");
        }
        ctx.log(String.format(Locale.ROOT, "ohne Recht: %d Rücksetzungen, Server-x %.2f vor der Wand, Meldung \"%s\"",
                drone.resetEpoch() - s.epoch, s.serverX, s.overlay));
        return StepResult.pass();
    }

    private static StepResult unknown(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, DroneSim sim, int waited) {
        double half = drone.getBbWidth() / 2.0;
        s.furthest = Math.max(s.furthest, drone.getX() + half);
        s.sawGhost |= ClientCollision.INSTANCE.ghost();
        s.sawClip |= CameraClip.active();
        s.sawAssist |= ClientCollision.INSTANCE.assistRule(drone) != null;
        if (waited < DENIED_TICKS) {
            return StepResult.pending();
        }
        if (Double.isNaN(s.serverX)) {
            ExampleGhost.force(false);
            AtomicReference<Double> x = new AtomicReference<>(Double.NaN);
            s.pending = ctx.onServer(server -> x.set(serverDrone(server, rig.droneId).getX()));
            s.pending = s.pending.thenRun(() -> s.serverX = x.get());
            return StepResult.pending();
        }
        List<String> errors = new ArrayList<>();
        if (s.sawGhost || s.sawClip || s.sawAssist) {
            errors.add("Regel wirkt beim Client, obwohl der Server sie nicht kennt: Geist " + s.sawGhost + ", CameraClip " + s.sawClip
                    + ", Runde mit Hilfe " + s.sawAssist);
        }
        if (s.furthest > rig.wallNear() + 0.05) {
            errors.add(String.format(Locale.ROOT, "Drohne fliegt in die Wand: weitester Punkt %.2f, Wand %.0f bis %.0f", s.furthest,
                    rig.wallNear(), rig.wallFar()));
        }
        if (s.furthest < rig.wallNear() - 0.3) {
            errors.add(String.format(Locale.ROOT, "Drohne erreicht die Wand nicht: weitester Punkt %.2f, Wand bei %.0f", s.furthest, rig.wallNear()));
        }
        if (drone.resetEpoch() != s.epoch) {
            errors.add("Server hat " + (drone.resetEpoch() - s.epoch) + "-mal zurückgesetzt");
        }
        if (ServerCollision.deniedCount() != s.denied) {
            errors.add("collision.denied gemeldet");
        }
        if (s.serverX + half > rig.wallNear() + 0.05) {
            errors.add(String.format(Locale.ROOT, "Server-Drohne in der Wand: x %.2f", s.serverX));
        }
        if (!errors.isEmpty()) {
            return StepResult.fail(String.join("; ", errors));
        }
        ctx.log(String.format(Locale.ROOT, "Regel %s dem Server unbekannt: beim Client fest (weitester Punkt %.2f an der Wand %.0f), "
                + "Server-x %.2f, keine Rücksetzung, kein collision.denied", ExampleGhost.GHOST, s.furthest, rig.wallNear(), s.serverX));
        return StepResult.pass();
    }

    private static void announce(MinecraftServer server, @Nullable Identifier hidden) {
        AddonFeaturesS2C known = AddonSwitchServer.features();
        List<Identifier> rules = new ArrayList<>(known.collisionRules());
        rules.remove(hidden);
        PropwashNet.sendToPlayer(player(server), new AddonFeaturesS2C(known.switches(), known.axes(), rules));
    }

    private static StepResult bounce(SelfTest.StepContext ctx, Scenario s, Rig rig, QuadcopterEntity drone, DroneSim sim, int waited) {
        double floor = rig.y + 1.0;
        double y = drone.getY();
        double height = y - floor;
        double speed = Double.isFinite(s.last) ? (s.last - y) * 20.0 : 0.0;
        s.last = y;
        if (!s.touched) {
            s.fastest = Math.max(s.fastest, speed);
            if (speed < -0.05 && s.fastest > 1.0) {
                s.touched = true;
                s.touchedAt = ctx.tick();
            } else if (waited > APEX_TICKS) {
                return StepResult.fail("Drohne kommt vom Stein nicht wieder hoch: " + where(drone, sim, floor));
            }
            return StepResult.pending();
        }
        s.apex = Math.max(s.apex, height);
        int since = ctx.tick() - s.touchedAt;
        boolean falling = height < s.apex - 0.05;
        if (!(falling && since > 2) || since < WEAR_TICKS) {
            return since > APEX_TICKS ? StepResult.fail("kein Gipfel nach dem Aufprall: " + where(drone, sim, floor)) : StepResult.pending();
        }
        if (PilotSession.INSTANCE.crashCount() != s.crashes) {
            return StepResult.fail(String.format(Locale.ROOT, "Absturz auf federndem Stein (%.1f m/s)", s.fastest));
        }
        if (!sim.wear().equals(s.wear)) {
            return StepResult.fail("Verschleiß nach dem Fall auf federnden Stein verändert");
        }
        if (s.apex <= STONE_APEX) {
            return StepResult.fail(String.format(Locale.ROOT, "zweiter Gipfel nur %.2f m über dem Stein (gefordert > %.1f m)", s.apex, STONE_APEX));
        }
        ctx.log(String.format(Locale.ROOT, "Fall aus %.0f m auf Stein mit %.1f m/s, zweiter Gipfel %.2f m, Abstürze 0", DROP, s.fastest, s.apex));
        return StepResult.pass();
    }

    private static void prepare(MinecraftServer server, boolean allowed) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        Rig rig = RIG.get();
        if (rig == null) {
            rig = new Rig(player.getBlockX(), player.getBlockY(), player.getBlockZ() + RIG_DZ);
            RIG.set(rig);
        }
        if (!rig.built) {
            build(level, rig);
        }
        if (!(rig.droneId >= 0 && level.getEntity(rig.droneId) instanceof QuadcopterEntity)) {
            spawn(player, rig);
        }
        QuadcopterEntity current = serverDrone(server, rig.droneId);
        if (DronePiloting.pilotedBy(player.getUUID()).filter(piloted -> piloted == current).isEmpty()) {
            fly(player, current, rig);
        }
        if (allowed) {
            player.addTag(ExampleGhost.PERMISSION_TAG);
        } else {
            player.removeTag(ExampleGhost.PERMISSION_TAG);
        }
        current.setWear(PartWear.PRISTINE);
        current.resetTo(rig.ground(), FACING_WALL);
    }

    private static void spawn(ServerPlayer player, Rig rig) {
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        drone.snapTo(rig.ground().x, rig.ground().y, rig.ground().z, 0.0F, 0.0F);
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

    private static void build(ServerLevel level, Rig rig) {
        for (int dx = -CLEAR_BACK; dx <= CLEAR_FRONT; dx++) {
            for (int dz = -CLEAR_SIDE; dz <= CLEAR_SIDE; dz++) {
                BlockPos floor = new BlockPos(rig.x + dx, rig.y - 1, rig.z + dz);
                rig.original.putIfAbsent(floor.immutable(), level.getBlockState(floor));
                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(rig.x + dx, rig.y + dy, rig.z + dz);
                    boolean wall = dx >= WALL_DX && dx < WALL_DX + WALL_THICK && Math.abs(dz) <= WALL_HALF && dy < WALL_HEIGHT;
                    boolean pad = dy == 0 && Math.abs(dx - PAD_DX) <= 1 && Math.abs(dz) <= 1;
                    rig.original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                    level.setBlockAndUpdate(pos, wall || pad ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        rig.built = true;
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        finish();
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
                player.removeTag(ExampleGhost.PERMISSION_TAG);
                announce(server, null);
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

    private static void push(DroneSim sim, double vx) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static void stop(DroneSim sim) {
        push(sim, 0.0);
    }

    private static boolean spinning(Telemetry t) {
        for (double rpm : t.motorRpm) {
            if (Math.abs(rpm) > STILL_RPM) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(QuadcopterEntity drone, Vec3 target) {
        return Math.abs(drone.getX() - target.x) < PLACED && Math.abs(drone.getZ() - target.z) < PLACED
                && Math.abs(drone.getY() - target.y) < PLACED;
    }

    private static String overlay() {
        try {
            Field field = Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Object message = field.get(Minecraft.getInstance().gui.hud);
            return message instanceof Component component ? component.getString() : "-";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "? " + e;
        }
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
