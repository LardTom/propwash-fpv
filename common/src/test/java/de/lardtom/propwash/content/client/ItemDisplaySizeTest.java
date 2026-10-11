package de.lardtom.propwash.content.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.drone.client.render.DroneItemRenderer;
import de.lardtom.propwash.drone.client.render.DroneVisualScale;
import de.lardtom.propwash.drone.client.render.PartItemRenderer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ItemDisplaySizeTest {
    private static final String GENERATED = "minecraft:builtin/generated";

    private static Path resources(String set) {
        for (Path candidate : List.of(Path.of("common"), Path.of("."))) {
            Path dir = candidate.resolve("src/" + set + "/resources/assets");
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        fail("Assets nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static List<Path> roots() {
        return List.of(resources("main"), resources("devtest"));
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String full(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static JsonObject model(String id) throws IOException {
        String[] parts = full(id).split(":", 2);
        for (Path root : roots()) {
            Path file = root.resolve(parts[0]).resolve("models").resolve(parts[1] + ".json");
            if (Files.isRegularFile(file)) {
                return read(file);
            }
        }
        String resource = "assets/" + parts[0] + "/models/" + parts[1] + ".json";
        try (InputStream in = ItemDisplaySizeTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                fail("Modell fehlt: " + id);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private record Resolved(List<Vector3f> corners, JsonObject display) {
    }

    private static Resolved resolve(String id) throws IOException {
        JsonObject display = new JsonObject();
        JsonArray elements = null;
        boolean sprite = false;
        String current = id;
        while (current != null) {
            if (full(current).equals(GENERATED)) {
                sprite = elements == null;
                break;
            }
            JsonObject json = model(current);
            if (json.has("display")) {
                for (var entry : json.getAsJsonObject("display").entrySet()) {
                    if (!display.has(entry.getKey())) {
                        display.add(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (elements == null && json.has("elements")) {
                elements = json.getAsJsonArray("elements");
            }
            current = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        List<Vector3f> corners = new ArrayList<>();
        if (sprite) {
            for (int c = 0; c < 8; c++) {
                corners.add(new Vector3f((c & 1) == 0 ? 0.0F : 16.0F, (c & 2) == 0 ? 0.0F : 16.0F, (c & 4) == 0 ? 7.5F : 8.5F));
            }
        } else if (elements != null) {
            for (JsonElement element : elements) {
                corners.addAll(corners(element.getAsJsonObject()));
            }
        }
        return new Resolved(corners, display);
    }

    private static Vector3f vec(JsonArray array) {
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }

    private static List<Vector3f> corners(JsonObject element) {
        Vector3f from = vec(element.getAsJsonArray("from"));
        Vector3f to = vec(element.getAsJsonArray("to"));
        Matrix4f rotation = new Matrix4f();
        Vector3f origin = new Vector3f(8.0F);
        if (element.has("rotation")) {
            JsonObject r = element.getAsJsonObject("rotation");
            origin = vec(r.getAsJsonArray("origin"));
            if (r.has("axis")) {
                float angle = (float) Math.toRadians(r.get("angle").getAsFloat());
                switch (r.get("axis").getAsString()) {
                    case "x" -> rotation.rotationX(angle);
                    case "y" -> rotation.rotationY(angle);
                    default -> rotation.rotationZ(angle);
                }
            } else {
                rotation.rotationZYX((float) Math.toRadians(r.has("z") ? r.get("z").getAsFloat() : 0.0F),
                        (float) Math.toRadians(r.has("y") ? r.get("y").getAsFloat() : 0.0F),
                        (float) Math.toRadians(r.has("x") ? r.get("x").getAsFloat() : 0.0F));
            }
        }
        List<Vector3f> out = new ArrayList<>();
        for (int c = 0; c < 8; c++) {
            Vector3f p = new Vector3f((c & 1) == 0 ? from.x : to.x, (c & 2) == 0 ? from.y : to.y, (c & 4) == 0 ? from.z : to.z);
            p.sub(origin);
            rotation.transformPosition(p);
            out.add(p.add(origin));
        }
        return out;
    }

    private record Leaf(String model, Matrix4f local) {
    }

    private static boolean matches(JsonElement when, String context) {
        if (when.isJsonArray()) {
            for (JsonElement entry : when.getAsJsonArray()) {
                if (entry.getAsString().equals(context)) {
                    return true;
                }
            }
            return false;
        }
        return when.getAsString().equals(context);
    }

    private static Matrix4f transformation(JsonObject node, Matrix4f outer) {
        if (!node.has("transformation")) {
            return outer;
        }
        JsonObject t = node.getAsJsonObject("transformation");
        Vector3f translation = t.has("translation") ? vec(t.getAsJsonArray("translation")) : new Vector3f();
        Vector3f scale = t.has("scale") ? vec(t.getAsJsonArray("scale")) : new Vector3f(1.0F);
        Quaternionf left = quaternion(t, "left_rotation");
        Quaternionf right = quaternion(t, "right_rotation");
        return new Matrix4f(outer).translate(translation).rotate(left).scale(scale).rotate(right);
    }

    private static Quaternionf quaternion(JsonObject t, String key) {
        if (!t.has(key)) {
            return new Quaternionf();
        }
        JsonArray q = t.getAsJsonArray(key);
        return new Quaternionf(q.get(0).getAsFloat(), q.get(1).getAsFloat(), q.get(2).getAsFloat(), q.get(3).getAsFloat());
    }

    private static void leaves(JsonObject node, String context, Matrix4f outer, List<Leaf> out) {
        Matrix4f local = transformation(node, outer);
        switch (node.get("type").getAsString()) {
            case "minecraft:model" -> out.add(new Leaf(node.get("model").getAsString(), local));
            case "minecraft:select" -> {
                JsonObject chosen = node.has("fallback") ? node.getAsJsonObject("fallback") : null;
                if (node.get("property").getAsString().equals("minecraft:display_context")) {
                    for (JsonElement entry : node.getAsJsonArray("cases")) {
                        JsonObject branch = entry.getAsJsonObject();
                        if (matches(branch.get("when"), context)) {
                            chosen = branch.getAsJsonObject("model");
                            break;
                        }
                    }
                }
                if (chosen != null) {
                    leaves(chosen, context, local, out);
                }
            }
            case "minecraft:range_dispatch" -> {
                if (node.has("fallback")) {
                    leaves(node.getAsJsonObject("fallback"), context, local, out);
                }
            }
            case "minecraft:condition" -> leaves(node.getAsJsonObject("on_false"), context, local, out);
            case "minecraft:composite" -> {
                for (JsonElement entry : node.getAsJsonArray("models")) {
                    leaves(entry.getAsJsonObject(), context, local, out);
                }
            }
            default -> {
            }
        }
    }

    private static JsonObject displayFor(JsonObject display, String context) {
        if (display.has(context)) {
            return display.getAsJsonObject(context);
        }
        String right = context.replace("lefthand", "righthand");
        return display.has(right) ? display.getAsJsonObject(right) : null;
    }

    private static float[] size(Resolved model, Matrix4f local, String context) {
        JsonObject transform = displayFor(model.display(), context);
        Matrix4f pose = new Matrix4f();
        if (transform != null) {
            Vector3f rotation = transform.has("rotation") ? vec(transform.getAsJsonArray("rotation")) : new Vector3f();
            if (context.endsWith("_lefthand")) {
                rotation.set(rotation.x, -rotation.y, -rotation.z);
            }
            Vector3f scale = transform.has("scale") ? vec(transform.getAsJsonArray("scale")) : new Vector3f(1.0F);
            pose.rotate(new Quaternionf().rotationXYZ((float) Math.toRadians(rotation.x), (float) Math.toRadians(rotation.y),
                    (float) Math.toRadians(rotation.z))).scale(scale);
        }
        pose.translate(-0.5F, -0.5F, -0.5F).mul(local);
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (Vector3f corner : model.corners()) {
            Vector3f p = pose.transformPosition(new Vector3f(corner).div(16.0F));
            min.min(p);
            max.max(p);
        }
        return new float[]{max.x - min.x, max.y - min.y, max.z - min.z};
    }

    private static final float MM_PER_BLOCK = 128.0F;

    private record PartLeaf(String category, Resolved model, Matrix4f local) {
    }

    private static PartLeaf partLeaf(JsonObject definition) throws IOException {
        List<Leaf> leaves = new ArrayList<>();
        leaves(definition, "none", new Matrix4f(), leaves);
        if (leaves.size() != 1) {
            return null;
        }
        String id = full(leaves.getFirst().model());
        String path = id.substring(id.indexOf(':') + 1);
        if (!path.startsWith("part/")) {
            return null;
        }
        String category = path.split("/")[1];
        Resolved model = resolve(id);
        return model.corners().isEmpty() ? null : new PartLeaf(category, model, leaves.getFirst().local());
    }

    private static float lengthMm(PartLeaf part) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (Vector3f corner : part.model().corners()) {
            Vector3f p = part.local().transformPosition(new Vector3f(corner).div(16.0F));
            min.min(p);
            max.max(p);
        }
        Vector3f edge = max.sub(min);
        return Math.max(edge.x, Math.max(edge.y, edge.z)) * MM_PER_BLOCK;
    }

    private static float[] box(JsonObject definition, String context) throws IOException {
        List<Leaf> leaves = new ArrayList<>();
        leaves(definition, context, new Matrix4f(), leaves);
        float[] box = {0.0F, 0.0F, 0.0F};
        for (Leaf leaf : leaves) {
            float[] part = size(resolve(leaf.model()), leaf.local(), context);
            for (int i = 0; i < 3; i++) {
                box[i] = Math.max(box[i], part[i]);
            }
        }
        return leaves.isEmpty() ? null : box;
    }

    private record PartItem(String id, String category, float lengthMm, JsonObject definition) {
    }

    private static List<PartItem> partItems() throws IOException {
        List<PartItem> parts = new ArrayList<>();
        for (Path root : roots()) {
            for (String namespace : List.of("propwash", "propwash_example")) {
                Path items = root.resolve(namespace).resolve("items");
                if (!Files.isDirectory(items)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(items, "*.json")) {
                    for (Path file : files) {
                        JsonObject definition = read(file).getAsJsonObject("model");
                        PartLeaf leaf = partLeaf(definition);
                        if (leaf != null) {
                            parts.add(new PartItem(namespace + ":" + file.getFileName().toString().replace(".json", ""), leaf.category(),
                                    lengthMm(leaf), definition));
                        }
                    }
                }
            }
        }
        return parts;
    }

    @Test
    void everyModelItemFitsTheSizeRuleInEveryDisplayContext() throws IOException {
        List<String> outliers = new ArrayList<>();
        int checked = 0;
        for (Path root : roots()) {
            for (String namespace : List.of("propwash", "propwash_example")) {
                Path items = root.resolve(namespace).resolve("items");
                if (!Files.isDirectory(items)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(items, "*.json")) {
                    for (Path file : files) {
                        JsonObject definition = read(file).getAsJsonObject("model");
                        PartLeaf part = partLeaf(definition);
                        float length = part == null ? 0.0F : lengthMm(part);
                        for (String context : ItemSizeRule.CONTEXTS) {
                            float[] box = box(definition, context);
                            if (box == null) {
                                continue;
                            }
                            checked++;
                            ItemSizeRule.Limit limit = (part == null ? ItemSizeRule.limit(context) : ItemSizeRule.partLimit(context, length)).orElseThrow();
                            if (!limit.fits(box[0], box[1], box[2])) {
                                outliers.add(String.format(Locale.ROOT, "%s:%s %s %.2f×%.2f×%.2f", namespace,
                                        file.getFileName().toString().replace(".json", ""), context, box[0], box[1], box[2]));
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked >= 40 * ItemSizeRule.CONTEXTS.size(), "zu wenige Items geprüft: " + checked);
        assertTrue(outliers.isEmpty(), outliers.size() + " Ausreißer: " + String.join("; ", outliers));
    }

    @Test
    void batteriesLieSmallOnTheGroundAndSidewaysInFrames() throws IOException {
        Path batteries = resources("main").resolve("propwash/models/part/battery");
        int checked = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(batteries, "*.json")) {
            for (Path file : files) {
                String id = "propwash:part/battery/" + file.getFileName().toString().replace(".json", "");
                Resolved model = resolve(id);
                Matrix4f local = new Matrix4f();
                Path item = resources("main").resolve("propwash/items/" + file.getFileName());
                if (Files.isRegularFile(item)) {
                    List<Leaf> leaves = new ArrayList<>();
                    leaves(read(item).getAsJsonObject("model"), "ground", new Matrix4f(), leaves);
                    local = leaves.getFirst().local();
                }
                float[] ground = size(model, local, "ground");
                float longest = Math.max(ground[0], Math.max(ground[1], ground[2]));
                assertTrue(longest <= 0.4F, id + " am Boden " + longest + " Blöcke lang");
                float[] frame = size(model, local, "fixed");
                assertTrue(frame[0] > frame[2] && frame[0] > frame[1], id + " liegt im Item-Rahmen nicht quer");
                checked++;
            }
        }
        assertTrue(checked >= 5, "zu wenige Akkus");
    }

    @Test
    void chunkyPartsLieLikeABlockOnTheGround() throws IOException {
        int checked = 0;
        for (String category : List.of("motor", "accessory")) {
            Path models = resources("main").resolve("propwash/models/part/" + category);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(models, "*.json")) {
                for (Path file : files) {
                    String name = file.getFileName().toString().replace(".json", "");
                    Path item = resources("main").resolve("propwash/items/" + name + ".json");
                    if (name.endsWith("_paint") || !Files.isRegularFile(item)) {
                        continue;
                    }
                    List<Leaf> leaves = new ArrayList<>();
                    leaves(read(item).getAsJsonObject("model"), "ground", new Matrix4f(), leaves);
                    float[] ground = size(resolve("propwash:part/" + category + "/" + name), leaves.getFirst().local(), "ground");
                    float bulk = ItemSizeRule.bulkOf(ground[0], ground[1], ground[2]);
                    float longest = Math.max(ground[0], Math.max(ground[1], ground[2]));
                    assertTrue(bulk <= ItemSizeRule.GROUND_BULK + 1.0E-3F, name + " am Boden zu sperrig: Würfelmaß " + bulk);
                    assertTrue(longest >= 0.25F, name + " am Boden kleiner als ein Vanilla-Block: " + longest);
                    checked++;
                }
            }
        }
        assertTrue(checked >= 8, "zu wenige Motoren und Action-Cams: " + checked);
    }

    @Test
    void iconsWithoutModelStayWithinTheBulkLimitOnTheGround() throws IOException {
        assertEquals(ItemSizeRule.GROUND_BULK, PartItemRenderer.GROUND_BOX_BULK * PartItemRenderer.GROUND_BASE_BLOCKS, 1.0E-6F);
        for (String category : List.of("frame", "stack", "motor", "prop", "video", "battery", "accessory")) {
            JsonObject base = model("propwash:item/part_base/" + category);
            float scale = base.getAsJsonObject("display").getAsJsonObject("ground").getAsJsonArray("scale").get(0).getAsFloat();
            assertTrue(scale <= PartItemRenderer.GROUND_BASE_BLOCKS + 1.0E-4F, "part_base/" + category + " am Boden " + scale);
        }
    }

    @Test
    void dronesOnTheGroundGrowWithTheirClassUpToHalfABlock() {
        float whoop = DroneItemRenderer.groundSize(48.0F);
        float freestyle = DroneItemRenderer.groundSize(177.0F);
        float longRange = DroneItemRenderer.groundSize(236.0F);
        float xclass = DroneItemRenderer.groundSize(620.0F);
        assertTrue(whoop >= ItemSizeRule.GROUND_MIN, "Whoop zu klein: " + whoop);
        assertTrue(whoop < freestyle && freestyle < longRange && longRange < xclass, whoop + " / " + freestyle + " / " + longRange + " / " + xclass);
        assertEquals(ItemSizeRule.GROUND_MAX, xclass, 1.0E-6F);
        assertEquals(ItemSizeRule.GROUND_MAX, DroneItemRenderer.groundSize(5000.0F), 1.0E-6F);
        for (float footprint : new float[]{48.0F, 177.0F, 640.0F}) {
            float width = footprint * 1.6F;
            float shown = DroneItemRenderer.groundScale(footprint, width) * width * DroneVisualScale.MM_TO_UNITS;
            assertEquals(DroneItemRenderer.groundSize(footprint), shown, 1.0E-5F, "Fußkreis " + footprint + " mm");
        }
    }

    @Test
    void partsInHandFollowTheirRealSize() throws IOException {
        int checked = 0;
        List<String> off = new ArrayList<>();
        for (PartItem part : partItems()) {
            float factor = ItemSizeRule.handFactor(part.lengthMm());
            for (String context : List.of("firstperson_righthand", "thirdperson_righthand")) {
                if (part.category().equals("battery") && context.startsWith("firstperson")) {
                    continue;
                }
                float target = (context.startsWith("firstperson") ? 9.0F : 7.0F) / 16.0F * factor;
                float[] right = box(part.definition(), context);
                float[] left = box(part.definition(), context.replace("righthand", "lefthand"));
                float view = Math.max(Math.max(right[0], right[1]), Math.max(left[0], left[1]));
                float longest = Math.max(view, Math.max(right[2], left[2]));
                boolean capped = longest >= 0.9F * factor * 0.97F;
                if (view > target * 1.02F || (view < target * 0.97F && !capped)) {
                    off.add(String.format(Locale.ROOT, "%s %s %.3f statt %.3f (%.1f mm)", part.id(), context, view, target, part.lengthMm()));
                }
                checked++;
            }
        }
        assertTrue(checked >= 60, "zu wenige Teile geprüft: " + checked);
        assertTrue(off.isEmpty(), "Hand-Größe folgt nicht der echten Größe: " + String.join("; ", off));
    }

    @Test
    void motorsInHandStaySmallerThanAHeldBlock() throws IOException {
        int checked = 0;
        float smallest = Float.POSITIVE_INFINITY;
        float largest = 0.0F;
        for (PartItem part : partItems()) {
            if (!part.category().equals("motor")) {
                continue;
            }
            float[] first = box(part.definition(), "firstperson_righthand");
            float[] third = box(part.definition(), "thirdperson_righthand");
            float firstView = Math.max(first[0], first[1]);
            assertTrue(firstView <= 0.3F, part.id() + " in der ersten Person " + firstView + " Block breit (gehaltener Block 0,4)");
            assertTrue(Math.max(third[0], third[1]) <= 0.25F, part.id() + " in der dritten Person " + Math.max(third[0], third[1]) + " Block");
            smallest = Math.min(smallest, firstView);
            largest = Math.max(largest, firstView);
            checked++;
        }
        assertTrue(checked >= 7, "zu wenige Motoren: " + checked);
        assertTrue(smallest >= 0.12F, "kleinster Motor kaum sichtbar: " + smallest);
        assertTrue(largest > smallest * 1.3F, "Motoren zeigen ihre Größe nicht: " + smallest + " bis " + largest);
    }

    @Test
    void oldMotorHandSizesFailTheRule() {
        float[][] oldFirstPerson = {{14.2F, 0.563F, 0.469F, 0.583F}, {27.9F, 0.525F, 0.562F, 0.573F}, {51.0F, 0.563F, 0.546F, 0.614F}};
        float[][] oldThirdPerson = {{14.2F, 0.399F, 0.437F, 0.357F}, {27.9F, 0.379F, 0.437F, 0.426F}, {51.0F, 0.380F, 0.438F, 0.390F}};
        for (float[] old : oldFirstPerson) {
            assertTrue(ItemSizeRule.limit("firstperson_righthand").orElseThrow().fits(old[1], old[2], old[3]), "allgemeine Grenze");
            assertFalse(ItemSizeRule.partLimit("firstperson_righthand", old[0]).orElseThrow().fits(old[1], old[2], old[3]),
                    old[0] + " mm in der ersten Person bestünde noch");
        }
        for (float[] old : oldThirdPerson) {
            assertFalse(ItemSizeRule.partLimit("thirdperson_righthand", old[0]).orElseThrow().fits(old[1], old[2], old[3]),
                    old[0] + " mm in der dritten Person bestünde noch");
        }
        assertEquals(1.0F, ItemSizeRule.handFactor(ItemSizeRule.HAND_FULL_MM), 1.0E-6F);
        assertEquals(1.0F, ItemSizeRule.handFactor(600.0F), 1.0E-6F);
        assertEquals(ItemSizeRule.HAND_MIN_FACTOR, ItemSizeRule.handFactor(5.0F), 1.0E-6F);
        assertEquals(1.0F, ItemSizeRule.handFactor(0.0F), 1.0E-6F);
    }
}
