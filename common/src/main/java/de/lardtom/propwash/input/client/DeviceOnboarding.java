package de.lardtom.propwash.input.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.SetupPrompts;
import de.lardtom.propwash.input.SetupStatus;
import de.lardtom.propwash.input.client.screen.DeviceSetupScreen;
import de.lardtom.propwash.input.client.screen.InputLabels;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.ui.client.PilotGuard;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class DeviceOnboarding {
    public static final DeviceOnboarding INSTANCE = new DeviceOnboarding();
    private static final long TOAST_MILLIS = 7000L;

    private final SetupPrompts prompts = new SetupPrompts();
    private List<DeviceInfo> devices = List.of();
    private @Nullable List<DeviceInfo> simulated;

    private DeviceOnboarding() {
    }

    public SetupPrompts prompts() {
        return this.prompts;
    }

    public void simulate(@Nullable List<DeviceInfo> devices) {
        this.simulated = devices == null ? null : List.copyOf(devices);
        this.prompts.clear();
        this.devices = List.of();
        if (this.simulated != null) {
            this.update(this.simulated, null, Util.getNanos());
        }
    }

    public boolean simulating() {
        return this.simulated != null;
    }

    public void onScan(List<DeviceInfo> list, @Nullable DeviceInfo open, long now) {
        if (this.simulated == null) {
            this.update(list, open, now);
        }
    }

    private void update(List<DeviceInfo> list, @Nullable DeviceInfo open, long now) {
        this.devices = List.copyOf(list);
        InputConfig config = InputConfig.get();
        List<SetupPrompts.Device> candidates = new ArrayList<>(list.size());
        for (DeviceInfo device : list) {
            candidates.add(new SetupPrompts.Device(device.guid(), eligible(device, open)));
        }
        for (String guid : this.prompts.update(candidates, config::setupStatus, now)) {
            DeviceInfo device = this.find(guid);
            Constants.LOG.info("Propwash: Einrichtung angeboten für \"{}\" | GUID {} | Status {}", device == null ? guid : device.name(), guid,
                    config.setupStatus(guid));
        }
    }

    public static boolean eligible(DeviceInfo device, @Nullable DeviceInfo open) {
        return device.radio() || device.gamepad() || open != null && open.guid().equals(device.guid());
    }

    public @Nullable DeviceInfo find(String guid) {
        for (DeviceInfo device : this.devices) {
            if (device.guid().equals(guid)) {
                return device;
            }
        }
        return null;
    }

    public boolean connected(String guid) {
        return this.find(guid) != null;
    }

    public static boolean safeMoment(Minecraft minecraft) {
        return inWorld(minecraft) && minecraft.gui.screen() == null && minecraft.gui.overlay() == null && !PilotSession.INSTANCE.isActive()
                && minecraft.player.isAlive() && minecraft.player.hurtTime == 0;
    }

    private static boolean inWorld(Minecraft minecraft) {
        return minecraft.level != null && minecraft.player != null && minecraft.gui != null;
    }

    public void tick(Minecraft minecraft) {
        long now = Util.getNanos();
        List<DeviceInfo> fake = this.simulated;
        if (fake != null) {
            this.update(fake, null, now);
        }
        SetupPrompts.Step step = this.prompts.poll(now, inWorld(minecraft), safeMoment(minecraft));
        if (step == null) {
            return;
        }
        DeviceInfo device = this.find(step.guid());
        switch (step.action()) {
            case PROMPT -> {
                if (device == null) {
                    this.prompts.withdraw(step.guid());
                    return;
                }
                Constants.LOG.info("Propwash: frage nach Einrichtung von \"{}\" ({})", device.name(), device.guid());
                minecraft.gui.setScreen(new DeviceSetupScreen(device));
            }
            case NOTIFY -> {
                if (device != null) {
                    showPendingToast(minecraft, device);
                }
            }
        }
    }

    public void answer(DeviceInfo device, SetupPrompts.Answer answer, Minecraft minecraft) {
        long now = Util.getNanos();
        Constants.LOG.info("Propwash: Einrichtung von \"{}\" ({}): {}", device.name(), device.guid(), answer);
        this.prompts.answer(device.guid(), answer, now);
        switch (answer) {
            case SET_UP -> minecraft.gui.setScreen(this.wizardFor(device.guid(), null));
            case LATER -> {
                minecraft.gui.setScreen(null);
                String key = this.prompts.waiting(device.guid()) ? "propwash.input.setup.later.message" : "propwash.input.setup.later.final";
                showToast(minecraft, Component.translatable("propwash.input.setup.later.title"), Component.translatable(key, configuratorKey()), Palette.INFO);
            }
            case NEVER -> {
                InputConfig config = InputConfig.get();
                config.neverAskFor(device.guid());
                InputConfig.commit(config);
                minecraft.gui.setScreen(null);
                showToast(minecraft, Component.translatable("propwash.input.setup.never.title"),
                        Component.translatable("propwash.input.setup.never.message", configuratorKey()), Palette.INFO);
            }
        }
    }

    public void promptClosed(DeviceInfo device) {
        this.prompts.withdraw(device.guid());
    }

    public boolean openFromKey(Minecraft minecraft) {
        String guid = this.prompts.firstWaiting();
        if (guid == null || this.find(guid) == null) {
            return false;
        }
        if (PilotGuard.prepareOpen(minecraft)) {
            this.prompts.answer(guid, SetupPrompts.Answer.SET_UP, Util.getNanos());
            minecraft.gui.setScreen(this.wizardFor(guid, minecraft.gui.screen()));
        }
        return true;
    }

    public ReceiverWizardScreen wizardFor(String guid, @Nullable Screen parent) {
        return ReceiverWizardScreen.forDevice(parent, guid, result -> {
            InputConfig.commit(result);
            TransmitterInput.INSTANCE.rescanSoon();
            boolean done = result.setupStatus(guid) == SetupStatus.COMPLETED;
            this.prompts.setupClosed(guid, done, Util.getNanos());
        }, () -> this.prompts.setupClosed(guid, false, Util.getNanos()));
    }

    private static void showPendingToast(Minecraft minecraft, DeviceInfo device) {
        Component name = InputLabels.deviceTitle(device);
        Component message;
        if (!InputKeys.RECEIVER_WIZARD.isUnbound()) {
            message = Component.translatable("propwash.input.setup.toast.key", name, InputKeys.label(InputKeys.RECEIVER_WIZARD));
        } else if (PilotSession.INSTANCE.isActive()) {
            message = Component.translatable("propwash.input.setup.toast.after_flight", name);
        } else if (minecraft.gui.screen() != null) {
            message = Component.translatable("propwash.input.setup.toast.after_menu", name);
        } else {
            message = Component.translatable("propwash.input.setup.toast.soon", name);
        }
        showToast(minecraft, title(device), message, Palette.ACCENT);
    }

    public static Component title(DeviceInfo device) {
        return Component.translatable("propwash.input.setup.title." + InputLabels.kindId(device));
    }

    private static void showToast(Minecraft minecraft, Component title, Component message, int accent) {
        if (minecraft.gui != null) {
            ThemedToast.show(minecraft.gui.toastManager(), TransmitterInput.TOAST_TOKEN, title, message, accent, TOAST_MILLIS);
        }
    }

    public static Component configuratorKey() {
        KeyMapping mapping = KeyMapping.get(KeyDefaults.CONFIGURATOR.mappingName());
        return mapping == null || mapping.isUnbound() ? Component.translatable("propwash.input.setup.no_key") : KeyNames.of(mapping);
    }
}
