package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.share.ShareServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ShareJoinCheck {
    public static final String GIVE_CODE = "PW3-0Qrgmm3Vrwu7BmBGyzRkDxxPh7PFgND2kDVUl5KebVEk4Qm6UEyT";
    public static final int GIVE_TICKS = 60;
    public static final int BENCH_TICKS = 140;
    private static final Map<UUID, Integer> JOINED = new HashMap<>();
    private static final ConcurrentLinkedQueue<Gift> GIFTS = new ConcurrentLinkedQueue<>();
    private static boolean listening;
    private static int gifts;

    private record Gift(UUID player, List<Identifier> parts) {
    }

    private ShareJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        if (!listening) {
            listening = true;
            PropwashEvents.BUILD_IMPORTED.register((player, request, result) -> {
                Constants.LOG.info("[Beitrittstest] Sharecode: Import von {} mit {} Ebenen: {}, fehlend {}", player.getPlainTextName(),
                        request.layers(), result.status(), result.missingParts().size());
                if (result.status() == ImportStatus.MISSING_PARTS && !result.missingParts().isEmpty()) {
                    GIFTS.add(new Gift(player.getUUID(), List.copyOf(result.missingParts())));
                }
            });
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int joined = JOINED.computeIfAbsent(player.getUUID(), id -> tick);
            if (tick - joined == GIVE_TICKS) {
                give(server, player);
            }
            if (tick - joined == BENCH_TICKS) {
                bench(player);
            }
        }
        Gift gift;
        while ((gift = GIFTS.poll()) != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(gift.player());
            if (player == null) {
                continue;
            }
            List<String> given = new ArrayList<>();
            for (Identifier id : gift.parts()) {
                ItemStack stack = PropwashApi.partItems().stack(id);
                if (!stack.isEmpty()) {
                    ShareServer.give(player, stack);
                    given.add(id.toString());
                }
            }
            gifts++;
            Constants.LOG.info("[Beitrittstest] Sharecode: {} fehlende Teile an {} gegeben (Runde {}): {}", given.size(), player.getPlainTextName(), gifts,
                    given);
        }
    }

    private static void give(MinecraftServer server, ServerPlayer player) {
        int result = server.getCommands().getDispatcher().getRoot() == null ? 0 : execute(server, "propwash code give " + GIVE_CODE + " "
                + player.getScoreboardName());
        Constants.LOG.info("[Beitrittstest] Sharecode: /propwash code give an {} ergab {}", player.getPlainTextName(), result);
    }

    private static int execute(MinecraftServer server, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
        } catch (Exception e) {
            Constants.LOG.error("[Beitrittstest] Sharecode: Befehl {} warf", command, e);
            return -1;
        }
    }

    private static void bench(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition().offset(2, 0, 2);
        Block block = BuiltInRegistries.BLOCK.getValue(Constants.id("drone_workbench"));
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
        boolean opened = provider != null && player.openMenu(provider).isPresent();
        player.containerMenu.broadcastChanges();
        Constants.LOG.info("[Beitrittstest] Sharecode: {} im Überlebensmodus an der Werkbank {} (Menü {})", player.getPlainTextName(), pos.toShortString(),
                opened ? "offen" : "nicht offen");
    }
}
