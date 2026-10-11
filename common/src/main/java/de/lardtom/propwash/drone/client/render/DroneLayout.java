package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.sim.FpvRig;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class DroneLayout {
    public static final int SLOTS = 4;
    public static final int FRONT_LEFT = 0;
    public static final int FRONT_RIGHT = 1;
    public static final int REAR_LEFT = 2;
    public static final int REAR_RIGHT = 3;

    private static final int[] SLOT_BY_MOTOR = {REAR_RIGHT, FRONT_RIGHT, REAR_LEFT, FRONT_LEFT};
    private static final boolean[] PROPS_IN_CCW = {false, true, true, false};
    public static final float CAMERA_BEHIND_LENS_MM = 10.0F;

    private final Vector3fc[] motors;
    private final boolean[] counterClockwise;
    private final float propHeight;
    private final Vector3fc stack;
    private final Vector3fc battery;
    private final Vector3fc video;
    private final float cameraTiltDegrees;
    private final boolean fromFile;
    private final boolean batteryUnder;
    private final Map<AccessoryMount, AccessoryAnchor> accessories;

    public DroneLayout(Vector3fc[] motors, boolean[] counterClockwise, float propHeight, Vector3fc stack, Vector3fc battery,
                       Vector3fc video, float cameraTiltDegrees, boolean fromFile) {
        this(motors, counterClockwise, propHeight, stack, battery, video, cameraTiltDegrees, fromFile, Map.of());
    }

    public DroneLayout(Vector3fc[] motors, boolean[] counterClockwise, float propHeight, Vector3fc stack, Vector3fc battery,
                       Vector3fc video, float cameraTiltDegrees, boolean fromFile, Map<AccessoryMount, AccessoryAnchor> accessories) {
        this(motors, counterClockwise, propHeight, stack, battery, video, cameraTiltDegrees, fromFile, accessories, battery.y() < 0.0F);
    }

    public DroneLayout(Vector3fc[] motors, boolean[] counterClockwise, float propHeight, Vector3fc stack, Vector3fc battery,
                       Vector3fc video, float cameraTiltDegrees, boolean fromFile, Map<AccessoryMount, AccessoryAnchor> accessories,
                       boolean batteryUnder) {
        if (motors.length != SLOTS || counterClockwise.length != SLOTS) {
            throw new IllegalArgumentException("Layout braucht genau " + SLOTS + " Motoren");
        }
        this.motors = new Vector3fc[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            this.motors[i] = new Vector3f(motors[i]);
        }
        this.counterClockwise = Arrays.copyOf(counterClockwise, SLOTS);
        this.propHeight = propHeight;
        this.stack = new Vector3f(stack);
        this.battery = new Vector3f(battery);
        this.video = new Vector3f(video);
        this.cameraTiltDegrees = cameraTiltDegrees;
        this.fromFile = fromFile;
        this.batteryUnder = batteryUnder;
        Map<AccessoryMount, AccessoryAnchor> copy = new EnumMap<>(AccessoryMount.class);
        copy.putAll(accessories);
        this.accessories = Collections.unmodifiableMap(copy);
    }

    public static int slotForMotor(int motorIndex) {
        return SLOT_BY_MOTOR[Math.floorMod(motorIndex, SLOTS)];
    }

    public static int motorForSlot(int slot) {
        for (int motor = 0; motor < SLOTS; motor++) {
            if (SLOT_BY_MOTOR[motor] == slot) {
                return motor;
            }
        }
        throw new IllegalArgumentException("Unbekannter Motorplatz " + slot);
    }

    public static boolean isFront(int slot) {
        return slot == FRONT_LEFT || slot == FRONT_RIGHT;
    }

    public static boolean propsInCounterClockwise(int slot) {
        return PROPS_IN_CCW[slot];
    }

    public Vector3fc motor(int slot) {
        return this.motors[slot];
    }

    public boolean counterClockwise(int slot) {
        return this.counterClockwise[slot];
    }

    public float propHeight() {
        return this.propHeight;
    }

    public boolean hasPropHeight() {
        return Float.isFinite(this.propHeight);
    }

    public Vector3fc stack() {
        return this.stack;
    }

    public Vector3fc battery() {
        return this.battery;
    }

    public Vector3fc video() {
        return this.video;
    }

    public float cameraTiltDegrees() {
        return this.cameraTiltDegrees;
    }

    public boolean fromFile() {
        return this.fromFile;
    }

    public Optional<AccessoryAnchor> accessory(AccessoryMount mount) {
        return Optional.ofNullable(this.accessories.get(mount));
    }

    public Map<AccessoryMount, AccessoryAnchor> accessories() {
        return this.accessories;
    }

    public static Map<AccessoryMount, AccessoryAnchor> defaultAccessories(FrameType frame) {
        Map<AccessoryMount, AccessoryAnchor> anchors = new EnumMap<>(AccessoryMount.class);
        for (AccessoryMount mount : frame.accessoryMounts) {
            anchors.put(mount, AccessoryAnchor.of(frame, mount));
        }
        return anchors;
    }

    public boolean batteryBelow() {
        return this.batteryUnder;
    }

    public float motorOffset() {
        float sum = 0.0F;
        for (Vector3fc m : this.motors) {
            sum += (float) Math.sqrt(m.x() * m.x() + m.z() * m.z());
        }
        return sum / SLOTS;
    }

    public static DroneLayout fallback(FrameType frame) {
        return fromDefinition(frame);
    }

    public static DroneLayout fromDefinition(FrameType frame) {
        FrameDefinition definition = frame.definition();
        AirframeGeometry geometry = definition.geometry();
        float wheelbase = frame.wheelbaseMm;
        Vector3f[] motors = new Vector3f[SLOTS];
        boolean[] spins;
        float stackY;
        if (geometry.hasExplicitArms()) {
            spins = new boolean[SLOTS];
            float sum = 0.0F;
            for (int slot = 0; slot < SLOTS; slot++) {
                MotorArm arm = geometry.arms().get(slot);
                motors[slot] = new Vector3f((float) arm.position().x(), (float) arm.position().y(), (float) arm.position().z());
                spins[slot] = arm.spin() == MotorArm.Spin.CCW;
                sum += motors[slot].y;
            }
            stackY = Math.max(0.0F, sum / SLOTS);
        } else {
            float s = (float) (wheelbase / (2.0 * Math.sqrt(2.0)));
            float motorY = motorMountHeight(frame);
            motors[FRONT_LEFT] = new Vector3f(-s, motorY, -s);
            motors[FRONT_RIGHT] = new Vector3f(s, motorY, -s);
            motors[REAR_LEFT] = new Vector3f(-s, motorY, s);
            motors[REAR_RIGHT] = new Vector3f(s, motorY, s);
            spins = PROPS_IN_CCW;
            stackY = motorY;
        }
        float plate = batteryPlateHeight(frame);
        Vector3f battery = geometry.batteryOnTop() ? new Vector3f(0.0F, plate + 2.0F, 0.04F * wheelbase) : new Vector3f(0.0F, plate, 0.0F);
        Vector3f video = geometry.cameraMm()
                .map(lens -> new Vector3f((float) lens.x(), (float) lens.y(), (float) lens.z() + CAMERA_BEHIND_LENS_MM))
                .orElseGet(() -> new Vector3f(0.0F, electronicsHeight(frame), -0.21F * wheelbase));
        float propHeight = frame.builtin() ? defaultPropHeight(typicalMotor(frame)) : Float.NaN;
        return new DroneLayout(motors, spins, propHeight, new Vector3f(0.0F, stackY, 0.0F), battery, video,
                defaultCameraTilt(frame), false, defaultAccessories(frame), !geometry.batteryOnTop());
    }

    public static float motorMountHeight(FrameType frame) {
        return FpvRig.motorMountHeightMm(frame);
    }

    public static float batteryPlateHeight(FrameType frame) {
        return (float) frame.definition().geometry().batteryPlateMm();
    }

    public static float electronicsHeight(FrameType frame) {
        return (float) frame.definition().geometry().electronicsMm();
    }

    public static float defaultCameraTilt(FrameType frame) {
        return (float) frame.definition().geometry().cameraTiltDeg();
    }

    public static MotorType typicalMotor(FrameType frame) {
        return FpvRig.typicalMotor(frame);
    }

    public static float statorDiameter(MotorType motor) {
        return (float) motor.definition().statorDiameterMm();
    }

    public static float statorHeight(MotorType motor) {
        return (float) motor.definition().statorHeightMm();
    }

    public static float bellDiameter(MotorType motor) {
        return statorDiameter(motor) + 5.0F;
    }

    public static float bellHeight(MotorType motor) {
        return statorHeight(motor) + 6.0F;
    }

    public static float defaultPropHeight(MotorType motor) {
        return FpvRig.defaultSeatMm(motor);
    }

    static float[] parseStator(String label) {
        if (label == null || label.length() < 3) {
            return new float[]{22.0F, 7.0F};
        }
        try {
            float diameter = Float.parseFloat(label.substring(0, 2));
            float height = Float.parseFloat(label.substring(2));
            if (diameter <= 0.0F || height <= 0.0F || !Float.isFinite(diameter) || !Float.isFinite(height)) {
                return new float[]{22.0F, 7.0F};
            }
            return new float[]{diameter, height};
        } catch (NumberFormatException e) {
            return new float[]{22.0F, 7.0F};
        }
    }
}
