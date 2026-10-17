package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class EulerPath {
    public static final double GOOD_ENOUGH_DEGREES = 0.5;
    public static final double STEP_DEGREES = 0.5;
    public static final double MAX_SHIFT_DEGREES = 90.0;
    public static final double KEYFRAME_LIMIT_DEGREES = 2.0;
    public static final double TWIST_DEGREES = 45.0;
    private static final double[] PROBES = {0.25, 0.5, 0.75};

    private EulerPath() {
    }

    public static List<CameraAngles> continuous(List<? extends Quaterniondc> rotations) {
        List<CameraAngles> out = new ArrayList<>(rotations.size());
        CameraAngles previous = null;
        Quaterniondc previousTrue = null;
        for (Quaterniondc rotation : rotations) {
            CameraAngles chosen = next(previous, previousTrue, rotation);
            out.add(chosen);
            previous = chosen;
            previousTrue = rotation;
        }
        return out;
    }

    public static CameraAngles next(@Nullable CameraAngles previous, @Nullable Quaterniondc previousTrue, Quaterniondc rotation) {
        CameraAngles canonical = CameraAngles.of(rotation);
        return previous == null || previousTrue == null ? canonical : choose(previous, previousTrue, canonical, rotation);
    }

    public static List<CameraAngles> canonical(List<? extends Quaterniondc> rotations) {
        List<CameraAngles> out = new ArrayList<>(rotations.size());
        for (Quaterniondc rotation : rotations) {
            out.add(CameraAngles.of(rotation));
        }
        return out;
    }

    static CameraAngles choose(CameraAngles from, Quaterniondc fromTrue, CameraAngles canonical, Quaterniondc target) {
        double canonicalCost = cost(from, fromTrue, canonical, target);
        if (canonicalCost <= GOOD_ENOUGH_DEGREES) {
            return canonical;
        }
        Choice choice = search(from, fromTrue, canonical, target, new Choice(canonical, canonicalCost), KEYFRAME_LIMIT_DEGREES);
        if (choice.cost > TWIST_DEGREES) {
            choice = search(from, fromTrue, canonical, target, choice, 180.0);
        }
        return choice.angles;
    }

    private static Choice search(CameraAngles from, Quaterniondc fromTrue, CameraAngles canonical, Quaterniondc target, Choice start,
                                 double keyframeLimit) {
        double rollSign = canonical.pitch() >= 0.0 ? 1.0 : -1.0;
        Choice best = start;
        int steps = (int) Math.round(MAX_SHIFT_DEGREES / STEP_DEGREES);
        for (int step = 1; step <= steps; step++) {
            for (int direction = -1; direction <= 1; direction += 2) {
                double shift = direction * step * STEP_DEGREES;
                CameraAngles candidate = canonical.shifted(shift, rollSign * shift);
                if (CameraAngles.degreesBetween(candidate.rotation(), target) > keyframeLimit) {
                    continue;
                }
                double c = cost(from, fromTrue, candidate, target);
                if (c < best.cost - 1.0E-9) {
                    best = new Choice(candidate, c);
                }
            }
        }
        return best;
    }

    private record Choice(CameraAngles angles, double cost) {
    }

    public static double cost(CameraAngles from, Quaterniondc fromTrue, CameraAngles to, Quaterniondc target) {
        double worst = CameraAngles.degreesBetween(to.rotation(), target);
        Quaterniond truth = new Quaterniond();
        for (double t : PROBES) {
            fromTrue.slerp(target, t, truth);
            worst = Math.max(worst, CameraAngles.degreesBetween(from.lerpShortest(to, t).rotation(), truth));
        }
        return worst;
    }

    public static double worstInterpolationError(List<? extends Quaterniondc> rotations, List<CameraAngles> angles, int substeps) {
        double worst = 0.0;
        Quaterniond truth = new Quaterniond();
        for (int i = 0; i < angles.size(); i++) {
            worst = Math.max(worst, CameraAngles.degreesBetween(angles.get(i).rotation(), rotations.get(i)));
            if (i + 1 >= angles.size()) {
                continue;
            }
            for (int s = 1; s < substeps; s++) {
                double t = s / (double) substeps;
                rotations.get(i).slerp(rotations.get(i + 1), t, truth);
                worst = Math.max(worst, CameraAngles.degreesBetween(angles.get(i).lerpShortest(angles.get(i + 1), t).rotation(), truth));
            }
        }
        return worst;
    }
}
