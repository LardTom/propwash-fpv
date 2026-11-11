package de.lardtom.propwash.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.content.DronePresets;
import de.lardtom.propwash.content.paint.PaintCodecs;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.WearCodecs;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.bind.BindCodecs;
import de.lardtom.propwash.drone.coating.CoatingComponents;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RecordGhosts;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.share.Pw3Params;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareConvert;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.video.VtxSettings;
import de.lardtom.propwash.video.VtxTables;
import de.lardtom.propwash.video.vtx.VtxPowerSettings;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class MigrationValues {
    public static final int FORMAT = 1;
    public static final String JMP = "justmoreparts";
    public static final String FREECAM_RULE = "allow_freecam";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls()
            .serializeSpecialFloatingPointValues().create();
    private static final double TOLERANCE = 1.0E-4;
    private static final double POSITION_TOLERANCE = 0.05;
    private static final Pattern IGNORED = Pattern.compile("^(stand|created|loader|minecraft|mods(\\..*)?|files(\\..*)?|verifyNotes(\\..*)?"
            + "|disk(\\..*)?|registeredAddonPresets(\\..*)?|clientConfigs(\\..*)?|world\\.(seed|preset|gameTime|home)(\\..*)?"
            + "|worldRules\\.(defaults|changed)(\\..*)?|(drones|gates)\\[\\]\\.(snbt|role|geometry)|.*\\.shareCodes(\\..*)?|.*\\.role)$");
    private static final String EMPTY_CREATIVE = ", creative=" + CreativeSpec.EMPTY;
    private static final Pattern POSITION = Pattern.compile(".*(\\.pos\\.[xyz]|\\.yRot|\\.Orientation\\[\\]|owner\\.pos\\.[xyz])$");
    private static final Set<String> DRONE_KEYS = Set.of("Build", "Tune", "Battery", "HasBattery", "Paint", "Wear", "CameraCharge",
            "CustomName", "HomePoint", "Waterproof", "ReceiverBind", "AccessoryContents", "UUID", "Tags", "Orientation");
    private static final Set<String> GATE_KEYS = Set.of("Size", "Kind", "Variant", "Region", "GateIndex", "Course", "WorldCourse", "Mount", "Flipped",
            "Tilt", "OpeningWidth", "OpeningHeight", "RedstoneSignal", "PulseTicks", "SignalRaceOnly", "UUID", "Pos", "Rotation");
    public static final List<String> PLAYER_SECTIONS = List.of("owner", "inventory");

    public record Fixture(Path world, Path expected, @Nullable Path configs, String label, boolean generated) {
    }

    public record Stack(int course, List<Integer> legs) {
    }

    private MigrationValues() {
    }

    public static @Nullable Fixture fixture(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Path path = Path.of(value.trim()).toAbsolutePath().normalize();
        if (Files.isDirectory(path) && Files.isRegularFile(path.resolve("world.zip"))) {
            return new Fixture(path.resolve("world.zip"), path.resolve("expected.json"), configsIn(path), path.getFileName().toString(), false);
        }
        Path parent = path.getParent();
        String file = path.getFileName().toString();
        String stem = file.endsWith(".zip") ? file.substring(0, file.length() - 4) : file;
        List<Path> candidates = new ArrayList<>();
        if (parent != null) {
            candidates.add(parent.resolve(stem + ".json"));
            candidates.add(parent.resolve(stem + ".expected.json"));
            candidates.add(parent.resolve("expected.json"));
        }
        candidates.add(path.resolve("expected.json"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return new Fixture(path, candidate, parent == null ? null : configsIn(parent), stem, false);
            }
        }
        return new Fixture(path, candidates.getFirst(), null, stem, false);
    }

    private static @Nullable Path configsIn(Path dir) {
        Path configs = dir.resolve("config").resolve(Constants.MOD_ID);
        return Files.isDirectory(configs) ? configs : null;
    }

    public static int install(Fixture fixture, Path target) throws IOException {
        deleteTree(target);
        int files = Files.isDirectory(fixture.world()) ? copyTree(fixture.world(), target) : unzip(fixture.world(), target);
        if (!Files.isRegularFile(target.resolve("level.dat"))) {
            throw new IOException("Welt-Kopie ohne level.dat: " + target);
        }
        return files;
    }

    public static JsonObject read(Path expected) throws IOException {
        return parse(Files.readString(expected, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
    }

    public static JsonObject without(JsonObject expected, List<String> sections, boolean folder) {
        JsonObject copy = expected.deepCopy();
        sections.forEach(copy::remove);
        if (folder && copy.has("world") && copy.get("world").isJsonObject()) {
            copy.getAsJsonObject("world").remove("folder");
        }
        return copy;
    }

    public static JsonObject only(JsonObject expected, List<String> sections) {
        JsonObject copy = new JsonObject();
        for (String section : sections) {
            if (expected.has(section)) {
                copy.add(section, expected.get(section).deepCopy());
            }
        }
        return copy;
    }

    public static List<Stack> stacks(JsonObject expected) {
        Map<Integer, List<Integer>> legs = new LinkedHashMap<>();
        JsonElement gates = expected.get("gates");
        if (gates == null || !gates.isJsonArray()) {
            return List.of();
        }
        for (JsonElement element : gates.getAsJsonArray()) {
            JsonObject gate = element.getAsJsonObject();
            JsonElement stack = gate.get("stack");
            if (stack == null || !stack.isJsonArray() || stack.getAsJsonArray().size() < 2 || !gate.has("course") || !gate.has("gateIndex")) {
                continue;
            }
            int course = gate.get("course").getAsInt();
            int index = gate.get("gateIndex").getAsInt();
            if (course <= 0 || index < 0 || !stack.getAsJsonArray().get(0).getAsString().equals(gate.get("uuid").getAsString())) {
                continue;
            }
            legs.computeIfAbsent(course, k -> new ArrayList<>()).add(index);
        }
        List<Stack> out = new ArrayList<>();
        legs.forEach((course, indices) -> out.add(new Stack(course, indices.stream().sorted().toList())));
        return out;
    }

    public static double low(GateGeometry gate) {
        return gate.y() + gate.frame() * 0.5;
    }

    public static Vec3 point(GateGeometry gate, double ahead, double side, double y) {
        return new Vec3(gate.x() + gate.normalX() * ahead + gate.sideX() * side, y, gate.z() + gate.normalZ() * ahead + gate.sideZ() * side);
    }

    public static double height(GateGeometry gate, int index, List<Integer> legs) {
        return legs.contains(index) ? low(gate) : gate.centerY();
    }

    public static @Nullable String legsOnServer(ServerLevel level, CourseRegistry.Course course, Stack stack) {
        for (int index : stack.legs()) {
            GateEntity gate = index < course.gates.size() && level.getEntity(UUID.fromString(course.gates.get(index).uuid)) instanceof GateEntity found
                    ? found : null;
            if (gate == null) {
                return "Gate " + index + " von Kurs " + course.id + " nicht geladen";
            }
            if (gate.base() != GateStacking.Base.LEGS || !gate.geometry().legs() || gate.geometry().openingFloor() != 0.0) {
                return "unterstes Gate " + index + " im Stapel ohne Beine: " + gate.base() + ", Öffnung ab " + gate.geometry().openingFloor();
            }
        }
        for (GateGeometry gate : course.toData().geometry()) {
            if (gate.legs()) {
                return "Kursdaten tragen das Flag legs";
            }
        }
        return null;
    }

    public static String legsLap(ServerPlayer player, Stack stack) {
        ServerLevel level = player.level();
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(stack.course());
        if (course == null) {
            return "Kurs " + stack.course() + " fehlt auf dem Server";
        }
        String legs = legsOnServer(level, course, stack);
        if (legs != null) {
            return legs;
        }
        List<GateGeometry> gates = course.toData().geometry();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        GateGeometry first = gates.getFirst();
        Vec3 start = point(first, -3.0, 0.0, low(first));
        drone.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        drone.setPilot(player);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        Leaderboard board = registry.board(course);
        int lapsBefore = board.lapsOf(player.getUUID());
        long assistedBefore = latestAssisted(board, player.getUUID());
        try {
            long lap = Math.max(RaceServer.minimumLapNanos(course, player) * 3L, 8_000_000_000L);
            long t = System.nanoTime() + 900_000_000_000L;
            RaceServer.Verdict verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.START, course.id, 0, 0L, 0L), player, t);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Start abgelehnt: " + verdict;
            }
            long begin = t + 100_000_000L;
            for (int i = 0; i <= gates.size(); i++) {
                int index = i % gates.size();
                GateGeometry gate = gates.get(index);
                double y = height(gate, index, stack.legs());
                RaceServer.onDroneMoved(player, point(gate, -1.0, 0.0, y), point(gate, 1.0, 0.0, y), begin + lap * i / gates.size());
            }
            verdict = RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, course.id, 1, lap, 0L), player,
                    begin + lap + 100_000_000L);
            RaceServer.handle(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.ABORT, course.id, 0, 0L, 0L), player, begin + lap + 200_000_000L);
            if (verdict != RaceServer.Verdict.ACCEPTED) {
                return "Runde unten durch das Gate auf Beinen vom Server abgelehnt: " + verdict;
            }
            int laps = registry.board(course).lapsOf(player.getUUID());
            String assist = assistOf(registry.board(course), player.getUUID(), assistedBefore);
            AssistReasons reasons = RaceServer.serverReasons(drone).only(LapAssist.SERVER_REASONS);
            String counted = lapCounted(lapsBefore, laps, assist, reasons.any(), WorldRules.on(level.getServer(), WorldRuleSpecs.SAVE_ASSISTED_LAPS));
            if (counted == null) {
                return "Runde angenommen, aber nicht gezählt: Runden " + lapsBefore + " → " + laps + (reasons.any() ? ", Flughilfe " + reasons.list() : "");
            }
            return String.format(Locale.ROOT, "ok: Server zählt die Runde (%.1f s) auf Kurs %d, Runden von %s %d → %d%s", lap / 1.0E9, course.id,
                    player.getGameProfile().name(), lapsBefore, laps, counted.isEmpty() || assist != null ? counted : counted + " " + reasons.list());
        } finally {
            drone.setPilot(null);
            drone.discard();
        }
    }

    public static @Nullable String lapCounted(int lapsBefore, int lapsAfter, @Nullable String assistEntry, boolean assisted, boolean saveAssisted) {
        if (assistEntry != null) {
            return ", als Runde mit Flughilfe " + assistEntry;
        }
        if (lapsAfter == lapsBefore + 1) {
            return "";
        }
        if (assisted && !saveAssisted) {
            return ", als Runde mit Flughilfe angenommen, nicht gespeichert (Welt-Regel save_assisted_laps aus)";
        }
        return null;
    }

    public static long latestAssisted(Leaderboard board, UUID player) {
        long latest = 0L;
        for (Leaderboard.Entry entry : board.assistedEntries()) {
            if (entry.player().equals(player)) {
                latest = Math.max(latest, entry.dateMillis());
            }
        }
        return latest;
    }

    public static @Nullable String assistOf(Leaderboard board, UUID player, long before) {
        for (Leaderboard.Entry entry : board.assistedEntries()) {
            if (entry.player().equals(player) && entry.dateMillis() > before) {
                return entry.reasons().list().stream().map(Enum::name).toList().toString().replace(" ", "");
            }
        }
        return null;
    }

    public static int zip(Path world, Path zip) throws IOException {
        int count = 0;
        Files.createDirectories(zip.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream stream = new ZipOutputStream(out);
             Stream<Path> files = Files.walk(world)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = world.relativize(file).toString().replace('\\', '/');
                if (name.equals("session.lock")) {
                    continue;
                }
                stream.putNextEntry(new ZipEntry(world.getFileName() + "/" + name));
                Files.copy(file, stream);
                stream.closeEntry();
                count++;
            }
        }
        return count;
    }

    public static int unzip(Path zip, Path folder) throws IOException {
        int count = 0;
        Path target = folder.toAbsolutePath().normalize();
        Files.createDirectories(target);
        try (InputStream in = Files.newInputStream(zip); ZipInputStream stream = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                int slash = name.indexOf('/');
                String inner = slash >= 0 ? name.substring(slash + 1) : name;
                if (inner.isEmpty() || entry.isDirectory() || inner.equals("session.lock")) {
                    continue;
                }
                Path out = target.resolve(inner).normalize();
                if (!out.startsWith(target)) {
                    throw new IOException("Eintrag außerhalb der Welt: " + name);
                }
                Files.createDirectories(out.getParent());
                Files.copy(stream, out, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    public static int copyTree(Path from, Path to) throws IOException {
        int count = 0;
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path out = to.resolve(from.relativize(file).toString());
                if (out.getFileName().toString().equals("session.lock")) {
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(file, out, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    public static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    public static boolean jmpLoaded() {
        return Services.PLATFORM.modVersion(JMP).isPresent();
    }

    public static void force(MinecraftServer server, JsonObject expected, boolean on) {
        ServerLevel level = server.overworld();
        Set<Long> chunks = new TreeSet<>();
        for (String section : List.of("drones", "gates")) {
            JsonElement list = expected.get(section);
            if (list == null || !list.isJsonArray()) {
                continue;
            }
            for (JsonElement element : list.getAsJsonArray()) {
                JsonObject pos = element.getAsJsonObject().getAsJsonObject("pos");
                if (pos != null) {
                    int cx = (int) Math.floor(pos.get("x").getAsDouble()) >> 4;
                    int cz = (int) Math.floor(pos.get("z").getAsDouble()) >> 4;
                    chunks.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
                }
            }
        }
        for (long chunk : chunks) {
            level.setChunkForced((int) (chunk >> 32), (int) chunk, on);
        }
    }

    public static String missingEntities(MinecraftServer server, JsonObject expected) {
        ServerLevel level = server.overworld();
        List<String> missing = new ArrayList<>();
        for (String section : List.of("drones", "gates")) {
            JsonElement list = expected.get(section);
            if (list == null || !list.isJsonArray()) {
                continue;
            }
            for (JsonElement element : list.getAsJsonArray()) {
                String uuid = element.getAsJsonObject().get("uuid").getAsString();
                if (level.getEntity(UUID.fromString(uuid)) == null) {
                    missing.add(uuid);
                }
            }
        }
        return String.join(",", missing);
    }

    public static JsonObject capture(MinecraftServer server, @Nullable JsonObject expected) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return capture(server, expected, players.isEmpty() ? null : players.getFirst());
    }

    public static JsonObject capture(MinecraftServer server, @Nullable JsonObject expected, @Nullable ServerPlayer player) {
        ServerLevel level = server.overworld();
        DynamicOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        DynamicOps<Tag> nbtOps = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("stand", Services.PLATFORM.getModVersion());
        JsonObject world = new JsonObject();
        world.addProperty("folder", server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString());
        world.addProperty("levelName", server.getWorldData().getLevelName());
        root.add("world", world);
        if (player != null) {
            JsonObject owner = new JsonObject();
            owner.addProperty("uuid", player.getUUID().toString());
            owner.addProperty("name", player.getGameProfile().name());
            owner.add("pos", vec(player.getX(), player.getY(), player.getZ()));
            root.add("owner", owner);
        }
        JsonObject values = new JsonObject();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            values.addProperty(spec.name(), WorldRules.get(server, spec));
        }
        JsonObject rules = new JsonObject();
        rules.add("values", values);
        root.add("worldRules", rules);
        List<UUID> droneIds = new ArrayList<>();
        List<UUID> gateIds = new ArrayList<>();
        if (expected != null) {
            ids(expected, "drones", droneIds);
            ids(expected, "gates", gateIds);
        } else {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof QuadcopterEntity) {
                    droneIds.add(entity.getUUID());
                } else if (entity instanceof GateEntity) {
                    gateIds.add(entity.getUUID());
                }
            }
            droneIds.sort(Comparator.naturalOrder());
            gateIds.sort(Comparator.naturalOrder());
        }
        JsonArray drones = new JsonArray();
        for (UUID id : droneIds) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                drones.add(drone(server, ops, drone));
            }
        }
        root.add("drones", drones);
        JsonArray gates = new JsonArray();
        for (UUID id : gateIds) {
            if (level.getEntity(id) instanceof GateEntity gate) {
                gates.add(gate(server, ops, gate));
            }
        }
        root.add("gates", gates);
        CourseRegistry registry = CourseRegistry.get();
        JsonArray courses = new JsonArray();
        if (registry != null) {
            Set<String> frames = new TreeSet<>();
            if (expected != null && expected.has("courses")) {
                for (JsonElement course : expected.getAsJsonArray("courses")) {
                    JsonElement ghosts = course.getAsJsonObject().get("recordGhosts");
                    if (ghosts != null && ghosts.isJsonArray()) {
                        for (JsonElement ghost : ghosts.getAsJsonArray()) {
                            frames.add(ghost.getAsJsonObject().get("frame").getAsString());
                        }
                    }
                }
            }
            for (CourseRegistry.Course course : registry.courses()) {
                courses.add(course(ops, registry, course, frames));
            }
            JsonObject all = new JsonObject();
            all.addProperty("count", registry.courses().size());
            all.addProperty("nextCourseId", registry.nextCourseId());
            root.add("courseRegistry", all);
        }
        root.add("courses", courses);
        JsonArray zones = new JsonArray();
        for (Zone zone : ZoneData.get(server).all()) {
            zones.add(Zone.CODEC.encodeStart(ops, zone).getOrThrow());
        }
        JsonObject zoneData = new JsonObject();
        zoneData.add("zones", zones);
        zoneData.addProperty("nextId", ZoneData.get(server).nextId());
        root.add("zones", zoneData);
        if (player != null) {
            JsonObject inventory = new JsonObject();
            JsonArray slots = new JsonArray();
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    JsonObject entry = item(nbtOps, stack);
                    entry.addProperty("slot", i);
                    slots.add(entry);
                }
            }
            inventory.add("slots", slots);
            JsonObject equipment = new JsonObject();
            ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!head.isEmpty()) {
                equipment.add("head", item(nbtOps, head));
            }
            inventory.add("equipment", equipment);
            JsonArray ender = new JsonArray();
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
                ItemStack stack = player.getEnderChestInventory().getItem(i);
                if (!stack.isEmpty()) {
                    JsonObject entry = item(nbtOps, stack);
                    entry.addProperty("slot", i);
                    ender.add(entry);
                }
            }
            inventory.add("enderChest", ender);
            root.add("inventory", inventory);
        }
        if (expected != null && expected.has("chest")) {
            JsonObject pos = expected.getAsJsonObject("chest").getAsJsonObject("pos");
            BlockPos chestPos = BlockPos.containing(pos.get("x").getAsDouble(), pos.get("y").getAsDouble(), pos.get("z").getAsDouble());
            JsonObject chest = new JsonObject();
            chest.add("pos", vec(chestPos.getX(), chestPos.getY(), chestPos.getZ()));
            JsonArray chestSlots = new JsonArray();
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity block) {
                for (int i = 0; i < block.getContainerSize(); i++) {
                    ItemStack stack = block.getItem(i);
                    if (!stack.isEmpty()) {
                        JsonObject entry = item(nbtOps, stack);
                        entry.addProperty("slot", i);
                        chestSlots.add(entry);
                    }
                }
            }
            chest.add("slots", chestSlots);
            root.add("chest", chest);
        }
        return root;
    }

    private static void ids(JsonObject expected, String section, List<UUID> out) {
        JsonElement list = expected.get(section);
        if (list != null && list.isJsonArray()) {
            for (JsonElement element : list.getAsJsonArray()) {
                out.add(UUID.fromString(element.getAsJsonObject().get("uuid").getAsString()));
            }
        }
    }

    private static <T> JsonElement json(DynamicOps<JsonElement> ops, Codec<T> codec, T value) {
        return codec.encodeStart(ops, value).getOrThrow();
    }

    private static JsonObject vec(double x, double y, double z) {
        JsonObject out = new JsonObject();
        out.addProperty("x", x);
        out.addProperty("y", y);
        out.addProperty("z", z);
        return out;
    }

    private static CompoundTag nbt(MinecraftServer server, Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(new ProblemReporter.Collector(), server.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    private static JsonObject keys(CompoundTag tag, Set<String> keys) {
        JsonObject out = new JsonObject();
        for (String key : new TreeSet<>(tag.keySet())) {
            if (keys.contains(key)) {
                out.add(key, NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag.get(key)));
            }
        }
        return out;
    }

    private static Optional<String> presetOf(DroneBuild build) {
        Optional<String> own = DronePresets.key(build);
        if (own.isPresent()) {
            return Optional.of(Constants.MOD_ID + ":" + own.get());
        }
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            Optional<DroneBuild> presetBuild = Conversions.build(preset.data().build());
            if (presetBuild.isPresent() && presetBuild.get().withoutAccessories().equals(build.withoutAccessories())) {
                return Optional.of(preset.id().toString());
            }
        }
        return Optional.empty();
    }

    private static JsonObject drone(MinecraftServer server, DynamicOps<JsonElement> ops, QuadcopterEntity drone) {
        JsonObject out = new JsonObject();
        out.addProperty("uuid", drone.getUUID().toString());
        out.add("pos", vec(drone.getX(), drone.getY(), drone.getZ()));
        out.addProperty("yRot", drone.getYRot());
        DroneBuild build = drone.build();
        presetOf(build).ifPresent(preset -> out.addProperty("preset", preset));
        out.add("build", json(ops, DroneBuildCodecs.CODEC, build));
        JsonArray parts = new JsonArray();
        for (PartInfo part : build.parts()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", part.id().toString());
            entry.addProperty("builtin", part.builtin());
            parts.add(entry);
        }
        out.add("parts", parts);
        out.addProperty("frameClass", build.frame().key());
        JsonArray accessories = new JsonArray();
        build.accessories().list().forEach(type -> accessories.add(type.key()));
        out.add("accessories", accessories);
        JsonArray problems = new JsonArray();
        build.problems().forEach(problem -> problems.add(problem.name()));
        out.add("problems", problems);
        out.addProperty("name", drone.getCustomName() == null ? "" : drone.getCustomName().getString());
        out.addProperty("paintText", drone.paint().toText());
        out.add("paint", json(ops, PaintCodecs.CODEC, drone.paint()));
        out.add("tune", json(ops, FlightTune.CODEC, drone.tune()));
        Map<String, Float> tune = drone.tune().values();
        JsonObject vtx = new JsonObject();
        vtx.addProperty("manual", VtxPowerSettings.manual(tune));
        vtx.addProperty("auto", VtxPowerSettings.auto(tune));
        vtx.addProperty("configuredMw", VtxTables.configuredMw(tune, build.video()));
        vtx.addProperty("lowPower", VtxPowerSettings.lowPower(tune).name());
        out.add("vtx", vtx);
        out.addProperty("vtxChannel", drone.tune().get(VtxSettings.CHANNEL, Float.NaN));
        out.addProperty("cameraUptiltDeg", drone.tune().get(TuneMapping.CAMERA_UPTILT, Float.NaN));
        HomePoint home = drone.homePoint();
        out.add("homePoint", home == null ? JsonNull.INSTANCE : json(ops, HomePointCodecs.CODEC, home));
        out.addProperty("waterproof", drone.waterproof());
        out.addProperty("hasBattery", drone.hasBattery());
        out.addProperty("battery", drone.batteryCharge());
        out.addProperty("cameraCharge", drone.cameraCharge());
        out.add("wear", json(ops, WearCodecs.CODEC, drone.wear()));
        out.addProperty("wearNew", drone.wear().isNew());
        out.add("receiverBind", drone.receiverBind() == null ? JsonNull.INSTANCE : json(ops, BindCodecs.CODEC, drone.receiverBind()));
        JsonArray tags = new JsonArray();
        drone.entityTags().stream().sorted().forEach(tags::add);
        out.add("tags", tags);
        CompoundTag tag = nbt(server, drone);
        out.add("propwashNbt", keys(tag, DRONE_KEYS));
        return out;
    }

    private static JsonObject gate(MinecraftServer server, DynamicOps<JsonElement> ops, GateEntity gate) {
        JsonObject out = new JsonObject();
        out.addProperty("uuid", gate.getUUID().toString());
        out.add("pos", vec(gate.getX(), gate.getY(), gate.getZ()));
        out.addProperty("facingYaw", gate.facingYaw());
        out.add("size", json(ops, GateSize.CODEC, gate.size()));
        out.addProperty("kind", gate.kind().key());
        out.addProperty("variant", gate.variant());
        out.add("mount", json(ops, GateMount.CODEC, gate.mount()));
        out.addProperty("flipped", gate.flipped());
        out.add("tilt", json(ops, GateTilt.CODEC, gate.tilt()));
        out.addProperty("customOpening", gate.customOpening());
        out.addProperty("openingWidth", gate.openingWidth());
        out.addProperty("openingHeight", gate.openingHeight());
        out.addProperty("course", gate.courseId());
        out.addProperty("gateIndex", gate.gateIndex());
        out.addProperty("base", gate.base().name());
        out.addProperty("inStack", gate.inStack());
        List<GateEntity> stack = gate.stack();
        out.addProperty("stackIndex", stack.indexOf(gate));
        out.addProperty("stackSize", stack.size());
        JsonArray column = new JsonArray();
        stack.forEach(member -> column.add(member.getUUID().toString()));
        out.add("stack", column);
        JsonObject signal = new JsonObject();
        signal.addProperty("enabled", gate.signal().enabled());
        signal.addProperty("pulseTicks", gate.signal().pulseTicks());
        signal.addProperty("raceOnly", gate.signal().raceOnly());
        out.add("signal", signal);
        out.add("propwashNbt", keys(nbt(server, gate), GATE_KEYS));
        return out;
    }

    static JsonObject leaderboard(DynamicOps<JsonElement> ops, Leaderboard board) {
        JsonObject out = new JsonObject();
        out.add("codec", json(ops, Leaderboard.CODEC, board));
        out.addProperty("size", board.size());
        out.addProperty("assistedSize", board.assistedSize());
        out.addProperty("times", board.times());
        JsonArray classes = new JsonArray();
        board.classes().forEach(classes::add);
        out.add("classes", classes);
        JsonArray top = new JsonArray();
        for (Leaderboard.Entry entry : pilotBests(board, 50)) {
            top.add(json(ops, Leaderboard.Entry.CODEC, entry));
        }
        out.add("top", top);
        return out;
    }

    private static List<Leaderboard.Entry> pilotBests(Leaderboard board, int limit) {
        List<Leaderboard.Entry> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Leaderboard.Entry entry : board.top(null, Integer.MAX_VALUE)) {
            if (result.size() >= limit) {
                break;
            }
            if (seen.add(entry.player())) {
                result.add(entry);
            }
        }
        return result;
    }

    private static JsonObject course(DynamicOps<JsonElement> ops, CourseRegistry registry, CourseRegistry.Course course, Set<String> frames) {
        JsonObject out = new JsonObject();
        out.addProperty("id", course.id);
        out.addProperty("name", course.name);
        out.addProperty("laps", course.laps);
        out.addProperty("layout", course.layout);
        out.addProperty("layoutHash", course.toData().layoutHash());
        out.addProperty("owner", course.owner);
        out.addProperty("ownerName", course.ownerName);
        out.addProperty("dimension", course.dimension);
        JsonArray gates = new JsonArray();
        course.gates.forEach(gate -> gates.add(gate.uuid));
        out.add("gates", gates);
        out.add("registryEntry", new Gson().toJsonTree(course));
        out.add("leaderboard", leaderboard(ops, registry.board(course)));
        JsonArray archive = new JsonArray();
        for (RaceRecords.Archived archived : registry.records().archive(course.id)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("layout", archived.layout());
            entry.addProperty("archivedAt", archived.archivedAt());
            entry.addProperty("gates", archived.gates());
            entry.add("board", leaderboard(ops, archived.board()));
            archive.add(entry);
        }
        out.add("archive", archive);
        Set<String> classes = new TreeSet<>(frames);
        classes.addAll(registry.board(course).classes());
        JsonArray ghosts = new JsonArray();
        for (String frame : classes) {
            RecordGhosts.Stored stored = registry.ghosts().get(course.id, frame);
            if (stored == null) {
                continue;
            }
            JsonObject ghost = new JsonObject();
            ghost.addProperty("frame", stored.frame());
            ghost.addProperty("layout", stored.layout());
            ghost.addProperty("revision", stored.revision());
            ghost.addProperty("holder", stored.holder());
            ghost.addProperty("nanos", stored.nanos());
            ghost.addProperty("bytes", stored.data().length);
            ghost.addProperty("sha256", sha256(stored.data()));
            try {
                GhostTrack track = GhostCodec.decode(stored.data());
                ghost.addProperty("samples", track.samples());
                ghost.addProperty("lapNanos", track.lapNanos());
                ghost.addProperty("trackFrame", track.frame());
                ghost.addProperty("trackLayout", track.layout());
                ghost.addProperty("look", String.valueOf(track.look()));
            } catch (IOException e) {
                ghost.addProperty("error", e.toString());
            }
            ghosts.add(ghost);
        }
        out.add("recordGhosts", ghosts);
        return out;
    }

    private static JsonObject item(DynamicOps<Tag> ops, ItemStack stack) {
        JsonObject out = new JsonObject();
        out.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        out.addProperty("count", stack.getCount());
        out.addProperty("name", stack.getHoverName().getString());
        out.addProperty("snbt", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow().toString());
        DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
        if (build != null) {
            presetOf(build).ifPresent(preset -> out.addProperty("preset", preset));
            out.addProperty("paintText", PaintItems.paint(stack).toText());
            out.addProperty("waterproof", CoatingComponents.coated(stack));
        }
        if (stack.get(CoreComponents.BOUND_DRONE.get()) != null) {
            out.addProperty("boundDrone", stack.get(CoreComponents.BOUND_DRONE.get()).drone().toString());
        }
        return out;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonElement parse(String text) {
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setStrictness(Strictness.LENIENT);
        return JsonParser.parseReader(reader);
    }

    public interface ShareAccess {
        String export(DroneData data, Collection<ShareLayer> layers);

        ShareDecoded decode(String code);
    }

    public static final ShareAccess GAME_SHARE = new ShareAccess() {
        @Override
        public String export(DroneData data, Collection<ShareLayer> layers) {
            return ShareService.encode(data, layers, true, Optional.empty());
        }

        @Override
        public ShareDecoded decode(String code) {
            return ShareService.decode(code);
        }
    };

    public static final List<String> LEGACY_CODES = List.of("pw2", "pw1");

    public record ShareCheck(String export, List<String> conversions, int sameText, List<String> failures) {
        public int converted() {
            return this.conversions.size();
        }
    }

    public record ShareReport(int drones, int exported, int converted, int sameText, List<String> skipped, List<String> failures, List<String> lines) {
        public String summary() {
            return this.drones + " Drohnen als PW3 exportiert und wieder gelesen (" + this.exported + " Codes), " + this.converted
                    + " alte Codes umgerechnet, davon " + this.sameText + " mit demselben Text wie der Export, " + this.failures.size()
                    + " Abweichungen, " + this.skipped.size() + " nicht geprüft";
        }

        public String report() {
            StringBuilder out = new StringBuilder(this.summary()).append("\n\nAbweichungen:\n");
            this.failures.forEach(line -> out.append(line).append('\n'));
            out.append("\nNicht geprüft:\n");
            this.skipped.forEach(line -> out.append(line).append('\n'));
            out.append("\nExporte (uuid, PW3 mit den Ebenen ").append(ShareLayer.DEFAULT_EXPORT).append(", umgerechnete alte Codes):\n");
            this.lines.forEach(line -> out.append(line).append('\n'));
            return out.toString();
        }
    }

    public static ShareReport shareCodes(MinecraftServer server, @Nullable JsonObject expected, ShareAccess share) {
        ServerLevel level = server.overworld();
        boolean jmp = jmpLoaded();
        List<String> skipped = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        int drones = 0;
        int exported = 0;
        int converted = 0;
        int same = 0;
        JsonElement list = expected == null ? null : expected.get("drones");
        for (JsonElement element : list != null && list.isJsonArray() ? list.getAsJsonArray() : new JsonArray()) {
            JsonObject before = element.getAsJsonObject();
            String uuid = before.get("uuid").getAsString();
            String path = "drones[uuid=" + uuid + "]";
            if (needsJmp(before) && !jmp) {
                skipped.add(path + ": Teile aus " + JMP + " (Add-on nicht geladen)");
                continue;
            }
            Optional<DroneData> data = level.getEntity(UUID.fromString(uuid)) instanceof QuadcopterEntity drone
                    ? PropwashApi.builds().read(drone.toStack()) : Optional.empty();
            if (data.isEmpty()) {
                failures.add(path + ": Drohne fehlt in der Welt");
                continue;
            }
            drones++;
            ShareCheck check = shareCheck(path, data.get(), before.get("shareCodes"), share);
            if (!check.export().isEmpty()) {
                exported++;
            }
            converted += check.converted();
            same += check.sameText();
            failures.addAll(check.failures());
            lines.add(uuid + " " + (check.export().isEmpty() ? "–" : check.export()) + (check.conversions().isEmpty() ? ""
                    : ", " + String.join(", ", check.conversions())));
        }
        return new ShareReport(drones, exported, converted, same, skipped, failures, lines);
    }

    public static ShareCheck shareCheck(String path, DroneData data, @Nullable JsonElement old, ShareAccess share) {
        List<String> failures = new ArrayList<>();
        String code;
        try {
            code = share.export(data, ShareLayer.DEFAULT_EXPORT);
        } catch (RuntimeException e) {
            failures.add(path + ": PW3-Export scheitert: " + e);
            return new ShareCheck("", List.of(), 0, failures);
        }
        if (!code.startsWith(Pw3Params.PREFIX) || !ShareCommands.fits(code)) {
            failures.add(path + ": PW3-Export " + shorten(new JsonPrimitive(code)) + " ist kein PW3-Code für den Chat");
        }
        ShareDecoded back = share.decode(code);
        ShareContent want = ShareContent.of(data, ShareLayer.DEFAULT_EXPORT, Optional.empty());
        if (!back.partsKnown()) {
            failures.add(path + ": PW3-Export " + code + " nicht lesbar: " + back.status() + (back.unknownParts().isEmpty() ? "" : ", unbekannt "
                    + back.unknownParts()));
        } else if (!back.require().equals(want)) {
            failures.add(path + ": PW3-Export " + code + " ergibt " + back.require() + " statt " + want);
        }
        List<String> conversions = new ArrayList<>();
        int same = 0;
        JsonObject codes = old != null && old.isJsonObject() ? old.getAsJsonObject() : new JsonObject();
        for (String format : LEGACY_CODES) {
            JsonElement legacy = codes.get(format);
            if (legacy == null || !legacy.isJsonPrimitive() || legacy.getAsString().isBlank()) {
                continue;
            }
            ShareConvert.Conversion conversion = ShareConvert.toPw3(legacy.getAsString());
            if (!conversion.converted() || conversion.code().isEmpty()) {
                failures.add(path + ": alter Code " + format + " nicht umgerechnet: " + conversion.status() + conversion.reason().map(r -> " (" + r + ")")
                        .orElse(""));
                continue;
            }
            String text = conversion.code().get();
            conversions.add(format + " → " + text);
            ShareDecoded decoded = share.decode(text);
            if (!decoded.ok()) {
                failures.add(path + ": " + format + " umgerechnet zu " + text + ", nicht lesbar: " + decoded.status());
                continue;
            }
            if (!decoded.require().build().equals(data.build())) {
                failures.add(path + ": " + format + " umgerechnet zu " + text + " mit anderen Teilen als die Drohne in der Welt: "
                        + decoded.require().build() + " statt " + data.build());
                continue;
            }
            String again = share.export(data, decoded.layers());
            if (again.equals(text)) {
                same++;
            } else {
                failures.add(path + ": PW3-Export " + again + " (Ebenen " + decoded.layers() + ") ist nicht derselbe Text wie " + format
                        + " umgerechnet " + text);
            }
        }
        return new ShareCheck(code, List.copyOf(conversions), same, failures);
    }

    public static String freecamOff(MinecraftServer server, @Nullable JsonObject expected, List<String> failures) {
        Optional<RuleSpec> spec = WorldRuleSpecs.byName(FREECAM_RULE);
        if (spec.isEmpty()) {
            failures.add("Welt-Regel " + FREECAM_RULE + " fehlt");
            return FREECAM_RULE + " fehlt";
        }
        int standard = spec.get().defaultValue();
        int now = WorldRules.get(server, spec.get());
        if (standard != 0) {
            failures.add("Standard von " + FREECAM_RULE + " ist " + standard + " statt 0");
        }
        if (now != 0) {
            failures.add(FREECAM_RULE + " nach dem Umstieg " + now + " statt 0");
        }
        JsonObject values = expected != null && expected.has("worldRules") && expected.getAsJsonObject("worldRules").has("values")
                ? expected.getAsJsonObject("worldRules").getAsJsonObject("values") : null;
        String before = values == null || !values.has(FREECAM_RULE) ? "in der alten Welt nicht gesetzt" : "in der alten Welt " + values.get(FREECAM_RULE);
        return FREECAM_RULE + "=" + now + " (Standard " + standard + ", " + before + ")";
    }

    public static final class Diff {
        public final boolean jmp;
        public final List<String> failures = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
        public int compared;

        public Diff(boolean jmp) {
            this.jmp = jmp;
        }

        public void fail(String path, String message) {
            this.failures.add(path + ": " + message);
        }

        public void skip(String path, String reason) {
            this.skipped.add(path + ": " + reason);
        }

        public String summary() {
            return this.compared + " Werte verglichen, " + this.failures.size() + " Abweichungen, " + this.skipped.size() + " nicht geprüft"
                    + (this.skipped.isEmpty() ? "" : " (" + String.join("; ", this.skipped.subList(0, Math.min(4, this.skipped.size())))
                    + (this.skipped.size() > 4 ? " …" : "") + ")");
        }

        public String report() {
            StringBuilder out = new StringBuilder(this.summary()).append("\n\nAbweichungen:\n");
            this.failures.forEach(line -> out.append(line).append('\n'));
            out.append("\nNicht geprüft:\n");
            this.skipped.forEach(line -> out.append(line).append('\n'));
            return out.toString();
        }
    }

    public static void compare(String path, JsonElement expected, @Nullable JsonElement actual, Diff diff) {
        String norm = path.replaceAll("\\[[^\\]]*\\]", "[]");
        if (IGNORED.matcher(norm).matches()) {
            return;
        }
        if (expected.isJsonObject() && needsJmp(expected.getAsJsonObject()) && !diff.jmp) {
            diff.skip(path, "Teile aus " + JMP + " (Add-on nicht geladen)");
            return;
        }
        if (actual == null) {
            if (expected.isJsonPrimitive() && isNan(expected.getAsJsonPrimitive())) {
                diff.skip(path, "Feld aus dem Format des alten Stands, ohne Wert");
            } else if (!expected.isJsonNull()) {
                diff.fail(path, "fehlt");
            }
            return;
        }
        if (expected.isJsonNull()) {
            diff.compared++;
            if (!actual.isJsonNull()) {
                diff.fail(path, "erwartet null, ist " + shorten(actual));
            }
            return;
        }
        if (norm.endsWith(".snbt") && expected.isJsonPrimitive() && actual.isJsonPrimitive()) {
            JsonElement left = snbt(expected.getAsString());
            JsonElement right = snbt(actual.getAsString());
            if (left != null && right != null) {
                compare(path + "{}", left, right, diff);
                return;
            }
        }
        if (expected.isJsonPrimitive()) {
            diff.compared++;
            if (!actual.isJsonPrimitive() || !samePrimitive(norm, expected.getAsJsonPrimitive(), actual.getAsJsonPrimitive())) {
                if (!diff.jmp && (path.contains(JMP + ":") || expected.getAsString().contains(JMP + ":")) && actual.toString().contains("MISSING")) {
                    diff.compared--;
                    diff.skip(path, "Teile aus " + JMP + " (Add-on nicht geladen)");
                    return;
                }
                diff.fail(path, "erwartet " + shorten(expected) + ", ist " + shorten(actual));
            }
            return;
        }
        if (expected.isJsonObject()) {
            if (!actual.isJsonObject()) {
                diff.fail(path, "erwartet Objekt, ist " + shorten(actual));
                return;
            }
            JsonObject right = actual.getAsJsonObject();
            JsonObject left = expected.getAsJsonObject();
            boolean derivedPreset = false;
            String evidence = left.has("build") ? "build" : left.has("snbt") ? "snbt" : null;
            if (left.has("preset") && evidence != null && !right.has("preset")) {
                Diff build = new Diff(diff.jmp);
                compare(path + "." + evidence, left.get(evidence), right.get(evidence), build);
                derivedPreset = build.failures.isEmpty() && build.skipped.isEmpty();
            }
            for (Map.Entry<String, JsonElement> entry : left.entrySet()) {
                String child = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                if (derivedPreset && entry.getKey().equals("preset")) {
                    diff.skip(child, "Bau unverändert, entspricht nicht mehr dem heutigen Preset " + entry.getValue().getAsString());
                    continue;
                }
                compare(child, entry.getValue(), right.get(entry.getKey()), diff);
            }
            return;
        }
        JsonArray left = expected.getAsJsonArray();
        if (!actual.isJsonArray()) {
            diff.fail(path, "erwartet Liste, ist " + shorten(actual));
            return;
        }
        JsonArray right = actual.getAsJsonArray();
        String key = keyOf(left);
        if (key != null) {
            Map<String, JsonElement> byKey = new LinkedHashMap<>();
            for (JsonElement element : right) {
                if (element.isJsonObject() && element.getAsJsonObject().has(key)) {
                    byKey.put(element.getAsJsonObject().get(key).getAsString(), element);
                }
            }
            for (JsonElement element : left) {
                String id = element.getAsJsonObject().get(key).getAsString();
                compare(path + "[" + key + "=" + id + "]", element, byKey.get(id), diff);
            }
            return;
        }
        if (left.size() != right.size()) {
            diff.fail(path, "erwartet " + left.size() + " Einträge, ist " + right.size());
            return;
        }
        for (int i = 0; i < left.size(); i++) {
            compare(path + "[" + i + "]", left.get(i), right.get(i), diff);
        }
    }

    public static boolean needsJmp(JsonObject object) {
        JsonElement preset = object.get("preset");
        if (preset != null && preset.isJsonPrimitive() && preset.getAsString().startsWith(JMP + ":")) {
            return true;
        }
        JsonElement parts = object.get("parts");
        if (parts != null && parts.isJsonArray()) {
            for (JsonElement part : parts.getAsJsonArray()) {
                if (part.isJsonObject() && part.getAsJsonObject().get("id").getAsString().startsWith(JMP + ":")) {
                    return true;
                }
            }
        }
        JsonElement snbt = object.get("snbt");
        return snbt != null && snbt.isJsonPrimitive() && snbt.getAsString().contains(JMP + ":");
    }

    private static @Nullable String keyOf(JsonArray array) {
        if (array.isEmpty() || !array.get(0).isJsonObject()) {
            return null;
        }
        for (String key : List.of("uuid", "slot", "id", "frame")) {
            boolean all = true;
            Set<String> seen = new TreeSet<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject() || !element.getAsJsonObject().has(key) || !element.getAsJsonObject().get(key).isJsonPrimitive()
                        || !seen.add(element.getAsJsonObject().get(key).getAsString())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return key;
            }
        }
        return null;
    }

    private static @Nullable JsonElement snbt(String text) {
        try {
            Tag tag = TagParser.parseCompoundFully(text);
            return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean samePrimitive(String norm, JsonPrimitive expected, JsonPrimitive actual) {
        if (norm.endsWith(".look") && expected.isString() && actual.isString()) {
            return lookText(expected.getAsString()).equals(lookText(actual.getAsString()));
        }
        return same(expected, actual, POSITION.matcher(norm).matches() || norm.contains(".Pos[]"));
    }

    static String lookText(String look) {
        return look.replace(EMPTY_CREATIVE, "");
    }

    public static boolean same(JsonPrimitive a, JsonPrimitive b, boolean position) {
        if (a.isNumber() || b.isNumber() || isNan(a) || isNan(b)) {
            double x = number(a);
            double y = number(b);
            if (Double.isNaN(x) || Double.isNaN(y)) {
                return Double.isNaN(x) && Double.isNaN(y);
            }
            double tolerance = position ? POSITION_TOLERANCE : TOLERANCE * Math.max(1.0, Math.abs(x));
            return Math.abs(x - y) <= tolerance;
        }
        if (a.isBoolean() || b.isBoolean()) {
            return booleanOf(a) == booleanOf(b);
        }
        return a.getAsString().equals(b.getAsString());
    }

    public static boolean isNan(JsonPrimitive value) {
        return value.isString() && value.getAsString().equalsIgnoreCase("nan");
    }

    private static double number(JsonPrimitive value) {
        if (value.isBoolean()) {
            return value.getAsBoolean() ? 1.0 : 0.0;
        }
        try {
            return value.getAsDouble();
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static boolean booleanOf(JsonPrimitive value) {
        if (value.isBoolean()) {
            return value.getAsBoolean();
        }
        if (value.isNumber()) {
            return value.getAsDouble() != 0.0;
        }
        return Boolean.parseBoolean(value.getAsString());
    }

    private static String shorten(JsonElement value) {
        String text = value.toString();
        return text.length() > 120 ? text.substring(0, 117) + "…" : text;
    }
}
