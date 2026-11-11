package de.lardtom.propwash.race.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModelSymmetry {
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
    private static final int[][][] CORNERS = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}};
    private static final double CENTER = 8.0;

    private ModelSymmetry() {
    }

    public record Quad(String texture, double[][] vertices) {
        public String key() {
            List<String> corners = new ArrayList<>();
            for (double[] v : this.vertices) {
                corners.add(String.format(Locale.ROOT, "%.4f,%.4f,%.4f/%.4f,%.4f", v[0], v[1], v[2], v[3], v[4]));
            }
            corners.sort(String::compareTo);
            return this.texture + corners;
        }

        public Quad quarterTurn() {
            double[][] turned = new double[this.vertices.length][];
            for (int i = 0; i < this.vertices.length; i++) {
                double[] v = this.vertices[i];
                turned[i] = new double[]{2.0 * CENTER - v[2], v[1], v[0], v[3], v[4]};
            }
            return new Quad(this.texture, turned);
        }
    }

    public static List<Quad> quads(JsonObject model) {
        return quads(model, false);
    }

    public static List<Quad> quads(JsonObject model, boolean sidesOnly) {
        List<Quad> quads = new ArrayList<>();
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements == null) {
            return quads;
        }
        for (JsonElement entry : elements) {
            JsonObject element = entry.getAsJsonObject();
            if (element.has("rotation")) {
                throw new IllegalArgumentException("Element mit eigener Drehung wird nicht geprüft: " + element.get("name"));
            }
            double[] from = vector(element.getAsJsonArray("from"));
            double[] to = vector(element.getAsJsonArray("to"));
            JsonObject faces = element.getAsJsonObject("faces");
            for (int face = sidesOnly ? 2 : 0; face < FACES.length; face++) {
                JsonObject spec = faces == null ? null : faces.getAsJsonObject(FACES[face]);
                if (spec != null) {
                    quads.add(quad(face, from, to, spec));
                }
            }
        }
        return quads;
    }

    public static List<String> quarterTurnProblems(JsonObject model) {
        return quarterTurnProblems(model, false);
    }

    public static List<String> quarterTurnProblems(JsonObject model, boolean sidesOnly) {
        List<Quad> quads = quads(model, sidesOnly);
        Map<String, Integer> left = new HashMap<>();
        for (Quad quad : quads) {
            left.merge(quad.key(), 1, Integer::sum);
        }
        List<String> problems = new ArrayList<>();
        for (Quad quad : quads) {
            String turned = quad.quarterTurn().key();
            Integer count = left.get(turned);
            if (count == null || count == 0) {
                problems.add("nach einer Vierteldrehung fehlt " + turned);
            } else {
                left.put(turned, count - 1);
            }
        }
        return problems;
    }

    private static Quad quad(int face, double[] from, double[] to, JsonObject spec) {
        double[] uv = spec.has("uv") ? vector(spec.getAsJsonArray("uv")) : defaultUv(face, from, to);
        int shift = Math.floorMod(spec.has("rotation") ? spec.get("rotation").getAsInt() / 90 : 0, 4);
        double[][] vertices = new double[4][];
        for (int i = 0; i < 4; i++) {
            int[] corner = CORNERS[face][i];
            int uvIndex = (i + shift) % 4;
            double u = uvIndex == 0 || uvIndex == 1 ? uv[0] : uv[2];
            double v = uvIndex == 0 || uvIndex == 3 ? uv[1] : uv[3];
            vertices[i] = new double[]{corner[0] == 1 ? to[0] : from[0], corner[1] == 1 ? to[1] : from[1], corner[2] == 1 ? to[2] : from[2], u, v};
        }
        String texture = spec.has("texture") ? spec.get("texture").getAsString() : "";
        return new Quad(texture, vertices);
    }

    private static double[] defaultUv(int face, double[] from, double[] to) {
        return switch (face) {
            case 0 -> new double[]{from[0], 16.0 - to[2], to[0], 16.0 - from[2]};
            case 1 -> new double[]{from[0], from[2], to[0], to[2]};
            case 2 -> new double[]{16.0 - to[0], 16.0 - to[1], 16.0 - from[0], 16.0 - from[1]};
            case 3 -> new double[]{from[0], 16.0 - to[1], to[0], 16.0 - from[1]};
            case 4 -> new double[]{from[2], 16.0 - to[1], to[2], 16.0 - from[1]};
            default -> new double[]{16.0 - to[2], 16.0 - to[1], 16.0 - from[2], 16.0 - from[1]};
        };
    }

    private static double[] vector(JsonArray array) {
        double[] out = new double[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsDouble();
        }
        return out;
    }
}
