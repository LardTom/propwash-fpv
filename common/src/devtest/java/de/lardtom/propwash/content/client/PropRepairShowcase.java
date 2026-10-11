package de.lardtom.propwash.content.client;

import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseInventoryScreen;
import de.lardtom.propwash.guide.client.GuideSelfTests;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scenes.RepairScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PropRepairShowcase {
    private static final String PREFIX = "bildschirmfotos: Props reparieren – ";
    private static final String FUTURE = "prop_repair_shot_future";
    private static final int TOOLTIP_SLOT = 9;
    private static final float FIRST = 0.35F;
    private static final float SECOND = 0.45F;
    private static final Component GRINDSTONE_TITLE = Component.translatable("container.grindstone_title");

    private PropRepairShowcase() {
    }

    public static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        int step = combineStep();
        Screenshots.step(PREFIX + "zwei Props im Inventar-Raster", "prop_repair_crafting", 20, new CraftingScene());
        Screenshots.step(PREFIX + "zwei Props im Schleifstein", "prop_repair_grindstone", 20, new GrindstoneScene());
        Screenshots.step(PREFIX + "Shift-Tooltip eines kaputten Props", "tooltip_prop_broken", 20, new TooltipScene());
        Screenshots.step(PREFIX + "Flugschule, zwei beschädigte Props", "flight_school_repair_pair", 30,
                GuideSelfTests.sceneShot(RepairScene.ID, step, RepairScene.MERGE_AT - 30.0F));
        Screenshots.step(PREFIX + "Flugschule, reparierter Prop", "flight_school_repair_combined", 30,
                GuideSelfTests.sceneShot(RepairScene.ID, step, RepairScene.MERGE_AT + 26.0F));
        SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step(PREFIX + "Shift-Tooltip eines kaputten Props deutsch", "tooltip_prop_broken_de", 20, new TooltipScene());
        Screenshots.step(PREFIX + "Flugschule, reparierter Prop deutsch", "flight_school_repair_combined_de", 30,
                GuideSelfTests.sceneShot(RepairScene.ID, step, RepairScene.MERGE_AT + 26.0F));
        SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
    }

    private static int combineStep() {
        Scene scene = RepairScene.create();
        for (int i = 0; i < scene.stepCount(); i++) {
            if (scene.steps().get(i).name().equals("combine")) {
                return i;
            }
        }
        throw new IllegalStateException("Flugschule ohne Schritt combine");
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static @Nullable StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        Object value = ctx.state().get(FUTURE);
        if (!(value instanceof CompletableFuture<?> future)) {
            return StepResult.fail(what + ": kein Auftrag");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static final class CraftingScene implements Screenshots.Scene {
        private volatile @Nullable GameType previousMode;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.closeContainer();
                    this.previousMode = player.gameMode();
                    player.setGameMode(GameType.SURVIVAL);
                    List<Slot> grid = player.inventoryMenu.getInputGridSlots();
                    grid.forEach(slot -> slot.set(ItemStack.EMPTY));
                    grid.get(0).set(PropRepairSelfTests.prop(PropType.P51_3B, FIRST));
                    grid.get(1).set(PropRepairSelfTests.prop(PropType.P51_3B, SECOND));
                    player.inventoryMenu.broadcastChanges();
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Inventar-Raster");
            if (server != null) {
                return server;
            }
            if (mc.player == null || mc.player.hasInfiniteMaterials()
                    || mc.player.inventoryMenu.getSlot(InventoryMenu.RESULT_SLOT).getItem().isEmpty()) {
                return ctx.tick() > 200 ? StepResult.fail("Inventar-Raster: Ergebnis kommt nicht an") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, InventoryMenu.RESULT_SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            GameType mode = this.previousMode;
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.inventoryMenu.getInputGridSlots().forEach(slot -> slot.set(ItemStack.EMPTY));
                player.inventoryMenu.broadcastChanges();
                player.setGameMode(mode == null ? GameType.CREATIVE : mode);
            });
        }
    }

    private static final class GrindstoneScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof HoverGrindstoneScreen)) {
                Inventory inventory = mc.player.getInventory();
                GrindstoneMenu menu = new GrindstoneMenu(0, inventory);
                menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(PropRepairSelfTests.prop(PropType.P51_3B, FIRST));
                menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(PropRepairSelfTests.prop(PropType.P51_3B, SECOND));
                if (menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem().isEmpty()) {
                    return StepResult.fail("Schleifstein zeigt kein Ergebnis");
                }
                mc.gui.setScreen(new HoverGrindstoneScreen(menu, inventory, GRINDSTONE_TITLE, GrindstoneMenu.RESULT_SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
        }
    }

    private static final class TooltipScene implements Screenshots.Scene {
        private volatile @Nullable ItemStack previous;
        private volatile @Nullable GameType previousMode;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = PropRepairSelfTests.prop(PropType.P51_3B, 0.0F);
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    player.closeContainer();
                    this.previousMode = player.gameMode();
                    player.setGameMode(GameType.SURVIVAL);
                    this.previous = player.getInventory().getItem(TOOLTIP_SLOT).copy();
                    player.getInventory().setItem(TOOLTIP_SLOT, stack.copy());
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Tooltip");
            if (server != null) {
                return server;
            }
            ItemStack shown = mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(TOOLTIP_SLOT);
            if (!ItemStack.isSameItemSameComponents(shown, stack) || mc.player.hasInfiniteMaterials()) {
                return ctx.tick() > 200 ? StepResult.fail("Tooltip: Prop kommt nicht an") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                TooltipShift.override(true);
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, TOOLTIP_SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            TooltipShift.override(null);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ItemStack old = this.previous;
            GameType mode = this.previousMode;
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.getInventory().setItem(TOOLTIP_SLOT, old == null ? ItemStack.EMPTY : old);
                player.setGameMode(mode == null ? GameType.CREATIVE : mode);
            });
        }
    }

    private static final class HoverGrindstoneScreen extends GrindstoneScreen {
        private final int hoveredMenuSlot;

        private HoverGrindstoneScreen(GrindstoneMenu menu, Inventory inventory, Component title, int hoveredMenuSlot) {
            super(menu, inventory, title);
            this.hoveredMenuSlot = hoveredMenuSlot;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Slot slot = this.getMenu().getSlot(this.hoveredMenuSlot);
            super.extractRenderState(graphics, this.leftPos + slot.x + 8, this.topPos + slot.y + 8, a);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
