package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.example.ExampleGhost;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.collision.ServerCollision;
import de.lardtom.propwash.input.addon.AddonInputState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CollisionJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final int RIG_DZ = 4;
    public static final int WALL_DX = 4;
    public static final int WALL_THICK = 3;
    public static final double START_DX = 1.5;
    public static final double HOVER = 2.0;
    public static final int PHASE_TICKS = 20 * 45;
    private static final int CLEAR_BACK = 3;
    private static final int CLEAR_FRONT = 14;
    private static final int CLEAR_SIDE = 3;
    private static final int CLEAR_HEIGHT = 12;
    private static final int WALL_HALF = 2;
    private static final int WALL_HEIGHT = 5;
    private static final int ARM_TICKS = 20 * 30;
    private static final int OFF_TICKS = 20 * 15;
    private static final Quaternionf FACING_WALL = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    private static boolean listening;

    private static final class Track {
        final int joined;
        int stage;
        int mark;
        int x;
        int y;
        int z;
        int droneId = -1;
        int epoch;
        int denied;
        int passed;
        int switchOn;
        int switchOff;

        Track(int joined) {
            this.joined = joined;
        }

        Vec3 ground() {
            return new Vec3(this.x + START_DX, this.y, this.z + 0.5);
        }

        Vec3 air() {
            return this.ground().add(0.0, HOVER, 0.0);
        }

        double wallNear() {
            return this.x + WALL_DX;
        }

        double wallFar() {
            return this.x + WALL_DX + WALL_THICK;
        }
    }

    private CollisionJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        listen();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            try {
                step(player, track, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Kollision: Stufe {} fehlgeschlagen", track.stage, e);
                track.stage = 9;
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
                    track.denied = ServerCollision.deniedCount();
                    drone.resetTo(track.air(), FACING_WALL);
                    Constants.LOG.info("[Beitrittstest] Kollision: Phase ohne Recht, Geist über den Add-on-Schalter des Clients, Wand {} bis {}",
                            (int) track.wallNear(), (int) track.wallFar());
                    track.stage = 2;
                    track.mark = tick;
                } else if (waited > ARM_TICKS) {
                    Constants.LOG.error("[Beitrittstest] Kollision: Drohne nicht scharf geschaltet");
                    track.stage = 9;
                }
            }
            case 2 -> {
                QuadcopterEntity drone = drone(player, track);
                int resets = drone.resetEpoch() - track.epoch - 1;
                int denied = ServerCollision.deniedCount() - track.denied;
                double front = drone.getX() + drone.getBbWidth() / 2.0;
                if (front > track.wallNear() + 0.05) {
                    Constants.LOG.error("[Beitrittstest] Kollision ohne Recht: Server hat die Drohne in die Wand gelassen, x {}",
                            String.format(Locale.ROOT, "%.2f", drone.getX()));
                    track.stage = 9;
                    return;
                }
                if (resets >= 1 && denied >= 1 && waited > 40 || waited > PHASE_TICKS) {
                    Constants.LOG.info("[Beitrittstest] Kollision ohne Recht: {} Rücksetzungen, collision.denied {}, Server-x {} vor der Wand",
                            resets, denied, String.format(Locale.ROOT, "%.2f", drone.getX()));
                    viaSwitch(drone, track, "ohne Recht");
                    player.addTag(ExampleGhost.PERMISSION_TAG);
                    track.epoch = drone.resetEpoch();
                    track.passed = ServerCollision.passedCount();
                    drone.resetTo(track.air(), FACING_WALL);
                    Constants.LOG.info("[Beitrittstest] Kollision: Phase mit Recht (Tag {})", ExampleGhost.PERMISSION_TAG);
                    track.stage = 3;
                    track.mark = tick;
                }
            }
            case 3 -> {
                QuadcopterEntity drone = drone(player, track);
                double back = drone.getX() - drone.getBbWidth() / 2.0;
                if (back > track.wallFar() + 0.2 && waited > 20) {
                    int resets = drone.resetEpoch() - track.epoch - 1;
                    Constants.LOG.info("[Beitrittstest] Kollision mit Recht: Durchflug angenommen, Server-x {} hinter der Wand, "
                                    + "Rücksetzungen {}, angenommene Geist-Pakete {}", String.format(Locale.ROOT, "%.2f", drone.getX()), resets,
                            ServerCollision.passedCount() - track.passed);
                    viaSwitch(drone, track, "mit Recht");
                    track.stage = 4;
                    track.mark = tick;
                } else if (waited > PHASE_TICKS) {
                    Constants.LOG.error("[Beitrittstest] Kollision mit Recht: kein Durchflug, Server-x {}",
                            String.format(Locale.ROOT, "%.2f", drone.getX()));
                    track.stage = 9;
                }
            }
            case 4 -> {
                boolean held = player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone
                        && AddonInputState.active(drone, ExampleGhost.GHOST_SWITCH);
                if (waited > 20 && !held || waited > OFF_TICKS) {
                    player.removeTag(ExampleGhost.PERMISSION_TAG);
                    Constants.LOG.info("[Beitrittstest] Kollision: fertig, Add-on-Schalter am Server {} (ADDON_SWITCH_CHANGED an {}, aus {})",
                            held ? "an" : "aus", track.switchOn, track.switchOff);
                    track.stage = 5;
                }
            }
            default -> {
            }
        }
    }

    private static synchronized void listen() {
        if (listening) {
            return;
        }
        listening = true;
        PropwashEvents.ADDON_SWITCH_CHANGED.register((player, drone, id, active) -> {
            if (!ExampleGhost.GHOST_SWITCH.equals(id)) {
                return;
            }
            Track track = TRACKS.get(player.getUUID());
            if (track != null) {
                if (active) {
                    track.switchOn++;
                } else {
                    track.switchOff++;
                }
            }
            Constants.LOG.info("[Beitrittstest] Kollision: Add-on-Schalter {}={} von {} an Drohne {}", id, active, player.getPlainTextName(), drone.getId());
        });
    }

    private static void viaSwitch(QuadcopterEntity drone, Track track, String phase) {
        boolean held = AddonInputState.active(drone, ExampleGhost.GHOST_SWITCH);
        boolean forced = ExampleGhost.forced();
        if (held && !forced && track.switchOn >= 1) {
            Constants.LOG.info("[Beitrittstest] Kollision {}: Geist über den Add-on-Schalter (am Server an, ADDON_SWITCH_CHANGED an {}, aus {})",
                    phase, track.switchOn, track.switchOff);
            return;
        }
        Constants.LOG.error("[Beitrittstest] Kollision {}: Geist nicht über den Add-on-Schalter (am Server {}, ADDON_SWITCH_CHANGED an {}, "
                + "direkt erzwungen {})", phase, held ? "an" : "aus", track.switchOn, forced ? "ja" : "nein");
    }

    private static void start(ServerPlayer player, Track track) {
        ServerLevel level = player.level();
        track.x = player.getBlockX();
        track.y = player.getBlockY();
        track.z = player.getBlockZ() + RIG_DZ;
        for (int dx = -CLEAR_BACK; dx <= CLEAR_FRONT; dx++) {
            for (int dz = -CLEAR_SIDE; dz <= CLEAR_SIDE; dz++) {
                level.setBlockAndUpdate(new BlockPos(track.x + dx, track.y - 1, track.z + dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    boolean wall = dx >= WALL_DX && dx < WALL_DX + WALL_THICK && Math.abs(dz) <= WALL_HALF && dy < WALL_HEIGHT;
                    level.setBlockAndUpdate(new BlockPos(track.x + dx, track.y + dy, track.z + dz),
                            wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        player.removeTag(ExampleGhost.PERMISSION_TAG);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 ground = track.ground();
        drone.snapTo(ground.x, ground.y, ground.z, 0.0F, 0.0F);
        drone.setAttitude(FACING_WALL);
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
        Constants.LOG.info("[Beitrittstest] Kollision: Testfeld bei {} {} {}, Drohne {} vor einer {} Blöcke dicken Wand, FPV {}",
                track.x, track.y, track.z, drone.getId(), WALL_THICK, result);
    }

    private static QuadcopterEntity drone(ServerPlayer player, Track track) {
        if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne verschwunden");
        }
        return drone;
    }
}
