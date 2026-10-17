package de.lardtom.propwash.accessory;

import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.CargoMass;
import de.lardtom.propwash.interact.GripperSpec;

import java.util.Map;

public final class AccessoryPayload {
    public static final double MAX_PAYLOAD_GRAMS = 2000.0;

    private AccessoryPayload() {
    }

    public static DroneBuild withPayload(DroneBuild build, AccessoryContents contents) {
        if (contents == null || contents.isEmpty()) {
            return build;
        }
        DroneBuild result = build;
        for (Map.Entry<AccessoryMount, AccessoryType> entry : build.accessories().byMount().entrySet()) {
            AccessoryType type = entry.getValue();
            if (type.isGripper()) {
                continue;
            }
            AccessoryType base = type.unladen();
            double grams = AccessoryBehaviors.INSTANCE.payloadGrams(base, contents.at(entry.getKey()));
            AccessoryType wanted = base.laden(GripperSpec.massStep(grams));
            if (wanted != type) {
                result = result.withAccessory(wanted);
            }
        }
        return result;
    }

    public static DroneBuild unladen(DroneBuild build) {
        DroneBuild result = build;
        for (AccessoryType type : build.accessories().byMount().values()) {
            AccessoryType base = type.unladen();
            if (base != type) {
                result = result.withAccessory(base);
            }
        }
        return result;
    }

    public static DroneBuild flightBuild(QuadcopterEntity drone) {
        return withPayload(CargoMass.withPayload(drone.build(), drone.cargoGrams()), drone.accessoryContents());
    }

    public static double payloadGrams(QuadcopterEntity drone) {
        AccessoryContents contents = drone.accessoryContents();
        if (contents.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map.Entry<AccessoryMount, AccessoryType> entry : drone.build().accessories().byMount().entrySet()) {
            if (!entry.getValue().isGripper()) {
                sum += AccessoryBehaviors.INSTANCE.payloadGrams(entry.getValue().unladen(), contents.at(entry.getKey()));
            }
        }
        return sum;
    }
}
