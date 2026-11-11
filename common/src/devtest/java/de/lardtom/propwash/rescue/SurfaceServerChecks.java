package de.lardtom.propwash.rescue;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.SurfaceMaterials;
import de.lardtom.propwash.sim.SimSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SurfaceServerChecks {
    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000d7a91");
    private static final int HEIGHT = 70;
    private static final int OFFSET_X = -12;
    private static final int WALL_DX = 3;
    private static final int WALL_HALF = 2;
    private static final double SPEED = 15.0;
    private static final int CONTACT_TICKS = 6;
    private static final double STOPPED = 1.0;
    private static final int FLIGHT_TICKS = 40;
    private static String summary = "";

    private SurfaceServerChecks() {
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos base = level.getRespawnData().pos().above(HEIGHT).east(OFFSET_X);
        level.getChunk(base.getX() >> 4, base.getZ() >> 4);
        level.getChunk((base.getX() + WALL_DX) >> 4, base.getZ() >> 4);
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        try {
            Result wool = fly(level, base, Blocks.WOOL.pick(DyeColor.WHITE), original);
            Result slime = fly(level, base, Blocks.SLIME_BLOCK, original);
            Result stone = fly(level, base, Blocks.STONE, original);
            if (!stone.contact || stone.crashes == 0) {
                return "Kontrolle: Heimflug gegen Stein mit " + SPEED + " m/s ohne Absturz (Kontakt " + stone.contact + ")";
            }
            if (!wool.contact || wool.crashes != 0) {
                return "Heimflug gegen Wolle: Kontakt " + wool.contact + ", Abstürze " + wool.crashes;
            }
            if (!slime.contact || slime.crashes != 0 || slime.vx >= 0.0) {
                return String.format(Locale.ROOT, "Heimflug gegen Slime: Kontakt %s, Abstürze %d, vx danach %.2f m/s", slime.contact,
                        slime.crashes, slime.vx);
            }
            summary = String.format(Locale.ROOT, "Heimflug mit %.0f m/s gegen Wolle 0 Abstürze (vx danach %.2f), Slime 0 Abstürze (vx %.2f), "
                    + "Stein %d Absturz (Kontrolle), je %d Server-Ticks ohne Fehler", SPEED, wool.vx, slime.vx, stone.crashes, FLIGHT_TICKS);
            return null;
        } catch (RuntimeException e) {
            return "Ausnahme: " + e;
        } finally {
            for (Map.Entry<BlockPos, BlockState> entry : original.entrySet()) {
                level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            }
        }
    }

    public static String summary() {
        return summary;
    }

    private record Result(boolean contact, int crashes, double vx) {
    }

    private static Result fly(ServerLevel level, BlockPos base, Block wall, Map<BlockPos, BlockState> original) {
        for (int dx = -1; dx <= WALL_DX; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -WALL_HALF; dz <= WALL_HALF; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                    level.setBlockAndUpdate(pos, dx == WALL_DX ? wall.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockState placed = wall.defaultBlockState();
        boolean soft = placed.is(SurfaceMaterials.BOUNCY) || placed.is(SurfaceMaterials.CUSHION);
        if (soft == (wall == Blocks.STONE)) {
            throw new IllegalStateException(wall.getName().getString() + " hat nicht die erwartete Fläche");
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        try {
            drone.setBuild(DroneBuild.FREESTYLE);
            drone.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, -90.0F, 0.0F);
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("Drohne nicht gespawnt");
            }
            drone.setDeltaMovement(new Vec3(SPEED / 20.0, 0.0, 0.0));
            ReturnPlan plan = ReturnPlan.of(OWNER, base.getX() - 20.5, base.getY(), base.getZ() + 0.5, false, new RescueSettings());
            ServerReturnFlight flight = ServerReturnFlight.start(drone, plan, level, new ServerRescueWorld((l, x, z) -> true));
            double face = base.getX() + WALL_DX;
            boolean contact = false;
            double vx = Double.NaN;
            SimSnapshot snapshot = new SimSnapshot();
            for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
                flight.tick(drone, level, plan, null);
                flight.sim().copyStateTo(snapshot);
                if (tick < CONTACT_TICKS) {
                    contact |= drone.getX() + drone.getBbWidth() / 2.0 >= face - 0.02 || snapshot.vx < STOPPED;
                }
                if (tick == CONTACT_TICKS) {
                    vx = snapshot.vx;
                }
            }
            return new Result(contact, flight.crashes(), vx);
        } finally {
            drone.discard();
        }
    }
}
