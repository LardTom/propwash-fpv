package de.lardtom.propwash.drone.operator;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.operator.net.OperatorInputC2S;
import de.lardtom.propwash.drone.operator.net.OperatorRoleS2C;
import de.lardtom.propwash.drone.operator.net.OperatorStateS2C;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.SpectatorLinks;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.video.SpectateServer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class OperatorServer {
    private static final Map<UUID, Binding> BINDINGS = new LinkedHashMap<>();
    private static int accepted;
    private static int refused;
    private static int relayed;

    private OperatorServer() {
    }

    static final class Binding {
        final QuadcopterEntity drone;
        final UUID operator;
        ServerPlayer player;
        final short[] axes = new short[OperatorChannel.AXES];
        int actions;
        final OperatorChannel.Rate rate = new OperatorChannel.Rate();
        @Nullable UUID notifiedPilot;

        Binding(QuadcopterEntity drone, ServerPlayer player) {
            this.drone = drone;
            this.operator = player.getUUID();
            this.player = player;
        }
    }

    public static synchronized boolean bind(Entity entity, ServerPlayer operator) {
        if (!(entity instanceof QuadcopterEntity drone) || drone.isRemoved() || drone.level().isClientSide() || operator.hasDisconnected()) {
            return false;
        }
        Binding current = BINDINGS.get(drone.getUUID());
        OperatorChannel.Denial denial = OperatorChannel.bind(drone.isFlownBy(operator.getUUID()), pilotsOther(operator, drone),
                operator.level() == drone.level(), distance(operator, drone), range(operator, drone), current == null ? null : current.operator,
                operator.getUUID());
        if (denial != OperatorChannel.Denial.NONE) {
            Constants.LOG.debug("Operator {} für Drohne {} abgelehnt: {}", operator.getPlainTextName(), drone.getId(), denial);
            return false;
        }
        if (current != null) {
            current.player = operator;
            return true;
        }
        for (Binding other : new ArrayList<>(BINDINGS.values())) {
            if (other.operator.equals(operator.getUUID())) {
                release(other, false);
            }
        }
        Binding binding = new Binding(drone, operator);
        BINDINGS.put(drone.getUUID(), binding);
        if (connected(operator) && SpectateServer.watching(operator) == null) {
            SpectatorLinks.watch(operator, drone);
        }
        tell(operator, new OperatorRoleS2C(drone.getId(), drone.getUUID(), OperatorRoleS2C.Role.OPERATOR,
                DroneInteractions.droneName(drone).getString()));
        notifyPilot(binding);
        relay(binding);
        Constants.LOG.info("{} steuert jetzt die Kamera von Drohne {}", operator.getPlainTextName(), drone.getId());
        return true;
    }

    public static synchronized boolean unbind(Entity drone) {
        Binding binding = BINDINGS.get(drone.getUUID());
        if (binding == null) {
            return false;
        }
        release(binding, true);
        return true;
    }

    public static synchronized Optional<ServerPlayer> operator(Entity drone) {
        Binding binding = BINDINGS.get(drone.getUUID());
        return binding == null ? Optional.empty() : Optional.of(binding.player);
    }

    public static synchronized double axis(Entity drone, int index) {
        Binding binding = BINDINGS.get(drone.getUUID());
        if (binding == null || index < 0 || index >= OperatorChannel.AXES) {
            return 0.0;
        }
        return OperatorChannel.axis(binding.axes[index]);
    }

    public static synchronized boolean action(Entity drone, int number) {
        Binding binding = BINDINGS.get(drone.getUUID());
        return binding != null && OperatorChannel.action(binding.actions, number);
    }

    public static synchronized void feed(QuadcopterEntity drone, DroneSim sim) {
        Binding binding = BINDINGS.get(drone.getUUID());
        for (int i = 0; i < OperatorChannel.AXES; i++) {
            sim.setOperatorAxis(i, binding == null ? 0.0 : OperatorChannel.axis(binding.axes[i]));
        }
    }

    public static synchronized int bound() {
        return BINDINGS.size();
    }

    public static synchronized int accepted() {
        return accepted;
    }

    public static synchronized int refused() {
        return refused;
    }

    public static synchronized int relayed() {
        return relayed;
    }

    public static synchronized void onInput(OperatorInputC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        Entity entity = player.level().getEntity(payload.entityId());
        Binding binding = entity instanceof QuadcopterEntity drone ? BINDINGS.get(drone.getUUID()) : null;
        if (binding == null || binding.drone != entity || !OperatorChannel.accepts(binding.operator, player.getUUID())) {
            refused++;
            return;
        }
        if (!binding.rate.accept(Util.getMillis())) {
            refused++;
            return;
        }
        if (OperatorChannel.leaves(payload.bits())) {
            release(binding, true);
            return;
        }
        binding.player = player;
        for (int i = 0; i < OperatorChannel.AXES; i++) {
            binding.axes[i] = payload.axis(i);
        }
        binding.actions = OperatorChannel.sanitize(payload.bits());
        accepted++;
        relay(binding);
    }

    static synchronized void onTick(MinecraftServer server) {
        if (BINDINGS.isEmpty()) {
            return;
        }
        for (Binding binding : new ArrayList<>(BINDINGS.values())) {
            QuadcopterEntity drone = binding.drone;
            ServerPlayer live = server.getPlayerList().getPlayer(binding.operator);
            if (live != null) {
                binding.player = live;
            }
            ServerPlayer operator = binding.player;
            if (drone.isRemoved() || operator.hasDisconnected() || live == null && operator.isRemoved()) {
                release(binding, true);
                continue;
            }
            OperatorChannel.Denial keep = OperatorChannel.keep(drone.isFlownBy(operator.getUUID()), pilotsOther(operator, drone),
                    operator.level() == drone.level(), distance(operator, drone), range(operator, drone));
            if (keep != OperatorChannel.Denial.NONE) {
                Constants.LOG.info("Kamera-Steuerung von Drohne {} durch {} beendet: {}", drone.getId(), operator.getPlainTextName(), keep);
                release(binding, true);
                continue;
            }
            if (live != null && SpectateServer.watching(live) == null) {
                SpectatorLinks.watch(live, drone);
            }
            notifyPilot(binding);
        }
    }

    static synchronized void onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous, Optional<UUID> current) {
        Binding binding = BINDINGS.get(drone.getUUID());
        if (binding == null) {
            return;
        }
        if (current.isPresent() && current.get().equals(binding.operator)) {
            release(binding, true);
            return;
        }
        notifyPilot(binding);
    }

    static synchronized void onLeave(ServerPlayer player) {
        for (Binding binding : new ArrayList<>(BINDINGS.values())) {
            if (binding.operator.equals(player.getUUID())) {
                release(binding, true);
            } else if (player.getUUID().equals(binding.notifiedPilot)) {
                binding.notifiedPilot = null;
            }
        }
    }

    static synchronized void clear() {
        BINDINGS.clear();
        accepted = 0;
        refused = 0;
        relayed = 0;
    }

    private static void release(Binding binding, boolean tell) {
        BINDINGS.remove(binding.drone.getUUID(), binding);
        ServerPlayer operator = binding.player;
        if (connected(operator) && SpectatorLinks.watched(operator) == binding.drone && SpectateServer.watching(operator) == null) {
            SpectatorLinks.unwatch(operator);
        }
        Arrays.fill(binding.axes, (short) 0);
        binding.actions = 0;
        relay(binding);
        if (tell) {
            tell(operator, new OperatorRoleS2C(binding.drone.getId(), binding.drone.getUUID(), OperatorRoleS2C.Role.NONE,
                    DroneInteractions.droneName(binding.drone).getString()));
        }
        ServerPlayer pilot = player(binding.drone, binding.notifiedPilot);
        if (pilot != null) {
            PropwashNet.sendToPlayer(pilot, new OperatorRoleS2C(binding.drone.getId(), binding.drone.getUUID(), OperatorRoleS2C.Role.NONE, operator.getPlainTextName()));
        }
        binding.notifiedPilot = null;
    }

    private static void notifyPilot(Binding binding) {
        UUID pilotId = binding.drone.pilot().orElse(null);
        if (pilotId == null ? binding.notifiedPilot == null : pilotId.equals(binding.notifiedPilot)) {
            return;
        }
        ServerPlayer previous = player(binding.drone, binding.notifiedPilot);
        if (previous != null) {
            PropwashNet.sendToPlayer(previous, new OperatorRoleS2C(binding.drone.getId(), binding.drone.getUUID(), OperatorRoleS2C.Role.NONE, binding.player.getPlainTextName()));
        }
        binding.notifiedPilot = pilotId;
        ServerPlayer pilot = player(binding.drone, pilotId);
        if (pilot != null) {
            PropwashNet.sendToPlayer(pilot, new OperatorRoleS2C(binding.drone.getId(), binding.drone.getUUID(), OperatorRoleS2C.Role.PILOT, binding.player.getPlainTextName()));
            PropwashNet.sendToPlayer(pilot, state(binding));
        }
    }

    private static void relay(Binding binding) {
        OperatorStateS2C state = state(binding);
        List<ServerPlayer> sent = new ArrayList<>(4);
        send(binding.player, state, sent);
        LivingEntity pilot = binding.drone.pilotEntity();
        if (pilot instanceof ServerPlayer player) {
            send(player, state, sent);
        }
        MinecraftServer server = binding.drone.level().getServer();
        if (server != null) {
            for (ServerPlayer watcher : server.getPlayerList().getPlayers()) {
                if (SpectateServer.watching(watcher) == binding.drone) {
                    send(watcher, state, sent);
                }
            }
        }
        relayed++;
    }

    private static void send(ServerPlayer player, OperatorStateS2C state, List<ServerPlayer> sent) {
        if (!sent.contains(player)) {
            sent.add(player);
            tell(player, state);
        }
    }

    private static void tell(ServerPlayer player, CustomPacketPayload payload) {
        if (connected(player)) {
            PropwashNet.sendToPlayer(player, payload);
        }
    }

    private static boolean connected(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server != null && server.getPlayerList().getPlayer(player.getUUID()) == player && !player.hasDisconnected();
    }

    private static OperatorStateS2C state(Binding binding) {
        return new OperatorStateS2C(binding.drone.getId(), binding.axes, binding.actions);
    }

    private static @Nullable ServerPlayer player(QuadcopterEntity drone, @Nullable UUID id) {
        MinecraftServer server = drone.level().getServer();
        return id == null || server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static boolean pilotsOther(ServerPlayer operator, QuadcopterEntity drone) {
        return DronePiloting.pilotedBy(operator.getUUID()).filter(flown -> flown != drone).isPresent();
    }

    private static double distance(ServerPlayer operator, QuadcopterEntity drone) {
        return operator.getEyePosition().distanceTo(drone.position());
    }

    private static double range(ServerPlayer operator, QuadcopterEntity drone) {
        return DronePiloting.freeRange(operator, drone) ? Double.POSITIVE_INFINITY : drone.build().video().rangeBlocks;
    }
}
