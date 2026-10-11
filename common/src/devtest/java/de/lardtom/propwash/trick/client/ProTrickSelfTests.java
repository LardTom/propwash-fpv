package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.trick.Trick;
import de.lardtom.propwash.trick.TrickSample;
import de.lardtom.propwash.trick.TrickScore;
import de.lardtom.propwash.trick.TrickSession;
import de.lardtom.propwash.trick.TrickType;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;

public final class ProTrickSelfTests {
    private static final long FRAME_NANOS = 16_666_667L;
    private static final int MAX_STEPS = 1500;
    private static final List<TrickType> LINE = List.of(TrickType.GUILLOTINE, TrickType.EJECT, TrickType.SPLIT_S, TrickType.JUICY_FLICK,
            TrickType.SBANG);

    private ProTrickSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("tricks: Neue Schule in der Simulation – Guillotine, Eject und Sbang-Kombo mit OSD-Meldung", 1600,
                ProTrickSelfTests::lineStep);
    }

    private static StepResult lineStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            Player player = mc.player;
            if (player == null) {
                return StepResult.fail("kein Spieler");
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            for (TrickType type : LINE) {
                if (!Language.getInstance().has(type.translationKey()) || !Language.getInstance().has("propwash.osd.trick.type." + type.key())) {
                    return StepResult.fail("Übersetzung fehlt: " + type.key());
                }
            }
            LineSource source = new LineSource(player.getX(), player.getY() + 60.0, player.getZ());
            state.put("source", source);
            state.put("errors", OsdHud.errors());
            OsdHud.sampler().setOverride(source);
            return StepResult.pending();
        }
        LineSource source = (LineSource) state.get("source");
        int errors = OsdHud.errors() - (Integer) state.get("errors");
        if (errors > 0) {
            OsdHud.sampler().setOverride(null);
            return StepResult.fail(errors + " OSD-Fehler beim Zeichnen (siehe Log)");
        }
        List<TrickType> found = source.session.history().stream().map(Trick::type).toList();
        TrickScore score = source.session.score();
        if (!found.contains(TrickType.SBANG) || score.pendingCount() > 0) {
            if (source.steps > MAX_STEPS || source.sim.telemetry().crashCount > 0) {
                OsdHud.sampler().setOverride(null);
                return StepResult.fail("Linie nicht erkannt: " + found + " (Abschnitt " + source.script.stage() + ", Absturz "
                        + source.sim.telemetry().crashCount + ")");
            }
            return StepResult.pending();
        }
        if (!found.equals(LINE)) {
            OsdHud.sampler().setOverride(null);
            return StepResult.fail("statt " + LINE + " erkannt: " + found);
        }
        OsdData data = OsdHud.sampler().data();
        boolean popupEnabled = OsdHud.config().enabled(OsdElement.TRICKS);
        boolean named = false;
        for (int i = 0; i < data.trickTypeCount; i++) {
            named |= data.trickTypes[i] == TrickType.SBANG;
        }
        if (!named) {
            OsdHud.sampler().setOverride(null);
            return StepResult.fail("OSD-Meldung ohne Sbang-Kombo (" + data.trickTypeCount + " Namen)");
        }
        if (popupEnabled && !OsdHud.drewLastFrame(OsdElement.TRICKS)) {
            if (data.trickAgeSeconds > 2.0) {
                OsdHud.sampler().setOverride(null);
                return StepResult.fail("Meldung der Sbang-Kombo wurde im OSD nicht gezeichnet");
            }
            return StepResult.pending();
        }
        OsdHud.sampler().setOverride(null);
        ctx.log("tricks: " + found + " nach " + source.steps + " Sim-Schritten, " + score.flightScore() + " Punkte, Meldung "
                + score.popupPoints());
        return StepResult.pass();
    }

    private static final class LineSource implements OsdSampler.Source {
        final DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 17L);
        final TrickSession session = new TrickSession();
        final ProTrickScript script = new ProTrickScript(ProTrickScript.Stage.ARM, FRAME_NANOS * 1.0E-9);
        private final PilotInput input = new PilotInput();
        private final SimEnvironment environment = new SimEnvironment();
        private final de.lardtom.propwash.sim.StepResult result = new de.lardtom.propwash.sim.StepResult();
        private final TrickSample sample = new TrickSample();
        private final Vector3d position = new Vector3d();
        private final Vector3d velocity = new Vector3d();
        private final Quaterniond attitude = new Quaterniond();
        int steps;

        LineSource(double x, double y, double z) {
            this.sim.reset(x, y, z, 0.0);
            this.sim.setBatteryCharge(1.0);
            this.session.keepHistory(32);
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            Telemetry before = this.sim.telemetry();
            double hover = before.hoverThrottleStick;
            double hoverStick = Double.isFinite(hover) && hover > 0.05 ? hover : 0.35;
            this.script.command(this.position.y, this.velocity.y, before.armed, this.sim.config().rates, hoverStick);
            PilotInput in = this.input;
            in.armSwitch = this.steps >= 3 && this.steps < MAX_STEPS;
            in.mode = this.script.mode;
            in.roll = this.script.roll;
            in.pitch = this.script.pitch;
            in.yaw = 0.0;
            in.throttle = this.script.throttle;
            this.sim.step(FRAME_NANOS, in, this.environment, this.result);
            this.steps++;
            Telemetry t = this.sim.telemetry();
            d.applySim(this.sim);
            this.sim.getPosition(this.position);
            this.sim.getVelocity(this.velocity);
            this.sim.getAttitude(this.attitude);
            this.script.observe(this.position.y, this.attitude);
            d.hasPosition = true;
            d.x = this.position.x;
            d.y = this.position.y;
            d.z = this.position.z;
            d.radioConnected = true;
            d.linkStrength = 0.95;
            d.videoSystem = VideoSystem.DIGITAL;
            d.videoSignal = 0.9;
            TrickSample s = this.sample;
            s.time = t.time;
            s.x = this.position.x;
            s.y = this.position.y;
            s.z = this.position.z;
            s.vx = this.velocity.x;
            s.vy = this.velocity.y;
            s.vz = this.velocity.z;
            s.qx = this.attitude.x;
            s.qy = this.attitude.y;
            s.qz = this.attitude.z;
            s.qw = this.attitude.w;
            s.armed = t.armed;
            s.crashCount = t.crashCount;
            s.impactCount = t.impactCount;
            s.grounded = t.grounded;
            s.throttle = t.throttleOut;
            s.mode3d = t.mode3d;
            s.clearNearby();
            this.session.update(s);
            d.applyTricks(this.session);
        }
    }
}
