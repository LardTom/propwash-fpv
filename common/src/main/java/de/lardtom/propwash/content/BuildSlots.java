package de.lardtom.propwash.content;

import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.PartCategory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class BuildSlots {
    public static final int FRAME = 0;
    public static final int STACK = 1;
    public static final int MOTOR = 2;
    public static final int PROP = MOTOR + DroneBuild.MOTOR_COUNT;
    public static final int VIDEO = PROP + DroneBuild.MOTOR_COUNT;
    public static final int BATTERY = VIDEO + 1;
    public static final int ACCESSORY = BATTERY + 1;
    public static final int REQUIRED_COUNT = ACCESSORY;
    public static final int COUNT = ACCESSORY + AccessoryMount.values().length;

    private static final PartCategory[] CATEGORY_BY_SLOT = new PartCategory[COUNT];

    static {
        for (PartCategory category : PartCategory.values()) {
            int first = firstSlot(category);
            for (int i = 0; i < category.slotCount(); i++) {
                CATEGORY_BY_SLOT[first + i] = category;
            }
        }
    }

    private BuildSlots() {
    }

    public static int firstSlot(PartCategory category) {
        return switch (category) {
            case FRAME -> FRAME;
            case STACK -> STACK;
            case MOTOR -> MOTOR;
            case PROP -> PROP;
            case VIDEO -> VIDEO;
            case BATTERY -> BATTERY;
            case ACCESSORY -> ACCESSORY;
        };
    }

    public static PartCategory categoryOf(int slot) {
        if (slot < 0 || slot >= COUNT) {
            throw new IndexOutOfBoundsException("Werkbank-Slot " + slot);
        }
        return CATEGORY_BY_SLOT[slot];
    }

    public static int positionInCategory(int slot) {
        return slot - firstSlot(categoryOf(slot));
    }

    public static int accessorySlot(AccessoryMount mount) {
        return ACCESSORY + mount.ordinal();
    }

    public static Optional<AccessoryMount> mountOf(int slot) {
        if (categoryOf(slot) != PartCategory.ACCESSORY) {
            return Optional.empty();
        }
        return Optional.of(AccessoryMount.values()[positionInCategory(slot)]);
    }

    public static int[] slotsFor(PartInfo part) {
        PartCategory category = PartCategory.of(part);
        if (part instanceof AccessoryType accessory) {
            return new int[]{accessorySlot(accessory.mount)};
        }
        int first = firstSlot(category);
        int[] slots = new int[category.slotCount()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = first + i;
        }
        return slots;
    }

    public static boolean accepts(int slot, @Nullable PartInfo part) {
        if (part == null || !categoryOf(slot).partClass().isInstance(part)) {
            return false;
        }
        if (part instanceof AccessoryType accessory) {
            return mountOf(slot).map(accessory::fits).orElse(false);
        }
        return true;
    }

    public static List<@Nullable PartInfo> layout(DroneBuild build) {
        List<@Nullable PartInfo> parts = new ArrayList<>(COUNT);
        for (int slot = 0; slot < COUNT; slot++) {
            parts.add(categoryOf(slot).get(build, positionInCategory(slot)));
        }
        return Collections.unmodifiableList(parts);
    }

    public static Check check(List<? extends @Nullable PartInfo> slots) {
        if (slots.size() != COUNT) {
            throw new IllegalArgumentException("Erwartet " + COUNT + " Slots, bekommen " + slots.size());
        }
        EnumSet<PartCategory> missing = EnumSet.noneOf(PartCategory.class);
        EnumSet<PartCategory> mixed = EnumSet.noneOf(PartCategory.class);
        PartInfo[] chosen = new PartInfo[PartCategory.values().length];
        Accessories accessories = Accessories.NONE;
        int filled = 0;
        for (int slot = 0; slot < COUNT; slot++) {
            PartCategory category = categoryOf(slot);
            PartInfo part = slots.get(slot);
            if (!accepts(slot, part)) {
                if (!category.optional()) {
                    missing.add(category);
                }
                continue;
            }
            filled++;
            if (part instanceof AccessoryType accessory) {
                accessories = accessories.with(accessory);
                continue;
            }
            PartInfo previous = chosen[category.ordinal()];
            if (previous == null) {
                chosen[category.ordinal()] = part;
            } else if (previous != part) {
                mixed.add(category);
            }
        }
        DroneBuild build = null;
        if (missing.isEmpty() && mixed.isEmpty()) {
            build = new DroneBuild(
                    (FrameType) chosen[PartCategory.FRAME.ordinal()],
                    (StackType) chosen[PartCategory.STACK.ordinal()],
                    (MotorType) chosen[PartCategory.MOTOR.ordinal()],
                    (PropType) chosen[PartCategory.PROP.ordinal()],
                    (VideoType) chosen[PartCategory.VIDEO.ordinal()],
                    (BatteryType) chosen[PartCategory.BATTERY.ordinal()],
                    accessories);
        }
        return new Check(Optional.ofNullable(build), Set.copyOf(missing), Set.copyOf(mixed), filled);
    }

    public static Check check(@Nullable PartInfo... slots) {
        return check(Arrays.asList(slots));
    }

    public static Set<PartCategory> affectedBy(DroneBuild.Issue issue) {
        return switch (issue) {
            case MOTOR_MOUNT -> EnumSet.of(PartCategory.FRAME, PartCategory.MOTOR);
            case PROP_TOO_LARGE -> EnumSet.of(PartCategory.FRAME, PartCategory.PROP);
            case STACK_MOUNT -> EnumSet.of(PartCategory.FRAME, PartCategory.STACK);
            case MOTOR_OVERVOLTAGE, MOTOR_UNDERVOLTAGE -> EnumSet.of(PartCategory.MOTOR, PartCategory.BATTERY);
            case STACK_VOLTAGE -> EnumSet.of(PartCategory.STACK, PartCategory.BATTERY);
            case ESC_UNDERSIZED -> EnumSet.of(PartCategory.STACK, PartCategory.MOTOR);
            case BATTERY_WEAK -> EnumSet.of(PartCategory.BATTERY, PartCategory.MOTOR);
            case BATTERY_FRAME, BATTERY_UNDERSIZED -> EnumSet.of(PartCategory.FRAME, PartCategory.BATTERY);
            case ACCESSORY_MOUNT -> EnumSet.of(PartCategory.FRAME, PartCategory.ACCESSORY);
            case MISSING_PART -> EnumSet.noneOf(PartCategory.class);
            case PROP_TOO_SMALL -> EnumSet.of(PartCategory.FRAME, PartCategory.PROP);
            case PROP_MOUNT -> EnumSet.of(PartCategory.MOTOR, PartCategory.PROP);
        };
    }

    public record Check(Optional<DroneBuild> build, Set<PartCategory> missing, Set<PartCategory> mixed, int filledSlots) {
        public boolean complete() {
            return this.build.isPresent();
        }

        public boolean empty() {
            return this.filledSlots == 0;
        }

        public boolean buildable() {
            return this.build.map(DroneBuild::buildable).orElse(false);
        }

        public boolean buildable(PartCompatibility rule) {
            return this.build.map(build -> build.buildable(rule)).orElse(false);
        }
    }
}
