package de.lardtom.propwash.barrier.client;

import de.lardtom.propwash.barrier.BarrierKind;
import de.lardtom.propwash.barrier.BarrierModule;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class BarrierSite {
    static final int SITE_X = -1600;
    static final int SITE_Z = 1600;
    static final int MIN_X = -4;
    static final int MAX_X = 26;
    static final int MIN_Z = -6;
    static final int MAX_Z = 50;
    static final int HEIGHT = 9;
    static final int SYNC_TICKS = 100;
    private static final AtomicReference<BlockPos> ORIGIN = new AtomicReference<>();
    private static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    private static final Map<BlockPos, BlockState> ORIGINAL = new LinkedHashMap<>();

    record Saved(Vec3 position, float yaw, float pitch, ItemStack head, ItemStack hand, ItemStack offhand, GameType mode) {
    }

    private BarrierSite() {
    }

    static @Nullable BlockPos originOrNull() {
        return ORIGIN.get();
    }

    static BlockPos origin() {
        BlockPos origin = ORIGIN.get();
        if (origin == null) {
            throw new IllegalStateException("Testplatz der Barrieren fehlt");
        }
        return origin;
    }

    static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    static BlockState state(BarrierKind kind) {
        return BarrierModule.of(kind).block().get().defaultBlockState();
    }

    static ItemStack stack(BarrierKind kind) {
        return new ItemStack(BarrierModule.of(kind).item().get());
    }

    static BlockPos prepare(MinecraftServer server) {
        BlockPos existing = ORIGIN.get();
        ServerPlayer player = player(server);
        if (existing != null) {
            return existing;
        }
        ServerLevel level = player.level();
        SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getMainHandItem().copy(), player.getOffhandItem().copy(), player.gameMode()));
        BlockPos spawn = level.getRespawnData().pos();
        int x = spawn.getX() + SITE_X;
        int z = spawn.getZ() + SITE_Z;
        level.getChunk(x >> 4, z >> 4);
        BlockPos origin = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        for (int dx = MIN_X; dx <= MAX_X; dx++) {
            for (int dz = MIN_Z; dz <= MAX_Z; dz++) {
                set(level, origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < HEIGHT; dy++) {
                    set(level, origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        ORIGIN.set(origin);
        player.setGameMode(GameType.CREATIVE);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        teleport(player, Vec3.atBottomCenterOf(origin.offset(-2, 0, 0)), -90.0F, 10.0F);
        return origin;
    }

    static void set(ServerLevel level, BlockPos pos, BlockState state) {
        ORIGINAL.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            set(level, pos, state);
        }
    }

    static void teleport(ServerPlayer player, Vec3 feet, float yaw, float pitch) {
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), yaw, pitch, true);
    }

    static void restore(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        List<Map.Entry<BlockPos, BlockState>> entries = List.copyOf(ORIGINAL.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            level.setBlock(entries.get(i).getKey(), entries.get(i).getValue(), Block.UPDATE_ALL);
        }
        ORIGINAL.clear();
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            player.setItemSlot(EquipmentSlot.HEAD, saved.head());
            player.setItemSlot(EquipmentSlot.MAINHAND, saved.hand());
            player.setItemSlot(EquipmentSlot.OFFHAND, saved.offhand());
            player.setGameMode(saved.mode());
            teleport(player, saved.position(), saved.yaw(), saved.pitch());
        }
        ORIGIN.set(null);
    }

    static @Nullable StepResult await(SelfTest.StepContext ctx, String key, String what) {
        Object value = ctx.state().get(key);
        if (!(value instanceof CompletableFuture<?> future)) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        return null;
    }
}
