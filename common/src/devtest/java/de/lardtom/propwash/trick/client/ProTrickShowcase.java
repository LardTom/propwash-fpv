package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseTour;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.trick.Trick;
import de.lardtom.propwash.trick.TrickScore;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ProTrickShowcase {
    private static final double TICK_SECONDS = 0.05;
    private static final int MAX_TICKS = 900;
    private static final double GUILLOTINE_START = 5.0;
    private static final double SBANG_START = 24.0;
    private static final float CRUISE_PITCH = 0.3F;
    private static final String TAB = "tricks";
    private static final String SEARCH = "loop";
    private static final String SINCE = "since";
    private static final String FRAMES = "frames";
    private static final String SHOT = "shot";

    private ProTrickShowcase() {
    }

    public static void register() {
        Screenshots.step("bildschirmfotos: Guillotine im FPV-Flug mit Trick-Meldung im OSD", "trick_guillotine_fpv", 3,
                new LineScene(ProTrickScript.Stage.ARM, TrickType.GUILLOTINE, GUILLOTINE_START, true));
        Screenshots.step("bildschirmfotos: Sbang-Kombo aus Eject, Split-S und Juicy Flick im OSD", "trick_sbang_fpv", 3,
                new LineScene(ProTrickScript.Stage.SETTLE, TrickType.SBANG, SBANG_START, false));
        SelfTest.clientStep("bildschirmfotos: Tab Freestyle mit Familien und Suche", 600, ProTrickShowcase::searchStep);
    }

    private static StepResult searchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (ctx.tick() > 200) {
                return StepResult.fail("Konfigurator öffnet nicht");
            }
            ConfigTabs.open(null, TAB);
            return StepResult.pending();
        }
        Screenshots.parkMouse(mc);
        if (!(screen.activeTab() instanceof TrickConfigTab tab)) {
            screen.selectTab(TAB);
            return StepResult.pending();
        }
        if (!state.containsKey(SINCE)) {
            tab.search(SEARCH);
            state.put(FRAMES, screen.extractedFrames(TAB));
            state.put(SINCE, ctx.tick());
            Screenshots.clearOverlays(mc);
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(SINCE) < 12 || screen.extractedFrames(TAB) <= (int) state.get(FRAMES) + 3) {
            return StepResult.pending();
        }
        if (!state.containsKey(SHOT)) {
            if (tab.visible().isEmpty() || tab.visible().size() == TrickType.REPERTOIRE.size()) {
                screen.discardAndClose();
                return StepResult.fail("Suche „" + SEARCH + "“ zeigt " + tab.visible().size() + " Tricks");
            }
            state.put(SHOT, Screenshots.capture("configurator_tricks_search"));
            return StepResult.pending();
        }
        CompletableFuture<?> shot = (CompletableFuture<?>) state.get(SHOT);
        if (!shot.isDone()) {
            return StepResult.pending();
        }
        int found = tab.visible().size();
        int families = tab.sections().size();
        tab.search("");
        screen.discardAndClose();
        if (shot.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto: " + shot.exceptionNow());
        }
        ctx.log("Freestyle-Tab mit Suche „" + SEARCH + "“: " + found + " Tricks in " + families + " Familien");
        return StepResult.pass();
    }

    private static final class LineScene implements Screenshots.Scene {
        private final ProTrickScript.Stage start;
        private final TrickType expected;
        private final double startHeight;
        private final boolean keepHovering;
        private final Quaterniond attitude = new Quaterniond();
        private final Vector3d com = new Vector3d();
        private final Vector3d velocity = new Vector3d();
        private @Nullable ProTrickScript script;

        private boolean positioned;

        private LineScene(ProTrickScript.Stage start, TrickType expected, double startHeight, boolean keepHovering) {
            this.start = start;
            this.expected = expected;
            this.startHeight = startHeight;
            this.keepHovering = keepHovering;
        }

        private static float climbTo(double target, double y, double vy, double hover) {
            return (float) Math.clamp(hover + 0.12 * (target - y) - 0.08 * vy, 0.05, 0.8);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (!session.isFpv() || sim == null) {
                return ctx.tick() > 100 ? StepResult.fail("keine FPV-Sitzung für " + this.expected.key()) : StepResult.pending();
            }
            ProTrickScript s = this.script;
            if (s == null) {
                ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
                tracker.tracker().abort(tracker.now(), LapTracker.AbortReason.RESTART);
                s = new ProTrickScript(this.start, TICK_SECONDS);
                this.script = s;
                TrickTracker.INSTANCE.session().keepHistory(16);
                return StepResult.pending();
            }
            Telemetry t = sim.telemetry();
            double y = session.comPosition(this.com).y;
            sim.getAttitude(this.attitude);
            sim.getVelocity(this.velocity);
            s.observe(y, this.attitude);
            if (!t.armed) {
                boolean arm = t.armBlockers == 0 && ctx.tick() % 10 >= 5;
                session.setTestInput(StickPositions.IDLE, arm, StabMode.ANGLE);
                return ctx.tick() > 160 ? StepResult.fail("nicht scharf: " + t.armBlockers) : StepResult.pending();
            }
            if (!TrickTracker.INSTANCE.active()) {
                return ctx.tick() > 160 ? StepResult.fail("Trick-Erkennung für die Drohne aus") : StepResult.pending();
            }
            double hover = Double.isFinite(t.hoverThrottleStick) && t.hoverThrottleStick > 0.05 ? t.hoverThrottleStick : 0.35;
            if (!this.positioned) {
                double target = ShowcaseTour.base().getY() + this.startHeight;
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, climbTo(target, y, this.velocity.y, hover)), true, StabMode.ANGLE);
                this.positioned = Math.abs(target - y) < 0.8 && Math.abs(this.velocity.y) < 0.5;
                return ctx.tick() > 400 ? StepResult.fail("Starthöhe nicht erreicht: " + y) : StepResult.pending();
            }
            s.command(y, this.velocity.y, true, sim.config().rates, hover);
            session.setTestInput(new StickPositions((float) s.roll, (float) s.pitch, 0.0F, (float) s.throttle), true, s.mode);
            TrickScore score = TrickTracker.INSTANCE.session().score();
            if (score.popup() == TrickScore.Popup.TRICK) {
                for (int i = 0; i < score.popupTypeCount(); i++) {
                    if (score.popupType(i) == this.expected) {
                        return StepResult.pass();
                    }
                }
            }
            if (ctx.tick() > MAX_TICKS) {
                return StepResult.fail(this.expected.key() + " nicht erkannt (Abschnitt " + s.stage() + ", Tricks "
                        + TrickTracker.INSTANCE.session().history().stream().map(Trick::type).toList() + ")");
            }
            return StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (sim == null || !session.isActive()) {
                return;
            }
            sim.getVelocity(this.velocity);
            double hover = sim.telemetry().hoverThrottleStick;
            double stick = (Double.isFinite(hover) && hover > 0.05 ? hover : 0.35) - 0.05 * this.velocity.y;
            session.setTestInput(new StickPositions(0.0F, CRUISE_PITCH, 0.0F, (float) Math.clamp(stick, 0.05, 0.8)), true, StabMode.ANGLE);
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            this.script = null;
            this.positioned = false;
            if (!this.keepHovering) {
                PilotSession.INSTANCE.clearTestInput();
                TrickTracker.INSTANCE.session().keepHistory(0);
            }
        }
    }
}
