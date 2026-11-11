package de.lardtom.propwash.dev;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.ZoneEditC2S;
import de.lardtom.propwash.zone.ZoneOwner;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ZonesOpenJoinCheck {
    public static final String ZONE = "Beitrittstest Spielerzone";
    public static final String ADMIN_ZONE = "Beitrittstest Admin-Zone";
    public static final String OTHER_ZONE = "Beitrittstest Zone B";
    public static final int OTHER_PRIORITY = 9;
    public static final int OPERATOR_PRIORITY = 3;
    public static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-0000000002b0");
    public static final UUID OPERATOR = UUID.fromString("00000000-0000-4000-8000-0000000002c0");
    private static final int OPERATOR_AFTER_TICKS = 60;
    private static final int LOCK_AFTER_TICKS = 160;
    private static final int UNLOCK_AFTER_TICKS = 900;
    private static int seenAt = -1;
    private static boolean otherTried;
    private static boolean operatorDone;
    private static boolean locked;
    private static boolean unlocked;
    private static boolean extras;

    private ZonesOpenJoinCheck() {
    }

    public static ServerPlayer fake(MinecraftServer server, UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static @Nullable Zone find(MinecraftServer server) {
        return find(server, ZONE);
    }

    private static @Nullable Zone find(MinecraftServer server, String name) {
        for (Zone zone : ZoneData.get(server).all()) {
            if (zone.name().equals(name)) {
                return zone;
            }
        }
        return null;
    }

    private static void extras(MinecraftServer server, Zone own) {
        extras = true;
        ZoneBox box = own.box();
        Zone admin = Zone.create(own.dimension(), ADMIN_ZONE, ZoneShape.BOX, ZoneBox.around(box.centerX() + 40.0, box.minY(), box.centerZ(), 8.0, 8.0, 8.0));
        Zone other = Zone.create(own.dimension(), OTHER_ZONE, ZoneShape.BOX, ZoneBox.around(box.centerX() - 40.0, box.minY(), box.centerZ(), 8.0, 8.0, 8.0))
                .withOwner(new ZoneOwner(OTHER, "zones_open_b"));
        ZoneServer.Outcome a = ZoneServer.save(server, admin, Component.literal("Beitrittstest"), "Beitrittstest");
        ZoneServer.Outcome b = ZoneServer.save(server, other, Component.literal("Beitrittstest"), "Beitrittstest");
        Constants.LOG.info("[Beitrittstest] zones-open: Admin-Zone {}, Zone von Spieler B {}", a.ok() ? "angelegt" : "abgelehnt: " + a.problem(),
                b.ok() ? "angelegt" : "abgelehnt: " + b.problem());
    }

    private static void removeExtras(MinecraftServer server) {
        extras = false;
        for (String name : new String[]{ADMIN_ZONE, OTHER_ZONE}) {
            Zone zone = find(server, name);
            if (zone != null) {
                ZoneServer.delete(server, zone.id(), Component.literal("Beitrittstest"), "Beitrittstest");
            }
        }
    }

    static void tick(MinecraftServer server, int tick) {
        Zone zone = find(server);
        if (seenAt < 0) {
            if (zone == null) {
                return;
            }
            seenAt = tick;
            Constants.LOG.info("[Beitrittstest] zones-open: {} legt die Zone {} an (Besitzer {}, Operator: {})",
                    zone.owner() == null ? "?" : zone.owner().name(), ZONE, zone.owner() == null ? "keiner" : zone.owner().id(), ownerMayEdit(server, zone));
        }
        int since = tick - seenAt;
        if (locked && !unlocked && (zone == null || since >= UNLOCK_AFTER_TICKS)) {
            unlock(server);
        }
        if (zone == null) {
            if (extras) {
                removeExtras(server);
            }
            return;
        }
        if (!otherTried) {
            otherTried = true;
            extras(server, zone);
            ServerPlayer other = fake(server, OTHER, "zones_open_b");
            ZoneServer.Outcome save = ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, zone.withPriority(OTHER_PRIORITY)), other);
            ZoneServer.Outcome delete = ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.DELETE, zone), other);
            Constants.LOG.info("[Beitrittstest] zones-open: Spieler B ändert die Zone: {}, löscht sie: {}", save.ok() ? "angenommen" : save.problem(),
                    delete.ok() ? "angenommen" : delete.problem());
        } else if (!operatorDone && since >= OPERATOR_AFTER_TICKS) {
            operatorDone = true;
            ServerPlayer operator = fake(server, OPERATOR, "zones_open_op");
            server.getPlayerList().op(operator.nameAndId());
            try {
                ZoneServer.Outcome outcome = ZoneServer.edit(new ZoneEditC2S(ZoneEditC2S.Action.SAVE, zone.withPriority(OPERATOR_PRIORITY)), operator);
                Zone after = find(server);
                Constants.LOG.info("[Beitrittstest] zones-open: Operator ändert die Zone: {}, Besitzer danach {}", outcome.ok() ? "angenommen"
                        : outcome.problem(), after == null || after.owner() == null ? "keiner" : after.owner().name());
            } finally {
                server.getPlayerList().deop(operator.nameAndId());
            }
        } else if (operatorDone && !locked && since >= LOCK_AFTER_TICKS) {
            locked = true;
            int changed = ZoneServer.release(server, Map.of(WorldRuleSpecs.GRAVITY_SCALE.id(), false), Component.literal("Beitrittstest"), "Beitrittstest");
            Constants.LOG.info("[Beitrittstest] zones-open: Schwerkraft für Zonen gesperrt ({} Änderung), Wert der Zone gespeichert: {}", changed,
                    zone.value(WorldRuleSpecs.GRAVITY_SCALE, -1));
        }
    }

    private static void unlock(MinecraftServer server) {
        unlocked = true;
        ZoneServer.release(server, Map.of(WorldRuleSpecs.GRAVITY_SCALE.id(), true), Component.literal("Beitrittstest"), "Beitrittstest");
        Constants.LOG.info("[Beitrittstest] zones-open: Schwerkraft für Zonen wieder freigegeben");
    }

    private static boolean ownerMayEdit(MinecraftServer server, Zone zone) {
        if (zone.owner() == null) {
            return false;
        }
        Optional<ServerPlayer> owner = Optional.ofNullable(server.getPlayerList().getPlayer(zone.owner().id()));
        return owner.map(ZoneServer::mayEdit).orElse(false);
    }
}
