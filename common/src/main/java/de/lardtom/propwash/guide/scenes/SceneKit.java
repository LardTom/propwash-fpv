package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.ScenePos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SceneKit {
    public static final String NAMESPACE = "propwash:";
    public static final String WORKBENCH = NAMESPACE + "drone_workbench";
    public static final String CHARGER = NAMESPACE + "battery_charger";
    public static final String DRONE_ITEM = NAMESPACE + "drone";
    public static final String REMOTE = NAMESPACE + "remote";
    public static final String GOGGLES_ANALOG = NAMESPACE + "goggles_analog";
    public static final String GOGGLES_DIGITAL = NAMESPACE + "goggles_digital";
    public static final String GOGGLES_CREATIVE = NAMESPACE + "goggles_creative";
    public static final String GATE_SMALL = NAMESPACE + "gate_small";
    public static final String GATE_MEDIUM = NAMESPACE + "gate_medium";
    public static final String GATE_LARGE = NAMESPACE + "gate_large";
    public static final String COURSE_TOOL = NAMESPACE + "course_tool";
    public static final String RACE_FLAG = NAMESPACE + "race_flag";
    public static final String SLALOM_POLE = NAMESPACE + "slalom_pole";
    public static final String DIVE_CUBE = NAMESPACE + "dive_cube";
    public static final String FREEFORM_GATE = NAMESPACE + "freeform_gate";
    public static final String GATE_POLE = NAMESPACE + "gate_pole";
    public static final String BARRIER = "minecraft:barrier";
    public static final String CLOCK = "minecraft:clock";

    public static final String KEY_ARM = "key.propwash.drone_arm";
    public static final String KEY_FLIGHT_MODE = "key.propwash.drone_flight_mode";
    public static final String KEY_FLIP = "key.propwash.drone_flip_upright";
    public static final String KEY_EXIT = "key.propwash.drone_exit";
    public static final String KEY_BEEPER = "key.propwash.beeper";
    public static final String KEY_RESCUE = "key.propwash.rescue";
    public static final String KEY_SET_HOME = "key.propwash.set_home";
    public static final String KEY_RACE_RESTART = "key.propwash.race_restart";
    public static final String KEY_GATE_ROTATE = "key.propwash.gate_rotate";
    public static final String KEY_CONFIGURATOR = "key.propwash.configurator";
    public static final String KEY_RECORD = "key.propwash.action_cam_record";
    public static final String KEY_ALT_HOLD = "key.propwash.drone_alt_hold";
    public static final String KEY_POS_HOLD = "key.propwash.drone_pos_hold";
    public static final String KEY_MODE_3D = "key.propwash.drone_mode_3d";
    public static final String KEY_CARGO_DROP = "key.propwash.drone_cargo_drop";
    public static final String KEY_INVENTORY = "key.inventory";

    public static final BlockSpec GRASS = BlockSpec.of("minecraft:grass_block");
    public static final BlockSpec MOSS = BlockSpec.of("minecraft:moss_block");
    public static final BlockSpec STONE = BlockSpec.of("minecraft:stone");
    public static final BlockSpec STONE_BRICKS = BlockSpec.of("minecraft:stone_bricks");
    public static final BlockSpec SMOOTH_STONE = BlockSpec.of("minecraft:smooth_stone");
    public static final BlockSpec POLISHED_DEEPSLATE = BlockSpec.of("minecraft:polished_deepslate");
    public static final BlockSpec OAK_PLANKS = BlockSpec.of("minecraft:oak_planks");
    public static final BlockSpec SPRUCE_PLANKS = BlockSpec.of("minecraft:spruce_planks");
    public static final BlockSpec OAK_LOG = BlockSpec.of("minecraft:oak_log");
    public static final BlockSpec OAK_LEAVES = BlockSpec.of("minecraft:oak_leaves");
    public static final BlockSpec GRAY_CONCRETE = BlockSpec.of("minecraft:gray_concrete");
    public static final BlockSpec BLACK_CONCRETE = BlockSpec.of("minecraft:black_concrete");
    public static final BlockSpec WORKBENCH_BLOCK = BlockSpec.of(WORKBENCH);
    public static final BlockSpec CRAFTING_TABLE = BlockSpec.of("minecraft:crafting_table");

    public static final float EYE_HEIGHT = 1.62F;
    public static final float HANDS_HEIGHT = 1.0F;
    public static final float HANDS_REACH = 0.38F;
    private static final float CRUISE_SPEED = 0.25F;
    private static final float MAX_BANK = 40.0F;
    private static final float BANK_PER_DEGREE_PER_TICK = 2.2F;

    private SceneKit() {
    }

    public static String part(PartInfo part) {
        return NAMESPACE + part.key();
    }

    public static String[] parts(PartInfo[] parts) {
        return Arrays.stream(parts).map(SceneKit::part).toArray(String[]::new);
    }

    public static String[] batteries() {
        return parts(BatteryType.values());
    }

    public static String[] videos() {
        return parts(VideoType.values());
    }

    public static String[] stacks() {
        return parts(StackType.values());
    }

    public static BlockSpec charger(String facing, String state) {
        String bay = switch (state) {
            case "charging" -> "charging";
            case "done" -> "full";
            default -> "empty";
        };
        return BlockSpec.of(CHARGER).with("facing", facing).with("state", state).with("bay_1", bay);
    }

    public static void ground(SceneBuilder.StepBuilder s, int x0, int z0, int x1, int z1, BlockSpec surface, BlockSpec rim, float stagger,
                              float length) {
        float base = s.now();
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minZ = Math.min(z0, z1);
        int maxZ = Math.max(z0, z1);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                s.at(base + ((x - minX) + (z - minZ)) * stagger).drop(x, 0, z, edge ? rim : surface, length);
            }
        }
        s.at(base);
    }

    public static float pilotYaw(ScenePos feet, ScenePos target) {
        float dx = target.x() - feet.x();
        float dz = target.z() - feet.z();
        if (dx * dx + dz * dz < 1.0E-6F) {
            return 0.0F;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static ScenePos handsOffset(ScenePos feet, ScenePos target) {
        float dx = target.x() - feet.x();
        float dz = target.z() - feet.z();
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4F) {
            return new ScenePos(0.0F, HANDS_HEIGHT, HANDS_REACH);
        }
        return new ScenePos(dx / length * HANDS_REACH, HANDS_HEIGHT, dz / length * HANDS_REACH);
    }

    public static float headPitch(ScenePos feet, ScenePos target) {
        float dx = target.x() - feet.x();
        float dz = target.z() - feet.z();
        float dy = target.y() - (feet.y() + EYE_HEIGHT);
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        return Math.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.max(0.05F, horizontal))), -60.0F, 60.0F);
    }

    public static float heading(ScenePos from, ScenePos to) {
        float dx = to.x() - from.x();
        float dz = to.z() - from.z();
        if (dx * dx + dz * dz < 1.0E-8F) {
            return Float.NaN;
        }
        return (float) Math.toDegrees(Math.atan2(dx, -dz));
    }

    public static void follow(SceneBuilder.StepBuilder s, String pilot, ScenePos feet, ScenePos from, List<ScenePos> path, Ease ease, float start,
                              float length, int samples) {
        int count = Math.max(1, samples);
        float slice = length / count;
        for (int i = 1; i <= count; i++) {
            ScenePos target = SceneEvaluator.path(from, path, ease.apply(i / (float) count));
            float at = start + (i - 1) * slice;
            s.at(at).turn(pilot, pilotYaw(feet, target), 0.0F, 0.0F, slice, Ease.LINEAR);
            s.at(at).look(pilot, headPitch(feet, target), slice, Ease.LINEAR);
        }
    }

    public static void fly(SceneBuilder.StepBuilder s, String drone, ScenePos from, List<ScenePos> path, Ease ease, float start, float length,
                           int samples, float maxTilt) {
        s.at(start).move(drone, length, ease, path.toArray(ScenePos[]::new));
        int count = Math.max(2, samples);
        float slice = length / count;
        List<ScenePos> points = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            points.add(SceneEvaluator.path(from, path, ease.apply(i / (float) count)));
        }
        float lastYaw = Float.NaN;
        for (int i = 1; i <= count; i++) {
            ScenePos a = points.get(i - 1);
            ScenePos b = points.get(i);
            float yaw = heading(a, b);
            if (Float.isNaN(yaw)) {
                yaw = Float.isNaN(lastYaw) ? 0.0F : lastYaw;
            }
            float horizontal = (float) Math.sqrt((b.x() - a.x()) * (b.x() - a.x()) + (b.z() - a.z()) * (b.z() - a.z())) / slice;
            float pitch = -maxTilt * Math.min(1.0F, horizontal / CRUISE_SPEED);
            float turnRate = Float.isNaN(lastYaw) ? 0.0F : wrap(yaw - lastYaw) / slice;
            float roll = Math.clamp(turnRate * BANK_PER_DEGREE_PER_TICK, -MAX_BANK, MAX_BANK);
            if (i == count) {
                pitch *= 0.3F;
                roll = 0.0F;
            }
            s.at(start + (i - 1) * slice).turn(drone, yaw, pitch, roll, slice, Ease.LINEAR);
            lastYaw = yaw;
        }
        s.at(start);
    }

    public static float wrap(float degrees) {
        float d = degrees % 360.0F;
        if (d >= 180.0F) {
            d -= 360.0F;
        }
        if (d < -180.0F) {
            d += 360.0F;
        }
        return d;
    }
}
