package de.lardtom.propwash.compat.iris.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.client.FisheyeSelfTests;
import de.lardtom.propwash.video.client.Headlight;
import de.lardtom.propwash.video.client.VideoBreakupProbe;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import net.minecraft.client.Minecraft;

import java.util.Map;

public final class IrisShowcase {
    private static final String FUTURE = "iris_shot_future";
    private static final String READY = "iris_shot_ready";
    private static final String SWITCHED = "iris_switched";
    private static final int STOP_SETTLE_TICKS = 5;
    private static final int SHADER_SETTLE_TICKS = 40;
    private static final float NOISY_LINK = 0.38F;
    private static final double HOME_AHEAD = 3.5;
    private static boolean startedWithShaders;
    private static DroneRenderConfig savedRender;
    private static VideoConfig savedVideo;

    private IrisShowcase() {
    }

    public static void register() {
        if (!Screenshots.enabled() || !IrisControl.loaded()) {
            return;
        }
        SelfTest.clientStep("bildschirmfotos: Iris – Shaderpack an", 600, ctx -> shaders(ctx, true, true));
        Screenshots.step("bildschirmfotos: Iris – FPV tief über Wasser, Sonne im Rücken", "iris_fpv_water_shader", 30,
                new FlightScene(IrisSite.View.FPV, IrisSite.Spot.WATER, IrisSite.MORNING, false, false));
        Screenshots.step("bildschirmfotos: Iris – FPV tief über Wasser mit Fisheye-Objektiv", "iris_fpv_water_fisheye_shader", 30,
                FisheyeSelfTests.withFisheye(new FlightScene(IrisSite.View.FPV, IrisSite.Spot.WATER, IrisSite.MORNING, false, false)));
        Screenshots.step("bildschirmfotos: Iris – FPV tief über Wiese, Schatten der eigenen Drohne", "iris_fpv_land_shader", 30,
                new FlightScene(IrisSite.View.FPV, IrisSite.Spot.LAND, IrisSite.MORNING, false, false));
        Screenshots.step("bildschirmfotos: Iris – Sichtflug von der Seite, Schatten und Prop-Scheiben", "iris_los_land_shader", 30,
                new FlightScene(IrisSite.View.LOS, IrisSite.Spot.LAND, IrisSite.MORNING, false, false));
        Screenshots.step("bildschirmfotos: Iris – FPV 4:3 mit analogem Rauschen", "iris_fpv_water_4_3_noise_shader", 30,
                new FlightScene(IrisSite.View.FPV, IrisSite.Spot.WATER, IrisSite.MORNING, false, true));
        Screenshots.step("bildschirmfotos: Iris – Item-Rahmen mit Drohne und Gates", "iris_frame_gates_shader", 30, new FrameScene());
        Screenshots.step("bildschirmfotos: Iris – FPV bei Nacht mit LED-Streifen", "iris_fpv_night_shader", 30,
                new FlightScene(IrisSite.View.FPV, IrisSite.Spot.WATER, IrisSite.NIGHT, true, false));
        Screenshots.step("bildschirmfotos: Iris – Sichtflug bei Nacht mit LED-Streifen", "iris_los_night_shader", 30,
                new FlightScene(IrisSite.View.LOS, IrisSite.Spot.LAND, IrisSite.NIGHT, true, false));
        SelfTest.clientStep("bildschirmfotos: Iris – Shaderpack aus", 600, ctx -> shaders(ctx, false, false));
        Screenshots.step("bildschirmfotos: Iris – FPV tief über Wasser ohne Shaderpack", "iris_fpv_water_vanilla", 30,
                new FlightScene(IrisSite.View.FPV, IrisSite.Spot.WATER, IrisSite.MORNING, false, false));
        Screenshots.step("bildschirmfotos: Iris – FPV tief über Wasser mit Fisheye-Objektiv ohne Shaderpack", "iris_fpv_water_fisheye_vanilla", 30,
                FisheyeSelfTests.withFisheye(new FlightScene(IrisSite.View.FPV, IrisSite.Spot.WATER, IrisSite.MORNING, false, false)));
        Screenshots.step("bildschirmfotos: Iris – FPV tief über Wiese ohne Shaderpack", "iris_fpv_land_vanilla", 30,
                new FlightScene(IrisSite.View.FPV, IrisSite.Spot.LAND, IrisSite.MORNING, false, false));
        Screenshots.step("bildschirmfotos: Iris – Sichtflug von der Seite ohne Shaderpack", "iris_los_land_vanilla", 30,
                new FlightScene(IrisSite.View.LOS, IrisSite.Spot.LAND, IrisSite.MORNING, false, false));
        Screenshots.step("bildschirmfotos: Iris – Item-Rahmen mit Drohne und Gates ohne Shaderpack", "iris_frame_gates_vanilla", 30,
                new FrameScene());
        SelfTest.clientStep("bildschirmfotos: Iris – aufräumen", 600, IrisShowcase::cleanupStep);
    }

    private static StepResult shaders(SelfTest.StepContext ctx, boolean enabled, boolean remember) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(SWITCHED)) {
            if (remember) {
                startedWithShaders = IrisControl.shadersEnabled();
            }
            if (IrisControl.shadersEnabled() != enabled) {
                try {
                    IrisControl.enableShaders(enabled);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return StepResult.fail("Iris-Shader nicht umschaltbar: " + e);
                }
            }
            state.put(SWITCHED, ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(SWITCHED) < SHADER_SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (enabled && !IrisControl.packInUse()) {
            return StepResult.fail("kein Shaderpack aktiv: Pack in shaderpacks/ legen und in config/iris.properties wählen");
        }
        if (!enabled && IrisControl.packInUse()) {
            return StepResult.fail("Shaderpack bleibt aktiv");
        }
        ctx.log("Iris: Shaderpack " + (enabled ? "an" : "aus"));
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            IrisSite.stopFlight(ctx);
            state.put(FUTURE, ctx.onServer(IrisSite::tearDown));
            if (startedWithShaders && !IrisControl.shadersEnabled()) {
                try {
                    IrisControl.enableShaders(true);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return StepResult.fail("Iris-Shader nicht wieder an: " + e);
                }
            }
            return StepResult.pending();
        }
        StepResult server = IrisSite.awaitServer(ctx, FUTURE, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        return ctx.tick() > 20 ? StepResult.pass() : StepResult.pending();
    }

    private static final class FlightScene implements Screenshots.Scene {
        private final IrisSite.View view;
        private final IrisSite.Spot spot;
        private final long time;
        private final boolean leds;
        private final boolean noisy;

        private FlightScene(IrisSite.View view, IrisSite.Spot spot, long time, boolean leds, boolean noisy) {
            this.view = view;
            this.spot = spot;
            this.time = time;
            this.leds = leds;
            this.noisy = noisy;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                if (ctx.tick() == 0) {
                    IrisSite.stopFlight(ctx);
                }
                if (ctx.tick() < STOP_SETTLE_TICKS) {
                    return StepResult.pending();
                }
                StepResult arrived = IrisSite.arrive(ctx);
                if (arrived.status() != StepResult.Status.PASS) {
                    return arrived;
                }
                state.put(IrisSite.ARRIVE_TICK, ctx.tick());
                savedRender = DroneRenderConfig.get().copy();
                DroneRenderConfig render = savedRender.copy();
                render.fpvProps = true;
                DroneRenderConfig.CONFIG.set(render);
                savedVideo = VideoSettings.get().copy();
                VideoConfig video = savedVideo.copy();
                video.imageFormat = this.noisy ? ImageFormat.FOUR_THREE : ImageFormat.WIDE;
                VideoSettings.CONFIG.set(video);
                IrisSite.View shown = this.view;
                IrisSite.Spot where = this.spot;
                long at = this.time;
                boolean lit = this.leds;
                state.put(FUTURE, ctx.onServer(server -> IrisSite.start(server, shown, where, at, lit)));
                return StepResult.pending();
            }
            StepResult server = IrisSite.awaitServer(ctx, FUTURE, "Start");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            StepResult climbed = IrisSite.climb(ctx, this.view);
            if (climbed.status() != StepResult.Status.PASS) {
                return climbed;
            }
            if (this.noisy) {
                VideoBreakupProbe.force(NOISY_LINK, VideoSystem.ANALOG);
            }
            if (!state.containsKey(READY)) {
                state.put(READY, ctx.tick());
                if (this.view == IrisSite.View.FPV) {
                    IrisSite.homeAhead(HOME_AHEAD);
                }
            }
            if (ctx.tick() - (int) state.get(READY) <= 10) {
                return StepResult.pending();
            }
            if (this.leds && this.view == IrisSite.View.FPV) {
                ctx.log("Scheinwerfer: an " + Headlight.INSTANCE.active() + ", Overlay " + Headlight.INSTANCE.usesOverlay() + ", Shader-Pass "
                        + Headlight.INSTANCE.usesShaderPass());
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            IrisSite.hold(ctx);
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.noisy) {
                VideoBreakupProbe.clear();
            }
            IrisSite.stopFlight(ctx);
            if (savedRender != null) {
                DroneRenderConfig.CONFIG.set(savedRender);
            }
            if (savedVideo != null) {
                VideoSettings.CONFIG.set(savedVideo);
            }
        }
    }

    private static final class FrameScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                if (ctx.tick() == 0) {
                    IrisSite.stopFlight(ctx);
                }
                if (ctx.tick() < STOP_SETTLE_TICKS) {
                    return StepResult.pending();
                }
                StepResult arrived = IrisSite.arrive(ctx);
                if (arrived.status() != StepResult.Status.PASS) {
                    return arrived;
                }
                state.put(IrisSite.ARRIVE_TICK, ctx.tick());
                state.put(FUTURE, ctx.onServer(server -> IrisSite.closeUp(server, IrisSite.MORNING)));
                return StepResult.pending();
            }
            StepResult server = IrisSite.awaitServer(ctx, FUTURE, "Item-Rahmen");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            return ctx.tick() - (int) state.get(IrisSite.ARRIVE_TICK) > 30 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }
    }
}
