package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

public record RaceRuleSet(List<Seen> seen) {
    public static final int WORLD = 0;
    public static final int MAX_SEEN = 96;
    public static final int MAX_RULE = 128;
    public static final String OVERFLOW = "propwash:too_many_rules";
    public static final Comparator<Seen> ORDER = Comparator.comparing(Seen::rule).thenComparing(Seen::free).thenComparingInt(Seen::zone)
            .thenComparingInt(Seen::value);
    public static final RaceRuleSet DEFAULTS = new RaceRuleSet(List.of());

    public record Seen(String rule, int value, int zone, boolean free) {
        public static final Codec<Seen> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("rule").forGetter(Seen::rule),
                Codec.INT.fieldOf("value").forGetter(Seen::value),
                Codec.INT.optionalFieldOf("zone", WORLD).forGetter(Seen::zone),
                Codec.BOOL.optionalFieldOf("free", false).forGetter(Seen::free)
        ).apply(i, Seen::new));

        public Seen {
            rule = key(rule);
            zone = Math.max(WORLD, zone);
            free = free && zone != WORLD;
        }

        public boolean world() {
            return this.zone == WORLD;
        }

        public boolean counts() {
            return !this.free;
        }
    }

    public static final Codec<RaceRuleSet> CODEC = Seen.CODEC.listOf().xmap(RaceRuleSet::new, RaceRuleSet::seen);

    public RaceRuleSet {
        seen = normalize(seen == null ? List.of() : seen);
    }

    public static String key(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(id.length(), MAX_RULE));
        String lower = id.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length() && out.length() < MAX_RULE; i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == ':' || c == '.' || c == '-' || c == '/') {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static RaceRuleSet of(Collection<Seen> seen) {
        return seen.isEmpty() ? DEFAULTS : new RaceRuleSet(List.copyOf(seen));
    }

    private static List<Seen> normalize(List<Seen> input) {
        if (input.isEmpty()) {
            return List.of();
        }
        TreeSet<Seen> sorted = new TreeSet<>(ORDER);
        for (Seen entry : input) {
            if (entry != null && !entry.rule().isEmpty()) {
                sorted.add(entry);
            }
        }
        Map<String, List<Seen>> grouped = new LinkedHashMap<>();
        for (Seen entry : sorted) {
            grouped.computeIfAbsent(entry.rule(), key -> new ArrayList<>()).add(entry);
        }
        List<Seen> out = new ArrayList<>();
        boolean overflow = grouped.containsKey(OVERFLOW);
        grouped.remove(OVERFLOW);
        for (Map.Entry<String, List<Seen>> group : grouped.entrySet()) {
            List<Seen> values = group.getValue();
            if (values.size() == 1 && plainDefault(values.getFirst())) {
                continue;
            }
            if (out.size() + values.size() > MAX_SEEN - 1) {
                overflow = true;
                break;
            }
            out.addAll(values);
        }
        if (overflow) {
            out.add(new Seen(OVERFLOW, 1, WORLD, false));
        }
        return List.copyOf(out);
    }

    private static boolean plainDefault(Seen seen) {
        if (!seen.world() || seen.free()) {
            return false;
        }
        Optional<RaceRules.Rule> rule = RaceRules.find(seen.rule());
        return rule.isPresent() && rule.get().defaultValue() == seen.value();
    }

    public boolean isDefault() {
        return this.seen.isEmpty();
    }

    public boolean overflow() {
        return !this.seen.isEmpty() && this.seen.getLast().rule().equals(OVERFLOW);
    }

    public List<Seen> seen(RaceRules.Rule rule) {
        List<Seen> out = new ArrayList<>();
        String key = key(rule.id());
        for (Seen entry : this.seen) {
            if (entry.rule().equals(key)) {
                out.add(entry);
            }
        }
        if (out.isEmpty()) {
            out.add(new Seen(rule.id(), rule.defaultValue(), WORLD, false));
        }
        return out;
    }

    public List<String> rules() {
        List<String> out = new ArrayList<>();
        for (Seen entry : this.seen) {
            if (!out.contains(entry.rule()) && !entry.rule().equals(OVERFLOW)) {
                out.add(entry.rule());
            }
        }
        return out;
    }

    public RaceRuleSet union(RaceRuleSet other) {
        if (this.equals(other) || other.isDefault() && this.isDefault()) {
            return this;
        }
        List<Seen> merged = new ArrayList<>(this.seen);
        merged.addAll(other.seen);
        List<String> mine = this.rules();
        List<String> theirs = other.rules();
        for (String rule : mine) {
            if (!theirs.contains(rule)) {
                implicit(rule, merged);
            }
        }
        for (String rule : theirs) {
            if (!mine.contains(rule)) {
                implicit(rule, merged);
            }
        }
        return new RaceRuleSet(merged);
    }

    private static void implicit(String rule, List<Seen> into) {
        RaceRules.find(rule).ifPresent(known -> into.add(new Seen(known.id(), known.defaultValue(), WORLD, false)));
    }

    public boolean matches(RaceStandard standard) {
        if (this.overflow()) {
            return false;
        }
        for (RaceRules.Rule rule : RaceRules.relevant()) {
            int wanted = standard.value(rule);
            for (Seen entry : this.seen(rule)) {
                if (entry.counts() && entry.value() != wanted) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<AssistDetail> mismatches(RaceStandard standard) {
        List<AssistDetail> out = new ArrayList<>();
        if (this.overflow()) {
            out.add(new AssistDetail(LapAssist.WORLD_PHYSICS, OVERFLOW, 1, 0));
        }
        for (RaceRules.Rule rule : RaceRules.relevant()) {
            int wanted = standard.value(rule);
            for (Seen entry : this.seen(rule)) {
                if (!entry.counts() || entry.value() == wanted) {
                    continue;
                }
                out.add(entry.world() ? new AssistDetail(LapAssist.WORLD_PHYSICS, rule.subject(), entry.value(), wanted)
                        : new AssistDetail(LapAssist.ZONE_PHYSICS, rule.subject(), entry.value(), entry.zone()));
            }
        }
        return out;
    }

    public List<AssistDetail> personalMismatches(RaceStandard server) {
        return this.personalMismatches(server, false);
    }

    private List<AssistDetail> personalMismatches(RaceStandard server, boolean namedOnly) {
        List<AssistDetail> out = new ArrayList<>();
        if (this.overflow()) {
            out.add(new AssistDetail(LapAssist.WORLD_PHYSICS, OVERFLOW, 1, 0));
        }
        List<String> named = namedOnly ? this.rules() : List.of();
        for (RaceRules.Rule rule : RaceRules.relevant()) {
            if (namedOnly && !named.contains(key(rule.id()))) {
                continue;
            }
            int wanted = server.value(rule);
            for (Seen entry : this.seen(rule)) {
                if (!entry.counts() || entry.value() == wanted) {
                    continue;
                }
                out.add(entry.world() ? new AssistDetail(LapAssist.PERSONAL_RULES, rule.subject(), entry.value(), wanted)
                        : new AssistDetail(LapAssist.ZONE_PHYSICS, rule.subject(), entry.value(), entry.zone()));
            }
        }
        return out;
    }

    public AssistReasons personalReasons(RaceStandard server) {
        List<AssistDetail> details = this.personalMismatches(server);
        return details.isEmpty() ? AssistReasons.NONE : AssistReasons.of(details);
    }

    public AssistReasons namedPersonalReasons(RaceStandard server) {
        List<AssistDetail> details = this.personalMismatches(server, true);
        return details.isEmpty() ? AssistReasons.NONE : AssistReasons.of(details);
    }

    public AssistReasons reasons(RaceStandard standard) {
        List<AssistDetail> details = this.mismatches(standard);
        return details.isEmpty() ? AssistReasons.NONE : AssistReasons.of(details);
    }

    public static Split split(AssistReasons reasons) {
        if (reasons.none()) {
            return new Split(AssistReasons.NONE, DEFAULTS);
        }
        int physicsBits = LapAssist.WORLD_PHYSICS.bit() | LapAssist.ZONE_PHYSICS.bit();
        List<Seen> seen = new ArrayList<>();
        List<AssistDetail> kept = new ArrayList<>();
        int mask = reasons.mask() & ~physicsBits;
        for (AssistDetail detail : reasons.details()) {
            Optional<RaceRules.Rule> rule = detail.reason().in(physicsBits) ? RaceRules.find(detail.subject()) : Optional.empty();
            if (rule.isEmpty()) {
                kept.add(detail);
                mask |= detail.reason().bit();
            } else if (rule.get().relevant()) {
                boolean zone = detail.reason() == LapAssist.ZONE_PHYSICS;
                seen.add(new Seen(rule.get().id(), detail.value(), zone ? Math.max(1, detail.reference()) : WORLD, false));
            }
        }
        for (LapAssist physics : List.of(LapAssist.WORLD_PHYSICS, LapAssist.ZONE_PHYSICS)) {
            if (reasons.has(physics) && reasons.details(physics).isEmpty()) {
                mask |= physics.bit();
            }
        }
        AssistReasons fixed = mask == LapAssist.NONE ? AssistReasons.NONE : AssistReasons.of(mask, kept);
        return new Split(fixed, of(seen));
    }

    public record Split(AssistReasons fixed, RaceRuleSet rules) {
    }
}
