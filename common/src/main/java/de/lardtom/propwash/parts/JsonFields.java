package de.lardtom.propwash.parts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.propwash.build.PartIndex;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

final class JsonFields {
    private final JsonObject json;
    private final String path;
    private final PartDataProblems problems;
    private final Set<String> used = new HashSet<>();

    JsonFields(JsonObject json, String path, PartDataProblems problems) {
        this.json = json;
        this.path = path;
        this.problems = problems;
    }

    PartDataProblems problems() {
        return this.problems;
    }

    String at(String key) {
        return this.path.isEmpty() ? key : this.path + "." + key;
    }

    boolean has(String key) {
        JsonElement element = this.json.get(key);
        return element != null && !element.isJsonNull();
    }

    void error(String key, String message) {
        this.problems.error(this.at(key), message);
    }

    private JsonElement take(String key) {
        this.used.add(key);
        JsonElement element = this.json.get(key);
        return element == null || element.isJsonNull() ? null : element;
    }

    private void missing(String key) {
        this.error(key, "Pflichtfeld fehlt");
    }

    OptionalDouble optDouble(String key) {
        JsonElement element = this.take(key);
        if (element == null) {
            return OptionalDouble.empty();
        }
        OptionalDouble value = number(element);
        if (value.isEmpty()) {
            this.error(key, "Zahl erwartet, gefunden: " + element);
        }
        return value;
    }

    double requireDouble(String key) {
        if (!this.has(key)) {
            this.take(key);
            this.missing(key);
            return Double.NaN;
        }
        return this.optDouble(key).orElse(Double.NaN);
    }

    double doubleOr(String key, double fallback) {
        return this.optDouble(key).orElse(fallback);
    }

    OptionalInt optInt(String key) {
        JsonElement element = this.take(key);
        if (element == null) {
            return OptionalInt.empty();
        }
        OptionalInt value = integer(element);
        if (value.isEmpty()) {
            this.error(key, "ganze Zahl erwartet, gefunden: " + element);
        }
        return value;
    }

    int requireInt(String key) {
        if (!this.has(key)) {
            this.take(key);
            this.missing(key);
            return 0;
        }
        return this.optInt(key).orElse(0);
    }

    int intOr(String key, int fallback) {
        return this.optInt(key).orElse(fallback);
    }

    boolean boolOr(String key, boolean fallback) {
        JsonElement element = this.take(key);
        if (element == null) {
            return fallback;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        this.error(key, "true oder false erwartet, gefunden: " + element);
        return fallback;
    }

    Optional<Boolean> optBool(String key) {
        if (!this.has(key)) {
            this.take(key);
            return Optional.empty();
        }
        return Optional.of(this.boolOr(key, false));
    }

    Optional<String> optString(String key) {
        JsonElement element = this.take(key);
        if (element == null) {
            return Optional.empty();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return Optional.of(element.getAsString());
        }
        this.error(key, "Text erwartet, gefunden: " + element);
        return Optional.empty();
    }

    String requireString(String key) {
        if (!this.has(key)) {
            this.take(key);
            this.missing(key);
            return "";
        }
        return this.optString(key).orElse("");
    }

    Optional<Identifier> optId(String key) {
        return this.optString(key).flatMap(text -> {
            Identifier id = parseId(text);
            if (id == null) {
                this.error(key, "ungültige Id \"" + text + "\"");
                return Optional.empty();
            }
            return Optional.of(id);
        });
    }

    Identifier requireId(String key) {
        if (!this.has(key)) {
            this.take(key);
            this.missing(key);
            return null;
        }
        return this.optId(key).orElse(null);
    }

    List<Identifier> requireIdList(String key) {
        JsonElement element = this.take(key);
        if (element == null) {
            this.missing(key);
            return List.of();
        }
        List<Identifier> out = new ArrayList<>();
        List<JsonElement> items = element.isJsonArray() ? asList(element.getAsJsonArray()) : List.of(element);
        if (items.isEmpty()) {
            this.error(key, "mindestens eine Id erwartet");
        }
        for (int i = 0; i < items.size(); i++) {
            JsonElement item = items.get(i);
            String where = element.isJsonArray() ? key + "[" + i + "]" : key;
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                this.error(where, "Id als Text erwartet, gefunden: " + item);
                continue;
            }
            Identifier id = parseId(item.getAsString());
            if (id == null) {
                this.error(where, "ungültige Id \"" + item.getAsString() + "\"");
            } else {
                out.add(id);
            }
        }
        return out;
    }

    <E> List<E> enumList(String key, Function<String, Optional<E>> byKey, List<String> allowed) {
        JsonElement element = this.take(key);
        if (element == null) {
            return List.of();
        }
        List<E> out = new ArrayList<>();
        List<JsonElement> items = element.isJsonArray() ? asList(element.getAsJsonArray()) : List.of(element);
        for (JsonElement item : items) {
            Optional<E> value = item.isJsonPrimitive() && item.getAsJsonPrimitive().isString() ? byKey.apply(item.getAsString()) : Optional.empty();
            if (value.isEmpty()) {
                this.error(key, "unbekannter Wert " + item + ", erlaubt: " + String.join(", ", allowed));
            } else {
                out.add(value.get());
            }
        }
        return out;
    }

    <E> Optional<E> optEnum(String key, Function<String, Optional<E>> byKey, List<String> allowed) {
        return this.optString(key).flatMap(text -> {
            Optional<E> value = byKey.apply(text);
            if (value.isEmpty()) {
                this.error(key, "unbekannter Wert \"" + text + "\", erlaubt: " + String.join(", ", allowed));
            }
            return value;
        });
    }

    <E> E requireEnum(String key, Function<String, Optional<E>> byKey, List<String> allowed) {
        if (!this.has(key)) {
            this.take(key);
            this.missing(key);
            return null;
        }
        return this.optEnum(key, byKey, allowed).orElse(null);
    }

    Optional<JsonFields> optObject(String key) {
        JsonElement element = this.take(key);
        if (element == null) {
            return Optional.empty();
        }
        if (!element.isJsonObject()) {
            this.error(key, "Objekt erwartet, gefunden: " + element);
            return Optional.empty();
        }
        return Optional.of(new JsonFields(element.getAsJsonObject(), this.at(key), this.problems));
    }

    JsonFields requireObject(String key) {
        if (!this.has(key)) {
            this.take(key);
            this.missing(key);
            return null;
        }
        return this.optObject(key).orElse(null);
    }

    Optional<JsonElement> raw(String key) {
        return Optional.ofNullable(this.take(key));
    }

    Optional<double[]> optVector(String key, int size) {
        JsonElement element = this.take(key);
        if (element == null) {
            return Optional.empty();
        }
        double[] values = numbers(element, size);
        if (values == null) {
            this.error(key, size + " Zahlen erwartet, gefunden: " + element);
            return Optional.empty();
        }
        return Optional.of(values);
    }

    double[] requireVector(String key, int size) {
        if (!this.has(key)) {
            this.take(key);
            this.missing(key);
            return null;
        }
        return this.optVector(key, size).orElse(null);
    }

    Optional<int[]> optRange(String key) {
        JsonElement element = this.take(key);
        if (element == null) {
            return Optional.empty();
        }
        OptionalInt single = integer(element);
        if (single.isPresent()) {
            return Optional.of(new int[]{single.getAsInt(), single.getAsInt()});
        }
        double[] values = numbers(element, 2);
        if (values == null || values[0] != Math.rint(values[0]) || values[1] != Math.rint(values[1])) {
            this.error(key, "[min, max] als ganze Zahlen erwartet, gefunden: " + element);
            return Optional.empty();
        }
        return Optional.of(new int[]{(int) values[0], (int) values[1]});
    }

    Optional<int[]> optIntList(String key) {
        JsonElement element = this.take(key);
        if (element == null) {
            return Optional.empty();
        }
        if (!element.isJsonArray()) {
            this.error(key, "Liste ganzer Zahlen erwartet, gefunden: " + element);
            return Optional.empty();
        }
        JsonArray array = element.getAsJsonArray();
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            OptionalInt value = integer(array.get(i));
            if (value.isEmpty()) {
                this.error(key, "Liste ganzer Zahlen erwartet, gefunden: " + element);
                return Optional.empty();
            }
            out[i] = value.getAsInt();
        }
        return Optional.of(out);
    }

    int[] requireRange(String key) {
        if (!this.has(key)) {
            this.take(key);
            this.missing(key);
            return null;
        }
        return this.optRange(key).orElse(null);
    }

    Set<String> keys() {
        return this.json.keySet();
    }

    void finish() {
        List<String> unknown = new ArrayList<>();
        for (String key : this.json.keySet()) {
            if (!this.used.contains(key)) {
                unknown.add(key);
            }
        }
        unknown.sort(String::compareTo);
        for (String key : unknown) {
            this.problems.warning(this.at(key), "unbekanntes Feld wird ignoriert");
        }
    }

    static Identifier parseId(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.equals(text)) {
            return null;
        }
        return text.indexOf(':') >= 0 ? Identifier.tryParse(text) : Identifier.tryBuild(PartIndex.DEFAULT_NAMESPACE, text);
    }

    static OptionalDouble number(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
        }
        return OptionalDouble.empty();
    }

    static OptionalInt integer(JsonElement element) {
        OptionalDouble value = number(element);
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        double d = value.getAsDouble();
        if (d != Math.rint(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) d);
    }

    static double[] numbers(JsonElement element, int size) {
        if (!element.isJsonArray() || element.getAsJsonArray().size() != size) {
            return null;
        }
        double[] out = new double[size];
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < size; i++) {
            OptionalDouble value = number(array.get(i));
            if (value.isEmpty()) {
                return null;
            }
            out[i] = value.getAsDouble();
        }
        return out;
    }

    private static List<JsonElement> asList(JsonArray array) {
        List<JsonElement> out = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            out.add(element);
        }
        return out;
    }

    static <E> List<String> keysOf(E[] values, Function<E, String> key) {
        return Arrays.stream(values).map(key).toList();
    }
}
