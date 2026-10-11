package de.lardtom.propwash.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TunePresetBook {
    public enum Outcome {
        SAVED,
        UPDATED,
        RENAMED,
        DELETED,
        IMPORTED,
        EXISTS,
        EMPTY_NAME,
        TAKEN,
        MISSING,
        FULL
    }

    public record Change(Outcome outcome, List<TunePresets.Preset> presets, String name) {
        public boolean changed() {
            return this.outcome == Outcome.SAVED || this.outcome == Outcome.UPDATED || this.outcome == Outcome.RENAMED
                    || this.outcome == Outcome.DELETED || this.outcome == Outcome.IMPORTED;
        }
    }

    private TunePresetBook() {
    }

    public static List<TunePresets.Preset> validate(TunePresets.Section section, List<TunePresets.Preset> raw) {
        List<TunePresets.Preset> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (TunePresets.Preset preset : raw) {
            if (preset == null) {
                continue;
            }
            String name = TunePresets.cleanName(preset.name());
            if (name.isEmpty() || find(out, name).isPresent()) {
                continue;
            }
            out.add(new TunePresets.Preset(name, TunePresets.clean(section, preset.values())));
            if (out.size() >= TunePresets.MAX_PER_SECTION) {
                break;
            }
        }
        return out;
    }

    public static Optional<TunePresets.Preset> find(List<TunePresets.Preset> presets, String name) {
        for (TunePresets.Preset preset : presets) {
            if (TunePresets.sameName(preset.name(), name)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    public static Change save(TunePresets.Section section, List<TunePresets.Preset> presets, String name, Map<String, Float> values) {
        String clean = TunePresets.cleanName(name);
        if (clean.isEmpty()) {
            return new Change(Outcome.EMPTY_NAME, presets, clean);
        }
        List<TunePresets.Preset> out = new ArrayList<>(presets);
        TunePresets.Preset preset = new TunePresets.Preset(clean, TunePresets.clean(section, values));
        for (int i = 0; i < out.size(); i++) {
            if (TunePresets.sameName(out.get(i).name(), clean)) {
                out.set(i, preset);
                return new Change(Outcome.UPDATED, List.copyOf(out), clean);
            }
        }
        if (out.size() >= TunePresets.MAX_PER_SECTION) {
            return new Change(Outcome.FULL, presets, clean);
        }
        out.add(preset);
        return new Change(Outcome.SAVED, List.copyOf(out), clean);
    }

    public static Change rename(List<TunePresets.Preset> presets, String from, String to) {
        String clean = TunePresets.cleanName(to);
        if (clean.isEmpty()) {
            return new Change(Outcome.EMPTY_NAME, presets, clean);
        }
        int index = -1;
        for (int i = 0; i < presets.size(); i++) {
            TunePresets.Preset preset = presets.get(i);
            if (preset.name().equals(from)) {
                index = i;
            } else if (TunePresets.sameName(preset.name(), clean)) {
                return new Change(Outcome.TAKEN, presets, clean);
            }
        }
        if (index < 0) {
            return new Change(Outcome.MISSING, presets, clean);
        }
        List<TunePresets.Preset> out = new ArrayList<>(presets);
        out.set(index, new TunePresets.Preset(clean, presets.get(index).values()));
        return new Change(Outcome.RENAMED, List.copyOf(out), clean);
    }

    public static Change delete(List<TunePresets.Preset> presets, String name) {
        List<TunePresets.Preset> out = new ArrayList<>(presets);
        boolean removed = out.removeIf(preset -> preset.name().equals(name));
        return removed ? new Change(Outcome.DELETED, List.copyOf(out), name) : new Change(Outcome.MISSING, presets, name);
    }

    public static Change importPreset(TunePresets.Section section, List<TunePresets.Preset> presets, String name, String fallback,
                                      Map<String, Float> values) {
        String base = TunePresets.cleanName(name).isEmpty() ? TunePresets.cleanName(fallback) : TunePresets.cleanName(name);
        if (base.isEmpty()) {
            return new Change(Outcome.EMPTY_NAME, presets, base);
        }
        Map<String, Float> clean = TunePresets.clean(section, values);
        Optional<TunePresets.Preset> same = find(presets, base);
        if (same.isPresent() && same.get().values().equals(clean)) {
            return new Change(Outcome.EXISTS, presets, same.get().name());
        }
        if (presets.size() >= TunePresets.MAX_PER_SECTION) {
            return new Change(Outcome.FULL, presets, base);
        }
        String unique = uniqueName(presets, base);
        List<TunePresets.Preset> out = new ArrayList<>(presets);
        out.add(new TunePresets.Preset(unique, clean));
        return new Change(Outcome.IMPORTED, List.copyOf(out), unique);
    }

    public static String uniqueName(List<TunePresets.Preset> presets, String base) {
        String clean = TunePresets.cleanName(base);
        if (find(presets, clean).isEmpty()) {
            return clean;
        }
        for (int n = 2; n < 1000; n++) {
            String suffix = " (" + n + ")";
            String head = clean.length() + suffix.length() > TunePresets.MAX_NAME_LENGTH
                    ? clean.substring(0, Math.max(1, TunePresets.MAX_NAME_LENGTH - suffix.length())).strip() : clean;
            String candidate = head + suffix;
            if (find(presets, candidate).isEmpty()) {
                return candidate;
            }
        }
        return clean;
    }
}
