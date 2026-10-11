package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.StackDefinition;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class StackType implements PartInfo {
    public static final StackType AIO_WHOOP = builtin("aio_whoop");
    public static final StackType AIO_20 = builtin("aio_20");
    public static final StackType STACK_30 = builtin("stack_30");
    public static final StackType CREATIVE_FC = builtin("creative_fc");

    private final StackDefinition definition;
    private final String name;
    private final boolean missing;
    private final String key;
    private final double massGrams;
    public final MountSize.Stack mount;
    public final double escCurrentPerMotor;
    public final int minCells;
    public final int maxCells;

    StackType(StackDefinition definition, @Nullable String name) {
        this(definition, name, false);
    }

    StackType(StackDefinition definition, @Nullable String name, boolean missing) {
        this.definition = definition;
        this.missing = missing;
        this.name = missing ? PartIndex.MISSING_NAME : name != null ? name : definition.id().getPath().toUpperCase(Locale.ROOT);
        this.key = PartIndex.keyOf(definition.id());
        this.massGrams = definition.basics().massGrams();
        this.mount = MountSize.Stack.of(definition.mount());
        this.escCurrentPerMotor = definition.escContinuousAmps();
        this.minCells = definition.minCells();
        this.maxCells = definition.maxCells();
    }

    private static StackType builtin(String path) {
        StackType stack = new StackType(BuiltinParts.load(PartKind.STACK, path, StackDefinition.class), null);
        PartIndex.addBuiltin(stack);
        return stack;
    }

    public static StackType[] values() {
        return PartIndex.<StackType>typed(PartKind.STACK).toArray(new StackType[0]);
    }

    public static List<StackType> all() {
        return PartIndex.typed(PartKind.STACK);
    }

    public static Optional<StackType> byId(Identifier id) {
        return PartIndex.byId(StackType.class, id);
    }

    public static Optional<StackType> byKey(String key) {
        return PartIndex.byKey(StackType.class, key);
    }

    public boolean creative() {
        return this == CREATIVE_FC;
    }

    public boolean fits(FrameType frame) {
        return this.creative() || frame.stackMounts.contains(this.mount);
    }

    @Override
    public StackDefinition definition() {
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
