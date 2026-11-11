package de.lardtom.propwash.dev.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.dev.MigrationValues;
import de.lardtom.propwash.dev.MigrationValues.Diff;
import de.lardtom.propwash.dev.MigrationValues.Fixture;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.freecam.client.FreecamSession;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchEvaluator;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GhostChoice;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.GateStackSelfTests;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxLink;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.video.record.path.KeyframeDensity;
import de.lardtom.propwash.video.record.path.Simplification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static de.lardtom.propwash.dev.MigrationValues.capture;
import static de.lardtom.propwash.dev.MigrationValues.compare;
import static de.lardtom.propwash.dev.MigrationValues.copyTree;
import static de.lardtom.propwash.dev.MigrationValues.deleteTree;
import static de.lardtom.propwash.dev.MigrationValues.force;
import static de.lardtom.propwash.dev.MigrationValues.jmpLoaded;
import static de.lardtom.propwash.dev.MigrationValues.missingEntities;
import static de.lardtom.propwash.dev.MigrationValues.needsJmp;
import static de.lardtom.propwash.dev.MigrationValues.parse;
import static de.lardtom.propwash.dev.MigrationValues.same;
import static de.lardtom.propwash.dev.MigrationValues.unzip;

public final class MigrationSelfTests {
    public static final String PROPERTY = "propwash.selftest.fixtureWorld";
    public static final String SOURCE_WORLD = SelfTestRunner.WORLD_NAME + " Migration Quelle";
    public static final String GENERATED_WORLD = SelfTestRunner.WORLD_NAME + " Migration";
    public static final int FORMAT = MigrationValues.FORMAT;
    public static final String BACK = "rueckweg";
    private static final String DIR = "migration";
    private static final String FUTURE = "future";
    private static final Gson GSON = MigrationValues.GSON;
    private static final Gson CONFIG_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final Set<String> CONFIG_VERSION_KEYS = Set.of("osd.json.version", "drone_render.json.version", "input.json.profileVersion");
    private static final String SAME_BITS_SINCE = "0.5.2";
    private static final int OSD_PARKING_VERSION = 4;
    private static final List<String> DRONE_PHYSICS_TO_RULES = List.of("propWash", "groundEffect", "batterySag", "infiniteBattery", "crashDisarm",
            "propStrikes", "wind", "turbulence", "hardwareLimits", "crashTolerance", "crashDamage", "crashDamageStrength", "gravity");
    private static final String SERVER_PART_WEAR = "partWear";
    private static final String ACTION_CAM_MEMORY = "memoryMb";
    private static final String RACE_GHOST = "ghost";
    private static final String RACE_RECORD_GHOST = "recordGhost";
    private static final String RACE_GHOST_MODE = "ghostMode";
    private static final Map<String, String> CONFIG_HOLDERS = configHolders();
    private static final AtomicReference<@Nullable Fixture> FIXTURE = new AtomicReference<>();
    private static final AtomicReference<@Nullable JsonObject> EXPECTED = new AtomicReference<>();
    private static final AtomicReference<@Nullable JsonObject> FIRST = new AtomicReference<>();
    private static final AtomicReference<MigrationValues.@Nullable ShareReport> SHARES = new AtomicReference<>();
    private static final List<BlockPos> RX_PLACED = new CopyOnWriteArrayList<>();

    private MigrationSelfTests() {
    }

    public static void register() {
        SelfTest.afterLeaveStep("migration: Testwelt im Test erzeugen (nur ohne -PpropwashFixtureWorld)", 3600, MigrationSelfTests::generateStep);
        SelfTest.afterLeaveStep("migration: Welt-Kopie und Erwartungsdatei bereitstellen", 1200, MigrationSelfTests::prepareStep);
        SelfTest.afterLeaveStep("migration: Welt-Kopie öffnen", 2400, MigrationSelfTests::openStep);
        SelfTest.afterLeaveStep("migration: alle Werte wie in der Erwartungsdatei", 1200, ctx -> compareStep(ctx, 1));
        SelfTest.afterLeaveStep("migration: Codes der Drohnen als PW3 exportieren (alte Codes umgerechnet, derselbe Text)", 200,
                MigrationSelfTests::shareStep);
        SelfTest.afterLeaveStep("migration: neue Standards (allow_freecam aus, Marker aus, Kamerawinkel unverändert, Schaltermaske umgestiegen, alle Modi schalten)",
                400, MigrationSelfTests::defaultsStep);
        SelfTest.afterLeaveStep("migration: speichern und verlassen", 1200, ctx -> DrehweltSelfTests.leaveWorld(ctx, "Propwash-Selbsttest Migration gespeichert"));
        SelfTest.afterLeaveStep("migration: neu laden", 2400, MigrationSelfTests::openStep);
        SelfTest.afterLeaveStep("migration: nach dem Neuladen alle Werte wie in der Erwartungsdatei und wie vor dem Speichern", 1200,
                ctx -> compareStep(ctx, 2));
        SelfTest.afterLeaveStep("migration: Runde durch das unterste gestapelte Gate zählt (Client und Server)", 600, MigrationLegs::step);
        SelfTest.afterLeaveStep("migration: RX-Blöcke für den Rückweg nach 0.5.2 setzen", 200, MigrationSelfTests::rxStep);
        SelfTest.afterLeaveStep("migration: verlassen", 1200, ctx -> DrehweltSelfTests.leaveWorld(ctx, "Propwash-Selbsttest Migration beendet"));
        SelfTest.afterLeaveStep("migration: Ausgabe für den Rückweg nach 0.5.2 (Welt-Zip, umgestiegene Configs, RX-Positionen)", 600,
                MigrationSelfTests::backStep);
        SelfTest.afterLeaveStep("migration: Log ohne Fehler (ERROR, FATAL und Ausnahmen seit Beginn der Gruppe, ohne Konto- und Realms-Meldungen)", 200,
                MigrationSelfTests::logStep);
    }

    static Path outDir() throws IOException {
        Path dir = Services.PLATFORM.getGameDir().resolve(DIR);
        Files.createDirectories(dir);
        return dir;
    }

    static @Nullable JsonObject expected() {
        return EXPECTED.get();
    }

    static @Nullable Fixture fixtureFromProperty() {
        return MigrationValues.fixture(System.getProperty(PROPERTY));
    }

    private static StepResult generateStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            MigrationLog.begin("migration: Testwelt im Test erzeugen");
            Fixture given = fixtureFromProperty();
            FIXTURE.set(given);
            if (given != null) {
                ctx.log("Testwelt aus " + given.world() + ", Erwartung " + given.expected().getFileName());
                return StepResult.pass();
            }
            state.put("phase", "create");
        }
        String phase = (String) state.get("phase");
        switch (phase) {
            case "create" -> {
                StepResult created = DrehweltSelfTests.createFlatWorld(sub(ctx, state, "createTick"), SOURCE_WORLD);
                if (created.status() == StepResult.Status.FAIL) {
                    return created;
                }
                if (created.status() == StepResult.Status.PASS) {
                    state.put("phase", "populate");
                    state.put(FUTURE, ctx.onServer(MigrationSelfTests::populate));
                }
                return StepResult.pending();
            }
            case "populate" -> {
                StepResult waiting = DrehweltSelfTests.future(ctx);
                if (waiting != null) {
                    return waiting;
                }
                state.put("phase", "settle");
                state.put("settleFrom", ctx.tick());
                return StepResult.pending();
            }
            case "settle" -> {
                if (ctx.tick() - (int) state.get("settleFrom") < 60) {
                    return StepResult.pending();
                }
                AtomicReference<JsonObject> captured = new AtomicReference<>();
                state.put("captured", captured);
                state.put("phase", "capture");
                state.put(FUTURE, ctx.onServer(server -> captured.set(capture(server, null))));
                return StepResult.pending();
            }
            case "capture" -> {
                StepResult waiting = DrehweltSelfTests.future(ctx);
                if (waiting != null) {
                    return waiting;
                }
                @SuppressWarnings("unchecked")
                JsonObject expected = ((AtomicReference<JsonObject>) state.get("captured")).get();
                try {
                    Path dir = outDir().resolve("generated");
                    deleteTree(dir);
                    Files.createDirectories(dir);
                    expected.add("clientConfigs", generatedConfigs(dir.resolve("config").resolve(Constants.MOD_ID)));
                    expected.getAsJsonObject("world").addProperty("folder", GENERATED_WORLD);
                    Files.writeString(dir.resolve("expected.json"), GSON.toJson(expected), StandardCharsets.UTF_8);
                    state.put("dir", dir);
                } catch (IOException e) {
                    return StepResult.fail("Erwartung nicht geschrieben: " + e);
                }
                state.put("phase", "leave");
                state.put("leaveFrom", ctx.tick());
                return StepResult.pending();
            }
            case "leave" -> {
                StepResult left = DrehweltSelfTests.leaveWorld(sub(ctx, state, "leaveTick"), "Propwash-Selbsttest Migration Quelle gespeichert");
                if (left.status() != StepResult.Status.PASS) {
                    return left;
                }
                Path dir = (Path) state.get("dir");
                Minecraft mc = Minecraft.getInstance();
                try {
                    Path zip = dir.resolve("world.zip");
                    DrehweltSelfTests.zipWorld(mc.getLevelSource().getBaseDir().resolve(SOURCE_WORLD), null, zip);
                    Fixture generated = new Fixture(zip, dir.resolve("expected.json"), dir.resolve("config").resolve(Constants.MOD_ID), "erzeugt", true);
                    FIXTURE.set(generated);
                    ctx.log("Testwelt erzeugt: " + Services.PLATFORM.getGameDir().relativize(dir) + " (world.zip, expected.json, config/)");
                    return StepResult.pass();
                } catch (IOException e) {
                    return StepResult.fail("erzeugte Testwelt nicht gepackt: " + e);
                }
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
    }

    private static SelfTest.StepContext sub(SelfTest.StepContext ctx, Map<String, Object> state, String key) {
        int start = (int) state.computeIfAbsent(key, k -> ctx.tick());
        Map<String, Object> inner = cast(state.computeIfAbsent(key + ".state", k -> SelfTest.newState()));
        return new SelfTest.StepContext() {
            @Override
            public int tick() {
                return ctx.tick() - start;
            }

            @Override
            public @Nullable MinecraftServer server() {
                return ctx.server();
            }

            @Override
            public CompletableFuture<Void> onServer(Consumer<MinecraftServer> action) {
                return ctx.onServer(action);
            }

            @Override
            public Map<String, Object> state() {
                return inner;
            }

            @Override
            public void log(String message) {
                ctx.log(message);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private static void populate(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        BlockPos spawn = level.getRespawnData().pos();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        int x = spawn.getX();
        int z = spawn.getZ() + 6;
        DroneBuild[] builds = {DroneBuild.FREESTYLE, DroneBuild.WHOOP, DroneBuild.RACER, DroneBuild.LONG_RANGE};
        for (int i = 0; i < builds.length; i++) {
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            drone.snapTo(x - 6 + i * 3 + 0.5, ground, z + 0.5, 0.0F, 0.0F);
            drone.setAttitude(new Quaternionf());
            drone.setBuild(builds[i]);
            drone.setBatteryCharge(0.25F + i * 0.2F);
            if (i == 0) {
                drone.setCustomName(Component.literal("Migration Lack"));
                drone.setPaint(DronePaint.DEFAULT.withAllProps(0xFF7A00));
            }
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("Drohne " + i + " nicht gespawnt");
            }
        }
        List<GateEntity> gates = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            gates.add(GateStackSelfTests.place(player, GateSize.MEDIUM, x + 10, ground, z + 4 + i * 8, 0.0F));
        }
        gates.add(GateStackSelfTests.stacked(player, gates.getFirst(), GateSize.SMALL, 0.0F));
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : gates.subList(0, 3)) {
            CourseToolItem.useOnGate(tool, player, gate);
        }
        Optional<RuleSpec> gravity = WorldRuleSpecs.byName("gravity_scale");
        Optional<RuleSpec> wind = WorldRuleSpecs.byName("wind");
        gravity.ifPresent(spec -> WorldRules.set(server, spec, 130));
        wind.ifPresent(spec -> WorldRules.set(server, spec, 2));
        player.getInventory().setItem(0, new ItemStack(RaceModule.GATE_SMALL.get(), 3));
        Constants.LOG.info("[Selbsttest] Migration: Testwelt mit {} Drohnen, {} Gates (Kurs {}) und geänderten Welt-Regeln erzeugt", builds.length,
                gates.size(), CourseToolItem.courseOf(tool));
    }

    private static JsonObject generatedConfigs(Path target) throws IOException {
        Files.createDirectories(target);
        Path live = Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID);
        JsonObject files = new JsonObject();
        for (String name : List.of("osd", "race", "hints")) {
            Path file = live.resolve(name + ".json");
            if (Files.isRegularFile(file)) {
                Files.copy(file, target.resolve(name + ".json"), StandardCopyOption.REPLACE_EXISTING);
                files.add(name + ".json", parse(Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        InputConfig input = GSON.fromJson(GSON.toJson(InputConfig.CONFIG.get()), InputConfig.class);
        DeviceProfile profile = new DeviceProfile();
        profile.name = "Migration Radio";
        Map<SwitchFunction, SwitchAssignment> bindings = new LinkedHashMap<>();
        bindings.put(SwitchFunction.ARM, assignment(SwitchSource.AXIS, 4, 0.4F, 1.2F));
        bindings.put(SwitchFunction.ANGLE, assignment(SwitchSource.AXIS, 5, -1.2F, -0.4F));
        bindings.put(SwitchFunction.HORIZON, assignment(SwitchSource.AXIS, 5, -0.2F, 0.2F));
        bindings.put(SwitchFunction.valueOf("COMMAND_8"), assignment(SwitchSource.BUTTON, 107, 0.5F, 1.0F));
        bindings.put(SwitchFunction.valueOf("TURTLE"), assignment(SwitchSource.BUTTON, 31, 0.5F, 1.0F));
        JsonArray bound = new JsonArray();
        long mask = 0L;
        int highest = 0;
        for (Map.Entry<SwitchFunction, SwitchAssignment> entry : bindings.entrySet()) {
            profile.switches.put(DeviceProfile.key(entry.getKey()), entry.getValue());
            bound.add(entry.getKey().name() + "(" + entry.getKey().ordinal() + ")=" + entry.getValue().source.name() + ":" + entry.getValue().index);
            mask |= 1L << entry.getKey().ordinal();
            highest = Math.max(highest, entry.getKey().ordinal());
        }
        String guid = "03000000migration0radio000000000";
        input.profiles.put(guid, profile);
        String json = GSON.toJson(input);
        Files.writeString(target.resolve("input.json"), json, StandardCharsets.UTF_8);
        files.add("input.json", parse(json));
        JsonObject device = new JsonObject();
        device.addProperty("guid", guid);
        device.addProperty("name", profile.name);
        device.add("bound", bound);
        device.addProperty("functionMask", mask);
        device.addProperty("highestOrdinal", highest);
        JsonObject checks = new JsonObject();
        checks.add("deviceProfile", device);
        JsonObject out = new JsonObject();
        out.add("files", files);
        out.add("checks", checks);
        return out;
    }

    private static SwitchAssignment assignment(SwitchSource source, int index, float low, float high) {
        SwitchAssignment assignment = new SwitchAssignment();
        assignment.source = source;
        assignment.index = index;
        assignment.low = low;
        assignment.high = high;
        return assignment;
    }

    private static StepResult prepareStep(SelfTest.StepContext ctx) {
        MigrationLog.begin("migration: Welt-Kopie und Erwartungsdatei bereitstellen");
        Fixture fixture = FIXTURE.get();
        if (fixture == null) {
            return StepResult.fail("keine Testwelt (weder -PpropwashFixtureWorld noch erzeugt)");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return StepResult.fail("noch in einer Welt");
        }
        try {
            if (!Files.isRegularFile(fixture.expected())) {
                return StepResult.fail("Erwartungsdatei fehlt neben der Welt: " + fixture.expected());
            }
            JsonObject expected = parse(Files.readString(fixture.expected(), StandardCharsets.UTF_8)).getAsJsonObject();
            EXPECTED.set(expected);
            FIRST.set(null);
            Path saves = mc.getLevelSource().getBaseDir();
            String folder = fixture.generated() ? GENERATED_WORLD : folderOf(fixture, expected);
            Path target = saves.resolve(folder);
            deleteTree(target);
            int files;
            if (Files.isDirectory(fixture.world())) {
                files = copyTree(fixture.world(), target);
            } else {
                files = unzip(fixture.world(), target);
            }
            if (!Files.isRegularFile(target.resolve("level.dat"))) {
                return StepResult.fail("Welt-Kopie ohne level.dat: " + target);
            }
            Files.writeString(outDir().resolve("world-name.txt"), folder, StandardCharsets.UTF_8);
            ctx.log(files + " Dateien nach saves/" + folder + " kopiert, Erwartung " + fixture.expected().getFileName() + " (Format "
                    + text(expected, "format") + ", Stand " + text(expected, "stand") + ")");
            return StepResult.pass();
        } catch (IOException | RuntimeException e) {
            return StepResult.fail("Testwelt nicht bereitgestellt: " + e);
        }
    }

    private static String folderOf(Fixture fixture, JsonObject expected) {
        JsonElement world = expected.get("world");
        if (world != null && world.isJsonObject() && world.getAsJsonObject().has("folder")) {
            return world.getAsJsonObject().get("folder").getAsString();
        }
        return SelfTestRunner.WORLD_NAME + " Migration " + fixture.label();
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "?" : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private static StepResult openStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            if (mc.level != null) {
                return StepResult.fail("noch in einer Welt");
            }
            String folder;
            try {
                folder = Files.readString(outDir().resolve("world-name.txt"), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                return StepResult.fail("keine bereitgestellte Welt: " + e);
            }
            state.put("folder", folder);
            mc.createWorldOpenFlows().openWorld(folder, () -> mc.gui.setScreen(new TitleScreen()));
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen != null && ctx.tick() % 20 == 0) {
            if (!press(screen, Component.translatable("selectWorld.backupJoinSkipButton").getString())
                    && screen instanceof ConfirmScreen) {
                press(screen, CommonComponents.GUI_YES.getString());
            }
        }
        if (mc.player != null && mc.level != null && screen == null && mc.getSingleplayerServer() != null) {
            state.putIfAbsent("ready", ctx.tick());
            if (ctx.tick() - (int) state.get("ready") > 60) {
                ctx.log("saves/" + state.get("folder") + " geöffnet als " + mc.player.getGameProfile().name());
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    private static boolean press(Screen screen, String text) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && text.equals(button.getMessage().getString())) {
                button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                return true;
            }
        }
        return false;
    }

    private static StepResult compareStep(SelfTest.StepContext ctx, int round) {
        JsonObject expected = EXPECTED.get();
        if (expected == null) {
            return StepResult.fail("keine Erwartung geladen");
        }
        Map<String, Object> state = ctx.state();
        if (ctx.server() == null) {
            return StepResult.fail("keine Welt geöffnet");
        }
        if (!state.containsKey("forced")) {
            if (round == 1) {
                SHARES.set(null);
            }
            state.put("forced", ctx.onServer(server -> force(server, expected, true)));
            return StepResult.pending();
        }
        if (!state.containsKey(FUTURE)) {
            if (!((CompletableFuture<?>) state.get("forced")).isDone()) {
                return StepResult.pending();
            }
            AtomicReference<String> missing = cast(state.computeIfAbsent("missing", k -> new AtomicReference<String>()));
            if (ctx.tick() % 10 == 0) {
                ctx.onServer(server -> missing.set(missingEntities(server, expected)));
            }
            String gone = missing.get();
            if (gone == null || !gone.isEmpty() && ctx.tick() < 400) {
                return StepResult.pending();
            }
            AtomicReference<JsonObject> actual = new AtomicReference<>();
            state.put("actual", actual);
            state.put(FUTURE, ctx.onServer(server -> {
                actual.set(capture(server, expected));
                if (round == 1) {
                    SHARES.set(MigrationValues.shareCodes(server, expected, MigrationValues.GAME_SHARE));
                }
                force(server, expected, false);
            }));
            return StepResult.pending();
        }
        StepResult waiting = DrehweltSelfTests.future(ctx);
        if (waiting != null) {
            return waiting;
        }
        JsonObject actual = MigrationSelfTests.<AtomicReference<JsonObject>>cast(state.get("actual")).get();
        Minecraft mc = Minecraft.getInstance();
        Diff diff = new Diff(jmpLoaded());
        JsonObject owner = expected.has("owner") ? expected.getAsJsonObject("owner") : null;
        if (owner != null && mc.player != null && !owner.get("uuid").getAsString().equals(mc.player.getUUID().toString())) {
            diff.fail("owner", "Spieler " + mc.player.getGameProfile().name() + " statt " + owner.get("name").getAsString()
                    + " (-PpropwashDevName=" + owner.get("name").getAsString() + ")");
        }
        compare("", expected, actual, diff);
        JsonObject first = FIRST.get();
        if (round == 1) {
            FIRST.set(actual);
        } else if (first != null) {
            Diff again = new Diff(diff.jmp);
            compare("", first, actual, again);
            for (String line : again.failures) {
                diff.fail("neu geladen", line);
            }
        }
        try {
            Path dir = outDir();
            Files.writeString(dir.resolve("actual-" + round + ".json"), GSON.toJson(actual), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("diff-" + round + ".txt"), diff.report(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return StepResult.fail("Vergleich nicht geschrieben: " + e);
        }
        ctx.log(diff.summary());
        if (!diff.failures.isEmpty()) {
            return StepResult.fail(diff.failures.size() + " Abweichungen, die ersten: " + String.join("; ", diff.failures.subList(0,
                    Math.min(6, diff.failures.size()))) + " (" + DIR + "/diff-" + round + ".txt)");
        }
        return StepResult.pass();
    }

    private static StepResult shareStep(SelfTest.StepContext ctx) {
        MigrationValues.ShareReport report = SHARES.get();
        if (report == null) {
            return StepResult.fail("kein Ergebnis aus dem Schritt „alle Werte wie in der Erwartungsdatei“");
        }
        try {
            Files.writeString(outDir().resolve("sharecodes.txt"), report.report(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return StepResult.fail("sharecodes.txt nicht geschrieben: " + e);
        }
        ctx.log(report.summary());
        if (!report.failures().isEmpty()) {
            return StepResult.fail(report.failures().size() + " Abweichungen, die ersten: " + String.join("; ", report.failures().subList(0,
                    Math.min(4, report.failures().size()))) + " (" + DIR + "/sharecodes.txt)");
        }
        return StepResult.check(report.drones() > 0, "keine Drohne exportiert");
    }

    private static StepResult rxStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Welt geöffnet");
            }
            RX_PLACED.clear();
            if (!BuiltInRegistries.BLOCK.containsKey(Constants.id("rx_block"))) {
                ctx.log("RX-Block fehlt (Strang rx), nichts gesetzt");
                return StepResult.pass();
            }
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = player.level();
                BlockPos feet = player.blockPosition();
                for (BlockPos pos : List.of(feet.offset(3, 0, 3), feet.offset(-3, 0, 3))) {
                    level.setBlock(pos, RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
                    if (RX_PLACED.isEmpty() && level.getBlockEntity(pos) instanceof RxBlockEntity entity) {
                        entity.apply(new RxSettings(player.getUUID(), RxSource.GAS, -1.0F, 1.0F, RxLink.MAX_RANGE / 2));
                    }
                    if (level.getBlockState(pos).getBlock() instanceof RxBlock) {
                        RX_PLACED.add(pos.immutable());
                    }
                }
            }));
            return StepResult.pending();
        }
        StepResult waiting = DrehweltSelfTests.future(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (RX_PLACED.size() != 2) {
            return StepResult.fail("nur " + RX_PLACED.size() + " von 2 RX-Blöcken gesetzt");
        }
        ctx.log("RX-Blöcke bei " + RX_PLACED + " (der erste an " + "den Spieler gebunden, Kanal Gas)");
        return StepResult.pass();
    }

    private static StepResult backStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return ctx.tick() > 200 ? StepResult.fail("Welt noch offen") : StepResult.pending();
        }
        Fixture fixture = FIXTURE.get();
        JsonObject expected = EXPECTED.get();
        if (fixture == null || expected == null) {
            return StepResult.fail("keine Testwelt geladen");
        }
        try {
            Path dir = outDir().resolve(BACK);
            deleteTree(dir);
            Files.createDirectories(dir);
            String folder = Files.readString(outDir().resolve("world-name.txt"), StandardCharsets.UTF_8).trim();
            Path world = mc.getLevelSource().getBaseDir().resolve(folder);
            int files = DrehweltSelfTests.zipWorld(world, null, dir.resolve("world.zip"));
            JsonArray rx = new JsonArray();
            for (BlockPos pos : RX_PLACED) {
                JsonObject entry = new JsonObject();
                entry.addProperty("x", pos.getX());
                entry.addProperty("y", pos.getY());
                entry.addProperty("z", pos.getZ());
                rx.add(entry);
            }
            JsonObject info = new JsonObject();
            info.addProperty("stand", Services.PLATFORM.getModVersion());
            info.addProperty("fixture", fixture.label());
            info.addProperty("folder", folder);
            info.addProperty("owner", expected.has("owner") ? expected.getAsJsonObject("owner").get("name").getAsString() : "");
            info.add("rxBlocks", rx);
            JsonObject configs = expected.has("clientConfigs") ? expected.getAsJsonObject("clientConfigs") : new JsonObject();
            JsonObject checks = configs.has("checks") ? configs.getAsJsonObject("checks") : new JsonObject();
            if (checks.has("deviceProfile")) {
                info.add("deviceProfile", checks.get("deviceProfile"));
            }
            int written = 0;
            List<String> unread = new ArrayList<>();
            if (fixture.configs() != null) {
                Path target = dir.resolve("config").resolve(Constants.MOD_ID);
                Files.createDirectories(target);
                try (Stream<Path> list = Files.list(fixture.configs())) {
                    for (Path file : list.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList()) {
                        String name = file.getFileName().toString().replace(".json", "");
                        String holder = CONFIG_HOLDERS.get(name);
                        if (holder == null) {
                            unread.add(file.getFileName().toString());
                            continue;
                        }
                        Object value = readConfig(holder, file);
                        Files.writeString(target.resolve(file.getFileName()), CONFIG_GSON.toJson(value), StandardCharsets.UTF_8);
                        written++;
                    }
                }
            }
            info.addProperty("configs", written);
            Files.writeString(dir.resolve("back.json"), GSON.toJson(info), StandardCharsets.UTF_8);
            ctx.log(files + " Dateien in " + Services.PLATFORM.getGameDir().relativize(dir.resolve("world.zip")) + ", " + RX_PLACED.size()
                    + " RX-Blöcke, " + written + " Configs mit dem heutigen Code gelesen und gespeichert"
                    + (unread.isEmpty() ? "" : ", ohne Klasse kopiert nicht: " + unread));
            return StepResult.pass();
        } catch (IOException | ReflectiveOperationException | RuntimeException e) {
            return StepResult.fail("Ausgabe für den Rückweg nicht geschrieben: " + e);
        }
    }

    private static StepResult logStep(SelfTest.StepContext ctx) {
        MigrationLog.Report report = MigrationLog.finish(!jmpLoaded());
        if (report != null) {
            try {
                Files.writeString(outDir().resolve(MigrationLog.FILE), report.report(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return StepResult.fail(MigrationLog.FILE + " nicht geschrieben: " + e);
            }
            ctx.log(report.summary());
        }
        return MigrationLog.result(report);
    }

    private static StepResult defaultsStep(SelfTest.StepContext ctx) {
        JsonObject expected = EXPECTED.get();
        Fixture fixture = FIXTURE.get();
        if (expected == null || fixture == null) {
            return StepResult.fail("keine Erwartung geladen");
        }
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Welt geöffnet");
            }
            AtomicReference<String> rule = new AtomicReference<>();
            List<String> ruleFailures = new CopyOnWriteArrayList<>();
            state.put("rule", rule);
            state.put("ruleFailures", ruleFailures);
            state.put(FUTURE, ctx.onServer(server -> rule.set(MigrationValues.freecamOff(server, expected, ruleFailures))));
            return StepResult.pending();
        }
        StepResult waiting = DrehweltSelfTests.future(ctx);
        if (waiting != null) {
            return waiting;
        }
        List<String> ok = new ArrayList<>();
        List<String> open = new ArrayList<>();
        List<String> failures = new ArrayList<>(MigrationSelfTests.<List<String>>cast(state.get("ruleFailures")));
        String rule = MigrationSelfTests.<AtomicReference<String>>cast(state.get("rule")).get();
        String client = freecamClient(Minecraft.getInstance(), failures);
        if (failures.isEmpty()) {
            ok.add("allow_freecam aus: " + rule + ", " + client);
        }
        int angles = 0;
        JsonObject first = FIRST.get();
        if (first != null && expected.has("drones")) {
            Map<String, JsonElement> now = new LinkedHashMap<>();
            for (JsonElement drone : first.getAsJsonArray("drones")) {
                now.put(drone.getAsJsonObject().get("uuid").getAsString(), drone);
            }
            for (JsonElement drone : expected.getAsJsonArray("drones")) {
                JsonObject before = drone.getAsJsonObject();
                if (!before.has("cameraUptiltDeg") || needsJmp(before) && !jmpLoaded()) {
                    continue;
                }
                JsonElement after = now.get(before.get("uuid").getAsString());
                JsonElement angle = after == null ? null : after.getAsJsonObject().get("cameraUptiltDeg");
                if (angle == null || !same(before.getAsJsonPrimitive("cameraUptiltDeg"), angle.getAsJsonPrimitive(), false)) {
                    failures.add("Kamerawinkel von " + before.get("uuid").getAsString() + ": " + before.get("cameraUptiltDeg") + " → " + angle);
                }
                angles++;
            }
        }
        ok.add("Kamerawinkel unverändert bei " + angles + " Drohnen");
        JsonObject configs = expected.has("clientConfigs") ? expected.getAsJsonObject("clientConfigs") : null;
        if (configs == null || fixture.configs() == null) {
            open.add("keine Client-Configs neben der Testwelt");
        } else {
            configChecks(fixture.configs(), configs, text(expected, "stand"), jmpLoaded(), ok, open, failures);
        }
        ctx.log("geprüft: " + String.join("; ", ok));
        if (!open.isEmpty()) {
            ctx.log("offen: " + String.join("; ", open));
        }
        try {
            Files.writeString(outDir().resolve("defaults.txt"), "geprüft:\n" + String.join("\n", ok) + "\n\noffen:\n" + String.join("\n", open)
                    + "\n\nAbweichungen:\n" + String.join("\n", failures) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            return StepResult.fail("defaults.txt nicht geschrieben: " + e);
        }
        return failures.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", failures.subList(0, Math.min(6, failures.size()))));
    }

    static String freecamClient(Minecraft mc, List<String> failures) {
        ClientWorldRules rules = ClientWorldRules.INSTANCE;
        if (!rules.known()) {
            failures.add("Client kennt die Welt-Regeln nicht");
            return "Regeln beim Client unbekannt";
        }
        if (rules.on(WorldRuleSpecs.ALLOW_FREECAM)) {
            failures.add("Client sieht " + MigrationValues.FREECAM_RULE + " an");
        }
        FreecamRules.Denial denial = FreecamSession.INSTANCE.tryStart(mc);
        if (FreecamSession.active()) {
            FreecamSession.INSTANCE.end(mc, FreecamRules.End.KEY);
            failures.add("Freecam startet trotz Regel aus");
        } else if (denial != FreecamRules.Denial.RULE_OFF) {
            failures.add("Freecam abgelehnt mit " + denial + " statt RULE_OFF");
        }
        return "Client: Regel aus, Freecam-Start abgelehnt mit " + denial;
    }

    static void configChecks(Path dir, JsonObject configs, String stand, boolean jmp, List<String> ok, List<String> open, List<String> failures) {
        JsonObject files = configs.has("files") ? configs.getAsJsonObject("files") : new JsonObject();
        Map<String, Object> loaded = new LinkedHashMap<>();
        Diff diff = new Diff(jmp);
        List<String> upgrades = new ArrayList<>();
        for (Map.Entry<String, JsonElement> file : files.entrySet()) {
            String name = file.getKey().replace(".json", "");
            String holder = CONFIG_HOLDERS.get(name);
            Path path = dir.resolve(file.getKey());
            if (holder == null || !Files.isRegularFile(path)) {
                open.add("Config " + file.getKey() + " nicht geprüft (" + (holder == null ? "keine Klasse bekannt" : "Datei fehlt") + ")");
                continue;
            }
            try {
                Object value = readConfig(holder, path);
                loaded.put(name, value);
                JsonElement now = parse(GSON.toJson(value));
                Diff one = new Diff(diff.jmp);
                JsonElement before = upgrade(name, file.getValue(), upgrades);
                compare(file.getKey(), before, now, one);
                for (String line : one.failures) {
                    String key = line.substring(0, line.indexOf(':'));
                    if (!CONFIG_VERSION_KEYS.contains(key)) {
                        failures.add("Config " + line);
                    }
                }
                diff.compared += one.compared;
            } catch (ReflectiveOperationException | IOException | RuntimeException e) {
                failures.add("Config " + file.getKey() + " nicht lesbar: " + e);
            }
        }
        ok.add(loaded.size() + " Client-Configs mit dem heutigen Code gelesen, " + diff.compared + " Werte wie vorher");
        if (!upgrades.isEmpty()) {
            ok.add("bewusste Umzüge seit " + stand + " erwartet: " + String.join(", ", upgrades));
        }
        JsonObject checks = configs.has("checks") ? configs.getAsJsonObject("checks") : new JsonObject();
        Object osdConfig = loaded.get("osd");
        JsonObject osd = osdConfig == null ? new JsonObject() : parse(GSON.toJson(osdConfig)).getAsJsonObject();
        JsonObject elements = osd.has("elements") ? osd.getAsJsonObject("elements") : new JsonObject();
        if (checks.has("osd") && osdConfig != null) {
            for (Map.Entry<String, String> check : Map.of("craftName", "craft_name", "batteryVoltage", "battery_voltage").entrySet()) {
                JsonElement want = checks.getAsJsonObject("osd").get(check.getKey());
                JsonObject element = elements.has(check.getValue()) ? elements.getAsJsonObject(check.getValue()) : null;
                String have = element == null ? "fehlt" : element.get("col").getAsInt() + "," + element.get("row").getAsInt();
                if (want != null && !want.getAsString().equals(have)) {
                    failures.add("OSD " + check.getValue() + " " + want.getAsString() + " → " + have);
                }
            }
            ok.add("eigenes OSD-Layout erhalten");
        }
        if (osdConfig == null) {
            open.add("Flugbahn-Marker nicht geprüft (keine osd.json der Testwelt gelesen)");
        } else if (OsdElement.byKey("flight_path_marker") == null) {
            open.add("Flugbahn-Marker fehlt noch (Strang fpm)");
        } else {
            JsonObject marker = elements.has("flight_path_marker") ? elements.getAsJsonObject("flight_path_marker") : null;
            if (marker != null && marker.has("enabled") && marker.get("enabled").getAsBoolean()) {
                failures.add("Flugbahn-Marker nach dem Umstieg an");
            } else {
                ok.add("Marker aus");
            }
        }
        if (checks.has("deviceProfile") && loaded.get("input") instanceof InputConfig input) {
            switchChecks(input, checks.getAsJsonObject("deviceProfile"), stand, ok, failures);
        } else {
            open.add("kein Geräteprofil in der Testwelt");
        }
    }

    static boolean older(String stand, String than) {
        int[] a = numbers(stand);
        int[] b = numbers(than);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    private static int[] numbers(String stand) {
        String core = stand.split("[-+]")[0];
        String[] parts = core.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    static JsonElement upgrade(String name, JsonElement old, List<String> upgrades) {
        if (!old.isJsonObject()) {
            return old;
        }
        JsonObject copy = old.getAsJsonObject().deepCopy();
        switch (name) {
            case "input" -> {
                JsonObject mouse = copy.has(InputConfig.LEGACY_MOUSE_BLOCK) && copy.get(InputConfig.LEGACY_MOUSE_BLOCK).isJsonObject()
                        ? copy.getAsJsonObject(InputConfig.LEGACY_MOUSE_BLOCK) : null;
                JsonElement legacy = mouse == null ? null : mouse.remove(InputConfig.LEGACY_MOUSE_INVERT_KEY);
                if (legacy != null) {
                    if (legacy.isJsonPrimitive() && legacy.getAsBoolean() && !copy.has(InputConfig.INVERT_PITCH_KEY)) {
                        copy.addProperty(InputConfig.INVERT_PITCH_KEY, true);
                    }
                    upgrades.add("input mouse.invertY → " + InputConfig.INVERT_PITCH_KEY);
                }
                JsonObject profiles = copy.has("profiles") && copy.get("profiles").isJsonObject() ? copy.getAsJsonObject("profiles") : new JsonObject();
                for (Map.Entry<String, JsonElement> profile : profiles.entrySet()) {
                    JsonElement switches = profile.getValue().isJsonObject() ? profile.getValue().getAsJsonObject().get("switches") : null;
                    if (switches == null || !switches.isJsonObject()) {
                        continue;
                    }
                    for (Map.Entry<String, SwitchFunction> renamed : DeviceProfile.RENAMED.entrySet()) {
                        JsonElement binding = switches.getAsJsonObject().remove(renamed.getKey());
                        if (binding != null) {
                            switches.getAsJsonObject().add(DeviceProfile.key(renamed.getValue()), binding);
                            upgrades.add("Schalter " + renamed.getKey() + " → " + DeviceProfile.key(renamed.getValue()));
                        }
                    }
                }
            }
            case "action_cam" -> {
                JsonElement density = copy.get("flashbackDensity");
                if (density != null && density.isJsonPrimitive() && !copy.has("flashbackSimplify")) {
                    KeyframeDensity value = KeyframeDensity.valueOf(density.getAsString());
                    copy.addProperty("flashbackSimplify", Simplification.sliderOf(value));
                    copy.add("flashbackDensity", JsonNull.INSTANCE);
                    upgrades.add("action_cam flashbackDensity " + value + " → flashbackSimplify " + Simplification.sliderOf(value));
                }
                if (copy.remove(ACTION_CAM_MEMORY) != null) {
                    upgrades.add("action_cam " + ACTION_CAM_MEMORY + " entfällt (Puffer nach Arbeitsspeicher, seit 0.5.0)");
                }
            }
            case "drone" -> {
                int moved = 0;
                for (String key : DRONE_PHYSICS_TO_RULES) {
                    if (copy.remove(key) != null) {
                        moved++;
                    }
                }
                if (moved > 0) {
                    upgrades.add("drone " + moved + " Physikfelder → Welt-Regeln (seit 0.5.0, alte Werte ignoriert)");
                }
            }
            case "server" -> {
                if (copy.remove(SERVER_PART_WEAR) != null) {
                    upgrades.add("server " + SERVER_PART_WEAR + " → Gamerule crash_damage (seit 0.5.0, alter Wert ignoriert)");
                }
            }
            case "race" -> {
                JsonElement ghost = copy.remove(RACE_GHOST);
                JsonElement record = copy.remove(RACE_RECORD_GHOST);
                if ((ghost != null || record != null) && !copy.has(RACE_GHOST_MODE)) {
                    GhostChoice.Mode mode = GhostChoice.migrate(flag(ghost), flag(record));
                    copy.addProperty(RACE_GHOST_MODE, mode.name());
                    upgrades.add("race " + RACE_GHOST + " " + ghost + ", " + RACE_RECORD_GHOST + " " + record + " → " + RACE_GHOST_MODE + " " + mode
                            + " (seit 0.6.0, ein Geist je Kurs)");
                }
            }
            case "drone_render" -> {
                int version = copy.has("version") ? copy.get("version").getAsInt() : 0;
                JsonElement scale = copy.get("visualScale");
                if (version < DroneRenderConfig.VERSION && scale != null && scale.isJsonPrimitive()
                        && scale.getAsFloat() == DroneRenderConfig.LEGACY_DEFAULT_VISUAL_SCALE) {
                    copy.addProperty("visualScale", DroneRenderConfig.DEFAULT_VISUAL_SCALE);
                    copy.addProperty("sizeNotice", true);
                    upgrades.add("drone_render visualScale " + DroneRenderConfig.LEGACY_DEFAULT_VISUAL_SCALE + " → " + DroneRenderConfig.DEFAULT_VISUAL_SCALE
                            + " mit sizeNotice (alter Standard, Config-Version " + DroneRenderConfig.VERSION + ")");
                }
            }
            case "osd" -> {
                int version = copy.has("version") ? copy.get("version").getAsInt() : 0;
                OsdPreset preset = copy.has("preset") ? OsdPreset.valueOf(copy.get("preset").getAsString()) : null;
                JsonObject elements = copy.has("elements") && copy.get("elements").isJsonObject() ? copy.getAsJsonObject("elements") : null;
                if (version < OSD_PARKING_VERSION && preset != null && elements != null) {
                    int parked = 0;
                    for (Map.Entry<String, JsonElement> entry : elements.entrySet()) {
                        OsdElement element = OsdElement.byKey(entry.getKey());
                        JsonObject slot = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : null;
                        if (element == null || slot == null || !preset.parked(element) || !slot.has("col") || !slot.has("row")
                                || slot.get("col").getAsInt() != element.baseCol() || slot.get("row").getAsInt() != element.baseRow()) {
                            continue;
                        }
                        slot.remove("col");
                        slot.remove("row");
                        parked++;
                    }
                    if (parked > 0) {
                        upgrades.add("osd " + parked + " geparkte Elemente auf den Standardplätzen der Vorlage " + preset
                                + " (Umzug ab OSD-Version " + OSD_PARKING_VERSION + ", Position nicht verglichen)");
                    }
                }
            }
            default -> {
            }
        }
        return copy;
    }

    private static @Nullable Boolean flag(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : null;
    }

    static Object readConfig(String holder, Path path) throws ReflectiveOperationException, IOException {
        int hash = holder.indexOf('#');
        Class<?> owner = Class.forName(holder.substring(0, hash), true, MigrationSelfTests.class.getClassLoader());
        JsonConfig<?> config = (JsonConfig<?>) owner.getField(holder.substring(hash + 1)).get(null);
        return read(config, path);
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(JsonConfig<T> config, Path path) throws ReflectiveOperationException, IOException {
        Class<T> type = (Class<T>) field(config, "type");
        Consumer<com.google.gson.JsonObject> migration = (Consumer<com.google.gson.JsonObject>) field(config, "migration");
        Consumer<T> validator = (Consumer<T>) field(config, "validator");
        T value;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            value = JsonConfig.read(reader, type, migration);
        }
        if (value == null) {
            throw new IOException("leer: " + path.getFileName());
        }
        validator.accept(value);
        return value;
    }

    private static @Nullable Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = JsonConfig.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void switchChecks(InputConfig input, JsonObject device, String stand, List<String> ok, List<String> failures) {
        String guid = device.get("guid").getAsString();
        DeviceProfile profile = input.profile(guid);
        if (profile == null) {
            failures.add("Geräteprofil " + guid + " fehlt nach dem Umstieg");
            return;
        }
        boolean sameBits = !older(stand, SAME_BITS_SINCE);
        long mask = 0L;
        long expectedMask = 0L;
        int highest = 0;
        int expectedHighest = 0;
        int modes = 0;
        List<String> moved = new ArrayList<>();
        for (JsonElement entry : device.getAsJsonArray("bound")) {
            String text = entry.getAsString();
            String name = text.substring(0, text.indexOf('('));
            int ordinal = Integer.parseInt(text.substring(text.indexOf('(') + 1, text.indexOf(')')));
            SwitchFunction function = DeviceProfile.RENAMED.get(name.toLowerCase(Locale.ROOT));
            if (function != null) {
                moved.add(name + " → " + function.name());
            } else {
                try {
                    function = SwitchFunction.valueOf(name);
                } catch (IllegalArgumentException e) {
                    failures.add("Schalter " + name + " gibt es nicht mehr");
                    continue;
                }
            }
            expectedMask |= 1L << (sameBits ? ordinal : function.ordinal());
            expectedHighest = Math.max(expectedHighest, sameBits ? ordinal : function.ordinal());
            if (function.ordinal() != ordinal) {
                if (sameBits) {
                    failures.add("Schalter " + name + " hat Bit " + function.ordinal() + " statt " + ordinal);
                } else {
                    moved.add(name + " Bit " + ordinal + " → " + function.ordinal());
                }
            }
            SwitchAssignment binding = profile.binding(function);
            String source = text.substring(text.indexOf('=') + 1);
            if (binding == null || !(binding.source.name() + ":" + binding.index).equals(source)) {
                failures.add("Schalter " + name + " belegt " + (binding == null ? "nichts" : binding.source.name() + ":" + binding.index) + " statt " + source);
                continue;
            }
            long bit = SwitchEvaluator.bit(function);
            if (bit != 1L << function.ordinal()) {
                failures.add("Schalter " + name + ": Maske " + Long.toHexString(bit) + " statt Bit " + function.ordinal());
            }
            mask |= bit;
            highest = Math.max(highest, function.ordinal());
            if (binding.toggle) {
                modes++;
                continue;
            }
            String toggled = toggles(profile, function, binding);
            if (toggled != null) {
                failures.add("Schalter " + name + " schaltet nicht: " + toggled);
            } else {
                modes++;
            }
        }
        if (sameBits && expectedMask != device.get("functionMask").getAsLong()) {
            failures.add("Erwartungsdatei: Maske " + Long.toHexString(device.get("functionMask").getAsLong()) + " passt nicht zu den Schaltern");
        }
        if (mask != expectedMask) {
            failures.add("Schaltermaske " + Long.toHexString(mask) + " statt " + Long.toHexString(expectedMask));
        }
        if (highest != expectedHighest) {
            failures.add("höchstes Bit " + highest + " statt " + expectedHighest);
        }
        ok.add("Schaltermaske umgestiegen (" + Long.toHexString(mask) + ", höchstes Bit " + highest + ")");
        if (!moved.isEmpty()) {
            ok.add("seit " + stand + " umbenannt oder neu nummeriert (Belegung über den Namen erhalten): " + String.join(", ", moved));
        }
        ok.add("alle " + modes + " belegten Schalter und Modi schalten");
    }

    private static @Nullable String toggles(DeviceProfile profile, SwitchFunction function, SwitchAssignment binding) {
        int axes = 8;
        int buttons = 128;
        for (SwitchAssignment other : profile.switches.values()) {
            if (other.source == SwitchSource.AXIS) {
                axes = Math.max(axes, other.index + 1);
            } else if (other.source == SwitchSource.BUTTON) {
                buttons = Math.max(buttons, other.index + 1);
            }
        }
        float[] axisValues = new float[axes];
        boolean[] buttonValues = new boolean[buttons];
        for (SwitchAssignment other : profile.switches.values()) {
            if (other.source == SwitchSource.BUTTON && other.index >= 0 && other.invert) {
                buttonValues[other.index] = true;
            }
        }
        float on;
        float off;
        if (binding.source == SwitchSource.AXIS) {
            float low = Math.max(-1.0F, binding.low);
            float high = Math.min(1.0F, binding.high);
            on = (low + high) * 0.5F;
            off = on > 0.0F ? -1.0F : 1.0F;
            if (off >= binding.low && off <= binding.high) {
                off = on > 0.0F ? on - 1.5F : on + 1.5F;
            }
        } else {
            on = binding.invert ? 0.0F : 1.0F;
            off = binding.invert ? 1.0F : 0.0F;
        }
        long withOn = evaluate(profile, axisValues, buttonValues, binding, on);
        long withOff = evaluate(profile, axisValues, buttonValues, binding, off);
        long bit = SwitchEvaluator.bit(function);
        if ((withOn & bit) == 0L) {
            return "an bei " + on + " nicht gesetzt (Maske " + Long.toHexString(withOn) + ")";
        }
        if ((withOff & bit) != 0L) {
            return "aus bei " + off + " noch gesetzt (Maske " + Long.toHexString(withOff) + ")";
        }
        return null;
    }

    private static long evaluate(DeviceProfile profile, float[] axes, boolean[] buttons, SwitchAssignment binding, float value) {
        float[] a = axes.clone();
        boolean[] b = buttons.clone();
        if (binding.source == SwitchSource.AXIS) {
            a[binding.index] = value;
        } else {
            b[binding.index] = value > 0.5F;
        }
        SwitchEvaluator evaluator = new SwitchEvaluator();
        long mask = evaluator.evaluate(profile, a, b);
        return mask;
    }

    private static Map<String, String> configHolders() {
        String p = "de.lardtom.propwash.";
        Map<String, String> map = new LinkedHashMap<>();
        map.put("ui", p + "ui.client.UiConfig#CONFIG");
        map.put("video", p + "video.client.VideoSettings#CONFIG");
        map.put("spectate", p + "video.client.SpectateConfig#CONFIG");
        map.put("action_cam", p + "video.client.record.RecordingConfig#CONFIG");
        map.put("core", p + "core.CoreConfig#CONFIG");
        map.put("osd", p + "osd.client.OsdClient#CONFIG");
        map.put("input", p + "input.InputConfig#CONFIG");
        map.put("rescue", p + "rescue.client.RescueConfigs#CONFIG");
        map.put("blackbox", p + "blackbox.client.BlackboxClient#CONFIG");
        map.put("server", p + "server.ServerRulesConfig#CONFIG");
        map.put("drone", p + "drone.client.DroneClientConfig#CONFIG");
        map.put("dust", p + "drone.client.dust.DustConfig#CONFIG");
        map.put("trails", p + "drone.client.trail.TrailConfig#CONFIG");
        map.put("drone_render", p + "drone.client.render.DroneRenderConfig#CONFIG");
        map.put("audio", p + "audio.client.AudioConfig#CONFIG");
        map.put("tricks", p + "trick.client.TrickClient#CONFIG");
        map.put("race", p + "race.client.RaceConfig#CONFIG");
        map.put("hints", p + "ui.client.FirstUseHints#CONFIG");
        map.put("creative_templates", p + "creative.client.CreativeTemplates#CONFIG");
        map.put("tune_presets", p + "ui.client.TunePresetStore#CONFIG");
        return map;
    }
}
