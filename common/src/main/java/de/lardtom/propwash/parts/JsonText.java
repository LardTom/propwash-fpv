package de.lardtom.propwash.parts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

public final class JsonText {
    private static final int INLINE_WIDTH = 100;

    private JsonText() {
    }

    public static String pretty(JsonElement element) {
        StringBuilder out = new StringBuilder();
        write(element, out, 0, true);
        out.append('\n');
        return out.toString();
    }

    public static String compact(JsonElement element) {
        StringBuilder out = new StringBuilder();
        inline(element, out);
        return out.toString();
    }

    public static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return new BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
    }

    private static void write(JsonElement element, StringBuilder out, int depth, boolean root) {
        if (!root && (element.isJsonObject() || element.isJsonArray())) {
            String single = compact(element);
            if (single.length() + depth * 2 <= INLINE_WIDTH && nesting(element) <= 2) {
                out.append(single);
                return;
            }
        }
        if (element instanceof JsonObject object) {
            if (object.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int i = 0;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                indent(out, depth + 1);
                out.append(new JsonPrimitive(entry.getKey())).append(": ");
                write(entry.getValue(), out, depth + 1, false);
                out.append(++i < object.size() ? ",\n" : "\n");
            }
            indent(out, depth);
            out.append('}');
        } else if (element instanceof JsonArray array) {
            if (array.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int i = 0; i < array.size(); i++) {
                indent(out, depth + 1);
                write(array.get(i), out, depth + 1, false);
                out.append(i + 1 < array.size() ? ",\n" : "\n");
            }
            indent(out, depth);
            out.append(']');
        } else {
            primitive(element, out);
        }
    }

    private static void inline(JsonElement element, StringBuilder out) {
        if (element instanceof JsonObject object) {
            out.append('{');
            int i = 0;
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (i++ > 0) {
                    out.append(", ");
                }
                out.append(new JsonPrimitive(entry.getKey())).append(": ");
                inline(entry.getValue(), out);
            }
            out.append('}');
        } else if (element instanceof JsonArray array) {
            out.append('[');
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                inline(array.get(i), out);
            }
            out.append(']');
        } else {
            primitive(element, out);
        }
    }

    private static void primitive(JsonElement element, StringBuilder out) {
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            Number value = primitive.getAsNumber();
            if (value instanceof Double || value instanceof Float) {
                out.append(number(value.doubleValue()));
                return;
            }
            out.append(value);
            return;
        }
        out.append(element);
    }

    private static int nesting(JsonElement element) {
        int deepest = 0;
        if (element instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                deepest = Math.max(deepest, nesting(entry.getValue()));
            }
            return deepest + 1;
        }
        if (element instanceof JsonArray array) {
            for (JsonElement child : array) {
                deepest = Math.max(deepest, nesting(child));
            }
            return deepest + 1;
        }
        return 0;
    }

    private static void indent(StringBuilder out, int depth) {
        out.append("  ".repeat(depth));
    }
}
