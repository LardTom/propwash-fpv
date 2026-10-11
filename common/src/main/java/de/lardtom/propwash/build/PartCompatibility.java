package de.lardtom.propwash.build;

import java.util.ArrayList;
import java.util.List;

public enum PartCompatibility {
    ENFORCED,
    LIFTED;

    public static PartCompatibility of(boolean enforced) {
        return enforced ? ENFORCED : LIFTED;
    }

    public boolean enforced() {
        return this == ENFORCED;
    }

    public boolean blocks(DroneBuild.Issue issue) {
        return this == ENFORCED || !issue.compatibility();
    }

    public List<DroneBuild.Issue> blocking(List<DroneBuild.Issue> issues) {
        if (this == ENFORCED) {
            return issues;
        }
        List<DroneBuild.Issue> out = new ArrayList<>(issues.size());
        for (DroneBuild.Issue issue : issues) {
            if (this.blocks(issue)) {
                out.add(issue);
            }
        }
        return out;
    }

    public List<DroneBuild.Issue> lifted(List<DroneBuild.Issue> issues) {
        if (this == ENFORCED) {
            return List.of();
        }
        List<DroneBuild.Issue> out = new ArrayList<>(issues.size());
        for (DroneBuild.Issue issue : issues) {
            if (!this.blocks(issue)) {
                out.add(issue);
            }
        }
        return out;
    }

    public boolean batteryFits(FrameType frame, BatteryType battery) {
        return this == LIFTED || BatteryFit.allowed(BatteryFit.check(frame, battery));
    }
}
