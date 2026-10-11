package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.client.DroneWorkbenchScreen;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.client.ExampleAddonClient;
import de.lardtom.propwash.dev.example.client.ExampleConfigTab;
import de.lardtom.propwash.dev.example.client.ExampleImportScreen;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class ApiShowcase {
    private static final String FUTURE = "future";
    private static final String CHILD = "child";
    private static final AtomicReference<ImportResult> SERVER_IMPORT = new AtomicReference<>();

    private ApiShowcase() {
    }

    public static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        Screenshots.step("bildschirmfotos: Werkbank mit Knopf eines Addons (Parts-API)", "api_workbench_extension", 30, new WorkbenchScene(false));
        Screenshots.step("bildschirmfotos: Kind-Fenster eines Addons über der Werkbank (Parts-API)", "api_workbench_import", 30,
                new WorkbenchScene(true));
        Screenshots.step("bildschirmfotos: Konfigurator-Tab eines Addons (Parts-API)", "api_config_tab", 30, new ConfigScene());
    }

    private static final class WorkbenchScene implements Screenshots.Scene {
        private final boolean child;

        private WorkbenchScene(boolean child) {
            this.child = child;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                ExampleAddon.showcase(true);
                SERVER_IMPORT.set(null);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ServerLevel level = player.level();
                    player.setGameMode(GameType.CREATIVE);
                    BlockPos pos = benchPos(player);
                    BlockState bench = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
                    level.setBlockAndUpdate(pos, bench);
                    if (player.openMenu(bench.getMenuProvider(level, pos)).isEmpty() || !(player.containerMenu instanceof DroneWorkbenchMenu)) {
                        throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
                    }
                    SERVER_IMPORT.set(PropwashApi.builds().applyImport(player, ImportRequest.wholeBuild(ExampleAddon.xclass())));
                }));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (!future.isDone()) {
                return StepResult.pending();
            }
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Werkbank: " + future.exceptionNow().getMessage());
            }
            ImportResult imported = SERVER_IMPORT.get();
            if (imported == null || !imported.status().success()) {
                return StepResult.fail("X-Class ließ sich nicht in die Werkbank importieren: " + imported);
            }
            WorkbenchContext context = ExampleAddonClient.lastContext();
            if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen) || context == null || context.screen() != screen
                    || screen.getMenu().resultPreview().isEmpty()) {
                if (mc.gui.screen() instanceof ExampleImportScreen opened && opened.frames() > 2) {
                    return StepResult.pass();
                }
                return ctx.tick() > 300 ? StepResult.fail("Werkbank mit Beispiel-Knopf erscheint nicht: " + mc.gui.screen()) : StepResult.pending();
            }
            if (!this.child) {
                return StepResult.pass();
            }
            if (!state.containsKey(CHILD)) {
                state.put(CHILD, Boolean.TRUE);
                context.openChild(parent -> new ExampleImportScreen(parent, context));
            }
            return StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ExampleImportScreen) {
                mc.gui.setScreen(null);
            }
            ExampleAddon.showcase(false);
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                if (player.containerMenu instanceof DroneWorkbenchMenu menu) {
                    for (int slot = 0; slot < DroneWorkbenchMenu.PART_SLOTS; slot++) {
                        menu.getSlot(slot).set(ItemStack.EMPTY);
                    }
                }
                player.closeContainer();
                player.level().setBlockAndUpdate(benchPos(player), Blocks.AIR.defaultBlockState());
            });
        }
    }

    private static final class ConfigScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(FUTURE)) {
                ExampleAddon.showcase(true);
                ExampleAddonClient.registerTab();
                state.put(FUTURE, ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND,
                        PropwashApi.builds().createDrone(ExampleAddon.xclass()))));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
            if (!future.isDone()) {
                return StepResult.pending();
            }
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Drohne in die Hand: " + future.exceptionNow().getMessage());
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                if (mc.gui.screen() == null && !PropwashClientApi.openConfigTab(ExampleConfigTab.ID, null)) {
                    return StepResult.fail("openConfigTab findet den Beispiel-Tab nicht");
                }
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet sich nicht") : StepResult.pending();
            }
            if (screen.activeTab() == null || !ExampleConfigTab.ID.equals(screen.activeTab().id())) {
                screen.selectTab(ExampleConfigTab.ID);
                return StepResult.pending();
            }
            return screen.extractedFrames(ExampleConfigTab.ID) > 3 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ExampleAddon.showcase(false);
            ctx.onServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static BlockPos benchPos(ServerPlayer player) {
        return player.blockPosition().offset(2, 0, -2);
    }
}
