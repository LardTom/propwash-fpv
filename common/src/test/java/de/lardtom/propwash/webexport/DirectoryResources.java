package de.lardtom.propwash.webexport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.platform.ModMeta;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

final class DirectoryResources implements ModResources {
    static final String VERSION = "0.0.0-test";

    private final Map<String, Path> roots;
    private final Path langModules;

    DirectoryResources(Map<String, Path> roots) {
        this(roots, null);
    }

    private DirectoryResources(Map<String, Path> roots, Path langModules) {
        this.roots = new TreeMap<>(roots);
        this.langModules = langModules;
    }

    static DirectoryResources propwash() {
        for (Path candidate : List.of(Path.of("common/src"), Path.of("src"))) {
            if (Files.isDirectory(candidate.resolve("main/resources/assets/propwash/lang")) && Files.isDirectory(candidate.resolve("lang"))) {
                return new DirectoryResources(Map.of("propwash", candidate.resolve("main/resources")), candidate.resolve("lang"));
            }
        }
        throw new IllegalStateException("Ressourcen von Propwash nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
    }

    @Override
    public boolean loaded(String modId) {
        return this.roots.containsKey(modId);
    }

    @Override
    public SortedMap<String, byte[]> read(String modId, String folder, String suffix) {
        Path root = this.roots.get(modId);
        if (root == null) {
            throw new IllegalArgumentException("Mod " + modId + " ist nicht geladen");
        }
        SortedMap<String, byte[]> out = new TreeMap<>();
        Path start = root.resolve(folder);
        if (!Files.isDirectory(start)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(start)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                if (relative.endsWith(suffix)) {
                    out.put(relative, Files.readAllBytes(file));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (this.langModules != null && modId.equals("propwash") && folder.equals("assets") && suffix.equals(".json")) {
            for (String language : LangTable.LANGUAGES) {
                out.put("assets/propwash/lang/" + language + ".json", this.mergedLang(language));
            }
        }
        return out;
    }

    private byte[] mergedLang(String language) {
        JsonObject merged = new JsonObject();
        Map<String, JsonElement> sorted = new TreeMap<>();
        try (Stream<Path> modules = Files.list(this.langModules)) {
            for (Path module : modules.sorted().toList()) {
                Path file = module.resolve(language + ".json");
                if (Files.isRegularFile(file)) {
                    JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
                            .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        sorted.forEach(merged::add);
        return ExportJson.text(merged).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Optional<ModMeta> meta(String modId) {
        return this.loaded(modId) ? Optional.of(new ModMeta(modId, modId, VERSION, "", false)) : Optional.empty();
    }

    @Override
    public String loader() {
        return "test";
    }

    @Override
    public Optional<String> version(String modId) {
        return this.loaded(modId) ? Optional.of(VERSION) : Optional.empty();
    }
}
