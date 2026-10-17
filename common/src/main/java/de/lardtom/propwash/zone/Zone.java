package de.lardtom.propwash.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record Zone(int id, String dimension, String name, ZoneShape shape, ZoneBox box, int priority, boolean raceLegal, int transitionTenths,
                   Map<String, Integer> overrides) {
    public static final int NEW = 0;
    public static final String OVERWORLD = "minecraft:overworld";

    public static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(Zone::id),
            Codec.STRING.optionalFieldOf("dimension", OVERWORLD).forGetter(Zone::dimension),
            Codec.STRING.fieldOf("name").forGetter(Zone::name),
            Codec.STRING.optionalFieldOf("shape", ZoneShape.BOX.key()).forGetter(zone -> zone.shape().key()),
            ZoneBox.CODEC.fieldOf("box").forGetter(Zone::box),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Zone::priority),
            Codec.BOOL.optionalFieldOf("race_legal", false).forGetter(Zone::raceLegal),
            Codec.INT.optionalFieldOf("transition", ZoneLimits.DEFAULT_TRANSITION_TENTHS).forGetter(Zone::transitionTenths),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("overrides", Map.of()).forGetter(Zone::overrides)
    ).apply(i, (id, dimension, name, shape, box, priority, raceLegal, transition, overrides) -> new Zone(id, dimension, name,
            ZoneShape.byKey(shape), box, priority, raceLegal, transition, overrides)));

    public Zone {
        dimension = dimension == null || dimension.isEmpty() ? OVERWORLD : dimension;
        name = cleanName(name);
        shape = shape == null ? ZoneShape.BOX : shape;
        Objects.requireNonNull(box, "box");
        priority = Math.max(ZoneLimits.MIN_PRIORITY, Math.min(ZoneLimits.MAX_PRIORITY, priority));
        transitionTenths = Math.max(0, Math.min(ZoneLimits.MAX_TRANSITION_TENTHS, transitionTenths));
        overrides = normalize(overrides);
    }

    public static Zone create(String dimension, String name, ZoneShape shape, ZoneBox box) {
        return new Zone(NEW, dimension, name, shape, shape == ZoneShape.CYLINDER ? box.squared() : box, 0, false,
                ZoneLimits.DEFAULT_TRANSITION_TENTHS, Map.of());
    }

    public static String cleanName(@Nullable String name) {
        if (name == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        name.strip().codePoints().filter(c -> !Character.isISOControl(c) && c != '§').forEach(c -> {
            if (out.codePointCount(0, out.length()) < ZoneLimits.MAX_NAME) {
                out.appendCodePoint(c);
            }
        });
        return out.toString().strip();
    }

    public static Map<String, Integer> normalize(@Nullable Map<String, Integer> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            Integer value = overrides.get(spec.name());
            if (value != null && spec.valid(value)) {
                out.put(spec.name(), value);
            }
        }
        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
    }

    public static boolean overridable(RuleSpec spec) {
        return PhysicsRules.SPECS.contains(spec);
    }

    public boolean contains(double x, double y, double z) {
        return this.box.contains(this.shape, x, y, z);
    }

    public boolean overrides(RuleSpec spec) {
        return this.overrides.containsKey(spec.name());
    }

    public int value(RuleSpec spec, int fallback) {
        Integer value = this.overrides.get(spec.name());
        return value == null ? fallback : value;
    }

    public boolean changesPhysics() {
        for (RuleSpec spec : PhysicsRules.SPECS) {
            Integer value = this.overrides.get(spec.name());
            if (value != null && value != spec.defaultValue()) {
                return true;
            }
        }
        return false;
    }

    public boolean countsAsAssist() {
        return !this.raceLegal && this.changesPhysics();
    }

    public double transitionSeconds() {
        return this.transitionTenths / 10.0;
    }

    public Zone withId(int value) {
        return new Zone(value, this.dimension, this.name, this.shape, this.box, this.priority, this.raceLegal, this.transitionTenths, this.overrides);
    }

    public Zone withName(String value) {
        return new Zone(this.id, this.dimension, value, this.shape, this.box, this.priority, this.raceLegal, this.transitionTenths, this.overrides);
    }

    public Zone withShape(ZoneShape value) {
        ZoneBox shaped = value == ZoneShape.CYLINDER ? this.box.squared() : this.box;
        return new Zone(this.id, this.dimension, this.name, value, shaped, this.priority, this.raceLegal, this.transitionTenths, this.overrides);
    }

    public Zone withBox(ZoneBox value) {
        ZoneBox shaped = this.shape == ZoneShape.CYLINDER ? value.squared() : value;
        return new Zone(this.id, this.dimension, this.name, this.shape, shaped, this.priority, this.raceLegal, this.transitionTenths, this.overrides);
    }

    public Zone withPriority(int value) {
        return new Zone(this.id, this.dimension, this.name, this.shape, this.box, value, this.raceLegal, this.transitionTenths, this.overrides);
    }

    public Zone withRaceLegal(boolean value) {
        return new Zone(this.id, this.dimension, this.name, this.shape, this.box, this.priority, value, this.transitionTenths, this.overrides);
    }

    public Zone withTransition(int tenths) {
        return new Zone(this.id, this.dimension, this.name, this.shape, this.box, this.priority, this.raceLegal, tenths, this.overrides);
    }

    public Zone withDimension(String value) {
        return new Zone(this.id, value, this.name, this.shape, this.box, this.priority, this.raceLegal, this.transitionTenths, this.overrides);
    }

    public Zone withOverride(RuleSpec spec, int value) {
        Map<String, Integer> copy = new LinkedHashMap<>(this.overrides);
        copy.put(spec.name(), spec.clamp(value));
        return new Zone(this.id, this.dimension, this.name, this.shape, this.box, this.priority, this.raceLegal, this.transitionTenths, copy);
    }

    public Zone without(RuleSpec spec) {
        Map<String, Integer> copy = new LinkedHashMap<>(this.overrides);
        copy.remove(spec.name());
        return new Zone(this.id, this.dimension, this.name, this.shape, this.box, this.priority, this.raceLegal, this.transitionTenths, copy);
    }
}
