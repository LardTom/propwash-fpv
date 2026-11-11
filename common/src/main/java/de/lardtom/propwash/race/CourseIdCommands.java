package de.lardtom.propwash.race;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceDataMirror;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CourseIdCommands {
    public static final String KEY = "propwash.race.course_id.";
    private static final int SUCCESS = 1;
    private static final int FAILURE = 0;

    private CourseIdCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("propwash").then(Commands.literal("course").then(Commands.literal("id")
                .executes(c -> show(c.getSource(), null))
                .then(Commands.argument("course", StringArgumentType.greedyString())
                        .suggests((c, builder) -> suggest(c.getSource(), builder))
                        .executes(c -> show(c.getSource(), StringArgumentType.getString(c, "course")))))));
    }

    private static CompletableFuture<Suggestions> suggest(CommandSourceStack source, SuggestionsBuilder builder) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(options(registry.courses(), CourseRegistry.dimensionOf(source.getLevel())), builder);
    }

    public static List<String> options(Collection<CourseRegistry.Course> courses, String dimension) {
        List<String> options = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (CourseRegistry.Course course : courses) {
            if (course.dimension.equals(dimension)) {
                options.add(Integer.toString(course.id));
                if (CourseNames.named(course.name) && CourseArgument.readsAsName(course.name) && names.add(course.name.toLowerCase(Locale.ROOT))) {
                    options.add(course.name);
                }
            }
        }
        return options;
    }

    public static List<CourseRegistry.Course> find(CourseRegistry registry, String input, String dimension) {
        return CourseArgument.find(registry, input, dimension).courses();
    }

    private static int show(CommandSourceStack source, @Nullable String input) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            source.sendFailure(Component.translatable("propwash.race.command.unavailable"));
            return FAILURE;
        }
        List<CourseRegistry.Course> found;
        if (input == null) {
            ServerPlayer player = source.getPlayer();
            CourseRegistry.Course nearest = player == null ? null : registry.nearestCourse(player, RaceCommands.NEAREST_COURSE_RANGE);
            if (nearest == null) {
                source.sendFailure(Component.translatable(KEY + "no_nearby"));
                return FAILURE;
            }
            found = List.of(nearest);
        } else {
            found = find(registry, input, CourseRegistry.dimensionOf(source.getLevel()));
            if (found.isEmpty()) {
                source.sendFailure(Component.translatable(KEY + "not_found", CourseArgument.unquote(input)));
                return FAILURE;
            }
        }
        if (found.size() > 1) {
            int count = found.size();
            source.sendSuccess(() -> Component.translatable(KEY + "several", count, found.getFirst().name).withStyle(ChatFormatting.GOLD), false);
        }
        for (CourseRegistry.Course course : found) {
            Component line = line(course);
            source.sendSuccess(() -> line, false);
        }
        return found.size() == 1 ? SUCCESS : found.size();
    }

    public static Component line(CourseRegistry.Course course) {
        return Component.translatable(KEY + "line", CourseRegistry.label(course), course.id, course.layout, idText(course.key()));
    }

    public static MutableComponent idText(UUID key) {
        String id = CourseId.text(key);
        String file = RaceDataMirror.FOLDER + "/" + RaceDataJson.LEADERBOARDS + "/" + id + ".json";
        return Component.literal(id).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(id))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(KEY + "click_to_copy", file))));
    }
}
