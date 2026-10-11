package de.lardtom.propwash.bando.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.bando.BandoModule;
import de.lardtom.propwash.bando.BandoPiece;
import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.Mat;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.world.WorldCoursePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

final class BandoPlacementTests {
    private static final String FUTURE = "future";
    private static final int MAX_RINGS = 26;
    private static final int WANT = 3;
    private static final int KEEP = 24;
    private static final int GRID = 5;
    private static final int FLAT = 3;
    private static final List<Site> SITES = new CopyOnWriteArrayList<>();
    private static final List<Site> SHOTS = new CopyOnWriteArrayList<>();

    record Site(BandoFamily family, BandoVariant variant, ChunkPos chunk, int spread, String terrain) {
        Site as(String label) {
            return new Site(this.family, this.variant, this.chunk, this.spread, label);
        }

        String file() {
            return "bando_placement_" + this.variant.key() + "_" + this.terrain;
        }

        String label() {
            return this.variant.key() + "/" + this.terrain + " (Hang " + this.spread + ")";
        }
    }

    private BandoPlacementTests() {
    }

    static void register() {
        SelfTest.afterLeaveStep("bando: Lage – Plätze je Variante auf flachem und steilem Gelände gesucht", 6000, BandoPlacementTests::searchStep);
        SelfTest.afterLeaveStep("bando: Lage – Boden auf Geländehöhe, nichts schwebt, keine Pflanzen auf dem Bau", 20 * 60 * 10, new CheckTour()::tick);
        if (Screenshots.enabled()) {
            SelfTest.afterLeaveStep("bando: Lage – Luftbilder jeder Variante auf flachem und steilem Gelände", 20 * 60 * 12, new ShotTour()::tick);
        }
    }

    private static StepResult searchStep(SelfTest.StepContext ctx) {
        MinecraftServer server = ctx.server();
        if (server == null) {
            return StepResult.fail("keine Welt");
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, CompletableFuture.supplyAsync(() -> search(server)));
            return StepResult.pending();
        }
        StepResult waiting = BandoSelfTests.waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        String result = (String) ((CompletableFuture<?>) ctx.state().get(FUTURE)).join();
        if (!result.startsWith("ok")) {
            return StepResult.fail(result);
        }
        ctx.log(result.substring(2).trim());
        return StepResult.pass();
    }

    private static String search(MinecraftServer server) {
        BandoSelfTests.Generator gen = BandoSelfTests.Generator.overworld(server, BandoSelfTests.SEED);
        StructureSet set = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(Constants.id(BandoModule.SET));
        if (set == null || !(set.placement() instanceof WorldCoursePlacement placement)) {
            return "Strukturset bandos fehlt";
        }
        Map<BandoVariant, List<Site>> found = new EnumMap<>(BandoVariant.class);
        int cells = 0;
        int ring = 0;
        for (; ring <= MAX_RINGS && !enough(found); ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    cells++;
                    ChunkPos chunk = placement.getPotentialStructureChunk(BandoSelfTests.SEED, dx * placement.spacing(), dz * placement.spacing());
                    if (!placement.isStructureChunk(gen.state(), chunk.x(), chunk.z())) {
                        continue;
                    }
                    for (BandoFamily family : BandoFamily.values()) {
                        StructureStart start = gen.generate(server, BandoSelfTests.holder(server, family), BandoSelfTests.SEED, chunk);
                        if (!start.isValid()) {
                            continue;
                        }
                        BandoPiece piece = BandoWorldTests.piece(start);
                        if (piece != null) {
                            List<Site> list = found.computeIfAbsent(piece.variant(), v -> new ArrayList<>());
                            if (list.size() < KEEP) {
                                list.add(new Site(family, piece.variant(), chunk, spread(gen, piece), ""));
                            }
                        }
                        break;
                    }
                }
            }
        }
        SITES.clear();
        SHOTS.clear();
        List<String> missing = new ArrayList<>();
        StringBuilder log = new StringBuilder("ok ").append(cells).append(" Zellen bis Ring ").append(ring - 1).append(';');
        for (BandoVariant variant : BandoVariant.values()) {
            List<Site> list = found.getOrDefault(variant, List.of());
            if (list.isEmpty()) {
                missing.add(variant.key());
                continue;
            }
            for (int k = 0; k < list.size(); k++) {
                SITES.add(list.get(k).as("n" + k));
            }
            Site flat = list.stream().min(Comparator.comparingInt(Site::spread)).orElseThrow();
            Site steep = list.stream().max(Comparator.comparingInt(Site::spread)).orElseThrow();
            if (steep.spread() > flat.spread()) {
                SHOTS.add(flat.as("flat"));
                SHOTS.add(steep.as("slope"));
            } else {
                SHOTS.add(flat.as(flat.spread() <= FLAT ? "flat" : "slope"));
            }
            log.append(' ').append(variant.key()).append(": ").append(list.size()).append(" Plätze, Hang ").append(flat.spread()).append('–')
                    .append(steep.spread()).append(';');
        }
        if (!missing.isEmpty() && BandoSelfTests.SEED == BandoSelfTests.TEST_SEED) {
            return "keine Plätze für " + missing;
        }
        if (!missing.isEmpty()) {
            log.append(" keine Plätze bis Ring ").append(MAX_RINGS).append(" für ").append(missing).append(" (Seed ").append(BandoSelfTests.SEED)
                    .append(", nicht geprüft);");
        }
        if (SITES.isEmpty()) {
            return "keine Plätze für " + missing;
        }
        return log.append(' ').append(SITES.size()).append(" Plätze zur Prüfung, ").append(SHOTS.size()).append(" für Luftbilder").toString();
    }

    private static boolean enough(Map<BandoVariant, List<Site>> found) {
        for (BandoVariant variant : BandoVariant.values()) {
            if (found.getOrDefault(variant, List.of()).size() < WANT) {
                return false;
            }
        }
        return true;
    }

    private static int[] footprint(BandoPlan plan) {
        int g = plan.ground();
        int[] f = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int z = 0; z < plan.sizeZ(); z++) {
            for (int x = 0; x < plan.sizeX(); x++) {
                if (plan.mat(x, g, z).kind() == Mat.Kind.STRUCTURE || plan.mat(x, g + 1, z).kind() == Mat.Kind.STRUCTURE) {
                    f[0] = Math.min(f[0], x);
                    f[1] = Math.min(f[1], z);
                    f[2] = Math.max(f[2], x);
                    f[3] = Math.max(f[3], z);
                }
            }
        }
        return f[0] > f[2] ? new int[]{0, 0, plan.sizeX() - 1, plan.sizeZ() - 1} : f;
    }

    private static int spread(BandoSelfTests.Generator gen, BandoPiece piece) {
        BandoPlan plan = piece.plan();
        BandoTransform t = piece.transform();
        int[] f = footprint(plan);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                int x = f[0] + (f[2] - f[0]) * i / (GRID - 1);
                int z = f[1] + (f[3] - f[1]) * j / (GRID - 1);
                int h = gen.generator().getFirstOccupiedHeight(t.worldX(x, z), t.worldZ(x, z), Heightmap.Types.WORLD_SURFACE_WG, gen.height(), gen.random());
                low = Math.min(low, h);
                high = Math.max(high, h);
            }
        }
        return high - low;
    }

    private static @Nullable BandoPiece piece(ServerLevel level, MinecraftServer server, Site site) {
        return piece(level.getChunk(site.chunk().x(), site.chunk().z(), ChunkStatus.STRUCTURE_STARTS, true), server, site);
    }

    private static @Nullable BandoPiece piece(ChunkAccess chunk, MinecraftServer server, Site site) {
        StructureStart start = chunk.getStartForStructure(BandoSelfTests.holder(server, site.family()).value());
        BandoPiece piece = start == null ? null : BandoWorldTests.piece(start);
        return piece != null && piece.variant() == site.variant() ? piece : null;
    }

    private static final class CheckTour {
        private static final int RETRIES = 3;
        private int index;
        private int phase;
        private int retries;
        private volatile @Nullable CompletableFuture<?> load;
        private volatile @Nullable BandoPiece piece;
        private volatile boolean located;
        private volatile boolean missing;
        private @Nullable CompletableFuture<?> pending;
        private final List<String> problems = new CopyOnWriteArrayList<>();
        private final StringBuffer log = new StringBuffer();
        private volatile int blocks;

        StepResult tick(SelfTest.StepContext ctx) {
            if (SITES.isEmpty()) {
                return StepResult.fail("keine Plätze (Suche fehlgeschlagen)");
            }
            MinecraftServer server = ctx.server();
            if (server == null || !server.isRunning()) {
                this.pending = null;
                this.load = null;
                String at = this.index < SITES.size() ? SITES.get(this.index).label() : "Ende";
                return StepResult.fail("Server beendet, Prüfung bei " + at + " abgebrochen");
            }
            if (this.pending != null && this.pending.isCompletedExceptionally()) {
                return StepResult.fail("Server-Aktion: " + this.pending.exceptionNow());
            }
            if (this.pending != null && !this.pending.isDone()) {
                return StepResult.pending();
            }
            if (this.index >= SITES.size()) {
                ctx.log(this.blocks + " Blöcke an " + SITES.size() + " Plätzen;" + this.log);
                if (!this.problems.isEmpty()) {
                    return StepResult.fail(String.join("; ", this.problems));
                }
                return StepResult.pass();
            }
            Site site = SITES.get(this.index);
            switch (this.phase) {
                case 0 -> {
                    this.load = null;
                    this.piece = null;
                    this.located = false;
                    this.missing = false;
                    this.pending = ctx.onServer(s -> this.request(s, site));
                    this.phase = 1;
                }
                case 1 -> {
                    CompletableFuture<?> loading = this.load;
                    if (loading != null && !loading.isDone()) {
                        return StepResult.pending();
                    }
                    if (loading != null && loading.isCompletedExceptionally()) {
                        this.problems.add(site.label() + ": Chunks nicht geladen (" + loading.exceptionNow() + ")");
                        this.next();
                    } else if (!this.located) {
                        this.pending = ctx.onServer(s -> this.request(s, site));
                    } else if (this.piece == null) {
                        this.problems.add(site.label() + ": Start fehlt in der Welt");
                        this.next();
                    } else {
                        this.pending = ctx.onServer(s -> this.verify(s, site));
                        this.phase = 2;
                    }
                }
                default -> {
                    if (!this.missing) {
                        this.next();
                    } else if (this.retries < RETRIES) {
                        this.retries++;
                        this.missing = false;
                        this.located = false;
                        this.pending = ctx.onServer(s -> this.request(s, site));
                        this.phase = 1;
                    } else {
                        this.problems.add(site.label() + ": Chunks nach " + RETRIES + " Versuchen nicht geladen");
                        this.next();
                    }
                }
            }
            return StepResult.pending();
        }

        private void next() {
            this.index++;
            this.phase = 0;
            this.retries = 0;
        }

        private void request(MinecraftServer server, Site site) {
            ServerChunkCache source = server.overworld().getChunkSource();
            LevelChunk chunk = source.getChunkNow(site.chunk().x(), site.chunk().z());
            if (chunk == null) {
                this.load = source.addTicketAndLoadWithRadius(TicketType.PLAYER_SPAWN, site.chunk(), 0);
                return;
            }
            BandoPiece found = piece(chunk, server, site);
            this.piece = found;
            this.located = true;
            if (found == null) {
                this.load = null;
                return;
            }
            int[] area = area(found);
            int radius = Math.max(area[2] - area[0], area[3] - area[1]) / 2 + 1;
            ChunkPos center = new ChunkPos((area[0] + area[2]) / 2, (area[1] + area[3]) / 2);
            this.load = source.addTicketAndLoadWithRadius(TicketType.PLAYER_SPAWN, center, radius);
        }

        private static int[] area(BandoPiece piece) {
            BandoTransform t = piece.transform();
            return new int[] {(t.originX() >> 4) - 1, (t.originZ() >> 4) - 1, ((t.originX() + t.widthX() - 1) >> 4) + 1,
                    ((t.originZ() + t.widthZ() - 1) >> 4) + 1};
        }

        private void verify(MinecraftServer server, Site site) {
            ServerLevel level = server.overworld();
            BandoPiece piece = this.piece;
            if (piece == null) {
                this.problems.add(site.label() + ": Start fehlt nach der Erzeugung");
                return;
            }
            int[] area = area(piece);
            for (int cx = area[0]; cx <= area[2]; cx++) {
                for (int cz = area[1]; cz <= area[3]; cz++) {
                    if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                        this.missing = true;
                        return;
                    }
                }
            }
            boolean land = site.family().terrain() != BandoFamily.Terrain.SHORE;
            BandoGroundCheck.Report report = BandoGroundCheck.check(level, piece, land);
            BandoTransform t = piece.transform();
            BlockPos center = new BlockPos(t.originX() + t.widthX() / 2, t.worldY(piece.plan().ground()), t.originZ() + t.widthZ() / 2);
            String biome = level.getBiome(center).getRegisteredName().replace("minecraft:", "");
            this.blocks += report.blocks();
            this.log.append(' ').append(site.label()).append(' ').append(biome).append(" @").append(center.getX()).append(',').append(center.getY())
                    .append(',').append(center.getZ()).append(": ").append(report.summary()).append(';');
            if (report.floating() > 0) {
                this.problems.add(site.label() + ": " + report.floating() + " Blöcke schweben " + report.floatingAt());
            }
            if (report.plants() > 0) {
                this.problems.add(site.label() + ": " + report.plants() + " Pflanzen ohne Halt oder auf dem Dach " + report.plantsAt());
            }
            if (report.terrain() > 0) {
                this.problems.add(site.label() + ": " + report.terrain() + " Geländeblöcke über der Bodenzeile in Grundriss und Hof " + report.site().terrainAt());
            }
            if (report.raised() > 0) {
                this.problems.add(site.label() + ": " + report.raised() + " Pflanzen auf Gelände über der Bodenzeile " + report.site().raisedAt());
            }
            if (report.rooted() > 0) {
                this.problems.add(site.label() + ": " + report.rooted() + " Bäume wurzeln in Grundriss oder Hof " + report.site().rootedAt());
            }
            if (land && report.median() < 0) {
                this.problems.add(site.label() + ": Bando sitzt auf dem Gelände (Gelände am Rand im Median " + report.median() + " unter dem Boden)");
            }
        }
    }

    private static final class ShotTour {
        private int index;
        private String phase = "resize";
        private int since;
        private int stable;
        private int count;
        private @Nullable CompletableFuture<?> pending;
        private @Nullable CompletableFuture<String> shot;

        StepResult tick(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.pending != null && this.pending.isCompletedExceptionally()) {
                return this.fail(ctx, "Server-Aktion: " + this.pending.exceptionNow());
            }
            switch (this.phase) {
                case "resize" -> {
                    StepResult resized = Screenshots.resizeWindow(ctx);
                    if (resized.status() == StepResult.Status.PENDING) {
                        return resized;
                    }
                    this.next("go", ctx);
                }
                case "go" -> {
                    if (this.index >= SHOTS.size()) {
                        StepResult restored = Screenshots.restoreWindow(ctx);
                        if (mc.gui.hud.isHidden()) {
                            mc.gui.hud.toggle();
                        }
                        ctx.log(this.count + " Luftbilder in " + Screenshots.dir());
                        return restored;
                    }
                    Site site = SHOTS.get(this.index);
                    this.pending = ctx.onServer(server -> view(server, site));
                    this.next("wait", ctx);
                }
                case "wait" -> {
                    if (this.pending == null || !this.pending.isDone()) {
                        return StepResult.pending();
                    }
                    if (!mc.gui.hud.isHidden()) {
                        mc.gui.hud.toggle();
                    }
                    Screenshots.clearOverlays(mc);
                    Screenshots.parkMouse(mc);
                    if (mc.levelRenderer.hasRenderedAllSections() && ctx.tick() - this.since > 60) {
                        this.stable++;
                    } else {
                        this.stable = 0;
                    }
                    if (this.stable >= 20 || ctx.tick() - this.since > 600) {
                        this.shot = Screenshots.capture(SHOTS.get(this.index).file());
                        this.next("shot", ctx);
                    }
                }
                case "shot" -> {
                    if (this.shot == null || !this.shot.isDone()) {
                        return StepResult.pending();
                    }
                    if (this.shot.isCompletedExceptionally()) {
                        return this.fail(ctx, "Bildschirmfoto: " + this.shot.exceptionNow());
                    }
                    this.count++;
                    this.index++;
                    this.next("go", ctx);
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + this.phase);
                }
            }
            return StepResult.pending();
        }

        private StepResult fail(SelfTest.StepContext ctx, String reason) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.restoreWindow(ctx);
            Site site = this.index < SHOTS.size() ? SHOTS.get(this.index) : null;
            return StepResult.fail((site == null ? "" : site.label() + ": ") + reason);
        }

        private void next(String phase, SelfTest.StepContext ctx) {
            this.phase = phase;
            this.since = ctx.tick();
            this.stable = 0;
        }

        private static void view(MinecraftServer server, Site site) {
            ServerLevel level = server.overworld();
            BandoPiece piece = piece(level, server, site);
            if (piece == null) {
                throw new IllegalStateException("Start fehlt");
            }
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            CommandSourceStack quiet = server.createCommandSourceStack().withSource(CommandSource.NULL).withLevel(level);
            server.getCommands().performPrefixedCommand(quiet, "time set noon");
            server.getCommands().performPrefixedCommand(quiet, "weather clear");
            BandoPlan plan = piece.plan();
            BandoTransform t = piece.transform();
            int size = Math.max(t.widthX(), t.widthZ());
            Vec3 center = new Vec3(t.originX() + t.widthX() / 2.0, t.worldY(plan.ground()), t.originZ() + t.widthZ() / 2.0);
            double reach = size * 0.75 + 6.0;
            double best = Double.MAX_VALUE;
            double angle = 0.0;
            for (int k = 0; k < 8; k++) {
                double a = Math.toRadians(22.5 + 45.0 * k);
                int x = (int) Math.floor(center.x + Math.sin(a) * reach);
                int z = (int) Math.floor(center.z + Math.cos(a) * reach);
                double h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (h < best) {
                    best = h;
                    angle = a;
                }
            }
            Vec3 target = center.add(0.0, Math.min(plan.sizeY(), 24) * 0.25, 0.0);
            BandoCamera.Pose pose = BandoCamera.aerial(level, piece.getBoundingBox(), target, angle, size * 0.9 + 12.0, size * 0.45 + 9.0);
            player.teleportTo(level, pose.eye().x, pose.eye().y - player.getEyeHeight(), pose.eye().z, Set.of(), pose.yaw(), pose.pitch(), true);
        }
    }
}
