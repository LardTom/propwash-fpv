package de.lardtom.propwash.build;

import java.util.ArrayList;
import java.util.List;

public final class BatteryFit {
    public enum Result {
        OK,
        WEAK,
        CELLS_LOW,
        CELLS_HIGH,
        TOO_HEAVY;

        public boolean allowed() {
            return this == OK || this == WEAK;
        }
    }

    public enum WeakReason {
        NONE,
        CELLS,
        CAPACITY
    }

    public record Rule(int minCells, int maxCells, double maxMassGrams, int preferredMinCells, int preferredMinCapacityMah) {
    }

    private BatteryFit() {
    }

    public static Rule rule(FrameType frame) {
        return frame.batteryFitRule();
    }

    public static Result check(FrameType frame, BatteryType battery) {
        Rule rule = rule(frame);
        if (battery.cells < rule.minCells()) {
            return Result.CELLS_LOW;
        }
        if (battery.cells > rule.maxCells()) {
            return Result.CELLS_HIGH;
        }
        if (battery.massGrams() > rule.maxMassGrams()) {
            return Result.TOO_HEAVY;
        }
        if (weakReason(frame, battery) != WeakReason.NONE) {
            return Result.WEAK;
        }
        return Result.OK;
    }

    public static WeakReason weakReason(FrameType frame, BatteryType battery) {
        Rule rule = rule(frame);
        if (battery.cells < rule.preferredMinCells()) {
            return WeakReason.CELLS;
        }
        if (battery.capacityMah < rule.preferredMinCapacityMah()) {
            return WeakReason.CAPACITY;
        }
        return WeakReason.NONE;
    }

    public static boolean allowed(Result result) {
        return result.allowed();
    }

    public static boolean allowed(FrameType frame, BatteryType battery) {
        return check(frame, battery).allowed();
    }

    public static List<BatteryType> fitting(FrameType frame) {
        List<BatteryType> out = new ArrayList<>();
        for (BatteryType battery : BatteryType.values()) {
            if (allowed(check(frame, battery))) {
                out.add(battery);
            }
        }
        return List.copyOf(out);
    }

    public static List<BatteryType> fitting(FrameType frame, Result result) {
        List<BatteryType> out = new ArrayList<>();
        for (BatteryType battery : BatteryType.values()) {
            if (check(frame, battery) == result) {
                out.add(battery);
            }
        }
        return List.copyOf(out);
    }

    public static List<FrameType> framesFor(BatteryType battery) {
        List<FrameType> out = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (allowed(check(frame, battery))) {
                out.add(frame);
            }
        }
        return List.copyOf(out);
    }

    public static List<FrameType> framesFor(BatteryType battery, Result result) {
        List<FrameType> out = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (check(frame, battery) == result) {
                out.add(frame);
            }
        }
        return List.copyOf(out);
    }
}
