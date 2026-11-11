package de.lardtom.propwash.webexport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

final class ExportDiff {
    static final String TIMESTAMP = "generated_at";

    private ExportDiff() {
    }

    static SortedSet<String> files(Path root) throws IOException {
        SortedSet<String> out = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(file -> out.add(root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/")));
        }
        return out;
    }

    static List<String> differences(Path a, Path b) throws IOException {
        List<String> out = new ArrayList<>();
        SortedSet<String> left = files(a);
        SortedSet<String> right = files(b);
        for (String file : left) {
            if (!right.contains(file)) {
                out.add("nur im ersten Export: " + file);
            }
        }
        for (String file : right) {
            if (!left.contains(file)) {
                out.add("nur im zweiten Export: " + file);
            }
        }
        for (String file : left) {
            if (!right.contains(file)) {
                continue;
            }
            byte[] x = Files.readAllBytes(a.resolve(file));
            byte[] y = Files.readAllBytes(b.resolve(file));
            if (file.equals("manifest.json")) {
                JsonObject one = JsonParser.parseString(new String(x, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject two = JsonParser.parseString(new String(y, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!one.has(TIMESTAMP) || !two.has(TIMESTAMP)) {
                    out.add("manifest.json ohne " + TIMESTAMP);
                }
                one.remove(TIMESTAMP);
                two.remove(TIMESTAMP);
                if (!ExportJson.text(one).equals(ExportJson.text(two))) {
                    out.add("manifest.json weicht ab (außer " + TIMESTAMP + ")");
                }
                String rest = new String(x, StandardCharsets.UTF_8).replaceAll("\"" + TIMESTAMP + "\": \"[^\"]*\"", "");
                String restTwo = new String(y, StandardCharsets.UTF_8).replaceAll("\"" + TIMESTAMP + "\": \"[^\"]*\"", "");
                if (!rest.equals(restTwo)) {
                    out.add("manifest.json weicht in den Bytes ab (außer " + TIMESTAMP + ")");
                }
            } else if (!Arrays.equals(x, y)) {
                out.add("Bytes weichen ab: " + file);
            }
        }
        return out;
    }
}
