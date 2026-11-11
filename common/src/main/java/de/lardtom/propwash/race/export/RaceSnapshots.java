package de.lardtom.propwash.race.export;

import de.lardtom.propwash.api.race.AssistInfo;
import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.Category;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.api.race.RaceStandardInfo;
import de.lardtom.propwash.api.race.RuleValue;
import de.lardtom.propwash.api.race.TimeKind;
import de.lardtom.propwash.api.race.ZoneHint;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CreativeFcRule;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RaceRuleSet;
import de.lardtom.propwash.race.RaceRules;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.RaceStandards;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.zone.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

public final class RaceSnapshots {
    private static final Identifier UNKNOWN_DIMENSION = Identifier.withDefaultNamespace("overworld");
    private static final double ZONE_PROBE_LIFT = 1.0;

    private RaceSnapshots() {
    }

    public static CourseInfo info(CourseRegistry.Course course, RaceRecords records, List<Zone> zones) {
        Leaderboard board = records.current(course.id);
        Optional<BoardEntry> record = Optional.empty();
        if (board != null) {
            Optional<Leaderboard.Entry> best = board.record(null);
            if (best.isPresent()) {
                record = Optional.of(lapEntry(1, best.get(), Category.CLEAN));
            }
        }
        CourseRegistry.GateRecord first = course.gates.isEmpty() ? null : course.gates.getFirst();
        Optional<BlockPos> start = first == null ? Optional.empty() : Optional.of(BlockPos.containing(first.x, first.y, first.z));
        return new CourseInfo(course.key(), course.id, course.name, owner(course), course.ownerName, dimension(course.dimension), course.layout,
                layouts(course, records), course.laps, course.gates.size(), course.checkpoints(), !course.worldCourse.isEmpty(), start,
                zones(course, zones), record, course.mode());
    }

    public static Optional<UUID> owner(CourseRegistry.Course course) {
        if (course.owner == null || course.owner.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(course.owner));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Identifier dimension(@Nullable String dimension) {
        Identifier id = dimension == null ? null : Identifier.tryParse(dimension);
        return id == null ? UNKNOWN_DIMENSION : id;
    }

    public static List<Integer> layouts(CourseRegistry.Course course, RaceRecords records) {
        TreeSet<Integer> layouts = new TreeSet<>();
        for (RaceRecords.Archived archived : records.archive(course.id)) {
            layouts.add(archived.layout());
        }
        layouts.add(course.layout);
        return List.copyOf(layouts);
    }

    public static ZoneHint zones(CourseRegistry.Course course, List<Zone> zones) {
        if (zones.isEmpty() || course.gates.isEmpty()) {
            return ZoneHint.NONE;
        }
        RaceStandard standard = RaceStandards.serverNow();
        List<ZoneHint.Zone> touched = new ArrayList<>();
        for (Zone zone : zones) {
            if (!zone.dimension().equals(course.dimension)) {
                continue;
            }
            for (CourseRegistry.GateRecord gate : course.gates) {
                if (zone.contains(gate.x, gate.y, gate.z) || zone.contains(gate.x, gate.y + ZONE_PROBE_LIFT, gate.z)) {
                    touched.add(new ZoneHint.Zone(zone.id(), zone.name(), zone.raceLegal(), RaceRules.changes(zone, standard)));
                    break;
                }
            }
        }
        return ZoneHint.of(touched);
    }

    public record Source(Leaderboard board, int layout, boolean current, long archivedAt, int elements) {
    }

    public record Row(BoardEntry entry, boolean creativeFc) {
    }

    public record CreativeFc(CreativeFcRule course, boolean worldRule, boolean raceLegal) {
    }

    public static boolean creativeFcWorld() {
        CourseRegistry live = CourseRegistry.get();
        return WorldRules.on(live == null ? null : live.server(), WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL);
    }

    public static CreativeFc creativeFc(CourseRegistry.@Nullable Course course) {
        CreativeFcRule rule = course == null ? CreativeFcRule.WORLD : course.creativeFc();
        boolean world = creativeFcWorld();
        return new CreativeFc(rule, world, rule.legal(world));
    }

    public static boolean recordCreativeFc(CourseRegistry.@Nullable Course course, RaceRecords records) {
        Leaderboard board = course == null ? null : records.current(course.id);
        return board != null && board.record(null).map(Leaderboard.Entry::creativeFc).orElse(false);
    }

    public static List<Source> sources(CourseRegistry.Course course, RaceRecords records) {
        List<Source> out = new ArrayList<>();
        Leaderboard stored = records.current(course.id);
        out.add(new Source(stored == null ? new Leaderboard() : stored, course.layout, true, 0L, course.gates.size()));
        List<RaceRecords.Archived> archive = records.archive(course.id);
        for (int i = archive.size() - 1; i >= 0; i--) {
            RaceRecords.Archived archived = archive.get(i);
            out.add(new Source(archived.board(), archived.layout(), false, archived.archivedAt(), archived.gates()));
        }
        return out;
    }

    public static Optional<BoardSnapshot> board(CourseRegistry.Course course, RaceRecords records, BoardQuery query) {
        int layout = query.layout() == BoardQuery.CURRENT_LAYOUT ? course.layout : query.layout();
        for (Source source : sources(course, records)) {
            if (source.layout() == layout) {
                return Optional.of(board(course, source, query));
            }
        }
        return Optional.empty();
    }

    public static BoardSnapshot board(CourseRegistry.Course course, Source source, BoardQuery query) {
        Leaderboard board = source.board();
        boolean assisted = query.category() == Category.ASSISTED;
        List<Row> rows = new ArrayList<>();
        int total = collect(board, query, rows);
        List<BoardEntry> entries = new ArrayList<>(rows.size());
        for (Row row : rows) {
            entries.add(row.entry());
        }
        return new BoardSnapshot(course.key(), course.id, source.layout(), source.current(), source.archivedAt(), source.elements(), query, total,
                entries, frames(assisted ? board.assistedEntries() : board.entries()), board.raceLapCounts(assisted));
    }

    public static List<Row> rows(Source source, BoardQuery query) {
        List<Row> rows = new ArrayList<>();
        collect(source.board(), query, rows);
        return rows;
    }

    private static int collect(Leaderboard board, BoardQuery query, List<Row> out) {
        boolean assisted = query.category() == Category.ASSISTED;
        String frame = query.frame().orElse(null);
        if (query.kind() == TimeKind.LAP) {
            List<Leaderboard.Entry> all = assisted ? board.assistedTop(frame, Integer.MAX_VALUE) : board.top(frame, Integer.MAX_VALUE);
            for (int i = 0; i < all.size() && i < query.limit(); i++) {
                out.add(new Row(lapEntry(i + 1, all.get(i), query.category()), all.get(i).creativeFc()));
            }
            return all.size();
        }
        List<Leaderboard.RaceRow> rows = board.raceRows(assisted, query.raceLaps());
        if (frame != null) {
            rows = rows.stream().filter(row -> row.frame().equals(frame)).toList();
        }
        for (int i = 0; i < rows.size() && i < query.limit(); i++) {
            out.add(new Row(raceEntry(i + 1, rows.get(i), query.category()), rows.get(i).creativeFc()));
        }
        return rows.size();
    }

    public static List<String> frames(List<Leaderboard.Entry> entries) {
        List<String> frames = new ArrayList<>();
        for (Leaderboard.Entry entry : entries) {
            if (!frames.contains(entry.frame())) {
                frames.add(entry.frame());
            }
        }
        return frames;
    }

    public static BoardEntry lapEntry(int rank, Leaderboard.Entry entry, Category category) {
        AssistReasons reasons = category == Category.ASSISTED ? entry.reasons() : AssistReasons.NONE;
        return new BoardEntry(rank, entry.player(), entry.name(), BoardEntry.millis(entry.nanos()), entry.nanos(), entry.dateMillis(), entry.frame(),
                TimeKind.LAP, 1, category, reasons(reasons), details(reasons), rules(entry.rules()), serverRules(entry.server()));
    }

    public static BoardEntry raceEntry(int rank, Leaderboard.RaceRow row, Category category) {
        AssistReasons reasons = category == Category.ASSISTED ? row.reasons() : AssistReasons.NONE;
        return new BoardEntry(rank, row.player(), row.name(), BoardEntry.millis(row.nanos()), row.nanos(), row.dateMillis(), row.frame(),
                TimeKind.RACE, row.laps(), category, reasons(reasons), details(reasons), rules(row.rules()), serverRules(row.server()));
    }

    public static List<RuleValue> rules(RaceRuleSet rules) {
        List<RuleValue> out = new ArrayList<>();
        for (RaceRuleSet.Seen seen : rules.seen()) {
            out.add(new RuleValue(seen.rule(), seen.value(), seen.zone(), seen.free()));
        }
        return out;
    }

    public static Optional<List<RuleValue>> serverRules(@Nullable RaceStandard server) {
        if (server == null) {
            return Optional.empty();
        }
        List<RuleValue> out = new ArrayList<>();
        for (RaceRules.Rule rule : server.differences()) {
            out.add(new RuleValue(rule.id(), server.value(rule), RuleValue.WORLD, false));
        }
        return Optional.of(out);
    }

    public static RaceStandardInfo standard(RaceStandards data) {
        return standard(data.standard(), data.changedAt(), data.changedBy());
    }

    public static RaceStandardInfo standard(RaceStandard standard, long changedAt, String changedBy) {
        List<RaceStandardInfo.Rule> rules = new ArrayList<>();
        List<String> neutral = new ArrayList<>();
        for (RaceRules.Rule rule : RaceRules.all()) {
            if (rule.relevant()) {
                rules.add(new RaceStandardInfo.Rule(rule.id(), standard.value(rule), rule.defaultValue()));
            } else {
                neutral.add(rule.id());
            }
        }
        return new RaceStandardInfo(rules, neutral, changedAt, changedBy);
    }

    public static List<String> reasons(AssistReasons reasons) {
        List<String> out = new ArrayList<>();
        for (LapAssist reason : reasons.list()) {
            out.add(reason.key());
        }
        return out;
    }

    public static List<AssistInfo> details(AssistReasons reasons) {
        List<AssistInfo> out = new ArrayList<>();
        for (AssistDetail detail : reasons.details()) {
            out.add(new AssistInfo(detail.reason().key(), detail.subject(), detail.value(), detail.reference()));
        }
        return out;
    }

    public static int lapRank(Leaderboard board, UUID player, String frame, boolean assisted) {
        return board.allRank(player, Leaderboard.clipFrame(frame), assisted);
    }

    public static int raceRank(Leaderboard board, UUID player, int laps, boolean assisted) {
        List<Leaderboard.RaceRow> rows = board.raceRows(assisted, laps);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).player().equals(player)) {
                return i + 1;
            }
        }
        return -1;
    }
}
