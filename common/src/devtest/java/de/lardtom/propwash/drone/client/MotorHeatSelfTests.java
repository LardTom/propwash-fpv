package de.lardtom.propwash.drone.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.BuildTexts;
import de.lardtom.propwash.content.WorkbenchLayout;
import de.lardtom.propwash.content.client.DroneWorkbenchScreen;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseTour;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.BuildWarning;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class MotorHeatSelfTests {
    static final String PREFIX = "drone: Motor-Hitze";
    private static final String SHOTS = "bildschirmfotos: Motor-Hitze";
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String PHASE_TICK = "phaseTick";
    private static final String SOURCE = "source";
    private static final double OVER_THRESHOLD_C = 5.0;
    private static final double COOL_C = 60.0;
    private static final double ESC_HOT_C = 147.5;
    private static final double ESC_LIMIT_C = 140.0;
    private static final double BLINK_PERIOD = 0.8;
    private static final double BLINK_EARLY = 0.15;
    private static final long TICK_NANOS = 50_000_000L;
    private static final int HEAT_TICK = 5;
    private static final int SETTLE_TICKS = 15;
    private static final int TOAST_TICKS = 100;
    private static final int START_TICKS = 200;
    private static final int SCENE_TICKS = 200;
    private static final List<String> LOSES_POWER = List.of("lose power", "loses power", "verlieren Leistung", "Leistung sinkt");
    private static final List<String> MOTOR_THROTTLES = List.of("motor throttles", "motors throttle", "motor drosselt", "motoren drosseln",
            "motoren regeln ab", "motor regelt ab", "thermal throttling");
    private static final List<String> ESC_THROTTLES = List.of("ESC throttles back", "throttles back to protect itself", "regelt zum Selbstschutz ab",
            "ESC regelt ab");
    private static final List<String> LANGUAGES = List.of("en_us", "de_de");
    private static final List<String> MOTOR_KEYS = List.of("propwash.drone.motor_hot", "propwash.parts.tooltip.thermal_limits");
    private static final List<String> ESC_KEYS = List.of("propwash.drone.esc_derate", "propwash.content.workbench.warning.motor_thermal",
            "gamerule.propwash.hardware_limits.description");
    private static final AtomicInteger DRONE_ID = new AtomicInteger(-1);
    private static final DroneBuild HOT_SEVEN = new DroneBuild(DroneBuild.LONG_RANGE.frame(), StackType.AIO_20, DroneBuild.LONG_RANGE.motor(),
            DroneBuild.LONG_RANGE.prop(), DroneBuild.LONG_RANGE.video(), BatteryType.B6S_1300);

    private MotorHeatSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(PREFIX + " – heiße Motoren: Meldung „sehr heiß“, OSD MOTOR OVERHEAT, keine Leistungsgrenze", 900,
                MotorHeatSelfTests::motorStep);
        SelfTest.clientStep(PREFIX + " – heißer ESC: Meldung, OSD ESC OVERHEAT, Leistungsgrenze nach BLHeli_32", 900, MotorHeatSelfTests::escStep);
        SelfTest.clientStep(PREFIX + " – Texte: Motor verliert Leistung, ESC regelt ab", 40, MotorHeatSelfTests::textStep);
        SelfTest.clientStep(PREFIX + " – Aufräumen", 200, MotorHeatSelfTests::cleanupStep);
    }

    public static void registerShowcase() {
        if (!Screenshots.enabled()) {
            return;
        }
        shots("");
        SelfTest.switchStep(SHOTS + " – Sprache Deutsch", SHOTS + " – Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
        shots("_de");
        SelfTest.clientStep(SHOTS + " – Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
    }

    private static void shots(String suffix) {
        String label = suffix.isEmpty() ? "" : " deutsch";
        Screenshots.step(SHOTS + " – OSD-Warnung heiße Motoren" + label, "motor_heat_osd_motor" + suffix, 2, new OsdScene(false));
        Screenshots.step(SHOTS + " – OSD-Warnung heißer ESC" + label, "motor_heat_osd_esc" + suffix, 2, new OsdScene(true));
        Screenshots.step(SHOTS + " – Werkbank-Warnung beim 7″ mit 20-A-AIO, Liste der Hinweise" + label, "motor_heat_workbench" + suffix, 50,
                new IssueListScene(ShowcaseTour.workbenchScene(HOT_SEVEN, "7\" AIO 20A")));
    }

    private record IssueListScene(Screenshots.Scene bench) implements Screenshots.Scene {
        private static final int ISSUE_ROW = 9;
        private static final int HOVER_X = 30;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            return this.bench.prepare(ctx);
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            this.bench.hold(ctx);
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
                return;
            }
            int top = WorkbenchLayout.STATS_Y0 + 6 * WorkbenchLayout.STATS_ROW + 1;
            int rows = Math.max(1, (WorkbenchLayout.ISSUES_Y1 - top) / ISSUE_ROW);
            double guiX = screen.panelLeft() + WorkbenchLayout.STATS_X0 + HOVER_X;
            double guiY = screen.panelTop() + top + (rows - 1) * ISSUE_ROW + ISSUE_ROW / 2;
            double x = guiX * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = guiY * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            this.bench.finish(ctx);
        }
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(PHASE_TICK, ctx.tick());
    }

    private static int inPhase(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(PHASE_TICK, 0);
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static void placeAndFly(MinecraftServer server, DroneBuild build) {
        ServerPlayer player = serverPlayer(server);
        ServerLevel level = player.level();
        int x = player.getBlockX() + 4;
        int z = player.getBlockZ() - 3;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos ground = new BlockPos(x, top - 1, z);
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult use = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(x + 0.5, top, z + 0.5), Direction.UP, ground, false)));
        if (!use.consumesAction()) {
            throw new IllegalStateException("Platzieren lieferte " + use);
        }
        List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(ground).inflate(3.0),
                d -> d.build().equals(build));
        if (drones.isEmpty()) {
            throw new IllegalStateException("keine Drohne nach dem Platzieren");
        }
        QuadcopterEntity drone = drones.getFirst();
        DRONE_ID.set(drone.getId());
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        remote.use(level, player, InteractionHand.MAIN_HAND);
        if (!drone.isFlownBy(player.getUUID()) || drone.pilotMode() != PilotMode.LINE_OF_SIGHT) {
            throw new IllegalStateException("Sichtflug nicht gestartet");
        }
    }

    private static @Nullable StepResult startSession(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        if (!ctx.state().containsKey(FUTURE)) {
            if (ctx.tick() < DronePiloting.REMOTE_DEBOUNCE_TICKS + 2) {
                return StepResult.pending();
            }
            ctx.state().put(FUTURE, ctx.onServer(server -> placeAndFly(server, DroneBuild.FREESTYLE)));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Start fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        QuadcopterEntity drone = session.drone();
        if (!future.isDone() || !session.isLineOfSight() || drone == null || drone.getId() != DRONE_ID.get() || session.sim() == null) {
            return ctx.tick() > START_TICKS ? StepResult.fail("keine Sichtflug-Sitzung") : StepResult.pending();
        }
        return null;
    }

    private static void heat(DroneSim sim, double motorC, double escC) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            snapshot.motorTempC[i] = motorC;
            snapshot.escTempC[i] = escC;
        }
        sim.copyStateFrom(snapshot);
    }

    private static List<OsdWarnings.Warning> warnings(Telemetry t) {
        OsdData osd = new OsdData();
        osd.active = true;
        osd.applyTelemetry(t);
        List<OsdWarnings.Warning> out = new ArrayList<>();
        OsdWarnings.collect(osd, out);
        return out;
    }

    private static double minLimit(Telemetry t) {
        double min = 1.0;
        for (double limit : t.motorPowerLimit) {
            min = Math.min(min, limit);
        }
        return min;
    }

    private static StepResult heatStep(SelfTest.StepContext ctx, boolean esc) {
        if (phase(ctx) == 0) {
            StepResult started = startSession(ctx);
            if (started != null) {
                return started;
            }
            next(ctx);
        }
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        Minecraft mc = Minecraft.getInstance();
        String key = esc ? "propwash.drone.esc_derate" : "propwash.drone.motor_hot";
        OsdWarnings.Warning wanted = esc ? OsdWarnings.Warning.ESC_HOT : OsdWarnings.Warning.MOTOR_HOT;
        switch (phase(ctx)) {
            case 1 -> {
                if (sim == null) {
                    return StepResult.fail("Sitzung verloren");
                }
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
                if (inPhase(ctx) == HEAT_TICK) {
                    ThemedToast old = ThemedToast.find(mc.gui.toastManager(), PilotSession.TOAST_TOKEN);
                    if (old != null) {
                        old.hide();
                    }
                    double motor = esc ? COOL_C : sim.params().motorDerateStartC + OVER_THRESHOLD_C;
                    heat(sim, motor, esc ? ESC_HOT_C : COOL_C);
                }
                if (inPhase(ctx) < HEAT_TICK + SETTLE_TICKS) {
                    return StepResult.pending();
                }
                Telemetry t = sim.telemetry();
                List<String> problems = new ArrayList<>();
                double limit = minLimit(t);
                List<OsdWarnings.Warning> shown = warnings(t);
                if (esc) {
                    if (!t.escDerating || t.motorHot) {
                        problems.add("Hitze falsch gemeldet: ESC " + t.escDerating + ", Motoren " + t.motorHot);
                    }
                    if (!(limit < 0.8 && limit > 0.25)) {
                        problems.add("Leistungsgrenze folgt nicht der Kennlinie (" + t.escTempC[0] + " °C): " + limit);
                    }
                    if (!shown.contains(OsdWarnings.Warning.ESC_HOT) || shown.contains(OsdWarnings.Warning.MOTOR_HOT)) {
                        problems.add("OSD warnt falsch: " + shown);
                    }
                } else {
                    if (!t.motorHot || t.escDerating) {
                        problems.add("Hitze falsch gemeldet: Motoren " + t.motorHot + ", ESC " + t.escDerating);
                    }
                    if (limit != 1.0) {
                        problems.add("heiße Motoren drosseln: Grenze " + limit);
                    }
                    if (!shown.contains(OsdWarnings.Warning.MOTOR_HOT) || shown.contains(OsdWarnings.Warning.ESC_HOT)) {
                        problems.add("OSD warnt falsch: " + shown);
                    }
                }
                if (!problems.isEmpty()) {
                    return StepResult.fail(String.join("; ", problems));
                }
                ctx.state().put("limit", limit);
                ctx.state().put("temp", esc ? t.escTempC[0] : t.motorTempC[0]);
                ctx.state().put("osd", Component.translatable(wanted.translationKey()).getString());
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                String expected = Component.translatable(key).getString();
                ThemedToast toast = ThemedToast.find(mc.gui.toastManager(), PilotSession.TOAST_TOKEN);
                if (toast == null || !toast.title().getString().equals(expected)) {
                    return inPhase(ctx) > TOAST_TICKS
                            ? StepResult.fail("keine Meldung „" + expected + "“, Toast: " + (toast == null ? "-" : toast.title().getString()))
                            : StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "%s: Meldung „%s“, OSD „%s“, Leistungsgrenze %.2f bei %.1f °C", esc ? "ESC" : "Motoren", expected,
                        ctx.state().get("osd"), (double) ctx.state().get("limit"), (double) ctx.state().get("temp")));
                toast.hide();
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                session.clearTestInput();
                if (session.isActive()) {
                    session.requestStop();
                    return StepResult.pending();
                }
                return StepResult.pass();
            }
        }
    }

    private static StepResult motorStep(SelfTest.StepContext ctx) {
        return heatStep(ctx, false);
    }

    private static StepResult escStep(SelfTest.StepContext ctx) {
        return heatStep(ctx, true);
    }

    private static boolean containsAny(String text, List<String> words) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (lower.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String motorWording(String what, String text) {
        if (containsAny(text, MOTOR_THROTTLES)) {
            return what + " behauptet weiter, der Motor drossle: " + text;
        }
        return containsAny(text, LOSES_POWER) ? null : what + " sagt nicht, dass der heiße Motor Leistung verliert: " + text;
    }

    private static @Nullable String escWording(String what, String text) {
        if (containsAny(text, MOTOR_THROTTLES)) {
            return what + " behauptet weiter, der Motor drossle: " + text;
        }
        return containsAny(text, ESC_THROTTLES) ? null : what + " sagt nicht, dass der ESC abregelt: " + text;
    }

    private static StepResult textStep(SelfTest.StepContext ctx) {
        List<String> problems = new ArrayList<>();
        String bench = BuildTexts.warning(BuildWarning.MOTOR_THERMAL).getString();
        String tooltip = Component.translatable("propwash.parts.tooltip.thermal_limits", "130", "150").getString();
        String rule = Component.translatable("gamerule.propwash.hardware_limits.description").getString();
        for (String problem : new String[]{escWording("Werkbank-Warnung", bench), motorWording("Teile-Tooltip", tooltip),
                motorWording("Welt-Regel", rule), escWording("Welt-Regel", rule)}) {
            if (problem != null) {
                problems.add(problem);
            }
        }
        if (!BuildAnalysis.of(HOT_SEVEN).warnings().contains(BuildWarning.MOTOR_THERMAL)) {
            problems.add("Werkbank warnt beim 7″ mit 20-A-AIO und 6S-LiPo nicht vor Dauervollgas im Stand");
        }
        for (DroneBuild build : DroneBuild.PRESETS) {
            if (BuildAnalysis.of(build).warnings().contains(BuildWarning.MOTOR_THERMAL)) {
                problems.add("Werkbank warnt beim Preset " + build.frame().key() + " vor Hitze");
            }
        }
        for (String code : LANGUAGES) {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                    .getResource(Identifier.fromNamespaceAndPath("propwash", "lang/" + code + ".json"));
            if (resource.isEmpty()) {
                problems.add("Sprachdatei " + code + " fehlt");
                continue;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject texts = JsonParser.parseReader(reader).getAsJsonObject();
                for (String key : MOTOR_KEYS) {
                    String problem = texts.has(key) ? motorWording(code + " " + key, texts.get(key).getAsString()) : code + " ohne " + key;
                    if (problem != null) {
                        problems.add(problem);
                    }
                }
                for (String key : ESC_KEYS) {
                    String problem = texts.has(key) ? escWording(code + " " + key, texts.get(key).getAsString()) : code + " ohne " + key;
                    if (problem != null) {
                        problems.add(problem);
                    }
                }
                if (!texts.has(OsdWarnings.Warning.ESC_HOT.translationKey())) {
                    problems.add(code + " ohne OSD-Text ESC OVERHEAT");
                }
                if (texts.has("propwash.drone.motor_derate")) {
                    problems.add(code + " hat noch den alten Schlüssel propwash.drone.motor_derate");
                }
            } catch (IOException | RuntimeException e) {
                problems.add("Sprachdatei " + code + " nicht lesbar: " + e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        ctx.log("Werkbank „" + bench + "“, Tooltip „" + tooltip + "“, " + (MOTOR_KEYS.size() + ESC_KEYS.size()) + " Texte in " + LANGUAGES + " geprüft");
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.clearTestInput();
        if (PilotSession.INSTANCE.isActive()) {
            PilotSession.INSTANCE.requestStop();
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(DRONE_ID.get()) instanceof QuadcopterEntity drone) {
                        drone.discard();
                    }
                }
                ServerPlayer player = serverPlayer(server);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen fehlgeschlagen: " + future.exceptionNow().getMessage());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static final class HotFlight implements OsdSampler.Source {
        private final DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 7L);
        private final PilotInput input = new PilotInput();
        private final SimEnvironment environment = new SimEnvironment();
        private final de.lardtom.propwash.sim.StepResult result = new de.lardtom.propwash.sim.StepResult();
        private final Vector3d position = new Vector3d();
        private final boolean esc;
        private final double homeX;
        private final double homeY;
        private final double homeZ;

        HotFlight(boolean esc, double homeX, double homeY, double homeZ) {
            this.esc = esc;
            this.homeX = homeX;
            this.homeY = homeY;
            this.homeZ = homeZ;
            this.sim.reset(homeX, homeY + 150.0, homeZ, 0.0);
            this.sim.step(0L, this.input, this.environment, this.result);
            this.input.armSwitch = true;
            this.sim.step(0L, this.input, this.environment, this.result);
            this.input.throttle = 0.3;
        }

        void tick() {
            double motor = this.esc ? COOL_C : this.sim.params().motorDerateStartC + OVER_THRESHOLD_C;
            heat(this.sim, motor, this.esc ? ESC_HOT_C : COOL_C);
            this.sim.step(TICK_NANOS, this.input, this.environment, this.result);
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void fill(OsdData d) {
            d.applySim(this.sim);
            this.sim.getPosition(this.position);
            d.hasPosition = true;
            d.x = this.position.x;
            d.y = this.position.y;
            d.z = this.position.z;
            d.hasHome = true;
            d.homeX = this.homeX;
            d.homeY = this.homeY;
            d.homeZ = this.homeZ;
            d.craftName = "Selftest";
            d.radioConnected = true;
            d.linkStrength = 0.9;
            d.videoSystem = VideoSystem.ANALOG;
            d.videoSignal = 0.8;
            d.stickThrottle = (float) this.input.throttle;
        }
    }

    private record OsdScene(boolean esc) implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Map<String, Object> state = ctx.state();
            if (!state.containsKey(SOURCE)) {
                Player player = Minecraft.getInstance().player;
                if (player == null) {
                    return StepResult.fail("kein Spieler");
                }
                HotFlight flight = new HotFlight(this.esc, player.getX(), player.getY(), player.getZ());
                OsdHud.sampler().setOverride(flight);
                state.put(SOURCE, flight);
                return StepResult.pending();
            }
            HotFlight flight = (HotFlight) state.get(SOURCE);
            flight.tick();
            OsdData data = OsdHud.sampler().data();
            List<OsdWarnings.Warning> shown = new ArrayList<>();
            OsdWarnings.collect(data, shown);
            OsdWarnings.Warning wanted = this.esc ? OsdWarnings.Warning.ESC_HOT : OsdWarnings.Warning.MOTOR_HOT;
            int index = OsdWarnings.visibleIndex(shown, data.blinkSeconds);
            boolean visible = index >= 0 && shown.get(index) == wanted && data.blinkSeconds % BLINK_PERIOD < BLINK_EARLY;
            if (!visible) {
                return ctx.tick() > SCENE_TICKS ? StepResult.fail("OSD zeigt " + wanted + " nicht: " + shown) : StepResult.pending();
            }
            Telemetry t = flight.sim.telemetry();
            if (this.esc != t.escDerating || this.esc == t.motorHot) {
                return StepResult.fail("Hitze falsch gemeldet: ESC " + t.escDerating + ", Motoren " + t.motorHot);
            }
            ctx.log(String.format(Locale.ROOT, "Bild mit „%s“, Motor %.0f °C, ESC %.0f °C (Grenze ab %.0f °C), Leistungsgrenze %.2f",
                    Component.translatable(wanted.translationKey()).getString(), t.motorTempC[0], t.escTempC[0], ESC_LIMIT_C, minLimit(t)));
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (ctx.state().get(SOURCE) instanceof HotFlight flight) {
                flight.tick();
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            OsdHud.sampler().setOverride(null);
        }
    }
}
