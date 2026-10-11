package de.lardtom.propwash.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.platform.Services;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class FpvLayoutData {
    public static final String FILE = "drone_layout.json";
    public static final int SLOTS = 4;
    static final double MAX_ABS_MM = 2000.0;

    private static final Map<String, Source> SOURCES = new ConcurrentHashMap<>();
    private static volatile Function<String, Optional<byte[]>> reader = FpvLayoutData::readFromMods;

    private FpvLayoutData() {
    }

    public record Frame(double @Nullable [][] motors, double propHeight, boolean valid) {
    }

    public record Source(Map<Identifier, Double> seats, Map<Identifier, Frame> frames) {
        public static final Source EMPTY = new Source(Map.of(), Map.of());
    }

    public record View(Map<Identifier, Double> seats, Map<Identifier, Frame> frames) {
        public double seat(Identifier motor) {
            Double seat = this.seats.get(motor);
            return seat == null ? Double.NaN : seat;
        }

        public Optional<Frame> frame(Identifier frame) {
            return Optional.ofNullable(this.frames.get(frame));
        }
    }

    public static View view(Collection<String> namespaces) {
        TreeSet<String> sorted = new TreeSet<>(namespaces);
        sorted.remove(PartIndex.DEFAULT_NAMESPACE);
        List<String> ordered = new ArrayList<>();
        ordered.add(PartIndex.DEFAULT_NAMESPACE);
        ordered.addAll(sorted);
        Map<Identifier, Double> seats = new LinkedHashMap<>();
        Map<Identifier, Frame> frames = new LinkedHashMap<>();
        for (String namespace : ordered) {
            Source source = source(namespace);
            seats.putAll(source.seats());
            frames.putAll(source.frames());
        }
        return new View(Collections.unmodifiableMap(seats), Collections.unmodifiableMap(frames));
    }

    public static Source source(String namespace) {
        return SOURCES.computeIfAbsent(namespace, FpvLayoutData::load);
    }

    public static void setReader(Function<String, Optional<byte[]>> newReader) {
        reader = newReader;
        SOURCES.clear();
    }

    public static void reset() {
        reader = FpvLayoutData::readFromMods;
        SOURCES.clear();
    }

    private static Source load(String namespace) {
        Optional<byte[]> bytes;
        try {
            bytes = reader.apply(namespace);
        } catch (RuntimeException e) {
            Constants.LOG.warn("FPV-Anordnung: {} von {} nicht lesbar ({})", FILE, namespace, e.toString());
            return Source.EMPTY;
        }
        if (bytes.isEmpty()) {
            return Source.EMPTY;
        }
        try (Reader in = new InputStreamReader(new ByteArrayInputStream(bytes.get()), StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(in), namespace);
        } catch (IOException | RuntimeException e) {
            Constants.LOG.warn("FPV-Anordnung: {} von {} ist kein gültiges JSON ({})", FILE, namespace, e.toString());
            return Source.EMPTY;
        }
    }

    public static Source parse(JsonElement root, String namespace) {
        if (root == null || !root.isJsonObject()) {
            return Source.EMPTY;
        }
        JsonObject object = root.getAsJsonObject();
        Map<Identifier, Double> seats = new LinkedHashMap<>();
        if (object.get("motor_seat") instanceof JsonObject seatObject) {
            for (Map.Entry<String, JsonElement> entry : seatObject.entrySet()) {
                Identifier id = resolve(entry.getKey(), namespace);
                if (id != null && entry.getValue() instanceof JsonPrimitive primitive && primitive.isNumber()) {
                    float seat = primitive.getAsFloat();
                    if (Float.isFinite(seat) && seat > 0.0F && seat < 100.0F) {
                        seats.put(id, (double) seat);
                    }
                }
            }
        }
        Map<Identifier, Frame> frames = new LinkedHashMap<>();
        if (object.get("frames") instanceof JsonObject frameObject) {
            for (Map.Entry<String, JsonElement> entry : frameObject.entrySet()) {
                Identifier id = resolve(entry.getKey(), namespace);
                if (id != null && entry.getValue() instanceof JsonObject frame) {
                    frames.put(id, frame(frame));
                }
            }
        }
        return new Source(Collections.unmodifiableMap(seats), Collections.unmodifiableMap(frames));
    }

    private static Frame frame(JsonObject frame) {
        double[][] motors = null;
        boolean valid = true;
        JsonElement element = frame.get("motors");
        if (element != null) {
            if (element instanceof JsonArray array && array.size() == SLOTS) {
                motors = new double[SLOTS][];
                for (int i = 0; i < SLOTS; i++) {
                    motors[i] = vector(array.get(i));
                    valid &= motors[i] != null;
                }
            } else {
                valid = false;
            }
        }
        double propHeight = Double.NaN;
        if (frame.get("prop_height") instanceof JsonPrimitive primitive && primitive.isNumber()) {
            float value = primitive.getAsFloat();
            if (Float.isFinite(value) && value >= 0.0F && value <= 200.0F) {
                propHeight = value;
            }
        }
        return new Frame(valid ? motors : null, propHeight, valid);
    }

    private static double @Nullable [] vector(JsonElement element) {
        if (!(element instanceof JsonArray array) || array.size() != 3) {
            return null;
        }
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(array.get(i) instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
                return null;
            }
            float value = primitive.getAsFloat();
            if (!Float.isFinite(value) || Math.abs(value) > MAX_ABS_MM) {
                return null;
            }
            out[i] = value;
        }
        return out;
    }

    static @Nullable Identifier resolve(String key, String namespace) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (key.indexOf(':') >= 0) {
            return Identifier.tryParse(key);
        }
        return Identifier.tryBuild(namespace, key);
    }

    static Optional<byte[]> readFromMods(String namespace) {
        String path = "assets/" + namespace + "/" + FILE;
        if (!PartIndex.DEFAULT_NAMESPACE.equals(namespace)) {
            try {
                if (Services.PLATFORM.isModLoaded(namespace)) {
                    SortedMap<String, byte[]> files = Services.PLATFORM.readModResources(namespace, "assets/" + namespace, FILE);
                    byte[] bytes = files.get(path);
                    if (bytes != null) {
                        return Optional.of(bytes);
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                Constants.LOG.debug("FPV-Anordnung: {} über die Plattform nicht lesbar ({})", path, e.toString());
            }
        }
        try (InputStream in = FpvLayoutData.class.getResourceAsStream("/" + path)) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        } catch (IOException e) {
            Constants.LOG.warn("FPV-Anordnung: {} nicht lesbar ({})", path, e.toString());
            return Optional.empty();
        }
    }
}
