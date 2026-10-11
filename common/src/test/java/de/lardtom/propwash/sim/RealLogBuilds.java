package de.lardtom.propwash.sim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RealLogBuilds {
    static final double MASS_TOLERANCE_G = 0.05;
    static final double MIN_FRAME_G = 0.5;
    static final String[] KINDS = {"frame", "stack", "motor", "prop", "video", "battery"};

    private RealLogBuilds() {
    }

    public record Built(DroneBuild build, double massG, String frameNote) {
    }

    public static boolean needsAddon(JsonObject parts) {
        for (String kind : KINDS) {
            JsonElement e = parts.get(kind);
            String id = e == null ? "" : e.isJsonPrimitive() ? e.getAsString() : e.getAsJsonObject().get("base").getAsString();
            if (id.startsWith("justmoreparts:")) {
                return true;
            }
        }
        return false;
    }

    public static Optional<String> missingAddon(JsonObject parts) {
        if (needsAddon(parts) && CalibrationHarness.addonRoot().isEmpty()) {
            return Optional.of(CalibrationHarness.ADDON_HINT);
        }
        CalibrationHarness.addons();
        return Optional.empty();
    }

    public static Built build(String logId, JsonObject parts, double targetMassG) {
        CalibrationHarness.addons();
        JsonObject frameJson = json("frame", parts.get("frame"));
        DroneBuild first = assemble(logId, parts, frameJson);
        double mass = AirframeSpec.of(first).massKg * 1000.0;
        double frameMass = frameJson.get("mass_g").getAsDouble();
        double wanted = frameMass + (targetMassG - mass);
        String note = String.format(Locale.ROOT, "Rahmen %.1f g → %.1f g", frameMass, wanted);
        if (wanted < MIN_FRAME_G) {
            throw new IllegalStateException(logId + ": Teile sind schwerer als das Abfluggewicht (" + note + ")");
        }
        frameJson.addProperty("mass_g", Math.round(wanted * 100.0) / 100.0);
        DroneBuild build = assemble(logId, parts, frameJson);
        double got = AirframeSpec.of(build).massKg * 1000.0;
        if (Math.abs(got - targetMassG) > MASS_TOLERANCE_G * 10.0) {
            throw new IllegalStateException(logId + ": Gewicht " + got + " g statt " + targetMassG + " g");
        }
        return new Built(build, got, note);
    }

    static DroneBuild assemble(String logId, JsonObject parts, JsonObject frameJson) {
        String tag = logId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        FrameType frame = CalibrationHarness.fixture(PartKind.FRAME, tag + "_frame", frameJson.toString());
        StackType stack = part(PartKind.STACK, tag, parts.get("stack"), StackType::byId);
        MotorType motor = part(PartKind.MOTOR, tag, parts.get("motor"), MotorType::byId);
        PropType prop = part(PartKind.PROP, tag, parts.get("prop"), PropType::byId);
        VideoType video = part(PartKind.VIDEO, tag, parts.get("video"), VideoType::byId);
        BatteryType battery = part(PartKind.BATTERY, tag, parts.get("battery"), BatteryType::byId);
        return new DroneBuild(frame, stack, motor, prop, video, battery);
    }

    interface Lookup<T> {
        Optional<T> byId(net.minecraft.resources.Identifier id);
    }

    static <T> T part(PartKind kind, String tag, JsonElement spec, Lookup<T> lookup) {
        if (spec == null) {
            throw new IllegalArgumentException(tag + ": Teil fehlt: " + kind);
        }
        if (spec.isJsonPrimitive()) {
            String id = spec.getAsString();
            return lookup.byId(CalibrationHarness.id("propwash", id))
                    .orElseThrow(() -> new IllegalArgumentException(tag + ": unbekanntes Teil " + id));
        }
        return CalibrationHarness.fixture(kind, tag + "_" + kind.name().toLowerCase(Locale.ROOT), json(kind.name().toLowerCase(Locale.ROOT), spec).toString());
    }

    static JsonObject json(String kind, JsonElement spec) {
        String base = spec.isJsonPrimitive() ? spec.getAsString() : spec.getAsJsonObject().get("base").getAsString();
        JsonObject out = JsonParser.parseString(baseText(kind, base)).getAsJsonObject();
        if (spec.isJsonObject() && spec.getAsJsonObject().has("set")) {
            for (Map.Entry<String, JsonElement> e : spec.getAsJsonObject().getAsJsonObject("set").entrySet()) {
                out.add(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    static String baseText(String kind, String id) {
        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "propwash";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String rel = "data/" + ns + "/propwash_parts/" + kind + "/" + path + ".json";
        if (ns.equals("propwash")) {
            try (InputStream in = RealLogBuilds.class.getClassLoader().getResourceAsStream(rel)) {
                if (in == null) {
                    throw new IllegalArgumentException("Teil nicht gefunden: " + id);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Path root = CalibrationHarness.addonRoot().orElseThrow(() -> new IllegalStateException(CalibrationHarness.ADDON_HINT));
        try {
            return Files.readString(root.resolve(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
