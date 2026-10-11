package de.lardtom.propwash.input;

import java.util.ArrayList;
import java.util.List;

public final class SwitchSources {
    public static final int DEFAULT_LOW_US = 1700;
    public static final int DEFAULT_HIGH_US = ChannelMath.RANGE_MAX_US;

    private SwitchSources() {
    }

    public static List<Choice> choices(DeviceProfile profile, int axisCount, int buttonCount, SwitchAssignment current) {
        List<Choice> list = new ArrayList<>();
        list.add(Choice.NONE);
        for (int axis : profile.auxAxes(axisCount)) {
            list.add(new Choice(SwitchSource.AXIS, axis));
        }
        for (int button = 0; button < buttonCount; button++) {
            list.add(new Choice(SwitchSource.BUTTON, button));
        }
        Choice selected = of(current);
        if (!list.contains(selected)) {
            int at = selected.source() == SwitchSource.AXIS ? 1 + profile.auxAxes(axisCount).length : list.size();
            list.add(at, selected);
        }
        return list;
    }

    public static Choice of(SwitchAssignment binding) {
        return binding.bound() ? new Choice(binding.source, binding.index) : Choice.NONE;
    }

    public static SwitchAssignment apply(SwitchAssignment previous, Choice choice, SwitchFunction function, boolean gamepadLayout) {
        if (choice.source() == SwitchSource.NONE) {
            return SwitchAssignment.none();
        }
        SwitchAssignment next;
        if (previous.source == choice.source()) {
            next = previous.copy();
            next.index = choice.index();
        } else {
            next = choice.source() == SwitchSource.AXIS
                    ? SwitchAssignment.axisMicros(choice.index(), DEFAULT_LOW_US, DEFAULT_HIGH_US)
                    : SwitchAssignment.button(choice.index(), false);
            boolean toggleByDefault = gamepadLayout && choice.source() == SwitchSource.BUTTON && function.latchesOnGamepad();
            next.toggle = previous.bound() ? previous.toggle : toggleByDefault;
        }
        if (!function.supportsToggle()) {
            next.toggle = false;
        }
        return next;
    }

    public record Choice(SwitchSource source, int index) {
        public static final Choice NONE = new Choice(SwitchSource.NONE, -1);

        public Choice {
            source = source == null ? SwitchSource.NONE : source;
            index = source == SwitchSource.NONE ? -1 : index;
        }
    }
}
