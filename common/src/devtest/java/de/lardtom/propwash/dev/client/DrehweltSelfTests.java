package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.MigrationValues;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.freecam.client.FreecamSession;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.GateStackSelfTests;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class DrehweltSelfTests {
    public static final String WORLD = SelfTestRunner.WORLD_NAME + " Drehwelt";
    public static final String ZIP = "propwash-drehwelt.zip";
    public static final String LIST = "drehwelt.txt";
    public static final String ALL_ENV = "PROPWASH_DREHWELT_ALL";
    private static final String DIR = "drehwelt";
    private static final String FUTURE = "future";
    private static final int ROAD_HALF = 152;
    private static final int GAP = 8;
    static final int CANYON_HEIGHT = 22;
    private static final List<Built> BUILT = new CopyOnWriteArrayList<>();
    static final Map<String, Integer> COURSES = new ConcurrentHashMap<>();
    private static final AtomicReference<String> RULE = new AtomicReference<>("");
    private static final AtomicReference<BlockPos> SPAWN = new AtomicReference<>(BlockPos.ZERO);

    record Feature(String name, String strand, BooleanSupplier present) {
    }

    record XClass(Identifier preset, DroneData data, String source) {
    }

    record Built(Plot plot, boolean complete, String note, int gates, int drones) {
    }

    enum Row { NORTH, SOUTH }

    record Plot(String id, String title, int width, int depth, Row row, @Nullable Feature needs, @Nullable Feature shows,
                Builder builder) {
    }

    @FunctionalInterface
    interface Builder {
        String build(Site site);
    }

    static final Feature RX = new Feature("RX-Block", "rx",
            () -> BuiltInRegistries.BLOCK.containsKey(Constants.id("rx_block")));
    static final Feature SURFACE = new Feature("Slime federt, Wolle fängt ab", "surface",
            () -> present("de.lardtom.propwash.sim.SurfaceResponse"));
    static final Feature TRIGGERS = new Feature("Drohne löst Druckplatte und Stolperdraht aus", "block-triggers",
            () -> present("de.lardtom.propwash.interact.DroneBlockTriggers"));
    static final Feature FREECAM = new Feature("Freecam-Drohne (F6)", "freecam",
            () -> WorldRuleSpecs.byName("allow_freecam").isPresent());
    static final Feature MARKER = new Feature("Flugbahn-Marker im OSD", "fpm",
            () -> OsdElement.byKey("flight_path_marker") != null);
    static final Feature PLUG = new Feature("Akku gesteckt ohne Stecker und Kabel", "battery-plug",
            () -> present("de.lardtom.propwash.drone.client.render.PowerLead"));
    static final Feature LEGS = new Feature("Durchflug unten durch die Gate-Leiter zählt", "gate-legs",
            () -> present("de.lardtom.propwash.race.GateOpenings"));
    static final Feature HEAT = new Feature("Motor-Hitze mit Warnung", "heat",
            () -> present("de.lardtom.propwash.api.part.MotorThermal"));
    static final Feature XCLASS = new Feature("X-Class (Just More Parts in mods oder Beispiel-Add-on)", "jmp",
            () -> xclass() != null);

    static final DroneBuild HOT_SEVEN = DroneBuild.LONG_RANGE.withBattery(BatteryType.B6S_1300);

    static final List<Plot> PLOTS = List.of(
            new Plot("R2", "RX-Tor mit Lampenreihe", 32, 32, Row.NORTH, RX, null, DrehweltSelfTests::rxGate),
            new Plot("R3", "Dive-Spot", 32, 32, Row.NORTH, null, MARKER, DrehweltSelfTests::diveSpot),
            new Plot("R4", "Slime- und Wolle-Parcours", 48, 40, Row.NORTH, SURFACE, null, DrehweltSelfTests::bounceCourse),
            new Plot("R5", "Holzplatte vor Tür, Stolperdraht", 32, 32, Row.NORTH, TRIGGERS, null, DrehweltSelfTests::plateDoor),
            new Plot("R6", "Werkbank mit 5 Zoll und X-Class", 24, 24, Row.NORTH, XCLASS, PLUG, DrehweltSelfTests::workbench),
            new Plot("R11", "Indoor-Whoop-Kurs", 48, 32, Row.NORTH, null, null, DrehweltSelfTests::whoopHall),
            new Plot("T", "Gate-Leiter", 24, 24, Row.NORTH, null, LEGS, DrehweltSelfTests::gateLadder),
            new Plot("R1", "Schlucht mit Turm", 112, 56, Row.SOUTH, null, FREECAM, DrehweltSelfTests::canyon),
            new Plot("R8", "Hitze-Strecke", 176, 64, Row.SOUTH, null, HEAT, DrehweltSelfTests::heatTrack));

    private DrehweltSelfTests() {
    }

    public static void register() {
        SelfTest.afterLeaveStep("drehwelt: Welt anlegen (flach, Kreativ, Mittag, kein Wetter, keine Mobs)", 2400, DrehweltSelfTests::createStep);
        SelfTest.afterLeaveStep("drehwelt: Selbsttest-Optionen greifen (Grafik-Voreinstellung custom, Sichtweite 6 und Simulationsdistanz 5 bei Client und Server)",
                200, DrehweltChecks::options);
        SelfTest.afterLeaveStep("drehwelt: Plätze bauen (alle neun gefüllt, kein leerer Platz)", 2400, DrehweltSelfTests::buildStep);
        SelfTest.afterLeaveStep("drehwelt: RX-Tor schaltet Kolben und Lampen (Test-Funke am RX-Block: Gas 100 % offen, 50 % weniger Lampen, 0 % zu)", 800,
                DrehweltSelfTests::gateSwitchStep);
        SelfTest.afterLeaveStep("drehwelt: Slime federt, Wolle und Heu fangen ab (Flächen am Parcours)", 200, DrehweltSelfTests::surfaceStep);
        SelfTest.afterLeaveStep("drehwelt: Drohne drückt die Holzplatte (Tür auf) und löst den Stolperdraht aus (Lampe an)", 600,
                DrehweltSelfTests::triggerStep);
        SelfTest.afterLeaveStep("drehwelt: Hitze-Strecke mit 7 Zoll und LiPo 6S 1300 am Start", 200, DrehweltSelfTests::heatStep);
        SelfTest.afterLeaveStep("drehwelt: Freecam-Regel an (Server und Client, Freecam darf starten)", 400, DrehweltSelfTests::freecamStep);
        SelfTest.afterLeaveStep("drehwelt: Freecam startet auf dem Turm der Schlucht als 5 Zoll, fliegt los und wechselt im Flug auf Whoop (R1)", 800,
                DrehweltFreecam::towerStep);
        SelfTest.afterLeaveStep("drehwelt: Spieler an den Start, Welt speichern und verlassen", 1200, DrehweltSelfTests::leaveStep);
        SelfTest.afterLeaveStep("drehwelt: Ausgabe als Zip im Run-Ordner", 1200, DrehweltSelfTests::zipStep);
        SelfTest.afterLeaveStep("drehwelt: gespeicherte Drehwelt neu öffnen", 2400, DrehweltFreecam::reopenStep);
        SelfTest.afterLeaveStep("drehwelt: nach dem Neuladen Freecam-Regel an, Whoop-Kurs und Gate-Leiter gespeichert", 400, DrehweltFreecam::savedStep);
        SelfTest.afterLeaveStep("drehwelt: Drehwelt verlassen", 1200, ctx -> leaveWorld(ctx, "Propwash-Selbsttest Drehwelt geprüft"));
    }

    static boolean all() {
        String value = System.getenv(ALL_ENV);
        return value != null && value.trim().matches("1|true|on|yes");
    }

    static @Nullable XClass xclass() {
        XClass best = null;
        int bestWheelbase = -1;
        XClass fallback = null;
        for (DronePreset preset : PropwashApi.builds().presets()) {
            ItemStack stack = PropwashApi.builds().createDrone(preset.data());
            DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
            if (build == null || build.frame().definition().role() != FrameRole.X_CLASS) {
                continue;
            }
            int wheelbase = build.frame().definition().wheelbaseMm();
            if (MigrationValues.JMP.equals(preset.id().getNamespace())) {
                if (wheelbase > bestWheelbase) {
                    best = new XClass(preset.id(), preset.data(), "Just More Parts");
                    bestWheelbase = wheelbase;
                }
            } else if (fallback == null) {
                fallback = new XClass(preset.id(), preset.data(), preset.id().getNamespace().equals(ExampleAddon.NAMESPACE) ? "Beispiel-Add-on" : preset.id().getNamespace());
            }
        }
        return best != null ? best : fallback;
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, DrehweltSelfTests.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    static final class Site {
        final ServerPlayer player;
        final ServerLevel level;
        final Plot plot;
        final int x0;
        final int z0;
        final int ground;
        int gates;
        int drones;

        Site(ServerPlayer player, Plot plot, int x0, int z0, int ground) {
            this.player = player;
            this.level = player.level();
            this.plot = plot;
            this.x0 = x0;
            this.z0 = z0;
            this.ground = ground;
        }

        BlockPos at(int u, int y, int v) {
            int z = this.plot.row == Row.NORTH ? this.z0 + v : this.z0 + this.plot.depth - 1 - v;
            return new BlockPos(this.x0 + u, this.ground + y, z);
        }

        void set(int u, int y, int v, BlockState state) {
            this.level.setBlock(this.at(u, y, v), state, Block.UPDATE_ALL);
        }

        void quiet(int u, int y, int v, BlockState state) {
            this.level.setBlock(this.at(u, y, v), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        void fill(int u0, int y0, int v0, int u1, int y1, int v1, BlockState state) {
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) {
                for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++) {
                    for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                        this.quiet(u, y, v, state);
                    }
                }
            }
        }

        void sign(int u, int v, String... lines) {
            this.signAt(this.at(u, 0, v), this.plot.row == Row.NORTH ? 8 : 0, lines);
        }

        void signAt(BlockPos pos, int rotation, String... lines) {
            this.level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation), Block.UPDATE_ALL);
            if (this.level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                List<Component> text = new ArrayList<>();
                for (int i = 0; i < SignText.LINES; i++) {
                    text.add(Component.literal(i < lines.length ? lines[i] : ""));
                }
                sign.setText(new SignText(text, text, DyeColor.BLACK, false), SignTextSlot.FRONT);
                sign.setChanged();
                this.level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
            }
        }

        QuadcopterEntity drone(double u, double v, DroneBuild build, float yaw) {
            BlockPos base = this.at((int) Math.floor(u), 0, (int) Math.floor(v));
            int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ());
            QuadcopterEntity drone = DroneModule.DRONE.get().create(this.level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            drone.snapTo(base.getX() + 0.5, top, base.getZ() + 0.5, yaw, 0.0F);
            drone.setAttitude(new Quaternionf().rotationY((float) Math.toRadians(-yaw)));
            drone.setBuild(build);
            drone.setBatteryCharge(1.0F);
            if (!this.level.addFreshEntity(drone)) {
                throw new IllegalStateException("Drohne nicht gespawnt");
            }
            this.drones++;
            return drone;
        }

        QuadcopterEntity drone(double u, double v, ItemStack stack, float yaw) {
            BlockPos base = this.at((int) Math.floor(u), 0, (int) Math.floor(v));
            int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ());
            QuadcopterEntity drone = DroneModule.DRONE.get().create(this.level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
            drone.loadFromStack(stack);
            drone.snapTo(base.getX() + 0.5, top, base.getZ() + 0.5, yaw, 0.0F);
            drone.setAttitude(new Quaternionf().rotationY((float) Math.toRadians(-yaw)));
            if (!this.level.addFreshEntity(drone)) {
                throw new IllegalStateException("Drohne nicht gespawnt");
            }
            this.drones++;
            return drone;
        }

        GateEntity gate(GateSize size, int u, int v, float yaw) {
            BlockPos base = this.at(u, 0, v);
            this.player.teleportTo(this.level, base.getX() + 0.5, this.ground, base.getZ() - 3.5, Set.of(), yaw, 0.0F, true);
            GateEntity gate = GateStackSelfTests.place(this.player, size, base.getX(), this.ground, base.getZ(), yaw);
            this.gates++;
            return gate;
        }
    }

    private static boolean pressButton(Screen screen, String text) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && text.equals(button.getMessage().getString())) {
                button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                return true;
            }
        }
        return false;
    }

    static void deleteWorld(Minecraft mc, String name) throws IOException {
        Path dir = mc.getLevelSource().getBaseDir().resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    static StepResult createFlatWorld(SelfTest.StepContext ctx, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.level != null) {
                return StepResult.fail("noch in einer Welt");
            }
            try {
                deleteWorld(mc, name);
            } catch (IOException e) {
                return StepResult.fail("alte Welt '" + name + "' nicht gelöscht: " + e);
            }
            CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen()));
            if (!(mc.gui.screen() instanceof CreateWorldScreen screen)) {
                return StepResult.fail("CreateWorldScreen nicht geöffnet: " + mc.gui.screen());
            }
            WorldCreationUiState ui = screen.getUiState();
            ui.setName(name);
            Holder<WorldPreset> flat = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT);
            ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(flat));
            ui.setSeed("20261018");
            ui.setGenerateStructures(false);
            ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            ui.getGameRules().set(GameRules.ADVANCE_TIME, false, null);
            ui.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
            ui.getGameRules().set(GameRules.SPAWN_MOBS, false, null);
            if (!pressButton(screen, Component.translatable("selectWorld.create").getString())) {
                return StepResult.fail("Knopf 'selectWorld.create' nicht gefunden");
            }
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen instanceof ConfirmScreen confirm && ctx.tick() % 20 == 0) {
            pressButton(confirm, CommonComponents.GUI_YES.getString());
        }
        if (mc.player != null && mc.level != null && screen == null && mc.getSingleplayerServer() != null) {
            ctx.state().putIfAbsent("ready", ctx.tick());
            if (ctx.tick() - (int) ctx.state().get("ready") > 40) {
                ctx.log(name + " angelegt");
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    static StepResult leaveWorld(SelfTest.StepContext ctx, String why) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            if (mc.level != null) {
                mc.schedule(() -> mc.disconnectFromWorld(Component.literal(why)));
            }
            return StepResult.pending();
        }
        if (mc.level == null && mc.player == null && mc.getSingleplayerServer() == null && ctx.tick() > 5) {
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    static @Nullable StepResult future(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail("keine Server-Aufgabe");
        }
        if (future.isCompletedExceptionally()) {
            Throwable cause = future.exceptionNow();
            Constants.LOG.error("[Selbsttest] Drehwelt: Server-Aufgabe fehlgeschlagen", cause);
            return StepResult.fail("Server-Aufgabe: " + cause);
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static StepResult createStep(SelfTest.StepContext ctx) {
        return createFlatWorld(ctx, WORLD);
    }

    private static StepResult buildStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Drehwelt geöffnet");
            }
            BUILT.clear();
            COURSES.clear();
            AtomicBoolean ready = new AtomicBoolean();
            ctx.state().put("ready", ready);
            ctx.state().put(FUTURE, ctx.onServer(server -> ready.set(preload(server))));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        if (ctx.state().get("ready") instanceof AtomicBoolean ready) {
            if (ready.get() || ctx.tick() > 1200) {
                ctx.state().remove("ready");
                ctx.state().put(FUTURE, ctx.onServer(DrehweltSelfTests::buildAll));
            } else {
                ctx.state().put(FUTURE, ctx.onServer(server -> ready.set(preload(server))));
            }
            return StepResult.pending();
        }
        if (BUILT.size() != PLOTS.size()) {
            return StepResult.fail("nur " + BUILT.size() + " von " + PLOTS.size() + " Plätzen gebaut");
        }
        List<String> empty = new ArrayList<>();
        for (Built built : BUILT) {
            ctx.log(built.plot.id + " " + built.plot.title + ": " + built.note);
            if (!built.complete) {
                empty.add(built.plot.id + " (" + built.note + ")");
            }
        }
        if (!empty.isEmpty()) {
            return StepResult.fail("Plätze nicht gefüllt: " + String.join("; ", empty));
        }
        Built whoop = find("R11");
        Built ladder = find("T");
        Built bench = find("R6");
        if (whoop == null || whoop.gates != 6) {
            return StepResult.fail("Whoop-Kurs ohne sechs Gates: " + (whoop == null ? "fehlt" : whoop.gates));
        }
        if (ladder == null || ladder.gates < 5) {
            return StepResult.fail("Gate-Leiter ohne Stapel: " + (ladder == null ? "fehlt" : ladder.gates));
        }
        if (bench == null || bench.drones < 2) {
            return StepResult.fail("Werkbank ohne 5 Zoll und X-Class: " + (bench == null ? "fehlt" : bench.drones + " Drohnen"));
        }
        ctx.log(PLOTS.size() + " von " + PLOTS.size() + " Plätzen gefüllt, kein leerer Platz");
        return StepResult.pass();
    }

    static @Nullable Built find(String id) {
        for (Built built : BUILT) {
            if (built.plot.id.equals(id)) {
                return built;
            }
        }
        return null;
    }

    private static boolean preload(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        BlockPos spawn = level.getRespawnData().pos();
        int sx = spawn.getX();
        int sz = spawn.getZ();
        boolean ready = true;
        for (int cx = (sx - ROAD_HALF) >> 4; cx <= (sx + ROAD_HALF) >> 4; cx++) {
            for (int cz = (sz - 72) >> 4; cz <= (sz + 60) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                ready &= level.isPositionEntityTicking(new BlockPos(cx << 4, spawn.getY(), cz << 4));
            }
        }
        return ready;
    }

    private static void buildAll(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        player.setGameMode(GameType.CREATIVE);
        BlockPos spawn = level.getRespawnData().pos();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        int sx = spawn.getX();
        int sz = spawn.getZ();
        SPAWN.set(new BlockPos(sx, ground, sz + 5));
        List<long[]> forced = new ArrayList<>();
        force(level, sx - ROAD_HALF, sz - 72, sx + ROAD_HALF, sz + 60, forced);
        try {
            for (int x = sx - ROAD_HALF; x <= sx + ROAD_HALF; x++) {
                for (int z = sz + 2; z <= sz + 8; z++) {
                    level.setBlock(new BlockPos(x, ground - 1, z), (z == sz + 2 || z == sz + 8 ? Blocks.STONE_BRICKS : Blocks.CONCRETE.pick(DyeColor.GRAY))
                            .defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            int north = sx - 144;
            int south = sx - 148;
            for (Plot plot : PLOTS) {
                int x0;
                int z0;
                if (plot.row == Row.NORTH) {
                    x0 = north;
                    z0 = sz + 12;
                    north += plot.width + GAP;
                } else {
                    x0 = south;
                    z0 = sz - plot.depth;
                    south += plot.width + GAP;
                }
                Site site = new Site(player, plot, x0, z0, ground);
                BUILT.add(buildPlot(site));
            }
            board(player, level, sx, ground, sz);
            writeList(server, sx, ground, sz);
        } finally {
            for (long[] chunk : forced) {
                level.setChunkForced((int) chunk[0], (int) chunk[1], false);
            }
            BlockPos start = SPAWN.get();
            player.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, Set.of(), 0.0F, 10.0F, true);
        }
    }

    private static void force(ServerLevel level, int x0, int z0, int x1, int z1, List<long[]> forced) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                forced.add(new long[]{cx, cz});
            }
        }
    }

    private static Built buildPlot(Site site) {
        Plot plot = site.plot;
        site.fill(0, -1, 0, plot.width - 1, -1, plot.depth - 1, Blocks.GRASS_BLOCK.defaultBlockState());
        site.fill(0, -1, 0, plot.width - 1, -1, 0, Blocks.SMOOTH_STONE.defaultBlockState());
        List<String> missing = new ArrayList<>();
        for (Feature feature : new Feature[]{plot.needs, plot.shows}) {
            if (feature != null && !feature.present.getAsBoolean()) {
                missing.add(feature.name + " (Strang " + feature.strand + ")");
            }
        }
        if (!missing.isEmpty() && !all()) {
            Constants.LOG.error("[Selbsttest] Drehwelt: {} {} nicht gebaut, es fehlt {}", plot.id, plot.title, String.join(", ", missing));
            return new Built(plot, false, "nicht gebaut, es fehlt " + String.join(", ", missing), 0, 0);
        }
        String extra = plot.builder.build(site);
        String shows = plot.shows == null || !plot.shows.present.getAsBoolean() ? "" : ", " + plot.shows.name + " vorhanden";
        if (!missing.isEmpty()) {
            shows += ", gebaut ohne " + String.join(", ", missing) + " (" + ALL_ENV + ")";
        }
        site.sign(plot.width / 2, -1, plot.id + " " + shortTitle(plot), missing.isEmpty() ? "bereit" : "unvollständig",
                "x " + site.at(plot.width / 2, 0, 0).getX(), "z " + site.at(plot.width / 2, 0, 0).getZ());
        Constants.LOG.info("[Selbsttest] Drehwelt: {} {} bei x {} z {} gebaut{}{}", plot.id, plot.title, site.x0, site.z0, extra, shows);
        return new Built(plot, missing.isEmpty(), "gebaut" + extra + shows, site.gates, site.drones);
    }

    private static String shortTitle(Plot plot) {
        String title = plot.title;
        int cut = title.indexOf(' ', 10);
        return cut > 0 && title.length() > 12 ? title.substring(0, cut) : title;
    }

    private static void board(ServerPlayer player, ServerLevel level, int sx, int ground, int sz) {
        Site site = new Site(player, PLOTS.getFirst(), sx, sz, ground);
        site.signAt(new BlockPos(sx - 1, ground, sz + 9), 8, "Propwash", "Drehwelt 0.6.0", "Norden: R2-R11,T", "Süden: R1, R8");
        site.signAt(new BlockPos(sx + 1, ground, sz + 9), 8, "Freecam-Regel an", "F6 auf dem Turm", "der Schlucht", LIST + " im Zip");
    }

    private static void writeList(MinecraftServer server, int sx, int ground, int sz) {
        StringBuilder out = new StringBuilder();
        out.append("Propwash Drehwelt (Gruppe drehwelt:, docs/testing.md)\n");
        out.append(String.format(Locale.ROOT, "Start: x %d y %d z %d, Kreativ, Mittag, kein Wetter, keine Mobs%n", sx, ground, sz + 5));
        out.append("Freecam-Regel: an (allow_freecam, F6; Start auf dem Turm der Schlucht R1)\n");
        for (Built built : BUILT) {
            Plot plot = built.plot;
            out.append(String.format(Locale.ROOT, "%-4s %-36s %s%n", plot.id, plot.title, built.note));
        }
        try {
            Path dir = Services.PLATFORM.getGameDir().resolve(DIR);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(LIST), out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(LIST + " nicht geschrieben: " + e, e);
        }
    }

    private static String rxGate(Site s) {
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        s.fill(11, 0, 16, 26, 4, 16, bricks);
        s.fill(13, 0, 16, 15, 1, 16, Blocks.AIR.defaultBlockState());
        s.fill(6, 0, 16, 10, 0, 16, Blocks.STONE.defaultBlockState());
        for (int v = 2; v <= 15; v++) {
            s.quiet(6, 0, v, Blocks.REDSTONE_LAMP.defaultBlockState());
        }
        s.set(14, 0, 16, Blocks.SMOOTH_STONE.defaultBlockState());
        s.set(14, 1, 16, Blocks.SMOOTH_STONE.defaultBlockState());
        BlockState piston = Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, direction(s, Direction.EAST));
        s.set(13, 0, 16, piston);
        s.set(13, 1, 16, piston);
        for (int u = 7; u <= 10; u++) {
            s.set(u, 1, 16, Blocks.REDSTONE_WIRE.defaultBlockState());
        }
        for (int v = 2; v <= 15; v++) {
            s.set(6, 1, v, Blocks.REDSTONE_WIRE.defaultBlockState());
        }
        s.set(12, 1, 16, Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, direction(s, Direction.EAST)));
        boolean rx = RX.present.getAsBoolean();
        if (rx) {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Constants.id("rx_block"));
            s.set(6, 1, 16, block.orElseThrow().defaultBlockState());
        } else {
            s.set(6, 1, 16, Blocks.LEVER.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                    .setValue(LeverBlock.FACING, direction(s, Direction.SOUTH)));
        }
        s.sign(4, 14, "RX-Block bei", "der Lampenreihe", "Gas = Kanal", rx ? "an die Funke" : "(Hebel: Probe)");
        s.sign(18, 13, "Kolbentor", "öffnet mit", "Signal, Lampen", "zeigen Stärke");
        return rx ? ", RX-Block mit Kolbentor und 14 Lampen" : ", Hebel statt RX-Block (PROPWASH_DREHWELT_ALL)";
    }

    private static Direction direction(Site s, Direction north) {
        if (s.plot.row == Row.NORTH || north.getAxis() == Direction.Axis.X) {
            return north;
        }
        return north.getOpposite();
    }

    private static StepResult gateSwitchStep(SelfTest.StepContext ctx) {
        Built rx = find("R2");
        if (rx == null || !rx.complete) {
            ctx.log("RX-Tor nicht gebaut (Strang rx fehlt), übersprungen");
            return StepResult.pass();
        }
        if (RX.present.getAsBoolean()) {
            return DrehweltChecks.rxDrive(ctx, rx.plot);
        }
        Map<String, Object> state = ctx.state();
        @SuppressWarnings("unchecked")
        AtomicReference<String> result = (AtomicReference<String>) state.computeIfAbsent("result", k -> new AtomicReference<String>());
        if (state.containsKey(FUTURE)) {
            StepResult waiting = future(ctx);
            if (waiting != null) {
                return waiting;
            }
            state.remove(FUTURE);
        }
        if (!state.containsKey("near")) {
            state.put("near", Boolean.TRUE);
            state.put(FUTURE, ctx.onServer(server -> nearGate(server, rx.plot)));
            return StepResult.pending();
        }
        int phase = (int) state.getOrDefault("phase", 0);
        String now = result.get();
        boolean closed = "zu lampen=0".equals(now);
        boolean open = now != null && now.startsWith("offen") && !now.endsWith("lampen=0");
        switch (phase) {
            case 0, 2 -> {
                if (closed && phase == 0) {
                    state.put("phase", 1);
                    state.put(FUTURE, ctx.onServer(server -> result.set(gateState(server, rx.plot, true))));
                    return StepResult.pending();
                }
                if (closed) {
                    ctx.log("Hebel wieder aus: Tor zu, Lampen aus");
                    return StepResult.pass();
                }
            }
            case 1 -> {
                if (open) {
                    ctx.log("Hebel an: " + now);
                    state.put("phase", 2);
                    state.put(FUTURE, ctx.onServer(server -> result.set(gateState(server, rx.plot, false))));
                    return StepResult.pending();
                }
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        if (ctx.tick() > 360) {
            return StepResult.fail("Phase " + phase + ": " + now);
        }
        if (ctx.tick() % 10 == 0) {
            state.put(FUTURE, ctx.onServer(server -> result.set(gateState(server, rx.plot, null))));
        }
        return StepResult.pending();
    }

    static void nearGate(MinecraftServer server, Plot plot) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        Site site = locate(player, plot);
        BlockPos pos = site.at(plot.width / 2, 0, -4);
        player.teleportTo(site.level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 0.0F, 10.0F, true);
    }

    static String gateState(MinecraftServer server, Plot plot, @Nullable Boolean lever) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        Site site = locate(player, plot);
        ServerLevel level = site.level;
        if (lever != null) {
            BlockPos pos = site.at(6, 1, 16);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LeverBlock block && state.getValue(LeverBlock.POWERED) != lever) {
                block.pull(state, level, pos, null);
            }
            return "geschaltet " + lever;
        }
        boolean closed = level.getBlockState(site.at(15, 0, 16)).isSolid() && level.getBlockState(site.at(15, 1, 16)).isSolid();
        boolean open = level.getBlockState(site.at(15, 0, 16)).isAir() && level.getBlockState(site.at(15, 1, 16)).isAir();
        int lit = 0;
        for (int v = 2; v <= 15; v++) {
            BlockState lamp = level.getBlockState(site.at(6, 0, v));
            if (lamp.is(Blocks.REDSTONE_LAMP) && lamp.getValue(RedstoneLampBlock.LIT)) {
                lit++;
            }
        }
        return (closed ? "zu" : open ? "offen" : "halb") + " lampen=" + lit;
    }

    static Site locate(ServerPlayer player, Plot target) {
        BlockPos start = SPAWN.get();
        int sx = start.getX();
        int sz = start.getZ() - 5;
        int north = sx - 144;
        int south = sx - 148;
        for (Plot plot : PLOTS) {
            if (plot == target) {
                return plot.row == Row.NORTH ? new Site(player, plot, north, sz + 12, start.getY())
                        : new Site(player, plot, south, sz - plot.depth, start.getY());
            }
            if (plot.row == Row.NORTH) {
                north += plot.width + GAP;
            } else {
                south += plot.width + GAP;
            }
        }
        throw new IllegalStateException("Platz " + target.id + " nicht gefunden");
    }

    private static String diveSpot(Site s) {
        BlockState quartz = Blocks.QUARTZ_BRICKS.defaultBlockState();
        s.fill(12, 0, 6, 18, 39, 12, quartz);
        s.fill(13, 1, 7, 17, 38, 11, Blocks.AIR.defaultBlockState());
        s.fill(11, 40, 5, 19, 40, 13, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        s.fill(11, 41, 5, 19, 41, 5, Blocks.GLASS.defaultBlockState());
        s.fill(10, -1, 18, 22, -1, 30, Blocks.CONCRETE.pick(DyeColor.LIME).defaultBlockState());
        s.fill(15, -1, 22, 17, -1, 26, Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState());
        s.gate(GateSize.LARGE, 16, 20, 0.0F);
        s.drone(15.5, 8.5, DroneBuild.FREESTYLE, 0.0F);
        BlockPos top = s.at(15, 41, 9);
        s.signAt(top, s.plot.row == Row.NORTH ? 0 : 8, "Dive-Spot", "40 Blöcke", "Sprung durch", "das Gate unten");
        return ", Turm 40 Blöcke mit Gate am Fuß";
    }

    private static String bounceCourse(Site s) {
        s.fill(4, 0, 2, 8, 0, 6, Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState());
        s.fill(12, -1, 4, 16, -1, 20, Blocks.SLIME_BLOCK.defaultBlockState());
        s.fill(20, 0, 24, 28, 6, 24, Blocks.SLIME_BLOCK.defaultBlockState());
        s.fill(32, 0, 8, 36, 0, 12, Blocks.WOOL.pick(DyeColor.RED).defaultBlockState());
        s.fill(40, 0, 20, 44, 0, 24, Blocks.WOOL.pick(DyeColor.LIGHT_BLUE).defaultBlockState());
        s.fill(37, 0, 31, 41, 2, 35, Blocks.HAY_BLOCK.defaultBlockState());
        s.fill(37, 3, 31, 41, 3, 35, Blocks.WOOL.pick(DyeColor.YELLOW).defaultBlockState());
        s.drone(6.5, 4.5, DroneBuild.FREESTYLE, 0.0F);
        s.sign(6, 8, "Start von", "Wolle");
        s.sign(14, 22, "Slime-Boden", "federt");
        s.sign(24, 22, "Slime-Wand");
        s.sign(34, 14, "Wolle fängt", "ab");
        return ", Wolle-Start, Slime-Boden und -Wand, drei Wolle-Landeplätze";
    }

    private static String plateDoor(Site s) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        s.fill(10, 0, 16, 22, 4, 24, planks);
        s.fill(11, 0, 17, 21, 3, 23, Blocks.AIR.defaultBlockState());
        s.fill(10, 5, 16, 22, 5, 24, Blocks.OAK_SLAB.defaultBlockState());
        s.fill(13, -1, 11, 19, -1, 15, Blocks.SPRUCE_PLANKS.defaultBlockState());
        Direction facing = direction(s, Direction.SOUTH);
        s.set(16, 0, 16, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        s.set(16, 1, 16, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        s.set(16, 0, 15, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
        BlockState post = Blocks.STONE_BRICKS.defaultBlockState();
        s.set(4, 0, 8, post);
        s.set(28, 0, 8, post);
        s.set(28, 1, 8, Blocks.REDSTONE_LAMP.defaultBlockState());
        s.set(5, 0, 8, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, direction(s, Direction.EAST)));
        s.set(27, 0, 8, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, direction(s, Direction.WEST)));
        for (int u = 6; u <= 26; u++) {
            s.set(u, 0, 8, Blocks.TRIPWIRE.defaultBlockState());
        }
        s.sign(16, 13, "Landen auf der", "Holzplatte", "öffnet die Tür");
        s.sign(16, 6, "Stolperdraht", "schaltet die", "Lampe");
        return ", Haus mit Tür und Holzplatte, Stolperdraht mit Lampe";
    }

    private static String workbench(Site s) {
        s.fill(7, -1, 7, 17, -1, 17, Blocks.SMOOTH_STONE.defaultBlockState());
        s.set(12, 0, 12, ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState());
        s.drone(14.5, 12.5, DroneBuild.FREESTYLE, 90.0F);
        XClass xclass = xclass();
        String big = "";
        if (xclass != null) {
            QuadcopterEntity drone = s.drone(9.5, 14.5, PropwashApi.builds().createDrone(xclass.data()), 90.0F);
            big = " und X-Class " + xclass.preset() + " aus " + xclass.source() + " (" + drone.build().frame().key() + ")";
            s.sign(9, 11, "X-Class:", xclass.preset().getPath(), "aus", xclass.source());
        }
        s.sign(12, 9, "Akku wechseln", "an der Werkbank", "Stecker, Kabel", "ausgeblendet");
        return ", Werkbank mit 5 Zoll" + big;
    }

    private static String whoopHall(Site s) {
        BlockState wall = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
        s.fill(2, -1, 2, 45, -1, 29, Blocks.OAK_PLANKS.defaultBlockState());
        s.fill(2, 0, 2, 45, 5, 2, wall);
        s.fill(2, 0, 29, 45, 5, 29, wall);
        s.fill(2, 0, 2, 2, 5, 29, wall);
        s.fill(45, 0, 2, 45, 5, 29, wall);
        for (int u = 6; u <= 42; u += 4) {
            s.fill(u, 2, 29, u + 1, 3, 29, Blocks.GLASS.defaultBlockState());
        }
        s.fill(2, 6, 2, 45, 6, 29, Blocks.GLASS.defaultBlockState());
        s.fill(22, 0, 2, 25, 2, 2, Blocks.AIR.defaultBlockState());
        for (int u = 6; u <= 42; u += 6) {
            s.quiet(u, 5, 15, Blocks.GLOWSTONE.defaultBlockState());
        }
        float east = s.plot.row == Row.NORTH ? -90.0F : 90.0F;
        List<GateEntity> gates = new ArrayList<>();
        gates.add(s.gate(GateSize.SMALL, 12, 10, east));
        gates.add(s.gate(GateSize.SMALL, 24, 12, east));
        gates.add(s.gate(GateSize.SMALL, 36, 10, east));
        gates.add(s.gate(GateSize.SMALL, 36, 22, -east));
        gates.add(s.gate(GateSize.SMALL, 24, 20, -east));
        gates.add(s.gate(GateSize.SMALL, 12, 22, -east));
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : gates) {
            CourseToolItem.useOnGate(tool, s.player, gate);
        }
        s.drone(8.5, 8.5, DroneBuild.WHOOP, -90.0F);
        s.sign(23, 4, "Indoor-Kurs", "6 kleine Gates", "65-mm-Whoop:", "JMP 1.2.0");
        COURSES.put(s.plot.id, CourseToolItem.courseOf(tool));
        return ", Halle mit sechs kleinen Gates als Kurs " + CourseToolItem.courseOf(tool) + ", Whoop am Start";
    }

    private static String gateLadder(Site s) {
        BlockPos base = s.at(7, 0, 12);
        s.player.teleportTo(s.level, base.getX() + 0.5, s.ground, base.getZ() - 4.5, Set.of(), 0.0F, 0.0F, true);
        GateStackSelfTests.Built built = GateStackSelfTests.build(s.player, base.getX(), s.ground, base.getZ());
        s.gates += built.gates().size();
        COURSES.put(s.plot.id, built.course());
        s.sign(12, 8, "Gate-Leiter", "Durchflug", "unten", LEGS.present.getAsBoolean() ? "zählt" : "zählt ab gate-legs");
        return ", " + built.gates().size() + " gestapelte Gates als Kurs " + built.course();
    }

    static int towerV(int depth) {
        return (int) Math.round(depth / 2.0 + 9.0 * Math.sin(6 / 14.0)) + 12;
    }

    static BlockPos towerStand(Site s) {
        return s.at(7, CANYON_HEIGHT + 17, towerV(s.plot.depth));
    }

    private static String canyon(Site s) {
        int width = s.plot.width;
        int depth = s.plot.depth;
        int height = CANYON_HEIGHT;
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState andesite = Blocks.ANDESITE.defaultBlockState();
        BlockState granite = Blocks.GRANITE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int u = 0; u < width; u++) {
            double center = depth / 2.0 + 9.0 * Math.sin(u / 14.0);
            boolean cave = u >= width - 18;
            for (int v = 0; v < depth; v++) {
                double off = Math.abs(v + 0.5 - center);
                for (int y = 0; y < height; y++) {
                    double half = 4.5 + y * 0.18;
                    boolean open = off < half;
                    if (cave && y > 9) {
                        open = false;
                    }
                    BlockState state;
                    if (open) {
                        state = air;
                    } else if (y == height - 1) {
                        state = grass;
                    } else if (y >= height - 4) {
                        state = dirt;
                    } else {
                        state = (y / 4) % 3 == 1 ? andesite : (y / 4) % 3 == 2 ? granite : stone;
                    }
                    s.quiet(u, y, v, state);
                }
                if (off < 4.5) {
                    s.quiet(u, -1, v, Blocks.GRAVEL.defaultBlockState());
                }
            }
        }
        int towerV = towerV(depth);
        s.fill(4, height, towerV - 2, 8, height + 15, towerV + 2, Blocks.STONE_BRICKS.defaultBlockState());
        s.fill(3, height + 16, towerV - 3, 9, height + 16, towerV + 3, Blocks.POLISHED_ANDESITE.defaultBlockState());
        s.fill(3, height + 17, towerV - 3, 9, height + 17, towerV - 3, Blocks.STONE_BRICK_WALL.defaultBlockState());
        for (int u = width - 16; u < width; u += 5) {
            double center = depth / 2.0 + 9.0 * Math.sin(u / 14.0);
            s.quiet(u, 9, (int) Math.floor(center), Blocks.GLOWSTONE.defaultBlockState());
        }
        BlockPos top = s.at(6, height + 17, towerV);
        s.signAt(top, s.plot.row == Row.NORTH ? 8 : 0, "Turm: F6", "Freecam durch", "die Schlucht,", "Höhle am Ende");
        s.sign(width / 2 - 8, -1, "Schlucht", "Start am Turm", "(Westen)");
        return ", Schlucht 112 Blöcke mit Turm (" + (height + 17) + " hoch) und Höhle am Ende";
    }

    private static String heatTrack(Site s) {
        int width = s.plot.width;
        int depth = s.plot.depth;
        BlockState track = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
        for (int u = 2; u < width - 2; u++) {
            for (int v = 2; v < depth - 2; v++) {
                boolean ring = u < 10 || u >= width - 10 || v < 10 || v >= depth - 10;
                if (ring) {
                    s.quiet(u, -1, v, track);
                }
            }
        }
        for (int u = 2; u < width - 2; u += 2) {
            BlockState curb = (u / 2) % 2 == 0 ? Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState() : Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
            s.quiet(u, -1, 10, curb);
            s.quiet(u, -1, depth - 11, curb);
        }
        s.fill(30, -1, 2, 30, -1, 9, Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState());
        int[][] pylons = {{6, 6}, {width - 7, 6}, {6, depth - 7}, {width - 7, depth - 7}};
        for (int[] pylon : pylons) {
            s.fill(pylon[0], 0, pylon[1], pylon[0], 4, pylon[1], Blocks.WOOL.pick(DyeColor.ORANGE).defaultBlockState());
        }
        s.drone(32.5, 5.5, HOT_SEVEN, -90.0F);
        s.drone(36.5, 3.5, DroneBuild.LONG_RANGE, -90.0F);
        s.sign(28, 1, "Hitze-Strecke", "7 Zoll LiPo 6S", "Vollgas ~100 s", "MOTOR OVERHEAT");
        s.sign(40, 1, "7 Zoll Li-Ion", "bleibt kühl", "(Vergleich)");
        return ", Rundkurs 172 × 60 mit 7 Zoll und LiPo 6S 1300 am Start (wird bei Dauervollgas heiß), daneben 7 Zoll mit Li-Ion";
    }

    private static StepResult surfaceStep(SelfTest.StepContext ctx) {
        return DrehweltChecks.surface(ctx, find("R4"));
    }

    private static StepResult triggerStep(SelfTest.StepContext ctx) {
        return DrehweltChecks.triggers(ctx, find("R5"));
    }

    private static StepResult heatStep(SelfTest.StepContext ctx) {
        return DrehweltChecks.heat(ctx, find("R8"));
    }

    private static StepResult freecamStep(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(FUTURE)) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Drehwelt geöffnet");
            }
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                Optional<RuleSpec> spec = WorldRuleSpecs.byName(MigrationValues.FREECAM_RULE);
                if (spec.isEmpty()) {
                    RULE.set("Regel " + MigrationValues.FREECAM_RULE + " fehlt");
                    return;
                }
                WorldRules.set(server, spec.get(), 1);
                RULE.set(MigrationValues.FREECAM_RULE + "=" + WorldRules.get(server, spec.get()));
            }));
            return StepResult.pending();
        }
        StepResult waiting = future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String rule = RULE.get();
        if (!rule.equals(MigrationValues.FREECAM_RULE + "=1")) {
            return StepResult.fail("Freecam-Regel am Server nicht an: " + rule);
        }
        Minecraft mc = Minecraft.getInstance();
        FreecamRules.Denial denial = FreecamSession.denialFromMenu(mc);
        if (!ClientWorldRules.INSTANCE.on(WorldRuleSpecs.ALLOW_FREECAM) || !denial.allowed()) {
            return ctx.tick() > 200 ? StepResult.fail("Client: Freecam-Regel " + (ClientWorldRules.INSTANCE.known() ? "bekannt" : "unbekannt")
                    + ", Start " + denial) : StepResult.pending();
        }
        ctx.log(rule + " am Server, beim Client an, Freecam darf starten");
        return StepResult.pass();
    }

    private static StepResult leaveStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0 && ctx.server() != null) {
            BlockPos start = SPAWN.get();
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(player.level(), start.getX() + 0.5, start.getY(), start.getZ() + 0.5, Set.of(), 0.0F, 10.0F, true);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        "setworldspawn " + start.getX() + " " + start.getY() + " " + start.getZ());
            }));
            return StepResult.pending();
        }
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("leaving")) {
            StepResult waiting = state.containsKey(FUTURE) ? future(ctx) : null;
            if (waiting != null) {
                return waiting;
            }
            state.put("leaving", ctx.tick());
        }
        return leaveAfter(ctx, (int) state.get("leaving"));
    }

    private static StepResult leaveAfter(SelfTest.StepContext ctx, int since) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == since) {
            PilotSession.INSTANCE.clearTestInput();
            if (mc.level != null) {
                mc.schedule(() -> mc.disconnectFromWorld(Component.literal("Propwash-Selbsttest Drehwelt gespeichert")));
            }
            return StepResult.pending();
        }
        if (mc.level == null && mc.player == null && mc.getSingleplayerServer() == null && ctx.tick() - since > 5) {
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    private static StepResult zipStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return ctx.tick() > 200 ? StepResult.fail("Drehwelt noch offen") : StepResult.pending();
        }
        Path world = mc.getLevelSource().getBaseDir().resolve(WORLD);
        Path dir = Services.PLATFORM.getGameDir().resolve(DIR);
        Path zip = dir.resolve(ZIP);
        try {
            if (!Files.isRegularFile(world.resolve("level.dat"))) {
                return StepResult.fail("Drehwelt nicht gespeichert: " + world);
            }
            Files.createDirectories(dir);
            Files.deleteIfExists(zip);
            int files = zipWorld(world, dir.resolve(LIST), zip);
            try (ZipFile check = new ZipFile(zip.toFile())) {
                if (check.getEntry(WORLD + "/level.dat") == null || check.getEntry(WORLD + "/" + LIST) == null) {
                    return StepResult.fail("Zip ohne level.dat oder " + LIST);
                }
                boolean regions = check.stream().anyMatch(e -> e.getName().endsWith(".mca"));
                if (!regions) {
                    return StepResult.fail("Zip ohne Regionsdateien");
                }
            }
            ctx.log(files + " Dateien in " + Services.PLATFORM.getGameDir().relativize(zip) + " (" + Files.size(zip) / 1024 + " KB)");
            return StepResult.pass();
        } catch (IOException e) {
            return StepResult.fail("Zip nicht geschrieben: " + e);
        }
    }

    static int zipWorld(Path world, @Nullable Path extra, Path zip) throws IOException {
        int count = 0;
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
            if (extra != null && Files.isRegularFile(extra)) {
                stream.putNextEntry(new ZipEntry(world.getFileName() + "/" + extra.getFileName()));
                Files.copy(extra, stream);
                stream.closeEntry();
                count++;
            }
        }
        return count;
    }
}
