package de.lardtom.propwash.input.addon.client;

import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.input.ChannelMath;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.addon.AddonBindings;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.platform.Services;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class AddonTestRadio {
    public static final String GAMEPAD_GUID = "propwash-api-test-gamepad";
    public static final String RADIO_GUID = "propwash-api-test-radio";
    public static final int RADIO_CHANNELS = 12;
    public static final int RADIO_BUTTONS = 16;
    private static final int RADIO_THROTTLE = 2;
    private static final int RADIO_STICKS = 4;
    private static @Nullable SimulatedRadio radio;

    private AddonTestRadio() {
    }

    public static SimulatedRadio device(boolean gamepad) {
        String guid = gamepad ? GAMEPAD_GUID : RADIO_GUID;
        String name = gamepad ? "Propwash test gamepad" : "Propwash test radio";
        DeviceProfile profile = gamepad ? ProfileDefaults.gamepad(name) : ProfileDefaults.radio(name, RADIO_CHANNELS);
        profile.setupDone = true;
        SimulatedRadio device = gamepad
                ? new SimulatedRadio(guid, name, profile, ProfileDefaults.PAD_AXES, ProfileDefaults.PAD_BUTTONS).asGamepad()
                : new SimulatedRadio(guid, name, profile, RADIO_CHANNELS, RADIO_BUTTONS);
        int axes = device.axes().length;
        for (int i = 0; i < axes; i++) {
            boolean low = gamepad ? i >= ProfileDefaults.PAD_LEFT_TRIGGER : i == RADIO_THROTTLE || i >= RADIO_STICKS;
            device.setAxis(i, low ? -1.0F : 0.0F);
        }
        return device.useSavedProfile();
    }

    public static InputConfig withDevice(InputConfig current, SimulatedRadio device) {
        InputConfig next = withoutDevices(current);
        next.profiles.put(device.guid(), device.profile().copy());
        next.sanitize();
        return next;
    }

    public static InputConfig withBinding(InputConfig current, String guid, Identifier id, SwitchAssignment binding) {
        InputConfig next = current.copy();
        DeviceProfile profile = next.profile(guid);
        if (profile != null) {
            profile.setAddonBinding(id, binding.copy());
            next.sanitize();
        }
        return next;
    }

    public static InputConfig withoutDevices(InputConfig current) {
        InputConfig next = current.copy();
        next.profiles.remove(GAMEPAD_GUID);
        next.profiles.remove(RADIO_GUID);
        return next;
    }

    public static @Nullable SwitchAssignment buttonBinding(SimulatedRadio device, InputConfig config, Identifier id, int button) {
        SwitchSpec spec = AddonInputs.switchSpec(id);
        DeviceProfile profile = config.profile(device.guid());
        if (spec == null || profile == null || button < 0 || button >= device.buttons().length) {
            return null;
        }
        RawInput raw = device.raw();
        return AddonBindings.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.BUTTON, button), spec, raw.usesGamepadLayout(profile));
    }

    public static @Nullable SwitchAssignment channelBinding(SimulatedRadio device, Identifier id, int channel, int lowMicros, int highMicros) {
        SwitchSpec spec = AddonInputs.switchSpec(id);
        if (spec == null || !switchRange(device, channel, lowMicros, highMicros)) {
            return null;
        }
        SwitchAssignment binding = SwitchAssignment.axisMicros(channel, lowMicros, highMicros);
        AddonBindings.applyButtonDefaults(spec, binding, false);
        return binding;
    }

    public static boolean validAction(int number) {
        return number >= 1 && number <= SwitchFunction.ACCESSORY_COUNT;
    }

    public static @Nullable SwitchAssignment actionButtonBinding(SimulatedRadio device, InputConfig config, int number, int button) {
        DeviceProfile profile = config.profile(device.guid());
        if (!validAction(number) || profile == null || button < 0 || button >= device.buttons().length) {
            return null;
        }
        return SwitchSources.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.BUTTON, button), SwitchFunction.accessory(number),
                device.raw().usesGamepadLayout(profile));
    }

    public static @Nullable SwitchAssignment actionChannelBinding(SimulatedRadio device, int number, int channel, int lowMicros, int highMicros) {
        if (!validAction(number) || !switchRange(device, channel, lowMicros, highMicros)) {
            return null;
        }
        return SwitchAssignment.axisMicros(channel, lowMicros, highMicros);
    }

    public static InputConfig withActionBinding(InputConfig current, String guid, int number, SwitchAssignment binding) {
        InputConfig next = current.copy();
        DeviceProfile profile = next.profile(guid);
        if (profile != null && validAction(number)) {
            profile.setBinding(SwitchFunction.accessory(number), binding.copy());
            next.sanitize();
        }
        return next;
    }

    private static boolean switchRange(SimulatedRadio device, int channel, int lowMicros, int highMicros) {
        return auxChannel(device, channel) && lowMicros >= ChannelMath.RANGE_MIN_US && highMicros <= ChannelMath.RANGE_MAX_US && lowMicros < highMicros;
    }

    private static boolean auxChannel(SimulatedRadio device, int channel) {
        int axes = device.axes().length;
        if (channel < 0 || channel >= axes) {
            return false;
        }
        for (int aux : device.profile().auxAxes(axes)) {
            if (aux == channel) {
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean start(boolean gamepad) {
        if (!Services.PLATFORM.isDevelopmentEnvironment()) {
            return false;
        }
        stop();
        SimulatedRadio device = device(gamepad);
        InputConfig.commit(withDevice(InputConfig.get(), device));
        TransmitterInput.INSTANCE.simulate(device);
        radio = device;
        return true;
    }

    public static synchronized void stop() {
        SimulatedRadio device = radio;
        radio = null;
        if (device != null) {
            RawInput raw = TransmitterInput.INSTANCE.simulating() ? TransmitterInput.INSTANCE.raw() : null;
            if (raw != null && device.guid().equals(raw.device().guid())) {
                TransmitterInput.INSTANCE.simulate(null);
            }
        }
        InputConfig current = InputConfig.get();
        if (current.profile(GAMEPAD_GUID) != null || current.profile(RADIO_GUID) != null) {
            InputConfig.commit(withoutDevices(current));
        }
    }

    public static synchronized @Nullable SimulatedRadio active() {
        return radio;
    }

    public static synchronized boolean axis(int index, float value) {
        SimulatedRadio device = radio;
        if (device == null || index < 0 || index >= device.axes().length || Float.isNaN(value)) {
            return false;
        }
        device.setAxis(index, Math.max(-1.0F, Math.min(1.0F, value)));
        return true;
    }

    public static synchronized boolean button(int index, boolean down) {
        SimulatedRadio device = radio;
        if (device == null || index < 0 || index >= device.buttons().length) {
            return false;
        }
        device.setButton(index, down);
        return true;
    }

    public static synchronized boolean bindButton(Identifier id, int button) {
        SimulatedRadio device = radio;
        SwitchAssignment binding = device == null ? null : buttonBinding(device, InputConfig.get(), id, button);
        return binding != null && bind(device, id, binding);
    }

    public static synchronized boolean bindChannel(Identifier id, int channel, int lowMicros, int highMicros) {
        SimulatedRadio device = radio;
        SwitchAssignment binding = device == null ? null : channelBinding(device, id, channel, lowMicros, highMicros);
        return binding != null && bind(device, id, binding);
    }

    public static synchronized boolean bindActionButton(int number, int button) {
        SimulatedRadio device = radio;
        SwitchAssignment binding = device == null ? null : actionButtonBinding(device, InputConfig.get(), number, button);
        return binding != null && bindAction(device, number, binding);
    }

    public static synchronized boolean bindActionChannel(int number, int channel, int lowMicros, int highMicros) {
        SimulatedRadio device = radio;
        SwitchAssignment binding = device == null ? null : actionChannelBinding(device, number, channel, lowMicros, highMicros);
        return binding != null && bindAction(device, number, binding);
    }

    private static boolean bindAction(SimulatedRadio device, int number, SwitchAssignment binding) {
        InputConfig next = withActionBinding(InputConfig.get(), device.guid(), number, binding);
        DeviceProfile saved = next.profile(device.guid());
        if (saved == null || !saved.binding(SwitchFunction.accessory(number)).bound()) {
            return false;
        }
        InputConfig.commit(next);
        return true;
    }

    private static boolean bind(SimulatedRadio device, Identifier id, SwitchAssignment binding) {
        InputConfig next = withBinding(InputConfig.get(), device.guid(), id, binding);
        DeviceProfile saved = next.profile(device.guid());
        if (saved == null || !saved.addonBinding(id).bound()) {
            return false;
        }
        InputConfig.commit(next);
        return true;
    }
}
