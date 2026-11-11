package de.lardtom.propwash.rx;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.ServerRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RxServer {
    public static final int EVALUATE_TICKS = 2;
    public static final int LINK_TICKS = 10;
    public static final int DEMAND_TICKS = 20;
    public static final int STALE_TICKS = 100;
    public static final int MAX_INPUTS_PER_SECOND = 25;
    public static final double DEMAND_MARGIN = 16.0;
    public static final double CONFIG_REACH_BUFFER = 1.0;
    public static final int OPERATOR_LEVEL = 2;
    private static final Map<UUID, Pilot> PILOTS = new HashMap<>();
    private static long tick;
    private static boolean initialized;

    public enum Outcome {
        ACCEPTED,
        NO_DEMAND,
        OUTSIDE_DEMAND,
        RATE_LIMITED,
        OLD_SEQUENCE,
        TOO_FAR,
        NOT_LOADED,
        NO_BLOCK,
        FOREIGN,
        INVALID;

        public boolean accepted() {
            return this == ACCEPTED;
        }
    }

    private RxServer() {
    }

    static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerS2C(RxDemandS2C.TYPE, RxDemandS2C.CODEC);
        PropwashNet.registerC2S(RxInputC2S.TYPE, RxInputC2S.CODEC, RxServer::onInput);
        PropwashNet.registerC2S(RxConfigC2S.TYPE, RxConfigC2S.CODEC, RxServer::onConfig);
        ServerBus.STARTED.register(server -> {
            PILOTS.clear();
            tick = 0L;
        });
        ServerBus.STOPPED.register(server -> PILOTS.clear());
        ServerBus.PLAYER_LEAVE.register(player -> PILOTS.remove(player.getUUID()));
        ServerBus.TICK_END.register(RxServer::tick);
    }

    public static long now() {
        return tick;
    }

    private static void tick(MinecraftServer server) {
        tick++;
        if (tick % DEMAND_TICKS == 0L) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                refreshDemand(player);
            }
        }
        if (tick % EVALUATE_TICKS == 0L) {
            evaluate(server);
        }
    }

    public static boolean active(ServerLevel level, BlockPos pos) {
        return level.shouldTickBlocksAt(pos) && level.getChunkSource().isPositionTicking(ChunkPos.pack(pos));
    }

    private static void evaluate(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            RxIndex index = RxIndex.existing(level);
            if (index == null || !index.hasOwners()) {
                continue;
            }
            Cells cells = null;
            for (UUID owner : index.owners()) {
                ServerPlayer player = server.getPlayerList().getPlayer(owner);
                if (player == null || player.level() != level || player.isRemoved()) {
                    continue;
                }
                Pilot pilot = PILOTS.get(owner);
                for (RxBlockEntity entity : index.ownedBy(owner)) {
                    BlockPos pos = entity.getBlockPos();
                    if (entity.isRemoved() || !active(level, pos)) {
                        continue;
                    }
                    if (cells == null) {
                        cells = new Cells(level);
                    }
                    update(level, entity, player, pilot, cells);
                }
            }
        }
    }

    private static void update(ServerLevel level, RxBlockEntity entity, ServerPlayer player, @Nullable Pilot pilot, Cells cells) {
        BlockPos pos = entity.getBlockPos();
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof RxBlock)) {
            return;
        }
        RxSettings settings = entity.settings();
        RxSource source = settings.source();
        int power = current.getValue(RxBlock.POWER);
        RxLinkState link;
        if (source == null) {
            link = RxLinkState.NONE;
        } else {
            if (entity.linkDue(tick, LINK_TICKS)) {
                Vec3 eye = player.getEyePosition();
                entity.link(tick, RxLink.linked(RxBlock.unlimited(current), eye.x, eye.y, eye.z, pos.getX(), pos.getY(), pos.getZ(), settings.range(),
                        cells));
            }
            float value = pilot == null ? Float.NaN : pilot.value(source, tick);
            if (entity.inRange() && Float.isFinite(value)) {
                link = RxLinkState.LINKED;
                power = settings.strength(value, power);
            } else {
                link = RxLinkState.LOST;
            }
        }
        BlockState next = current.setValue(RxBlock.POWER, power).setValue(RxBlock.LINK, link);
        if (next != current) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
    }

    public static void refreshDemand(ServerPlayer player) {
        RxMask mask = demandOf(player);
        Pilot pilot = PILOTS.get(player.getUUID());
        if (pilot == null) {
            if (mask.empty()) {
                return;
            }
            pilot = new Pilot();
            PILOTS.put(player.getUUID(), pilot);
        }
        if (!mask.equals(pilot.demand)) {
            pilot.demand = mask;
            if (connected(player)) {
                PropwashNet.sendToPlayer(player, new RxDemandS2C(mask));
            }
        }
    }

    private static boolean connected(ServerPlayer player) {
        return player.connection != null && player.connection.isAcceptingMessages();
    }

    private static void tell(ServerPlayer player, Component message) {
        if (connected(player)) {
            player.sendOverlayMessage(message);
        }
    }

    public static RxMask demandOf(ServerPlayer player) {
        RxIndex index = RxIndex.existing(player.level());
        if (index == null) {
            return RxMask.NONE;
        }
        Vec3 eye = player.getEyePosition();
        RxMask mask = RxMask.NONE;
        for (RxBlockEntity entity : index.ownedBy(player.getUUID())) {
            RxSettings settings = entity.settings();
            RxSource source = settings.source();
            if (source == null || entity.isRemoved()) {
                continue;
            }
            if (RxLink.demands(RxBlock.unlimited(entity.getBlockState()), eye.distanceToSqr(Vec3.atCenterOf(entity.getBlockPos())), settings.range(),
                    DEMAND_MARGIN)) {
                mask = mask.with(source);
            }
        }
        return mask;
    }

    public static void forget(UUID player) {
        PILOTS.remove(player);
    }

    public static RxMask sentDemand(UUID player) {
        Pilot pilot = PILOTS.get(player);
        return pilot == null ? RxMask.NONE : pilot.demand;
    }

    public static float lastValue(UUID player, RxSource source) {
        Pilot pilot = PILOTS.get(player);
        return pilot == null ? Float.NaN : pilot.value(source, tick);
    }

    private static void onInput(RxInputC2S payload, ServerPayloadContext context) {
        Outcome outcome = input(context.player(), payload);
        if (!outcome.accepted()) {
            Pilot pilot = PILOTS.get(context.player().getUUID());
            if (pilot != null && pilot.rejected++ == 0) {
                Constants.LOG.debug("RX-Werte von {} verworfen: {}", context.player().getPlainTextName(), outcome);
            }
        }
    }

    public static Outcome input(ServerPlayer player, RxInputC2S payload) {
        Pilot pilot = PILOTS.get(player.getUUID());
        if (pilot == null || pilot.demand.empty()) {
            return Outcome.NO_DEMAND;
        }
        if (!payload.mask().within(pilot.demand)) {
            return Outcome.OUTSIDE_DEMAND;
        }
        if (tick - pilot.windowStart >= DEMAND_TICKS || tick < pilot.windowStart) {
            pilot.windowStart = tick;
            pilot.windowCount = 0;
        }
        if (++pilot.windowCount > MAX_INPUTS_PER_SECOND) {
            return Outcome.RATE_LIMITED;
        }
        if (pilot.sequenced && payload.sequence() - pilot.sequence <= 0) {
            return Outcome.OLD_SEQUENCE;
        }
        pilot.sequenced = true;
        pilot.sequence = payload.sequence();
        payload.unpack(pilot.sticks, pilot.axes);
        pilot.pressed = payload.pressed();
        pilot.received = payload.mask();
        pilot.receivedTick = tick;
        return Outcome.ACCEPTED;
    }

    private static void onConfig(RxConfigC2S payload, ServerPayloadContext context) {
        Outcome outcome = config(context.player(), payload);
        if (outcome == Outcome.FOREIGN) {
            RxBlockEntity entity = blockEntity(context.level(), payload.pos());
            if (entity != null) {
                tell(context.player(), foreign(context.level(), entity.settings()));
            }
        }
    }

    public static Outcome config(ServerPlayer player, RxConfigC2S payload) {
        ServerLevel level = player.level();
        BlockPos pos = payload.pos();
        if (!player.isWithinBlockInteractionRange(pos, CONFIG_REACH_BUFFER)) {
            return Outcome.TOO_FAR;
        }
        if (!active(level, pos)) {
            return Outcome.NOT_LOADED;
        }
        RxBlockEntity entity = blockEntity(level, pos);
        if (entity == null) {
            return Outcome.NO_BLOCK;
        }
        RxSettings settings = entity.settings();
        RxAccess access = access(player, settings);
        RxSettings next;
        switch (payload.action()) {
            case BIND -> {
                if (!access.mayBind()) {
                    return Outcome.FOREIGN;
                }
                next = settings.withOwner(player.getUUID());
            }
            case UNBIND -> {
                if (!access.mayChange()) {
                    return access == RxAccess.UNBOUND ? Outcome.INVALID : Outcome.FOREIGN;
                }
                next = settings.unbound();
            }
            case SOURCE -> {
                if (!access.mayChange()) {
                    return access == RxAccess.UNBOUND ? Outcome.INVALID : Outcome.FOREIGN;
                }
                RxSource source = payload.source();
                if (source == null) {
                    return Outcome.INVALID;
                }
                next = settings.withSource(source);
            }
            case LOW, HIGH -> {
                if (!access.mayChange()) {
                    return access == RxAccess.UNBOUND ? Outcome.INVALID : Outcome.FOREIGN;
                }
                if (!RxSettings.validEndpoint(payload.value()) || settings.source() == null) {
                    return Outcome.INVALID;
                }
                next = payload.action() == RxConfigC2S.Action.LOW ? settings.withLow(payload.value()) : settings.withHigh(payload.value());
            }
            case RANGE -> {
                if (!access.mayChange()) {
                    return access == RxAccess.UNBOUND ? Outcome.INVALID : Outcome.FOREIGN;
                }
                float value = payload.value();
                if (!Float.isFinite(value) || value != Math.rint(value) || !RxLink.validRange((int) value)) {
                    return Outcome.INVALID;
                }
                next = settings.withRange((int) value);
            }
            default -> {
                return Outcome.INVALID;
            }
        }
        UUID previousOwner = settings.owner();
        entity.apply(next);
        if (payload.action() == RxConfigC2S.Action.UNBIND) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof RxBlock) {
                BlockState reset = state.setValue(RxBlock.POWER, 0).setValue(RxBlock.LINK, RxLinkState.NONE);
                if (reset != state) {
                    level.setBlock(pos, reset, Block.UPDATE_ALL);
                }
            }
        }
        refreshDemandOf(level.getServer(), previousOwner);
        refreshDemandOf(level.getServer(), next.owner());
        return Outcome.ACCEPTED;
    }

    private static void refreshDemandOf(@Nullable MinecraftServer server, @Nullable UUID owner) {
        if (server == null || owner == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            refreshDemand(player);
        }
    }

    static void useRemote(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RxBlockEntity entity = blockEntity(level, pos);
        if (entity == null) {
            return;
        }
        RxSettings settings = entity.settings();
        RxAccess access = access(player, settings);
        if (access == RxAccess.UNBOUND) {
            entity.apply(settings.withOwner(player.getUUID()));
            refreshDemand(player);
            tell(player, Component.translatable(RxText.BOUND_TO, player.getDisplayName()).withStyle(ChatFormatting.AQUA));
        } else if (access == RxAccess.FOREIGN) {
            tell(player, foreign(level, settings));
        }
    }

    static void showStatus(ServerPlayer player, ServerLevel level, BlockPos pos) {
        RxBlockEntity entity = blockEntity(level, pos);
        BlockState state = level.getBlockState(pos);
        if (entity == null || !(state.getBlock() instanceof RxBlock)) {
            return;
        }
        RxSettings settings = entity.settings();
        tell(player, RxText.status(settings, state.getValue(RxBlock.POWER), ownerName(level, settings.owner()), RxBlock.unlimited(state)).copy()
                .withStyle(ChatFormatting.GRAY));
    }

    private static Component foreign(Level level, RxSettings settings) {
        return Component.translatable(RxText.FOREIGN, RxText.owner(ownerName(level, settings.owner()), settings.owner())).withStyle(ChatFormatting.RED);
    }

    public static RxAccess access(ServerPlayer player, RxSettings settings) {
        return RxAccess.of(settings.owner(), player.getUUID(), ServerRules.hasPermissionLevel(player, OPERATOR_LEVEL));
    }

    public static @Nullable RxBlockEntity blockEntity(ServerLevel level, BlockPos pos) {
        if (!active(level, pos)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof RxBlockEntity rx && !rx.isRemoved() ? rx : null;
    }

    public static String ownerName(@Nullable Level level, @Nullable UUID owner) {
        if (owner == null || level == null) {
            return "";
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return "";
        }
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) {
            return online.getPlainTextName();
        }
        Optional<NameAndId> cached = server.services().nameToIdCache().get(owner);
        return cached.map(NameAndId::name).orElse("");
    }

    static final class Pilot {
        final float[] sticks = new float[RxSource.STICKS];
        final float[] axes = new float[RxSource.AXES];
        RxMask demand = RxMask.NONE;
        RxMask received = RxMask.NONE;
        int pressed;
        long receivedTick = Long.MIN_VALUE;
        boolean sequenced;
        int sequence;
        long windowStart = -DEMAND_TICKS;
        int windowCount;
        int rejected;

        float value(RxSource source, long now) {
            if (this.receivedTick == Long.MIN_VALUE || now - this.receivedTick > STALE_TICKS || !this.received.contains(source)) {
                return Float.NaN;
            }
            return switch (source.kind()) {
                case STICK -> this.sticks[source.index()];
                case AXIS -> this.axes[source.index()];
                case BUTTON -> (this.pressed & source.bit()) != 0 ? 1.0F : 0.0F;
            };
        }
    }

    private static final class Cells implements RxLink.Cells {
        private final ServerLevel level;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;
        private @Nullable LevelChunk chunk;

        Cells(ServerLevel level) {
            this.level = level;
        }

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            return this.chunk(chunkX, chunkZ) != null;
        }

        @Override
        public boolean opaque(int x, int y, int z) {
            if (y < this.level.getMinY() || y > this.level.getMaxY()) {
                return false;
            }
            LevelChunk at = this.chunk(x >> 4, z >> 4);
            return at != null && at.getBlockState(this.cursor.set(x, y, z)).isSolidRender();
        }

        private @Nullable LevelChunk chunk(int x, int z) {
            if (x != this.chunkX || z != this.chunkZ) {
                this.chunkX = x;
                this.chunkZ = z;
                this.chunk = this.level.shouldTickBlocksAt(ChunkPos.pack(x, z)) ? this.level.getChunkSource().getChunkNow(x, z) : null;
            }
            return this.chunk;
        }
    }
}
