package de.lardtom.propwash.zone;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.ImportRateLimit;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleAccess;
import de.lardtom.propwash.server.rules.RuleBase;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class ZoneServer {
    public static final String CREATED = "propwash.zone.msg.created";
    public static final String CHANGED = "propwash.zone.msg.changed";
    public static final String DELETED = "propwash.zone.msg.deleted";
    public static final String DENIED = "propwash.zone.msg.denied";
    public static final String PROBLEM = "propwash.zone.problem.";
    public static final String RELEASED = "propwash.zones_open.msg.released";
    public static final String LOCKED = "propwash.zones_open.msg.locked";
    public static final String JUMPED = "propwash.zones_open.jump.done";
    public static final String JUMP_DENIED = "propwash.zones_open.jump.denied";
    public static final String JUMP_ELSEWHERE = "propwash.zones_open.jump.elsewhere";
    public static final String JUMP_NO_GROUND = "propwash.zones_open.jump.no_ground";
    private static final int EDITABLE_CHECK_TICKS = 20;
    private static final int DENIALS_PER_WINDOW = 2;
    private static final long DENIAL_WINDOW_TICKS = 100L;
    private static final Map<UUID, Sent> SENT = new HashMap<>();
    private static ImportRateLimit denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
    private static volatile @Nullable MinecraftServer running;
    private static boolean initialized;

    private record Sent(int version, String dimension, boolean editable, boolean creatable) {
    }

    public record Outcome(@Nullable Zone zone, @Nullable String problem) {
        public boolean ok() {
            return this.problem == null;
        }

        static Outcome failed(String problem) {
            return new Outcome(null, problem);
        }
    }

    private ZoneServer() {
    }

    static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerS2C(ZonesS2C.TYPE, ZonesS2C.CODEC);
        PropwashNet.registerC2S(ZoneEditC2S.TYPE, ZoneEditC2S.CODEC, ZoneServer::onEdit);
        PropwashNet.registerC2S(ZoneReleaseC2S.TYPE, ZoneReleaseC2S.CODEC, ZoneServer::onRelease);
        PropwashNet.registerC2S(ZoneJumpC2S.TYPE, ZoneJumpC2S.CODEC, ZoneServer::onJump);
        ServerBus.STARTED.register(server -> {
            SENT.clear();
            denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
            running = server;
            ZoneNames.server(ZoneServer::nameOnServerThread);
        });
        ServerBus.STOPPED.register(server -> {
            SENT.clear();
            if (running == server) {
                running = null;
                ZoneNames.server(null);
            }
        });
        ServerBus.PLAYER_JOIN.register(player -> send(player, true));
        ServerBus.PLAYER_LEAVE.register(player -> {
            SENT.remove(player.getUUID());
            denials.forget(player.getUUID());
        });
        ServerBus.TICK_END.register(ZoneServer::sync);
    }

    private static @Nullable String nameOnServerThread(int id) {
        MinecraftServer server = running;
        if (server == null || !server.isSameThread()) {
            return null;
        }
        Zone zone = ZoneData.get(server).get(id);
        return zone == null ? null : zone.name();
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    public static List<Zone> zones(Level level) {
        MinecraftServer server = level.getServer();
        return server == null ? List.of() : ZoneData.get(server).effectiveIn(dimension(level));
    }

    public static List<Zone> stored(Level level) {
        MinecraftServer server = level.getServer();
        return server == null ? List.of() : ZoneData.get(server).in(dimension(level));
    }

    public static ZoneResolution resolve(Level level, double x, double y, double z) {
        MinecraftServer server = level.getServer();
        PhysicsRules world = WorldRules.physics(server);
        if (server == null) {
            return ZoneResolution.world(world);
        }
        return ZoneResolver.resolve(ZoneData.get(server).effectiveIn(dimension(level)), world, x, y, z);
    }

    public static ZoneResolution resolve(Level level, @Nullable UUID player, double x, double y, double z) {
        MinecraftServer server = level.getServer();
        RuleBase base = PersonalRulesServer.base(server, player);
        if (server == null) {
            return ZoneResolution.world(base);
        }
        return ZoneResolver.resolve(ZoneData.get(server).effectiveIn(dimension(level)), base, x, y, z);
    }

    public static boolean mayEdit(ServerPlayer player) {
        return WorldRules.mayEdit(player);
    }

    public static boolean playersCreate(@Nullable MinecraftServer server) {
        return WorldRules.on(server, WorldRuleSpecs.PLAYERS_CREATE_ZONES);
    }

    public static boolean mayCreate(ServerPlayer player) {
        return ZoneRights.mayCreate(mayEdit(player), playersCreate(player.level().getServer()));
    }

    public static boolean mayChange(ServerPlayer player, Zone zone) {
        return ZoneRights.mayChange(mayEdit(player), playersCreate(player.level().getServer()), zone, player.getUUID());
    }

    public static boolean mayDelete(ServerPlayer player, Zone zone) {
        return ZoneRights.mayDelete(mayEdit(player), playersCreate(player.level().getServer()), zone, player.getUUID());
    }

    public static boolean mayJump(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        try {
            return RuleAccess.runs(server.getCommands().getDispatcher().getRoot(), player.createCommandSourceStack(), ZoneMenu.TELEPORT);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static ZoneOwner owner(ServerPlayer player) {
        return new ZoneOwner(player.getUUID(), player.getGameProfile().name());
    }

    public static ZonesS2C snapshotFor(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        String dimension = dimension(player.level());
        ZoneData data = server == null ? null : ZoneData.get(server);
        return new ZonesS2C(mayEdit(player), mayCreate(player), dimension, data == null ? List.of() : data.in(dimension),
                data == null ? ZoneRelease.DEFAULT : data.release());
    }

    private static void send(ServerPlayer player, boolean force) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ZoneData data = ZoneData.get(server);
        String dimension = dimension(player.level());
        Sent last = SENT.get(player.getUUID());
        boolean recheck = force || last == null || last.version != data.version() || !last.dimension.equals(dimension)
                || server.getTickCount() % EDITABLE_CHECK_TICKS == Math.floorMod(player.getUUID().hashCode(), EDITABLE_CHECK_TICKS);
        if (!recheck) {
            return;
        }
        boolean editable = mayEdit(player);
        boolean creatable = ZoneRights.mayCreate(editable, playersCreate(server));
        Sent next = new Sent(data.version(), dimension, editable, creatable);
        if (!force && next.equals(last)) {
            return;
        }
        if (PropwashNet.sendToPlayer(player, new ZonesS2C(editable, creatable, dimension, data.in(dimension), data.release()))) {
            SENT.put(player.getUUID(), next);
        }
    }

    public static void sync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, false);
        }
    }

    private static void onEdit(ZoneEditC2S payload, ServerPayloadContext context) {
        edit(payload, context.player());
    }

    public static Outcome edit(ZoneEditC2S payload, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return Outcome.failed(ZoneCheck.UNKNOWN);
        }
        ZoneData data = ZoneData.get(server);
        Zone sent = payload.zone();
        Zone stored = sent.id() == Zone.NEW ? null : data.get(sent.id());
        boolean admin = mayEdit(player);
        if (!ZoneRights.maySend(admin, playersCreate(server), payload.action(), sent, stored, player.getUUID())) {
            if (denials.tryAcquire(player.getUUID(), server.getTickCount())) {
                player.sendSystemMessage(Component.translatable(DENIED).withStyle(ChatFormatting.RED));
                Constants.LOG.warn("{} wollte eine Zone ohne Berechtigung ändern", player.getGameProfile().name());
                SENT.remove(player.getUUID());
            }
            return Outcome.failed(ZoneCheck.DENIED);
        }
        Outcome outcome = switch (payload.action()) {
            case SAVE -> save(server, prepared(data, sent, stored, admin, owner(player), dimension(player.level())), player.getDisplayName(),
                    player.getGameProfile().name());
            case DELETE -> delete(server, sent.id(), player.getDisplayName(), player.getGameProfile().name());
        };
        if (!outcome.ok()) {
            player.sendSystemMessage(Component.translatable(PROBLEM + outcome.problem()).withStyle(ChatFormatting.RED));
        }
        return outcome;
    }

    static Zone prepared(ZoneData data, Zone sent, @Nullable Zone stored, boolean admin, ZoneOwner editor, String dimension) {
        Zone candidate = stored == null
                ? (sent.id() == Zone.NEW ? sent.withDimension(dimension).withOwner(ZoneRights.ownerOfNew(admin, sent, editor)) : sent)
                : sent.withOwner(ZoneRights.ownerOfChanged(stored, editor));
        return data.release().enforce(candidate, stored);
    }

    public static Outcome save(MinecraftServer server, Zone zone, Component by, String byName) {
        ZoneData data = ZoneData.get(server);
        Zone stored = zone.id() == Zone.NEW ? null : data.get(zone.id());
        Zone candidate = stored == null ? zone : zone.withDimension(stored.dimension());
        String problem = ZoneCheck.problem(candidate, data.in(candidate.dimension()));
        if (problem != null) {
            return Outcome.failed(problem);
        }
        if (candidate.equals(stored)) {
            return new Outcome(stored, null);
        }
        Zone saved = data.put(candidate);
        Constants.LOG.info(stored == null ? "{} legt die Zone {} an ({}, {})" : "{} ändert die Zone {} ({}, {})", byName, saved.name(), saved.id(), saved.dimension());
        broadcast(server, Component.translatable(stored == null ? CREATED : CHANGED, by, saved.name()));
        sync(server);
        return new Outcome(saved, null);
    }

    public static Outcome delete(MinecraftServer server, int id, Component by, String byName) {
        Zone removed = ZoneData.get(server).remove(id);
        if (removed == null) {
            return Outcome.failed(ZoneCheck.UNKNOWN);
        }
        Constants.LOG.info("{} löscht die Zone {} ({}, {})", byName, removed.name(), removed.id(), removed.dimension());
        broadcast(server, Component.translatable(DELETED, by, removed.name()));
        sync(server);
        return new Outcome(removed, null);
    }

    private static void onJump(ZoneJumpC2S payload, ServerPayloadContext context) {
        jump(payload, context.player());
    }

    public static Outcome jump(ZoneJumpC2S payload, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return Outcome.failed(ZoneCheck.UNKNOWN);
        }
        Zone zone = ZoneData.get(server).get(payload.id());
        if (!mayJump(player) || zone == null || !ZoneMenu.listed(mayEdit(player), zone, player.getUUID())) {
            if (denials.tryAcquire(player.getUUID(), server.getTickCount())) {
                player.sendSystemMessage(Component.translatable(JUMP_DENIED).withStyle(ChatFormatting.RED));
                Constants.LOG.warn("{} wollte ohne Recht zur Zone {} springen", player.getGameProfile().name(), payload.id());
            }
            return Outcome.failed(ZoneCheck.DENIED);
        }
        ServerLevel level = player.level();
        if (!zone.dimension().equals(dimension(level))) {
            player.sendSystemMessage(Component.translatable(JUMP_ELSEWHERE, zone.name()).withStyle(ChatFormatting.RED));
            return Outcome.failed(ZoneCheck.UNKNOWN);
        }
        ZoneBox box = zone.box();
        level.getChunk(SectionPos.blockToSectionCoord(box.centerX()), SectionPos.blockToSectionCoord(box.centerZ()));
        double[] at = ZoneMenu.jumpTarget(zone, level.getMinY(), (x, y, z) -> standable(level, x, y, z));
        if (at == null) {
            player.sendSystemMessage(Component.translatable(JUMP_NO_GROUND, zone.name()).withStyle(ChatFormatting.RED));
            return Outcome.failed(ZoneCheck.UNKNOWN);
        }
        player.teleportTo(level, at[0], at[1], at[2], Set.of(), player.getYRot(), player.getXRot(), true);
        Constants.LOG.info("{} springt zur Zone {} ({}, {})", player.getGameProfile().name(), zone.name(), zone.id(), zone.dimension());
        player.sendSystemMessage(Component.translatable(JUMPED, zone.name()).withStyle(ChatFormatting.GRAY));
        return new Outcome(zone, null);
    }

    static boolean standable(ServerLevel level, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos below = feet.below();
        BlockPos head = feet.above();
        if (level.isOutsideBuildHeight(below) || level.isOutsideBuildHeight(head)) {
            return false;
        }
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && free(level, feet) && free(level, head);
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    private static void onRelease(ZoneReleaseC2S payload, ServerPayloadContext context) {
        release(payload, context.player());
    }

    public static boolean release(ZoneReleaseC2S payload, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        if (!mayEdit(player)) {
            if (denials.tryAcquire(player.getUUID(), server.getTickCount())) {
                player.sendSystemMessage(Component.translatable("propwash.server.world_rules.denied").withStyle(ChatFormatting.RED));
                Constants.LOG.warn("{} wollte die Freigabe der Welt-Regeln für Zonen ohne Berechtigung ändern", player.getGameProfile().name());
                SENT.remove(player.getUUID());
            }
            return false;
        }
        return release(server, payload.changes(), player.getDisplayName(), player.getGameProfile().name()) >= 0;
    }

    public static int release(MinecraftServer server, Map<String, Boolean> changes, Component by, String byName) {
        ZoneData data = ZoneData.get(server);
        ZoneRelease next = data.release();
        List<Component> messages = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : new TreeMap<>(changes).entrySet()) {
            RuleSpec spec = ZoneRelease.spec(entry.getKey());
            if (spec == null) {
                Constants.LOG.warn("{} wollte die Regel {} für Zonen freigeben, Zonen können sie nicht ändern", byName, entry.getKey());
                continue;
            }
            boolean on = entry.getValue();
            if (next.released(spec) == on) {
                continue;
            }
            next = next.with(spec.id(), on);
            Constants.LOG.info("{} {} die Welt-Regel {} für Zonen", byName, on ? "gibt" : "sperrt", spec.id());
            messages.add(Component.translatable(on ? RELEASED : LOCKED, by, Component.translatable(spec.translationKey())));
        }
        if (!data.setRelease(next)) {
            return 0;
        }
        for (Component message : messages) {
            broadcast(server, message);
        }
        sync(server);
        return messages.size();
    }

    private static void broadcast(MinecraftServer server, Component message) {
        Component styled = message.copy().withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(styled);
        }
    }
}
