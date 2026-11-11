package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

final class CovPropBlurCenterModels {
    static final float REACH_TOLERANCE = 1.0E-3F;
    static final float TILT_TOLERANCE = 1.0E-4F;
    private static final int TEXTURE_HOPS = 5;

    record Result(boolean same, List<String> missing, List<String> extra, int bladeParts) {
    }

    record Element(String name, String line, boolean translucent, float reach, boolean tilted) {
    }

    private CovPropBlurCenterModels() {
    }

    static @Nullable Result compare(JsonObject standingModel, JsonObject blurModel) {
        List<Element> standing = elements(standingModel);
        List<Element> spinning = elements(blurModel);
        if (standing == null || spinning == null) {
            return null;
        }
        List<Element> center = new ArrayList<>();
        boolean disc = false;
        for (Element element : spinning) {
            if (element.translucent()) {
                disc = true;
            } else {
                center.add(element);
            }
        }
        if (!disc) {
            return null;
        }
        float reach = centerReach(standing);
        List<String> centerLines = new ArrayList<>();
        for (Element element : center) {
            centerLines.add(element.line());
        }
        List<String> standingLines = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int blades = 0;
        for (Element element : standing) {
            standingLines.add(element.line());
            if (centerLines.contains(element.line())) {
                continue;
            }
            if (element.reach() > reach + REACH_TOLERANCE) {
                blades++;
            } else {
                missing.add(element.name());
            }
        }
        List<String> extra = new ArrayList<>();
        for (Element element : center) {
            if (!standingLines.contains(element.line())) {
                extra.add(element.name());
            }
        }
        return new Result(missing.isEmpty() && extra.isEmpty(), missing, extra, blades);
    }

    static float centerReach(List<Element> standing) {
        float reach = 0.0F;
        for (Element element : standing) {
            if (!element.tilted()) {
                reach = Math.max(reach, element.reach());
            }
        }
        return reach;
    }

    static @Nullable List<Element> elements(JsonObject model) {
        if (!(model.get("elements") instanceof JsonArray array)) {
            return null;
        }
        JsonObject textures = model.get("textures") instanceof JsonObject object ? object : new JsonObject();
        List<Element> out = new ArrayList<>();
        for (JsonElement entry : array) {
            JsonObject element = entry.getAsJsonObject();
            String name = element.has("name") ? element.get("name").getAsString() : "";
            StringBuilder line = new StringBuilder().append(element.get("from")).append(' ').append(element.get("to")).append(' ')
                    .append(element.get("rotation"));
            JsonObject faces = element.get("faces") instanceof JsonObject object ? object : new JsonObject();
            boolean translucent = !faces.isEmpty();
            for (String face : new TreeSet<>(faces.keySet())) {
                JsonObject value = faces.getAsJsonObject(face);
                Map<String, String> sorted = new TreeMap<>();
                boolean clear = false;
                for (String key : value.keySet()) {
                    if ("texture".equals(key)) {
                        JsonElement texture = texture(textures, value.get(key).getAsString());
                        clear = forcedTranslucent(texture);
                        sorted.put(key, sprite(texture) + (clear ? " translucent" : ""));
                    } else {
                        sorted.put(key, value.get(key).toString());
                    }
                }
                translucent &= clear;
                line.append(' ').append(face).append(sorted);
            }
            out.add(new Element(name, line.toString(), translucent, reach(element), tilted(element)));
        }
        return out;
    }

    static float reach(JsonObject element) {
        Vector3f from = vector(element.get("from"));
        Vector3f to = vector(element.get("to"));
        if (from == null || to == null) {
            return 0.0F;
        }
        JsonElement rotation = element.get("rotation");
        Matrix3f matrix = PlugAnchors.rotation(rotation);
        Vector3f origin = matrix == null ? null : vector(rotation.getAsJsonObject().get("origin"));
        Vector3f corner = new Vector3f();
        float reach = 0.0F;
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? from.x : to.x, (i & 2) == 0 ? from.y : to.y, (i & 4) == 0 ? from.z : to.z);
            if (matrix != null && origin != null) {
                corner.sub(origin);
                matrix.transform(corner);
                corner.add(origin);
            }
            reach = Math.max(reach, (float) Math.hypot(corner.x - PlugAnchors.MODEL_ANCHOR, corner.z - PlugAnchors.MODEL_ANCHOR));
        }
        return reach;
    }

    static boolean tilted(JsonObject element) {
        Matrix3f matrix = PlugAnchors.rotation(element.get("rotation"));
        return matrix != null && Math.abs(matrix.m11()) < 1.0F - TILT_TOLERANCE;
    }

    private static @Nullable Vector3f vector(@Nullable JsonElement element) {
        if (!(element instanceof JsonArray array) || array.size() != 3) {
            return null;
        }
        float[] values = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!(array.get(i) instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                return null;
            }
            values[i] = primitive.getAsFloat();
        }
        return new Vector3f(values[0], values[1], values[2]);
    }

    private static JsonElement texture(JsonObject textures, String reference) {
        JsonElement value = new JsonPrimitive(reference);
        for (int i = 0; i < TEXTURE_HOPS && value instanceof JsonPrimitive primitive && primitive.getAsString().startsWith("#"); i++) {
            JsonElement target = textures.get(primitive.getAsString().substring(1));
            if (target == null) {
                return value;
            }
            value = target;
        }
        return value;
    }

    private static String sprite(JsonElement texture) {
        if (texture instanceof JsonObject object) {
            return object.get("sprite") instanceof JsonPrimitive sprite ? sprite.getAsString() : object.toString();
        }
        return texture.getAsString();
    }

    private static boolean forcedTranslucent(JsonElement texture) {
        return texture instanceof JsonObject object && object.get("force_translucent") instanceof JsonPrimitive flag && flag.isBoolean()
                && flag.getAsBoolean();
    }
}
