package de.lardtom.propwash.freecam.client;

import com.google.gson.Gson;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.client.CameraClip;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotView;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.freecam.client.FreecamSelfTests.Phase;
import de.lardtom.propwash.freecam.client.FreecamSelfTests.Run;
import de.lardtom.propwash.guide.client.screen.GuideTab;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.client.CovFreecamOsdProbe;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.race.client.GateHintMemory;
import de.lardtom.propwash.race.client.GatePlaceHint;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.HintMemory;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.FirstUseHints;
import de.lardtom.propwash.ui.client.UiClient;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SettingsIndex;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class CovFreecamSelfTests {
    public static final String GROUP = FreecamSelfTests.GROUP + "Abdeckung – ";
    static final String CLEANUP = GROUP + "aufräumen";
    static final int RADIO_AXES = 8;
    static final int ROLL_AXIS = 0;
    static final int PITCH_AXIS = 1;
    static final int THROTTLE_AXIS = 2;
    static final int YAW_AXIS = 3;
    private static final int PARK_TICKS = 40;
    private static final int AIR_PARK_TICKS = 60;
    private static final double FAST = 15.0;
    private static final long HITCH_MILLIS = 400L;
    private static final String INTRO_BEFORE = "cov_intro_before";
    private static final String PROBE_CONFIG = "cov_freecam_probe";
    private static final String MISSING_CONFIG = "cov_freecam_missing";
    private static final Gson GSON = new Gson();
    static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    static final AtomicReference<String> NETHER = new AtomicReference<>();

    private CovFreecamSelfTests() {
    }

    record Saved(String hints, String worlds, boolean keyboardFallback, boolean startInAngle, boolean suppressDevice) {
    }

    enum Way {
        KEY,
        EXIT,
        BUTTON
    }

    public static void register() {
        SelfTest.switchStep(GROUP + "Testplatz, Hinweise und Eingaben sichern", CLEANUP, 900, FreecamSelfTests.flow(
                phase("Einstellungen, Hinweise und Eingaben sichern", 20, (ctx, run) -> {
                    save();
                    FreecamSelfTests.resetConfig();
                    return StepResult.pass();
                }),
                phase("Testplatz bauen", 600, (ctx, run) -> FreecamSelfTests.server(ctx, run, FreecamSelfTests::prepare)),
                phase("Spieler am Testplatz, Regel synchronisiert aus", 400,
                        (ctx, run) -> FreecamSelfTests.until(FreecamSelfTests.atSite() && FreecamSelfTests.ruleSynced(false), "Spieler oder Regel"))));
        SelfTest.clientStep(GROUP + "Start mit F6 – Augenhöhe, Blickrichtung, waagerecht, unscharf geparkt, Spieler still, Hand und HUD wie im FPV, "
                + "OSD-Name FREECAM · 5″, Hinweis freecam_intro", 1600, guarded(
                phase("Hinweis freecam_intro vergessen", 20, (ctx, run) -> {
                    FirstUseHints.forget(FirstUseHints.FREECAM_INTRO);
                    return StepResult.check(FirstUseHints.fresh(FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES), "Hinweis bleibt gesehen");
                }),
                phase("Regel an", 300, (ctx, run) -> FreecamSelfTests.setRule(ctx, run, true)),
                phase("F6 startet an der Augenposition in Blickrichtung", 120, CovFreecamSelfTests::startPose),
                phase("Hinweis freecam_intro gezeigt", 60, CovFreecamSelfTests::introShown),
                phase(PARK_TICKS + " Ticks unscharf geparkt, Spieler unbewegt, kein Paket", PARK_TICKS + 40, CovFreecamSelfTests::parkedStill),
                phase("Hand und HUD wie im FPV, OSD-Name", 100, CovFreecamSelfTests::handHudName),
                phase("Ende per F6", 100, (ctx, run) -> endBy(ctx, run, Way.KEY))));
        SelfTest.clientStep(GROUP + "Hinweis nur einmal, nach „Hinweise zurücksetzen“ wieder", 1600, guarded(
                phase("Regel an", 300, (ctx, run) -> FreecamSelfTests.setRule(ctx, run, true)),
                phase("zweiter Start ohne neuen Hinweis", 120, (ctx, run) -> startCountingIntro(ctx, run, false)),
                phase("Ende", 100, FreecamSelfTests::endQuietly),
                phase("K → Flugschule", 200, (ctx, run) -> FreecamSelfTests.openConfigurator(ctx, run, GuideTab.ID)),
                phase("„Hinweise zurücksetzen“", 60, CovFreecamSelfTests::resetHints),
                phase("K-Menü schließen", 100, FreecamSelfTests::closeScreen),
                phase("dritter Start zeigt den Hinweis wieder", 120, (ctx, run) -> startCountingIntro(ctx, run, true)),
                phase("Hinweis sichtbar", 60, CovFreecamSelfTests::introShown),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "Ende per F6, Y und Knopf – im nächsten Tick Kamera am Spieler, Freecam aus", 1600, guarded(
                phase("Regel an", 300, (ctx, run) -> FreecamSelfTests.setRule(ctx, run, true)),
                phase("Start per F6", 100, CovFreecamSelfTests::startByKey),
                phase("Ende per F6", 100, (ctx, run) -> endBy(ctx, run, Way.KEY)),
                phase("Start per F6", 100, CovFreecamSelfTests::startByKey),
                phase("Ende per Y", 100, (ctx, run) -> endBy(ctx, run, Way.EXIT)),
                phase("Start per F6", 100, CovFreecamSelfTests::startByKey),
                phase("K öffnet den Tab Freecam", 200, CovFreecamSelfTests::pressK),
                phase("Ende per „Freecam beenden“", 100, (ctx, run) -> endBy(ctx, run, Way.BUTTON))));
        SelfTest.clientStep(GROUP + "in 20 m entschärft – 60 Ticks an Ort und Stelle, waagerecht", 1600, guarded(
                hitch(),
                phase("Start", 300, FreecamSelfTests::start),
                phase("scharf in 20 m mit Fahrt und Schräglage", 300, CovFreecamSelfTests::armedMoving),
                phase("entschärfen", 100, CovFreecamSelfTests::disarmInAir),
                phase(AIR_PARK_TICKS + " Ticks geparkt", AIR_PARK_TICKS + 40, CovFreecamSelfTests::airParked),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "Eingaben – Test-Funke, Gamepad und Tastatur steuern die Sim, Modi per Schalter und Taste", 2400, guarded(
                phase("Start", 300, CovFreecamSelfTests::startPlain),
                phase("Test-Funke: Sim folgt den Knüppeln", 100, CovFreecamSelfTests::radioSticks),
                phase("Test-Funke: Schalter Acro", 100, (ctx, run) -> radioMode(ctx, run, 1000, StabMode.ACRO)),
                phase("Test-Funke: Schalter Mitte Horizon", 100, (ctx, run) -> radioMode(ctx, run, 1500, StabMode.HORIZON)),
                phase("Test-Funke: Schalter oben Angle", 100, (ctx, run) -> radioMode(ctx, run, 2000, StabMode.ANGLE)),
                phase("Gamepad: Sim folgt den Sticks", 100, CovFreecamSelfTests::padSticks),
                phase("Gamepad: Schultertaste schaltet Angle und Acro", 160, CovFreecamSelfTests::padMode),
                phase("Tastatur übernimmt", 100, CovFreecamSelfTests::keyboardOn),
                phase("Tastatur: W/S Gas, Pfeiltasten Nick und Roll", 200, CovFreecamSelfTests::keyboardSticks),
                phase("Tastatur: Ziffernblock Acro", 60, (ctx, run) -> keyMode(ctx, run, InputKeys.MODE_ACRO, StabMode.ACRO)),
                phase("Tastatur: Ziffernblock Horizon", 60, (ctx, run) -> keyMode(ctx, run, InputKeys.MODE_HORIZON, StabMode.HORIZON)),
                phase("Tastatur: Ziffernblock Angle", 60, (ctx, run) -> keyMode(ctx, run, InputKeys.MODE_ANGLE, StabMode.ANGLE)),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "Suche nach Action-Cam und OSD findet nichts aus ausgeblendeten Tabs", 1200, guarded(
                phase("Gegenprobe im normalen K-Menü", 200, (ctx, run) -> FreecamSelfTests.openConfigurator(ctx, run, OverviewTab.ID)),
                phase("normale Suche findet Action-Cam und OSD", 60, CovFreecamSelfTests::searchControl),
                phase("K-Menü schließen", 100, FreecamSelfTests::closeScreen),
                phase("Start", 300, FreecamSelfTests::start),
                phase("K öffnet den Tab Freecam", 200, CovFreecamSelfTests::pressK),
                phase("Suche im Freecam-K-Menü", 60, CovFreecamSelfTests::searchFreecam),
                phase("K-Menü schließen", 100, FreecamSelfTests::closeScreen),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "Größe bei 15 m/s von 5″ auf 7″ – Position, Lage, Tempo und Drehraten bleiben, scharf", 1600, guarded(
                hitch(),
                phase("Start", 300, FreecamSelfTests::start),
                phase("scharf mit 15 m/s, rollend und gierend", 300, CovFreecamSelfTests::fastTurning),
                phase("K öffnet den Tab Freecam", 200, CovFreecamSelfTests::pressK),
                phase("Chip 7″", 60, CovFreecamSelfTests::switchToSeven),
                phase("K-Menü schließen", 100, FreecamSelfTests::closeScreen),
                phase("7″ fliegt scharf mit Schwung weiter", 100, CovFreecamSelfTests::sevenKeepsGoing),
                phase("zurück auf 5″", 100, CovFreecamSelfTests::backToFive),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "Einstellungen je Größe – Kamerawinkel und Rates getrennt, Sichtfeld für alle, OSD aus im nächsten Frame", 1600, guarded(
                phase("Start", 300, FreecamSelfTests::start),
                phase("Kamerawinkel und Rates je Größe, Sichtfeld gleich", 200, CovFreecamSelfTests::perSize),
                phase("Sichtfeld 100° gilt für alle Größen", 100, CovFreecamSelfTests::sharedFov),
                phase("OSD aus: im nächsten Frame nichts gezeichnet", 100, CovFreecamSelfTests::osdOffNextFrame),
                phase("zurück auf 5″", 100, CovFreecamSelfTests::backToFive),
                phase("Ende", 100, FreecamSelfTests::endQuietly)));
        SelfTest.clientStep(GROUP + "Einstellungen bleiben in der Datei – schreiben, neu lesen, fehlende Datei gibt Standard", 400, guarded(
                phase("Datei schreiben und mit neuer Config lesen", 40, CovFreecamSelfTests::fileRoundTrip),
                phase("fehlende Datei", 40, CovFreecamSelfTests::fileMissing),
                phase("freecam.json enthält den Stand im Spiel", 40, CovFreecamSelfTests::fileMatchesLive)));
        SelfTest.clientStep(GROUP + "Ende beim Wechsel in den Nether am Server", 2400, guarded(
                phase("Start", 300, FreecamSelfTests::start),
                phase("Spieler am Server in den Nether", 600, CovFreecamSelfTests::toNether),
                phase("Freecam endet sofort mit Grund Dimension", 200, CovFreecamSelfTests::endedInNether),
                phase("zurück an den Testplatz", 600, CovFreecamSelfTests::backFromNether)));
        CovFreecamFlightSelfTests.register();
        CovFreecamIsolationSelfTests.register();
        SelfTest.clientStep(CLEANUP, 900, FreecamSelfTests.flow(phase("Testplatz abbauen, Einstellungen und Hinweise zurück", 600, (ctx, run) -> {
            if (run.future == null) {
                calm();
                restoreSaved();
                FreecamSelfTests.restoreConfig();
                run.future = ctx.onServer(server -> {
                    CovFreecamIsolationSelfTests.tearDown(server);
                    CovFreecamFlightSelfTests.tearDown(server);
                    FreecamSelfTests.tearDown(server);
                });
                return StepResult.pending();
            }
            if (!run.future.isDone()) {
                return StepResult.pending();
            }
            if (run.future.isCompletedExceptionally()) {
                return StepResult.fail("Aufräumen am Server: " + run.future.exceptionNow());
            }
            return StepResult.pass();
        })));
        CovFreecamShowcase.register();
        CovFreecamIrisShowcase.register();
    }

    static Phase phase(String name, int timeout, FreecamSelfTests.Body body) {
        return FreecamSelfTests.phase(name, timeout, body);
    }

    static SelfTest.ClientStep guarded(Phase... phases) {
        SelfTest.ClientStep inner = FreecamSelfTests.flow(phases);
        return ctx -> {
            StepResult result;
            try {
                result = inner.tick(ctx);
            } catch (RuntimeException e) {
                result = StepResult.fail(String.valueOf(e));
            }
            if (result.status() != StepResult.Status.PENDING) {
                calm();
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null && mc.level.dimension() != Level.OVERWORLD && FreecamSelfTests.SITE.get() != null) {
                    ctx.onServer(CovFreecamSelfTests::home);
                }
            }
            return result;
        };
    }

    static void home(MinecraftServer server) {
        ServerPlayer player = FreecamSelfTests.player(server);
        Vec3 stand = FreecamSelfTests.standPoint();
        player.teleportTo(server.overworld(), stand.x, stand.y, stand.z, Set.of(), -90.0F, 0.0F, true);
    }

    static StepResult pending(String what) {
        return new StepResult(StepResult.Status.PENDING, what);
    }

    static boolean stall() {
        try {
            Thread.sleep(HITCH_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static Phase hitch() {
        return phase("Ruckler vor dem Start", 20, (ctx, run) -> stall() ? StepResult.pass() : StepResult.fail("Ruckler unterbrochen"));
    }

    static void save() {
        if (SAVED.get() != null) {
            return;
        }
        FirstUseHints.count(FirstUseHints.FREECAM_INTRO);
        if (!GatePlaceHint.WORLDS.isLoaded()) {
            GatePlaceHint.WORLDS.load();
        }
        InputConfig input = InputConfig.get();
        SAVED.set(new Saved(GSON.toJson(FirstUseHints.CONFIG.get()), GSON.toJson(GatePlaceHint.WORLDS.get()), input.keyboardFallback,
                input.keyboard.startInAngle, TransmitterInput.INSTANCE.suppressDevice()));
    }

    static void restoreInput() {
        Saved saved = SAVED.get();
        TransmitterInput.INSTANCE.simulate(null);
        if (saved != null) {
            InputConfig input = InputConfig.get();
            input.keyboardFallback = saved.keyboardFallback();
            input.keyboard.startInAngle = saved.startInAngle();
            TransmitterInput.INSTANCE.setSuppressDevice(saved.suppressDevice());
        }
    }

    static void restoreSaved() {
        restoreInput();
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            FirstUseHints.CONFIG.set(GSON.fromJson(saved.hints(), HintMemory.Seen.class));
            GatePlaceHint.WORLDS.set(GSON.fromJson(saved.worlds(), GateHintMemory.Worlds.class));
        }
    }

    static void releaseKeys() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        for (KeyMapping key : List.of(InputKeys.THROTTLE_UP, InputKeys.THROTTLE_DOWN, InputKeys.PITCH_FORWARD, InputKeys.PITCH_BACK, InputKeys.ROLL_LEFT,
                InputKeys.ROLL_RIGHT, InputKeys.EXIT, InputKeys.MODE_ACRO, InputKeys.MODE_ANGLE, InputKeys.MODE_HORIZON, InputKeys.FLIGHT_MODE)) {
            key.setDown(false);
        }
    }

    static void calm() {
        Minecraft mc = Minecraft.getInstance();
        FreecamSession.INSTANCE.clearTestInput();
        releaseKeys();
        restoreInput();
        CovFreecamOsdProbe.restore();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        if (FreecamSession.active()) {
            FreecamSession.INSTANCE.end(mc, FreecamRules.End.BUTTON);
        }
        FreecamConfig config = FreecamConfig.get();
        if (!config.osd) {
            config.osd = true;
            FreecamConfig.save();
        }
    }

    static Vector3d position() {
        return FreecamSession.INSTANCE.position(new Vector3d());
    }

    static Quaternionf body() {
        return FreecamSession.INSTANCE.orientation(new Quaternionf());
    }

    static double angleDeg(Quaternionf a, Quaternionf b) {
        double dot = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w) / Math.sqrt(a.lengthSquared() * b.lengthSquared());
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot)));
    }

    static double pitchDeg(Quaternionf body) {
        Vector3f forward = body.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return Math.toDegrees(Math.asin(Math.clamp(forward.y, -1.0F, 1.0F)));
    }

    static double rollDeg(Quaternionf body) {
        Vector3f right = body.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        return Math.toDegrees(Math.asin(Math.clamp(right.y, -1.0F, 1.0F)));
    }

    static double headingDeg(Quaternionf body) {
        Vector3f forward = body.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return Math.toDegrees(Math.atan2(-forward.x, forward.z));
    }

    static double wrap(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    static float hover() {
        return FreecamSelfTests.hover();
    }

    static StepResult startPlain(SelfTest.StepContext ctx, Run run) {
        FreecamSession.INSTANCE.clearTestInput();
        return FreecamSelfTests.start(ctx, run);
    }

    static StepResult startByKey(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            if (FreecamSession.active()) {
                return StepResult.fail("Freecam läuft schon");
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            FreecamSelfTests.clickKey(FreecamClient.key());
            return StepResult.pending();
        }
        return FreecamSelfTests.until(FreecamSession.active(), "F6 startet nicht: " + FreecamSession.INSTANCE.lastDenial());
    }

    static StepResult pressK(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            if (!FreecamSession.active()) {
                return StepResult.fail("Freecam aus");
            }
            FreecamSelfTests.clickKey(UiClient.configuratorKey());
            return StepResult.pending();
        }
        if (mc.gui.screen() instanceof ConfiguratorScreen screen && screen.freecam() && screen.activeTab() instanceof FreecamTab && run.waited(ctx) > 4) {
            return StepResult.pass();
        }
        return pending("K öffnet den Tab Freecam nicht: " + mc.gui.screen());
    }

    private static StepResult startPose(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return StepResult.pending();
        }
        if (run.waited(ctx) == 0) {
            if (FreecamSession.active() || mc.gui.screen() != null) {
                return StepResult.fail("Ausgangslage falsch: Freecam " + FreecamSession.active() + ", Fenster " + mc.gui.screen());
            }
            run.sent = ClientNet.sentCount();
            run.player = player.position();
            ctx.state().put(INTRO_BEFORE, FreecamClient.introShown());
            FreecamSelfTests.clickKey(FreecamClient.key());
            return StepResult.pending();
        }
        if (!FreecamSession.active()) {
            return pending("F6 startet nicht: " + FreecamSession.INSTANCE.lastDenial());
        }
        Vector3d camera = new Vector3d();
        FreecamSession.INSTANCE.cameraPose(camera, new Quaternionf());
        Vec3 eye = player.getEyePosition();
        double offset = eye.distanceTo(new Vec3(camera.x, camera.y, camera.z));
        Quaternionf body = body();
        double heading = wrap(headingDeg(body) - player.getYRot());
        double pitch = pitchDeg(body);
        double roll = rollDeg(body);
        List<String> problems = new ArrayList<>();
        if (offset > 0.02) {
            problems.add(String.format(Locale.ROOT, "Kamera %.3f Blöcke neben dem Auge", offset));
        }
        if (Math.abs(heading) > 0.5) {
            problems.add(String.format(Locale.ROOT, "Gieren %.2f° neben der Blickrichtung %.1f°", heading, player.getYRot()));
        }
        if (Math.abs(pitch) > 0.5 || Math.abs(roll) > 0.5) {
            problems.add(String.format(Locale.ROOT, "nicht waagerecht: Nick %.2f°, Roll %.2f°", pitch, roll));
        }
        if (FreecamSession.INSTANCE.armed() || !FreecamSession.INSTANCE.parked()) {
            problems.add("nicht unscharf geparkt");
        }
        if (FreecamSession.INSTANCE.preset() != FreecamPreset.FIVE) {
            problems.add("Größe " + FreecamSession.INSTANCE.preset() + " statt 5″");
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        run.mark.set(position());
        run.ref = body;
        run.notes.add(String.format(Locale.ROOT, "Start %.4f Blöcke vom Auge, Gieren %+.3f° zur Blickrichtung %.1f°, Nick %+.3f°, Roll %+.3f°", offset,
                heading, player.getYRot(), pitch, roll));
        return StepResult.pass();
    }

    private static @Nullable String introToast() {
        ThemedToast toast = ThemedToast.find(Minecraft.getInstance().gui.toastManager(), FreecamSession.TOAST_TOKEN);
        if (toast == null) {
            return null;
        }
        Component message = toast.message();
        if (message != null && message.getContents() instanceof TranslatableContents contents && "propwash.freecam.intro".equals(contents.getKey())) {
            return message.getString();
        }
        return null;
    }

    private static StepResult introShown(SelfTest.StepContext ctx, Run run) {
        int before = (int) ctx.state().getOrDefault(INTRO_BEFORE, -1);
        if (FreecamClient.introShown() != before + 1) {
            return StepResult.fail("Hinweis-Zähler " + before + " → " + FreecamClient.introShown() + " statt +1");
        }
        if (FirstUseHints.count(FirstUseHints.FREECAM_INTRO) != FreecamClient.INTRO_TIMES) {
            return StepResult.fail("gemerkt " + FirstUseHints.count(FirstUseHints.FREECAM_INTRO) + " statt " + FreecamClient.INTRO_TIMES);
        }
        String text = introToast();
        if (text == null) {
            return pending("Hinweis-Fenster fehlt");
        }
        run.notes.add("Hinweis „" + text + "“");
        return StepResult.pass();
    }

    private static StepResult parkedStill(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !FreecamSession.active()) {
            return StepResult.fail("Freecam endet von selbst: " + FreecamSession.INSTANCE.lastEnd());
        }
        double moved = position().distance(run.mark);
        double turned = run.ref instanceof Quaternionf start ? angleDeg(start, body()) : Double.NaN;
        double walked = player.position().distanceTo(run.player);
        run.value = Math.max(run.value, moved);
        if (moved > 0.001 || !(turned < 0.05) || walked > 0.001) {
            return StepResult.fail(String.format(Locale.ROOT, "nach %d Ticks: Freecam %.4f Blöcke und %.3f° bewegt, Spieler %.4f Blöcke", run.waited(ctx), moved,
                    turned, walked));
        }
        if (FreecamSession.INSTANCE.armed() || !FreecamSession.INSTANCE.parked()) {
            return StepResult.fail("nicht mehr unscharf geparkt");
        }
        if (run.waited(ctx) < PARK_TICKS) {
            return StepResult.pending();
        }
        int sent = ClientNet.sentCount() - run.sent;
        if (sent != 0) {
            return StepResult.fail(sent + " Propwash-Pakete an den Server");
        }
        run.notes.add(String.format(Locale.ROOT, "%d Ticks geparkt (größte Abweichung %.5f Blöcke), Spieler unbewegt, 0 Pakete", PARK_TICKS, run.value));
        run.value = 0.0;
        return StepResult.pass();
    }

    private static StepResult handHudName(SelfTest.StepContext ctx, Run run) {
        if (run.waited(ctx) == 0) {
            CovFreecamOsdProbe.show(OsdElement.CRAFT_NAME);
            return StepResult.pending();
        }
        if (run.waited(ctx) < 10) {
            return StepResult.pending();
        }
        if (!FpvView.active() || !PilotView.active() || !CameraClip.active()) {
            return StepResult.fail("Kamera nicht wie im FPV: FpvView " + FpvView.active() + " (Hand aus), PilotView " + PilotView.active());
        }
        if (!OsdHud.hideVanilla()) {
            return StepResult.fail("Vanilla-HUD sichtbar");
        }
        String expected = FreecamClient.osdName(FreecamPreset.FIVE).getString().toUpperCase(Locale.ROOT);
        String name = CovFreecamOsdProbe.craftName().toUpperCase(Locale.ROOT);
        if (!name.equals(expected) || !name.startsWith("FREECAM · ")) {
            return StepResult.fail("OSD-Name „" + name + "“ statt „" + expected + "“");
        }
        if (!CovFreecamOsdProbe.drawn(OsdElement.CRAFT_NAME)) {
            return pending("Name nicht gezeichnet");
        }
        CovFreecamOsdProbe.restore();
        run.notes.add("Hand aus (FpvView), Vanilla-HUD aus, OSD-Name „" + name + "“ gezeichnet");
        return StepResult.pass();
    }

    static StepResult endBy(SelfTest.StepContext ctx, Run run, Way way) {
        Minecraft mc = Minecraft.getInstance();
        if (run.tick < 0) {
            if (!FreecamSession.active()) {
                return StepResult.fail("Freecam läuft nicht");
            }
            switch (way) {
                case KEY -> FreecamSelfTests.clickKey(FreecamClient.key());
                case EXIT -> InputKeys.EXIT.setDown(true);
                case BUTTON -> {
                    if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof FreecamTab tab)
                            || tab.exitButton() == null) {
                        return StepResult.fail("Tab Freecam mit „Freecam beenden“ nicht offen");
                    }
                    FlatButton exit = tab.exitButton();
                    if (!exit.visible || !exit.active) {
                        return StepResult.fail("„Freecam beenden“ nicht bedienbar");
                    }
                    exit.press();
                }
            }
            run.tick = ctx.tick();
            return StepResult.pending();
        }
        int since = ctx.tick() - run.tick;
        if (way == Way.EXIT && since >= 2) {
            InputKeys.EXIT.setDown(false);
        }
        if (FreecamSession.active()) {
            return since > 1 ? StepResult.fail("Freecam läuft " + since + " Ticks nach " + way + " weiter") : StepResult.pending();
        }
        if (run.count == 0) {
            run.count = since;
        }
        FreecamRules.End expected = way == Way.BUTTON ? FreecamRules.End.BUTTON : FreecamRules.End.KEY;
        if (FreecamSession.INSTANCE.lastEnd() != expected) {
            return StepResult.fail("Ende mit " + FreecamSession.INSTANCE.lastEnd() + " statt " + expected);
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.getCameraEntity() != player || FpvView.active() || PilotView.active() || FpvView.isPilotBody(player)) {
            return StepResult.fail("Kamera nach " + since + " Tick(s) nicht am Spieler");
        }
        double eye = mc.gameRenderer.mainCamera().position().distanceTo(player.getEyePosition());
        if (eye > 0.05) {
            return since > 3 ? StepResult.fail(String.format(Locale.ROOT, "Spielkamera %.3f Blöcke vom Auge nach %d Ticks", eye, since)) : StepResult.pending();
        }
        if (way == Way.BUTTON && mc.gui.screen() != null) {
            return since > 10 ? StepResult.fail("K-Menü bleibt nach dem Ende offen") : StepResult.pending();
        }
        InputKeys.EXIT.setDown(false);
        run.notes.add(way + ": aus nach " + run.count + " Tick(s), Kamera am Spieler nach " + since);
        return StepResult.pass();
    }

    private static StepResult startCountingIntro(SelfTest.StepContext ctx, Run run, boolean expectHint) {
        Minecraft mc = Minecraft.getInstance();
        if (run.waited(ctx) == 0) {
            if (FreecamSession.active()) {
                return StepResult.fail("Freecam läuft schon");
            }
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ctx.state().put(INTRO_BEFORE, FreecamClient.introShown());
            run.count = FirstUseHints.count(FirstUseHints.FREECAM_INTRO);
            FreecamSelfTests.clickKey(FreecamClient.key());
            return StepResult.pending();
        }
        if (!FreecamSession.active()) {
            return pending("F6 startet nicht: " + FreecamSession.INSTANCE.lastDenial());
        }
        int before = (int) ctx.state().get(INTRO_BEFORE);
        int shown = FreecamClient.introShown() - before;
        if (expectHint) {
            if (shown != 1) {
                return StepResult.fail("nach dem Zurücksetzen " + shown + " Hinweise statt 1");
            }
            run.notes.add("nach „Hinweise zurücksetzen“ wieder ein Hinweis");
        } else {
            if (shown != 0 || FirstUseHints.count(FirstUseHints.FREECAM_INTRO) != run.count) {
                return StepResult.fail("zweiter Start zeigt den Hinweis noch einmal (" + shown + ", gemerkt " + FirstUseHints.count(FirstUseHints.FREECAM_INTRO) + ")");
            }
            run.notes.add("zweiter Start ohne Hinweis");
        }
        return StepResult.pass();
    }

    private static StepResult resetHints(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof GuideTab guide)) {
            return StepResult.fail("Tab Flugschule nicht offen");
        }
        if (run.waited(ctx) == 0) {
            FlatButton reset = guide.resetHintsButton();
            if (reset == null || !reset.visible || !reset.active) {
                return StepResult.fail("„Hinweise zurücksetzen“ fehlt");
            }
            if (FirstUseHints.fresh(FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES)) {
                return StepResult.fail("Hinweis vor dem Zurücksetzen nicht gemerkt");
            }
            reset.press();
            return StepResult.pending();
        }
        if (!FirstUseHints.fresh(FirstUseHints.FREECAM_INTRO, FreecamClient.INTRO_TIMES)) {
            return StepResult.fail("Hinweis nach dem Zurücksetzen weiter gemerkt");
        }
        run.notes.add("„Hinweise zurücksetzen“ vergisst freecam_intro");
        return StepResult.pass();
    }

    private static StepResult armedMoving(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = FreecamSelfTests.site();
        if (!FreecamSelfTests.armAt(run, site.getX() + 2.5, site.getY() + 20.0, site.getZ() + 0.5, -90.0F)) {
            return FreecamSelfTests.notArmed(run);
        }
        FreecamSession.INSTANCE.setTestInput(new StickPositions(0.2F, 0.35F, 0.2F, hover()), true, StabMode.ANGLE);
        if (++run.count < 15) {
            if (run.count < 8) {
                FreecamSession.INSTANCE.testPush(3.0, 0.0, 1.0);
            }
            return StepResult.pending();
        }
        Quaternionf body = body();
        Vector3d velocity = FreecamSession.INSTANCE.velocity(new Vector3d());
        run.notes.add(String.format(Locale.ROOT, "vor dem Entschärfen %.1f m/s, Nick %.1f°, Roll %.1f°", velocity.length(), pitchDeg(body), rollDeg(body)));
        return StepResult.pass();
    }

    private static StepResult disarmInAir(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        FreecamSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hover()), false, StabMode.ANGLE);
        if (FreecamSession.INSTANCE.armed() || !FreecamSession.INSTANCE.parked() || run.waited(ctx) < 2) {
            return pending("entschärft nicht");
        }
        run.mark.set(position());
        Quaternionf body = body();
        run.ref = body;
        if (Math.abs(pitchDeg(body)) > 0.5 || Math.abs(rollDeg(body)) > 0.5) {
            return StepResult.fail(String.format(Locale.ROOT, "geparkt nicht waagerecht: Nick %.2f°, Roll %.2f°", pitchDeg(body), rollDeg(body)));
        }
        run.notes.add(String.format(Locale.ROOT, "entschärft in %.1f m über dem Platz", run.mark.y - FreecamSelfTests.site().getY()));
        return StepResult.pass();
    }

    private static StepResult airParked(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        double moved = position().distance(run.mark);
        double turned = run.ref instanceof Quaternionf start ? angleDeg(start, body()) : Double.NaN;
        run.value = Math.max(run.value, moved);
        if (moved >= 0.01 || !(turned < 0.1)) {
            return StepResult.fail(String.format(Locale.ROOT, "nach %d Ticks %.4f Blöcke und %.3f° bewegt", run.waited(ctx), moved, turned));
        }
        if (FreecamSession.INSTANCE.armed()) {
            return StepResult.fail("wieder scharf");
        }
        if (run.waited(ctx) < AIR_PARK_TICKS) {
            return StepResult.pending();
        }
        run.notes.add(String.format(Locale.ROOT, "%d Ticks entschärft in der Luft, größte Abweichung %.5f Blöcke, Lage %.4f°", AIR_PARK_TICKS, run.value, turned));
        run.value = 0.0;
        return StepResult.pass();
    }

    static SimulatedRadio radio() {
        SimulatedRadio radio = new SimulatedRadio("propwash-cov-freecam-radio", "Propwash-Testfunke (Freecam)",
                ProfileDefaults.radio("Propwash-Testfunke", RADIO_AXES), RADIO_AXES, 0);
        for (int axis = 0; axis < RADIO_AXES; axis++) {
            radio.setMicros(axis, axis == THROTTLE_AXIS || axis >= ProfileDefaults.AUX_ARM ? 1000 : 1500);
        }
        return radio;
    }

    private static @Nullable String sticksDiffer(StickPositions sim, StickPositions radio) {
        double d = Math.max(Math.max(Math.abs(sim.roll() - radio.roll()), Math.abs(sim.pitch() - radio.pitch())),
                Math.max(Math.abs(sim.yaw() - radio.yaw()), Math.abs(sim.throttle() - radio.throttle())));
        return d < 0.002 ? null : String.format(Locale.ROOT, "Sim %s gegen Funke %s", sim, radio);
    }

    private static StepResult radioSticks(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        SimulatedRadio radio;
        if (run.waited(ctx) == 0) {
            radio = radio();
            radio.setAxis(ROLL_AXIS, 0.5F);
            radio.setAxis(PITCH_AXIS, -0.4F);
            radio.setMicros(THROTTLE_AXIS, 1600);
            radio.setAxis(YAW_AXIS, 0.3F);
            ctx.state().put("cov_radio", radio);
            TransmitterInput.INSTANCE.simulate(radio);
            return StepResult.pending();
        }
        radio = (SimulatedRadio) ctx.state().get("cov_radio");
        RadioState state = RadioState.INSTANCE;
        if (state.source() != RadioSource.JOYSTICK || !state.connected()) {
            return pending("Test-Funke nicht verbunden: " + state.source());
        }
        StickPositions sim = FreecamSession.INSTANCE.sticks();
        StickPositions sticks = state.sticks().clamped();
        if (run.count == 0) {
            if (run.waited(ctx) < 8) {
                return StepResult.pending();
            }
            String differ = sticksDiffer(sim, sticks);
            if (differ != null) {
                return pending(differ);
            }
            if (Math.abs(sim.roll()) < 0.3F || Math.abs(sim.pitch()) < 0.2F || Math.abs(sim.yaw()) < 0.15F || sim.throttle() < 0.4F || sim.throttle() > 0.8F) {
                return StepResult.fail("Knüppel kommen nicht an: " + sim);
            }
            run.other.set(sim.roll(), sim.pitch(), sim.yaw());
            radio.setAxis(ROLL_AXIS, -0.5F);
            radio.setAxis(PITCH_AXIS, 0.4F);
            radio.setMicros(THROTTLE_AXIS, 1200);
            run.count = 1;
            run.tick = ctx.tick();
            return StepResult.pending();
        }
        if (Math.signum(sim.roll()) == Math.signum(run.other.x) || Math.signum(sim.pitch()) == Math.signum(run.other.y) || sim.throttle() > 0.35F) {
            return ctx.tick() - run.tick > 10 ? StepResult.fail("Sim folgt der Funke nicht: " + sim) : StepResult.pending();
        }
        String differ = sticksDiffer(sim, sticks);
        if (differ != null) {
            return pending(differ);
        }
        run.notes.add("Test-Funke: Sim folgt in " + (ctx.tick() - run.tick) + " Tick(s), " + sim);
        return StepResult.pass();
    }

    private static StepResult radioMode(SelfTest.StepContext ctx, Run run, int micros, StabMode expected) {
        SimulatedRadio radio = (SimulatedRadio) ctx.state().get("cov_radio");
        if (radio == null) {
            return StepResult.fail("keine Test-Funke");
        }
        if (run.waited(ctx) == 0) {
            radio.setMicros(ProfileDefaults.AUX_MODE, micros);
            return StepResult.pending();
        }
        if (FreecamSession.INSTANCE.flightMode() != expected) {
            return pending("Modus " + FreecamSession.INSTANCE.flightMode());
        }
        run.notes.add("Schalter " + micros + " µs → " + expected + " nach " + run.waited(ctx) + " Tick(s)");
        return StepResult.pass();
    }

    private static StepResult padSticks(SelfTest.StepContext ctx, Run run) {
        SimulatedRadio pad;
        if (run.waited(ctx) == 0) {
            pad = new SimulatedRadio("propwash-cov-freecam-pad", "Propwash-Testpad (Freecam)", ProfileDefaults.gamepad("Propwash-Testpad", StickMode.DEFAULT),
                    ProfileDefaults.PAD_AXES, ProfileDefaults.PAD_BUTTONS);
            pad.setAxis(ProfileDefaults.PAD_LEFT_TRIGGER, -1.0F);
            pad.setAxis(ProfileDefaults.PAD_RIGHT_TRIGGER, -1.0F);
            pad.setAxis(ProfileDefaults.padAxis(StickMode.DEFAULT, StickChannel.ROLL), 0.7F);
            pad.setAxis(ProfileDefaults.padAxis(StickMode.DEFAULT, StickChannel.PITCH), -0.6F);
            pad.setAxis(ProfileDefaults.padAxis(StickMode.DEFAULT, StickChannel.YAW), 0.5F);
            pad.setAxis(ProfileDefaults.padAxis(StickMode.DEFAULT, StickChannel.THROTTLE), -0.5F);
            ctx.state().put("cov_pad", pad);
            TransmitterInput.INSTANCE.simulate(pad);
            return StepResult.pending();
        }
        RadioState state = RadioState.INSTANCE;
        if (!"propwash-cov-freecam-pad".equals(state.deviceGuid()) || !state.connected()) {
            return pending("Testpad nicht verbunden");
        }
        if (run.waited(ctx) < 8) {
            return StepResult.pending();
        }
        StickPositions sim = FreecamSession.INSTANCE.sticks();
        String differ = sticksDiffer(sim, state.sticks().clamped());
        if (differ != null) {
            return pending(differ);
        }
        if (Math.abs(sim.roll()) < 0.3F || Math.abs(sim.pitch()) < 0.2F || Math.abs(sim.yaw()) < 0.15F) {
            return StepResult.fail("Sticks des Gamepads kommen nicht an: " + sim);
        }
        run.notes.add("Gamepad: Sim folgt den Sticks " + sim);
        return StepResult.pass();
    }

    private static StepResult padMode(SelfTest.StepContext ctx, Run run) {
        SimulatedRadio pad = (SimulatedRadio) ctx.state().get("cov_pad");
        if (pad == null) {
            return StepResult.fail("kein Testpad");
        }
        int waited = run.waited(ctx);
        StabMode mode = FreecamSession.INSTANCE.flightMode();
        if (run.count == 0) {
            if (mode != StabMode.ANGLE) {
                return waited > 40 ? StepResult.fail("Gamepad startet nicht in Angle: " + mode) : StepResult.pending();
            }
            run.count = 1;
            run.tick = ctx.tick();
            pad.setButton(ProfileDefaults.PAD_LEFT_SHOULDER, true);
            return StepResult.pending();
        }
        int since = ctx.tick() - run.tick;
        if (since == 3) {
            pad.setButton(ProfileDefaults.PAD_LEFT_SHOULDER, false);
        }
        if (run.count == 1) {
            if (since < 4 || mode != StabMode.ACRO) {
                return since > 40 ? StepResult.fail("Schultertaste schaltet nicht auf Acro: " + mode) : StepResult.pending();
            }
            run.count = 2;
            run.tick = ctx.tick();
            pad.setButton(ProfileDefaults.PAD_LEFT_SHOULDER, true);
            return StepResult.pending();
        }
        if (since < 4 || mode != StabMode.ANGLE) {
            return since > 40 ? StepResult.fail("Schultertaste schaltet nicht zurück auf Angle: " + mode) : StepResult.pending();
        }
        pad.setButton(ProfileDefaults.PAD_LEFT_SHOULDER, false);
        run.notes.add("Gamepad: Schultertaste Angle → Acro → Angle");
        return StepResult.pass();
    }

    private static StepResult keyboardOn(SelfTest.StepContext ctx, Run run) {
        if (run.waited(ctx) == 0) {
            TransmitterInput.INSTANCE.simulate(null);
            TransmitterInput.INSTANCE.setSuppressDevice(true);
            InputConfig.get().keyboardFallback = true;
            return StepResult.pending();
        }
        return FreecamSelfTests.until(RadioState.INSTANCE.source() == RadioSource.KEYBOARD, "Quelle " + RadioState.INSTANCE.source());
    }

    private static StepResult keyboardSticks(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        int t = run.waited(ctx);
        StickPositions sim = FreecamSession.INSTANCE.sticks();
        if (RadioState.INSTANCE.source() != RadioSource.KEYBOARD) {
            return StepResult.fail("Quelle nicht Tastatur: " + RadioState.INSTANCE.source());
        }
        switch (t) {
            case 0 -> mc.options.keyUp.setDown(true);
            case 25 -> {
                mc.options.keyUp.setDown(false);
                run.value = sim.throttle();
                if (sim.throttle() < 0.15F) {
                    return StepResult.fail("W gibt kein Gas: " + sim);
                }
                mc.options.keyDown.setDown(true);
            }
            case 35 -> {
                mc.options.keyDown.setDown(false);
                if (sim.throttle() > run.value - 0.1) {
                    return StepResult.fail(String.format(Locale.ROOT, "S nimmt kein Gas weg: %.3f → %.3f", run.value, sim.throttle()));
                }
                run.notes.add(String.format(Locale.ROOT, "Tastatur: W Gas %.2f, S %.2f", run.value, sim.throttle()));
                InputKeys.PITCH_FORWARD.setDown(true);
            }
            case 50 -> {
                InputKeys.PITCH_FORWARD.setDown(false);
                if (Math.abs(sim.pitch()) < 0.2F) {
                    return StepResult.fail("Pfeil hoch gibt kein Nick: " + sim);
                }
                run.other.y = sim.pitch();
                InputKeys.ROLL_RIGHT.setDown(true);
            }
            case 65 -> {
                InputKeys.ROLL_RIGHT.setDown(false);
                if (sim.roll() < 0.2F) {
                    return StepResult.fail("Pfeil rechts gibt kein Roll rechts: " + sim);
                }
                run.other.x = sim.roll();
                InputKeys.ROLL_LEFT.setDown(true);
            }
            case 80 -> {
                InputKeys.ROLL_LEFT.setDown(false);
                if (sim.roll() > -0.2F) {
                    return StepResult.fail("Pfeil links gibt kein Roll links: " + sim);
                }
                run.notes.add(String.format(Locale.ROOT, "Tastatur: Pfeil hoch Nick %.2f, rechts Roll %.2f, links Roll %.2f", run.other.y, run.other.x,
                        sim.roll()));
            }
            case 100 -> {
                if (Math.abs(sim.roll()) > 0.05F || Math.abs(sim.pitch()) > 0.05F) {
                    return StepResult.fail("Knüppel kehren ohne Taste nicht zur Mitte: " + sim);
                }
                String differ = sticksDiffer(sim, RadioState.INSTANCE.sticks().clamped());
                if (differ != null) {
                    return StepResult.fail("Sim weicht von der Tastatur ab: " + differ);
                }
                return StepResult.pass();
            }
            default -> {
            }
        }
        return StepResult.pending();
    }

    private static StepResult keyMode(SelfTest.StepContext ctx, Run run, KeyMapping key, StabMode expected) {
        int t = run.waited(ctx);
        if (t == 0) {
            key.setDown(true);
            return StepResult.pending();
        }
        if (t == 3) {
            key.setDown(false);
        }
        if (t < 4 || FreecamSession.INSTANCE.flightMode() != expected) {
            return t > 30 ? StepResult.fail("Taste " + key.getName() + " schaltet nicht auf " + expected + ": " + FreecamSession.INSTANCE.flightMode())
                    : StepResult.pending();
        }
        run.notes.add("Taste " + key.getName() + " → " + expected);
        return StepResult.pass();
    }

    private static List<SettingsSearch.Hit> search(ConfiguratorScreen screen, String query) {
        return SettingsIndex.build(screen).search(query, 12);
    }

    private static StepResult searchControl(SelfTest.StepContext ctx, Run run) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) || screen.freecam()) {
            return StepResult.fail("normales K-Menü nicht offen");
        }
        Map<String, List<String>> wanted = Map.of("action_cam", List.of("Action-Cam", "Action cam"), "osd", List.of("OSD"));
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : wanted.entrySet()) {
            boolean hit = false;
            for (String query : entry.getValue()) {
                List<SettingsSearch.Hit> hits = search(screen, query);
                if (hits.stream().anyMatch(h -> entry.getKey().equals(h.entry().tabId()))) {
                    hit = true;
                    found.add(query + " → " + entry.getKey() + " (" + hits.size() + " Treffer)");
                    break;
                }
            }
            if (!hit) {
                return StepResult.fail("Gegenprobe: Suche " + entry.getValue() + " findet im normalen K-Menü nichts im Tab " + entry.getKey());
            }
        }
        run.notes.add("Gegenprobe im normalen K-Menü: " + String.join(", ", found));
        return StepResult.pass();
    }

    private static StepResult searchFreecam(SelfTest.StepContext ctx, Run run) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) || !screen.freecam()) {
            return StepResult.fail("Freecam-K-Menü nicht offen");
        }
        Set<String> visible = Set.of(FreecamTab.ID, de.lardtom.propwash.ui.client.tab.RatesTab.ID, de.lardtom.propwash.input.client.screen.ReceiverTab.ID,
                de.lardtom.propwash.input.client.screen.ModesTab.ID);
        List<String> tabs = new ArrayList<>();
        for (ConfigTab tab : screen.tabs()) {
            tabs.add(tab.id());
        }
        if (!Set.copyOf(tabs).equals(visible)) {
            return StepResult.fail("Tabs " + tabs);
        }
        List<String> seen = new ArrayList<>();
        for (String query : List.of("Action-Cam", "Action cam", "OSD")) {
            List<SettingsSearch.Hit> hits = search(screen, query);
            for (SettingsSearch.Hit hit : hits) {
                if (!visible.contains(hit.entry().tabId())) {
                    return StepResult.fail("Suche „" + query + "“ findet „" + hit.entry().label() + "“ im ausgeblendeten Tab " + hit.entry().tabId());
                }
            }
            seen.add(query + ": " + hits.size() + (hits.isEmpty() ? "" : " (" + String.join(", ", hits.stream().map(h -> h.entry().tabId() + "/"
                    + h.entry().label()).toList()) + ")"));
        }
        run.notes.add("Freecam-Suche: " + String.join("; ", seen));
        return StepResult.pass();
    }

    private static StepResult fastTurning(SelfTest.StepContext ctx, Run run) {
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        BlockPos site = FreecamSelfTests.site();
        if (!FreecamSelfTests.armAt(run, site.getX() - 6.5, site.getY() + 25.0, site.getZ() + 0.5, -90.0F)) {
            return FreecamSelfTests.notArmed(run);
        }
        FreecamSession.INSTANCE.setTestInput(new StickPositions(0.3F, 0.0F, 0.5F, hover()), true, StabMode.ANGLE);
        FreecamSession.INSTANCE.testPush(FAST, 0.0, 0.0);
        return FreecamSelfTests.until(++run.count > 8, "Fahrt aufnehmen");
    }

    static Vector3d bodyRates(SimSnapshot s, AirframeSpec p) {
        Quaterniond q = new Quaterniond(s.qx, s.qy, s.qz, s.qw).normalize();
        Vector3d spin = q.transformInverse(new Vector3d(s.lx, s.ly, s.lz));
        return spin.set(spin.x / p.inertiaXx, spin.y / p.inertiaYy, spin.z / p.inertiaZz);
    }

    private static StepResult switchToSeven(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof FreecamTab tab)) {
            return StepResult.fail("Tab Freecam nicht offen");
        }
        DroneSim old = FreecamSession.INSTANCE.sim();
        if (old == null || FreecamSession.INSTANCE.preset() != FreecamPreset.FIVE) {
            return StepResult.fail("keine 5″-Freecam");
        }
        SimSnapshot before = new SimSnapshot();
        old.copyStateTo(before);
        Vector3d rates = bodyRates(before, old.params());
        double speed = Math.sqrt(before.vx * before.vx + before.vy * before.vy + before.vz * before.vz);
        if (speed < FAST - 1.5) {
            return StepResult.fail(String.format(Locale.ROOT, "vor dem Wechsel nur %.2f m/s", speed));
        }
        if (rates.length() < 0.2) {
            return StepResult.fail(String.format(Locale.ROOT, "vor dem Wechsel kaum Drehung: %.3f rad/s", rates.length()));
        }
        FlatButton chip = tab.chip(FreecamPreset.SEVEN);
        if (chip == null) {
            return StepResult.fail("Chip 7″ fehlt");
        }
        chip.press();
        DroneSim fresh = FreecamSession.INSTANCE.sim();
        if (fresh == null || fresh == old || FreecamSession.INSTANCE.preset() != FreecamPreset.SEVEN) {
            return StepResult.fail("Chip 7″ wechselt die Sim nicht");
        }
        SimSnapshot after = new SimSnapshot();
        fresh.copyStateTo(after);
        Vector3d ratesAfter = bodyRates(after, fresh.params());
        double moved = Math.sqrt(Math.pow(after.px - before.px, 2) + Math.pow(after.py - before.py, 2) + Math.pow(after.pz - before.pz, 2));
        double tempo = Math.sqrt(Math.pow(after.vx - before.vx, 2) + Math.pow(after.vy - before.vy, 2) + Math.pow(after.vz - before.vz, 2));
        double turned = Math.abs(after.qw * before.qw + after.qx * before.qx + after.qy * before.qy + after.qz * before.qz);
        double attitude = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, turned)));
        double spin = ratesAfter.distance(rates) / rates.length();
        double expectedMass = AirframeSpec.of(FreecamPreset.SEVEN.build()).massKg;
        List<String> problems = new ArrayList<>();
        if (moved > 1.0E-9 || tempo > 1.0E-9 || attitude > 1.0E-4) {
            problems.add(String.format(Locale.ROOT, "Position %.2e, Tempo %.2e, Lage %.2e°", moved, tempo, attitude));
        }
        if (spin > 1.0E-6) {
            problems.add(String.format(Locale.ROOT, "Drehraten %s → %s", rates, ratesAfter));
        }
        if (!after.armed) {
            problems.add("unscharf");
        }
        if (Math.abs(fresh.params().massKg - expectedMass) > 1.0E-9) {
            problems.add("Masse " + fresh.params().massKg + " statt " + expectedMass);
        }
        if (!problems.isEmpty()) {
            return StepResult.fail("Wechsel bei " + String.format(Locale.ROOT, "%.1f m/s: ", speed) + String.join("; ", problems));
        }
        run.notes.add(String.format(Locale.ROOT, "Wechsel 5″ → 7″ bei %.1f m/s und %.2f rad/s: Position, Lage, Tempo gleich, Drehraten gleich (rel. %.1e), "
                + "Masse %.3f kg, scharf", speed, rates.length(), spin, expectedMass));
        return StepResult.pass();
    }

    private static StepResult sevenKeepsGoing(SelfTest.StepContext ctx, Run run) {
        DroneSim sim = FreecamSession.INSTANCE.sim();
        if (sim == null || FreecamSession.INSTANCE.preset() != FreecamPreset.SEVEN) {
            return StepResult.fail("7″ nicht mehr aktiv");
        }
        if (run.waited(ctx) < 5) {
            return StepResult.pending();
        }
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        if (!sim.telemetry().armed) {
            return StepResult.fail("7″ nach dem Wechsel unscharf");
        }
        if (velocity.length() < 8.0) {
            return StepResult.fail(String.format(Locale.ROOT, "7″ verliert den Schwung: %.2f m/s", velocity.length()));
        }
        run.notes.add(String.format(Locale.ROOT, "7″ scharf, %.1f m/s nach 5 Ticks", velocity.length()));
        return StepResult.pass();
    }

    static StepResult backToFive(SelfTest.StepContext ctx, Run run) {
        FreecamSession.INSTANCE.clearTestInput();
        FreecamSession.INSTANCE.setPreset(FreecamPreset.FIVE);
        return FreecamSelfTests.until(FreecamSession.INSTANCE.preset() == FreecamPreset.FIVE && FreecamPreset.FIVE.key().equals(FreecamConfig.get().preset),
                "zurück auf 5″");
    }

    private static StepResult perSize(SelfTest.StepContext ctx, Run run) {
        FreecamConfig config = FreecamConfig.get();
        FreecamSession session = FreecamSession.INSTANCE;
        if (!FreecamSession.active()) {
            return StepResult.fail("Freecam aus");
        }
        String rc = de.lardtom.propwash.ui.TuneKeys.rateKey(de.lardtom.propwash.sim.Axis.ROLL, "rcRate");
        int t = run.waited(ctx);
        if (t == 0) {
            config.setUptilt(FreecamPreset.WHOOP, 30.0F);
            config.setUptilt(FreecamPreset.SEVEN, 8.0F);
            config.setTune(FreecamPreset.WHOOP, Map.of(rc, 2.0F));
            config.setTune(FreecamPreset.SEVEN, Map.of(rc, 0.6F));
            FreecamConfig.save();
            session.setPreset(FreecamPreset.WHOOP);
            return StepResult.pending();
        }
        if (t == 5) {
            run.other.set(session.cameraUptiltDeg(), VideoState.INSTANCE.fpvFov(), rcRate(session));
            session.setPreset(FreecamPreset.SEVEN);
            return StepResult.pending();
        }
        if (t == 10) {
            double uptilt = session.cameraUptiltDeg();
            double fov = VideoState.INSTANCE.fpvFov();
            double rate = rcRate(session);
            session.setPreset(FreecamPreset.FIVE);
            List<String> problems = new ArrayList<>();
            if (Math.abs(run.other.x - 30.0) > 0.01 || Math.abs(uptilt - 8.0) > 0.01) {
                problems.add(String.format(Locale.ROOT, "Kamerawinkel Whoop %.1f°, 7″ %.1f° statt 30° und 8°", run.other.x, uptilt));
            }
            if (Math.abs(run.other.z - 2.0) > 0.01 || Math.abs(rate - 0.6) > 0.01) {
                problems.add(String.format(Locale.ROOT, "RC-Rate Roll Whoop %.2f, 7″ %.2f statt 2,00 und 0,60", run.other.z, rate));
            }
            if (Math.abs(run.other.y - fov) > 0.01) {
                problems.add(String.format(Locale.ROOT, "Sichtfeld Whoop %.2f°, 7″ %.2f°", run.other.y, fov));
            }
            if (!problems.isEmpty()) {
                return StepResult.fail(String.join("; ", problems));
            }
            run.notes.add(String.format(Locale.ROOT, "Whoop 30° und RC-Rate 2,00, 7″ 8° und 0,60, Sichtfeld beide %.1f° (Standard %.0f°)", fov,
                    FreecamConfig.FOV_DEFAULT));
            return StepResult.pending();
        }
        if (t > 14) {
            double five = session.cameraUptiltDeg();
            float expected = config.uptilt(FreecamPreset.FIVE);
            if (Math.abs(five - expected) > 0.01 || config.fov() != FreecamConfig.FOV_DEFAULT) {
                return StepResult.fail(String.format(Locale.ROOT, "5″ Kamerawinkel %.1f° statt %.1f°, Sichtfeld %.1f°", five, expected, config.fov()));
            }
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    static double rcRate(FreecamSession session) {
        DroneSim sim = session.sim();
        return sim == null ? Double.NaN : sim.config().rates.roll.rcRate;
    }

    private static StepResult sharedFov(SelfTest.StepContext ctx, Run run) {
        FreecamConfig config = FreecamConfig.get();
        FreecamSession session = FreecamSession.INSTANCE;
        int t = run.waited(ctx);
        if (t == 0) {
            run.value = VideoState.INSTANCE.fpvFov();
            config.fovDeg = 100.0F;
            session.setPreset(FreecamPreset.WHOOP);
            return StepResult.pending();
        }
        if (t == 5) {
            run.other.x = VideoState.INSTANCE.fpvFov();
            session.setPreset(FreecamPreset.SEVEN);
            return StepResult.pending();
        }
        if (t == 10) {
            double seven = VideoState.INSTANCE.fpvFov();
            config.fovDeg = FreecamConfig.FOV_DEFAULT;
            FreecamConfig.save();
            session.setPreset(FreecamPreset.FIVE);
            if (Math.abs(run.other.x - seven) > 0.01 || Math.abs(seven - run.value) < 1.0) {
                return StepResult.fail(String.format(Locale.ROOT, "Sichtfeld 100°: Whoop %.2f°, 7″ %.2f° (vorher %.2f°)", run.other.x, seven, run.value));
            }
            run.notes.add(String.format(Locale.ROOT, "Sichtfeld 100° (Bild %.1f° statt %.1f°) gilt für Whoop und 7″", seven, run.value));
            return StepResult.pending();
        }
        return t > 14 ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult osdOffNextFrame(SelfTest.StepContext ctx, Run run) {
        FreecamConfig config = FreecamConfig.get();
        int t = run.waited(ctx);
        if (t == 3) {
            run.count = OsdHud.renderedFrames();
        }
        if (t < 8) {
            return StepResult.pending();
        }
        if (run.tick < 0) {
            if (OsdHud.renderedFrames() == run.count) {
                return StepResult.fail("OSD zeichnet vor dem Ausschalten nicht");
            }
            config.osd = false;
            run.count = OsdHud.renderedFrames();
            run.sent = (int) FreecamSession.INSTANCE.frames();
            run.tick = ctx.tick();
            return StepResult.pending();
        }
        long frames = FreecamSession.INSTANCE.frames() - run.sent;
        if (frames <= 0) {
            return ctx.tick() - run.tick > 5 ? StepResult.fail("keine Frames nach dem Ausschalten") : StepResult.pending();
        }
        int drawn = OsdHud.renderedFrames() - run.count;
        config.osd = true;
        FreecamConfig.save();
        if (drawn != 0 || CovFreecamOsdProbe.data().active) {
            return StepResult.fail("OSD aus zeichnet weiter: " + drawn + " von " + frames + " Frames");
        }
        run.notes.add("OSD aus: " + frames + " Frame(s) bis zum nächsten Tick, keiner gezeichnet");
        return StepResult.pass();
    }

    private static JsonConfig<FreecamConfig> probe(String name) {
        return JsonConfig.create(name, FreecamConfig.class, FreecamConfig::new, FreecamConfig::validate);
    }

    private static StepResult fileRoundTrip(SelfTest.StepContext ctx, Run run) {
        JsonConfig<FreecamConfig> writer = probe(PROBE_CONFIG);
        Path path = writer.path();
        try {
            FreecamConfig values = new FreecamConfig();
            values.preset = FreecamPreset.SEVEN.key();
            String rc = de.lardtom.propwash.ui.TuneKeys.rateKey(de.lardtom.propwash.sim.Axis.ROLL, "rcRate");
            values.setTune(FreecamPreset.WHOOP, Map.of(rc, 1.7F));
            values.setTune(FreecamPreset.SEVEN, Map.of(rc, 0.9F));
            values.setUptilt(FreecamPreset.WHOOP, 28.0F);
            values.setUptilt(FreecamPreset.THREE_FIVE, 12.0F);
            values.fovDeg = 135.0F;
            values.osd = false;
            writer.set(values);
            if (!Files.isRegularFile(path)) {
                return StepResult.fail("Datei " + path + " nicht geschrieben");
            }
            FreecamConfig read = probe(PROBE_CONFIG).load();
            String expected = GSON.toJson(values);
            String actual = GSON.toJson(read);
            if (!expected.equals(actual)) {
                return StepResult.fail("neu gelesen „" + actual + "“ statt „" + expected + "“");
            }
            if (read.preset() != FreecamPreset.SEVEN || read.osd || read.fov() != 135.0F || !read.tune(FreecamPreset.WHOOP).equals(Map.of(rc, 1.7F))
                    || read.uptilt(FreecamPreset.WHOOP) != 28.0F) {
                return StepResult.fail("Werte nach dem Lesen falsch: " + actual);
            }
            run.notes.add("Datei " + path.getFileName() + " geschrieben und gleich gelesen (preset, tunes, uptilt, fovDeg, osd)");
            return StepResult.pass();
        } finally {
            delete(path);
        }
    }

    private static StepResult fileMissing(SelfTest.StepContext ctx, Run run) {
        JsonConfig<FreecamConfig> config = probe(MISSING_CONFIG);
        Path path = config.path();
        try {
            delete(path);
            FreecamConfig read = config.load();
            FreecamConfig defaults = new FreecamConfig();
            FreecamConfig.validate(defaults);
            if (!GSON.toJson(defaults).equals(GSON.toJson(read))) {
                return StepResult.fail("ohne Datei nicht der Standard: " + GSON.toJson(read));
            }
            run.notes.add("ohne Datei Standard (5″, 120°, OSD an)");
            return StepResult.pass();
        } finally {
            delete(path);
        }
    }

    private static StepResult fileMatchesLive(SelfTest.StepContext ctx, Run run) {
        FreecamConfig.save();
        Path path = FreecamConfig.CONFIG.path();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            FreecamConfig read = JsonConfig.read(reader, FreecamConfig.class, null);
            if (read == null) {
                return StepResult.fail(path + " leer");
            }
            FreecamConfig.validate(read);
            if (!GSON.toJson(read).equals(GSON.toJson(FreecamConfig.get()))) {
                return StepResult.fail(path.getFileName() + " weicht vom Stand im Spiel ab");
            }
            run.notes.add(path.getFileName() + " = Stand im Spiel");
            return StepResult.pass();
        } catch (IOException e) {
            return StepResult.fail(path + ": " + e);
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
            Files.deleteIfExists(path.resolveSibling(path.getFileName() + ".tmp"));
            Files.deleteIfExists(path.resolveSibling(path.getFileName() + ".broken"));
        } catch (IOException ignored) {
            return;
        }
    }

    private static StepResult toNether(SelfTest.StepContext ctx, Run run) {
        if (run.future == null) {
            if (!FreecamSession.active()) {
                return StepResult.fail("Freecam aus");
            }
            NETHER.set(null);
            run.future = ctx.onServer(CovFreecamSelfTests::sendToNether);
            return StepResult.pending();
        }
        if (run.future.isCompletedExceptionally()) {
            return StepResult.fail("Teleport: " + run.future.exceptionNow());
        }
        if (!run.future.isDone()) {
            return StepResult.pending();
        }
        String problem = NETHER.get();
        if (problem != null && !problem.startsWith("ok")) {
            return StepResult.fail(problem);
        }
        return StepResult.pass();
    }

    private static void sendToNether(MinecraftServer server) {
        ServerPlayer player = FreecamSelfTests.player(server);
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            NETHER.set("kein Nether am Server");
            return;
        }
        BlockPos base = new BlockPos((int) Math.floor(player.getX() / 8.0), 70, (int) Math.floor(player.getZ() / 8.0));
        nether.getChunk(base.getX() >> 4, base.getZ() >> 4);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                nether.setBlock(base.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
                for (int dy = 0; dy <= 3; dy++) {
                    nether.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        player.teleportTo(nether, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
        NETHER.set("ok " + base);
    }

    private static StepResult endedInNether(SelfTest.StepContext ctx, Run run) {
        Minecraft mc = Minecraft.getInstance();
        boolean nether = mc.level != null && mc.level.dimension() == Level.NETHER;
        if (!nether) {
            if (!FreecamSession.active() && run.tick < 0) {
                run.flag = true;
            }
            return pending("Client noch nicht im Nether");
        }
        if (run.tick < 0) {
            run.tick = ctx.tick();
            run.count = FreecamSession.active() ? 1 : 0;
        }
        if (FreecamSession.active()) {
            return ctx.tick() - run.tick > 1 ? StepResult.fail("Freecam läuft im Nether " + (ctx.tick() - run.tick) + " Ticks weiter") : StepResult.pending();
        }
        if (FreecamSession.INSTANCE.lastEnd() != FreecamRules.End.DIMENSION) {
            return StepResult.fail("Ende mit Grund " + FreecamSession.INSTANCE.lastEnd() + " statt DIMENSION");
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.getCameraEntity() != player || FpvView.active()) {
            return StepResult.fail("Kamera im Nether nicht am Spieler");
        }
        run.notes.add("Nether: Freecam " + (run.flag ? "schon vor dem ersten Tick in der neuen Welt" : run.count == 0 ? "im ersten Tick im Nether"
                : "einen Tick nach dem Wechsel") + " beendet, Grund DIMENSION");
        return StepResult.pass();
    }

    private static StepResult backFromNether(SelfTest.StepContext ctx, Run run) {
        if (run.future == null) {
            run.future = ctx.onServer(server -> {
                ServerPlayer player = FreecamSelfTests.player(server);
                ServerLevel overworld = server.overworld();
                Vec3 stand = FreecamSelfTests.standPoint();
                player.teleportTo(overworld, stand.x, stand.y, stand.z, Set.of(), -90.0F, 0.0F, true);
            });
            return StepResult.pending();
        }
        if (run.future.isCompletedExceptionally()) {
            return StepResult.fail("Rückweg: " + run.future.exceptionNow());
        }
        Minecraft mc = Minecraft.getInstance();
        boolean home = mc.level != null && mc.level.dimension() == Level.OVERWORLD && FreecamSelfTests.atSite() && mc.gui.screen() == null;
        return FreecamSelfTests.until(run.future.isDone() && home && run.waited(ctx) > 20, "Rückweg in die Oberwelt");
    }
}
