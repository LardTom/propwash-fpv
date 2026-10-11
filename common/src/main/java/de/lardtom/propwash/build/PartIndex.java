package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.VideoDefinition;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class PartIndex {
    public static final String DEFAULT_NAMESPACE = "propwash";
    public static final String MISSING_NAME = "MISSING";
    public static final int MAX_KEY_LENGTH = 128;

    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+");
    private static final Object LOCK = new Object();
    private static final Map<PartKind, Slot> SLOTS = new EnumMap<>(PartKind.class);
    private static final Map<Identifier, PartInfo> BY_ID = new ConcurrentHashMap<>();
    private static final Set<Identifier> BUILTIN = ConcurrentHashMap.newKeySet();
    private static final Set<Identifier> BUILTIN_CHEMISTRY = ConcurrentHashMap.newKeySet();
    private static final Map<Identifier, BatteryType.Chemistry> CHEMISTRY_BY_ID = new ConcurrentHashMap<>();
    private static final List<BatteryType.Chemistry> BUILTIN_CHEMISTRIES = new ArrayList<>();
    private static final List<BatteryType.Chemistry> EXTRA_CHEMISTRIES = new ArrayList<>();
    private static final Map<String, PartInfo> MISSING = new ConcurrentHashMap<>();
    private static final Comparator<PartInfo> EXTRA_ORDER = Comparator
            .comparingInt((PartInfo p) -> p.definition().basics().sortOrder())
            .thenComparing(p -> p.id().getNamespace())
            .thenComparing(p -> p.id().getPath());
    private static final Comparator<BatteryType.Chemistry> EXTRA_CHEMISTRY_ORDER = Comparator
            .comparing((BatteryType.Chemistry c) -> c.id().getNamespace())
            .thenComparing(c -> c.id().getPath());
    private static volatile List<BatteryType.Chemistry> chemistrySnapshot = List.of();
    private static volatile boolean frozen;

    static {
        for (PartKind kind : PartKind.values()) {
            SLOTS.put(kind, new Slot());
        }
    }

    private PartIndex() {
    }

    public static void ensureBuiltins() {
        Object[] touched = {BatteryType.Chemistry.LIPO, FrameType.WHOOP_65, StackType.AIO_WHOOP, MotorType.M0802_22000, PropType.P31_4B,
                VideoType.ANALOG_NANO, BatteryType.B1S_300_HV, AccessoryType.LED_STRIP};
        assert touched.length > 0;
    }

    static void addBuiltin(PartInfo part) {
        synchronized (LOCK) {
            add(part, true);
        }
    }

    static void addBuiltinChemistry(BatteryType.Chemistry chemistry) {
        synchronized (LOCK) {
            addChemistry(chemistry, true);
        }
    }

    static BatteryType.@Nullable Chemistry chemistryDirect(Identifier id) {
        return CHEMISTRY_BY_ID.get(id);
    }

    public static PartInfo detached(PartDefinition definition) {
        ensureBuiltins();
        return create(definition, false);
    }

    private static PartInfo create(PartDefinition definition, boolean missing) {
        String name = missing ? MISSING_NAME : null;
        return switch (definition) {
            case FrameDefinition d -> new FrameType(d, name, missing);
            case StackDefinition d -> new StackType(d, name, missing);
            case MotorDefinition d -> new MotorType(d, name, missing);
            case PropDefinition d -> new PropType(d, name, missing);
            case VideoDefinition d -> new VideoType(d, name, missing);
            case BatteryDefinition d -> new BatteryType(d, name, missing);
            case AccessoryDefinition d -> new AccessoryType(d, name, missing);
        };
    }

    public static PartInfo register(PartDefinition definition) {
        ensureBuiltins();
        validateId(definition.id(), "Bauteil");
        if (frozen) {
            throw frozenError(definition.id());
        }
        PartInfo part = detached(definition);
        synchronized (LOCK) {
            if (frozen) {
                throw frozenError(definition.id());
            }
            add(part, false);
        }
        return part;
    }

    public static BatteryType.Chemistry registerChemistry(CellChemistry definition) {
        ensureBuiltins();
        validateId(definition.id(), "Zellchemie");
        BatteryType.Chemistry chemistry = new BatteryType.Chemistry(definition, null);
        synchronized (LOCK) {
            if (frozen) {
                throw frozenError(definition.id());
            }
            addChemistry(chemistry, false);
        }
        return chemistry;
    }

    public static void validateId(Identifier id, String what) {
        if (DEFAULT_NAMESPACE.equals(id.getNamespace())) {
            throw new IllegalArgumentException(what + " " + id + ": Der Namensraum " + DEFAULT_NAMESPACE
                    + " gehört den eingebauten Teilen, Addons melden ihre Teile unter der eigenen Mod-Id an");
        }
        if (!PATH.matcher(id.getPath()).matches()) {
            throw new IllegalArgumentException(what + " " + id + ": Der Pfad darf nur a–z, 0–9, _, . und - enthalten (keine Unterordner mit /)");
        }
        if (keyOf(id).length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(what + " " + id + ": Id ist länger als " + MAX_KEY_LENGTH + " Zeichen");
        }
    }

    public static boolean freeze() {
        ensureBuiltins();
        synchronized (LOCK) {
            if (frozen) {
                return false;
            }
            frozen = true;
            return true;
        }
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static boolean isBuiltin(Identifier id) {
        ensureBuiltins();
        return BUILTIN.contains(id);
    }

    public static boolean isBuiltinChemistry(Identifier id) {
        ensureBuiltins();
        return BUILTIN_CHEMISTRY.contains(id);
    }

    public static List<PartInfo> all(PartKind kind) {
        ensureBuiltins();
        List<PartInfo> snapshot = SLOTS.get(kind).snapshot;
        return PartAvailability.filtering() ? activeOnly(snapshot) : snapshot;
    }

    public static List<PartInfo> all() {
        ensureBuiltins();
        List<PartInfo> out = new ArrayList<>();
        for (PartKind kind : PartKind.values()) {
            out.addAll(all(kind));
        }
        return List.copyOf(out);
    }

    public static List<PartInfo> registered(PartKind kind) {
        ensureBuiltins();
        return SLOTS.get(kind).snapshot;
    }

    public static List<PartInfo> registered() {
        ensureBuiltins();
        List<PartInfo> out = new ArrayList<>();
        for (PartKind kind : PartKind.values()) {
            out.addAll(SLOTS.get(kind).snapshot);
        }
        return List.copyOf(out);
    }

    public static Optional<PartInfo> registeredById(Identifier id) {
        ensureBuiltins();
        return id == null ? Optional.empty() : Optional.ofNullable(BY_ID.get(id));
    }

    private static List<PartInfo> activeOnly(List<PartInfo> parts) {
        List<PartInfo> out = new ArrayList<>(parts.size());
        for (PartInfo part : parts) {
            if (PartAvailability.active(part.id())) {
                out.add(part);
            }
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    static <T extends PartInfo> List<T> typed(PartKind kind) {
        return (List<T>) all(kind);
    }

    public static Optional<PartInfo> byId(Identifier id) {
        ensureBuiltins();
        return id == null || !PartAvailability.active(id) ? Optional.empty() : Optional.ofNullable(BY_ID.get(id));
    }

    public static <T extends PartInfo> Optional<T> byId(Class<T> type, Identifier id) {
        return byId(id).filter(type::isInstance).map(type::cast);
    }

    public static Optional<PartInfo> byKey(String key) {
        Identifier id = idOfKey(key);
        return id == null ? Optional.empty() : byId(id);
    }

    public static <T extends PartInfo> Optional<T> byKey(Class<T> type, String key) {
        return byKey(key).filter(type::isInstance).map(type::cast);
    }

    public static PartInfo missing(PartKind kind, Identifier id) {
        return missing(kind, id, null);
    }

    public static PartInfo missing(PartKind kind, Identifier id, @Nullable AccessorySlot slot) {
        ensureBuiltins();
        AccessorySlot resolvedSlot = kind == PartKind.ACCESSORY ? slot == null ? AccessorySlot.BOTTOM : slot : null;
        String cacheKey = kind.key() + "|" + (resolvedSlot == null ? "" : resolvedSlot.key()) + "|" + id;
        return MISSING.computeIfAbsent(cacheKey, ignored -> create(Definitions.withId(substitute(kind, resolvedSlot).definition(), id), true));
    }

    public static Optional<PartInfo> resolveOrMissing(PartKind kind, String key, @Nullable AccessorySlot slot) {
        Identifier id = idOfKey(key);
        if (id == null || keyOf(id).length() > MAX_KEY_LENGTH) {
            return Optional.empty();
        }
        Optional<PartInfo> known = byId(id);
        if (known.isPresent()) {
            PartInfo part = known.get();
            boolean slotFits = slot == null || !(part instanceof AccessoryType accessory) || accessory.definition().slot() == slot;
            return part.definition().kind() == kind && slotFits ? known : Optional.empty();
        }
        if (DEFAULT_NAMESPACE.equals(id.getNamespace())) {
            return Optional.empty();
        }
        return Optional.of(missing(kind, id, slot));
    }

    public static <T extends PartInfo> Optional<T> resolveOrMissing(Class<T> type, PartKind kind, String key) {
        return resolveOrMissing(kind, key, null).filter(type::isInstance).map(type::cast);
    }

    public static PartInfo substitute(PartKind kind, @Nullable AccessorySlot slot) {
        return switch (kind) {
            case FRAME -> FrameType.FREESTYLE_5;
            case STACK -> StackType.STACK_30;
            case MOTOR -> MotorType.M2207_1750;
            case PROP -> PropType.P51_3B;
            case VIDEO -> VideoType.ANALOG_RACE;
            case BATTERY -> BatteryType.B6S_1300;
            case ACCESSORY -> slot == AccessorySlot.TOP ? AccessoryType.ACTION_CAM_LIGHT : AccessoryType.LED_STRIP;
        };
    }

    public static @Nullable Identifier idOfKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return key.indexOf(':') >= 0 ? Identifier.tryParse(key) : Identifier.tryBuild(DEFAULT_NAMESPACE, key);
    }

    public static String keyOf(Identifier id) {
        return DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    public static List<BatteryType.Chemistry> chemistries() {
        ensureBuiltins();
        List<BatteryType.Chemistry> snapshot = chemistrySnapshot;
        if (!PartAvailability.filtering()) {
            return snapshot;
        }
        List<BatteryType.Chemistry> out = new ArrayList<>(snapshot.size());
        for (BatteryType.Chemistry chemistry : snapshot) {
            if (PartAvailability.active(chemistry.id())) {
                out.add(chemistry);
            }
        }
        return List.copyOf(out);
    }

    public static List<BatteryType.Chemistry> registeredChemistries() {
        ensureBuiltins();
        return chemistrySnapshot;
    }

    public static Optional<BatteryType.Chemistry> chemistry(Identifier id) {
        ensureBuiltins();
        return id == null || !PartAvailability.active(id) ? Optional.empty() : Optional.ofNullable(CHEMISTRY_BY_ID.get(id));
    }

    public static String fingerprint() {
        ensureBuiltins();
        return fingerprint(chemistrySnapshot, registered());
    }

    static String fingerprint(List<BatteryType.Chemistry> chemistries, Iterable<PartInfo> parts) {
        StringBuilder text = new StringBuilder();
        for (BatteryType.Chemistry chemistry : chemistries) {
            text.append(chemistry.definition()).append('\n');
        }
        for (PartInfo part : parts) {
            text.append(part.definition()).append('\n');
        }
        return sha256(text.toString());
    }

    public static String hashOf(Object definition) {
        return sha256(String.valueOf(definition)).substring(0, 16);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt", e);
        }
    }

    private static void add(PartInfo part, boolean builtin) {
        Identifier id = part.id();
        PartInfo existing = BY_ID.get(id);
        if (existing != null) {
            throw new IllegalArgumentException("Bauteil doppelt angemeldet: " + id + " (schon vorhanden als "
                    + existing.definition().kind().key() + ")");
        }
        Slot slot = SLOTS.get(part.definition().kind());
        if (builtin) {
            slot.builtins.add(part);
            BUILTIN.add(id);
        } else {
            slot.extras.add(part);
            slot.extras.sort(EXTRA_ORDER);
        }
        List<PartInfo> snapshot = new ArrayList<>(slot.builtins.size() + slot.extras.size());
        snapshot.addAll(slot.builtins);
        snapshot.addAll(slot.extras);
        BY_ID.put(id, part);
        slot.snapshot = List.copyOf(snapshot);
    }

    private static void addChemistry(BatteryType.Chemistry chemistry, boolean builtin) {
        Identifier id = chemistry.id();
        if (CHEMISTRY_BY_ID.containsKey(id)) {
            throw new IllegalArgumentException("Zellchemie doppelt angemeldet: " + id);
        }
        CHEMISTRY_BY_ID.put(id, chemistry);
        if (builtin) {
            BUILTIN_CHEMISTRY.add(id);
            BUILTIN_CHEMISTRIES.add(chemistry);
        } else {
            EXTRA_CHEMISTRIES.add(chemistry);
        }
        chemistrySnapshot = chemistryOrder(BUILTIN_CHEMISTRIES, EXTRA_CHEMISTRIES);
    }

    static List<BatteryType.Chemistry> chemistryOrder(List<BatteryType.Chemistry> builtins, List<BatteryType.Chemistry> extras) {
        List<BatteryType.Chemistry> sorted = new ArrayList<>(extras);
        sorted.sort(EXTRA_CHEMISTRY_ORDER);
        List<BatteryType.Chemistry> order = new ArrayList<>(builtins.size() + sorted.size());
        order.addAll(builtins);
        order.addAll(sorted);
        return List.copyOf(order);
    }

    private static IllegalStateException frozenError(Identifier id) {
        return new IllegalStateException("Bauteil-Register ist eingefroren, " + id
                + " kommt zu spät. Bauteile nur während der Mod-Initialisierung anmelden (Fabric onInitialize, NeoForge Mod-Konstruktor)");
    }

    private static final class Slot {
        private final List<PartInfo> builtins = new ArrayList<>();
        private final List<PartInfo> extras = new ArrayList<>();
        private volatile List<PartInfo> snapshot = List.of();
    }
}
