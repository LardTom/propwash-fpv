package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.CovCrashRulesJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class CovCrashRulesSelfTests {
    public static final String GROUP = "drone: Absturz ohne Disarm mit Welt-Regeln aus";
    private static final int SESSION_TICKS = 200;
    private static final String FUTURE = "future";
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);

    private CovCrashRulesSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + " – Welt-Regeln aus, Testfeld mit Wand, eigene Sitzung", 400, CovCrashRulesSelfTests::prepareStep);
        SelfTest.clientStep(GROUP + " – " + CovCrashRulesRun.cases() + " Abstürze im Einzelspieler wie im Beitrittstest: kein Schub, kein Wegfahren, "
                + "kein Rücksetzen", CovCrashRulesRun.TIMEOUT_TICKS + 200, CovCrashRulesSelfTests::runStep);
        SelfTest.clientStep(GROUP + " – Welt-Regeln und Testfeld zurück", 300, CovCrashRulesSelfTests::cleanupStep);
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": kein Serverauftrag");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    static StepResult prepareStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        if (!ctx.state().containsKey(FUTURE)) {
            session.clearTestInput();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                QuadcopterEntity drone = CovCrashRulesJoinCheck.prepare(serverPlayer(server), ORIGINAL);
                if (drone == null) {
                    throw new IllegalStateException("Drohne nicht erzeugt");
                }
                DRONE.set(drone.getId());
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Regeln, Testfeld und Sitzung");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        QuadcopterEntity drone = session.drone();
        if (drone == null || drone.getId() != DRONE.get() || !session.isFpv() || session.sim() == null) {
            return ctx.tick() > SESSION_TICKS ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
        }
        ctx.log("FPV-Sitzung mit Drohne " + drone.getId() + ", Welt-Regeln crash_disarm, crash_damage, prop_strikes aus, battery_scale 0");
        return StepResult.pass();
    }

    static StepResult runStep(SelfTest.StepContext ctx) {
        String report = CovCrashRulesRun.observe(ctx.state(), ctx.tick());
        if (report == null) {
            return StepResult.pending();
        }
        for (String line : CovCrashRulesRun.lines(ctx.state())) {
            ctx.log(line);
        }
        int passed = CovCrashRulesRun.passed(ctx.state());
        int resets = CovCrashRulesRun.resets(ctx.state());
        String summary = passed + " von " + CovCrashRulesRun.cases() + " Abstürzen in Ruhe, Rücksetzer vom Server " + resets;
        boolean rules = report.contains("crasharm_rules=applied");
        return rules && passed == CovCrashRulesRun.cases() && resets == 0 ? StepResult.pass()
                : StepResult.fail(summary + (rules ? "" : ", Welt-Regeln am Client nicht angekommen") + "\n" + report);
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        int own = DRONE.get();
        if (!ctx.state().containsKey(FUTURE)) {
            session.clearTestInput();
            if (own >= 0 && session.drone() != null && session.drone().getId() == own) {
                session.requestStop();
            }
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = serverPlayer(server);
                ServerLevel level = player.level();
                if (own >= 0 && level.getEntity(own) instanceof QuadcopterEntity drone) {
                    DronePiloting.release(drone);
                    drone.discard();
                }
                List<Map.Entry<BlockPos, BlockState>> entries = List.copyOf(ORIGINAL.entrySet());
                for (int i = entries.size() - 1; i >= 0; i--) {
                    level.setBlockAndUpdate(entries.get(i).getKey(), entries.get(i).getValue());
                }
                ORIGINAL.clear();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                CovCrashRulesJoinCheck.restoreRules(server);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Welt-Regeln und Testfeld zurück");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        QuadcopterEntity flown = session.drone();
        if (flown != null && flown.getId() == own) {
            return ctx.tick() > SESSION_TICKS ? StepResult.fail("Sitzung mit der Testdrohne läuft nach dem Abbau weiter") : StepResult.pending();
        }
        DRONE.set(-1);
        return StepResult.pass();
    }
}
