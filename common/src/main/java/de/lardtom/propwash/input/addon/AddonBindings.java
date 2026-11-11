package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.api.input.SwitchRegistry;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class AddonBindings {
    public static final int MAX_STORED = SwitchRegistry.MAX_SWITCHES;

    private AddonBindings() {
    }

    public static boolean addonKey(@Nullable String key) {
        return key != null && key.indexOf(':') >= 0;
    }

    public static void keepSwitches(@Nullable Map<String, SwitchAssignment> stored, Map<String, SwitchAssignment> clean) {
        if (stored == null) {
            return;
        }
        Map<String, SwitchAssignment> kept = keep(stored, key -> AddonInputs.switchSpec(AddonIds.parse(key)) != null, (key, binding) -> {
            if (binding == null) {
                return false;
            }
            binding.sanitize();
            SwitchSpec spec = AddonInputs.switchSpec(AddonIds.parse(key));
            if (spec != null && !spec.toggle()) {
                binding.toggle = false;
                binding.startOn = false;
            }
            return binding.bound();
        });
        clean.putAll(kept);
    }

    public static Map<String, AxisAssignment> keepAxes(@Nullable Map<String, AxisAssignment> stored) {
        if (stored == null) {
            return new LinkedHashMap<>();
        }
        return keep(stored, key -> AddonInputs.axisSpec(AddonIds.parse(key)) != null, (key, assignment) -> {
            if (assignment == null) {
                return false;
            }
            assignment.sanitize();
            return true;
        });
    }

    private static <T> Map<String, T> keep(Map<String, T> stored, Function<String, Boolean> registered, Check<T> check) {
        List<String> first = new ArrayList<>();
        List<String> later = new ArrayList<>();
        for (Map.Entry<String, T> entry : stored.entrySet()) {
            String key = entry.getKey();
            if (!AddonIds.valid(key) || !check.keep(key, entry.getValue())) {
                continue;
            }
            (registered.apply(key) ? first : later).add(key);
        }
        List<String> chosen = new ArrayList<>(first);
        for (String key : later) {
            if (chosen.size() >= MAX_STORED) {
                break;
            }
            chosen.add(key);
        }
        Map<String, T> out = new LinkedHashMap<>();
        for (Map.Entry<String, T> entry : stored.entrySet()) {
            if (chosen.contains(entry.getKey()) && out.size() < MAX_STORED) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    public static SwitchAssignment apply(SwitchAssignment previous, SwitchSources.Choice choice, SwitchSpec spec, boolean gamepadLayout) {
        if (choice.source() == SwitchSource.NONE) {
            return SwitchAssignment.none();
        }
        SwitchAssignment next;
        if (previous.source == choice.source()) {
            next = previous.copy();
            next.index = choice.index();
        } else {
            next = choice.source() == SwitchSource.AXIS
                    ? SwitchAssignment.axisMicros(choice.index(), SwitchSources.DEFAULT_LOW_US, SwitchSources.DEFAULT_HIGH_US)
                    : SwitchAssignment.button(choice.index(), false);
            boolean toggleByDefault = gamepadLayout && choice.source() == SwitchSource.BUTTON && spec.latchesOnGamepad();
            next.toggle = previous.bound() ? previous.toggle : toggleByDefault;
        }
        if (!spec.toggle()) {
            next.toggle = false;
            next.startOn = false;
        }
        return next;
    }

    public static void applyButtonDefaults(SwitchSpec spec, SwitchAssignment binding, boolean gamepadLayout) {
        if (binding.source == SwitchSource.BUTTON && gamepadLayout && spec.toggle() && spec.latchesOnGamepad()) {
            binding.toggle = true;
            binding.invert = false;
            binding.startOn = false;
        }
        if (!spec.toggle()) {
            binding.toggle = false;
            binding.startOn = false;
        }
    }

    public static AxisAssignment axisFor(@Nullable AxisAssignment stored) {
        return stored == null ? AxisAssignment.keys() : stored;
    }

    @FunctionalInterface
    private interface Check<T> {
        boolean keep(String key, T value);
    }
}
