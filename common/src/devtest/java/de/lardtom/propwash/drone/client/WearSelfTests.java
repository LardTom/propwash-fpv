package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class WearSelfTests {
    private static final String FUTURE = "wear_future";
    private static final String RESULT = "wear_result";
    private static final int SYNC_TICKS = 160;

    private WearSelfTests() {
    }

    static StepResult crashWearStep(SelfTest.StepContext ctx, int droneId) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null) {
            return StepResult.fail("keine aktive Sitzung nach dem Absturz");
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
        PartWear wear = drone.wear();
        if (wear.isNew() || !sim.wear().equals(wear)) {
            return ctx.tick() > SYNC_TICKS ? StepResult.fail("Absturz hat keine Bauteile beschädigt oder das Flugmodell kennt den Zustand nicht: Drohne "
                    + wear + ", Flugmodell " + sim.wear()) : StepResult.pending();
        }
        if (!ctx.state().containsKey(FUTURE)) {
            AtomicReference<String> result = new AtomicReference<>();
            PartWear clientWear = wear;
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                if (!(player.level().getEntity(droneId) instanceof QuadcopterEntity serverDrone)) {
                    result.set("Drohne auf dem Server verschwunden");
                    return;
                }
                PartWear serverWear = serverDrone.wear();
                for (int slot = 0; slot < PartWear.SLOTS; slot++) {
                    if (serverWear.slot(slot) >= PartWear.NEW) {
                        result.set("Platz " + slot + " nach dem Absturz unbeschädigt: " + serverWear);
                        return;
                    }
                }
                if (!serverWear.equals(clientWear)) {
                    result.set("Server und Client uneins: " + serverWear + " / " + clientWear);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Serverprüfung: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        String problem = ((AtomicReference<String>) ctx.state().get(RESULT)).get();
        if (problem != null) {
            return StepResult.fail(problem);
        }
        ctx.log(String.format(Locale.ROOT, "Zustand nach dem Absturz: Props %.0f %%, Motoren %.0f %%, Rahmen %.0f %%, Kamera %.0f %%, FC %.1f %%, Akku %.1f %%",
                wear.worstProp() * 100.0F, wear.worstMotor() * 100.0F, wear.frame() * 100.0F, wear.video() * 100.0F, wear.stack() * 100.0F,
                wear.battery() * 100.0F));
        return StepResult.pass();
    }
}
