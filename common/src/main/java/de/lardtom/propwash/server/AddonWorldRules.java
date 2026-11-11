package de.lardtom.propwash.server;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.core.event.ServerBus;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AddonWorldRules {
    private static final Object LOCK = new Object();
    private static final Map<Identifier, WorldRule<?>> QUEUED = new LinkedHashMap<>();
    private static final Map<Identifier, GameRule<?>> GAME_RULES = new ConcurrentHashMap<>();
    private static @Nullable BiConsumer<ResourceKey<GameRule<?>>, GameRule<?>> directSink;
    private static boolean closed;
    private static boolean initialized;
    private static volatile @Nullable MinecraftServer running;
    private static volatile @Nullable Supplier<Map<Identifier, String>> clientValues;

    private AddonWorldRules() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerBus.STARTED.register(server -> running = server);
        ServerBus.STOPPED.register(server -> {
            if (running == server) {
                running = null;
            }
        });
    }

    public static void register(WorldRule<?> rule) {
        Objects.requireNonNull(rule, "rule");
        synchronized (LOCK) {
            if (closed) {
                throw new IllegalStateException("Welt-Regel " + rule.id() + " kommt zu spät: Welt-Regeln nur während der Mod-Initialisierung"
                        + " anmelden (Fabric onInitialize, NeoForge Mod-Konstruktor)");
            }
            if (QUEUED.containsKey(rule.id()) || GAME_RULES.containsKey(rule.id())) {
                throw new IllegalArgumentException("Welt-Regel doppelt angemeldet: " + rule.id());
            }
            BiConsumer<ResourceKey<GameRule<?>>, GameRule<?>> sink = directSink;
            if (sink != null) {
                create(rule, sink);
            } else {
                QUEUED.put(rule.id(), rule);
            }
        }
    }

    public static void flush(BiConsumer<ResourceKey<GameRule<?>>, GameRule<?>> sink, boolean stayOpen) {
        synchronized (LOCK) {
            for (WorldRule<?> rule : List.copyOf(QUEUED.values())) {
                create(rule, sink);
            }
            QUEUED.clear();
            if (stayOpen) {
                directSink = sink;
            } else {
                directSink = null;
                closed = true;
            }
        }
    }

    public static List<Identifier> pending() {
        synchronized (LOCK) {
            return List.copyOf(QUEUED.keySet());
        }
    }

    private static void create(WorldRule<?> rule, BiConsumer<ResourceKey<GameRule<?>>, GameRule<?>> sink) {
        GameRule<?> game = gameRule(rule);
        sink.accept(ResourceKey.create(Registries.GAME_RULE, rule.id()), game);
        GAME_RULES.put(rule.id(), game);
        Constants.LOG.debug("Welt-Regel {} aus einem Add-on angemeldet", rule.id());
    }

    static GameRule<?> gameRule(WorldRule<?> rule) {
        if (rule.type() == WorldRule.Type.BOOLEAN) {
            return new GameRule<>(WorldRules.CATEGORY, GameRuleType.BOOL, BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean, Codec.BOOL,
                    b -> b ? 1 : 0, (Boolean) rule.defaultValue(), FeatureFlagSet.of());
        }
        return new GameRule<>(WorldRules.CATEGORY, GameRuleType.INT, IntegerArgumentType.integer(rule.min(), rule.max()),
                GameRuleTypeVisitor::visitInteger, Codec.intRange(rule.min(), rule.max()), i -> i, (Integer) rule.defaultValue(),
                FeatureFlagSet.of());
    }

    public static Optional<GameRule<?>> gameRule(Identifier id) {
        return Optional.ofNullable(GAME_RULES.get(id));
    }

    public static boolean isAddonRule(Identifier id) {
        return Api17Registries.WORLD_RULES.find(id).isPresent();
    }

    public static List<WorldRule<?>> registered() {
        return Api17Registries.WORLD_RULES.registered();
    }

    public static void clientValues(@Nullable Supplier<Map<Identifier, String>> source) {
        clientValues = source;
    }

    public static <T> T value(WorldRule<T> rule, @Nullable Level level) {
        if (level == null) {
            return rule.defaultValue();
        }
        if (level.isClientSide()) {
            Supplier<Map<Identifier, String>> client = clientValues;
            return parse(rule, client == null ? null : client.get().get(rule.id()));
        }
        return value(rule, level.getServer());
    }

    public static <T> T value(WorldRule<T> rule, @Nullable MinecraftServer server) {
        GameRule<?> game = GAME_RULES.get(rule.id());
        if (server == null || game == null) {
            return rule.defaultValue();
        }
        return parse(rule, serialized(server.getGameRules(), game));
    }

    public static Map<Identifier, String> values(@Nullable MinecraftServer server) {
        Map<Identifier, String> out = new LinkedHashMap<>();
        if (server == null) {
            return out;
        }
        GameRules rules = server.getGameRules();
        for (WorldRule<?> rule : registered()) {
            GameRule<?> game = GAME_RULES.get(rule.id());
            String value = game == null ? null : serialized(rules, game);
            if (value != null) {
                out.put(rule.id(), value);
            }
        }
        return out;
    }

    private static <T> @Nullable String serialized(GameRules rules, GameRule<T> rule) {
        try {
            return rule.serialize(rules.get(rule));
        } catch (IllegalArgumentException e) {
            Constants.LOG.debug("Welt-Regel {} nicht in dieser Welt", rule, e);
            return null;
        }
    }

    public static String serialize(WorldRule<?> rule, Object value) {
        if (rule.type() == WorldRule.Type.BOOLEAN) {
            return Boolean.toString(Boolean.TRUE.equals(value));
        }
        return Integer.toString(value instanceof Integer number ? number : (Integer) rule.defaultValue());
    }

    @SuppressWarnings("unchecked")
    public static <T> T parse(WorldRule<T> rule, @Nullable String raw) {
        if (raw == null) {
            return rule.defaultValue();
        }
        String text = raw.trim();
        if (rule.type() == WorldRule.Type.BOOLEAN) {
            if ("true".equals(text)) {
                return (T) Boolean.TRUE;
            }
            return "false".equals(text) ? (T) Boolean.FALSE : rule.defaultValue();
        }
        try {
            int value = Integer.parseInt(text);
            return value >= rule.min() && value <= rule.max() ? (T) Integer.valueOf(value) : rule.defaultValue();
        } catch (NumberFormatException e) {
            return rule.defaultValue();
        }
    }

    public static Map<Identifier, String> presetChanges(String presetId, Collection<WorldRule<?>> rules,
                                                        Function<Identifier, Map<String, Object>> presets, Map<Identifier, String> current) {
        Map<Identifier, String> out = new LinkedHashMap<>();
        for (WorldRule<?> rule : rules) {
            Object wanted = presets.apply(rule.id()).get(presetId);
            if (wanted != null && !wanted.equals(parse(rule, current.get(rule.id())))) {
                out.put(rule.id(), serialize(rule, wanted));
            }
        }
        return out;
    }

    public static boolean presetMatches(String presetId, Collection<WorldRule<?>> rules, Function<Identifier, Map<String, Object>> presets,
                                        Map<Identifier, String> current) {
        for (WorldRule<?> rule : rules) {
            Object wanted = presets.apply(rule.id()).get(presetId);
            String raw = current.get(rule.id());
            if (wanted != null && raw != null && !wanted.equals(parse(rule, raw))) {
                return false;
            }
        }
        return true;
    }

    public static int applyPreset(MinecraftServer server, String presetId) {
        Map<Identifier, String> changes = presetChanges(presetId, registered(), Api17Registries.WORLD_RULES::presetValues, values(server));
        int changed = 0;
        for (Map.Entry<Identifier, String> change : changes.entrySet()) {
            GameRule<?> game = GAME_RULES.get(change.getKey());
            if (game != null && WorldRules.setSerialized(server, game, change.getValue())) {
                changed++;
            }
        }
        return changed;
    }

    public static boolean presetActive(String presetId) {
        List<WorldRule<?>> rules = registered();
        if (rules.isEmpty()) {
            return true;
        }
        Map<Identifier, String> current = current();
        return current == null || presetMatches(presetId, rules, Api17Registries.WORLD_RULES::presetValues, current);
    }

    private static @Nullable Map<Identifier, String> current() {
        MinecraftServer server = running;
        Supplier<Map<Identifier, String>> client = clientValues;
        if (server != null && (client == null || server.isSameThread())) {
            return values(server);
        }
        return client == null ? null : client.get();
    }
}
