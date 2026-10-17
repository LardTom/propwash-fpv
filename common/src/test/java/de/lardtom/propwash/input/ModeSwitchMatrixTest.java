package de.lardtom.propwash.input;

import de.lardtom.propwash.sim.StabMode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeSwitchMatrixTest {
    private static final int AXES = 8;
    private static final int AUX4 = 7;
    private static final int BUTTONS = 4;
    private static final int[] WALK = {1000, 1500, 2000, 1500, 1000, 1500, 1500, 2000, 1000, 1000, 1500, 2000, 2000, 1500};
    private static final Band LOW = new Band(900, 1300, 1000);
    private static final Band MID = new Band(1400, 1600, 1500);
    private static final Band HIGH = new Band(1700, 2100, 2000);
    private static final Band[] BANDS = {LOW, MID, HIGH};

    @Test
    void boxerWithAngleInTheMiddleAndHorizonLowOnAux4FliesEveryPosition() {
        for (boolean wizard : new boolean[]{false, true}) {
            DeviceProfile profile = new DeviceProfile();
            profile.setBinding(SwitchFunction.ARM, SwitchAssignment.axisMicros(4, 1700, 2100));
            if (wizard) {
                profile.setBinding(SwitchFunction.ANGLE, detect(AUX4, 1000, 1500));
                SwitchAssignment horizon = detect(AUX4, 2000, 1000);
                horizon.setActiveHighMicros(1300);
                profile.setBinding(SwitchFunction.HORIZON, horizon);
            } else {
                profile.setBinding(SwitchFunction.ANGLE, SwitchAssignment.axisMicros(AUX4, 1400, 1600));
                profile.setBinding(SwitchFunction.HORIZON, SwitchAssignment.axisMicros(AUX4, 1000, 1300));
            }
            assertEquals(1400, profile.binding(SwitchFunction.ANGLE).activeLowMicros(), "Angle unten, Assistent " + wizard);
            assertEquals(1600, profile.binding(SwitchFunction.ANGLE).activeHighMicros(), "Angle oben, Assistent " + wizard);
            assertEquals(1300, profile.binding(SwitchFunction.HORIZON).activeHighMicros(), "Horizon oben, Assistent " + wizard);
            Rig rig = new Rig(profile, AXES, 0);
            rig.set(AUX4, 1000);
            rig.startSession();
            assertEquals(StabMode.HORIZON, rig.session.mode());
            for (int us : WALK) {
                rig.set(AUX4, us);
                rig.frame();
                StabMode wanted = us == 1500 ? StabMode.ANGLE : us == 1000 ? StabMode.HORIZON : StabMode.ACRO;
                assertEquals(wanted, rig.session.mode(), us + " µs, Assistent " + wizard);
                assertEquals(FlightModeSelector.Source.SWITCH, rig.session.source(), us + " µs");
                assertEquals(wanted, rig.tabMode(), "Modi-Tab bei " + us + " µs");
            }
        }
    }

    @Test
    void lampsInTheModesTabFollowTheSwitchWhileThePausedSessionStandsStill() {
        DeviceProfile profile = owner();
        Rig rig = new Rig(profile, AXES, 0);
        rig.set(AUX4, 1000);
        rig.startSession();
        assertEquals(StabMode.HORIZON, rig.session.mode());
        rig.paused = true;
        rig.openTab();
        rig.set(AUX4, 1500);
        rig.frame();
        assertEquals(StabMode.HORIZON, rig.session.mode(), "die pausierte Sitzung liest den Schalter nicht");
        assertEquals(StabMode.ANGLE, rig.tabMode(), "der Modi-Tab zeigt, was nach dem Schließen gilt");
        rig.set(AUX4, 2000);
        rig.frame();
        assertEquals(StabMode.ACRO, rig.tabMode());
        rig.set(AUX4, 1500);
        rig.frame();
        rig.paused = false;
        rig.frame();
        assertEquals(StabMode.ANGLE, rig.session.mode(), "nach dem Schließen fliegt die Drohne den gezeigten Modus");
    }

    @Test
    void unsavedRangesInTheModesTabAreWhatTheLampsShow() {
        DeviceProfile saved = ProfileDefaults.radio("Radiomaster Boxer", AXES);
        saved.setBinding(SwitchFunction.ANGLE, SwitchAssignment.axisMicros(AUX4, 1700, 2100));
        saved.setBinding(SwitchFunction.HORIZON, SwitchAssignment.axisMicros(AUX4, 1300, 1675));
        Rig rig = new Rig(saved, AXES, 0);
        rig.set(AUX4, 1500);
        rig.startSession();
        assertEquals(StabMode.HORIZON, rig.session.mode());
        rig.openTab();
        rig.working.setBinding(SwitchFunction.ANGLE, SwitchAssignment.axisMicros(AUX4, 1400, 1600));
        rig.working.binding(SwitchFunction.HORIZON).setActiveHighMicros(1300);
        rig.working.binding(SwitchFunction.HORIZON).setActiveLowMicros(1000);
        rig.frame();
        assertEquals(StabMode.ANGLE, rig.tabMode(), "Lampe folgt den bearbeiteten Bereichen");
        assertEquals(StabMode.HORIZON, rig.session.mode(), "ungespeichert fliegt die Drohne die alte Belegung");
        rig.save();
        rig.frame();
        assertEquals(StabMode.ANGLE, rig.session.mode(), "gespeichert fliegt die Drohne, was die Lampe zeigte");
        assertEquals(StabMode.ANGLE, rig.tabMode());
    }

    @Test
    void everyFunctionOnOneSharedChannelAndOnItsOwnChannel() {
        int checked = 0;
        for (SwitchFunction function : SwitchFunction.values()) {
            for (Kind kind : Kind.values()) {
                if (kind.toggle && !function.supportsToggle()) {
                    continue;
                }
                for (Layout layout : Layout.values()) {
                    if (kind.button && layout == Layout.SHARED) {
                        continue;
                    }
                    for (Band band : kind.button ? new Band[]{MID} : BANDS) {
                        checked += checkFunction(function, kind, layout, band);
                    }
                }
            }
        }
        assertTrue(checked > 5000, "geprüfte Schaltstellungen: " + checked);
    }

    @Test
    void everyFlightModeLayoutShowsInTheTabWhatTheDroneFlies() {
        List<DeviceProfile> layouts = new ArrayList<>();
        layouts.add(owner());
        layouts.add(ProfileDefaults.radio("Radiomaster Pocket", AXES));
        DeviceProfile separate = new DeviceProfile();
        separate.setBinding(SwitchFunction.ANGLE, SwitchAssignment.axisMicros(5, 1700, 2100));
        separate.setBinding(SwitchFunction.HORIZON, SwitchAssignment.axisMicros(6, 1700, 2100));
        separate.setBinding(SwitchFunction.MODE_3D, SwitchAssignment.axisMicros(7, 1700, 2100));
        layouts.add(separate);
        DeviceProfile inverted = owner();
        inverted.binding(SwitchFunction.HORIZON).invert = true;
        inverted.binding(SwitchFunction.ANGLE).invert = true;
        layouts.add(inverted);
        DeviceProfile all = new DeviceProfile();
        all.setBinding(SwitchFunction.ANGLE, SwitchAssignment.axisMicros(AUX4, 900, 1100));
        all.setBinding(SwitchFunction.HORIZON, SwitchAssignment.axisMicros(AUX4, 1150, 1350));
        all.setBinding(SwitchFunction.MODE_3D, SwitchAssignment.axisMicros(AUX4, 1400, 1600));
        all.setBinding(SwitchFunction.BEEPER, SwitchAssignment.axisMicros(AUX4, 1650, 1850));
        all.setBinding(SwitchFunction.RESCUE, SwitchAssignment.axisMicros(AUX4, 1900, 2100));
        all.setBinding(SwitchFunction.ARM, SwitchAssignment.axisMicros(4, 1700, 2100));
        layouts.add(all);
        DeviceProfile toggles = owner();
        toggles.binding(SwitchFunction.ANGLE).toggle = true;
        toggles.binding(SwitchFunction.HORIZON).toggle = true;
        toggles.binding(SwitchFunction.HORIZON).invert = true;
        layouts.add(toggles);
        DeviceProfile pad = ProfileDefaults.gamepad("Pad");
        pad.setBinding(SwitchFunction.MODE_3D, SwitchAssignment.toggleButton(3));
        layouts.add(pad);
        long seed = 20261014L;
        int pausedChecks = 0;
        for (int l = 0; l < layouts.size(); l++) {
            DeviceProfile profile = layouts.get(l);
            boolean gamepad = profile == pad;
            for (int run = 0; run < 40; run++) {
                Random random = new Random(seed + 1000L * l + run);
                Rig rig = gamepad ? new Rig(profile, ProfileDefaults.PAD_AXES, ProfileDefaults.PAD_BUTTONS) : new Rig(profile, AXES, BUTTONS);
                for (int axis = 4; axis < AXES && !gamepad; axis++) {
                    rig.set(axis, BANDS[random.nextInt(3)].at);
                }
                rig.startSession();
                for (int step = 0; step < 120; step++) {
                    String where = "Belegung " + l + ", Lauf " + run + ", Schritt " + step;
                    rig.randomEvent(random, gamepad);
                    rig.frame();
                    rig.checkPreview(where);
                }
                pausedChecks += rig.pausedChecks;
            }
        }
        assertTrue(pausedChecks > 200, "Wechsel aus der Pause: " + pausedChecks);
    }

    @Test
    void toggleLatchesInTheTabStartFromTheLiveSwitchNotFromTheirStartState() {
        DeviceProfile pad = ProfileDefaults.gamepad("Pad");
        SwitchAssignment angle = pad.binding(SwitchFunction.ANGLE);
        assertTrue(angle.toggle && angle.startOn, "Gamepad: Angle als Umschalter, der eingeschaltet startet");
        Rig rig = new Rig(pad, ProfileDefaults.PAD_AXES, ProfileDefaults.PAD_BUTTONS);
        rig.startSession();
        assertEquals(StabMode.ANGLE, rig.session.mode());
        rig.buttons[angle.index] = true;
        rig.frame();
        rig.buttons[angle.index] = false;
        rig.frame();
        assertEquals(StabMode.ACRO, rig.session.mode());
        SwitchEvaluator standalone = new SwitchEvaluator();
        int alone = standalone.evaluate(pad, rig.axes, rig.buttons);
        assertTrue(SwitchEvaluator.isSet(alone, SwitchFunction.ANGLE), "ohne Abgleich startet der Tab mit Angle an (alter Fehler)");
        rig.openTab();
        rig.frame();
        assertFalse(SwitchEvaluator.isSet(rig.tabBits, SwitchFunction.ANGLE), "mit Abgleich zeigt der Tab den Schalterzustand der Drohne");
        assertEquals(StabMode.ACRO, rig.tabMode());
        rig.paused = true;
        rig.buttons[angle.index] = true;
        rig.frame();
        rig.buttons[angle.index] = false;
        rig.frame();
        assertEquals(StabMode.ANGLE, rig.tabMode());
        rig.paused = false;
        rig.frame();
        assertEquals(StabMode.ANGLE, rig.session.mode());
    }

    @Test
    void anEditedToggleKeepsItsOwnLatchInTheTab() {
        DeviceProfile profile = owner();
        Rig rig = new Rig(profile, AXES, 0);
        rig.set(AUX4, 2000);
        rig.startSession();
        rig.openTab();
        rig.working.binding(SwitchFunction.ANGLE).toggle = true;
        rig.resetTab();
        rig.frame();
        rig.set(AUX4, 1500);
        rig.frame();
        assertTrue(SwitchEvaluator.isSet(rig.tabBits, SwitchFunction.ANGLE));
        rig.set(AUX4, 2000);
        rig.frame();
        assertTrue(SwitchEvaluator.isSet(rig.tabBits, SwitchFunction.ANGLE), "bearbeiteter Umschalter bleibt im Tab an");
        assertEquals(StabMode.ANGLE, rig.tabMode());
        assertEquals(StabMode.ACRO, rig.session.mode(), "ungespeichert gilt für die Drohne noch der Bereich");
        rig.save();
        rig.frame();
        assertEquals(StabMode.ANGLE, rig.session.mode(), "nach dem Speichern übernimmt die Drohne den Umschalter-Zustand des Tabs");
        rig.set(AUX4, 1500);
        rig.frame();
        assertEquals(StabMode.ACRO, rig.session.mode());
        assertEquals(StabMode.ACRO, rig.tabMode());
    }

    @Test
    void keyChoiceShowsUntilTheSwitchMovesEvenWhilePaused() {
        Rig rig = new Rig(owner(), AXES, 0);
        rig.set(AUX4, 1500);
        rig.startSession();
        rig.session.select(StabMode.ACRO);
        rig.frame();
        assertEquals(StabMode.ACRO, rig.session.mode());
        assertTrue(rig.session.keyOverridesSwitch(true, rig.liveBits, rig.liveReadable));
        rig.paused = true;
        rig.openTab();
        rig.frame();
        assertEquals(StabMode.ACRO, rig.tabMode());
        assertTrue(rig.tabOverride());
        rig.set(AUX4, 1000);
        rig.frame();
        assertEquals(StabMode.HORIZON, rig.tabMode());
        assertFalse(rig.tabOverride(), "Schalter bewegt: kein Tasten-Vorrang mehr");
        rig.set(AUX4, 1500);
        rig.frame();
        assertEquals(StabMode.ACRO, rig.tabMode(), "zurück an der alten Stellung sieht die Drohne keine Bewegung");
        assertTrue(rig.tabOverride());
        rig.set(AUX4, 2000);
        rig.frame();
        assertEquals(StabMode.ACRO, rig.tabMode());
        assertEquals(FlightModeSelector.Source.KEY, rig.session.source());
        rig.paused = false;
        rig.frame();
        assertEquals(StabMode.ACRO, rig.session.mode());
        assertEquals(FlightModeSelector.Source.SWITCH, rig.session.source());
    }

    @Test
    void mode3dPreviewFollowsTheSharedChannel() {
        DeviceProfile profile = owner();
        profile.setBinding(SwitchFunction.MODE_3D, SwitchAssignment.axisMicros(AUX4, 1700, 2100));
        Rig rig = new Rig(profile, AXES, 0);
        rig.set(AUX4, 1000);
        rig.startSession();
        assertFalse(rig.mode3d.selected());
        rig.paused = true;
        rig.openTab();
        rig.set(AUX4, 2000);
        rig.frame();
        assertFalse(rig.mode3d.selected(), "pausiert liest die Sitzung den Schalter nicht");
        assertTrue(rig.mode3d.preview(true, rig.tabBits, rig.tabReadable), "der Tab zeigt 3D an");
        assertEquals(StabMode.ACRO, rig.tabMode());
        rig.paused = false;
        rig.frame();
        assertTrue(rig.mode3d.selected());
        rig.mode3d.toggle();
        rig.frame();
        assertFalse(rig.mode3d.selected());
        assertTrue(rig.mode3d.keyOverridesSwitch(true, rig.tabBits, rig.tabReadable), "Taste 3D aus gegen den Schalter");
        assertFalse(rig.mode3d.preview(true, rig.tabBits, rig.tabReadable));
        rig.paused = true;
        rig.set(AUX4, 1500);
        rig.frame();
        assertFalse(rig.mode3d.preview(true, rig.tabBits, rig.tabReadable));
        assertFalse(rig.mode3d.keyOverridesSwitch(true, rig.tabBits, rig.tabReadable), "Schalter bewegt: kein Tasten-Vorrang");
        assertEquals(StabMode.ANGLE, rig.tabMode());
        rig.set(AUX4, 2000);
        rig.frame();
        assertFalse(rig.mode3d.preview(true, rig.tabBits, rig.tabReadable), "zurück an der alten Stellung gilt weiter die Taste");
        assertTrue(rig.mode3d.keyOverridesSwitch(true, rig.tabBits, rig.tabReadable));
        rig.paused = false;
        rig.frame();
        assertFalse(rig.mode3d.selected());
        assertEquals(Mode3dSelector.Source.KEY, rig.mode3d.source());
        rig.set(AUX4, 1500);
        rig.frame();
        assertFalse(rig.mode3d.selected());
        assertEquals(Mode3dSelector.Source.SWITCH, rig.mode3d.source());
        assertEquals(StabMode.ANGLE, rig.session.mode());
    }

    private static int checkFunction(SwitchFunction function, Kind kind, Layout layout, Band band) {
        DeviceProfile profile = new DeviceProfile();
        int axis = layout == Layout.SHARED ? AUX4 : 4 + function.ordinal() % 4;
        SwitchAssignment binding = kind.create(axis, band, function.ordinal() % BUTTONS);
        profile.setBinding(function, binding);
        List<SwitchFunction> neighbours = new ArrayList<>();
        List<Band> neighbourBands = new ArrayList<>();
        if (layout == Layout.SHARED) {
            SwitchFunction[] all = SwitchFunction.values();
            for (Band other : BANDS) {
                if (other == band) {
                    continue;
                }
                SwitchFunction neighbour = all[(function.ordinal() + 1 + neighbours.size()) % all.length];
                profile.setBinding(neighbour, SwitchAssignment.axisMicros(AUX4, other.low, other.high));
                neighbours.add(neighbour);
                neighbourBands.add(other);
            }
        }
        Model model = new Model(kind, band, binding.startOn);
        SwitchEvaluator evaluator = new SwitchEvaluator();
        SwitchEvaluator tab = new SwitchEvaluator();
        float[] axes = new float[AXES];
        boolean[] buttons = new boolean[BUTTONS];
        for (int a = 0; a < AXES; a++) {
            axes[a] = ChannelMath.fromRangeMicros(1000);
        }
        int checks = 0;
        String what = function + " " + kind + " " + layout + " " + band.low + "–" + band.high;
        for (int step = 0; step < WALK.length; step++) {
            int us = WALK[step];
            if (kind.button) {
                buttons[binding.index] = us == band.at;
            } else {
                axes[axis] = ChannelMath.fromRangeMicros(us);
            }
            int bits = evaluator.evaluate(profile, axes, buttons);
            if (step == WALK.length / 2) {
                tab = new SwitchEvaluator();
            }
            tab.follow(evaluator, profile, profile);
            int tabBits = tab.evaluate(profile, axes, buttons);
            boolean wanted = model.next(us);
            assertEquals(wanted, SwitchEvaluator.isSet(bits, function), what + " bei " + us + " µs (Schritt " + step + ")");
            assertEquals(bits, tabBits, what + ": Modi-Tab bei " + us + " µs (Schritt " + step + ")");
            for (int n = 0; n < neighbours.size(); n++) {
                Band other = neighbourBands.get(n);
                assertEquals(us >= other.low && us <= other.high, SwitchEvaluator.isSet(bits, neighbours.get(n)),
                        what + ": Nachbar " + neighbours.get(n) + " bei " + us + " µs");
            }
            assertTrue(SwitchEvaluator.isSet(SwitchEvaluator.readable(profile, axes, buttons), function), what + " lesbar");
            checks++;
        }
        return checks;
    }

    private static SwitchAssignment detect(int axis, int fromUs, int toUs) {
        float[] axes = new float[AXES];
        for (int a = 0; a < AXES; a++) {
            axes[a] = ChannelMath.fromRangeMicros(1000);
        }
        axes[axis] = ChannelMath.fromRangeMicros(fromUs);
        SwitchDetector detector = new SwitchDetector(axes, new boolean[0], new boolean[AXES]);
        axes[axis] = ChannelMath.fromRangeMicros(toUs);
        SwitchAssignment found = null;
        for (long t = 0; t <= 400_000_000L && found == null; t += 50_000_000L) {
            found = detector.update(axes, new boolean[0], t);
        }
        assertNotNull(found, "Assistent erkennt den Schalter");
        assertEquals(axis, found.index);
        return found;
    }

    private static DeviceProfile owner() {
        DeviceProfile profile = new DeviceProfile();
        profile.setBinding(SwitchFunction.ARM, SwitchAssignment.axisMicros(4, 1700, 2100));
        profile.setBinding(SwitchFunction.ANGLE, SwitchAssignment.axisMicros(AUX4, 1400, 1600));
        profile.setBinding(SwitchFunction.HORIZON, SwitchAssignment.axisMicros(AUX4, 1000, 1300));
        return profile;
    }

    private record Shown(StabMode mode, boolean mode3d, boolean override, boolean override3d) {
    }

    private record Band(int low, int high, int at) {
        boolean contains(int us) {
            return us >= this.low && us <= this.high;
        }
    }

    private enum Layout {
        SHARED,
        SEPARATE
    }

    private enum Kind {
        RANGE(false, false, false, false),
        RANGE_INVERTED(false, true, false, false),
        TOGGLE(true, false, false, false),
        TOGGLE_INVERTED(true, true, false, false),
        TOGGLE_STARTING_ON(true, false, false, true),
        BUTTON(false, false, true, false),
        BUTTON_INVERTED(false, true, true, false),
        BUTTON_TOGGLE(true, false, true, false),
        BUTTON_TOGGLE_STARTING_ON(true, false, true, true);

        private final boolean toggle;
        private final boolean invert;
        private final boolean button;
        private final boolean startOn;

        Kind(boolean toggle, boolean invert, boolean button, boolean startOn) {
            this.toggle = toggle;
            this.invert = invert;
            this.button = button;
            this.startOn = startOn;
        }

        SwitchAssignment create(int axis, Band band, int button) {
            SwitchAssignment binding;
            if (this.button) {
                binding = SwitchAssignment.button(button, this.invert);
            } else if (this.invert && !this.toggle) {
                binding = SwitchAssignment.axisMicros(axis, SwitchAssignment.mirrorMicros(band.high), SwitchAssignment.mirrorMicros(band.low));
                binding.invert = true;
                assertEquals(band.low, binding.activeLowMicros(), "gespiegelter Bereich unten");
                assertEquals(band.high, binding.activeHighMicros(), "gespiegelter Bereich oben");
            } else {
                binding = SwitchAssignment.axisMicros(axis, band.low, band.high);
                binding.invert = this.invert;
            }
            binding.toggle = this.toggle;
            if (this.startOn) {
                binding.startingOn();
            }
            return binding;
        }
    }

    private static final class Model {
        private final Kind kind;
        private final Band band;
        private boolean latched;
        private boolean previous;
        private boolean primed;

        private Model(Kind kind, Band band, boolean startOn) {
            this.kind = kind;
            this.band = band;
            this.latched = startOn;
        }

        boolean next(int us) {
            boolean engaged = this.kind.button ? us == this.band.at : this.band.contains(us);
            if (!this.kind.toggle) {
                return engaged != (this.kind.button && this.kind.invert);
            }
            if (this.primed && engaged && !this.previous) {
                this.latched = !this.latched;
            }
            this.primed = true;
            this.previous = engaged;
            return this.kind.invert != this.latched;
        }
    }

    private static final class Rig {
        private DeviceProfile saved;
        private DeviceProfile working;
        private final float[] axes;
        private final boolean[] buttons;
        private final SwitchEvaluator live = new SwitchEvaluator();
        private SwitchEvaluator tab = new SwitchEvaluator();
        private final FlightModeSelector session = new FlightModeSelector();
        private final Mode3dSelector mode3d = new Mode3dSelector();
        private boolean paused;
        private boolean tabOpen;
        private int liveBits;
        private int liveReadable;
        private int tabBits;
        private int tabReadable;
        private @Nullable Shown pending;
        private int pausedChecks;

        private Rig(DeviceProfile profile, int axisCount, int buttonCount) {
            this.saved = profile;
            this.working = copy(profile);
            this.axes = new float[axisCount];
            this.buttons = new boolean[buttonCount];
            for (int a = 4; a < axisCount && !profile.gamepadLayout; a++) {
                this.axes[a] = ChannelMath.fromRangeMicros(1000);
            }
        }

        private static DeviceProfile copy(DeviceProfile profile) {
            DeviceProfile copy = new DeviceProfile();
            copy.gamepadLayout = profile.gamepadLayout;
            copy.copySwitchesFrom(profile);
            return copy;
        }

        void set(int axis, int us) {
            this.axes[axis] = ChannelMath.fromRangeMicros(us);
        }

        void startSession() {
            this.read();
            this.session.observe(true, this.liveBits, this.liveReadable);
            this.mode3d.observe(true, this.liveBits, this.liveReadable);
        }

        void openTab() {
            this.tabOpen = true;
            this.working = copy(this.saved);
            this.tab = new SwitchEvaluator();
        }

        void resetTab() {
            this.tab.reset();
        }

        void save() {
            this.saved = copy(this.working);
            if (this.tabOpen) {
                this.live.copyFrom(this.tab);
            }
        }

        void frame() {
            this.read();
            if (!this.paused) {
                this.session.observe(true, this.liveBits, this.liveReadable);
                this.mode3d.observe(true, this.liveBits, this.liveReadable);
            }
            if (this.tabOpen) {
                this.tab.follow(this.live, this.saved, this.working);
                this.tabBits = this.tab.evaluate(this.working, this.axes, this.buttons);
                this.tabReadable = SwitchEvaluator.readable(this.working, this.axes, this.buttons);
            } else {
                this.tabBits = this.liveBits;
                this.tabReadable = this.liveReadable;
            }
        }

        StabMode tabMode() {
            return this.session.preview(true, this.tabBits, this.tabReadable);
        }

        boolean tabOverride() {
            return this.session.keyOverridesSwitch(true, this.tabBits, this.tabReadable);
        }

        void randomEvent(Random random, boolean gamepad) {
            int roll = random.nextInt(14);
            if (roll < 6) {
                if (gamepad) {
                    int button = random.nextInt(this.buttons.length);
                    this.buttons[button] = !this.buttons[button];
                } else {
                    this.set(4 + random.nextInt(4), BANDS[random.nextInt(3)].at);
                }
            } else if (roll == 6) {
                this.session.select(StabMode.values()[random.nextInt(3)]);
            } else if (roll == 7) {
                this.session.cycle();
            } else if (roll == 8) {
                this.mode3d.toggle();
            } else if (roll == 9 && !this.paused) {
                this.paused = true;
                this.openTab();
            } else if (roll == 10 && this.paused) {
                this.paused = false;
                this.tabOpen = random.nextBoolean();
            } else if (roll == 11) {
                this.tabOpen = !this.tabOpen;
                if (this.tabOpen) {
                    this.openTab();
                }
            }
        }

        void checkPreview(String where) {
            Shown now = new Shown(this.tabMode(), this.mode3d.preview(true, this.tabBits, this.tabReadable), this.tabOverride(),
                    this.mode3d.keyOverridesSwitch(true, this.tabBits, this.tabReadable));
            if (this.paused) {
                this.pending = now;
                return;
            }
            Shown flown = new Shown(this.session.mode(), this.mode3d.selected(), this.session.keyOverridesSwitch(true, this.liveBits, this.liveReadable),
                    this.mode3d.keyOverridesSwitch(true, this.liveBits, this.liveReadable));
            assertEquals(flown, now, where + ": Tab und Sitzung");
            Shown before = this.pending;
            this.pending = null;
            if (before != null) {
                assertEquals(flown, before, where + ": im pausierten Tab gezeigt, nach dem Schließen geflogen");
                this.pausedChecks++;
            }
        }

        private void read() {
            this.liveBits = this.live.evaluate(this.saved, this.axes, this.buttons);
            this.liveReadable = SwitchEvaluator.readable(this.saved, this.axes, this.buttons);
        }
    }

    @Test
    void previewNeverMutatesTheSelector() {
        FlightModeSelector selector = new FlightModeSelector();
        int angle = SwitchEvaluator.bit(SwitchFunction.ANGLE);
        int readable = angle | SwitchEvaluator.bit(SwitchFunction.HORIZON);
        selector.observe(true, 0, readable);
        assertEquals(StabMode.ACRO, selector.mode());
        assertEquals(StabMode.ANGLE, selector.preview(true, angle, readable));
        assertEquals(StabMode.ACRO, selector.mode());
        assertEquals(StabMode.ACRO, selector.lastSwitch());
        assertEquals(StabMode.ACRO, selector.preview(false, angle, readable), "ohne Gerät bleibt der Modus");
        selector.observe(true, angle, readable);
        assertEquals(StabMode.ACRO, selector.preview(true, 0, 0), "Belegung weg: vom Schalter gesetzter Modus fällt auf Acro");
        selector.select(StabMode.HORIZON);
        assertEquals(StabMode.HORIZON, selector.preview(true, 0, 0), "Belegung weg: Tastenwahl bleibt");
        assertEquals(StabMode.ACRO, selector.preview(true, 0, readable), "Schalter auf Acro bewegt");
    }
}
