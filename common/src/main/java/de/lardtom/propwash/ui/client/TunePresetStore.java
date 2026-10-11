package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.ui.TunePresetBook;
import de.lardtom.propwash.ui.TunePresets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class TunePresetStore {
    public static final JsonConfig<TunePresetStore> CONFIG = JsonConfig.create("tune_presets", TunePresetStore.class, TunePresetStore::new,
            TunePresetStore::validate);

    public List<Stored> rates = new ArrayList<>();
    public List<Stored> pid = new ArrayList<>();

    public static final class Stored {
        public String name = "";
        public Map<String, Float> values = new TreeMap<>();

        public Stored() {
        }

        Stored(TunePresets.Preset preset) {
            this.name = preset.name();
            this.values = new TreeMap<>(preset.values());
        }
    }

    public static TunePresetStore get() {
        if (!CONFIG.isLoaded()) {
            CONFIG.load();
        }
        return CONFIG.get();
    }

    public static List<TunePresets.Preset> all(TunePresets.Section section) {
        List<TunePresets.Preset> out = new ArrayList<>();
        for (Stored stored : get().list(section)) {
            out.add(new TunePresets.Preset(stored.name, stored.values));
        }
        return out;
    }

    public static TunePresetBook.Change commit(TunePresets.Section section, TunePresetBook.Change change) {
        if (change.changed()) {
            TunePresetStore store = get();
            List<Stored> list = new ArrayList<>();
            for (TunePresets.Preset preset : change.presets()) {
                list.add(new Stored(preset));
            }
            if (section == TunePresets.Section.RATES) {
                store.rates = list;
            } else {
                store.pid = list;
            }
            CONFIG.save();
        }
        return change;
    }

    private List<Stored> list(TunePresets.Section section) {
        List<Stored> list = section == TunePresets.Section.RATES ? this.rates : this.pid;
        return list == null ? List.of() : list;
    }

    private static void validate(TunePresetStore store) {
        store.rates = clean(TunePresets.Section.RATES, store.rates);
        store.pid = clean(TunePresets.Section.PID, store.pid);
    }

    private static List<Stored> clean(TunePresets.Section section, List<Stored> raw) {
        List<TunePresets.Preset> presets = new ArrayList<>();
        if (raw != null) {
            for (Stored stored : raw) {
                if (stored != null && stored.name != null) {
                    presets.add(new TunePresets.Preset(stored.name, stored.values == null ? Map.of() : withoutNulls(stored.values)));
                }
            }
        }
        List<Stored> out = new ArrayList<>();
        for (TunePresets.Preset preset : TunePresetBook.validate(section, presets)) {
            out.add(new Stored(preset));
        }
        return out;
    }

    private static Map<String, Float> withoutNulls(Map<String, Float> values) {
        TreeMap<String, Float> out = new TreeMap<>();
        for (Map.Entry<String, Float> e : values.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }
}
