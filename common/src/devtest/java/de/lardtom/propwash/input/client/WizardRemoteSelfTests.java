package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.input.DetectionStage;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RemoteDemo;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.input.client.screen.remote.RemoteDemoRenderer;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class WizardRemoteSelfTests {
    static final String GUID = "05000000091200004f5400005e1f0004";
    static final String NAME = "EdgeTX Propwash Vorführfunke Joystick";
    private static final int AXES = 8;
    private static final int BUTTONS = 8;
    private static final int THROTTLE = 2;
    private static final int AUX = 4;
    private static final float DOWN = 1.0F;
    private static final float UP = -1.0F;
    private static final float LEAD_IN = -0.7F;
    private static final int RAMP_TICKS = 4;
    private static final int TIMEOUT = 80;

    private WizardRemoteSelfTests() {
    }

    enum Stop {
        THROTTLE,
        CONFIRM,
        CHECK,
        CENTER,
        EXTREMES,
        REVIEW,
        SWITCHES,
        LISTEN_ARM
    }

    private static final Map<Stop, RemoteDemo.Motion> EXPECTED = new EnumMap<>(Stop.class);
    private static final int SMALL_WIDTH = 1708;
    private static final int SMALL_HEIGHT = 960;

    static {
        EXPECTED.put(Stop.THROTTLE, RemoteDemo.Motion.THROTTLE);
        EXPECTED.put(Stop.CONFIRM, RemoteDemo.Motion.THROTTLE);
        EXPECTED.put(Stop.CHECK, RemoteDemo.Motion.CHECK);
        EXPECTED.put(Stop.CENTER, RemoteDemo.Motion.CENTER);
        EXPECTED.put(Stop.EXTREMES, RemoteDemo.Motion.EXTREMES);
        EXPECTED.put(Stop.REVIEW, RemoteDemo.Motion.REVIEW);
        EXPECTED.put(Stop.SWITCHES, RemoteDemo.Motion.ARM_AND_MODE);
        EXPECTED.put(Stop.LISTEN_ARM, RemoteDemo.Motion.SWITCH);
    }

    public static void register() {
        SelfTest.clientStep("input: Assistent zeigt in jedem Schritt die animierte Funke (simuliert)", 2400, ctx -> {
            Tour tour = (Tour) ctx.state().computeIfAbsent("tour", k -> new Tour());
            return guard(tour.walker, ctx, tour::advance);
        });
        SelfTest.clientStep("input: animierte Funke bei GUI-Skala 1 bis 4 (simuliert)", 1500, ctx -> {
            Scales scales = (Scales) ctx.state().computeIfAbsent("scales", k -> new Scales());
            return guard(scales.walker, ctx, scales::advance);
        });
        SelfTest.clientStep("input: Assistent fragt nach, wenn das Gas gegen die Startlage gehalten wird (simuliert)", 900, ctx -> {
            Confirm confirm = (Confirm) ctx.state().computeIfAbsent("confirm", k -> new Confirm());
            return guard(confirm.walker, ctx, confirm::advance);
        });
        SelfTest.clientStep("input: Assistent fragt bei „Fertig“ ohne Arm-Schalter einmal nach (simuliert)", 900, ctx -> {
            NoArm noArm = (NoArm) ctx.state().computeIfAbsent("noarm", k -> new NoArm());
            return guard(noArm.walker, ctx, noArm::advance);
        });
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? " deutsch" : "";
        String file = german ? "_de" : "";
        if (!german) {
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Gas-Rückfrage", "receiver_wizard_remote_confirm", 2,
                    new WalkScene(Stop.CONFIRM, Float.NaN, false));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Prüfansicht Roll", "receiver_wizard_remote_check", 2,
                    new WalkScene(Stop.CHECK, 2.9F, false));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, 3D-Gasmitte", "receiver_wizard_remote_center_3d", 2,
                    new WalkScene(Stop.CENTER, 2.8F, true));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, große Kreise", "receiver_wizard_remote_extremes", 2,
                    new WalkScene(Stop.EXTREMES, 0.5F, false));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Arm-Schalter zuweisen", "receiver_wizard_remote_listening", 2,
                    new WalkScene(Stop.LISTEN_ARM, 1.2F, false));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Gas bei GUI-Skala 2", "receiver_wizard_remote_scale2", 2,
                    Screenshots.guiScale(2, new WalkScene(Stop.THROTTLE, 1.3F, false)));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Gas bei GUI-Skala 4", "receiver_wizard_remote_scale4", 2,
                    Screenshots.guiScale(4, new WalkScene(Stop.THROTTLE, 1.3F, false)));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Schalter bei GUI-Skala 4", "receiver_wizard_remote_switches_scale4", 2,
                    Screenshots.guiScale(4, new WalkScene(Stop.SWITCHES, 1.5F, false)));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Maus auf „Zuweisen“ bei GUI-Skala 4", "receiver_wizard_remote_hover_scale4", 2,
                    Screenshots.guiScale(4, new WalkScene(Stop.SWITCHES, 1.0F, false).hoveringFirstAssign()));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Arm-Schalter zuweisen bei GUI-Skala 4", "receiver_wizard_remote_listening_scale4", 2,
                    Screenshots.guiScale(4, new WalkScene(Stop.LISTEN_ARM, 1.2F, false)));
            Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Schalter bei 240 Pixel GUI-Höhe", "receiver_wizard_remote_switches_gui240", 2,
                    smallWindow(new WalkScene(Stop.SWITCHES, 1.5F, false)));
            SelfTest.clientStep("bildschirmfotos: Empfänger-Assistent mit Funke, Fenster wieder 1920x1080", 200, WizardRemoteSelfTests::restoreWindowStep);
        }
        Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Gas" + suffix, "receiver_wizard_remote_throttle" + file, 2,
                new WalkScene(Stop.THROTTLE, 1.3F, false));
        Screenshots.step("bildschirmfotos: Empfänger-Assistent mit Funke, Schalter" + suffix, "receiver_wizard_remote_switches" + file, 2,
                new WalkScene(Stop.SWITCHES, 1.5F, false));
    }

    private static Screenshots.Scene smallWindow(Screenshots.Scene scene) {
        return new Screenshots.Scene() {
            private int since = -1;

            @Override
            public StepResult prepare(SelfTest.StepContext ctx) {
                Minecraft mc = Minecraft.getInstance();
                Window window = mc.getWindow();
                if (this.since < 0) {
                    this.since = ctx.tick();
                    float density = Math.max(1.0F, window.getPixelDensity());
                    window.setWindowed(Math.round(SMALL_WIDTH / density), Math.round(SMALL_HEIGHT / density));
                    return StepResult.pending();
                }
                if (window.getHeight() > SMALL_HEIGHT && ctx.tick() - this.since < 60) {
                    return StepResult.pending();
                }
                if (mc.options.guiScale().get() != 4 || window.getGuiScale() != 4) {
                    mc.options.guiScale().set(4);
                    mc.resizeGui();
                }
                if (window.getGuiScaledHeight() > 250) {
                    return StepResult.fail("Kleines Fenster nicht erreicht: GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight());
                }
                return scene.prepare(ctx);
            }

            @Override
            public void hold(SelfTest.StepContext ctx) {
                scene.hold(ctx);
            }

            @Override
            public void finish(SelfTest.StepContext ctx) {
                scene.finish(ctx);
            }
        };
    }

    private static StepResult restoreWindowStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (ctx.tick() == 0) {
            float density = Math.max(1.0F, window.getPixelDensity());
            window.setWindowed(Math.round(Screenshots.TARGET_WIDTH / density), Math.round(Screenshots.TARGET_HEIGHT / density));
            return StepResult.pending();
        }
        boolean sized = window.getWidth() >= Screenshots.TARGET_WIDTH && window.getHeight() >= Screenshots.TARGET_HEIGHT;
        if (!sized && ctx.tick() < 60) {
            return StepResult.pending();
        }
        mc.options.guiScale().set(Screenshots.GUI_SCALE);
        mc.resizeGui();
        return StepResult.check(sized, "Fenster " + window.getWidth() + "x" + window.getHeight() + ", GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight());
    }

    @FunctionalInterface
    private interface Body {
        StepResult run(ReceiverWizardScreen wizard, int tick);
    }

    private static StepResult guard(Walker walker, SelfTest.StepContext ctx, Body body) {
        StepResult result;
        try {
            StepResult walked = walker.advance(ctx.tick());
            if (walked.status() != StepResult.Status.PASS) {
                result = walked;
            } else {
                result = body.run(walker.wizard(), ctx.tick());
            }
        } catch (RuntimeException e) {
            walker.close();
            throw e;
        }
        if (result.status() != StepResult.Status.PENDING) {
            walker.close();
            ctx.log("Ablauf: " + walker.trail());
        }
        return result;
    }

    private static StepResult demoShows(ReceiverWizardScreen wizard, RemoteDemo.Motion motion, String where) {
        if (wizard.demo().target().motion() != motion) {
            return StepResult.fail(where + ": Funke führt " + wizard.demo().target().motion() + " statt " + motion + " vor");
        }
        if (!wizard.demo().drawn()) {
            return StepResult.fail(where + ": animierte Funke nicht gezeichnet");
        }
        return StepResult.pass();
    }

    private static final class Tour {
        private Walker walker = new Walker(Stop.THROTTLE, false);
        private final Stop[] stops = {Stop.THROTTLE, Stop.CHECK, Stop.CENTER, Stop.EXTREMES, Stop.REVIEW, Stop.SWITCHES, Stop.LISTEN_ARM};
        private int index;
        private int arrived = -1;
        private long renderedAt = -1L;
        private float lastLeftY = Float.NaN;
        private boolean moved;

        StepResult advance(ReceiverWizardScreen wizard, int tick) {
            Stop stop = this.stops[this.index];
            if (this.arrived < 0) {
                this.arrived = tick;
                this.renderedAt = RemoteDemoRenderer.renderedFrames();
                this.lastLeftY = Float.NaN;
                this.moved = false;
            }
            RemoteDemo.Frame frame = wizard.demo().lastFrame();
            float sample = frame.leftY() + frame.leftX() * 3.0F + frame.rightX() * 5.0F + frame.rightY() * 7.0F + frame.switch0() * 11.0F;
            if (!Float.isNaN(this.lastLeftY) && Math.abs(sample - this.lastLeftY) > 1.0E-3F) {
                this.moved = true;
            }
            this.lastLeftY = sample;
            if (tick - this.arrived < 40) {
                return StepResult.pending();
            }
            boolean visible = stop != Stop.SWITCHES || wizard.switchDemoRoom();
            StepResult shown = visible ? demoShows(wizard, EXPECTED.get(stop), stop.name())
                    : wizard.demo().target().motion() == EXPECTED.get(stop) ? StepResult.pass()
                    : StepResult.fail("SWITCHES: Funke führt " + wizard.demo().target().motion() + " vor");
            if (shown.status() == StepResult.Status.FAIL) {
                return shown;
            }
            if (visible && RemoteDemoRenderer.renderedFrames() <= this.renderedAt) {
                return StepResult.fail(stop + ": Bild-im-Bild der Funke rendert nicht (" + RemoteDemoRenderer.renderedFrames() + ")");
            }
            if (visible && !this.moved) {
                return StepResult.fail(stop + ": Funke bewegt sich in 2 s nicht");
            }
            if (stop == Stop.LISTEN_ARM && wizard.demo().target().index() != RemoteDemo.ARM_SWITCH) {
                return StepResult.fail("Arm-Schalter: Funke zeigt Schalter " + wizard.demo().target().index());
            }
            this.index++;
            this.arrived = -1;
            if (this.index >= this.stops.length) {
                return StepResult.pass();
            }
            this.walker.retarget(this.stops[this.index]);
            return StepResult.pending();
        }
    }

    private static final class Scales {
        private final Walker walker = new Walker(Stop.THROTTLE, false);
        private int scale = 1;
        private int since = -1;
        private final int saved = Minecraft.getInstance().options.guiScale().get();
        private final List<String> seen = new ArrayList<>();
        private boolean switches;

        StepResult advance(ReceiverWizardScreen wizard, int tick) {
            Minecraft mc = Minecraft.getInstance();
            if (this.since < 0) {
                mc.options.guiScale().set(this.scale);
                mc.resizeGui();
                this.since = tick;
                return StepResult.pending();
            }
            if (tick - this.since < 6) {
                return StepResult.pending();
            }
            int effective = mc.getWindow().getGuiScale();
            String where = "GUI-Skala " + this.scale + " (wirksam " + effective + ")";
            if (this.switches) {
                if (wizard.switchGuideCut()) {
                    this.restore(mc);
                    return StepResult.fail("Arm-Hinweis auf der Seite Schalter abgeschnitten bei " + where + ", GUI "
                            + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
                }
            } else {
                this.seen.add(this.scale + "→" + effective + " (" + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + ")");
                StepResult shown = demoShows(wizard, RemoteDemo.Motion.THROTTLE, where);
                if (shown.status() == StepResult.Status.FAIL) {
                    this.restore(mc);
                    return shown;
                }
                if (wizard.currentChannel() != StickChannel.THROTTLE) {
                    this.restore(mc);
                    return StepResult.fail("Nach dem Skalieren nicht mehr beim Gas: " + wizard.currentChannel());
                }
            }
            this.scale++;
            this.since = -1;
            if (this.scale > 4) {
                this.restore(mc);
                if (!this.switches) {
                    this.switches = true;
                    this.scale = 1;
                    this.walker.retarget(Stop.SWITCHES);
                    return StepResult.pending();
                }
                return StepResult.check(true, "Skalen " + this.seen + ", Arm-Hinweis auf der Seite Schalter überall vollständig");
            }
            return StepResult.pending();
        }

        private void restore(Minecraft mc) {
            mc.options.guiScale().set(this.saved);
            mc.resizeGui();
        }
    }

    private static final class Confirm {
        private final Walker walker = new Walker(Stop.CONFIRM, false);
        private int since = -1;

        StepResult advance(ReceiverWizardScreen wizard, int tick) {
            if (this.since < 0) {
                this.since = tick;
                if (!wizard.confirmingThrottle() || wizard.detectionStage() != DetectionStage.CONFIRM) {
                    return StepResult.fail("Keine Rückfrage: Stufe " + wizard.detectionStage());
                }
                if (button(wizard, "propwash.input.wizard.throttle.confirm_down") == null) {
                    return StepResult.fail("Knopf „Ganz UNTEN“ fehlt");
                }
                Button up = button(wizard, "propwash.input.wizard.throttle.confirm_up");
                if (up == null) {
                    return StepResult.fail("Knopf „Ganz OBEN“ fehlt");
                }
                up.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                return StepResult.pending();
            }
            if (tick - this.since < 3) {
                return StepResult.pending();
            }
            if (wizard.currentChannel() != null) {
                return StepResult.fail("Nach der Antwort nicht in der Prüfansicht: " + wizard.currentChannel());
            }
            DeviceProfile profile = wizard.working().profile(GUID);
            if (profile == null || profile.throttle.axis != THROTTLE || !profile.throttle.invert) {
                return StepResult.fail("Antwort „oben“ nicht übernommen: " + (profile == null ? "-" : profile.throttle.axis + " inv " + profile.throttle.invert));
            }
            return StepResult.pass();
        }
    }

    private static final class NoArm {
        private final Walker walker = new Walker(Stop.SWITCHES, false);
        private String phase = "clear";
        private int since;

        StepResult advance(ReceiverWizardScreen wizard, int tick) {
            switch (this.phase) {
                case "clear" -> {
                    Button clear = clearButton(wizard);
                    if (clear == null || !clear.active) {
                        return StepResult.fail("„×“ beim Arm-Schalter fehlt oder ist aus");
                    }
                    clear.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    this.phase = "first";
                    this.since = tick;
                }
                case "first" -> {
                    DeviceProfile profile = wizard.working().profile(GUID);
                    if (profile == null || profile.binding(SwitchFunction.ARM).bound()) {
                        return StepResult.fail("Arm-Schalter nicht gelöscht");
                    }
                    Button finish = button(wizard, "propwash.input.wizard.finish");
                    if (finish == null) {
                        return StepResult.fail("„Fertig“ fehlt");
                    }
                    finish.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    this.phase = "second";
                    this.since = tick;
                }
                case "second" -> {
                    if (tick - this.since < 2) {
                        return StepResult.pending();
                    }
                    if (!(Minecraft.getInstance().gui.screen() instanceof ReceiverWizardScreen)) {
                        return StepResult.fail("„Fertig“ ohne Arm-Schalter schließt ohne Nachfrage");
                    }
                    if (!wizard.confirmingNoArm() || button(wizard, "propwash.input.wizard.finish_no_arm") == null) {
                        return StepResult.fail("Nachfrage ohne Arm-Schalter fehlt");
                    }
                    if (clearButton(wizard) != null) {
                        return StepResult.fail("Nachfrage ohne Arm-Schalter zeigt noch die Schalterzeilen");
                    }
                    Button back = button(wizard, "propwash.input.wizard.back");
                    if (back == null) {
                        return StepResult.fail("„Zurück“ fehlt in der Nachfrage");
                    }
                    back.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    this.phase = "back";
                    this.since = tick;
                }
                case "back" -> {
                    if (tick - this.since < 2) {
                        return StepResult.pending();
                    }
                    if (wizard.confirmingNoArm() || wizard.page() != ReceiverWizardScreen.Page.SWITCHES || clearButton(wizard) == null) {
                        return StepResult.fail("„Zurück“ führt aus der Nachfrage nicht zu den Schaltern");
                    }
                    Button finish = button(wizard, "propwash.input.wizard.finish");
                    if (finish == null) {
                        return StepResult.fail("„Fertig“ fehlt nach „Zurück“");
                    }
                    finish.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    this.phase = "confirm";
                    this.since = tick;
                }
                case "confirm" -> {
                    if (tick - this.since < 2) {
                        return StepResult.pending();
                    }
                    Button anyway = button(wizard, "propwash.input.wizard.finish_no_arm");
                    if (!wizard.confirmingNoArm() || anyway == null) {
                        return StepResult.fail("Nachfrage ohne Arm-Schalter fehlt beim zweiten Mal");
                    }
                    anyway.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    this.phase = "closed";
                    this.since = tick;
                }
                case "closed" -> {
                    return Minecraft.getInstance().gui.screen() instanceof ReceiverWizardScreen
                            ? StepResult.fail("„Trotzdem fertig“ schließt den Assistenten nicht") : StepResult.pass();
                }
                default -> {
                    return StepResult.fail("Phase " + this.phase);
                }
            }
            return StepResult.pending();
        }
    }

    private static final class WalkScene implements Screenshots.Scene {
        private final Stop stop;
        private final float seconds;
        private final boolean threeD;
        private @Nullable Walker walker;
        private int ready = -1;
        private boolean hover;

        private WalkScene(Stop stop, float seconds, boolean threeD) {
            this.stop = stop;
            this.seconds = seconds;
            this.threeD = threeD;
        }

        private WalkScene hoveringFirstAssign() {
            this.hover = true;
            return this;
        }

        private void pointMouse(ReceiverWizardScreen wizard) {
            Minecraft mc = Minecraft.getInstance();
            Button assign = button(wizard, "propwash.input.wizard.assign");
            if (assign == null) {
                Screenshots.parkMouse(mc);
                return;
            }
            double gx = assign.getX() + assign.getWidth() / 2.0;
            double gy = assign.getY() + assign.getHeight() / 2.0;
            double x = gx * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = gy * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (this.walker == null) {
                this.walker = new Walker(this.stop, this.threeD);
            }
            StepResult walked = this.walker.advance(ctx.tick());
            if (walked.status() != StepResult.Status.PASS) {
                return walked;
            }
            ReceiverWizardScreen wizard = this.walker.wizard();
            if (!Float.isNaN(this.seconds)) {
                wizard.demo().freezeAt(this.seconds);
            } else if (this.stop == Stop.CONFIRM) {
                wizard.demo().freezeAt(2.6F);
            }
            if (this.ready < 0) {
                this.ready = ctx.tick();
            }
            wizard.clearStatus();
            if (ctx.tick() - this.ready < 4) {
                return StepResult.pending();
            }
            RemoteDemo.Motion expected = this.threeD && this.stop == Stop.CENTER ? RemoteDemo.Motion.CENTER_3D : EXPECTED.get(this.stop);
            if (this.hover) {
                this.pointMouse(wizard);
                if (ctx.tick() - this.ready < 8) {
                    return StepResult.pending();
                }
                return demoShows(wizard, RemoteDemo.Motion.SWITCH, "Maus auf „Zuweisen“");
            }
            if (this.stop == Stop.SWITCHES && !wizard.switchDemoRoom()) {
                return wizard.demo().target().motion() == expected ? StepResult.pass() : StepResult.fail("Schalter-Seite führt "
                        + wizard.demo().target().motion() + " vor");
            }
            return demoShows(wizard, expected, "Bildschirmfoto " + this.stop);
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            if (this.walker != null && Minecraft.getInstance().gui.screen() instanceof ReceiverWizardScreen wizard) {
                wizard.clearStatus();
                if (this.hover) {
                    this.pointMouse(wizard);
                    return;
                }
            }
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.walker != null) {
                this.walker.close();
                this.walker = null;
            }
            this.ready = -1;
        }
    }

    static final class Walker {
        private Stop stop;
        private final boolean threeD;
        private final StringBuilder trail = new StringBuilder();
        private @Nullable SimulatedRadio radio;
        private @Nullable ReceiverWizardScreen wizard;
        private String phase = "begin";
        private int since;
        private int lastPress = -100;
        private float rampFrom;
        private float rampTo;
        private @Nullable Boolean savedThreeD;
        private @Nullable Stop last;
        private boolean reached;

        Walker(Stop stop, boolean threeD) {
            this.stop = stop;
            this.threeD = threeD;
        }

        void retarget(Stop next) {
            this.stop = next;
            this.reached = false;
            this.phase = "walk";
        }

        ReceiverWizardScreen wizard() {
            if (this.wizard == null) {
                throw new IllegalStateException("Assistent nicht offen");
            }
            return this.wizard;
        }

        String trail() {
            return this.trail.toString();
        }

        void close() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof ReceiverWizardScreen) {
                minecraft.gui.setScreen(null);
            }
            if (this.radio != null) {
                TransmitterInput.INSTANCE.simulate(null);
                TransmitterInput.INSTANCE.rescanSoon();
                this.radio = null;
            }
            if (this.savedThreeD != null) {
                InputConfig.get().mode3dUnlocked = this.savedThreeD;
                this.savedThreeD = null;
            }
            this.wizard = null;
        }

        private void next(String phase, int tick) {
            this.phase = phase;
            this.since = tick;
            this.trail.append(" → ").append(phase).append('@').append(tick);
        }

        private void ramp(float from, float to, String phase, int tick) {
            this.rampFrom = from;
            this.rampTo = to;
            this.next(phase, tick);
        }

        private boolean ramping(int tick) {
            float t = Math.min(1.0F, (tick - this.since) / (float) RAMP_TICKS);
            if (this.radio != null) {
                this.radio.setAxis(THROTTLE, this.rampFrom + (this.rampTo - this.rampFrom) * t);
            }
            return tick - this.since < RAMP_TICKS;
        }

        private void press(ReceiverWizardScreen wizard, String key, int tick) {
            if (tick - this.lastPress < 3) {
                return;
            }
            Button button = button(wizard, key);
            if (button != null && button.active) {
                this.lastPress = tick;
                this.trail.append(" [").append(key.substring(key.lastIndexOf('.') + 1)).append(']');
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            }
        }

        private static @Nullable Stop position(ReceiverWizardScreen w) {
            return switch (w.page()) {
                case DEVICE -> null;
                case CHANNELS -> {
                    StickChannel channel = w.currentChannel();
                    if (channel == null) {
                        yield Stop.CHECK;
                    }
                    if (channel == StickChannel.THROTTLE && w.detectionStage() != null) {
                        yield w.confirmingThrottle() ? Stop.CONFIRM : Stop.THROTTLE;
                    }
                    yield null;
                }
                case CALIBRATE -> switch (w.calibrationStep()) {
                    case "CENTER" -> Stop.CENTER;
                    case "EXTREMES" -> Stop.EXTREMES;
                    default -> Stop.REVIEW;
                };
                case SWITCHES -> w.listening() == SwitchFunction.ARM ? Stop.LISTEN_ARM : Stop.SWITCHES;
            };
        }

        StepResult advance(int tick) {
            if (this.reached) {
                return StepResult.pass();
            }
            Minecraft minecraft = Minecraft.getInstance();
            if ("begin".equals(this.phase)) {
                if (this.threeD) {
                    this.savedThreeD = InputConfig.get().mode3dUnlocked;
                    InputConfig.get().mode3dUnlocked = true;
                }
                SimulatedRadio fake = new SimulatedRadio(GUID, NAME, ProfileDefaults.radio(NAME, AXES), AXES, BUTTONS);
                for (int i = AUX; i < AXES; i++) {
                    fake.setAxis(i, -1.0F);
                }
                fake.setAxis(THROTTLE, DOWN);
                this.radio = fake;
                TransmitterInput.INSTANCE.simulate(fake);
                ReceiverWizardScreen opened = new ReceiverWizardScreen(null, InputConfig.get(), result -> {
                });
                this.wizard = opened;
                minecraft.gui.setScreen(opened);
                this.next("walk", tick);
                return StepResult.pending();
            }
            Screen screen = minecraft.gui.screen();
            if (!(screen instanceof ReceiverWizardScreen w) || w != this.wizard) {
                return StepResult.fail("Assistent nicht mehr offen in Phase " + this.phase + ": " + (screen == null ? "kein Screen" : screen.getClass().getName()));
            }
            if (ThemeStats.frames(ReceiverWizardScreen.class) == 0L) {
                return StepResult.pending();
            }
            Stop at = position(w);
            if (at != this.last) {
                this.last = at;
                this.since = tick;
                this.trail.append(" → ").append(at == null ? w.page() + "/" + w.currentChannel() : at).append('@').append(tick);
            }
            if (tick - this.since > TIMEOUT && !this.phase.startsWith("hold")) {
                return StepResult.fail("Assistent hängt bei " + (at == null ? w.page() + " " + w.currentChannel() : at) + " (Ziel " + this.stop + ")");
            }
            if (at == this.stop) {
                this.reached = true;
                return StepResult.pass();
            }
            if (!"walk".equals(this.phase)) {
                return this.conflict(w, tick);
            }
            if (at == null) {
                if (w.page() == ReceiverWizardScreen.Page.DEVICE) {
                    this.press(w, "propwash.input.wizard.next", tick);
                } else if (w.page() == ReceiverWizardScreen.Page.CHANNELS && w.currentChannel() != null && w.currentChannel() != StickChannel.THROTTLE) {
                    this.press(w, "propwash.input.wizard.skip_channel", tick);
                }
                return StepResult.pending();
            }
            switch (at) {
                case THROTTLE -> {
                    if (this.stop == Stop.CONFIRM) {
                        this.next("rest", tick);
                    } else {
                        this.press(w, "propwash.input.wizard.skip_channel", tick);
                    }
                }
                case CHECK -> this.press(w, "propwash.input.wizard.to_calibration", tick);
                case CENTER -> this.press(w, "propwash.input.wizard.capture_center", tick);
                case EXTREMES -> this.press(w, "propwash.input.wizard.apply_calibration", tick);
                case REVIEW -> this.press(w, "propwash.input.wizard.next", tick);
                case SWITCHES -> this.press(w, "propwash.input.wizard.assign", tick);
                default -> {
                    return StepResult.fail("Ziel " + this.stop + " liegt vor " + at);
                }
            }
            return StepResult.pending();
        }

        private StepResult conflict(ReceiverWizardScreen w, int tick) {
            int elapsed = tick - this.since;
            switch (this.phase) {
                case "rest" -> {
                    if (elapsed >= 12) {
                        this.ramp(DOWN, LEAD_IN, "lead_up", tick);
                    }
                }
                case "lead_up" -> {
                    if (!this.ramping(tick)) {
                        this.ramp(LEAD_IN, DOWN, "lead_down", tick);
                    }
                }
                case "lead_down" -> {
                    if (!this.ramping(tick)) {
                        this.ramp(DOWN, UP, "up", tick);
                    }
                }
                case "up" -> {
                    if (!this.ramping(tick)) {
                        this.next("hold_top", tick);
                    }
                }
                case "hold_top" -> {
                    if (w.currentChannel() != StickChannel.THROTTLE) {
                        DeviceProfile profile = w.working().profile(GUID);
                        return StepResult.fail("Gas trotz Widerspruch zur Startlage gespeichert, invertiert " + (profile != null && profile.throttle.invert));
                    }
                    if (elapsed > 70) {
                        return StepResult.fail("Keine Rückfrage nach 3,5 s oben: " + w.detectionStage());
                    }
                }
                default -> {
                    return StepResult.fail("Unbekannte Phase " + this.phase);
                }
            }
            return StepResult.pending();
        }
    }

    static @Nullable Button button(ReceiverWizardScreen wizard, String key) {
        String label = Component.translatable(key).getString();
        for (GuiEventListener child : wizard.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(label)) {
                return button;
            }
        }
        return null;
    }

    static @Nullable Button clearButton(ReceiverWizardScreen wizard) {
        for (GuiEventListener child : wizard.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals("×")) {
                return button;
            }
        }
        return null;
    }
}
