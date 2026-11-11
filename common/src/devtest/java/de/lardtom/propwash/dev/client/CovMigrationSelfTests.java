package de.lardtom.propwash.dev.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.bando.BandoPiece;
import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoSite;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.Mat;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.MigrationValues;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.AddonWorldRules;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovMigrationSelfTests {
    public static final String GROUP = "migration: Abdeckung cov-combo – ";
    public static final String DIR = "cov-combo";
    public static final String BANDO_KEY = "covComboBando";
    private static final String CAM = "camdrones";
    private static final String MOB_DEFENSE = "mobdefense";
    private static final String FUTURE = "future";
    private static final int MARGIN = 4;
    private static final int CANDIDATES = 6;
    private static final int CANDIDATE_STEP = 192;
    private static final String FULL = "minecraft:full";
    private static final double FRAME = 1.0 / 60.0;

    private CovMigrationSelfTests() {
    }

    public static void register() {
        SelfTest.afterLeaveStep(GROUP + "Inhalt der Testwelt (Drohnen mit Namen, Lack, Tuning, Homepoint, Wasserschutz und VTX, Gates, Kurse, "
                + "Bestenlisten, Ghosts, Zonen, Regeln und Geräteprofil)", 20, CovMigrationSelfTests::contentStep);
        SelfTest.afterLeaveStep(GROUP + "umgestiegene Welt wieder öffnen", 2400, CovMigrationSelfTests::openStep);
        SelfTest.afterLeaveStep(GROUP + "Bando aus 0.5.2 unverändert (Blockhash), daneben neu erzeugte Chunks mit der neuen Lage", 2400,
                CovMigrationSelfTests::bandoStep);
        SelfTest.afterLeaveStep(GROUP + "Welt-Regeln des Kamera-Add-ons auf ihren Standardwerten (nur mit dem Jar)", 200, CovMigrationSelfTests::camRulesStep);
        SelfTest.afterLeaveStep(GROUP + "alte Drohnen fliegen gleich (Flug-Fingerabdruck mit und ohne Add-on)", 600, CovMigrationSelfTests::flightStep);
        SelfTest.afterLeaveStep(GROUP + "verlassen", 1200, ctx -> DrehweltSelfTests.leaveWorld(ctx, "Propwash-Selbsttest cov-combo beendet"));
        SelfTest.afterLeaveStep(GROUP + "Log ohne Fehler seit dem Wiederöffnen", 200, CovMigrationSelfTests::logStep);
    }

    static Path outDir() throws IOException {
        Path dir = Services.PLATFORM.getGameDir().resolve(DIR);
        Files.createDirectories(dir);
        return dir;
    }

    private static StepResult contentStep(SelfTest.StepContext ctx) {
        JsonObject expected = MigrationSelfTests.expected();
        if (expected == null) {
            return StepResult.fail("keine Erwartung geladen (Gruppe migration: lief nicht)");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        JsonArray drones = array(expected, "drones");
        counts.put("Drohnen", drones.size());
        int named = 0;
        int painted = 0;
        int tuned = 0;
        int home = 0;
        int waterproof = 0;
        int vtx = 0;
        for (JsonElement element : drones) {
            JsonObject drone = element.getAsJsonObject();
            if (!text(drone, "name").isEmpty()) {
                named++;
            }
            JsonObject nbt = drone.has("propwashNbt") ? drone.getAsJsonObject("propwashNbt") : new JsonObject();
            if (nbt.has("Paint")) {
                painted++;
            }
            if (nbt.has("Tune") && nbt.get("Tune").isJsonObject() && !nbt.getAsJsonObject("Tune").isEmpty()) {
                tuned++;
            }
            if (drone.has("homePoint") && !drone.get("homePoint").isJsonNull()) {
                home++;
            }
            if (drone.has("waterproof") && drone.get("waterproof").getAsBoolean()) {
                waterproof++;
            }
            if (drone.has("vtx") && drone.getAsJsonObject("vtx").has("manual") && drone.getAsJsonObject("vtx").get("manual").isJsonPrimitive()
                    && !drone.getAsJsonObject("vtx").get("manual").getAsString().equals("false")
                    && !drone.getAsJsonObject("vtx").get("manual").getAsString().equals("0")) {
                vtx++;
            }
        }
        counts.put("mit Namen", named);
        counts.put("mit Lack", painted);
        counts.put("mit Tuning", tuned);
        counts.put("mit Homepoint", home);
        counts.put("wasserdicht", waterproof);
        counts.put("mit eigener VTX-Leistung", vtx);
        counts.put("Gates", array(expected, "gates").size());
        JsonArray courses = array(expected, "courses");
        counts.put("Kurse", courses.size());
        int board = 0;
        int ghosts = 0;
        for (JsonElement element : courses) {
            JsonObject course = element.getAsJsonObject();
            if (course.has("leaderboard") && course.getAsJsonObject("leaderboard").has("size")) {
                board += course.getAsJsonObject("leaderboard").get("size").getAsInt();
            }
            ghosts += array(course, "recordGhosts").size();
        }
        counts.put("Bestenlisten-Einträge", board);
        counts.put("Rekord-Geister", ghosts);
        JsonObject zones = expected.has("zones") ? expected.getAsJsonObject("zones") : new JsonObject();
        counts.put("Zonen", array(zones, "zones").size());
        JsonObject rules = expected.has("worldRules") ? expected.getAsJsonObject("worldRules") : new JsonObject();
        counts.put("geänderte Welt-Regeln", array(rules, "changed").size());
        JsonObject configs = expected.has("clientConfigs") ? expected.getAsJsonObject("clientConfigs") : new JsonObject();
        JsonObject checks = configs.has("checks") ? configs.getAsJsonObject("checks") : new JsonObject();
        JsonObject device = checks.has("deviceProfile") ? checks.getAsJsonObject("deviceProfile") : new JsonObject();
        counts.put("belegte Schalter im Geräteprofil", array(device, "bound").size());
        counts.put("Client-Configs", configs.has("files") ? configs.getAsJsonObject("files").size() : 0);
        StringBuilder line = new StringBuilder("Testwelt ").append(text(expected.getAsJsonObject("world"), "folder")).append(" (Stand ")
                .append(text(expected, "stand")).append("):");
        counts.forEach((key, value) -> line.append(' ').append(key).append(' ').append(value).append(','));
        line.setLength(line.length() - 1);
        try {
            Files.writeString(outDir().resolve("inhalt-" + safe(text(expected.getAsJsonObject("world"), "folder")) + ".txt"), line + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            return StepResult.fail("Inhalt nicht geschrieben: " + e);
        }
        ctx.log(line.toString());
        return drones.isEmpty() ? StepResult.fail("Testwelt ohne Drohnen") : StepResult.pass();
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String text(@Nullable JsonObject object, String key) {
        if (object == null) {
            return "";
        }
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private static StepResult openStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            String folder;
            try {
                folder = Files.readString(MigrationSelfTests.outDir().resolve("world-name.txt"), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                return StepResult.fail("keine bereitgestellte Welt: " + e);
            }
            MigrationLog.begin(GROUP + "umgestiegene Welt wieder öffnen");
            ctx.state().put("folder", folder);
        }
        return openWorld(ctx, (String) ctx.state().get("folder"));
    }

    static StepResult openWorld(SelfTest.StepContext ctx, String folder) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            if (mc.level != null) {
                return StepResult.fail("noch in einer Welt");
            }
            mc.createWorldOpenFlows().openWorld(folder, () -> mc.gui.setScreen(new TitleScreen()));
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen != null && ctx.tick() % 20 == 0) {
            if (!press(screen, Component.translatable("selectWorld.backupJoinSkipButton").getString()) && screen instanceof ConfirmScreen) {
                press(screen, CommonComponents.GUI_YES.getString());
            }
        }
        if (mc.player != null && mc.level != null && screen == null && mc.getSingleplayerServer() != null) {
            state.putIfAbsent("ready", ctx.tick());
            if (ctx.tick() - (int) state.get("ready") > 60) {
                ctx.log("saves/" + folder + " geöffnet");
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

    @SuppressWarnings("unchecked")
    private static StepResult onServer(SelfTest.StepContext ctx, java.util.function.Function<MinecraftServer, String> task) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Welt geöffnet");
            }
            AtomicReference<String> result = new AtomicReference<>();
            state.put("result", result);
            state.put(FUTURE, ctx.onServer(server -> result.set(task.apply(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            Throwable error = future.exceptionNow();
            Constants.LOG.error("[Selbsttest] cov-combo: Server-Aufgabe fehlgeschlagen", error);
            return StepResult.fail("Server: " + error);
        }
        String result = ((AtomicReference<String>) state.get("result")).get();
        if (result != null && result.startsWith("OK ")) {
            ctx.log(result.substring(3));
            return StepResult.pass();
        }
        return StepResult.fail(result == null ? "kein Ergebnis" : result);
    }

    static String hash(ServerLevel level, int[] r, int[] solid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int count = 0;
            for (int x = r[0]; x <= r[3]; x++) {
                for (int z = r[2]; z <= r[5]; z++) {
                    for (int y = r[1]; y <= r[4]; y++) {
                        BlockState state = level.getBlockState(new BlockPos(x, y, z));
                        if (!state.isAir()) {
                            count++;
                        }
                        digest.update(((x - r[0]) + "," + (y - r[1]) + "," + (z - r[2]) + "=" + BlockStateParser.serialize(state) + "\n")
                                .getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            solid[0] = count;
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean newPlacement(BandoPlan plan, int x, int z) {
        return BandoSite.underpinned(plan, x, z) && plan.fill(x, z) != Mat.EMPTY;
    }

    static int pit(ServerLevel level, BandoPlan plan, BandoTransform t, int ground) {
        int dug = 0;
        for (int z = 0; z < plan.sizeZ(); z++) {
            for (int x = 0; x < plan.sizeX(); x++) {
                int wx = t.worldX(x, z);
                int wz = t.worldZ(x, z);
                for (int y = level.getMinY() + 1; y <= ground; y++) {
                    BlockPos pos = new BlockPos(wx, y, wz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        dug++;
                    }
                }
            }
        }
        return dug;
    }

    static @Nullable String savedStatus(MinecraftServer server, int cx, int cz) throws IOException {
        return CovRegionFiles.status(server.getWorldPath(LevelResource.ROOT), cx, cz);
    }

    private static StepResult bandoStep(SelfTest.StepContext ctx) {
        JsonObject expected = MigrationSelfTests.expected();
        if (expected == null) {
            return StepResult.fail("keine Erwartung geladen");
        }
        JsonObject disk = expected.has("disk") && expected.get("disk").isJsonObject() ? expected.getAsJsonObject("disk") : new JsonObject();
        if (!disk.has(BANDO_KEY)) {
            ctx.log("Testwelt ohne Bando aus 0.5.2 (disk." + BANDO_KEY + " fehlt), nichts zu prüfen");
            return StepResult.pass();
        }
        JsonObject bando = disk.getAsJsonObject(BANDO_KEY);
        return onServer(ctx, server -> bando(server, bando));
    }

    private static int[] ints(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsInt();
        }
        return out;
    }

    private static String bando(MinecraftServer server, JsonObject bando) {
        ServerLevel level = server.overworld();
        BandoVariant variant = BandoVariant.valueOf(bando.get("variant").getAsString().toUpperCase(Locale.ROOT));
        long seed = bando.get("seed").getAsLong();
        int turns = bando.get("turns").getAsInt();
        int[] region = ints(bando, "region");
        int[] center = ints(bando, "center");
        int[] centerNew = ints(bando, "centerNew");
        int groundOld = bando.get("ground").getAsInt();
        for (int x = region[0] >> 4; x <= region[3] >> 4; x++) {
            for (int z = region[2] >> 4; z <= region[5] >> 4; z++) {
                try {
                    String status = savedStatus(server, x, z);
                    if (!FULL.equals(status)) {
                        return "Chunk " + x + "," + z + " des alten Bandos ist nicht als fertig gespeichert (" + status + ")";
                    }
                } catch (IOException e) {
                    return "Region nicht gelesen: " + e;
                }
                level.getChunk(x, z);
            }
        }
        int[] solid = new int[1];
        String hashOld = hash(level, region, solid);
        String want = bando.get("hash").getAsString();
        List<String> problems = new ArrayList<>();
        if (!hashOld.equals(want)) {
            problems.add("Blockhash des alten Bandos " + hashOld + " statt " + want + " (" + solid[0] + " feste Blöcke, in 0.5.2 "
                    + bando.get("solid").getAsInt() + ")");
        }
        BandoPlan plan = BandoGenerator.generate(variant, seed);
        BandoTransform oldT = BandoTransform.centered(plan, turns, center[0], groundOld - plan.ground(), center[1]);
        int bx = Integer.MIN_VALUE;
        int bz = 0;
        List<String> skipped = new ArrayList<>();
        for (int attempt = 0; attempt < CANDIDATES && bx == Integer.MIN_VALUE; attempt++) {
            int cx = centerNew[0] + attempt * CANDIDATE_STEP;
            int cz = centerNew[1];
            BandoTransform probe = BandoTransform.centered(plan, turns, cx, 0, cz);
            int c0x = (probe.originX() - MARGIN - 16) >> 4;
            int c1x = (probe.originX() + probe.widthX() + MARGIN + 16) >> 4;
            int c0z = (probe.originZ() - MARGIN - 16) >> 4;
            int c1z = (probe.originZ() + probe.widthZ() + MARGIN + 16) >> 4;
            String generated = null;
            for (int x = c0x; x <= c1x && generated == null; x++) {
                for (int z = c0z; z <= c1z && generated == null; z++) {
                    try {
                        String status = savedStatus(server, x, z);
                        if (status != null) {
                            generated = x + "," + z + " (" + status + ")";
                        }
                    } catch (IOException e) {
                        generated = x + "," + z + " (" + e + ")";
                    }
                }
            }
            if (generated != null) {
                skipped.add(cx + "," + cz + ": Chunk " + generated + " schon gespeichert");
                continue;
            }
            boolean clear = true;
            for (int x = c0x; x <= c1x; x++) {
                for (int z = c0z; z <= c1z; z++) {
                    level.getChunk(x, z);
                    if (!level.structureManager().getAllStructuresAt(new BlockPos(x << 4, 0, z << 4)).isEmpty()) {
                        clear = false;
                    }
                }
            }
            if (!clear) {
                skipped.add(cx + "," + cz + ": Bauwerk im Bereich");
                continue;
            }
            bx = cx;
            bz = cz;
        }
        if (bx == Integer.MIN_VALUE) {
            return "kein neuer Platz für den Vergleich: " + skipped;
        }
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
        if (ground != groundOld) {
            problems.add("Boden der neuen Chunks " + ground + " statt " + groundOld);
        }
        BandoTransform newT = BandoTransform.centered(plan, turns, bx, ground - plan.ground(), bz);
        int dug = pit(level, plan, newT, ground);
        BandoPiece piece = new BandoPiece(plan, newT);
        BoundingBox area = new BoundingBox(newT.originX() - 1, level.getMinY() + 1, newT.originZ() - 1, newT.originX() + newT.widthX(),
                level.getMaxY(), newT.originZ() + newT.widthZ());
        piece.place(level, area, RandomSource.create(seed));
        BoundingBox box = piece.getBoundingBox();
        int[] regionNew = {box.minX() - MARGIN, level.getMinY(), box.minZ() - MARGIN, box.maxX() + MARGIN, box.maxY() + MARGIN, box.maxZ() + MARGIN};
        int dx = regionNew[0] - region[0];
        int dz = regionNew[2] - region[2];
        if (regionNew[3] - regionNew[0] != region[3] - region[0] || regionNew[5] - regionNew[2] != region[5] - region[2]
                || regionNew[4] != region[4] || regionNew[1] != region[1]) {
            problems.add("Bereich des neuen Bandos anders groß: " + java.util.Arrays.toString(regionNew) + " zu " + java.util.Arrays.toString(region));
        }
        boolean[] site = BandoSite.siteColumns(plan);
        int differing = 0;
        int material = 0;
        int outside = 0;
        List<String> foreign = new ArrayList<>();
        for (int x = region[0]; x <= region[3]; x++) {
            for (int z = region[2]; z <= region[5]; z++) {
                int lx = oldT.localX(x, z);
                int lz = oldT.localZ(x, z);
                boolean inPlan = lx >= 0 && lz >= 0 && lx < plan.sizeX() && lz < plan.sizeZ();
                boolean allowed = inPlan && site[lz * plan.sizeX() + lx] && newPlacement(plan, lx, lz);
                for (int y = region[1]; y <= region[4]; y++) {
                    BlockState before = level.getBlockState(new BlockPos(x, y, z));
                    BlockState after = level.getBlockState(new BlockPos(x + dx, y, z + dz));
                    if (before.isAir() == after.isAir()) {
                        if (before != after) {
                            material++;
                        }
                        continue;
                    }
                    differing++;
                    if (!allowed) {
                        outside++;
                        if (foreign.size() < 6) {
                            foreign.add((x - region[0]) + "," + (y - region[1]) + "," + (z - region[2]) + " " + BlockStateParser.serialize(before) + " → "
                                    + BlockStateParser.serialize(after));
                        }
                    }
                }
            }
        }
        int columns = 0;
        int holesOld = 0;
        int closedNew = 0;
        for (int lz = 0; lz < plan.sizeZ(); lz++) {
            for (int lx = 0; lx < plan.sizeX(); lx++) {
                if (!site[lz * plan.sizeX() + lx] || !newPlacement(plan, lx, lz)) {
                    continue;
                }
                columns++;
                BlockPos oldYard = new BlockPos(oldT.worldX(lx, lz), oldT.worldY(plan.ground()), oldT.worldZ(lx, lz));
                BlockPos newYard = new BlockPos(newT.worldX(lx, lz), newT.worldY(plan.ground()), newT.worldZ(lx, lz));
                if (level.getBlockState(oldYard).isAir()) {
                    holesOld++;
                }
                if (!level.getBlockState(newYard).isAir()) {
                    closedNew++;
                }
            }
        }
        if (differing == 0) {
            problems.add("der in 0.6.0 gesetzte Bando hat dieselbe Form wie der aus 0.5.2 (keine neue Lage sichtbar)");
        }
        if (outside > 0) {
            problems.add(outside + " Blöcke mit anderer Form (Luft gegen Block) außerhalb der Hofspalten über eigenem Fundament, etwa " + foreign);
        }
        if (columns == 0 || closedNew != columns) {
            problems.add("Hof über der Grube in neuen Chunks " + closedNew + " von " + columns + " Spalten geschlossen");
        }
        if (holesOld == 0) {
            problems.add("alter Bando ohne Löcher im Hof (Grube aus 0.5.2 nicht sichtbar)");
        }
        String summary = String.format(Locale.ROOT, "alter Bando %s/%d bei %d,%d: Blockhash %s wie in 0.5.2 (%d feste Blöcke); neuer bei %d,%d in vorher "
                        + "nicht erzeugten Chunks%s, Grube %d Blöcke: Form an %d Stellen anders, alle in den %d Hofspalten über eigenem Fundament "
                        + "(alt %d Löcher im Hof, neu %d von %d geschlossen); %d Blöcke nur im Material anders (Palette nach Lage)", variant.key(), seed,
                center[0], center[1], hashOld.substring(0, 12), solid[0], bx, bz, skipped.isEmpty() ? "" : " (übersprungen: " + skipped + ")", dug,
                differing, columns, holesOld, closedNew, columns, material);
        try {
            Files.writeString(outDir().resolve("bando.txt"), summary + "\n" + String.join("\n", problems) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            problems.add("bando.txt nicht geschrieben: " + e);
        }
        return problems.isEmpty() ? "OK " + summary : String.join("; ", problems) + " (" + summary + ")";
    }

    private static StepResult camRulesStep(SelfTest.StepContext ctx) {
        if (!Services.PLATFORM.isModLoaded(CAM)) {
            ctx.log("Kamera-Add-on nicht geladen, nichts zu prüfen");
            return StepResult.pass();
        }
        return onServer(ctx, CovMigrationSelfTests::camRules);
    }

    private static String camRules(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        Map<String, Object> values = new TreeMap<>();
        for (WorldRule<?> rule : AddonWorldRules.registered()) {
            if (!CAM.equals(rule.id().getNamespace())) {
                continue;
            }
            Object value = AddonWorldRules.value(rule, server);
            values.put(rule.id().getPath(), value);
            seen.add(rule.id().getPath());
            if (!rule.defaultValue().equals(value)) {
                problems.add(rule.id() + " = " + value + " statt Standard " + rule.defaultValue());
            }
        }
        if (!Boolean.TRUE.equals(values.get("allow_autonomy"))) {
            problems.add("allow_autonomy " + values.get("allow_autonomy") + " statt an");
        }
        if (!Integer.valueOf(120).equals(values.get("max_altitude"))) {
            problems.add("max_altitude " + values.get("max_altitude") + " statt 120");
        }
        if (seen.isEmpty()) {
            problems.add("keine Welt-Regel des Kamera-Add-ons angemeldet");
        }
        return problems.isEmpty() ? "OK " + seen.size() + " Regeln des Kamera-Add-ons auf Standard: " + values : String.join("; ", problems);
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        JsonObject expected = MigrationSelfTests.expected();
        if (expected == null) {
            return StepResult.fail("keine Erwartung geladen");
        }
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("forced")) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Welt geöffnet");
            }
            state.put("forced", ctx.onServer(server -> MigrationValues.force(server, expected, true)));
            return StepResult.pending();
        }
        if (!state.containsKey(FUTURE)) {
            if (!((CompletableFuture<?>) state.get("forced")).isDone()) {
                return StepResult.pending();
            }
            AtomicReference<String> missing = cast(state.computeIfAbsent("missing", k -> new AtomicReference<String>()));
            if (ctx.tick() % 10 == 0) {
                ctx.onServer(server -> missing.set(MigrationValues.missingEntities(server, expected)));
            }
            String gone = missing.get();
            if (gone == null || !gone.isEmpty() && ctx.tick() < 400) {
                return StepResult.pending();
            }
        }
        return onServer(ctx, server -> {
            try {
                return flight(server, expected);
            } finally {
                MigrationValues.force(server, expected, false);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    static String fingerprint(DroneBuild build, Map<String, Float> tune, double charge, de.lardtom.propwash.build.PartWear wear) {
        AirframeSpec spec = AirframeSpec.of(build);
        DroneSim sim = new DroneSim(spec, TuneMapping.toConfig(tune, spec), new SimTuning(), 7L);
        sim.setBatteryCharge(charge);
        sim.setWear(wear);
        sim.reset(0.0, 100.0, 0.0, 0.0);
        PilotInput input = new PilotInput();
        SimEnvironment env = new SimEnvironment();
        de.lardtom.propwash.sim.StepResult out = new de.lardtom.propwash.sim.StepResult();
        sim.step(0L, input, env, out);
        input.armSwitch = true;
        sim.step(0L, input, env, out);
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (int i = 0; i < 240; i++) {
            input.throttle = i < 90 ? 0.65 : 0.45;
            input.roll = i >= 120 && i < 150 ? 0.3 : 0.0;
            input.pitch = i >= 150 && i < 180 ? -0.3 : 0.0;
            input.yaw = i >= 180 && i < 210 ? 0.4 : 0.0;
            sim.step(FRAME, input, env, out);
            x += out.dx;
            y += out.dy;
            z += out.dz;
        }
        return String.format(Locale.ROOT, "pos=%.6f,%.6f,%.6f quat=%.6f,%.6f,%.6f,%.6f volt=%.5f", x, y, z, out.orientation.x, out.orientation.y,
                out.orientation.z, out.orientation.w, sim.telemetry().packVoltage);
    }

    private static String flight(MinecraftServer server, JsonObject expected) {
        ServerLevel level = server.overworld();
        Map<String, String> prints = new TreeMap<>();
        int skipped = 0;
        for (JsonElement element : array(expected, "drones")) {
            JsonObject entry = element.getAsJsonObject();
            if (MigrationValues.needsJmp(entry) && !MigrationValues.jmpLoaded()) {
                skipped++;
                continue;
            }
            if (!(level.getEntity(UUID.fromString(entry.get("uuid").getAsString())) instanceof QuadcopterEntity drone)) {
                return "Drohne " + entry.get("uuid").getAsString() + " nicht geladen";
            }
            prints.put(drone.getUUID().toString(), fingerprint(AccessoryPayload.flightBuild(drone), drone.tune().values(), drone.batteryCharge(),
                    drone.wear()));
        }
        if (prints.isEmpty()) {
            return "keine Drohne zum Fliegen";
        }
        List<String> addons = new ArrayList<>();
        for (String mod : List.of(CAM, MOB_DEFENSE, MigrationValues.JMP)) {
            if (Services.PLATFORM.isModLoaded(mod)) {
                addons.add(mod);
            }
        }
        boolean withAddon = Services.PLATFORM.isModLoaded(CAM) || Services.PLATFORM.isModLoaded(MOB_DEFENSE);
        String folder = text(expected.getAsJsonObject("world"), "folder");
        StringBuilder text = new StringBuilder();
        prints.forEach((uuid, print) -> text.append(uuid).append(' ').append(print).append('\n'));
        try {
            Path dir = outDir();
            String base = "flug-" + safe(folder) + "-" + Services.PLATFORM.getPlatformName().toLowerCase(Locale.ROOT);
            Path own = dir.resolve(base + (withAddon ? "-mit-addon.txt" : "-ohne-addon.txt"));
            Files.writeString(own, text.toString(), StandardCharsets.UTF_8);
            Path other = dir.resolve(base + (withAddon ? "-ohne-addon.txt" : "-mit-addon.txt"));
            String summary = prints.size() + " Drohnen geflogen (" + skipped + " ohne Just More Parts nicht geprüft), Mods " + addons + ", " + own.getFileName();
            if (!Files.isRegularFile(other)) {
                return "OK " + summary + "; Vergleichslauf " + other.getFileName() + " fehlt noch";
            }
            Map<String, String> before = new TreeMap<>();
            for (String line : Files.readAllLines(other, StandardCharsets.UTF_8)) {
                int space = line.indexOf(' ');
                if (space > 0) {
                    before.put(line.substring(0, space), line.substring(space + 1));
                }
            }
            List<String> changed = new ArrayList<>();
            for (Map.Entry<String, String> print : prints.entrySet()) {
                String old = before.get(print.getKey());
                if (old != null && !old.equals(print.getValue())) {
                    changed.add(print.getKey() + ": " + old + " → " + print.getValue());
                }
            }
            long common = prints.keySet().stream().filter(before::containsKey).count();
            if (common == 0) {
                return "keine gemeinsame Drohne mit " + other.getFileName();
            }
            return changed.isEmpty() ? "OK " + summary + "; " + common + " Drohnen fliegen gleich wie in " + other.getFileName()
                    : changed.size() + " Drohnen fliegen anders als in " + other.getFileName() + ": " + changed.subList(0, Math.min(3, changed.size()));
        } catch (IOException e) {
            return "Fingerabdruck nicht geschrieben: " + e;
        }
    }

    private static StepResult logStep(SelfTest.StepContext ctx) {
        MigrationLog.Report report = MigrationLog.finish(!MigrationValues.jmpLoaded());
        if (report != null) {
            try {
                Files.writeString(outDir().resolve("log.txt"), report.report(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return StepResult.fail("log.txt nicht geschrieben: " + e);
            }
            ctx.log(report.summary());
        }
        return MigrationLog.result(report);
    }
}
