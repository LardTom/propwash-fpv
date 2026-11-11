package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PlugAnchors {
    public static final String CONNECTOR = "connector";
    public static final String CONNECTOR_GRIP = "connector_grip";
    public static final String BT_CONNECTOR = "bt_connector";
    public static final String PIGTAIL = "pigtail";
    public static final String WIRE = "wire";
    public static final String BALANCE_PLUG = "balance_plug";
    public static final Set<String> PLUG_ELEMENTS = Set.of(CONNECTOR, CONNECTOR_GRIP, BT_CONNECTOR);
    public static final float MODEL_ANCHOR = 8.0F;
    public static final float TOUCH_MM = 0.01F;

    private PlugAnchors() {
    }

    public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public Box union(Box other) {
            return new Box(Math.min(this.minX, other.minX), Math.min(this.minY, other.minY), Math.min(this.minZ, other.minZ),
                    Math.max(this.maxX, other.maxX), Math.max(this.maxY, other.maxY), Math.max(this.maxZ, other.maxZ));
        }

        public float sizeX() {
            return this.maxX - this.minX;
        }

        public float sizeY() {
            return this.maxY - this.minY;
        }

        public float sizeZ() {
            return this.maxZ - this.minZ;
        }

        public boolean touches(Box other, float margin) {
            return this.minX <= other.maxX + margin && other.minX <= this.maxX + margin && this.minY <= other.maxY + margin
                    && other.minY <= this.maxY + margin && this.minZ <= other.maxZ + margin && other.minZ <= this.maxZ + margin;
        }

        public boolean contains(float x, float y, float z, float margin) {
            return x >= this.minX - margin && x <= this.maxX + margin && y >= this.minY - margin && y <= this.maxY + margin
                    && z >= this.minZ - margin && z <= this.maxZ + margin;
        }
    }

    public record Element(String name, Box box) {
    }

    public static List<Element> elements(JsonObject model) {
        List<Element> out = new ArrayList<>();
        JsonElement elements = model.get("elements");
        if (elements == null || !elements.isJsonArray()) {
            return out;
        }
        for (JsonElement entry : elements.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject element = entry.getAsJsonObject();
            Vector3f from = vector(element.get("from"));
            Vector3f to = vector(element.get("to"));
            if (from == null || to == null) {
                continue;
            }
            String name = element.get("name") instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : "";
            out.add(new Element(name, box(from, to, element.get("rotation"))));
        }
        return out;
    }

    public static boolean isLead(String name) {
        return PLUG_ELEMENTS.contains(name) || name.equals(WIRE) || name.equals(BALANCE_PLUG) || name.equals(PIGTAIL);
    }

    public static boolean hasPlug(JsonObject model) {
        return hasPlug(elements(model));
    }

    public static boolean hasPlug(List<Element> elements) {
        for (Element element : elements) {
            if (PLUG_ELEMENTS.contains(element.name())) {
                return true;
            }
        }
        return false;
    }

    public static int count(JsonObject model, String name) {
        int count = 0;
        for (Element element : elements(model)) {
            if (element.name().equals(name)) {
                count++;
            }
        }
        return count;
    }

    public static List<Box> batteryPlug(JsonObject model) {
        return batteryPlug(elements(model));
    }

    public static List<Box> batteryPlug(List<Element> elements) {
        Box body = null;
        List<Box> plugs = new ArrayList<>();
        for (Element element : elements) {
            if (PLUG_ELEMENTS.contains(element.name()) || element.name().equals(BALANCE_PLUG)) {
                plugs.add(element.box());
            }
            if (element.name().equals(CONNECTOR) || element.name().equals(BT_CONNECTOR)) {
                body = body == null ? element.box() : body.union(element.box());
            }
        }
        if (body == null || !(body.sizeX() > 0.0F && body.sizeY() > 0.0F && body.sizeZ() > 0.0F)) {
            return List.of();
        }
        List<Box> out = new ArrayList<>(plugs);
        for (Element element : elements) {
            if (element.name().equals(WIRE) && touchesAny(element.box(), plugs)) {
                out.add(element.box());
            }
        }
        return List.copyOf(out);
    }

    private static boolean touchesAny(Box box, List<Box> others) {
        for (Box other : others) {
            if (box.touches(other, TOUCH_MM)) {
                return true;
            }
        }
        return false;
    }

    public static List<Box> pigtails(JsonObject model) {
        return pigtails(elements(model));
    }

    public static List<Box> pigtails(List<Element> elements) {
        if (hasPlug(elements)) {
            return List.of();
        }
        List<Box> out = new ArrayList<>(2);
        for (Element element : elements) {
            if (!element.name().equals(PIGTAIL)) {
                continue;
            }
            Box box = element.box();
            if (!(Math.min(box.sizeX(), box.sizeY()) > 0.0F)) {
                return List.of();
            }
            out.add(box);
        }
        return out.size() == 2 ? List.copyOf(out) : List.of();
    }

    static Box box(Vector3fc from, Vector3fc to, @Nullable JsonElement rotation) {
        Matrix3f matrix = rotation(rotation);
        Vector3f origin = matrix == null ? null : vector(rotation.getAsJsonObject().get("origin"));
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? from.x() : to.x(), (i & 2) == 0 ? from.y() : to.y(), (i & 4) == 0 ? from.z() : to.z());
            if (matrix != null && origin != null) {
                corner.sub(origin);
                matrix.transform(corner);
                corner.add(origin);
            }
            min.min(corner);
            max.max(corner);
        }
        return new Box(millimetres(min.x), millimetres(min.y), millimetres(min.z), millimetres(max.x), millimetres(max.y), millimetres(max.z));
    }

    static @Nullable Matrix3f rotation(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject rotation = element.getAsJsonObject();
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        if (rotation.get("axis") instanceof JsonPrimitive axis && axis.isString()) {
            float angle = number(rotation.get("angle"));
            switch (axis.getAsString()) {
                case "x" -> x = angle;
                case "y" -> y = angle;
                case "z" -> z = angle;
                default -> {
                    return null;
                }
            }
        } else {
            x = number(rotation.get("x"));
            y = number(rotation.get("y"));
            z = number(rotation.get("z"));
        }
        if (x == 0.0F && y == 0.0F && z == 0.0F) {
            return null;
        }
        return new Matrix3f().rotationZ((float) Math.toRadians(z)).rotateY((float) Math.toRadians(y)).rotateX((float) Math.toRadians(x));
    }

    static float millimetres(float modelUnits) {
        return (modelUnits - MODEL_ANCHOR) * DroneVisualScale.MILLIMETRES_PER_MODEL_UNIT;
    }

    private static float number(@Nullable JsonElement element) {
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            float value = primitive.getAsFloat();
            return Float.isFinite(value) ? value : 0.0F;
        }
        return 0.0F;
    }

    private static @Nullable Vector3f vector(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            return null;
        }
        float[] values = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!(array.get(i) instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                return null;
            }
            values[i] = primitive.getAsFloat();
            if (!Float.isFinite(values[i])) {
                return null;
            }
        }
        return new Vector3f(values[0], values[1], values[2]);
    }
}
