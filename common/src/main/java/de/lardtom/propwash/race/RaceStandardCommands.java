package de.lardtom.propwash.race;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

public final class RaceStandardCommands {
    private static final String KEY = RaceStandards.KEY + "command.";
    private static final SuggestionProvider<CommandSourceStack> RULES = (context, builder) -> SharedSuggestionProvider.suggest(
            RaceRules.relevant().stream().map(rule -> rule.addon() ? "\"" + rule.id() + "\"" : rule.subject()), builder);

    private RaceStandardCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("propwash").then(Commands.literal("race").then(Commands.literal("standard")
                .executes(c -> show(c.getSource()))
                .then(Commands.literal("adopt").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> apply(c.getSource(), RaceStandard.adopt(RaceRules.values(c.getSource().getServer())))))
                .then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> apply(c.getSource(), RaceStandard.DEFAULTS)))
                .then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("rule", StringArgumentType.string()).suggests(RULES)
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(c -> set(c.getSource(), StringArgumentType.getString(c, "rule"),
                                                StringArgumentType.getString(c, "value")))))))));
    }

    public static OptionalInt parse(RaceRules.Rule rule, String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (rule.bool()) {
            return switch (text) {
                case "true", "on", "1" -> OptionalInt.of(1);
                case "false", "off", "0" -> OptionalInt.of(0);
                default -> OptionalInt.empty();
            };
        }
        try {
            int value = Integer.parseInt(text);
            return rule.valid(value) ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static int show(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        RaceStandard standard = RaceStandards.of(server);
        List<RaceRules.Rule> differences = standard.differences();
        Component summary = differences.isEmpty() ? Component.translatable(KEY + "defaults") : Component.literal(Integer.toString(differences.size()));
        source.sendSuccess(() -> Component.translatable(KEY + "header", summary).withStyle(ChatFormatting.GOLD), false);
        for (RaceRules.Rule rule : differences) {
            source.sendSuccess(() -> Component.translatable(KEY + "rule", AssistWords.name(rule), AssistWords.value(rule, standard.value(rule)),
                    AssistWords.value(rule, rule.defaultValue())), false);
        }
        List<String> relevant = new ArrayList<>();
        List<String> neutral = new ArrayList<>();
        for (RaceRules.Rule rule : RaceRules.all()) {
            (rule.relevant() ? relevant : neutral).add(rule.subject());
        }
        source.sendSuccess(() -> Component.translatable(KEY + "relevant", String.join(", ", relevant)).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable(KEY + "neutral", String.join(", ", neutral)).withStyle(ChatFormatting.GRAY), false);
        return differences.size();
    }

    private static int apply(CommandSourceStack source, RaceStandard next) {
        if (!RaceStandards.set(source.getServer(), next, source.getDisplayName(), source.getTextName())) {
            source.sendSuccess(() -> Component.translatable(KEY + "unchanged"), false);
            return 0;
        }
        return Math.max(1, next.differences().size());
    }

    private static int set(CommandSourceStack source, String key, String raw) {
        Optional<RaceRules.Rule> rule = RaceRules.find(key).filter(RaceRules.Rule::relevant);
        if (rule.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown", key));
            return 0;
        }
        OptionalInt value = parse(rule.get(), raw);
        if (value.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "invalid", AssistWords.name(rule.get()), raw));
            return 0;
        }
        return apply(source, RaceStandards.of(source.getServer()).with(rule.get().id(), value.getAsInt()));
    }
}
