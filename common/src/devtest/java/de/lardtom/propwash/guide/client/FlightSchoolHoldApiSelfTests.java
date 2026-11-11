package de.lardtom.propwash.guide.client;

import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.guide.HoldTracker;
import de.lardtom.propwash.guide.client.screen.SceneIndexScreen;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scene.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class FlightSchoolHoldApiSelfTests {
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String SAVED = "saved";
    private static final String MODE = "mode";
    private static final String INVENTORY = "inventory";
    private static final String HINTS = "hints";
    private static final String STARTED = "started";
    private static final int PART_SLOT = InventoryMenu.INV_SLOT_START;
    private static final int EMPTY_SLOT = InventoryMenu.INV_SLOT_START + 1;
    private static final int TIMEOUT = 200;
    private static final int QUIET = 30;

    private FlightSchoolHoldApiSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("guide: Taste halten über einem Slot nur über die API (Testweg für Add-ons)", 1200, FlightSchoolHoldApiSelfTests::apiHoldStep);
    }

    private static StepResult apiHoldStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        ItemStack part = ContentItems.stack(PropType.P51_3B);
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (mc.player == null) {
            return fail(ctx, "kein Spieler");
        }
        if (phase == 0) {
            if (PropwashClientApi.simulateFlightSchoolHold(-1)) {
                return fail(ctx, "simulateFlightSchoolHold(-1) nimmt einen negativen Slot an");
            }
            ItemStack[] saved = new ItemStack[2];
            GameType[] mode = new GameType[1];
            state.put(SAVED, saved);
            state.put(MODE, mode);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.closeContainer();
                mode[0] = player.gameMode();
                player.setGameMode(GameType.SURVIVAL);
                saved[0] = player.getInventory().getItem(PART_SLOT).copy();
                saved[1] = player.getInventory().getItem(EMPTY_SLOT).copy();
                player.getInventory().setItem(PART_SLOT, part.copy());
                player.getInventory().setItem(EMPTY_SLOT, ItemStack.EMPTY);
            }));
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (phase == 1) {
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (future.isCompletedExceptionally()) {
                return fail(ctx, "Inventar am Server nicht vorbereitet");
            }
            boolean arrived = future.isDone() && ItemStack.isSameItem(mc.player.getInventory().getItem(PART_SLOT), part)
                    && mc.player.getInventory().getItem(EMPTY_SLOT).isEmpty() && !mc.player.hasInfiniteMaterials();
            if (!arrived) {
                return since(ctx) > TIMEOUT ? fail(ctx, "Teil kommt nicht im Inventar an") : StepResult.pending();
            }
            InventoryScreen inventory = new InventoryScreen(mc.player);
            mc.gui.setScreen(inventory);
            state.put(INVENTORY, inventory);
            return startHold(ctx, 2);
        }
        if (phase == 2 && mc.gui.screen() != state.get(INVENTORY) && openedScenes(mc.gui.screen()) == null) {
            return fail(ctx, "Inventar nicht offen, sondern " + mc.gui.screen());
        }
        Screen inventory = (Screen) state.get(INVENTORY);
        List<String> expected = PropwashClientApi.flightSchoolScenes(part);
        return switch (phase) {
            case 2, 6 -> {
                List<String> opened = openedScenes(mc.gui.screen());
                if (opened == null) {
                    yield since(ctx) > TIMEOUT
                            ? fail(ctx, "Halten über Slot " + PART_SLOT + " öffnet keine Flugschul-Szene (Inventar-Bilder " + HoldToOpen.frames() + ", offen "
                            + mc.gui.screen() + ")")
                            : StepResult.pending();
                }
                long held = Util.getMillis() - (long) state.get(STARTED);
                boolean hinted = HoldToOpen.hintFrames() > (long) state.get(HINTS);
                mc.gui.screen().onClose();
                boolean back = mc.gui.screen() == inventory;
                if (!opened.equals(expected) || !hinted || !back || held < HoldTracker.HOLD_MILLIS) {
                    yield fail(ctx, "Halten über Slot " + PART_SLOT + " öffnet falsch (Szenen " + opened + " statt " + expected + ", Hinweis " + hinted
                            + ", zurück im Inventar " + back + ", gehalten " + held + " ms)");
                }
                ctx.log((phase == 2 ? "erstes" : "neues") + " Halten über Slot " + PART_SLOT + ": " + opened + " nach " + held + " ms");
                if (phase == 6) {
                    PropwashClientApi.stopSimulatedFlightSchoolHold();
                    mc.gui.setScreen(null);
                    restore(ctx);
                    phase(ctx, 7);
                    yield StepResult.pending();
                }
                phase(ctx, 3);
                yield StepResult.pending();
            }
            case 3, 4, 5 -> {
                if (openedScenes(mc.gui.screen()) != null || mc.gui.screen() != inventory) {
                    String why = phase == 3 ? "ohne Loslassen ein zweites Mal" : phase == 4 ? "über dem leeren Slot " + EMPTY_SLOT : "nach stopSimulatedFlightSchoolHold";
                    yield fail(ctx, "Flugschule öffnet " + why + " (offen " + mc.gui.screen() + ")");
                }
                if (phase == 4 && HoldToOpen.hintFrames() > (long) state.get(HINTS)) {
                    yield fail(ctx, "Hinweis über dem leeren Slot " + EMPTY_SLOT);
                }
                if (since(ctx) < QUIET) {
                    yield StepResult.pending();
                }
                if (phase == 3) {
                    if (!PropwashClientApi.simulateFlightSchoolHold(EMPTY_SLOT)) {
                        yield fail(ctx, "simulateFlightSchoolHold(" + EMPTY_SLOT + ") abgelehnt");
                    }
                    state.put(HINTS, HoldToOpen.hintFrames());
                    phase(ctx, 4);
                    yield StepResult.pending();
                }
                if (phase == 4) {
                    PropwashClientApi.stopSimulatedFlightSchoolHold();
                    phase(ctx, 5);
                    yield StepResult.pending();
                }
                yield startHold(ctx, 6);
            }
            default -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (!future.isDone()) {
                    yield since(ctx) > TIMEOUT ? StepResult.fail("Inventar am Server nicht zurückgesetzt") : StepResult.pending();
                }
                yield StepResult.check(!future.isCompletedExceptionally(), "Inventar am Server nicht zurückgesetzt");
            }
        };
    }

    private static StepResult startHold(SelfTest.StepContext ctx, int next) {
        ctx.state().put(HINTS, HoldToOpen.hintFrames());
        ctx.state().put(STARTED, Util.getMillis());
        if (!PropwashClientApi.simulateFlightSchoolHold(PART_SLOT)) {
            return fail(ctx, "simulateFlightSchoolHold(" + PART_SLOT + ") abgelehnt (Entwicklungsumgebung)");
        }
        phase(ctx, next);
        return StepResult.pending();
    }

    private static @Nullable List<String> openedScenes(@Nullable Screen screen) {
        if (screen instanceof SceneIndexScreen index) {
            return index.shownScenes().stream().map(FlightSchoolHoldApiSelfTests::apiId).toList();
        }
        if (screen instanceof SceneScreen scene) {
            return List.of(apiId(scene.scene()));
        }
        return null;
    }

    private static String apiId(Scene scene) {
        return scene.builtin() ? Scene.BUILTIN_NAMESPACE + ":" + scene.id() : scene.id();
    }

    private static StepResult fail(SelfTest.StepContext ctx, String reason) {
        PropwashClientApi.stopSimulatedFlightSchoolHold();
        Minecraft.getInstance().gui.setScreen(null);
        restore(ctx);
        return StepResult.fail(reason);
    }

    private static void restore(SelfTest.StepContext ctx) {
        ItemStack[] saved = (ItemStack[]) ctx.state().get(SAVED);
        GameType[] mode = (GameType[]) ctx.state().get(MODE);
        if (saved == null || mode == null) {
            return;
        }
        ctx.state().remove(SAVED);
        ctx.state().put(FUTURE, ctx.onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().setItem(PART_SLOT, saved[0] == null ? ItemStack.EMPTY : saved[0]);
            player.getInventory().setItem(EMPTY_SLOT, saved[1] == null ? ItemStack.EMPTY : saved[1]);
            player.setGameMode(mode[0] == null ? GameType.CREATIVE : mode[0]);
        }));
    }

    private static void phase(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }
}
