package de.lardtom.propwash.zone;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ZoneMenu {
    public static final List<String> TELEPORT = List.of("teleport", "location");
    public static final List<String> ARROWS = List.of("↑", "↗", "→", "↘", "↓", "↙", "←", "↖");

    public enum Action {
        SHOW,
        JUMP,
        EDIT,
        DELETE
    }

    @FunctionalInterface
    public interface Ground {
        boolean standable(int x, int y, int z);
    }

    private ZoneMenu() {
    }

    public static boolean listed(boolean admin, Zone zone, @Nullable UUID player) {
        return admin || zone.ownedBy(player);
    }

    public static List<Zone> listed(List<Zone> zones, boolean admin, @Nullable UUID player) {
        List<Zone> out = new ArrayList<>();
        for (Zone zone : zones) {
            if (listed(admin, zone, player)) {
                out.add(zone);
            }
        }
        return out;
    }

    public static List<Action> actions(boolean admin, boolean playersCreate, boolean teleport, Zone zone, @Nullable UUID player) {
        List<Action> out = new ArrayList<>();
        if (!listed(admin, zone, player)) {
            return out;
        }
        out.add(Action.SHOW);
        if (teleport) {
            out.add(Action.JUMP);
        }
        if (ZoneRights.mayChange(admin, playersCreate, zone, player)) {
            out.add(Action.EDIT);
        }
        if (ZoneRights.mayDelete(admin, playersCreate, zone, player)) {
            out.add(Action.DELETE);
        }
        return out;
    }

    public static double @Nullable [] jumpTarget(Zone zone, int floor, Ground ground) {
        ZoneBox box = zone.box();
        int x = (int) Math.floor(box.centerX());
        int z = (int) Math.floor(box.centerZ());
        int bottom = (int) Math.floor(box.minY());
        int top = (int) Math.ceil(box.maxY()) - 1;
        for (int y = bottom; y <= top; y++) {
            if (ground.standable(x, y, z)) {
                return new double[]{x + 0.5, y, z + 0.5};
            }
        }
        for (int y = bottom - 1; y >= floor; y--) {
            if (ground.standable(x, y, z)) {
                return new double[]{x + 0.5, y, z + 0.5};
            }
        }
        return null;
    }

    public static String arrow(double dx, double dz, float yaw) {
        if (dx * dx + dz * dz < 1.0E-6) {
            return ARROWS.getFirst();
        }
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = bearing - yaw;
        relative -= 360.0 * Math.floor((relative + 180.0) / 360.0);
        return ARROWS.get(Math.floorMod((int) Math.round(relative / 45.0), ARROWS.size()));
    }
}
