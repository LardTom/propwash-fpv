package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.example.ExampleFollow;
import de.lardtom.propwash.dev.example.ExampleOperator;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.operator.OperatorChannel;
import de.lardtom.propwash.drone.operator.OperatorServer;
import de.lardtom.propwash.drone.operator.net.OperatorInputC2S;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.server.WorldRulesRights;
import de.lardtom.propwash.sim.DroneSim;
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

public final class OperatorJoinCheck {
    public static final int SETTLE_TICKS = 60;
    public static final double TAKEOFF = 4.0;
    public static final int BIND_AFTER_TICKS = 80;
    public static final int OPERATOR_TICKS = 20 * 60;
    public static final int GUEST_TICKS = 20 * 8;
    public static final int FPV_WAIT_TICKS = 20 * 30;
    private static final int CLEAR = 4;
    private static final int CLEAR_HEIGHT = 10;
    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private OperatorJoinCheck() {
    }

    private static final class Track {
        final int joined;
        int stage;
        int mark;
        int droneId = -1;
        int sent;
        double maxAxis;
        ServerPlayer guest;

        Track(int joined) {
            this.joined = joined;
        }
    }

    static void tick(MinecraftServer server, int tick) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track(tick));
            try {
                step(server, player, track, tick);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Operator: Stufe {} fehlgeschlagen", track.stage, e);
                track.stage = 99;
            }
        }
    }

    private static void step(MinecraftServer server, ServerPlayer player, Track track, int tick) {
        int waited = tick - track.mark;
        switch (track.stage) {
            case 0 -> {
                if (tick - track.joined >= SETTLE_TICKS) {
                    QuadcopterEntity drone = spawn(player, 2.5);
                    track.droneId = drone.getId();
                    goggles(player, drone);
                    AutonomyResult result = ExampleFollow.takeoff(drone, player, TAKEOFF);
                    Constants.LOG.info("[Beitrittstest] Operator a: Drohne {} mit Gimbal vorn, autonomes Halten am Server: {}", drone.getId(), result);
                    track.stage = result.started() ? 1 : 99;
                    track.mark = tick;
                }
            }
            case 1 -> {
                QuadcopterEntity drone = drone(player, track);
                if (waited >= BIND_AFTER_TICKS) {
                    boolean bound = ExampleOperator.bind(drone, player);
                    Constants.LOG.info("[Beitrittstest] Operator a: {} wird Operator der Drohne im Halten ({}, Flug {}, Höhe {})",
                            player.getPlainTextName(), bound ? "gebunden" : "verweigert: " + ExampleOperator.LAST.get(),
                            AddonAutonomy.active(drone).map(Object::toString).orElse("-"), String.format(Locale.ROOT, "%.1f", drone.getY() - player.getY()));
                    track.stage = bound ? 2 : 99;
                    track.mark = tick;
                }
            }
            case 2 -> {
                QuadcopterEntity drone = drone(player, track);
                DroneSim sim = AddonAutonomy.sim(drone);
                double axis = sim == null ? Double.NaN : sim.operatorAxis(0);
                if (Double.isFinite(axis)) {
                    track.maxAxis = Math.max(track.maxAxis, axis);
                }
                if (OperatorServer.operator(drone).isEmpty()) {
                    Constants.LOG.info("[Beitrittstest] Operator a: Kamera zurückgegeben nach {} Ticks, Server: {} angenommen, {} abgewiesen, "
                                    + "FlightState#operatorAxis(0) höchstens {}, Flug {}", waited, OperatorServer.accepted(), OperatorServer.refused(),
                            String.format(Locale.ROOT, "%.2f", track.maxAxis), AddonAutonomy.active(drone).map(Object::toString).orElse("-"));
                    ExampleFollow.stop(drone);
                    drone.discard();
                    QuadcopterEntity flown = spawn(player, 3.0);
                    track.droneId = flown.getId();
                    goggles(player, flown);
                    ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                    remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(flown));
                    player.getInventory().setItem(0, remote);
                    player.getInventory().setSelectedSlot(0);
                    player.connection.send(new ClientboundSetHeldSlotPacket(0));
                    DronePiloting.StartResult result = DronePiloting.tryStart(player, flown, PilotMode.FPV);
                    Constants.LOG.info("[Beitrittstest] Operator b: {} fliegt Drohne {} im FPV: {}", player.getPlainTextName(), flown.getId(), result);
                    track.stage = 3;
                    track.mark = tick;
                } else if (waited > OPERATOR_TICKS) {
                    Constants.LOG.error("[Beitrittstest] Operator a: Client gibt die Kamera nicht zurück, {} Eingaben angenommen", OperatorServer.accepted());
                    OperatorServer.unbind(drone);
                    track.stage = 99;
                }
            }
            case 3 -> {
                QuadcopterEntity drone = drone(player, track);
                if (!drone.isFlownBy(player.getUUID())) {
                    if (waited > FPV_WAIT_TICKS) {
                        Constants.LOG.error("[Beitrittstest] Operator b: Client fliegt nicht");
                        track.stage = 99;
                    }
                    return;
                }
                ServerPlayer guest = WorldRulesRights.guest(server);
                guest.snapTo(drone.getX() + 2.0, drone.getY(), drone.getZ() + 2.0);
                track.guest = guest;
                boolean bound = ExampleOperator.bind(drone, guest);
                Constants.LOG.info("[Beitrittstest] Operator b: simulierter Spieler {} wird Operator der geflogenen Drohne: {}", guest.getPlainTextName(),
                        bound ? "gebunden" : "verweigert: " + ExampleOperator.LAST.get());
                track.stage = bound ? 4 : 99;
                track.mark = tick;
                track.sent = 0;
            }
            case 4 -> {
                QuadcopterEntity drone = drone(player, track);
                double pan = waited < GUEST_TICKS / 2 ? 1.0 : 0.0;
                double tilt = waited < GUEST_TICKS / 2 ? 0.0 : -1.0;
                short[] axes = {OperatorChannel.quantize(pan), OperatorChannel.quantize(tilt), 0, OperatorChannel.quantize(0.25)};
                OperatorServer.onInput(new OperatorInputC2S(drone.getId(), axes, OperatorChannel.actions(waited % 40 < 20)), new ServerPayloadContext(track.guest));
                track.sent++;
                if (waited >= GUEST_TICKS) {
                    Constants.LOG.info("[Beitrittstest] Operator b: {} Eingaben des simulierten Spielers geschickt (erst schwenken, dann neigen), "
                            + "Server: {} angenommen, {} weitergegeben", track.sent, OperatorServer.accepted(), OperatorServer.relayed());
                    OperatorServer.unbind(drone);
                    track.stage = 5;
                    track.mark = tick;
                }
            }
            case 5 -> {
                if (waited > 100) {
                    QuadcopterEntity drone = drone(player, track);
                    DronePiloting.release(drone);
                    Constants.LOG.info("[Beitrittstest] Operator: fertig");
                    track.stage = 6;
                }
            }
            default -> {
            }
        }
    }

    private static QuadcopterEntity spawn(ServerPlayer player, double ahead) {
        ServerLevel level = player.level();
        int x0 = player.getBlockX();
        int y0 = player.getBlockY();
        int z0 = player.getBlockZ();
        for (int dx = -CLEAR; dx <= CLEAR; dx++) {
            for (int dz = -CLEAR; dz <= CLEAR + 4; dz++) {
                level.setBlockAndUpdate(new BlockPos(x0 + dx, y0 - 1, z0 + dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(x0 + dx, y0 + dy, z0 + dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        AccessoryType gimbal = AccessoryType.byId(ExampleOperator.GIMBAL_FRONT).orElseThrow(() -> new IllegalStateException("Beispiel-Gimbal fehlt"));
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE.withAccessory(gimbal));
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        Vec3 pos = new Vec3(x0 + 0.5, y0, z0 + 0.5 + ahead);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf());
        return drone;
    }

    private static void goggles(ServerPlayer player, QuadcopterEntity drone) {
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
    }

    private static QuadcopterEntity drone(ServerPlayer player, Track track) {
        if (!(player.level().getEntity(track.droneId) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            throw new IllegalStateException("Testdrohne verschwunden");
        }
        return drone;
    }
}
