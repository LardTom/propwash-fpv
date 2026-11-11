package de.lardtom.propwash.dev;

import com.google.gson.JsonObject;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class MigrationJoinCheck {
    public static final String MODE = "migration";
    public static final String FIXTURE_ENV = "PROPWASH_JOINTEST_FIXTURE";
    public static final String SIGNAL = "propwash-migration";
    public static final String DIR = "migration";
    public static final String AFTER = "after";
    public static final int SETTLE_TICKS = 60;
    public static final int ENTITY_WAIT = 600;
    public static final int SAVE_AFTER = 200;
    private static final Map<String, String> VERDICT = new LinkedHashMap<>();
    private static final Map<RuleSpec, Integer> PHYSICS = new LinkedHashMap<>();
    private static @Nullable JsonObject expected;
    private static @Nullable JsonObject early;
    private static @Nullable String failure;
    private static boolean worldDone;
    private static int playerSince = -1;
    private static boolean playerDone;
    private static int course = -1;
    private static int lapsBefore;
    private static long assistedBefore;
    private static int acceptedAt = -1;
    private static boolean saved;

    private MigrationJoinCheck() {
    }

    public static boolean active() {
        return MODE.equals(JoinTestEnv.multi());
    }

    static @Nullable String fixtureValue() {
        String property = System.getProperty("propwash.jointest.fixture");
        return property != null ? property : System.getenv(FIXTURE_ENV);
    }

    static void init() {
        MigrationValues.Fixture fixture = MigrationValues.fixture(fixtureValue());
        if (fixture == null) {
            failure = FIXTURE_ENV + " fehlt";
            Constants.LOG.error("[Beitrittstest] Migration: {} fehlt (Ordner mit world.zip und expected.json)", FIXTURE_ENV);
            return;
        }
        try {
            expected = MigrationValues.read(fixture.expected());
            Path world = Services.PLATFORM.getGameDir().resolve(levelName());
            int files = MigrationValues.install(fixture, world);
            Constants.LOG.info("[Beitrittstest] Migration: {} Dateien aus {} nach {} (Stand {}, Erwartung {})", files, fixture.world(),
                    Services.PLATFORM.getGameDir().relativize(world), text(expected, "stand"), fixture.expected().getFileName());
        } catch (IOException | RuntimeException e) {
            failure = "Testwelt nicht bereitgestellt: " + e;
            Constants.LOG.error("[Beitrittstest] Migration: Testwelt nicht bereitgestellt", e);
            return;
        }
        ServerBus.STARTED.register(MigrationJoinCheck::onStarted);
    }

    private static String levelName() {
        Path file = Services.PLATFORM.getGameDir().resolve("server.properties");
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                Constants.LOG.warn("[Beitrittstest] Migration: server.properties nicht gelesen: {}", e.toString());
            }
        }
        String name = properties.getProperty("level-name", "world").trim();
        return name.isEmpty() ? "world" : name;
    }

    private static String text(@Nullable JsonObject object, String key) {
        return object == null || !object.has(key) || !object.get(key).isJsonPrimitive() ? "?" : object.get(key).getAsString();
    }

    private static JsonObject worldExpected(JsonObject all) {
        return MigrationValues.without(all, MigrationValues.PLAYER_SECTIONS, true);
    }

    private static void onStarted(MinecraftServer server) {
        JsonObject all = expected;
        if (all != null) {
            MigrationValues.force(server, all, true);
        }
    }

    private static int count(JsonObject all, String section) {
        return all.has(section) && all.get(section).isJsonArray() ? all.getAsJsonArray(section).size() : 0;
    }

    static void tick(MinecraftServer server, int tick) {
        if (failure != null) {
            if (tick % 200 == 0) {
                Constants.LOG.error("[Beitrittstest] Migration: {}", failure);
            }
            VERDICT.put("world", "fail");
            signal(server);
            return;
        }
        JsonObject all = expected;
        if (all == null) {
            return;
        }
        if (early == null) {
            early = MigrationValues.capture(server, worldExpected(all), null);
            Constants.LOG.info("[Beitrittstest] Migration: Welt-Daten im ersten Tick erfasst (vor den Prüfungen des Server-Rauchtests), warte auf {} Entities",
                    count(all, "drones") + count(all, "gates"));
            return;
        }
        if (!worldDone) {
            if (tick % 10 != 0) {
                return;
            }
            String missing = MigrationValues.missingEntities(server, all);
            if (!missing.isEmpty() && tick < ENTITY_WAIT) {
                return;
            }
            world(server, all, missing);
            return;
        }
        ServerPlayer owner = owner(server, all);
        if (!playerDone) {
            if (owner == null) {
                playerSince = -1;
                return;
            }
            if (playerSince < 0) {
                playerSince = tick;
                Constants.LOG.info("[Beitrittstest] Migration: {} ist auf dem Server", owner.getGameProfile().name());
            }
            if (tick - playerSince >= SETTLE_TICKS) {
                player(server, all, owner);
            }
            return;
        }
        if (owner == null || course < 0) {
            return;
        }
        if (acceptedAt < 0 && tick % 20 == 0) {
            lap(server, owner, tick);
        }
        if (acceptedAt >= 0 && !saved && tick - acceptedAt >= SAVE_AFTER) {
            save(server, all, owner);
        }
    }

    private static @Nullable ServerPlayer owner(MinecraftServer server, JsonObject all) {
        String name = all.has("owner") ? all.getAsJsonObject("owner").get("name").getAsString() : null;
        if (name != null) {
            return server.getPlayerList().getPlayerByName(name);
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.getFirst();
    }

    private static void world(MinecraftServer server, JsonObject all, String missing) {
        worldDone = true;
        JsonObject want = worldExpected(all);
        JsonObject actual = MigrationValues.capture(server, want, null);
        JsonObject merged = early == null ? actual : early.deepCopy();
        merged.add("drones", actual.get("drones"));
        merged.add("gates", actual.get("gates"));
        MigrationValues.force(server, all, false);
        MigrationValues.Diff diff = new MigrationValues.Diff(MigrationValues.jmpLoaded());
        MigrationValues.compare("", want, merged, diff);
        List<String> notes = new ArrayList<>();
        List<String> freecam = new ArrayList<>();
        notes.add(MigrationValues.freecamOff(server, all, freecam));
        freecam.forEach(line -> diff.fail(MigrationValues.FREECAM_RULE, line));
        write("server-world-actual.json", MigrationValues.GSON.toJson(merged));
        write("server-world-diff.txt", diff.report() + "\nHinweise:\n" + String.join("\n", notes) + "\n");
        boolean ok = diff.failures.isEmpty() && missing.isEmpty();
        VERDICT.put("world", ok ? "ok" : "fail");
        VERDICT.put("values", diff.compared + "/" + diff.failures.size());
        VERDICT.put("freecam", freecam.isEmpty() ? "off" : "fail");
        Constants.LOG.info("[Beitrittstest] Migration: Welt am dedizierten Server: {}{}; {}", diff.summary(),
                missing.isEmpty() ? "" : ", nicht geladen: " + missing, String.join("; ", notes));
        if (!diff.failures.isEmpty()) {
            Constants.LOG.warn("[Beitrittstest] Migration: Abweichungen, die ersten: {}", String.join("; ", diff.failures.subList(0,
                    Math.min(6, diff.failures.size()))));
        }
    }

    private static void player(MinecraftServer server, JsonObject all, ServerPlayer owner) {
        playerDone = true;
        JsonObject want = MigrationValues.only(all, MigrationValues.PLAYER_SECTIONS);
        JsonObject actual = MigrationValues.capture(server, want, owner);
        JsonObject part = MigrationValues.only(actual, MigrationValues.PLAYER_SECTIONS);
        MigrationValues.Diff diff = new MigrationValues.Diff(MigrationValues.jmpLoaded());
        MigrationValues.compare("", want, part, diff);
        write("server-player-diff.txt", diff.report());
        VERDICT.put("player", diff.failures.isEmpty() ? "ok" : "fail");
        Constants.LOG.info("[Beitrittstest] Migration: Spieler {} mit Inventar und Endertruhe: {}", owner.getGameProfile().name(), diff.summary());
        if (!diff.failures.isEmpty()) {
            Constants.LOG.warn("[Beitrittstest] Migration: Abweichungen am Spieler: {}", String.join("; ", diff.failures.subList(0,
                    Math.min(6, diff.failures.size()))));
        }
        setup(server, all, owner);
        signal(server);
    }

    private static void setup(MinecraftServer server, JsonObject all, ServerPlayer owner) {
        List<MigrationValues.Stack> stacks = MigrationValues.stacks(all);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course data = stacks.isEmpty() || registry == null ? null : registry.course(stacks.getFirst().course());
        if (data == null) {
            VERDICT.put("lap", "nostack");
            Constants.LOG.error("[Beitrittstest] Migration: kein Kurs mit gestapelten Gates");
            return;
        }
        ServerLevel level = owner.level();
        String legs = MigrationValues.legsOnServer(level, data, stacks.getFirst());
        if (legs != null) {
            VERDICT.put("legs", "fail");
            Constants.LOG.error("[Beitrittstest] Migration: {}", legs);
        } else {
            VERDICT.put("legs", "ok");
        }
        course = data.id;
        VERDICT.put("course", Integer.toString(course));
        VERDICT.put("bottom", stacks.getFirst().legs().toString().replace(" ", ""));
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        for (RuleSpec spec : PhysicsRules.SPECS) {
            int value = WorldRules.get(server, spec);
            if (value != spec.defaultValue()) {
                PHYSICS.put(spec, value);
                WorldRules.set(server, spec, spec.defaultValue());
            }
        }
        Leaderboard board = registry.board(data);
        lapsBefore = board.lapsOf(owner.getUUID());
        assistedBefore = MigrationValues.latestAssisted(board, owner.getUUID());
        GateGeometry first = data.toData().geometry().getFirst();
        Vec3 start = MigrationValues.point(first, -4.0, 0.0, first.y());
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            VERDICT.put("lap", "nodrone");
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-first.normalX(), first.normalZ()));
        drone.snapTo(start.x, start.y, start.z, yaw, 0.0F);
        drone.setAttitude(new Quaternionf().rotationY((float) Math.toRadians(-yaw)));
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        level.addFreshEntity(drone);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        owner.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        owner.getInventory().setItem(0, remote);
        owner.getInventory().setSelectedSlot(0);
        owner.connection.send(new ClientboundSetHeldSlotPacket(0));
        Vec3 stand = MigrationValues.point(first, -7.0, 3.0, first.y());
        owner.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), yaw, 10.0F, true);
        DronePiloting.StartResult result = DronePiloting.tryStart(owner, drone, PilotMode.FPV);
        Constants.LOG.info("[Beitrittstest] Migration: Kurs {} mit {} Gates, unten auf Beinen {} ({}), Runden von {} bisher {}, Drohne vor Gate 0, FPV {}, "
                        + "Physik-Regeln für die Runde auf Standard: {}", course, data.gates.size(), stacks.getFirst().legs(),
                legs == null ? "Server erkennt die Beine" : legs, owner.getGameProfile().name(), lapsBefore, result, PHYSICS.keySet().stream().map(RuleSpec::name).toList());
    }

    private static void lap(MinecraftServer server, ServerPlayer owner, int tick) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course data = registry == null ? null : registry.course(course);
        if (data == null) {
            return;
        }
        Leaderboard board = registry.board(data);
        int laps = board.lapsOf(owner.getUUID());
        String assist = MigrationValues.assistOf(board, owner.getUUID(), assistedBefore);
        if (laps > lapsBefore || assist != null) {
            acceptedAt = tick;
            VERDICT.put("lap", "accepted");
            VERDICT.put("assist", assist == null ? "none" : assist);
            Constants.LOG.info("[Beitrittstest] Migration: Runde unten durch das Gate auf Beinen vom Server gezählt, Runden {} → {}{}", lapsBefore, laps,
                    assist == null ? "" : ", als Runde mit Flughilfe " + assist);
            PHYSICS.forEach((spec, value) -> WorldRules.set(server, spec, value));
            PHYSICS.clear();
            signal(server);
        }
    }

    private static void save(MinecraftServer server, JsonObject all, ServerPlayer owner) {
        saved = true;
        try {
            JsonObject after = MigrationValues.capture(server, all, owner);
            after.addProperty("format", MigrationValues.FORMAT);
            Path dir = Services.PLATFORM.getGameDir().resolve(DIR).resolve(AFTER);
            Files.createDirectories(dir);
            server.saveEverything(true, true, true);
            Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Files.deleteIfExists(dir.resolve("world.zip"));
            int files = MigrationValues.zip(world, dir.resolve("world.zip"));
            Files.writeString(dir.resolve("expected.json"), MigrationValues.GSON.toJson(after), StandardCharsets.UTF_8);
            VERDICT.put("saved", "yes");
            Constants.LOG.info("[Beitrittstest] Migration: gespeichert, {} Dateien und Erwartung in {} (zum Neuladen: {}={})", files,
                    Services.PLATFORM.getGameDir().relativize(dir), FIXTURE_ENV, dir);
        } catch (IOException | RuntimeException e) {
            VERDICT.put("saved", "fail");
            Constants.LOG.error("[Beitrittstest] Migration: Speichern fehlgeschlagen", e);
        }
        signal(server);
    }

    private static void signal(MinecraftServer server) {
        JsonObject all = expected;
        ServerPlayer owner = all == null ? null : owner(server, all);
        if (owner == null || !playerDone && failure == null) {
            return;
        }
        StringBuilder text = new StringBuilder(SIGNAL);
        VERDICT.forEach((key, value) -> text.append(' ').append(key).append('=').append(value));
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal(text.toString()));
        ItemStack current = owner.getInventory().getItem(MultiJoinCheck.SIGNAL_SLOT);
        Component name = current.get(DataComponents.CUSTOM_NAME);
        if (name == null || !name.getString().equals(text.toString())) {
            owner.getInventory().setItem(MultiJoinCheck.SIGNAL_SLOT, paper);
        }
    }

    private static void write(String name, String text) {
        try {
            Path dir = Services.PLATFORM.getGameDir().resolve(DIR);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("[Beitrittstest] Migration: {} nicht geschrieben", name, e);
        }
    }
}
