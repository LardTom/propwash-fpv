package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class Leaderboard {
    public static final int TOP = 10;
    public static final int MAX_PER_CLASS = 50;
    public static final int MAX_NAME = 32;
    public static final int MAX_FRAME = 32;
    public static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::nanos).thenComparingLong(Entry::dateMillis)
            .thenComparing(Entry::name);

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> assisted = new ArrayList<>();
    private final Map<UUID, Integer> laps = new HashMap<>();
    private final Map<UUID, Map<Integer, Long>> races = new HashMap<>();
    private final Map<UUID, Map<Integer, AssistedRace>> assistedRaces = new HashMap<>();

    public record Entry(UUID player, String name, String frame, long nanos, long dateMillis, int assist, List<AssistDetail> details) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player),
                Codec.STRING.fieldOf("name").forGetter(Entry::name),
                Codec.STRING.optionalFieldOf("frame", "").forGetter(Entry::frame),
                Codec.LONG.fieldOf("nanos").forGetter(Entry::nanos),
                Codec.LONG.optionalFieldOf("date", 0L).forGetter(Entry::dateMillis),
                Codec.INT.optionalFieldOf("assist", LapAssist.NONE).forGetter(Entry::assist),
                AssistDetail.CODEC.listOf().optionalFieldOf("assist_details", List.of()).forGetter(Entry::details)
        ).apply(i, Entry::new));

        public Entry {
            name = clip(name, MAX_NAME);
            frame = clip(frame, MAX_FRAME);
            assist = assist & LapAssist.ALL;
            details = AssistReasons.of(assist, details).details();
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

    private record RaceBest(UUID player, int laps, long nanos) {
        static final Codec<RaceBest> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(RaceBest::player),
                Codec.INT.fieldOf("laps").forGetter(RaceBest::laps),
                Codec.LONG.fieldOf("nanos").forGetter(RaceBest::nanos)
        ).apply(i, RaceBest::new));
    }

    private record AssistedRaceBest(UUID player, int laps, long nanos, int assist, List<AssistDetail> details) {
        static final Codec<AssistedRaceBest> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(AssistedRaceBest::player),
                Codec.INT.fieldOf("laps").forGetter(AssistedRaceBest::laps),
                Codec.LONG.fieldOf("nanos").forGetter(AssistedRaceBest::nanos),
                Codec.INT.optionalFieldOf("assist", LapAssist.NONE).forGetter(AssistedRaceBest::assist),
                AssistDetail.CODEC.listOf().optionalFieldOf("assist_details", List.of()).forGetter(AssistedRaceBest::details)
        ).apply(i, AssistedRaceBest::new));
    }

    public static final Codec<Leaderboard> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(Leaderboard::entries),
            Laps.CODEC.listOf().optionalFieldOf("laps", List.of()).forGetter(Leaderboard::lapList),
            RaceBest.CODEC.listOf().optionalFieldOf("races", List.of()).forGetter(Leaderboard::raceList),
            Entry.CODEC.listOf().optionalFieldOf("assisted", List.of()).forGetter(Leaderboard::assistedEntries),
            AssistedRaceBest.CODEC.listOf().optionalFieldOf("assisted_races", List.of()).forGetter(Leaderboard::assistedRaceList)
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

    private static Leaderboard of(List<Entry> entries, List<Laps> laps, List<RaceBest> races, List<Entry> assisted,
                                  List<AssistedRaceBest> assistedRaces) {
        Leaderboard board = new Leaderboard();
        for (Entry entry : entries) {
            if (entry.nanos() > 0L) {
                board.put(entry);
            }
        }
        for (Entry entry : assisted) {
            if (entry.nanos() > 0L) {
                board.putAssisted(entry);
            }
        }
        for (AssistedRaceBest race : assistedRaces) {
            if (race.laps() > 0 && race.nanos() > 0L) {
                board.assistedRaces.computeIfAbsent(race.player(), p -> new TreeMap<>()).put(race.laps(), new AssistedRace(race.nanos(),
                        race.assist(), race.details()));
            }
        }
        board.assisted.sort(ORDER);
        for (Laps lap : laps) {
            board.laps.put(lap.player(), Math.max(0, lap.count()));
        }
        for (RaceBest race : races) {
            if (race.laps() > 0 && race.nanos() > 0L) {
                board.races.computeIfAbsent(race.player(), p -> new TreeMap<>()).put(race.laps(), race.nanos());
            }
        }
        board.entries.sort(ORDER);
        return board;
    }

    private static String clip(@Nullable String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    public List<Entry> entries() {
        return List.copyOf(this.entries);
    }

    private List<Laps> lapList() {
        List<Laps> list = new ArrayList<>();
        this.laps.forEach((player, count) -> list.add(new Laps(player, count)));
        return list;
    }

    public List<Entry> assistedEntries() {
        return List.copyOf(this.assisted);
    }

    private List<AssistedRaceBest> assistedRaceList() {
        List<AssistedRaceBest> list = new ArrayList<>();
        this.assistedRaces.forEach((player, map) -> map.forEach((count, race) -> list.add(new AssistedRaceBest(player, count, race.nanos(),
                race.assist(), race.details()))));
        return list;
    }

    private List<RaceBest> raceList() {
        List<RaceBest> list = new ArrayList<>();
        this.races.forEach((player, map) -> map.forEach((count, nanos) -> list.add(new RaceBest(player, count, nanos))));
        return list;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty() && this.laps.isEmpty() && this.races.isEmpty() && this.assisted.isEmpty() && this.assistedRaces.isEmpty();
    }

    public int size() {
        return this.entries.size();
    }

    public int assistedSize() {
        return this.assisted.size();
    }

    public int times() {
        return this.entries.size() + this.assisted.size();
    }

    private void put(Entry entry) {
        Entry plain = entry.assisted() ? new Entry(entry.player(), entry.name(), entry.frame(), entry.nanos(), entry.dateMillis()) : entry;
        this.entries.removeIf(e -> e.player().equals(plain.player()) && e.frame().equals(plain.frame()));
        this.entries.add(plain);
    }

    private void putAssisted(Entry entry) {
        Entry marked = entry.assisted() ? entry
                : new Entry(entry.player(), entry.name(), entry.frame(), entry.nanos(), entry.dateMillis(), LapAssist.OTHER.bit());
        this.assisted.removeIf(e -> e.player().equals(marked.player()) && e.frame().equals(marked.frame()));
        this.assisted.add(marked);
    }

    public Outcome submit(UUID player, String name, String frame, long nanos, long dateMillis) {
        if (nanos <= 0L) {
            return Outcome.NONE;
        }
        this.laps.merge(player, 1, Integer::sum);
        String cls = clip(frame, MAX_FRAME);
        Optional<Entry> courseRecord = this.record(null);
        Optional<Entry> classRecord = this.record(cls);
        Entry own = this.best(player, cls);
        this.rename(player, name);
        if (own != null && own.nanos() <= nanos) {
            return Outcome.NONE;
        }
        this.put(new Entry(player, name, cls, nanos, dateMillis));
        this.entries.sort(ORDER);
        this.trim(cls);
        if (courseRecord.isEmpty() || nanos < courseRecord.get().nanos()) {
            return Outcome.COURSE_RECORD;
        }
        if (classRecord.isEmpty() || nanos < classRecord.get().nanos()) {
            return Outcome.CLASS_RECORD;
        }
        return Outcome.PERSONAL_BEST;
    }

    public boolean submitAssisted(UUID player, String name, String frame, long nanos, long dateMillis, int assist) {
        return this.submitAssisted(player, name, frame, nanos, dateMillis, AssistReasons.of(assist));
    }

    public boolean submitAssisted(UUID player, String name, String frame, long nanos, long dateMillis, AssistReasons assist) {
        if (nanos <= 0L) {
            return false;
        }
        String cls = clip(frame, MAX_FRAME);
        Entry own = this.bestAssisted(player, cls);
        this.rename(player, name);
        if (own != null && own.nanos() <= nanos) {
            return false;
        }
        AssistReasons reasons = assist.orOther();
        this.putAssisted(new Entry(player, name, cls, nanos, dateMillis, reasons.mask(), reasons.details()));
        this.assisted.sort(ORDER);
        trim(this.assisted, cls);
        return true;
    }

    private void rename(UUID player, String name) {
        rename(this.entries, player, name);
        rename(this.assisted, player, name);
    }

    private static void rename(List<Entry> list, UUID player, String name) {
        for (int i = 0; i < list.size(); i++) {
            Entry entry = list.get(i);
            if (entry.player().equals(player) && !entry.name().equals(clip(name, MAX_NAME))) {
                list.set(i, new Entry(player, name, entry.frame(), entry.nanos(), entry.dateMillis(), entry.assist(), entry.details()));
            }
        }
    }

    private void trim(String frame) {
        trim(this.entries, frame);
    }

    private static void trim(List<Entry> list, String frame) {
        int kept = 0;
        for (int i = 0; i < list.size(); i++) {
            if (!list.get(i).frame().equals(frame)) {
                continue;
            }
            if (++kept > MAX_PER_CLASS) {
                list.remove(i--);
            }
        }
    }

    public boolean submitRace(UUID player, int laps, long totalNanos) {
        if (laps <= 0 || totalNanos <= 0L) {
            return false;
        }
        Map<Integer, Long> map = this.races.computeIfAbsent(player, p -> new TreeMap<>());
        Long best = map.get(laps);
        if (best == null || totalNanos < best) {
            map.put(laps, totalNanos);
            return true;
        }
        return false;
    }

    public boolean submitAssistedRace(UUID player, int laps, long totalNanos, int assist) {
        return this.submitAssistedRace(player, laps, totalNanos, AssistReasons.of(assist));
    }

    public boolean submitAssistedRace(UUID player, int laps, long totalNanos, AssistReasons assist) {
        if (laps <= 0 || totalNanos <= 0L) {
            return false;
        }
        Map<Integer, AssistedRace> map = this.assistedRaces.computeIfAbsent(player, p -> new TreeMap<>());
        AssistedRace best = map.get(laps);
        if (best == null || totalNanos < best.nanos()) {
            AssistReasons reasons = assist.orOther();
            map.put(laps, new AssistedRace(totalNanos, reasons.mask(), reasons.details()));
            return true;
        }
        return false;
    }

    public @Nullable AssistedRace assistedRaceBest(UUID player, int laps) {
        Map<Integer, AssistedRace> map = this.assistedRaces.get(player);
        return map == null ? null : map.get(laps);
    }

    public long raceBest(UUID player, int laps) {
        Map<Integer, Long> map = this.races.get(player);
        Long best = map == null ? null : map.get(laps);
        return best == null ? LapTracker.NONE : best;
    }

    public int lapsOf(UUID player) {
        return this.laps.getOrDefault(player, 0);
    }

    public List<Entry> top(@Nullable String frame, int limit) {
        return top(this.entries, frame, limit);
    }

    public List<Entry> assistedTop(@Nullable String frame, int limit) {
        return top(this.assisted, frame, limit);
    }

    private static List<Entry> top(List<Entry> list, @Nullable String frame, int limit) {
        List<Entry> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Entry entry : list) {
            if (result.size() >= limit) {
                break;
            }
            if (frame != null ? entry.frame().equals(frame) : seen.add(entry.player())) {
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
        return best(this.entries, player, frame);
    }

    public @Nullable Entry bestAssisted(UUID player, @Nullable String frame) {
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
        return rank(this.entries, player, frame);
    }

    public int assistedRank(UUID player, @Nullable String frame) {
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

    public List<String> classes() {
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

    public List<Entry> view(@Nullable UUID viewer, int limit) {
        return view(this.entries, viewer, limit);
    }

    public List<Entry> assistedView(@Nullable UUID viewer, int limit) {
        return view(this.assisted, viewer, limit);
    }

    private static List<Entry> view(List<Entry> list, @Nullable UUID viewer, int limit) {
        List<Entry> result = new ArrayList<>();
        for (String frame : classes(list)) {
            result.addAll(top(list, frame, limit));
        }
        if (viewer != null) {
            for (Entry entry : list) {
                if (entry.player().equals(viewer) && !result.contains(entry)) {
                    result.add(entry);
                }
            }
        }
        result.sort(ORDER);
        return result;
    }

    public int removePlayer(String name) {
        int before = this.entries.size() + this.assisted.size();
        Set<UUID> removed = new HashSet<>();
        removeNamed(this.entries, name, removed);
        removeNamed(this.assisted, name, removed);
        for (UUID player : removed) {
            this.laps.remove(player);
            this.races.remove(player);
            this.assistedRaces.remove(player);
        }
        return before - this.entries.size() - this.assisted.size();
    }

    private static void removeNamed(List<Entry> list, String name, Set<UUID> removed) {
        list.removeIf(entry -> {
            boolean match = entry.name().equalsIgnoreCase(name);
            if (match) {
                removed.add(entry.player());
            }
            return match;
        });
    }

    public int clear() {
        int removed = this.entries.size() + this.assisted.size();
        this.entries.clear();
        this.assisted.clear();
        this.laps.clear();
        this.races.clear();
        this.assistedRaces.clear();
        return removed;
    }

    public void importLegacy(UUID player, String name, long bestLap, long dateMillis, int lapCount, Map<String, Long> raceTotals) {
        if (bestLap > 0L && this.best(player, "") == null) {
            this.put(new Entry(player, name, "", bestLap, dateMillis));
            this.entries.sort(ORDER);
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
