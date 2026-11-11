package de.lardtom.propwash.rescue;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.SurfaceMaterials;
import de.lardtom.propwash.sim.SimSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CovSurfaceServerChecks {
    public static final int START_TICK = 3;
    public static final int LIMIT_TICKS = 400;
    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000c0f56");
    private static final int HEIGHT = 80;
    private static final int OFFSET_X = 56;
    private static final int SPACING = 14;
    private static final int PAD = 2;
    private static final int START_DX = -12;
    private static final double START_DY = 6.0;
    private static final int MAX_FLIGHT_TICKS = 900;
    private static final int PLATE_CHECK_TICKS = 25;
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();
    private static final List<Integer> DRONES = new ArrayList<>();
    private static final List<String> LOG = new ArrayList<>();
    private static final List<ChunkPos> FORCED = new ArrayList<>();
    private static @Nullable String failure;
    private static @Nullable BlockPos plate;
    private static @Nullable BlockPos door;
    private static int plateDrone = -1;
    private static int landedTicks;
    private static int tickedAt = -1;
    private static int flownAt = -1;

    private CovSurfaceServerChecks() {
    }

    public static @Nullable String failure() {
        return failure;
    }

    public static String summary() {
        return String.join("; ", LOG);
    }

    private enum Pad {
        WOOL("Wolle", Blocks.WOOL.pick(DyeColor.WHITE)),
        SLIME("Slime", Blocks.SLIME_BLOCK),
        PLATE("Holzdruckplatte vor Tür", Blocks.OAK_PRESSURE_PLATE);

        final String what;
        final Block block;

        Pad(String what, Block block) {
            this.what = what;
            this.block = block;
        }
    }

    public static boolean tick(MinecraftServer server, int ticks) {
        return run(server, ticks, List.of(Pad.WOOL, Pad.SLIME));
    }

    public static boolean tickWithPlate(MinecraftServer server, int ticks) {
        return run(server, ticks, List.of(Pad.values()));
    }

    private static boolean run(MinecraftServer server, int ticks, List<Pad> pads) {
        if (ticks < START_TICK) {
            return false;
        }
        ServerLevel level = server.overworld();
        try {
            if (flownAt < 0) {
                flownAt = ticks;
                BlockPos base = level.getRespawnData().pos().above(HEIGHT).east(OFFSET_X);
                for (Pad pad : pads) {
                    String problem = fly(level, base.south(SPACING * pad.ordinal()), pad);
                    if (problem != null) {
                        return finish(level, problem);
                    }
                }
                return false;
            }
            if (!pads.contains(Pad.PLATE)) {
                return finish(level, null);
            }
            if (!(level.getEntity(plateDrone) instanceof QuadcopterEntity onPlate)) {
                return finish(level, "Landung auf der Holzdruckplatte: Drohne nicht mehr im Level – " + String.join("; ", LOG));
            }
            boolean simulated = plate != null && level.isPositionEntityTicking(plate);
            if (tickedAt < 0) {
                if (onPlate.tickCount == landedTicks) {
                    if (ticks + PLATE_CHECK_TICKS < LIMIT_TICKS) {
                        return false;
                    }
                    return finish(level, String.format(Locale.ROOT, "Landung auf der Holzdruckplatte: Drohne %d Ticks nach der Landung noch nicht vom Server getickt "
                            + "(Chunk der Platte %s) – %s", ticks - flownAt, simulated ? "simuliert" : "nicht simuliert", String.join("; ", LOG)));
                }
                tickedAt = ticks;
            }
            if (ticks - tickedAt < PLATE_CHECK_TICKS) {
                return false;
            }
            BlockState plateState = plate == null ? null : level.getBlockState(plate);
            BlockState doorState = door == null ? null : level.getBlockState(door);
            boolean pressed = plateState != null && plateState.hasProperty(BlockStateProperties.POWERED) && plateState.getValue(BlockStateProperties.POWERED);
            boolean open = doorState != null && doorState.is(Blocks.OAK_DOOR) && doorState.getValue(DoorBlock.OPEN);
            LOG.add(String.format(Locale.ROOT, "Drohne %d Ticks nach der Landung erstmals vom Server getickt, %d Ticks danach: Platte %s, Tür %s "
                            + "(Drohne %d Server-Ticks, Chunk der Platte %s)", tickedAt - flownAt, ticks - tickedAt, pressed ? "gedrückt" : "aus",
                    open ? "offen" : "zu", onPlate.tickCount, simulated ? "simuliert" : "nicht simuliert"));
            return finish(level, pressed && open ? null : "Landung auf der Holzdruckplatte: Platte " + (pressed ? "gedrückt" : "aus") + ", Tür "
                    + (open ? "offen" : "zu") + " – " + String.join("; ", LOG));
        } catch (RuntimeException e) {
            return finish(level, "Ausnahme: " + e);
        }
    }

    private static boolean finish(ServerLevel level, @Nullable String problem) {
        failure = problem;
        for (Integer id : DRONES) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
        DRONES.clear();
        for (ChunkPos pos : FORCED) {
            level.setChunkForced(pos.x(), pos.z(), false);
        }
        FORCED.clear();
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(ORIGINAL.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            level.setBlock(entries.get(i).getKey(), entries.get(i).getValue(), Block.UPDATE_ALL);
        }
        ORIGINAL.clear();
        return true;
    }

    private static void simulate(ServerLevel level, ChunkPos pos) {
        if (!FORCED.contains(pos) && level.setChunkForced(pos.x(), pos.z(), true)) {
            FORCED.add(pos);
        }
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        ORIGINAL.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static @Nullable String fly(ServerLevel level, BlockPos base, Pad pad) {
        for (int dx = START_DX - 2; dx <= PAD + 1; dx++) {
            for (int dz = -PAD - 1; dz <= PAD + 1; dz++) {
                level.getChunk((base.getX() + dx) >> 4, (base.getZ() + dz) >> 4);
                set(level, base.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                for (int dy = 9; dy >= 0; dy--) {
                    set(level, base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        double homeY;
        if (pad == Pad.PLATE) {
            plate = base;
            door = base.north();
            simulate(level, ChunkPos.containing(base));
            set(level, base, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
            BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            set(level, door, lower);
            set(level, door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            homeY = base.getY();
        } else {
            for (int dx = -PAD; dx <= PAD; dx++) {
                for (int dz = -PAD; dz <= PAD; dz++) {
                    set(level, base.offset(dx, 0, dz), pad.block.defaultBlockState());
                }
            }
            homeY = base.getY() + 1.0;
            BlockState placed = pad.block.defaultBlockState();
            if (!placed.is(SurfaceMaterials.BOUNCY) && !placed.is(SurfaceMaterials.CUSHION)) {
                throw new IllegalStateException(pad.what + " steht in keinem der Tags drone_bouncy oder drone_cushion");
            }
        }
        if (pad == Pad.PLATE && level.getBlockState(door).getValue(DoorBlock.OPEN)) {
            return "Tür schon vor der Landung offen";
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Drohne nicht erzeugt";
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        drone.setWear(PartWear.PRISTINE);
        drone.snapTo(base.getX() + START_DX + 0.5, homeY + START_DY, base.getZ() + 0.5, -90.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            return "Drohne nicht gespawnt";
        }
        DRONES.add(drone.getId());
        if (pad == Pad.PLATE) {
            plateDrone = drone.getId();
        }
        ReturnPlan plan = ReturnPlan.of(OWNER, base.getX() + 0.5, homeY, base.getZ() + 0.5, false, new RescueSettings());
        ServerReturnFlight flight = ServerReturnFlight.start(drone, plan, level, new ServerRescueWorld((l, x, z) -> true));
        ServerFlight.Outcome outcome = ServerFlight.Outcome.FLYING;
        int ticks = 0;
        double minY = Double.POSITIVE_INFINITY;
        double maxBounce = 0.0;
        SimSnapshot snapshot = new SimSnapshot();
        boolean touched = false;
        for (; ticks < MAX_FLIGHT_TICKS && outcome == ServerFlight.Outcome.FLYING; ticks++) {
            outcome = flight.tick(drone, level, plan, null);
            flight.sim().copyStateTo(snapshot);
            minY = Math.min(minY, drone.getY());
            if (drone.getY() - homeY < 0.05) {
                touched = true;
            }
            if (touched) {
                maxBounce = Math.max(maxBounce, drone.getY() - homeY);
            }
        }
        double offset = Math.hypot(drone.getX() - (base.getX() + 0.5), drone.getZ() - (base.getZ() + 0.5));
        boolean pristine = drone.wear().equals(PartWear.PRISTINE);
        int strikes = flight.sim().telemetry().propStrikeCount;
        LOG.add(String.format(Locale.ROOT, "%s: %s nach %d Ticks, %.2f Blöcke neben dem Homepoint (dx %.2f, dz %.2f), Abstürze %d, Prop-Schläge %d, "
                        + "Verschleiß %s, höchster Sprung nach dem Aufsetzen %.2f m", pad.what, outcome == ServerFlight.Outcome.LANDED ? "gelandet" : outcome.name(),
                ticks, offset, drone.getX() - (base.getX() + 0.5), drone.getZ() - (base.getZ() + 0.5), flight.crashes(), strikes, pristine ? "keiner" : drone.wear(),
                maxBounce));
        if (pad == Pad.PLATE) {
            simulate(level, drone.chunkPosition());
            landedTicks = drone.tickCount;
        }
        if (outcome != ServerFlight.Outcome.LANDED) {
            return pad.what + ": Heimflug landet nicht (" + outcome + " nach " + ticks + " Ticks, " + String.format(Locale.ROOT, "%.2f", drone.getY() - homeY)
                    + " m über dem Homepoint)";
        }
        if (flight.crashes() != 0 || strikes != 0 || !pristine) {
            return pad.what + ": Landung mit Absturz, Prop-Schlag oder Verschleiß – " + LOG.getLast();
        }
        return null;
    }
}
