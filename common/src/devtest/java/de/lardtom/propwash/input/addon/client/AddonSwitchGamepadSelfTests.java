package de.lardtom.propwash.input.addon.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleSwitches;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.InputLabels;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AddonSwitchGamepadSelfTests {
    static final String PAD_GUID = "0300a1a1addon17pad000000000000000";
    static final String PLAIN_GUID = "0300a1a1addon17plain0000000000000";
    static final int PAD_GHOST = ProfileDefaults.PAD_SOUTH;
    private static final int LAST = ProfileDefaults.PAD_BUTTONS - 1;
    private static final int SHIFT = 1;
    private static final int WAIT = 6;
    private static final String PHASE = "gamepad_phase";
    private static final String MARK = "gamepad_mark";
    private static final String RUN = "gamepad_run";
    private static final String STATES = "gamepad_states";
    private static final String RESTORE = "gamepad_restore";
    private static final String NOTES = "gamepad_notes";
    private static @Nullable SimulatedRadio device;

    private AddonSwitchGamepadSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(AddonSwitchSelfTests.GROUP + "Gamepad: „Quelle“ und „Schalter umlegen“ auf einem Gamepad-Knopf lassen „Geist“ einrasten, "
                + "an einer Funke ohne Gamepad-Belegung nicht", 1200, AddonSwitchGamepadSelfTests::step);
    }

    private static boolean exampleLoaded() {
        return JoinTestEnv.exampleAddon() && AddonInputs.switchSpec(ExampleSwitches.GHOST) != null;
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static boolean pad(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(RUN, 0) == 0;
    }

    private static String guid(boolean pad) {
        return pad ? PAD_GUID : PLAIN_GUID;
    }

    private static String what(boolean pad) {
        return pad ? "Gamepad" : "Funke ohne Gamepad-Belegung";
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, k -> new ArrayList<String>());
    }

    @SuppressWarnings("unchecked")
    private static List<Boolean> states(SelfTest.StepContext ctx) {
        return (List<Boolean>) ctx.state().computeIfAbsent(STATES, k -> new ArrayList<Boolean>());
    }

    private static void install(boolean pad) {
        DeviceProfile profile = ProfileDefaults.gamepad(what(pad));
        profile.setupDone = true;
        InputConfig next = InputConfig.get().copy();
        next.profiles.put(guid(pad), profile);
        next.sanitize();
        InputConfig.commit(next);
        SimulatedRadio simulated = new SimulatedRadio(guid(pad), what(pad), profile, ProfileDefaults.PAD_AXES, ProfileDefaults.PAD_BUTTONS).useSavedProfile();
        if (pad) {
            simulated.asGamepad();
        }
        for (int i = 0; i < ProfileDefaults.PAD_AXES; i++) {
            simulated.setAxis(i, i >= ProfileDefaults.PAD_LEFT_TRIGGER ? -1.0F : 0.0F);
        }
        TransmitterInput.INSTANCE.simulate(simulated);
        device = simulated;
    }

    private static void uninstall() {
        if (device != null) {
            TransmitterInput.INSTANCE.simulate(null);
            device = null;
        }
        InputConfig next = InputConfig.get().copy();
        boolean removed = next.profiles.remove(PAD_GUID) != null;
        removed |= next.profiles.remove(PLAIN_GUID) != null;
        if (removed) {
            InputConfig.commit(next);
        }
    }

    private static boolean radioActive() {
        RawInput raw = TransmitterInput.INSTANCE.simulating() ? TransmitterInput.INSTANCE.raw() : null;
        return raw != null && AddonSwitchSelfTests.RADIO_GUID.equals(raw.device().guid());
    }

    private static StepResult finish(SelfTest.StepContext ctx, StepResult result) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen configurator) {
            configurator.discardAndClose();
        }
        uninstall();
        if (Boolean.TRUE.equals(ctx.state().get(RESTORE))) {
            AddonSwitchSelfTests.installRadio(AddonSwitchSelfTests.radioProfile());
        }
        if (result.status() == StepResult.Status.PASS) {
            notes(ctx).forEach(ctx::log);
        }
        return result;
    }

    private static @Nullable ConfiguratorScreen configurator(Minecraft mc) {
        return mc.gui.screen() instanceof ConfiguratorScreen configurator && configurator.activeTab() instanceof ModesTab ? configurator : null;
    }

    private static @Nullable CycleButton<?> sourceButton(ConfiguratorScreen configurator) {
        for (GuiEventListener child : configurator.children()) {
            if (child instanceof CycleButton<?> cycle && cycle.getValue() instanceof SwitchSources.Choice) {
                return cycle;
            }
        }
        return null;
    }

    private static @Nullable Boolean behavior(ConfiguratorScreen configurator) {
        String label = Component.translatable("propwash.input.binding.mode").getString();
        for (GuiEventListener child : configurator.children()) {
            if (child instanceof CycleButton<?> cycle && cycle.getValue() instanceof Boolean value && cycle.getMessage().getString().startsWith(label)) {
                return value;
            }
        }
        return null;
    }

    private static boolean press(ConfiguratorScreen configurator, String message) {
        for (GuiEventListener child : configurator.children()) {
            if (child instanceof Button button && button.active && message.equals(button.getMessage().getString())) {
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                return true;
            }
        }
        return false;
    }

    private static @Nullable Boolean shownActive(Minecraft mc, ConfiguratorScreen configurator) {
        String active = Component.translatable("propwash.input.modes.state_active").getString();
        String inactive = Component.translatable("propwash.input.modes.state_inactive").getString();
        for (LayoutAudit.Text text : LayoutProbe.capture(mc, configurator).texts()) {
            if (active.equals(text.value())) {
                return true;
            }
            if (inactive.equals(text.value())) {
                return false;
            }
        }
        return null;
    }

    private static String mode(@Nullable Boolean toggle) {
        if (toggle == null) {
            return "ohne Verhalten";
        }
        return Component.translatable(toggle ? "propwash.input.binding.mode.toggle" : "propwash.input.binding.mode.hold").getString();
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            ctx.log("Beispiel-Add-on nicht geladen – übersprungen");
            return StepResult.pass();
        }
        try {
            return run(ctx);
        } catch (RuntimeException e) {
            return finish(ctx, StepResult.fail("Ausnahme: " + e));
        }
    }

    private static StepResult run(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        boolean pad = pad(ctx);
        String who = what(pad);
        switch (phase(ctx)) {
            case 0 -> {
                if (pad) {
                    ctx.state().put(RESTORE, radioActive());
                }
                install(pad);
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                RawInput raw = TransmitterInput.INSTANCE.raw();
                DeviceProfile saved = InputConfig.get().profile(guid(pad));
                if (raw == null || saved == null || !guid(pad).equals(raw.device().guid())) {
                    return since(ctx) > 60 ? finish(ctx, StepResult.fail(who + ": simulierte Funke kommt nicht an")) : StepResult.pending();
                }
                if (raw.usesGamepadLayout(saved) != pad) {
                    return finish(ctx, StepResult.fail(who + " mit Gamepad-Profil (gamepadLayout " + saved.gamepadLayout + "): DeviceInfo.gamepad "
                            + raw.device().gamepad() + ", Gamepad-Knöpfe " + raw.gamepadCapable() + ", usesGamepadLayout " + raw.usesGamepadLayout(saved)
                            + " statt " + pad));
                }
                ConfigTabs.open(null, ModesTab.ID);
                next(ctx);
                return StepResult.pending();
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof ModesTab modes)) {
                    return since(ctx) > 60 ? finish(ctx, StepResult.fail("Tab „Modi“ öffnet nicht")) : StepResult.pending();
                }
                modes.showAddons();
                if (!modes.addonsShown()) {
                    return finish(ctx, StepResult.fail("Seite „Add-ons“ lässt sich nicht öffnen"));
                }
                modes.addons().select(ExampleSwitches.GHOST);
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (since(ctx) < WAIT) {
                    return StepResult.pending();
                }
                ConfiguratorScreen configurator = configurator(mc);
                CycleButton<?> source = configurator == null ? null : sourceButton(configurator);
                if (source == null) {
                    return finish(ctx, StepResult.fail(who + ": Auswahl „Quelle“ für „Geist“ fehlt"));
                }
                if (!SwitchSources.Choice.NONE.equals(source.getValue())) {
                    return finish(ctx, StepResult.fail(who + ": „Geist“ ist vor der Auswahl schon belegt: " + source.getValue()));
                }
                source.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, SHIFT));
                next(ctx);
                return StepResult.pending();
            }
            case 4 -> {
                if (since(ctx) < 3) {
                    return StepResult.pending();
                }
                ConfiguratorScreen configurator = configurator(mc);
                if (configurator == null) {
                    return finish(ctx, StepResult.fail("Konfigurator geschlossen"));
                }
                CycleButton<?> source = sourceButton(configurator);
                SwitchSources.Choice wanted = new SwitchSources.Choice(SwitchSource.BUTTON, LAST);
                String label = InputLabels.button(LAST, pad, ProfileDefaults.PAD_BUTTONS).getString();
                if (source == null || !wanted.equals(source.getValue())) {
                    return finish(ctx, StepResult.fail(who + ": „Quelle“ rückwärts ergibt " + (source == null ? "keine Auswahl" : source.getValue())
                            + " statt " + wanted));
                }
                if (!source.getMessage().getString().contains(label)) {
                    return finish(ctx, StepResult.fail(who + ": „Quelle“ zeigt „" + source.getMessage().getString() + "“ statt „" + label + "“"));
                }
                Boolean toggle = behavior(configurator);
                if (toggle == null || toggle != pad) {
                    return finish(ctx, StepResult.fail(who + ": „Quelle“ " + label + " ergibt Verhalten „" + mode(toggle) + "“ statt „" + mode(pad) + "“"));
                }
                notes(ctx).add(who + ": „Quelle“ " + label + " → " + mode(toggle));
                if (!press(configurator, "×")) {
                    return finish(ctx, StepResult.fail(who + ": Knopf „×“ fehlt"));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                if (since(ctx) < 3) {
                    return StepResult.pending();
                }
                ConfiguratorScreen configurator = configurator(mc);
                if (configurator == null || !(configurator.activeTab() instanceof ModesTab modes)) {
                    return finish(ctx, StepResult.fail("Konfigurator geschlossen"));
                }
                CycleButton<?> source = sourceButton(configurator);
                if (source == null || !SwitchSources.Choice.NONE.equals(source.getValue())) {
                    return finish(ctx, StepResult.fail(who + ": „×“ löscht die Belegung nicht: " + (source == null ? "keine Auswahl" : source.getValue())));
                }
                String assign = Component.translatable("propwash.input.modes.assign").getString();
                if (!press(configurator, assign) || !modes.addons().listening()) {
                    return finish(ctx, StepResult.fail(who + ": Knopf „" + assign + "“ fehlt oder startet das Erkennen nicht"));
                }
                next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                if (since(ctx) < WAIT) {
                    return StepResult.pending();
                }
                if (device == null) {
                    return finish(ctx, StepResult.fail("keine simulierte Funke"));
                }
                device.setButton(PAD_GHOST, true);
                next(ctx);
                return StepResult.pending();
            }
            case 7 -> {
                ConfiguratorScreen configurator = configurator(mc);
                if (configurator == null || !(configurator.activeTab() instanceof ModesTab modes) || device == null) {
                    return finish(ctx, StepResult.fail("Konfigurator geschlossen"));
                }
                if (modes.addons().listening()) {
                    return since(ctx) > 100 ? finish(ctx, StepResult.fail(who + ": Knopf " + (PAD_GHOST + 1) + " nicht erkannt")) : StepResult.pending();
                }
                if (since(ctx) < 3) {
                    return StepResult.pending();
                }
                CycleButton<?> source = sourceButton(configurator);
                SwitchSources.Choice wanted = new SwitchSources.Choice(SwitchSource.BUTTON, PAD_GHOST);
                String label = InputLabels.button(PAD_GHOST, pad, ProfileDefaults.PAD_BUTTONS).getString();
                if (source == null || !wanted.equals(source.getValue()) || !source.getMessage().getString().contains(label)) {
                    return finish(ctx, StepResult.fail(who + ": „Schalter umlegen“ erkennt " + (source == null ? "nichts" : source.getMessage().getString())
                            + " statt „" + label + "“"));
                }
                Boolean toggle = behavior(configurator);
                if (toggle == null || toggle != pad) {
                    return finish(ctx, StepResult.fail(who + ": „Schalter umlegen“ auf " + label + " ergibt Verhalten „" + mode(toggle) + "“ statt „"
                            + mode(pad) + "“"));
                }
                notes(ctx).add(who + ": „Schalter umlegen“ erkennt " + label + " → " + mode(toggle));
                device.setButton(PAD_GHOST, false);
                states(ctx).clear();
                next(ctx);
                return StepResult.pending();
            }
            case 8, 9, 10, 11 -> {
                if (since(ctx) < WAIT) {
                    return StepResult.pending();
                }
                ConfiguratorScreen configurator = configurator(mc);
                if (configurator == null || device == null) {
                    return finish(ctx, StepResult.fail("Konfigurator geschlossen"));
                }
                Boolean shown = shownActive(mc, configurator);
                if (shown == null) {
                    return finish(ctx, StepResult.fail(who + ": Seite „Add-ons“ zeigt keinen Zustand für „Geist“"));
                }
                states(ctx).add(shown);
                device.setButton(PAD_GHOST, phase(ctx) % 2 == 0);
                next(ctx);
                return StepResult.pending();
            }
            case 12 -> {
                if (since(ctx) < WAIT) {
                    return StepResult.pending();
                }
                ConfiguratorScreen configurator = configurator(mc);
                if (configurator == null) {
                    return finish(ctx, StepResult.fail("Konfigurator geschlossen"));
                }
                Boolean shown = shownActive(mc, configurator);
                if (shown == null) {
                    return finish(ctx, StepResult.fail(who + ": Seite „Add-ons“ zeigt keinen Zustand für „Geist“"));
                }
                List<Boolean> states = states(ctx);
                states.add(shown);
                boolean[] s = new boolean[states.size()];
                for (int i = 0; i < s.length; i++) {
                    s[i] = states.get(i);
                }
                boolean ok = s.length == 5 && (pad
                        ? s[1] != s[0] && s[2] == s[1] && s[3] == s[0] && s[4] == s[0]
                        : !s[0] && s[1] && !s[2] && s[3] && !s[4]);
                String seen = "Zustände los/gedrückt/los/gedrückt/los " + states;
                if (!ok) {
                    return finish(ctx, StepResult.fail(who + ": " + (pad ? "„Geist“ rastet nicht ein, " : "„Geist“ wirkt nicht nur gehalten, ") + seen));
                }
                notes(ctx).add(who + ": " + (pad ? "rastet ein, " : "wirkt nur gehalten, ") + seen);
                configurator.saveAndClose();
                DeviceProfile saved = InputConfig.get().profile(guid(pad));
                SwitchAssignment binding = saved == null ? SwitchAssignment.none() : saved.addonBinding(ExampleSwitches.GHOST);
                if (binding.source != SwitchSource.BUTTON || binding.index != PAD_GHOST || binding.toggle != pad || binding.invert) {
                    return finish(ctx, StepResult.fail(who + ": gespeichert " + binding + " statt Knopf " + (PAD_GHOST + 1) + " „" + mode(pad) + "“"));
                }
                notes(ctx).add(who + ": gespeichert " + binding);
                if (!pad) {
                    return finish(ctx, StepResult.pass());
                }
                uninstall();
                ctx.state().put(RUN, 1);
                ctx.state().put(PHASE, 0);
                ctx.state().put(MARK, ctx.tick());
                return StepResult.pending();
            }
            default -> {
                return finish(ctx, StepResult.fail("Phase " + phase(ctx)));
            }
        }
    }
}
