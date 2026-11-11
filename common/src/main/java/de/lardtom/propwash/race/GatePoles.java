package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.List;

public final class GatePoles {
    public static final int COLORS = 16;
    public static final double THICKNESS = 0.5;
    public static final double DEFAULT_LENGTH = 4.0;
    public static final double MIN_LENGTH = 1.0;
    public static final double MAX_LENGTH = 16.0;
    public static final double LENGTH_STEP = 0.5;
    public static final double END = 0.5;
    public static final int MAX_CHAIN = 4;
    public static final double EPSILON = 1.0E-3;
    public static final int WHITE = 0;
    public static final int BLACK = 15;
    public static final int CENTER_SEAT = 0;
    public static final int CUBE_SEAT = 10;
    private static final String[] COLOR_KEYS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int[] LED_COLORS = {0xFFFFFF, 0xFF8A1F, 0xFF3DF0, 0x5AC8FF, 0xFFE81F, 0x9DFF2E, 0xFF8AC8, 0x8A8A8A, 0xC8C8C8,
            0x1FE8E0, 0xA64DFF, 0x3D6BFF, 0xB0682E, 0x33FF66, 0xFF3B30, 0x1A1A1A};

    private GatePoles() {
    }

    public enum Refusal {
        NONE,
        NO_SEAT,
        TOO_HIGH
    }

    public record Seat(double x, double y, double z, float yaw, float fine, int corner) {
    }

    public static String colorKey(int variant) {
        return COLOR_KEYS[Math.floorMod(variant, COLORS)];
    }

    public static int ledColor(int variant) {
        return LED_COLORS[Math.floorMod(variant, COLORS)];
    }

    public static double snapLength(double length) {
        if (!Double.isFinite(length)) {
            return DEFAULT_LENGTH;
        }
        double snapped = Math.round(length / LENGTH_STEP) * LENGTH_STEP;
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, snapped));
    }

    public static boolean pole(GateGeometry geometry) {
        return geometry.kind() == CourseElement.GATE_POLE;
    }

    public static boolean standing(GateGeometry geometry) {
        return pole(geometry) && geometry.mount() == GateMount.STANDING && geometry.upY() > 1.0 - 1.0E-9;
    }

    public static boolean seatable(CourseElement kind) {
        return kind == CourseElement.GATE_POLE || kind == CourseElement.FLAG || kind == CourseElement.POLE;
    }

    public static boolean seated(GateGeometry geometry) {
        if (pole(geometry)) {
            return standing(geometry);
        }
        return seatable(geometry.kind()) && geometry.mount() == GateMount.STANDING && geometry.upY() > 1.0 - 1.0E-9;
    }

    public static GateStacking.Base base(GateMount mount, boolean supported) {
        if (mount == GateMount.STANDING) {
            return supported ? GateStacking.Base.STACKED : GateStacking.Base.BAR;
        }
        return GateStacking.Base.MOUNTED;
    }

    public static boolean carriesPoles(GateGeometry geometry) {
        if (geometry.freeform()) {
            return false;
        }
        if (pole(geometry)) {
            return standing(geometry);
        }
        if (geometry.kind() == CourseElement.CUBE) {
            return geometry.upY() > 1.0 - 1.0E-9;
        }
        return geometry.gate() && geometry.upright() && geometry.mount() != GateMount.HANGING && geometry.upY() > 1.0 - 1.0E-9;
    }

    public static List<Seat> seats(GateGeometry geometry) {
        if (!carriesPoles(geometry)) {
            return List.of();
        }
        float yaw = GateGeometry.snapYaw(geometry.yawDegrees());
        float fine = geometry.tilt().yaw();
        if (pole(geometry)) {
            return List.of(new Seat(geometry.x(), geometry.y() + geometry.height(), geometry.z(), yaw, fine, 0));
        }
        double top = geometry.height();
        if (geometry.kind() == CourseElement.CUBE) {
            double post = geometry.width() * 0.5 - geometry.frame() * 0.5;
            List<Seat> seats = new ArrayList<>(8);
            int corner = CUBE_SEAT;
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a != 0 || b != 0) {
                        seats.add(new Seat(geometry.worldX(a * post, top, b * post), geometry.worldY(a * post, top, b * post),
                                geometry.worldZ(a * post, top, b * post), yaw, fine, corner++));
                    }
                }
            }
            return List.copyOf(seats);
        }
        double post = geometry.postOffset();
        List<Seat> seats = new ArrayList<>(3);
        for (int corner = -1; corner <= 1; corner += 2) {
            seats.add(new Seat(geometry.worldX(corner * post, top, 0.0), geometry.worldY(corner * post, top, 0.0),
                    geometry.worldZ(corner * post, top, 0.0), yaw, fine, corner));
        }
        seats.add(new Seat(geometry.worldX(0.0, top, 0.0), geometry.worldY(0.0, top, 0.0), geometry.worldZ(0.0, top, 0.0), yaw, fine, CENTER_SEAT));
        return List.copyOf(seats);
    }

    public static boolean on(GateGeometry pole, Seat seat) {
        return Math.abs(pole.x() - seat.x()) < EPSILON && Math.abs(pole.y() - seat.y()) < EPSILON && Math.abs(pole.z() - seat.z()) < EPSILON;
    }

    public static boolean restsOn(GateGeometry pole, GateGeometry support) {
        if (!seated(pole)) {
            return false;
        }
        for (Seat seat : seats(support)) {
            if (on(pole, seat)) {
                return true;
            }
        }
        return false;
    }

    public static int nearest(List<Seat> seats, double x, double z) {
        int best = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);
            double dx = seat.x() - x;
            double dz = seat.z() - z;
            double d = dx * dx + dz * dz;
            if (d < distance - 1.0E-9) {
                distance = d;
                best = i;
            }
        }
        return best;
    }

    public static GateGeometry onSeat(Seat seat, int variant, double length) {
        return GateGeometry.element(seat.x(), seat.y(), seat.z(), seat.yaw(), CourseElement.GATE_POLE, variant, THICKNESS, snapLength(length),
                new GateTilt(seat.fine(), 0.0F, 0.0F), GateMount.STANDING);
    }

    public static GateGeometry elementOnSeat(Seat seat, CourseElement kind, int variant, float yaw, float fine) {
        return GateGeometry.element(seat.x(), seat.y(), seat.z(), GateGeometry.snapYaw(yaw), kind, variant, kind.outerWidth(kind.defaultWidth()),
                kind.outerHeight(kind.defaultHeight()), new GateTilt(fine, 0.0F, 0.0F));
    }

    public static Refusal check(int chain) {
        return chain >= MAX_CHAIN ? Refusal.TOO_HIGH : Refusal.NONE;
    }

    public static GateGeometry place(GatePlacement.Click click, GatePlacement.Preference preference, int variant, double length) {
        double l = snapLength(length);
        int turn = preference.floor().turns() == 4 ? preference.floorTurn() : 0;
        float fine = preference.floor().turns() == 4 ? preference.floorFine() : 0.0F;
        float yaw = GateGeometry.snapYaw(click.playerYaw() + GatePlacement.QUARTER * turn);
        GateTilt tilt = new GateTilt(fine, 0.0F, 0.0F);
        return switch (click.surface()) {
            case FLOOR -> GateGeometry.element(click.floorX(), click.floorY(), click.floorZ(), yaw, CourseElement.GATE_POLE, variant, THICKNESS, l,
                    tilt, GateMount.STANDING);
            case CEILING -> GateGeometry.element(click.blockX() + 0.5, click.plane() - l, click.blockZ() + 0.5, yaw, CourseElement.GATE_POLE,
                    variant, THICKNESS, l, tilt, GateMount.HANGING);
            case WALL -> {
                boolean alongZ = click.faceX() != 0;
                double along = (alongZ ? click.blockZ() : click.blockX()) + 0.5;
                double px = alongZ ? click.plane() : along;
                double pz = alongZ ? along : click.plane();
                yield GateGeometry.element(px, click.blockY() + 0.5, pz, GatePlacement.wallYaw(click.faceX(), click.faceZ()),
                        CourseElement.GATE_POLE, variant, THICKNESS, l, GateTilt.NONE, GateMount.WALL_DIVE);
            }
        };
    }

    public static GateGeometry edited(GateGeometry pole, float heading, double length) {
        double l = snapLength(length);
        GateGeometry turned = GatePlacement.turnedTo(pole, heading);
        double y = pole.mount() == GateMount.HANGING ? pole.y() + pole.height() - l : pole.y();
        return GateGeometry.element(pole.x(), y, pole.z(), turned.yawDegrees(), CourseElement.GATE_POLE, pole.variant(), THICKNESS, l,
                turned.tilt(), pole.mount());
    }

    public static GateGeometry moved(GateGeometry pole, double dx, double dy, double dz) {
        return pole.at(pole.x() + dx, pole.y() + dy, pole.z() + dz);
    }
}
