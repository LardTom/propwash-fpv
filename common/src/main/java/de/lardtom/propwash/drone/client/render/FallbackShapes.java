package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.PackSize;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.MountSize;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.sim.FpvRig;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class FallbackShapes {
    public static final int CARBON = 0xFF232327;
    public static final int ICON_CARBON = 0xFF6E7480;
    public static final int STANDOFF = 0xFF8E5BD6;
    public static final int WHOOP_PLASTIC = 0xFF3D8FD6;
    public static final int DUCT_PLASTIC = 0xFF3A3A40;
    public static final int MOTOR_BASE = 0xFF2C2C31;
    public static final int MOTOR_CAP = 0xFF1B1B1F;
    public static final int STEEL = 0xFFD6D8DC;
    public static final int PROP_NUT = 0xFF303035;
    public static final int ESC_BOARD = 0xFF1A1C20;
    public static final int FC_BOARD = 0xFF174A2A;
    public static final int CHIP = 0xFF0B0B0B;
    public static final int CAPACITOR = 0xFF2B2B2B;
    public static final int BRASS = 0xFFC9A94A;
    public static final int CAMERA = 0xFF151518;
    public static final int LENS = 0xFF1A1A1E;
    public static final int GLASS = 0xFF3A6FB0;
    public static final int TRANSMITTER = 0xFF2C2E33;
    public static final int ANTENNA = 0xFF111111;
    public static final int ANTENNA_CAP = 0xFFC83232;
    public static final int STRAP = 0xFF222222;
    public static final int CONNECTOR = 0xFFE0B020;
    public static final int LED_OFF = 0xFF2E2E33;
    public static final int LED_FRONT = 0xFFE8F6FF;
    public static final int LED_REAR = 0xFFFF2A2A;
    public static final int LED_WARNING = 0xFFFF9020;
    public static final int ACCESSORY_BODY = 0xFF3B3E44;
    public static final int ACCESSORY_HOUSING = 0xFF1C1D21;
    public static final int ACCESSORY_HEATSINK = 0xFF3F6F8C;
    public static final int ACCESSORY_TPU = 0xFF262628;
    public static final int ACCESSORY_LED = 0xFFF4F7FF;
    public static final int ACCESSORY_CHANNEL = 0xFFB9C0C8;
    public static final int GLOW_CORE = 0xFFFFFFFF;
    public static final int GLOW_MID = 0x66FFFFFF;
    public static final int GLOW_EDGE = 0x00FFFFFF;
    public static final float STRIP_HALO_MM = 7.0F;
    public static final float ARM_HALO_MM = 10.0F;
    public static final float MAX_ARM_WIDTH_MM = 36.0F;
    public static final float MAX_PAD_RADIUS_MM = 26.0F;
    public static final float MAX_CAGE_HALF_WIDTH_MM = 30.0F;
    public static final float BODY_SPAN_KNEE_MM = 300.0F;
    public static final float LARGE_AIO_MM = 27.0F;
    public static final float ICON_ARM_RATIO = 0.075F;
    public static final float ICON_PAD_RATIO = 0.075F;
    public static final float ICON_ARM_THICKNESS_RATIO = 0.012F;
    public static final float ICON_CHORD_SCALE = 1.3F;

    private static final Map<FrameType, Shape> FRAMES = new HashMap<>();
    private static final Map<FrameType, Shape> FRAME_ICONS = new HashMap<>();
    private static final Map<FrameType, Shape> LEDS = new HashMap<>();
    private static final Map<FrameType, Shape[]> LED_GLOWS = new HashMap<>();
    private static final Map<FrameType, Shape[]> LED_HALOS = new HashMap<>();
    private static final Map<StackType, Shape> STACKS = new HashMap<>();
    private static final Map<MotorType, Shape> MOTORS = new HashMap<>();
    private static final Map<MotorType, Shape> MOTOR_GLOWS = new HashMap<>();
    private static final Map<PropType, Shape[]> PROPS = new HashMap<>();
    private static final Map<PropType, Shape> PROP_ICONS = new HashMap<>();
    private static final Map<PropType, Shape[]> WIDE_PROPS = new HashMap<>();
    private static final Map<PropType, Shape> BLURS = new HashMap<>();
    private static final Map<BatteryType, Shape> BATTERIES = new HashMap<>();
    private static final Map<VideoType, Shape> CAMERAS = new HashMap<>();
    private static final Map<VideoType, Shape> TRANSMITTERS = new HashMap<>();
    private static final Map<AccessoryType, Shape> ACCESSORIES = new HashMap<>();
    private static final Map<AccessoryMount, Shape> ACCESSORY_BASES = new EnumMap<>(AccessoryMount.class);
    private static final Map<AccessoryType, Shape[]> STRIP_GLOWS = new HashMap<>();
    private static final Map<AccessoryType, Shape[]> STRIP_HALOS = new HashMap<>();
    private static int cachedGeneration = Integer.MIN_VALUE;

    private FallbackShapes() {
    }

    public static synchronized void invalidateIfStale(int generation) {
        if (generation != cachedGeneration) {
            cachedGeneration = generation;
            FRAMES.clear();
            FRAME_ICONS.clear();
            LEDS.clear();
            LED_GLOWS.clear();
            LED_HALOS.clear();
        }
    }

    public static synchronized Shape frame(FrameType frame, DroneLayout layout) {
        return FRAMES.computeIfAbsent(frame, f -> buildFrame(f, layout));
    }

    public static synchronized Shape frameIcon(FrameType frame, DroneLayout layout) {
        return FRAME_ICONS.computeIfAbsent(frame, f -> buildFrame(f, layout, true));
    }

    public static synchronized Shape leds(FrameType frame, DroneLayout layout) {
        return LEDS.computeIfAbsent(frame, f -> buildLeds(f, layout));
    }

    public static synchronized Shape ledGlow(FrameType frame, DroneLayout layout, int slot) {
        return LED_GLOWS.computeIfAbsent(frame, f -> {
            Shape[] glows = new Shape[DroneLayout.SLOTS];
            for (int i = 0; i < DroneLayout.SLOTS; i++) {
                glows[i] = buildLedGlow(f, layout, i);
            }
            return glows;
        })[slot];
    }

    public static synchronized Shape ledHalo(FrameType frame, DroneLayout layout, int slot) {
        return LED_HALOS.computeIfAbsent(frame, f -> {
            Shape[] halos = new Shape[DroneLayout.SLOTS];
            for (int i = 0; i < DroneLayout.SLOTS; i++) {
                halos[i] = buildLedHalo(f, layout, i);
            }
            return halos;
        })[slot];
    }

    static Shape buildLedHalo(FrameType frame, DroneLayout layout, int slot) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float[] box = ledBox(frame, layout, slot);
        if (box != null) {
            float radius = Math.max(ARM_HALO_MM, box[5] * 0.9F);
            b.glow(box[0], box[1] - box[4] * 0.5F, box[2], radius, GLOW_CORE, GLOW_MID, GLOW_EDGE);
        }
        return b.build();
    }

    public static synchronized Shape stack(StackType stack) {
        return STACKS.computeIfAbsent(stack, FallbackShapes::buildStack);
    }

    public static synchronized Shape motor(MotorType motor) {
        return MOTORS.computeIfAbsent(motor, FallbackShapes::buildMotor);
    }

    public static synchronized Shape prop(PropType prop, boolean counterClockwise) {
        Shape[] spins = PROPS.computeIfAbsent(prop, k -> new Shape[2]);
        int index = counterClockwise ? 1 : 0;
        if (spins[index] == null) {
            spins[index] = buildProp(prop, counterClockwise);
        }
        return spins[index];
    }

    public static synchronized Shape propIcon(PropType prop) {
        return PROP_ICONS.computeIfAbsent(prop, p -> buildProp(p, false, ICON_CHORD_SCALE));
    }

    public static synchronized Shape propWide(PropType prop, boolean counterClockwise) {
        Shape[] spins = WIDE_PROPS.computeIfAbsent(prop, k -> new Shape[2]);
        int index = counterClockwise ? 1 : 0;
        if (spins[index] == null) {
            spins[index] = buildProp(prop, counterClockwise, ICON_CHORD_SCALE);
        }
        return spins[index];
    }

    public static synchronized Shape blur(PropType prop) {
        return BLURS.computeIfAbsent(prop, FallbackShapes::buildBlur);
    }

    public static synchronized Shape motorGlow(MotorType motor) {
        return MOTOR_GLOWS.computeIfAbsent(motor, FallbackShapes::buildMotorGlow);
    }

    public static synchronized Shape battery(BatteryType battery) {
        return BATTERIES.computeIfAbsent(battery, FallbackShapes::buildBattery);
    }

    public static synchronized Shape camera(VideoType video) {
        return CAMERAS.computeIfAbsent(video, FallbackShapes::buildCamera);
    }

    public static synchronized Shape transmitter(VideoType video) {
        return TRANSMITTERS.computeIfAbsent(video, FallbackShapes::buildTransmitter);
    }

    public static synchronized Shape accessory(AccessoryType accessory) {
        return ACCESSORIES.computeIfAbsent(accessory, FallbackShapes::buildAccessory);
    }

    public static synchronized Shape accessoryBase(AccessoryMount mount) {
        return ACCESSORY_BASES.computeIfAbsent(mount, FallbackShapes::buildAccessoryBase);
    }

    public static float armThickness(FrameType frame) {
        if (frame.wheelbaseMm >= 600) {
            return 6.0F;
        }
        return frame.wheelbaseMm >= 200 ? 3.0F : frame.wheelbaseMm >= 100 ? 2.5F : 1.5F;
    }

    public static float armWidth(FrameType frame) {
        return Math.min(MAX_ARM_WIDTH_MM, Math.max(4.0F, 0.06F * frame.wheelbaseMm));
    }

    public static float bodySpan(FrameType frame) {
        float wb = frame.wheelbaseMm;
        return wb <= BODY_SPAN_KNEE_MM ? wb : BODY_SPAN_KNEE_MM + 0.3F * (wb - BODY_SPAN_KNEE_MM);
    }

    public static float hubDiameter(PropType prop) {
        return FpvRig.hubDiameterMm(prop);
    }

    public static float hubHeight(PropType prop) {
        return FpvRig.hubHeightMm(prop);
    }

    public static float cameraSize(VideoType video) {
        return (float) video.definition().cameraWidthMm();
    }

    public static float[] batteryDimensions(BatteryType battery) {
        Optional<PackSize> pack = battery.definition().packSize();
        if (pack.isPresent()) {
            PackSize size = pack.get();
            return new float[]{(float) size.widthMm(), (float) size.heightMm(), (float) size.lengthMm()};
        }
        double density;
        if (battery.chemistry == BatteryType.Chemistry.LION) {
            density = 2.4;
        } else if (battery.chemistry == BatteryType.Chemistry.LIPO || battery.chemistry == BatteryType.Chemistry.LIHV) {
            density = 2.1;
        } else {
            density = battery.chemistry.definition().densityGramsPerCm3();
        }
        double volumeCm3 = battery.massGrams() / density;
        double height = Math.cbrt(volumeCm3 / 2.86) * 10.0;
        return new float[]{(float) (1.3 * height), (float) height, (float) (2.2 * height)};
    }

    public static int frameBodyColor(FrameType frame) {
        return frame.ducted && frame.wheelbaseMm < 100 ? WHOOP_PLASTIC : CARBON;
    }

    public static int frameIconColor(FrameType frame) {
        int body = frameBodyColor(frame);
        return body == CARBON ? ICON_CARBON : body;
    }

    public static int ductColor(FrameType frame) {
        return frame.wheelbaseMm < 100 ? WHOOP_PLASTIC : DUCT_PLASTIC;
    }

    public static int motorColor(MotorType motor) {
        return 0xFF000000 | motor.definition().basics().visual().accentRgb().orElse(0xB9BEC7);
    }

    public static int propColor(PropType prop) {
        OptionalInt accent = prop.definition().basics().visual().accentRgb();
        if (accent.isPresent()) {
            return 0xFF000000 | accent.getAsInt();
        }
        return switch (prop.definition().material()) {
            case CARBON -> 0xFF2B2C31;
            case CARBON_FIBER_NYLON -> 0xFF3A3C42;
            case GLASS_FIBER_NYLON -> 0xFF5C6068;
            default -> 0xFFE4E4EA;
        };
    }

    public static int batteryColor(BatteryType battery) {
        if (!battery.builtin()) {
            OptionalInt accent = battery.definition().basics().visual().accentRgb();
            if (accent.isPresent()) {
                return 0xFF000000 | accent.getAsInt();
            }
        }
        if (battery.chemistry == BatteryType.Chemistry.LION) {
            return 0xFF2F6D3A;
        }
        return switch (battery.cells) {
            case 1 -> 0xFF6A3FA0;
            case 4 -> 0xFF2E3A8C;
            default -> 0xFF1F1F23;
        };
    }

    static Shape buildFrame(FrameType frame, DroneLayout layout) {
        return buildFrame(frame, layout, false);
    }

    static Shape buildFrame(FrameType frame, DroneLayout layout, boolean icon) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float wb = frame.wheelbaseMm;
        float span = bodySpan(frame);
        float armThick = icon ? Math.max(armThickness(frame), ICON_ARM_THICKNESS_RATIO * wb) : armThickness(frame);
        float armWidth = icon ? Math.max(armWidth(frame), ICON_ARM_RATIO * wb) : armWidth(frame);
        int body = icon ? frameIconColor(frame) : frameBodyColor(frame);
        int plates = icon ? ICON_CARBON : CARBON;
        float motorY = averageMotorHeight(layout);
        float armTop = motorY;
        float armBottom = Math.min(0.0F, motorY - armThick);
        if (armTop - armBottom < 1.0F) {
            armBottom = armTop - armThick;
        }
        float armMid = (armTop + armBottom) * 0.5F;
        float padRadius = Math.min(MAX_PAD_RADIUS_MM, Math.max(3.5F, 0.07F * wb));
        if (icon) {
            padRadius = Math.max(padRadius, ICON_PAD_RATIO * wb);
        }
        boolean hFrame = frame.definition().shape() == FrameShape.H_FRAME;
        if (hFrame) {
            float railLeft = 0.0F;
            float railRight = 0.0F;
            for (int side = 0; side < 2; side++) {
                Vector3fc front = layout.motor(side == 0 ? DroneLayout.FRONT_LEFT : DroneLayout.FRONT_RIGHT);
                Vector3fc rear = layout.motor(side == 0 ? DroneLayout.REAR_LEFT : DroneLayout.REAR_RIGHT);
                float dx = rear.x() - front.x();
                float dz = rear.z() - front.z();
                float length = (float) Math.sqrt(dx * dx + dz * dz) + armWidth * 0.7F;
                float yaw = (float) Math.atan2(dx, dz);
                b.cube(ShapeBuilder.at((front.x() + rear.x()) * 0.5F, armMid, (front.z() + rear.z()) * 0.5F).rotateY(yaw)
                        .scale(armWidth, armTop - armBottom, length), body);
                float railX = (front.x() + rear.x()) * 0.5F;
                if (side == 0) {
                    railLeft = railX;
                } else {
                    railRight = railX;
                }
            }
            float beam = Math.max(armWidth, 0.12F * span);
            b.boxBetween(Math.min(railLeft, railRight), armBottom, -beam, Math.max(railLeft, railRight), armTop, beam, body);
        }
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            Vector3fc m = layout.motor(slot);
            float dist = (float) Math.sqrt(m.x() * m.x() + m.z() * m.z());
            if (dist < 1.0F) {
                continue;
            }
            if (!hFrame) {
                float start = 0.12F * dist;
                float end = dist + armWidth * 0.35F;
                float mid = (start + end) * 0.5F;
                float yaw = (float) Math.atan2(m.x(), m.z());
                float dx = m.x() / dist;
                float dz = m.z() / dist;
                b.cube(ShapeBuilder.at(dx * mid, armMid, dz * mid).rotateY(yaw).scale(armWidth, armTop - armBottom, end - start), body);
            }
            b.cylinder(ShapeBuilder.at(m.x(), armMid, m.z()).scale(padRadius * 2.0F, armTop - armBottom, padRadius * 2.0F), 12, body);
        }
        if (frame.ducted) {
            float ringRadius = frame.maxPropMm * 0.5F + 1.5F;
            float wall = Math.max(0.8F, 0.02F * frame.maxPropMm);
            float ductBottom = armBottom;
            float ductTop = motorY + DroneLayout.defaultPropHeight(DroneLayout.typicalMotor(frame)) + 2.0F;
            int segments = frame.maxPropMm > 150 ? 28 : 18;
            float segmentLength = (float) (2.0 * Math.PI * ringRadius / segments) * 1.08F;
            int duct = ductColor(frame);
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                Vector3fc m = layout.motor(slot);
                for (int i = 0; i < segments; i++) {
                    float angle = (float) (2.0 * Math.PI * i / segments);
                    float x = m.x() + (float) Math.cos(angle) * ringRadius;
                    float z = m.z() - (float) Math.sin(angle) * ringRadius;
                    b.cube(ShapeBuilder.at(x, (ductTop + ductBottom) * 0.5F, z).rotateY(angle)
                            .scale(wall, ductTop - ductBottom, segmentLength), duct);
                }
            }
            b.boxBetween(-0.24F * span, armBottom, -0.3F * span, 0.24F * span, armTop, 0.3F * span, body);
        } else {
            b.boxBetween(-0.12F * span, -2.0F, -0.3F * span, 0.12F * span, 0.0F, 0.3F * span, plates);
        }
        float topPlateTop = topPlateHeight(frame, layout);
        if (topPlateTop > 8.0F) {
            float topPlateBottom = topPlateTop - 2.0F;
            b.boxBetween(-0.09F * span, topPlateBottom, -0.24F * span, 0.09F * span, topPlateTop, 0.24F * span, plates);
            float sx = 0.07F * span;
            float sz = 0.16F * span;
            float standoffBottom = Math.max(0.0F, armTop);
            for (int i = 0; i < 4; i++) {
                float x = (i & 1) == 0 ? -sx : sx;
                float z = (i & 2) == 0 ? -sz : sz;
                b.cylinder(ShapeBuilder.at(x, (standoffBottom + topPlateBottom) * 0.5F, z).scale(5.0F, topPlateBottom - standoffBottom, 5.0F), 8, STANDOFF);
            }
            Vector3fc video = layout.video();
            float cageHeight = Math.max(8.0F, video.y() + 10.0F);
            float cageDepth = 0.16F * span;
            float cageX = Math.min(0.1F * span, MAX_CAGE_HALF_WIDTH_MM);
            for (int side = -1; side <= 1; side += 2) {
                b.boxBetween(side * cageX - 1.0F, 0.0F, video.z() - cageDepth * 0.35F, side * cageX + 1.0F, cageHeight,
                        video.z() + cageDepth * 0.65F, plates);
            }
        }
        return b.build();
    }

    static float topPlateHeight(FrameType frame, DroneLayout layout) {
        if (!layout.batteryBelow()) {
            return layout.battery().y();
        }
        if (!AirframeGeometry.defaultBatteryOnTop(frame.wheelbaseMm)) {
            return Float.NaN;
        }
        return Math.max(10.0F, 2.0F * DroneLayout.electronicsHeight(frame));
    }

    static Shape buildLeds(FrameType frame, DroneLayout layout) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            float[] box = ledBox(frame, layout, slot);
            if (box == null) {
                continue;
            }
            b.cube(ShapeBuilder.at(box[0], box[1], box[2]).rotateY(box[6]).scale(box[3], box[4], box[5]), LED_OFF);
        }
        return b.build();
    }

    static Shape buildLedGlow(FrameType frame, DroneLayout layout, int slot) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float[] box = ledBox(frame, layout, slot);
        if (box != null) {
            b.cube(ShapeBuilder.at(box[0], box[1], box[2]).rotateY(box[6]).scale(box[3] + 0.6F, box[4] + 0.6F, box[5] + 0.6F), 0xFFFFFFFF);
        }
        return b.build();
    }

    static float[] ledBox(FrameType frame, DroneLayout layout, int slot) {
        Vector3fc m = layout.motor(slot);
        float dist = (float) Math.sqrt(m.x() * m.x() + m.z() * m.z());
        if (dist < 1.0F) {
            return null;
        }
        float armThick = armThickness(frame);
        float motorY = averageMotorHeight(layout);
        float armBottom = Math.min(0.0F, motorY - armThick);
        float along = 0.62F * dist;
        float length = Math.max(3.0F, 0.2F * dist);
        float width = Math.max(1.5F, 0.35F * armWidth(frame));
        float height = Math.max(0.8F, 0.4F * armThick);
        float yaw = (float) Math.atan2(m.x(), m.z());
        return new float[]{m.x() / dist * along, armBottom - height * 0.5F, m.z() / dist * along, width, height, length, yaw};
    }

    static Shape buildStack(StackType stack) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float size = boardSize(stack);
        float half = size * 0.5F;
        float hole = size * 0.5F - 3.0F;
        if (!stack.definition().allInOne()) {
            b.boxBetween(-half, 1.5F, -half, half, 3.1F, half, ESC_BOARD);
            b.boxBetween(-half, 8.0F, -half, half, 9.6F, half, FC_BOARD);
            for (int i = 0; i < 4; i++) {
                float x = (i & 1) == 0 ? -hole : hole;
                float z = (i & 2) == 0 ? -hole : hole;
                b.cylinder(ShapeBuilder.at(x, 5.5F, z).scale(2.4F, 5.0F, 2.4F), 6, BRASS);
            }
            b.box(0.0F, 10.1F, 0.0F, 7.0F, 1.0F, 7.0F, CHIP);
            b.cylinder(ShapeBuilder.at(0.0F, 5.0F, half + 6.0F).rotateX((float) (Math.PI / 2.0)).scale(9.0F, 14.0F, 9.0F), 10, CAPACITOR);
        } else {
            float y = size >= LARGE_AIO_MM ? 2.0F : 1.0F;
            b.boxBetween(-half, y, -half, half, y + 1.6F, half, FC_BOARD);
            b.box(0.0F, y + 2.1F, 0.0F, size * 0.22F, 1.0F, size * 0.22F, CHIP);
            b.box(-size * 0.25F, y + 2.0F, size * 0.2F, size * 0.18F, 0.8F, size * 0.14F, CHIP);
            b.box(size * 0.25F, y + 2.0F, size * 0.2F, size * 0.18F, 0.8F, size * 0.14F, CHIP);
            if (size >= LARGE_AIO_MM) {
                b.cylinder(ShapeBuilder.at(0.0F, y + 3.5F, half + 4.0F).rotateX((float) (Math.PI / 2.0)).scale(6.0F, 10.0F, 6.0F), 10, CAPACITOR);
            }
        }
        return b.build();
    }

    static Shape buildMotor(MotorType motor) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float stator = DroneLayout.statorDiameter(motor);
        float bell = DroneLayout.bellDiameter(motor);
        float bellHeight = DroneLayout.bellHeight(motor);
        float baseHeight = Math.min(2.5F, bellHeight * 0.25F);
        b.cylinder(ShapeBuilder.at(0.0F, baseHeight * 0.5F, 0.0F).scale(stator + 2.0F, baseHeight, stator + 2.0F), 14, MOTOR_BASE);
        float bellBottom = baseHeight - 0.2F;
        b.cylinder(ShapeBuilder.at(0.0F, (bellBottom + bellHeight) * 0.5F, 0.0F).scale(bell, bellHeight - bellBottom, bell), 16, motorColor(motor));
        b.cylinder(ShapeBuilder.at(0.0F, bellHeight + 0.3F, 0.0F).scale(bell * 0.72F, 0.6F, bell * 0.72F), 16, MOTOR_CAP);
        float shaft = Math.max(1.0F, 0.16F * stator);
        b.cylinder(ShapeBuilder.at(0.0F, bellHeight + 1.5F, 0.0F).scale(shaft, 3.0F, shaft), 8, STEEL);
        return b.build();
    }

    static Shape buildProp(PropType prop, boolean counterClockwise) {
        return buildProp(prop, counterClockwise, 1.0F);
    }

    static Shape buildProp(PropType prop, boolean counterClockwise, float chordScale) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float radius = prop.diameterMm * 0.5F;
        float hub = hubDiameter(prop);
        float hubHeight = hubHeight(prop);
        int color = propColor(prop);
        b.cylinder(ShapeBuilder.at(0.0F, hubHeight * 0.5F, 0.0F).scale(hub, hubHeight, hub), 12, color);
        if (prop.diameterMm >= 50) {
            b.cylinder(ShapeBuilder.at(0.0F, hubHeight + 1.5F, 0.0F).scale(hub * 0.55F, 3.0F, hub * 0.55F), 6, PROP_NUT);
        }
        float root = hub * 0.4F;
        float span = radius - root;
        float chord = Math.max(2.5F, 0.19F * radius) * chordScale;
        float thickness = prop.diameterMm < 50 ? 0.8F : 1.2F;
        float pitch = (float) Math.toRadians(counterClockwise ? 14.0 : -14.0);
        int blades = Math.max(2, prop.blades);
        for (int i = 0; i < blades; i++) {
            float angle = (float) (2.0 * Math.PI * i / blades);
            float innerLength = span * 0.62F;
            float outerLength = span - innerLength;
            Matrix4f inner = new Matrix4f().rotateY(angle).translate(root + innerLength * 0.5F, hubHeight * 0.55F, 0.0F)
                    .rotateX(pitch).scale(innerLength, thickness, chord);
            Matrix4f outer = new Matrix4f().rotateY(angle).translate(root + innerLength + outerLength * 0.5F, hubHeight * 0.55F, 0.0F)
                    .rotateX(pitch * 0.7F).scale(outerLength, thickness * 0.8F, chord * 0.72F);
            b.cube(inner, color);
            b.cube(outer, color);
        }
        return b.build();
    }

    static Shape buildBlur(PropType prop) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float radius = prop.diameterMm * 0.5F;
        float hub = hubDiameter(prop);
        int rgb = propColor(prop) & 0x00FFFFFF;
        int inner = 0x78000000 | rgb;
        int outer = 0x30000000 | rgb;
        float y = hubHeight(prop) * 0.55F;
        b.annulus(ShapeBuilder.at(0.0F, y, 0.0F).scale(radius * 2.0F, 1.0F, radius * 2.0F), hub / (radius * 2.0F), 28, inner, outer);
        b.cylinder(ShapeBuilder.at(0.0F, hubHeight(prop) * 0.5F, 0.0F).scale(hub, hubHeight(prop), hub), 12, 0xFF000000 | rgb);
        return b.build();
    }

    static Shape buildBattery(BatteryType battery) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float[] d = batteryDimensions(battery);
        float w = d[0];
        float h = d[1];
        float l = d[2];
        b.box(0.0F, h * 0.5F, 0.0F, w, h, l, batteryColor(battery));
        b.box(0.0F, h * 0.5F, -l * 0.1F, w + 0.4F, h + 0.4F, l * 0.3F, 0xFFF2C230);
        if (battery.cells > 1) {
            b.box(0.0F, h * 0.5F - 1.0F, l * 0.22F, w + 1.2F, h + 3.0F, Math.max(6.0F, l * 0.16F), STRAP);
            b.box(0.0F, h * 0.35F, l * 0.5F + 4.0F, 8.0F, 6.0F, 8.0F, CONNECTOR);
        }
        return b.build();
    }

    static Shape buildCamera(VideoType video) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float c = cameraSize(video);
        b.box(0.0F, 0.0F, 0.0F, c, c, c * 0.9F, CAMERA);
        float lensLength = c * 0.45F;
        float lensZ = -c * 0.45F - lensLength * 0.5F;
        Matrix4f lens = ShapeBuilder.at(0.0F, 0.0F, lensZ).rotateX((float) (Math.PI / 2.0)).scale(c * 0.72F, lensLength, c * 0.72F);
        b.cylinder(lens, 14, LENS);
        Matrix4f glass = ShapeBuilder.at(0.0F, 0.0F, lensZ - lensLength * 0.5F - 0.3F).rotateX((float) (Math.PI / 2.0))
                .scale(c * 0.5F, 0.6F, c * 0.5F);
        b.cylinder(glass, 14, GLASS);
        return b.build();
    }

    static Shape buildTransmitter(VideoType video) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        boolean digital = video.system == VideoSystem.DIGITAL;
        boolean large = largeTransmitter(video);
        boolean small = smallTransmitter(video);
        float w = digital ? (large ? 30.0F : 22.0F) : (small ? 14.0F : 20.0F);
        float h = digital ? (large ? 12.0F : 7.0F) : 4.0F;
        float l = digital ? (large ? 34.0F : 24.0F) : (small ? 14.0F : 24.0F);
        b.box(0.0F, h * 0.5F, 0.0F, w, h, l, TRANSMITTER);
        if (digital) {
            b.cylinder(ShapeBuilder.at(0.0F, h + 0.3F, -l * 0.1F).scale(w * 0.5F, 0.6F, w * 0.5F), 12, MOTOR_CAP);
        }
        float antennaLength = small ? 18.0F : 34.0F;
        int antennas = digital ? 2 : 1;
        for (int i = 0; i < antennas; i++) {
            float roll = antennas == 1 ? 0.0F : (i == 0 ? 0.45F : -0.45F);
            float x = antennas == 1 ? 0.0F : (i == 0 ? -w * 0.3F : w * 0.3F);
            Matrix4f antenna = ShapeBuilder.at(x, h, l * 0.5F - 2.0F).rotateX(0.55F).rotateZ(roll)
                    .translate(0.0F, antennaLength * 0.5F, 0.0F);
            b.cylinder(new Matrix4f(antenna).scale(2.2F, antennaLength, 2.2F), 6, ANTENNA);
            b.cylinder(new Matrix4f(antenna).translate(0.0F, antennaLength * 0.5F, 0.0F).scale(5.0F, 3.0F, 5.0F), 8, ANTENNA_CAP);
        }
        return b.build();
    }

    public static synchronized Shape[] stripGlows(AccessoryType accessory) {
        return STRIP_GLOWS.computeIfAbsent(accessory, type -> buildStripLeds(type, false));
    }

    public static synchronized Shape[] stripHalos(AccessoryType accessory) {
        return STRIP_HALOS.computeIfAbsent(accessory, type -> buildStripLeds(type, true));
    }

    static Shape[] buildStripLeds(AccessoryType accessory, boolean halo) {
        int count = Math.min(accessory.ledCount, DroneRenderState.MAX_STRIP_LEDS);
        Shape[] out = new Shape[Math.max(0, count)];
        float pitch = (float) (accessory.widthMm / Math.max(1, accessory.ledCount));
        for (int i = 0; i < out.length; i++) {
            double[] led = accessory.ledMm(i, accessory.widthMm);
            ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
            float x = (float) led[0];
            float y = (float) led[1];
            float z = (float) led[2];
            if (halo) {
                b.glow(x, y - 0.6F, z, Math.max(STRIP_HALO_MM, pitch * 1.6F), GLOW_CORE, GLOW_MID, GLOW_EDGE);
            } else {
                b.box(x, y - 0.2F, z, 3.0F, 0.8F, 3.0F, 0xFFFFFFFF);
            }
            out[i] = b.build();
        }
        return out;
    }

    static Shape buildAccessory(AccessoryType accessory) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        double[] min = accessory.boxMinMm();
        double[] max = accessory.boxMaxMm();
        float x0 = (float) min[0];
        float y0 = (float) min[1];
        float z0 = (float) min[2];
        float x1 = (float) max[0];
        float y1 = (float) max[1];
        float z1 = (float) max[2];
        if (accessory.isLight()) {
            b.boxBetween(x0, y0, z0, x1, y1, z1, ACCESSORY_CHANNEL);
            for (int i = 0; i < accessory.ledCount; i++) {
                double[] led = accessory.ledMm(i, accessory.widthMm);
                b.box((float) led[0], (float) led[1] - 0.2F, (float) led[2], 2.4F, 0.4F, 2.4F, ACCESSORY_LED);
            }
            return b.build();
        }
        b.boxBetween(-1.4F, -2.6F, -4.5F, 1.4F, y0, 4.5F, ACCESSORY_HOUSING);
        if (accessory.camera().map(camera -> camera.ownBattery()).orElse(false)) {
            b.boxBetween(x0, y0, z0, x1, y1, z1, ACCESSORY_HOUSING);
            b.boxBetween(x0 + 1.5F, y0 + 1.5F, z0 - 0.2F, x1 - 1.5F, y1 - 1.5F, z1 + 0.2F, ACCESSORY_BODY);
        } else {
            b.boxBetween(x0, y0, z0, x1, y0 + 8.0F, z1, ACCESSORY_HOUSING);
            b.boxBetween(x0, y1 - 9.0F, z0, x1, y1, z1, ACCESSORY_HEATSINK);
            b.boxBetween(-9.0F, y0 + 6.0F, z0, 9.0F, y1 - 6.0F, z0 + 6.0F, CAMERA);
        }
        double[] lens = accessory.lensMm();
        float lensLength = (float) accessory.lensLengthMm;
        float lensDiameter = (float) Math.min(18.0, accessory.heightMm * 0.45);
        Matrix4f barrel = ShapeBuilder.at((float) lens[0], (float) lens[1], (float) lens[2] + lensLength * 0.5F)
                .rotateX((float) (Math.PI / 2.0)).scale(lensDiameter, lensLength, lensDiameter);
        b.cylinder(barrel, 14, LENS);
        Matrix4f glass = ShapeBuilder.at((float) lens[0], (float) lens[1], (float) lens[2] - 0.3F).rotateX((float) (Math.PI / 2.0))
                .scale(lensDiameter * 0.7F, 0.6F, lensDiameter * 0.7F);
        b.cylinder(glass, 14, GLASS);
        return b.build();
    }

    static Shape buildAccessoryBase(AccessoryMount mount) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        b.boxBetween(-14.6F, -8.7F, -8.0F, 14.6F, -6.0F, 12.0F, ACCESSORY_TPU);
        for (float side : new float[]{-1.0F, 1.0F}) {
            b.boxBetween(side * 12.8F, -15.0F, -5.0F, side * 14.6F, -8.7F, 12.0F, ACCESSORY_TPU);
        }
        b.boxBetween(-8.5F, -6.0F, -7.0F, 8.5F, -3.0F, 7.0F, ACCESSORY_TPU);
        for (float x : new float[]{-6.0F, 0.0F, 6.0F}) {
            b.boxBetween(x - 1.5F, -3.0F, -4.5F, x + 1.5F, 3.0F, 4.5F, ACCESSORY_TPU);
        }
        b.cylinder(ShapeBuilder.at(10.5F, 0.0F, 0.0F).rotateZ((float) (Math.PI / 2.0)).scale(8.5F, 4.0F, 8.5F), 12, BRASS);
        return b.build();
    }

    public static float stackHeight(StackType stack) {
        if (!stack.definition().allInOne()) {
            return 10.6F;
        }
        return boardSize(stack) >= LARGE_AIO_MM ? 4.6F : 3.6F;
    }

    public static float boardSize(StackType stack) {
        OptionalDouble board = stack.definition().boardSizeMm();
        if (board.isPresent()) {
            return (float) board.getAsDouble();
        }
        if (stack.mount == MountSize.Stack.WHOOP_AIO) {
            return 26.0F;
        }
        if (stack.mount == MountSize.Stack.S20) {
            return 27.0F;
        }
        return stack.mount == MountSize.Stack.S30 ? 36.0F : 30.0F;
    }

    static boolean largeTransmitter(VideoType video) {
        if (video.builtin()) {
            return video == VideoType.DIGITAL_HD;
        }
        return video.massGrams() >= 15.0 || video.definition().vtxPowerMw() > 600;
    }

    static boolean smallTransmitter(VideoType video) {
        if (video.builtin()) {
            return video == VideoType.ANALOG_NANO;
        }
        return video.definition().cameraWidthMm() <= 13.0;
    }

    static Shape buildMotorGlow(MotorType motor) {
        ShapeBuilder b = new ShapeBuilder(DroneVisualScale.MM_TO_UNITS);
        float outer = DroneLayout.bellDiameter(motor) + 7.0F;
        float inner = DroneLayout.bellDiameter(motor) + 1.0F;
        b.annulus(ShapeBuilder.at(0.0F, 0.25F, 0.0F).scale(outer, 1.0F, outer), inner / outer, 24, 0xFFFFFFFF, 0x10FFFFFF);
        return b.build();
    }

    static float averageMotorHeight(DroneLayout layout) {
        float sum = 0.0F;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            sum += layout.motor(slot).y();
        }
        return sum / DroneLayout.SLOTS;
    }
}
