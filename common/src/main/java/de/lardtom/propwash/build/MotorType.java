package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MotorType implements PartInfo {
    public static final MotorType M0802_22000 = builtin("m0802_22000");
    public static final MotorType M1404_4600 = builtin("m1404_4600");
    public static final MotorType M2004_3000 = builtin("m2004_3000");
    public static final MotorType M2207_1750 = builtin("m2207_1750");
    public static final MotorType M2306_1860 = builtin("m2306_1860");
    public static final MotorType M2807_1300 = builtin("m2807_1300");

    private final MotorDefinition definition;
    private final String name;
    private final boolean missing;
    private final String key;
    public final String statorLabel;
    public final int kv;
    private final double massGrams;
    public final MountSize.Motor mount;
    public final double maxCurrent;
    public final double resistanceOhm;
    public final int minCells;
    public final int maxCells;

    MotorType(MotorDefinition definition, @Nullable String name) {
        this(definition, name, false);
    }

    MotorType(MotorDefinition definition, @Nullable String name, boolean missing) {
        this.definition = definition;
        this.missing = missing;
        this.name = missing ? PartIndex.MISSING_NAME : name != null ? name : definition.id().getPath().toUpperCase(Locale.ROOT);
        this.key = PartIndex.keyOf(definition.id());
        this.statorLabel = definition.statorLabel();
        this.kv = definition.kv();
        this.massGrams = definition.basics().massGrams();
        this.mount = MountSize.Motor.of(definition.mount());
        this.maxCurrent = definition.maxCurrentAmps();
        this.resistanceOhm = definition.resistanceOhm();
        this.minCells = definition.minCells();
        this.maxCells = definition.maxCells();
    }

    private static MotorType builtin(String path) {
        MotorType motor = new MotorType(BuiltinParts.load(PartKind.MOTOR, path, MotorDefinition.class), null);
        PartIndex.addBuiltin(motor);
        return motor;
    }

    public static MotorType[] values() {
        return PartIndex.<MotorType>typed(PartKind.MOTOR).toArray(new MotorType[0]);
    }

    public static List<MotorType> all() {
        return PartIndex.typed(PartKind.MOTOR);
    }

    public static Optional<MotorType> byId(Identifier id) {
        return PartIndex.byId(MotorType.class, id);
    }

    public static Optional<MotorType> byKey(String key) {
        return PartIndex.byKey(MotorType.class, key);
    }

    @Override
    public MotorDefinition definition() {
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
