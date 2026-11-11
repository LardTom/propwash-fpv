package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class CovFreecamIrisShowcase {
    private static final String PREFIX = "bildschirmfotos: Iris Freecam – ";
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String CONFIG = "net.irisshaders.iris.api.v0.IrisApiConfig";
    private static final String FUTURE = "cov_iris_future";
    private static final String SWITCHED = "cov_iris_switched";
    private static final int SETTLE_TICKS = 40;
    private static final int OFFSET_X = -90;
    private static final int OFFSET_Z = 70;
    private static final int HALF = 10;
    private static final long MORNING = 1500L;
    private static final AtomicReference<BlockPos> SITE = new AtomicReference<>();
    private static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    private static final AtomicLong CLOCK = new AtomicLong(-1L);
    private static final AtomicBoolean READY = new AtomicBoolean();
    private static final List<Placed> PLACED = new CopyOnWriteArrayList<>();
    private static boolean startedWithShaders;

    private CovFreecamIrisShowcase() {
    }

    private record Saved(Vec3 pos, float yaw, float pitch) {
    }

    private record Placed(BlockPos pos, BlockState state) {
    }

    private enum View {
        TREE(-9.5, 2.6, -5.5),
        SIDE(-3.5, 2.0, -10.5),
        SHADOW(-6.5, 0.9, 1.5);

        final double dx;
        final double dy;
        final double dz;

        View(double dx, double dy, double dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    static void register() {
        if (!Screenshots.enabled() || !loaded()) {
            return;
        }
        String reset = PREFIX + "aufräumen";
        SelfTest.switchStep(PREFIX + "Platz mit Baum, Säule und Dach in der Morgensonne", reset, 1600, CovFreecamIrisShowcase::siteStep);
        SelfTest.clientStep(PREFIX + "Shaderpack an", 600, ctx -> shaders(ctx, true));
        Screenshots.step(PREFIX + "Freecam vor dem Baum, Schatten auf dem Platz", "cov_freecam_iris_01_shader", 40, new Shot(View.TREE));
        Screenshots.step(PREFIX + "Freecam seitlich versetzt", "cov_freecam_iris_02_shader", 40, new Shot(View.SIDE));
        Screenshots.step(PREFIX + "Freecam tief im Schatten", "cov_freecam_iris_03_shader", 40, new Shot(View.SHADOW));
        SelfTest.clientStep(PREFIX + "Shaderpack aus", 600, ctx -> shaders(ctx, false));
        Screenshots.step(PREFIX + "Freecam vor dem Baum ohne Shaderpack", "cov_freecam_iris_04_vanilla", 40, new Shot(View.TREE));
        SelfTest.clientStep(reset, 900, CovFreecamIrisShowcase::cleanup);
    }

    static boolean loaded() {
        return Services.PLATFORM.isModLoaded("iris") || Services.PLATFORM.isModLoaded("oculus");
    }

    private static Object api() throws ReflectiveOperationException {
        return Class.forName(API).getMethod("getInstance").invoke(null);
    }

    private static Object config() throws ReflectiveOperationException {
        return Class.forName(API).getMethod("getConfig").invoke(api());
    }

    static boolean packInUse() {
        try {
            return Boolean.TRUE.equals(Class.forName(API).getMethod("isShaderPackInUse").invoke(api()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    static boolean shadersEnabled() {
        try {
            return Boolean.TRUE.equals(Class.forName(CONFIG).getMethod("areShadersEnabled").invoke(config()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    static void enableShaders(boolean enabled) throws ReflectiveOperationException {
        Class.forName(CONFIG).getMethod("setShadersEnabledAndApply", boolean.class).invoke(config(), enabled);
    }

    private static StepResult shaders(SelfTest.StepContext ctx, boolean enabled) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(SWITCHED)) {
            if (enabled) {
                startedWithShaders = shadersEnabled();
            }
            if (shadersEnabled() != enabled) {
                try {
                    enableShaders(enabled);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return StepResult.fail("Iris-Shader nicht umschaltbar: " + e);
                }
            }
            state.put(SWITCHED, ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(SWITCHED) < SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (enabled != packInUse()) {
            return StepResult.fail(enabled ? "kein Shaderpack aktiv: Pack in shaderpacks/ legen und in config/iris.properties wählen" : "Shaderpack bleibt aktiv");
        }
        ctx.log("Iris: Shaderpack " + (enabled ? "an" : "aus"));
        return StepResult.pass();
    }

    private static StepResult siteStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            FreecamSelfTests.resetConfig();
            FreecamConfig config = FreecamConfig.get();
            config.setUptilt(FreecamPreset.FIVE, 0.0F);
            config.osd = false;
            FreecamConfig.save();
            READY.set(false);
            state.put(FUTURE, ctx.onServer(CovFreecamIrisShowcase::build));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Platz: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (!READY.get()) {
            state.put(FUTURE, ctx.onServer(server -> READY.set(loaded(server))));
            return ctx.tick() > 1200 ? StepResult.fail("Chunks am Platz nicht geladen") : StepResult.pending();
        }
        Minecraft mc = Minecraft.getInstance();
        BlockPos site = SITE.get();
        if (site == null || mc.player == null || mc.player.position().distanceTo(Vec3.atBottomCenterOf(site)) > 16.0 || !FreecamSelfTests.ruleSynced(true)) {
            return ctx.tick() > 1400 ? StepResult.fail("Spieler oder Regel nicht am Platz") : StepResult.pending();
        }
        if (!state.containsKey("since")) {
            state.put("since", ctx.tick());
        }
        return ctx.tick() - (int) state.get("since") > 40 && mc.levelRenderer.hasRenderedAllSections() ? StepResult.pass() : StepResult.pending();
    }

    private static boolean loaded(MinecraftServer server) {
        BlockPos site = SITE.get();
        if (site == null) {
            return false;
        }
        ServerLevel level = FreecamSelfTests.player(server).level();
        for (int cx = (site.getX() - HALF - 16) >> 4; cx <= (site.getX() + HALF + 16) >> 4; cx++) {
            for (int cz = (site.getZ() - HALF - 16) >> 4; cz <= (site.getZ() + HALF + 16) >> 4; cz++) {
                if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState before = level.getBlockState(pos);
        if (before.equals(state)) {
            return;
        }
        PLACED.add(new Placed(pos.immutable(), before));
        level.setBlockAndUpdate(pos, state);
    }

    private static void build(MinecraftServer server) {
        ServerPlayer player = FreecamSelfTests.player(server);
        ServerLevel level = player.level();
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot()));
        BlockPos spawn = level.getRespawnData().pos();
        int x = spawn.getX() + OFFSET_X;
        int z = spawn.getZ() + OFFSET_Z;
        level.getChunk(x >> 4, z >> 4);
        BlockPos site = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        SITE.set(site);
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                place(level, site.offset(dx, -1, dz), (dx + dz) % 2 == 0 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy <= 12; dy++) {
                    place(level, site.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dy = 3; dy <= 7; dy++) {
            int radius = dy >= 6 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius == 2) {
                        continue;
                    }
                    place(level, site.offset(dx, dy, dz), leaves);
                }
            }
        }
        for (int dy = 0; dy < 6; dy++) {
            place(level, site.above(dy), log);
        }
        BlockPos pillar = site.offset(5, 0, -5);
        for (int dy = 0; dy < 4; dy++) {
            place(level, pillar.above(dy), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                place(level, pillar.offset(dx - 2, 4, dz), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
        }
        time(server, level);
        WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, 1);
        player.teleportTo(level, site.getX() - 7.5, site.getY(), site.getZ() - 7.5, Set.of(), -45.0F, 10.0F, true);
    }

    private static void time(MinecraftServer server, ServerLevel level) {
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (clock.isEmpty()) {
            return;
        }
        long now = server.clockManager().getInstance(clock.get()).totalTicks();
        CLOCK.compareAndSet(-1L, now);
        server.clockManager().setTotalTicks(clock.get(), now - Math.floorMod(now, 24000L) + 24000L + MORNING);
    }

    private static void tearDown(MinecraftServer server) {
        ServerPlayer player = FreecamSelfTests.player(server);
        ServerLevel level = player.level();
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            Placed placed = PLACED.get(i);
            level.setBlockAndUpdate(placed.pos(), placed.state());
        }
        PLACED.clear();
        SITE.set(null);
        long ticks = CLOCK.getAndSet(-1L);
        Optional<Holder<WorldClock>> clock = level.dimensionType().defaultClock();
        if (ticks >= 0L && clock.isPresent()) {
            server.clockManager().setTotalTicks(clock.get(), ticks);
        }
        WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, WorldRuleSpecs.ALLOW_FREECAM.defaultValue());
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            player.teleportTo(level, saved.pos().x, saved.pos().y, saved.pos().z, Set.of(), saved.yaw(), saved.pitch(), true);
        }
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            FreecamSession.INSTANCE.clearTestInput();
            if (FreecamSession.active()) {
                FreecamSession.INSTANCE.end(Minecraft.getInstance(), FreecamRules.End.BUTTON);
            }
            FreecamSelfTests.restoreConfig();
            if (startedWithShaders && !shadersEnabled()) {
                try {
                    enableShaders(true);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return StepResult.fail("Iris-Shader nicht wieder an: " + e);
                }
            }
            state.put(FUTURE, ctx.onServer(CovFreecamIrisShowcase::tearDown));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow());
        }
        return future.isDone() && ctx.tick() > 20 ? StepResult.pass() : StepResult.pending();
    }

    private static float yawTowards(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static final class Shot implements Screenshots.Scene {
        private final View view;
        private int placed = -1;

        private Shot(View view) {
            this.view = view;
        }

        private void place() {
            BlockPos site = SITE.get();
            if (site == null) {
                return;
            }
            double x = site.getX() + 0.5 + this.view.dx;
            double z = site.getZ() + 0.5 + this.view.dz;
            float yaw = yawTowards(site.getX() + 0.5 - x, site.getZ() + 0.5 - z);
            FreecamSession.INSTANCE.testPlace(x, site.getY() + this.view.dy, z, yaw);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (SITE.get() == null) {
                return StepResult.fail("Platz fehlt");
            }
            if (!FreecamSession.active()) {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                FreecamSession.INSTANCE.tryStart(mc);
                return ctx.tick() > 200 ? StepResult.fail("Freecam startet nicht: " + FreecamSession.INSTANCE.lastDenial()) : StepResult.pending();
            }
            if (this.placed < 0) {
                FreecamSession.INSTANCE.clearTestInput();
                this.place();
                this.placed = ctx.tick();
                return StepResult.pending();
            }
            this.place();
            Screenshots.parkMouse(mc);
            return ctx.tick() - this.placed > 40 && mc.levelRenderer.hasRenderedAllSections() ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            this.place();
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            this.placed = -1;
        }
    }
}
