package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.barrier.BarrierKind;
import de.lardtom.propwash.barrier.BarrierModule;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BarrierJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int RIG_DZ = 5;
    public static final int PASS_X = 4;
    public static final int PASS_THICK = 3;
    public static final int BLOCK_X = 12;
    public static final int BLOCK_THICK = 2;
    public static final int WALK_WALL_X = 4;
    public static final int WALK_PASS_DZ = -6;
    public static final int WALK_BLOCK_DZ = 6;
    public static final double START_DX = 1.5;
    public static final double HOVER = 2.0;
    private static final int CLEAR_BACK = 3;
    private static final int CLEAR_FRONT = 20;
    private static final int CLEAR_SIDE = 8;
    private static final int CLEAR_HEIGHT = 8;
    private static final int WALL_HALF = 2;
    private static final int WALL_HEIGHT = 5;
    private static final int ARM_TICKS = 20 * 30;
    private static final int PHASE_TICKS = 20 * 45;
    private static final int HOLD_TICKS = 100;
    private static final int AFTER_PASS_TICKS = 200;
    private static final int WALK_TICKS = 20 * 20;
    private static final Quaternionf FACING_WALLS = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        int stage;
        int mark;
        int x;
        int y;
        int z;
        int droneId = -1;
        int epoch;
        boolean passed;
        int passedAt;
        double still = Double.NaN;
        int stillSince;

        Track(int joined) {
            this.joined = joined;
        }

        Vec3 ground() {
            return new Vec3(this.x + START_DX, this.y, this.z + 0.5);
        }

        Vec3 walkStart(int dz) {
            return new Vec3(this.x + START_DX, this.y, this.z + dz + 0.5);
        }
    }

    private BarrierJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            try {
                step(player, track, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Barrieren: Stufe {} fehlgeschlagen", track.stage, e);
                track.stage = 99;
            }
        }
    }

    private static void step(ServerPlayer player, Track track, int tick) {
        int waited = tick - track.mark;
        switch (track.stage) {
            case 0 -> {
                if (tick - track.joined >= SETTLE_TICKS) {
                    start(player, track);
                    track.stage = 1;
                    track.mark = tick;
                }
            }
            case 1 -> {
                QuadcopterEntity drone = drone(player, track);
                if (drone.isArmed()) {
                    track.epoch = drone.resetEpoch();
                    drone.resetTo(track.ground().add(0.0, HOVER, 0.0), FACING_WALLS);
                    Constants.LOG.info("[Beitrittstest] Barrieren: Drohne scharf, vor dem Durchlass {} bis {} in die Luft gesetzt",
                            track.x + PASS_X, track.x + PASS_X + PASS_THICK);
                    track.stage = 2;
                    track.mark = tick;
                } else if (waited > ARM_TICKS) {
                    Constants.LOG.error("[Beitrittstest] Barrieren: Drohne nicht scharf geschaltet");
                    track.stage = 99;
                }
            }
            case 2 -> {
                QuadcopterEntity drone = drone(player, track);
                double half = drone.getBbWidth() / 2.0;
                double blockNear = track.x + BLOCK_X;
                if (drone.getX() + half > blockNear + 0.05) {
                    Constants.LOG.error("[Beitrittstest] Barrieren: Server hat die Drohne in die Sperre gelassen, x {}", fmt(drone.getX() - track.x));
                    track.stage = 99;
                    return;
                }
                if (!track.passed && drone.getX() - half > track.x + PASS_X + PASS_THICK + 0.2) {
                    track.passed = true;
                    track.passedAt = tick;
                    Constants.LOG.info("[Beitrittstest] Barrieren: Durchlass angenommen, Server-x {} hinter dem Durchlass, Rücksetzungen {}",
                            fmt(drone.getX() - track.x), drone.resetEpoch() - track.epoch - 1);
                }
                if (track.passed && tick - track.passedAt > AFTER_PASS_TICKS) {
                    Constants.LOG.info("[Beitrittstest] Barrieren: Sperre hält, Server-Front {} vor der Sperre bei {}, Rücksetzungen {}",
                            fmt(blockNear - drone.getX() - half), BLOCK_X, drone.resetEpoch() - track.epoch - 1);
                    DronePiloting.release(drone);
                    teleport(player, track.walkStart(WALK_PASS_DZ));
                    track.stage = 3;
                    track.mark = tick;
                    track.still = Double.NaN;
                } else if (waited > PHASE_TICKS) {
                    Constants.LOG.error("[Beitrittstest] Barrieren: Drohne kommt nicht durch den Durchlass, Server-x {}", fmt(drone.getX() - track.x));
                    track.stage = 99;
                }
            }
            case 3 -> walk(player, track, tick, waited, true);
            case 4 -> walk(player, track, tick, waited, false);
            case 5 -> {
                if (waited > HOLD_TICKS * 3) {
                    Constants.LOG.info("[Beitrittstest] Barrieren: fertig");
                    track.stage = 6;
                }
            }
            default -> {
            }
        }
    }

    private static void walk(ServerPlayer player, Track track, int tick, int waited, boolean pass) {
        double wall = track.x + WALK_WALL_X;
        double half = player.getBbWidth() / 2.0;
        double x = player.getX();
        if (Double.isNaN(track.still) || Math.abs(x - track.still) > 0.01) {
            track.still = x;
            track.stillSince = tick;
        }
        boolean moved = x > track.x + START_DX + 0.5;
        if (pass) {
            if (x + half > wall + 0.05) {
                Constants.LOG.error("[Beitrittstest] Barrieren: Server ließ den Spieler in den Durchlass, x {}", fmt(x - track.x));
                track.stage = 99;
                return;
            }
            if (moved && tick - track.stillSince > 40) {
                Constants.LOG.info("[Beitrittstest] Barrieren: Spieler am Durchlass aufgehalten, Front {} vor der Wand", fmt(wall - x - half));
                teleport(player, track.walkStart(WALK_BLOCK_DZ));
                track.stage = 4;
                track.mark = tick;
                track.still = Double.NaN;
            } else if (waited > WALK_TICKS) {
                Constants.LOG.error("[Beitrittstest] Barrieren: Spieler läuft nicht zum Durchlass, x {}", fmt(x - track.x));
                track.stage = 99;
            }
            return;
        }
        if (x - half > wall + 1.0) {
            Constants.LOG.info("[Beitrittstest] Barrieren: Spieler durch die Sperre, Server-x {} hinter der Wand", fmt(x - half - wall));
            player.setGameMode(GameType.CREATIVE);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BarrierModule.DRONE_PASS.item().get()));
            track.stage = 5;
            track.mark = tick;
        } else if (waited > WALK_TICKS) {
            Constants.LOG.error("[Beitrittstest] Barrieren: Spieler kommt nicht durch die Sperre, x {}", fmt(x - track.x));
            track.stage = 99;
        }
    }

    private static void start(ServerPlayer player, Track track) {
        ServerLevel level = player.level();
        track.x = player.getBlockX();
        track.y = player.getBlockY();
        track.z = player.getBlockZ() + RIG_DZ;
        BlockState pass = BarrierModule.of(BarrierKind.DRONE_PASS).block().get().defaultBlockState();
        BlockState block = BarrierModule.of(BarrierKind.DRONE_BLOCK).block().get().defaultBlockState();
        for (int dx = -CLEAR_BACK; dx <= CLEAR_FRONT; dx++) {
            for (int dz = -CLEAR_SIDE; dz <= CLEAR_SIDE; dz++) {
                level.setBlockAndUpdate(new BlockPos(track.x + dx, track.y - 1, track.z + dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    BlockState state = Blocks.AIR.defaultBlockState();
                    boolean field = Math.abs(dz) <= WALL_HALF && dy < WALL_HEIGHT;
                    if (field && dx >= PASS_X && dx < PASS_X + PASS_THICK) {
                        state = pass;
                    } else if (field && dx >= BLOCK_X && dx < BLOCK_X + BLOCK_THICK) {
                        state = block;
                    } else if (dx == WALK_WALL_X && dy < 3 && Math.abs(dz - WALK_PASS_DZ) <= 1) {
                        state = pass;
                    } else if (dx == WALK_WALL_X && dy < 3 && Math.abs(dz - WALK_BLOCK_DZ) <= 1) {
                        state = block;
                    }
                    level.setBlockAndUpdate(new BlockPos(track.x + dx, track.y + dy, track.z + dz), state);
                }
            }
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 ground = track.ground();
        drone.snapTo(ground.x, ground.y, ground.z, 0.0F, 0.0F);
        drone.setAttitude(FACING_WALLS);
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setBatteryCharge(1.0F);
        level.addFreshEntity(drone);
        track.droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        Constants.LOG.info("[Beitrittstest] Barrieren: Testfeld bei {} {} {}, Durchlass {} Blöcke, Sperre {} Blöcke, Drohne {}, FPV {}",
                track.x, track.y, track.z, PASS_THICK, BLOCK_THICK, drone.getId(), result);
    }

    private static void teleport(ServerPlayer player, Vec3 feet) {
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), -90.0F, 0.0F, true);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static QuadcopterEntity drone(ServerPlayer player, Track track) {
        if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne verschwunden");
        }
        return drone;
    }
}
