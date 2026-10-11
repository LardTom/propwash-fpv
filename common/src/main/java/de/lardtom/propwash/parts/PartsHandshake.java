package de.lardtom.propwash.parts;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.network.PartsManifestPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PartsHandshake {
    public static final ConfigurationTask.Type TASK_TYPE = new ConfigurationTask.Type(PartsManifestPayload.TYPE.id().toString());
    public static final int MAX_LISTED = 8;
    public static final String TITLE_KEY = "propwash.parts.handshake.title";
    public static final String MISSING_CLIENT_KEY = "propwash.parts.handshake.missing_client";
    public static final String MISSING_SERVER_KEY = "propwash.parts.handshake.missing_server";
    public static final String DIFFERENT_KEY = "propwash.parts.handshake.different";
    public static final String BUILTIN_KEY = "propwash.parts.handshake.builtin";
    public static final String MORE_KEY = "propwash.parts.handshake.more";
    public static final String HINT_KEY = "propwash.parts.handshake.hint";
    public static final String CHEMISTRY = "chemistry";

    private static volatile PartsManifestPayload cached;

    private PartsHandshake() {
    }

    public static PartsManifestPayload localManifest() {
        PartsManifestPayload manifest = cached;
        if (manifest != null) {
            return manifest;
        }
        List<PartsManifestPayload.Entry> entries = new ArrayList<>();
        for (BatteryType.Chemistry chemistry : PartIndex.registeredChemistries()) {
            if (!PartIndex.isBuiltinChemistry(chemistry.id())) {
                entries.add(new PartsManifestPayload.Entry(CHEMISTRY, chemistry.id(), PartIndex.hashOf(chemistry.definition())));
            }
        }
        for (PartInfo part : PartIndex.registered()) {
            if (!part.builtin()) {
                entries.add(new PartsManifestPayload.Entry(part.definition().kind().key(), part.id(), PartIndex.hashOf(part.definition())));
            }
        }
        manifest = new PartsManifestPayload(PartIndex.fingerprint(), entries);
        if (PartIndex.isFrozen()) {
            cached = manifest;
        }
        return manifest;
    }

    public static Diff compare(PartsManifestPayload server, PartsManifestPayload client) {
        if (server.fingerprint().equals(client.fingerprint())) {
            return Diff.NONE;
        }
        Map<String, PartsManifestPayload.Entry> serverEntries = index(server);
        Map<String, PartsManifestPayload.Entry> clientEntries = index(client);
        List<Identifier> missingOnClient = new ArrayList<>();
        List<Identifier> different = new ArrayList<>();
        for (Map.Entry<String, PartsManifestPayload.Entry> entry : serverEntries.entrySet()) {
            PartsManifestPayload.Entry other = clientEntries.get(entry.getKey());
            if (other == null) {
                missingOnClient.add(entry.getValue().id());
            } else if (!other.hash().equals(entry.getValue().hash())) {
                different.add(entry.getValue().id());
            }
        }
        List<Identifier> missingOnServer = new ArrayList<>();
        for (Map.Entry<String, PartsManifestPayload.Entry> entry : clientEntries.entrySet()) {
            if (!serverEntries.containsKey(entry.getKey())) {
                missingOnServer.add(entry.getValue().id());
            }
        }
        boolean builtins = missingOnClient.isEmpty() && missingOnServer.isEmpty() && different.isEmpty();
        return new Diff(missingOnClient, missingOnServer, different, builtins);
    }

    private static Map<String, PartsManifestPayload.Entry> index(PartsManifestPayload manifest) {
        Map<String, PartsManifestPayload.Entry> out = new LinkedHashMap<>();
        for (PartsManifestPayload.Entry entry : manifest.entries()) {
            out.put(entry.kind() + "|" + entry.id(), entry);
        }
        return out;
    }

    public static Component message(Diff diff) {
        MutableComponent text = Component.translatableWithFallback(TITLE_KEY, "Your Propwash parts differ from the server's.");
        int[] budget = {MAX_LISTED};
        line(text, MISSING_CLIENT_KEY, "Missing on your side: %s", diff.missingOnClient(), budget);
        line(text, MISSING_SERVER_KEY, "Missing on the server: %s", diff.missingOnServer(), budget);
        line(text, DIFFERENT_KEY, "Different values: %s", diff.different(), budget);
        if (diff.builtinsDiffer()) {
            text.append("\n").append(Component.translatableWithFallback(BUILTIN_KEY,
                    "The built-in parts differ (another Propwash FPV version?)"));
        }
        text.append("\n").append(Component.translatableWithFallback(HINT_KEY,
                "Install the same Propwash addons in the same versions as the server."));
        return text;
    }

    private static void line(MutableComponent text, String key, String fallback, List<Identifier> ids, int[] budget) {
        if (ids.isEmpty()) {
            return;
        }
        List<String> shown = new ArrayList<>();
        for (Identifier id : ids) {
            if (budget[0] <= 0) {
                break;
            }
            shown.add(id.toString());
            budget[0]--;
        }
        String listed = String.join(", ", shown);
        text.append("\n").append(Component.translatableWithFallback(key, fallback, listed));
        int hidden = ids.size() - shown.size();
        if (hidden > 0) {
            text.append(" ").append(Component.translatableWithFallback(MORE_KEY, "(+%s more)", String.valueOf(hidden)));
        }
    }

    public static void logMismatch(Diff diff) {
        Constants.LOG.info("Propwash-Bauteile passen nicht: fehlen beim Client {}, fehlen auf dem Server {}, andere Werte {}, eingebaute anders: {}",
                diff.missingOnClient(), diff.missingOnServer(), diff.different(), diff.builtinsDiffer());
    }

    public record Diff(List<Identifier> missingOnClient, List<Identifier> missingOnServer, List<Identifier> different, boolean builtinsDiffer) {
        public static final Diff NONE = new Diff(List.of(), List.of(), List.of(), false);

        public Diff {
            missingOnClient = List.copyOf(missingOnClient);
            missingOnServer = List.copyOf(missingOnServer);
            different = List.copyOf(different);
        }

        public boolean matches() {
            return this.missingOnClient.isEmpty() && this.missingOnServer.isEmpty() && this.different.isEmpty() && !this.builtinsDiffer;
        }
    }
}
