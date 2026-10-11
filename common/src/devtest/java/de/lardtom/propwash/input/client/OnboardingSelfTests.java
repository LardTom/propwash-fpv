package de.lardtom.propwash.input.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.SetupPrompts;
import de.lardtom.propwash.input.SetupStatus;
import de.lardtom.propwash.input.client.screen.DeviceSetupScreen;
import de.lardtom.propwash.input.client.screen.InputLabels;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

public final class OnboardingSelfTests {
    static final String RADIO_GUID = "05000000091200004f5400005e1f0001";
    static final String PAD_GUID = "05000000045e0000130b00005e1f0002";
    static final DeviceInfo RADIO = new DeviceInfo(-71, "EdgeTX Radiomaster Pocket Joystick", RADIO_GUID, false, 0x1209, 0x4F54);
    static final DeviceInfo PAD = new DeviceInfo(-72, "Xbox Wireless Controller", PAD_GUID, true, 0x045E, 0x0B13);
    private static final int BLOCKED_TICKS = 40;
    private static final int QUIET_TICKS = 40;
    private static final int WAIT_TICKS = 120;

    private OnboardingSelfTests() {
    }

    public static void register() {
        DeviceOnboarding.INSTANCE.simulate(List.of());

        SelfTest.clientStep("input: neue Funke erkennen und einmal einrichten (simuliert)", 600, ctx -> {
            Minecraft minecraft = Minecraft.getInstance();
            Map<String, Object> state = ctx.state();
            try {
                StepResult result = run(minecraft, state, ctx.tick());
                if (result.status() != StepResult.Status.PENDING) {
                    cleanup(minecraft, state);
                    ctx.log("Ablauf: " + state.get("trail"));
                }
                return result;
            } catch (RuntimeException e) {
                cleanup(minecraft, state);
                throw e;
            }
        });
    }

    private static StepResult run(Minecraft minecraft, Map<String, Object> state, int tick) {
        String phase = (String) state.getOrDefault("phase", "begin");
        Screen screen = minecraft.gui.screen();
        int since = (Integer) state.getOrDefault("since", 0);
        switch (phase) {
            case "begin" -> {
                if (PilotSession.INSTANCE.isActive()) {
                    return StepResult.fail("Pilot-Sitzung läuft noch");
                }
                InputConfig config = InputConfig.get();
                state.put("backup", config.copy());
                config.profiles.remove(RADIO_GUID);
                config.profiles.remove(PAD_GUID);
                config.neverAsk.remove(RADIO_GUID);
                config.neverAsk.remove(PAD_GUID);
                InputConfig.commit(config);
                minecraft.gui.toastManager().clear();
                minecraft.gui.setScreen(new Blocker());
                DeviceOnboarding.INSTANCE.simulate(List.of(RADIO));
                next(state, "blocked", tick);
                return StepResult.pending();
            }
            case "blocked" -> {
                if (screen instanceof DeviceSetupScreen) {
                    return StepResult.fail("Einrichtungsfrage hat einen offenen Screen verdrängt");
                }
                if (!(screen instanceof Blocker)) {
                    return StepResult.fail("Offener Screen wurde ersetzt: " + (screen == null ? "keiner" : screen.getClass().getName()));
                }
                if (tick - since < BLOCKED_TICKS) {
                    return StepResult.pending();
                }
                ThemedToast toast = ThemedToast.find(minecraft.gui.toastManager(), TransmitterInput.TOAST_TOKEN);
                String title = DeviceOnboarding.title(RADIO).getString();
                if (toast == null || !toast.title().getString().equals(title)) {
                    return StepResult.fail("Kein Hinweis „" + title + "“ während ein Menü offen ist: " + (toast == null ? "kein Toast" : toast.title().getString()));
                }
                if (toast.message() == null || !toast.message().getString().contains(RADIO.displayName())) {
                    return StepResult.fail("Hinweis nennt das Gerät nicht: " + toast.message());
                }
                minecraft.gui.setScreen(null);
                next(state, "prompt", tick);
                return StepResult.pending();
            }
            case "prompt" -> {
                if (screen instanceof DeviceSetupScreen prompt) {
                    if (!prompt.device().guid().equals(RADIO_GUID)) {
                        return StepResult.fail("Frage für falsches Gerät: " + prompt.device().guid());
                    }
                    if (prompt.frames() == 0L) {
                        return StepResult.pending();
                    }
                    if (!prompt.getTitle().getString().equals(Component.translatable("propwash.input.setup.title.radio").getString())) {
                        return StepResult.fail("Titel der Frage falsch: " + prompt.getTitle().getString());
                    }
                    if (prompt.children().size() != 3) {
                        return StepResult.fail("Frage braucht Einrichten/Später/Nie, hat " + prompt.children().size() + " Bedienelemente");
                    }
                    if (ThemeStats.frames(DeviceSetupScreen.class) == 0L) {
                        return StepResult.fail("Frage meldet keine Theme-Frames");
                    }
                    prompt.press(SetupPrompts.Answer.SET_UP);
                    next(state, "wizard", tick);
                    return StepResult.pending();
                }
                if (screen != null) {
                    return StepResult.fail("Unerwarteter Screen statt der Frage: " + screen.getClass().getName());
                }
                return tick - since > WAIT_TICKS ? StepResult.fail("Frage erscheint nicht im Spiel ohne offenes Menü") : StepResult.pending();
            }
            case "wizard" -> {
                if (screen instanceof ReceiverWizardScreen wizard) {
                    if (!wizard.previewGuid().equals(RADIO_GUID)) {
                        return StepResult.fail("Assistent zeigt nicht das neue Gerät: " + wizard.previewGuid());
                    }
                    if (!DeviceOnboarding.INSTANCE.prompts().busy()) {
                        return StepResult.fail("Einrichtung läuft, aber die Warteschlange ist frei");
                    }
                    wizard.onClose();
                    SetupPrompts prompts = DeviceOnboarding.INSTANCE.prompts();
                    if (minecraft.gui.screen() != null) {
                        return StepResult.fail("Abbrechen führt nicht zurück ins Spiel");
                    }
                    if (!prompts.waiting(RADIO_GUID) || prompts.reminders(RADIO_GUID) != 1) {
                        return StepResult.fail("Abbrechen zählt nicht als „Später“ mit einer Erinnerung");
                    }
                    next(state, "quiet_after_cancel", tick);
                    return StepResult.pending();
                }
                return tick - since > 20 ? StepResult.fail("„Einrichten“ öffnet den Assistenten nicht") : StepResult.pending();
            }
            case "quiet_after_cancel" -> {
                if (screen instanceof DeviceSetupScreen) {
                    return StepResult.fail("Nach „Später“ sofort erneut gefragt");
                }
                if (tick - since < QUIET_TICKS) {
                    return StepResult.pending();
                }
                InputConfig config = InputConfig.get();
                config.profileOrCreate(RADIO_GUID, RADIO.name(), false, 8);
                config.markSetupDone(RADIO_GUID);
                InputConfig.commit(config);
                DeviceOnboarding.INSTANCE.simulate(List.of(RADIO, PAD));
                next(state, "pad", tick);
                return StepResult.pending();
            }
            case "pad" -> {
                if (screen instanceof DeviceSetupScreen prompt) {
                    if (!prompt.device().guid().equals(PAD_GUID)) {
                        return StepResult.fail("Eingerichtete Funke wird erneut abgefragt");
                    }
                    if (!prompt.getTitle().getString().equals(Component.translatable("propwash.input.setup.title.gamepad").getString())) {
                        return StepResult.fail("Titel für Gamepads falsch: " + prompt.getTitle().getString());
                    }
                    prompt.press(SetupPrompts.Answer.NEVER);
                    if (InputConfig.get().setupStatus(PAD_GUID) != SetupStatus.NEVER) {
                        return StepResult.fail("„Nie für dieses Gerät“ wird nicht gespeichert");
                    }
                    if (minecraft.gui.screen() != null) {
                        return StepResult.fail("„Nie“ schließt die Frage nicht");
                    }
                    next(state, "quiet", tick);
                    return StepResult.pending();
                }
                return tick - since > WAIT_TICKS ? StepResult.fail("Kein Hinweis für das neue Gamepad") : StepResult.pending();
            }
            case "quiet" -> {
                if (screen instanceof DeviceSetupScreen) {
                    return StepResult.fail("Nach „Nie“ bzw. fertiger Einrichtung trotzdem gefragt");
                }
                if (tick - since < QUIET_TICKS) {
                    return StepResult.pending();
                }
                String loaded = Component.translatable("propwash.input.toast.profile_loaded", InputLabels.deviceTitle(RADIO)).getString();
                if (!TransmitterInput.deviceToastMessage(RADIO, true).getString().equals(loaded)) {
                    return StepResult.fail("Toast für eingerichtete Funke ohne „Profil geladen“: " + TransmitterInput.deviceToastMessage(RADIO, true).getString());
                }
                if (!TransmitterInput.deviceToastTitle(RADIO, true).getString().equals(Component.translatable("propwash.input.toast.connected.radio").getString())) {
                    return StepResult.fail("Toast-Titel für Funken falsch: " + TransmitterInput.deviceToastTitle(RADIO, true).getString());
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("Unbekannte Phase " + phase);
            }
        }
    }

    private static void next(Map<String, Object> state, String phase, int tick) {
        state.put("phase", phase);
        state.put("since", tick);
        state.put("trail", state.getOrDefault("trail", "") + " → " + phase + "@" + tick);
    }

    private static void cleanup(Minecraft minecraft, Map<String, Object> state) {
        Screen screen = minecraft.gui.screen();
        if (screen instanceof Blocker || screen instanceof DeviceSetupScreen || screen instanceof ReceiverWizardScreen) {
            minecraft.gui.setScreen(null);
        }
        DeviceOnboarding.INSTANCE.simulate(List.of());
        Object backup = state.remove("backup");
        if (backup instanceof InputConfig config) {
            InputConfig.commit(config);
        }
        minecraft.gui.toastManager().clear();
        TransmitterInput.INSTANCE.rescanSoon();
    }

    private static final class Blocker extends Screen {
        private Blocker() {
            super(Component.literal("Propwash-Selbsttest"));
        }
    }
}
