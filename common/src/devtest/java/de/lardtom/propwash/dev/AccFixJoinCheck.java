package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AccFixJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final String PREFIX = "accfix speed=";
    public static final int MOVING_WINDOW = 10;
    public static final double MOVING_SPEED = 5.0;
    public static final int FLIGHT_TIMEOUT_TICKS = 20 * 120;
    private static final Quaternionf FACING_EAST = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private static final class Track {
        final int joined;
        final Deque<Vec3> positions = new ArrayDeque<>();
        int stage;
        int mark;
        int droneId = -1;
        boolean actions;

        Track(int joined) {
            this.joined = joined;
        }
    }

    private AccFixJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            try {
                step(player, track, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Tempo: Stufe {} fehlgeschlagen", track.stage, e);
                report(player, track, false, "Stufe " + track.stage + ": " + e.getMessage());
            }
        }
    }

    private static void step(ServerPlayer player, Track track, int tick) {
        switch (track.stage) {
            case 0 -> {
                if (tick - track.joined >= SETTLE_TICKS) {
                    start(player, track);
                    track.stage = 1;
                    track.mark = tick;
                }
            }
            case 1 -> {
                if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone)) {
                    throw new IllegalStateException("Testdrohne verschwunden");
                }
                track.positions.addLast(drone.position());
                while (track.positions.size() > MOVING_WINDOW + 1) {
                    track.positions.removeFirst();
                }
                boolean piloted = drone.isFlownBy(player.getUUID()) && drone.isArmed();
                if (piloted && track.positions.size() > MOVING_WINDOW) {
                    double speed = track.positions.getLast().subtract(track.positions.getFirst()).horizontalDistance() * 20.0 / MOVING_WINDOW;
                    if (speed >= MOVING_SPEED) {
                        AccFixSpeedRecorder.start(drone.getId(), track.actions);
                        Constants.LOG.info("[Beitrittstest] Tempo: Drohne {} fliegt mit {} m/s (Entity-Position über {} Ticks), Messung über {} Ticks beginnt",
                                drone.getId(), String.format(Locale.ROOT, "%.2f", speed), MOVING_WINDOW, AccFixSpeedRecorder.MEASURE_TICKS);
                        track.stage = 2;
                        track.mark = tick;
                        return;
                    }
                }
                if (tick - track.mark > FLIGHT_TIMEOUT_TICKS) {
                    report(player, track, false, "kein Flug mit mindestens " + MOVING_SPEED + " m/s");
                }
            }
            case 2 -> {
                String result = AccFixSpeedRecorder.result();
                if (result != null) {
                    report(player, track, AccFixSpeedRecorder.passed(), result);
                } else if (tick - track.mark > AccFixSpeedRecorder.MEASURE_TICKS * 4) {
                    report(player, track, false, "Messung bleibt stehen: " + AccFixSpeedRecorder.progress());
                }
            }
            default -> {
            }
        }
    }

    private static void report(ServerPlayer player, Track track, boolean ok, String text) {
        track.stage = 9;
        AccFixSpeedRecorder.reset();
        if (ok) {
            Constants.LOG.info("[Beitrittstest] Tempo: {}", text);
        } else {
            Constants.LOG.error("[Beitrittstest] Tempo: {}", text);
        }
        player.sendSystemMessage(Component.literal(PREFIX + (ok ? "ok " : "fail ") + text));
    }

    private static void start(ServerPlayer player, Track track) {
        ServerLevel level = player.level();
        AccFixSpeedRecorder.hook();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Optional<AccessoryType> dispenser = JoinTestEnv.exampleAddon() ? AccessoryType.byId(ExampleDispenser.DISPENSER) : Optional.empty();
        DroneBuild build = dispenser.map(DroneBuild.FREESTYLE::withAccessory).orElse(DroneBuild.FREESTYLE);
        track.actions = dispenser.isPresent();
        drone.snapTo(player.getX() + 0.5, player.getY(), player.getZ() + 4.5, 0.0F, 0.0F);
        drone.setAttitude(FACING_EAST);
        drone.setBuild(build);
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
        Constants.LOG.info("[Beitrittstest] Tempo: Drohne {} ({}) vor {}, FPV {}", drone.getId(), track.actions ? "mit Beispiel-Werfer" : "ohne Zubehör",
                player.getPlainTextName(), result);
    }
}
