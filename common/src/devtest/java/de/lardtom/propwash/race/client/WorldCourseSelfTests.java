package de.lardtom.propwash.race.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.ChestLoot;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.world.WorldCourseBoard;
import de.lardtom.propwash.race.world.WorldCoursePiece;
import de.lardtom.propwash.race.world.WorldCourseSite;
import de.lardtom.propwash.race.world.WorldCourseStructure;
import de.lardtom.propwash.race.world.WorldCourseVariant;
import de.lardtom.propwash.race.world.WorldCourses;
import de.lardtom.propwash.server.ServerRulesConfig;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private record Placement(Identifier template, WorldCourseVariant variant, BlockPos origin, int turns, BoundingBox box,
                             List<Vec3> expected, BlockPos board) {
    }

    private WorldCourseSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race: Weltkurse registriert (Strukturen, Tag, Vorlagen)", 100, WorldCourseSelfTests::registryStep);
        SelfTest.clientStep("race: Weltkurse im Testgelände gefunden wie mit /locate", 1200, WorldCourseSelfTests::locateStep);
        SelfTest.clientStep("race: Weltkurs-Vorlagen mit Gates in Kursreihenfolge", 3000, WorldCourseSelfTests::templateStep);
        SelfTest.clientStep("race: Weltkurs-Truhe würfelt bescheidene Beute", 200, WorldCourseSelfTests::lootStep);
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
            state.put(FUTURE, ctx.onServer(server -> ids.set(templates(server))));
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
                    ctx.log(ids.size() + " Vorlagen platziert, alle Gates in Kursreihenfolge");
                    return StepResult.pass();
                }
                Identifier id = ids.get(index);
                status.set(PENDING);
                state.put("since", ctx.tick());
                state.put(FUTURE, ctx.onServer(server -> placement.set(place(server, id, index % 4))));
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

    private static Placement place(MinecraftServer server, Identifier id, int turns) {
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
        int cornerX = player.getBlockX() + OFFSET;
        int cornerZ = player.getBlockZ() + OFFSET;
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, cornerX, cornerZ) - 1;
        BlockPos origin = new BlockPos(cornerX - local.minX(), ground, cornerZ - local.minZ());
        BoundingBox box = template.getBoundingBox(settings, origin);
        for (GateEntity old : level.getEntitiesOfClass(GateEntity.class, AABB.of(box).inflate(2.0))) {
            old.discard();
        }
        CompoundTag nbt = readTemplate(server, id);
        ListTag entities = nbt.getListOrEmpty("entities");
        Vec3[] expected = new Vec3[entities.size()];
        WorldCourseSite site = new WorldCourseSite(origin.getX(), origin.getY(), origin.getZ(), turns);
        BlockPos board = null;
        WorldCourseVariant variant = null;
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag marker = entities.getCompoundOrEmpty(i).getCompoundOrEmpty("nbt").getCompoundOrEmpty("WorldCourse");
            ListTag localPos = marker.getListOrEmpty("Local");
            double[] world = site.entityToWorld(localPos.getDoubleOr(0, 0), localPos.getDoubleOr(1, 0), localPos.getDoubleOr(2, 0));
            expected[marker.getIntOr("Slot", i)] = new Vec3(world[0], world[1], world[2]);
            int[] boardLocal = marker.getIntArray("Board").orElseThrow();
            int[] boardWorld = site.blockToWorld(boardLocal[0], boardLocal[1], boardLocal[2]);
            board = new BlockPos(boardWorld[0], boardWorld[1], boardWorld[2]);
            variant = WorldCourseVariant.byKey(marker.getStringOr("Variant", "")).orElseThrow();
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
        return new Placement(id, variant, origin, turns, box, List.of(expected), board);
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
        String site = placement.origin().getX() + "," + placement.origin().getY() + "," + placement.origin().getZ();
        CourseRegistry.Course course = registry.worldCourse(CourseRegistry.dimensionOf(level), site);
        if (course == null || course.gates.size() < placement.expected().size()) {
            return PENDING;
        }
        if (course.gates.size() != placement.expected().size()) {
            return "Kurs hat " + course.gates.size() + " statt " + placement.expected().size() + " Gates";
        }
        if (!course.worldCourse.equals(placement.variant().key()) || !course.owner.isEmpty()) {
            return "Kurs falsch angelegt: " + course.worldCourse + " Besitzer " + course.owner;
        }
        List<GateEntity> gates = new ArrayList<>();
        for (int i = 0; i < course.gates.size(); i++) {
            CourseRegistry.GateRecord record = course.gates.get(i);
            Vec3 expected = placement.expected().get(i);
            if (record.slot != i || record.index != i || Math.abs(record.x - expected.x) > 1.0E-6 || Math.abs(record.y - expected.y) > 1.0E-6
                    || Math.abs(record.z - expected.z) > 1.0E-6) {
                return "Gate " + i + " falsch eingereiht: slot " + record.slot + " bei " + record.x + "," + record.y + "," + record.z + " statt " + expected;
            }
            Entity entity = level.getEntity(UUID.fromString(record.uuid));
            if (!(entity instanceof GateEntity gate) || gate.courseId() != course.id || gate.gateIndex() != i || gate.worldCourse() != null) {
                return "Gate " + i + " nicht nummeriert: " + entity;
            }
            String ground = groundProblem(level, gate);
            if (ground != null) {
                return "Gate " + i + " " + ground + " (Ursprung " + placement.origin() + ", Gate " + gate.position() + ", Grund "
                        + level.getBlockState(placement.origin()) + ", " + PLACE_DEBUG.get() + ")";
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
        GateStacking.Base base = GateStacking.Base.of(gate.gateBelow() != null, gate.gateAbove() != null);
        List<AABB> boxes = new ArrayList<>();
        geometry.boxes((x0, y0, z0, x1, y1, z1) -> boxes.add(new AABB(x0, y0, z0, x1, y1, z1)), base);
        for (int i = 0; i < boxes.size(); i++) {
            AABB box = boxes.get(i).deflate(1.0E-3);
            if (!level.noBlockCollision(null, box)) {
                return "steckt in Blöcken bei " + box;
            }
            if (i < 2 && base != GateStacking.Base.STACKED) {
                BlockPos below = BlockPos.containing(box.getCenter().x, geometry.y() - 0.5, box.getCenter().z);
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    return "schwebt über " + level.getBlockState(below) + " bei " + below;
                }
            }
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
            String site = placement.origin().getX() + "," + placement.origin().getY() + "," + placement.origin().getZ();
            CourseRegistry.Course course = registry.worldCourse(CourseRegistry.dimensionOf(level), site);
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
