package de.lardtom.propwash.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public record Accessories(Map<AccessoryMount, AccessoryType> byMount) {
    public static final Accessories NONE = new Accessories(Map.of());

    public Accessories {
        EnumMap<AccessoryMount, AccessoryType> copy = new EnumMap<>(AccessoryMount.class);
        for (Map.Entry<AccessoryMount, AccessoryType> entry : byMount.entrySet()) {
            AccessoryMount mount = entry.getKey();
            AccessoryType type = entry.getValue();
            if (mount == null || type == null) {
                throw new IllegalArgumentException("Zubehör ohne Halterung oder Typ");
            }
            if (!type.fits(mount)) {
                throw new IllegalArgumentException(type.key() + " passt nicht an die Halterung " + mount.key());
            }
            copy.put(mount, type);
        }
        byMount = Collections.unmodifiableMap(copy);
    }

    public static Accessories of(AccessoryType... types) {
        Accessories result = NONE;
        for (AccessoryType type : types) {
            result = result.with(type);
        }
        return result;
    }

    public static Accessories of(List<AccessoryType> types) {
        return of(types.toArray(new AccessoryType[0]));
    }

    public Optional<AccessoryType> at(AccessoryMount mount) {
        return Optional.ofNullable(this.byMount.get(mount));
    }

    public boolean has(AccessoryType type) {
        return this.byMount.get(type.mount) == type;
    }

    public boolean isEmpty() {
        return this.byMount.isEmpty();
    }

    public int size() {
        return this.byMount.size();
    }

    public Accessories with(AccessoryType type) {
        EnumMap<AccessoryMount, AccessoryType> copy = new EnumMap<>(AccessoryMount.class);
        copy.putAll(this.byMount);
        copy.put(type.mount, type);
        return new Accessories(copy);
    }

    public Accessories without(AccessoryMount mount) {
        if (!this.byMount.containsKey(mount)) {
            return this;
        }
        EnumMap<AccessoryMount, AccessoryType> copy = new EnumMap<>(AccessoryMount.class);
        copy.putAll(this.byMount);
        copy.remove(mount);
        return new Accessories(copy);
    }

    public List<AccessoryType> list() {
        return List.copyOf(this.byMount.values());
    }

    public List<AccessoryMount> mounts() {
        return new ArrayList<>(this.byMount.keySet());
    }

    public Optional<AccessoryType> camera() {
        for (AccessoryType type : this.byMount.values()) {
            if (type.isCamera()) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public Optional<AccessoryType> flashbackCam() {
        for (AccessoryType type : this.byMount.values()) {
            if (type.isFlashbackCam()) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public double massGrams() {
        double sum = 0.0;
        for (AccessoryType type : this.byMount.values()) {
            sum += type.massGrams();
        }
        return sum;
    }

    public double flightBatteryWatts() {
        double sum = 0.0;
        for (AccessoryType type : this.byMount.values()) {
            sum += type.flightBatteryWatts;
        }
        return sum;
    }

    public double frontalAreaM2() {
        double sum = 0.0;
        for (AccessoryType type : this.byMount.values()) {
            sum += type.frontalAreaM2;
        }
        return sum;
    }

    public String key() {
        StringJoiner joiner = new StringJoiner("+");
        for (Map.Entry<AccessoryMount, AccessoryType> entry : this.byMount.entrySet()) {
            joiner.add(entry.getKey().key() + "=" + entry.getValue().key());
        }
        return joiner.toString();
    }
}
