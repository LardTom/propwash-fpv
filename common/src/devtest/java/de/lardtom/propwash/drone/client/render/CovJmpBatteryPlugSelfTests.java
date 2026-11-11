package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.BatteryFit;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.share.ShareVectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovJmpBatteryPlugSelfTests {
    public static final String STEP = BatteryPlugSelfTests.PREFIX + " – Akkuwechsel in der Werkbank (Abdeckung cov-pw3)";
    static final List<String> CONNECTORS = List.of("BT2.0", "XT60", "XT90");
    static final Identifier JMP_PRESET = Identifier.fromNamespaceAndPath(ShareVectors.JMP, "bash5_6s");
    private static final int BATTERY_COLOR = 0xC0FFEE;
    private static final int FRAME_COLOR = 0x203040;
    private static final AtomicReference<String> SERVER_ERROR = new AtomicReference<>();
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String PLAN = "plan";
    private static final String INDEX = "index";
    private static final String PAINT = "paint";
    private static final String LOG = "log";

    private CovJmpBatteryPlugSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 1600, CovJmpBatteryPlugSelfTests::swapStep);
    }

    record Plan(DroneBuild base, List<BatteryType> batteries) {
    }

    static Optional<DroneBuild> jmpDrone() {
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (preset.id().equals(JMP_PRESET)) {
                return Conversions.build(preset.data().build());
            }
        }
        return Optional.empty();
    }

    static Plan plan(DroneBuild base) {
        List<BatteryType> chosen = new ArrayList<>();
        for (String connector : CONNECTORS) {
            BatteryType best = null;
            int bestScore = -1;
            for (BatteryType battery : BatteryType.values()) {
                if (!connector.equals(battery.definition().connector())) {
                    continue;
                }
                int score = (BatteryFit.check(base.frame(), battery).allowed() ? 2 : 0)
                        + (battery.id().getNamespace().equals(base.frame().id().getNamespace()) ? 1 : 0);
                if (score > bestScore) {
                    best = battery;
                    bestScore = score;
                }
            }
            if (best != null) {
                chosen.add(best);
            }
        }
        return new Plan(base, chosen);
    }

    @SuppressWarnings("unchecked")
    private static StepResult swapStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase == 0) {
            if (!ShareVectors.jmpLoaded()) {
                ctx.log("Just More Parts nicht geladen: Akkuwechsel BT2.0 → XT60 → XT90 nur im Lauf mit JMP");
                return StepResult.pass();
            }
            Optional<DroneBuild> jmp = jmpDrone();
            if (jmp.isEmpty()) {
                return StepResult.fail("JMP-Preset " + JMP_PRESET + " nicht gefunden");
            }
            Plan plan = plan(jmp.get());
            state.put(PLAN, plan);
            state.put(LOG, new ArrayList<String>());
            ctx.log("Rahmen " + plan.base().frame().id() + ", Akkus " + plan.batteries().stream().map(b -> b.id() + " (" + b.definition().connector() + ", "
                    + (BatteryFit.check(plan.base().frame(), b).allowed() ? "passt" : "passt nicht") + ")").toList());
            if (plan.batteries().size() < 2) {
                return StepResult.fail("weniger als zwei Steckerarten unter den Akkus: " + plan.batteries());
            }
            SERVER_ERROR.set(null);
            DroneBuild first = plan.base().withBattery(plan.batteries().getFirst());
            PaintSpec paint = PaintSpec.DEFAULT.with(PaintSlot.FRAME.key(), FRAME_COLOR).with(PaintSlot.BATTERY.key(), BATTERY_COLOR);
            state.put("server", ctx.onServer(server -> {
                try {
                    ServerPlayer player = openBench(server);
                    DroneData data = new DroneData(Conversions.spec(first), paint, de.lardtom.propwash.api.build.TuneSpec.EMPTY, Optional.empty());
                    ImportResult result = PropwashApi.builds().applyImport(player, new ImportRequest(data, EnumSet.of(ImportLayer.PARTS, ImportLayer.PAINT),
                            EnumSet.noneOf(TuneGroup.class), ImportTarget.WORKBENCH));
                    if (result.status() != ImportStatus.APPLIED) {
                        SERVER_ERROR.set("Drohne nicht in der Werkbank: " + result);
                    }
                } catch (RuntimeException e) {
                    SERVER_ERROR.set(e.toString());
                }
            }));
            state.put(INDEX, 0);
            state.put(PHASE, 1);
            state.put(SINCE, ctx.tick());
            return StepResult.pending();
        }
        CompletableFuture<Void> server = (CompletableFuture<Void>) state.get("server");
        if (server != null && !server.isDone()) {
            return ctx.tick() > 1500 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
        }
        if (SERVER_ERROR.get() != null) {
            close(ctx);
            return StepResult.fail(SERVER_ERROR.get());
        }
        Plan plan = (Plan) state.get(PLAN);
        int index = (int) state.get(INDEX);
        BatteryType wanted = plan.batteries().get(index);
        if (!(mc.player != null && mc.player.containerMenu instanceof DroneWorkbenchMenu menu)) {
            return wait(ctx, "Werkbank nicht offen");
        }
        Optional<DroneBuild> build = menu.check().build();
        if (build.isEmpty() || build.get().battery() != wanted || ctx.tick() - (int) state.get(SINCE) < 5) {
            return wait(ctx, "Werkbank zeigt nicht " + wanted.id() + ": " + build.map(b -> b.battery().id()));
        }
        DronePaint paint = menu.paint();
        if (index == 0) {
            state.put(PAINT, paint);
            if (paint.color(PaintSlot.BATTERY).orElse(-1) != BATTERY_COLOR) {
                close(ctx);
                return StepResult.fail("Lack-Eintrag des Akkus fehlt nach dem Import: " + paint);
            }
        }
        String problem = check(build.get(), paint, (DronePaint) state.get(PAINT), wanted);
        if (problem != null) {
            close(ctx);
            return StepResult.fail(problem);
        }
        List<String> log = (List<String>) state.get(LOG);
        log.add(wanted.id().getPath() + " " + wanted.definition().connector());
        if (index + 1 >= plan.batteries().size()) {
            ctx.log("Akkuwechsel " + String.join(" → ", log) + ": Stecker und Kabel jeweils ausgeblendet, Lack-Eintrag des Akkus (#C0FFEE) unverändert");
            close(ctx);
            return StepResult.pass();
        }
        BatteryType next = plan.batteries().get(index + 1);
        state.put(INDEX, index + 1);
        state.put(SINCE, ctx.tick());
        SERVER_ERROR.set(null);
        state.put("server", ctx.onServer(s -> {
            try {
                ServerPlayer player = player(s);
                if (!(player.containerMenu instanceof DroneWorkbenchMenu bench)) {
                    SERVER_ERROR.set("Werkbank am Server nicht offen");
                    return;
                }
                bench.getSlot(BuildSlots.BATTERY).set(PropwashApi.partItems().stack(next.id()));
                bench.broadcastChanges();
            } catch (RuntimeException e) {
                SERVER_ERROR.set(e.toString());
            }
        }));
        return StepResult.pending();
    }

    static @Nullable String check(DroneBuild build, DronePaint paint, DronePaint first, BatteryType wanted) {
        String name = build.frame().id() + " + " + wanted.id();
        DroneRenderState state = DroneVisuals.previewState(build, PartWear.PRISTINE, paint);
        if (state == null) {
            return name + ": kein Darstellungszustand";
        }
        if (state.build.battery() != wanted) {
            return name + ": Darstellung mit Akku " + state.build.battery().id();
        }
        if (!wanted.definition().connector().equals(state.build.battery().definition().connector())) {
            return name + ": Steckerart " + state.build.battery().definition().connector() + " statt " + wanted.definition().connector();
        }
        if (!state.powerLead.shown(state)) {
            return name + ": Stecker und Kabel nicht ausgeblendet";
        }
        String plugged = BatteryPlugSelfTests.plugged(DroneVisuals.resources(), build);
        if (plugged != null) {
            return plugged;
        }
        if (!paint.equals(first) || !state.paint.equals(first)) {
            return name + ": Lack-Eintrag nach dem Tausch " + paint + " statt " + first;
        }
        return null;
    }

    private static StepResult wait(SelfTest.StepContext ctx, String reason) {
        if (ctx.tick() > 1500) {
            close(ctx);
            return StepResult.fail(reason);
        }
        return StepResult.pending();
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static BlockPos benchPos(ServerPlayer player) {
        return player.blockPosition().offset(-2, 0, 2);
    }

    static ServerPlayer openBench(MinecraftServer server) {
        ServerPlayer player = player(server);
        player.closeContainer();
        player.setGameMode(GameType.CREATIVE);
        Block block = BuiltInRegistries.BLOCK.getValue(Constants.id("drone_workbench"));
        BlockPos pos = benchPos(player);
        player.level().setBlockAndUpdate(pos, block.defaultBlockState());
        MenuProvider provider = player.level().getBlockState(pos).getMenuProvider(player.level(), pos);
        if (provider == null || player.openMenu(provider).isEmpty()) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
        return player;
    }

    private static void close(SelfTest.StepContext ctx) {
        Minecraft.getInstance().gui.setScreen(null);
        ctx.onServer(server -> {
            ServerPlayer player = player(server);
            player.closeContainer();
            player.getInventory().clearContent();
            player.level().removeBlock(benchPos(player), false);
        });
    }
}
