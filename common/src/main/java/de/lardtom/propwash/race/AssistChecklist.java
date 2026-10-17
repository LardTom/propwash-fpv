package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AssistChecklist {
    private AssistChecklist() {
    }

    public enum Status {
        OK,
        FLAGGED,
        INFO
    }

    public enum Category {
        PHYSICS(LapAssist.WORLD_PHYSICS.bit() | LapAssist.ZONE_PHYSICS.bit()),
        DRONE(LapAssist.CREATIVE_DRONE.bit() | LapAssist.PARTS_MISMATCH.bit()),
        GOGGLES(LapAssist.GOGGLES_LATENCY.bit()),
        HOLD(LapAssist.HOLD_MODE.bit()),
        RESCUE(LapAssist.RESCUE.bit() | LapAssist.OTHER.bit());

        private final int mask;

        Category(int mask) {
            this.mask = mask;
        }

        public int mask() {
            return this.mask;
        }

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public static Category of(LapAssist reason) {
            for (Category category : values()) {
                if (reason.in(category.mask)) {
                    return category;
                }
            }
            return RESCUE;
        }
    }

    public record Item(Category category, Status status, AssistReasons reasons) {
    }

    public static List<Item> items(AssistReasons reasons, boolean hasDrone, boolean hasGoggles) {
        List<Item> out = new ArrayList<>();
        for (Category category : Category.values()) {
            AssistReasons own = reasons.only(category.mask());
            Status status;
            if (own.any()) {
                status = Status.FLAGGED;
            } else if (category == Category.DRONE && !hasDrone || category == Category.GOGGLES && (!hasGoggles || !hasDrone)) {
                status = Status.INFO;
            } else {
                status = Status.OK;
            }
            out.add(new Item(category, status, own));
        }
        return out;
    }

    public static int covered() {
        int mask = LapAssist.NONE;
        for (Category category : Category.values()) {
            mask |= category.mask();
        }
        return mask;
    }
}
