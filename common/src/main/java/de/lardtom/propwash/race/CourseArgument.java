package de.lardtom.propwash.race;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CourseArgument {
    public static final String NAME = "course";
    public static final String KEY = "propwash.race.course_arg.";

    public record Match(List<CourseRegistry.Course> courses, boolean number) {
        public CourseRegistry.@Nullable Course single() {
            return this.courses.size() == 1 ? this.courses.getFirst() : null;
        }
    }

    public record Option(String text, CourseRegistry.Course course) {
    }

    @FunctionalInterface
    public interface Action {
        int run(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course) throws CommandSyntaxException;
    }

    private CourseArgument() {
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> last() {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument(NAME, StringArgumentType.greedyString())
                .suggests((c, builder) -> suggest(c.getSource(), builder, false));
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> word() {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument(NAME, StringArgumentType.string())
                .suggests((c, builder) -> suggest(c.getSource(), builder, true));
    }

    private static CompletableFuture<Suggestions> suggest(CommandSourceStack source, SuggestionsBuilder builder, boolean quoted) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(options(registry.courses(), CourseRegistry.dimensionOf(source.getLevel()), quoted), builder,
                Option::text, option -> tooltip(option.course()));
    }

    public static List<Option> options(Collection<CourseRegistry.Course> courses, String dimension, boolean quoted) {
        List<CourseRegistry.Course> sorted = new ArrayList<>(courses);
        sorted.sort(Comparator.comparing((CourseRegistry.Course course) -> !course.dimension.equals(dimension)).thenComparingInt(course -> course.id));
        List<Option> out = new ArrayList<>();
        for (CourseRegistry.Course course : sorted) {
            out.add(new Option(CourseId.text(course.key()), course));
        }
        Map<String, List<CourseRegistry.Course>> groups = new HashMap<>();
        for (CourseRegistry.Course course : sorted) {
            if (CourseNames.named(course.name)) {
                groups.computeIfAbsent(key(course.name), k -> new ArrayList<>()).add(course);
            }
        }
        for (CourseRegistry.Course course : sorted) {
            if (CourseNames.named(course.name) && readsAsName(course.name)) {
                List<CourseRegistry.Course> hits = resolve(groups.get(key(course.name)), dimension);
                if (hits.size() == 1 && hits.getFirst() == course) {
                    out.add(new Option(quoted ? StringArgumentType.escapeIfRequired(course.name) : course.name, course));
                }
            }
        }
        return out;
    }

    private static String key(String name) {
        return CourseNames.clean(name).toLowerCase(Locale.ROOT);
    }

    private static List<CourseRegistry.Course> resolve(List<CourseRegistry.Course> named, String dimension) {
        List<CourseRegistry.Course> here = named.stream().filter(course -> course.dimension.equals(dimension)).toList();
        return here.isEmpty() ? named : here;
    }

    public static Component tooltip(CourseRegistry.Course course) {
        return CourseNames.named(course.name) ? Component.translatable(KEY + "suggestion", course.name, course.id)
                : Component.translatable("propwash.race.course.number", course.id);
    }

    public static String unquote(String input) {
        return CourseNames.unquote(input);
    }

    private static @Nullable Integer number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean readsAsName(String name) {
        return CourseNames.clean(name).equals(name) && number(name) == null && CourseId.parse(name) == null;
    }

    public static String reference(int number, @Nullable UUID key) {
        return CourseId.usable(key) ? CourseId.text(key) : Integer.toString(number);
    }

    public static Match find(CourseRegistry registry, String input, String dimension) {
        String text = unquote(input);
        if (text.isEmpty()) {
            return new Match(List.of(), false);
        }
        Integer number = number(text);
        if (number != null) {
            CourseRegistry.Course course = registry.course(number);
            return new Match(course == null ? List.of() : List.of(course), true);
        }
        UUID key = CourseId.parse(text);
        if (key != null) {
            CourseRegistry.Course course = registry.course(key);
            return new Match(course == null ? List.of() : List.of(course), false);
        }
        String wanted = key(text);
        if (wanted.isEmpty()) {
            return new Match(List.of(), false);
        }
        List<CourseRegistry.Course> named = new ArrayList<>();
        for (CourseRegistry.Course course : registry.courses()) {
            if (CourseNames.named(course.name) && key(course.name).equals(wanted)) {
                named.add(course);
            }
        }
        return new Match(resolve(named, dimension), false);
    }

    public static CourseRegistry.@Nullable Course peek(CommandContext<CommandSourceStack> context) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return null;
        }
        return find(registry, StringArgumentType.getString(context, NAME), CourseRegistry.dimensionOf(context.getSource().getLevel())).single();
    }

    public static int run(CommandContext<CommandSourceStack> context, Action action) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            source.sendFailure(Component.translatable("propwash.race.command.unavailable"));
            return 0;
        }
        String input = StringArgumentType.getString(context, NAME);
        Match match = find(registry, input, CourseRegistry.dimensionOf(source.getLevel()));
        if (match.courses().isEmpty()) {
            source.sendFailure(Component.translatable(CourseIdCommands.KEY + "not_found", unquote(input)));
            return 0;
        }
        if (match.courses().size() > 1) {
            source.sendFailure(Component.translatable(KEY + "several", match.courses().size(), match.courses().getFirst().name));
            for (CourseRegistry.Course course : match.courses()) {
                Component line = CourseIdCommands.line(course);
                source.sendSuccess(() -> line, false);
            }
            return 0;
        }
        CourseRegistry.Course course = match.courses().getFirst();
        int result = action.run(source, registry, course);
        if (match.number()) {
            Component hint = hint(command(context.getInput(), range(context), CourseId.text(course.key())), course);
            source.sendSuccess(() -> hint, false);
        }
        return result;
    }

    public static @Nullable StringRange range(CommandContext<?> context) {
        for (ParsedCommandNode<?> node : context.getNodes()) {
            if (node.getNode() instanceof ArgumentCommandNode<?, ?> && NAME.equals(node.getNode().getName())) {
                return node.getRange();
            }
        }
        return null;
    }

    public static String command(String input, @Nullable StringRange range, String replacement) {
        String text = range == null ? input : input.substring(0, range.getStart()) + replacement + input.substring(range.getEnd());
        return text.startsWith("/") ? text : "/" + text;
    }

    public static Component hint(String command, CourseRegistry.Course course) {
        Component link = Component.literal(command).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(KEY + "copy"))));
        return Component.translatable(KEY + "number_hint", course.id, link).withStyle(ChatFormatting.GRAY);
    }
}
