package de.lardtom.propwash.stats;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.FrameRole;

import java.util.ArrayList;
import java.util.List;

public final class StatNames {
    public static final String FLIGHT_TIME = "flight_time";
    public static final String DISTANCE = "flight_distance";
    public static final String TOP_SPEED = "top_speed";
    public static final String CRASHES = "crashes";
    public static final String BATTERIES_DEPLETED = "batteries_depleted";
    public static final String ARMS = "arms";
    public static final String LAPS = "laps";
    public static final String RACES_FINISHED = "races_finished";
    public static final List<String> COUNTERS = List.of(FLIGHT_TIME, DISTANCE, TOP_SPEED, CRASHES, BATTERIES_DEPLETED, ARMS, LAPS, RACES_FINISHED);

    private StatNames() {
    }

    public static String classTime(FrameRole role) {
        return FLIGHT_TIME + "_" + role.key();
    }

    public static List<String> all() {
        List<String> names = new ArrayList<>(COUNTERS);
        for (FrameRole role : FrameRole.values()) {
            names.add(classTime(role));
        }
        return List.copyOf(names);
    }

    public static String translationKey(String name) {
        return "stat." + Constants.MOD_ID + "." + name;
    }
}
