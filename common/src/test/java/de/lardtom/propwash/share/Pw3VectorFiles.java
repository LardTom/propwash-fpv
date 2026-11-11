package de.lardtom.propwash.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class Pw3VectorFiles {
    static final String FILE = "pw3-vectors.json";

    private Pw3VectorFiles() {
    }

    record Spec(String name, String description, String encodeContext, String decodeContext, ShareContent content, String code) {
    }

    static ShareContent legacy(String name) {
        for (JsonObject vector : ShareTestData.vectors("pw2-vectors.json")) {
            if (vector.get("name").getAsString().equals(name)) {
                return ShareTestData.content(vector.getAsJsonObject("content"));
            }
        }
        throw new IllegalArgumentException(name);
    }

    static Identifier id(String text) {
        return Identifier.parse(text);
    }

    static List<Spec> specs() {
        List<Spec> out = new ArrayList<>();
        ShareContent p1 = legacy("preset/propwash:freestyle/parts");
        out.add(spec("P1_parts_propwash_freestyle", "nur Teile, Propwash-Preset Freestyle", "frozen", p1));
        BuildSpec bash = legacy("preset/justmoreparts:bash5_6s/parts").build().withAccessory(AccessorySlot.FRONT, id("propwash:action_cam_light"));
        ShareContent p2 = ShareContent.of(bash);
        out.add(spec("P2_parts_jmp_front_accessory", "nur Teile, JMP-Preset bash5_6s mit Zubehör vorne", "frozen", p2));
        out.add(spec("P3_all_layers", "Teile, Lack, BF-Tuning, Name und OSD (JMP-Preset deadcat5_6s)", "frozen", legacy("preset/justmoreparts:deadcat5_6s/all_osd")));
        BuildSpec base = p1.build();
        out.add(spec("P4_foreign_without_table", "Fremd-Add-on ohne Tabelle (Text-Namensraum und Text-Pfad) mit Info (Name, Modrinth-Slug)", "extra",
                ShareContent.of(base.withPart(PartKind.FRAME, id("otheraddon:frame_x")))));
        BuildSpec p5 = base.withPart(PartKind.FRAME, id("extraparts:x_frame_2")).withPart(PartKind.MOTOR, id("extraparts:new_motor"))
                .withAccessory(AccessorySlot.TOP, id("extraparts:x_cam"));
        out.add(spec("P5_foreign_table_without_short_id", "Fremd-Add-on mit Tabelle ohne Kurzkennung, ein Teil (x_cam) als v = 0 mit Pfad", "extra",
                ShareContent.of(p5)));
        BuildSpec p6 = base.withPart(PartKind.FRAME, id("extraparts:x_frame_3"));
        out.add(new Spec("P6_number_after_table_end", "Nummer hinter dem Tabellenende: geschrieben mit extraparts 1.2.0, gelesen mit 1.1.0; Neu-Schreiben übernimmt den Kopf",
                "extra_newer", "extra", ShareContent.of(p6), null));
        out.add(spec("P7_deprecated_replaced", "gestrichenes Teil old_motor mit replaced_by new_motor", "extra",
                ShareContent.of(base.withPart(PartKind.MOTOR, id("extraparts:old_motor")))));
        out.add(spec("P8_free_https_link", "Linkart 3 (freie Adresse, nur als Text)", "extra", ShareContent.of(base.withPart(PartKind.FRAME, id("freeaddon:fp_frame")))));
        out.add(spec("P9_version_suffix", "Versionszusatz 1.3.0-beta.1 (Add-on ohne Tabelle, CurseForge-Slug)", "extra",
                ShareContent.of(base.withPart(PartKind.MOTOR, id("betaaddon:beta_motor")))));
        return out;
    }

    private static Spec spec(String name, String description, String context, ShareContent content) {
        return new Spec(name, description, context, context, content, null);
    }

    static String swapped(String code) {
        for (int i = code.length() - 1; i > 4; i--) {
            if (code.charAt(i) != code.charAt(i - 1)) {
                String candidate = code.substring(0, i - 1) + code.charAt(i) + code.charAt(i - 1) + code.substring(i + 1);
                if (ShareFormat.decode(candidate, ShareTables.frozen()).status() == ShareStatus.CHECKSUM) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("kein Vertauschen mit CHECKSUM gefunden");
    }

    static JsonArray compute() {
        JsonArray out = new JsonArray();
        String p1 = null;
        String p2 = null;
        for (Spec spec : specs()) {
            String code = Pw3Codec.encode(spec.content(), ShareTestData.context(spec.encodeContext()));
            out.add(vector(spec.name(), spec.description(), spec.encodeContext(), spec.decodeContext(), spec.content(), code));
            if (spec.name().startsWith("P1")) {
                p1 = code;
            }
            if (spec.name().startsWith("P2")) {
                p2 = code;
            }
        }
        out.add(vector("P10_checksum_error", "P1 mit zwei vertauschten Zeichen", null, "frozen", null, swapped(p1)));
        out.add(vector("P11_bare_body", "P2 ohne Präfix (nackter Rumpf)", null, "frozen", null, p2.substring(Pw3Params.PREFIX.length())));
        int number = 0;
        String last = "";
        for (JsonObject conversion : ShareTestData.vectors(ShareConvertTest.FILE)) {
            String name = conversion.get("name").getAsString();
            if (!name.startsWith("PW2/preset/")) {
                continue;
            }
            String[] parts = name.split("/");
            if (!parts[2].equals(last)) {
                number++;
                last = parts[2];
            }
            out.add(vector(String.format("U%02d/%s/%s", number, parts[2], parts[3]), "aus " + name + " umgerechnet", null, "frozen", null,
                    conversion.get("pw3").getAsString()));
        }
        return out;
    }

    static JsonObject vector(String name, String description, String encodeContext, String decodeContext, ShareContent content, String code) {
        ShareDecoded decoded = ShareFormat.decode(code, ShareTestData.context(decodeContext));
        JsonObject out = new JsonObject();
        out.addProperty("name", name);
        out.addProperty("description", description);
        out.add("encode_context", encodeContext == null ? JsonNull.INSTANCE : new JsonPrimitive(encodeContext));
        out.addProperty("decode_context", decodeContext);
        out.add("encode_content", content == null ? JsonNull.INSTANCE : ShareTestData.content(content));
        out.addProperty("code", code);
        out.addProperty("length", code.length());
        out.addProperty("status", decoded.status().name());
        out.addProperty("format", decoded.format());
        out.add("layers", ShareTestData.layers(decoded.layers()));
        out.add("content", decoded.ok() ? ShareTestData.content(decoded.require()) : JsonNull.INSTANCE);
        out.add("skipped", ShareTestData.strings(decoded.skipped()));
        JsonArray reports = new JsonArray();
        for (NamespaceReport report : decoded.namespaces()) {
            reports.add(report(report));
        }
        out.add("namespaces", reports);
        out.add("unknown_parts", ShareTestData.strings(decoded.unknownParts().stream().map(Identifier::toString).toList()));
        JsonArray renamed = new JsonArray();
        for (ShareDecoded.Rename rename : decoded.renamed()) {
            JsonObject r = new JsonObject();
            r.addProperty("from", rename.from().toString());
            r.addProperty("to", rename.to().toString());
            renamed.add(r);
        }
        out.add("renamed", renamed);
        return out;
    }

    static JsonObject report(NamespaceReport report) {
        JsonObject out = new JsonObject();
        out.addProperty("index", report.index());
        out.add("namespace", string(report.namespace()));
        out.addProperty("short_id", report.shortId());
        out.addProperty("table", report.hasTable());
        out.add("checksum", report.checksum().isPresent() ? new JsonPrimitive(report.checksum().getAsInt()) : JsonNull.INSTANCE);
        out.add("min_version", string(report.minVersion()));
        out.add("name", string(report.codeName()));
        out.addProperty("link_type", report.linkType());
        out.add("link", string(report.link()));
        out.add("installed_version", string(report.installedVersion()));
        out.addProperty("result", report.result().name());
        out.addProperty("used", report.used());
        return out;
    }

    private static JsonElement string(Optional<String> value) {
        return value.<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE);
    }

    static JsonObject file(JsonArray vectors) {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.addProperty("code_format", Pw3Params.FORMAT);
        root.addProperty("revision", Pw3Params.REVISION);
        root.addProperty("source", "Pw3Codec (Propwash 0.6.0); Inhalte aus pw2-vectors.json, Kontexte unten");
        root.add("contexts", ShareTestData.contexts());
        root.addProperty("count", vectors.size());
        root.add("vectors", vectors);
        return root;
    }
}
