package de.lardtom.propwash.creative;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class CreativeLimits {
    private CreativeLimits() {
    }

    public enum Type {
        CLAMPED,
        UNKNOWN_VALUE,
        UNKNOWN_PART
    }

    public record Note(Type type, String key, @Nullable CreativeParam param, double requested, double applied) {
        public String translationKey() {
            return "propwash.creative.note." + this.type.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Result(CreativeSpec spec, List<Note> notes) {
        public boolean changed() {
            return !this.notes.isEmpty();
        }
    }

    public static Result sanitize(CreativeSpec spec) {
        List<Note> notes = new ArrayList<>();
        EnumMap<PartKind, String> base = new EnumMap<>(PartKind.class);
        for (Map.Entry<PartKind, String> entry : spec.base().entrySet()) {
            if (resolves(entry.getKey(), entry.getValue())) {
                base.put(entry.getKey(), entry.getValue());
            } else {
                notes.add(new Note(Type.UNKNOWN_PART, entry.getValue(), null, Double.NaN, Double.NaN));
            }
        }
        TreeMap<String, Double> values = new TreeMap<>();
        for (Map.Entry<String, Double> entry : spec.values().entrySet()) {
            Optional<CreativeParam> param = CreativeParam.byKey(entry.getKey());
            if (param.isEmpty()) {
                notes.add(new Note(Type.UNKNOWN_VALUE, entry.getKey(), null, entry.getValue(), Double.NaN));
                continue;
            }
            double requested = entry.getValue();
            double applied = param.get().clamp(requested);
            if (Math.abs(applied - requested) > tolerance(param.get())) {
                notes.add(new Note(Type.CLAMPED, entry.getKey(), param.get(), requested, applied));
            }
            values.put(entry.getKey(), applied);
        }
        return new Result(new CreativeSpec(base, values), List.copyOf(notes));
    }

    public static double tolerance(CreativeParam param) {
        return param.integer() ? 0.5 : Math.pow(10.0, -param.decimals() - 1);
    }

    public static boolean resolves(PartKind kind, String key) {
        return part(kind, key).isPresent();
    }

    public static Optional<? extends PartInfo> part(PartKind kind, String key) {
        return switch (kind) {
            case FRAME -> CreativeFlight.lookup(FrameType.class, key);
            case STACK -> CreativeFlight.lookup(StackType.class, key);
            case MOTOR -> CreativeFlight.lookup(MotorType.class, key);
            case PROP -> CreativeFlight.lookup(PropType.class, key);
            case BATTERY -> CreativeFlight.lookup(BatteryType.class, key);
            case VIDEO, ACCESSORY -> Optional.empty();
        };
    }
}
