package de.lardtom.propwash.share;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.platform.ModMeta;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ShareTables {
    public static final String PROPWASH = "propwash";
    public static final int PROPWASH_SHORT_ID = 1;
    public static final String TABLE_RESOURCE = "/data/propwash/propwash_share/table.json";
    private static final int MAX_CHAIN = 8;

    public interface PartLookup {
        PartLookup NONE = id -> Optional.empty();

        Optional<PartKind> kind(Identifier id);

        default boolean active(Identifier id) {
            return true;
        }
    }

    public static final PartLookup REGISTERED = new PartLookup() {
        @Override
        public Optional<PartKind> kind(Identifier id) {
            return PartIndex.registeredById(id).map(part -> part.definition().kind());
        }

        @Override
        public boolean active(Identifier id) {
            return PartAvailability.active(id);
        }
    };

    private static volatile ShareTables current;
    private static volatile ShareTable propwashTable;

    private final Map<String, ShareTable> installed;
    private final AddonDirectory directory;
    private final Map<String, String> versions;
    private final Map<String, ModMeta> mods;
    private final PartLookup parts;
    private final List<String> problems;
    private final boolean frozen;
    private final Map<Integer, String> namespaceOfShort = new HashMap<>();
    private final Map<String, Integer> shortOfNamespace = new HashMap<>();

    private ShareTables(Builder builder) {
        this.installed = Map.copyOf(builder.installed);
        this.directory = builder.directory;
        this.versions = Map.copyOf(builder.versions);
        this.mods = Map.copyOf(builder.mods);
        this.parts = builder.parts;
        this.frozen = builder.frozen;
        List<String> problems = new ArrayList<>(builder.problems);
        problems.addAll(this.directory.problems());
        this.resolveShortIds(problems);
        this.problems = List.copyOf(problems);
    }

    private void resolveShortIds(List<String> problems) {
        this.namespaceOfShort.put(PROPWASH_SHORT_ID, PROPWASH);
        this.shortOfNamespace.put(PROPWASH, PROPWASH_SHORT_ID);
        for (AddonDirectory.Addon addon : this.directory.addons()) {
            String owner = this.namespaceOfShort.get(addon.shortId());
            if (owner != null && !owner.equals(addon.namespace())) {
                problems.add("Verzeichnis: Kurzkennung " + addon.shortId() + " doppelt (" + owner + ", " + addon.namespace() + ")");
                continue;
            }
            if (this.shortOfNamespace.containsKey(addon.namespace()) && this.shortOfNamespace.get(addon.namespace()) != addon.shortId()) {
                problems.add("Verzeichnis: " + addon.namespace() + " hat zwei Kurzkennungen");
                continue;
            }
            this.namespaceOfShort.put(addon.shortId(), addon.namespace());
            this.shortOfNamespace.put(addon.namespace(), addon.shortId());
        }
        Map<Integer, List<String>> claims = new TreeMap<>();
        for (ShareTable table : this.installed.values()) {
            if (table.shortId().isEmpty()) {
                continue;
            }
            int claimed = table.shortId().getAsInt();
            Integer known = this.shortOfNamespace.get(table.namespace());
            if (known != null) {
                if (known != claimed) {
                    problems.add("Tabelle " + table.namespace() + ": Kurzkennung " + claimed + " widerspricht dem Verzeichnis-Abzug (" + known + "), der Abzug gilt");
                }
                continue;
            }
            String owner = this.namespaceOfShort.get(claimed);
            if (owner != null) {
                problems.add("Tabelle " + table.namespace() + ": Kurzkennung " + claimed + " gehört laut Verzeichnis-Abzug zu " + owner + ", wird nicht benutzt");
                continue;
            }
            claims.computeIfAbsent(claimed, key -> new ArrayList<>()).add(table.namespace());
        }
        for (Map.Entry<Integer, List<String>> claim : claims.entrySet()) {
            if (claim.getValue().size() == 1) {
                this.namespaceOfShort.put(claim.getKey(), claim.getValue().get(0));
                this.shortOfNamespace.put(claim.getValue().get(0), claim.getKey());
            } else {
                problems.add("Kurzkennung " + claim.getKey() + " von mehreren Add-ons beansprucht " + new TreeSet<>(claim.getValue()) + ", voller Namensraum");
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ShareTables current() {
        ShareTables value = current;
        if (value == null) {
            value = builtin();
            current = value;
        }
        return value;
    }

    public static void install(ShareTables tables) {
        current = Objects.requireNonNull(tables, "tables");
    }

    public static ShareTables builtin() {
        return builder().installed(propwashTable()).directory(AddonDirectory.builtin()).parts(REGISTERED).build();
    }

    private static volatile ShareTables frozenTables;

    public static ShareTables frozen() {
        ShareTables value = frozenTables;
        if (value == null) {
            value = buildFrozen();
            frozenTables = value;
        }
        return value;
    }

    private static ShareTables buildFrozen() {
        Builder builder = builder().installed(propwashTable()).directory(AddonDirectory.builtin()).parts(PartLookup.NONE).frozen(true);
        for (AddonDirectory.Addon addon : AddonDirectory.builtin().addons()) {
            if (!addon.namespace().equals(PROPWASH)) {
                addon.table().ifPresent(builder::installed);
            }
        }
        return builder.build();
    }

    public static ShareTable propwashTable() {
        ShareTable value = propwashTable;
        if (value == null) {
            value = readPropwashTable();
            propwashTable = value;
        }
        return value;
    }

    private static ShareTable readPropwashTable() {
        try (InputStream in = ShareTables.class.getResourceAsStream(TABLE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Teile-Tabelle von Propwash fehlt: " + TABLE_RESOURCE);
            }
            ShareTableJson.Parsed parsed = ShareTableJson.parse(PROPWASH, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return parsed.table().orElseThrow(() -> new IllegalStateException("Teile-Tabelle von Propwash ungültig: " + parsed.problems()));
        } catch (IOException e) {
            throw new IllegalStateException("Teile-Tabelle von Propwash nicht lesbar", e);
        }
    }

    public Map<String, ShareTable> installedTables() {
        return this.installed;
    }

    public Optional<ShareTable> installedTable(String namespace) {
        return Optional.ofNullable(this.installed.get(namespace));
    }

    public Optional<ShareTable> table(String namespace) {
        ShareTable table = this.installed.get(namespace);
        if (table != null) {
            return Optional.of(table);
        }
        return this.directory.byNamespace(namespace).flatMap(AddonDirectory.Addon::table);
    }

    public boolean installed(String namespace) {
        return PROPWASH.equals(namespace) || this.installed.containsKey(namespace) || this.versions.containsKey(namespace);
    }

    public Optional<String> version(String namespace) {
        return Optional.ofNullable(this.versions.get(namespace));
    }

    public Optional<ModMeta> mod(String namespace) {
        return Optional.ofNullable(this.mods.get(namespace));
    }

    public AddonDirectory directory() {
        return this.directory;
    }

    public PartLookup parts() {
        return this.parts;
    }

    public List<String> problems() {
        return this.problems;
    }

    public boolean isFrozen() {
        return this.frozen;
    }

    public Optional<String> namespaceOfShort(int shortId) {
        return Optional.ofNullable(this.namespaceOfShort.get(shortId));
    }

    public int shortIdOf(String namespace) {
        return this.shortOfNamespace.getOrDefault(namespace, 0);
    }

    public boolean registered(Identifier id) {
        try {
            return this.parts.kind(id).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Optional<ShareTable.Located> entry(Identifier id) {
        return this.table(id.getNamespace()).flatMap(table -> table.find(id));
    }

    public Optional<Identifier> fallback(Identifier id) {
        return this.entry(id).flatMap(located -> located.entry().fallback());
    }

    public Optional<Map<String, String>> partNames(Identifier id) {
        return this.entry(id).map(located -> located.entry().names()).filter(names -> !names.isEmpty());
    }

    public Identifier replacement(Identifier id) {
        Identifier current = id;
        Set<Identifier> seen = new LinkedHashSet<>();
        for (int i = 0; i < MAX_CHAIN && seen.add(current); i++) {
            if (this.registered(current)) {
                return current;
            }
            Optional<ShareTable.Located> located = this.entry(current);
            if (located.isEmpty() || located.get().entry().deprecated().isEmpty() || located.get().entry().replacedBy().isEmpty()) {
                return current;
            }
            current = located.get().entry().replacedBy().get();
        }
        return current;
    }

    public static ShareTables load(Collection<String> modIds, Loader loader, PartLookup parts, Collection<Identifier> registered) {
        Builder builder = builder().parts(parts);
        List<String> problems = new ArrayList<>();
        Map<String, ShareTable> tables = new TreeMap<>();
        for (String modId : new TreeSet<>(modIds)) {
            Map<String, byte[]> files;
            try {
                files = loader.tables(modId);
            } catch (RuntimeException e) {
                problems.add(modId + ": Tabellen nicht lesbar: " + e.getMessage());
                continue;
            }
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                String path = file.getKey();
                String[] segments = path.split("/");
                if (segments.length != 4 || !segments[0].equals("data") || !segments[2].equals(ShareTableJson.FOLDER)
                        || !segments[3].equals(ShareTableJson.TABLE_FILE)) {
                    continue;
                }
                String folder = segments[1];
                if (folder.equals(PROPWASH) && !modId.equals(PROPWASH)) {
                    problems.add("FEHLER " + modId + ": Tabelle für den reservierten Namensraum propwash verworfen (" + path + ")");
                    continue;
                }
                if (!folder.equals(modId)) {
                    problems.add(modId + ": Tabelle " + path + " passt nicht zur Mod-Id und zählt nicht");
                    continue;
                }
                ShareTableJson.Parsed parsed = ShareTableJson.parse(folder, file.getValue());
                for (String problem : parsed.problems()) {
                    problems.add(modId + ": " + problem);
                }
                parsed.table().ifPresent(table -> tables.put(table.namespace(), table));
            }
            loader.version(modId).ifPresent(version -> builder.version(modId, version));
            loader.meta(modId).ifPresent(meta -> builder.mod(modId, meta));
        }
        if (!tables.containsKey(PROPWASH)) {
            tables.put(PROPWASH, propwashTable());
        }
        tables.values().forEach(builder::installed);
        AddonDirectory directory = loader.directory();
        problems.addAll(coverage(tables, registered, directory));
        return builder.directory(directory).problems(problems).build();
    }

    public static List<String> coverage(Map<String, ShareTable> tables, Collection<Identifier> registered) {
        return coverage(tables, registered, AddonDirectory.EMPTY);
    }

    public static List<String> coverage(Map<String, ShareTable> tables, Collection<Identifier> registered, AddonDirectory directory) {
        List<String> problems = new ArrayList<>();
        Map<String, List<Identifier>> byNamespace = new TreeMap<>();
        for (Identifier id : registered) {
            byNamespace.computeIfAbsent(id.getNamespace(), key -> new ArrayList<>()).add(id);
        }
        for (Map.Entry<String, List<Identifier>> entry : byNamespace.entrySet()) {
            ShareTable table = tables.get(entry.getKey());
            if (table == null) {
                Optional<ShareTable> listed = directory.byNamespace(entry.getKey()).flatMap(AddonDirectory.Addon::table);
                if (listed.isEmpty()) {
                    problems.add(entry.getKey() + ": Add-on mit " + entry.getValue().size() + " Teilen ohne Teile-Tabelle, alle Teile gehen als Text");
                    continue;
                }
                problems.add(entry.getKey() + ": Add-on ohne eigene Teile-Tabelle, Sharecodes nutzen die Tabelle aus dem Verzeichnis-Abzug (Version aktualisieren)");
                table = listed.get();
            }
            List<String> missing = new ArrayList<>();
            for (Identifier id : entry.getValue()) {
                if (table.find(id).isEmpty()) {
                    missing.add(id.getPath());
                }
            }
            if (!missing.isEmpty()) {
                problems.add(entry.getKey() + ": Teile ohne Eintrag in der Tabelle gehen als Pfad: " + missing);
            }
        }
        Set<Identifier> known = new LinkedHashSet<>(registered);
        for (ShareTable table : tables.values()) {
            List<String> orphans = new ArrayList<>();
            for (PartKind kind : Pw3Params.KINDS) {
                for (ShareTable.Entry entry : table.entries(kind)) {
                    if (entry.deprecated().isEmpty() && !known.contains(table.id(entry))) {
                        orphans.add(kind.key() + "/" + entry.id());
                    }
                }
            }
            if (!orphans.isEmpty()) {
                problems.add(table.namespace() + ": Einträge ohne angemeldetes Teil und ohne deprecated: " + orphans);
            }
        }
        return problems;
    }

    public static List<Identifier> registeredParts() {
        List<Identifier> out = new ArrayList<>();
        for (PartInfo part : PartIndex.registered()) {
            out.add(part.id());
        }
        return out;
    }

    public interface Loader {
        Map<String, byte[]> tables(String modId);

        Optional<String> version(String modId);

        Optional<ModMeta> meta(String modId);

        AddonDirectory directory();
    }

    public static final class Builder {
        private final Map<String, ShareTable> installed = new LinkedHashMap<>();
        private final Map<String, String> versions = new LinkedHashMap<>();
        private final Map<String, ModMeta> mods = new LinkedHashMap<>();
        private final List<String> problems = new ArrayList<>();
        private AddonDirectory directory = AddonDirectory.EMPTY;
        private PartLookup parts = PartLookup.NONE;
        private boolean frozen;

        private Builder() {
        }

        public Builder installed(ShareTable table) {
            this.installed.put(table.namespace(), table);
            return this;
        }

        public Builder version(String namespace, String version) {
            this.versions.put(namespace, version);
            return this;
        }

        public Builder mod(String namespace, ModMeta meta) {
            this.mods.put(namespace, meta);
            return this;
        }

        public Builder directory(AddonDirectory value) {
            this.directory = Objects.requireNonNull(value, "directory");
            return this;
        }

        public Builder parts(PartLookup value) {
            this.parts = Objects.requireNonNull(value, "parts");
            return this;
        }

        public Builder frozen(boolean value) {
            this.frozen = value;
            return this;
        }

        public Builder problems(Collection<String> values) {
            this.problems.addAll(values);
            return this;
        }

        public ShareTables build() {
            return new ShareTables(this);
        }
    }
}
