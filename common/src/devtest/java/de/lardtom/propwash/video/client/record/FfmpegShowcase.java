package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.video.record.external.FfmpegLocator;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class FfmpegShowcase {
    public static final String PREFIX = "bildschirmfotos: Action-Cam ffmpeg – ";
    private static final String FUTURE = "future";
    private static final int TAB_SLOT = 1;

    private FfmpegShowcase() {
    }

    public static void register() {
        Screenshots.step(PREFIX + "Tab mit Status dieses Rechners", "configurator_action_cam_ffmpeg", 30, new TabScene(false));
        Screenshots.step(PREFIX + "Tab ohne ffmpeg", "configurator_action_cam_ffmpeg_missing", 30, new TabScene(true));
        SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step(PREFIX + "Tab mit Status dieses Rechners deutsch", "configurator_action_cam_ffmpeg_de", 30, new TabScene(false));
        SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
    }

    private static ServerPlayer player(MinecraftServer server) {
        ServerPlayer player = FlashbackCamSelfTests.firstPlayer(server);
        if (player == null) {
            throw new IllegalStateException("kein Spieler");
        }
        return player;
    }

    private static final class TabScene implements Screenshots.Scene {
        private final boolean missing;
        private final DroneBuild build = DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY);
        private volatile @Nullable ItemStack previous;
        private volatile int previousSlot;
        private @Nullable RecordingConfig saved;

        private TabScene(boolean missing) {
            this.missing = missing;
        }

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
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Drohne für den Tab: " + future.exceptionNow());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            if (PilotSession.INSTANCE.isActive()) {
                return ctx.tick() > 200 ? StepResult.fail("Flugsitzung endet nicht") : StepResult.pending();
            }
            if (mc.player == null || !this.build.equals(mc.player.getMainHandItem().get(CoreComponents.DRONE_BUILD.get()))) {
                return ctx.tick() > 200 ? StepResult.fail("Drohne mit Action-Cam Heavy fehlt in der Hand") : StepResult.pending();
            }
            ActionCamTab tab = FfmpegSelfTests.openTab();
            if (tab == null) {
                return ctx.tick() > 400 ? StepResult.fail("Tab „Action-Cam“ öffnet nicht") : StepResult.pending();
            }
            if (this.missing) {
                tab.preview(new HardwareEncoders.Status(HardwareEncoders.State.NO_FFMPEG, "", null, null, 0, 0.0, "", null,
                        FfmpegLocator.CustomPath.NONE, List.of()));
            } else if (tab.ffmpegStatus().state() == HardwareEncoders.State.CHECKING) {
                return ctx.tick() > 900 ? StepResult.fail("Prüfung von ffmpeg endet nicht") : StepResult.pending();
            }
            ctx.log("Abschnitt ffmpeg: " + FfmpegSelfTests.texts(tab));
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            FfmpegSelfTests.closeTab();
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
}
