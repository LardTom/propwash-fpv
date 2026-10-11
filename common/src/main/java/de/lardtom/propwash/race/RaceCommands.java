package de.lardtom.propwash.race;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lardtom.propwash.network.PropwashNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
                        .executes(c -> leaderboard(c.getSource(), GateEntity.NO_COURSE))
                        .then(Commands.argument("course", IntegerArgumentType.integer(1))
                                .executes(c -> leaderboard(c.getSource(), IntegerArgumentType.getInteger(c, "course")))))
                .then(Commands.literal("archive")
                        .then(Commands.argument("course", IntegerArgumentType.integer(1))
                                .executes(c -> archive(c.getSource(), IntegerArgumentType.getInteger(c, "course")))))
                .then(Commands.literal("laps")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, CourseLaps.MAX))
                                .executes(c -> laps(c.getSource(), IntegerArgumentType.getInteger(c, "count")))
                                .then(Commands.argument("course", IntegerArgumentType.integer(1))
                                        .executes(c -> courseLaps(c.getSource(), IntegerArgumentType.getInteger(c, "course"),
                                                IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("name")
                        .then(Commands.argument("course", IntegerArgumentType.integer(1))
                                .executes(c -> courseName(c.getSource(), IntegerArgumentType.getInteger(c, "course"), ""))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> courseName(c.getSource(), IntegerArgumentType.getInteger(c, "course"),
                                                StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("course", IntegerArgumentType.integer(1))
                                .executes(c -> reset(c.getSource(), IntegerArgumentType.getInteger(c, "course"), null))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(c -> reset(c.getSource(), IntegerArgumentType.getInteger(c, "course"),
                                                StringArgumentType.getString(c, "player"))))))
                .then(Commands.literal("delete").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("course", IntegerArgumentType.integer(1))
                                .executes(c -> delete(c.getSource(), IntegerArgumentType.getInteger(c, "course")))))));
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
                    CourseRegistry.lapsLabel(course.laps), where, record), false);
        }
        return list.size();
    }

    private static int leaderboard(CommandSourceStack source, int courseId) {
        CourseRegistry registry = registry(source);
        if (registry == null) {
            return 0;
        }
        CourseRegistry.Course course;
        if (courseId == GateEntity.NO_COURSE) {
            ServerPlayer player = source.getPlayer();
            course = player == null ? null : registry.nearestCourse(player, NEAREST_COURSE_RANGE);
            if (course == null) {
                source.sendFailure(Component.translatable("propwash.race.command.no_nearby_course"));
                return 0;
            }
        } else {
            course = registry.course(courseId);
            if (course == null) {
                source.sendFailure(Component.translatable("propwash.race.command.unknown_course", courseId));
                return 0;
            }
        }
        Leaderboard board = registry.board(course);
        List<Leaderboard.Entry> ranking = board.top(null, LEADERBOARD_LINES);
        CourseRegistry.Course shown = course;
        source.sendSuccess(() -> Component.translatable("propwash.race.command.leaderboard_header", shown.id, shown.gates.size())
                .withStyle(ChatFormatting.GOLD), false);
        if (ranking.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("propwash.race.command.no_record"), false);
            return 0;
        }
        long record = ranking.getFirst().nanos();
        for (int i = 0; i < ranking.size(); i++) {
            Leaderboard.Entry entry = ranking.get(i);
            int place = i + 1;
            String gap = i == 0 ? "" : " (" + RaceFormat.delta(entry.nanos() - record) + ")";
            ChatFormatting color = i == 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
            String date = entry.dateMillis() > 0L ? DAY.format(Instant.ofEpochMilli(entry.dateMillis())) : "-";
            source.sendSuccess(() -> Component.translatable("propwash.race.command.leaderboard_line", place, entry.name(),
                    RaceFormat.time(entry.nanos()) + gap, RaceServer.className(entry.frame()), date).withStyle(color), false);
        }
        return ranking.size();
    }

    private static int archive(CommandSourceStack source, int courseId) {
        CourseRegistry registry = registry(source);
        if (registry == null) {
            return 0;
        }
        CourseRegistry.Course course = registry.course(courseId);
        if (course == null) {
            source.sendFailure(Component.translatable("propwash.race.command.unknown_course", courseId));
            return 0;
        }
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
                    board.board().top(null, Integer.MAX_VALUE).size(), record), false);
        }
        return archive.size();
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

    private static CourseRegistry.@Nullable Course editable(CommandSourceStack source, CourseRegistry registry, int courseId) {
        CourseRegistry.Course course = registry.course(courseId);
        if (course == null) {
            source.sendFailure(Component.translatable("propwash.race.command.unknown_course", courseId));
            return null;
        }
        ServerPlayer player = source.getPlayer();
        CourseAccess access = player == null ? CourseAccess.ALLOWED : CourseEdits.access(player, course, CourseEdits.Action.LAPS);
        if (!access.allowed()) {
            source.sendFailure(CourseEdits.denied(access, CourseRegistry.label(course), CourseRegistry.ownerName(course)));
            return null;
        }
        return course;
    }

    private static int courseLaps(CommandSourceStack source, int courseId, int count) {
        CourseRegistry registry = registry(source);
        CourseRegistry.Course course = registry == null ? null : editable(source, registry, courseId);
        if (course == null) {
            return 0;
        }
        registry.setLaps(course, count);
        source.sendSuccess(() -> Component.translatable("propwash.race.command.course_laps", CourseRegistry.label(course),
                CourseRegistry.lapsLabel(course.laps)), true);
        return 1;
    }

    private static int courseName(CommandSourceStack source, int courseId, String name) {
        CourseRegistry registry = registry(source);
        CourseRegistry.Course course = registry == null ? null : editable(source, registry, courseId);
        if (course == null) {
            return 0;
        }
        registry.rename(course, name);
        source.sendSuccess(() -> Component.translatable("propwash.race.command.course_name", course.id, CourseRegistry.label(course)), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, int courseId, @Nullable String playerName) {
        CourseRegistry registry = registry(source);
        if (registry == null) {
            return 0;
        }
        CourseRegistry.Course course = registry.course(courseId);
        if (course == null) {
            source.sendFailure(Component.translatable("propwash.race.command.unknown_course", courseId));
            return 0;
        }
        int removed = registry.resetLeaderboard(course, playerName);
        registry.save();
        source.sendSuccess(() -> Component.translatable("propwash.race.command.reset", courseId, removed), true);
        return removed;
    }

    private static int delete(CommandSourceStack source, int courseId) {
        CourseRegistry registry = registry(source);
        if (registry == null) {
            return 0;
        }
        if (!registry.deleteCourse(courseId)) {
            source.sendFailure(Component.translatable("propwash.race.command.unknown_course", courseId));
            return 0;
        }
        registry.save();
        source.sendSuccess(() -> Component.translatable("propwash.race.command.deleted", courseId), true);
        return 1;
    }
}
