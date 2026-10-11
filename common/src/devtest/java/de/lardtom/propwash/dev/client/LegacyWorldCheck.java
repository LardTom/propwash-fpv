package de.lardtom.propwash.dev.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.core.BoundDrone;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class LegacyWorldCheck {
    public static final String ENV = "PROPWASH_SMOKE_WORLD";
    private static final String FUTURE = "future";
    private static final String ERRORS = "errors";
    private static final String NOTES = "notes";
    private static final Set<String> STRICT_ENTITY_KEYS = Set.of("Build", "Tune", "Battery", "HasBattery", "Paint", "Wear", "Orientation",
            "CameraCharge", "Size", "GateIndex", "Course", "WorldCourse", "UUID", "Pos");
    private static @Nullable Snapshot snapshot;

    private LegacyWorldCheck() {
    }

    public static @Nullable String worldName() {
        String name = System.getenv(ENV);
        return name == null || name.isBlank() ? null : name.trim();
    }

    static void open(Minecraft mc, String name) throws IOException {
        Path dir = mc.getLevelSource().getBaseDir().resolve(name);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("alte Welt fehlt: " + dir);
        }
        snapshot = Snapshot.read(dir);
        Constants.LOG.info("[Selbsttest] öffne alte Welt '{}' (Besitzer {}, {} Kurse)", name, snapshot.owner(), snapshot.courses().size());
        mc.createWorldOpenFlows().openWorld(name, () -> mc.gui.setScreen(new TitleScreen()));
    }

    static List<SelfTest.Step> steps() {
        return List.of(
                new SelfTest.Step("alte Welt: Inventar des Spielers lädt und speichert gleich", 200,
                        ctx -> serverCheck(ctx, LegacyWorldCheck::checkPlayer)),
                new SelfTest.Step("alte Welt: Drohnen und Gates laden und speichern gleich", 600,
                        ctx -> serverCheck(ctx, LegacyWorldCheck::checkEntities)),
                new SelfTest.Step("alte Welt: Rennkurse geladen", 200,
                        ctx -> serverCheck(ctx, LegacyWorldCheck::checkCourses)));
    }

    @FunctionalInterface
    private interface ServerCheck {
        void run(MinecraftServer server, Snapshot snapshot, List<String> errors, List<String> notes) throws IOException;
    }

    @SuppressWarnings("unchecked")
    private static StepResult serverCheck(SelfTest.StepContext ctx, ServerCheck check) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            Snapshot snap = snapshot;
            if (snap == null) {
                return StepResult.fail("keine alte Welt geöffnet");
            }
            List<String> errors = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            state.put(ERRORS, errors);
            state.put(NOTES, notes);
            state.put(FUTURE, ctx.onServer(server -> {
                try {
                    check.run(server, snap, errors, notes);
                } catch (IOException e) {
                    errors.add("Lesefehler: " + e);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Prüfung: " + future.exceptionNow());
        }
        List<String> errors = (List<String>) state.get(ERRORS);
        for (String note : (List<String>) state.get(NOTES)) {
            ctx.log(note);
        }
        return errors.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", errors.subList(0, Math.min(8, errors.size())))
                + (errors.size() > 8 ? " … (" + errors.size() + " Fehler)" : ""));
    }

    private static void checkPlayer(MinecraftServer server, Snapshot snap, List<String> errors, List<String> notes) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            errors.add("kein Spieler");
            return;
        }
        ServerPlayer player = players.getFirst();
        CompoundTag original = snap.ownerData();
        if (original == null) {
            errors.add("keine Spielerdaten des Besitzers " + snap.owner());
            return;
        }
        RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        int items = 0;
        int propwash = 0;
        ListTag inventory = original.getListOrEmpty("Inventory");
        for (int i = 0; i < inventory.size(); i++) {
            CompoundTag entry = inventory.getCompoundOrEmpty(i).copy();
            int slot = entry.getByteOr("Slot", (byte) -1) & 255;
            entry.remove("Slot");
            ItemStack stack = player.getInventory().getItem(slot);
            items++;
            propwash += compareItem("Inventar " + slot, entry, stack, ops, errors, notes) ? 1 : 0;
        }
        ListTag ender = original.getListOrEmpty("EnderItems");
        for (int i = 0; i < ender.size(); i++) {
            CompoundTag entry = ender.getCompoundOrEmpty(i).copy();
            int slot = entry.getByteOr("Slot", (byte) -1) & 255;
            entry.remove("Slot");
            ItemStack stack = player.getEnderChestInventory().getItem(slot);
            items++;
            propwash += compareItem("Endertruhe " + slot, entry, stack, ops, errors, notes) ? 1 : 0;
        }
        CompoundTag equipment = original.getCompoundOrEmpty("equipment");
        for (String key : equipment.keySet()) {
            ItemStack stack = player.getItemBySlot(EquipmentSlot.byName(key));
            items++;
            propwash += compareItem("Ausrüstung " + key, equipment.getCompoundOrEmpty(key), stack, ops, errors, notes) ? 1 : 0;
        }
        notes.add(items + " Stapel verglichen, davon " + propwash + " von Propwash, alle byte-gleich neu gespeichert: " + errors.isEmpty());
    }

    private static boolean compareItem(String where, CompoundTag expected, ItemStack stack, RegistryOps<Tag> ops, List<String> errors,
                                       List<String> notes) {
        Tag actual = stack.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        if (!expected.equals(actual)) {
            errors.add(where + ": gespeichert " + expected + ", geladen " + actual);
        }
        boolean own = expected.getString("id").orElse("").startsWith(Constants.MOD_ID + ":");
        DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
        if (build != null) {
            if (!build.missingParts().isEmpty()) {
                errors.add(where + ": fehlende Teile " + build.missingParts());
            }
            notes.add(where + ": Drohne " + build.frame().key() + "/" + build.motor().key() + "/" + build.battery().key() + ", baubar "
                    + build.buildable());
        }
        BoundDrone bound = stack.get(CoreComponents.BOUND_DRONE.get());
        if (bound != null && bound.frame().missing()) {
            errors.add(where + ": gebundener Rahmen fehlt " + bound.frame().id());
        }
        PartRef part = stack.get(CoreComponents.PART.get());
        if (part != null && part.resolve().filter(p -> !p.missing()).isEmpty()) {
            errors.add(where + ": Teil " + part + " löst nicht auf");
        }
        return own;
    }

    private static void checkEntities(MinecraftServer server, Snapshot snap, List<String> errors, List<String> notes) throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        Set<String> builds = new TreeSet<>();
        for (Path region : snap.entityRegions()) {
            for (CompoundTag chunk : readRegion(region)) {
                ListTag entities = chunk.getListOrEmpty("Entities");
                for (int i = 0; i < entities.size(); i++) {
                    CompoundTag original = entities.getCompoundOrEmpty(i);
                    String id = original.getString("id").orElse("");
                    if (!id.startsWith(Constants.MOD_ID + ":")) {
                        continue;
                    }
                    counts.merge(id, 1, Integer::sum);
                    checkEntity(server, region.getFileName() + " " + id, original, errors, builds);
                }
            }
        }
        if (counts.isEmpty()) {
            errors.add("keine Propwash-Entities in der alten Welt gefunden");
        }
        notes.add("Entities " + counts + ", Baupläne " + builds);
    }

    private static void checkEntity(MinecraftServer server, String where, CompoundTag original, List<String> errors, Set<String> builds) {
        ProblemReporter.Collector reporter = new ProblemReporter.Collector();
        Optional<Entity> loaded = EntityType.create(TagValueInput.create(reporter, server.registryAccess(), original), server.overworld(),
                new EntitySpawnRequest(EntitySpawnReason.LOAD, true));
        if (loaded.isEmpty()) {
            errors.add(where + ": nicht geladen");
            return;
        }
        Entity entity = loaded.get();
        TagValueOutput output = TagValueOutput.createWithContext(reporter, server.registryAccess());
        entity.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        if (!reporter.isEmpty()) {
            errors.add(where + ": Meldungen beim Laden/Speichern " + reporter.getReport());
        }
        for (String key : original.keySet()) {
            if (key.equals("id")) {
                continue;
            }
            Tag before = original.get(key);
            Tag after = saved.get(key);
            if (before != null && !before.equals(after) && STRICT_ENTITY_KEYS.contains(key)) {
                errors.add(where + ": " + key + " gespeichert " + before + ", neu " + after);
            }
        }
        if (entity instanceof QuadcopterEntity drone) {
            DroneBuild build = drone.build();
            if (!build.missingParts().isEmpty()) {
                errors.add(where + ": fehlende Teile " + build.missingParts());
            }
            for (PartInfo part : build.parts()) {
                if (!part.builtin()) {
                    errors.add(where + ": fremdes Teil " + part.id());
                }
            }
            builds.add(build.frame().key() + "/" + build.stack().key() + "/" + build.motor().key() + "/" + build.prop().key() + "/"
                    + build.video().key() + "/" + build.battery().key());
        } else if (!(entity instanceof GateEntity)) {
            errors.add(where + ": unerwarteter Typ " + entity.getClass().getSimpleName());
        }
    }

    private static void checkCourses(MinecraftServer server, Snapshot snap, List<String> errors, List<String> notes) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            errors.add("Kursregister nicht gestartet");
            return;
        }
        Map<Integer, Set<String>> loaded = new HashMap<>();
        for (CourseRegistry.Course course : registry.courses()) {
            Set<String> gates = new TreeSet<>();
            for (CourseRegistry.GateRecord gate : course.gates) {
                gates.add(gate.uuid);
            }
            loaded.put(course.id, gates);
        }
        for (Map.Entry<Integer, Set<String>> expected : snap.courses().entrySet()) {
            Set<String> gates = loaded.get(expected.getKey());
            if (gates == null) {
                errors.add("Kurs " + expected.getKey() + " fehlt");
            } else if (!gates.equals(expected.getValue())) {
                errors.add("Kurs " + expected.getKey() + ": Gates " + expected.getValue() + " → " + gates);
            }
        }
        notes.add(snap.courses().size() + " Kurse aus der alten Welt, geladen " + loaded.keySet());
    }

    private static List<CompoundTag> readRegion(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        List<CompoundTag> chunks = new ArrayList<>();
        if (data.length < 8192) {
            return chunks;
        }
        for (int i = 0; i < 1024; i++) {
            int location = ((data[i * 4] & 255) << 24) | ((data[i * 4 + 1] & 255) << 16) | ((data[i * 4 + 2] & 255) << 8) | (data[i * 4 + 3] & 255);
            if (location == 0) {
                continue;
            }
            int offset = (location >>> 8) * 4096;
            if (offset + 5 > data.length) {
                throw new IOException(file.getFileName() + ": Chunk " + i + " außerhalb der Datei");
            }
            int length = ((data[offset] & 255) << 24) | ((data[offset + 1] & 255) << 16) | ((data[offset + 2] & 255) << 8) | (data[offset + 3] & 255);
            int compression = data[offset + 4] & 255;
            ByteArrayInputStream raw = new ByteArrayInputStream(data, offset + 5, length - 1);
            InputStream in = switch (compression) {
                case 1 -> new GZIPInputStream(raw);
                case 2 -> new InflaterInputStream(raw);
                case 3 -> raw;
                default -> throw new IOException(file.getFileName() + ": Kompression " + compression + " wird hier nicht gelesen");
            };
            try (DataInputStream input = new DataInputStream(in)) {
                chunks.add(NbtIo.read(input, NbtAccounter.unlimitedHeap()));
            }
        }
        return chunks;
    }

    private record Snapshot(Path dir, @Nullable UUID owner, @Nullable CompoundTag ownerData, Map<Integer, Set<String>> courses,
                            List<Path> entityRegions) {
        static Snapshot read(Path dir) throws IOException {
            CompoundTag level = NbtIo.readCompressed(dir.resolve("level.dat"), NbtAccounter.unlimitedHeap());
            UUID owner = level.getCompoundOrEmpty("Data").getIntArray("singleplayer_uuid").map(UUIDUtil::uuidFromIntArray).orElse(null);
            CompoundTag ownerData = null;
            if (owner != null) {
                Path file = dir.resolve(LevelResource.PLAYER_DATA_DIR.id()).resolve(owner + ".dat");
                if (Files.isRegularFile(file)) {
                    ownerData = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                }
            }
            Map<Integer, Set<String>> courses = new TreeMap<>();
            Path race = dir.resolve("data").resolve("propwash_race.json");
            if (Files.isRegularFile(race)) {
                JsonObject json = JsonParser.parseString(Files.readString(race, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray list = json.has("courses") ? json.getAsJsonArray("courses") : new JsonArray();
                for (JsonElement element : list) {
                    JsonObject course = element.getAsJsonObject();
                    Set<String> gates = new TreeSet<>();
                    if (course.has("gates")) {
                        for (JsonElement gate : course.getAsJsonArray("gates")) {
                            gates.add(gate.getAsJsonObject().get("uuid").getAsString());
                        }
                    }
                    courses.put(course.get("id").getAsInt(), gates);
                }
            }
            List<Path> regions;
            try (Stream<Path> files = Files.walk(dir)) {
                regions = files.filter(p -> p.getFileName().toString().endsWith(".mca") && p.getParent() != null
                        && p.getParent().getFileName().toString().equals("entities")).sorted().toList();
            }
            return new Snapshot(dir, owner, ownerData, courses, regions);
        }
    }
}
