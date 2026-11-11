package de.lardtom.propwash.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CovPlayerListAccess {
    private CovPlayerListAccess() {
    }

    public static void online(MinecraftServer server, ServerPlayer player, boolean add, boolean byId) {
        PlayerList list = server.getPlayerList();
        List<ServerPlayer> players = field(list, "players");
        if (add) {
            if (!players.contains(player)) {
                players.add(player);
            }
        } else {
            players.remove(player);
        }
        if (byId) {
            Map<UUID, ServerPlayer> ids = field(list, "playersByUUID");
            if (add) {
                ids.put(player.getUUID(), player);
            } else {
                ids.remove(player.getUUID(), player);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(PlayerList list, String name) {
        try {
            Field field = PlayerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(list);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Spielerliste nicht erreichbar (" + name + "): " + e, e);
        }
    }
}
