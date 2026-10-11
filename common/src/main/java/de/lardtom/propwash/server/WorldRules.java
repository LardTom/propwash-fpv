package de.lardtom.propwash.server;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.ImportRateLimit;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import de.lardtom.propwash.registry.RegList;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleEffects;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class WorldRules {
    public static final GameRuleCategory CATEGORY = new GameRuleCategory(Constants.id(Constants.MOD_ID));

    private static final Map<RuleSpec, RegHolder<? extends GameRule<?>>> RULES = new LinkedHashMap<>();
    private static final Map<UUID, WorldRulesS2C> SENT = new HashMap<>();
    private static final int DENIALS_PER_WINDOW = 2;
    private static final long DENIAL_WINDOW_TICKS = 100L;
    private static ImportRateLimit denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
    private static boolean initialized;
    private static @Nullable List<GameRule<?>> cachedRules;
    private static int cachedSize = -1;
    private static volatile @Nullable MinecraftServer running;
    private static volatile @Nullable Supplier<RuleValues> clientValues;

    private WorldRules() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RegList<GameRule<?>> list = PropwashRegistries.list(Registries.GAME_RULE);
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            RULES.put(spec, list.add(spec.name(), key -> create(spec)));
        }
        PropwashNet.registerS2C(WorldRulesS2C.TYPE, WorldRulesS2C.CODEC);
        PropwashNet.registerC2S(WorldRuleEditC2S.TYPE, WorldRuleEditC2S.CODEC, WorldRules::onEdit);
        PropwashNet.registerC2S(WorldRulePresetC2S.TYPE, WorldRulePresetC2S.CODEC, WorldRules::onPreset);
        ServerBus.STARTED.register(server -> {
            SENT.clear();
            denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
            running = server;
        });
        ServerBus.STOPPED.register(server -> {
            SENT.clear();
            if (running == server) {
                running = null;
            }
        });
        ServerBus.PLAYER_JOIN.register(player -> send(player, entries(player.level().getServer())));
        ServerBus.PLAYER_LEAVE.register(player -> {
            SENT.remove(player.getUUID());
            denials.forget(player.getUUID());
        });
        ServerBus.TICK_END.register(WorldRules::sync);
    }

    static GameRule<?> create(RuleSpec spec) {
        if (spec.kind() == RuleSpec.Kind.BOOL) {
            return new GameRule<>(CATEGORY, GameRuleType.BOOL, BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean, Codec.BOOL,
                    b -> b ? 1 : 0, spec.defaultOn(), FeatureFlagSet.of());
        }
        return new GameRule<>(CATEGORY, GameRuleType.INT, IntegerArgumentType.integer(spec.min(), spec.max()), GameRuleTypeVisitor::visitInteger,
                Codec.intRange(spec.min(), spec.max()), i -> i, spec.defaultValue(), FeatureFlagSet.of());
    }

    public static Optional<GameRule<?>> rule(RuleSpec spec) {
        RegHolder<? extends GameRule<?>> holder = RULES.get(spec);
        return holder != null && holder.isBound() ? Optional.of(holder.get()) : Optional.empty();
    }

    public static boolean isPropwash(GameRule<?> rule) {
        Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
        return id != null && (Constants.MOD_ID.equals(id.getNamespace()) || CATEGORY.equals(rule.category()));
    }

    public static synchronized List<GameRule<?>> propwashRules() {
        int size = BuiltInRegistries.GAME_RULE.size();
        if (cachedRules == null || cachedSize != size) {
            List<GameRule<?>> out = new ArrayList<>();
            BuiltInRegistries.GAME_RULE.stream().filter(WorldRules::isPropwash).forEach(out::add);
            out.sort(Comparator.comparingInt(WorldRules::order).thenComparing(rule -> String.valueOf(BuiltInRegistries.GAME_RULE.getKey(rule))));
            cachedRules = List.copyOf(out);
            cachedSize = size;
        }
        return cachedRules;
    }

    public static int order(GameRule<?> rule) {
        Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
        return id == null ? Integer.MAX_VALUE : WorldRuleText.spec(id).map(WorldRuleSpecs::order).orElse(Integer.MAX_VALUE);
    }

    public static RuleValues values(@Nullable MinecraftServer server) {
        if (server == null) {
            return RuleValues.DEFAULTS;
        }
        GameRules rules = server.getGameRules();
        Map<String, Integer> map = new HashMap<>();
        for (Map.Entry<RuleSpec, RegHolder<? extends GameRule<?>>> entry : RULES.entrySet()) {
            if (entry.getValue().isBound()) {
                map.put(entry.getKey().name(), intValue(rules, entry.getValue().get(), entry.getKey().defaultValue()));
            }
        }
        return new RuleValues(map);
    }

    public static void clientValues(@Nullable Supplier<RuleValues> source) {
        clientValues = source;
    }

    public static RuleValues values(Level level) {
        if (level.isClientSide()) {
            Supplier<RuleValues> client = clientValues;
            return client == null ? RuleValues.DEFAULTS : client.get();
        }
        return values(level.getServer());
    }

    public static RuleValues current() {
        MinecraftServer server = running;
        Supplier<RuleValues> client = clientValues;
        if (server != null && (client == null || server.isSameThread())) {
            return values(server);
        }
        return client == null ? RuleValues.DEFAULTS : client.get();
    }

    public static int get(@Nullable MinecraftServer server, RuleSpec spec) {
        if (server == null) {
            return spec.defaultValue();
        }
        Optional<GameRule<?>> rule = rule(spec);
        if (rule.isEmpty()) {
            return spec.defaultValue();
        }
        int value = intValue(server.getGameRules(), rule.get(), spec.defaultValue());
        return spec.valid(value) ? value : spec.defaultValue();
    }

    public static boolean on(@Nullable MinecraftServer server, RuleSpec spec) {
        return get(server, spec) != 0;
    }

    public static PhysicsRules physics(@Nullable MinecraftServer server) {
        return PhysicsRules.of(values(server));
    }

    public static PartCompatibility partCompatibility(@Nullable MinecraftServer server) {
        return PartCompatibility.of(on(server, WorldRuleSpecs.PART_COMPATIBILITY));
    }

    public static PartCompatibility partCompatibility(Level level) {
        return RuleEffects.partCompatibility(values(level));
    }

    public static PartCompatibility partCompatibility() {
        return RuleEffects.partCompatibility(current());
    }

    public static boolean crashDamage(@Nullable MinecraftServer server) {
        return on(server, WorldRuleSpecs.CRASH_DAMAGE);
    }

    public static float crashDamageStrength(@Nullable MinecraftServer server) {
        return RuleEffects.crashDamageStrength(values(server));
    }

    public static boolean infiniteRange(@Nullable MinecraftServer server, VideoSystem transmitter) {
        return RuleEffects.infiniteRange(values(server), transmitter);
    }

    private static int intValue(GameRules rules, GameRule<?> rule, int fallback) {
        try {
            Object value = rules.get(rule);
            if (value instanceof Boolean b) {
                return b ? 1 : 0;
            }
            if (value instanceof Integer i) {
                return i;
            }
        } catch (IllegalArgumentException e) {
            Constants.LOG.debug("Welt-Regel {} nicht in dieser Welt", rule, e);
        }
        return fallback;
    }

    public static <T> boolean set(MinecraftServer server, GameRule<T> rule, T value) {
        GameRules rules = server.getGameRules();
        if (value.equals(rules.get(rule))) {
            return false;
        }
        rules.set(rule, value, server);
        return true;
    }

    public static boolean set(MinecraftServer server, RuleSpec spec, int value) {
        Optional<GameRule<?>> rule = rule(spec);
        if (rule.isEmpty() || !spec.valid(value)) {
            return false;
        }
        return setSerialized(server, rule.get(), spec.serialize(value));
    }

    static <T> boolean setSerialized(MinecraftServer server, GameRule<T> rule, String raw) {
        Optional<T> parsed = rule.deserialize(raw).result();
        return parsed.isPresent() && set(server, rule, parsed.get());
    }

    public static boolean mayEdit(ServerPlayer player) {
        return ServerRules.isHost(player) || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static List<WorldRulesS2C.Entry> entries(@Nullable MinecraftServer server) {
        List<WorldRulesS2C.Entry> out = new ArrayList<>();
        if (server == null) {
            return out;
        }
        GameRules rules = server.getGameRules();
        for (GameRule<?> rule : propwashRules()) {
            Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
            String value = serialized(rules, rule);
            if (id != null && value != null && out.size() < WorldRulesS2C.MAX_ENTRIES
                    && value.length() <= WorldRulesS2C.Entry.MAX_VALUE_LENGTH) {
                out.add(new WorldRulesS2C.Entry(id, value));
            }
        }
        return out;
    }

    private static <T> @Nullable String serialized(GameRules rules, GameRule<T> rule) {
        try {
            return rule.serialize(rules.get(rule));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static WorldRulesS2C snapshotFor(ServerPlayer player) {
        return new WorldRulesS2C(mayEdit(player), entries(player.level().getServer()));
    }

    private static void send(ServerPlayer player, List<WorldRulesS2C.Entry> entries) {
        WorldRulesS2C payload = new WorldRulesS2C(mayEdit(player), entries);
        if (!payload.equals(SENT.get(player.getUUID())) && PropwashNet.sendToPlayer(player, payload)) {
            SENT.put(player.getUUID(), payload);
        }
    }

    public static void sync(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        List<WorldRulesS2C.Entry> entries = entries(server);
        for (ServerPlayer player : players) {
            send(player, entries);
        }
    }

    private static void onEdit(WorldRuleEditC2S payload, ServerPayloadContext context) {
        edit(payload, context);
    }

    private static void onPreset(WorldRulePresetC2S payload, ServerPayloadContext context) {
        preset(payload, context);
    }

    public static Optional<RulePreset> matchingPreset(@Nullable MinecraftServer server) {
        return RulePreset.match(values(server));
    }

    public static int applyPreset(MinecraftServer server, RulePreset preset) {
        int changed = 0;
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            if (set(server, spec, preset.value(spec))) {
                changed++;
            }
        }
        return changed;
    }

    public static int applyPreset(MinecraftServer server, RulePreset preset, Component by, String byName) {
        int changed = applyPreset(server, preset);
        Constants.LOG.info("{} setzt die Welt-Regeln auf die Voreinstellung {} ({} Regeln geändert)", byName, preset.id(), changed);
        if (changed > 0) {
            Component message = Component.translatable("propwash.server.world_rules.preset_applied", by,
                    Component.translatable(preset.nameKey())).withStyle(ChatFormatting.GRAY);
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                other.sendSystemMessage(message);
            }
        }
        sync(server);
        return changed;
    }

    public static boolean preset(WorldRulePresetC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        if (server == null) {
            return false;
        }
        if (!mayEdit(player)) {
            if (reportDenial(player, server)) {
                Constants.LOG.warn("{} wollte die Voreinstellung {} ohne Berechtigung setzen", player.getGameProfile().name(), payload.preset());
            }
            return false;
        }
        Optional<RulePreset> preset = RulePreset.byId(payload.preset());
        if (preset.isEmpty()) {
            Constants.LOG.warn("{} schickte die unbekannte Voreinstellung {}", player.getGameProfile().name(), payload.preset());
            return false;
        }
        applyPreset(server, preset.get(), player.getDisplayName(), player.getGameProfile().name());
        return true;
    }

    private static boolean reportDenial(ServerPlayer player, MinecraftServer server) {
        if (!denials.tryAcquire(player.getUUID(), server.getTickCount())) {
            return false;
        }
        player.sendSystemMessage(Component.translatable("propwash.server.world_rules.denied").withStyle(ChatFormatting.RED));
        SENT.remove(player.getUUID());
        sync(server);
        return true;
    }

    public static boolean edit(WorldRuleEditC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        if (server == null) {
            return false;
        }
        if (!mayEdit(player)) {
            if (reportDenial(player, server)) {
                Constants.LOG.warn("{} wollte Welt-Regeln ohne Berechtigung ändern", player.getGameProfile().name());
            }
            return false;
        }
        List<Component> changes = new ArrayList<>();
        for (WorldRulesS2C.Entry entry : payload.entries()) {
            GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(entry.id());
            if (rule == null || !isPropwash(rule)) {
                Constants.LOG.warn("{} wollte die unbekannte oder fremde Regel {} ändern", player.getGameProfile().name(), entry.id());
                continue;
            }
            if (setSerialized(server, rule, entry.value())) {
                Constants.LOG.info("{} setzt die Welt-Regel {} auf {}", player.getGameProfile().name(), entry.id(), entry.value());
                changes.add(Component.translatable("propwash.server.world_rules.changed", player.getDisplayName(),
                        WorldRuleText.name(entry.id()), WorldRuleText.value(entry.id(), entry.value())).withStyle(ChatFormatting.GRAY));
            } else if (rule.deserialize(entry.value()).result().isEmpty()) {
                Constants.LOG.warn("{} schickte einen ungültigen Wert für {}: {}", player.getGameProfile().name(), entry.id(), entry.value());
            }
        }
        for (Component change : changes) {
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                other.sendSystemMessage(change);
            }
        }
        sync(server);
        return true;
    }
}
