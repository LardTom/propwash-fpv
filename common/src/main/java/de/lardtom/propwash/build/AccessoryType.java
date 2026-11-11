package de.lardtom.propwash.build;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    public final double recessMm;
    public final double dropMm;
    public final double flightBatteryWatts;
    public final double frontalAreaM2;
    public final int ledCount;
    private final List<Vec3Mm> ledPath;
    private final double[] ledPathLength;
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
        this.recessMm = definition.recessMm();
        this.dropMm = definition.dropMm();
        this.flightBatteryWatts = definition.flightBatteryWatts();
        this.frontalAreaM2 = definition.frontalAreaM2();
        this.ledCount = definition.ledCount();
        this.ledPath = definition.ledPathMm();
        this.ledPathLength = cumulative(this.ledPath);
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

    public boolean fitsFrame(PartInfo frame) {
        return this.definition.frames().isEmpty() || this.definition.frames().contains(frame.id());
    }

    public Set<Identifier> frames() {
        return this.definition.frames();
    }

    public boolean isGripper() {
        return GRIPPER_ID.equals(this.id());
    }

    public boolean hangsStraight() {
        return hangsStraight(this.definition);
    }

    public static boolean hangsStraight(AccessoryDefinition definition) {
        return GRIPPER_ID.equals(definition.id()) || definition.hangsStraight();
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
                    d.lensXMm(), d.lensLengthMm(), d.ledPathMm(), d.hangsStraight(), d.muzzleMm(), d.frames(), d.recessMm(), d.dropMm());
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
        return this.recessed(this.modelBoxMinMm());
    }

    public double[] boxMaxMm() {
        return this.recessed(this.modelBoxMaxMm());
    }

    private double[] recessed(double[] local) {
        if (this.recessMm == 0.0 && this.dropMm == 0.0) {
            return local;
        }
        return new double[]{local[0], local[1] - this.dropMm, local[2] + this.recessMm};
    }

    public double[] modelBoxMinMm() {
        return switch (this.mount) {
            case TOP -> new double[]{-this.widthMm / 2.0, HINGE_CLEARANCE_MM, -this.depthMm / 2.0};
            case BOTTOM -> new double[]{-this.widthMm / 2.0, -this.heightMm, -this.depthMm / 2.0};
            case FRONT -> new double[]{-this.widthMm / 2.0, -this.heightMm, -this.depthMm};
        };
    }

    public double[] modelBoxMaxMm() {
        return switch (this.mount) {
            case TOP -> new double[]{this.widthMm / 2.0, HINGE_CLEARANCE_MM + this.heightMm, this.depthMm / 2.0};
            case BOTTOM -> new double[]{this.widthMm / 2.0, 0.0, this.depthMm / 2.0};
            case FRONT -> new double[]{this.widthMm / 2.0, 0.0, 0.0};
        };
    }

    public double[] centerMm() {
        double[] min = this.boxMinMm();
        double[] max = this.boxMaxMm();
        return new double[]{(min[0] + max[0]) / 2.0, (min[1] + max[1]) / 2.0, (min[2] + max[2]) / 2.0};
    }

    public double[] muzzleMm() {
        double[] min = this.boxMinMm();
        double[] max = this.boxMaxMm();
        Vec3Mm offset = this.definition.muzzleMm().orElse(Vec3Mm.ZERO);
        return new double[]{(min[0] + max[0]) / 2.0 + offset.x(), (min[1] + max[1]) / 2.0 + offset.y(), min[2] + offset.z()};
    }

    public double[] lensMm() {
        return this.recessed(this.modelLensMm());
    }

    public double[] modelLensMm() {
        if (!this.isCamera()) {
            double[] min = this.modelBoxMinMm();
            double[] max = this.modelBoxMaxMm();
            return new double[]{(min[0] + max[0]) / 2.0, (min[1] + max[1]) / 2.0, (min[2] + max[2]) / 2.0};
        }
        return new double[]{this.lensX, HINGE_CLEARANCE_MM + this.heightMm / 2.0, -this.depthMm / 2.0 - this.lensLengthMm};
    }

    public boolean hasLedPath() {
        return !this.ledPath.isEmpty();
    }

    public List<Vec3Mm> ledPath() {
        return this.ledPath;
    }

    private static double[] cumulative(List<Vec3Mm> path) {
        double[] out = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            Vec3Mm a = path.get(i - 1);
            Vec3Mm b = path.get(i);
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double dz = b.z() - a.z();
            out[i] = out[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return out;
    }

    private double[] ledOnPath(int index) {
        int count = Math.max(1, this.ledCount);
        int last = this.ledPathLength.length - 1;
        double at = this.ledPathLength[last] * (index + 0.5) / count;
        int segment = 1;
        while (segment < last && this.ledPathLength[segment] < at) {
            segment++;
        }
        double length = this.ledPathLength[segment] - this.ledPathLength[segment - 1];
        double t = length <= 0.0 ? 0.0 : Math.clamp((at - this.ledPathLength[segment - 1]) / length, 0.0, 1.0);
        Vec3Mm a = this.ledPath.get(segment - 1);
        Vec3Mm b = this.ledPath.get(segment);
        return new double[]{a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t};
    }

    public double[] ledMm(int index, double stripLengthMm) {
        return this.recessed(this.modelLedMm(index, stripLengthMm));
    }

    public double[] modelLedMm(int index, double stripLengthMm) {
        if (this.hasLedPath()) {
            return this.ledOnPath(index);
        }
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
