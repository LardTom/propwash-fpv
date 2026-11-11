package de.lardtom.propwash.interact;

import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CovVineArena {
    public static final int SCENE_DX = 60;
    public static final int BEAM_DX = 4;
    public static final int BEAM_DY = 3;
    public static final int BEAM_HALF = 4;
    private static final int BACK = 9;
    private static final int AHEAD = 15;
    private static final int SIDE = 6;
    private static final int HEIGHT = 10;
    private static final int FLOOR_FROM = 6;
    private static final int FLOOR_TO = 11;
    private static final int WALL_DX = 13;
    public static final double DROP = 1.6;
    private static final double GRAVITY = 9.81;

    private CovVineArena() {
    }

    public enum Pose {
        LEVEL("waagerecht", 0.0, 0.0),
        INVERTED("kopfüber", 180.0, 0.0),
        KNIFE("auf der Seite", 90.0, 0.0),
        INVERTED_NOSE("kopfüber, Nase 30° tief", 180.0, 30.0);

        final String what;
        final double rollDeg;
        final double pitchDeg;

        Pose(String what, double rollDeg, double pitchDeg) {
            this.what = what;
            this.rollDeg = rollDeg;
            this.pitchDeg = pitchDeg;
        }
    }

    public record Flight(double offset, Pose pose, double across, double descent) {
        public String what() {
            return String.format(Locale.ROOT, "%s, Treffpunkt %.2f über der Westkante, %.0f m/s quer und %.0f m/s abwärts", this.pose.what, this.offset,
                    this.across, this.descent);
        }
    }

    public static List<Flight> selfTestFlights() {
        List<Flight> flights = new ArrayList<>();
        for (double[] speed : new double[][]{{3.0, 3.0}, {9.0, 6.0}, {6.0, 10.0}}) {
            for (Pose pose : Pose.values()) {
                for (double offset : new double[]{0.5, 0.15, 0.85, -0.05}) {
                    flights.add(new Flight(offset, pose, speed[0], speed[1]));
                }
            }
        }
        return flights;
    }

    public static List<Flight> joinFlights() {
        List<Flight> flights = new ArrayList<>();
        for (double[] speed : new double[][]{{3.0, 3.0}, {6.0, 10.0}}) {
            for (Pose pose : new Pose[]{Pose.INVERTED, Pose.INVERTED_NOSE, Pose.KNIFE}) {
                for (double offset : new double[]{0.5, -0.05}) {
                    flights.add(new Flight(offset, pose, speed[0], speed[1]));
                }
            }
        }
        return flights;
    }

    public static Quaternionf facing() {
        return new Quaternionf().rotationY((float) Math.toRadians(90.0) + (float) Math.PI);
    }

    public static Vec3 launch(int[] base, Flight flight) {
        double fall = (-flight.descent() + Math.sqrt(flight.descent() * flight.descent() + 2.0 * GRAVITY * DROP)) / GRAVITY;
        double hitX = base[0] + BEAM_DX + flight.offset();
        double top = base[1] + BEAM_DY + 1.0;
        return new Vec3(hitX - flight.across() * fall, top + DROP, base[2] + 0.5);
    }

    public static void launchFlight(DroneSim sim, Flight flight) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = flight.across();
        snapshot.vy = -flight.descent();
        snapshot.vz = 0.0;
        snapshot.lx = 0.0;
        snapshot.ly = 0.0;
        snapshot.lz = 0.0;
        snapshot.sleeping = false;
        snapshot.armed = true;
        Quaterniond q = new Quaterniond(snapshot.qx, snapshot.qy, snapshot.qz, snapshot.qw);
        Pose pose = flight.pose();
        if (pose.pitchDeg != 0.0) {
            Vector3d forward = new Vector3d(0.0, 0.0, 1.0);
            q.transform(forward);
            double sign = forward.x > 0.0 ? 1.0 : -1.0;
            Quaterniond pitched = new Quaterniond(q).rotateX(Math.toRadians(pose.pitchDeg));
            Vector3d check = new Vector3d(0.0, 0.0, sign);
            pitched.transform(check);
            q = check.y > 0.0 ? new Quaterniond(q).rotateX(-Math.toRadians(pose.pitchDeg)) : pitched;
        }
        if (pose.rollDeg != 0.0) {
            q = new Quaterniond(q).rotateZ(Math.toRadians(pose.rollDeg));
        }
        snapshot.qw = q.w;
        snapshot.qx = q.x;
        snapshot.qy = q.y;
        snapshot.qz = q.z;
        sim.copyStateFrom(snapshot);
    }

    public static void build(ServerLevel level, int[] base) {
        int bx = base[0];
        int gy = base[1];
        int bz = base[2];
        for (int x = bx - BACK; x <= bx + AHEAD; x++) {
            for (int z = bz - SIDE; z <= bz + SIDE; z++) {
                level.setBlock(new BlockPos(x, gy - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = gy; y < gy + HEIGHT; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockState log = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        BlockState vineFront = Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true);
        BlockState vineBack = Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true);
        BlockState vineBelow = Blocks.VINE.defaultBlockState().setValue(VineBlock.UP, true).setValue(VineBlock.EAST, true);
        int beamX = bx + BEAM_DX;
        int beamY = gy + BEAM_DY;
        for (int z = bz - BEAM_HALF; z <= bz + BEAM_HALF; z++) {
            level.setBlock(new BlockPos(beamX, beamY, z), log, Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(beamX - 1, beamY, z), vineFront, Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(beamX + 1, beamY, z), vineBack, Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(beamX, beamY - 1, z), vineBelow, Block.UPDATE_CLIENTS);
            if ((z & 1) == 0) {
                level.setBlock(new BlockPos(beamX - 1, beamY - 1, z), vineFront, Block.UPDATE_CLIENTS);
            }
        }
        for (int y = gy; y < beamY; y++) {
            level.setBlock(new BlockPos(beamX, y, bz - BEAM_HALF - 1), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), Block.UPDATE_CLIENTS);
            level.setBlock(new BlockPos(beamX, y, bz + BEAM_HALF + 1), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), Block.UPDATE_CLIENTS);
        }
        BlockState[] floor = {Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(),
                Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState()};
        for (int x = bx + FLOOR_FROM; x <= bx + FLOOR_TO; x++) {
            for (int z = bz - BEAM_HALF; z <= bz + BEAM_HALF; z++) {
                level.setBlock(new BlockPos(x, gy, z), floor[Math.floorMod(x * 7 + z * 3, floor.length)], Block.UPDATE_CLIENTS);
            }
        }
        for (int z = bz - BEAM_HALF; z <= bz + BEAM_HALF; z++) {
            level.setBlock(new BlockPos(bx + FLOOR_FROM - 1, gy, z), vineFront, Block.UPDATE_CLIENTS);
            for (int y = gy; y < gy + 6; y++) {
                level.setBlock(new BlockPos(bx + WALL_DX, y, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(bx + WALL_DX - 1, y, z), vineBack.setValue(VineBlock.WEST, false).setValue(VineBlock.EAST, true),
                        Block.UPDATE_CLIENTS);
            }
            if ((z & 1) != 0) {
                level.setBlock(new BlockPos(bx + FLOOR_FROM - 2, gy, z), Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        level.setBlock(new BlockPos(bx + FLOOR_FROM, gy + 1, bz + 2), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(bx + FLOOR_FROM, gy + 1, bz - 2), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    public static void clear(ServerLevel level, int[] base) {
        for (int x = base[0] - BACK; x <= base[0] + AHEAD; x++) {
            for (int z = base[2] - SIDE; z <= base[2] + SIDE; z++) {
                for (int y = base[1]; y < base[1] + HEIGHT; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
