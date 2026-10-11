package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.sim.DroneSim;
import org.jspecify.annotations.Nullable;

public final class ArmSwitchGuard {
    private int revision;

    public void sync(int revision) {
        this.revision = revision;
    }

    public boolean check(int revision, @Nullable DroneSim sim) {
        if (revision == this.revision) {
            return false;
        }
        this.revision = revision;
        if (sim != null) {
            sim.requireArmSwitchReset();
        }
        return true;
    }
}
