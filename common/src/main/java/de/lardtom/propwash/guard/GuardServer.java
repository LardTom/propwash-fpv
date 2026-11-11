package de.lardtom.propwash.guard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuardServer {
    public static final String HINT_KEY = "propwash.guard.server_hint";
    private static final Set<UUID> NOTIFIED = ConcurrentHashMap.newKeySet();

    private GuardServer() {
    }

    public static void started(MinecraftServer server) {
        NOTIFIED.clear();
        check(server, "server start");
    }

    public static void reloaded(MinecraftServer server) {
        check(server, "reload");
    }

    static void check(MinecraftServer server, String phase) {
        long start = System.nanoTime();
        GuardReport report = NamespaceGuard.report();
        NamespaceGuard.scan(GuardCategory.REGISTRY, () -> RegistryScan.run(report));
        NamespaceGuard.scan(GuardCategory.NETWORK, () -> NetworkScan.run(report, false));
        NamespaceGuard.scan(GuardCategory.COMMAND, () -> CommandScan.run(report, server.getCommands().getDispatcher().getRoot()));
        NamespaceGuard.scan(GuardCategory.DATA, () -> FileScan.playerPacks(server.getResourceManager(), PackType.SERVER_DATA));
        NamespaceGuard.finish(phase, start);
    }

    public static void joined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isDedicatedServer() || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }
        Component hint = hint();
        if (hint != null && NOTIFIED.add(player.getUUID())) {
            player.sendSystemMessage(hint);
        }
    }

    public static @Nullable Component hint() {
        GuardReport report = NamespaceGuard.report();
        List<String> mods = report.mods();
        if (mods.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (String mod : mods) {
            names.add(NamespaceGuard.name(mod));
        }
        String list = String.join(", ", names);
        int count = report.count();
        return Component.translatableWithFallback(HINT_KEY, "Server: %s use the reserved namespace propwash (%s findings, see server log).", list,
                count).withStyle(ChatFormatting.GOLD);
    }
}
