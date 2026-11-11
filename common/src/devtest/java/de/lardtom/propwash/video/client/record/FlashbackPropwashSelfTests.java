package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.compat.flashback.client.FlashbackBridge;
import de.lardtom.propwash.compat.flashback.client.FlashbackProbe;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.video.record.path.CameraAngles;
import de.lardtom.propwash.video.record.path.CameraPathBuffer;
import de.lardtom.propwash.video.record.path.PathResampler;
import de.lardtom.propwash.video.record.path.PathSample;
import de.lardtom.propwash.video.record.path.PathWobble;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class FlashbackPropwashSelfTests {
    static final String STEP = "video: Action-Cam (Flashback) – Propwash im Kamerapfad wie live";
    static final String CSV = "propwash-flashback-wobble.csv";
    private static final String PHASE = "wash_phase";
    private static final String TARGET = "wash_target";
    private static final String MARK = "wash_mark";
    private static final String TRACE = "wash_trace";
    private static final String REPLAY = "wash_replay";
    private static final String CYCLE = "wash_cycle";
    private static final String STAGE = "wash_stage";
    private static final String LOWEST = "wash_lowest";
    private static final double CLIMB = 18.0;
    private static final int DROP_TICKS = 14;
    private static final int CATCH_TICKS = 26;
    private static final int MIN_RECOVER_TICKS = 30;
    private static final int MAX_RECOVER_TICKS = 160;
    private static final double RECOVERED = 1.5;
    private static final int CYCLES = 3;
    private static final float DROP_THROTTLE = 0.05F;
    private static final double CATCH_FACTOR = 1.05;
    private static final double RATE = 120.0;
    private static final double WASH = 0.3;
    private static final double EDGE_TICKS = 20.0;
    private static final double MIN_WASH_SECONDS = 1.0;
    private static final double FAKE_SLOW_LIMIT = 0.01;
    private static final double RATIO_MIN = 0.9;
    private static final double RATIO_MAX = 1.05;

    private FlashbackPropwashSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(STEP, 2400, FlashbackPropwashSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        FlashbackCam cam = FlashbackCam.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        if (sim == null) {
            return StepResult.fail("keine Flugsitzung");
        }
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        double y = session.comPosition(new Vector3d()).y;
        double target = (double) state.computeIfAbsent(TARGET, key -> y + CLIMB);
        float hold = FlashbackCamSelfTests.altitude(state, y, target);
        try {
            switch (phase) {
                case 0 -> {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hold), true, StabMode.ANGLE);
                    if (FlashbackBridge.recording() || cam.recording() || cam.saving() || Math.abs(y - target) > 0.8) {
                        return ctx.tick() > 900 ? StepResult.fail(String.format(Locale.ROOT, "nicht bereit: Höhe %.1f statt %.1f, Flashback nimmt auf %b",
                                y, target, FlashbackBridge.recording())) : StepResult.pending();
                    }
                    RecordingConfig config = RecordingConfig.get().copy();
                    config.flashbackAutoRecord = true;
                    config.flashbackSimplify = 0.0;
                    RecordingConfig.CONFIG.set(config);
                    KeyMapping.click(InputConstants.getKey(RecordingKeys.RECORD.saveString()));
                    state.put(PHASE, 1);
                    return StepResult.pending();
                }
                case 1 -> {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hold), true, StabMode.ANGLE);
                    if (!cam.recording() || !FlashbackBridge.writing() || cam.samples() < 10) {
                        return ctx.tick() > 1300 ? StepResult.fail("Kamerapfad startet nicht: " + cam.lastResult()) : StepResult.pending();
                    }
                    state.put(REPLAY, cam.replay());
                    state.put(MARK, ctx.tick());
                    state.put(TRACE, new HashMap<Integer, Double>());
                    state.put(PHASE, 2);
                    return StepResult.pending();
                }
                case 2 -> {
                    int cycle = (int) state.getOrDefault(CYCLE, 0);
                    int stage = (int) state.getOrDefault(STAGE, 0);
                    int since = ctx.tick() - (int) state.get(MARK);
                    float throttle = hold;
                    if (stage == 0) {
                        throttle = DROP_THROTTLE;
                    } else if (stage == 1) {
                        throttle = (float) Math.min(1.0, sim.telemetry().hoverThrottleStick * CATCH_FACTOR);
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
                    @SuppressWarnings("unchecked")
                    Map<Integer, Double> trace = (Map<Integer, Double>) state.get(TRACE);
                    trace.merge(FlashbackProbe.writtenTicks(), sim.telemetry().propWash, Math::max);
                    state.put(LOWEST, Math.min((double) state.getOrDefault(LOWEST, y), y));
                    if (!cam.recording()) {
                        return StepResult.fail("Kamerapfad endet während des Propwash-Flugs: " + cam.lastError());
                    }
                    boolean next = switch (stage) {
                        case 0 -> since >= DROP_TICKS;
                        case 1 -> since >= CATCH_TICKS;
                        default -> since >= MAX_RECOVER_TICKS || since >= MIN_RECOVER_TICKS && Math.abs(y - target) < RECOVERED;
                    };
                    if (!next) {
                        return StepResult.pending();
                    }
                    state.put(MARK, ctx.tick());
                    if (stage < 2) {
                        state.put(STAGE, stage + 1);
                        return StepResult.pending();
                    }
                    state.put(STAGE, 0);
                    state.put(CYCLE, cycle + 1);
                    if (cycle + 1 < CYCLES) {
                        return StepResult.pending();
                    }
                    KeyMapping.click(InputConstants.getKey(RecordingKeys.RECORD.saveString()));
                    state.put(PHASE, 3);
                    return StepResult.pending();
                }
                case 3 -> {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hold), true, StabMode.ANGLE);
                    FlashbackCam.Handover handover = cam.lastHandover();
                    UUID replay = (UUID) state.get(REPLAY);
                    if (cam.recording() || cam.saving() || handover == null || !handover.replay().equals(replay)) {
                        return ctx.tick() - (int) state.get(MARK) > 400 ? StepResult.fail("Kamerapfad nicht übergeben: " + cam.lastError())
                                : StepResult.pending();
                    }
                    @SuppressWarnings("unchecked")
                    Map<Integer, Double> trace = (Map<Integer, Double>) state.get(TRACE);
                    return compare(ctx, handover, trace);
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + phase);
                }
            }
        } catch (ReflectiveOperationException | IOException e) {
            return StepResult.fail("Flashback nicht lesbar: " + e);
        }
    }

    private static StepResult compare(SelfTest.StepContext ctx, FlashbackCam.Handover handover, Map<Integer, Double> trace)
            throws ReflectiveOperationException, IOException {
        FlashbackProbe.Editor editor = FlashbackProbe.load(FlashbackProbe.editorState(handover.replay()));
        CameraPathBuffer path = FlashbackCam.INSTANCE.lastPath();
        if (editor == null || path == null) {
            return StepResult.fail("Editorzustand oder Kamerapfad fehlt");
        }
        double first = Math.max(handover.startTick(), path.firstTick());
        double last = Math.min(handover.endTick(), path.lastTick());
        int count = (int) Math.floor((last - first) * RATE / 20.0);
        if (count < 4 * RATE) {
            return StepResult.fail("Kamerapfad zu kurz: " + count + " Proben");
        }
        Quaterniond[] live = new Quaterniond[count];
        Quaterniond[] exported = new Quaterniond[count];
        boolean[] mask = new boolean[count];
        double[] ticks = new double[count];
        double[] wash = new double[count];
        double peak = 0.0;
        for (int i = 0; i < count; i++) {
            double tick = first + i * 20.0 / RATE;
            PathSample sample = PathResampler.at(path, tick).orElse(null);
            FlashbackProbe.Camera camera = editor.camera(handover.sceneIndex(), 0, (float) tick);
            if (sample == null || camera == null) {
                return StepResult.fail("keine Lage bei Tick " + tick);
            }
            ticks[i] = tick;
            live[i] = new Quaterniond(sample.qx(), sample.qy(), sample.qz(), sample.qw());
            exported[i] = new CameraAngles(camera.yaw(), camera.pitch(), camera.roll()).rotation();
            wash[i] = trace.getOrDefault((int) Math.floor(tick), 0.0);
            peak = Math.max(peak, wash[i]);
            mask[i] = wash[i] > WASH && tick > first + EDGE_TICKS && tick < last - EDGE_TICKS;
        }
        PathWobble.Result result = PathWobble.compare(live, exported, mask, RATE);
        write(live, exported, ticks, wash, first);
        String text = String.format(Locale.ROOT, "Propwash im Kamerapfad (0 %%, Flashbacks eigene Interpolation, %.0f Hz): %.1f s im Propwash "
                        + "(Spitze %.2f), Wackeln 0,5–8 Hz live %.3f°, in Flashback %.3f° (x%.2f), über 12 Hz live %.3f°, "
                        + "Schein-Wackeln %.3f°, davon langsam (0,5–3 Hz) %.3f°", RATE, result.samples() / RATE, peak, result.liveSway(),
                result.exportSway(), result.ratio(), result.liveFast(), result.fakeSway(), result.fakeSlowSway());
        ctx.log(text + String.format(Locale.ROOT, ", tiefster Punkt %.1f Blöcke unter der Starthöhe", (double) ctx.state().get(TARGET)
                - (double) ctx.state().getOrDefault(LOWEST, ctx.state().get(TARGET))));
        if (result.samples() < MIN_WASH_SECONDS * RATE) {
            return StepResult.fail("zu wenig Propwash im Flug: " + text);
        }
        if (result.fakeSlowSway() > FAKE_SLOW_LIMIT || result.ratio() < RATIO_MIN || result.ratio() > RATIO_MAX) {
            return StepResult.fail("Flashback wackelt anders als live: " + text);
        }
        return StepResult.pass();
    }

    private static void write(Quaterniond[] live, Quaterniond[] exported, double[] ticks, double[] wash, double first) throws IOException {
        Quaterniond[] reference = PathWobble.smooth(live, PathWobble.REFERENCE_SECONDS * RATE);
        double[][] liveDeviation = PathWobble.deviation(reference, live);
        double[][] exportDeviation = PathWobble.deviation(reference, exported);
        StringBuilder out = new StringBuilder("t,wash,live_x,live_y,live_z,fb_x,fb_y,fb_z\n");
        for (int i = 0; i < live.length; i++) {
            out.append(String.format(Locale.ROOT, "%.5f,%.3f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n", (ticks[i] - first) / 20.0, wash[i],
                    liveDeviation[0][i], liveDeviation[1][i], liveDeviation[2][i], exportDeviation[0][i], exportDeviation[1][i], exportDeviation[2][i]));
        }
        Path file = Services.PLATFORM.getGameDir().resolve(CSV);
        Files.writeString(file, out.toString());
    }
}
