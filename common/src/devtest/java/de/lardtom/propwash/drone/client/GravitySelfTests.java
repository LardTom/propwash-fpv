package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimTuning;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GravitySelfTests {
    private static final String PHASE = "gravity_phase";
    private static final String HOVER = "gravity_hover";
    private static final String SINCE = "gravity_since";
    private static final String FUTURE = "gravity_future";
    private static final String FLAGGED = "gravity_flagged";
    private static final int PERCENT = 130;
    private static final double EARTH = new SimTuning().gravity();
    private static final int WAIT_TICKS = 60;

    private GravitySelfTests() {
    }

    static StepResult flightStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        Map<String, Object> state = ctx.state();
        if (sim == null) {
            return restore(ctx, StepResult.fail("keine aktive Sitzung"));
        }
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            if (sim.gravity() != EARTH) {
                return StepResult.fail("Schwerkraft vor dem Test " + sim.gravity() + " statt " + EARTH);
            }
            state.put(HOVER, sim.telemetry().hoverThrottleStick);
            state.put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, PERCENT)));
            state.put(SINCE, ctx.tick());
            state.put(PHASE, 1);
            return StepResult.pending();
        }
        int waited = ctx.tick() - (int) state.get(SINCE);
        double factor = PERCENT / 100.0;
        if (phase == 1) {
            if (ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) != PERCENT || Math.abs(sim.gravity() - EARTH * factor) > 1e-9) {
                return waited < WAIT_TICKS ? StepResult.pending()
                        : restore(ctx, StepResult.fail("Schwerkraft im Flug " + sim.gravity() + " statt " + EARTH * factor
                        + " (Regel am Client " + ClientWorldRules.INSTANCE.get(WorldRuleSpecs.GRAVITY_SCALE) + ")"));
            }
            double before = (double) state.get(HOVER);
            double after = sim.telemetry().hoverThrottleStick;
            if (!(after > before + 0.01)) {
                return waited < WAIT_TICKS ? StepResult.pending()
                        : restore(ctx, StepResult.fail("Schwebegas-Schätzung folgt nicht: " + before + " -> " + after));
            }
            if (!ClientWorldRules.INSTANCE.physics().modified()) {
                return restore(ctx, StepResult.fail("Schwerkraft " + PERCENT + " % zählt nicht als veränderte Physik"));
            }
            QuadcopterEntity drone = session.drone();
            if (drone == null) {
                return restore(ctx, StepResult.fail("Drohne der Sitzung fehlt"));
            }
            int droneId = drone.getId();
            AtomicBoolean flagged = new AtomicBoolean();
            state.put(FLAGGED, flagged);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                flagged.set(player.level().getEntity(droneId) instanceof QuadcopterEntity serverDrone && serverDrone.physicsModified());
            }));
            ctx.log(String.format(Locale.ROOT, "Welt-Regel Schwerkraft %d %% im Flug: %.3f m/s², Schwebegas-Schätzung %.1f %% -> %.1f %%",
                    PERCENT, sim.gravity(), 100.0 * before, 100.0 * after));
            state.put(SINCE, ctx.tick());
            state.put(PHASE, 2);
            return StepResult.pending();
        }
        if (phase == 2) {
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (!future.isDone()) {
                return StepResult.pending();
            }
            if (!((AtomicBoolean) state.get(FLAGGED)).get()) {
                if (waited < WAIT_TICKS) {
                    QuadcopterEntity drone = session.drone();
                    int droneId = drone != null ? drone.getId() : -1;
                    AtomicBoolean flagged = (AtomicBoolean) state.get(FLAGGED);
                    state.put(FUTURE, ctx.onServer(server -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                        flagged.set(player.level().getEntity(droneId) instanceof QuadcopterEntity serverDrone && serverDrone.physicsModified());
                    }));
                    return StepResult.pending();
                }
                return restore(ctx, StepResult.fail("Server kennt die veränderte Physik der Drohne nicht"));
            }
            restore(ctx, StepResult.pass());
            state.put(SINCE, ctx.tick());
            state.put(PHASE, 3);
            return StepResult.pending();
        }
        if (sim.gravity() != EARTH || ClientWorldRules.INSTANCE.physics().modified()) {
            return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Schwerkraft nach dem Zurücksetzen " + sim.gravity());
        }
        return StepResult.pass();
    }

    private static StepResult restore(SelfTest.StepContext ctx, StepResult result) {
        ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GRAVITY_SCALE.defaultValue()));
        return result;
    }
}
