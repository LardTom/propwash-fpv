package de.lardtom.propwash.accessory;

import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.CargoMass;
import de.lardtom.propwash.interact.GripperSpec;

import java.util.Map;

public final class AccessoryPayload {
    public static final double MAX_LADEN_GRAMS = 20000.0;

    private AccessoryPayload() {
    }

    public static double limitGrams(AccessoryType type) {
        return Math.max(0.0, MAX_LADEN_GRAMS - type.unladen().massGrams());
    }

    public static double flightGrams(AccessoryType type, double grams) {
        if (!(grams > 0.0)) {
            return 0.0;
        }
        return Math.min(grams, limitGrams(type));
    }

    public static boolean overloaded(AccessoryType type, double grams) {
        return grams > limitGrams(type);
    }

    public static long massStep(AccessoryType type, double grams) {
        long wanted = GripperSpec.massStep(flightGrams(type, grams));
        long most = (long) (Math.floor(limitGrams(type) / GripperSpec.MASS_STEP_GRAMS) * GripperSpec.MASS_STEP_GRAMS);
        return Math.max(0L, Math.min(wanted, most));
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
            AccessoryType wanted = base.laden(massStep(base, grams));
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
                AccessoryType base = entry.getValue().unladen();
                sum += flightGrams(base, AccessoryBehaviors.INSTANCE.payloadGrams(base, contents.at(entry.getKey())));
            }
        }
        return sum;
    }

    public static boolean overloaded(DroneBuild build, AccessoryContents contents) {
        if (contents == null || contents.isEmpty()) {
            return false;
        }
        for (Map.Entry<AccessoryMount, AccessoryType> entry : build.accessories().byMount().entrySet()) {
            AccessoryType base = entry.getValue().unladen();
            if (!base.isGripper() && overloaded(base, AccessoryBehaviors.INSTANCE.payloadGrams(base, contents.at(entry.getKey())))) {
                return true;
            }
        }
        return false;
    }

    public static boolean overloaded(QuadcopterEntity drone) {
        return overloaded(drone.build(), drone.accessoryContents());
    }
}
