package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.RaceModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class GatePushJoinCheck {
    public static final String NAME = "Kolbenprobe";
    public static final int BUILD_TICK = 40;
    public static final int AWAY = 24;
    public static final int LIFT = 12;
    public static final int APART = 8;
    public static final int SETTLE_TICKS = 100;
    public static final int HOLD_TICKS = 20;
    public static final int LOAD_TICKS = 600;
    private static final double SAME = 1.0E-3;
    private static int stage;
    private static int since;
    private static int joined = -1;
    private static int course = GateEntity.NO_COURSE;
    private static int gateA = -1;
    private static int gateB = -1;
    private static @Nullable Vec3 posA;
    private static @Nullable Vec3 posB;
    private static @Nullable BlockPos site;
    private static @Nullable BlockPos lift;
    private static @Nullable BlockPos slime;

    private GatePushJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        try {
            step(server, tick);
        } catch (RuntimeException e) {
            stage = -1;
            Constants.LOG.error("[Beitrittstest] Kolben: Ablauf fehlgeschlagen", e);
        }
    }

    private static void step(MinecraftServer server, int tick) {
        ServerLevel level = server.overworld();
        switch (stage) {
            case 0 -> {
                if (tick < BUILD_TICK) {
                    return;
                }
                BlockPos spawn = level.getRespawnData().pos();
                site = new BlockPos(spawn.getX() + AWAY, spawn.getY(), spawn.getZ());
                forEachChunk(chunk -> level.setChunkForced(chunk.x(), chunk.z(), true));
                next(1, tick);
            }
            case 1 -> {
                boolean[] ready = {true};
                forEachChunk(chunk -> ready[0] &= level.areEntitiesActuallyLoadedAndTicking(chunk));
                if (!ready[0]) {
                    if (tick - since > LOAD_TICKS) {
                        stage = -1;
                        Constants.LOG.error("[Beitrittstest] Kolben: Platz lädt nicht");
                    }
                    return;
                }
                build(level);
                next(2, tick);
            }
            case 2 -> {
                if (tick - since < HOLD_TICKS) {
                    return;
                }
                String problem = extended(level, lift) ? null : "Kolben unter Gate A nicht ausgefahren";
                String moved = problem != null ? problem : moved(level);
                if (moved != null) {
                    Constants.LOG.error("[Beitrittstest] Kolben: vor dem Beitritt: {}", moved);
                } else {
                    Constants.LOG.info("[Beitrittstest] Kolben: vor dem Beitritt: Kolben unter Gate A ausgefahren, Gate A am Server und im Kurs bei {}",
                            text(posA));
                }
                next(3, tick);
            }
            case 3 -> {
                if (server.getPlayerList().getPlayers().isEmpty()) {
                    joined = -1;
                    return;
                }
                if (joined < 0) {
                    joined = tick;
                }
                if (tick - joined < SETTLE_TICKS) {
                    return;
                }
                GateEntity b = gate(level, gateB);
                if (b == null) {
                    stage = -1;
                    Constants.LOG.error("[Beitrittstest] Kolben: Gate B fehlt am Server");
                    return;
                }
                AABB box = b.getBoundingBox();
                BlockPos at = new BlockPos((int) Math.floor(box.minX) - 1, (int) Math.floor(box.minY) + 1, (int) Math.floor((box.minZ + box.maxZ) * 0.5));
                slime = at;
                level.setBlockAndUpdate(at.west(), piston(true, Direction.EAST));
                level.setBlockAndUpdate(at, Blocks.SLIME_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(at.west(2), Blocks.REDSTONE_BLOCK.defaultBlockState());
                Constants.LOG.info("[Beitrittstest] Kolben: klebriger Kolben mit Schleimblock bei {} {} {} gegen Gate B gezündet, {} Spieler da",
                        at.getX(), at.getY(), at.getZ(), server.getPlayerList().getPlayers().size());
                next(4, tick);
            }
            case 4 -> {
                if (tick - since < HOLD_TICKS) {
                    return;
                }
                BlockPos at = slime;
                if (at == null || !extended(level, at.west()) || !level.getBlockState(at.east()).is(Blocks.SLIME_BLOCK)) {
                    Constants.LOG.error("[Beitrittstest] Kolben: Schleimblock nicht verschoben");
                } else {
                    Constants.LOG.info("[Beitrittstest] Kolben: Schleimblock ausgefahren, Gate B am Server bei {}", text(position(level, gateB)));
                }
                if (at != null) {
                    level.setBlockAndUpdate(at.west(2), Blocks.AIR.defaultBlockState());
                }
                next(5, tick);
            }
            case 5 -> {
                if (tick - since < HOLD_TICKS) {
                    return;
                }
                String problem = moved(level);
                if (problem != null) {
                    Constants.LOG.error("[Beitrittstest] Kolben: nach dem Schleimblock: {}", problem);
                } else {
                    Constants.LOG.info("[Beitrittstest] Kolben: nach dem Schleimblock: Gate A bei {} und Gate B bei {} am Server und im Kurs unverändert",
                            text(posA), text(posB));
                }
                stage = 6;
            }
            default -> {
            }
        }
    }

    private static void next(int value, int tick) {
        stage = value;
        since = tick;
    }

    private interface ChunkAction {
        void accept(ChunkPos chunk);
    }

    private static void forEachChunk(ChunkAction action) {
        BlockPos o = site;
        if (o == null) {
            return;
        }
        for (int cx = (o.getX() - 6) >> 4; cx <= (o.getX() + 6) >> 4; cx++) {
            for (int cz = (o.getZ() - 4) >> 4; cz <= (o.getZ() + APART + 4) >> 4; cz++) {
                action.accept(new ChunkPos(cx, cz));
            }
        }
    }

    private static void build(ServerLevel level) {
        BlockPos o = site;
        if (o == null) {
            throw new IllegalStateException("kein Platz");
        }
        int top = level.getMinY();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -2; dz <= APART + 2; dz++) {
                top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, o.getX() + dx, o.getZ() + dz));
            }
        }
        int y = top + LIFT;
        GateEntity a = spawn(level, new Vec3(o.getX() + 0.5, y, o.getZ() + 0.5));
        GateEntity b = spawn(level, new Vec3(o.getX() + 0.5, y, o.getZ() + APART + 0.5));
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("keine Kursdaten");
        }
        course = registry.createCourse(CourseRegistry.dimensionOf(level), null);
        CourseRegistry.Course data = registry.course(course);
        if (data == null) {
            throw new IllegalStateException("Kurs nicht angelegt");
        }
        registry.rename(data, NAME);
        registry.appendGate(course, a);
        registry.appendGate(course, b);
        gateA = a.getId();
        gateB = b.getId();
        posA = a.position();
        posB = b.position();
        BlockPos head = BlockPos.containing(a.getX(), a.getY() + 1.0E-4, a.getZ());
        lift = head.below();
        level.setBlockAndUpdate(head, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(head.below(), piston(false, Direction.UP));
        level.setBlockAndUpdate(head.below(2), Blocks.REDSTONE_BLOCK.defaultBlockState());
        Constants.LOG.info("[Beitrittstest] Kolben: Kurs {} „{}“ mit Gate A bei {} und Gate B bei {}, Kolben unter Gate A gezündet, {} Spieler da",
                course, NAME, text(posA), text(posB), level.getServer().getPlayerList().getPlayers().size());
    }

    private static GateEntity spawn(ServerLevel level, Vec3 at) {
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.COMMAND);
        if (gate == null) {
            throw new IllegalStateException("Gate nicht erzeugt");
        }
        gate.setSize(GateSize.MEDIUM);
        gate.applyGeometry(GateGeometry.of(at.x, at.y, at.z, 0.0F, GateSize.MEDIUM));
        if (!level.addFreshEntity(gate)) {
            throw new IllegalStateException("Gate nicht gespawnt");
        }
        return gate;
    }

    private static BlockState piston(boolean sticky, Direction facing) {
        return (sticky ? Blocks.STICKY_PISTON : Blocks.PISTON).defaultBlockState().setValue(PistonBaseBlock.FACING, facing);
    }

    private static boolean extended(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(PistonBaseBlock.EXTENDED) && state.getValue(PistonBaseBlock.EXTENDED);
    }

    private static @Nullable GateEntity gate(ServerLevel level, int id) {
        return level.getEntity(id) instanceof GateEntity gate && !gate.isRemoved() ? gate : null;
    }

    private static @Nullable Vec3 position(ServerLevel level, int id) {
        GateEntity gate = gate(level, id);
        return gate == null ? null : gate.position();
    }

    private static @Nullable String moved(ServerLevel level) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course data = registry == null ? null : registry.course(course);
        if (data == null || data.gates.size() != 2) {
            return "Kurs fehlt am Server";
        }
        Vec3[] expected = {posA, posB};
        int[] ids = {gateA, gateB};
        for (int i = 0; i < 2; i++) {
            String name = i == 0 ? "A" : "B";
            Vec3 now = position(level, ids[i]);
            if (now == null || expected[i] == null) {
                return "Gate " + name + " fehlt am Server";
            }
            if (now.distanceTo(expected[i]) > SAME) {
                return "Gate " + name + " am Server verschoben von " + text(expected[i]) + " nach " + text(now);
            }
            CourseRegistry.GateRecord record = data.gates.get(i);
            Vec3 stored = new Vec3(record.x, record.y, record.z);
            if (stored.distanceTo(expected[i]) > SAME) {
                return "Gate " + name + " im Kurs verschoben nach " + text(stored);
            }
        }
        return null;
    }

    private static String text(@Nullable Vec3 v) {
        return v == null ? "-" : String.format(Locale.ROOT, "%.3f %.3f %.3f", v.x, v.y, v.z);
    }
}
