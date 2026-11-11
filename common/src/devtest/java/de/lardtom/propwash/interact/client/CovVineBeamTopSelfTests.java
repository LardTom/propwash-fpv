package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.interact.CovVineArena;
import de.lardtom.propwash.interact.InteractRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CovVineBeamTopSelfTests {
    static final String GROUP = CovVineBeamSelfTests.GROUP + "von oben";
    static final float LATCH_THROTTLE = 0.35F;
    private static final int RESET_TICKS = 60;
    private static final String RUN = "run";

    private CovVineBeamTopSelfTests() {
    }

    public static void register() {
        List<CovVineArena.Flight> flights = CovVineArena.selfTestFlights();
        SelfTest.clientStep(GROUP + ": Sturz auf den Balken mit Ranken, Gas 0 ab dem Treffer – Motoren im Leerlauf (Airmode höchstens kurz), "
                + "Schwerkraft frei, nie im Block", 20 * 400, ctx -> run(ctx, flights));
    }

    private static StepResult run(SelfTest.StepContext ctx, List<CovVineArena.Flight> flights) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run(flights));
        return run.tick(ctx);
    }

    private static final class Run {
        final List<CovVineArena.Flight> flights;
        final List<String> findings = new ArrayList<>();
        int stage;
        int mark;
        int index;
        @Nullable CovVineDropWatch watch;
        @Nullable CompletableFuture<Void> future;

        Run(List<CovVineArena.Flight> flights) {
            this.flights = flights;
        }

        StepResult tick(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            PilotSession session = PilotSession.INSTANCE;
            CompletableFuture<Void> waiting = this.future;
            if (waiting != null) {
                if (waiting.isCompletedExceptionally()) {
                    return this.finish(ctx, StepResult.fail("Server: " + waiting.exceptionNow().getMessage()));
                }
                if (!waiting.isDone()) {
                    return StepResult.pending();
                }
                this.future = null;
            }
            int since = ctx.tick() - this.mark;
            switch (this.stage) {
                case 0 -> {
                    if (ctx.tick() < 10) {
                        return StepResult.pending();
                    }
                    this.future = ctx.onServer(server -> {
                        ServerPlayer player = InteractSelfTests.player(server);
                        ServerLevel level = player.level();
                        int[] base = CovVineBeamSelfTests.BASE.get();
                        if (base == null) {
                            int x = player.getBlockX() + CovVineArena.SCENE_DX;
                            int z = player.getBlockZ();
                            base = new int[]{x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z};
                            CovVineBeamSelfTests.BASE.set(base);
                        }
                        WorldRules.set(server, InteractRules.breakRule(), false);
                        WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, 0);
                        CovVineArena.build(level, base);
                        player.teleportTo(level, base[0] - 6.5, base[1], base[2] - 5.5, Set.of(), -60.0F, 15.0F, true);
                        InteractSelfTests.ARENA.set(new int[]{base[0], base[1], base[2]});
                        InteractSelfTests.fly(player, DroneBuild.FREESTYLE, new Vec3(base[0] + 0.5 - 2.0, base[1], base[2] + 0.5));
                        QuadcopterEntity drone = InteractSelfTests.serverDrone(server);
                        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElseThrow();
                        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                        player.setItemSlot(EquipmentSlot.HEAD, goggles);
                    });
                    state.put(InteractSelfTests.FUTURE, this.future);
                    this.next(ctx, 1);
                }
                case 1 -> {
                    QuadcopterEntity flown = session.drone();
                    if (!session.isFpv() || flown == null || flown.getId() != InteractSelfTests.DRONE_ID.get() || session.sim() == null) {
                        return since > 200 ? this.finish(ctx, StepResult.fail("keine FPV-Sitzung")) : StepResult.pending();
                    }
                    if (ClientInteractRules.dronesBreakBlocks() || ClientWorldRules.INSTANCE.on(WorldRuleSpecs.CRASH_DISARM)) {
                        return since > 100 ? this.finish(ctx, StepResult.fail("Welt-Regeln kommen nicht beim Client an")) : StepResult.pending();
                    }
                    this.next(ctx, 2);
                }
                case 2 -> {
                    DroneSim sim = session.sim();
                    if (sim == null) {
                        return this.finish(ctx, StepResult.fail("Sitzung verloren"));
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= 10, StabMode.ANGLE);
                    if (!sim.telemetry().armed) {
                        return since > 100 ? this.finish(ctx, StepResult.fail("nicht scharf: " + sim.telemetry().armBlockers)) : StepResult.pending();
                    }
                    this.next(ctx, 3);
                }
                case 3 -> {
                    if (this.index >= this.flights.size()) {
                        return this.finish(ctx, this.findings.isEmpty() ? StepResult.pass()
                                : StepResult.fail(this.findings.size() + " von " + this.flights.size() + " Stürzen auffällig: " + String.join(" | ", this.findings)));
                    }
                    Vec3 at = CovVineArena.launch(CovVineBeamSelfTests.BASE.get(), this.flights.get(this.index));
                    this.future = ctx.onServer(server -> {
                        QuadcopterEntity drone = InteractSelfTests.serverDrone(server);
                        drone.setWear(PartWear.PRISTINE);
                        drone.resetTo(at, InteractSelfTests.upright());
                    });
                    this.next(ctx, 4);
                }
                case 4 -> {
                    QuadcopterEntity drone = session.drone();
                    DroneSim sim = session.sim();
                    if (drone == null || sim == null) {
                        return this.finish(ctx, StepResult.fail("Sitzung verloren"));
                    }
                    CovVineArena.Flight flight = this.flights.get(this.index);
                    Vec3 at = CovVineArena.launch(CovVineBeamSelfTests.BASE.get(), flight);
                    float throttle = (float) Math.max(LATCH_THROTTLE, sim.telemetry().hoverThrottleStick);
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
                    if (drone.position().distanceTo(at) > 0.2 || since < 3) {
                        return since > RESET_TICKS ? this.finish(ctx, StepResult.fail("Rücksetzen auf den Startpunkt kommt nicht an")) : StepResult.pending();
                    }
                    CovVineArena.launchFlight(sim, flight);
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
                    this.watch = new CovVineDropWatch(flight, drone, sim);
                    this.next(ctx, 5);
                }
                case 5 -> {
                    QuadcopterEntity drone = session.drone();
                    DroneSim sim = session.sim();
                    Minecraft mc = Minecraft.getInstance();
                    CovVineDropWatch w = this.watch;
                    if (drone == null || sim == null || mc.level == null || w == null) {
                        return this.finish(ctx, StepResult.fail("Sitzung verloren"));
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ACRO);
                    w.tick(mc.level, drone, sim, mc.gameRenderer.mainCamera().position());
                    if (!w.done()) {
                        return StepResult.pending();
                    }
                    String line = w.line(drone, sim, CovVineBeamSelfTests.BASE.get());
                    ctx.log(line);
                    if (w.bad(drone)) {
                        this.findings.add(line);
                    }
                    this.index++;
                    this.next(ctx, 3);
                }
                default -> {
                    return StepResult.fail("unbekannter Schritt " + this.stage);
                }
            }
            return StepResult.pending();
        }

        private StepResult finish(SelfTest.StepContext ctx, StepResult result) {
            if (!ctx.state().containsKey("result")) {
                ctx.state().put("result", result);
                ctx.state().put(InteractSelfTests.PHASE, 0);
            }
            StepResult stopped = InteractSelfTests.stop(ctx, server -> {
                WorldRules.set(server, InteractRules.breakRule(), false);
                WorldRules.set(server, WorldRuleSpecs.CRASH_DISARM, 1);
                int[] base = CovVineBeamSelfTests.BASE.get();
                if (base != null) {
                    CovVineArena.clear(InteractSelfTests.player(server).level(), base);
                }
                InteractSelfTests.ARENA.set(null);
                InteractSelfTests.player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            });
            if (stopped.status() == StepResult.Status.PENDING) {
                return stopped;
            }
            StepResult wanted = (StepResult) ctx.state().get("result");
            return stopped.status() == StepResult.Status.FAIL && wanted.status() == StepResult.Status.PASS ? stopped : wanted;
        }

        private void next(SelfTest.StepContext ctx, int stage) {
            this.stage = stage;
            this.mark = ctx.tick();
        }
    }
}
