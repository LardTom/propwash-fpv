package de.lardtom.propwash.dev;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.share.ShareVectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CovShareJoinCheck {
    public static final String COMMAND = "covpw3";
    public static final String IMPORTS = "covpw3 imports=";
    public static final String GIVEN = "covpw3 given";
    public static final String BENCH = "covpw3 bench ";
    public static final String HELD_NAME = "Cov-Share";
    private static final Map<UUID, AtomicInteger> COUNTS = new ConcurrentHashMap<>();
    private static boolean initialized;

    private CovShareJoinCheck() {
    }

    static synchronized void init() {
        if (JoinTestEnv.covShare()) {
            install();
        }
    }

    static synchronized void install() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerBus.PLAYER_JOIN.register(CovShareJoinCheck::joined);
        ServerBus.REGISTER_COMMANDS.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal(COMMAND)
                .then(Commands.literal("give").executes(c -> give(c.getSource())))
                .then(Commands.literal("imports").executes(c -> imports(c.getSource())))
                .then(Commands.literal("bench")
                        .then(Commands.literal("creative").executes(c -> bench(c.getSource(), GameType.CREATIVE)))
                        .then(Commands.literal("survival").executes(c -> bench(c.getSource(), GameType.SURVIVAL))))));
        PropwashEvents.BUILD_IMPORTED.register((player, request, result) -> {
            int count = COUNTS.computeIfAbsent(player.getUUID(), id -> new AtomicInteger()).incrementAndGet();
            Constants.LOG.info("[Beitrittstest] Abdeckung cov-pw3: Import-Anfrage {} von {} ({} {}): {}", count, player.getPlainTextName(), request.target(),
                    request.layers(), result.status());
        });
    }

    public static DroneData drone() {
        return new DroneData(ShareVectors.freestyle(), PaintSpec.DEFAULT.with("frame", 0x1B1D22).with("motors", 0xE8B622),
                new TuneSpec(Map.of("rates.roll.rcRate", 1.25F, "pid.roll.p", 47.0F)), Optional.of(HELD_NAME));
    }

    private static void joined(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        server.getPlayerList().deop(player.nameAndId());
        Constants.LOG.info("[Beitrittstest] Abdeckung cov-pw3: {} beigetreten, ohne Operator-Rechte", player.getPlainTextName());
    }

    private static int give(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.closeContainer();
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        player.getInventory().setItem(player.getInventory().getSelectedSlot(), PropwashApi.builds().createDrone(drone()));
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal(GIVEN));
        Constants.LOG.info("[Beitrittstest] Abdeckung cov-pw3: Drohne {} an {}", HELD_NAME, player.getPlainTextName());
        return 1;
    }

    private static int imports(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int count = COUNTS.getOrDefault(player.getUUID(), new AtomicInteger()).get();
        player.sendSystemMessage(Component.literal(IMPORTS + count));
        Constants.LOG.info("[Beitrittstest] Abdeckung cov-pw3: {} Import-Anfragen von {}", count, player.getPlainTextName());
        return 1;
    }

    private static int bench(CommandSourceStack source, GameType mode) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.closeContainer();
        player.setGameMode(mode);
        player.getInventory().clearContent();
        BlockPos pos = player.blockPosition().offset(2, 0, 2);
        Block block = BuiltInRegistries.BLOCK.getValue(Constants.id("drone_workbench"));
        player.level().removeBlock(pos, false);
        player.level().setBlockAndUpdate(pos, block.defaultBlockState());
        MenuProvider provider = player.level().getBlockState(pos).getMenuProvider(player.level(), pos);
        boolean opened = provider != null && player.openMenu(provider).isPresent();
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.literal(BENCH + mode.getName() + " " + (opened ? "open" : "closed")));
        Constants.LOG.info("[Beitrittstest] Abdeckung cov-pw3: {} im Modus {} an der Werkbank {} ({})", player.getPlainTextName(), mode.getName(),
                pos.toShortString(), opened ? "offen" : "nicht offen");
        return 1;
    }
}
