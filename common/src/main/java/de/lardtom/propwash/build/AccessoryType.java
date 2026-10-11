package de.lardtom.propwash.build;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AccessoryType implements PartInfo {
    public static final double HINGE_CLEARANCE_MM = 5.0;
    public static final Identifier FLASHBACK_CAM_ID = Constants.id("action_cam_flashback");
    public static final String FLASHBACK_MOD_ID = "flashback";
    public static final Identifier GRIPPER_ID = Constants.id("gripper");

    public static final AccessoryType LED_STRIP = builtin("led_strip");
    public static final AccessoryType ACTION_CAM_LIGHT = builtin("action_cam_light");
    public static final AccessoryType ACTION_CAM_HEAVY = builtin("action_cam_heavy");
    public static final AccessoryType ACTION_CAM_FLASHBACK = builtin("action_cam_flashback");
    public static final AccessoryType GRIPPER = builtin("gripper");

    private final AccessoryDefinition definition;
    private final String name;
    private final boolean missing;
    private final String key;
    public final AccessoryMount mount;
    private final double massGrams;
    public final double widthMm;
    public final double heightMm;
    public final double depthMm;
    public final double flightBatteryWatts;
    public final double frontalAreaM2;
    public final int ledCount;
    private final @Nullable ActionCamSpec camera;
    private final double lensX;
    public final double lensLengthMm;
    private final @Nullable AccessoryType unladen;
    private final Map<Long, AccessoryType> ladenVariants = new ConcurrentHashMap<>();

    AccessoryType(AccessoryDefinition definition, @Nullable String name) {
        this(definition, name, false);
    }

    AccessoryType(AccessoryDefinition definition, @Nullable String name, boolean missing) {
        this(definition, name, missing, null);
    }

    private AccessoryType(AccessoryDefinition definition, @Nullable String name, boolean missing, @Nullable AccessoryType unladen) {
        this.unladen = unladen;
        this.definition = definition;
        this.missing = missing;
        this.name = missing ? PartIndex.MISSING_NAME : name != null ? name : definition.id().getPath().toUpperCase(Locale.ROOT);
        this.key = PartIndex.keyOf(definition.id());
        this.mount = AccessoryMount.of(definition.slot());
        this.massGrams = definition.basics().massGrams();
        this.widthMm = definition.widthMm();
        this.heightMm = definition.heightMm();
        this.depthMm = definition.depthMm();
        this.flightBatteryWatts = definition.flightBatteryWatts();
        this.frontalAreaM2 = definition.frontalAreaM2();
        this.ledCount = definition.ledCount();
        this.camera = definition.actionCam().map(ActionCamSpec::of).orElse(null);
        this.lensX = definition.lensXMm();
        this.lensLengthMm = definition.lensLengthMm();
    }

    private static AccessoryType builtin(String path) {
        AccessoryType type = new AccessoryType(BuiltinParts.load(PartKind.ACCESSORY, path, AccessoryDefinition.class), null);
        PartIndex.addBuiltin(type);
        return type;
    }

    public static AccessoryType[] values() {
        return PartIndex.<AccessoryType>typed(PartKind.ACCESSORY).toArray(new AccessoryType[0]);
    }

    public static List<AccessoryType> all() {
        return PartIndex.typed(PartKind.ACCESSORY);
    }

    public static Optional<AccessoryType> byId(Identifier id) {
        return PartIndex.byId(AccessoryType.class, id);
    }

    public static Optional<AccessoryType> byKey(String key) {
        return PartIndex.byKey(AccessoryType.class, key);
    }

    public boolean fits(AccessoryMount mount) {
        return this.mount == mount;
    }

    public boolean isGripper() {
        return GRIPPER_ID.equals(this.id());
    }

    public boolean hangsStraight() {
        return this.isGripper();
    }

    public AccessoryType unladen() {
        return this.unladen == null ? this : this.unladen;
    }

    public double payloadGrams() {
        return this.unladen == null ? 0.0 : this.massGrams - this.unladen.massGrams;
    }

    public AccessoryType laden(long payloadGrams) {
        AccessoryType base = this.unladen();
        if (payloadGrams <= 0L) {
            return base;
        }
        return base.ladenVariants.computeIfAbsent(payloadGrams, grams -> {
            AccessoryDefinition d = base.definition;
            AccessoryDefinition heavier = new AccessoryDefinition(d.id(), PartBasics.withMass(d.basics(), base.massGrams + grams), d.slot(),
                    d.widthMm(), d.heightMm(), d.depthMm(), d.flightBatteryWatts(), d.frontalAreaM2(), d.ledCount(), d.actionCam(),
                    d.lensXMm(), d.lensLengthMm());
            return new AccessoryType(heavier, base.name, base.missing, base);
        });
    }

    public Optional<ActionCamSpec> camera() {
        return Optional.ofNullable(this.camera);
    }

    public boolean isCamera() {
        return this.camera != null;
    }

    public boolean isFlashbackCam() {
        return FLASHBACK_CAM_ID.equals(this.id());
    }

    public boolean isLight() {
        return this.ledCount > 0;
    }

    public double[] boxMinMm() {
        return switch (this.mount) {
            case TOP -> new double[]{-this.widthMm / 2.0, HINGE_CLEARANCE_MM, -this.depthMm / 2.0};
            case BOTTOM -> new double[]{-this.widthMm / 2.0, -this.heightMm, -this.depthMm / 2.0};
        };
    }

    public double[] boxMaxMm() {
        return switch (this.mount) {
            case TOP -> new double[]{this.widthMm / 2.0, HINGE_CLEARANCE_MM + this.heightMm, this.depthMm / 2.0};
            case BOTTOM -> new double[]{this.widthMm / 2.0, 0.0, this.depthMm / 2.0};
        };
    }

    public double[] centerMm() {
        double[] min = this.boxMinMm();
        double[] max = this.boxMaxMm();
        return new double[]{(min[0] + max[0]) / 2.0, (min[1] + max[1]) / 2.0, (min[2] + max[2]) / 2.0};
    }

    public double[] lensMm() {
        if (!this.isCamera()) {
            return this.centerMm();
        }
        return new double[]{this.lensX, HINGE_CLEARANCE_MM + this.heightMm / 2.0, -this.depthMm / 2.0 - this.lensLengthMm};
    }

    public double[] ledMm(int index, double stripLengthMm) {
        int count = Math.max(1, this.ledCount);
        double spacing = stripLengthMm / count;
        return new double[]{-stripLengthMm / 2.0 + spacing * (index + 0.5), -this.heightMm, 0.0};
    }

    @Override
    public AccessoryDefinition definition() {
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
