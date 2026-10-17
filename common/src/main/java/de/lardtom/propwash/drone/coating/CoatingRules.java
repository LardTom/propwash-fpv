package de.lardtom.propwash.drone.coating;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartInfo;

import java.util.ArrayList;
import java.util.List;

public final class CoatingRules {
    public static final int DURABILITY = 100;
    public static final int COST = 10;

    private CoatingRules() {
    }

    public static int usesLeft(int damage) {
        return Math.max(0, DURABILITY - Math.max(0, damage)) / COST;
    }

    public static int damageAfterUse(int damage) {
        return Math.max(0, damage) + COST;
    }

    public static boolean usedUp(int damage) {
        return damage >= DURABILITY;
    }

    public static List<String> electronics(DroneBuild build) {
        List<String> keys = new ArrayList<>();
        keys.add(key(build.frame()));
        keys.add(key(build.stack()));
        keys.add(key(build.motor()));
        keys.add(key(build.video()));
        List<String> accessories = new ArrayList<>();
        for (PartInfo accessory : build.accessories().list()) {
            accessories.add(key(accessory));
        }
        accessories.sort(String::compareTo);
        keys.addAll(accessories);
        return keys;
    }

    public static int signature(DroneBuild build) {
        return String.join("|", electronics(build)).hashCode();
    }

    public static boolean matches(Integer coating, DroneBuild build) {
        return coating != null && build != null && coating == signature(build);
    }

    private static String key(PartInfo part) {
        return part == null ? "-" : part.id().toString();
    }
}
