package de.lardtom.propwash.zone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

public final class ZoneCommands {
    private static final String KEY = "propwash.zone.command.";
    private static final String NAME = "name";
    private static final String RULE = "rule";
    private static final String VALUE = "value";

    private ZoneCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> zone = Commands.literal("zone").executes(ctx -> list(ctx.getSource()));
        zone.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));
        zone.then(Commands.literal("info").then(name().executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, NAME)))));
        zone.then(create());
        zone.then(editing("set").then(name().then(Commands.argument(RULE, StringArgumentType.word()).suggests(rules())
                .then(Commands.argument(VALUE, StringArgumentType.word()).suggests(values())
                        .executes(ctx -> set(ctx, StringArgumentType.getString(ctx, RULE), StringArgumentType.getString(ctx, VALUE)))))));
        zone.then(editing("reset").then(name().then(Commands.argument(RULE, StringArgumentType.word()).suggests(rules())
                .executes(ctx -> reset(ctx, StringArgumentType.getString(ctx, RULE))))));
        zone.then(editing("priority").then(name().then(Commands.argument(VALUE, IntegerArgumentType.integer(ZoneLimits.MIN_PRIORITY,
                ZoneLimits.MAX_PRIORITY)).executes(ctx -> change(ctx, z -> z.withPriority(IntegerArgumentType.getInteger(ctx, VALUE)))))));
        zone.then(editing("racelegal").then(name().then(Commands.argument(VALUE, BoolArgumentType.bool())
                .executes(ctx -> change(ctx, z -> z.withRaceLegal(BoolArgumentType.getBool(ctx, VALUE)))))));
        zone.then(editing("transition").then(name().then(Commands.argument(VALUE, DoubleArgumentType.doubleArg(0.0,
                ZoneLimits.MAX_TRANSITION_TENTHS / 10.0)).executes(ctx -> change(ctx, z -> z.withTransition(
                (int) Math.round(DoubleArgumentType.getDouble(ctx, VALUE) * 10.0)))))));
        zone.then(editing("rename").then(name().then(Commands.argument("new_name", StringArgumentType.string())
                .executes(ctx -> change(ctx, z -> z.withName(StringArgumentType.getString(ctx, "new_name")))))));
        zone.then(editing("move").then(name().then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to",
                BlockPosArgument.blockPos()).executes(ctx -> change(ctx, z -> z.withBox(box(ctx))))))));
        zone.then(editing("delete").then(name().executes(ZoneCommands::delete)));
        dispatcher.register(Commands.literal("propwash").then(zone));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> create() {
        RequiredArgumentBuilder<CommandSourceStack, String> named = Commands.argument(NAME, StringArgumentType.string());
        named.then(Commands.literal("box").then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to",
                BlockPosArgument.blockPos()).executes(ZoneCommands::createBox))));
        named.then(Commands.literal("cylinder").then(Commands.argument("center", BlockPosArgument.blockPos()).then(Commands.argument("radius",
                IntegerArgumentType.integer(1, ZoneLimits.MAX_SIZE / 2)).then(Commands.argument("height",
                IntegerArgumentType.integer(ZoneLimits.MIN_SIZE, ZoneLimits.MAX_SIZE)).executes(ZoneCommands::createCylinder)))));
        return editing("create").then(named);
    }

    public static boolean mayEdit(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? ZoneServer.mayEdit(player) : Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> editing(String literal) {
        return Commands.literal(literal).requires(ZoneCommands::mayEdit);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument(NAME, StringArgumentType.string()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                ZoneServer.zones(ctx.getSource().getLevel()).stream().map(zone -> quoted(zone.name())), builder));
    }

    static String quoted(String name) {
        for (int i = 0; i < name.length(); i++) {
            if (!StringReader.isAllowedInUnquotedString(name.charAt(i))) {
                return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return name;
    }

    private static SuggestionProvider<CommandSourceStack> rules() {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(PhysicsRules.SPECS.stream().map(RuleSpec::name), builder);
    }

    private static SuggestionProvider<CommandSourceStack> values() {
        return (ctx, builder) -> {
            Optional<RuleSpec> spec = spec(StringArgumentType.getString(ctx, RULE));
            if (spec.isEmpty()) {
                return builder.buildFuture();
            }
            RuleSpec rule = spec.get();
            if (rule.kind() == RuleSpec.Kind.BOOL) {
                return SharedSuggestionProvider.suggest(List.of("true", "false"), builder);
            }
            return SharedSuggestionProvider.suggest(List.of(Integer.toString(rule.min()), Integer.toString(rule.defaultValue()),
                    Integer.toString(rule.max())), builder);
        };
    }

    private static Optional<RuleSpec> spec(String name) {
        return WorldRuleSpecs.byName(name).filter(Zone::overridable);
    }

    private static int list(CommandSourceStack source) {
        List<Zone> zones = ZoneServer.zones(source.getLevel());
        if (zones.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY + "list.header", zones.size()), false);
        for (Zone zone : zones) {
            source.sendSuccess(() -> ZoneText.summary(zone), false);
        }
        return zones.size();
    }

    private static int info(CommandSourceStack source, String name) {
        Optional<Zone> zone = find(source, name);
        if (zone.isEmpty()) {
            return 0;
        }
        Zone found = zone.get();
        source.sendSuccess(() -> ZoneText.summary(found), false);
        source.sendSuccess(() -> Component.translatable(ZoneText.KEY + "transition.label", ZoneText.transition(found.transitionTenths())), false);
        return 1;
    }

    private static Optional<Zone> find(CommandSourceStack source, String name) {
        Optional<Zone> zone = ZoneData.get(source.getServer()).byName(ZoneServer.dimension(source.getLevel()), name);
        if (zone.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown", name));
        }
        return zone;
    }

    private static ZoneBox box(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos from = BlockPosArgument.getBlockPos(ctx, "from");
        BlockPos to = BlockPosArgument.getBlockPos(ctx, "to");
        return ZoneBox.blocks(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    private static int createBox(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return create(ctx, ZoneShape.BOX, box(ctx));
    }

    private static int createCylinder(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos center = BlockPosArgument.getBlockPos(ctx, "center");
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        int height = IntegerArgumentType.getInteger(ctx, "height");
        return create(ctx, ZoneShape.CYLINDER, ZoneBox.cylinder(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, radius, height));
    }

    private static int create(CommandContext<CommandSourceStack> ctx, ZoneShape shape, ZoneBox box) {
        CommandSourceStack source = ctx.getSource();
        Zone zone = Zone.create(ZoneServer.dimension(source.getLevel()), StringArgumentType.getString(ctx, NAME), shape, box);
        return report(source, ZoneServer.save(source.getServer(), zone, source.getDisplayName(), source.getTextName()));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, String rule, String raw) throws CommandSyntaxException {
        Optional<RuleSpec> spec = spec(rule);
        if (spec.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "unknown_rule", rule));
            return 0;
        }
        int value;
        try {
            value = spec.get().parse(raw);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "bad_value", raw, Component.translatable(spec.get().translationKey())));
            return 0;
        }
        return change(ctx, zone -> zone.withOverride(spec.get(), value));
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, String rule) throws CommandSyntaxException {
        Optional<RuleSpec> spec = spec(rule);
        if (spec.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "unknown_rule", rule));
            return 0;
        }
        return change(ctx, zone -> zone.without(spec.get()));
    }

    private interface Change {
        Zone apply(Zone zone) throws CommandSyntaxException;
    }

    private static int change(CommandContext<CommandSourceStack> ctx, Change change) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Optional<Zone> zone = find(source, StringArgumentType.getString(ctx, NAME));
        if (zone.isEmpty()) {
            return 0;
        }
        return report(source, ZoneServer.save(source.getServer(), change.apply(zone.get()), source.getDisplayName(), source.getTextName()));
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Optional<Zone> zone = find(source, StringArgumentType.getString(ctx, NAME));
        if (zone.isEmpty()) {
            return 0;
        }
        return report(source, ZoneServer.delete(source.getServer(), zone.get().id(), source.getDisplayName(), source.getTextName()));
    }

    private static int report(CommandSourceStack source, ZoneServer.Outcome outcome) {
        if (!outcome.ok()) {
            source.sendFailure(Component.translatable(ZoneServer.PROBLEM + outcome.problem()).withStyle(ChatFormatting.RED));
            return 0;
        }
        Zone zone = outcome.zone();
        if (zone != null) {
            source.sendSuccess(() -> ZoneText.summary(zone), false);
        }
        return 1;
    }
}
