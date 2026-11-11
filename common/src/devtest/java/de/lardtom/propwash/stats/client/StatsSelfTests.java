package de.lardtom.propwash.stats.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.freecam.client.FreecamSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.stats.PropwashStats;
import de.lardtom.propwash.stats.StatFormats;
import de.lardtom.propwash.stats.StatNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StatsSelfTests {
    public static final String GROUP = "stats: ";
    public static final String REGISTERED = GROUP + "Statistiken angemeldet – 16 Zeilen im Reiter Allgemein mit Format, Text und Namensraum propwash";
    public static final String FLIGHT = GROUP + "Flug im FPV – Scharfschaltung, Flugzeit gesamt und Freestyle, Strecke und Höchsttempo am Server";
    public static final String CRASH = GROUP + "Crash – Sturz aus 10 m zählt am Server so oft wie im OSD";
    public static final String BATTERY = GROUP + "Akku leer – leer geflogener Akku zählt genau einmal am Server";
    public static final String FREECAM = GROUP + "Freecam zählt nicht – Flug mit der Freecam-Drohne ändert keine Statistik";
    public static final String LAP = GROUP + "Runde und Rennen – gewertete Runde und beendetes Rennen nur beim Piloten, Server-Bewegung ohne Flugzeit";
    public static final String WINDOW = GROUP + "Statistikfenster – Reiter Allgemein zeigt die Propwash-Zeilen beieinander mit den Werten des Servers";
    public static final String CLEANUP = GROUP + "Aufräumen";
    private static final String GERMAN = "de_de";
    private static final String SECOND = "PwStatsPilot";
    private static final long MS = 1_000_000L;
    private static final float CRUISE_PITCH = 0.45F;
    private static final double CRUISE_HEIGHT = 3.0;
    private static final double DROP_HEIGHT = 10.0;
    private static final double DRAIN_HEIGHT = 2.0;
    private static final int CRUISE_TICKS = 50;
    private static final Set<String> PASSED = new HashSet<>();

    private StatsSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(REGISTERED, 100, StatsSelfTests::registeredStep);
        FpvAdvancementSelfTests.registerLoaded();
        SelfTest.clientStep(FLIGHT, 1600, StatsSelfTests::flightStep);
        SelfTest.clientStep(CRASH, 1600, StatsSelfTests::crashStep);
        SelfTest.clientStep(BATTERY, 1600, StatsSelfTests::batteryStep);
        SelfTest.clientStep(FREECAM, 900, StatsSelfTests::freecamStep);
        SelfTest.clientStep(LAP, 600, StatsSelfTests::lapStep);
        SelfTest.clientStep(WINDOW, 600, StatsSelfTests::windowStep);
        FpvAdvancementSelfTests.register();
        if (Screenshots.enabled()) {
            String backWindow = GROUP + "Fenster nach den Bildschirmfotos zurück";
            String backLanguage = GROUP + "Bildschirmfotos Sprache zurückstellen";
            SelfTest.switchStep(GROUP + "Fenster für die Bildschirmfotos", backWindow, 200, Screenshots::resizeWindow);
            Screenshots.step(GROUP + "Bildschirmfoto Statistikfenster, Reiter Allgemein", "stats_general_en", 10, new WindowScene());
            FpvAdvancementSelfTests.registerScreenshot("Bildschirmfoto Fortschritte, Tab Propwash FPV", "stats_advancements_en");
            SelfTest.switchStep(GROUP + "Bildschirmfotos Sprache Deutsch", backLanguage, 2400, ctx -> Screenshots.language(ctx, GERMAN));
            Screenshots.step(GROUP + "Bildschirmfoto Statistikfenster, Reiter Allgemein deutsch", "stats_general_de", 10, new WindowScene());
            FpvAdvancementSelfTests.registerScreenshot("Bildschirmfoto Fortschritte, Tab Propwash FPV deutsch", "stats_advancements_de");
            SelfTest.clientStep(backLanguage, 2400, Screenshots::restoreLanguage);
            SelfTest.clientStep(backWindow, 40, Screenshots::restoreWindow);
        }
        SelfTest.setupStep(CLEANUP, 300, StatsSelfTests::cleanupStep);
    }

    private static StepResult registeredStep(SelfTest.StepContext ctx) {
        List<String> problems = new ArrayList<>();
        List<PropwashStats.Entry> all = PropwashStats.all();
        if (!PropwashStats.bound()) {
            problems.add("Formate nicht gebunden");
        }
        if (all.size() != StatNames.all().size() || all.size() != 16) {
            problems.add(all.size() + " Statistiken statt " + StatNames.all().size());
        }
        Set<Identifier> listed = new HashSet<>();
        for (Stat<Identifier> stat : Stats.CUSTOM) {
            listed.add(stat.getValue());
        }
        for (PropwashStats.Entry entry : all) {
            Identifier id = entry.id();
            Stat<Identifier> stat = entry.stat();
            if (!Constants.MOD_ID.equals(id.getNamespace()) || !StatNames.all().contains(id.getPath())) {
                problems.add("Id " + id);
            }
            if (BuiltInRegistries.CUSTOM_STAT.getValue(id) != stat.getValue()) {
                problems.add("nicht im Register minecraft:custom_stat: " + id);
            }
            if (!listed.contains(id)) {
                problems.add("fehlt in Stats.CUSTOM: " + id);
            }
            if (!stat.format(PropwashStats.PROBE_VALUE).equals(entry.formatter().format(PropwashStats.PROBE_VALUE))) {
                problems.add("Format " + id + ": " + stat.format(PropwashStats.PROBE_VALUE));
            }
            if (!Language.getInstance().has(entry.translationKey())) {
                problems.add("Text fehlt: " + entry.translationKey());
            }
            if (!stat.getName().equals("minecraft.custom:propwash." + entry.name())) {
                problems.add("Kriterium " + stat.getName());
            }
        }
        expect(problems, PropwashStats.TOP_SPEED.stat().format(1234), "123.4 km/h");
        expect(problems, PropwashStats.FLIGHT_TIME.stat().format(72_000), StatFormatter.TIME.format(72_000));
        expect(problems, PropwashStats.classTime(FrameRole.WHOOP).stat().format(1200), "1.00 min");
        expect(problems, PropwashStats.DISTANCE.stat().format(250_000), "2.50 km");
        expect(problems, PropwashStats.CRASHES.stat().format(12_345), "12,345");
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        ctx.log(all.size() + " Statistiken im Namensraum propwash, Höchsttempo als " + StatFormats.speed(1234) + ", Flugzeit "
                + PropwashStats.FLIGHT_TIME.stat().format(72_000));
        return StepResult.pass();
    }

    private static void expect(List<String> problems, String actual, String expected) {
        if (!expected.equals(actual)) {
            problems.add("„" + actual + "“ statt „" + expected + "“");
        }
    }

    private static final class Measure {
        int armedTicks;
        double path;
        double maxSpeed;
        @Nullable Vec3 last;
        int crashes = -1;

        void sample() {
            QuadcopterEntity drone = PilotSession.INSTANCE.drone();
            if (drone == null) {
                return;
            }
            Vec3 now = drone.position();
            if (StatsFlight.armed()) {
                this.armedTicks++;
                if (this.last != null) {
                    this.path += now.distanceTo(this.last);
                }
                this.maxSpeed = Math.max(this.maxSpeed, StatsFlight.speed());
            }
            this.last = now;
            if (this.crashes < 0) {
                this.crashes = PilotSession.INSTANCE.crashCount();
            }
        }

        int crashDelta() {
            return this.crashes < 0 ? 0 : PilotSession.INSTANCE.crashCount() - this.crashes;
        }
    }

    private static Measure measure(SelfTest.StepContext ctx) {
        return (Measure) ctx.state().computeIfAbsent("measure", key -> new Measure());
    }

    private static @Nullable StepResult common(SelfTest.StepContext ctx, String phase) {
        if (!phase.equals("spawn") && !phase.equals("fpv") && !phase.equals("cleanup") && !phase.equals("check") && !StatsFlight.flying()) {
            return StatsFlight.finish(ctx, StepResult.fail("FPV-Sitzung endete in Phase " + phase));
        }
        return null;
    }

    private static @Nullable StepResult armPhase(SelfTest.StepContext ctx, String then) {
        StatsFlight.arm(StatsFlight.since(ctx));
        if (StatsFlight.since(ctx) < StatsFlight.ARM_TICKS) {
            return StepResult.pending();
        }
        if (!StatsFlight.armed()) {
            return StatsFlight.since(ctx) > StatsFlight.ARM_TICKS * 4 ? StatsFlight.finish(ctx, StepResult.fail("Drohne wird nicht scharf"))
                    : StepResult.pending();
        }
        return StatsFlight.next(ctx, then);
    }

    private static Map<String, Integer> readAfter(SelfTest.StepContext ctx, String key) {
        StatsFlight.Answer<Map<String, Integer>> answer = StatsFlight.answer(ctx, key);
        return answer == null || answer.value() == null ? Map.of() : answer.value();
    }

    private static boolean ready(SelfTest.StepContext ctx, String key) {
        return StatsFlight.answer(ctx, key) != null;
    }

    private static void askValues(SelfTest.StepContext ctx, String key) {
        StatsFlight.ask(ctx, key, server -> StatsFlight.values(StatsFlight.player(server)));
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        String phase = StatsFlight.phase(ctx, "spawn");
        StepResult ended = common(ctx, phase);
        if (ended != null) {
            return ended;
        }
        Measure measure = measure(ctx);
        if (!phase.equals("spawn") && !phase.equals("fpv") && !phase.equals("cleanup")) {
            measure.sample();
        }
        int since = StatsFlight.since(ctx);
        switch (phase) {
            case "spawn" -> {
                return StatsFlight.start(ctx, 1.0F, 2.0, -4.0);
            }
            case "fpv" -> {
                StepResult wait = StatsFlight.awaitFpv(ctx, "arm");
                return wait == null ? StepResult.pending() : wait;
            }
            case "arm" -> {
                StepResult wait = armPhase(ctx, "climb");
                return wait == null ? StepResult.pending() : wait;
            }
            case "climb" -> {
                StatsFlight.fly(CRUISE_HEIGHT, 0.0F);
                if (StatsFlight.height() > CRUISE_HEIGHT - 0.5) {
                    return StatsFlight.next(ctx, "cruise");
                }
                return since > StatsFlight.TIMEOUT_TICKS ? StatsFlight.finish(ctx, StepResult.fail("steigt nicht: Höhe " + StatsFlight.height()))
                        : StepResult.pending();
            }
            case "cruise" -> {
                StatsFlight.fly(CRUISE_HEIGHT, CRUISE_PITCH);
                return since >= CRUISE_TICKS ? StatsFlight.next(ctx, "brake") : StepResult.pending();
            }
            case "brake" -> {
                StatsFlight.fly(CRUISE_HEIGHT, StatsFlight.speed() > 2.0 ? -CRUISE_PITCH : 0.0F);
                return since >= 30 ? StatsFlight.next(ctx, "land") : StepResult.pending();
            }
            case "land" -> {
                StatsFlight.fly(0.1, 0.0F);
                Telemetry t = StatsFlight.telemetry();
                boolean down = StatsFlight.height() < 0.4 && t != null && Math.abs(t.verticalSpeed) < 0.4;
                if (down || since > 200) {
                    StatsFlight.disarm();
                    return StatsFlight.next(ctx, "settle");
                }
                return StepResult.pending();
            }
            case "settle" -> {
                StatsFlight.disarm();
                if (since == 15) {
                    askValues(ctx, "after");
                }
                return since > 15 && ready(ctx, "after") ? StatsFlight.next(ctx, "check") : StepResult.pending();
            }
            case "check" -> {
                return StatsFlight.finish(ctx, checkFlight(ctx, measure));
            }
            default -> {
                StepResult result = StatsFlight.awaitCleanup(ctx);
                if (result.status() == StepResult.Status.PASS) {
                    PASSED.add(FLIGHT);
                }
                return result;
            }
        }
    }

    private static StepResult checkFlight(SelfTest.StepContext ctx, Measure measure) {
        Map<String, Integer> before = StatsFlight.snapshot(ctx).values();
        Map<String, Integer> after = readAfter(ctx, "after");
        Map<String, Integer> delta = StatsFlight.delta(before, after);
        List<String> problems = new ArrayList<>();
        int time = delta.getOrDefault(StatNames.FLIGHT_TIME, 0);
        int armed = measure.armedTicks;
        if (time < Math.max(20, armed * 0.7) || time > armed * 1.2 + 10) {
            problems.add("Flugzeit " + time + " Ticks, scharf am Client " + armed);
        }
        String own = StatNames.classTime(FrameRole.FREESTYLE);
        if (delta.getOrDefault(own, 0) != time) {
            problems.add("Freestyle-Zeit " + delta.getOrDefault(own, 0) + " statt " + time);
        }
        for (FrameRole role : FrameRole.values()) {
            if (role != FrameRole.FREESTYLE && delta.containsKey(StatNames.classTime(role))) {
                problems.add("fremde Klasse " + role.key() + " zählt mit");
            }
        }
        int centimeters = delta.getOrDefault(StatNames.DISTANCE, 0);
        double path = measure.path * 100.0;
        if (centimeters < path * 0.7 - 50.0 || centimeters > path * 1.3 + 50.0) {
            problems.add(String.format(Locale.ROOT, "Strecke %d cm, am Client %.0f cm", centimeters, path));
        }
        if (delta.getOrDefault(StatNames.ARMS, 0) != 1) {
            problems.add("Scharfschaltungen +" + delta.getOrDefault(StatNames.ARMS, 0));
        }
        int top = after.getOrDefault(StatNames.TOP_SPEED, 0);
        int flown = StatFormats.tenthsKmh(measure.maxSpeed);
        if (top < flown * 0.8 || before.getOrDefault(StatNames.TOP_SPEED, 0) < top && top > flown * 1.05 + 5) {
            problems.add("Höchsttempo " + StatFormats.speed(top) + ", am Client " + StatFormats.speed(flown));
        }
        if (flown < 100) {
            problems.add("Flug zu langsam für die Prüfung: " + StatFormats.speed(flown));
        }
        if (delta.getOrDefault(StatNames.CRASHES, 0) != measure.crashDelta()) {
            problems.add("Crashes +" + delta.getOrDefault(StatNames.CRASHES, 0) + ", im OSD +" + measure.crashDelta());
        }
        for (String name : List.of(StatNames.LAPS, StatNames.RACES_FINISHED, StatNames.BATTERIES_DEPLETED)) {
            if (delta.containsKey(name)) {
                problems.add(name + " +" + delta.get(name));
            }
        }
        ctx.log("Server zählt " + delta + " bei " + armed + " scharfen Client-Ticks, " + String.format(Locale.ROOT, "%.1f m", measure.path)
                + " Weg und " + StatFormats.speed(flown) + " Spitze am Client");
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static StepResult crashStep(SelfTest.StepContext ctx) {
        String phase = StatsFlight.phase(ctx, "spawn");
        StepResult ended = common(ctx, phase);
        if (ended != null) {
            return ended;
        }
        int since = StatsFlight.since(ctx);
        switch (phase) {
            case "spawn" -> {
                return StatsFlight.start(ctx, 1.0F, -2.0, -4.0);
            }
            case "fpv" -> {
                StepResult wait = StatsFlight.awaitFpv(ctx, "arm");
                return wait == null ? StepResult.pending() : wait;
            }
            case "arm" -> {
                StepResult wait = armPhase(ctx, "climb");
                return wait == null ? StepResult.pending() : wait;
            }
            case "climb" -> {
                StatsFlight.fly(DROP_HEIGHT, 0.0F);
                Telemetry t = StatsFlight.telemetry();
                if (StatsFlight.height() > DROP_HEIGHT - 0.8 && t != null && Math.abs(t.verticalSpeed) < 1.0) {
                    ctx.state().put("crashes", PilotSession.INSTANCE.crashCount());
                    ctx.state().put("sent", StatsClient.sentCrashes());
                    StatsFlight.disarm();
                    return StatsFlight.next(ctx, "drop");
                }
                return since > StatsFlight.TIMEOUT_TICKS ? StatsFlight.finish(ctx, StepResult.fail("steigt nicht auf 10 m: " + StatsFlight.height()))
                        : StepResult.pending();
            }
            case "drop" -> {
                StatsFlight.disarm();
                int crashes = PilotSession.INSTANCE.crashCount() - (int) ctx.state().get("crashes");
                if (crashes > 0 && StatsFlight.height() < 0.5 && StatsFlight.speed() < 0.5) {
                    return StatsFlight.next(ctx, "rest");
                }
                return since > 200 ? StatsFlight.finish(ctx, StepResult.fail("kein Crash nach dem Sturz: OSD +" + crashes + ", Höhe "
                        + String.format(Locale.ROOT, "%.2f", StatsFlight.height()))) : StepResult.pending();
            }
            case "rest" -> {
                StatsFlight.disarm();
                if (since == 20) {
                    askValues(ctx, "after");
                }
                return since > 20 && ready(ctx, "after") ? StatsFlight.next(ctx, "check") : StepResult.pending();
            }
            case "check" -> {
                Map<String, Integer> delta = StatsFlight.delta(StatsFlight.snapshot(ctx).values(), readAfter(ctx, "after"));
                int osd = PilotSession.INSTANCE.crashCount() - (int) ctx.state().get("crashes");
                int sent = StatsClient.sentCrashes() - (int) ctx.state().get("sent");
                int counted = delta.getOrDefault(StatNames.CRASHES, 0);
                List<String> problems = new ArrayList<>();
                if (osd < 1 || counted != osd || sent != osd) {
                    problems.add("Crashes am Server +" + counted + ", im OSD +" + osd + ", gemeldet " + sent);
                }
                if (delta.getOrDefault(StatNames.ARMS, 0) != 1 || delta.getOrDefault(StatNames.FLIGHT_TIME, 0) <= 0) {
                    problems.add("Flug vor dem Sturz nicht gezählt: " + delta);
                }
                ctx.log("Sturz aus " + DROP_HEIGHT + " m: OSD +" + osd + ", Server " + delta);
                return StatsFlight.finish(ctx, problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems)));
            }
            default -> {
                StepResult result = StatsFlight.awaitCleanup(ctx);
                if (result.status() == StepResult.Status.PASS) {
                    PASSED.add(CRASH);
                }
                return result;
            }
        }
    }

    private static StepResult batteryStep(SelfTest.StepContext ctx) {
        String phase = StatsFlight.phase(ctx, "spawn");
        StepResult ended = common(ctx, phase);
        if (ended != null) {
            return ended;
        }
        int since = StatsFlight.since(ctx);
        switch (phase) {
            case "spawn" -> {
                return StatsFlight.start(ctx, 1.0F, 0.0, -7.0);
            }
            case "fpv" -> {
                StepResult wait = StatsFlight.awaitFpv(ctx, "arm");
                return wait == null ? StepResult.pending() : wait;
            }
            case "arm" -> {
                StepResult wait = armPhase(ctx, "climb");
                return wait == null ? StepResult.pending() : wait;
            }
            case "climb" -> {
                StatsFlight.fly(DRAIN_HEIGHT, 0.0F);
                if (StatsFlight.height() > DRAIN_HEIGHT - 0.4 || since > 160) {
                    DroneSim sim = PilotSession.INSTANCE.sim();
                    if (sim == null) {
                        return StatsFlight.finish(ctx, StepResult.fail("keine Simulation"));
                    }
                    sim.setBatteryCharge(0.002);
                    return StatsFlight.next(ctx, "drain");
                }
                return StepResult.pending();
            }
            case "drain" -> {
                StatsFlight.fly(DRAIN_HEIGHT, 0.0F);
                Telemetry t = StatsFlight.telemetry();
                if (t != null && t.batteryCharge <= 0.0) {
                    return StatsFlight.next(ctx, "empty");
                }
                return since > StatsFlight.TIMEOUT_TICKS ? StatsFlight.finish(ctx, StepResult.fail("Akku wird nicht leer: "
                        + (t == null ? "?" : t.batteryCharge))) : StepResult.pending();
            }
            case "empty" -> {
                StatsFlight.disarm();
                if (since == 20) {
                    StatsFlight.ask(ctx, "after", server -> {
                        QuadcopterEntity drone = StatsFlight.serverDrone(server);
                        Map<String, Integer> values = new LinkedHashMap<>(StatsFlight.values(StatsFlight.player(server)));
                        values.put("server_battery_permille", drone == null ? -1 : Math.round(drone.batteryCharge() * 1000.0F));
                        return values;
                    });
                }
                return since > 20 && ready(ctx, "after") ? StatsFlight.next(ctx, "again") : StepResult.pending();
            }
            case "again" -> {
                StatsFlight.disarm();
                if (since == 40) {
                    askValues(ctx, "later");
                }
                return since > 40 && ready(ctx, "later") ? StatsFlight.next(ctx, "check") : StepResult.pending();
            }
            case "check" -> {
                Map<String, Integer> before = StatsFlight.snapshot(ctx).values();
                Map<String, Integer> after = new LinkedHashMap<>(readAfter(ctx, "after"));
                int battery = after.remove("server_battery_permille") instanceof Integer value ? value : -1;
                Map<String, Integer> delta = StatsFlight.delta(before, after);
                Map<String, Integer> later = StatsFlight.delta(before, readAfter(ctx, "later"));
                List<String> problems = new ArrayList<>();
                if (battery != 0) {
                    problems.add("Akku am Server " + battery + " ‰");
                }
                if (delta.getOrDefault(StatNames.BATTERIES_DEPLETED, 0) != 1) {
                    problems.add("Akkus leer +" + delta.getOrDefault(StatNames.BATTERIES_DEPLETED, 0));
                }
                if (later.getOrDefault(StatNames.BATTERIES_DEPLETED, 0) != 1) {
                    problems.add("leerer Akku zählt nach 2 s +" + later.getOrDefault(StatNames.BATTERIES_DEPLETED, 0));
                }
                if (delta.getOrDefault(StatNames.ARMS, 0) != 1) {
                    problems.add("Scharfschaltungen +" + delta.getOrDefault(StatNames.ARMS, 0));
                }
                ctx.log("Akku leer geflogen: Server " + delta + ", 2 s später " + later);
                return StatsFlight.finish(ctx, problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems)));
            }
            default -> {
                StepResult result = StatsFlight.awaitCleanup(ctx);
                if (result.status() == StepResult.Status.PASS) {
                    PASSED.add(BATTERY);
                }
                return result;
            }
        }
    }

    private static StepResult freecamStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        String phase = StatsFlight.phase(ctx, "rule");
        int since = StatsFlight.since(ctx);
        FreecamSession freecam = FreecamSession.INSTANCE;
        switch (phase) {
            case "rule" -> {
                askValues(ctx, "before");
                ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, 1));
                return StatsFlight.next(ctx, "start");
            }
            case "start" -> {
                ClientWorldRules rules = ClientWorldRules.INSTANCE;
                if (!rules.known() || !rules.on(WorldRuleSpecs.ALLOW_FREECAM) || !ready(ctx, "before")) {
                    return since > 200 ? endFreecam(ctx, StepResult.fail("Regel allow_freecam kommt nicht an")) : StepResult.pending();
                }
                if (!FreecamSession.active()) {
                    if (mc.gui.screen() != null) {
                        mc.gui.setScreen(null);
                    }
                    freecam.tryStart(mc);
                    return since > 300 ? endFreecam(ctx, StepResult.fail("Freecam startet nicht: " + freecam.lastDenial())) : StepResult.pending();
                }
                ctx.state().put("freecam_from", freecam.position(new Vector3d()));
                return StatsFlight.next(ctx, "arm");
            }
            case "arm" -> {
                freecam.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), since >= StatsFlight.ARM_TICKS / 2, StabMode.ANGLE);
                if (since >= StatsFlight.ARM_TICKS && freecam.armed()) {
                    return StatsFlight.next(ctx, "fly");
                }
                return since > 120 ? endFreecam(ctx, StepResult.fail("Freecam wird nicht scharf")) : StepResult.pending();
            }
            case "fly" -> {
                if (!FreecamSession.active()) {
                    return endFreecam(ctx, StepResult.fail("Freecam endete im Flug"));
                }
                freecam.setTestInput(new StickPositions(0.0F, 0.3F, 0.0F, 0.6F), true, StabMode.ANGLE);
                if (since < 60) {
                    return StepResult.pending();
                }
                Vector3d from = (Vector3d) ctx.state().get("freecam_from");
                ctx.state().put("freecam_flown", freecam.position(new Vector3d()).distance(from));
                freecam.clearTestInput();
                freecam.end(mc, FreecamRules.End.BUTTON);
                return StatsFlight.next(ctx, "after");
            }
            case "after" -> {
                if (FreecamSession.active()) {
                    return since > 100 ? endFreecam(ctx, StepResult.fail("Freecam endet nicht")) : StepResult.pending();
                }
                if (since == 10) {
                    askValues(ctx, "after");
                }
                if (since <= 10 || !ready(ctx, "after")) {
                    return StepResult.pending();
                }
                Map<String, Integer> delta = StatsFlight.delta(readAfter(ctx, "before"), readAfter(ctx, "after"));
                double flown = (double) ctx.state().get("freecam_flown");
                StepResult result = flown < 2.0 ? StepResult.fail(String.format(Locale.ROOT, "Freecam flog nur %.2f m", flown))
                        : delta.isEmpty() ? StepResult.pass() : StepResult.fail("Freecam zählt " + delta);
                ctx.log(String.format(Locale.ROOT, "Freecam %.1f m in 3 s geflogen, Statistik unverändert: %s", flown, delta.isEmpty()));
                return endFreecam(ctx, result);
            }
            default -> {
                return (StepResult) ctx.state().get("freecam_result");
            }
        }
    }

    private static StepResult endFreecam(SelfTest.StepContext ctx, StepResult result) {
        Minecraft mc = Minecraft.getInstance();
        FreecamSession.INSTANCE.clearTestInput();
        if (FreecamSession.active()) {
            FreecamSession.INSTANCE.end(mc, FreecamRules.End.BUTTON);
        }
        ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, WorldRuleSpecs.ALLOW_FREECAM.defaultValue()));
        ctx.state().put("freecam_result", result);
        StatsFlight.next(ctx, "done");
        return result.status() == StepResult.Status.PENDING ? StepResult.pending() : result;
    }

    private static StepResult lapStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            StatsFlight.ask(ctx, "lap", StatsSelfTests::flyLaps);
            return StepResult.pending();
        }
        StatsFlight.Answer<String> answer = StatsFlight.answer(ctx, "lap");
        if (answer == null) {
            return StepResult.pending();
        }
        if (answer.error() != null) {
            return StepResult.fail(answer.error());
        }
        String result = answer.value();
        if (result == null || !result.startsWith("ok ")) {
            return StepResult.fail(String.valueOf(result));
        }
        ctx.log(result.substring(3));
        PASSED.add(LAP);
        return StepResult.pass();
    }

    private static String flyLaps(MinecraftServer server) {
        ServerPlayer player = StatsFlight.player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        Vec3 origin = RaceDataJoinCheck.origin(player);
        List<GateEntity> gates = new ArrayList<>();
        CourseRegistry.Course course = RaceDataJoinCheck.build(player, origin, gates);
        if (course == null) {
            gates.forEach(GateEntity::discard);
            return "Kurs nicht angelegt";
        }
        try {
            ServerPlayer second = RaceDataJoinCheck.secondPilot(server, player.level(), SECOND);
            Map<String, Integer> own = StatsFlight.values(player);
            Map<String, Integer> other = StatsFlight.values(second);
            String problem = RaceDataJoinCheck.fly(course, second, origin, 9_000L * MS);
            if (problem != null) {
                return SECOND + ": " + problem;
            }
            Map<String, Integer> ownMid = StatsFlight.values(player);
            Map<String, Integer> otherDelta = StatsFlight.delta(other, StatsFlight.values(second));
            Map<String, Integer> expected = Map.of(StatNames.LAPS, 1, StatNames.RACES_FINISHED, 1);
            if (!otherDelta.equals(expected)) {
                return SECOND + " zählt " + otherDelta + " statt " + expected;
            }
            Map<String, Integer> ownDelta = StatsFlight.delta(own, ownMid);
            if (!ownDelta.isEmpty()) {
                return "Runde von " + SECOND + " zählt beim Zuschauer " + ownDelta;
            }
            problem = RaceDataJoinCheck.fly(course, player, origin, 8_000L * MS);
            if (problem != null) {
                return player.getGameProfile().name() + ": " + problem;
            }
            Map<String, Integer> ownAfter = StatsFlight.delta(ownMid, StatsFlight.values(player));
            if (!ownAfter.equals(expected)) {
                return player.getGameProfile().name() + " zählt " + ownAfter + " statt " + expected;
            }
            return "ok Kurs " + course.id + ": " + SECOND + " " + otherDelta + ", " + player.getGameProfile().name() + " " + ownAfter
                    + ", vom Server geschobene Drohne ohne Flugzeit und Strecke";
        } finally {
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                registry.deleteCourse(course.id);
            }
            gates.forEach(GateEntity::discard);
        }
    }

    static @Nullable ObjectSelectionList<?> generalList(@Nullable Screen screen) {
        if (!(screen instanceof StatsScreen)) {
            return null;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ObjectSelectionList<?> list && !list.children().isEmpty()) {
                return list;
            }
        }
        return null;
    }

    static List<Integer> rows(ObjectSelectionList<?> list, StatsCounter stats, List<String> problems) {
        List<String> narrations = new ArrayList<>();
        for (Object child : list.children()) {
            narrations.add(child instanceof ObjectSelectionList.Entry<?> entry ? entry.getNarration().getString() : "");
        }
        List<Integer> rows = new ArrayList<>();
        for (PropwashStats.Entry entry : PropwashStats.all()) {
            Stat<Identifier> stat = entry.stat();
            String shown = Language.getInstance().getOrDefault(entry.translationKey()) + " " + stat.format(stats.getValue(stat));
            int row = -1;
            for (int i = 0; i < narrations.size(); i++) {
                if (narrations.get(i).contains(shown)) {
                    row = i;
                    break;
                }
            }
            if (row < 0) {
                problems.add("Zeile fehlt: " + shown);
            } else {
                rows.add(row);
            }
        }
        rows.sort(Comparator.naturalOrder());
        return rows;
    }

    private static StepResult windowStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        if (ctx.tick() == 0) {
            askValues(ctx, "server");
            mc.gui.setScreen(new StatsScreen(null, mc.player.getStats()));
            return StepResult.pending();
        }
        ObjectSelectionList<?> list = generalList(mc.gui.screen());
        if (list == null || !ready(ctx, "server")) {
            return ctx.tick() > 400 ? close(mc, StepResult.fail("Statistikfenster lädt nicht")) : StepResult.pending();
        }
        Map<String, Integer> server = readAfter(ctx, "server");
        StatsCounter stats = mc.player.getStats();
        List<String> problems = new ArrayList<>();
        Map<String, Integer> shown = new LinkedHashMap<>();
        for (PropwashStats.Entry entry : PropwashStats.all()) {
            int value = stats.getValue(entry.stat());
            shown.put(entry.name(), value);
            if (value != server.getOrDefault(entry.name(), -1)) {
                problems.add(entry.name() + " beim Client " + value + ", am Server " + server.get(entry.name()));
            }
        }
        List<Integer> rows = rows(list, stats, problems);
        if (rows.size() == PropwashStats.all().size() && rows.getLast() - rows.getFirst() != rows.size() - 1) {
            problems.add("Propwash-Zeilen stehen nicht beieinander: " + rows);
        }
        if (PASSED.contains(FLIGHT) && (shown.get(StatNames.FLIGHT_TIME) <= 0 || shown.get(StatNames.DISTANCE) <= 0
                || shown.get(StatNames.TOP_SPEED) <= 0 || shown.get(StatNames.ARMS) <= 0)) {
            problems.add("Flug fehlt im Fenster: " + shown);
        }
        if (PASSED.contains(CRASH) && shown.get(StatNames.CRASHES) <= 0) {
            problems.add("Crash fehlt im Fenster");
        }
        if (PASSED.contains(BATTERY) && shown.get(StatNames.BATTERIES_DEPLETED) <= 0) {
            problems.add("leerer Akku fehlt im Fenster");
        }
        if (PASSED.contains(LAP) && (shown.get(StatNames.LAPS) <= 0 || shown.get(StatNames.RACES_FINISHED) <= 0)) {
            problems.add("Runde oder Rennen fehlt im Fenster");
        }
        StringBuilder line = new StringBuilder();
        for (PropwashStats.Entry entry : PropwashStats.all()) {
            int value = shown.get(entry.name());
            if (value != 0 || entry.name().equals(StatNames.CRASHES)) {
                line.append(line.isEmpty() ? "" : ", ").append(Language.getInstance().getOrDefault(entry.translationKey())).append(" ")
                        .append(entry.stat().format(value));
            }
        }
        ctx.log("Reiter Allgemein, Zeilen " + (rows.isEmpty() ? "-" : rows.getFirst() + "–" + rows.getLast()) + " von " + list.children().size()
                + ": " + line);
        return close(mc, problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems)));
    }

    private static StepResult close(Minecraft mc, StepResult result) {
        if (mc.gui.screen() instanceof StatsScreen) {
            mc.gui.setScreen(null);
        }
        return result;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            close(mc, StepResult.pass());
            StatsFlight.ask(ctx, "cleanup", server -> {
                StatsFlight.release(server);
                return Boolean.TRUE;
            });
            return StepResult.pending();
        }
        if (!ready(ctx, "cleanup") || PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 200 ? StepResult.fail("Aufräumen hängt") : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static final class WindowScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return StepResult.fail("kein Spieler");
            }
            ObjectSelectionList<?> list = generalList(mc.gui.screen());
            if (list == null) {
                if (!(mc.gui.screen() instanceof StatsScreen)) {
                    mc.gui.setScreen(new StatsScreen(null, mc.player.getStats()));
                }
                return ctx.tick() > 400 ? StepResult.fail("Statistikfenster lädt nicht") : StepResult.pending();
            }
            List<String> problems = new ArrayList<>();
            List<Integer> rows = rows(list, mc.player.getStats(), problems);
            if (!problems.isEmpty() || rows.isEmpty()) {
                return StepResult.fail(String.join("; ", problems));
            }
            Object first = list.children().get(rows.getFirst());
            int height = first instanceof ObjectSelectionList.Entry<?> entry ? entry.getHeight() : 14;
            list.setScrollAmount(Math.max(0, rows.getFirst() - 1) * height);
            Screenshots.parkMouse(mc);
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            close(Minecraft.getInstance(), StepResult.pass());
        }
    }
}
