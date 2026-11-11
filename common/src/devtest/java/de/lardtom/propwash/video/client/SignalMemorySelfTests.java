package de.lardtom.propwash.video.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SignalJoinCheck;
import de.lardtom.propwash.video.link.ObstructionScan;
import de.lardtom.propwash.video.link.RfMaterial;
import de.lardtom.propwash.video.link.TerrainColumns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class SignalMemorySelfTests {
    public static final double TOLERANCE_DB = 3.0;
    public static final double MIN_WALL_DB = 20.0;
    public static final double OPEN_DB = 0.5;
    private static final int SITE_DX = 640;
    private static final int SITE_DZ = -640;
    private static final int CHUNK_MARGIN = 20;
    private static final int SETTLE_TICKS = 10;
    private static final int TIMEOUT_TICKS = 600;
    private static final int JOIN_TIMEOUT_TICKS = 20 * 75;
    private static final int STREAM_QUIET_TICKS = 40;
    private static final int STREAM_TIMEOUT_TICKS = 20 * 30;
    private static final int WALL_SEARCH_FROM = 20;
    private static final int WALL_SEARCH_TO = 80;
    private static final double PLACE_TOLERANCE = 1.5;
    private static final String FUTURE = "signal_future";
    private static final String PHASE = "signal_phase";
    private static final String SINCE = "signal_since";
    private static final String READY = "signal_ready";
    private static final String LOADED_DB = "signal_loaded_db";
    private static final String JOIN_START = "signal_join_start";
    private static final String JOIN_ORIGIN = "signal_join_origin";
    private static final String JOIN_BASE = "signal_join_base";
    private static final String JOIN_SEEN = "signal_join_seen";
    private static final String JOIN_LOADED = "signal_join_loaded";
    private static final String STREAM_COUNT = "signal_stream_count";
    private static final String STREAM_SINCE = "signal_stream_since";
    private static final String JOIN_AWAY = "signal_join_away";
    private static final String HOME = "signal_home";
    private static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    private static final AtomicReference<BlockPos> ORIGIN = new AtomicReference<>();
    private static final AtomicReference<Double> REMEMBERED = new AtomicReference<>();
    private static final AtomicInteger AWAY = new AtomicInteger(SignalJoinCheck.AWAY);

    private enum Phase { ARRIVE, AWAY, DONE }

    private record Away(double remembered, double off, int chunks, int tick) {
    }

    private SignalMemorySelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("signal: Gelände außerhalb der Sichtweite gemerkt", 2400, SignalMemorySelfTests::rememberStep);
        SelfTest.clientStep("signal: ohne Gedächtnis Luft (Gegenprobe)", 200, SignalMemorySelfTests::withoutMemoryStep);
        SelfTest.clientStep("signal: geladene Chunks haben Vorrang vor dem Gedächtnis", 1600, SignalMemorySelfTests::loadedFirstStep);
        SelfTest.clientStep("signal: Aufräumen", 1200, SignalMemorySelfTests::cleanupStep);
    }

    private static StepResult rememberStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            REMEMBERED.set(null);
            TerrainMemory.INSTANCE.enabled(true);
            begin(ctx, Phase.ARRIVE, ctx.onServer(SignalMemorySelfTests::buildSite));
            return StepResult.pending();
        }
        StepResult waiting = awaitServer(ctx, "Aufbau");
        if (waiting != null) {
            return waiting;
        }
        BlockPos origin = ORIGIN.get();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (origin == null || level == null || player == null) {
            return timeout(ctx, "kein Testgelände oder keine Welt");
        }
        Phase phase = (Phase) state.get(PHASE);
        if (phase == Phase.ARRIVE) {
            boolean ready = near(player, stand(origin)) && allLoaded(level, origin) && wallVisible(level, origin, true);
            if (!settled(ctx, ready)) {
                return timeout(ctx, "Wand oder Chunks kommen beim Client nicht an (Platz " + near(player, stand(origin)) + ", Chunks "
                        + allLoaded(level, origin) + ", Wand " + wallVisible(level, origin, true) + ")");
            }
            double loaded = measure(level, origin);
            state.put(LOADED_DB, loaded);
            ctx.log(String.format(Locale.ROOT, "Verlust A mit geladenen Chunks: %.2f dB", loaded));
            if (loaded < MIN_WALL_DB) {
                return StepResult.fail(String.format(Locale.ROOT, "Wand schirmt geladen nur %.2f dB ab (mindestens %.0f dB erwartet)", loaded, MIN_WALL_DB));
            }
            begin(ctx, Phase.AWAY, ctx.onServer(server -> {
                AWAY.set(SignalJoinCheck.away(server));
                place(player(server), away(origin));
            }));
            return StepResult.pending();
        }
        if (phase == Phase.AWAY) {
            boolean ready = near(player, away(origin)) && noneLoaded(level, origin) && allRemembered(level, origin);
            if (!settled(ctx, ready)) {
                return timeout(ctx, String.format(Locale.ROOT, "Chunks der Wand werden nicht verworfen oder nicht gemerkt (Platz %s, %d Blöcke weg, %d von %d noch geladen, %d gemerkt, Gedächtnis %d Chunks)",
                        near(player, away(origin)), AWAY.get(), count(level, origin, true), pathChunks(origin).size(), count(level, origin, false), TerrainMemory.INSTANCE.size()));
            }
            double loaded = (double) state.get(LOADED_DB);
            double remembered = measure(level, origin);
            REMEMBERED.set(remembered);
            state.put(PHASE, Phase.DONE);
            String columns = wallColumns(level, origin);
            ctx.log(String.format(Locale.ROOT, "Verlust B aus dem Gedächtnis: %.2f dB (A %.2f dB, Abweichung %.2f dB), Spieler %d Blöcke weg, %d Chunks gemerkt (%d Mal, Ø %.3f ms je Chunk); %s",
                    remembered, loaded, Math.abs(remembered - loaded), AWAY.get(), TerrainMemory.INSTANCE.size(), TerrainMemory.INSTANCE.scans(),
                    TerrainMemory.INSTANCE.averageScanMillis(), columns));
            if (!columns.startsWith("Wand-Säule")) {
                return StepResult.fail(columns);
            }
            if (remembered < MIN_WALL_DB) {
                return StepResult.fail(String.format(Locale.ROOT, "gemerkte Wand schirmt nur %.2f dB ab (A %.2f dB)", remembered, loaded));
            }
            return StepResult.check(Math.abs(remembered - loaded) <= TOLERANCE_DB,
                    String.format(Locale.ROOT, "gemerkt %.2f dB statt %.2f dB wie geladen (höchstens ±%.0f dB)", remembered, loaded, TOLERANCE_DB));
        }
        return StepResult.pass();
    }

    private static StepResult withoutMemoryStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos origin = ORIGIN.get();
        Double remembered = REMEMBERED.get();
        ClientLevel level = mc.level;
        if (origin == null || remembered == null || level == null) {
            return StepResult.fail("braucht den Schritt „signal: Gelände außerhalb der Sichtweite gemerkt“ davor");
        }
        if (!noneLoaded(level, origin) || !allRemembered(level, origin)) {
            return StepResult.fail("Chunks der Wand sind wieder geladen oder nicht mehr gemerkt");
        }
        double off;
        TerrainMemory.INSTANCE.enabled(false);
        try {
            off = measure(level, origin);
        } finally {
            TerrainMemory.INSTANCE.enabled(true);
        }
        double on = measure(level, origin);
        MinecraftServer server = ctx.server();
        Level foreign = server == null ? null : server.overworld();
        boolean foreignSees = false;
        if (foreign != null) {
            for (ChunkPos chunk : pathChunks(origin)) {
                foreignSees |= TerrainMemory.INSTANCE.columns(foreign, chunk.x(), chunk.z()) != null;
            }
        }
        ctx.log(String.format(Locale.ROOT, "ohne Gedächtnis %.2f dB, wieder mit Gedächtnis %.2f dB (vorher %.2f dB), andere Welt sieht das Gedächtnis: %s",
                off, on, remembered, foreignSees));
        if (off > 0.0) {
            return StepResult.fail(String.format(Locale.ROOT, "ohne Gedächtnis %.2f dB statt 0 dB (nicht geladene Chunks müssen Luft sein)", off));
        }
        if (foreignSees) {
            return StepResult.fail("eine andere Welt liest das Gedächtnis dieser Welt");
        }
        return StepResult.check(Math.abs(on - remembered) < 0.01, String.format(Locale.ROOT, "wieder eingeschaltet %.2f dB statt %.2f dB", on, remembered));
    }

    private static StepResult loadedFirstStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(PHASE)) {
            BlockPos origin = ORIGIN.get();
            if (origin == null) {
                return StepResult.fail("braucht den Schritt „signal: Gelände außerhalb der Sichtweite gemerkt“ davor");
            }
            begin(ctx, Phase.ARRIVE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                SignalJoinCheck.wall(player.level(), origin, Blocks.AIR.defaultBlockState());
                place(player, stand(origin));
            }));
            return StepResult.pending();
        }
        StepResult waiting = awaitServer(ctx, "Abbau der Wand");
        if (waiting != null) {
            return waiting;
        }
        BlockPos origin = ORIGIN.get();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (origin == null || level == null || player == null) {
            return timeout(ctx, "kein Testgelände oder keine Welt");
        }
        boolean ready = near(player, stand(origin)) && allLoaded(level, origin) && wallVisible(level, origin, false);
        if (!settled(ctx, ready)) {
            return timeout(ctx, "Chunks ohne Wand kommen beim Client nicht an");
        }
        String columns = wallColumns(level, origin);
        double loaded = measure(level, origin);
        ctx.log(String.format(Locale.ROOT, "Wand abgebaut, Chunks wieder geladen: %.2f dB; Gedächtnis noch mit Wand: %s", loaded, columns));
        if (!columns.startsWith("Wand-Säule")) {
            return StepResult.fail("Gedächtnis hält die alte Wand nicht mehr: " + columns);
        }
        return StepResult.check(loaded <= OPEN_DB, String.format(Locale.ROOT, "geladene Chunks ohne Wand ergeben %.2f dB, das Gedächtnis hat Vorrang bekommen", loaded));
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        TerrainMemory.INSTANCE.enabled(true);
        if (!state.containsKey(PHASE)) {
            Saved saved = SAVED.get();
            if (saved != null) {
                state.put(HOME, saved.pos);
            }
            begin(ctx, Phase.ARRIVE, ctx.onServer(SignalMemorySelfTests::teardown));
            return StepResult.pending();
        }
        StepResult waiting = awaitServer(ctx, "Aufräumen");
        if (waiting != null) {
            return waiting;
        }
        Vec3 home = (Vec3) state.get(HOME);
        LocalPlayer player = mc.player;
        boolean ready = player != null && mc.level != null && (home == null || (near(player, home)
                && mc.level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(home.x), SectionPos.blockToSectionCoord(home.z)) != null));
        if (!settled(ctx, ready)) {
            return timeout(ctx, "Spieler kommt nach dem Test nicht an seinen Platz zurück");
        }
        return StepResult.pass();
    }

    public static @Nullable String joinProbe(Map<String, Object> state, int tick, Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return null;
        }
        int start = (int) state.computeIfAbsent(JOIN_START, key -> tick);
        boolean streamSettled = streamSettled(state, tick, mc, level, player);
        BlockPos origin = (BlockPos) state.computeIfAbsent(JOIN_ORIGIN, key -> player.blockPosition());
        if (!state.containsKey(JOIN_LOADED)) {
            BlockPos base = (BlockPos) state.get(JOIN_BASE);
            if (base == null) {
                base = findWall(level, origin);
                if (base == null) {
                    return tick - start > JOIN_TIMEOUT_TICKS ? "signal_memory=timeout no_wall\n" : null;
                }
                state.put(JOIN_BASE, base);
            }
            if (!allLoaded(level, base) || !wallVisible(level, base, true)) {
                state.remove(JOIN_SEEN);
                return tick - start > JOIN_TIMEOUT_TICKS ? "signal_memory=timeout wall_chunks\n" : null;
            }
            int seen = (int) state.computeIfAbsent(JOIN_SEEN, key -> tick);
            if (tick - seen < SETTLE_TICKS) {
                return null;
            }
            state.put(JOIN_LOADED, measure(level, base));
            return null;
        }
        BlockPos base = (BlockPos) state.get(JOIN_BASE);
        double loaded = (double) state.get(JOIN_LOADED);
        Away away = (Away) state.get(JOIN_AWAY);
        if (away == null) {
            if (!noneLoaded(level, base) || !allRemembered(level, base)) {
                return tick - start > JOIN_TIMEOUT_TICKS ? String.format(Locale.ROOT, "signal_memory=timeout away%nsignal_loaded_db=%.2f%nsignal_dropped=%s%nsignal_remembered=%s%n",
                        loaded, noneLoaded(level, base), allRemembered(level, base)) : null;
            }
            double remembered = measure(level, base);
            double off;
            TerrainMemory.INSTANCE.enabled(false);
            try {
                off = measure(level, base);
            } finally {
                TerrainMemory.INSTANCE.enabled(true);
            }
            away = new Away(remembered, off, TerrainMemory.INSTANCE.size(), tick);
            state.put(JOIN_AWAY, away);
        }
        if (!streamSettled && tick - away.tick() < STREAM_TIMEOUT_TICKS) {
            return null;
        }
        boolean ok = loaded >= MIN_WALL_DB && away.remembered() >= MIN_WALL_DB && Math.abs(away.remembered() - loaded) <= TOLERANCE_DB;
        return String.format(Locale.ROOT, "signal_memory=%s%nsignal_loaded_db=%.2f%nsignal_remembered_db=%.2f%nsignal_without_memory_db=%.2f%nsignal_chunks=%d%nsignal_stream=%s%nsignal_stream_chunks=%d%nsignal_stream_quiet_ticks=%d%nsignal_stream_wait_ticks=%d%n",
                ok && away.off() <= 0.0 && streamSettled ? "ok" : "fail", loaded, away.remembered(), away.off(), away.chunks(),
                streamSettled ? "settled" : "open", level.getChunkSource().getLoadedChunksCount(), tick - (int) state.get(STREAM_SINCE), tick - away.tick());
    }

    private static boolean streamSettled(Map<String, Object> state, int tick, Minecraft mc, ClientLevel level, LocalPlayer player) {
        int count = level.getChunkSource().getLoadedChunksCount();
        Object last = state.get(STREAM_COUNT);
        if (!(last instanceof Integer previous) || previous != count) {
            state.put(STREAM_COUNT, count);
            state.put(STREAM_SINCE, tick);
        }
        return tick - (int) state.get(STREAM_SINCE) >= STREAM_QUIET_TICKS && aroundLoaded(level, player.chunkPosition(), mc.options.getEffectiveRenderDistance());
    }

    private static boolean aroundLoaded(ClientLevel level, ChunkPos center, int radius) {
        for (int x = center.x() - radius - 1; x <= center.x() + radius + 1; x++) {
            for (int z = center.z() - radius - 1; z <= center.z() + radius + 1; z++) {
                if (ChunkTrackingView.isInViewDistance(center.x(), center.z(), radius, x, z) && level.getChunkSource().getChunkNow(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    static double measure(ClientLevel level, BlockPos origin) {
        ClientBlockLoss blocks = new ClientBlockLoss();
        blocks.bind(level);
        Vec3 from = pathFrom(origin);
        Vec3 to = pathTo(origin);
        ObstructionScan scan = new ObstructionScan();
        scan.start(from.x, from.y, from.z, to.x, to.y, to.z);
        return scan.evaluate(blocks);
    }

    private static Vec3 pathFrom(BlockPos origin) {
        return new Vec3(origin.getX() + SignalJoinCheck.WALL_DX - SignalJoinCheck.PATH_MARGIN + 0.5,
                origin.getY() + SignalJoinCheck.WALL_HEIGHT / 2.0 + 0.5, origin.getZ() + 0.5);
    }

    private static Vec3 pathTo(BlockPos origin) {
        return new Vec3(origin.getX() + SignalJoinCheck.WALL_DX + SignalJoinCheck.WALL_THICKNESS + SignalJoinCheck.PATH_MARGIN + 0.5,
                origin.getY() + SignalJoinCheck.WALL_HEIGHT / 2.0 + 0.5, origin.getZ() + 0.5);
    }

    private static List<ChunkPos> pathChunks(BlockPos origin) {
        List<ChunkPos> chunks = new ArrayList<>();
        int fromX = SectionPos.blockToSectionCoord(pathFrom(origin).x);
        int toX = SectionPos.blockToSectionCoord(pathTo(origin).x);
        int fromZ = SectionPos.blockToSectionCoord(origin.getZ() - CHUNK_MARGIN);
        int toZ = SectionPos.blockToSectionCoord(origin.getZ() + CHUNK_MARGIN);
        for (int cx = fromX; cx <= toX; cx++) {
            for (int cz = fromZ; cz <= toZ; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        return chunks;
    }

    private static boolean allLoaded(ClientLevel level, BlockPos origin) {
        for (ChunkPos chunk : pathChunks(origin)) {
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean noneLoaded(ClientLevel level, BlockPos origin) {
        for (ChunkPos chunk : pathChunks(origin)) {
            if (level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null) {
                return false;
            }
        }
        return true;
    }

    private static int count(ClientLevel level, BlockPos origin, boolean loaded) {
        int count = 0;
        for (ChunkPos chunk : pathChunks(origin)) {
            if (loaded ? level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null : TerrainMemory.INSTANCE.remembers(level, chunk.x(), chunk.z())) {
                count++;
            }
        }
        return count;
    }

    private static boolean allRemembered(ClientLevel level, BlockPos origin) {
        for (ChunkPos chunk : pathChunks(origin)) {
            if (!TerrainMemory.INSTANCE.remembers(level, chunk.x(), chunk.z())) {
                return false;
            }
        }
        return true;
    }

    private static boolean wallVisible(ClientLevel level, BlockPos origin, boolean standing) {
        BlockPos near = origin.offset(SignalJoinCheck.WALL_DX, SignalJoinCheck.WALL_HEIGHT / 2, 0);
        BlockPos far = origin.offset(SignalJoinCheck.WALL_DX + SignalJoinCheck.WALL_THICKNESS - 1, SignalJoinCheck.WALL_HEIGHT - 1,
                SignalJoinCheck.WALL_HALF_WIDTH - 1);
        BlockPos other = origin.offset(SignalJoinCheck.WALL_DX, 0, -SignalJoinCheck.WALL_HALF_WIDTH);
        if (standing) {
            return level.getBlockState(near).is(Blocks.STONE) && level.getBlockState(far).is(Blocks.STONE) && level.getBlockState(other).is(Blocks.STONE);
        }
        return level.getBlockState(near).isAir() && level.getBlockState(far).isAir() && level.getBlockState(other).isAir();
    }

    private static @Nullable BlockPos findWall(ClientLevel level, BlockPos origin) {
        int y = origin.getY() + SignalJoinCheck.WALL_HEIGHT / 2;
        for (int dx = WALL_SEARCH_FROM; dx <= WALL_SEARCH_TO; dx++) {
            if (level.getBlockState(new BlockPos(origin.getX() + dx, y, origin.getZ())).is(Blocks.STONE)) {
                return new BlockPos(origin.getX() + dx - SignalJoinCheck.WALL_DX, origin.getY(), origin.getZ());
            }
        }
        return null;
    }

    private static String wallColumns(ClientLevel level, BlockPos origin) {
        int wallX = origin.getX() + SignalJoinCheck.WALL_DX;
        int openX = origin.getX() + SignalJoinCheck.WALL_DX - SignalJoinCheck.PATH_MARGIN / 2;
        int z = origin.getZ();
        TerrainColumns wall = TerrainMemory.INSTANCE.columns(level, SectionPos.blockToSectionCoord(wallX), SectionPos.blockToSectionCoord(z));
        TerrainColumns open = TerrainMemory.INSTANCE.columns(level, SectionPos.blockToSectionCoord(openX), SectionPos.blockToSectionCoord(z));
        if (wall == null || open == null) {
            return "Säulen nicht im Gedächtnis";
        }
        int top = origin.getY() + SignalJoinCheck.WALL_HEIGHT - 1;
        String text = String.format(Locale.ROOT, "Wand-Säule Oberkante %d (%s), freie Säule Oberkante %d (%s)",
                wall.top(wallX & 15, z & 15), wall.material(wallX & 15, z & 15), open.top(openX & 15, z & 15), open.material(openX & 15, z & 15));
        boolean right = wall.top(wallX & 15, z & 15) == top && wall.material(wallX & 15, z & 15) == RfMaterial.EARTH
                && open.top(openX & 15, z & 15) == origin.getY() - 1 && open.material(openX & 15, z & 15) == RfMaterial.EARTH;
        return right ? text : "falsche Säulen: " + text + ", erwartet Oberkante " + top + " und " + (origin.getY() - 1) + " aus EARTH";
    }

    private static void buildSite(MinecraftServer server) {
        ServerPlayer player = player(server);
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.gameMode(), player.getAbilities().flying));
        Saved saved = SAVED.get();
        ServerLevel level = player.level();
        int x = (int) Math.floor(saved.pos.x) + SITE_DX;
        int z = (int) Math.floor(saved.pos.z) + SITE_DZ;
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        BlockPos origin = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        ORIGIN.set(origin);
        SignalJoinCheck.wall(level, origin, Blocks.STONE.defaultBlockState());
        player.setGameMode(GameType.CREATIVE);
        place(player, stand(origin));
    }

    private static void teardown(MinecraftServer server) {
        ServerPlayer player = player(server);
        BlockPos origin = ORIGIN.getAndSet(null);
        if (origin != null) {
            SignalJoinCheck.wall(player.level(), origin, Blocks.AIR.defaultBlockState());
        }
        REMEMBERED.set(null);
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            player.setGameMode(saved.mode);
            player.getAbilities().flying = saved.flying && player.getAbilities().mayfly;
            player.onUpdateAbilities();
            player.teleportTo(player.level(), saved.pos.x, saved.pos.y, saved.pos.z, Set.of(), saved.yaw, saved.pitch, true);
        }
    }

    private static void begin(SelfTest.StepContext ctx, Phase phase, CompletableFuture<?> future) {
        Map<String, Object> state = ctx.state();
        state.put(PHASE, phase);
        state.put(SINCE, ctx.tick());
        state.put(FUTURE, future);
        state.remove(READY);
    }

    private static @Nullable StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static boolean settled(SelfTest.StepContext ctx, boolean ready) {
        Map<String, Object> state = ctx.state();
        if (!ready) {
            state.remove(READY);
            return false;
        }
        int since = (int) state.computeIfAbsent(READY, key -> ctx.tick());
        return ctx.tick() - since >= SETTLE_TICKS;
    }

    private static StepResult timeout(SelfTest.StepContext ctx, String reason) {
        Map<String, Object> state = ctx.state();
        if (state.containsKey(READY)) {
            return StepResult.pending();
        }
        return ctx.tick() - (int) state.get(SINCE) > TIMEOUT_TICKS ? StepResult.fail(reason) : StepResult.pending();
    }

    private static Vec3 stand(BlockPos origin) {
        return new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
    }

    private static Vec3 away(BlockPos origin) {
        return new Vec3(origin.getX() - AWAY.get() + 0.5, origin.getY(), origin.getZ() + 0.5);
    }

    private static boolean near(LocalPlayer player, Vec3 target) {
        return Math.hypot(player.getX() - target.x, player.getZ() - target.z) < PLACE_TOLERANCE;
    }

    private static void place(ServerPlayer player, Vec3 feet) {
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), -90.0F, 0.0F, true);
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler");
        }
        return players.getFirst();
    }

    private record Saved(Vec3 pos, float yaw, float pitch, GameType mode, boolean flying) {
    }
}
