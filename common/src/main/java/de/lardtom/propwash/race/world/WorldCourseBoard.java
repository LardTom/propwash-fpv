package de.lardtom.propwash.race.world;

import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.jspecify.annotations.Nullable;

public final class WorldCourseBoard {
    public static final String COURSE_KEY = "propwash.race.world.board.course";
    public static final String RECORD_KEY = "propwash.race.world.board.record";
    public static final String NO_RECORD_KEY = "propwash.race.world.board.record_none";
    public static final String UNKNOWN_KEY = "propwash.race.world.unknown";
    public static final int LINES = 4;

    private WorldCourseBoard() {
    }

    public static @Nullable BlockPos position(CourseRegistry.Course course) {
        int[] board = course.board;
        if (course.worldCourse.isEmpty() || board == null || board.length != 3) {
            return null;
        }
        return new BlockPos(board[0], board[1], board[2]);
    }

    public static String nameKey(CourseRegistry.Course course) {
        return WorldCourseVariant.byKey(course.worldCourse).map(WorldCourseVariant::nameKey).orElse(UNKNOWN_KEY);
    }

    public static Component[] lines(CourseRegistry.Course course, Leaderboard.@Nullable Entry best) {
        return new Component[]{
                Component.translatable(nameKey(course)).withStyle(ChatFormatting.BOLD),
                Component.translatable(COURSE_KEY, course.id),
                best == null ? Component.translatable(NO_RECORD_KEY) : Component.translatable(RECORD_KEY, RaceFormat.time(best.nanos())),
                best == null ? Component.empty() : Component.literal(best.name())
        };
    }

    public static boolean update(MinecraftServer server, CourseRegistry.Course course, Leaderboard.@Nullable Entry best) {
        BlockPos pos = position(course);
        if (pos == null) {
            return true;
        }
        Identifier dimension = Identifier.tryParse(course.dimension);
        ServerLevel level = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            return true;
        }
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return true;
        }
        SignText current = sign.getText(SignTextSlot.FRONT);
        SignText.Mutable text = current.asMutable();
        Component[] lines = lines(course, best);
        for (int i = 0; i < LINES; i++) {
            text.setLine(i, lines[i]);
        }
        SignText updated = text.asImmutable();
        if (!updated.equals(current)) {
            sign.setText(updated, SignTextSlot.FRONT);
        }
        return true;
    }
}
