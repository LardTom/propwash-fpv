package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BatteryType implements PartInfo {
    public static final BatteryType B1S_300_HV = builtin("b1s_300_hv");
    public static final BatteryType B4S_650 = builtin("b4s_650");
    public static final BatteryType B6S_1100 = builtin("b6s_1100");
    public static final BatteryType B6S_1300 = builtin("b6s_1300");
    public static final BatteryType B6S_4000_LION = builtin("b6s_4000_lion");

    public static final class Chemistry {
        public static final Chemistry LIPO = builtin("lipo");
        public static final Chemistry LIHV = builtin("lihv");
        public static final Chemistry LION = builtin("lion");

        private final CellChemistry definition;
        private final String name;
        public final double fullCellVoltage;
        public final double emptyCellVoltage;
        public final double cutoffCellVoltage;

        Chemistry(CellChemistry definition, @Nullable String name) {
            this.definition = definition;
            this.name = name != null ? name : definition.id().getPath().toUpperCase(Locale.ROOT);
            this.fullCellVoltage = definition.fullCellVoltage();
            this.emptyCellVoltage = definition.emptyCellVoltage();
            this.cutoffCellVoltage = definition.cutoffCellVoltage();
        }

        private static Chemistry builtin(String path) {
            Chemistry chemistry = new Chemistry(BuiltinParts.chemistry(path), null);
            PartIndex.addBuiltinChemistry(chemistry);
            return chemistry;
        }

        public static Chemistry[] values() {
            return PartIndex.chemistries().toArray(new Chemistry[0]);
        }

        public static Optional<Chemistry> byId(Identifier id) {
            return PartIndex.chemistry(id);
        }

        public CellChemistry definition() {
            return this.definition;
        }

        public Identifier id() {
            return this.definition.id();
        }

        public String key() {
            return PartIndex.keyOf(this.definition.id());
        }

        public String name() {
            return this.name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    private final BatteryDefinition definition;
    private final String name;
    private final boolean missing;
    private final String key;
    public final int cells;
    public final int capacityMah;
    private final double massGrams;
    public final double cellResistanceOhm;
    public final int cRating;
    public final Chemistry chemistry;

    BatteryType(BatteryDefinition definition, @Nullable String name) {
        this(definition, name, false);
    }

    BatteryType(BatteryDefinition definition, @Nullable String name, boolean missing) {
        this.definition = definition;
        this.missing = missing;
        this.name = missing ? PartIndex.MISSING_NAME : name != null ? name : definition.id().getPath().toUpperCase(Locale.ROOT);
        this.key = PartIndex.keyOf(definition.id());
        this.cells = definition.cells();
        this.capacityMah = definition.capacityMah();
        this.massGrams = definition.basics().massGrams();
        this.cellResistanceOhm = definition.cellResistanceOhm();
        this.cRating = definition.cRating();
        Chemistry.LIPO.definition();
        Chemistry resolved = PartIndex.chemistryDirect(definition.chemistry());
        if (resolved == null) {
            throw new IllegalArgumentException(definition.id() + ": unbekannte Zellchemie " + definition.chemistry());
        }
        this.chemistry = resolved;
    }

    private static BatteryType builtin(String path) {
        BatteryType battery = new BatteryType(BuiltinParts.load(PartKind.BATTERY, path, BatteryDefinition.class), null);
        PartIndex.addBuiltin(battery);
        return battery;
    }

    public static BatteryType[] values() {
        return PartIndex.<BatteryType>typed(PartKind.BATTERY).toArray(new BatteryType[0]);
    }

    public static List<BatteryType> all() {
        return PartIndex.typed(PartKind.BATTERY);
    }

    public static Optional<BatteryType> byId(Identifier id) {
        return PartIndex.byId(BatteryType.class, id);
    }

    public static Optional<BatteryType> byKey(String key) {
        return PartIndex.byKey(BatteryType.class, key);
    }

    public double maxContinuousCurrent() {
        return this.cRating * this.capacityMah / 1000.0;
    }

    @Override
    public BatteryDefinition definition() {
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
