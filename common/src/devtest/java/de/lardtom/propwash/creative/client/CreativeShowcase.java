package de.lardtom.propwash.creative.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.content.WorkbenchLayout;
import de.lardtom.propwash.content.client.DroneWorkbenchScreen;
import de.lardtom.propwash.creative.CreativeFlight;
import de.lardtom.propwash.creative.CreativeModule;
import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseInventoryScreen;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.widget.NumberSpinner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CreativeShowcase {
    private static final int SLOT = 10;

    private CreativeShowcase() {
    }

    public static void register() {
        Screenshots.step("bildschirmfotos: Drohnen-Editor mit Vorlage, Kennzahlen und Hinweisen", "configurator_creative_editor", 30,
                new EditorScene(showcase(), null, 0, GameType.CREATIVE));
        Screenshots.step("bildschirmfotos: Drohnen-Editor, Motorpositionen mit Tooltip", "configurator_creative_editor_arms", 30,
                new EditorScene(showcase(), CreativeParam.ARM_FRONT_LEFT_FORWARD, 60, GameType.CREATIVE));
        Screenshots.step("bildschirmfotos: Drohnen-Editor, Propeller und Motoren mit Tooltip Schubbeiwert", "configurator_creative_editor_props", 30,
                new EditorScene(showcase(), CreativeParam.PROP_THRUST_COEFFICIENT, 50, GameType.CREATIVE));
        SelfTest.switchStep("bildschirmfotos: Drohnen-Editor – Sprache Deutsch", "bildschirmfotos: Drohnen-Editor – Sprache zurücksetzen", 1200,
                ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step("bildschirmfotos: Drohnen-Editor mit Vorlage, Kennzahlen und Hinweisen deutsch", "configurator_creative_editor_de", 30,
                new EditorScene(showcase(), null, 0, GameType.CREATIVE));
        Screenshots.step("bildschirmfotos: Drohnen-Editor, Motorpositionen mit Tooltip deutsch", "configurator_creative_editor_arms_de", 30,
                new EditorScene(showcase(), CreativeParam.ARM_FRONT_LEFT_FORWARD, 60, GameType.CREATIVE));
        Screenshots.step("bildschirmfotos: Drohnen-Editor, Akku, Luftwiderstand und Kamera deutsch", "configurator_creative_editor_camera_de", 30,
                new EditorScene(showcase(), CreativeParam.CAMERA_FOV, 90, GameType.CREATIVE));
        Screenshots.step("bildschirmfotos: Drohnen-Editor im Überlebensmodus nur lesbar deutsch", "configurator_creative_editor_readonly_de", 30,
                new EditorScene(showcase(), null, 0, GameType.SURVIVAL));
        Screenshots.step("bildschirmfotos: Shift-Tooltip Kreativ-Flugcontroller deutsch", "tooltip_creative_fc_de", 30, new TooltipScene());
        Screenshots.step("bildschirmfotos: Werkbank mit Kreativ-Flugcontroller deutsch", "workbench_creative_fc_de", 40, new WorkbenchScene());
        SelfTest.clientStep("bildschirmfotos: Drohnen-Editor – Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
    }

    public static CreativeSpec showcase() {
        DroneBuild installed = CreativeSelfTests.INSTALLED;
        CreativeSpec spec = CreativeEditorTab.baseOf(DroneBuild.RACER).with(CreativeParam.MOTOR_KV, 2150.0)
                .with(CreativeParam.PROP_DIAMETER, 140.0).with(CreativeParam.PROP_THRUST_COEFFICIENT, 0.125)
                .with(CreativeParam.FRAME_MASS, 140.0).with(CreativeParam.ARM_FRONT_LEFT_FORWARD, 92.0)
                .with(CreativeParam.ARM_FRONT_RIGHT_FORWARD, 92.0).with(CreativeParam.CAMERA_FOV, 150.0);
        double roll = CreativeFlight.baseValue(installed.withCreative(spec), CreativeParam.INERTIA_ROLL);
        return spec.with(CreativeParam.INERTIA_ROLL, Math.rint(roll * 0.2));
    }

    private static void hover(Minecraft mc, @Nullable NumberSpinner widget) {
        if (widget == null || !widget.visible) {
            Screenshots.parkMouse(mc);
            return;
        }
        double guiX = widget.getX() + 0.5 * widget.getWidth();
        double guiY = widget.getY() + 0.5 * widget.getHeight();
        double x = guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double y = guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
    }

    private static final class EditorScene implements Screenshots.Scene {
        private final CreativeSpec spec;
        private final @Nullable CreativeParam hover;
        private final int margin;
        private final GameType mode;
        private @Nullable CompletableFuture<Void> given;
        private volatile @Nullable ItemStack previous;
        private volatile @Nullable GameType previousMode;
        private @Nullable CreativeEditorTab tab;

        private EditorScene(CreativeSpec spec, @Nullable CreativeParam hover, int margin, GameType mode) {
            this.spec = spec;
            this.hover = hover;
            this.margin = margin;
            this.mode = mode;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            DroneBuild build = CreativeSelfTests.INSTALLED.withCreative(this.spec);
            if (this.given == null) {
                this.given = ctx.onServer(server -> {
                    ServerPlayer player = CreativeSelfTests.player(server);
                    player.closeContainer();
                    this.previous = player.getMainHandItem().copy();
                    this.previousMode = player.gameMode();
                    player.setGameMode(this.mode);
                    player.setItemInHand(InteractionHand.MAIN_HAND, CreativeSelfTests.drone(build));
                });
                return StepResult.pending();
            }
            if (this.given.isCompletedExceptionally()) {
                return StepResult.fail("Drohnen-Editor-Szene: " + this.given.exceptionNow().getMessage());
            }
            if (!this.given.isDone() || mc.player == null || !CreativeSelfTests.clientHeld().equals(build)
                    || mc.player.isCreative() != (this.mode == GameType.CREATIVE)) {
                return ctx.tick() > 300 ? StepResult.fail("Drohnen-Editor-Szene: Drohne oder Spielmodus kommt nicht an") : StepResult.pending();
            }
            CreativeEditorTab open = CreativeSelfTests.openTab();
            if (open == null) {
                return ctx.tick() > 400 ? StepResult.fail("Tab Drohnen-Editor öffnet nicht") : StepResult.pending();
            }
            if (this.hover != null) {
                CreativeEditorTab.Row row = open.row(this.hover).orElse(null);
                if (row == null) {
                    return StepResult.fail("Zeile " + this.hover.key() + " fehlt");
                }
                open.reveal(row, this.margin);
            }
            this.tab = open;
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            CreativeEditorTab open = this.tab;
            if (open == null || this.hover == null) {
                Screenshots.parkMouse(mc);
                return;
            }
            hover(mc, open.row(this.hover).map(CreativeEditorTab.Row::spinner).orElse(null));
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            CreativeSelfTests.closeScreen();
            Screenshots.parkMouse(Minecraft.getInstance());
            ItemStack restore = this.previous;
            GameType mode = this.previousMode;
            ctx.onServer(server -> {
                ServerPlayer player = CreativeSelfTests.player(server);
                player.setItemInHand(InteractionHand.MAIN_HAND, restore == null ? ItemStack.EMPTY : restore);
                if (mode != null) {
                    player.setGameMode(mode);
                }
            });
            this.given = null;
            this.tab = null;
            this.previous = null;
            this.previousMode = null;
        }
    }

    private static ItemStack controller() {
        ItemStack stack = ContentItems.stack(StackType.CREATIVE_FC);
        stack.set(CreativeModule.SPEC.get(), showcase());
        return stack;
    }

    private static final class TooltipScene implements Screenshots.Scene {
        private @Nullable CompletableFuture<Void> placed;
        private volatile @Nullable ItemStack previous;
        private volatile @Nullable GameType previousMode;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.placed == null) {
                this.placed = ctx.onServer(server -> {
                    ServerPlayer player = CreativeSelfTests.player(server);
                    player.closeContainer();
                    this.previousMode = player.gameMode();
                    player.setGameMode(GameType.SURVIVAL);
                    this.previous = player.getInventory().getItem(SLOT).copy();
                    player.getInventory().setItem(SLOT, controller());
                });
                return StepResult.pending();
            }
            if (this.placed.isCompletedExceptionally()) {
                return StepResult.fail("Tooltip-Szene: " + this.placed.exceptionNow().getMessage());
            }
            ItemStack shown = mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(SLOT);
            if (!this.placed.isDone() || !shown.has(CreativeModule.SPEC.get()) || mc.player.hasInfiniteMaterials()) {
                return ctx.tick() > 200 ? StepResult.fail("Tooltip-Szene: Kreativ-FC kommt nicht an") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ShowcaseInventoryScreen)) {
                if (ctx.tick() > 400) {
                    return StepResult.fail("Tooltip-Szene: Inventar öffnet nicht");
                }
                TooltipShift.override(true);
                mc.gui.setScreen(new ShowcaseInventoryScreen(mc.player, SLOT));
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            TooltipShift.override(null);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ShowcaseInventoryScreen) {
                mc.gui.setScreen(null);
            }
            ItemStack restore = this.previous;
            GameType mode = this.previousMode;
            ctx.onServer(server -> {
                ServerPlayer player = CreativeSelfTests.player(server);
                player.getInventory().setItem(SLOT, restore == null ? ItemStack.EMPTY : restore);
                if (mode != null) {
                    player.setGameMode(mode);
                }
            });
            this.placed = null;
            this.previous = null;
            this.previousMode = null;
        }
    }

    private static void put(DroneWorkbenchMenu menu, ServerPlayer player, int slot, ItemStack stack) {
        menu.setCarried(stack);
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            throw new IllegalStateException("Slot " + slot + " nahm " + stack + " nicht an");
        }
    }

    private static final class WorkbenchScene implements Screenshots.Scene {
        private @Nullable CompletableFuture<Void> opened;
        private volatile @Nullable BlockPos pos;
        private volatile @Nullable BlockState previous;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            DroneBuild installed = CreativeSelfTests.INSTALLED;
            if (this.opened == null) {
                this.opened = ctx.onServer(server -> {
                    ServerPlayer player = CreativeSelfTests.player(server);
                    player.closeContainer();
                    player.setGameMode(GameType.CREATIVE);
                    ServerLevel level = player.level();
                    BlockPos at = player.blockPosition().offset(-2, 0, 3);
                    this.previous = level.getBlockState(at);
                    this.pos = at;
                    BlockState bench = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
                    level.setBlockAndUpdate(at, bench);
                    if (player.openMenu(bench.getMenuProvider(level, at)).isEmpty() || !(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
                        throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
                    }
                    put(menu, player, BuildSlots.FRAME, ContentItems.stack(installed.frame()));
                    put(menu, player, BuildSlots.STACK, controller());
                    put(menu, player, BuildSlots.VIDEO, ContentItems.stack(installed.video()));
                    put(menu, player, BuildSlots.BATTERY, Batteries.stackOf(installed.battery(), 0.9F));
                    for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
                        put(menu, player, BuildSlots.MOTOR + i, ContentItems.stack(installed.motor()));
                        put(menu, player, BuildSlots.PROP + i, ContentItems.stack(installed.prop()));
                    }
                });
                return StepResult.pending();
            }
            if (this.opened.isCompletedExceptionally()) {
                return StepResult.fail("Werkbank-Szene: " + this.opened.exceptionNow().getMessage());
            }
            if (!this.opened.isDone() || !(mc.gui.screen() instanceof DroneWorkbenchScreen screen)
                    || screen.getMenu().check().build().map(b -> !b.edited()).orElse(true)) {
                return ctx.tick() > 300 ? StepResult.fail("Werkbank-Szene: Kreativ-Drohne erscheint nicht") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
                Screenshots.parkMouse(mc);
                return;
            }
            Slot slot = screen.getMenu().getSlot(BuildSlots.STACK);
            double guiX = (screen.width - WorkbenchLayout.WIDTH) / 2.0 + slot.x + 8;
            double guiY = (screen.height - WorkbenchLayout.HEIGHT) / 2.0 + slot.y + 8;
            double x = guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            BlockPos at = this.pos;
            BlockState before = this.previous;
            ctx.onServer(server -> {
                ServerPlayer player = CreativeSelfTests.player(server);
                if (player.containerMenu instanceof DroneWorkbenchMenu menu) {
                    List<ItemStack> empty = new ArrayList<>();
                    for (int i = 0; i < BuildSlots.COUNT; i++) {
                        empty.add(ItemStack.EMPTY);
                    }
                    menu.replaceParts(empty);
                }
                player.closeContainer();
                if (at != null && before != null) {
                    player.level().setBlockAndUpdate(at, before);
                }
            });
            Screenshots.parkMouse(Minecraft.getInstance());
            this.opened = null;
            this.pos = null;
            this.previous = null;
        }
    }
}
