package de.lardtom.propwash.bando.client;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BandoCamera {
    private static final int ANGLES = 16;
    private static final double TURN = 22.5;
    private static final int LIFTS = 3;
    private static final double LIFT = 8.0;
    private static final double CLEARANCE = 4.0;
    private static final double STEP = 0.25;
    private static final int ENCLOSED = 1000;
    private static final int NEAR = 3;

    record Pose(Vec3 eye, float yaw, float pitch, int blocked, double angle) {
    }

    private BandoCamera() {
    }

    static Pose aerial(ServerLevel level, BoundingBox bando, Vec3 target, double angle, double distance, double height) {
        List<Vec3> marks = marks(bando, target);
        Pose best = null;
        for (int k = 0; k < ANGLES; k++) {
            int side = (k + 1) / 2;
            double turn = Math.toRadians(TURN * side * (k % 2 == 0 ? 1 : -1));
            double a = angle + turn;
            for (int lift = 0; lift < LIFTS; lift++) {
                Vec3 eye = target.add(Math.sin(a) * distance, height + lift * LIFT, Math.cos(a) * distance);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(eye.x), (int) Math.floor(eye.z));
                if (eye.y < ground + CLEARANCE) {
                    eye = new Vec3(eye.x, ground + CLEARANCE, eye.z);
                }
                int blocked = crowded(level, eye);
                for (Vec3 mark : marks) {
                    blocked += blocked(level, bando, eye, mark);
                }
                Pose pose = pose(eye, target, blocked, a);
                if (blocked == 0) {
                    return pose;
                }
                if (best == null || blocked < best.blocked()) {
                    best = pose;
                }
            }
        }
        return best;
    }

    static Pose pose(Vec3 eye, Vec3 target, int blocked, double angle) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(target.x - eye.x), target.z - eye.z));
        double horizontal = Math.sqrt((target.x - eye.x) * (target.x - eye.x) + (target.z - eye.z) * (target.z - eye.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, horizontal));
        return new Pose(eye, yaw, pitch, blocked, angle);
    }

    private static List<Vec3> marks(BoundingBox bando, Vec3 target) {
        List<Vec3> marks = new ArrayList<>();
        marks.add(target);
        double low = Math.min(target.y, bando.minY() + 1.5);
        for (double y : new double[]{low, bando.maxY() + 0.5}) {
            marks.add(new Vec3(bando.minX() + 0.5, y, bando.minZ() + 0.5));
            marks.add(new Vec3(bando.maxX() + 0.5, y, bando.minZ() + 0.5));
            marks.add(new Vec3(bando.minX() + 0.5, y, bando.maxZ() + 0.5));
            marks.add(new Vec3(bando.maxX() + 0.5, y, bando.maxZ() + 0.5));
        }
        return marks;
    }

    private static int crowded(ServerLevel level, Vec3 eye) {
        BlockPos at = BlockPos.containing(eye);
        int crowded = 0;
        for (int dx = -NEAR; dx <= NEAR; dx++) {
            for (int dy = -NEAR; dy <= NEAR; dy++) {
                for (int dz = -NEAR; dz <= NEAR; dz++) {
                    if (occludes(level.getBlockState(at.offset(dx, dy, dz)))) {
                        crowded += Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) <= 1 ? ENCLOSED : 1;
                    }
                }
            }
        }
        return crowded;
    }

    private static int blocked(ServerLevel level, BoundingBox bando, Vec3 eye, Vec3 mark) {
        BoundingBox inside = bando.inflatedBy(1);
        Set<BlockPos> seen = new HashSet<>();
        int blocked = 0;
        double length = eye.distanceTo(mark);
        for (double d = 0.0; d < length; d += STEP) {
            BlockPos at = BlockPos.containing(eye.lerp(mark, d / length));
            if (inside.isInside(at) || !seen.add(at)) {
                continue;
            }
            if (occludes(level.getBlockState(at))) {
                blocked++;
            }
        }
        return blocked;
    }

    private static boolean occludes(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.BAMBOO) || state.canOcclude();
    }
}
