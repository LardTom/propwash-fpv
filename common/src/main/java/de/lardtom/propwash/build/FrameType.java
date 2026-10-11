package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FrameType implements PartInfo {
    public static final FrameType WHOOP_65 = builtin("whoop_65");
    public static final FrameType CINEWHOOP_3 = builtin("cinewhoop_3");
    public static final FrameType FREESTYLE_5 = builtin("freestyle_5");
    public static final FrameType RACE_5 = builtin("race_5");
    public static final FrameType LONG_RANGE_7 = builtin("long_range_7");

    private final FrameDefinition definition;
    private final String name;
    private final boolean missing;
    private final String key;
    public final int wheelbaseMm;
    private final double massGrams;
    public final boolean ducted;
    public final int minPropMm;
    public final int maxPropMm;
    public final Set<MountSize.Motor> motorMounts;
    public final Set<MountSize.Stack> stackMounts;
    public final double frontalAreaM2;
    public final double dragCoefficient;
    public final double impactTolerance;
    public final Set<AccessoryMount> accessoryMounts;
    private final BatteryFit.Rule batteryFitRule;
    private final Map<AccessoryMount, AccessoryAnchor> accessoryAnchors;

    FrameType(FrameDefinition definition, @Nullable String name) {
        this(definition, name, false);
    }

    FrameType(FrameDefinition definition, @Nullable String name, boolean missing) {
        this.definition = definition;
        this.missing = missing;
        this.name = missing ? PartIndex.MISSING_NAME : name != null ? name : definition.id().getPath().toUpperCase(Locale.ROOT);
        this.key = PartIndex.keyOf(definition.id());
        this.wheelbaseMm = definition.wheelbaseMm();
        this.massGrams = definition.basics().massGrams();
        this.ducted = definition.ducted();
        this.minPropMm = definition.minPropMm();
        this.maxPropMm = definition.maxPropMm();
        this.motorMounts = Collections.unmodifiableSet(new LinkedHashSet<>(MountSize.motors(definition.motorMounts())));
        this.stackMounts = Collections.unmodifiableSet(new LinkedHashSet<>(MountSize.stacks(definition.stackMounts())));
        this.frontalAreaM2 = definition.aero().frontalAreaM2();
        this.dragCoefficient = definition.aero().dragCoefficient();
        this.impactTolerance = definition.impactTolerance();
        EnumSet<AccessoryMount> mounts = EnumSet.noneOf(AccessoryMount.class);
        for (AccessorySlot slot : definition.accessoryMounts()) {
            mounts.add(AccessoryMount.of(slot));
        }
        this.accessoryMounts = Collections.unmodifiableSet(mounts);
        BatteryFitRule fit = definition.batteryFit();
        this.batteryFitRule = new BatteryFit.Rule(fit.minCells(), fit.maxCells(), fit.maxMassGrams(), fit.preferredMinCells(),
                fit.preferredMinCapacityMah());
        EnumMap<AccessoryMount, AccessoryAnchor> anchors = new EnumMap<>(AccessoryMount.class);
        for (Map.Entry<AccessorySlot, AnchorSpec> entry : definition.geometry().accessoryAnchors().entrySet()) {
            AnchorSpec spec = entry.getValue();
            anchors.put(AccessoryMount.of(entry.getKey()), new AccessoryAnchor(spec.x(), spec.y(), spec.z(), spec.tiltDeg(), spec.lengthMm()));
        }
        this.accessoryAnchors = Collections.unmodifiableMap(anchors);
    }

    private static FrameType builtin(String path) {
        FrameType frame = new FrameType(BuiltinParts.load(PartKind.FRAME, path, FrameDefinition.class), null);
        PartIndex.addBuiltin(frame);
        return frame;
    }

    public static FrameType[] values() {
        return PartIndex.<FrameType>typed(PartKind.FRAME).toArray(new FrameType[0]);
    }

    public static List<FrameType> all() {
        return PartIndex.typed(PartKind.FRAME);
    }

    public static Optional<FrameType> byId(Identifier id) {
        return PartIndex.byId(FrameType.class, id);
    }

    public static Optional<FrameType> byKey(String key) {
        return PartIndex.byKey(FrameType.class, key);
    }

    public double armLengthMeters() {
        return this.wheelbaseMm / 2000.0;
    }

    public BatteryFit.Rule batteryFitRule() {
        return this.batteryFitRule;
    }

    public Optional<AccessoryAnchor> accessoryAnchor(AccessoryMount mount) {
        return Optional.ofNullable(this.accessoryAnchors.get(mount));
    }

    @Override
    public FrameDefinition definition() {
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
