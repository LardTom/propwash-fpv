package de.lardtom.propwash.creative.client;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.creative.CreativeShareCode;
import de.lardtom.propwash.creative.CreativeSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CreativeTemplates {
    public static final int MAX_TEMPLATES = 64;
    public static final int MAX_NAME_LENGTH = 32;
    public static final JsonConfig<CreativeTemplates> CONFIG = JsonConfig.create("creative_templates", CreativeTemplates.class,
            CreativeTemplates::new, CreativeTemplates::validate);

    public List<Entry> templates = new ArrayList<>();

    public static final class Entry {
        public String name = "";
        public String code = "";

        public Entry() {
        }

        public Entry(String name, String code) {
            this.name = name;
            this.code = code;
        }

        public Optional<CreativeSpec> spec() {
            return CreativeShareCode.decode(this.code);
        }
    }

    public static CreativeTemplates get() {
        if (!CONFIG.isLoaded()) {
            CONFIG.load();
        }
        return CONFIG.get();
    }

    public static List<Entry> all() {
        return List.copyOf(get().templates);
    }

    public static String cleanName(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) : clean;
    }

    public static Entry save(String name, CreativeSpec spec) {
        CreativeTemplates config = get();
        String clean = cleanName(name);
        Entry entry = new Entry(clean, CreativeShareCode.encode(spec));
        config.templates.removeIf(existing -> existing.name.toLowerCase(Locale.ROOT).equals(clean.toLowerCase(Locale.ROOT)));
        config.templates.add(entry);
        while (config.templates.size() > MAX_TEMPLATES) {
            config.templates.removeFirst();
        }
        CONFIG.save();
        return entry;
    }

    public static boolean delete(String name) {
        CreativeTemplates config = get();
        boolean removed = config.templates.removeIf(existing -> existing.name.equals(name));
        if (removed) {
            CONFIG.save();
        }
        return removed;
    }

    public static Optional<Entry> byName(String name) {
        for (Entry entry : get().templates) {
            if (entry.name.equals(name)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static void validate(CreativeTemplates config) {
        List<Entry> clean = new ArrayList<>();
        if (config.templates != null) {
            for (Entry entry : config.templates) {
                if (entry == null || entry.code == null || CreativeShareCode.decode(entry.code).isEmpty()) {
                    continue;
                }
                String name = cleanName(entry.name);
                if (name.isEmpty() || clean.stream().anyMatch(other -> other.name.equals(name))) {
                    continue;
                }
                clean.add(new Entry(name, entry.code.strip()));
                if (clean.size() >= MAX_TEMPLATES) {
                    break;
                }
            }
        }
        config.templates = clean;
    }
}
