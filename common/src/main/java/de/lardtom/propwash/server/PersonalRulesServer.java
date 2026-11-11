package de.lardtom.propwash.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.ImportRateLimit;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.rules.PersonalRuleEdit;
import de.lardtom.propwash.server.rules.PersonalRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleBase;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.RuleTiers;
import de.lardtom.propwash.zone.ZoneData;
import de.lardtom.propwash.zone.ZoneRelease;
import de.lardtom.propwash.zone.ZoneServer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class PersonalRulesServer extends SavedData {
    public static final String KEY = "propwash.rules.";
    public static final int MAX_PLAYERS = 4096;
    private static final int DENIALS_PER_WINDOW = 2;
    private static final long DENIAL_WINDOW_TICKS = 100L;
    private static final Map<UUID, PersonalRulesS2C> SENT = new HashMap<>();
    private static ImportRateLimit denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
    private static boolean initialized;

    public static final Codec<PersonalRulesServer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("player_rules", List.of()).forGetter(data -> List.copyOf(data.playerRules)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PersonalRules.CODEC).optionalFieldOf("players", Map.of()).forGetter(data -> data.players)
    ).apply(i, PersonalRulesServer::new));
    public static final SavedDataType<PersonalRulesServer> TYPE = new SavedDataType<>(Constants.id("personal_rules"), PersonalRulesServer::new, CODEC,
            null);

    private final Set<String> playerRules = new TreeSet<>();
    private final Map<UUID, PersonalRules> players = new LinkedHashMap<>();

    public PersonalRulesServer() {
        this(List.of(), Map.of());
    }

    private PersonalRulesServer(List<String> playerRules, Map<UUID, PersonalRules> players) {
        for (String name : playerRules) {
            PersonalRuleEdit.tierRule(name).ifPresent(spec -> this.playerRules.add(spec.name()));
        }
        for (Map.Entry<UUID, PersonalRules> entry : players.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty() && this.players.size() < MAX_PLAYERS) {
                this.players.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public RuleTiers tiers(ZoneRelease release) {
        return RuleTiers.of(release::released, this.playerRules);
    }

    public PersonalRules own(@Nullable UUID player) {
        PersonalRules rules = player == null ? null : this.players.get(player);
        return rules == null ? PersonalRules.NONE : rules;
    }

    public Map<UUID, PersonalRules> players() {
        return Map.copyOf(this.players);
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashNet.registerS2C(PersonalRulesS2C.TYPE, PersonalRulesS2C.CODEC);
        PropwashNet.registerC2S(RuleTierC2S.TYPE, RuleTierC2S.CODEC, PersonalRulesServer::onTier);
        PropwashNet.registerC2S(PersonalRulesC2S.TYPE, PersonalRulesC2S.CODEC, PersonalRulesServer::onEdit);
        ServerBus.STARTED.register(server -> {
            SENT.clear();
            denials = new ImportRateLimit(DENIALS_PER_WINDOW, DENIAL_WINDOW_TICKS);
        });
        ServerBus.STOPPED.register(server -> SENT.clear());
        ServerBus.PLAYER_JOIN.register(PersonalRulesServer::send);
        ServerBus.PLAYER_LEAVE.register(player -> {
            SENT.remove(player.getUUID());
            denials.forget(player.getUUID());
        });
        ServerBus.TICK_END.register(PersonalRulesServer::resync);
        ServerBus.REGISTER_COMMANDS.register(PersonalRulesServer::register);
    }

    public static PersonalRulesServer get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static RuleTiers tiers(@Nullable MinecraftServer server) {
        return server == null ? RuleTiers.DEFAULTS : get(server).tiers(ZoneData.get(server).release());
    }

    public static PersonalRules own(@Nullable MinecraftServer server, @Nullable UUID player) {
        return server == null ? PersonalRules.NONE : get(server).own(player);
    }

    public static RuleBase base(@Nullable MinecraftServer server, @Nullable UUID player) {
        PhysicsRules world = WorldRules.physics(server);
        if (server == null) {
            return RuleBase.of(world);
        }
        PersonalRulesServer data = get(server);
        return RuleBase.of(world, tiers(server), data.own(player));
    }

    public static RuleBase base(Level level, @Nullable UUID player) {
        return base(level.getServer(), player);
    }

    public static boolean setTier(MinecraftServer server, RuleSpec spec, RuleTier tier, @Nullable Component by, String byName) {
        if (!RuleTiers.tierable(spec)) {
            return false;
        }
        PersonalRulesServer data = get(server);
        if (tiers(server).tier(spec) == tier) {
            return false;
        }
        boolean players = tier == RuleTier.PLAYERS;
        if (players ? data.playerRules.add(spec.name()) : data.playerRules.remove(spec.name())) {
            data.setDirty();
        }
        ZoneServer.release(server, Map.of(spec.id(), tier.zones()), by == null ? Component.literal(byName) : by, byName);
        Constants.LOG.info("{} setzt die Freigabe der Regel {} auf {}", byName, spec.id(), tier.key());
        if (by != null) {
            Component message = Component.translatable(KEY + "tier.changed", by, Component.translatable(spec.translationKey()),
                    Component.translatable(tier.translationKey())).withStyle(ChatFormatting.GRAY);
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                other.sendSystemMessage(message);
            }
        }
        sync(server);
        return true;
    }

    public static PersonalRuleEdit.Result edit(MinecraftServer server, UUID player, PersonalRuleEdit.Op op, Map<String, Integer> values) {
        PersonalRulesServer data = get(server);
        PersonalRules before = data.own(player);
        PersonalRuleEdit.Result result = PersonalRuleEdit.apply(before, tiers(server), WorldRules.physics(server), op, values);
        if (result.changed(before)) {
            if (result.rules().isEmpty()) {
                data.players.remove(player);
            } else if (data.players.containsKey(player) || data.players.size() < MAX_PLAYERS) {
                data.players.put(player, result.rules());
            } else {
                return new PersonalRuleEdit.Result(before, List.copyOf(values.keySet()));
            }
            data.setDirty();
        }
        return result;
    }

    public static void reset(MinecraftServer server) {
        PersonalRulesServer data = get(server);
        if (!data.playerRules.isEmpty() || !data.players.isEmpty()) {
            data.playerRules.clear();
            data.players.clear();
            data.setDirty();
        }
        sync(server);
    }

    private static void onTier(RuleTierC2S payload, ServerPayloadContext context) {
        tier(payload, context.player());
    }

    public static boolean tier(RuleTierC2S payload, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return false;
        }
        Optional<RuleSpec> spec = PersonalRuleEdit.tierRule(payload.rule());
        if (!WorldRules.mayEdit(player) || spec.isEmpty()) {
            if (denials.tryAcquire(player.getUUID(), server.getTickCount())) {
                player.sendSystemMessage(Component.translatable(KEY + "tier.denied").withStyle(ChatFormatting.RED));
                Constants.LOG.warn("{} wollte die Freigabe der Regel {} ohne Berechtigung ändern", player.getGameProfile().name(), payload.rule());
            }
            SENT.remove(player.getUUID());
            send(player);
            return false;
        }
        return setTier(server, spec.get(), payload.tier(), player.getDisplayName(), player.getGameProfile().name());
    }

    private static void onEdit(PersonalRulesC2S payload, ServerPayloadContext context) {
        personal(payload, context.player());
    }

    public static PersonalRuleEdit.Result personal(PersonalRulesC2S payload, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return new PersonalRuleEdit.Result(PersonalRules.NONE, List.copyOf(payload.values().keySet()));
        }
        PersonalRuleEdit.Result result = edit(server, player.getUUID(), payload.op(), payload.values());
        if (!result.refused().isEmpty()) {
            if (denials.tryAcquire(player.getUUID(), server.getTickCount())) {
                player.sendSystemMessage(Component.translatable(KEY + "personal.refused", String.join(", ", result.refused()))
                        .withStyle(ChatFormatting.RED));
            }
            Constants.LOG.warn("{} schickte persönliche Regeln, die nicht freigegeben oder ungültig sind: {}", player.getGameProfile().name(),
                    result.refused());
        }
        SENT.remove(player.getUUID());
        send(player);
        return result;
    }

    public static PersonalRulesS2C snapshotFor(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        PersonalRules own = server == null ? PersonalRules.NONE : get(server).own(player.getUUID());
        return new PersonalRulesS2C(WorldRules.mayEdit(player), tiers(server), own);
    }

    public static void send(ServerPlayer player) {
        PersonalRulesS2C payload = snapshotFor(player);
        if (!payload.equals(SENT.get(player.getUUID())) && PropwashNet.sendToPlayer(player, payload)) {
            SENT.put(player.getUUID(), payload);
        }
    }

    public static void sync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    private static void resync(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0 && !server.getPlayerList().getPlayers().isEmpty()) {
            sync(server);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("propwash").then(Commands.literal("rules").then(Commands.literal("tier")
                .executes(c -> show(c.getSource()))
                .then(Commands.argument("rule", StringArgumentType.word())
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(PhysicsRules.SPECS.stream().map(RuleSpec::name), builder))
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((c, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(RuleTier.values()).map(RuleTier::key), builder))
                                .executes(c -> command(c.getSource(), StringArgumentType.getString(c, "rule"),
                                        StringArgumentType.getString(c, "tier"))))))));
    }

    private static int show(CommandSourceStack source) {
        RuleTiers tiers = tiers(source.getServer());
        source.sendSuccess(() -> Component.translatable(KEY + "tier.header").withStyle(ChatFormatting.GOLD), false);
        for (RuleSpec spec : PhysicsRules.SPECS) {
            RuleTier tier = tiers.tier(spec);
            source.sendSuccess(() -> Component.translatable(KEY + "tier.line", Component.translatable(spec.translationKey()), spec.name(),
                    Component.translatable(tier.translationKey())), false);
        }
        return PhysicsRules.SPECS.size();
    }

    private static int command(CommandSourceStack source, String rule, String tier) {
        Optional<RuleSpec> spec = PersonalRuleEdit.tierRule(rule);
        if (spec.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "tier.unknown", rule));
            return 0;
        }
        Optional<RuleTier> wanted = RuleTier.byKey(tier);
        if (wanted.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "tier.invalid", tier));
            return 0;
        }
        if (!setTier(source.getServer(), spec.get(), wanted.get(), source.getDisplayName(), source.getTextName())) {
            source.sendSuccess(() -> Component.translatable(KEY + "tier.unchanged"), false);
            return 0;
        }
        return 1;
    }
}
