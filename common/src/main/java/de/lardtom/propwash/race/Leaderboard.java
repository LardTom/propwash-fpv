package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class Leaderboard {
    public static final int MAX_NAME = 32;
    public static final int MAX_FRAME = 32;
    public static final int MAX_RULE_SETS = 8;
    public static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::nanos).thenComparingLong(Entry::dateMillis)
            .thenComparing(Entry::name);
    private static final Comparator<RaceTime> RACE_ORDER = Comparator.comparingLong(RaceTime::nanos).thenComparingLong(RaceTime::dateMillis)
            .thenComparing(RaceTime::name);
    private static volatile Supplier<Scoring> source = () -> Scoring.DEFAULT;
    private static final AtomicInteger REVISIONS = new AtomicInteger();

    private final List<Entry> stored = new ArrayList<>();
    private final List<RaceTime> storedRaces = new ArrayList<>();
    private final Map<UUID, Integer> laps = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> assisted = new ArrayList<>();
    private final Map<UUID, Map<Integer, Long>> races = new HashMap<>();
    private final Map<UUID, Map<Integer, AssistedRace>> assistedRaces = new HashMap<>();
    private final Map<UUID, Map<Integer, RaceInfo>> raceInfo = new HashMap<>();
    private final Map<UUID, Map<Integer, RaceInfo>> assistedRaceInfo = new HashMap<>();
    private @Nullable Scoring pinned;
    private @Nullable Scoring scoring;
    private int revision = REVISIONS.incrementAndGet();

    public record Ranked(Entry entry, int classRank, int overallRank) {
    }

    public record Scoring(RaceStandard standard) {
        public static final Scoring DEFAULT = new Scoring(RaceStandard.DEFAULTS);

        public Scoring {
            standard = standard == null ? RaceStandard.DEFAULTS : standard;
        }

        public boolean clean(int assist, RaceRuleSet rules) {
            return (assist & LapAssist.ALL) == LapAssist.NONE && rules.matches(this.standard);
        }

        public boolean clean(int assist, RaceRuleSet rules, @Nullable RaceStandard server) {
            return server == null ? this.clean(assist, rules) : (assist & LapAssist.ALL) == LapAssist.NONE;
        }

        public boolean clean(Entry entry) {
            return this.clean(entry.assist(), entry.rules(), entry.server());
        }

        public boolean clean(RaceTime race) {
            return this.clean(race.assist(), race.rules(), race.server());
        }

        public AssistReasons reasons(int assist, List<AssistDetail> details, RaceRuleSet rules) {
            AssistReasons fixed = assist == LapAssist.NONE ? AssistReasons.NONE : AssistReasons.of(assist, details);
            return fixed.plus(rules.reasons(this.standard)).orOther();
        }

        public AssistReasons reasons(int assist, List<AssistDetail> details, RaceRuleSet rules, @Nullable RaceStandard server) {
            if (server == null) {
                return this.reasons(assist, details, rules);
            }
            return (assist == LapAssist.NONE ? AssistReasons.NONE : AssistReasons.of(assist, details)).orOther();
        }
    }

    public record Result(boolean clean, Outcome outcome, boolean improved, boolean stored) {
        public static final Result NONE = new Result(false, Outcome.NONE, false, false);
    }

    public record RaceInfo(String name, String frame, long dateMillis) {
        public static final RaceInfo NONE = new RaceInfo("", "", 0L);

        public RaceInfo {
            name = clip(name, MAX_NAME);
            frame = clip(frame, MAX_FRAME);
            dateMillis = Math.max(0L, dateMillis);
        }

        public boolean known() {
            return !this.equals(NONE);
        }
    }

    public record RaceRow(UUID player, String name, String frame, int laps, long nanos, long dateMillis, AssistReasons reasons, RaceRuleSet rules,
                          @Nullable RaceStandard server, boolean creativeFc) {
        public RaceRow(UUID player, String name, String frame, int laps, long nanos, long dateMillis, AssistReasons reasons, RaceRuleSet rules,
                       @Nullable RaceStandard server) {
            this(player, name, frame, laps, nanos, dateMillis, reasons, rules, server, false);
        }

        public RaceRow(UUID player, String name, String frame, int laps, long nanos, long dateMillis, AssistReasons reasons, RaceRuleSet rules) {
            this(player, name, frame, laps, nanos, dateMillis, reasons, rules, null);
        }

        public RaceRow(UUID player, String name, String frame, int laps, long nanos, long dateMillis, AssistReasons reasons) {
            this(player, name, frame, laps, nanos, dateMillis, reasons, RaceRuleSet.DEFAULTS);
        }
    }

    public record Entry(UUID player, String name, String frame, long nanos, long dateMillis, int assist, List<AssistDetail> details,
                        RaceRuleSet rules, boolean hidden, @Nullable RaceStandard server, boolean creativeFc) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player),
                Codec.STRING.fieldOf("name").forGetter(Entry::name),
                Codec.STRING.optionalFieldOf("frame", "").forGetter(Entry::frame),
                Codec.LONG.fieldOf("nanos").forGetter(Entry::nanos),
                Codec.LONG.optionalFieldOf("date", 0L).forGetter(Entry::dateMillis),
                Codec.INT.optionalFieldOf("assist", LapAssist.NONE).forGetter(Entry::assist),
                AssistDetail.CODEC.listOf().optionalFieldOf("assist_details", List.of()).forGetter(Entry::details),
                RaceRuleSet.CODEC.optionalFieldOf("rules", RaceRuleSet.DEFAULTS).forGetter(Entry::rules),
                Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Entry::hidden),
                RaceStandard.CODEC.optionalFieldOf("server_rules").forGetter(entry -> Optional.ofNullable(entry.server())),
                Codec.BOOL.optionalFieldOf("creative_fc", false).forGetter(Entry::creativeFc)
        ).apply(i, (player, name, frame, nanos, date, assist, details, rules, hidden, server, creativeFc) -> new Entry(player, name, frame, nanos, date,
                assist, details, rules, hidden, server.orElse(null), creativeFc)));

        public Entry {
            name = clip(name, MAX_NAME);
            frame = clip(frame, MAX_FRAME);
            assist = assist & LapAssist.ALL;
            details = AssistReasons.of(assist, details).details();
            rules = rules == null ? RaceRuleSet.DEFAULTS : rules;
            hidden = hidden && assist == LapAssist.NONE;
        }

        public Entry(UUID player, String name, String frame, long nanos, long dateMillis, int assist, List<AssistDetail> details, RaceRuleSet rules,
                     boolean hidden, @Nullable RaceStandard server) {
            this(player, name, frame, nanos, dateMillis, assist, details, rules, hidden, server, false);
        }

        public Entry(UUID player, String name, String frame, long nanos, long dateMillis, int assist, List<AssistDetail> details, RaceRuleSet rules,
                     boolean hidden) {
            this(player, name, frame, nanos, dateMillis, assist, details, rules, hidden, null);
        }

        public Entry(UUID player, String name, String frame, long nanos, long dateMillis, int assist, List<AssistDetail> details, RaceRuleSet rules) {
            this(player, name, frame, nanos, dateMillis, assist, details, rules, false);
        }

        public Entry(UUID player, String name, String frame, long nanos, long dateMillis, int assist, List<AssistDetail> details) {
            this(player, name, frame, nanos, dateMillis, assist, details, RaceRuleSet.DEFAULTS);
        }

        public Entry(UUID player, String name, String frame, long nanos, long dateMillis, int assist) {
            this(player, name, frame, nanos, dateMillis, assist, List.of());
        }

        public Entry(UUID player, String name, String frame, long nanos, long dateMillis) {
            this(player, name, frame, nanos, dateMillis, LapAssist.NONE);
        }

        public boolean assisted() {
            return this.assist != LapAssist.NONE;
        }

        public AssistReasons reasons() {
            return AssistReasons.of(this.assist, this.details);
        }

        Entry named(String next) {
            return new Entry(this.player, next, this.frame, this.nanos, this.dateMillis, this.assist, this.details, this.rules, this.hidden, this.server,
                    this.creativeFc);
        }
    }

    public record RaceTime(UUID player, int laps, long nanos, String name, String frame, long dateMillis, int assist, List<AssistDetail> details,
                           RaceRuleSet rules, boolean hidden, @Nullable RaceStandard server, boolean creativeFc) {
        public static final Codec<RaceTime> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(RaceTime::player),
                Codec.INT.fieldOf("laps").forGetter(RaceTime::laps),
                Codec.LONG.fieldOf("nanos").forGetter(RaceTime::nanos),
                Codec.STRING.optionalFieldOf("name", "").forGetter(RaceTime::name),
                Codec.STRING.optionalFieldOf("frame", "").forGetter(RaceTime::frame),
                Codec.LONG.optionalFieldOf("date", 0L).forGetter(RaceTime::dateMillis),
                Codec.INT.optionalFieldOf("assist", LapAssist.NONE).forGetter(RaceTime::assist),
                AssistDetail.CODEC.listOf().optionalFieldOf("assist_details", List.of()).forGetter(RaceTime::details),
                RaceRuleSet.CODEC.optionalFieldOf("rules", RaceRuleSet.DEFAULTS).forGetter(RaceTime::rules),
                Codec.BOOL.optionalFieldOf("hidden", false).forGetter(RaceTime::hidden),
                RaceStandard.CODEC.optionalFieldOf("server_rules").forGetter(race -> Optional.ofNullable(race.server())),
                Codec.BOOL.optionalFieldOf("creative_fc", false).forGetter(RaceTime::creativeFc)
        ).apply(i, (player, laps, nanos, name, frame, date, assist, details, rules, hidden, server, creativeFc) -> new RaceTime(player, laps, nanos,
                name, frame, date, assist, details, rules, hidden, server.orElse(null), creativeFc)));

        public RaceTime {
            name = clip(name, MAX_NAME);
            frame = clip(frame, MAX_FRAME);
            assist = assist & LapAssist.ALL;
            details = AssistReasons.of(assist, details).details();
            rules = rules == null ? RaceRuleSet.DEFAULTS : rules;
            hidden = hidden && assist == LapAssist.NONE;
        }

        public RaceTime(UUID player, int laps, long nanos, String name, String frame, long dateMillis, int assist, List<AssistDetail> details,
                        RaceRuleSet rules, boolean hidden, @Nullable RaceStandard server) {
            this(player, laps, nanos, name, frame, dateMillis, assist, details, rules, hidden, server, false);
        }

        public RaceTime(UUID player, int laps, long nanos, String name, String frame, long dateMillis, int assist, List<AssistDetail> details,
                        RaceRuleSet rules, boolean hidden) {
            this(player, laps, nanos, name, frame, dateMillis, assist, details, rules, hidden, null);
        }

        public RaceTime(UUID player, int laps, long nanos, String name, String frame, long dateMillis, int assist, List<AssistDetail> details,
                        RaceRuleSet rules) {
            this(player, laps, nanos, name, frame, dateMillis, assist, details, rules, false);
        }

        RaceTime named(String next) {
            return new RaceTime(this.player, this.laps, this.nanos, next, this.frame, this.dateMillis, this.assist, this.details, this.rules, this.hidden,
                    this.server, this.creativeFc);
        }
    }

    public record AssistedRace(long nanos, int assist, List<AssistDetail> details) {
        public AssistedRace {
            assist = assist & LapAssist.ALL;
            details = AssistReasons.of(assist, details).details();
        }

        public AssistedRace(long nanos, int assist) {
            this(nanos, assist, List.of());
        }

        public AssistReasons reasons() {
            return AssistReasons.of(this.assist, this.details);
        }
    }

    private record Laps(UUID player, int count) {
        static final Codec<Laps> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Laps::player),
                Codec.INT.fieldOf("count").forGetter(Laps::count)
        ).apply(i, Laps::new));
    }

    private record RaceBest(UUID player, int laps, long nanos, String name, String frame, long date) {
        static final Codec<RaceBest> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(RaceBest::player),
                Codec.INT.fieldOf("laps").forGetter(RaceBest::laps),
                Codec.LONG.fieldOf("nanos").forGetter(RaceBest::nanos),
                Codec.STRING.optionalFieldOf("name", "").forGetter(RaceBest::name),
                Codec.STRING.optionalFieldOf("frame", "").forGetter(RaceBest::frame),
                Codec.LONG.optionalFieldOf("date", 0L).forGetter(RaceBest::date)
        ).apply(i, RaceBest::new));
    }

    private record AssistedRaceBest(UUID player, int laps, long nanos, int assist, List<AssistDetail> details, String name, String frame, long date) {
        static final Codec<AssistedRaceBest> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(AssistedRaceBest::player),
                Codec.INT.fieldOf("laps").forGetter(AssistedRaceBest::laps),
                Codec.LONG.fieldOf("nanos").forGetter(AssistedRaceBest::nanos),
                Codec.INT.optionalFieldOf("assist", LapAssist.NONE).forGetter(AssistedRaceBest::assist),
                AssistDetail.CODEC.listOf().optionalFieldOf("assist_details", List.of()).forGetter(AssistedRaceBest::details),
                Codec.STRING.optionalFieldOf("name", "").forGetter(AssistedRaceBest::name),
                Codec.STRING.optionalFieldOf("frame", "").forGetter(AssistedRaceBest::frame),
                Codec.LONG.optionalFieldOf("date", 0L).forGetter(AssistedRaceBest::date)
        ).apply(i, AssistedRaceBest::new));
    }

    public static final Codec<Leaderboard> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(Leaderboard::entries),
            Laps.CODEC.listOf().optionalFieldOf("laps", List.of()).forGetter(Leaderboard::lapList),
            RaceBest.CODEC.listOf().optionalFieldOf("races", List.of()).forGetter(Leaderboard::raceList),
            Entry.CODEC.listOf().optionalFieldOf("assisted", List.of()).forGetter(Leaderboard::assistedEntries),
            AssistedRaceBest.CODEC.listOf().optionalFieldOf("assisted_races", List.of()).forGetter(Leaderboard::assistedRaceList),
            Entry.CODEC.listOf().optionalFieldOf("lap_times").forGetter(board -> Optional.of(board.storedTimes())),
            RaceTime.CODEC.listOf().optionalFieldOf("race_times").forGetter(board -> Optional.of(board.storedRaceTimes()))
    ).apply(i, Leaderboard::of));

    public enum Outcome {
        NONE,
        PERSONAL_BEST,
        CLASS_RECORD,
        COURSE_RECORD;

        public boolean improved() {
            return this != NONE;
        }

        public boolean record() {
            return this == CLASS_RECORD || this == COURSE_RECORD;
        }
    }

    public static void scoringSource(Supplier<Scoring> next) {
        source = next == null ? () -> Scoring.DEFAULT : next;
    }

    private static Leaderboard of(List<Entry> entries, List<Laps> laps, List<RaceBest> races, List<Entry> assisted,
                                  List<AssistedRaceBest> assistedRaces, Optional<List<Entry>> lapTimes, Optional<List<RaceTime>> raceTimes) {
        Leaderboard board = new Leaderboard();
        board.scoring = Scoring.DEFAULT;
        if (lapTimes.isPresent()) {
            for (Entry entry : lapTimes.get()) {
                if (entry.nanos() > 0L) {
                    board.insert(entry);
                }
            }
        } else {
            for (Entry entry : entries) {
                if (entry.nanos() > 0L) {
                    board.insert(new Entry(entry.player(), entry.name(), entry.frame(), entry.nanos(), entry.dateMillis(), LapAssist.NONE, List.of(),
                            entry.rules()));
                }
            }
            for (Entry entry : assisted) {
                if (entry.nanos() > 0L) {
                    RaceRuleSet.Split split = RaceRuleSet.split(entry.reasons().orOther());
                    board.insert(new Entry(entry.player(), entry.name(), entry.frame(), entry.nanos(), entry.dateMillis(), split.fixed().mask(),
                            split.fixed().details(), entry.rules().isDefault() ? split.rules() : entry.rules()));
                }
            }
        }
        if (raceTimes.isPresent()) {
            for (RaceTime race : raceTimes.get()) {
                if (race.laps() > 0 && race.nanos() > 0L) {
                    board.insertRace(race);
                }
            }
        } else {
            for (RaceBest race : races) {
                if (race.laps() > 0 && race.nanos() > 0L) {
                    board.insertRace(new RaceTime(race.player(), race.laps(), race.nanos(), race.name(), race.frame(), race.date(), LapAssist.NONE,
                            List.of(), RaceRuleSet.DEFAULTS));
                }
            }
            for (AssistedRaceBest race : assistedRaces) {
                if (race.laps() > 0 && race.nanos() > 0L) {
                    RaceRuleSet.Split split = RaceRuleSet.split(AssistReasons.of(race.assist(), race.details()).orOther());
                    board.insertRace(new RaceTime(race.player(), race.laps(), race.nanos(), race.name(), race.frame(), race.date(), split.fixed().mask(),
                            split.fixed().details(), split.rules()));
                }
            }
        }
        for (Laps lap : laps) {
            board.laps.put(lap.player(), Math.max(0, lap.count()));
        }
        board.scoring = null;
        return board;
    }

    public static String clipFrame(@Nullable String frame) {
        return clip(frame, MAX_FRAME);
    }

    private static String clip(@Nullable String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    public void score(@Nullable Scoring fixed) {
        this.pinned = fixed;
        this.refresh();
    }

    public Scoring scoring() {
        this.refresh();
        return this.scoring == null ? Scoring.DEFAULT : this.scoring;
    }

    private void refresh() {
        Scoring wanted = this.pinned != null ? this.pinned : source.get();
        if (!wanted.equals(this.scoring)) {
            this.scoring = wanted;
            this.rebuild();
        }
    }

    private Scoring current() {
        return this.scoring == null ? Scoring.DEFAULT : this.scoring;
    }

    private void rebuild() {
        Scoring rules = this.current();
        this.revision = REVISIONS.incrementAndGet();
        this.entries.clear();
        this.assisted.clear();
        Map<String, Entry> clean = new LinkedHashMap<>();
        Map<String, Entry> flagged = new LinkedHashMap<>();
        for (Entry time : this.stored) {
            String key = time.player() + "|" + time.frame();
            if (rules.clean(time)) {
                clean.merge(key, new Entry(time.player(), time.name(), time.frame(), time.nanos(), time.dateMillis(), LapAssist.NONE, List.of(),
                        time.rules(), false, time.server(), time.creativeFc()), Leaderboard::faster);
            } else if (!time.hidden()) {
                AssistReasons reasons = rules.reasons(time.assist(), time.details(), time.rules(), time.server());
                flagged.merge(key, new Entry(time.player(), time.name(), time.frame(), time.nanos(), time.dateMillis(), reasons.mask(),
                        reasons.details(), time.rules(), false, time.server(), time.creativeFc()), Leaderboard::faster);
            }
        }
        this.entries.addAll(clean.values());
        this.entries.sort(ORDER);
        this.assisted.addAll(flagged.values());
        this.assisted.sort(ORDER);
        this.races.clear();
        this.assistedRaces.clear();
        this.raceInfo.clear();
        this.assistedRaceInfo.clear();
        List<RaceTime> ordered = new ArrayList<>(this.storedRaces);
        ordered.sort(RACE_ORDER);
        for (RaceTime race : ordered) {
            if (rules.clean(race)) {
                Map<Integer, Long> map = this.races.computeIfAbsent(race.player(), p -> new TreeMap<>());
                if (!map.containsKey(race.laps())) {
                    map.put(race.laps(), race.nanos());
                    putInfo(this.raceInfo, race.player(), race.laps(), new RaceInfo(race.name(), race.frame(), race.dateMillis()));
                }
            } else if (!race.hidden()) {
                Map<Integer, AssistedRace> map = this.assistedRaces.computeIfAbsent(race.player(), p -> new TreeMap<>());
                if (!map.containsKey(race.laps())) {
                    AssistReasons reasons = rules.reasons(race.assist(), race.details(), race.rules(), race.server());
                    map.put(race.laps(), new AssistedRace(race.nanos(), reasons.mask(), reasons.details()));
                    putInfo(this.assistedRaceInfo, race.player(), race.laps(), new RaceInfo(race.name(), race.frame(), race.dateMillis()));
                }
            }
        }
    }

    private static Entry faster(Entry a, Entry b) {
        return ORDER.compare(b, a) < 0 ? b : a;
    }

    private boolean insert(Entry time) {
        boolean fixed = time.assist() != LapAssist.NONE;
        Entry same = null;
        for (Entry entry : this.stored) {
            if (entry.player().equals(time.player()) && entry.frame().equals(time.frame())
                    && (fixed ? entry.assist() != LapAssist.NONE : entry.assist() == LapAssist.NONE && entry.rules().equals(time.rules())
                    && entry.hidden() == time.hidden() && Objects.equals(entry.server(), time.server()))) {
                same = entry;
                break;
            }
        }
        if (same != null) {
            if (same.nanos() <= time.nanos()) {
                return false;
            }
            this.stored.remove(same);
        }
        this.stored.add(time);
        Scoring rules = this.current();
        Comparator<Entry> keep = Comparator.<Entry, Boolean>comparing(entry -> !rules.clean(entry)).thenComparing(ORDER);
        List<Entry> sets = new ArrayList<>();
        for (Entry entry : this.stored) {
            if (entry.frame().equals(time.frame()) && entry.player().equals(time.player()) && entry.assist() == LapAssist.NONE) {
                sets.add(entry);
            }
        }
        sets.sort(keep);
        for (int i = MAX_RULE_SETS; i < sets.size(); i++) {
            this.stored.remove(sets.get(i));
        }
        return this.stored.contains(time);
    }

    private boolean insertRace(RaceTime time) {
        boolean fixed = time.assist() != LapAssist.NONE;
        RaceTime same = null;
        for (RaceTime race : this.storedRaces) {
            if (race.player().equals(time.player()) && race.laps() == time.laps()
                    && (fixed ? race.assist() != LapAssist.NONE : race.assist() == LapAssist.NONE && race.rules().equals(time.rules())
                    && race.hidden() == time.hidden() && Objects.equals(race.server(), time.server()))) {
                same = race;
                break;
            }
        }
        if (same != null) {
            if (same.nanos() <= time.nanos()) {
                return false;
            }
            this.storedRaces.remove(same);
        }
        this.storedRaces.add(time);
        Scoring rules = this.current();
        Comparator<RaceTime> keep = Comparator.<RaceTime, Boolean>comparing(race -> !rules.clean(race)).thenComparing(RACE_ORDER);
        List<RaceTime> sets = new ArrayList<>();
        for (RaceTime race : this.storedRaces) {
            if (race.laps() == time.laps() && race.player().equals(time.player()) && race.assist() == LapAssist.NONE) {
                sets.add(race);
            }
        }
        sets.sort(keep);
        for (int i = MAX_RULE_SETS; i < sets.size(); i++) {
            this.storedRaces.remove(sets.get(i));
        }
        return this.storedRaces.contains(time);
    }

    public List<Entry> storedTimes() {
        return List.copyOf(this.stored);
    }

    public List<RaceTime> storedRaceTimes() {
        return List.copyOf(this.storedRaces);
    }

    public int stored() {
        return this.stored.size() + this.storedRaces.size();
    }

    public List<Entry> entries() {
        this.refresh();
        return List.copyOf(this.entries);
    }

    private List<Laps> lapList() {
        List<Laps> list = new ArrayList<>();
        this.laps.forEach((player, count) -> list.add(new Laps(player, count)));
        return list;
    }

    public List<Entry> assistedEntries() {
        this.refresh();
        return List.copyOf(this.assisted);
    }

    private List<AssistedRaceBest> assistedRaceList() {
        this.refresh();
        List<AssistedRaceBest> list = new ArrayList<>();
        this.assistedRaces.forEach((player, map) -> map.forEach((count, race) -> {
            RaceInfo info = info(this.assistedRaceInfo, player, count);
            list.add(new AssistedRaceBest(player, count, race.nanos(), race.assist(), race.details(), info.name(), info.frame(), info.dateMillis()));
        }));
        return list;
    }

    private List<RaceBest> raceList() {
        this.refresh();
        List<RaceBest> list = new ArrayList<>();
        this.races.forEach((player, map) -> map.forEach((count, nanos) -> {
            RaceInfo info = info(this.raceInfo, player, count);
            list.add(new RaceBest(player, count, nanos, info.name(), info.frame(), info.dateMillis()));
        }));
        return list;
    }

    private static void putInfo(Map<UUID, Map<Integer, RaceInfo>> target, UUID player, int laps, RaceInfo info) {
        if (info.known()) {
            target.computeIfAbsent(player, p -> new TreeMap<>()).put(laps, info);
            return;
        }
        Map<Integer, RaceInfo> map = target.get(player);
        if (map != null) {
            map.remove(laps);
            if (map.isEmpty()) {
                target.remove(player);
            }
        }
    }

    private static RaceInfo info(Map<UUID, Map<Integer, RaceInfo>> source, UUID player, int laps) {
        Map<Integer, RaceInfo> map = source.get(player);
        RaceInfo info = map == null ? null : map.get(laps);
        return info == null ? RaceInfo.NONE : info;
    }

    public RaceInfo raceInfo(UUID player, int laps, boolean assisted) {
        this.refresh();
        return info(assisted ? this.assistedRaceInfo : this.raceInfo, player, laps);
    }

    public List<Integer> raceLapCounts(boolean assisted) {
        this.refresh();
        Set<Integer> counts = new TreeSet<>();
        if (assisted) {
            this.assistedRaces.values().forEach(map -> counts.addAll(map.keySet()));
        } else {
            this.races.values().forEach(map -> counts.addAll(map.keySet()));
        }
        return List.copyOf(counts);
    }

    public List<RaceRow> raceRows(boolean assisted, int laps) {
        this.refresh();
        Scoring rules = this.current();
        Map<UUID, RaceTime> sets = new HashMap<>();
        List<RaceTime> ordered = new ArrayList<>(this.storedRaces);
        ordered.sort(RACE_ORDER);
        for (RaceTime race : ordered) {
            if (race.laps() == laps && rules.clean(race) != assisted) {
                sets.putIfAbsent(race.player(), race);
            }
        }
        List<RaceRow> rows = new ArrayList<>();
        if (assisted) {
            this.assistedRaces.forEach((player, map) -> {
                AssistedRace race = map.get(laps);
                if (race != null) {
                    RaceInfo info = info(this.assistedRaceInfo, player, laps);
                    RaceTime source = sets.get(player);
                    rows.add(new RaceRow(player, this.nameOf(player, info), info.frame(), laps, race.nanos(), info.dateMillis(), race.reasons(),
                            source == null ? RaceRuleSet.DEFAULTS : source.rules(), source == null ? null : source.server(),
                            source != null && source.creativeFc()));
                }
            });
        } else {
            this.races.forEach((player, map) -> {
                Long nanos = map.get(laps);
                if (nanos != null) {
                    RaceInfo info = info(this.raceInfo, player, laps);
                    RaceTime source = sets.get(player);
                    rows.add(new RaceRow(player, this.nameOf(player, info), info.frame(), laps, nanos, info.dateMillis(), AssistReasons.NONE,
                            source == null ? RaceRuleSet.DEFAULTS : source.rules(), source == null ? null : source.server(),
                            source != null && source.creativeFc()));
                }
            });
        }
        rows.sort(Comparator.comparingLong(RaceRow::nanos).thenComparingLong(RaceRow::dateMillis).thenComparing(RaceRow::name)
                .thenComparing(RaceRow::player));
        return rows;
    }

    public String nameOf(UUID player) {
        this.refresh();
        return this.nameOf(player, RaceInfo.NONE);
    }

    private String nameOf(UUID player, RaceInfo info) {
        if (!info.name().isEmpty()) {
            return info.name();
        }
        for (Entry entry : this.stored) {
            if (entry.player().equals(player)) {
                return entry.name();
            }
        }
        for (RaceTime race : this.storedRaces) {
            if (race.player().equals(player) && !race.name().isEmpty()) {
                return race.name();
            }
        }
        return "";
    }

    public boolean isEmpty() {
        return this.stored.isEmpty() && this.laps.isEmpty() && this.storedRaces.isEmpty();
    }

    public int size() {
        this.refresh();
        return this.entries.size();
    }

    public int assistedSize() {
        this.refresh();
        return this.assisted.size();
    }

    public int times() {
        this.refresh();
        return this.entries.size() + this.assisted.size();
    }

    public Result submitTime(UUID player, String name, String frame, long nanos, long dateMillis, AssistReasons fixed, RaceRuleSet rules,
                             boolean keepAssisted) {
        return this.submitTime(player, name, frame, nanos, dateMillis, fixed, rules, keepAssisted, null);
    }

    public Result submitTime(UUID player, String name, String frame, long nanos, long dateMillis, AssistReasons fixed, RaceRuleSet rules,
                             boolean keepAssisted, @Nullable RaceStandard server) {
        return this.submitTime(player, name, frame, nanos, dateMillis, fixed, rules, keepAssisted, server, false);
    }

    public Result submitTime(UUID player, String name, String frame, long nanos, long dateMillis, AssistReasons fixed, RaceRuleSet rules,
                             boolean keepAssisted, @Nullable RaceStandard server, boolean creativeFc) {
        this.refresh();
        if (nanos <= 0L) {
            return Result.NONE;
        }
        String cls = clip(frame, MAX_FRAME);
        RaceRuleSet set = rules == null ? RaceRuleSet.DEFAULTS : rules;
        Scoring scoring = this.current();
        this.rename(player, name);
        if (scoring.clean(fixed.mask(), set, server)) {
            this.laps.merge(player, 1, Integer::sum);
            Optional<Entry> courseRecord = this.record(null);
            Optional<Entry> classRecord = this.record(cls);
            Entry own = this.best(player, cls);
            boolean stored = this.insert(new Entry(player, name, cls, nanos, dateMillis, LapAssist.NONE, List.of(), set, !keepAssisted, server,
                    creativeFc));
            this.rebuild();
            if (own != null && own.nanos() <= nanos) {
                return new Result(true, Outcome.NONE, false, stored);
            }
            Outcome outcome;
            if (courseRecord.isEmpty() || nanos < courseRecord.get().nanos()) {
                outcome = Outcome.COURSE_RECORD;
            } else if (classRecord.isEmpty() || nanos < classRecord.get().nanos()) {
                outcome = Outcome.CLASS_RECORD;
            } else {
                outcome = Outcome.PERSONAL_BEST;
            }
            return new Result(true, outcome, true, stored);
        }
        if (fixed.any() && !keepAssisted) {
            return Result.NONE;
        }
        Entry before = this.flagged(player, cls);
        boolean stored = this.insert(new Entry(player, name, cls, nanos, dateMillis, fixed.mask(), fixed.details(), set, !keepAssisted, server,
                creativeFc));
        this.rebuild();
        boolean improved = stored && keepAssisted && (before == null || nanos < before.nanos());
        return new Result(false, Outcome.NONE, improved, stored);
    }

    private @Nullable Entry flagged(UUID player, String frame) {
        Scoring scoring = this.current();
        Entry best = null;
        for (Entry entry : this.stored) {
            if (entry.player().equals(player) && entry.frame().equals(frame) && !entry.hidden() && !scoring.clean(entry)
                    && (best == null || ORDER.compare(entry, best) < 0)) {
                best = entry;
            }
        }
        return best;
    }

    public Outcome submit(UUID player, String name, String frame, long nanos, long dateMillis) {
        return this.submitTime(player, name, frame, nanos, dateMillis, AssistReasons.NONE, RaceRuleSet.DEFAULTS, true).outcome();
    }

    public boolean submitAssisted(UUID player, String name, String frame, long nanos, long dateMillis, int assist) {
        return this.submitAssisted(player, name, frame, nanos, dateMillis, AssistReasons.of(assist));
    }

    public boolean submitAssisted(UUID player, String name, String frame, long nanos, long dateMillis, AssistReasons assist) {
        RaceRuleSet.Split split = RaceRuleSet.split(assist.orOther());
        return this.submitTime(player, name, frame, nanos, dateMillis, split.fixed(), split.rules(), true).improved();
    }

    private void rename(UUID player, String name) {
        String clipped = clip(name, MAX_NAME);
        for (int i = 0; i < this.stored.size(); i++) {
            Entry entry = this.stored.get(i);
            if (entry.player().equals(player) && !entry.name().equals(clipped)) {
                this.stored.set(i, entry.named(name));
            }
        }
        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);
            if (entry.player().equals(player) && !entry.name().equals(clipped)) {
                this.entries.set(i, entry.named(name));
            }
        }
        for (int i = 0; i < this.assisted.size(); i++) {
            Entry entry = this.assisted.get(i);
            if (entry.player().equals(player) && !entry.name().equals(clipped)) {
                this.assisted.set(i, entry.named(name));
            }
        }
    }

    public Result submitRaceTime(UUID player, int laps, long totalNanos, String name, String frame, long dateMillis, AssistReasons fixed,
                                 RaceRuleSet rules, boolean keepAssisted) {
        return this.submitRaceTime(player, laps, totalNanos, name, frame, dateMillis, fixed, rules, keepAssisted, null);
    }

    public Result submitRaceTime(UUID player, int laps, long totalNanos, String name, String frame, long dateMillis, AssistReasons fixed,
                                 RaceRuleSet rules, boolean keepAssisted, @Nullable RaceStandard server) {
        return this.submitRaceTime(player, laps, totalNanos, name, frame, dateMillis, fixed, rules, keepAssisted, server, false);
    }

    public Result submitRaceTime(UUID player, int laps, long totalNanos, String name, String frame, long dateMillis, AssistReasons fixed,
                                 RaceRuleSet rules, boolean keepAssisted, @Nullable RaceStandard server, boolean creativeFc) {
        this.refresh();
        if (laps <= 0 || totalNanos <= 0L) {
            return Result.NONE;
        }
        RaceRuleSet set = rules == null ? RaceRuleSet.DEFAULTS : rules;
        Scoring scoring = this.current();
        boolean clean = scoring.clean(fixed.mask(), set, server);
        if (!clean && fixed.any() && !keepAssisted) {
            return Result.NONE;
        }
        long before = LapTracker.NONE;
        for (RaceTime race : this.storedRaces) {
            if (race.player().equals(player) && race.laps() == laps && scoring.clean(race) == clean && (clean || !race.hidden())
                    && (before == LapTracker.NONE || race.nanos() < before)) {
                before = race.nanos();
            }
        }
        boolean stored = this.insertRace(new RaceTime(player, laps, totalNanos, name, frame, dateMillis, clean ? LapAssist.NONE : fixed.mask(),
                clean ? List.of() : fixed.details(), set, !keepAssisted, server, creativeFc));
        this.rebuild();
        boolean improved = stored && (clean || keepAssisted) && (before == LapTracker.NONE || totalNanos < before);
        return new Result(clean, Outcome.NONE, improved, stored);
    }

    public boolean submitRace(UUID player, int laps, long totalNanos) {
        return this.submitRace(player, laps, totalNanos, "", "", 0L);
    }

    public boolean submitRace(UUID player, int laps, long totalNanos, String name, String frame, long dateMillis) {
        return this.submitRaceTime(player, laps, totalNanos, name, frame, dateMillis, AssistReasons.NONE, RaceRuleSet.DEFAULTS, true).improved();
    }

    public boolean submitAssistedRace(UUID player, int laps, long totalNanos, int assist) {
        return this.submitAssistedRace(player, laps, totalNanos, AssistReasons.of(assist));
    }

    public boolean submitAssistedRace(UUID player, int laps, long totalNanos, AssistReasons assist) {
        return this.submitAssistedRace(player, laps, totalNanos, assist, "", "", 0L);
    }

    public boolean submitAssistedRace(UUID player, int laps, long totalNanos, AssistReasons assist, String name, String frame, long dateMillis) {
        RaceRuleSet.Split split = RaceRuleSet.split(assist.orOther());
        return this.submitRaceTime(player, laps, totalNanos, name, frame, dateMillis, split.fixed(), split.rules(), true).improved();
    }

    public @Nullable AssistedRace assistedRaceBest(UUID player, int laps) {
        this.refresh();
        Map<Integer, AssistedRace> map = this.assistedRaces.get(player);
        return map == null ? null : map.get(laps);
    }

    public long raceBest(UUID player, int laps) {
        this.refresh();
        Map<Integer, Long> map = this.races.get(player);
        Long best = map == null ? null : map.get(laps);
        return best == null ? LapTracker.NONE : best;
    }

    public int lapsOf(UUID player) {
        return this.laps.getOrDefault(player, 0);
    }

    public List<Entry> top(@Nullable String frame, int limit) {
        this.refresh();
        return top(this.entries, frame, limit);
    }

    public List<Entry> assistedTop(@Nullable String frame, int limit) {
        this.refresh();
        return top(this.assisted, frame, limit);
    }

    private static List<Entry> top(List<Entry> list, @Nullable String frame, int limit) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : list) {
            if (result.size() >= limit) {
                break;
            }
            if (frame == null || entry.frame().equals(frame)) {
                result.add(entry);
            }
        }
        return result;
    }

    public Optional<Entry> record(@Nullable String frame) {
        List<Entry> top = this.top(frame, 1);
        return top.isEmpty() ? Optional.empty() : Optional.of(top.getFirst());
    }

    public @Nullable Entry best(UUID player, @Nullable String frame) {
        this.refresh();
        return best(this.entries, player, frame);
    }

    public @Nullable Entry bestAssisted(UUID player, @Nullable String frame) {
        this.refresh();
        return best(this.assisted, player, frame);
    }

    private static @Nullable Entry best(List<Entry> list, UUID player, @Nullable String frame) {
        for (Entry entry : list) {
            if (entry.player().equals(player) && (frame == null || entry.frame().equals(frame))) {
                return entry;
            }
        }
        return null;
    }

    public int rank(UUID player, @Nullable String frame) {
        this.refresh();
        return rank(this.entries, player, frame);
    }

    public int assistedRank(UUID player, @Nullable String frame) {
        this.refresh();
        return rank(this.assisted, player, frame);
    }

    private static int rank(List<Entry> list, UUID player, @Nullable String frame) {
        List<Entry> ranking = top(list, frame, Integer.MAX_VALUE);
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).player().equals(player)) {
                return i + 1;
            }
        }
        return -1;
    }

    public int allRank(UUID player, @Nullable String frame, boolean assisted) {
        this.refresh();
        List<Entry> list = assisted ? this.assisted : this.entries;
        for (int i = 0; i < list.size(); i++) {
            Entry entry = list.get(i);
            if (entry.player().equals(player) && (frame == null || entry.frame().equals(frame))) {
                return i + 1;
            }
        }
        return -1;
    }

    public List<String> classes() {
        this.refresh();
        return classes(this.entries);
    }

    private static List<String> classes(List<Entry> list) {
        List<String> classes = new ArrayList<>();
        for (Entry entry : list) {
            if (!classes.contains(entry.frame())) {
                classes.add(entry.frame());
            }
        }
        return classes;
    }

    public int revision() {
        this.refresh();
        return this.revision;
    }

    public List<Ranked> ranked(boolean assisted) {
        this.refresh();
        List<Entry> list = assisted ? this.assisted : this.entries;
        List<Ranked> result = new ArrayList<>(list.size());
        Map<String, Integer> classes = new HashMap<>();
        int overall = 0;
        for (Entry entry : list) {
            int classRank = classes.merge(entry.frame(), 1, Integer::sum);
            result.add(new Ranked(entry, classRank, ++overall));
        }
        return result;
    }

    public enum Section {
        ALL,
        CLEAN,
        ASSISTED
    }

    public List<Entry> removeTimes(UUID player, @Nullable String frame, Section section) {
        this.refresh();
        Scoring rules = this.current();
        Section wanted = section == null ? Section.ALL : section;
        List<Entry> removed = new ArrayList<>();
        this.stored.removeIf(entry -> {
            if (!entry.player().equals(player) || frame != null && !entry.frame().equals(frame)) {
                return false;
            }
            boolean clean = rules.clean(entry);
            boolean hit = wanted == Section.ALL || (wanted == Section.CLEAN) == clean;
            if (hit) {
                removed.add(entry);
            }
            return hit;
        });
        if (!removed.isEmpty()) {
            this.rebuild();
        }
        return removed;
    }

    public List<String> pilotNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry entry : this.stored) {
            if (!entry.name().isEmpty()) {
                names.add(entry.name());
            }
        }
        return List.copyOf(names);
    }

    public @Nullable UUID playerNamed(String name) {
        for (Entry entry : this.stored) {
            if (entry.name().equalsIgnoreCase(name)) {
                return entry.player();
            }
        }
        return null;
    }

    public int removePlayer(String name) {
        Set<UUID> removed = new HashSet<>();
        for (Entry entry : this.stored) {
            if (entry.name().equalsIgnoreCase(name)) {
                removed.add(entry.player());
            }
        }
        int before = this.stored.size();
        this.stored.removeIf(entry -> removed.contains(entry.player()));
        this.storedRaces.removeIf(race -> removed.contains(race.player()));
        for (UUID player : removed) {
            this.laps.remove(player);
        }
        this.rebuild();
        return before - this.stored.size();
    }

    public int clear() {
        int removed = this.stored.size();
        this.stored.clear();
        this.storedRaces.clear();
        this.laps.clear();
        this.rebuild();
        return removed;
    }

    public void importLegacy(UUID player, String name, long bestLap, long dateMillis, int lapCount, Map<String, Long> raceTotals) {
        this.refresh();
        if (bestLap > 0L && this.best(player, "") == null) {
            this.insert(new Entry(player, name, "", bestLap, dateMillis));
            this.rebuild();
        }
        if (lapCount > 0) {
            this.laps.merge(player, lapCount, Math::max);
        }
        if (raceTotals != null) {
            raceTotals.forEach((key, total) -> {
                try {
                    int count = Integer.parseInt(key);
                    if (total != null) {
                        this.submitRace(player, count, total);
                    }
                } catch (NumberFormatException ignored) {
                }
            });
        }
    }
}
