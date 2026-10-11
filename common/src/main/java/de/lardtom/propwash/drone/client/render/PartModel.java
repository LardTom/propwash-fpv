package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PartModel {
    public static final String ITEM_PREFIX = "drone_render/";
    public static final String BLUR_SUFFIX = "_blur";
    public static final String CCW_SUFFIX = "_ccw";
    public static final String WORN_SUFFIX = PropDamage.WORN.suffix();
    public static final String DAMAGED_SUFFIX = PropDamage.DAMAGED.suffix();
    public static final String BROKEN_SUFFIX = PropDamage.BROKEN.suffix();
    public static final String PAINT_SUFFIX = "_paint";
    public static final String UNDER_SUFFIX = "_under";
    public static final String MODEL_PREFIX = "part/";

    private static final Map<Identifier, PartModel> INTERNED = new ConcurrentHashMap<>();
    private static final Map<PartInfo, PartModel> BASE = new ConcurrentHashMap<>();
    private static final Map<AccessoryMount, PartModel> BASES = new EnumMap<>(AccessoryMount.class);
    private static volatile @Nullable List<PartModel> shipped;

    static {
        for (AccessoryMount mount : AccessoryMount.values()) {
            if (mount.hasBase()) {
                BASES.put(mount, intern(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ITEM_PREFIX + PartKind.ACCESSORY.key() + "/mount_" + mount.key())));
            }
        }
    }

    private final Identifier itemModelId;
    private final String category;
    private final String key;
    private final String name;
    private final @Nullable Identifier modelFile;
    private final Identifier itemModelFile;

    private PartModel(Identifier itemModelId) {
        this.itemModelId = itemModelId;
        String path = itemModelId.getPath();
        String local = path.startsWith(ITEM_PREFIX) ? path.substring(ITEM_PREFIX.length()) : path;
        int slash = local.indexOf('/');
        boolean conventional = path.startsWith(ITEM_PREFIX) && slash > 0 && slash == local.lastIndexOf('/');
        this.category = slash > 0 ? local.substring(0, slash) : "";
        this.key = local.substring(local.lastIndexOf('/') + 1);
        String ns = itemModelId.getNamespace();
        this.name = Constants.MOD_ID.equals(ns) ? local : ns + ":" + local;
        this.modelFile = conventional ? Identifier.fromNamespaceAndPath(ns, "models/" + MODEL_PREFIX + local + ".json") : null;
        this.itemModelFile = Identifier.fromNamespaceAndPath(ns, "items/" + path + ".json");
    }

    static PartModel intern(Identifier itemModelId) {
        return INTERNED.computeIfAbsent(itemModelId, PartModel::new);
    }

    public static PartModel of(PartInfo part) {
        if (part == null) {
            throw new IllegalArgumentException("Unbekanntes Bauteil: null");
        }
        return BASE.computeIfAbsent(part, PartModel::baseOf);
    }

    public static Identifier defaultItemModelId(Identifier partId, PartKind kind) {
        return Identifier.fromNamespaceAndPath(partId.getNamespace(), ITEM_PREFIX + kind.key() + "/" + partId.getPath());
    }

    private static PartModel baseOf(PartInfo part) {
        Optional<Identifier> custom = part.definition().basics().visual().renderModel();
        return intern(custom.orElseGet(() -> defaultItemModelId(part.id(), part.definition().kind())));
    }

    public static PartModel prop(PropType prop, boolean counterClockwise) {
        PartModel base = of(prop);
        return counterClockwise ? base.variant(CCW_SUFFIX) : base;
    }

    public static PartModel prop(PropType prop, boolean counterClockwise, PropDamage damage) {
        if (damage == null || damage == PropDamage.NONE) {
            return prop(prop, counterClockwise);
        }
        return of(prop).variant(damage.suffix() + (counterClockwise ? CCW_SUFFIX : ""));
    }

    public static PartModel batteryUnder(BatteryType battery) {
        return of(battery).variant(UNDER_SUFFIX);
    }

    public static PartModel blur(PropType prop) {
        return of(prop).variant(BLUR_SUFFIX);
    }

    public static Optional<PartModel> base(AccessoryMount mount) {
        return Optional.ofNullable(BASES.get(mount));
    }

    public static List<PartModel> variants(PartInfo part) {
        PartModel base = of(part);
        if (!(part instanceof PropType prop)) {
            return List.of(base);
        }
        List<PartModel> out = new ArrayList<>(9);
        out.add(base);
        out.add(prop(prop, true));
        out.add(blur(prop));
        for (PropDamage damage : PropDamage.values()) {
            if (damage != PropDamage.NONE) {
                out.add(prop(prop, false, damage));
                out.add(prop(prop, true, damage));
            }
        }
        return out;
    }

    public static List<PartModel> all() {
        List<PartModel> list = shipped;
        if (list == null) {
            Set<PartModel> models = new LinkedHashSet<>();
            for (PartKind kind : PartKind.values()) {
                for (PartInfo part : PartIndex.registered(kind)) {
                    if (part.builtin()) {
                        models.addAll(variants(part));
                    }
                }
            }
            models.addAll(BASES.values());
            list = Collections.unmodifiableList(new ArrayList<>(models));
            shipped = list;
        }
        return list;
    }

    public static List<PartModel> known() {
        Set<PartModel> models = new LinkedHashSet<>(all());
        for (PartInfo part : PartIndex.registered()) {
            models.addAll(variants(part));
        }
        return List.copyOf(models);
    }

    public PartModel variant(String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return this;
        }
        return intern(this.itemModelId.withSuffix(suffix));
    }

    public PartModel paintVariant() {
        if (this.key.endsWith(PAINT_SUFFIX)) {
            return this;
        }
        return this.variant(PAINT_SUFFIX);
    }

    public String name() {
        return this.name;
    }

    public String namespace() {
        return this.itemModelId.getNamespace();
    }

    public String category() {
        return this.category;
    }

    public String key() {
        return this.key;
    }

    public @Nullable Identifier modelFile() {
        return this.modelFile;
    }

    public Identifier itemModelId() {
        return this.itemModelId;
    }

    public Identifier itemModelFile() {
        return this.itemModelFile;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
