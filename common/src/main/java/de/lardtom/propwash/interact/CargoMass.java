package de.lardtom.propwash.interact;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;

import java.util.Optional;

public final class CargoMass {
    private CargoMass() {
    }

    public static Optional<AccessoryType> gripper(DroneBuild build) {
        for (AccessoryType type : build.accessories().byMount().values()) {
            if (type.isGripper()) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static boolean hasGripper(DroneBuild build) {
        return gripper(build).isPresent();
    }

    public static DroneBuild withPayload(DroneBuild build, double payloadGrams) {
        Optional<AccessoryType> gripper = gripper(build);
        if (gripper.isEmpty()) {
            return build;
        }
        AccessoryType current = gripper.get();
        AccessoryType wanted = current.laden(GripperSpec.massStep(payloadGrams));
        return wanted == current ? build : build.withAccessory(wanted);
    }

    public static DroneBuild unladen(DroneBuild build) {
        return withPayload(build, 0.0);
    }
}
