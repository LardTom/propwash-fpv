package de.lardtom.propwash.server.rules;

import de.lardtom.propwash.server.rules.RuleSpec.Group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WorldRuleSpecs {
    private static final Map<String, RuleSpec> BY_NAME = new LinkedHashMap<>();

    public static final RuleSpec GRAVITY_SCALE = add(RuleSpec.percent("gravity_scale", 100, 0, 1000, 1, false, Group.PHYSICS));
    public static final RuleSpec PROP_WASH = add(RuleSpec.bool("prop_wash", true, Group.PHYSICS));
    public static final RuleSpec GROUND_EFFECT = add(RuleSpec.bool("ground_effect", true, Group.PHYSICS));
    public static final RuleSpec HARDWARE_LIMITS = add(RuleSpec.bool("hardware_limits", true, Group.PHYSICS));
    public static final RuleSpec WIND = add(RuleSpec.levels("wind", 1, 3, Group.WEATHER));
    public static final RuleSpec TURBULENCE = add(RuleSpec.bool("turbulence", true, Group.WEATHER));
    public static final RuleSpec BATTERY_SCALE = add(RuleSpec.percent("battery_scale", 100, 0, 1000, 10, true, Group.BATTERY));
    public static final RuleSpec BATTERY_SAG = add(RuleSpec.bool("battery_sag", true, Group.BATTERY));
    public static final RuleSpec CRASH_DAMAGE = add(RuleSpec.bool("crash_damage", true, Group.CRASH));
    public static final RuleSpec CRASH_DAMAGE_SCALE = add(RuleSpec.percent("crash_damage_scale", 100, 25, 300, 5, false, Group.CRASH));
    public static final RuleSpec CRASH_TOLERANCE = add(RuleSpec.percent("crash_tolerance", 100, 50, 400, 5, false, Group.CRASH));
    public static final RuleSpec CRASH_DISARM = add(RuleSpec.bool("crash_disarm", true, Group.CRASH));
    public static final RuleSpec PROP_STRIKES = add(RuleSpec.bool("prop_strikes", true, Group.CRASH));
    public static final RuleSpec INFINITE_RANGE_ANALOG = add(RuleSpec.bool("infinite_range_analog", false, Group.VIDEO));
    public static final RuleSpec INFINITE_RANGE_DIGITAL = add(RuleSpec.bool("infinite_range_digital", false, Group.VIDEO));
    public static final RuleSpec MAX_VIDEO_CHANNELS = add(RuleSpec.number("max_video_channels", 0, 0, 64, true, Group.VIDEO));
    public static final RuleSpec SIGNAL_REPEATERS = add(RuleSpec.number("signal_repeaters", 4, 0, 4, true, Group.VIDEO));
    public static final RuleSpec DRONE_HITS_ENTITIES = add(RuleSpec.bool("drone_hits_entities", true, Group.GAMEPLAY));
    public static final RuleSpec DRONES_BREAK_BLOCKS = add(RuleSpec.bool("drones_break_blocks", false, Group.GAMEPLAY));
    public static final RuleSpec BREAK_IMPACT_SCALE = add(RuleSpec.percent("break_impact_scale", 100, 0, 300, 10, true, Group.GAMEPLAY));
    public static final RuleSpec ALLOW_3D_MODE = add(RuleSpec.bool("allow_3d_mode", true, Group.GAMEPLAY));
    public static final RuleSpec PART_COMPATIBILITY = add(RuleSpec.bool("part_compatibility", true, Group.GAMEPLAY));
    public static final RuleSpec AUTONOMOUS_RESCUE = add(RuleSpec.bool("autonomous_rescue", true, Group.GAMEPLAY));
    public static final RuleSpec FREEFORM_GATE_MAX_SIZE = add(RuleSpec.number("freeform_gate_max_size", 20, 1, 64, false, Group.GAMEPLAY));
    public static final RuleSpec DRONES_GENERATE_CHUNKS = add(RuleSpec.bool("drones_generate_chunks", true, Group.GAMEPLAY));
    public static final RuleSpec SAVE_ASSISTED_LAPS = add(RuleSpec.bool("save_assisted_laps", false, Group.GAMEPLAY));
    public static final RuleSpec DRONE_TRAILS = add(RuleSpec.bool("drone_trails", false, Group.GAMEPLAY));
    public static final RuleSpec INVULNERABLE_PILOTS = add(RuleSpec.bool("invulnerable_pilots", false, Group.GAMEPLAY));

    private WorldRuleSpecs() {
    }

    private static synchronized RuleSpec add(RuleSpec spec) {
        if (BY_NAME.putIfAbsent(spec.name(), spec) != null) {
            throw new IllegalArgumentException("Doppelte Welt-Regel: " + spec.name());
        }
        return spec;
    }

    public static synchronized List<RuleSpec> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_NAME.values()));
    }

    public static synchronized Optional<RuleSpec> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    public static int order(RuleSpec spec) {
        int i = 0;
        for (RuleSpec candidate : all()) {
            if (candidate.equals(spec)) {
                return i;
            }
            i++;
        }
        return Integer.MAX_VALUE;
    }
}
