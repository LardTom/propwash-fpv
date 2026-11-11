package de.lardtom.propwash.race;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.network.PropwashNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RaceCommands {
    public static final int LEADERBOARD_LINES = 10;
    public static final double NEAREST_COURSE_RANGE = 256.0;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private RaceCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("propwash").then(Commands.literal("race")
                .then(Commands.literal("courses").executes(c -> courses(c.getSource())))
                .then(Commands.literal("leaderboard")
                        .executes(c -> nearestLeaderboard(c.getSource(), 1))
                        .then(Commands.literal("page")
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(c -> nearestLeaderboard(c.getSource(), IntegerArgumentType.getInteger(c, "page")))
                                        .then(CourseArgument.last().executes(c -> CourseArgument.run(c, (source, registry, course) ->
                                                leaderboard(source, registry, course, IntegerArgumentType.getInteger(c, "page")))))))
                        .then(CourseArgument.last().executes(c -> CourseArgument.run(c, (source, registry, course) ->
                                leaderboard(source, registry, course, 1)))))
                .then(Commands.literal("archive")
                        .then(CourseArgument.last().executes(c -> CourseArgument.run(c, RaceCommands::archive))))
                .then(Commands.literal("laps")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, CourseLaps.MAX))
                                .executes(c -> laps(c.getSource(), IntegerArgumentType.getInteger(c, "count")))
                                .then(CourseArgument.last().executes(c -> CourseArgument.run(c, (source, registry, course) ->
                                        courseLaps(source, registry, course, IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("mode")
                        .then(CourseArgument.word()
                                .then(Commands.literal(CourseMode.LAPS.key())
                                        .executes(c -> CourseArgument.run(c, (source, registry, course) -> courseMode(source, registry, course, CourseMode.LAPS))))
                                .then(Commands.literal(CourseMode.POINT_TO_POINT.key())
                                        .executes(c -> CourseArgument.run(c, (source, registry, course) ->
                                                courseMode(source, registry, course, CourseMode.POINT_TO_POINT))))))
                .then(Commands.literal("name")
                        .then(CourseArgument.word()
                                .executes(c -> CourseArgument.run(c, (source, registry, course) -> courseName(source, registry, course, "")))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> CourseArgument.run(c, (source, registry, course) ->
                                                courseName(source, registry, course, StringArgumentType.getString(c, "name")))))))
                .then(Commands.literal("trim")
                        .then(CourseArgument.word()
                                .then(Commands.argument("element", IntegerArgumentType.integer(1, CourseData.MAX_GATES))
                                        .executes(c -> CourseArgument.run(c, (source, registry, course) ->
                                                trim(source, registry, course, IntegerArgumentType.getInteger(c, "element")))))))
                .then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(CourseArgument.word()
                                .executes(c -> CourseArgument.run(c, (source, registry, course) -> reset(source, registry, course, null)))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(c -> CourseArgument.run(c, (source, registry, course) ->
                                                reset(source, registry, course, StringArgumentType.getString(c, "player")))))))
                .then(Commands.literal("remove").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(CourseArgument.word()
                                .then(Commands.argument("pilot", StringArgumentType.word())
                                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(pilots(CourseArgument.peek(c)), builder))
                                        .executes(c -> CourseArgument.run(c, (source, registry, course) ->
                                                remove(source, registry, course, StringArgumentType.getString(c, "pilot"), null)))
                                        .then(Commands.argument("class", StringArgumentType.greedyString())
                                                .suggests((c, builder) -> SharedSuggestionProvider.suggest(classes(CourseArgument.peek(c)), builder))
                                                .executes(c -> CourseArgument.run(c, (source, registry, course) -> remove(source, registry, course,
                                                        StringArgumentType.getString(c, "pilot"), StringArgumentType.getString(c, "class"))))))))
                .then(Commands.literal("delete").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(CourseArgument.last().executes(c -> CourseArgument.run(c, RaceCommands::delete))))));
    }

    private static @Nullable CourseRegistry registry(CommandSourceStack source) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            source.sendFailure(Component.translatable("propwash.race.command.unavailable"));
        }
        return registry;
    }

    private static int courses(CommandSourceStack source) {
        CourseRegistry registry = registry(source);
        if (registry == null) {
            return 0;
        }
        String dimension = CourseRegistry.dimensionOf(source.getLevel());
        List<CourseRegistry.Course> list = registry.courses().stream().filter(c -> c.dimension.equals(dimension)).toList();
        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("propwash.race.command.no_courses"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("propwash.race.command.courses_header", list.size()).withStyle(ChatFormatting.GOLD), false);
        for (CourseRegistry.Course course : list) {
            Component record = recordLine(registry.board(course));
            CourseRegistry.GateRecord start = course.gates.isEmpty() ? null : course.gates.getFirst();
            String where = start == null ? "-" : Math.round(start.x) + " " + Math.round(start.y) + " " + Math.round(start.z);
            Component name = CourseNames.named(course.name) ? Component.translatable("propwash.race.command.course_label", course.name)
                    : Component.empty();
            source.sendSuccess(() -> Component.translatable("propwash.race.command.course_line", course.id, name, course.gates.size(),
                    CourseRegistry.formatLabel(course), where, record), false);
        }
        return list.size();
    }

    private static int nearestLeaderboard(CommandSourceStack source, int page) {
        CourseRegistry registry = registry(source);
        if (registry == null) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        CourseRegistry.Course course = player == null ? null : registry.nearestCourse(player, NEAREST_COURSE_RANGE);
        if (course == null) {
            source.sendFailure(Component.translatable("propwash.race.command.no_nearby_course"));
            return 0;
        }
        return leaderboard(source, registry, course, page);
    }

    public static int pages(int times) {
        return Math.max(1, (times + LEADERBOARD_LINES - 1) / LEADERBOARD_LINES);
    }

    public static <T> List<T> page(List<T> ranking, int page) {
        int from = (Math.clamp(page, 1, pages(ranking.size())) - 1) * LEADERBOARD_LINES;
        return ranking.subList(Math.min(from, ranking.size()), Math.min(ranking.size(), from + LEADERBOARD_LINES));
    }

    public static String pageCommand(CourseRegistry.Course course, int page) {
        return "/propwash race leaderboard page " + page + " " + CourseArgument.reference(course.id, course.key());
    }

    private static int leaderboard(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course, int page) {
        Leaderboard board = registry.board(course);
        List<Leaderboard.Entry> ranking = board.top(null, Integer.MAX_VALUE);
        CourseRegistry.Course shown = course;
        source.sendSuccess(() -> Component.translatable("propwash.race.command.leaderboard_header", shown.id, shown.gates.size())
                .withStyle(ChatFormatting.GOLD), false);
        if (ranking.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("propwash.race.command.no_record"), false);
            return 0;
        }
        int pages = pages(ranking.size());
        int current = Math.clamp(page, 1, pages);
        int first = (current - 1) * LEADERBOARD_LINES;
        List<Leaderboard.Entry> lines = page(ranking, current);
        long record = ranking.getFirst().nanos();
        for (int i = 0; i < lines.size(); i++) {
            Leaderboard.Entry entry = lines.get(i);
            int place = first + i + 1;
            String gap = place == 1 ? "" : " (" + RaceFormat.delta(entry.nanos() - record) + ")";
            ChatFormatting color = place == 1 ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
            String date = entry.dateMillis() > 0L ? DAY.format(Instant.ofEpochMilli(entry.dateMillis())) : "-";
            source.sendSuccess(() -> Component.translatable("propwash.race.command.leaderboard_line", place, entry.name(),
                    RaceFormat.time(entry.nanos()) + gap, RaceServer.className(entry.frame()), date).withStyle(color), false);
        }
        if (pages > 1) {
            Component footer = Component.translatable("propwash.race.command.leaderboard_page", current, pages, ranking.size())
                    .withStyle(ChatFormatting.GRAY);
            if (current < pages) {
                String next = pageCommand(course, current + 1);
                footer = Component.empty().append(footer).append(" ").append(Component.translatable("propwash.race.command.leaderboard_next")
                        .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(next))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal(next)))));
            }
            Component shownFooter = footer;
            source.sendSuccess(() -> shownFooter, false);
        }
        return lines.size();
    }

    private static int trim(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course, int number) {
        ServerPlayer player = source.getPlayer();
        int index = number - 1;
        if (player == null || index >= course.gates.size()) {
            source.sendFailure(Component.translatable("propwash.race.overlap.nothing", Component.literal("#"), number));
            return 0;
        }
        List<GateGeometry> gates = course.toData().geometry();
        Component name = GateMounting.elementName(gates.get(index).kind());
        PassZoneOverlap.Finding finding = PassZoneOverlap.shortest(PassZoneOverlap.ofElement(gates, index, course.pointToPoint()));
        if (finding == null) {
            source.sendSuccess(() -> Component.translatable("propwash.race.overlap.nothing", name, number), false);
            return 0;
        }
        if (!finding.shortenable() || !(player.level().getEntity(UUID.fromString(course.gates.get(index).uuid)) instanceof GateEntity gate)) {
            source.sendFailure(Component.translatable("propwash.race.overlap.fix.move"));
            return 0;
        }
        GateMounting.Result result = GateMounting.shortenReach(player, gate, finding.shortened());
        if (result != GateMounting.Result.EDITED) {
            Component message = GateMounting.message(result, gate);
            source.sendFailure(message == null ? Component.translatable("propwash.race.overlap.fix.move") : message);
            return 0;
        }
        String value = GateMounting.degrees((float) finding.shortened());
        source.sendSuccess(() -> Component.translatable("propwash.race.overlap.shortened", name, number, value).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int archive(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course) {
        int courseId = course.id;
        List<RaceRecords.Archived> archive = registry.records().archive(course.id);
        if (archive.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("propwash.race.command.archive_empty", courseId), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("propwash.race.command.archive_header", courseId, archive.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = archive.size() - 1; i >= 0; i--) {
            RaceRecords.Archived board = archive.get(i);
            Component record = recordLine(board.board());
            String date = DATE.format(Instant.ofEpochMilli(board.archivedAt()));
            source.sendSuccess(() -> Component.translatable("propwash.race.command.archive_line", board.layout(), board.gates(), date,
                    pilotCount(board.board()), record), false);
        }
        return archive.size();
    }

    private static int pilotCount(Leaderboard board) {
        return (int) board.top(null, Integer.MAX_VALUE).stream().map(Leaderboard.Entry::player).distinct().count();
    }

    private static Component recordLine(Leaderboard board) {
        return board.record(null)
                .<Component>map(entry -> Component.translatable("propwash.race.command.record", entry.name(), RaceFormat.time(entry.nanos())))
                .orElseGet(() -> Component.translatable("propwash.race.command.no_record"));
    }

    private static int laps(CommandSourceStack source, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!PropwashNet.sendToPlayer(player, new RacePayloads.RaceSettingsS2C(count))) {
            source.sendFailure(Component.translatable("propwash.race.command.client_missing"));
            return 0;
        }
        CourseEdits.setDefaultLaps(player.getUUID(), count);
        source.sendSuccess(() -> count == 0
                ? Component.translatable("propwash.race.command.laps_endless")
                : Component.translatable("propwash.race.command.laps", count), false);
        return 1;
    }

    private static boolean editable(CommandSourceStack source, CourseRegistry.Course course) {
        ServerPlayer player = source.getPlayer();
        CourseAccess access = player == null ? CourseAccess.ALLOWED : CourseEdits.access(player, course, CourseEdits.Action.LAPS);
        if (!access.allowed()) {
            source.sendFailure(CourseEdits.denied(access, CourseRegistry.label(course), CourseRegistry.ownerName(course)));
            return false;
        }
        return true;
    }

    private static int courseLaps(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course, int count) {
        if (!editable(source, course)) {
            return 0;
        }
        registry.setLaps(course, count);
        source.sendSuccess(() -> Component.translatable("propwash.race.command.course_laps", CourseRegistry.label(course),
                CourseRegistry.lapsLabel(course.laps)), true);
        return 1;
    }

    private static int courseMode(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course, CourseMode mode) {
        if (!editable(source, course)) {
            return 0;
        }
        registry.setMode(course, mode);
        source.sendSuccess(() -> Component.translatable("propwash.race.p2p.command.mode", CourseRegistry.label(course), CourseRegistry.formatLabel(course)),
                true);
        return 1;
    }

    private static int courseName(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course, String name) {
        if (!editable(source, course)) {
            return 0;
        }
        registry.rename(course, name);
        source.sendSuccess(() -> Component.translatable("propwash.race.command.course_name", course.id, CourseRegistry.label(course)), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course, @Nullable String playerName) {
        int courseId = course.id;
        int removed = registry.resetLeaderboard(course, playerName);
        registry.save();
        source.sendSuccess(() -> Component.translatable("propwash.race.command.reset", courseId, removed), true);
        return removed;
    }

    private static List<String> pilots(CourseRegistry.@Nullable Course course) {
        CourseRegistry registry = CourseRegistry.get();
        return course == null || registry == null ? List.of() : registry.board(course).pilotNames();
    }

    private static List<String> classes(CourseRegistry.@Nullable Course course) {
        CourseRegistry registry = CourseRegistry.get();
        if (course == null || registry == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Leaderboard.Entry entry : registry.board(course).storedTimes()) {
            if (!entry.frame().isEmpty() && !out.contains(entry.frame())) {
                out.add(entry.frame());
            }
        }
        return out;
    }

    private static int remove(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course, String pilot, @Nullable String frame) {
        int courseId = course.id;
        Leaderboard board = registry.board(course);
        UUID player = board.playerNamed(pilot);
        if (player == null) {
            source.sendFailure(Component.translatable("propwash.race.ghosts.command.unknown_pilot", pilot, courseId));
            return 0;
        }
        String cls = frame == null ? null : frame.strip();
        ServerPlayer by = source.getPlayer();
        List<Leaderboard.Entry> removed = GhostServer.remove(registry, course, player, cls == null || cls.isEmpty() ? null : cls, Leaderboard.Section.ALL,
                source.getTextName(), by == null ? null : by.getUUID(), "command");
        if (removed.isEmpty()) {
            source.sendFailure(Component.translatable("propwash.race.ghosts.command.nothing", pilot, cls == null ? "*" : cls, courseId));
            return 0;
        }
        source.sendSuccess(() -> GhostServer.removedMessage(course, removed), true);
        return removed.size();
    }

    private static int delete(CommandSourceStack source, CourseRegistry registry, CourseRegistry.Course course) {
        int courseId = course.id;
        if (!registry.deleteCourse(courseId)) {
            source.sendFailure(Component.translatable("propwash.race.command.unknown_course", courseId));
            return 0;
        }
        registry.save();
        source.sendSuccess(() -> Component.translatable("propwash.race.command.deleted", courseId), true);
        return 1;
    }
}
