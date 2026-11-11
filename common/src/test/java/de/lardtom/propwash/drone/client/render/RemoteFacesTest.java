package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class RemoteFacesTest {
    private static final double EPS = 1.0E-3;
    private static final List<RemoteGimbalRenderer.Controls> CONTROLS = List.of(
            RemoteGimbalRenderer.Controls.NEUTRAL,
            new RemoteGimbalRenderer.Controls(0.0F, 0.0F, 0.0F, 0.0F, 0.5F, 0.5F, 0.5F),
            new RemoteGimbalRenderer.Controls(0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F));
    private static final float[][] STICKS = {
            {0.0F, -1.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F, 0.0F}, {1.0F, 1.0F, -1.0F, -1.0F}, {-1.0F, -1.0F, 1.0F, 1.0F},
            {1.0F, 0.0F, 0.0F, 1.0F}, {0.0F, 1.0F, -1.0F, 0.0F}};

    private record Face(String owner, int axis, int sign, double plane, List<double[]> outline) {
        String describe() {
            StringBuilder text = new StringBuilder(this.owner + " " + "xyz".charAt(this.axis) + (this.sign > 0 ? "+" : "-") + "=" + this.plane);
            for (double[] point : this.outline) {
                text.append(String.format(Locale.ROOT, " (%.3f, %.3f)", point[0], point[1]));
            }
            return text.toString();
        }
    }

    private static Path models() {
        for (Path candidate : List.of(Path.of("common/src/main/resources"), Path.of("src/main/resources"))) {
            Path dir = candidate.resolve("assets/propwash/models/item");
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        fail("Ressourcen nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static List<Face> jsonFaces(String model) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(models().resolve(model + ".json"), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        List<Face> faces = new ArrayList<>();
        JsonArray elements = root.getAsJsonArray("elements");
        for (JsonElement entry : elements) {
            JsonObject element = entry.getAsJsonObject();
            assertTrue(!element.has("rotation"), model + ": gedrehte Elemente prüft der Test nicht");
            double[] from = vector(element.getAsJsonArray("from"));
            double[] to = vector(element.getAsJsonArray("to"));
            String owner = model + ":" + element.get("name").getAsString();
            for (String side : element.getAsJsonObject("faces").keySet()) {
                int axis = switch (side) {
                    case "east", "west" -> 0;
                    case "up", "down" -> 1;
                    case "north", "south" -> 2;
                    default -> throw new AssertionError(owner + ": unbekannte Fläche " + side);
                };
                int sign = side.equals("east") || side.equals("up") || side.equals("south") ? 1 : -1;
                int u = axis == 0 ? 1 : 0;
                int v = axis == 2 ? 1 : 2;
                List<double[]> outline = List.of(new double[]{from[u], from[v]}, new double[]{to[u], from[v]},
                        new double[]{to[u], to[v]}, new double[]{from[u], to[v]});
                faces.add(new Face(owner, axis, sign, sign > 0 ? to[axis] : from[axis], outline));
            }
        }
        return faces;
    }

    private static double[] vector(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static List<Face> gimbalFaces(RemoteGimbalRenderer.Pose pose) {
        ModelPart root = RemoteGimbalModel.createLayer().bakeRoot();
        RemoteGimbalModel model = new RemoteGimbalModel(root);
        model.setupAnim(pose);
        List<Face> faces = new ArrayList<>();
        root.visit(new PoseStack(), (last, path, index, cube) -> {
            Vector3f centre = last.pose().transformPosition((cube.minX + cube.maxX) / 32.0F, (cube.minY + cube.maxY) / 32.0F,
                    (cube.minZ + cube.maxZ) / 32.0F, new Vector3f()).mul(16.0F);
            double[] middle = {centre.x, centre.y, centre.z};
            for (ModelPart.Polygon polygon : cube.polygons) {
                List<double[]> points = new ArrayList<>();
                for (ModelPart.Vertex vertex : polygon.vertices()) {
                    Vector3f p = last.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f()).mul(16.0F);
                    points.add(new double[]{p.x, p.y, p.z});
                }
                for (int axis = 0; axis < 3; axis++) {
                    double low = Double.MAX_VALUE;
                    double high = -Double.MAX_VALUE;
                    for (double[] p : points) {
                        low = Math.min(low, p[axis]);
                        high = Math.max(high, p[axis]);
                    }
                    if (high - low < 1.0E-4) {
                        double plane = (low + high) / 2.0;
                        int u = axis == 0 ? 1 : 0;
                        int v = axis == 2 ? 1 : 2;
                        List<double[]> outline = points.stream().map(p -> new double[]{p[u], p[v]}).toList();
                        faces.add(new Face("gimbal:" + path + "#" + index, axis, plane > middle[axis] ? 1 : -1, plane, outline));
                    }
                }
            }
        });
        return faces;
    }

    private static double signedArea(List<double[]> polygon) {
        double area = 0.0;
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get((i + 1) % polygon.size());
            area += a[0] * b[1] - b[0] * a[1];
        }
        return area / 2.0;
    }

    private static List<double[]> counterClockwise(List<double[]> polygon) {
        if (signedArea(polygon) >= 0.0) {
            return polygon;
        }
        List<double[]> reversed = new ArrayList<>(polygon);
        Collections.reverse(reversed);
        return reversed;
    }

    private static double cross(double[] a, double[] b, double[] p) {
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    }

    private static double overlapArea(List<double[]> first, List<double[]> second) {
        List<double[]> result = counterClockwise(first);
        List<double[]> clip = counterClockwise(second);
        for (int i = 0; i < clip.size() && !result.isEmpty(); i++) {
            double[] a = clip.get(i);
            double[] b = clip.get((i + 1) % clip.size());
            List<double[]> input = result;
            result = new ArrayList<>();
            for (int j = 0; j < input.size(); j++) {
                double[] p = input.get(j);
                double[] q = input.get((j + 1) % input.size());
                double cp = cross(a, b, p);
                double cq = cross(a, b, q);
                if (cp >= 0.0) {
                    result.add(p);
                }
                if ((cp >= 0.0) != (cq >= 0.0)) {
                    double t = cp / (cp - cq);
                    result.add(new double[]{p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])});
                }
            }
        }
        return result.size() < 3 ? 0.0 : Math.abs(signedArea(result));
    }

    private static List<String> fights(List<Face> faces) {
        List<String> found = new ArrayList<>();
        for (int i = 0; i < faces.size(); i++) {
            for (int j = i + 1; j < faces.size(); j++) {
                Face a = faces.get(i);
                Face b = faces.get(j);
                if (a.owner().equals(b.owner()) || a.axis() != b.axis() || a.sign() != b.sign() || Math.abs(a.plane() - b.plane()) > EPS) {
                    continue;
                }
                double area = overlapArea(a.outline(), b.outline());
                if (area > EPS * EPS) {
                    found.add(String.format(Locale.ROOT, "%s <-> %s (%.4f px²)", a.describe(), b.describe(), area));
                }
            }
        }
        return found;
    }

    @Test
    void heldRemoteHasNoCoplanarOverlappingFaces() throws IOException {
        List<Face> body = jsonFaces("remote_body");
        for (RemoteGimbalRenderer.Controls controls : CONTROLS) {
            for (float[] s : STICKS) {
                RemoteGimbalRenderer.Pose pose = new RemoteGimbalRenderer.Pose(s[0], s[1], s[2], s[3], controls);
                List<Face> moving = gimbalFaces(pose);
                assertTrue(moving.size() >= 6 * RemoteGimbalModel.BUTTON_X.length, "bewegtes Modell ohne Tasten-Flächen");
                List<Face> faces = new ArrayList<>(body);
                faces.addAll(moving);
                assertEquals(List.of(), fights(faces), "Z-Fighting in der Hand (remote_body + RemoteGimbalModel) bei " + pose);
            }
        }
    }

    @Test
    void itemRemoteHasNoCoplanarOverlappingFaces() throws IOException {
        assertEquals(List.of(), fights(jsonFaces("remote")), "Z-Fighting im Item-Modell remote.json");
    }

    @Test
    void overlapAreaOfSquares() {
        List<double[]> unit = List.of(new double[]{0, 0}, new double[]{1, 0}, new double[]{1, 1}, new double[]{0, 1});
        List<double[]> shifted = List.of(new double[]{0.75, 0.75}, new double[]{1.75, 0.75}, new double[]{1.75, 1.75}, new double[]{0.75, 1.75});
        List<double[]> beside = List.of(new double[]{1, 0}, new double[]{2, 0}, new double[]{2, 1}, new double[]{1, 1});
        assertEquals(0.0625, overlapArea(unit, shifted), 1.0E-9);
        assertEquals(0.0625, overlapArea(shifted, unit.reversed()), 1.0E-9);
        assertEquals(0.0, overlapArea(unit, beside), 1.0E-9);
    }
}
