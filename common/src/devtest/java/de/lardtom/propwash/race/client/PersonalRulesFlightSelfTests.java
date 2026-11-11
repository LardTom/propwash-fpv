package de.lardtom.propwash.race.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.PersonalRulesDev;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.RaceRulesJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.rescue.RescueSettings;
import de.lardtom.propwash.rescue.ReturnPlan;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class PersonalRulesFlightSelfTests {
    private static final String PREFIX = RaceRulesSelfTests.PREFIX;
    private static final long S = 1_000_000_000L;
    private static final int PERSONAL_GRAVITY = 50;
    private static final int HOME_TIMEOUT_TICKS = 200;
    private static final String FUTURE = "flight_future";
    private static final String PHASE = "flight_phase";
    private static final String HOME_START = "flight_home_start";
    private static final String WAIT = "warten";
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static final AtomicReference<@Nullable Integer> HOME_DRONE = new AtomicReference<>();
    private static final AtomicReference<@Nullable Integer> RESCUE_RULE = new AtomicReference<>();

    private PersonalRulesFlightSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + "Server stellt Wind während der Runde um: ohne persönliche Regeln rennfrei, persönliche Abweichung zählt gegen "
                + "den Serverwert des Moments", 1200, PersonalRulesFlightSelfTests::switchStep);
        SelfTest.clientStep(PREFIX + "Heimflug ohne Pilot fliegt mit den persönlichen Regeln des Besitzers", 1200, PersonalRulesFlightSelfTests::homeStep);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
    }

    private static void server(SelfTest.StepContext ctx, Function<MinecraftServer, @Nullable String> action) {
        RESULT.set(null);
        ctx.state().put(FUTURE, ctx.onServer(server -> RESULT.set(action.apply(server))));
    }

    private static @Nullable StepResult waitFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        String result = RESULT.getAndSet(null);
        if (WAIT.equals(result)) {
            ctx.state().put(PHASE, phase(ctx) - 1);
            return StepResult.pending();
        }
        return result == null ? null : StepResult.fail(result);
    }

    static String rated(MinecraftServer server, UUID player, long nanos) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "keine Bestenliste";
        }
        Leaderboard board = registry.board(RaceRulesSelfTests.course(server));
        for (Leaderboard.Entry entry : board.storedTimes()) {
            if (!entry.player().equals(player) || entry.nanos() != nanos) {
                continue;
            }
            if (board.scoring().clean(entry)) {
                return "rennfrei";
            }
            List<String> reasons = new ArrayList<>();
            for (AssistDetail detail : entry.details()) {
                reasons.add(detail.reason().key() + ":" + detail.subject() + "=" + detail.value() + "/" + detail.reference());
            }
            return "mit Flughilfe[" + String.join(",", reasons) + "]";
        }
        return "fehlt";
    }

    private static @Nullable String switchLaps(MinecraftServer server) {
        ServerPlayer player = player(server);
        UUID id = player.getUUID();
        CourseRegistry.Course course = RaceRulesSelfTests.course(server);
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        PersonalRulesDev.clear(server);
        try {
            WorldRules.set(server, WorldRuleSpecs.WIND, 1);
            String flown = RaceRulesJoinCheck.fly(course, player, RaceDataJoinCheck.origin(player), 6 * S,
                    () -> WorldRules.set(server, WorldRuleSpecs.WIND, 0));
            if (flown != null) {
                return "Runde mit Wechsel auf Wind 0: " + flown;
            }
            String plain = rated(server, id, 6 * S);
            if (!plain.equals("rennfrei")) {
                return "ohne persönliche Regeln, Server stellt Wind während der Runde von 1 auf 0: " + plain + " statt rennfrei";
            }
            WorldRules.set(server, WorldRuleSpecs.WIND, 1);
            PersonalRulesDev.personal(server, id, Map.of(WorldRuleSpecs.WIND, 0));
            flown = RaceRulesJoinCheck.fly(course, player, RaceDataJoinCheck.origin(player), 11 * S / 2,
                    () -> WorldRules.set(server, WorldRuleSpecs.WIND, 0));
            if (flown != null) {
                return "Runde mit persönlichem Wind 0 und Wechsel auf Wind 0: " + flown;
            }
            String personal = rated(server, id, 11 * S / 2);
            if (!personal.equals("mit Flughilfe[personal_rules:wind=0/1]")) {
                return "persönlicher Wind 0, Server erst Wind 1, dann Wind 0: " + personal + " statt mit Flughilfe[personal_rules:wind=0/1]";
            }
            return null;
        } finally {
            PersonalRulesDev.clear(server);
            WorldRules.set(server, WorldRuleSpecs.WIND, WorldRuleSpecs.WIND.defaultValue());
        }
    }

    private static StepResult switchStep(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (phase(ctx) == 0) {
            server(ctx, PersonalRulesFlightSelfTests::switchLaps);
            next(ctx);
            return StepResult.pending();
        }
        ctx.log("Server-Wind 1 → 0 in der Runde: ohne persönliche Regeln 6,0 s rennfrei, persönlicher Wind 0 5,5 s mit Flughilfe "
                + "„persönliche Regeln: Wind 0 statt 1“");
        return StepResult.pass();
    }

    private static @Nullable String startHome(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        RESCUE_RULE.set(WorldRules.get(server, WorldRuleSpecs.AUTONOMOUS_RESCUE));
        WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_RESCUE, 1);
        PersonalRulesDev.personal(server, player.getUUID(), Map.of(WorldRuleSpecs.GRAVITY_SCALE, PERSONAL_GRAVITY));
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(player.getX() + 4.0, player.getY() + 12.0, player.getZ() + 4.0, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        HOME_DRONE.set(drone.getId());
        drone.setReturnPlan(ReturnPlan.of(player.getUUID(), player.getX(), player.getY(), player.getZ(), false, new RescueSettings()));
        return null;
    }

    private static @Nullable QuadcopterEntity homeDrone(MinecraftServer server) {
        Integer id = HOME_DRONE.get();
        return id != null && player(server).level().getEntity(id) instanceof QuadcopterEntity drone ? drone : null;
    }

    private static @Nullable String checkHome(MinecraftServer server, boolean late) {
        QuadcopterEntity drone = homeDrone(server);
        if (drone == null) {
            return "Drohne fehlt";
        }
        DroneSim sim = AutonomousReturns.sim(drone);
        if (sim == null || !AutonomousReturns.flying(drone)) {
            return late ? "Heimflug ohne Pilot startet nicht" : WAIT;
        }
        double gravity = sim.tuning().gravityScale * 100.0;
        int serverGravity = WorldRules.get(server, WorldRuleSpecs.GRAVITY_SCALE);
        if (Math.abs(gravity - PERSONAL_GRAVITY) > 0.01) {
            return String.format(Locale.ROOT, "Heimflug fliegt mit Schwerkraft %.0f %% statt der persönlichen %d %% des Besitzers (Server %d %%)", gravity,
                    PERSONAL_GRAVITY, serverGravity);
        }
        return null;
    }

    private static void stopHome(MinecraftServer server) {
        QuadcopterEntity drone = homeDrone(server);
        if (drone != null) {
            drone.setReturnPlan(null);
            drone.discard();
        }
        HOME_DRONE.set(null);
        Integer rule = RESCUE_RULE.getAndSet(null);
        WorldRules.set(server, WorldRuleSpecs.AUTONOMOUS_RESCUE, rule == null ? WorldRuleSpecs.AUTONOMOUS_RESCUE.defaultValue() : rule);
        PersonalRulesDev.clear(server);
    }

    private static StepResult homeStep(SelfTest.StepContext ctx) {
        StepResult result = homePhase(ctx);
        if (result.status() == StepResult.Status.FAIL) {
            ctx.onServer(PersonalRulesFlightSelfTests::stopHome);
        }
        return result;
    }

    private static StepResult homePhase(SelfTest.StepContext ctx) {
        StepResult waiting = waitFuture(ctx);
        if (waiting != null) {
            return waiting;
        }
        switch (phase(ctx)) {
            case 0 -> {
                server(ctx, PersonalRulesFlightSelfTests::startHome);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                int start = (int) ctx.state().computeIfAbsent(HOME_START, key -> ctx.tick());
                boolean late = ctx.tick() - start > HOME_TIMEOUT_TICKS;
                server(ctx, server -> checkHome(server, late));
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                server(ctx, server -> {
                    stopHome(server);
                    return null;
                });
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                ctx.log("Heimflug ohne Pilot: Server-Schwerkraft 100 %, persönliche " + PERSONAL_GRAVITY + " % des Besitzers, Flug am Server mit "
                        + PERSONAL_GRAVITY + " %");
                return StepResult.pass();
            }
        }
    }
}
