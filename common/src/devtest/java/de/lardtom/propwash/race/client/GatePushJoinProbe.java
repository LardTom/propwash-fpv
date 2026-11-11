package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.GatePushJoinCheck;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.GateEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GatePushJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 90;
    private static final int SEEN_SETTLE = 20;
    private static final int AFTER_MACHINE = 80;
    private static final double SAME = 1.0E-3;
    private static final String START = "gatepush_start";
    private static final String RESULT = "gatepush_result";
    private static final String LINES = "gatepush_lines";
    private static final String STAGE = "gatepush_stage";
    private static final String SEEN = "gatepush_seen";
    private static final String B_START = "gatepush_b";
    private static final String MACHINE = "gatepush_machine";

    private GatePushJoinProbe() {
    }

    private static void line(Map<String, Object> state, String text) {
        state.put(LINES, state.getOrDefault(LINES, "") + text + "\n");
    }

    private static String finish(Map<String, Object> state, String last) {
        line(state, last);
        String result = (String) state.get(LINES);
        state.put(RESULT, result);
        return result;
    }

    private static @Nullable CourseData course() {
        for (CourseData course : ClientCourses.all()) {
            if (course.name().equals(GatePushJoinCheck.NAME) && course.gates().size() == 2) {
                return course;
            }
        }
        return null;
    }

    private static @Nullable GateEntity entity(Minecraft mc, CourseData course, int index) {
        if (mc.level == null) {
            return null;
        }
        CourseData.Gate gate = course.gates().get(index);
        Vec3 at = new Vec3(gate.x(), gate.y(), gate.z());
        List<GateEntity> found = mc.level.getEntitiesOfClass(GateEntity.class, new AABB(at, at).inflate(4.0),
                each -> !each.isRemoved() && each.courseId() == course.id() && each.hasPass(index));
        return found.size() == 1 ? found.getFirst() : null;
    }

    private static Vec3 stored(CourseData course, int index) {
        CourseData.Gate gate = course.gates().get(index);
        return new Vec3(gate.x(), gate.y(), gate.z());
    }

    private static String text(Vec3 v) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", v.x, v.y, v.z);
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (state.containsKey(RESULT)) {
            return (String) state.get(RESULT);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int stage = (int) state.getOrDefault(STAGE, 0);
        if (tick - start > TIMEOUT_TICKS) {
            return finish(state, "gatepush timeout stage=" + stage);
        }
        CourseData course = course();
        if (course == null || mc.level == null) {
            return null;
        }
        GateEntity a = entity(mc, course, 0);
        GateEntity b = entity(mc, course, 1);
        if (a == null || b == null) {
            return null;
        }
        if (stage == 0) {
            int seen = (int) state.computeIfAbsent(SEEN, key -> tick);
            if (tick - seen < SEEN_SETTLE) {
                return null;
            }
            Vec3 record = stored(course, 0);
            if (a.position().distanceTo(record) > SAME) {
                return finish(state, "gatepush late join mismatch: Gate A beim Client bei " + text(a.position()) + ", im Kurs bei " + text(record));
            }
            line(state, "gatepush late join ok " + text(a.position()));
            state.put(B_START, b.position());
            AABB box = b.getBoundingBox();
            state.put(MACHINE, new BlockPos((int) Math.floor(box.minX), (int) Math.floor(box.minY) + 1, (int) Math.floor((box.minZ + box.maxZ) * 0.5)));
            state.put(STAGE, 1);
            return null;
        }
        Vec3 before = (Vec3) state.get(B_START);
        if (stage == 1) {
            BlockState pushed = mc.level.getBlockState((BlockPos) state.get(MACHINE));
            if (b.position().distanceTo(before) > SAME) {
                return finish(state, "gatepush present moved: Gate B beim Client von " + text(before) + " nach " + text(b.position()));
            }
            if (!pushed.is(Blocks.SLIME_BLOCK) && !pushed.is(Blocks.MOVING_PISTON)) {
                return null;
            }
            line(state, "gatepush machine seen");
            state.put(SEEN, tick);
            state.put(STAGE, 2);
            return null;
        }
        if (b.position().distanceTo(before) > SAME) {
            return finish(state, "gatepush present moved: Gate B beim Client von " + text(before) + " nach " + text(b.position()));
        }
        if (tick - (int) state.get(SEEN) < AFTER_MACHINE) {
            return null;
        }
        Vec3 record = stored(course, 1);
        if (record.distanceTo(before) > SAME || a.position().distanceTo(stored(course, 0)) > SAME) {
            return finish(state, "gatepush present mismatch: Gate B im Kurs bei " + text(record) + ", beim Client bei " + text(b.position()));
        }
        return finish(state, "gatepush present ok " + text(b.position()));
    }
}
