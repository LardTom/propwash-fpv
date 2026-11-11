package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.osd.client.OsdRenderer;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.ui.client.LayoutProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CovMotorHeatSelfTests {
    public static final String STEP = MotorHeatSelfTests.PREFIX + " – zusammen mit VTX-Hitze: ESC- und VTX-Warnung beide lesbar, ohne Überschneidung";
    private static final double ESC_HOT_C = 147.5;
    private static final double COOL_C = 60.0;
    private static final long TICK_NANOS = 50_000_000L;
    private static final double SWEEP_SECONDS = 2.0 * OsdWarnings.CYCLE_SECONDS * 2.0;
    private static final double SWEEP_STEP = 0.1;
    private static final String SOURCE = "cov_heat_source";

    private CovMotorHeatSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 200, CovMotorHeatSelfTests::bothStep);
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Abdeckung Motor-Hitze – ESC- und VTX-Warnung, ESC sichtbar", "cov_motor_heat_osd_esc_vtx_01", 2,
                    new Scene(OsdWarnings.Warning.ESC_HOT));
            Screenshots.step("bildschirmfotos: Abdeckung Motor-Hitze – ESC- und VTX-Warnung, VTX sichtbar", "cov_motor_heat_osd_esc_vtx_02", 2,
                    new Scene(OsdWarnings.Warning.VTX_HOT));
        }
    }

    static final class HotBoth implements OsdSampler.Source {
        final DroneSim sim = new DroneSim(DroneBuild.CINEWHOOP, new FlightControllerConfig(), new SimTuning(), 11L);
        private final PilotInput input = new PilotInput();
        private final SimEnvironment environment = new SimEnvironment();
        private final de.lardtom.propwash.sim.StepResult result = new de.lardtom.propwash.sim.StepResult();
        private final Vector3d position = new Vector3d();
        private final double homeX;
        private final double homeY;
        private final double homeZ;
        volatile double blink = Double.NaN;

        HotBoth(double homeX, double homeY, double homeZ) {
            this.homeX = homeX;
            this.homeY = homeY;
            this.homeZ = homeZ;
            this.sim.reset(homeX, homeY + 150.0, homeZ, 0.0);
            this.sim.step(0L, this.input, this.environment, this.result);
            this.input.armSwitch = true;
            this.sim.step(0L, this.input, this.environment, this.result);
            this.input.throttle = 0.4;
            this.tick();
        }

        void tick() {
            SimSnapshot snapshot = new SimSnapshot();
            this.sim.copyStateTo(snapshot);
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                snapshot.motorTempC[i] = COOL_C;
                snapshot.escTempC[i] = ESC_HOT_C;
            }
            this.sim.copyStateFrom(snapshot);
            this.sim.step(TICK_NANOS, this.input, this.environment, this.result);
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            d.applySim(this.sim);
            this.sim.getPosition(this.position);
            d.hasPosition = true;
            d.x = this.position.x;
            d.y = this.position.y;
            d.z = this.position.z;
            d.hasHome = true;
            d.homeX = this.homeX;
            d.homeY = this.homeY;
            d.homeZ = this.homeZ;
            d.craftName = "Selftest";
            d.radioConnected = true;
            d.linkStrength = 0.9;
            d.videoSystem = VideoSystem.DIGITAL;
            d.videoSignal = 0.9;
            d.vtxHot = true;
            if (Double.isFinite(this.blink)) {
                d.blinkSeconds = this.blink;
            }
        }
    }

    private static int width(Minecraft mc) {
        return mc.getWindow().getGuiScaledWidth();
    }

    private static StepResult bothStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return StepResult.fail("kein Spieler");
        }
        HotBoth hot = new HotBoth(player.getX(), player.getY(), player.getZ());
        for (int i = 0; i < 20; i++) {
            hot.tick();
        }
        if (!hot.sim.telemetry().escDerating) {
            return StepResult.fail("ESC regelt bei " + hot.sim.telemetry().escTempC[0] + " °C nicht ab");
        }
        String esc = Component.translatable(OsdWarnings.Warning.ESC_HOT.translationKey()).getString();
        String vtx = Component.translatable(OsdWarnings.Warning.VTX_HOT.translationKey()).getString();
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(OsdPreset.FULL);
        config.slot(OsdElement.WARNINGS).enabled = true;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        OsdGrid grid = OsdGrid.screen(screenWidth, height);
        float scale = grid.textScale(config.scale);
        Set<String> seen = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        int frames = 0;
        for (double t = 0.05; t < SWEEP_SECONDS; t += SWEEP_STEP) {
            OsdData data = new OsdData();
            data.active = true;
            hot.blink = t;
            hot.fill(data);
            List<OsdWarnings.Warning> warnings = new ArrayList<>();
            OsdWarnings.collect(data, warnings);
            if (!warnings.contains(OsdWarnings.Warning.ESC_HOT) || !warnings.contains(OsdWarnings.Warning.VTX_HOT)) {
                return StepResult.fail("Warnungen ohne ESC und VTX: " + warnings);
            }
            int index = OsdWarnings.visibleIndex(warnings, data.blinkSeconds);
            if (index < 0 || !OsdWarnings.blinkOn(data.blinkSeconds)) {
                continue;
            }
            OsdWarnings.Warning shown = warnings.get(index);
            String text = Component.translatable(shown.translationKey()).getString();
            OsdRenderer renderer = new OsdRenderer();
            LayoutProbe.capture(mc, graphics -> renderer.render(graphics, mc.font, data, config, grid, scale, null, false), List.of());
            frames++;
            if (!renderer.wasDrawn(OsdElement.WARNINGS)) {
                problems.add(String.format(Locale.ROOT, "Warnung „%s“ bei t=%.1f s nicht gezeichnet", text, t));
                continue;
            }
            float[] b = renderer.bounds(OsdElement.WARNINGS);
            float width = (mc.font.width(text) + 1) * scale;
            if (Math.abs((b[2] - b[0]) - width) > Math.max(2.0F, width * 0.15F)) {
                problems.add(String.format(Locale.ROOT, "Warnung „%s“ bei t=%.1f s %.1f breit statt %.1f", text, t, b[2] - b[0], width));
            }
            if (b[0] < 0 || b[2] > width(mc) || b[1] < 0 || b[3] > height) {
                problems.add(String.format(Locale.ROOT, "„%s“ ragt aus dem Bild bei t=%.1f s", text, t));
            }
            seen.add(text);
            for (OsdElement other : OsdElement.values()) {
                if (other == OsdElement.WARNINGS || !renderer.wasDrawn(other) || !other.movable()) {
                    continue;
                }
                float[] o = renderer.bounds(other);
                if (b[0] < o[2] && o[0] < b[2] && b[1] < o[3] && o[1] < b[3]) {
                    problems.add(String.format(Locale.ROOT, "„%s“ überschneidet %s bei t=%.1f s", text, other.key(), t));
                }
            }
        }
        if (!seen.contains(esc) || !seen.contains(vtx)) {
            problems.add("nicht beide Warntexte gezeigt: " + seen + " (erwartet „" + esc + "“ und „" + vtx + "“)");
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems.subList(0, Math.min(problems.size(), 8))));
        }
        ctx.log(String.format(Locale.ROOT, "Vorlage Voll mit Warnungen, ESC %.1f °C und VTX heiß: in %d Bildern über %.1f s abwechselnd „%s“ und „%s“, "
                + "ohne Überschneidung mit anderen OSD-Elementen (GUI %d × %d)", ESC_HOT_C, frames, SWEEP_SECONDS, esc, vtx, screenWidth, height));
        return StepResult.pass();
    }

    private record Scene(OsdWarnings.Warning wanted) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(SOURCE)) {
                Player player = Minecraft.getInstance().player;
                if (player == null) {
                    return StepResult.fail("kein Spieler");
                }
                HotBoth hot = new HotBoth(player.getX(), player.getY(), player.getZ());
                OsdHud.sampler().setOverride(hot);
                state.put(SOURCE, hot);
                return StepResult.pending();
            }
            HotBoth hot = (HotBoth) state.get(SOURCE);
            hot.tick();
            OsdData data = OsdHud.sampler().data();
            List<OsdWarnings.Warning> shown = new ArrayList<>();
            OsdWarnings.collect(data, shown);
            int index = OsdWarnings.visibleIndex(shown, data.blinkSeconds);
            boolean visible = index >= 0 && shown.get(index) == this.wanted && data.blinkSeconds % OsdWarnings.CYCLE_SECONDS < 0.5;
            if (!visible) {
                return ctx.tick() > 200 ? StepResult.fail("OSD zeigt " + this.wanted + " nicht: " + shown) : StepResult.pending();
            }
            ctx.log("Bild mit „" + Component.translatable(this.wanted.translationKey()).getString() + "“, Warnungen " + shown);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (ctx.state().get(SOURCE) instanceof HotBoth hot) {
                hot.tick();
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            OsdHud.sampler().setOverride(null);
        }
    }
}
