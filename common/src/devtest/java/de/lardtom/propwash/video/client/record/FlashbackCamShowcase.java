package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.compat.flashback.client.FlashbackBridge;
import de.lardtom.propwash.compat.flashback.client.FlashbackProbe;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseInventoryScreen;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class FlashbackCamShowcase {
    private static final String FUTURE = "fb_future";
    private static final String PHASE = "fb_phase";
    private static final String UP = "fb_up";
    private static final int TOOLTIP_SLOT = 9;
    private static final int TAB_SLOT = 1;

    private FlashbackCamShowcase() {
    }

    public static void register() {
        Screenshots.step("bildschirmfotos: Shift-Tooltip der Action-Cam (Flashback)", "tooltip_action_cam_flashback", 30, tooltipScene());
        Screenshots.step("bildschirmfotos: Tab Action-Cam mit Action-Cam (Flashback)", "configurator_action_cam_flashback", 30, new TabScene());
        if (FlashbackCamSelfTests.flashbackPresent()) {
            Screenshots.step("bildschirmfotos: Kamerapfad für Flashback im FPV-Flug", "flashback_cam_fpv", 30, new FlightScene(false));
            Screenshots.step("bildschirmfotos: Kamerapfad an Flashback übergeben", "flashback_cam_handed_over", 20, new FlightScene(true));
        } else {
            Screenshots.step("bildschirmfotos: Action-Cam (Flashback) ohne Flashback", "flashback_cam_needs_flashback", 30, new NeedsScene());
        }
    }

    public static Screenshots.Scene tooltipScene() {
        return new TooltipScene();
    }

    private static CompletableFuture<?> future(SelfTest.StepContext ctx) {
        Object value = ctx.state().get(FUTURE);
        return value instanceof CompletableFuture<?> future ? future : CompletableFuture.failedFuture(new IllegalStateException("kein Auftrag"));
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = future(ctx);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static ServerPlayer player(MinecraftServer server) {
        ServerPlayer player = FlashbackCamSelfTests.firstPlayer(server);
        if (player == null) {
            throw new IllegalStateException("kein Spieler");
        }
        return player;
    }

    private static final class TooltipScene implements Screenshots.Scene {
        private volatile @Nullable ItemStack previous;
        private volatile @Nullable GameType previousMode;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = ContentItems.stack(AccessoryType.ACTION_CAM_FLASHBACK);
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
            StepResult server = awaitServer(ctx, "Tooltip-Szene");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            ItemStack shown = mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(TOOLTIP_SLOT);
            if (!ItemStack.isSameItem(shown, stack) || mc.player.hasInfiniteMaterials()) {
                return ctx.tick() > 200 ? StepResult.fail("Tooltip-Szene: Item kommt nicht an") : StepResult.pending();
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

    private static final class TabScene implements Screenshots.Scene {
        private volatile @Nullable ItemStack previous;
        private volatile int previousSlot;
        private @Nullable RecordingConfig saved;
        private final DroneBuild build = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_FLASHBACK);

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                this.saved = RecordingConfig.get().copy();
                RecordingConfig.CONFIG.set(new RecordingConfig());
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
                    ItemStack drone = new ItemStack(DroneModule.DRONE_ITEM.get());
                    drone.set(CoreComponents.DRONE_BUILD.get(), this.build);
                    this.previous = player.getInventory().getItem(TAB_SLOT).copy();
                    this.previousSlot = player.getInventory().getSelectedSlot();
                    player.getInventory().setItem(TAB_SLOT, drone);
                    player.getInventory().setSelectedSlot(TAB_SLOT);
                    player.connection.send(new ClientboundSetHeldSlotPacket(TAB_SLOT));
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx, "Drohne für den Tab");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            Screenshots.parkMouse(mc);
            if (PilotSession.INSTANCE.isActive()) {
                return ctx.tick() > 200 ? StepResult.fail("Flugsitzung endet nicht") : StepResult.pending();
            }
            if (mc.player == null || !this.build.equals(mc.player.getMainHandItem().get(CoreComponents.DRONE_BUILD.get()))) {
                return ctx.tick() > 200 ? StepResult.fail("Drohne mit Action-Cam (Flashback) fehlt in der Hand") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, "action_cam");
                return StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !"action_cam".equals(active.id())) {
                screen.selectTab("action_cam");
                return StepResult.pending();
            }
            return StepResult.pass();
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
            if (this.saved != null) {
                RecordingConfig.CONFIG.set(this.saved);
                this.saved = null;
            }
            ItemStack old = this.previous;
            int slot = this.previousSlot;
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.getInventory().setItem(TAB_SLOT, old == null ? ItemStack.EMPTY : old);
                player.getInventory().setSelectedSlot(slot);
                player.connection.send(new ClientboundSetHeldSlotPacket(slot));
            });
        }
    }

    private static StepResult airborne(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        PilotSession current = PilotSession.INSTANCE;
        if (!state.containsKey(FUTURE) && current.isFpv() && current.armed() && FlashbackCam.mounted(current.drone())) {
            state.put(UP, Boolean.TRUE);
            return StepResult.pass();
        }
        if (!state.containsKey(FUTURE)) {
            state.put(FUTURE, ctx.onServer(FlashbackCamSelfTests::spawnAndFly));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Flashback-Drohne");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (!session.isFpv() || drone == null || !FlashbackCam.mounted(drone)) {
            return ctx.tick() > 300 ? StepResult.fail("keine FPV-Sitzung mit der Action-Cam (Flashback)") : StepResult.pending();
        }
        if (!state.containsKey(UP)) {
            StepResult up = FlashbackCamSelfTests.takeOff(ctx);
            if (up.status() != StepResult.Status.PASS) {
                return up;
            }
            state.put(UP, Boolean.TRUE);
        }
        return StepResult.pass();
    }

    private static void land(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        FlashbackCam cam = FlashbackCam.INSTANCE;
        if (cam.recording()) {
            cam.stop(mc, FlashbackCam.StopReason.USER);
        }
        if (FlashbackBridge.recording()) {
            FlashbackBridge.finishRecording();
        }
        PilotSession.INSTANCE.clearTestInput();
        ctx.onServer(FlashbackCamSelfTests::releaseDrone);
    }

    private static final class FlightScene implements Screenshots.Scene {
        private final boolean handedOver;
        private @Nullable RecordingConfig saved;
        private boolean triggered;

        private FlightScene(boolean handedOver) {
            this.handedOver = handedOver;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            this.triggered = false;
            StepResult up = airborne(ctx);
            if (up.status() != StepResult.Status.PASS) {
                return up;
            }
            FlashbackCam cam = FlashbackCam.INSTANCE;
            if (!state.containsKey(PHASE)) {
                FlashbackCamSelfTests.hover(ctx);
                try {
                    FlashbackProbe.quicksave(true);
                } catch (ReflectiveOperationException e) {
                    return StepResult.fail("Flashback-Einstellung nicht setzbar: " + e);
                }
                this.saved = RecordingConfig.get().copy();
                RecordingConfig config = RecordingConfig.get().copy();
                config.flashbackAutoRecord = true;
                config.flashbackSimplify = 0.0;
                RecordingConfig.CONFIG.set(config);
                state.put(PHASE, 1);
                if (this.handedOver && !cam.recording()) {
                    FlashbackCam.Result result = cam.start(mc, false);
                    if (result != FlashbackCam.Result.STARTED) {
                        return StepResult.fail("Kamerapfad startet nicht: " + result);
                    }
                }
                return StepResult.pending();
            }
            if (!this.handedOver) {
                FlashbackCamSelfTests.hover(ctx);
                return StepResult.pass();
            }
            FlashbackCamSelfTests.fly(ctx);
            return cam.recordedSeconds() >= 2.5 ? StepResult.pass()
                    : ctx.tick() > 900 ? StepResult.fail("Kamerapfad wächst nicht") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            FlashbackCam cam = FlashbackCam.INSTANCE;
            if (!this.triggered) {
                this.triggered = true;
                if (this.handedOver) {
                    cam.stop(mc, FlashbackCam.StopReason.USER);
                } else {
                    cam.start(mc, false);
                }
            }
            if (this.handedOver) {
                FlashbackCamSelfTests.hover(ctx);
            } else {
                FlashbackCamSelfTests.fly(ctx);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.handedOver) {
                land(ctx);
            } else {
                FlashbackCamSelfTests.hover(ctx);
            }
            if (this.saved != null) {
                RecordingConfig.CONFIG.set(this.saved);
                this.saved = null;
            }
        }
    }

    private static final class NeedsScene implements Screenshots.Scene {
        private boolean triggered;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            this.triggered = false;
            StepResult up = airborne(ctx);
            if (up.status() != StepResult.Status.PASS) {
                return up;
            }
            FlashbackCamSelfTests.hover(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (!this.triggered) {
                this.triggered = true;
                KeyMapping.click(InputConstants.getKey(RecordingKeys.RECORD.saveString()));
            }
            FlashbackCamSelfTests.hover(ctx);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (FlashbackCam.INSTANCE.lastResult() != FlashbackCam.Result.NEEDS_FLASHBACK) {
                ctx.log("Hinweis ohne Flashback fehlt im Bildschirmfoto: " + FlashbackCam.INSTANCE.lastResult());
            }
            land(ctx);
        }
    }
}
