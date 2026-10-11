package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.Constants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RaceRecords extends SavedData {
    public static final int ARCHIVE_LIMIT = 5;

    private final Map<Integer, CourseBoard> boards = new TreeMap<>();

    public static final class CourseBoard {
        static final Codec<CourseBoard> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("course").forGetter(b -> b.course),
                Leaderboard.CODEC.fieldOf("board").forGetter(b -> b.board),
                Archived.CODEC.listOf().optionalFieldOf("archive", List.of()).forGetter(b -> b.archive)
        ).apply(i, CourseBoard::new));

        public final int course;
        public final Leaderboard board;
        public final List<Archived> archive;

        CourseBoard(int course, Leaderboard board, List<Archived> archive) {
            this.course = course;
            this.board = board;
            this.archive = new ArrayList<>(archive);
        }

        boolean isEmpty() {
            return this.board.isEmpty() && this.archive.isEmpty();
        }
    }

    public record Archived(int layout, long archivedAt, int gates, Leaderboard board) {
        static final Codec<Archived> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("layout").forGetter(Archived::layout),
                Codec.LONG.fieldOf("archived_at").forGetter(Archived::archivedAt),
                Codec.INT.fieldOf("gates").forGetter(Archived::gates),
                Leaderboard.CODEC.fieldOf("board").forGetter(Archived::board)
        ).apply(i, Archived::new));
    }

    private static final Codec<RaceRecords> CODEC = CourseBoard.CODEC.listOf().xmap(RaceRecords::of, RaceRecords::list);

    public static final SavedDataType<RaceRecords> TYPE = new SavedDataType<>(Constants.id("race_records"), RaceRecords::new, CODEC, null);

    public RaceRecords() {
    }

    private static RaceRecords of(List<CourseBoard> list) {
        RaceRecords records = new RaceRecords();
        for (CourseBoard board : list) {
            if (board.course > 0) {
                records.boards.put(board.course, board);
            }
        }
        return records;
    }

    private List<CourseBoard> list() {
        return List.copyOf(this.boards.values());
    }

    public static RaceRecords get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Leaderboard board(int course) {
        return this.courseBoard(course).board;
    }

    public List<Archived> archive(int course) {
        CourseBoard board = this.boards.get(course);
        return board == null ? List.of() : List.copyOf(board.archive);
    }

    private CourseBoard courseBoard(int course) {
        return this.boards.computeIfAbsent(course, id -> new CourseBoard(id, new Leaderboard(), List.of()));
    }

    public boolean isEmpty(int course) {
        CourseBoard board = this.boards.get(course);
        return board == null || board.isEmpty();
    }

    public boolean hasEntries(int course) {
        CourseBoard board = this.boards.get(course);
        return board != null && board.board.size() > 0;
    }

    public boolean archiveCurrent(int course, int layout, int gates, long now) {
        CourseBoard board = this.boards.get(course);
        if (board == null || board.board.times() == 0) {
            return false;
        }
        CourseBoard fresh = new CourseBoard(course, new Leaderboard(), board.archive);
        fresh.archive.add(new Archived(layout, now, gates, board.board));
        while (fresh.archive.size() > ARCHIVE_LIMIT) {
            fresh.archive.removeFirst();
        }
        this.boards.put(course, fresh);
        this.setDirty();
        return true;
    }

    public void remove(int course) {
        if (this.boards.remove(course) != null) {
            this.setDirty();
        }
    }

    public void changed() {
        this.setDirty();
    }
}
