package de.lardtom.propwash.api.client;

import java.util.List;

public final class FlightSchoolScenes {
    public static final String DRONE_BUILD = "propwash:drone_build";
    public static final String BIND = "propwash:bind";
    public static final String FPV_LOS = "propwash:fpv_los";
    public static final String BATTERY = "propwash:battery";
    public static final String RACE = "propwash:race";
    public static final String GATE_FREE = "propwash:gate_free";
    public static final String COURSE_ELEMENTS = "propwash:course_elements";
    public static final String TRICKS = "propwash:tricks";
    public static final String VIDEO = "propwash:video";
    public static final String ACTION_CAM = "propwash:action_cam";
    public static final String GRIPPER = "propwash:gripper";
    public static final String CONFIGURATOR = "propwash:configurator";
    public static final String RESCUE = "propwash:rescue";
    public static final String HOLD = "propwash:hold";
    public static final String MODE_3D = "propwash:mode_3d";
    public static final String RECOVERY = "propwash:recovery";
    public static final String REPAIR = "propwash:repair";
    public static final String SURVIVAL = "propwash:survival";
    public static final List<String> ALL = List.of(DRONE_BUILD, BIND, FPV_LOS, BATTERY, RACE, GATE_FREE, COURSE_ELEMENTS, TRICKS, VIDEO, ACTION_CAM, GRIPPER, CONFIGURATOR,
            RESCUE, HOLD, MODE_3D, RECOVERY, REPAIR, SURVIVAL);

    private FlightSchoolScenes() {
    }
}
