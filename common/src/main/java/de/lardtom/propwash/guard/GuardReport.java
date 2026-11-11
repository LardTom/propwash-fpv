package de.lardtom.propwash.guard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

public final class GuardReport {
    public static final int MAX_IDS = 20;
    public static final String TITLE = "Propwash: reserved namespace propwash";
    private final Map<String, EnumMap<GuardCategory, TreeSet<String>>> findings = new TreeMap<>();
    private final Map<String, TreeSet<String>> mixins = new TreeMap<>();
    private final Map<String, String> names = new TreeMap<>();
    private final Set<Finding> logged = new HashSet<>();
    private final Set<String> loggedMixins = new HashSet<>();

    public record Finding(String mod, GuardCategory category, String id) {
        public Finding {
            Objects.requireNonNull(mod, "mod");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(id, "id");
        }
    }

    public synchronized boolean add(String mod, GuardCategory category, String id) {
        return this.findings.computeIfAbsent(mod, key -> new EnumMap<>(GuardCategory.class))
                .computeIfAbsent(category, key -> new TreeSet<>()).add(id);
    }

    public synchronized void mixins(String mod, Collection<String> targets) {
        this.mixins.computeIfAbsent(mod, key -> new TreeSet<>()).addAll(targets);
    }

    public synchronized void name(String mod, String name) {
        this.names.put(mod, name);
    }

    public synchronized Optional<String> name(String mod) {
        return Optional.ofNullable(this.names.get(mod));
    }

    public synchronized int count() {
        return this.count(category -> true);
    }

    public synchronized int count(Predicate<GuardCategory> filter) {
        int count = 0;
        for (EnumMap<GuardCategory, TreeSet<String>> categories : this.findings.values()) {
            for (Map.Entry<GuardCategory, TreeSet<String>> entry : categories.entrySet()) {
                if (filter.test(entry.getKey())) {
                    count += entry.getValue().size();
                }
            }
        }
        return count;
    }

    public synchronized List<String> mods() {
        return this.mods(category -> true);
    }

    public synchronized List<String> mods(Predicate<GuardCategory> filter) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, EnumMap<GuardCategory, TreeSet<String>>> entry : this.findings.entrySet()) {
            if (entry.getValue().keySet().stream().anyMatch(filter)) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    public synchronized SortedMap<GuardCategory, List<String>> categories(String mod) {
        SortedMap<GuardCategory, List<String>> out = new TreeMap<>();
        EnumMap<GuardCategory, TreeSet<String>> categories = this.findings.get(mod);
        if (categories != null) {
            categories.forEach((category, ids) -> out.put(category, List.copyOf(ids)));
        }
        return out;
    }

    public synchronized List<String> ids(GuardCategory category) {
        TreeSet<String> out = new TreeSet<>();
        for (EnumMap<GuardCategory, TreeSet<String>> categories : this.findings.values()) {
            TreeSet<String> ids = categories.get(category);
            if (ids != null) {
                out.addAll(ids);
            }
        }
        return List.copyOf(out);
    }

    public synchronized SortedMap<String, List<String>> mixins() {
        SortedMap<String, List<String>> out = new TreeMap<>();
        this.mixins.forEach((mod, targets) -> out.put(mod, List.copyOf(targets)));
        return out;
    }

    public synchronized List<String> drainLogLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, EnumMap<GuardCategory, TreeSet<String>>> mod : this.findings.entrySet()) {
            for (Map.Entry<GuardCategory, TreeSet<String>> category : mod.getValue().entrySet()) {
                List<String> fresh = new ArrayList<>();
                for (String id : category.getValue()) {
                    if (this.logged.add(new Finding(mod.getKey(), category.getKey(), id))) {
                        fresh.add(id);
                    }
                }
                if (!fresh.isEmpty()) {
                    lines.add(line(mod.getKey(), category.getKey(), fresh));
                }
            }
        }
        return lines;
    }

    public synchronized List<String> drainMixinLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, TreeSet<String>> entry : this.mixins.entrySet()) {
            String key = entry.getKey() + " " + entry.getValue().size();
            if (this.loggedMixins.add(key)) {
                lines.add("Mod '" + entry.getKey() + "' has mixins into " + entry.getValue().size() + " Propwash classes (allowed, at own risk): "
                        + capped(new ArrayList<>(entry.getValue())));
            }
        }
        return lines;
    }

    public static String line(String mod, GuardCategory category, List<String> ids) {
        return "Mod '" + mod + "' uses the reserved namespace propwash (" + category.logName() + ", " + ids.size() + "): " + capped(ids);
    }

    public static String capped(List<String> ids) {
        if (ids.size() <= MAX_IDS) {
            return String.join(", ", ids);
        }
        return String.join(", ", ids.subList(0, MAX_IDS)) + " and " + (ids.size() - MAX_IDS) + " more";
    }

    public synchronized String text(Map<String, String> names) {
        StringBuilder out = new StringBuilder(TITLE).append('\n');
        if (this.findings.isEmpty()) {
            out.append("No add-on uses the reserved namespace.\n");
        }
        for (Map.Entry<String, EnumMap<GuardCategory, TreeSet<String>>> mod : this.findings.entrySet()) {
            String name = names.getOrDefault(mod.getKey(), mod.getKey());
            out.append('\n').append(name.equals(mod.getKey()) ? name : name + " (" + mod.getKey() + ")").append('\n');
            for (Map.Entry<GuardCategory, TreeSet<String>> category : mod.getValue().entrySet()) {
                out.append("  ").append(category.getKey().logName()).append(" (").append(category.getValue().size()).append("):\n");
                for (String id : category.getValue()) {
                    out.append("    ").append(id).append('\n');
                }
            }
        }
        if (!this.mixins.isEmpty()) {
            out.append("\nMixins into Propwash internals (allowed, at own risk)\n");
            for (Map.Entry<String, TreeSet<String>> entry : this.mixins.entrySet()) {
                out.append("  ").append(names.getOrDefault(entry.getKey(), entry.getKey())).append(": ").append(entry.getValue().size())
                        .append(" classes\n");
            }
        }
        return out.toString();
    }

    public synchronized Map<String, Integer> summary() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String mod : this.findings.keySet()) {
            int count = 0;
            for (TreeSet<String> ids : this.findings.get(mod).values()) {
                count += ids.size();
            }
            out.put(mod, count);
        }
        return out;
    }
}
