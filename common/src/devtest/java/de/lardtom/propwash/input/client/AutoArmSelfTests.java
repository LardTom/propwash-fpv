package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.race.client.RaceConfig;
import de.lardtom.propwash.rescue.HomeMode;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.rescue.HomeResetRules;
import de.lardtom.propwash.rescue.client.HomeResetInput;
import de.lardtom.propwash.rescue.client.RescueConfigs;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SearchTags;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class AutoArmSelfTests {
    private static final String RADIO_GUID = "propwash-selftest-autoarm-radio";
    private static final String PAD_GUID = "propwash-selftest-autoarm-pad";
    private static final int AXES = 8;
    private static final int THROTTLE_AXIS = 2;
    private static final int ARM_AXIS = ProfileDefaults.AUX_ARM;
    private static final int RADIO_LOW = 1000;
    private static final int RADIO_HIGH = 1350;
    private static final float PAD_HIGH = -0.6F;
    private static final float KEY_HIGH = 0.3F;
    private static final double DRONE_DZ = 3.0;
    private static final double START_DZ = 8.0;
    private static final int WAIT_TICKS = 50;
    private static final int ARM_TICKS = 60;
    private static final int QUIET_TICKS = 80;
    private static final int TAP_TICKS = 3;
    private static final int MAX_TICKS = 3000;
    private static final double FALL_HEIGHT = 30.0;
    private static final String GERMAN = "de_de";

    private AutoArmSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("input: Auto-Arm – Modi-Tab: „Nach Reset automatisch armen“ bei „Scharf schalten“, Standard an, speichern", 400,
                AutoArmSelfTests::tabStep);
        SelfTest.clientStep("input: Auto-Arm – Funke: Reset per Taste mit Arm-Schalter auf ARM wartet mit OSD-Hinweis auf Gas unten und armt",
                MAX_TICKS, ctx -> step(ctx, Kind.RADIO));
        SelfTest.clientStep("input: Auto-Arm – Gamepad: Reset über die Back-Taste, Arm-Taste als Umschalter", MAX_TICKS, ctx -> step(ctx, Kind.GAMEPAD));
        SelfTest.clientStep("input: Auto-Arm – Gamepad: Arm-Taste gehalten", MAX_TICKS, ctx -> step(ctx, Kind.GAMEPAD_HOLD));
        SelfTest.clientStep("input: Auto-Arm – Tastatur: Arm-Zustand gilt nach dem Reset weiter, auch nach einem Absturz, nicht nach Entschärfen",
                MAX_TICKS, ctx -> step(ctx, Kind.KEYBOARD));
        SelfTest.clientStep("input: Auto-Arm aus – Reset mit Arm-Schalter auf ARM bleibt entschärft, Schalter aus und an armt wie bisher", MAX_TICKS,
                ctx -> step(ctx, Kind.OFF));
        SelfTest.clientStep("input: Auto-Arm am Start-Gate – Reset startet keinen Countdown, Scharfschalten von Hand weiterhin", MAX_TICKS,
                ctx -> step(ctx, Kind.RACE));
        if (Screenshots.enabled()) {
            String back = "input: Auto-Arm – Sprache zurückstellen";
            SelfTest.switchStep("input: Auto-Arm – Sprache Deutsch für das Bildschirmfoto", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
            SelfTest.clientStep("input: Auto-Arm – Funke deutsch: OSD-Hinweis „AUTO-ARM: GAS RUNTER“", MAX_TICKS, ctx -> step(ctx, Kind.RADIO_DE));
            SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        }
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int phase = (int) ctx.state().getOrDefault("phase", 0);
        if (ctx.tick() == 0) {
            ctx.state().put("before", InputConfig.get().autoArmAfterReset);
            InputConfig.get().autoArmAfterReset = true;
            mc.gui.setScreen(null);
            ConfigTabs.open(null, ModesTab.ID);
            return StepResult.pending();
        }
        if (ctx.tick() > 300) {
            InputConfig.get().autoArmAfterReset = (boolean) ctx.state().get("before");
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            return StepResult.fail("Zeit abgelaufen in Phase " + phase);
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof ModesTab tab)) {
            return ctx.tick() > 100 ? StepResult.fail("Modi-Tab öffnet nicht: " + mc.gui.screen()) : StepResult.pending();
        }
        SwitchFunction wanted = phase == 4 ? SwitchFunction.BEEPER : SwitchFunction.ARM;
        if (tab.selected() != wanted) {
            tab.select(wanted);
            return StepResult.pending();
        }
        CycleButton<Boolean> button = tab.autoArmButton();
        boolean shown = button != null && screen.children().contains(button);
        if (phase == 4) {
            if (shown) {
                return StepResult.pending();
            }
            ctx.state().put("phase", Screenshots.enabled() ? 1 : 2);
            ctx.state().put("since", ctx.tick());
            return StepResult.pending();
        }
        if (!shown) {
            return StepResult.pending();
        }
        switch (phase) {
            case 0 -> {
                if (!Boolean.TRUE.equals(button.getValue())) {
                    screen.discardAndClose();
                    return StepResult.fail("Auto-Arm ist nicht an");
                }
                SearchTags.Tag tag = SearchTags.of(button);
                String label = Component.translatable("propwash.input.modes.auto_arm.search").getString();
                if (tag == null || tag.label() == null || !tag.label().getString().equals(label)) {
                    screen.discardAndClose();
                    return StepResult.fail("Suchbegriff fehlt: " + (tag == null ? "-" : tag.label()));
                }
                ctx.state().put("phase", 4);
                return StepResult.pending();
            }
            case 1 -> {
                Screenshots.parkMouse(mc);
                if (!ctx.state().containsKey("shot")) {
                    screen.keyPressed(new KeyEvent(InputConstants.KEY_END, 0, 0));
                    if (ctx.tick() - (int) ctx.state().get("since") < 10) {
                        return StepResult.pending();
                    }
                    ctx.state().put("shot", Screenshots.capture("input_auto_arm_modes_tab"));
                    return StepResult.pending();
                }
                CompletableFuture<?> shot = (CompletableFuture<?>) ctx.state().get("shot");
                if (!shot.isDone()) {
                    return StepResult.pending();
                }
                ctx.log("Bildschirmfoto " + Screenshots.dir().resolve("input_auto_arm_modes_tab.png") + (shot.isCompletedExceptionally() ? " fehlgeschlagen" : ""));
                ctx.state().put("phase", 2);
                return StepResult.pending();
            }
            case 2 -> {
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                if (Boolean.TRUE.equals(button.getValue())) {
                    screen.discardAndClose();
                    return StepResult.fail("Schalter lässt sich nicht ausschalten");
                }
                screen.saveAndClose();
                if (InputConfig.get().autoArmAfterReset) {
                    return StepResult.fail("Speichern übernimmt Auto-Arm aus nicht");
                }
                ctx.state().put("phase", 3);
                ConfigTabs.open(null, ModesTab.ID);
                return StepResult.pending();
            }
            default -> {
                if (Boolean.TRUE.equals(button.getValue())) {
                    screen.discardAndClose();
                    return StepResult.fail("nach dem Speichern zeigt der Schalter wieder an");
                }
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                screen.saveAndClose();
                boolean on = InputConfig.get().autoArmAfterReset;
                InputConfig.get().autoArmAfterReset = (boolean) ctx.state().get("before");
                ctx.log("Modi-Tab: Schalter „" + button.getMessage().getString() + "“ bei „Scharf schalten“, nicht bei „Suchpiepser“, aus und an gespeichert");
                return StepResult.check(on, "Speichern übernimmt Auto-Arm an nicht");
            }
        }
    }

    private static StepResult step(SelfTest.StepContext ctx, Kind kind) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent("auto_arm", key -> new Scenario(kind));
        StepResult result;
        try {
            result = run(ctx, s);
        } catch (RuntimeException exception) {
            cleanup(ctx, s);
            throw exception;
        }
        if (result.status() != StepResult.Status.PENDING) {
            cleanup(ctx, s);
            if (result.status() == StepResult.Status.PASS) {
                ctx.log(String.join("; ", s.notes));
            }
        }
        return result;
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        if (s.pending != null) {
            if (s.pending.isCompletedExceptionally()) {
                return StepResult.fail(s.stage + ": Server-Aktion fehlgeschlagen: " + s.pending.exceptionNow().getMessage());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        if (ctx.tick() > MAX_TICKS - 20) {
            return StepResult.fail("Zeit abgelaufen in " + s.stage + " (" + describe(s) + ")");
        }
        drive(ctx, s);
        PilotSession session = PilotSession.INSTANCE;
        int waited = ctx.tick() - s.since;
        if (s.stage.ordinal() > Stage.SESSION.ordinal() && (!session.isActive() || session.drone() == null || session.drone().getId() != s.droneId)) {
            return StepResult.fail(s.stage + ": Sitzung verloren");
        }
        switch (s.stage) {
            case PREPARE -> {
                prepare(s);
                s.enter(Stage.SOURCE, ctx.tick());
            }
            case SOURCE -> {
                boolean ready = switch (s.kind) {
                    case GAMEPAD, GAMEPAD_HOLD -> PAD_GUID.equals(RadioState.INSTANCE.deviceGuid());
                    case KEYBOARD -> RadioState.INSTANCE.source() == RadioSource.KEYBOARD;
                    default -> RADIO_GUID.equals(RadioState.INSTANCE.deviceGuid());
                };
                if (!ready) {
                    return waited > 100 ? StepResult.fail("Eingabequelle stellt sich nicht ein: " + describe(s)) : StepResult.pending();
                }
                s.pending = ctx.onServer(server -> spawn(server, s));
                s.enter(Stage.SESSION, ctx.tick());
            }
            case SESSION -> {
                QuadcopterEntity drone = session.drone();
                boolean course = s.kind != Kind.RACE || ClientRaceTracker.INSTANCE.courseId() == s.course;
                if (drone == null || drone.getId() != s.droneId || !session.isFpv() || !course) {
                    return waited > 300 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne (Kurs " + ClientRaceTracker.INSTANCE.courseId()
                            + " statt " + s.course + ")") : StepResult.pending();
                }
                session.clearTestInput();
                if (waited < 20) {
                    return StepResult.pending();
                }
                armOn(ctx, s);
                s.enter(Stage.ARM, ctx.tick());
            }
            case ARM -> {
                if (!session.armed()) {
                    return waited > 100 ? StepResult.fail("lässt sich nicht scharf schalten: " + describe(s)) : StepResult.pending();
                }
                if (session.autoArmed()) {
                    return StepResult.fail("Scharfschalten von Hand gilt als Auto-Arm");
                }
                if (waited < 20) {
                    return StepResult.pending();
                }
                throttle(s, true);
                s.enter(Stage.CLIMB, ctx.tick());
            }
            case CLIMB -> {
                if (!throttleReached(s, true) || waited < 15) {
                    return waited > 120 ? StepResult.fail("Gas geht nicht hoch: " + describe(s)) : StepResult.pending();
                }
                if (!session.armed()) {
                    return StepResult.fail("vor dem Reset entschärft: " + describe(s));
                }
                if (s.kind == Kind.RACE) {
                    RaceConfig.CONFIG.get().countdown = true;
                }
                s.sent = HomeResetInput.sent();
                reset(ctx, s);
                s.enter(Stage.RESET, ctx.tick());
            }
            case RESET -> {
                if (HomeResetInput.sent() <= s.sent) {
                    return waited > 40 ? StepResult.fail("Reset sendet keine Anfrage") : StepResult.pending();
                }
                QuadcopterEntity drone = Objects.requireNonNull(session.drone());
                if (drone.resetEpoch() <= s.epoch || session.armed() || drone.position().distanceTo(s.home) > 1.5) {
                    s.settled = -1;
                    return waited > 100 ? StepResult.fail("Reset kommt nicht an: " + describe(s)) : StepResult.pending();
                }
                if (s.settled < 0) {
                    s.settled = ctx.tick();
                }
                if (ctx.tick() - s.settled < 5) {
                    return StepResult.pending();
                }
                s.resets++;
                s.enter(Stage.WAIT, ctx.tick());
            }
            case WAIT -> {
                String problem = waiting(s);
                if (problem != null) {
                    return StepResult.fail("nach dem Reset mit Gas oben: " + problem);
                }
                boolean shoot = Screenshots.enabled() && (s.kind == Kind.RADIO || s.kind == Kind.RADIO_DE);
                if (waited == 22 && shoot) {
                    Screenshots.clearOverlays(Minecraft.getInstance());
                    Screenshots.parkMouse(Minecraft.getInstance());
                }
                if (waited == 25 && shoot) {
                    s.shot = Screenshots.capture(shotName(s));
                }
                if (s.shot != null && !s.shot.isDone()) {
                    return StepResult.pending();
                }
                if (waited < WAIT_TICKS) {
                    return StepResult.pending();
                }
                if (s.shot != null) {
                    s.notes.add("Bildschirmfoto " + Screenshots.dir().resolve(shotName(s) + ".png") + (s.shot.isCompletedExceptionally() ? " fehlgeschlagen" : ""));
                    s.shot = null;
                }
                s.notes.add("Reset " + s.resets + ": " + WAIT_TICKS + " Ticks mit Gas oben entschärft" + (s.kind == Kind.OFF ? ", ARM-SCHALTER AN" : ", OSD „"
                        + Component.translatable(OsdWarnings.Warning.AUTO_ARM.translationKey()).getString() + "“"));
                throttle(s, false);
                s.enter(Stage.DOWN, ctx.tick());
            }
            case DOWN -> {
                if (s.kind == Kind.OFF) {
                    if (session.armed()) {
                        return StepResult.fail("Auto-Arm aus, trotzdem scharf");
                    }
                    if (waited < ARM_TICKS) {
                        return StepResult.pending();
                    }
                    if (!ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(session.armBlockers())) {
                        return StepResult.fail("Sperre „Schalter aus, dann an“ fehlt: " + session.armBlockers());
                    }
                    s.notes.add("Gas unten: bleibt entschärft");
                    armOff(ctx, s);
                    s.enter(Stage.CYCLE_OFF, ctx.tick());
                    return StepResult.pending();
                }
                if (!session.armed()) {
                    return waited > ARM_TICKS ? StepResult.fail("Gas unten armt nicht: " + describe(s)) : StepResult.pending();
                }
                if (!session.autoArmed()) {
                    return StepResult.fail("scharf, aber nicht als Auto-Arm");
                }
                s.notes.add("Reset " + s.resets + ": Gas unten armt nach " + waited + " Ticks");
                s.enter(s.kind == Kind.RACE ? Stage.QUIET : s.kind == Kind.KEYBOARD && s.resets == 1 ? Stage.LIFT : Stage.FINISH, ctx.tick());
            }
            case QUIET -> {
                ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
                if (tracker.lobbyMember() || tracker.countdownActive()) {
                    return StepResult.fail("Reset mit Auto-Arm am Start-Gate startet einen Countdown");
                }
                if (!session.armed()) {
                    return StepResult.fail("Auto-Arm hält nicht");
                }
                if (waited < QUIET_TICKS) {
                    return StepResult.pending();
                }
                s.notes.add("Auto-Arm am Start-Gate mit „Start beim Scharfschalten“: " + QUIET_TICKS + " Ticks ohne Countdown");
                armOff(ctx, s);
                s.enter(Stage.CYCLE_OFF, ctx.tick());
            }
            case CYCLE_OFF -> {
                if (session.armed()) {
                    return waited > 60 ? StepResult.fail("Schalter aus entschärft nicht") : StepResult.pending();
                }
                if (waited < 10) {
                    return StepResult.pending();
                }
                armOn(ctx, s);
                s.enter(Stage.CYCLE_ON, ctx.tick());
            }
            case CYCLE_ON -> {
                if (!session.armed()) {
                    return waited > 60 ? StepResult.fail("Schalter aus und an armt nicht: " + describe(s)) : StepResult.pending();
                }
                if (session.autoArmed()) {
                    return StepResult.fail("Schalter aus und an gilt als Auto-Arm");
                }
                if (s.kind == Kind.RACE) {
                    ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
                    if (!tracker.lobbyMember() && !tracker.countdownActive()) {
                        return waited > 140 ? StepResult.fail("Scharfschalten von Hand am Start-Gate startet keinen Countdown") : StepResult.pending();
                    }
                    s.notes.add("Scharfschalten von Hand am Start-Gate: Countdown wie bisher");
                    s.sent = HomeResetInput.sent();
                    reset(ctx, s);
                    s.enter(Stage.LEAVE, ctx.tick());
                    return StepResult.pending();
                }
                s.notes.add("Schalter aus und an: scharf");
                s.enter(Stage.FINISH, ctx.tick());
            }
            case LEAVE -> {
                ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
                if (tracker.lobbyMember() || tracker.countdownActive() || tracker.tracker().phase() == LapTracker.Phase.RACING) {
                    return waited > 200 ? StepResult.fail("Reset verlässt die Startaufstellung nicht") : StepResult.pending();
                }
                if (waited < 20) {
                    return StepResult.pending();
                }
                s.enter(Stage.FINISH, ctx.tick());
            }
            case LIFT -> {
                if (!s.lifted) {
                    s.lifted = true;
                    s.pending = ctx.onServer(server -> lift(server, s));
                    return StepResult.pending();
                }
                DroneSim sim = session.sim();
                Telemetry t = sim == null ? null : sim.telemetry();
                if (t == null || t.armed || t.lastDisarmReason != DisarmReason.CRASH) {
                    return waited > 300 ? StepResult.fail("kein Absturz nach dem Fall: " + describe(s)) : StepResult.pending();
                }
                if (!s.crashed) {
                    s.crashed = true;
                    s.crashTick = ctx.tick();
                    s.notes.add("Absturz aus " + (int) FALL_HEIGHT + " m: entschärft");
                }
                if (ctx.tick() - s.crashTick < 30) {
                    return StepResult.pending();
                }
                throttle(s, true);
                s.enter(Stage.CRASH_CLIMB, ctx.tick());
            }
            case CRASH_CLIMB -> {
                if (!throttleReached(s, true)) {
                    return waited > 120 ? StepResult.fail("Gas geht nach dem Absturz nicht hoch") : StepResult.pending();
                }
                if (session.armed()) {
                    return StepResult.fail("nach dem Absturz ohne Reset wieder scharf");
                }
                if (ctx.tick() - s.lastReset() < HomeResetRules.COOLDOWN_TICKS + 5) {
                    return StepResult.pending();
                }
                s.sent = HomeResetInput.sent();
                reset(ctx, s);
                s.enter(Stage.RESET, ctx.tick());
            }
            case FINISH -> {
                if (s.kind == Kind.KEYBOARD && s.resets == 2 && !s.keyOff) {
                    s.keyOff = true;
                    armOff(ctx, s);
                    s.enter(Stage.KEY_OFF, ctx.tick());
                    return StepResult.pending();
                }
                armOff(ctx, s);
                throttle(s, false);
                s.enter(Stage.DONE, ctx.tick());
            }
            case KEY_OFF -> {
                if (session.armed()) {
                    return waited > 60 ? StepResult.fail("Arm-Taste entschärft nicht") : StepResult.pending();
                }
                if (waited < HomeResetRules.COOLDOWN_TICKS + 5) {
                    return StepResult.pending();
                }
                s.sent = HomeResetInput.sent();
                reset(ctx, s);
                s.enter(Stage.KEY_RESET, ctx.tick());
            }
            case KEY_RESET -> {
                if (HomeResetInput.sent() <= s.sent) {
                    return waited > 40 ? StepResult.fail("Reset sendet keine Anfrage") : StepResult.pending();
                }
                if (session.armed()) {
                    return StepResult.fail("nach Entschärfen per Taste armt der Reset trotzdem");
                }
                if (waited < ARM_TICKS) {
                    return StepResult.pending();
                }
                if (session.autoArmPending()) {
                    return StepResult.fail("nach Entschärfen per Taste wartet der Reset auf Gas unten");
                }
                s.notes.add("von Hand entschärft, dann Reset: bleibt entschärft");
                s.enter(Stage.DONE, ctx.tick());
            }
            case DONE -> {
                if (session.armed()) {
                    return waited > 60 ? StepResult.fail("entschärft am Ende nicht") : StepResult.pending();
                }
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    private static String shotName(Scenario s) {
        return s.kind == Kind.RADIO_DE ? "input_auto_arm_osd_de" : "input_auto_arm_osd";
    }

    private static @Nullable String waiting(Scenario s) {
        PilotSession session = PilotSession.INSTANCE;
        if (session.armed()) {
            return "scharf trotz Gas oben";
        }
        OsdData osd = OsdHud.sampler().data();
        List<OsdWarnings.Warning> warnings = new ArrayList<>();
        OsdWarnings.collect(osd, warnings);
        if (s.kind == Kind.OFF) {
            if (session.autoArmPending() || osd.autoArmPending || warnings.contains(OsdWarnings.Warning.AUTO_ARM)) {
                return "Auto-Arm aus, wartet trotzdem";
            }
            return warnings.contains(OsdWarnings.Warning.ARM_SWITCH) ? null : "OSD ohne „ARM-SCHALTER AN“: " + warnings;
        }
        if (!session.autoArmPending()) {
            return "wartet nicht auf Gas unten (Sperren " + session.armBlockers() + ")";
        }
        if (ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(session.armBlockers())) {
            return "verlangt Schalter aus und an";
        }
        if (!osd.active || !osd.autoArmPending || !warnings.contains(OsdWarnings.Warning.AUTO_ARM)
                || warnings.contains(OsdWarnings.Warning.ARM_THROTTLE)) {
            return "OSD-Hinweis fehlt: aktiv " + osd.active + ", " + warnings;
        }
        return null;
    }

    private static void prepare(Scenario s) {
        InputConfig config = InputConfig.get();
        s.keyboardFallback = config.keyboardFallback;
        s.autoArm = config.autoArmAfterReset;
        s.suppressDevice = TransmitterInput.INSTANCE.suppressDevice();
        s.homeMode = RescueConfigs.get().homeMode;
        s.countdown = RaceConfig.CONFIG.get().countdown;
        RescueConfigs.get().homeMode = HomeMode.FIRST_ARM;
        RescueController.INSTANCE.clearHome();
        RaceConfig.CONFIG.get().countdown = false;
        switch (s.kind) {
            case KEYBOARD -> {
                TransmitterInput.INSTANCE.simulate(null);
                TransmitterInput.INSTANCE.setSuppressDevice(true);
                InputConfig.get().keyboardFallback = true;
            }
            case GAMEPAD, GAMEPAD_HOLD -> {
                DeviceProfile profile = ProfileDefaults.gamepad("Propwash-Testgamepad (Auto-Arm)");
                if (s.kind == Kind.GAMEPAD_HOLD) {
                    profile.setBinding(SwitchFunction.ARM, SwitchAssignment.button(ProfileDefaults.PAD_RIGHT_SHOULDER, false));
                }
                SimulatedRadio radio = install(PAD_GUID, profile, ProfileDefaults.PAD_AXES, ProfileDefaults.PAD_BUTTONS, true);
                for (int axis = 0; axis < ProfileDefaults.PAD_AXES; axis++) {
                    radio.setAxis(axis, axis >= ProfileDefaults.PAD_LEFT_TRIGGER ? -1.0F : 0.0F);
                }
                s.radio = radio;
            }
            default -> {
                SimulatedRadio radio = install(RADIO_GUID, ProfileDefaults.radio("Propwash-Testfunke (Auto-Arm)", AXES), AXES, 0, false);
                for (int axis = 0; axis < AXES; axis++) {
                    radio.setMicros(axis, axis == THROTTLE_AXIS ? RADIO_LOW : 1500);
                }
                for (int axis = ARM_AXIS; axis < AXES; axis++) {
                    radio.setMicros(axis, 1000);
                }
                radio.setMicros(ProfileDefaults.AUX_MODE, 2000);
                s.radio = radio;
            }
        }
        InputConfig.get().autoArmAfterReset = s.kind != Kind.OFF;
    }

    private static SimulatedRadio install(String guid, DeviceProfile profile, int axes, int buttons, boolean pad) {
        profile.setupDone = true;
        InputConfig next = InputConfig.get().copy();
        next.profiles.put(guid, profile);
        next.sanitize();
        InputConfig.commit(next);
        SimulatedRadio radio = new SimulatedRadio(guid, profile.name, profile, axes, buttons).useSavedProfile();
        if (pad) {
            radio.asGamepad();
        }
        TransmitterInput.INSTANCE.simulate(radio);
        return radio;
    }

    private static void drive(SelfTest.StepContext ctx, Scenario s) {
        SimulatedRadio radio = s.radio;
        if (radio != null && s.tapButton >= 0) {
            boolean down = ctx.tick() < s.tapUntil;
            radio.setButton(s.tapButton, down);
            if (!down) {
                s.tapButton = -1;
            }
        }
        if (s.kind == Kind.KEYBOARD && s.keyTarget != null) {
            float now = RadioState.INSTANCE.sticks().throttle();
            float target = s.keyTarget;
            boolean up = now < target - 0.02F;
            boolean down = target <= 0.0F ? now > 0.0F : now > target + 0.08F;
            InputKeys.effective(InputKeys.THROTTLE_UP).setDown(up);
            InputKeys.effective(InputKeys.THROTTLE_DOWN).setDown(down);
            if (!up && !down) {
                s.keyTarget = null;
            }
        }
    }

    private static void tap(SelfTest.StepContext ctx, Scenario s, int button) {
        s.tapButton = button;
        s.tapUntil = ctx.tick() + TAP_TICKS;
        Objects.requireNonNull(s.radio).setButton(button, true);
    }

    private static void armOn(SelfTest.StepContext ctx, Scenario s) {
        switch (s.kind) {
            case KEYBOARD -> click(InputKeys.ARM);
            case GAMEPAD -> tap(ctx, s, ProfileDefaults.PAD_RIGHT_SHOULDER);
            case GAMEPAD_HOLD -> Objects.requireNonNull(s.radio).setButton(ProfileDefaults.PAD_RIGHT_SHOULDER, true);
            default -> Objects.requireNonNull(s.radio).setMicros(ARM_AXIS, 2000);
        }
    }

    private static void armOff(SelfTest.StepContext ctx, Scenario s) {
        switch (s.kind) {
            case KEYBOARD -> {
                if (PilotSession.INSTANCE.armed()) {
                    click(InputKeys.ARM);
                }
            }
            case GAMEPAD -> {
                if (RadioState.INSTANCE.isActive(SwitchFunction.ARM)) {
                    tap(ctx, s, ProfileDefaults.PAD_RIGHT_SHOULDER);
                }
            }
            case GAMEPAD_HOLD -> Objects.requireNonNull(s.radio).setButton(ProfileDefaults.PAD_RIGHT_SHOULDER, false);
            default -> Objects.requireNonNull(s.radio).setMicros(ARM_AXIS, 1000);
        }
    }

    private static void throttle(Scenario s, boolean high) {
        switch (s.kind) {
            case KEYBOARD -> s.keyTarget = high ? KEY_HIGH : 0.0F;
            case GAMEPAD, GAMEPAD_HOLD -> Objects.requireNonNull(s.radio).setAxis(ProfileDefaults.PAD_LEFT_Y, high ? PAD_HIGH : 0.0F);
            default -> Objects.requireNonNull(s.radio).setMicros(THROTTLE_AXIS, high ? RADIO_HIGH : RADIO_LOW);
        }
    }

    private static boolean throttleReached(Scenario s, boolean high) {
        float now = RadioState.INSTANCE.sticks().throttle();
        return high ? now > 0.15F && s.keyTarget == null : now < 0.02F;
    }

    private static void reset(SelfTest.StepContext ctx, Scenario s) {
        s.resetTick = ctx.tick();
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        s.epoch = drone == null ? Integer.MAX_VALUE : drone.resetEpoch();
        s.settled = -1;
        if (s.kind == Kind.GAMEPAD || s.kind == Kind.GAMEPAD_HOLD) {
            tap(ctx, s, ProfileDefaults.PAD_BACK);
        } else {
            click(Objects.requireNonNull(FunctionKeys.mapping(SwitchFunction.RESET)));
        }
    }

    private static void click(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(InputKeys.effective(mapping).saveString()));
    }

    private static String describe(Scenario s) {
        PilotSession session = PilotSession.INSTANCE;
        RadioState radio = RadioState.INSTANCE;
        return s.kind + " Quelle " + radio.source() + " " + radio.deviceGuid() + ", Arm " + radio.isActive(SwitchFunction.ARM) + ", Gas "
                + radio.sticks().throttle() + ", Sitzung " + session.isActive() + ", scharf " + session.armed() + ", wartet "
                + session.autoArmPending() + ", Sperren " + session.armBlockers();
    }

    private static void spawn(MinecraftServer server, Scenario s) {
        ServerPlayer player = firstPlayer(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        s.allowReset = WorldRules.get(server, WorldRuleSpecs.ALLOW_RESET);
        s.crashDamage = WorldRules.get(server, WorldRuleSpecs.CRASH_DAMAGE);
        WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, 1);
        WorldRules.set(server, WorldRuleSpecs.CRASH_DAMAGE, 0);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 feet = player.position();
        double x = Math.floor(feet.x) + 0.5;
        double z = Math.floor(feet.z + DRONE_DZ) + 0.5;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        Vec3 home = new Vec3(x, ground + 0.05, z);
        drone.snapTo(home.x, home.y, home.z, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf().rotationY((float) Math.PI));
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        drone.setHomePoint(new HomePoint(home.x, home.y, home.z, HomePointCodecs.dimension(level), level.getGameTime(), HomePoint.Source.MANUAL));
        s.home = home;
        s.droneId = drone.getId();
        if (s.kind == Kind.RACE) {
            course(level, player, home, s);
        }
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        s.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        s.previousHand = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void course(ServerLevel level, ServerPlayer player, Vec3 home, Scenario s) {
        List<GateEntity> placed = new ArrayList<>();
        for (double dz : new double[]{START_DZ, START_DZ + 16.0}) {
            GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
            if (gate == null) {
                throw new IllegalStateException("Gate nicht erzeugt");
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(home.x), (int) Math.floor(home.z + dz));
            gate.setSize(GateSize.MEDIUM);
            gate.applyGeometry(GateGeometry.of(home.x, top, home.z + dz, 0.0F, GateSize.MEDIUM));
            if (!level.addFreshEntity(gate)) {
                throw new IllegalStateException("Gate nicht gespawnt");
            }
            placed.add(gate);
            s.gates.add(gate.getId());
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : placed) {
            CourseToolItem.useOnGate(tool, player, gate);
        }
        int id = CourseToolItem.courseOf(tool);
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(id);
        if (course == null || course.gates.size() != 2) {
            throw new IllegalStateException("Kurs nicht angelegt");
        }
        s.course = id;
    }

    private static void lift(MinecraftServer server, Scenario s) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(s.droneId) instanceof QuadcopterEntity drone) {
                drone.resetTo(drone.position().add(0.0, FALL_HEIGHT, 0.0), HomeResetRules.level(drone.orientation()));
                return;
            }
        }
        throw new IllegalStateException("Testdrohne fehlt auf dem Server");
    }

    private static void cleanup(SelfTest.StepContext ctx, Scenario s) {
        if (s.cleaned) {
            return;
        }
        s.cleaned = true;
        InputKeys.effective(InputKeys.THROTTLE_UP).setDown(false);
        InputKeys.effective(InputKeys.THROTTLE_DOWN).setDown(false);
        InputKeys.drain(InputKeys.ARM);
        PilotSession.INSTANCE.clearTestInput();
        TransmitterInput.INSTANCE.simulate(null);
        if (s.suppressDevice != null) {
            TransmitterInput.INSTANCE.setSuppressDevice(s.suppressDevice);
        }
        InputConfig next = InputConfig.get().copy();
        boolean removed = next.profiles.remove(RADIO_GUID) != null;
        removed |= next.profiles.remove(PAD_GUID) != null;
        if (removed) {
            InputConfig.commit(next);
        }
        if (s.keyboardFallback != null) {
            InputConfig.get().keyboardFallback = s.keyboardFallback;
        }
        if (s.autoArm != null) {
            InputConfig.get().autoArmAfterReset = s.autoArm;
        }
        if (s.homeMode != null) {
            RescueConfigs.get().homeMode = s.homeMode;
        }
        if (s.countdown != null) {
            RaceConfig.CONFIG.get().countdown = s.countdown;
        }
        RescueController.INSTANCE.clearHome();
        ctx.onServer(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = s.droneId >= 0 ? level.getEntity(s.droneId) : null;
                if (entity instanceof QuadcopterEntity drone) {
                    DronePiloting.release(drone);
                    drone.discard();
                }
                for (int gate : s.gates) {
                    if (level.getEntity(gate) instanceof GateEntity entityGate) {
                        entityGate.discard();
                    }
                }
            }
            if (s.allowReset >= 0) {
                WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, s.allowReset);
            }
            if (s.crashDamage >= 0) {
                WorldRules.set(server, WorldRuleSpecs.CRASH_DAMAGE, s.crashDamage);
            }
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (!players.isEmpty()) {
                ServerPlayer player = players.getFirst();
                if (s.previousHead != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, s.previousHead);
                }
                if (s.previousHand != null) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, s.previousHand);
                }
            }
        });
    }

    private static ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private enum Kind {
        RADIO,
        RADIO_DE,
        GAMEPAD,
        GAMEPAD_HOLD,
        KEYBOARD,
        OFF,
        RACE
    }

    private enum Stage {
        PREPARE,
        SOURCE,
        SESSION,
        ARM,
        CLIMB,
        RESET,
        WAIT,
        DOWN,
        QUIET,
        CYCLE_OFF,
        CYCLE_ON,
        LEAVE,
        LIFT,
        CRASH_CLIMB,
        FINISH,
        KEY_OFF,
        KEY_RESET,
        DONE
    }

    private static final class Scenario {
        private final Kind kind;
        private final List<String> notes = new ArrayList<>();
        private final List<Integer> gates = new ArrayList<>();
        private Stage stage = Stage.PREPARE;
        private int since;
        private int sent;
        private int resets;
        private int resetTick = Integer.MIN_VALUE / 2;
        private int tapButton = -1;
        private int tapUntil;
        private int crashTick;
        private int settled = -1;
        private boolean crashed;
        private boolean lifted;
        private int epoch;
        private boolean keyOff;
        private boolean cleaned;
        private @Nullable Float keyTarget;
        private @Nullable SimulatedRadio radio;
        private @Nullable CompletableFuture<Void> pending;
        private @Nullable CompletableFuture<String> shot;
        private @Nullable Boolean keyboardFallback;
        private @Nullable Boolean autoArm;
        private @Nullable Boolean suppressDevice;
        private @Nullable Boolean countdown;
        private @Nullable HomeMode homeMode;
        private volatile int droneId = -1;
        private volatile int course = GateEntity.NO_COURSE;
        private volatile int allowReset = -1;
        private volatile int crashDamage = -1;
        private volatile Vec3 home = Vec3.ZERO;
        private volatile @Nullable ItemStack previousHead;
        private volatile @Nullable ItemStack previousHand;

        private Scenario(Kind kind) {
            this.kind = kind;
        }

        private void enter(Stage next, int tick) {
            this.stage = next;
            this.since = tick;
        }

        private int lastReset() {
            return this.resetTick;
        }
    }
}
