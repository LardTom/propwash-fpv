package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DroneLayoutParser {
    static final float MAX_ABS_MM = 2000.0F;

    private DroneLayoutParser() {
    }

    public static Result parse(JsonElement root) {
        return parse(root, PartIndex.DEFAULT_NAMESPACE);
    }

    public static Result parse(JsonElement root, String namespace) {
        List<String> problems = new ArrayList<>();
        Map<FrameType, DroneLayout> layouts = new LinkedHashMap<>();
        Map<Identifier, JsonElement> entries = new LinkedHashMap<>();
        if (root == null || !root.isJsonObject()) {
            problems.add("Wurzel ist kein JSON-Objekt");
            return new Result(layouts, problems, entries);
        }
        JsonElement framesElement = root.getAsJsonObject().get("frames");
        if (framesElement == null && !PartIndex.DEFAULT_NAMESPACE.equals(namespace)) {
            return new Result(layouts, problems, entries);
        }
        if (framesElement == null || !framesElement.isJsonObject()) {
            problems.add("\"frames\" fehlt oder ist kein Objekt");
            return new Result(layouts, problems, entries);
        }
        JsonObject frames = framesElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : frames.entrySet()) {
            Identifier id = resolve(entry.getKey(), namespace);
            if (id == null) {
                problems.add("ungültiger Rahmen-Schlüssel " + entry.getKey() + " wird ignoriert");
                continue;
            }
            entries.put(id, entry.getValue());
            FrameType frame = FrameType.byId(id).orElse(null);
            if (frame == null) {
                problems.add("unbekannter Rahmen " + entry.getKey() + " wird ignoriert");
                continue;
            }
            DroneLayout layout = parseFrame(frame, entry.getValue(), problems);
            if (layout != null) {
                layouts.put(frame, layout);
            }
        }
        if (PartIndex.DEFAULT_NAMESPACE.equals(namespace)) {
            for (FrameType frame : FrameType.values()) {
                if (frame.builtin() && !entries.containsKey(frame.id())) {
                    problems.add("kein Eintrag für Rahmen " + frame.key());
                }
            }
        }
        return new Result(layouts, problems, entries);
    }

    public static @Nullable Identifier resolve(String key, String namespace) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (key.indexOf(':') >= 0) {
            return Identifier.tryParse(key);
        }
        return Identifier.tryBuild(namespace == null ? PartIndex.DEFAULT_NAMESPACE : namespace, key);
    }

    static DroneLayout parseFrame(FrameType frame, JsonElement element, List<String> problems) {
        String where = frame.key() + ": ";
        if (!element.isJsonObject()) {
            problems.add(where + "Eintrag ist kein Objekt");
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        DroneLayout fallback = DroneLayout.fallback(frame);
        JsonElement motorsElement = obj.get("motors");
        Vector3fc[] motors = new Vector3fc[DroneLayout.SLOTS];
        if (motorsElement == null && !frame.builtin()) {
            for (int i = 0; i < DroneLayout.SLOTS; i++) {
                motors[i] = fallback.motor(i);
            }
        } else {
            if (motorsElement == null || !motorsElement.isJsonArray() || motorsElement.getAsJsonArray().size() != DroneLayout.SLOTS) {
                problems.add(where + "\"motors\" braucht genau " + DroneLayout.SLOTS + " Punkte");
                return null;
            }
            JsonArray motorArray = motorsElement.getAsJsonArray();
            for (int i = 0; i < DroneLayout.SLOTS; i++) {
                motors[i] = vector(motorArray.get(i));
                if (motors[i] == null) {
                    problems.add(where + "Motor " + i + " ist kein gültiger Punkt [x, y, z]");
                    return null;
                }
            }
        }
        boolean[] ccw = new boolean[DroneLayout.SLOTS];
        for (int i = 0; i < DroneLayout.SLOTS; i++) {
            ccw[i] = fallback.counterClockwise(i);
        }
        JsonElement spinElement = obj.get("motor_spin");
        if (spinElement != null) {
            boolean[] parsed = spins(spinElement);
            if (parsed == null) {
                problems.add(where + "\"motor_spin\" braucht " + DroneLayout.SLOTS + "× \"cw\"/\"ccw\", Standard „Props in“ benutzt");
            } else {
                ccw = parsed;
            }
        }
        float propHeight = number(obj.get("prop_height"), Float.NaN, where + "prop_height", problems);
        if (Float.isFinite(propHeight) && (propHeight < 0.0F || propHeight > 200.0F)) {
            problems.add(where + "prop_height außerhalb 0..200 mm");
            propHeight = Float.NaN;
        }
        Vector3fc stack = optionalVector(obj.get("stack"), fallback.stack(), where + "stack", problems);
        Vector3fc battery = optionalVector(obj.get("battery"), fallback.battery(), where + "battery", problems);
        Vector3fc video = optionalVector(obj.get("video"), fallback.video(), where + "video", problems);
        float tilt = number(obj.get("camera_tilt_default"), fallback.cameraTiltDegrees(), where + "camera_tilt_default", problems);
        if (tilt < -30.0F || tilt > 80.0F) {
            problems.add(where + "camera_tilt_default außerhalb -30..80°");
            tilt = fallback.cameraTiltDegrees();
        }
        Map<AccessoryMount, AccessoryAnchor> accessories = accessories(obj.get("accessories"), fallback, where, problems);
        if (!frame.builtin()) {
            Map<AccessoryMount, AccessoryAnchor> merged = new EnumMap<>(AccessoryMount.class);
            merged.putAll(fallback.accessories());
            merged.putAll(accessories);
            merged.keySet().retainAll(frame.accessoryMounts);
            accessories = merged;
        }
        return new DroneLayout(motors, ccw, propHeight, stack, battery, video, tilt, true, accessories,
                !frame.definition().geometry().batteryOnTop());
    }

    static Map<AccessoryMount, AccessoryAnchor> accessories(JsonElement element, DroneLayout fallback, String where, List<String> problems) {
        Map<AccessoryMount, AccessoryAnchor> anchors = new EnumMap<>(AccessoryMount.class);
        if (element == null) {
            anchors.putAll(fallback.accessories());
            return anchors;
        }
        if (!element.isJsonObject()) {
            problems.add(where + "\"accessories\" ist kein Objekt, eingebaute Halterungen benutzt");
            anchors.putAll(fallback.accessories());
            return anchors;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String what = where + "accessories." + entry.getKey();
            AccessoryMount mount = AccessoryMount.byKey(entry.getKey()).orElse(null);
            if (mount == null) {
                problems.add(what + ": unbekannte Halterung wird ignoriert");
                continue;
            }
            AccessoryAnchor anchor = anchor(entry.getValue(), what, problems);
            if (anchor != null) {
                anchors.put(mount, anchor);
            } else {
                fallback.accessory(mount).ifPresent(builtIn -> anchors.put(mount, builtIn));
            }
        }
        return anchors;
    }

    static AccessoryAnchor anchor(JsonElement element, String what, List<String> problems) {
        if (element == null || !element.isJsonObject()) {
            problems.add(what + " ist kein Objekt");
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        Vector3f position = vector(obj.get("position"));
        if (position == null) {
            problems.add(what + ".position ist kein gültiger Punkt [x, y, z]");
            return null;
        }
        float tilt = number(obj.get("tilt"), 0.0F, what + ".tilt", problems);
        if (tilt < AccessoryAnchor.MIN_TILT || tilt > AccessoryAnchor.MAX_TILT) {
            problems.add(what + ".tilt außerhalb " + (int) AccessoryAnchor.MIN_TILT + ".." + (int) AccessoryAnchor.MAX_TILT + "°");
            return null;
        }
        float length = number(obj.get("length"), 0.0F, what + ".length", problems);
        if (length < 0.0F || length > 200.0F) {
            problems.add(what + ".length außerhalb 0..200 mm");
            return null;
        }
        return new AccessoryAnchor(position.x, position.y, position.z, tilt, length);
    }

    static Vector3f vector(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            return null;
        }
        float[] values = new float[3];
        for (int i = 0; i < 3; i++) {
            JsonElement e = array.get(i);
            if (!(e instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                return null;
            }
            float v = primitive.getAsFloat();
            if (!Float.isFinite(v) || Math.abs(v) > MAX_ABS_MM) {
                return null;
            }
            values[i] = v;
        }
        return new Vector3f(values[0], values[1], values[2]);
    }

    private static Vector3fc optionalVector(JsonElement element, Vector3fc fallback, String what, List<String> problems) {
        if (element == null) {
            return fallback;
        }
        Vector3f v = vector(element);
        if (v == null) {
            problems.add(what + " ist kein gültiger Punkt [x, y, z]");
            return fallback;
        }
        return v;
    }

    private static float number(JsonElement element, float fallback, String what, List<String> problems) {
        if (element == null) {
            return fallback;
        }
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            float v = primitive.getAsFloat();
            if (Float.isFinite(v)) {
                return v;
            }
        }
        problems.add(what + " ist keine endliche Zahl");
        return fallback;
    }

    private static boolean[] spins(JsonElement element) {
        if (!element.isJsonArray() || element.getAsJsonArray().size() != DroneLayout.SLOTS) {
            return null;
        }
        boolean[] result = new boolean[DroneLayout.SLOTS];
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < DroneLayout.SLOTS; i++) {
            JsonElement e = array.get(i);
            if (!(e instanceof JsonPrimitive primitive) || !primitive.isString()) {
                return null;
            }
            String value = primitive.getAsString().toLowerCase(Locale.ROOT);
            if (value.equals("ccw")) {
                result[i] = true;
            } else if (!value.equals("cw")) {
                return null;
            }
        }
        return result;
    }

    public static Map<String, Float> motorSeats(JsonElement root, List<String> problems) {
        Map<String, Float> seats = new HashMap<>();
        for (Map.Entry<Identifier, Float> entry : motorSeats(root, PartIndex.DEFAULT_NAMESPACE, problems).entrySet()) {
            seats.put(PartIndex.keyOf(entry.getKey()), entry.getValue());
        }
        return seats;
    }

    public static Map<Identifier, Float> motorSeats(JsonElement root, String namespace, List<String> problems) {
        Map<Identifier, Float> seats = new LinkedHashMap<>();
        if (root == null || !root.isJsonObject()) {
            return seats;
        }
        JsonElement element = root.getAsJsonObject().get("motor_seat");
        if (element == null) {
            return seats;
        }
        if (!element.isJsonObject()) {
            problems.add("\"motor_seat\" ist kein Objekt");
            return seats;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            Identifier id = resolve(entry.getKey(), namespace);
            if (id != null && entry.getValue() instanceof JsonPrimitive primitive && primitive.isNumber()) {
                float seat = primitive.getAsFloat();
                if (Float.isFinite(seat) && seat > 0.0F && seat < 100.0F) {
                    seats.put(id, seat);
                    continue;
                }
            }
            problems.add("motor_seat." + entry.getKey() + " ist keine Höhe in mm");
        }
        return seats;
    }

    public record Result(Map<FrameType, DroneLayout> layouts, List<String> problems, Map<Identifier, JsonElement> entries) {
        public Result {
            layouts = Collections.unmodifiableMap(new LinkedHashMap<>(layouts));
            problems = List.copyOf(problems);
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }

        public Result(Map<FrameType, DroneLayout> layouts, List<String> problems) {
            this(layouts, problems, Map.of());
        }
    }
}
