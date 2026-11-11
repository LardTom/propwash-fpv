package de.lardtom.propwash.server.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class PersonalPresets {
    public static final String RACE_LEGAL_NAME = "Race Legal";
    public static final int MAX_PRESETS = 32;
    public static final int MAX_NAME_LENGTH = 32;
    public static final Preset RACE_LEGAL = new Preset(RACE_LEGAL_NAME, Map.of(), true);

    public enum Outcome {
        SAVED,
        UPDATED,
        RENAMED,
        DELETED,
        EMPTY_NAME,
        RESERVED,
        TAKEN,
        MISSING,
        FULL
    }

    public record Preset(String name, Map<String, Integer> values, boolean raceLegal) {
        public Preset {
            name = raceLegal ? RACE_LEGAL_NAME : cleanName(name);
            values = raceLegal ? Map.of() : clean(values);
        }

        public Preset(String name, Map<String, Integer> values) {
            this(name, values, false);
        }
    }

    public record Change(Outcome outcome, List<Preset> presets, String name) {
        public Change {
            presets = List.copyOf(presets);
        }

        public boolean changed() {
            return this.outcome == Outcome.SAVED || this.outcome == Outcome.UPDATED || this.outcome == Outcome.RENAMED || this.outcome == Outcome.DELETED;
        }
    }

    public record Applied(Map<String, Integer> values, List<RuleSpec> skipped) {
        public Applied {
            values = Collections.unmodifiableMap(new TreeMap<>(values));
            skipped = List.copyOf(skipped);
        }
    }

    private PersonalPresets() {
    }

    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                space = !out.isEmpty();
                continue;
            }
            if (space) {
                out.append(' ');
                space = false;
            }
            out.append(c);
        }
        String clean = out.toString();
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH).strip() : clean;
    }

    public static boolean reserved(String name) {
        return sameName(cleanName(name), RACE_LEGAL_NAME);
    }

    public static boolean sameName(String a, String b) {
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    public static Map<String, Integer> clean(Map<String, Integer> values) {
        Map<String, Integer> out = new TreeMap<>();
        if (values != null) {
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                Optional<RuleSpec> spec = entry.getKey() == null ? Optional.empty() : WorldRuleSpecs.byName(entry.getKey());
                Integer value = entry.getValue();
                if (spec.isPresent() && RuleTiers.tierable(spec.get()) && value != null && spec.get().valid(value)) {
                    out.put(spec.get().name(), value);
                }
            }
        }
        return Collections.unmodifiableMap(out);
    }

    public static List<Preset> choices(List<Preset> own) {
        List<Preset> out = new ArrayList<>();
        out.add(RACE_LEGAL);
        out.addAll(own);
        return List.copyOf(out);
    }

    public static List<Preset> validate(List<Preset> raw) {
        List<Preset> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Preset preset : raw) {
            if (preset == null || preset.raceLegal() || preset.name().isEmpty() || reserved(preset.name()) || find(out, preset.name()).isPresent()) {
                continue;
            }
            out.add(preset);
            if (out.size() >= MAX_PRESETS) {
                break;
            }
        }
        return out;
    }

    public static Optional<Preset> find(List<Preset> presets, String name) {
        for (Preset preset : presets) {
            if (sameName(preset.name(), name)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    public static Change save(List<Preset> presets, String name, Map<String, Integer> values) {
        String clean = cleanName(name);
        if (clean.isEmpty()) {
            return new Change(Outcome.EMPTY_NAME, presets, clean);
        }
        if (reserved(clean)) {
            return new Change(Outcome.RESERVED, presets, clean);
        }
        List<Preset> out = new ArrayList<>(presets);
        Preset preset = new Preset(clean, values);
        for (int i = 0; i < out.size(); i++) {
            if (sameName(out.get(i).name(), clean)) {
                out.set(i, preset);
                return new Change(Outcome.UPDATED, out, clean);
            }
        }
        if (out.size() >= MAX_PRESETS) {
            return new Change(Outcome.FULL, presets, clean);
        }
        out.add(preset);
        return new Change(Outcome.SAVED, out, clean);
    }

    public static Change rename(List<Preset> presets, String from, String to) {
        String clean = cleanName(to);
        if (clean.isEmpty()) {
            return new Change(Outcome.EMPTY_NAME, presets, clean);
        }
        if (reserved(clean)) {
            return new Change(Outcome.RESERVED, presets, clean);
        }
        int index = -1;
        for (int i = 0; i < presets.size(); i++) {
            Preset preset = presets.get(i);
            if (preset.name().equals(from)) {
                index = i;
            } else if (sameName(preset.name(), clean)) {
                return new Change(Outcome.TAKEN, presets, clean);
            }
        }
        if (index < 0) {
            return new Change(Outcome.MISSING, presets, clean);
        }
        List<Preset> out = new ArrayList<>(presets);
        out.set(index, new Preset(clean, presets.get(index).values()));
        return new Change(Outcome.RENAMED, out, clean);
    }

    public static Change delete(List<Preset> presets, String name) {
        List<Preset> out = new ArrayList<>(presets);
        boolean removed = out.removeIf(preset -> preset.name().equals(name));
        return removed ? new Change(Outcome.DELETED, out, name) : new Change(Outcome.MISSING, presets, name);
    }

    public static String uniqueName(List<Preset> presets, String base) {
        String clean = cleanName(base);
        if (find(presets, clean).isEmpty() && !reserved(clean)) {
            return clean;
        }
        for (int n = 2; n < 1000; n++) {
            String suffix = " (" + n + ")";
            String head = clean.length() + suffix.length() > MAX_NAME_LENGTH ? clean.substring(0, Math.max(1, MAX_NAME_LENGTH - suffix.length())).strip()
                    : clean;
            String candidate = head + suffix;
            if (find(presets, candidate).isEmpty()) {
                return candidate;
            }
        }
        return clean;
    }

    public static Applied apply(Preset preset, RuleTiers tiers) {
        Map<String, Integer> values = new TreeMap<>();
        List<RuleSpec> skipped = new ArrayList<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            Integer value = preset.values().get(spec.name());
            if (value == null) {
                continue;
            }
            if (tiers.players(spec)) {
                values.put(spec.name(), value);
            } else {
                skipped.add(spec);
            }
        }
        return new Applied(values, skipped);
    }

    public static boolean matches(Preset preset, Map<String, Integer> desired, PhysicsRules server, RuleTiers tiers) {
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (!tiers.players(spec)) {
                continue;
            }
            int base = server.value(spec);
            if (preset.values().getOrDefault(spec.name(), base).intValue() != desired.getOrDefault(spec.name(), base).intValue()) {
                return false;
            }
        }
        return true;
    }
}
