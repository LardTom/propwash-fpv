package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PropDefinition;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PropType implements PartInfo {
    public static final PropType P31_4B = builtin("p31_4b");
    public static final PropType P31_3B = builtin("p31_3b");
    public static final PropType P3_3B = builtin("p3_3b");
    public static final PropType P51_3B = builtin("p51_3b");
    public static final PropType P7_2B = builtin("p7_2b");

    private final PropDefinition definition;
    private final String name;
    private final boolean missing;
    private final String key;
    public final int diameterMm;
    public final double pitchInch;
    public final int blades;
    private final double massGrams;
    public final double thrustCoefficient;
    public final double powerCoefficient;

    PropType(PropDefinition definition, @Nullable String name) {
        this(definition, name, false);
    }

    PropType(PropDefinition definition, @Nullable String name, boolean missing) {
        this.definition = definition;
        this.missing = missing;
        this.name = missing ? PartIndex.MISSING_NAME : name != null ? name : definition.id().getPath().toUpperCase(Locale.ROOT);
        this.key = PartIndex.keyOf(definition.id());
        this.diameterMm = definition.diameterMm();
        this.pitchInch = definition.pitchInch();
        this.blades = definition.blades();
        this.massGrams = definition.basics().massGrams();
        this.thrustCoefficient = definition.thrustCoefficient();
        this.powerCoefficient = definition.powerCoefficient();
    }

    private static PropType builtin(String path) {
        PropType prop = new PropType(BuiltinParts.load(PartKind.PROP, path, PropDefinition.class), null);
        PartIndex.addBuiltin(prop);
        return prop;
    }

    public static PropType[] values() {
        return PartIndex.<PropType>typed(PartKind.PROP).toArray(new PropType[0]);
    }

    public static List<PropType> all() {
        return PartIndex.typed(PartKind.PROP);
    }

    public static Optional<PropType> byId(Identifier id) {
        return PartIndex.byId(PropType.class, id);
    }

    public static Optional<PropType> byKey(String key) {
        return PartIndex.byKey(PropType.class, key);
    }

    public double diameterMeters() {
        return this.diameterMm / 1000.0;
    }

    @Override
    public PropDefinition definition() {
        return this.definition;
    }

    @Override
    public Identifier id() {
        return this.definition.id();
    }

    @Override
    public String key() {
        return this.key;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public boolean missing() {
        return this.missing;
    }

    @Override
    public double massGrams() {
        return this.massGrams;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
