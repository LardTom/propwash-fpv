package de.lardtom.propwash.parts;

import com.google.gson.JsonObject;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.join.LocalJoinInfo;
import de.lardtom.propwash.platform.Services;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public final class BundledParts {
    public static final String FOLDER = "propwash_parts";
    public static final String CHEMISTRY_FOLDER = PartJson.CHEMISTRY;
    public static final int FORMAT = PartJson.FORMAT;
    public static final List<String> ORDER = List.of(CHEMISTRY_FOLDER, "frame", "stack", "motor", "prop", "video", "battery", "accessory");

    private BundledParts() {
    }

    public static int load(String modId) {
        SortedMap<String, byte[]> files = Services.PLATFORM.readModResources(modId, "data", ".json");
        return load(modId, files, true);
    }

    public static int load(String modId, Map<String, byte[]> files, boolean register) {
        List<Entry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String path = file.getKey();
            String[] segments = path.split("/");
            if (segments.length < 4 || !"data".equals(segments[0]) || !FOLDER.equals(segments[2]) || !path.endsWith(".json")) {
                continue;
            }
            String namespace = segments[1];
            if (Constants.MOD_ID.equals(modId) && PartIndex.DEFAULT_NAMESPACE.equals(namespace)) {
                continue;
            }
            if (segments.length != 5) {
                errors.add(path + " – Bauteil-Dateien liegen direkt in data/<namensraum>/" + FOLDER + "/<kategorie>/, ohne Unterordner");
                continue;
            }
            String category = segments[3];
            int order = ORDER.indexOf(category);
            if (order < 0) {
                errors.add(path + " – unbekannte Kategorie \"" + category + "\", erlaubt: " + String.join(", ", ORDER));
                continue;
            }
            String name = segments[4].substring(0, segments[4].length() - ".json".length());
            Identifier id = Identifier.tryBuild(namespace, name);
            if (id == null) {
                errors.add(path + " – ungültiger Dateiname für eine Id: " + namespace + ":" + name);
                continue;
            }
            entries.add(new Entry(order, id, path, category, new String(file.getValue(), StandardCharsets.UTF_8)));
        }
        entries.sort(Comparator.comparingInt(Entry::order).thenComparing(Entry::path));
        List<Decoded> decoded = new ArrayList<>();
        for (Entry entry : entries) {
            PartDataProblems problems = new PartDataProblems(entry.path());
            JsonObject json = PartJson.parse(entry.text(), problems);
            if (json != null) {
                if (entry.order() == 0) {
                    CellChemistry chemistry = PartJson.decodeChemistry(entry.id(), json, problems);
                    if (chemistry != null) {
                        decoded.add(new Decoded(entry, chemistry, null));
                    }
                } else {
                    PartKind kind = PartKind.byKey(entry.category()).orElseThrow();
                    PartDefinition definition = PartJson.decode(kind, entry.id(), json, problems);
                    if (definition != null) {
                        decoded.add(new Decoded(entry, null, definition));
                    }
                }
            }
            errors.addAll(problems.errorLines());
            warnings.addAll(problems.warningLines());
        }
        for (String warning : warnings) {
            Constants.LOG.warn("Propwash-Bauteildaten von {}: {}", modId, warning);
        }
        if (!errors.isEmpty()) {
            throw new PartDataException("Bauteil-Dateien von " + modId + " sind fehlerhaft (" + errors.size() + " Fehler)", errors);
        }
        if (!register) {
            return decoded.size();
        }
        for (Entry entry : entries) {
            LocalJoinInfo.noteBundled(modId, entry.id().getNamespace());
        }
        int count = 0;
        for (Decoded item : decoded) {
            try {
                if (item.chemistry() != null) {
                    PartRegistryImpl.INSTANCE.registerChemistry(item.chemistry());
                } else {
                    PartRegistryImpl.INSTANCE.register(item.definition());
                }
                count++;
            } catch (IllegalArgumentException | IllegalStateException e) {
                errors.add(item.entry().path() + " – " + PartJson.rootMessage(e));
            }
        }
        if (!errors.isEmpty()) {
            throw new PartDataException("Bauteile von " + modId + " ließen sich nicht anmelden", errors);
        }
        Constants.LOG.info("Propwash: {} Bauteil-Definitionen aus {} geladen", count, modId);
        return count;
    }

    private record Entry(int order, Identifier id, String path, String category, String text) {
    }

    private record Decoded(Entry entry, CellChemistry chemistry, PartDefinition definition) {
    }
}
