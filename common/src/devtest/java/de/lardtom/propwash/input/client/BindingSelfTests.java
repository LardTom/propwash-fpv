package de.lardtom.propwash.input.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.client.ArmSwitchGuard;
import de.lardtom.propwash.input.ArmSourceWatch;
import de.lardtom.propwash.input.ChannelMath;
import de.lardtom.propwash.input.DeviceNames;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchEvaluator;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.client.screen.InputGui;
import de.lardtom.propwash.input.client.screen.InputLabels;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.EventQueue;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.sim.SimEnvironment;
import de.lardtom.propwash.sim.SimEventType;
import de.lardtom.propwash.sim.SimTuning;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BindingSelfTests {
    private static final Gson GSON = new Gson();
    private static final String BOXER = "OpenTX Radiomaster Boxer Joystick";

    private BindingSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("input: Anzeigename EdgeTX/OpenTX", 1, ctx -> {
            DeviceInfo boxer = new DeviceInfo(1, BOXER, "0300c19b09120000544f000000020000", false,
                    DeviceNames.OPENTX_VENDOR, DeviceNames.OPENTX_PRODUCT);
            if (!"Radiomaster Boxer".equals(boxer.displayName())) {
                return StepResult.fail("Anzeigename falsch: " + boxer.displayName());
            }
            if (!boxer.edgeTxOrOpenTx() || !boxer.radio() || boxer.gamepadByDefault()) {
                return StepResult.fail("Boxer nicht als EdgeTX/OpenTX-Funke erkannt");
            }
            String label = InputLabels.deviceLabel(boxer).getString();
            if (!label.contains("Radiomaster Boxer") || !label.contains(DeviceNames.FIRMWARE_BADGE) || label.contains("OpenTX Radiomaster")) {
                return StepResult.fail("Gerätebeschriftung falsch: " + label);
            }
            DeviceInfo unnamed = new DeviceInfo(2, "USB HID", "x", false, DeviceNames.OPENTX_VENDOR, DeviceNames.OPENTX_PRODUCT);
            if (!unnamed.edgeTxOrOpenTx() || !unnamed.radio()) {
                return StepResult.fail("VID 1209 / PID 4F54 nicht als EdgeTX/OpenTX erkannt");
            }
            DeviceInfo pad = new DeviceInfo(3, "Xbox Wireless Controller", "y", true, 0x045E, 0x0B13);
            if (!"Xbox Wireless Controller".equals(pad.displayName()) || pad.edgeTxOrOpenTx() || InputLabels.deviceLabel(pad).getString().contains(DeviceNames.FIRMWARE_BADGE)) {
                return StepResult.fail("Gamepad-Name verändert oder falsches Abzeichen: " + pad.displayName());
            }
            DeviceInfo open = TransmitterInput.INSTANCE.openDevice();
            RadioSource source = RadioState.INSTANCE.source();
            if (open != null && (source == RadioSource.JOYSTICK || source == RadioSource.GAMEPAD)
                    && !open.displayName().equals(RadioState.INSTANCE.deviceName())) {
                return StepResult.fail("RadioState.deviceName() nicht bereinigt: " + RadioState.INSTANCE.deviceName());
            }
            return StepResult.pass();
        });

        SelfTest.clientStep("input: Freie Schalterbelegung (AUX/Taste)", 1, ctx -> {
            DeviceProfile profile = ProfileDefaults.radio(BOXER, 8);
            List<SwitchSources.Choice> choices = SwitchSources.choices(profile, 8, 24, profile.binding(SwitchFunction.HORIZON));
            if (choices.size() != 1 + 4 + 24) {
                return StepResult.fail("Quellen-Liste hat " + choices.size() + " Einträge statt 29");
            }
            SwitchSources.Choice aux1 = choices.get(1);
            if (aux1.source() != SwitchSource.AXIS || aux1.index() != 4) {
                return StepResult.fail("AUX 1 ist nicht Achse 5: " + aux1);
            }
            String auxLabel = InputLabels.source(aux1, profile, 8, false, 24).getString();
            if (!auxLabel.contains("AUX 1") || !auxLabel.contains("5")) {
                return StepResult.fail("AUX-Beschriftung falsch: " + auxLabel);
            }
            SwitchSources.Choice lastButton = choices.getLast();
            if (lastButton.source() != SwitchSource.BUTTON || lastButton.index() != 23) {
                return StepResult.fail("Letzte Quelle ist nicht Taste 24: " + lastButton);
            }

            SwitchAssignment horizon = SwitchSources.apply(profile.binding(SwitchFunction.HORIZON), new SwitchSources.Choice(SwitchSource.AXIS, 6),
                    SwitchFunction.HORIZON, false);
            horizon.setLowMicros(1290);
            horizon.setHighMicros(1710);
            profile.setBinding(SwitchFunction.HORIZON, horizon);
            if (horizon.lowMicros() != 1300 || horizon.highMicros() != 1700) {
                return StepResult.fail("Bereich nicht auf 25 µs gerastet: " + horizon);
            }
            SwitchAssignment rescue = SwitchSources.apply(SwitchAssignment.none(), lastButton, SwitchFunction.RESCUE, false);
            rescue.toggle = true;
            profile.setBinding(SwitchFunction.RESCUE, rescue);
            SwitchAssignment flip = SwitchSources.apply(rescue, new SwitchSources.Choice(SwitchSource.BUTTON, 2), SwitchFunction.FLIP_OVER, false);
            if (flip.toggle) {
                return StepResult.fail("Umschalten bei einer Tast-Funktion (Umdrehen) nicht abgeschaltet");
            }
            SwitchAssignment arm = SwitchAssignment.axisMicros(5, ChannelMath.RANGE_MIN_US, 1100);
            arm.invert = true;
            profile.setBinding(SwitchFunction.ARM, arm);

            SwitchEvaluator evaluator = new SwitchEvaluator();
            float[] axes = new float[8];
            boolean[] buttons = new boolean[24];
            axes[5] = ChannelMath.fromRangeMicros(1000);
            axes[6] = ChannelMath.fromRangeMicros(1500);
            int bits = evaluator.evaluate(profile, axes, buttons);
            if (!SwitchEvaluator.isSet(bits, SwitchFunction.HORIZON) || SwitchEvaluator.isSet(bits, SwitchFunction.ARM)) {
                return StepResult.fail("Bereichsauswertung falsch (Mitte/unten): " + Integer.toBinaryString(bits));
            }
            axes[5] = ChannelMath.fromRangeMicros(2000);
            axes[6] = ChannelMath.fromRangeMicros(2000);
            buttons[23] = true;
            bits = evaluator.evaluate(profile, axes, buttons);
            if (SwitchEvaluator.isSet(bits, SwitchFunction.HORIZON) || !SwitchEvaluator.isSet(bits, SwitchFunction.ARM)
                    || !SwitchEvaluator.isSet(bits, SwitchFunction.RESCUE)) {
                return StepResult.fail("Bereichs-/Tastenauswertung falsch (oben): " + Integer.toBinaryString(bits));
            }
            buttons[23] = false;
            bits = evaluator.evaluate(profile, axes, buttons);
            if (!SwitchEvaluator.isSet(bits, SwitchFunction.RESCUE)) {
                return StepResult.fail("Taste 24 im Umschalt-Modus rastet nicht ein");
            }

            profile.assignAxis(StickChannel.ROLL, 1);
            if (profile.roll.axis != 1 || profile.pitch.axis != 0) {
                return StepResult.fail("Kanal-Tausch Roll/Pitch falsch: roll=" + profile.roll.axis + " pitch=" + profile.pitch.axis);
            }
            profile.assignAxis(StickChannel.YAW, 7);
            int[] aux = profile.auxAxes(8);
            if (aux.length != 4 || aux[0] != 3 || profile.auxNumber(7, 8) != 0) {
                return StepResult.fail("AUX-Liste nach Umbelegung falsch");
            }

            InputConfig config = new InputConfig();
            config.profiles.put("boxer", profile);
            config.sanitize();
            InputConfig back = GSON.fromJson(GSON.toJson(config), InputConfig.class);
            back.sanitize();
            DeviceProfile loaded = back.profile("boxer");
            if (loaded == null || !loaded.binding(SwitchFunction.ARM).equals(arm) || !loaded.binding(SwitchFunction.HORIZON).equals(horizon)
                    || !loaded.binding(SwitchFunction.RESCUE).toggle || loaded.yaw.axis != 7) {
                return StepResult.fail("Freie Belegung übersteht den Config-Roundtrip nicht");
            }
            return StepResult.pass();
        });

        SelfTest.clientStep("input: Schalter invertieren (2/3 Stufen, Bereich, Umschalter, Arm-Sperre)", 1, ctx -> {
            Language language = Language.getInstance();
            for (String key : List.of("propwash.input.modes.invert", "propwash.input.modes.invert.tooltip_axis", "propwash.input.modes.invert.tooltip_button",
                    "propwash.input.modes.invert.tooltip_toggle", "propwash.input.modes.invert.tooltip_arm", "propwash.input.modes.header_invert",
                    "propwash.input.modes.header_invert.tooltip", "propwash.input.wizard.inverted_on", "propwash.input.wizard.inverted_off")) {
                if (!language.has(key)) {
                    return StepResult.fail("Text fehlt: " + key);
                }
            }
            DeviceProfile profile = ProfileDefaults.radio(BOXER, 8);
            SwitchAssignment arm = profile.binding(SwitchFunction.ARM);
            SwitchAssignment low = SwitchAssignment.axisMicros(5, 900, 1250);
            SwitchAssignment mid = SwitchAssignment.axisMicros(5, 1300, 1700);
            SwitchAssignment high = SwitchAssignment.axisMicros(5, 1750, 2100);
            profile.setBinding(SwitchFunction.ANGLE, low);
            profile.setBinding(SwitchFunction.HORIZON, mid);
            profile.setBinding(SwitchFunction.RESCUE, high);
            SwitchEvaluator evaluator = new SwitchEvaluator();
            float[] axes = new float[8];
            boolean[] buttons = new boolean[24];
            axes[4] = ChannelMath.fromRangeMicros(1000);
            axes[5] = ChannelMath.fromRangeMicros(1000);
            int bits = evaluator.evaluate(profile, axes, buttons);
            if (SwitchEvaluator.isSet(bits, SwitchFunction.ARM) || !SwitchEvaluator.isSet(bits, SwitchFunction.ANGLE)) {
                return StepResult.fail("Ausgangslage falsch: " + Integer.toBinaryString(bits));
            }
            int mirroredLow = 3000 - arm.activeHighMicros();
            int mirroredHigh = 3000 - arm.activeLowMicros();
            CycleButton<Boolean> toggle = InputGui.invertToggle(0, 0, InputGui.INVERT_WIDTH, 12, SwitchFunction.ARM, arm, v -> arm.invert = v);
            if (toggle.getValue()) {
                return StepResult.fail("Umschalter steht ohne Invertierung auf an");
            }
            toggle.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            if (!arm.invert || !toggle.getValue()) {
                return StepResult.fail("Umschalter setzt „Invertiert“ nicht");
            }
            if (!SwitchEvaluator.isSet(evaluator.evaluate(profile, axes, buttons), SwitchFunction.ARM)) {
                return StepResult.fail("Lampe zeigt die Invertierung nicht sofort (2-Stufen-Schalter)");
            }
            if (arm.activeLowMicros() != mirroredLow || arm.activeHighMicros() != mirroredHigh) {
                return StepResult.fail("Bereich nicht gespiegelt: " + arm.activeLowMicros() + "–" + arm.activeHighMicros());
            }
            String armTip = InputGui.invertTooltip(SwitchFunction.ARM, arm).getString();
            if (!armTip.contains(language.getOrDefault("propwash.input.modes.invert.tooltip_arm"))
                    || !armTip.contains(language.getOrDefault("propwash.input.modes.invert.tooltip_axis"))) {
                return StepResult.fail("Arm-Tooltip ohne Sicherheitshinweis: " + armTip);
            }
            if (!InputGui.invertTooltip(SwitchFunction.FLIP_OVER, SwitchAssignment.button(2, true)).getString()
                    .equals(language.getOrDefault("propwash.input.modes.invert.tooltip_button"))) {
                return StepResult.fail("Tasten-Tooltip falsch");
            }
            if (!InputGui.invertTooltip(SwitchFunction.BEEPER, SwitchAssignment.toggleButton(2)).getString()
                    .equals(language.getOrDefault("propwash.input.modes.invert.tooltip_toggle"))) {
                return StepResult.fail("Umschalt-Tooltip falsch");
            }
            low.invert = true;
            mid.invert = true;
            high.invert = true;
            SwitchFunction[] order = {SwitchFunction.RESCUE, SwitchFunction.HORIZON, SwitchFunction.ANGLE};
            int[] positions = {1000, 1500, 2000};
            for (int i = 0; i < positions.length; i++) {
                axes[5] = ChannelMath.fromRangeMicros(positions[i]);
                bits = evaluator.evaluate(profile, axes, buttons);
                for (int j = 0; j < order.length; j++) {
                    if (SwitchEvaluator.isSet(bits, order[j]) != (i == j)) {
                        return StepResult.fail("3-Stufen-Schalter nicht umgedreht bei " + positions[i] + " µs: " + Integer.toBinaryString(bits));
                    }
                }
            }

            arm.invert = false;
            axes[4] = ChannelMath.fromRangeMicros(1000);
            DroneSim sim = new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), new SimTuning(), 7L);
            sim.reset(0.0, 100.0, 0.0, 0.0);
            PilotInput input = new PilotInput();
            SimEnvironment environment = new SimEnvironment();
            de.lardtom.propwash.sim.StepResult out = new de.lardtom.propwash.sim.StepResult();
            ArmSourceWatch watch = new ArmSourceWatch();
            ArmSwitchGuard guard = new ArmSwitchGuard();
            watch.observe(RadioSource.JOYSTICK, "selftest", arm);
            guard.sync(watch.revision());
            boolean[] armSteps = new boolean[5];
            int[] armPositions = {1000, 1000, 1000, 2000, 1000};
            boolean[] invertSteps = {false, true, true, true, true};
            for (int i = 0; i < armSteps.length; i++) {
                arm.invert = invertSteps[i];
                axes[4] = ChannelMath.fromRangeMicros(armPositions[i]);
                bits = evaluator.evaluate(profile, axes, buttons);
                watch.observe(RadioSource.JOYSTICK, "selftest", arm);
                guard.check(watch.revision(), sim);
                input.armSwitch = SwitchEvaluator.isSet(bits, SwitchFunction.ARM);
                sim.step(0L, input, environment, out);
                armSteps[i] = sim.telemetry().armed;
                if (i == 1 && (!input.armSwitch || !ArmBlocker.ARM_SWITCH_NOT_RESET.isSet(sim.telemetry().armBlockers) || !armBlocked(sim))) {
                    return StepResult.fail("Invertieren auf „an“ meldet keine Arm-Sperre");
                }
            }
            if (armSteps[0] || armSteps[1] || armSteps[2] || armSteps[3] || !armSteps[4]) {
                return StepResult.fail("Arm-Sperre nach dem Invertieren falsch: " + Arrays.toString(armSteps));
            }
            return StepResult.pass();
        });

        SelfTest.clientStep("input: Wechsel der Arm-Quelle wird gemeldet", 90, ctx -> {
            InputConfig config = InputConfig.get();
            RadioState radio = RadioState.INSTANCE;
            Map<String, Object> state = ctx.state();
            if (ctx.tick() == 0) {
                state.put("keyboard", config.keyboardFallback);
                state.put("phase", 0);
                state.put("frames", radio.frameCount());
                TransmitterInput.INSTANCE.setSuppressDevice(true);
                config.keyboardFallback = true;
                return StepResult.pending();
            }
            int phase = (Integer) state.get("phase");
            if (ctx.tick() >= 88) {
                return restoreSource(config, state, StepResult.fail("Zeitüberschreitung in Phase " + phase));
            }
            if (radio.frameCount() < (Long) state.get("frames") + 3) {
                return StepResult.pending();
            }
            if (phase == 0) {
                if (radio.source() != RadioSource.KEYBOARD) {
                    return restoreSource(config, state, StepResult.fail("Tastatur-Ersatz nicht aktiv: " + radio.source()));
                }
                state.put("revision", radio.armRevision());
                state.put("phase", 1);
                state.put("frames", radio.frameCount());
                config.keyboardFallback = false;
                return StepResult.pending();
            }
            int before = (Integer) state.get("revision");
            if (phase == 1) {
                if (radio.source() != RadioSource.NONE || radio.armRevision() == before) {
                    return restoreSource(config, state, StepResult.fail("Wechsel Tastatur → keine Eingabe ändert armRevision nicht: " + radio.source()));
                }
                state.put("revision", radio.armRevision());
                state.put("phase", 2);
                state.put("frames", radio.frameCount());
                config.keyboardFallback = true;
                return StepResult.pending();
            }
            if (phase == 2) {
                if (radio.source() != RadioSource.KEYBOARD || radio.armRevision() == before) {
                    return restoreSource(config, state, StepResult.fail("Rückkehr zur Tastatur ändert armRevision nicht: " + radio.source()));
                }
                state.put("revision", radio.armRevision());
                state.put("phase", 3);
                state.put("frames", radio.frameCount());
                return StepResult.pending();
            }
            return restoreSource(config, state, StepResult.check(radio.armRevision() == before, "armRevision zählt ohne Wechsel weiter"));
        });

        SelfTest.clientStep("input: Tastenbelegung ohne Doppelungen", 1, ctx -> {
            if (KeyDefaults.UNBOUND != InputConstants.UNKNOWN.getValue()) {
                return StepResult.fail("KeyDefaults.UNBOUND passt nicht zu InputConstants.UNKNOWN");
            }
            List<KeyMapping> ours = ClientRegistrar.keyMappings();
            Map<InputConstants.Key, List<String>> byKey = new LinkedHashMap<>();
            for (KeyMapping mapping : ours) {
                if (!mapping.getDefaultKey().equals(InputConstants.UNKNOWN)) {
                    byKey.computeIfAbsent(mapping.getDefaultKey(), k -> new ArrayList<>()).add(mapping.getName());
                }
            }
            for (Map.Entry<InputConstants.Key, List<String>> e : byKey.entrySet()) {
                List<String> names = e.getValue();
                for (int i = 0; i < names.size(); i++) {
                    for (int j = i + 1; j < names.size(); j++) {
                        if (!KeyDefaults.sharesKey(names.get(i), names.get(j))) {
                            return StepResult.fail("Doppelte Standardtaste " + e.getKey().getName() + ": " + names);
                        }
                    }
                }
            }
            for (String removed : KeyDefaults.REMOVED) {
                if (KeyMapping.get(KeyDefaults.mappingName(removed)) != null) {
                    return StepResult.fail("Alte Taste noch registriert: " + removed);
                }
            }
            for (KeyDefaults.Entry entry : KeyDefaults.all()) {
                KeyMapping mapping = KeyMapping.get(entry.mappingName());
                InputConstants.Type type = entry.mouse() ? InputConstants.Type.MOUSE : InputConstants.Type.KEYBOARD;
                boolean matches = mapping != null && mapping.getDefaultKey().getType() == type
                        && mapping.getDefaultKey().getValue() == entry.key();
                if (!matches) {
                    if (entry.own()) {
                        return StepResult.fail("Taste " + entry.name() + " fehlt oder hat einen anderen Standard");
                    }
                    ctx.log("Hinweis: Taste " + entry.name() + " eines anderen Moduls weicht von KeyDefaults ab");
                }
            }
            for (SwitchFunction function : SwitchFunction.values()) {
                if (FunctionKeys.mapping(function) == null) {
                    return StepResult.fail("Keine Taste für " + function);
                }
            }
            for (KeyMapping vanilla : Minecraft.getInstance().options.keyMappings) {
                if (vanilla.getName().startsWith(KeyDefaults.mappingName("")) || vanilla.getDefaultKey().equals(InputConstants.UNKNOWN)) {
                    continue;
                }
                for (KeyMapping mapping : ours) {
                    if (!mapping.getDefaultKey().equals(vanilla.getDefaultKey()) || KeyDefaults.sharesKey(mapping.getName(), vanilla.getName())) {
                        continue;
                    }
                    return StepResult.fail("Standardtaste von " + mapping.getName() + " kollidiert mit " + vanilla.getName());
                }
            }
            return StepResult.pass();
        });

        SelfTest.clientStep("input: Modi-Tab aufbauen", 1, ctx -> {
            ModesTab tab = null;
            for (ConfigTab candidate : ConfigTabs.create()) {
                if (candidate instanceof ModesTab modes) {
                    tab = modes;
                }
            }
            if (tab == null) {
                return StepResult.fail("Modi-Tab nicht registriert");
            }
            StubHost host = new StubHost();
            tab.attach(host, 0, 0, 480, 280);
            if (host.widgets.isEmpty()) {
                return StepResult.fail("Modi-Tab ohne Bedienelemente");
            }
            tab.select(SwitchFunction.BEEPER);
            if (tab.selected() != SwitchFunction.BEEPER || host.rebuilds == 0) {
                return StepResult.fail("Auswahl einer Funktion baut den Tab nicht neu auf");
            }
            if (!tab.rowNames().contains("acro") || tab.rowNames().indexOf("acro") != tab.rowNames().indexOf("arm") + 1) {
                return StepResult.fail("Modi-Tab ohne Acro-Zeile direkt nach Arm: " + tab.rowNames());
            }
            tab.selectAcro();
            if (!tab.acroSelected() || tab.selected() != null) {
                return StepResult.fail("Acro-Zeile lässt sich nicht auswählen");
            }
            Language language = Language.getInstance();
            for (String row : tab.rowNames()) {
                for (String key : List.of("propwash.input.modes.effect." + row, "propwash.input.modes.desc." + row)) {
                    if (!language.has(key)) {
                        return StepResult.fail("Text fehlt: " + key);
                    }
                }
            }
            host.widgets.clear();
            tab.attach(host, 0, 0, 360, 200);
            return StepResult.check(!host.widgets.isEmpty(), "Modi-Tab in kleinem Fenster ohne Bedienelemente");
        });
    }

    private static StepResult restoreSource(InputConfig config, Map<String, Object> state, StepResult result) {
        config.keyboardFallback = (Boolean) state.get("keyboard");
        TransmitterInput.INSTANCE.setSuppressDevice(false);
        return result;
    }

    private static boolean armBlocked(DroneSim sim) {
        EventQueue events = sim.events();
        for (int i = 0; i < events.size(); i++) {
            if (events.type(i) == SimEventType.ARM_BLOCKED && ArmBlocker.ARM_SWITCH_NOT_RESET.isSet((int) events.value(i))) {
                return true;
            }
        }
        return false;
    }

    private static final class StubHost implements ConfigTabHost {
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private int rebuilds;

        @Override
        public <T extends AbstractWidget> T addWidget(T widget) {
            this.widgets.add(widget);
            return widget;
        }

        @Override
        public Font font() {
            return Minecraft.getInstance().font;
        }

        @Override
        public Minecraft minecraft() {
            return Minecraft.getInstance();
        }

        @Override
        public Screen screen() {
            return Minecraft.getInstance().gui.screen();
        }

        @Override
        public void rebuild() {
            this.rebuilds++;
        }

        @Override
        public void changed() {
        }
    }
}
