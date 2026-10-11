package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.input.DeviceNames;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.client.DeviceInfo;
import de.lardtom.propwash.input.client.RawInput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

public final class InputLabels {
    private InputLabels() {
    }

    public static Component axis(int index, boolean gamepadLayout) {
        if (gamepadLayout && index >= 0 && index < ProfileDefaults.PAD_AXES) {
            return Component.translatable("propwash.input.pad.axis." + index);
        }
        return Component.translatable("propwash.input.axis", index + 1);
    }

    public static Component axisShort(int index, boolean gamepadLayout) {
        if (gamepadLayout && index >= 0 && index < ProfileDefaults.PAD_AXES) {
            return Component.translatable("propwash.input.pad.axis_short." + index);
        }
        return Component.translatable("propwash.input.axis_short", index + 1);
    }

    public static Component button(int index, boolean gamepadLayout, int buttonCount) {
        if (gamepadLayout && index >= 0 && index < ProfileDefaults.PAD_BUTTONS) {
            return Component.translatable("propwash.input.pad.button." + index);
        }
        if (buttonCount >= 0 && index >= buttonCount) {
            int hat = (index - buttonCount) / 4;
            int direction = (index - buttonCount) % 4;
            return Component.translatable("propwash.input.hat", hat + 1, Component.translatable("propwash.input.hat.dir." + direction));
        }
        return Component.translatable("propwash.input.button", index + 1);
    }

    public static Component source(SwitchSources.Choice choice, DeviceProfile profile, int axisCount, boolean gamepadLayout, int buttonCount) {
        return switch (choice.source()) {
            case NONE -> Component.translatable("propwash.input.binding.none");
            case BUTTON -> button(choice.index(), gamepadLayout, buttonCount);
            case AXIS -> {
                int aux = profile.auxNumber(choice.index(), axisCount);
                if (aux > 0) {
                    yield Component.translatable("propwash.input.aux_axis", aux, axis(choice.index(), gamepadLayout));
                }
                StickChannel stick = profile.stickOnAxis(choice.index());
                if (stick != null) {
                    yield Component.translatable("propwash.input.stick_axis", axis(choice.index(), gamepadLayout), Component.translatable(stick.translationKey()));
                }
                yield axis(choice.index(), gamepadLayout);
            }
        };
    }

    public static Component sourceShort(SwitchAssignment binding, DeviceProfile profile, int axisCount, boolean gamepadLayout, int buttonCount) {
        return switch (binding.source) {
            case NONE -> Component.translatable("propwash.input.binding.none");
            case BUTTON -> button(binding.index, gamepadLayout, buttonCount);
            case AXIS -> {
                int aux = profile.auxNumber(binding.index, axisCount);
                yield aux > 0 ? Component.translatable("propwash.input.aux", aux) : axisShort(binding.index, gamepadLayout);
            }
        };
    }

    public static Component binding(SwitchAssignment binding, DeviceProfile profile, int axisCount, boolean gamepadLayout, int buttonCount) {
        return binding(binding, profile, axisCount, gamepadLayout, buttonCount, true);
    }

    public static Component binding(SwitchAssignment binding, DeviceProfile profile, int axisCount, boolean gamepadLayout, int buttonCount,
                                    boolean withInvert) {
        if (!binding.bound()) {
            return Component.translatable("propwash.input.binding.none");
        }
        MutableComponent text = source(SwitchSources.of(binding), profile, axisCount, gamepadLayout, buttonCount).copy();
        if (binding.source == SwitchSource.AXIS) {
            text.append(" ").append(Component.translatable("propwash.input.binding.range", binding.activeLowMicros(), binding.activeHighMicros()));
        }
        if (withInvert && binding.invert) {
            text.append(" ").append(Component.translatable("propwash.input.binding.inverted"));
        }
        if (binding.toggle) {
            text.append(" ").append(Component.translatable("propwash.input.binding.toggled"));
        }
        return text;
    }

    public static Component bindingFor(SwitchAssignment binding, DeviceProfile profile, @Nullable RawInput raw) {
        boolean pad = raw != null && raw.usesGamepadLayout(profile);
        int axisCount = raw == null ? 0 : raw.axes(profile).length;
        return binding(binding, profile, axisCount, pad, raw == null ? -1 : raw.buttonCount());
    }

    public static String kindId(DeviceInfo device) {
        if (device.radio()) {
            return "radio";
        }
        return device.gamepad() ? "gamepad" : "joystick";
    }

    public static Component deviceKind(DeviceInfo device) {
        return Component.translatable("propwash.input.kind." + kindId(device));
    }

    public static Component badge() {
        return Component.literal(DeviceNames.FIRMWARE_BADGE);
    }

    public static Component deviceTags(DeviceInfo device) {
        if (device.edgeTxOrOpenTx()) {
            return Component.translatable("propwash.input.device.tags", deviceKind(device), badge());
        }
        return deviceKind(device);
    }

    public static Component deviceLabel(DeviceInfo device) {
        return Component.translatable("propwash.input.device.entry", device.displayName(), deviceTags(device));
    }

    public static Component deviceTitle(DeviceInfo device) {
        if (device.edgeTxOrOpenTx()) {
            return Component.translatable("propwash.input.device.badged", device.displayName(), badge());
        }
        return Component.literal(device.displayName());
    }

    public static String profileName(DeviceProfile profile, String fallback) {
        String name = DeviceNames.displayName(profile.name);
        return name.isEmpty() ? fallback : name;
    }

    public static Component source(RadioSource source) {
        return Component.translatable(source.translationKey());
    }
}
