package de.lardtom.propwash.server.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.server.rules.PersonalPresets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class PersonalPresetStore {
    public static final JsonConfig<PersonalPresetStore> CONFIG = JsonConfig.create("personal_presets", PersonalPresetStore.class,
            PersonalPresetStore::new, PersonalPresetStore::validate);

    public List<Stored> presets = new ArrayList<>();

    public static final class Stored {
        public String name = "";
        public Map<String, Integer> values = new TreeMap<>();

        public Stored() {
        }

        Stored(PersonalPresets.Preset preset) {
            this.name = preset.name();
            this.values = new TreeMap<>(preset.values());
        }
    }

    public static PersonalPresetStore get() {
        if (!CONFIG.isLoaded()) {
            CONFIG.load();
        }
        return CONFIG.get();
    }

    public static List<PersonalPresets.Preset> all() {
        List<PersonalPresets.Preset> out = new ArrayList<>();
        for (Stored stored : get().list()) {
            out.add(new PersonalPresets.Preset(stored.name, stored.values));
        }
        return List.copyOf(out);
    }

    public static PersonalPresets.Change commit(PersonalPresets.Change change) {
        if (change.changed()) {
            PersonalPresetStore store = get();
            store.presets = stored(change.presets());
            CONFIG.save();
        }
        return change;
    }

    public static void replace(List<PersonalPresets.Preset> presets) {
        PersonalPresetStore store = get();
        store.presets = stored(PersonalPresets.validate(presets));
        CONFIG.save();
    }

    private List<Stored> list() {
        return this.presets == null ? List.of() : this.presets;
    }

    private static List<Stored> stored(List<PersonalPresets.Preset> presets) {
        List<Stored> out = new ArrayList<>();
        for (PersonalPresets.Preset preset : presets) {
            out.add(new Stored(preset));
        }
        return out;
    }

    private static void validate(PersonalPresetStore store) {
        List<PersonalPresets.Preset> raw = new ArrayList<>();
        if (store.presets != null) {
            for (Stored stored : store.presets) {
                if (stored != null && stored.name != null) {
                    Map<String, Integer> values = new TreeMap<>();
                    if (stored.values != null) {
                        stored.values.forEach((key, value) -> {
                            if (key != null && value != null) {
                                values.put(key, value);
                            }
                        });
                    }
                    raw.add(new PersonalPresets.Preset(stored.name, values));
                }
            }
        }
        store.presets = stored(PersonalPresets.validate(raw));
    }
}
