package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SimulatedPilotFeed;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdLayoutCodec;
import de.lardtom.propwash.osd.SpectatorCountS2C;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.osd.client.SpectatorCountClient;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.race.client.GateRenderer;
import de.lardtom.propwash.race.client.ScoreAreaRenderer;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.SpeedUnit;
import de.lardtom.propwash.ui.client.UiConfig;
import de.lardtom.propwash.video.PilotFeed;
import de.lardtom.propwash.video.PilotFeedServer;
import de.lardtom.propwash.video.SpectateServer;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SpectateFeedSelfTests {
    public static final String WATCH_STEP = "video: Zuschauen – Renn-Zustand, OSD-Layout und Sticks des Piloten beim Zuschauer";
    public static final String PILOT_STEP = "video: Zuschauen – Pilot schickt Renn-Zustand, OSD-Layout und Sticks nur mit Zuschauer";
    private static final String PILOT_NAME = "Rennpilotin";
    private static final int GATES = 3;
    private static final double GATE_SPACING = 8.0;
    private static final int COUNTDOWN_TICKS = 30;
    private static final int RACE_TICKS = 60;
    private static final int QUIET_TICKS = 30;
    private static final int WATCHED_TICKS = 50;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";

    private static @Nullable Scene scene;

    private SpectateFeedSelfTests() {
    }

    private static final class Scene {
        final List<UUID> gates = new ArrayList<>();
        final List<Integer> gateEntities = new ArrayList<>();
        int[] gateIndices = new int[0];
        int course = GateEntity.NO_COURSE;
        @Nullable UUID drone;
        int droneId = -1;
        @Nullable ItemStack previousHead;
        @Nullable ItemStack previousHand;
        volatile PilotFeedServer.@Nullable Stats stats;
    }

    public static void register() {
        SelfTest.clientStep(WATCH_STEP, 1400, SpectateFeedSelfTests::watchStep);
        SelfTest.clientStep(PILOT_STEP, 1200, SpectateFeedSelfTests::pilotStep);
    }

    private static String phase(SelfTest.StepContext ctx, String initial) {
        return (String) ctx.state().computeIfAbsent(PHASE, key -> initial);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().computeIfAbsent(SINCE, key -> ctx.tick());
    }

    private static StepResult next(SelfTest.StepContext ctx, String phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static @Nullable StepResult waitServer(SelfTest.StepContext ctx, Scene s, String what) {
        Object value = ctx.state().get(FUTURE);
        if (!(value instanceof CompletableFuture<?> future)) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            ctx.state().remove(FUTURE);
            return cleanup(ctx, s, StepResult.fail(what + ": " + future.exceptionNow().getMessage()));
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(FUTURE);
        return null;
    }

    private static StepResult watchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        SpectateSession session = SpectateSession.INSTANCE;
        Scene s = scene;
        String phase = phase(ctx, "setup");
        int since = since(ctx);
        if (s != null) {
            StepResult waiting = waitServer(ctx, s, phase);
            if (waiting != null) {
                return waiting;
            }
        }
        switch (phase) {
            case "setup" -> {
                if (PilotSession.INSTANCE.isActive()) {
                    PilotSession.INSTANCE.requestStop();
                    return since > 200 ? StepResult.fail("alte Flugsitzung endet nicht") : StepResult.pending();
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                Scene fresh = new Scene();
                scene = fresh;
                state.put("own_layout", OsdLayoutCodec.encode(OsdClient.CONFIG.get()));
                state.put("own_unit", UiConfig.speedUnit());
                state.put(FUTURE, ctx.onServer(server -> buildWatched(server, fresh)));
                return next(ctx, "list");
            }
            case "list" -> {
                if (s == null || s.drone == null) {
                    return StepResult.fail("kein Aufbau");
                }
                ChannelListS2C.Entry entry = session.entry(s.drone);
                if (entry == null || SpectateSession.goggles(mc) == null) {
                    return since > 300 ? cleanup(ctx, s, StepResult.fail("Kanal der Drohne oder Brille fehlt auf dem Client")) : StepResult.pending();
                }
                if (!session.request(mc, entry)) {
                    return cleanup(ctx, s, StepResult.fail("Zuschauen abgelehnt: " + session.blocker(mc, entry)));
                }
                return next(ctx, "watch");
            }
            case "watch" -> {
                if (!session.watching() || session.drone() == null || s == null) {
                    return since > 300 ? cleanup(ctx, s, StepResult.fail("Zuschauen startet nicht: " + session.phase() + ", " + session.lastResult()))
                            : StepResult.pending();
                }
                state.put("feed_tick", 0);
                state.put("feeds_from", WatchedPilot.feeds());
                return next(ctx, "countdown");
            }
            case "countdown" -> {
                if (s == null || !session.watching()) {
                    return cleanup(ctx, s, StepResult.fail("Zuschauen endete im Countdown"));
                }
                int k = feed(ctx, s, true);
                int received = WatchedPilot.feeds() - (int) state.get("feeds_from");
                if (k < COUNTDOWN_TICKS || received < COUNTDOWN_TICKS / 2) {
                    return since > 400 ? cleanup(ctx, s, StepResult.fail("Countdown des Piloten kommt beim Zuschauer nicht an: " + WatchedPilot.feed()
                            + ", " + received + " von " + k + " Feeds empfangen")) : StepResult.pending();
                }
                List<String> problems = problems(state);
                PilotFeed watched = WatchedPilot.feed();
                ScoreAreaRenderer.Drawn start = opening(s.gateEntities.getFirst());
                if (watched == null || !watched.countdown()) {
                    problems.add("Countdown des Piloten kommt beim Zuschauer nicht an (" + received + " von " + k + " Feeds empfangen, "
                            + "Renn-Zustand des Zuschauers selbst: " + ClientRaceTracker.INSTANCE.tracker().phase() + ")");
                } else if (start == null || start.color() != GateRenderer.LIGHT_RED) {
                    problems.add("Start-Gate im Countdown des Piloten nicht rot mit Durchflugfläche: " + ScoreAreaRenderer.lastDrawn());
                } else {
                    state.put("countdown_log", String.format(Locale.ROOT, "Countdown %.1f s, Start-Gate rot mit Durchflugfläche",
                            watched.countdownRemainingNanos(WatchedPilot.ageNanos()) / 1.0E9));
                }
                state.put("feeds_from", WatchedPilot.feeds());
                return next(ctx, "race");
            }
            case "race" -> {
                if (s == null || !session.watching()) {
                    return cleanup(ctx, s, StepResult.fail("Zuschauen endete im Rennen"));
                }
                feed(ctx, s, false);
                int received = WatchedPilot.feeds() - (int) state.get("feeds_from");
                OsdData data = OsdHud.sampler().data();
                if (received > 10) {
                    state.merge("stick_min", data.stickRoll, (a, b) -> Math.min((float) a, (float) b));
                    state.merge("stick_max", data.stickRoll, (a, b) -> Math.max((float) a, (float) b));
                    if (OsdHud.drewLastFrame(OsdElement.STICK_OVERLAY)) {
                        state.merge("sticks_drawn", 1, (a, b) -> (int) a + (int) b);
                    }
                    if (OsdHud.drewLastFrame(OsdElement.LAP) || OsdHud.drewLastFrame(OsdElement.LAP_TIME) || OsdHud.drewLastFrame(OsdElement.GATE)) {
                        state.merge("race_drawn", 1, (a, b) -> (int) a + (int) b);
                    }
                }
                if (received >= RACE_TICKS / 3 && Screenshots.enabled() && !state.containsKey("cleared")) {
                    Screenshots.clearOverlays(mc);
                    state.put("cleared", ctx.tick());
                }
                if (state.get("cleared") instanceof Integer cleared && ctx.tick() - cleared >= 5 && !state.containsKey("shot")) {
                    state.put("shot", Screenshots.capture("spectate_pilot_osd"));
                }
                if (received < RACE_TICKS) {
                    return since > 600 ? cleanup(ctx, s, StepResult.fail("Rennen des Piloten kommt beim Zuschauer nicht an: " + received + " Feeds"))
                            : StepResult.pending();
                }
                List<String> problems = problems(state);
                checkRace(s, data, state, problems);
                if (!problems.isEmpty()) {
                    return cleanup(ctx, s, StepResult.fail(String.join("; ", problems)));
                }
                session.stop(mc, true);
                return next(ctx, "stopped");
            }
            case "stopped" -> {
                if (since < 5) {
                    return StepResult.pending();
                }
                if (OsdHud.config() != OsdClient.CONFIG.get() || WatchedPilot.feed() != null || OsdHud.speedUnit() != UiConfig.speedUnit()) {
                    return cleanup(ctx, s, StepResult.fail("nach dem Zuschauen zeigt das OSD weiter Layout oder Werte des Piloten"));
                }
                if (!OsdLayoutCodec.encode(OsdClient.CONFIG.get()).equals(state.get("own_layout")) || UiConfig.speedUnit() != state.get("own_unit")) {
                    return cleanup(ctx, s, StepResult.fail("eigene OSD-Einstellungen nach dem Zuschauen verändert"));
                }
                Object shot = state.get("shot");
                if (shot instanceof CompletableFuture<?> picture && !picture.isDone()) {
                    return since > 200 ? cleanup(ctx, s, StepResult.fail("Bildschirmfoto wird nicht fertig")) : StepResult.pending();
                }
                ctx.log(state.get("countdown_log") + "; " + state.get("race_log") + "; danach wieder eigenes OSD"
                        + (shot instanceof CompletableFuture<?> picture ? ", Bild " + Screenshots.dir().resolve("spectate_pilot_osd.png") + " ("
                        + picture.join() + ")" : ""));
                return cleanup(ctx, s, StepResult.pass());
            }
            default -> {
                return cleanup(ctx, s, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private static int feed(SelfTest.StepContext ctx, Scene s, boolean countdown) {
        int k = (int) ctx.state().merge("feed_tick", 1, (a, b) -> (int) a + (int) b);
        UUID id = s.drone;
        long remaining = Math.max(500_000_000L, SimulatedPilotFeed.COUNTDOWN_NANOS - k * 50_000_000L);
        PilotFeed feed = countdown ? SimulatedPilotFeed.countdown(k, s.course, s.gateIndices, remaining)
                : SimulatedPilotFeed.racing(k, s.course, s.gateIndices, 1);
        ctx.onServer(server -> {
            QuadcopterEntity drone = id == null ? null : serverDrone(server, id);
            if (drone != null) {
                PilotFeedServer.simulate(drone, feed);
            }
        });
        return k;
    }

    @SuppressWarnings("unchecked")
    private static List<String> problems(Map<String, Object> state) {
        return (List<String>) state.computeIfAbsent("problems", key -> new ArrayList<String>());
    }

    private static void checkRace(Scene s, OsdData data, Map<String, Object> state, List<String> problems) {
        OsdConfig shown = OsdHud.config();
        if (shown == OsdClient.CONFIG.get() || shown.preset != SimulatedPilotFeed.PRESET || !SimulatedPilotFeed.COLOR.hex().equalsIgnoreCase(shown.color)) {
            problems.add("Zuschauer sieht sein eigenes OSD statt Layout " + SimulatedPilotFeed.PRESET.key() + " in " + SimulatedPilotFeed.COLOR.hex()
                    + " des Piloten: " + shown.preset.key() + " " + shown.color + ", " + WatchedPilot.looks() + " Layouts empfangen");
        }
        if (OsdHud.speedUnit() != SimulatedPilotFeed.UNIT || WatchedPilot.stickMode() != SimulatedPilotFeed.STICK_MODE) {
            problems.add("Einheit oder Stick-Mode des Piloten fehlen: " + OsdHud.speedUnit() + ", " + WatchedPilot.stickMode());
        }
        if (!OsdLayoutCodec.encode(OsdClient.CONFIG.get()).equals(state.get("own_layout")) || UiConfig.speedUnit() != state.get("own_unit")) {
            problems.add("eigene OSD-Einstellungen beim Zuschauen verändert");
        }
        if (!data.spectating || !data.racing || data.nextGate != 1 || data.gateCount != GATES || data.lap < 1 || data.currentLapNanos <= 0L) {
            problems.add(String.format(Locale.ROOT, "Renn-Zustand des Piloten fehlt im OSD: zuschauen=%s Rennen=%s Gate %d/%d Runde %d Zeit %.2f s",
                    data.spectating, data.racing, data.nextGate, data.gateCount, data.lap, data.currentLapNanos / 1.0E9));
        }
        if ((int) state.getOrDefault("race_drawn", 0) == 0) {
            problems.add("Runde, Rundenzeit und Gate werden beim Zuschauen nicht gezeichnet");
        }
        float min = (float) state.getOrDefault("stick_min", 0.0F);
        float max = (float) state.getOrDefault("stick_max", 0.0F);
        if (max - min < 1.0F || (int) state.getOrDefault("sticks_drawn", 0) == 0) {
            problems.add(String.format(Locale.ROOT, "Sticks des Piloten bewegen sich beim Zuschauen nicht: Roll %.2f bis %.2f, gezeichnet in %s Bildern",
                    min, max, state.getOrDefault("sticks_drawn", 0)));
        }
        if (!data.armed || data.flightSeconds < 30.0 || Math.abs(data.chargeFraction - 0.82) > 0.01 || data.cells != 6) {
            problems.add(String.format(Locale.ROOT, "Werte des Piloten fehlen: scharf=%s Flugzeit %.1f s Akku %.2f Zellen %d", data.armed,
                    data.flightSeconds, data.chargeFraction, data.cells));
        }
        ScoreAreaRenderer.Drawn active = opening(s.gateEntities.get(1));
        if (active == null || active.color() != GateRenderer.LIGHT_GREEN) {
            problems.add("aktives Gate 2 des Piloten ohne grüne Durchflugfläche: " + ScoreAreaRenderer.lastDrawn());
        }
        if (opening(s.gateEntities.getFirst()) != null) {
            problems.add("Start-Gate im Rennen weiter als aktiv gezeichnet: " + ScoreAreaRenderer.lastDrawn());
        }
        StickMode mode = WatchedPilot.stickMode();
        state.put("race_log", String.format(Locale.ROOT, "Rennen: Gate %d/%d grün mit Durchflugfläche, Runde %d, %.2f s, Sticks Roll %.2f bis %.2f "
                        + "(gezeichnet in %s Bildern), Layout %s in %s, %s, Mode %s, Flugzeit %.1f s, %d Feeds",
                data.nextGate + 1, data.gateCount, data.lap, data.currentLapNanos / 1.0E9, min, max, state.get("sticks_drawn"), shown.preset.key(),
                shown.color, OsdHud.speedUnit(), mode == null ? "-" : Integer.toString(mode.number()), data.flightSeconds, WatchedPilot.feeds()));
    }

    private static ScoreAreaRenderer.@Nullable Drawn opening(int entityId) {
        for (ScoreAreaRenderer.Drawn drawn : ScoreAreaRenderer.lastDrawn()) {
            if (drawn.entityId() == entityId && drawn.kind() == ScoreAreaRenderer.Kind.OPENING) {
                return drawn;
            }
        }
        return null;
    }

    private static void buildWatched(MinecraftServer server, Scene s) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        buildCourse(level, player, new Vec3(eye.x, player.getY(), eye.z - 8.0), s);
        QuadcopterEntity drone = spawn(level, new Vec3(eye.x, player.getY() + 1.2, eye.z - 3.0), DroneBuild.RACER);
        drone.setNoGravity(true);
        s.drone = drone.getUUID();
        s.droneId = drone.getId();
        if (s.previousHead == null) {
            s.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new).orElseThrow(() -> new IllegalStateException("Analog-Brille fehlt"));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        SpectateServer.simulateTransmitter(drone, PILOT_NAME);
        SpectateServer.refreshSoon();
        PilotFeedServer.simulateLook(drone, SimulatedPilotFeed.look());
    }

    private static void buildCourse(ServerLevel level, ServerPlayer player, Vec3 first, Scene s) {
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        float yaw = player.getYRot();
        player.setYRot(180.0F);
        try {
            for (int i = 0; i < GATES; i++) {
                GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
                if (gate == null) {
                    throw new IllegalStateException("Gate nicht erzeugt");
                }
                gate.setSize(GateSize.MEDIUM);
                gate.applyGeometry(GateGeometry.of(first.x, first.y, first.z - i * GATE_SPACING, 0.0F, GateSize.MEDIUM));
                if (!level.addFreshEntity(gate)) {
                    throw new IllegalStateException("Gate nicht in die Welt gesetzt");
                }
                s.gates.add(gate.getUUID());
                s.gateEntities.add(gate.getId());
                CourseToolItem.useOnGate(tool, player, gate);
            }
        } finally {
            player.setYRot(yaw);
        }
        s.course = CourseToolItem.courseOf(tool);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(s.course);
        if (course == null || course.gates.size() != GATES) {
            throw new IllegalStateException("Kurs nicht angelegt: " + (course == null ? "keiner" : course.gates.size() + " Gates"));
        }
        int[] indices = new int[GATES];
        for (int i = 0; i < GATES; i++) {
            if (!(level.getEntity(s.gates.get(i)) instanceof GateEntity gate)) {
                throw new IllegalStateException("Gate " + i + " verschwunden");
            }
            indices[i] = gate.gateIndex();
        }
        s.gateIndices = indices;
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 pos, DroneBuild build) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(build);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        drone.setCustomName(Component.literal("Zuschauer-Feed"));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        drone.setAttitude(new Quaternionf());
        return drone;
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved()) {
                return drone;
            }
        }
        return null;
    }

    private static StepResult pilotStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        PilotSession session = PilotSession.INSTANCE;
        Scene s = scene;
        String phase = phase(ctx, "setup");
        int since = since(ctx);
        if (s != null) {
            StepResult waiting = waitServer(ctx, s, phase);
            if (waiting != null) {
                return waiting;
            }
        }
        switch (phase) {
            case "setup" -> {
                if (SpectateSession.INSTANCE.active()) {
                    SpectateSession.INSTANCE.stop(mc, true);
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                Scene fresh = new Scene();
                scene = fresh;
                state.put(FUTURE, ctx.onServer(server -> buildPilot(server, fresh)));
                return next(ctx, "fpv");
            }
            case "fpv" -> {
                QuadcopterEntity drone = session.drone();
                if (s == null || !session.isFpv() || drone == null) {
                    return since > 300 ? cleanup(ctx, s, StepResult.fail("FPV startet nicht")) : StepResult.pending();
                }
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                if (ClientRaceTracker.INSTANCE.courseId() != s.course) {
                    return since > 200 ? cleanup(ctx, s, StepResult.fail("Kurs " + s.course + " am Start nicht gewählt: "
                            + ClientRaceTracker.INSTANCE.courseId())) : StepResult.pending();
                }
                SpectatorCountClient.accept(new SpectatorCountS2C(drone.getId(), 0));
                state.put("sent_before", PilotFeedClient.sent());
                return next(ctx, "quiet");
            }
            case "quiet" -> {
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                if (since < QUIET_TICKS) {
                    return StepResult.pending();
                }
                if (PilotFeedClient.sent() != (int) state.get("sent_before") || PilotFeedClient.watched()) {
                    return cleanup(ctx, s, StepResult.fail("Pilot schickt ohne Zuschauer " + (PilotFeedClient.sent() - (int) state.get("sent_before"))
                            + " Feeds"));
                }
                QuadcopterEntity drone = session.drone();
                if (drone == null || s == null) {
                    return cleanup(ctx, s, StepResult.fail("Drohne weg"));
                }
                SpectatorCountClient.accept(new SpectatorCountS2C(drone.getId(), 1));
                ClientRaceTracker race = ClientRaceTracker.INSTANCE;
                state.put("countdown", race.beginCountdown(race.now(), 2L * LapTracker.SECOND, 1));
                state.put("sent_before", PilotFeedClient.sent());
                return next(ctx, "watched");
            }
            case "watched" -> {
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                QuadcopterEntity drone = session.drone();
                if (drone == null || s == null) {
                    return cleanup(ctx, s, StepResult.fail("Drohne weg"));
                }
                if (since < WATCHED_TICKS) {
                    return StepResult.pending();
                }
                UUID pilot = mc.player == null ? null : mc.player.getUUID();
                state.put("expected_layout", OsdLayoutCodec.encode(OsdClient.CONFIG.get()));
                state.put(FUTURE, ctx.onServer(server -> s.stats = pilot == null ? null : PilotFeedServer.stats(pilot).orElse(null)));
                return next(ctx, "check");
            }
            case "check" -> {
                if (s == null) {
                    return StepResult.fail("kein Aufbau");
                }
                PilotFeedServer.Stats stats = s.stats;
                int sent = PilotFeedClient.sent() - (int) state.get("sent_before");
                if (stats == null || stats.last() == null || stats.look() == null) {
                    return cleanup(ctx, s, StepResult.fail("Server bekommt keinen Feed vom beobachteten Piloten: " + sent + " gesendet, " + stats));
                }
                PilotFeed last = stats.last();
                ClientRaceTracker race = ClientRaceTracker.INSTANCE;
                if (stats.feeds() < WATCHED_TICKS - 10 || stats.feedsPerSecond() < 18.0) {
                    return cleanup(ctx, s, StepResult.fail(String.format(Locale.ROOT, "Feed zu selten: %d in %d Ticks, %.1f je Sekunde",
                            stats.feeds(), WATCHED_TICKS, stats.feedsPerSecond())));
                }
                if (last.courseId() != s.course || last.startGateIndex() != s.gateIndices[0]
                        || (Boolean.TRUE.equals(state.get("countdown")) && last.phase() == LapTracker.Phase.IDLE)) {
                    return cleanup(ctx, s, StepResult.fail("Renn-Zustand fehlt im Feed: Kurs " + last.courseId() + " statt " + s.course + ", Phase "
                            + last.phase() + ", Start-Gate " + last.startGateIndex() + ", Countdown gestartet " + state.get("countdown")));
                }
                OsdData own = OsdHud.sampler().data();
                if (last.armed() != own.armed || last.mode() != own.mode || Math.abs(last.stickThrottle() - own.stickThrottle) > 0.05F) {
                    return cleanup(ctx, s, StepResult.fail("Feed weicht vom eigenen OSD ab: " + last + " gegen scharf=" + own.armed + " " + own.mode));
                }
                if (!stats.look().layout().equals(state.get("expected_layout")) || stats.look().unit() != UiConfig.speedUnit()) {
                    return cleanup(ctx, s, StepResult.fail("OSD-Layout oder Einheit des Piloten kommen nicht am Server an"));
                }
                QuadcopterEntity drone = session.drone();
                if (drone != null) {
                    SpectatorCountClient.accept(new SpectatorCountS2C(drone.getId(), 0));
                }
                state.put("pilot_log", String.format(Locale.ROOT, "ohne Zuschauer 0 Feeds in %d Ticks; mit Zuschauer %d Feeds (%.1f je Sekunde), %d Layout(s), "
                                + "Kurs %d, Phase %s, Start-Gate %d, Einheit %s", QUIET_TICKS, stats.feeds(), stats.feedsPerSecond(), stats.looks(), last.courseId(),
                        last.phase(), last.startGateIndex(), stats.look().unit()));
                state.put("sent_before", PilotFeedClient.sent());
                return next(ctx, "unwatched");
            }
            case "unwatched" -> {
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                if (since < 10) {
                    return StepResult.pending();
                }
                int after = PilotFeedClient.sent() - (int) state.get("sent_before");
                if (after > 1) {
                    return cleanup(ctx, s, StepResult.fail("Pilot schickt nach dem Ende des Zuschauens weiter: " + after + " Feeds"));
                }
                ctx.log(state.get("pilot_log") + "; danach keine Feeds mehr");
                return cleanup(ctx, s, StepResult.pass());
            }
            default -> {
                return cleanup(ctx, s, StepResult.fail("unbekannte Phase " + phase));
            }
        }
    }

    private static void buildPilot(MinecraftServer server, Scene s) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        Vec3 feet = player.position();
        buildCourse(level, player, new Vec3(feet.x + 3.0, feet.y, feet.z - 6.0), s);
        QuadcopterEntity drone = spawn(level, new Vec3(feet.x + 3.0, feet.y, feet.z - 2.0), DroneBuild.RACER);
        drone.setBatteryCharge(1.0F);
        s.drone = drone.getUUID();
        s.droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new).orElseThrow(() -> new IllegalStateException("Analog-Brille fehlt"));
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (s.previousHead == null) {
            s.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (s.previousHand == null) {
            s.previousHand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static StepResult cleanup(SelfTest.StepContext ctx, @Nullable Scene s, StepResult result) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession.INSTANCE.clearTestInput();
        if (SpectateSession.INSTANCE.active()) {
            SpectateSession.INSTANCE.stop(mc, true);
        }
        if (s != null) {
            ctx.onServer(server -> teardown(server, s));
        }
        scene = null;
        return result;
    }

    private static void teardown(MinecraftServer server, Scene s) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        ServerPlayer player = players.isEmpty() ? null : players.getFirst();
        if (player != null) {
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            if (s.previousHead != null) {
                player.setItemSlot(EquipmentSlot.HEAD, s.previousHead);
            }
            if (s.previousHand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, s.previousHand);
            }
        }
        UUID id = s.drone;
        QuadcopterEntity drone = id == null ? null : serverDrone(server, id);
        if (drone != null) {
            SpectateServer.simulateTransmitter(drone, null);
            PilotFeedServer.simulateLook(drone, null);
            drone.discard();
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID gate : s.gates) {
                if (level.getEntity(gate) instanceof GateEntity entity) {
                    entity.discard();
                }
            }
        }
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && s.course != GateEntity.NO_COURSE) {
            registry.deleteCourse(s.course);
        }
        SpectateServer.refreshSoon();
    }
}
