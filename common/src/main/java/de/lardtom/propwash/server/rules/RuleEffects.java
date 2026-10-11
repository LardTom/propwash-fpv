package de.lardtom.propwash.server.rules;

import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.sim.CrashDamage;

public final class RuleEffects {
    private RuleEffects() {
    }

    public static boolean crashDamage(RuleValues values) {
        return values.on(WorldRuleSpecs.CRASH_DAMAGE);
    }

    public static float crashDamageStrength(RuleValues values) {
        if (!crashDamage(values)) {
            return 0.0F;
        }
        return (float) CrashDamage.clampStrength(values.get(WorldRuleSpecs.CRASH_DAMAGE_SCALE) / 100.0);
    }

    public static boolean infiniteRange(RuleValues values, VideoSystem transmitter) {
        RuleSpec rule = transmitter == VideoSystem.DIGITAL ? WorldRuleSpecs.INFINITE_RANGE_DIGITAL : WorldRuleSpecs.INFINITE_RANGE_ANALOG;
        return values.on(rule);
    }

    public static int maxVideoChannels(RuleValues values) {
        return values.get(WorldRuleSpecs.MAX_VIDEO_CHANNELS);
    }

    public static PartCompatibility partCompatibility(RuleValues values) {
        return PartCompatibility.of(values.on(WorldRuleSpecs.PART_COMPATIBILITY));
    }

    public static boolean channelsFull(RuleValues values, int otherTransmitters) {
        int max = maxVideoChannels(values);
        return max > 0 && otherTransmitters >= max;
    }
}
