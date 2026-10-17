package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class StickOverlaySelfTests {
    private static final long FRAME_NANOS = 16_666_667L;
    private static final float THROTTLE = 1.0F;
    private static final float YAW = 1.0F;
    private static final float PITCH = -0.5F;
    private static final float ROLL = -0.5F;
    private static final Map<StickMode, int[]> EXPECTED = new EnumMap<>(StickMode.class);

    static {
        EXPECTED.put(StickMode.MODE_1, new int[]{-5, 3, 8, -6});
        EXPECTED.put(StickMode.MODE_2, new int[]{-5, -6, 8, 3});
        EXPECTED.put(StickMode.MODE_3, new int[]{-14, 3, 17, -6});
        EXPECTED.put(StickMode.MODE_4, new int[]{-14, -6, 17, 3});
    }

    private StickOverlaySelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("stickmode: Stick-Overlay im OSD zeigt Mode 1 bis 4 sofort", 600, StickOverlaySelfTests::step);
    }

    public static void registerScreenshots() {
        for (StickMode mode : StickMode.values()) {
            Screenshots.step("bildschirmfotos: Stick-Mode, Stick-Overlay im OSD in Mode " + mode.number(), "osd_stick_overlay_mode" + mode.number(), 4,
                    overlayScene(mode));
        }
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) {
                return StepResult.fail("kein Spieler");
            }
            if (minecraft.gui.screen() != null) {
                minecraft.gui.setScreen(null);
            }
            state.put("saved", InputConfig.get().stickMode);
            state.put("errors", OsdHud.errors());
            state.put("index", 0);
            begin(player, StickMode.values()[0]);
            state.put("frames", OsdHud.renderedFrames());
            return StepResult.pending();
        }
        StickMode saved = (StickMode) state.get("saved");
        int errors = OsdHud.errors() - (Integer) state.get("errors");
        if (errors > 0) {
            end(saved);
            return StepResult.fail(errors + " OSD-Fehler beim Zeichnen (siehe Log)");
        }
        int index = (Integer) state.get("index");
        StickMode mode = StickMode.values()[index];
        if (OsdHud.renderedFrames() - (Integer) state.get("frames") < 3) {
            return StepResult.pending();
        }
        OsdRenderer renderer = OsdHud.renderer();
        if (!renderer.wasDrawn(OsdElement.STICK_OVERLAY)) {
            end(saved);
            return StepResult.fail("Stick-Overlay in Mode " + mode.number() + " nicht gezeichnet");
        }
        int[] shown = renderer.shownSticks();
        int[] expected = EXPECTED.get(mode);
        if (!Arrays.equals(shown, expected)) {
            end(saved);
            return StepResult.fail("Stick-Overlay in Mode " + mode.number() + " zeigt " + Arrays.toString(shown) + " statt " + Arrays.toString(expected)
                    + " (Gas oben, Gier rechts, Nick halb zurück, Roll halb links)");
        }
        ctx.log("stickmode: OSD Mode " + mode.number() + " Knüppelpunkte " + Arrays.toString(shown));
        if (index + 1 < StickMode.values().length) {
            state.put("index", index + 1);
            InputConfig.get().stickMode = StickMode.values()[index + 1];
            state.put("frames", OsdHud.renderedFrames());
            return StepResult.pending();
        }
        end(saved);
        return StepResult.pass();
    }

    private static void begin(Player player, StickMode mode) {
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(OsdPreset.FULL);
        OsdConfig.Slot slot = config.slot(OsdElement.STICK_OVERLAY);
        slot.enabled = true;
        slot.scale = OsdConfig.MAX_SCALE;
        OsdHud.overrideConfig(config);
        OsdHud.sampler().setOverride(new StickSource(player.getX(), player.getY(), player.getZ()));
        InputConfig.get().stickMode = mode;
    }

    private static void end(StickMode saved) {
        OsdHud.sampler().setOverride(null);
        OsdHud.overrideConfig(null);
        InputConfig.get().stickMode = saved;
    }

    private static Screenshots.Scene overlayScene(StickMode mode) {
        return new Screenshots.Scene() {
            private StickMode saved = StickMode.DEFAULT;
            private int frames = -1;

            @Override
            public StepResult prepare(SelfTest.StepContext ctx) {
                Minecraft minecraft = Minecraft.getInstance();
                Player player = minecraft.player;
                if (player == null) {
                    return StepResult.fail("kein Spieler");
                }
                if (this.frames < 0) {
                    if (minecraft.gui.screen() != null) {
                        minecraft.gui.setScreen(null);
                    }
                    this.saved = InputConfig.get().stickMode;
                    begin(player, mode);
                    this.frames = OsdHud.renderedFrames();
                    return StepResult.pending();
                }
                if (OsdHud.renderedFrames() - this.frames < 3) {
                    return StepResult.pending();
                }
                int[] shown = OsdHud.renderer().shownSticks();
                if (!Arrays.equals(shown, EXPECTED.get(mode))) {
                    return StepResult.fail("Stick-Overlay in Mode " + mode.number() + " zeigt " + Arrays.toString(shown));
                }
                float[] bounds = OsdHud.renderer().bounds(OsdElement.STICK_OVERLAY);
                if (bounds != null) {
                    ctx.log("stickmode: Stick-Overlay Mode " + mode.number() + " bei " + Arrays.toString(bounds) + " (GUI-Koordinaten)");
                }
                return StepResult.pass();
            }

            @Override
            public void finish(SelfTest.StepContext ctx) {
                end(this.saved);
                this.frames = -1;
            }
        };
    }

    private static final class StickSource implements OsdSampler.Source {
        private final DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 11L);
        private final PilotInput input = new PilotInput();
        private final SimEnvironment environment = new SimEnvironment();
        private final de.lardtom.propwash.sim.StepResult result = new de.lardtom.propwash.sim.StepResult();
        private int steps;

        StickSource(double x, double y, double z) {
            this.sim.reset(x + 6.0, y + 4.0, z - 10.0, 0.0);
            this.environment.groundDistance = 4.0;
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            this.input.armSwitch = this.steps >= 3;
            this.input.throttle = this.steps >= 6 ? 0.45 : 0.0;
            this.sim.step(FRAME_NANOS, this.input, this.environment, this.result);
            this.steps++;
            d.applySim(this.sim);
            d.craftName = "Selftest";
            d.radioConnected = true;
            d.linkStrength = 0.9;
            d.stickThrottle = THROTTLE;
            d.stickYaw = YAW;
            d.stickPitch = PITCH;
            d.stickRoll = ROLL;
        }
    }
}
