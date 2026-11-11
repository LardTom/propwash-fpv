package de.lardtom.propwash.server;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.rescue.RescueSettings;
import de.lardtom.propwash.rescue.ReturnPlan;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClearServerChecks {
    public static final String TAG = "propwash_clear_check";
    private static final String EMPTY_TAG = "propwash_clear_none";
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-4000-8000-0000000c1ea1");
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-4000-8000-0000000c1ea2");
    private static final UUID PILOT_ID = UUID.fromString("00000000-0000-4000-8000-0000000c1ea3");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-4000-8000-0000000c1ea4");
    private static final int HEIGHT = 12;
    private static final double HOME_DISTANCE = 20.0;
    private static final int READY_TICKS = 25;
    private static final int SETTLE_TICKS = 3;
    private static final int NOT_RUN = -1;

    private static final List<QuadcopterEntity> TARGETS = new ArrayList<>();
    private static @Nullable QuadcopterEntity flown;
    private static @Nullable QuadcopterEntity returning;
    private static @Nullable QuadcopterEntity control;
    private static @Nullable Entity marker;
    private static @Nullable String failure;
    private static String summary = "";
    private static int stage;
    private static int clearedAt;
    private static int ticketsBefore;
    private static int flightsBefore;
    private static int ticketsDuring;
    private static @Nullable ChunkPos ticketChunk;

    private ClearServerChecks() {
    }

    public static @Nullable String failure() {
        return failure;
    }

    public static String summary() {
        return summary;
    }

    public static boolean tick(MinecraftServer server, int tick) {
        if (stage >= 3) {
            return true;
        }
        try {
            if (stage == 0) {
                start(server);
                stage = 1;
                return false;
            }
            if (stage == 1) {
                if (!ready(server)) {
                    if (tick > READY_TICKS) {
                        return done("Heimflug ohne Ticket nach " + tick + " Ticks: fliegt " + flying() + ", Tickets "
                                + RangeManager.ticketCount() + " (vorher " + ticketsBefore + ")");
                    }
                    return false;
                }
                String problem = clear(server);
                if (problem != null) {
                    return done(problem);
                }
                clearedAt = tick;
                stage = 2;
                return false;
            }
            if (tick < clearedAt + SETTLE_TICKS) {
                return false;
            }
            return done(settled(server));
        } catch (RuntimeException e) {
            return done("Ausnahme: " + e);
        }
    }

    private static void start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        int x = (spawn.getX() >> 4 << 4) + 3;
        int z = (spawn.getZ() >> 4 << 4) + 8;
        int y = spawn.getY() + HEIGHT;
        ticketsBefore = RangeManager.ticketCount();
        flightsBefore = AutonomousReturns.flights();
        flown = spawn(level, x, y, z, true);
        returning = spawn(level, x + 3, y, z, true);
        QuadcopterEntity idle = spawn(level, x + 6, y, z, true);
        TARGETS.add(flown);
        TARGETS.add(returning);
        TARGETS.add(idle);
        control = spawn(level, x + 9, y, z, false);
        Entity probe = EntityTypes.MARKER.create(level, EntitySpawnReason.COMMAND);
        if (probe == null) {
            throw new IllegalStateException("Marker nicht erzeugt");
        }
        probe.addTag(TAG);
        probe.snapTo(x + 0.5, y, z + 2.5, 0.0F, 0.0F);
        if (!level.addFreshEntity(probe)) {
            throw new IllegalStateException("Marker nicht gespawnt");
        }
        marker = probe;
        returning.setReturnPlan(ReturnPlan.of(OWNER_ID, x + 3.5 + HOME_DISTANCE, spawn.getY(), z + 0.5, false, new RescueSettings()));
    }

    private static boolean ready(MinecraftServer server) {
        QuadcopterEntity drone = returning;
        if (drone == null || !flying() || RangeManager.ticketCount() <= ticketsBefore) {
            return false;
        }
        ticketChunk = ticketNear(server.overworld(), drone.chunkPosition());
        ticketsDuring = RangeManager.ticketCount();
        return ticketChunk != null;
    }

    private static boolean flying() {
        QuadcopterEntity drone = returning;
        return drone != null && !drone.isRemoved() && AutonomousReturns.flying(drone) && drone.returnPlan() != null;
    }

    private static @Nullable String clear(MinecraftServer server) {
        QuadcopterEntity a = flown;
        QuadcopterEntity b = returning;
        if (a == null || b == null) {
            return "Drohnen fehlen";
        }
        ServerPlayer pilot = player(server, PILOT_ID, "Clear_Pilot");
        pilot.snapTo(a.getX() + 1.0, a.getY(), a.getZ());
        DronePiloting.StartResult started = DronePiloting.tryStart(pilot, a, PilotMode.LINE_OF_SIGHT);
        if (started != DronePiloting.StartResult.STARTED || !a.isFlownBy(PILOT_ID)) {
            return "Pilot startet nicht: " + started;
        }
        if (RangeManager.link(PILOT_ID).isEmpty() || DronePiloting.pilotedBy(PILOT_ID).orElse(null) != a) {
            return "Pilot-Sitzung nicht angemeldet";
        }
        ServerPlayer guest = player(server, GUEST_ID, "Clear_Guest");
        if (server.getPlayerList().isOp(guest.nameAndId())) {
            return "Gast steht schon in der Operator-Liste";
        }
        int guestAll = run(server, guest.createCommandSourceStack(), "propwash clear");
        int guestTargets = run(server, guest.createCommandSourceStack(), "propwash clear @e[tag=" + TAG + "]");
        for (QuadcopterEntity drone : TARGETS) {
            if (drone.isRemoved()) {
                return "Gast ohne Recht entfernt Drohne " + drone.getId() + " (Ergebnis " + guestAll + "/" + guestTargets + ")";
            }
        }
        if (guestAll > 0 || guestTargets > 0 || !a.isFlownBy(PILOT_ID) || b.returnPlan() == null || !AutonomousReturns.flying(b)) {
            return "Gast ohne Recht: Ergebnis " + guestAll + "/" + guestTargets + ", Pilot " + a.isFlownBy(PILOT_ID) + ", Heimflug "
                    + AutonomousReturns.flying(b);
        }
        ServerPlayer operator = player(server, OPERATOR_ID, "Clear_Operator");
        int none;
        int removed;
        server.getPlayerList().op(operator.nameAndId());
        try {
            none = run(server, operator.createCommandSourceStack(), "propwash clear @e[tag=" + EMPTY_TAG + "]");
            removed = run(server, operator.createCommandSourceStack(), "propwash clear @e[tag=" + TAG + "]");
        } finally {
            server.getPlayerList().deop(operator.nameAndId());
        }
        if (none != 0) {
            return "Selektor ohne Drohnen meldet " + none + " statt 0";
        }
        if (removed != TARGETS.size()) {
            return "Operator entfernt " + removed + " statt " + TARGETS.size() + " Drohnen";
        }
        ServerLevel level = server.overworld();
        for (QuadcopterEntity drone : TARGETS) {
            if (!drone.isRemoved() || level.getEntity(drone.getUUID()) != null) {
                return "Drohne " + drone.getId() + " nach dem Befehl noch im Level";
            }
        }
        if (a.pilot().isPresent() || DronePiloting.pilotedBy(PILOT_ID).isPresent() || RangeManager.link(PILOT_ID).isPresent()) {
            return "Pilot-Sitzung nicht beendet: Pilot " + a.pilot() + ", Verbindung " + RangeManager.link(PILOT_ID).isPresent();
        }
        if (b.returnPlan() != null) {
            return "Heimflug-Plan nicht gelöscht";
        }
        QuadcopterEntity kept = control;
        if (kept == null || kept.isRemoved()) {
            return "Drohne ohne Tag mit entfernt";
        }
        Entity probe = marker;
        if (probe == null || probe.isRemoved()) {
            return "Entity ohne Drohne im Selektor entfernt";
        }
        return null;
    }

    private static @Nullable String settled(MinecraftServer server) {
        QuadcopterEntity b = returning;
        if (b != null && AutonomousReturns.flying(b)) {
            return "Heimflug nach " + SETTLE_TICKS + " Ticks nicht beendet";
        }
        if (AutonomousReturns.flights() > flightsBefore) {
            return "Heimflüge " + AutonomousReturns.flights() + " statt " + flightsBefore;
        }
        ChunkPos chunk = ticketChunk;
        if (chunk != null && droneTicket(server.overworld(), chunk)) {
            return "Drohnen-Ticket bei " + chunk + " nach " + SETTLE_TICKS + " Ticks nicht gelöst";
        }
        if (RangeManager.ticketCount() > ticketsBefore) {
            return "Tickets " + RangeManager.ticketCount() + " statt höchstens " + ticketsBefore;
        }
        summary = "Gast ohne Recht abgelehnt (/propwash clear und mit Selektor), Operator: Selektor ohne Drohnen 0, "
                + TARGETS.size() + " Drohnen mit Selektor entfernt (eine geflogen, eine im Heimflug, eine abgestellt), Pilot-Sitzung beendet, "
                + "Heimflug beendet, Drohnen-Ticket gelöst (Tickets " + ticketsDuring + " → " + RangeManager.ticketCount()
                + "), Drohne ohne Tag und Marker mit Tag bleiben";
        return null;
    }

    private static boolean done(@Nullable String problem) {
        failure = problem;
        stage = 3;
        for (QuadcopterEntity drone : TARGETS) {
            if (!drone.isRemoved()) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.setReturnPlan(null);
                drone.discard();
            }
        }
        if (control != null && !control.isRemoved()) {
            control.discard();
        }
        if (marker != null && !marker.isRemoved()) {
            marker.discard();
        }
        return true;
    }

    private static @Nullable ChunkPos ticketNear(ServerLevel level, ChunkPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos pos = new ChunkPos(center.x() + dx, center.z() + dz);
                if (droneTicket(level, pos)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean droneTicket(ServerLevel level, ChunkPos pos) {
        TicketStorage storage = level.getDataStorage().get(TicketStorage.TYPE);
        if (storage == null) {
            return false;
        }
        TicketType type = ServerModule.DRONE_TICKET.get();
        for (Ticket ticket : storage.getTickets(pos.pack())) {
            if (ticket.getType() == type) {
                return true;
            }
        }
        return false;
    }

    private static int run(MinecraftServer server, CommandSourceStack source, String command) {
        AtomicInteger result = new AtomicInteger(NOT_RUN);
        server.getCommands().performPrefixedCommand(source.withCallback((success, value) -> result.set(success ? value : 0)), command);
        return result.get();
    }

    private static QuadcopterEntity spawn(ServerLevel level, int x, int y, int z, boolean tagged) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
        if (tagged) {
            drone.addTag(TAG);
        }
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        return drone;
    }

    private static ServerPlayer player(MinecraftServer server, UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }
}
