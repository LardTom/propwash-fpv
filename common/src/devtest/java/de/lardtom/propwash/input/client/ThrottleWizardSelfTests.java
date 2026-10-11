package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.input.ChannelMapping;
import de.lardtom.propwash.input.DetectionStage;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.ThrottleDetector;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ThrottleWizardSelfTests {
    static final String GUID = "05000000091200004f5400005e1f0003";
    static final String NAME = "EdgeTX Propwash Testfunke Joystick";
    private static final int AXES = 8;
    private static final int BUTTONS = 8;
    private static final int ROLL = 0;
    private static final int PITCH = 1;
    private static final int THROTTLE = 2;
    private static final int YAW = 3;
    private static final float THROTTLE_DOWN = 1.0F;
    private static final float THROTTLE_UP = -1.0F;
    private static final float THROTTLE_NUDGE = THROTTLE_DOWN + (THROTTLE_UP - THROTTLE_DOWN) * 0.4F;
    private static final int HOLD_TICKS = (int) (ThrottleDetector.HOLD_NANOS / 50_000_000L);
    private static final int RAMP_TICKS = 4;
    private static final int STEP_TIMEOUT = 60;

    private ThrottleWizardSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("input: Assistent erkennt Gas aus der Bewegung (Gas steht unten, kurz angetippt, Funke invertiert, simuliert)", 900, ctx -> {
            Driver driver = (Driver) ctx.state().computeIfAbsent("driver", k -> new Driver(Goal.VERIFIED));
            StepResult result;
            try {
                result = driver.advance(ctx.tick());
            } catch (RuntimeException e) {
                driver.close();
                throw e;
            }
            if (result.status() != StepResult.Status.PENDING) {
                driver.close();
                ctx.log("Ablauf: " + driver.trail());
            }
            return result;
        });
    }

    public static Screenshots.Scene throttleScene() {
        return new DriverScene(Goal.ONE_END);
    }

    public static Screenshots.Scene holdScene() {
        return new DriverScene(Goal.HOLDING);
    }

    public static Screenshots.Scene checkScene() {
        return new DriverScene(Goal.CHECK);
    }

    enum Goal {
        ONE_END,
        HOLDING,
        CHECK,
        VERIFIED
    }

    private static final class DriverScene implements Screenshots.Scene {
        private final Goal goal;
        private @Nullable Driver driver;

        private DriverScene(Goal goal) {
            this.goal = goal;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            if (this.driver == null) {
                this.driver = new Driver(this.goal);
            }
            return this.driver.advance(ctx.tick());
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Screenshots.parkMouse(mc);
            if (mc.gui.screen() instanceof ReceiverWizardScreen wizard) {
                wizard.demo().freezeAt(switch (this.goal) {
                    case ONE_END -> 1.3F;
                    case HOLDING -> 2.6F;
                    case CHECK, VERIFIED -> 2.9F;
                });
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (this.driver != null) {
                this.driver.close();
                this.driver = null;
            }
        }
    }

    static final class Driver {
        private final Goal goal;
        private final StringBuilder trail = new StringBuilder();
        private @Nullable SimulatedRadio radio;
        private String phase = "begin";
        private int since;
        private float rampFrom;
        private float rampTo;
        private boolean confirmed;

        Driver(Goal goal) {
            this.goal = goal;
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
        }

        private void next(String phase, int tick) {
            this.phase = phase;
            this.since = tick;
            this.trail.append(" → ").append(phase).append('@').append(tick);
        }

        private void set(int axis, float value) {
            SimulatedRadio current = this.radio;
            if (current != null) {
                current.setAxis(axis, value);
            }
        }

        private void ramp(float from, float to, String phase, int tick) {
            this.rampFrom = from;
            this.rampTo = to;
            this.next(phase, tick);
        }

        private boolean ramping(int tick) {
            int step = tick - this.since;
            float t = Math.min(1.0F, step / (float) RAMP_TICKS);
            this.set(THROTTLE, this.rampFrom + (this.rampTo - this.rampFrom) * t);
            return step < RAMP_TICKS;
        }

        StepResult advance(int tick) {
            Minecraft minecraft = Minecraft.getInstance();
            Screen screen = minecraft.gui.screen();
            if (!"begin".equals(this.phase) && !(screen instanceof ReceiverWizardScreen)) {
                return StepResult.fail("Assistent nicht mehr offen in Phase " + this.phase + ": " + (screen == null ? "kein Screen" : screen.getClass().getName()));
            }
            ReceiverWizardScreen wizard = screen instanceof ReceiverWizardScreen w ? w : null;
            int elapsed = tick - this.since;
            switch (this.phase) {
                case "begin" -> {
                    DeviceProfile profile = ProfileDefaults.radio(NAME, AXES);
                    SimulatedRadio fake = new SimulatedRadio(GUID, NAME, profile, AXES, BUTTONS);
                    for (int i = 4; i < AXES; i++) {
                        fake.setAxis(i, -1.0F);
                    }
                    fake.setAxis(THROTTLE, THROTTLE_DOWN);
                    this.radio = fake;
                    TransmitterInput.INSTANCE.simulate(fake);
                    minecraft.gui.setScreen(new ReceiverWizardScreen(null, InputConfig.get(), result -> {
                    }));
                    this.next("device", tick);
                }
                case "device" -> {
                    if (wizard == null || ThemeStats.frames(ReceiverWizardScreen.class) == 0L) {
                        return elapsed > STEP_TIMEOUT ? StepResult.fail("Assistent wird nicht gezeichnet") : StepResult.pending();
                    }
                    if (wizard.page() != ReceiverWizardScreen.Page.DEVICE) {
                        return StepResult.fail("Assistent startet nicht auf der Geräteseite: " + wizard.page());
                    }
                    Button next = button(wizard, "propwash.input.wizard.next");
                    if (next == null || !next.active) {
                        return elapsed > STEP_TIMEOUT ? StepResult.fail("„Weiter“ fehlt oder ist aus – simulierte Funke nicht als Gerät sichtbar")
                                : StepResult.pending();
                    }
                    next.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    this.next("roll", tick);
                }
                case "roll" -> {
                    StepResult waited = this.awaitDetector(wizard, StickChannel.ROLL, elapsed);
                    if (waited != null) {
                        return waited;
                    }
                    this.set(ROLL, 1.0F);
                    this.next("pitch", tick);
                }
                case "pitch" -> {
                    StepResult waited = this.awaitDetector(wizard, StickChannel.PITCH, elapsed);
                    if (waited != null) {
                        return waited;
                    }
                    this.set(ROLL, 0.0F);
                    this.set(PITCH, 1.0F);
                    this.next("yaw", tick);
                }
                case "yaw" -> {
                    StepResult waited = this.awaitDetector(wizard, StickChannel.YAW, elapsed);
                    if (waited != null) {
                        return waited;
                    }
                    this.set(PITCH, 0.0F);
                    this.set(YAW, 1.0F);
                    this.next("throttle", tick);
                }
                case "throttle" -> {
                    StepResult waited = this.awaitDetector(wizard, StickChannel.THROTTLE, elapsed);
                    if (waited != null) {
                        return waited;
                    }
                    this.set(YAW, 0.0F);
                    this.next("rest_down", tick);
                }
                case "rest_down" -> {
                    if (wizard.currentChannel() != StickChannel.THROTTLE) {
                        return StepResult.fail("Gas erkannt, obwohl es nur unten stand: " + wizard.currentChannel());
                    }
                    if (elapsed < 20) {
                        return StepResult.pending();
                    }
                    if (wizard.detectionStage() != DetectionStage.WAITING) {
                        return StepResult.fail("Ruhendes Gas meldet " + wizard.detectionStage() + " statt WAITING");
                    }
                    this.ramp(THROTTLE_DOWN, THROTTLE_NUDGE, "nudge_up", tick);
                }
                case "nudge_up" -> {
                    if (this.ramping(tick)) {
                        return StepResult.pending();
                    }
                    this.ramp(THROTTLE_NUDGE, THROTTLE_DOWN, "nudge_down", tick);
                }
                case "nudge_down" -> {
                    if (this.ramping(tick)) {
                        return StepResult.pending();
                    }
                    if (wizard.currentChannel() != StickChannel.THROTTLE) {
                        return StepResult.fail("Gas nach kurzem Antippen erkannt: " + wizard.currentChannel());
                    }
                    this.ramp(THROTTLE_DOWN, THROTTLE_UP, "up", tick);
                }
                case "up" -> {
                    if (this.ramping(tick)) {
                        return StepResult.pending();
                    }
                    this.next("pause_top", tick);
                }
                case "pause_top" -> {
                    if (wizard.currentChannel() != StickChannel.THROTTLE) {
                        return StepResult.fail("Pause oben als Gas-Ende genommen (Fehler aus dem Discord-Bericht)");
                    }
                    if (this.goal == Goal.ONE_END && elapsed >= 4) {
                        return wizard.detectionStage() == DetectionStage.ONE_END ? StepResult.pass()
                                : StepResult.fail("Nach dem Weg nach oben nicht „Ein Ende“: " + wizard.detectionStage());
                    }
                    if (elapsed < HOLD_TICKS + 10) {
                        return StepResult.pending();
                    }
                    if (wizard.detectionStage() != DetectionStage.ONE_END) {
                        return StepResult.fail("Oben gehalten, Stufe " + wizard.detectionStage() + " statt ONE_END");
                    }
                    this.ramp(THROTTLE_UP, THROTTLE_DOWN, "down", tick);
                }
                case "down" -> {
                    if (this.ramping(tick)) {
                        return StepResult.pending();
                    }
                    this.next("hold_down", tick);
                }
                case "hold_down" -> {
                    if (this.goal == Goal.HOLDING && wizard.currentChannel() == StickChannel.THROTTLE) {
                        return wizard.detectionStage() == DetectionStage.HOLDING ? StepResult.pass()
                                : elapsed > 4 ? StepResult.fail("Unten nach beiden Enden nicht „Halten“: " + wizard.detectionStage()) : StepResult.pending();
                    }
                    if (wizard.currentChannel() == StickChannel.THROTTLE && wizard.detectionStage() == DetectionStage.CONFIRM) {
                        if (this.confirmed) {
                            return StepResult.fail("Rückfrage nach der Antwort „Ganz UNTEN“ noch offen");
                        }
                        Button down = button(wizard, "propwash.input.wizard.throttle.confirm_down");
                        if (down == null) {
                            return StepResult.fail("Rückfrage ohne Knopf „Ganz UNTEN“");
                        }
                        down.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                        this.confirmed = true;
                        this.trail.append(" [unten bestätigt]");
                        return StepResult.pending();
                    }
                    if (wizard.currentChannel() == StickChannel.THROTTLE) {
                        return elapsed > STEP_TIMEOUT ? StepResult.fail("Gas nach oben–unten–halten nicht erkannt: " + wizard.detectionStage())
                                : StepResult.pending();
                    }
                    if (wizard.currentChannel() != null || wizard.page() != ReceiverWizardScreen.Page.CHANNELS) {
                        return StepResult.fail("Nach dem Gas keine Prüfansicht: " + wizard.page() + " " + wizard.currentChannel());
                    }
                    if (!this.confirmed) {
                        return StepResult.fail("Invertiertes Gas gegen die Richtung im Profil ohne Rückfrage gespeichert");
                    }
                    if (elapsed < HOLD_TICKS / 2) {
                        return StepResult.fail("Gas schon nach " + elapsed + " Ticks erkannt (Haltezeit " + HOLD_TICKS + " Ticks)");
                    }
                    this.next("check_down", tick);
                }
                case "check_down" -> {
                    if (elapsed < 3) {
                        return StepResult.pending();
                    }
                    StepResult mapping = checkMapping(wizard);
                    if (mapping.status() == StepResult.Status.FAIL) {
                        return mapping;
                    }
                    if (wizard.previewSticks().throttle() > 0.02F) {
                        return StepResult.fail("Prüfansicht: Gas unten zeigt " + wizard.previewSticks().throttle());
                    }
                    this.ramp(THROTTLE_DOWN, THROTTLE_UP, "check_up", tick);
                }
                case "check_up" -> {
                    if (this.ramping(tick)) {
                        return StepResult.pending();
                    }
                    if (elapsed < RAMP_TICKS + 3) {
                        return StepResult.pending();
                    }
                    if (wizard.previewSticks().throttle() < 0.98F) {
                        return StepResult.fail("Prüfansicht: Gas oben zeigt " + wizard.previewSticks().throttle());
                    }
                    if (this.goal == Goal.CHECK) {
                        return StepResult.pass();
                    }
                    this.next("toggle", tick);
                }
                case "toggle" -> {
                    List<CycleButton<Boolean>> toggles = toggles(wizard);
                    CycleButton<Boolean> throttle = toggles.get(StickChannel.THROTTLE.ordinal());
                    throttle.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    DeviceProfile profile = wizard.working().profile(GUID);
                    if (profile == null || profile.throttle.invert || throttle.getValue()) {
                        return StepResult.fail("„Inv.“ beim Gas schaltet die Richtung nicht um");
                    }
                    this.next("toggled", tick);
                }
                case "toggled" -> {
                    if (elapsed < 3) {
                        return StepResult.pending();
                    }
                    if (wizard.previewSticks().throttle() > 0.02F) {
                        return StepResult.fail("Nach „Norm.“ zeigt Gas oben nicht 0: " + wizard.previewSticks().throttle());
                    }
                    return StepResult.pass();
                }
                default -> {
                    return StepResult.fail("Unbekannte Phase " + this.phase);
                }
            }
            return StepResult.pending();
        }

        private @Nullable StepResult awaitDetector(@Nullable ReceiverWizardScreen wizard, StickChannel channel, int elapsed) {
            if (wizard != null && wizard.currentChannel() == channel && wizard.detectionStage() != null) {
                return null;
            }
            if (elapsed > STEP_TIMEOUT) {
                return StepResult.fail("Assistent kommt nicht zu " + channel + ": " + (wizard == null ? "-" : wizard.page() + " " + wizard.currentChannel()));
            }
            return StepResult.pending();
        }

        private static StepResult checkMapping(ReceiverWizardScreen wizard) {
            DeviceProfile profile = wizard.working().profile(GUID);
            if (profile == null) {
                return StepResult.fail("Kein Profil für die simulierte Funke");
            }
            int[] expectedAxis = {ROLL, PITCH, YAW, THROTTLE};
            boolean[] expectedInvert = {false, false, false, true};
            StickChannel[] channels = StickChannel.values();
            List<CycleButton<Boolean>> toggles = toggles(wizard);
            if (toggles.size() != channels.length) {
                return StepResult.fail("Prüfansicht braucht vier „Inv.“-Schalter, hat " + toggles.size());
            }
            for (int i = 0; i < channels.length; i++) {
                ChannelMapping mapping = profile.channel(channels[i]);
                if (mapping.axis != expectedAxis[i] || mapping.invert != expectedInvert[i]) {
                    return StepResult.fail(channels[i] + " falsch erkannt: Achse " + mapping.axis + ", invertiert " + mapping.invert);
                }
                if (toggles.get(i).getValue() != expectedInvert[i]) {
                    return StepResult.fail("„Inv.“-Schalter von " + channels[i] + " zeigt " + toggles.get(i).getValue());
                }
            }
            return StepResult.pass();
        }

        private static @Nullable Button button(ReceiverWizardScreen wizard, String key) {
            String label = Component.translatable(key).getString();
            for (GuiEventListener child : wizard.children()) {
                if (child instanceof Button button && button.getMessage().getString().equals(label)) {
                    return button;
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private static List<CycleButton<Boolean>> toggles(ReceiverWizardScreen wizard) {
            List<CycleButton<Boolean>> list = new ArrayList<>();
            for (GuiEventListener child : wizard.children()) {
                if (child instanceof CycleButton<?> cycle && cycle.getValue() instanceof Boolean) {
                    list.add((CycleButton<Boolean>) cycle);
                }
            }
            return list;
        }
    }
}
