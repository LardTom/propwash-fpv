package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.freecam.client.FreecamSelfTests.Run;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.CovFreecamOsdProbe;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.video.client.CovFreecamVideoProbe;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.ZoneField;
import de.lardtom.propwash.zone.ZoneResolution;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZoneShape;
import de.lardtom.propwash.zone.client.ClientZones;
import de.lardtom.propwash.zone.client.ZonePhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class CovFreecamFlightSelfTests {
    static final String ZONE_NAME = "Abdeckung Freecam";
    private static final int CLEAN_TICKS = 40;
    private static final int MARKER_TICKS = 80;
    private static final double CRUISE = 6.0;
    private static final double SIDE = 2.0;
    private static final double SINK = 6.0;
    private static final double GROUND_GAP = 0.08;
    private static final double ZONE_X = 30.5;
    private static final double ZONE_SIZE = 12.0;
    private static final double ZONE_BOTTOM = 20.0;
    private static final double ZONE_FLIGHT = 26.0;
    private static final double STEP_BLOCKS = 0.3;
    private static final AtomicInteger ZONE_ID = new AtomicInteger(-1);
    private static final String FIELD = "cov_zone_field";

    private CovFreecamFlightSelfTests() {
    }

    static void register() {
        String group = CovFreecamSelfTests.GROUP;
        SelfTest.clientStep(group + "sauberes Bild hinter der Wand und tief im Gelände – kein Rauschen, keine Störung, kein Failsafe", 1600,
                CovFreecamSelfTests.guarded(
                        CovFreecamSelfTests.phase("Start", 300, FreecamSelfTests::start),
                        CovFreecamSelfTests.phase("hinter der Wand des Testplatzes", CLEAN_TICKS + 40, (ctx, run) -> cleanImage(ctx, run, false)),
                        CovFreecamSelfTests.phase("tief im Gelände", CLEAN_TICKS + 40, (ctx, run) -> cleanImage(ctx, run, true)),
                        CovFreecamSelfTests.phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(group + "Flugbahn-Marker im Vorwärtsflug mit 6 m/s – an der Projektion der Live-Geschwindigkeit, ohne Latenz", 1600,
                CovFreecamSelfTests.guarded(
                        CovFreecamSelfTests.hitch(),
                        CovFreecamSelfTests.phase("Start", 300, FreecamSelfTests::start),
                        CovFreecamSelfTests.phase("Marker an, scharf in der Luft", 300, CovFreecamFlightSelfTests::markerSetup),
                        CovFreecamSelfTests.phase("Vorwärtsflug mit Seitenwechseln", MARKER_TICKS + 60, CovFreecamFlightSelfTests::markerCruise),
                        CovFreecamSelfTests.phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(group + "Prop-Wash wie DroneSim mit gleichen Regeln, Bodeneffekt dicht über dem Boden, im Block keiner", 2400,
                CovFreecamSelfTests.guarded(
                        CovFreecamSelfTests.phase("Wind 0", 300, (ctx, run) -> FreecamSelfTests.setWind(ctx, run, 0)),
                        CovFreecamSelfTests.hitch(),
                        CovFreecamSelfTests.phase("Start", 300, FreecamSelfTests::start),
                        CovFreecamSelfTests.phase("schweben in 30 m", 300, CovFreecamFlightSelfTests::hoverHigh),
                        CovFreecamSelfTests.phase("Sinkflug in die eigene Luft", 120, CovFreecamFlightSelfTests::sinkIntoWash),
                        CovFreecamSelfTests.phase("Vergleich mit DroneSim nach Welt-Regeln", 20, CovFreecamFlightSelfTests::washLikeDroneSim),
                        CovFreecamSelfTests.phase("dicht über dem Boden", 100, CovFreecamFlightSelfTests::nearGround),
                        CovFreecamSelfTests.phase("im Block", 100, CovFreecamFlightSelfTests::insideBlock),
                        CovFreecamSelfTests.phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(group + "Zone mit halber Schwerkraft – weicher Übergang hinein und zurück wie bei der echten Drohne", 2400,
                CovFreecamSelfTests.guarded(
                        CovFreecamSelfTests.phase("Zone anlegen", 300, CovFreecamFlightSelfTests::createZone),
                        CovFreecamSelfTests.hitch(),
                        CovFreecamSelfTests.phase("Start", 300, FreecamSelfTests::start),
                        CovFreecamSelfTests.phase("vor der Zone schweben", 200, CovFreecamFlightSelfTests::beforeZone),
                        CovFreecamSelfTests.phase("in die Zone", 300, (ctx, run) -> crossZone(ctx, run, true)),
                        CovFreecamSelfTests.phase("aus der Zone", 300, (ctx, run) -> crossZone(ctx, run, false)),
                        CovFreecamSelfTests.phase("Ende", 100, FreecamSelfTests::endQuietly),
                        CovFreecamSelfTests.phase("Zone löschen", 300, (ctx, run) -> FreecamSelfTests.server(ctx, run, CovFreecamFlightSelfTests::tearDown))));
    }

    static void tearDown(MinecraftServer server) {
        int id = ZONE_ID.getAndSet(-1);
        for (Zone zone : List.copyOf(ZoneData.get(server).all())) {
            if (zone.id() == id || zone.name().equals(ZONE_NAME)) {
                ZoneServer.delete(server, zone.id(), Component.literal("Selbsttest"), "Selbsttest");
            }
        }
    }

    private static @Nullable String clean() {
        String video = CovFreecamVideoProbe.disturbance();
        if (video != null) {
            return video;
        }
        DroneSim sim = FreecamSession.INSTANCE.sim();
        if (sim == null) {
            return "keine Sim";
        }
        if (sim.telemetry().failsafeStage != 0) {
            return "Failsafe-Stufe " + sim.telemetry().failsafeStage;
        }
        for (OsdWarnings.Warning warning : CovFreecamOsdProbe.warnings()) {
            if (warning == OsdWarnings.Warning.FAILSAFE || warning == OsdWarnings.Warning.WEAK_SIGNAL || warning == OsdWarnings.Warning.WEAK_VIDEO
                    || warning == OsdWarnings.Warning.NO_VIDEO) {
                return "OSD-Warnung " + warning;
            }
        }
        return null;
    }

    private static StepResult cleanImage(SelfTest.StepContext ctx, Run run, boolean underground) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!FreecamSession.active() || player == null || mc.level == null) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = FreecamSelfTests.site();
        if (run.waited(ctx) == 0) {
            FreecamSession.INSTANCE.clearTestInput();
            if (underground) {
                FreecamSession.INSTANCE.testPlace(site.getX() + 2.5, site.getY() - 14.0, site.getZ() + 0.5, -90.0F);
            } else {
                FreecamSession.INSTANCE.testPlace(site.getX() + FreecamSelfTests.WALL_DX + FreecamSelfTests.WALL_THICK + 4.5, site.getY() + 1.5,
                        site.getZ() + 0.5, 90.0F);
            }
            return StepResult.pending();
        }
        if (!FpvView.active()) {
            return StepResult.fail("Bild nicht aus der Freecam");
        }
        String problem = clean();
        if (problem != null) {
            return StepResult.fail("nach " + run.waited(ctx) + " Ticks: " + problem);
        }
        if (run.waited(ctx) == 5) {
            Vector3d camera = new Vector3d();
            FreecamSession.INSTANCE.cameraPose(camera, new Quaternionf());
            run.value = CovFreecamVideoProbe.lossDb(mc.level, player.getEyePosition(), new Vec3(camera.x, camera.y, camera.z));
        }
        if (run.waited(ctx) < CLEAN_TICKS) {
            return StepResult.pending();
        }
        if (underground && run.value < 20.0) {
            return StepResult.fail(String.format(Locale.ROOT, "Gelände schirmt nur %.1f dB ab, der Test beweist nichts", run.value));
        }
        run.notes.add(String.format(Locale.ROOT, "%s: Strecke vom Spieler %.1f dB gedämpft, %d Ticks ohne Rauschen, Störung und Failsafe (Video %s)",
                underground ? "14 Blöcke unter dem Platz im Boden" : "hinter der Wand", run.value, CLEAN_TICKS, CovFreecamVideoProbe.status()));
        return StepResult.pass();
    }

    private static Vector3d velocity() {
        return FreecamSession.INSTANCE.velocity(new Vector3d());
    }

    private static StepResult markerSetup(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = FreecamSelfTests.site();
        if (run.waited(ctx) == 0) {
            CovFreecamOsdProbe.show(OsdElement.FLIGHT_PATH_MARKER);
        }
        if (!FreecamSelfTests.armAt(run, site.getX() - 20.5, site.getY() + 30.0, site.getZ() + 0.5, -90.0F)) {
            return FreecamSelfTests.notArmed(run);
        }
        FreecamSelfTests.arm(FreecamSelfTests.hover(), StabMode.ANGLE);
        return FreecamSelfTests.until(++run.count > 10, "schweben");
    }

    private static StepResult markerCruise(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!FreecamSession.active() || !FreecamSession.INSTANCE.armed()) {
            return StepResult.fail("Freecam aus oder entschärft");
        }
        int t = run.waited(ctx);
        Vector3d live = velocity();
        if (t >= 20 && t < 20 + MARKER_TICKS) {
            OsdData data = CovFreecamOsdProbe.data();
            double lag = data.hasVelocity() ? Math.max(Math.abs(data.velocityX - live.x), Math.max(Math.abs(data.velocityY - live.y),
                    Math.abs(data.velocityZ - live.z))) : Double.POSITIVE_INFINITY;
            run.other.z = Math.max(run.other.z, lag);
            if (CovFreecamOsdProbe.drawn(OsdElement.FLIGHT_PATH_MARKER)) {
                CovFreecamOsdProbe.Marker shown = CovFreecamOsdProbe.marker();
                CovFreecamOsdProbe.Marker expected = CovFreecamOsdProbe.predict(mc, live.x, live.y, live.z);
                run.count++;
                if (!shown.inView()) {
                    run.sent++;
                }
                if (expected != null && expected.visible()) {
                    run.value = Math.max(run.value, Math.hypot(expected.x() - shown.x(), expected.y() - shown.y()));
                }
            }
            run.other.x = Math.max(run.other.x, Math.hypot(live.x, live.z));
        }
        if (t >= 20 + MARKER_TICKS) {
            OsdGrid grid = CovFreecamOsdProbe.grid(mc);
            double tolerance = Math.max(8.0, grid.height() * 0.05);
            String numbers = String.format(Locale.ROOT, "%d von %d Ticks gezeichnet, %d am Rand, größte Abweichung zur Projektion der Live-Geschwindigkeit "
                    + "%.1f Punkte (Grenze %.1f), OSD-Tempo weicht höchstens %.3f m/s von der Sim ab, Tempo bis %.1f m/s", run.count, MARKER_TICKS, run.sent,
                    run.value, tolerance, run.other.z, run.other.x);
            FreecamSession.INSTANCE.clearTestInput();
            if (run.count < MARKER_TICKS * 9 / 10 || run.sent > 0) {
                return StepResult.fail("Marker nicht durchgehend im Bild: " + numbers);
            }
            if (!(run.value < tolerance)) {
                return StepResult.fail("Marker weicht von der Projektion ab: " + numbers);
            }
            if (!(run.other.z < 0.05)) {
                return StepResult.fail("OSD-Tempo hängt hinter der Sim: " + numbers);
            }
            run.notes.add(numbers);
            run.value = 0.0;
            return StepResult.pass();
        }
        Quaternionf body = FreecamSession.INSTANCE.orientation(new Quaternionf());
        Vector3f forward = body.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        double length = Math.hypot(forward.x, forward.z);
        double fx = length < 1.0E-4 ? 1.0 : forward.x / length;
        double fz = length < 1.0E-4 ? 0.0 : forward.z / length;
        double side = ((t / 8) % 2 == 0 ? 1.0 : -1.0) * SIDE;
        FreecamSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, FreecamSelfTests.hover()), true, StabMode.ANGLE);
        FreecamSession.INSTANCE.testPush(fx * CRUISE - fz * side, 0.0, fz * CRUISE + fx * side);
        return StepResult.pending();
    }

    private static StepResult hoverHigh(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = FreecamSelfTests.site();
        if (!FreecamSelfTests.armAt(run, site.getX() + 2.5, site.getY() + 30.0, site.getZ() + 0.5, -90.0F)) {
            return FreecamSelfTests.notArmed(run);
        }
        FreecamSelfTests.arm(FreecamSelfTests.hover(), StabMode.ANGLE);
        return FreecamSelfTests.until(++run.count > 40, "einschwingen");
    }

    private static StepResult sinkIntoWash(SelfTest.StepContext ctx, Run run) {
        DroneSim sim = FreecamSession.INSTANCE.sim();
        if (sim == null || !sim.telemetry().armed) {
            return StepResult.fail("Freecam aus oder entschärft");
        }
        Telemetry t = sim.telemetry();
        if (run.waited(ctx) == 0) {
            FreecamSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) t.hoverThrottleStick), true, StabMode.ACRO);
            Vector3d v = velocity();
            FreecamSession.INSTANCE.testPush(v.x, -SINK, v.z);
            return StepResult.pending();
        }
        run.value = Math.max(run.value, t.propWash);
        run.other.x = Math.min(run.other.x, t.verticalSpeed);
        if (run.waited(ctx) < 20) {
            return StepResult.pending();
        }
        double peak = run.value;
        run.value = 0.0;
        ctx.state().put("cov_wash_live", peak);
        FreecamSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, FreecamSelfTests.hover()), true, StabMode.ANGLE);
        if (peak < 0.4) {
            return StepResult.fail(String.format(Locale.ROOT, "Sinkflug mit %.1f m/s gibt nur Prop-Wash %.2f", -run.other.x, peak));
        }
        run.notes.add(String.format(Locale.ROOT, "Freecam sinkt mit %.1f m/s in die eigene Luft: Prop-Wash bis %.2f", -run.other.x, peak));
        run.other.x = 0.0;
        return StepResult.pass();
    }

    static double washPeak(AirframeSpec params, SimTuning tuning, java.util.Map<String, Float> tune) {
        DroneSim sim = new DroneSim(params, TuneMapping.toConfig(tune, params), tuning.copy(), 42L);
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        env.solidDistance = Double.POSITIVE_INFINITY;
        de.lardtom.propwash.sim.StepResult out = new de.lardtom.propwash.sim.StepResult();
        sim.reset(0.0, 60.0, 0.0, 0.0);
        input.mode = StabMode.ANGLE;
        input.linkOk = true;
        input.throttle = 0.0;
        input.armSwitch = false;
        sim.step(0L, input, env, out);
        input.armSwitch = true;
        sim.step(0L, input, env, out);
        long frame = Math.round(1.0E9 / 144.0);
        Vector3d position = new Vector3d();
        Vector3d velocity = new Vector3d();
        for (int f = 0; f < 432; f++) {
            sim.getPosition(position);
            sim.getVelocity(velocity);
            input.throttle = Math.clamp(sim.telemetry().hoverThrottleStick + 0.08 * (60.0 - position.y) - 0.06 * velocity.y, 0.0, 1.0);
            sim.step(frame, input, env, out);
        }
        input.mode = StabMode.ACRO;
        input.throttle = sim.telemetry().hoverThrottleStick;
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vy = -SINK;
        sim.copyStateFrom(snapshot);
        double peak = 0.0;
        for (int f = 0; f < 72; f++) {
            sim.step(frame, input, env, out);
            peak = Math.max(peak, sim.telemetry().propWash);
        }
        return sim.telemetry().armed ? peak : -1.0;
    }

    private static StepResult washLikeDroneSim(SelfTest.StepContext ctx, Run run) {
        DroneSim live = FreecamSession.INSTANCE.sim();
        if (live == null) {
            return StepResult.fail("Freecam aus");
        }
        SimTuning world = ClientWorldRules.INSTANCE.physics().toSimTuning();
        SimTuning freecam = live.tuning();
        if (freecam.propWash != world.propWash || freecam.wakeField != world.wakeField || freecam.groundEffect != world.groundEffect) {
            return StepResult.fail("Freecam-Regeln weichen ab: Prop-Wash " + freecam.propWash + "/" + world.propWash + ", Nachlauf " + freecam.wakeField + "/"
                    + world.wakeField + ", Bodeneffekt " + freecam.groundEffect + "/" + world.groundEffect);
        }
        java.util.Map<String, Float> tune = FreecamConfig.get().tune(FreecamSession.INSTANCE.preset());
        double withFreecam = washPeak(live.params(), freecam, tune);
        double withWorld = washPeak(live.params(), world, tune);
        if (withFreecam < 0.0 || withWorld < 0.0) {
            return StepResult.fail("Vergleichs-Sim entschärft");
        }
        if (withWorld < 0.5 || Math.abs(withFreecam - withWorld) > Math.max(0.05, withWorld * 0.1)) {
            return StepResult.fail(String.format(Locale.ROOT, "Prop-Wash beim Sinken mit 6 m/s: Freecam-Regeln %.3f, DroneSim nach Welt-Regeln %.3f",
                    withFreecam, withWorld));
        }
        double livePeak = (double) ctx.state().getOrDefault("cov_wash_live", 0.0);
        run.notes.add(String.format(Locale.ROOT, "DroneSim nach Welt-Regeln %.3f, mit Freecam-Regeln %.3f (Prop-Wash %s, Bodeneffekt %s), Freecam im Spiel %.2f",
                withWorld, withFreecam, world.propWash ? "an" : "aus", world.groundEffect ? "an" : "aus", livePeak));
        return StepResult.pass();
    }

    private static StepResult nearGround(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        DroneSim sim = FreecamSession.INSTANCE.sim();
        if (sim == null || mc.level == null) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = FreecamSelfTests.site();
        double x = site.getX() + 2.5;
        double y = site.getY() + GROUND_GAP;
        double z = site.getZ() + 0.5;
        FreecamSession.INSTANCE.testPlace(x, y, z, -90.0F);
        FreecamSelfTests.arm(FreecamSelfTests.hover(), StabMode.ANGLE);
        if (run.waited(ctx) < 15) {
            return StepResult.pending();
        }
        double distance = FreecamSession.groundDistance(mc.level, x, y, z);
        Telemetry t = sim.telemetry();
        if (!(distance >= 0.0 && distance < 0.3)) {
            return StepResult.fail(String.format(Locale.ROOT, "Bodenabstand %.3f statt %.2f", distance, GROUND_GAP));
        }
        if (!t.armed) {
            return CovFreecamSelfTests.pending("unscharf");
        }
        if (!(t.groundEffectFactor > 1.02) || !t.inGroundEffect) {
            return run.waited(ctx) > 60 ? StepResult.fail(String.format(Locale.ROOT, "dicht über dem Boden (%.3f) kein Bodeneffekt: Faktor %.4f", distance,
                    t.groundEffectFactor)) : StepResult.pending();
        }
        run.notes.add(String.format(Locale.ROOT, "%.2f Blöcke über dem Boden: Bodeneffekt-Faktor %.3f", distance, t.groundEffectFactor));
        return StepResult.pass();
    }

    private static StepResult insideBlock(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        DroneSim sim = FreecamSession.INSTANCE.sim();
        if (sim == null || mc.level == null) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = FreecamSelfTests.site();
        double x = site.getX() + FreecamSelfTests.WALL_DX + 1.5;
        double y = site.getY() + 0.5;
        double z = site.getZ() + 0.5;
        FreecamSession.INSTANCE.testPlace(x, y, z, -90.0F);
        FreecamSelfTests.arm(FreecamSelfTests.hover(), StabMode.ANGLE);
        if (run.waited(ctx) < 15) {
            return StepResult.pending();
        }
        if (mc.level.getBlockState(BlockPos.containing(x, y, z)).isAir()) {
            return StepResult.fail("Testpunkt nicht im Block");
        }
        double distance = FreecamSession.groundDistance(mc.level, x, y, z);
        Telemetry t = sim.telemetry();
        if (distance != Double.POSITIVE_INFINITY) {
            return StepResult.fail("im Block Bodenabstand " + distance + " statt unendlich");
        }
        if (t.groundEffectFactor != 1.0 || t.inGroundEffect) {
            return StepResult.fail(String.format(Locale.ROOT, "im Block Bodeneffekt-Faktor %.4f", t.groundEffectFactor));
        }
        run.notes.add("im Block: Bodenabstand unendlich, Faktor 1,000");
        FreecamSession.INSTANCE.clearTestInput();
        return StepResult.pass();
    }

    private static StepResult createZone(SelfTest.StepContext ctx, Run run) {
        if (run.future == null) {
            run.future = ctx.onServer(server -> {
                tearDown(server);
                ServerLevel level = FreecamSelfTests.player(server).level();
                BlockPos site = FreecamSelfTests.site();
                Zone zone = Zone.create(ZoneServer.dimension(level), ZONE_NAME, ZoneShape.BOX, ZoneBox.around(site.getX() + ZONE_X,
                                site.getY() + ZONE_BOTTOM, site.getZ() + 0.5, ZONE_SIZE, ZONE_SIZE, ZONE_SIZE))
                        .withRaceLegal(true).withTransition(10).withPriority(1).withOverride(WorldRuleSpecs.GRAVITY_SCALE, 50);
                ZoneServer.Outcome outcome = ZoneServer.save(server, zone, Component.literal("Selbsttest"), "Selbsttest");
                if (!outcome.ok() || outcome.zone() == null) {
                    FreecamSelfTests.SERVER.set("Zone abgelehnt: " + outcome.problem());
                    return;
                }
                ZONE_ID.set(outcome.zone().id());
            });
            return StepResult.pending();
        }
        if (!run.future.isDone()) {
            return StepResult.pending();
        }
        if (run.future.isCompletedExceptionally()) {
            return StepResult.fail("Zone: " + run.future.exceptionNow());
        }
        String problem = FreecamSelfTests.SERVER.getAndSet(null);
        if (problem != null) {
            return StepResult.fail(problem);
        }
        boolean synced = ClientZones.INSTANCE.zones().stream().anyMatch(zone -> zone.name().equals(ZONE_NAME));
        return FreecamSelfTests.until(synced, "Zone kommt nicht beim Client an");
    }

    private static double gravity() {
        DroneSim sim = FreecamSession.INSTANCE.sim();
        return sim == null ? Double.NaN : sim.tuning().gravityScale;
    }

    private static double target(Minecraft mc, Vector3d position) {
        ZoneResolution resolution = ZonePhysics.at(mc.level, position.x, position.y, position.z);
        return resolution.rules().toSimTuning().gravityScale;
    }

    private static StepResult beforeZone(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!FreecamSession.active() || mc.level == null) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = FreecamSelfTests.site();
        if (run.waited(ctx) == 0) {
            ctx.state().remove(FIELD);
        }
        if (!FreecamSelfTests.armAt(run, site.getX() + ZONE_X - 16.0, site.getY() + ZONE_FLIGHT, site.getZ() + 0.5, -90.0F)) {
            return FreecamSelfTests.notArmed(run);
        }
        FreecamSelfTests.arm(FreecamSelfTests.hover(), StabMode.ANGLE);
        Vector3d position = CovFreecamSelfTests.position();
        FreecamSession.INSTANCE.testPlace(position.x, site.getY() + ZONE_FLIGHT, position.z, -90.0F);
        if (++run.count < 20) {
            return StepResult.pending();
        }
        if (gravity() != 1.0 || target(mc, position) != 1.0) {
            return StepResult.fail("vor der Zone schon Schwerkraft " + gravity());
        }
        ZoneField field = new ZoneField();
        ZonePhysics.update(field, mc.level, position.x, position.y, position.z, 0.0);
        ctx.state().put(FIELD, field);
        return StepResult.pass();
    }

    private static StepResult crossZone(SelfTest.StepContext ctx, Run run, boolean into) {
        Minecraft mc = Minecraft.getInstance();
        if (!FreecamSession.active() || !FreecamSession.INSTANCE.armed() || mc.level == null) {
            return StepResult.fail("Freecam aus oder entschärft");
        }
        BlockPos site = FreecamSelfTests.site();
        ZoneField field = (ZoneField) ctx.state().get(FIELD);
        if (field == null) {
            return StepResult.fail("Vergleichsfeld fehlt");
        }
        double stopX = site.getX() + ZONE_X + (into ? 0.0 : ZONE_SIZE / 2.0 + 10.0);
        Vector3d position = CovFreecamSelfTests.position();
        double x = Math.min(stopX, position.x + STEP_BLOCKS);
        FreecamSelfTests.arm(FreecamSelfTests.hover(), StabMode.ANGLE);
        FreecamSession.INSTANCE.testPlace(x, site.getY() + ZONE_FLIGHT, site.getZ() + 0.5, -90.0F);
        Vector3d now = new Vector3d(x, site.getY() + ZONE_FLIGHT, site.getZ() + 0.5);
        ZonePhysics.update(field, mc.level, now.x, now.y, now.z, 0.05);
        double g = gravity();
        double reference = field.apply(field.rules().toSimTuning()).gravityScale;
        double wanted = target(mc, now);
        double from = into ? 1.0 : 0.5;
        double to = into ? 0.5 : 1.0;
        if (g < 0.5 - 1.0E-9 || g > 1.0 + 1.0E-9) {
            return StepResult.fail("Schwerkraft außerhalb 50–100 %: " + g);
        }
        if (g != from && g != to) {
            run.count++;
            run.value = Math.max(run.value, Math.abs(g - reference));
        }
        if (run.tick < 0 && wanted == to) {
            run.tick = ctx.tick();
        }
        if (g != to || x < stopX) {
            return run.waited(ctx) > 280 ? StepResult.fail(String.format(Locale.ROOT, "Schwerkraft %.3f erreicht %.2f nicht (Ziel laut ZonePhysics %.2f)", g,
                    to, wanted)) : StepResult.pending();
        }
        if (wanted != to) {
            return StepResult.fail("ZonePhysics.at nennt " + wanted + " statt " + to);
        }
        int blended = run.count;
        double deviation = run.value;
        run.value = 0.0;
        if (blended < 8) {
            return StepResult.fail("Übergang " + (into ? "hinein" : "hinaus") + " ohne weiche Blende: " + blended + " Ticks mit Zwischenwert");
        }
        if (deviation > 0.15) {
            return StepResult.fail(String.format(Locale.ROOT, "Blende weicht vom ZoneField der echten Drohne um bis zu %.3f ab", deviation));
        }
        run.notes.add(String.format(Locale.ROOT, "%s: Schwerkraft %.0f %% → %.0f %%, %d Ticks Zwischenwerte, größte Abweichung zum ZoneField %.3f",
                into ? "hinein" : "hinaus", from * 100.0, to * 100.0, blended, deviation));
        return StepResult.pass();
    }
}
