package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.ImportRateLimit;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.race.export.RaceDataMirror;
import de.lardtom.propwash.server.WorldRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class RaceStandards extends SavedData {
    public static final String KEY = "propwash.race_rules.";
    private static final int DENIALS_PER_WINDOW = 2;
    private static final long DENIAL_WINDOW_TICKS = 100L;
    private static final Map<UUID, RaceStandard> SENT = new HashMap<>();
    private static ImportRateLimit denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
    private static boolean initialized;
    private static volatile @Nullable MinecraftServer running;
    private static volatile RaceStandard active = RaceStandard.DEFAULTS;
    private static volatile @Nullable Supplier<RaceStandard> client;

    public static final Codec<RaceStandards> CODEC = RecordCodecBuilder.create(i -> i.group(
            RaceStandard.CODEC.optionalFieldOf("standard", RaceStandard.DEFAULTS).forGetter(RaceStandards::standard),
            Codec.LONG.optionalFieldOf("changed_at", 0L).forGetter(RaceStandards::changedAt),
            Codec.STRING.optionalFieldOf("changed_by", "").forGetter(RaceStandards::changedBy)
    ).apply(i, RaceStandards::new));
    public static final SavedDataType<RaceStandards> TYPE = new SavedDataType<>(Constants.id("race_standard"), RaceStandards::new, CODEC, null);

    private RaceStandard standard;
    private long changedAt;
    private String changedBy;

    public RaceStandards() {
        this(RaceStandard.DEFAULTS, 0L, "");
    }

    private RaceStandards(RaceStandard standard, long changedAt, String changedBy) {
        this.standard = standard;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public RaceStandard standard() {
        return this.standard;
    }

    public long changedAt() {
        return this.changedAt;
    }

    public String changedBy() {
        return this.changedBy;
    }

    public enum Action {
        ADOPT,
        RESET;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record SyncS2C(RaceStandard standard) implements CustomPacketPayload {
        public static final Type<SyncS2C> TYPE = new Type<>(Constants.id("race_standard"));
        public static final StreamCodec<FriendlyByteBuf, SyncS2C> CODEC = StreamCodec.of((buf, payload) -> payload.standard.write(buf),
                buf -> new SyncS2C(RaceStandard.read(buf)));

        public SyncS2C {
            Objects.requireNonNull(standard, "standard");
        }

        @Override
        public Type<SyncS2C> type() {
            return TYPE;
        }
    }

    public record EditC2S(Action action) implements CustomPacketPayload {
        public static final Type<EditC2S> TYPE = new Type<>(Constants.id("race_standard_edit"));
        public static final StreamCodec<FriendlyByteBuf, EditC2S> CODEC = StreamCodec.of((buf, payload) -> buf.writeEnum(payload.action),
                buf -> new EditC2S(buf.readEnum(Action.class)));

        public EditC2S {
            Objects.requireNonNull(action, "action");
        }

        @Override
        public Type<EditC2S> type() {
            return TYPE;
        }
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        Leaderboard.scoringSource(RaceStandards::scoring);
        PropwashNet.registerS2C(SyncS2C.TYPE, SyncS2C.CODEC);
        PropwashNet.registerC2S(EditC2S.TYPE, EditC2S.CODEC, RaceStandards::onEdit);
        ServerBus.STARTED.register(server -> {
            SENT.clear();
            denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
            running = server;
            active = get(server).standard;
        });
        ServerBus.STOPPED.register(server -> {
            SENT.clear();
            if (running == server) {
                running = null;
                active = RaceStandard.DEFAULTS;
            }
        });
        ServerBus.PLAYER_JOIN.register(RaceStandards::send);
        ServerBus.PLAYER_LEAVE.register(player -> {
            SENT.remove(player.getUUID());
            denials.forget(player.getUUID());
        });
    }

    public static RaceStandards get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static RaceStandard of(@Nullable MinecraftServer server) {
        if (server == null) {
            return RaceStandard.DEFAULTS;
        }
        return server == running ? active : get(server).standard;
    }

    public static RaceStandard server(@Nullable MinecraftServer server) {
        return RaceStandard.adopt(RaceRules.values(server));
    }

    public static RaceStandard serverNow() {
        MinecraftServer server = running;
        return server == null ? RaceStandard.DEFAULTS : server(server);
    }

    public static long changedAtNow() {
        MinecraftServer server = running;
        return server == null ? 0L : get(server).changedAt;
    }

    public static String changedByNow() {
        MinecraftServer server = running;
        return server == null ? "" : get(server).changedBy;
    }

    public static void client(@Nullable Supplier<RaceStandard> source) {
        client = source;
    }

    public static RaceStandard current() {
        MinecraftServer server = running;
        Supplier<RaceStandard> source = client;
        if (server != null && (source == null || server.isSameThread())) {
            return active;
        }
        return source == null ? RaceStandard.DEFAULTS : source.get();
    }

    public static Leaderboard.Scoring scoring() {
        MinecraftServer server = running;
        if (server == null) {
            return Leaderboard.Scoring.DEFAULT;
        }
        return new Leaderboard.Scoring(active);
    }

    public static boolean set(MinecraftServer server, RaceStandard next, @Nullable Component by, String byName) {
        RaceStandards data = get(server);
        if (data.standard.equals(next)) {
            return false;
        }
        data.standard = next;
        data.changedAt = System.currentTimeMillis();
        data.changedBy = byName;
        data.setDirty();
        if (server == running) {
            active = next;
        }
        Constants.LOG.info("{} setzt den Rennstandard: {}", byName, next.isDefault() ? "Propwash-Standardwerte" : next.values());
        if (by != null) {
            Component message = Component.translatable(next.isDefault() ? KEY + "changed.defaults" : KEY + "changed.custom", by,
                    next.differences().size()).withStyle(ChatFormatting.GRAY);
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                other.sendSystemMessage(message);
            }
        }
        changed(server);
        return true;
    }

    private static void changed(MinecraftServer server) {
        sync(server);
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null) {
            for (CourseRegistry.Course course : registry.courses()) {
                if (!course.worldCourse.isEmpty()) {
                    registry.refreshBoard(course);
                }
            }
            RaceDataMirror.touchAll(registry);
        }
    }

    public static void sync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    private static void send(ServerPlayer player) {
        RaceStandard standard = of(player.level().getServer());
        if (!standard.equals(SENT.get(player.getUUID())) && PropwashNet.sendToPlayer(player, new SyncS2C(standard))) {
            SENT.put(player.getUUID(), standard);
        }
    }

    private static void onEdit(EditC2S payload, ServerPayloadContext context) {
        edit(payload, context.player());
    }

    public static boolean edit(EditC2S payload, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        if (!WorldRules.mayEdit(player)) {
            if (denials.tryAcquire(player.getUUID(), server.getTickCount())) {
                player.sendSystemMessage(Component.translatable(KEY + "denied").withStyle(ChatFormatting.RED));
                Constants.LOG.warn("{} wollte den Rennstandard ohne Berechtigung ändern", player.getGameProfile().name());
            }
            SENT.remove(player.getUUID());
            send(player);
            return false;
        }
        RaceStandard next = switch (payload.action()) {
            case ADOPT -> RaceStandard.adopt(RaceRules.values(server));
            case RESET -> RaceStandard.DEFAULTS;
        };
        return set(server, next, player.getDisplayName(), player.getGameProfile().name());
    }
}
