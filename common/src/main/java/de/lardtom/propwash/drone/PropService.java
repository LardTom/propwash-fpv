package de.lardtom.propwash.drone;

import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;

public final class PropService {
    public static final int NONE = -1;

    private PropService() {
    }

    public enum Fit {
        SWAP_ONE,
        SWAP_ALL,
        NEED_FOUR,
        TOO_LARGE,
        TOO_SMALL,
        WRONG_MOUNT
    }

    public static boolean fitsFrame(FrameType frame, PropType prop) {
        return prop.diameterMm <= frame.maxPropMm;
    }

    public static boolean fitsFrame(PartCompatibility rule, FrameType frame, PropType prop) {
        return !rule.enforced() || fitsFrame(frame, prop);
    }

    public static Fit fit(FrameType frame, PropType installed, PropType offered, int available) {
        return fit(PartCompatibility.ENFORCED, frame, installed, offered, available);
    }

    public static Fit fit(PartCompatibility rule, FrameType frame, PropType installed, PropType offered, int available) {
        if (!fitsFrame(rule, frame, offered)) {
            return Fit.TOO_LARGE;
        }
        if (offered == installed) {
            return Fit.SWAP_ONE;
        }
        return available >= PartWear.POSITIONS ? Fit.SWAP_ALL : Fit.NEED_FOUR;
    }

    public static Fit fit(PartCompatibility rule, DroneBuild build, PropType offered, int available) {
        if (rule.enforced()) {
            if (!fitsFrame(build.frame(), offered)) {
                return Fit.TOO_LARGE;
            }
            if (offered.diameterMm < build.frame().minPropMm) {
                return Fit.TOO_SMALL;
            }
            if (!Mounts.propFits(build.motor().definition().propMount(), offered.definition().mount())) {
                return Fit.WRONG_MOUNT;
            }
        }
        return fit(PartCompatibility.LIFTED, build.frame(), build.prop(), offered, available);
    }

    public static boolean allowed(Fit fit) {
        return fit == Fit.SWAP_ONE || fit == Fit.SWAP_ALL;
    }

    public static boolean needsReplacement(float condition, float replacement) {
        return PartWear.damaged(condition) && replacement > condition;
    }

    public static int worstReplaceable(PartWear wear, float replacement) {
        int worst = NONE;
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            float condition = wear.prop(position);
            if (needsReplacement(condition, replacement) && (worst == NONE || condition < wear.prop(worst))) {
                worst = position;
            }
        }
        return worst;
    }

    public static PartWear swapOne(PartWear wear, int position, float replacement) {
        return wear.withProp(position, PartWear.quantize(replacement));
    }

    public static PartWear swapAll(PartWear wear, float replacement) {
        float[] props = wear.props();
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            props[position] = PartWear.quantize(replacement);
        }
        return wear.withProps(props);
    }
}
