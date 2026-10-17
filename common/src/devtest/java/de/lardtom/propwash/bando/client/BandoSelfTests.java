package de.lardtom.propwash.bando.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.bando.BandoBlocks;
import de.lardtom.propwash.bando.BandoModule;
import de.lardtom.propwash.bando.BandoPiece;
import de.lardtom.propwash.bando.BandoScene;
import de.lardtom.propwash.bando.BandoStructure;
import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPalettes;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.Mat;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.ChestLoot;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.race.world.WorldCoursePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class BandoSelfTests {
    public static final long TEST_SEED = 20261003L;
    public static final long SEED = Long.getLong("propwash.selftest.bandoSeed", TEST_SEED);
    static final int SEARCH_RINGS = 14;
    static final int FAR_RINGS = 40;
    static final int STAT_RINGS = 8;
    static final double MIN_DISTANCE = 650.0;
    static final double MAX_DISTANCE = 2200.0;
    private static final int LOOT_ROLLS = 600;
    private static final int FLAT_OFFSET = 400;
    private static final String FUTURE = "future";
    private static final String OK = "ok";
    static final AtomicReference<Map<BandoFamily, List<ChunkPos>>> FOUND = new AtomicReference<>(Map.of());
    static final AtomicReference<Map<BandoVariant, ChunkPos>> VARIANTS = new AtomicReference<>(Map.of());
    static final AtomicReference<Double> START_MS = new AtomicReference<>(0.0);
    static final AtomicReference<Double> PLACE_MS = new AtomicReference<>(0.0);

    private BandoSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("bando: Strukturen, Tag, Set, Beute und Paletten registriert", 200, BandoSelfTests::registryStep);
        SelfTest.clientStep("bando: Weltgenerator findet jede Familie wie /locate, Seltenheit", 2400, BandoSelfTests::locateStep);
        SelfTest.clientStep("bando: alle Varianten in die Testwelt gesetzt und geprüft", 1200, BandoSelfTests::placeStep);
        SelfTest.clientStep("bando: Truhe würfelt gebrauchte Propwash-Teile", 200, BandoSelfTests::lootStep);
        BandoMapTests.registerClient();
        SelfTest.clientStep("bando: Flugschul-Szene angemeldet", 5, ctx -> StepResult.check(GuideScenes.byId(BandoScene.ID) != null
                && GuideScenes.all().stream().anyMatch(scene -> scene.id().equals(BandoScene.ID)), "Szene bandos fehlt in der Flugschule"));
        if (Screenshots.enabled()) {
            SelfTest.clientStep("bando: Bildschirmfotos der Flugschul-Szene", 1200, BandoSelfTests::sceneShots);
        }
        BandoWorldTests.register();
    }

    static @Nullable StepResult waitFor(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail("keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    static Registry<Structure> structures(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    }

    static Holder.Reference<Structure> holder(MinecraftServer server, BandoFamily family) {
        return structures(server).get(ResourceKey.create(Registries.STRUCTURE, Constants.id("bando/" + family.key())))
                .orElseThrow(() -> new IllegalStateException("Struktur fehlt: bando/" + family.key()));
    }

    private static StepResult registryStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            AtomicReference<String> result = new AtomicReference<>();
            ctx.state().put("result", result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(checkRegistry(server))));
            return StepResult.pending();
        }
        StepResult waiting = waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        @SuppressWarnings("unchecked")
        String result = ((AtomicReference<String>) ctx.state().get("result")).get();
        if (!result.startsWith(OK)) {
            return StepResult.fail(result);
        }
        ctx.log(result.substring(OK.length()).trim());
        return StepResult.pass();
    }

    private static String checkRegistry(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        HolderSet.Named<Structure> tagged = structures(server).get(BandoModule.TAG).orElse(null);
        if (tagged == null || tagged.size() != BandoFamily.values().length) {
            problems.add("Tag #propwash:bandos fehlt oder unvollständig");
        }
        for (BandoFamily family : BandoFamily.values()) {
            Holder.Reference<Structure> holder = holder(server, family);
            if (!(holder.value() instanceof BandoStructure bando) || bando.family() != family) {
                problems.add(family.key() + ": falscher Strukturtyp " + holder.value().type());
                continue;
            }
            if (!holder.is(BandoModule.TAG)) {
                problems.add(family.key() + ": nicht im Tag");
            }
            if (!holder.value().spawnOverrides().isEmpty()) {
                problems.add(family.key() + ": Mob-Spawns");
            }
            if (holder.value().biomes().size() == 0) {
                problems.add(family.key() + ": keine Biome");
            }
        }
        StructureSet set = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(Constants.id(BandoModule.SET));
        if (set == null || set.structures().size() != BandoFamily.values().length) {
            problems.add("Strukturset bandos fehlt oder unvollständig");
        } else if (!(set.placement() instanceof WorldCoursePlacement placement) || placement.spacing() < 32 || placement.avoid().isEmpty()) {
            problems.add("Strukturset zu dicht oder ohne Abstände: " + set.placement());
        }
        if (server.reloadableRegistries().getLootTable(BandoModule.LOOT) == LootTable.EMPTY) {
            problems.add("Beutetabelle chests/bando fehlt");
        }
        int states = 0;
        for (String state : BandoPalettes.allStates()) {
            if (BandoBlocks.parse(state).isEmpty()) {
                problems.add("Palette: " + state);
            }
            states++;
        }
        if (!problems.isEmpty()) {
            return String.join("; ", problems);
        }
        return OK + " " + BandoFamily.values().length + " Strukturen, " + BandoVariant.values().length + " Varianten, " + states + " Blockzustände";
    }

    record Generator(ChunkGenerator generator, BiomeSource biomes, Climate.Sampler sampler, RandomState random,
                     ChunkGeneratorStructureState state, LevelHeightAccessor height) {
        static Generator overworld(MinecraftServer server, long seed) {
            RegistryAccess access = server.registryAccess();
            Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            Holder<MultiNoiseBiomeSourceParameterList> preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            BiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(preset);
            ChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
            RandomState random = RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, settings.value());
            ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(random, seed, ChunkPos.ZERO, biomes,
                    access.lookupOrThrow(Registries.STRUCTURE_SET));
            Climate.Sampler sampler = random.createClimateSampler(SamplerContext.builder().enableCaches().build());
            LevelHeightAccessor height = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
            return new Generator(generator, biomes, sampler, random, state, height);
        }

        StructureStart generate(MinecraftServer server, Holder.Reference<Structure> holder, long seed, ChunkPos chunk) {
            Structure structure = holder.value();
            return structure.generate(holder, Level.OVERWORLD, server.registryAccess(), this.generator, this.biomes, this.sampler, this.random,
                    server.getStructureTemplateManager(), seed, chunk, 0, this.height, structure.biomes()::contains);
        }
    }

    private static StepResult locateStep(SelfTest.StepContext ctx) {
        MinecraftServer server = ctx.server();
        if (server == null) {
            return StepResult.fail("kein Server");
        }
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, CompletableFuture.supplyAsync(() -> locate(server)));
            return StepResult.pending();
        }
        StepResult waiting = waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        String result = (String) ((CompletableFuture<?>) ctx.state().get(FUTURE)).join();
        if (!result.startsWith(OK)) {
            return StepResult.fail(result);
        }
        ctx.log(result.substring(OK.length()).trim());
        return StepResult.pass();
    }

    private static String locate(MinecraftServer server) {
        Generator gen = Generator.overworld(server, SEED);
        StructureSet set = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(Constants.id(BandoModule.SET));
        if (set == null || !(set.placement() instanceof WorldCoursePlacement placement)) {
            return "Strukturset bandos fehlt";
        }
        Map<BandoFamily, List<ChunkPos>> found = new EnumMap<>(BandoFamily.class);
        Map<BandoVariant, Integer> variants = new EnumMap<>(BandoVariant.class);
        Map<BandoVariant, ChunkPos> firstOf = new EnumMap<>(BandoVariant.class);
        int cells = 0;
        int candidates = 0;
        int hits = 0;
        int chests = 0;
        long nanos = 0L;
        int tries = 0;
        for (int ring = 0; ring <= FAR_RINGS && (ring <= STAT_RINGS || !allFound(found)); ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    cells++;
                    ChunkPos chunk = placement.getPotentialStructureChunk(SEED, dx * placement.spacing(), dz * placement.spacing());
                    if (!placement.isStructureChunk(gen.state(), chunk.x(), chunk.z())) {
                        continue;
                    }
                    candidates++;
                    boolean hit = false;
                    for (BandoFamily family : BandoFamily.values()) {
                        if (ring > SEARCH_RINGS && found.containsKey(family)) {
                            continue;
                        }
                        long start = System.nanoTime();
                        StructureStart started = gen.generate(server, holder(server, family), SEED, chunk);
                        nanos += System.nanoTime() - start;
                        tries++;
                        if (!started.isValid()) {
                            continue;
                        }
                        List<ChunkPos> list = found.computeIfAbsent(family, f -> new ArrayList<>());
                        if (list.size() < 4) {
                            list.add(chunk);
                        }
                        for (StructurePiece piece : started.getPieces()) {
                            if (piece instanceof BandoPiece bando) {
                                variants.merge(bando.variant(), 1, Integer::sum);
                                firstOf.putIfAbsent(bando.variant(), chunk);
                                if (bando.plan().hasChest()) {
                                    chests++;
                                }
                            }
                        }
                        if (!hit && ring <= STAT_RINGS) {
                            hits++;
                        }
                        hit = true;
                        break;
                    }
                }
            }
        }
        FOUND.set(Collections.unmodifiableMap(found));
        VARIANTS.set(Collections.unmodifiableMap(firstOf));
        START_MS.set(nanos / 1.0E6 / Math.max(1, tries));
        List<String> far = new ArrayList<>();
        for (BandoFamily family : BandoFamily.values()) {
            if (!found.containsKey(family)) {
                far.add(family.key());
            }
        }
        if (!far.isEmpty() && SEED == TEST_SEED) {
            return "nicht alle Bando-Familien gefunden (" + cells + " Zellen, " + candidates + " Kandidaten): " + found.keySet();
        }
        int statCells = (2 * STAT_RINGS + 1) * (2 * STAT_RINGS + 1);
        double share = hits / (double) statCells;
        double distance = placement.spacing() * 16.0 / Math.sqrt(Math.max(share, 1.0E-6));
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            return String.format(Locale.ROOT, "etwa ein Bando je %.0f Blöcke, erwartet %.0f–%.0f (%d Bandos in %d Zellen)", distance,
                    MIN_DISTANCE, MAX_DISTANCE, hits, statCells);
        }
        int bandos = variants.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder log = new StringBuilder(OK).append(' ').append(cells).append(" Zellen, ").append(candidates).append(" Kandidaten, ")
                .append(String.format(Locale.ROOT, "%d/%d Zellen mit Bando (etwa einer je %.0f Blöcke), %d mit Truhe, Start im Mittel %.2f ms;",
                        hits, statCells, distance, chests, nanos / 1.0E6 / Math.max(1, tries)));
        for (Map.Entry<BandoVariant, Integer> entry : variants.entrySet()) {
            log.append(' ').append(entry.getKey().key()).append('=').append(entry.getValue());
        }
        for (Map.Entry<BandoFamily, List<ChunkPos>> entry : found.entrySet()) {
            ChunkPos first = entry.getValue().getFirst();
            log.append("; ").append(entry.getKey().key()).append('@').append(first.getMiddleBlockX()).append(',').append(first.getMiddleBlockZ());
        }
        if (!far.isEmpty()) {
            log.append("; keine ").append(String.join(", ", far)).append(" bis Ring ").append(FAR_RINGS).append(" (Seed ").append(SEED)
                    .append(", nicht geprüft)");
        }
        if (bandos < BandoFamily.values().length * 2) {
            return "zu wenige Bandos für eine Stichprobe: " + bandos;
        }
        return log.toString();
    }

    private static boolean allFound(Map<BandoFamily, List<ChunkPos>> found) {
        return found.size() == BandoFamily.values().length;
    }

    private static StepResult placeStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            AtomicReference<String> result = new AtomicReference<>();
            ctx.state().put("result", result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(placeAll(server))));
            return StepResult.pending();
        }
        StepResult waiting = waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        @SuppressWarnings("unchecked")
        String result = ((AtomicReference<String>) ctx.state().get("result")).get();
        if (!result.startsWith(OK)) {
            return StepResult.fail(result);
        }
        ctx.log(result.substring(OK.length()).trim());
        return StepResult.pass();
    }

    private static String placeAll(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, spawn.getX(), spawn.getZ()) - 1;
        List<String> problems = new ArrayList<>();
        int x = spawn.getX() + FLAT_OFFSET;
        int z = spawn.getZ() + FLAT_OFFSET;
        long nanos = 0L;
        int blocks = 0;
        int sites = 0;
        int chests = 0;
        int planted = 0;
        for (BandoVariant variant : BandoVariant.values()) {
            for (int i = 0; i < 2; i++) {
                long seed = 31L * variant.ordinal() + 977L * i + 5L;
                BandoPlan plan = BandoGenerator.generate(variant, seed);
                BandoTransform transform = BandoTransform.centered(plan, i + variant.ordinal(), x, ground - plan.ground(), z);
                BandoPiece piece = new BandoPiece(plan, transform);
                BoundingBox area = new BoundingBox(transform.originX() - 1, level.getMinY() + 1, transform.originZ() - 1,
                        transform.originX() + transform.widthX(), level.getMaxY(), transform.originZ() + transform.widthZ());
                planted += plant(level, plan, transform);
                long start = System.nanoTime();
                int placed = piece.place(level, area, level.getRandom());
                nanos += System.nanoTime() - start;
                blocks += placed;
                sites++;
                String problem = verify(level, piece);
                if (problem != null) {
                    problems.add(variant.key() + "/" + seed + ": " + problem);
                }
                BandoPiece loaded = new BandoPiece(piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
                if (!loaded.transform().equals(piece.transform()) || !loaded.getBoundingBox().equals(piece.getBoundingBox())
                        || loaded.plan().fingerprint() != plan.fingerprint() || loaded.variant() != variant) {
                    problems.add(variant.key() + "/" + seed + ": nach Speichern und Laden anders");
                }
                if (plan.hasChest()) {
                    chests++;
                }
                x += 72;
            }
        }
        PLACE_MS.set(nanos / 1.0E6 / sites);
        if (!problems.isEmpty()) {
            return String.join("; ", problems);
        }
        return OK + String.format(Locale.ROOT, " %d Bandos gesetzt, %d Blöcke, %d Truhen, im Mittel %.1f ms je Bando, vorher %d Grasbüschel im Grundriss",
                sites, blocks, chests, nanos / 1.0E6 / sites, planted);
    }

    private static int plant(ServerLevel level, BandoPlan plan, BandoTransform t) {
        BlockState grass = Blocks.SHORT_GRASS.defaultBlockState();
        int y = t.worldY(plan.ground()) + 1;
        int planted = 0;
        for (int z = 0; z < plan.sizeZ(); z++) {
            for (int x = 0; x < plan.sizeX(); x++) {
                BlockPos pos = new BlockPos(t.worldX(x, z), y, t.worldZ(x, z));
                if (level.getBlockState(pos).isAir() && grass.canSurvive(level, pos)) {
                    level.setBlock(pos, grass, 2);
                    planted++;
                }
            }
        }
        return planted;
    }

    static @Nullable String verify(ServerLevel level, BandoPiece piece) {
        BandoPlan plan = piece.plan();
        BandoTransform t = piece.transform();
        int checked = 0;
        int missing = 0;
        for (int y = 0; y < plan.sizeY(); y++) {
            for (int z = 0; z < plan.sizeZ(); z++) {
                for (int x = 0; x < plan.sizeX(); x++) {
                    Mat mat = plan.mat(x, y, z);
                    if (mat.kind() != Mat.Kind.STRUCTURE || t.worldY(y) <= level.getMinY()) {
                        continue;
                    }
                    BlockState state = level.getBlockState(new BlockPos(t.worldX(x, z), t.worldY(y), t.worldZ(x, z)));
                    checked++;
                    if (state.isAir()) {
                        missing++;
                    }
                }
            }
        }
        if (checked == 0) {
            return "kein Tragwerk im Plan";
        }
        if (missing > checked / 100) {
            return missing + " von " + checked + " Bauteilen fehlen";
        }
        BandoGroundCheck.Report report = BandoGroundCheck.check(level, piece, false);
        if (report.floating() > 0) {
            return report.floating() + " Blöcke schweben " + report.floatingAt();
        }
        if (report.plants() > 0) {
            return report.plants() + " Pflanzen ohne Halt oder auf dem Dach " + report.plantsAt();
        }
        if (report.terrain() > 0) {
            return report.terrain() + " Geländeblöcke über der Bodenzeile in Grundriss und Hof " + report.site().terrainAt();
        }
        if (report.raised() > 0) {
            return report.raised() + " Pflanzen auf Gelände über der Bodenzeile " + report.site().raisedAt();
        }
        if (report.rooted() > 0) {
            return report.rooted() + " Bäume wurzeln in Grundriss oder Hof " + report.site().rootedAt();
        }
        BlockPos chest = piece.chestPos();
        if (chest != null) {
            if (!level.getBlockState(chest).is(Blocks.CHEST)) {
                return "Truhe fehlt bei " + chest.toShortString();
            }
            if (!(level.getBlockEntity(chest) instanceof RandomizableContainer container) || !BandoModule.LOOT.equals(container.getLootTable())) {
                return "Truhe ohne Beutetabelle bei " + chest.toShortString();
            }
            if (level.getBlockState(chest.above()).isSolidRender()) {
                return "Truhe lässt sich nicht öffnen";
            }
        }
        return null;
    }

    private record SceneShot(String file, int step, float offset) {
    }

    private static final List<SceneShot> SCENE_SHOTS = List.of(
            new SceneShot("flight_school_bandos_fly", 1, 78.0F),
            new SceneShot("flight_school_bandos_chest", 2, 70.0F),
            new SceneShot("flight_school_bandos_map", 3, 60.0F));

    @SuppressWarnings("unchecked")
    private static StepResult sceneShots(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault("phase", 0);
        if (phase == 0) {
            mc.gui.setScreen(null);
            StepResult resized = Screenshots.resizeWindow(ctx);
            if (resized.status() != StepResult.Status.PASS) {
                return resized;
            }
            Screenshots.hideMarker(true);
            state.put("phase", 1);
            state.put("shot", 0);
            return StepResult.pending();
        }
        int index = (int) state.get("shot");
        Scene scene = GuideScenes.byId(BandoScene.ID);
        if (scene == null) {
            return StepResult.fail("Szene fehlt");
        }
        if (index >= SCENE_SHOTS.size()) {
            mc.gui.setScreen(null);
            Screenshots.hideMarker(false);
            return Screenshots.restoreWindow(ctx);
        }
        SceneShot shot = SCENE_SHOTS.get(index);
        switch (phase) {
            case 1 -> {
                mc.gui.setScreen(new SceneScreen(scene, null));
                Screenshots.clearOverlays(mc);
                state.put("since", ctx.tick());
                state.put("phase", 2);
            }
            case 2 -> {
                Screenshots.parkMouse(mc);
                if (mc.gui.screen() instanceof SceneScreen screen) {
                    screen.playback().seek(scene.stepStart(shot.step()) + shot.offset());
                    screen.playback().pause();
                }
                if (ctx.tick() - (int) state.get("since") >= 30) {
                    state.put(FUTURE, Screenshots.capture(shot.file()));
                    state.put("phase", 3);
                }
            }
            case 3 -> {
                CompletableFuture<String> future = (CompletableFuture<String>) state.get(FUTURE);
                if (!future.isDone()) {
                    return StepResult.pending();
                }
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Bildschirmfoto " + shot.file() + ": " + future.exceptionNow());
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(shot.file() + ".png"));
                state.put("shot", index + 1);
                state.put("phase", 1);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    private static StepResult lootStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            AtomicReference<String> result = new AtomicReference<>();
            ctx.state().put("result", result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(rollLoot(server))));
            return StepResult.pending();
        }
        StepResult waiting = waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        @SuppressWarnings("unchecked")
        String result = ((AtomicReference<String>) ctx.state().get("result")).get();
        if (!result.startsWith(OK)) {
            return StepResult.fail(result);
        }
        ctx.log(result.substring(OK.length()).trim());
        return StepResult.pass();
    }

    private static String rollLoot(MinecraftServer server) {
        ServerLevel level = server.overworld();
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        LootTable loot = server.reloadableRegistries().getLootTable(BandoModule.LOOT);
        if (loot == LootTable.EMPTY) {
            return "Beutetabelle fehlt";
        }
        List<String> problems = new ArrayList<>();
        int parts = 0;
        int used = 0;
        int all = 0;
        for (int seed = 1; seed <= LOOT_ROLLS; seed++) {
            for (ItemStack stack : loot.getRandomItems(params, seed)) {
                all++;
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (id.startsWith(Constants.MOD_ID + ":")) {
                    parts++;
                    if (PartCondition.worn(stack)) {
                        used++;
                    }
                    if (Batteries.isBattery(stack) && Math.abs(Batteries.charge(stack) - ChestLoot.FOUND_CHARGE) > 0.01F) {
                        problems.add("Akku mit Ladung " + Batteries.charge(stack));
                    }
                    if (id.contains("gate")) {
                        problems.add("Gate in der Bando-Truhe");
                    }
                }
            }
        }
        double perChest = parts / (double) LOOT_ROLLS;
        if (perChest < 1.5 || perChest > 6.0) {
            problems.add(perChest + " Propwash-Stapel je Truhe");
        }
        if (used == 0) {
            problems.add("keine gebrauchten Teile");
        }
        if (!problems.isEmpty()) {
            return String.join("; ", problems);
        }
        return OK + String.format(Locale.ROOT, " %.2f Propwash-Stapel je Truhe, %.0f %% davon gebraucht, %d Stapel insgesamt", perChest,
                100.0 * used / Math.max(1, parts), all);
    }
}
