package de.lardtom.propwash.rescue;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CovVineServerChecks {
    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000d7a92");
    private static final int HEIGHT = 60;
    private static final int OFFSET_X = 24;
    private static final int HALF = 4;
    private static final int ROOM = 6;
    private static final int BEAM_DX = 3;
    private static final int BEAM_DY = 2;
    private static final double INTO_WALL = 0.1;
    private static final int WALL_DX = 2;
    private static final int WALL_HEIGHT = 4;
    private static final double SPEED = 15.0;
    private static final double PENETRATION = 0.01;
    private static final int FLIGHT_TICKS = 60;
    private static String summary = "";

    private CovVineServerChecks() {
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos base = level.getRespawnData().pos().above(HEIGHT).east(OFFSET_X);
        for (int dx = -HALF - 2; dx <= HALF + BEAM_DX + 2; dx += 8) {
            level.getChunk((base.getX() + dx) >> 4, base.getZ() >> 4);
        }
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        try {
            Result wall = climbFromWall(level, base, original);
            if (wall.inside > 0 || wall.end > PENETRATION) {
                return String.format(Locale.ROOT, "Heimflug aus einer Drohne %.2f m in einer Mauer: %d von %d Ticks bis %.3f m im Block, am Ende %.3f m",
                        INTO_WALL, wall.inside, FLIGHT_TICKS, wall.worst, wall.end);
            }
            Result beam = intoVines(level, base, original);
            if (beam.worst > PENETRATION) {
                return String.format(Locale.ROOT, "Heimflug mit %.0f m/s gegen den Ranken-Balken: bis %.3f m im Block", SPEED, beam.worst);
            }
            summary = String.format(Locale.ROOT, "Heimflug aus einer Drohne %.2f m in einer Mauer: vor dem ersten Tick heraus, kein Tick im Block, "
                    + "am Ende frei (Unterkante %+.2f m); mit %.0f m/s gegen den Ranken-Balken: höchstens %.3f m im Block, %d Abstürze, je %d Server-Ticks",
                    INTO_WALL, wall.lowest, SPEED, beam.worst, beam.crashes, FLIGHT_TICKS);
            return null;
        } catch (RuntimeException e) {
            return "Ausnahme: " + e;
        } finally {
            for (Map.Entry<BlockPos, BlockState> entry : original.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
            }
        }
    }

    public static String summary() {
        return summary;
    }

    private record Result(double lowest, double worst, double end, int crashes, int inside) {
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state, Map<BlockPos, BlockState> original) {
        original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void room(ServerLevel level, BlockPos base, Map<BlockPos, BlockState> original) {
        for (int dx = -HALF; dx <= HALF + BEAM_DX + 2; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                put(level, base.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), original);
                put(level, base.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), original);
                for (int dy = 0; dy < ROOM; dy++) {
                    put(level, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), original);
                }
            }
        }
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 at, Vec3 velocity) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.snapTo(at.x, at.y, at.z, -90.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        drone.setDeltaMovement(velocity.scale(1.0 / 20.0));
        return drone;
    }

    private static double penetration(ServerLevel level, QuadcopterEntity drone) {
        AABB box = drone.getBoundingBox();
        if (level.noBlockCollision(drone, box.deflate(PENETRATION))) {
            return 0.0;
        }
        double worst = 0.0;
        for (var shape : level.getBlockCollisions(drone, box)) {
            for (AABB part : shape.toAabbs()) {
                if (part.intersects(box)) {
                    worst = Math.max(worst, Math.min(Math.min(box.maxX - part.minX, part.maxX - box.minX),
                            Math.min(Math.min(box.maxY - part.minY, part.maxY - box.minY), Math.min(box.maxZ - part.minZ, part.maxZ - box.minZ))));
                }
            }
        }
        return worst;
    }

    private static Result climbFromWall(ServerLevel level, BlockPos base, Map<BlockPos, BlockState> original) {
        room(level, base, original);
        for (int dx = WALL_DX; dx <= WALL_DX + 1; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                    put(level, base.offset(dx, dy, dz), Blocks.STONE_BRICKS.defaultBlockState(), original);
                }
            }
        }
        QuadcopterEntity drone = spawn(level, Vec3.ZERO, Vec3.ZERO);
        try {
            double half = drone.getBbWidth() / 2.0;
            drone.snapTo(base.getX() + WALL_DX - half + INTO_WALL, base.getY() + 0.5, base.getZ() + 0.5, -90.0F, 0.0F);
            ReturnPlan plan = ReturnPlan.of(OWNER, base.getX() + WALL_DX + 4.5, base.getY(), base.getZ() + 0.5, false, new RescueSettings());
            ServerReturnFlight flight = ServerReturnFlight.start(drone, plan, level, new ServerRescueWorld((l, x, z) -> true));
            double worst = 0.0;
            int inside = 0;
            for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
                flight.tick(drone, level, plan, null);
                double depth = penetration(level, drone);
                worst = Math.max(worst, depth);
                if (depth > PENETRATION) {
                    inside++;
                }
            }
            return new Result(drone.getBoundingBox().minY - base.getY(), worst, penetration(level, drone), flight.crashes(), inside);
        } finally {
            drone.discard();
        }
    }

    private static Result intoVines(ServerLevel level, BlockPos base, Map<BlockPos, BlockState> original) {
        room(level, base, original);
        BlockState log = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int dz = -HALF + 1; dz <= HALF - 1; dz++) {
            BlockPos beam = base.offset(BEAM_DX, BEAM_DY, dz);
            put(level, beam, log, original);
            put(level, beam.west(), Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true), original);
            put(level, beam.below(), Blocks.VINE.defaultBlockState().setValue(VineBlock.UP, true), original);
            put(level, beam.east(), Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true), original);
        }
        QuadcopterEntity drone = spawn(level, new Vec3(base.getX() - 1.5, base.getY() + BEAM_DY + 0.3, base.getZ() + 0.5), new Vec3(SPEED, 0.0, 0.0));
        try {
            ReturnPlan plan = ReturnPlan.of(OWNER, base.getX() + BEAM_DX + 2.5, base.getY() + BEAM_DY + 0.3, base.getZ() + 0.5, false,
                    new RescueSettings());
            ServerReturnFlight flight = ServerReturnFlight.start(drone, plan, level, new ServerRescueWorld((l, x, z) -> true));
            double worst = 0.0;
            double lowest = Double.POSITIVE_INFINITY;
            for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
                flight.tick(drone, level, plan, null);
                worst = Math.max(worst, penetration(level, drone));
                lowest = Math.min(lowest, drone.getBoundingBox().minY - base.getY());
            }
            return new Result(lowest, worst, penetration(level, drone), flight.crashes(), 0);
        } finally {
            drone.discard();
        }
    }
}
