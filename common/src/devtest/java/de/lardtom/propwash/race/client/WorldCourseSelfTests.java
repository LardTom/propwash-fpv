package de.lardtom.propwash.race.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.ChestLoot;
import de.lardtom.propwash.dev.RaceRulesJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseOrder;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.world.WorldCourseBoard;
import de.lardtom.propwash.race.world.WorldCourseMarker;
import de.lardtom.propwash.race.world.WorldCoursePiece;
import de.lardtom.propwash.race.world.WorldCourseSite;
import de.lardtom.propwash.race.world.WorldCourseStructure;
import de.lardtom.propwash.race.world.WorldCourseVariant;
import de.lardtom.propwash.race.world.WorldCourses;
import de.lardtom.propwash.server.ServerRulesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldCourseSelfTests {
    private static final long SEED = 20260924L;
    private static final int SEARCH_RINGS = 24;
    private static final int STAT_RINGS = 10;
    private static final double MIN_DISTANCE = 1000.0;
    private static final double MAX_DISTANCE = 4400.0;
    private static final int LOOT_ROLLS = 600;
    private static final int OFFSET = 6;
    private static final AtomicReference<String> PLACE_DEBUG = new AtomicReference<>("");
    private static final long TEST_LAP = 42_318_000_000L;
    private static final String FUTURE = "future";
    private static final String OK = "ok";
    private static final String PENDING = "pending";
    private static final Set<String> VANILLA_LOOT = Set.of("minecraft:bread", "minecraft:string", "minecraft:torch", "minecraft:copper_ingot");
    private static final Set<String> BETTER_PARTS = Set.of("propwash:m2207_1750", "propwash:analog_race", "propwash:aio_20",
            "propwash:b6s_1100", "propwash:action_cam_light", "propwash:drone", "propwash:gate_medium", "propwash:gate_large");
    private static final List<Identifier> OLD_TEMPLATES = List.of(Constants.id("world_course/drone_arena"),
            Constants.id("world_course/field_slalom"));
    private static final Identifier TWIN = Constants.id("world_course/v2/drone_arena");
    private static final String LINES = "/worldcourse/lines.json";
    private static final long MS = 1_000_000L;
    private static final double FOOT_EPSILON = 1.0E-3;

    private record Placement(Identifier template, WorldCourseVariant variant, BlockPos origin, int turns, BoundingBox box,
                             List<Vec3> expected, List<Boolean> reverse, BlockPos board, CourseMode mode, WorldCourseSite site) {
        String key() {
            return this.origin.getX() + "," + this.origin.getY() + "," + this.origin.getZ();
        }
    }

    private WorldCourseSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Weltkurse registriert (Strukturen, Tag, Vorlagen)", 100, WorldCourseSelfTests::registryStep);
        SelfTest.clientStep("race: Weltkurse im Testgelände gefunden wie mit /locate", 1200, WorldCourseSelfTests::locateStep);
        SelfTest.clientStep("race: Weltkurs-Vorlagen mit Gates in Kursreihenfolge", 4000, WorldCourseSelfTests::templateStep);
        SelfTest.clientStep("race: Weltkurse sauber abgeflogen – jede Vorlage eine gewertete Runde beim Client und am Server", 5000,
                WorldCourseSelfTests::flightStep);
        SelfTest.clientStep("race: Weltkurs zweimal gesetzt – zwei Kurs-IDs, getrennte Bestenlisten", 1200, WorldCourseSelfTests::twinStep);
        SelfTest.clientStep("race: Weltkurs-Truhe würfelt bescheidene Beute", 200, WorldCourseSelfTests::lootStep);
        if (Screenshots.enabled()) {
            for (String name : List.of("field_slalom", "canyon_run_badlands", "canyon_run_desert", "forest_weave_oak", "forest_weave_birch",
                    "forest_weave_spruce", "drone_arena")) {
                Screenshots.step("bildschirmfotos: Weltkurs " + name, "world_course_" + name, 60,
                        new CourseView(Constants.id("world_course/v2/" + name)));
            }
        }
    }

    private static @Nullable StepResult waitFor(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static Registry<Structure> structures(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
    }

    private static Holder.Reference<Structure> holder(MinecraftServer server, WorldCourseVariant variant) {
        return structures(server).get(ResourceKey.create(Registries.STRUCTURE, Constants.id(variant.key())))
                .orElseThrow(() -> new IllegalStateException("Struktur fehlt: " + variant.key()));
    }

    private static List<Identifier> templates(MinecraftServer server) {
        Set<Identifier> ids = new LinkedHashSet<>();
        for (WorldCourseVariant variant : WorldCourseVariant.values()) {
            if (holder(server, variant).value() instanceof WorldCourseStructure structure) {
                ids.add(structure.template());
                for (WorldCourseStructure.BiomeTemplate entry : structure.biomeTemplates()) {
                    ids.add(entry.template());
                }
            }
        }
        return List.copyOf(ids);
    }

    private static StepResult registryStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(WorldCourseSelfTests::checkRegistry));
            return StepResult.pending();
        }
        StepResult waiting = waitFor(ctx);
        return waiting != null ? waiting : StepResult.pass();
    }

    private static void checkRegistry(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        HolderSet.Named<Structure> tagged = structures(server).get(WorldCourses.TAG)
                .orElseThrow(() -> new IllegalStateException("Tag #propwash:world_courses fehlt (/locate structure #propwash:world_courses)"));
        if (tagged.size() != WorldCourseVariant.values().length) {
            problems.add("Tag enthält " + tagged.size() + " Strukturen");
        }
        for (WorldCourseVariant variant : WorldCourseVariant.values()) {
            Holder.Reference<Structure> holder = holder(server, variant);
            if (!(holder.value() instanceof WorldCourseStructure)) {
                problems.add(variant.key() + ": falscher Strukturtyp " + holder.value().type());
            }
            if (!holder.is(WorldCourses.TAG)) {
                problems.add(variant.key() + ": nicht im Tag");
            }
            if (!holder.value().spawnOverrides().isEmpty()) {
                problems.add(variant.key() + ": Mob-Spawns");
            }
        }
        for (Identifier id : templates(server)) {
            StructureTemplate template = server.getStructureTemplateManager().get(id).orElse(null);
            if (template == null || template.getSize().getX() < 16) {
                problems.add("Vorlage fehlt oder leer: " + id);
            }
        }
        StructureSet set = server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(Constants.id("world_courses"));
        if (set == null || set.structures().size() != WorldCourseVariant.values().length) {
            problems.add("Strukturset world_courses fehlt oder unvollständig");
        } else if (!(set.placement() instanceof RandomSpreadStructurePlacement spread) || spread.spacing() < 32) {
            problems.add("Strukturset zu dicht: " + set.placement());
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
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
        RegistryAccess access = server.registryAccess();
        Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        Holder<MultiNoiseBiomeSourceParameterList> preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        BiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(preset);
        ChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
        RandomState random = RandomState.create(access.lookupOrThrow(Registries.NOISE), SEED, settings.value());
        ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(random, SEED, ChunkPos.ZERO, biomes,
                access.lookupOrThrow(Registries.STRUCTURE_SET));
        Climate.Sampler sampler = random.createClimateSampler(SamplerContext.builder().enableCaches().build());
        LevelHeightAccessor height = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
        StructureSet set = access.lookupOrThrow(Registries.STRUCTURE_SET).getValue(Constants.id("world_courses"));
        if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
            return "Strukturset world_courses fehlt";
        }
        Map<WorldCourseVariant, ChunkPos> found = new EnumMap<>(WorldCourseVariant.class);
        int cells = 0;
        int candidates = 0;
        int hits = 0;
        int statCandidates = 0;
        for (int ring = 0; ring <= SEARCH_RINGS && (ring <= STAT_RINGS || found.size() < WorldCourseVariant.values().length); ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    cells++;
                    ChunkPos chunk = placement.getPotentialStructureChunk(SEED, dx * placement.spacing(), dz * placement.spacing());
                    if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) {
                        continue;
                    }
                    candidates++;
                    if (ring <= STAT_RINGS) {
                        statCandidates++;
                    }
                    boolean hit = false;
                    for (WorldCourseVariant variant : WorldCourseVariant.values()) {
                        if ((ring <= STAT_RINGS || !found.containsKey(variant))
                                && generate(server, variant, generator, biomes, sampler, random, chunk, height).isValid()) {
                            found.putIfAbsent(variant, chunk);
                            hit = true;
                        }
                    }
                    if (hit && ring <= STAT_RINGS) {
                        hits++;
                    }
                }
            }
        }
        if (found.size() < WorldCourseVariant.values().length) {
            return "nicht alle Weltkurse gefunden (" + cells + " Zellen, " + candidates + " Kandidaten): " + found;
        }
        int statCells = (2 * STAT_RINGS + 1) * (2 * STAT_RINGS + 1);
        double share = hits / (double) statCells;
        double distance = placement.spacing() * 16.0 / Math.sqrt(Math.max(share, 1.0E-6));
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            return String.format(Locale.ROOT, "etwa ein Kurs je %.0f Blöcke, erwartet %.0f–%.0f (%d Kurse, %d Kandidaten nach Abstandsregeln in %d Zellen)",
                    distance, MIN_DISTANCE, MAX_DISTANCE, hits, statCandidates, statCells);
        }
        Map.Entry<WorldCourseVariant, ChunkPos> sample = found.entrySet().iterator().next();
        ServerRulesConfig rules = ServerRulesConfig.get();
        boolean previous = rules.worldCourses;
        try {
            rules.worldCourses = false;
            if (generate(server, sample.getKey(), generator, biomes, sampler, random, sample.getValue(), height).isValid()) {
                return "Serverregel worldCourses=false verhindert die Erzeugung nicht";
            }
        } finally {
            rules.worldCourses = previous;
        }
        double spacingBlocks = placement.spacing() * 16.0;
        StringBuilder log = new StringBuilder(OK).append(' ').append(cells).append(" Zellen, ").append(candidates).append(" Kandidaten, ")
                .append(hits).append('/').append(statCells).append(String.format(Locale.ROOT, " Zellen mit Kurs (etwa einer je %.0f Blöcke):",
                        spacingBlocks / Math.sqrt(Math.max(share, 1.0E-6))));
        for (Map.Entry<WorldCourseVariant, ChunkPos> entry : found.entrySet()) {
            log.append(' ').append(entry.getKey().key()).append('@').append(entry.getValue().getMiddleBlockX()).append(',')
                    .append(entry.getValue().getMiddleBlockZ());
        }
        return log.toString();
    }

    private static StructureStart generate(MinecraftServer server, WorldCourseVariant variant, ChunkGenerator generator, BiomeSource biomes,
                                           Climate.Sampler sampler, RandomState random, ChunkPos chunk, LevelHeightAccessor height) {
        Holder.Reference<Structure> holder = holder(server, variant);
        Structure structure = holder.value();
        return structure.generate(holder, Level.OVERWORLD, server.registryAccess(), generator, biomes, sampler, random,
                server.getStructureTemplateManager(), SEED, chunk, 0, height, structure.biomes()::contains);
    }

    @SuppressWarnings("unchecked")
    private static StepResult templateStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            AtomicReference<List<Identifier>> ids = new AtomicReference<>();
            state.put("ids", ids);
            state.put(FUTURE, ctx.onServer(server -> {
                List<Identifier> all = new ArrayList<>(templates(server));
                all.addAll(OLD_TEMPLATES);
                ids.set(List.copyOf(all));
            }));
            state.put("index", 0);
            state.put("phase", "list");
            return StepResult.pending();
        }
        StepResult waiting = waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        List<Identifier> ids = ((AtomicReference<List<Identifier>>) state.get("ids")).get();
        int index = (Integer) state.get("index");
        String phase = (String) state.get("phase");
        AtomicReference<Placement> placement = (AtomicReference<Placement>) state.computeIfAbsent("placement", k -> new AtomicReference<Placement>());
        AtomicReference<String> status = (AtomicReference<String>) state.computeIfAbsent("status", k -> new AtomicReference<>(PENDING));
        switch (phase) {
            case "list", "next" -> {
                if (index >= ids.size()) {
                    ctx.log(ids.size() + " Vorlagen platziert (davon " + OLD_TEMPLATES.size() + " aus alten Welten), alle Gates in Kursreihenfolge");
                    return StepResult.pass();
                }
                Identifier id = ids.get(index);
                status.set(PENDING);
                state.put("since", ctx.tick());
                state.put(FUTURE, ctx.onServer(server -> placement.set(place(server, id, index % 4, false))));
                state.put("phase", "check");
            }
            case "check" -> {
                String current = status.get();
                if (current.equals(OK)) {
                    state.put(FUTURE, ctx.onServer(server -> cleanup(server, placement.get())));
                    state.put("phase", "next");
                    state.put("index", index + 1);
                    ctx.log(placement.get().template() + " (" + placement.get().turns() + " Vierteldrehungen) in Ordnung");
                } else if (!current.equals(PENDING)) {
                    Placement failed = placement.get();
                    ctx.onServer(server -> cleanup(server, failed));
                    return StepResult.fail(placement.get().template() + ": " + current);
                } else if (ctx.tick() - (Integer) state.get("since") > 400) {
                    Placement failed = placement.get();
                    ctx.onServer(server -> cleanup(server, failed));
                    return StepResult.fail(placement.get().template() + ": Gates nicht als Kurs übernommen");
                } else if (ctx.tick() % 10 == 0) {
                    Placement pending = placement.get();
                    boolean first = index == 0;
                    state.put(FUTURE, ctx.onServer(server -> status.set(check(server, pending, first))));
                }
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    private static Placement place(MinecraftServer server, Identifier id, int turns, boolean other) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        StructureTemplate template = server.getStructureTemplateManager().get(id).orElseThrow();
        Rotation rotation = switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        StructurePlaceSettings settings = WorldCoursePiece.settings(rotation);
        BoundingBox local = template.getBoundingBox(settings, BlockPos.ZERO);
        int cornerX = other ? player.getBlockX() - OFFSET - local.getXSpan() : player.getBlockX() + OFFSET;
        int cornerZ = player.getBlockZ() + OFFSET;
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, cornerX, cornerZ) - 1;
        BlockPos origin = new BlockPos(cornerX - local.minX(), ground, cornerZ - local.minZ());
        BoundingBox box = template.getBoundingBox(settings, origin);
        for (GateEntity old : level.getEntitiesOfClass(GateEntity.class, AABB.of(box).inflate(2.0))) {
            old.discard();
        }
        CompoundTag nbt = readTemplate(server, id);
        ListTag entities = nbt.getListOrEmpty("entities");
        Map<Integer, Vec3> expected = new TreeMap<>();
        Map<Integer, Boolean> reverse = new TreeMap<>();
        WorldCourseSite site = new WorldCourseSite(origin.getX(), origin.getY(), origin.getZ(), turns);
        BlockPos board = null;
        WorldCourseVariant variant = null;
        CourseMode mode = CourseMode.LAPS;
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag tag = entities.getCompoundOrEmpty(i).getCompoundOrEmpty("nbt").getCompoundOrEmpty("WorldCourse");
            WorldCourseMarker marker = WorldCourseMarker.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            double[] world = site.entityToWorld(marker.local().x, marker.local().y, marker.local().z);
            for (CourseOrder.Pass pass : marker.passes()) {
                expected.put(pass.index(), new Vec3(world[0], world[1], world[2]));
                reverse.put(pass.index(), pass.reverse());
            }
            int[] boardWorld = site.blockToWorld(marker.board().getX(), marker.board().getY(), marker.board().getZ());
            board = new BlockPos(boardWorld[0], boardWorld[1], boardWorld[2]);
            variant = WorldCourseVariant.byKey(marker.variant()).orElseThrow();
            mode = marker.mode();
        }
        if (board == null) {
            throw new IllegalStateException(id + ": Vorlage ohne Gates");
        }
        boolean placed = template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        List<StructureTemplate.StructureBlockInfo> grass = template.filterBlocks(origin, settings, Blocks.GRASS_BLOCK);
        List<StructureTemplate.StructureBlockInfo> path = template.filterBlocks(origin, settings, Blocks.DIRT_PATH);
        PLACE_DEBUG.set("placeInWorld=" + placed + " Grund danach " + level.getBlockState(origin) + " Oberfläche vorher " + ground
                + " Gras " + grass.size() + (grass.isEmpty() ? "" : " erstes " + grass.getFirst().pos() + "=" + level.getBlockState(grass.getFirst().pos()))
                + " Pfad " + path.size() + (path.isEmpty() ? "" : " erstes " + path.getFirst().pos() + "=" + level.getBlockState(path.getFirst().pos()))
                + " Größe " + template.getSize());
        return new Placement(id, variant, origin, turns, box, List.copyOf(expected.values()), List.copyOf(reverse.values()), board, mode, site);
    }

    private static CompoundTag readTemplate(MinecraftServer server, Identifier id) {
        Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt");
        Resource resource = server.getResourceManager().getResource(file).orElseThrow(() -> new IllegalStateException("fehlt: " + file));
        try (InputStream in = resource.open()) {
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException(file + " nicht lesbar", e);
        }
    }

    private static String check(MinecraftServer server, Placement placement, boolean extended) {
        ServerLevel level = server.overworld();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "CourseRegistry läuft nicht";
        }
        CourseRegistry.Course course = registry.worldCourse(CourseRegistry.dimensionOf(level), placement.key());
        if (course == null || course.gates.size() < placement.expected().size()) {
            return PENDING;
        }
        if (course.gates.size() != placement.expected().size()) {
            return "Kurs hat " + course.gates.size() + " statt " + placement.expected().size() + " Durchgänge";
        }
        if (!course.worldCourse.equals(placement.variant().key()) || !course.owner.isEmpty()) {
            return "Kurs falsch angelegt: " + course.worldCourse + " Besitzer " + course.owner;
        }
        if (course.mode() != placement.mode()) {
            return "Kurs fährt " + course.mode() + " statt " + placement.mode();
        }
        List<GateEntity> gates = new ArrayList<>();
        Set<String> checked = new LinkedHashSet<>();
        for (int i = 0; i < course.gates.size(); i++) {
            CourseRegistry.GateRecord record = course.gates.get(i);
            Vec3 expected = placement.expected().get(i);
            if (record.slot != i || record.index != i || Math.abs(record.x - expected.x) > 1.0E-6 || Math.abs(record.y - expected.y) > 1.0E-6
                    || Math.abs(record.z - expected.z) > 1.0E-6 || record.reversed() != placement.reverse().get(i)) {
                return "Durchgang " + i + " falsch eingereiht: slot " + record.slot + " bei " + record.x + "," + record.y + "," + record.z
                        + (record.reversed() ? " rückwärts" : "") + " statt " + expected + (placement.reverse().get(i) ? " rückwärts" : "");
            }
            Entity entity = level.getEntity(UUID.fromString(record.uuid));
            if (!(entity instanceof GateEntity gate) || gate.courseId() != course.id || gate.worldCourse() != null
                    || !gate.passes().equals(registry.passes(course, gate.getUUID()))) {
                return "Durchgang " + i + " nicht nummeriert: " + entity;
            }
            if (!checked.add(record.uuid)) {
                continue;
            }
            String ground = groundProblem(level, gate);
            if (ground != null) {
                return "Durchgang " + i + " (" + gate.kind().key() + ") " + ground + " (Ursprung " + placement.origin() + ", bei " + gate.position()
                        + ", Grund " + level.getBlockState(placement.origin()) + ", " + PLACE_DEBUG.get() + ")";
            }
            gates.add(gate);
        }
        if (!(level.getBlockEntity(placement.board()) instanceof SignBlockEntity sign)) {
            return "Tafel fehlt bei " + placement.board();
        }
        List<Component> lines = sign.getText(SignTextSlot.FRONT).getMessages(false);
        if (!translates(lines.get(0), placement.variant().nameKey()) || !translates(lines.get(1), WorldCourseBoard.COURSE_KEY)
                || !translates(lines.get(2), WorldCourseBoard.NO_RECORD_KEY)) {
            return "Tafel zeigt " + lines;
        }
        if (findChest(level, placement) == null) {
            return "Truhe mit Weltkurs-Beute fehlt";
        }
        if (extended) {
            GateEntity first = gates.getFirst();
            Vec3 before = first.position();
            level.explode(null, first.getX(), first.getY() + 1.0, first.getZ(), 3.0F, Level.ExplosionInteraction.NONE);
            if (first.isRemoved() || first.position().distanceTo(before) > 1.0E-6) {
                return "Gate von Explosion beschädigt oder verschoben";
            }
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            if (registry.submitLap(course, player, "race_5", TEST_LAP) != Leaderboard.Outcome.COURSE_RECORD) {
                return "Testrunde kein Kursrekord";
            }
            List<Component> updated = sign.getText(SignTextSlot.FRONT).getMessages(false);
            if (!translates(updated.get(2), WorldCourseBoard.RECORD_KEY) || !updated.get(3).getString().equals(player.getGameProfile().name())) {
                return "Tafel zeigt den Rekord nicht: " + updated;
            }
        }
        return OK;
    }

    private static @Nullable String groundProblem(ServerLevel level, GateEntity gate) {
        GateGeometry geometry = gate.geometry();
        GateStacking.Base base = gate.kind().gate() ? GateStacking.Base.of(gate.gateBelow() != null, gate.gateAbove() != null) : GateStacking.Base.BAR;
        List<AABB> boxes = new ArrayList<>();
        geometry.boxes((x0, y0, z0, x1, y1, z1) -> boxes.add(new AABB(x0, y0, z0, x1, y1, z1)), base);
        for (AABB raw : boxes) {
            AABB box = raw.deflate(1.0E-3);
            if (!level.noBlockCollision(null, box)) {
                return "steckt in Blöcken bei " + box;
            }
            if (base == GateStacking.Base.STACKED || Math.abs(raw.minY - geometry.y()) > FOOT_EPSILON || gate.kind().gate() && raw.getYsize() < 1.0) {
                continue;
            }
            double inset = Math.min(0.05, Math.min(raw.getXsize(), raw.getZsize()) * 0.25);
            for (double[] corner : new double[][]{{raw.minX + inset, raw.minZ + inset}, {raw.maxX - inset, raw.minZ + inset},
                    {raw.minX + inset, raw.maxZ - inset}, {raw.maxX - inset, raw.maxZ - inset}}) {
                BlockPos below = BlockPos.containing(corner[0], geometry.y() - FOOT_EPSILON, corner[1]);
                VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
                double top = shape.isEmpty() ? Double.NEGATIVE_INFINITY : below.getY() + shape.max(Direction.Axis.Y);
                if (Math.abs(top - geometry.y()) > FOOT_EPSILON) {
                    return "steht nicht bündig auf " + level.getBlockState(below) + " bei " + below + " (Oberkante " + top + ", Fuß " + geometry.y() + ")";
                }
            }
        }
        if (!gate.kind().gate()) {
            return null;
        }
        double inner = geometry.innerHalfWidth() * 0.5;
        AABB opening = new AABB(geometry.x() - inner, geometry.centerY() - 0.5, geometry.z() - inner,
                geometry.x() + inner, geometry.centerY() + 0.5, geometry.z() + inner);
        return level.noBlockCollision(null, opening) ? null : "Öffnung zugebaut";
    }

    private static boolean translates(Component component, String key) {
        return component.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
    }

    private static @Nullable ChestBlockEntity findChest(ServerLevel level, Placement placement) {
        BoundingBox box = placement.box();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.minY() + 2, box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.getLootTable() != null
                    && chest.getLootTable().identifier().getPath().startsWith("chests/world_course/")) {
                return chest;
            }
        }
        return null;
    }

    private static void cleanup(MinecraftServer server, @Nullable Placement placement) {
        if (placement == null) {
            return;
        }
        ServerLevel level = server.overworld();
        BoundingBox box = placement.box();
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, AABB.of(box).inflate(2.0))) {
            gate.discard();
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null) {
            CourseRegistry.Course course = registry.worldCourse(CourseRegistry.dimensionOf(level), placement.key());
            if (course != null) {
                registry.deleteCourse(course.id);
            }
        }
        ChestBlockEntity chest = findChest(level, placement);
        if (chest != null) {
            chest.setLootTable(null);
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            level.setBlock(pos, pos.getY() == box.minY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), flags);
        }
        for (Entity item : level.getEntitiesOfClass(ItemEntity.class, AABB.of(box).inflate(2.0))) {
            item.discard();
        }
    }

    private static JsonObject lines() {
        try (InputStream in = WorldCourseSelfTests.class.getResourceAsStream(LINES)) {
            if (in == null) {
                throw new IllegalStateException("Fluglinien fehlen: " + LINES);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new IllegalStateException(LINES + " nicht lesbar", e);
        }
    }

    private static List<Vec3> line(Placement placement) {
        String path = placement.template().getPath();
        JsonObject entry = lines().getAsJsonObject(path.substring(path.lastIndexOf('/') + 1));
        if (entry == null) {
            throw new IllegalStateException("keine Fluglinie für " + placement.template());
        }
        List<Vec3> points = new ArrayList<>();
        for (JsonElement element : entry.getAsJsonArray("line")) {
            JsonArray point = element.getAsJsonArray();
            double[] world = placement.site().entityToWorld(point.get(0).getAsDouble(), point.get(1).getAsDouble(), point.get(2).getAsDouble());
            points.add(new Vec3(world[0], world[1], world[2]));
        }
        return points;
    }

    private static List<Integer> of(List<LapTracker.Event> events, LapTracker.EventType type) {
        return events.stream().filter(e -> e.type() == type).map(LapTracker.Event::gate).toList();
    }

    private static @Nullable String clientLap(CourseData data, List<Vec3> path) {
        List<GateGeometry> gates = ClientCourses.geometry(data);
        boolean pointToPoint = data.mode() == CourseMode.POINT_TO_POINT;
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(data.id(), gates, pointToPoint, 0L);
        List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        Vec3 at = path.getFirst();
        tracker.update(now, at.x, at.y, at.z);
        for (int i = 1; i < path.size(); i++) {
            Vec3 to = path.get(i);
            int steps = Math.max(1, (int) Math.ceil(at.distanceTo(to) / 0.25));
            for (int s = 1; s <= steps; s++) {
                now += 20L * MS;
                Vec3 point = at.lerp(to, s / (double) steps);
                tracker.update(now, point.x, point.y, point.z);
                tracker.tick(now);
                events.addAll(tracker.drainEvents());
            }
            at = to;
        }
        List<Integer> expected = new ArrayList<>();
        for (int i = 1; i < (pointToPoint ? gates.size() - 1 : gates.size()); i++) {
            expected.add(i);
        }
        boolean clean = of(events, LapTracker.EventType.MISSED).isEmpty() && of(events, LapTracker.EventType.WRONG_WAY).isEmpty()
                && of(events, LapTracker.EventType.WRONG_SIDE).isEmpty() && of(events, LapTracker.EventType.ABORT).isEmpty();
        if (of(events, LapTracker.EventType.START).size() != 1 || !of(events, LapTracker.EventType.GATE).equals(expected)
                || of(events, LapTracker.EventType.LAP).size() != 1 || of(events, LapTracker.EventType.FINISH).size() != (pointToPoint ? 1 : 0) || !clean) {
            return "Client zählt " + events;
        }
        return null;
    }

    private static double[] window(List<Vec3> path, GateGeometry start, GateGeometry finish, boolean pointToPoint) {
        double travelled = 0.0;
        double first = -1.0;
        double last = -1.0;
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1);
            Vec3 b = path.get(i);
            double length = a.distanceTo(b);
            double t = start.crossing(a.x, a.y, a.z, b.x, b.y, b.z, true);
            if (t >= 0.0 && first < 0.0) {
                first = travelled + length * t;
            }
            double f = finish.crossing(a.x, a.y, a.z, b.x, b.y, b.z, true);
            if (f >= 0.0 && first >= 0.0 && (pointToPoint || travelled + length * f > first + 1.0E-6)) {
                last = travelled + length * f;
            }
            travelled += length;
        }
        return new double[]{first, last};
    }

    private static @Nullable String serverLap(MinecraftServer server, Placement placement, List<Vec3> path) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.worldCourse(CourseRegistry.dimensionOf(level), placement.key());
        if (course == null) {
            return "Kurs fehlt am Server";
        }
        boolean pointToPoint = course.pointToPoint();
        if (!pointToPoint) {
            registry.setLaps(course, 1);
        }
        List<GateGeometry> gates = course.toData().geometry();
        double[] window = window(path, gates.getFirst(), pointToPoint ? gates.getLast() : gates.getFirst(), pointToPoint);
        if (window[0] < 0.0 || window[1] <= window[0]) {
            return "Fluglinie kreuzt Start und Ziel nicht";
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        Vec3 first = path.getFirst();
        drone.snapTo(first.x, first.y, first.z, 0.0F, 0.0F);
        drone.setNoGravity(true);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        try {
            long run = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 12_000L * MS);
            double perBlock = run / (window[1] - window[0]);
            long t0 = RaceRulesJoinCheck.nextStart(run);
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L),
                    player, t0 - 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            double travelled = 0.0;
            long t = t0;
            for (int i = 1; i < path.size(); i++) {
                Vec3 a = path.get(i - 1);
                Vec3 b = path.get(i);
                double length = a.distanceTo(b);
                int steps = Math.max(1, (int) Math.ceil(length / 0.5));
                for (int s = 1; s <= steps; s++) {
                    Vec3 from = a.lerp(b, (s - 1) / (double) steps);
                    Vec3 next = a.lerp(b, s / (double) steps);
                    t = t0 + Math.round((travelled + length * s / steps - window[0]) * perBlock);
                    RaceServer.onDroneMoved(player, from, next, t);
                }
                travelled += length;
            }
            Vec3 end = path.getLast();
            drone.snapTo(end.x, end.y, end.z, 0.0F, 0.0F);
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, course.id, 1, run, 0L), player, t + 100L * MS);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return (pointToPoint ? "Lauf am Ziel" : "Runde") + " am Server abgelehnt: " + verdict;
            }
            if (pointToPoint) {
                verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.FINISH, course.id, 1, run, run), player,
                        t + 200L * MS);
                if (verdict != RaceServer.Verdict.ACCEPTED) {
                    return "Ziel am Server abgelehnt: " + verdict;
                }
            }
            List<Leaderboard.Entry> entries = new ArrayList<>(registry.board(course).entries());
            entries.addAll(registry.board(course).assistedEntries());
            if (entries.stream().noneMatch(entry -> entry.player().equals(player.getUUID()))) {
                return "Runde fehlt in der Bestenliste";
            }
            return null;
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    @SuppressWarnings("unchecked")
    private static StepResult flightStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            AtomicReference<List<Identifier>> ids = new AtomicReference<>();
            state.put("ids", ids);
            state.put(FUTURE, ctx.onServer(server -> ids.set(templates(server))));
            state.put("index", 0);
            state.put("phase", "next");
            return StepResult.pending();
        }
        StepResult waiting = waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        List<Identifier> ids = ((AtomicReference<List<Identifier>>) state.get("ids")).get();
        int index = (Integer) state.get("index");
        AtomicReference<Placement> placement = (AtomicReference<Placement>) state.computeIfAbsent("placement", k -> new AtomicReference<Placement>());
        AtomicReference<String> status = (AtomicReference<String>) state.computeIfAbsent("status", k -> new AtomicReference<>(PENDING));
        AtomicReference<Integer> course = (AtomicReference<Integer>) state.computeIfAbsent("course", k -> new AtomicReference<>(GateEntity.NO_COURSE));
        AtomicReference<String> result = (AtomicReference<String>) state.computeIfAbsent("result", k -> new AtomicReference<String>());
        switch ((String) state.get("phase")) {
            case "next" -> {
                if (index >= ids.size()) {
                    ctx.log(ids.size() + " Weltkurse abgeflogen, jede Runde beim Client gezählt und am Server in der Bestenliste");
                    return StepResult.pass();
                }
                Identifier id = ids.get(index);
                status.set(PENDING);
                result.set(null);
                state.put("since", ctx.tick());
                state.put(FUTURE, ctx.onServer(server -> placement.set(place(server, id, (index + 1) % 4, false))));
                state.put("phase", "adopt");
            }
            case "adopt" -> {
                String current = status.get();
                Placement pending = placement.get();
                if (current.equals(OK)) {
                    state.put("since", ctx.tick());
                    state.put("phase", "client");
                } else if (!current.equals(PENDING)) {
                    ctx.onServer(server -> cleanup(server, pending));
                    return StepResult.fail(pending.template() + ": " + current);
                } else if (ctx.tick() - (Integer) state.get("since") > 400) {
                    ctx.onServer(server -> cleanup(server, pending));
                    return StepResult.fail(pending.template() + ": nicht als Kurs übernommen");
                } else if (ctx.tick() % 10 == 0) {
                    state.put(FUTURE, ctx.onServer(server -> {
                        String checked = check(server, pending, false);
                        CourseRegistry registry = CourseRegistry.get();
                        CourseRegistry.Course found = registry == null ? null : registry.worldCourse(CourseRegistry.dimensionOf(server.overworld()),
                                pending.key());
                        course.set(found == null ? GateEntity.NO_COURSE : found.id);
                        status.set(checked);
                    }));
                }
            }
            case "client" -> {
                Placement current = placement.get();
                CourseData data = ClientCourses.get(course.get());
                if (data == null || data.gates().size() != current.expected().size()) {
                    if (ctx.tick() - (Integer) state.get("since") > 200) {
                        ctx.onServer(server -> cleanup(server, current));
                        return StepResult.fail(current.template() + ": Kurs fehlt beim Client");
                    }
                    return StepResult.pending();
                }
                List<Vec3> path = line(current);
                String problem = clientLap(data, path);
                if (problem != null) {
                    ctx.onServer(server -> cleanup(server, current));
                    return StepResult.fail(current.template() + ": " + problem);
                }
                state.put(FUTURE, ctx.onServer(server -> result.set(serverLap(server, current, path))));
                state.put("phase", "server");
            }
            case "server" -> {
                Placement current = placement.get();
                String problem = result.get();
                state.put(FUTURE, ctx.onServer(server -> cleanup(server, current)));
                if (problem != null) {
                    return StepResult.fail(current.template() + ": " + problem);
                }
                ctx.log(current.template() + " (" + current.turns() + " Vierteldrehungen, " + current.expected().size() + " Durchgänge, "
                        + current.mode().key() + ") sauber abgeflogen");
                state.put("index", index + 1);
                state.put("phase", "next");
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + state.get("phase"));
            }
        }
        return StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult twinStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        AtomicReference<Placement> one = (AtomicReference<Placement>) state.computeIfAbsent("one", k -> new AtomicReference<Placement>());
        AtomicReference<Placement> two = (AtomicReference<Placement>) state.computeIfAbsent("two", k -> new AtomicReference<Placement>());
        AtomicReference<String> status = (AtomicReference<String>) state.computeIfAbsent("status", k -> new AtomicReference<>(PENDING));
        if (ctx.tick() == 0) {
            state.put(FUTURE, ctx.onServer(server -> {
                one.set(place(server, TWIN, 0, false));
                two.set(place(server, TWIN, 1, true));
            }));
            return StepResult.pending();
        }
        StepResult waiting = waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        String current = status.get();
        if (current.startsWith(OK)) {
            state.put(FUTURE, ctx.onServer(server -> {
                cleanup(server, one.get());
                cleanup(server, two.get());
            }));
            ctx.log(current.substring(OK.length()).trim());
            return StepResult.pass();
        }
        if (!current.equals(PENDING)) {
            Placement a = one.get();
            Placement b = two.get();
            ctx.onServer(server -> {
                cleanup(server, a);
                cleanup(server, b);
            });
            return StepResult.fail(current);
        }
        if (ctx.tick() > 900) {
            return StepResult.fail("die beiden Arenen wurden nicht als Kurse übernommen");
        }
        if (ctx.tick() % 10 == 0) {
            Placement a = one.get();
            Placement b = two.get();
            state.put(FUTURE, ctx.onServer(server -> status.set(twins(server, a, b))));
        }
        return StepResult.pending();
    }

    private static String twins(MinecraftServer server, Placement a, Placement b) {
        String first = check(server, a, false);
        String second = check(server, b, false);
        if (!first.equals(OK) || !second.equals(OK)) {
            return first.equals(PENDING) || second.equals(PENDING) ? PENDING : "erste Arena: " + first + ", zweite Arena: " + second;
        }
        ServerLevel level = server.overworld();
        CourseRegistry registry = CourseRegistry.get();
        String dimension = CourseRegistry.dimensionOf(level);
        CourseRegistry.Course one = registry.worldCourse(dimension, a.key());
        CourseRegistry.Course two = registry.worldCourse(dimension, b.key());
        if (one == null || two == null || one == two || one.id == two.id) {
            return "beide Fundorte teilen sich einen Kurs: " + (one == null ? "-" : one.id) + " / " + (two == null ? "-" : two.id);
        }
        if (one.key().equals(two.key())) {
            return "beide Fundorte teilen sich die Kurs-ID " + one.key();
        }
        Set<String> gatesOne = new LinkedHashSet<>();
        for (CourseRegistry.GateRecord record : one.gates) {
            gatesOne.add(record.uuid);
        }
        for (CourseRegistry.GateRecord record : two.gates) {
            if (gatesOne.contains(record.uuid)) {
                return "ein Gate steht in beiden Kursen: " + record.uuid;
            }
        }
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        if (registry.submitLap(one, player, "race_5", TEST_LAP) != Leaderboard.Outcome.COURSE_RECORD) {
            return "Testrunde in der ersten Arena kein Kursrekord";
        }
        if (registry.board(one).entries().isEmpty() || !registry.board(two).entries().isEmpty() || !registry.board(two).assistedEntries().isEmpty()) {
            return "Bestenlisten nicht getrennt: " + registry.board(one).entries() + " / " + registry.board(two).entries();
        }
        if (!(level.getBlockEntity(a.board()) instanceof SignBlockEntity signOne) || !(level.getBlockEntity(b.board()) instanceof SignBlockEntity signTwo)) {
            return "Tafel fehlt";
        }
        List<Component> linesOne = signOne.getText(SignTextSlot.FRONT).getMessages(false);
        List<Component> linesTwo = signTwo.getText(SignTextSlot.FRONT).getMessages(false);
        if (!courseNumber(linesOne.get(1), one.id) || !courseNumber(linesTwo.get(1), two.id) || !translates(linesOne.get(2), WorldCourseBoard.RECORD_KEY)
                || !translates(linesTwo.get(2), WorldCourseBoard.NO_RECORD_KEY)) {
            return "Tafeln zeigen " + linesOne + " / " + linesTwo;
        }
        return OK + " zwei Arenen: Kurs #" + one.id + " (ID " + one.key() + ", Rekord) und Kurs #" + two.id + " (ID " + two.key()
                + ", noch kein Rekord), kein gemeinsames Gate";
    }

    private static boolean courseNumber(Component component, int id) {
        return component.getContents() instanceof TranslatableContents contents && contents.getKey().equals(WorldCourseBoard.COURSE_KEY)
                && contents.getArgs().length == 1 && String.valueOf(contents.getArgs()[0]).equals(String.valueOf(id));
    }

    private static void aim(LocalPlayer player, Vec3 point) {
        Vec3 eye = player.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.yBodyRotO = yaw;
        player.yHeadRotO = yaw;
    }

    private static final class CourseView implements Screenshots.Scene {
        private final Identifier template;
        private final AtomicReference<Placement> placement = new AtomicReference<>();
        private final AtomicReference<String> status = new AtomicReference<>(PENDING);

        private CourseView(Identifier template) {
            this.template = template;
        }

        private static Vec3 eye(Placement placement) {
            BoundingBox box = placement.box();
            return new Vec3((box.minX() + box.maxX() + 1) * 0.5, box.minY() + 22.0, box.minZ() - 12.0);
        }

        private static Vec3 target(Placement placement) {
            BoundingBox box = placement.box();
            return new Vec3((box.minX() + box.maxX() + 1) * 0.5, box.minY() + 1.0, (box.minZ() + box.maxZ() + 1) * 0.55);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey("view")) {
                state.put("view", "place");
                state.put(FUTURE, ctx.onServer(server -> this.placement.set(place(server, this.template, 0, false))));
                return StepResult.pending();
            }
            StepResult waiting = waitFor(ctx);
            if (waiting != null) {
                return waiting;
            }
            Placement current = this.placement.get();
            if (!OK.equals(this.status.get())) {
                if (!PENDING.equals(this.status.get())) {
                    return StepResult.fail(this.template + ": " + this.status.get());
                }
                if (ctx.tick() > 600) {
                    return StepResult.fail(this.template + ": nicht als Kurs übernommen");
                }
                if (ctx.tick() % 10 == 0) {
                    state.put(FUTURE, ctx.onServer(server -> this.status.set(check(server, current, false))));
                }
                return StepResult.pending();
            }
            Vec3 at = eye(current);
            if ("place".equals(state.get("view"))) {
                state.put("view", "move");
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), at.x, at.y, at.z, Set.of(), 0.0F, 0.0F, true);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                }));
                return StepResult.pending();
            }
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.position().distanceTo(at) > 0.6) {
                return ctx.tick() > 900 ? StepResult.fail("Spieler nicht am Blickpunkt") : StepResult.pending();
            }
            this.hold(ctx);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Placement current = this.placement.get();
            LocalPlayer player = Minecraft.getInstance().player;
            if (current != null && player != null) {
                aim(player, target(current));
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Placement current = this.placement.get();
            ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                cleanup(server, current);
            });
        }
    }

    private static StepResult lootStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            AtomicReference<String> summary = new AtomicReference<>();
            ctx.state().put("summary", summary);
            ctx.state().put(FUTURE, ctx.onServer(server -> summary.set(rollLoot(server))));
            return StepResult.pending();
        }
        StepResult waiting = waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        @SuppressWarnings("unchecked")
        String summary = ((AtomicReference<String>) ctx.state().get("summary")).get();
        ctx.log(summary);
        return StepResult.pass();
    }

    private static String rollLoot(MinecraftServer server) {
        ServerLevel level = server.overworld();
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        List<String> problems = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        for (String table : List.of("common", "arena")) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Constants.id("chests/world_course/" + table));
            LootTable loot = server.reloadableRegistries().getLootTable(key);
            if (loot == LootTable.EMPTY) {
                problems.add("Beutetabelle fehlt: " + key.identifier());
                continue;
            }
            int propwashStacks = 0;
            int better = 0;
            for (int seed = 1; seed <= LOOT_ROLLS; seed++) {
                boolean betterHere = false;
                for (ItemStack stack : loot.getRandomItems(params, seed)) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (id.startsWith(Constants.MOD_ID + ":")) {
                        propwashStacks++;
                        betterHere |= BETTER_PARTS.contains(id);
                        if (Batteries.isBattery(stack) && Math.abs(Batteries.charge(stack) - ChestLoot.FOUND_CHARGE) > 0.01F) {
                            problems.add(table + ": Akku mit Ladung " + Batteries.charge(stack));
                        }
                    } else if (!VANILLA_LOOT.contains(id)) {
                        problems.add(table + ": unerwartete Beute " + id);
                    }
                }
                if (betterHere) {
                    better++;
                }
            }
            double perChest = propwashStacks / (double) LOOT_ROLLS;
            double betterRate = better / (double) LOOT_ROLLS;
            if (perChest < 1.2 || perChest > 6.0) {
                problems.add(table + ": " + perChest + " Propwash-Stapel je Truhe");
            }
            if (betterRate < 0.04 || betterRate > 0.3) {
                problems.add(table + ": bessere Teile in " + betterRate + " der Truhen");
            }
            summary.append(table).append(String.format(Locale.ROOT, ": %.2f Teile-Stapel je Truhe, %.1f %% mit besserem Teil; ",
                    perChest, betterRate * 100.0));
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", new LinkedHashSet<>(problems)));
        }
        return summary.toString().trim();
    }
}
