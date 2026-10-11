package de.lardtom.propwash.compat.iris.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.drone.client.render.FpvProps;
import de.lardtom.propwash.drone.client.render.QuadcopterRenderer;

import java.util.Map;

public final class IrisSelfTests {
    private static final String PHASE = "phase";
    private static final String FUTURE = "future";
    private static final String SINCE = "since";
    private static final String COUNTS = "counts";
    private static final String CONFIG = "config";
    private static final String PROBLEM = "problem";
    private static final int MEASURE_TICKS = 40;
    private static final int OFF_TICKS = 20;

    private IrisSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("iris: eigene FPV-Drohne im Schattenpass – keine FPV-Props, echter Schatten", 2000, IrisSelfTests::shadowStep);
    }

    private static StepResult shadowStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        String phase = (String) state.getOrDefault(PHASE, "check");
        switch (phase) {
            case "check" -> {
                if (!IrisControl.loaded()) {
                    ctx.log("Iris nicht geladen, übersprungen");
                    return StepResult.pass();
                }
                if (!IrisControl.packInUse()) {
                    ctx.log("Iris ohne aktives Shaderpack, übersprungen");
                    return StepResult.pass();
                }
                DroneRenderConfig saved = DroneRenderConfig.get().copy();
                state.put(CONFIG, saved);
                DroneRenderConfig config = saved.copy();
                config.fpvProps = true;
                config.shadow = true;
                DroneRenderConfig.CONFIG.set(config);
                state.put(PHASE, "arrive");
                return StepResult.pending();
            }
            case "arrive" -> {
                StepResult arrived = IrisSite.arrive(ctx);
                if (arrived.status() == StepResult.Status.FAIL) {
                    return finish(ctx, arrived.message());
                }
                if (arrived.status() == StepResult.Status.PENDING) {
                    return arrived;
                }
                state.put(IrisSite.ARRIVE_TICK, ctx.tick());
                state.put(FUTURE, ctx.onServer(server -> IrisSite.start(server, IrisSite.View.FPV, IrisSite.Spot.LAND, IrisSite.MORNING, false)));
                state.put(PHASE, "climb");
                return StepResult.pending();
            }
            case "climb" -> {
                StepResult server = IrisSite.awaitServer(ctx, FUTURE, "FPV-Start am See");
                if (server.status() == StepResult.Status.FAIL) {
                    return finish(ctx, server.message());
                }
                if (server.status() == StepResult.Status.PENDING) {
                    return server;
                }
                StepResult climbed = IrisSite.climb(ctx, IrisSite.View.FPV);
                if (climbed.status() == StepResult.Status.FAIL) {
                    return finish(ctx, climbed.message());
                }
                if (climbed.status() == StepResult.Status.PENDING) {
                    return climbed;
                }
                state.put(COUNTS, counts());
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "measure");
                return StepResult.pending();
            }
            case "measure" -> {
                IrisSite.hold(ctx);
                if (ctx.tick() - (int) state.get(SINCE) < MEASURE_TICKS) {
                    return StepResult.pending();
                }
                int[] before = (int[]) state.get(COUNTS);
                int[] now = counts();
                int calls = now[0] - before[0];
                int bodies = now[1] - before[1];
                int blurs = now[2] - before[2];
                ctx.log("Schattenpass in " + MEASURE_TICKS + " Ticks: FPV-Props " + calls + "-mal angefragt, Drohne " + bodies
                        + "-mal als Schattenkörper; Prop-Scheiben im Bild " + blurs + "-mal");
                if (calls != 0) {
                    return finish(ctx, "FPV-Props im Schattenpass angefragt: " + calls);
                }
                if (bodies <= 0) {
                    return finish(ctx, "eigene Drohne wirft keinen Schatten (kein Schattenkörper im Schattenpass)");
                }
                if (blurs <= 0) {
                    return finish(ctx, "Prop-Scheiben im FPV-Bild fehlen mit Shaderpack");
                }
                DroneRenderConfig config = DroneRenderConfig.get().copy();
                config.shadow = false;
                DroneRenderConfig.CONFIG.set(config);
                state.put(COUNTS, counts());
                state.put(SINCE, ctx.tick());
                state.put(PHASE, "off");
                return StepResult.pending();
            }
            case "off" -> {
                IrisSite.hold(ctx);
                if (ctx.tick() - (int) state.get(SINCE) < OFF_TICKS) {
                    return StepResult.pending();
                }
                int[] before = (int[]) state.get(COUNTS);
                int[] now = counts();
                if (now[1] != before[1] || now[0] != before[0]) {
                    return finish(ctx, "Schatten aus: Drohne trotzdem im Schattenpass (" + (now[1] - before[1]) + ")");
                }
                return finish(ctx, null);
            }
            case "cleanup" -> {
                StepResult server = IrisSite.awaitServer(ctx, FUTURE, "Aufräumen");
                if (server.status() == StepResult.Status.PENDING) {
                    return server;
                }
                String problem = (String) state.get(PROBLEM);
                if (server.status() == StepResult.Status.FAIL) {
                    return StepResult.fail(problem == null ? server.message() : problem + "; " + server.message());
                }
                return problem == null ? StepResult.pass() : StepResult.fail(problem);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static int[] counts() {
        return new int[]{FpvProps.shadowPassCalls(), QuadcopterRenderer.fpvShadowBodies(), FpvProps.blursDrawn()};
    }

    private static StepResult finish(SelfTest.StepContext ctx, String problem) {
        Map<String, Object> state = ctx.state();
        if (problem != null) {
            state.put(PROBLEM, problem);
        }
        if (state.get(CONFIG) instanceof DroneRenderConfig saved) {
            DroneRenderConfig.CONFIG.set(saved);
        }
        IrisSite.stopFlight(ctx);
        state.put(FUTURE, ctx.onServer(IrisSite::tearDown));
        state.put(PHASE, "cleanup");
        return StepResult.pending();
    }
}
