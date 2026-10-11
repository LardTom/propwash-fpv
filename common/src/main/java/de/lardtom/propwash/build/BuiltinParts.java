package de.lardtom.propwash.build;

import com.google.gson.JsonObject;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.parts.PartDataException;
import de.lardtom.propwash.parts.PartDataProblems;
import de.lardtom.propwash.parts.PartJson;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class BuiltinParts {
    public static final String ROOT = "data/" + Constants.MOD_ID + "/propwash_parts/";

    private BuiltinParts() {
    }

    public static String resource(String folder, String path) {
        return ROOT + folder + "/" + path + ".json";
    }

    static <D extends PartDefinition> D load(PartKind kind, String path, Class<D> type) {
        Identifier id = Constants.id(path);
        String resource = resource(PartJson.folder(kind), path);
        PartDataProblems problems = new PartDataProblems(resource);
        JsonObject json = PartJson.parse(read(resource), problems);
        PartDefinition definition = json == null ? null : PartJson.decode(kind, id, json, problems);
        if (definition == null || !type.isInstance(definition)) {
            throw new PartDataException("Eingebautes Bauteil " + id + " ist fehlerhaft", problems.errorLines());
        }
        warn(problems);
        return type.cast(definition);
    }

    static CellChemistry chemistry(String path) {
        Identifier id = Constants.id(path);
        String resource = resource(PartJson.CHEMISTRY, path);
        PartDataProblems problems = new PartDataProblems(resource);
        JsonObject json = PartJson.parse(read(resource), problems);
        CellChemistry chemistry = json == null ? null : PartJson.decodeChemistry(id, json, problems);
        if (chemistry == null) {
            throw new PartDataException("Eingebaute Zellchemie " + id + " ist fehlerhaft", problems.errorLines());
        }
        warn(problems);
        return chemistry;
    }

    private static void warn(PartDataProblems problems) {
        for (String warning : problems.warningLines()) {
            Constants.LOG.warn("Propwash-Bauteildaten: {}", warning);
        }
    }

    private static String read(String resource) {
        InputStream in = BuiltinParts.class.getResourceAsStream("/" + resource);
        if (in == null) {
            ClassLoader loader = BuiltinParts.class.getClassLoader();
            in = loader == null ? null : loader.getResourceAsStream(resource);
        }
        if (in == null) {
            throw new IllegalStateException("Eingebaute Bauteil-Datei fehlt: " + resource + " (Mod-Datei unvollständig)");
        }
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Eingebaute Bauteil-Datei nicht lesbar: " + resource, e);
        }
    }
}
