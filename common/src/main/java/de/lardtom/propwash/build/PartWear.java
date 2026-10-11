package de.lardtom.propwash.build;

import java.util.Arrays;

public final class PartWear {
    public static final int POSITIONS = DroneBuild.MOTOR_COUNT;
    public static final int FRONT_LEFT = 0;
    public static final int FRONT_RIGHT = 1;
    public static final int REAR_LEFT = 2;
    public static final int REAR_RIGHT = 3;
    public static final int SLOT_FRAME = 0;
    public static final int SLOT_STACK = 1;
    public static final int SLOT_MOTOR = 2;
    public static final int SLOT_PROP = SLOT_MOTOR + POSITIONS;
    public static final int SLOT_VIDEO = SLOT_PROP + POSITIONS;
    public static final int SLOT_BATTERY = SLOT_VIDEO + 1;
    public static final int SLOTS = SLOT_BATTERY + 1;
    public static final float NEW = 1.0F;
    public static final float DAMAGED_BELOW = 0.9F;
    public static final float CRITICAL_BELOW = 0.4F;
    public static final int STEPS = 1000;
    public static final float STEP = 1.0F / STEPS;
    public static final PartWear PRISTINE = new PartWear(full(), NEW, full(), full(), NEW, NEW);

    private static final int[] SIM_MOTOR_BY_POSITION = {3, 1, 2, 0};

    private final float[] arms;
    private final float stack;
    private final float[] motors;
    private final float[] props;
    private final float video;
    private final float battery;

    private PartWear(float[] arms, float stack, float[] motors, float[] props, float video, float battery) {
        this.arms = arms;
        this.stack = stack;
        this.motors = motors;
        this.props = props;
        this.video = video;
        this.battery = battery;
    }

    public static PartWear of(float[] arms, float stack, float[] motors, float[] props, float video, float battery) {
        PartWear wear = new PartWear(quantize(arms), quantize(stack), quantize(motors), quantize(props), quantize(video), quantize(battery));
        return wear.equals(PRISTINE) ? PRISTINE : wear;
    }

    public static PartWear uniform(float frame, float stack, float motor, float prop, float video, float battery) {
        return of(filled(frame), stack, filled(motor), filled(prop), video, battery);
    }

    public static PartWear fromSlots(float[] slots) {
        if (slots == null || slots.length != SLOTS) {
            throw new IllegalArgumentException("Erwartet " + SLOTS + " Bauteilplätze");
        }
        float[] motors = new float[POSITIONS];
        float[] props = new float[POSITIONS];
        System.arraycopy(slots, SLOT_MOTOR, motors, 0, POSITIONS);
        System.arraycopy(slots, SLOT_PROP, props, 0, POSITIONS);
        return of(filled(slots[SLOT_FRAME]), slots[SLOT_STACK], motors, props, slots[SLOT_VIDEO], slots[SLOT_BATTERY]);
    }

    public float[] slots() {
        float[] slots = new float[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            slots[slot] = this.slot(slot);
        }
        return slots;
    }

    public PartWear withProps(float[] props) {
        return of(this.arms, this.stack, this.motors, props, this.video, this.battery);
    }

    public static int simMotor(int position) {
        return SIM_MOTOR_BY_POSITION[Math.floorMod(position, POSITIONS)];
    }

    public static int positionOfSimMotor(int motor) {
        return SIM_MOTOR_BY_POSITION[Math.floorMod(motor, POSITIONS)];
    }

    public static float quantize(float condition) {
        if (!Float.isFinite(condition) || condition <= 0.0F) {
            return 0.0F;
        }
        if (condition >= NEW) {
            return NEW;
        }
        return fromSteps(toSteps(condition));
    }

    public static int toSteps(float condition) {
        if (!Float.isFinite(condition)) {
            return 0;
        }
        return Math.round(Math.max(0.0F, Math.min(NEW, condition)) * STEPS);
    }

    public static float fromSteps(int steps) {
        return Math.max(0, Math.min(STEPS, steps)) / (float) STEPS;
    }

    public static boolean broken(float condition) {
        return condition <= 0.0F;
    }

    public static boolean damaged(float condition) {
        return condition < DAMAGED_BELOW;
    }

    public static boolean critical(float condition) {
        return condition < CRITICAL_BELOW;
    }

    public float arm(int position) {
        return this.arms[checked(position)];
    }

    public float frame() {
        return min(this.arms);
    }

    public float stack() {
        return this.stack;
    }

    public float motor(int position) {
        return this.motors[checked(position)];
    }

    public float prop(int position) {
        return this.props[checked(position)];
    }

    public float video() {
        return this.video;
    }

    public float battery() {
        return this.battery;
    }

    public float worstMotor() {
        return min(this.motors);
    }

    public float worstProp() {
        return min(this.props);
    }

    public float worst() {
        return Math.min(Math.min(Math.min(this.frame(), this.stack), Math.min(this.worstMotor(), this.worstProp())),
                Math.min(this.video, this.battery));
    }

    public boolean isNew() {
        return this.equals(PRISTINE);
    }

    private static int count(float[] values, float below) {
        int n = 0;
        for (float value : values) {
            if (value < below) {
                n++;
            }
        }
        return n;
    }

    public int damagedProps() {
        return count(this.props, DAMAGED_BELOW);
    }

    public int brokenProps() {
        return count(this.props, STEP * 0.5F);
    }

    public float slot(int slot) {
        if (slot == SLOT_FRAME) {
            return this.frame();
        }
        if (slot == SLOT_STACK) {
            return this.stack;
        }
        if (slot >= SLOT_MOTOR && slot < SLOT_PROP) {
            return this.motors[slot - SLOT_MOTOR];
        }
        if (slot >= SLOT_PROP && slot < SLOT_VIDEO) {
            return this.props[slot - SLOT_PROP];
        }
        if (slot == SLOT_VIDEO) {
            return this.video;
        }
        if (slot == SLOT_BATTERY) {
            return this.battery;
        }
        throw new IndexOutOfBoundsException("Bauteilplatz " + slot);
    }

    public PartWear withSlot(int slot, float condition) {
        if (slot == SLOT_FRAME) {
            return of(filled(condition), this.stack, this.motors, this.props, this.video, this.battery);
        }
        if (slot == SLOT_STACK) {
            return this.withStack(condition);
        }
        if (slot >= SLOT_MOTOR && slot < SLOT_PROP) {
            return this.withMotor(slot - SLOT_MOTOR, condition);
        }
        if (slot >= SLOT_PROP && slot < SLOT_VIDEO) {
            return this.withProp(slot - SLOT_PROP, condition);
        }
        if (slot == SLOT_VIDEO) {
            return this.withVideo(condition);
        }
        if (slot == SLOT_BATTERY) {
            return this.withBattery(condition);
        }
        throw new IndexOutOfBoundsException("Bauteilplatz " + slot);
    }

    public PartWear withArm(int position, float condition) {
        return of(replaced(this.arms, position, condition), this.stack, this.motors, this.props, this.video, this.battery);
    }

    public PartWear withStack(float condition) {
        return of(this.arms, condition, this.motors, this.props, this.video, this.battery);
    }

    public PartWear withMotor(int position, float condition) {
        return of(this.arms, this.stack, replaced(this.motors, position, condition), this.props, this.video, this.battery);
    }

    public PartWear withProp(int position, float condition) {
        return of(this.arms, this.stack, this.motors, replaced(this.props, position, condition), this.video, this.battery);
    }

    public PartWear withVideo(float condition) {
        return of(this.arms, this.stack, this.motors, this.props, condition, this.battery);
    }

    public PartWear withBattery(float condition) {
        return of(this.arms, this.stack, this.motors, this.props, this.video, condition);
    }

    public float[] arms() {
        return this.arms.clone();
    }

    public float[] motors() {
        return this.motors.clone();
    }

    public float[] props() {
        return this.props.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PartWear w)) {
            return false;
        }
        return Float.compare(this.stack, w.stack) == 0 && Float.compare(this.video, w.video) == 0 && Float.compare(this.battery, w.battery) == 0
                && Arrays.equals(this.arms, w.arms) && Arrays.equals(this.motors, w.motors) && Arrays.equals(this.props, w.props);
    }

    @Override
    public int hashCode() {
        int h = Arrays.hashCode(this.arms);
        h = 31 * h + Float.hashCode(this.stack);
        h = 31 * h + Arrays.hashCode(this.motors);
        h = 31 * h + Arrays.hashCode(this.props);
        h = 31 * h + Float.hashCode(this.video);
        return 31 * h + Float.hashCode(this.battery);
    }

    @Override
    public String toString() {
        return "PartWear[arms=" + Arrays.toString(this.arms) + ", stack=" + this.stack + ", motors=" + Arrays.toString(this.motors)
                + ", props=" + Arrays.toString(this.props) + ", video=" + this.video + ", battery=" + this.battery + "]";
    }

    private static int checked(int position) {
        if (position < 0 || position >= POSITIONS) {
            throw new IndexOutOfBoundsException("Position " + position);
        }
        return position;
    }

    private static float min(float[] values) {
        float min = NEW;
        for (float value : values) {
            min = Math.min(min, value);
        }
        return min;
    }

    private static float[] full() {
        return filled(NEW);
    }

    private static float[] filled(float value) {
        float[] values = new float[POSITIONS];
        Arrays.fill(values, value);
        return values;
    }

    private static float[] replaced(float[] values, int position, float condition) {
        float[] copy = values.clone();
        copy[checked(position)] = condition;
        return copy;
    }

    private static float[] quantize(float[] values) {
        if (values == null || values.length != POSITIONS) {
            throw new IllegalArgumentException("Erwartet " + POSITIONS + " Werte");
        }
        float[] copy = new float[POSITIONS];
        for (int i = 0; i < POSITIONS; i++) {
            copy[i] = quantize(values[i]);
        }
        return copy;
    }
}
