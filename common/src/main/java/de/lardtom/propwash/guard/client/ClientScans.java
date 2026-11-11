package de.lardtom.propwash.guard.client;

import com.google.gson.stream.JsonReader;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.guard.GuardCategory;
import de.lardtom.propwash.guard.GuardReport;
import de.lardtom.propwash.guard.KeyClassifier;
import de.lardtom.propwash.guard.NamespaceGuard;
import de.lardtom.propwash.platform.services.ModOrigins;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ClientScans {
    public static final String OWN_LANG = "assets/propwash/lang/en_us.json";
    private static volatile Set<String> ownKeys;

    private ClientScans() {
    }

    public static void keyMappings(GuardReport report, Minecraft minecraft) {
        Set<String> own = new HashSet<>();
        for (KeyMapping mapping : ClientRegistrar.keyMappings()) {
            own.add(mapping.getName());
        }
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            String name = mapping.getName();
            if (!name.startsWith(KeyMappingOrigins.PREFIX) || own.contains(name)) {
                continue;
            }
            Optional<String> mod = KeyMappingOrigins.get(name);
            if (!NamespaceGuard.own(mod)) {
                report.add(NamespaceGuard.modOrUnknown(mod), GuardCategory.KEY_MAPPING, name);
            }
        }
    }

    public static void translations(GuardReport report, String language) {
        Set<String> own = ownKeys();
        Set<String> files = new HashSet<>(List.of("en_us.json", language + ".json"));
        ModOrigins origins = NamespaceGuard.origins();
        for (String mod : origins.modIds()) {
            if (NamespaceGuard.own(mod) || origins.platform(mod)) {
                continue;
            }
            origins.visitFiles(mod, "assets", (path, opener) -> {
                String[] parts = path.split("/");
                if (parts.length != 4 || !parts[2].equals("lang") || parts[1].equals(KeyClassifier.NAMESPACE) || !files.contains(parts[3])) {
                    return;
                }
                try (Reader reader = new InputStreamReader(opener.open(), StandardCharsets.UTF_8); JsonReader json = new JsonReader(reader)) {
                    json.beginObject();
                    while (json.hasNext()) {
                        String key = json.nextName();
                        json.skipValue();
                        if (KeyClassifier.classify(key, own) == KeyClassifier.Result.FINDING) {
                            report.add(mod, GuardCategory.TRANSLATION, key);
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    Constants.LOG.warn("Namespace check: {} of mod '{}' not readable ({})", path, mod, e.toString());
                }
            });
        }
    }

    public static Set<String> ownKeys() {
        Set<String> known = ownKeys;
        if (known != null) {
            return known;
        }
        Set<String> keys = new HashSet<>();
        ModOrigins origins = NamespaceGuard.origins();
        for (String mod : NamespaceGuard.OWN_MODS) {
            origins.visitFiles(mod, "assets/propwash/lang", (path, opener) -> {
                if (!path.equals(OWN_LANG)) {
                    return;
                }
                try (Reader reader = new InputStreamReader(opener.open(), StandardCharsets.UTF_8); JsonReader json = new JsonReader(reader)) {
                    json.beginObject();
                    while (json.hasNext()) {
                        keys.add(json.nextName());
                        json.skipValue();
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(path + ": " + e, e);
                }
            });
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException(OWN_LANG + " not found");
        }
        ownKeys = Set.copyOf(keys);
        return ownKeys;
    }
}
