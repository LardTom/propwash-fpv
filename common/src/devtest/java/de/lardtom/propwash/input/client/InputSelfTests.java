package de.lardtom.propwash.input.client;

import com.google.gson.Gson;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.input.ChannelMapping;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.ThrottleMode;
import de.lardtom.propwash.input.client.screen.InputLabels;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public final class InputSelfTests {
    private static final Gson GSON = new Gson();

    private InputSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("input: Funke ohne Gerät", 60, ctx -> {
            InputConfig config = InputConfig.get();
            if (ctx.tick() == 0) {
                ctx.state().put("keyboard", config.keyboardFallback);
                ctx.state().put("frames", RadioState.INSTANCE.frameCount());
                TransmitterInput.INSTANCE.setSuppressDevice(true);
                config.keyboardFallback = false;
                return StepResult.pending();
            }
            long frames = (Long) ctx.state().get("frames");
            if (RadioState.INSTANCE.frameCount() < frames + 3 && ctx.tick() < 59) {
                return StepResult.pending();
            }
            try {
                return checkDisconnected();
            } finally {
                config.keyboardFallback = (Boolean) ctx.state().get("keyboard");
                TransmitterInput.INSTANCE.setSuppressDevice(true);
            }
        });

        SelfTest.clientStep("input: Tastatur-Ersatz", 60, ctx -> {
            InputConfig config = InputConfig.get();
            if (ctx.tick() == 0) {
                ctx.state().put("keyboard", config.keyboardFallback);
                ctx.state().put("frames", RadioState.INSTANCE.frameCount());
                TransmitterInput.INSTANCE.setSuppressDevice(true);
                config.keyboardFallback = true;
                return StepResult.pending();
            }
            long frames = (Long) ctx.state().get("frames");
            if (RadioState.INSTANCE.frameCount() < frames + 3 && ctx.tick() < 59) {
                return StepResult.pending();
            }
            try {
                RadioState radio = RadioState.INSTANCE;
                if (radio.source() != RadioSource.KEYBOARD || !radio.connected()) {
                    return StepResult.fail("Tastatur-Ersatz nicht aktiv: source=" + radio.source());
                }
                if (!radio.sticks().equals(StickPositions.IDLE) || radio.isActive(SwitchFunction.ARM)) {
                    return StepResult.fail("Tastatur außerhalb des Flugs nicht in Ruhe: " + radio.sticks());
                }
                if (radio.rawAxes().length != 0 || radio.rawButtons().length != 0 || radio.deviceName().isEmpty()) {
                    return StepResult.fail("Tastatur-Rohdaten/Name falsch");
                }
                return StepResult.pass();
            } finally {
                config.keyboardFallback = (Boolean) ctx.state().get("keyboard");
                TransmitterInput.INSTANCE.setSuppressDevice(true);
            }
        });

        SelfTest.clientStep("input: Tastatur fest gewählt", 60, ctx -> {
            InputConfig config = InputConfig.get();
            if (ctx.tick() == 0) {
                ctx.state().put("keyboard", config.keyboardFallback);
                ctx.state().put("selected", config.selectedGuid);
                ctx.state().put("frames", RadioState.INSTANCE.frameCount());
                config.keyboardFallback = false;
                config.selectedGuid = InputConfig.KEYBOARD_GUID;
                TransmitterInput.INSTANCE.rescanSoon();
                return StepResult.pending();
            }
            long frames = (Long) ctx.state().get("frames");
            if ((RadioState.INSTANCE.frameCount() < frames + 3 || TransmitterInput.INSTANCE.openDevice() != null) && ctx.tick() < 59) {
                return StepResult.pending();
            }
            try {
                RadioState radio = RadioState.INSTANCE;
                DeviceInfo stillOpen = TransmitterInput.INSTANCE.openDevice();
                if (stillOpen != null) {
                    return StepResult.fail("Gerät trotz Auswahl „Tastatur“ offen: " + stillOpen.name());
                }
                return StepResult.check(radio.source() == RadioSource.KEYBOARD && radio.connected(),
                        "Tastatur fest gewählt, aber Quelle " + radio.source());
            } finally {
                config.keyboardFallback = (Boolean) ctx.state().get("keyboard");
                config.selectedGuid = (String) ctx.state().get("selected");
                TransmitterInput.INSTANCE.rescanSoon();
            }
        });

        SelfTest.clientStep("input: Gamepad-Standardbelegung", 1, ctx -> {
            DeviceProfile pad = ProfileDefaults.create("Selbsttest-Pad", true, 6);
            SwitchAssignment rescue = pad.binding(SwitchFunction.RESCUE);
            SwitchAssignment angle = pad.binding(SwitchFunction.ANGLE);
            return StepResult.check(rescue.toggle && angle.toggle && angle.startOn,
                    "Gamepad-Standard: Rescue muss umschalten, Angle muss eingeschaltet starten: " + rescue + " " + angle);
        });

        SelfTest.clientStep("input: Echtes Gerät", 40, ctx -> {
            if (ctx.tick() < 10) {
                return StepResult.pending();
            }
            RadioState radio = RadioState.INSTANCE;
            DeviceInfo open = TransmitterInput.INSTANCE.openDevice();
            ctx.log("SDL verfügbar: " + TransmitterInput.INSTANCE.available() + ", Geräte: " + TransmitterInput.INSTANCE.devices().size()
                    + ", aktiv: " + (open == null ? "-" : open.name() + " " + open.guid()) + ", Quelle: " + radio.source());
            if (open == null) {
                return StepResult.pass();
            }
            StickPositions s = radio.sticks();
            if (!radio.connected() || !s.equals(s.clamped()) || radio.deviceGuid().isEmpty()) {
                return StepResult.fail("Gerät offen, aber RadioState inkonsistent: " + radio.source() + " " + s);
            }
            return StepResult.check(InputConfig.get().profile(open.guid()) != null, "Kein Profil für " + open.guid() + " angelegt");
        });

        SelfTest.clientStep("input: Config-Roundtrip", 1, ctx -> {
            if (!InputConfig.CONFIG.isLoaded() || !Files.isRegularFile(InputConfig.CONFIG.path())) {
                return StepResult.fail("config/propwash/input.json fehlt");
            }
            JsonConfig<InputConfig> probe = JsonConfig.create("input_selftest", InputConfig.class, InputConfig::new, InputConfig::sanitize);
            try {
                InputConfig sample = sampleConfig();
                probe.set(sample);
                JsonConfig<InputConfig> reread = JsonConfig.create("input_selftest", InputConfig.class, InputConfig::new, InputConfig::sanitize);
                InputConfig loaded = reread.load();
                String expected = GSON.toJson(sample);
                String actual = GSON.toJson(loaded);
                return StepResult.check(expected.equals(actual), "Roundtrip verändert die Config: " + actual);
            } finally {
                try {
                    Files.deleteIfExists(probe.path());
                } catch (IOException e) {
                    ctx.log("input_selftest.json nicht gelöscht: " + e);
                }
            }
        });

        SelfTest.clientStep("input: Konfigurator-Tabs", 1, ctx -> {
            List<ConfigTab> tabs = ConfigTabs.create();
            boolean receiver = tabs.stream().anyMatch(t -> t instanceof ReceiverTab && ReceiverTab.ID.equals(t.id()));
            boolean modes = tabs.stream().anyMatch(t -> t instanceof ModesTab && ModesTab.ID.equals(t.id()));
            return StepResult.check(receiver && modes, "Receiver/Modes-Tab nicht registriert: " + tabs.stream().map(ConfigTab::id).toList());
        });

        SelfTest.clientStep("input: Assistent öffnen/schließen", 100, ctx -> {
            Minecraft minecraft = Minecraft.getInstance();
            Screen screen = minecraft.gui.screen();
            if (ctx.tick() == 0) {
                ctx.state().put("before", screen == null ? "" : screen.getClass().getName());
                InputClient.openReceiverWizard(minecraft);
                return StepResult.pending();
            }
            if (screen instanceof ReceiverWizardScreen wizard) {
                if (wizard.page() != ReceiverWizardScreen.Page.MODE) {
                    return StepResult.fail("Assistent startet nicht mit der Wahl des Knüppel-Modes: " + wizard.page());
                }
                if (wizard.children().isEmpty()) {
                    return StepResult.fail("Assistent ohne Bedienelemente");
                }
                ctx.state().put("seen", Boolean.TRUE);
                wizard.onClose();
                return StepResult.pending();
            }
            if (!Boolean.TRUE.equals(ctx.state().get("seen"))) {
                return ctx.tick() > 20 ? StepResult.fail("Assistent wurde nicht geöffnet") : StepResult.pending();
            }
            String now = screen == null ? "" : screen.getClass().getName();
            return StepResult.check(now.equals(ctx.state().get("before")), "Nach dem Schließen falscher Screen: " + now);
        });

        SelfTest.clientStep("input: Gerätemeldung im Propwash-Look", 80, ctx -> {
            Minecraft minecraft = Minecraft.getInstance();
            ToastManager toasts = minecraft.gui.toastManager();
            DeviceInfo device = new DeviceInfo(-1, "OpenTX Radiomaster Boxer Joystick", "0300f0f0091200004f54000000000000", false, 0x1209, 0x4F54);
            if (ctx.tick() == 0) {
                toasts.clear();
                TransmitterInput.showDeviceToast(device, true);
                return StepResult.pending();
            }
            ThemedToast toast = ThemedToast.find(toasts, TransmitterInput.TOAST_TOKEN);
            if (toast == null) {
                return StepResult.fail("Gerätemeldung fehlt im Toast-Manager");
            }
            if (ctx.tick() == 1) {
                String title = Component.translatable("propwash.input.toast.connected.radio").getString();
                String name = TransmitterInput.deviceToastMessage(device, true).getString();
                if (toast.accent() != Palette.SUCCESS || !toast.title().getString().equals(title) || toast.message() == null
                        || !toast.message().getString().equals(name) || !name.contains(InputLabels.deviceTitle(device).getString())) {
                    return StepResult.fail("Meldung „verbunden“ falsch: " + toast.title().getString() + " / " + toast.message());
                }
                TransmitterInput.showDeviceToast(device, false);
                ThemedToast again = ThemedToast.find(toasts, TransmitterInput.TOAST_TOKEN);
                if (again != toast || toast.accent() != Palette.WARNING
                        || !toast.title().getString().equals(Component.translatable("propwash.input.toast.disconnected.radio").getString())) {
                    return StepResult.fail("Trennen ersetzt die Meldung nicht");
                }
                return StepResult.pending();
            }
            if (toast.getWantedVisibility() != Toast.Visibility.SHOW) {
                return ctx.tick() < 60 ? StepResult.pending() : StepResult.fail("Gerätemeldung wird nicht angezeigt");
            }
            toasts.clear();
            return StepResult.pass();
        });

        BindingSelfTests.register();
        OnboardingSelfTests.register();
        ThrottleWizardSelfTests.register();
        WizardRemoteSelfTests.register();
        StickModeSelfTests.register();
        SwitchCommandSelfTests.register();
        MouseSelfTests.register();
        MacControllerSelfTests.register();
    }

    private static StepResult checkDisconnected() {
        RadioState radio = RadioState.INSTANCE;
        if (radio.connected() || radio.source() != RadioSource.NONE) {
            return StepResult.fail("connected() ohne Gerät: " + radio.source());
        }
        if (!radio.sticks().equals(StickPositions.IDLE)) {
            return StepResult.fail("sticks() ohne Gerät nicht IDLE: " + radio.sticks());
        }
        for (SwitchFunction function : SwitchFunction.values()) {
            if (radio.isActive(function)) {
                return StepResult.fail("isActive(" + function + ") ohne Gerät");
            }
        }
        if (radio.rawAxes().length != 0 || radio.rawButtons().length != 0 || !radio.deviceName().isEmpty()) {
            return StepResult.fail("Rohdaten/Name ohne Gerät nicht leer: " + Arrays.toString(radio.rawAxes()));
        }
        return StepResult.pass();
    }

    private static InputConfig sampleConfig() {
        InputConfig config = new InputConfig();
        config.selectedGuid = "03000000c82d00000000000000000000";
        config.keyboardFallback = false;
        config.keyboard.throttleRate = 0.75F;
        DeviceProfile radio = ProfileDefaults.radio("Radiomaster Boxer Joystick", 8);
        radio.roll.min = -0.97F;
        radio.roll.center = 0.012F;
        radio.roll.max = 0.985F;
        radio.yaw = new ChannelMapping(3, true);
        radio.setBinding(SwitchFunction.HORIZON, SwitchAssignment.axisRange(5, -0.2F, 0.2F));
        radio.setBinding(SwitchFunction.FLIP_OVER, SwitchAssignment.button(2, true));
        config.profiles.put(config.selectedGuid, radio);
        DeviceProfile pad = ProfileDefaults.gamepad("Xbox Controller");
        pad.throttleMode = ThrottleMode.INCREMENTAL;
        pad.deadband = 0.1F;
        config.profiles.put("030000005e0400000000000000000000", pad);
        config.sanitize();
        return config;
    }
}
