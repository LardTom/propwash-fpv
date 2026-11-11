package de.lardtom.propwash.rx.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scenes.RxBlockScene;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class RxShowcase {
    private static final String PREFIX = "bildschirmfotos: RX-Block – ";
    private static final String FUTURE = "rx_shot_future";
    private static final String SINCE = "rx_shot_since";
    private static final AtomicLong CLOCK = new AtomicLong(-1L);
    private static final int[] SMALL = {960, 720, 3};
    private static final int ROW = 8;
    private static final double LEVELS_FEET = 2.1;
    private static final float LEVELS_PITCH = 55.0F;

    private RxShowcase() {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        Screenshots.step(PREFIX + "LED aus, grün und rot, Lampe am gebundenen Block", "rx_block_leds", 30, new LedScene());
        Screenshots.step(PREFIX + "Fenster", "rx_block_window", 10, new WindowScene(false, false));
        Screenshots.step(PREFIX + "Fenster bei 320 × 240", "rx_block_window_small", 10, new WindowScene(false, true));
        Screenshots.step(PREFIX + "Bindung lösen mit Bestätigung", "rx_block_window_unbind", 10, new WindowScene(true, false));
        SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step(PREFIX + "Fenster deutsch", "rx_block_window_de", 10, new WindowScene(false, false));
        Screenshots.step(PREFIX + "Fenster bei 320 × 240 deutsch", "rx_block_window_small_de", 10, new WindowScene(false, true));
        Screenshots.step(PREFIX + "Kapitel deutsch", "rx_block_scene_de", 20, new ChapterScene());
        SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        Screenshots.step(PREFIX + "Kapitel in der Flugschule", "rx_block_scene", 20, new ChapterScene());
        Screenshots.step(PREFIX + "Signalstärke 0 bis 15 am Tag", "rx_block_levels", 30, new LevelsScene(false));
        Screenshots.step(PREFIX + "Signalstärke 0 bis 15 bei Nacht", "rx_block_levels_night", 30, new LevelsScene(true));
        SelfTest.clientStep(PREFIX + "aufräumen", 600, RxShowcase::cleanupStep);
    }

    private static void setNoon(MinecraftServer server) {
        setTime(server, ClockTimeMarkers.NOON);
    }

    private static void setTime(MinecraftServer server, ResourceKey<ClockTimeMarker> marker) {
        ServerLevel level = RxSelfTests.player(server).level();
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isPresent()) {
            CLOCK.compareAndSet(-1L, server.clockManager().getInstance(clock.get()).totalTicks());
            server.clockManager().moveToTimeMarker(clock.get(), marker);
        }
    }

    private static List<BlockPos> levels() {
        BlockPos site = RxSelfTests.site();
        List<BlockPos> out = new ArrayList<>();
        for (int level = 0; level < RxModelSelfTests.LEVELS; level++) {
            out.add(site.offset(4 - level % ROW, 0, level < ROW ? 3 : 1));
        }
        return out;
    }

    private static void buildLevels(MinecraftServer server, boolean night) {
        RxSelfTests.prepareSite(server);
        ServerPlayer player = RxSelfTests.player(server);
        ServerLevel level = player.level();
        BlockState rx = RxBlock.facing(RxModule.RX_BLOCK.block().get().defaultBlockState(), Direction.NORTH).setValue(RxBlock.LINK, RxLinkState.LINKED);
        List<BlockPos> spots = levels();
        for (int power = 0; power < spots.size(); power++) {
            level.setBlock(spots.get(power), rx.setValue(RxBlock.POWER, power), Block.UPDATE_ALL);
        }
        setTime(server, night ? ClockTimeMarkers.MIDNIGHT : ClockTimeMarkers.NOON);
        BlockPos site = RxSelfTests.site();
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, site.getX() + 1.0, site.getY() + LEVELS_FEET, site.getZ() - 0.1, Set.of(), 0.0F, LEVELS_PITCH, true);
    }

    private static List<BlockPos> showcase() {
        BlockPos base = RxSelfTests.site().south(3);
        return List.of(base.west(1), base.east(1), base.east(3));
    }

    private static void build(MinecraftServer server) {
        RxSelfTests.prepareSite(server);
        ServerPlayer player = RxSelfTests.player(server);
        ServerLevel level = player.level();
        List<BlockPos> spots = showcase();
        BlockState rx = RxBlock.facing(RxModule.RX_BLOCK.block().get().defaultBlockState(), Direction.NORTH);
        level.setBlock(spots.get(0), rx.setValue(RxBlock.LINK, RxLinkState.NONE), Block.UPDATE_ALL);
        level.setBlock(spots.get(1), RxBlock.facing(rx, Direction.SOUTH).setValue(RxBlock.LINK, RxLinkState.LINKED).setValue(RxBlock.POWER, 15),
                Block.UPDATE_ALL);
        level.setBlock(spots.get(2), rx.setValue(RxBlock.LINK, RxLinkState.LOST).setValue(RxBlock.POWER, 9), Block.UPDATE_ALL);
        level.setBlock(spots.get(1).south(), Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true), Block.UPDATE_ALL);
        setNoon(server);
        RxSelfTests.teleport(player, Vec3.atBottomCenterOf(RxSelfTests.site().east(1).north(1)), 0.0F, 38.0F);
    }

    private static @Nullable StepResult waitFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Szene: " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static final class LedScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(RxShowcase::build));
                ctx.state().put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            if (mc.level == null || mc.player == null) {
                return StepResult.pending();
            }
            List<BlockPos> spots = showcase();
            boolean ready = mc.level.getBlockState(spots.get(2)).getBlock() instanceof RxBlock
                    && mc.level.getBlockState(spots.get(1).south()).is(Blocks.REDSTONE_LAMP)
                    && mc.player.position().distanceTo(Vec3.atBottomCenterOf(RxSelfTests.site().east(1).north(1))) < 0.1;
            if (!ready) {
                return (int) ctx.state().get(SINCE) + 300 < ctx.tick() ? StepResult.fail("RX-Szene kommt nicht an") : StepResult.pending();
            }
            return ctx.tick() - (int) ctx.state().get(SINCE) > 20 ? StepResult.pass() : StepResult.pending();
        }
    }

    private static final class LevelsScene implements Screenshots.Scene {
        private final boolean night;

        private LevelsScene(boolean night) {
            this.night = night;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> buildLevels(server, this.night)));
                ctx.state().put(SINCE, ctx.tick());
                return StepResult.pending();
            }
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            if (mc.level == null || mc.player == null) {
                return StepResult.pending();
            }
            List<BlockPos> spots = levels();
            boolean ready = true;
            for (int power = 0; power < spots.size(); power++) {
                BlockState state = mc.level.getBlockState(spots.get(power));
                ready &= state.getBlock() instanceof RxBlock && state.getValue(RxBlock.POWER) == power;
            }
            ready &= mc.player.position().y > RxSelfTests.site().getY() + LEVELS_FEET - 0.1;
            if (!ready) {
                return (int) ctx.state().get(SINCE) + 300 < ctx.tick() ? StepResult.fail("Stufen-Szene kommt nicht an") : StepResult.pending();
            }
            return ctx.tick() - (int) ctx.state().get(SINCE) > 20 ? StepResult.pass() : StepResult.pending();
        }
    }

    private static final class WindowScene implements Screenshots.Scene {
        private final boolean confirm;
        private final boolean small;
        private int @Nullable [] saved;
        private int opened = -1;

        private WindowScene(boolean confirm, boolean small) {
            this.confirm = confirm;
            this.small = small;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Window window = mc.getWindow();
            if (this.small && this.saved == null) {
                this.saved = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                float density = Math.max(1.0F, window.getPixelDensity());
                window.setWindowed(Math.round(SMALL[0] / density), Math.round(SMALL[1] / density));
                return StepResult.pending();
            }
            if (this.small) {
                mc.options.guiScale().set(SMALL[2]);
                mc.resizeGui();
                if (window.getGuiScaledWidth() != 320 || window.getGuiScaledHeight() != 240) {
                    return ctx.tick() > 200 ? StepResult.fail("GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight()) : StepResult.pending();
                }
            }
            if (this.opened < 0) {
                mc.gui.setScreen(this.confirm ? RxSelfTests.confirmScreen() : RxSelfTests.previewScreen());
                this.opened = ctx.tick();
                return StepResult.pending();
            }
            return mc.gui.screen() instanceof RxScreen && ctx.tick() - this.opened > 3 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(null);
            int[] previous = this.saved;
            if (previous != null) {
                mc.options.guiScale().set(previous[2]);
                mc.getWindow().setWindowed(previous[0], previous[1]);
                mc.resizeGui();
                this.saved = null;
            }
            this.opened = -1;
        }
    }

    private static final class ChapterScene implements Screenshots.Scene {
        private int opened = -1;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Scene scene = GuideScenes.byId(RxBlockScene.ID);
            if (scene == null) {
                return StepResult.fail("Kapitel " + RxBlockScene.ID + " nicht angemeldet");
            }
            if (this.opened < 0) {
                SceneScreen screen = new SceneScreen(scene, null);
                mc.gui.setScreen(screen);
                screen.playback().pause();
                screen.playback().seek(scene.stepStart(3) + 90.0F);
                this.opened = ctx.tick();
                return StepResult.pending();
            }
            return mc.gui.screen() instanceof SceneScreen && ctx.tick() - this.opened > 10 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            this.opened = -1;
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerLevel level = RxSelfTests.player(server).level();
                if (RxSelfTests.SITE.get() != null) {
                    for (BlockPos pos : showcase()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                    level.setBlock(showcase().get(1).south(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    for (BlockPos pos : levels()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                long ticks = CLOCK.getAndSet(-1L);
                Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
                if (ticks >= 0L && clock.isPresent()) {
                    server.clockManager().setTotalTicks(clock.get(), ticks);
                }
                RxSelfTests.tearDown(server);
            }));
            return StepResult.pending();
        }
        StepResult waiting = waitFuture(ctx);
        return waiting != null ? waiting : StepResult.pass();
    }
}
