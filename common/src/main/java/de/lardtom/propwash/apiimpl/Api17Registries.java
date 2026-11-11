package de.lardtom.propwash.apiimpl;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.api.client.RecordingControl;
import de.lardtom.propwash.api.client.ServiceExtension;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdWarning;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.drone.AutonomyAccess;
import de.lardtom.propwash.api.drone.AutonomyNotReady;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.CollisionRule;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.api.drone.ExitHandler;
import de.lardtom.propwash.api.drone.OperatorAccess;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.input.AddonAxis;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchRegistry;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.api.share.ShareCodes;
import de.lardtom.propwash.api.share.ShareOptions;
import de.lardtom.propwash.api.share.ShareResult;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.api.worldrule.WorldRules;
import de.lardtom.propwash.apiimpl.client.RecordingControlImpl;
import de.lardtom.propwash.drone.data.DroneDataStore;
import de.lardtom.propwash.drone.operator.OperatorServer;
import de.lardtom.propwash.drone.operator.client.OperatorView;
import de.lardtom.propwash.input.addon.AddonInputState;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.server.AddonWorldRules;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class Api17Registries {
    public static final Switches SWITCHES = new Switches();
    public static final Rules WORLD_RULES = new Rules();
    public static final Data DRONE_DATA = new Data();
    public static final Autonomy AUTONOMY = new Autonomy();
    public static final Operators OPERATORS = new Operators();
    public static final Share SHARE_CODES = new Share();

    private static final Map<Identifier, CollisionRule> COLLISION_RULES = new LinkedHashMap<>();

    private Api17Registries() {
    }

    public record Registered<T>(Identifier id, T value) {
        public Registered {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(value, "value");
        }
    }

    public static synchronized void registerCollisionRule(Identifier id, CollisionRule rule) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rule, "rule");
        NamespaceRules.requireForeign(id.toString(), "collision rule");
        if (COLLISION_RULES.putIfAbsent(id, rule) != null) {
            throw new IllegalArgumentException("Kollisionsregel doppelt angemeldet: " + id);
        }
    }

    public static synchronized List<Registered<CollisionRule>> collisionRules() {
        return entries(COLLISION_RULES);
    }

    private static <T> List<Registered<T>> entries(Map<Identifier, T> map) {
        List<Registered<T>> out = new ArrayList<>(map.size());
        for (Map.Entry<Identifier, T> entry : map.entrySet()) {
            out.add(new Registered<>(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(out);
    }

    public static final class Switches implements SwitchRegistry {
        private final Map<Identifier, AddonSwitch> switches = new LinkedHashMap<>();
        private final Map<Identifier, AddonAxis> axes = new LinkedHashMap<>();

        private Switches() {
        }

        @Override
        public synchronized AddonSwitch register(SwitchSpec spec) {
            Objects.requireNonNull(spec, "spec");
            NamespaceRules.requireForeign(spec.id().toString(), "switch");
            if (this.switches.containsKey(spec.id())) {
                throw new IllegalArgumentException("Schalter doppelt angemeldet: " + spec.id());
            }
            if (this.switches.size() >= MAX_SWITCHES) {
                throw new IllegalStateException("Höchstens " + MAX_SWITCHES + " Add-on-Schalter, " + spec.id() + " ist zu viel");
            }
            AddonSwitch handle = new SwitchHandle(spec);
            this.switches.put(spec.id(), handle);
            return handle;
        }

        @Override
        public synchronized AddonAxis registerAxis(AxisSpec spec) {
            Objects.requireNonNull(spec, "spec");
            NamespaceRules.requireForeign(spec.id().toString(), "axis");
            if (this.axes.containsKey(spec.id())) {
                throw new IllegalArgumentException("Achse doppelt angemeldet: " + spec.id());
            }
            if (this.axes.size() >= MAX_AXES) {
                throw new IllegalStateException("Höchstens " + MAX_AXES + " Add-on-Achsen, " + spec.id() + " ist zu viel");
            }
            AddonAxis handle = new AxisHandle(spec);
            this.axes.put(spec.id(), handle);
            return handle;
        }

        @Override
        public synchronized List<AddonSwitch> switches() {
            return List.copyOf(this.switches.values());
        }

        @Override
        public synchronized List<AddonAxis> axes() {
            return List.copyOf(this.axes.values());
        }

        @Override
        public synchronized Optional<AddonSwitch> find(Identifier id) {
            return Optional.ofNullable(this.switches.get(id));
        }

        @Override
        public synchronized Optional<AddonAxis> findAxis(Identifier id) {
            return Optional.ofNullable(this.axes.get(id));
        }
    }

    private record SwitchHandle(SwitchSpec spec) implements AddonSwitch {
        @Override
        public Identifier id() {
            return this.spec.id();
        }

        @Override
        public boolean active(Entity drone) {
            return AddonInputState.active(drone, this.spec.id());
        }
    }

    private record AxisHandle(AxisSpec spec) implements AddonAxis {
        @Override
        public Identifier id() {
            return this.spec.id();
        }

        @Override
        public double value(Entity drone) {
            return AddonInputState.axis(drone, this.spec.id(), this.spec.center());
        }
    }

    public static final class Rules implements WorldRules {
        private final Map<Identifier, WorldRule<?>> rules = new LinkedHashMap<>();
        private final Map<Identifier, Map<String, Object>> presets = new LinkedHashMap<>();
        private final Set<Identifier> neutral = new LinkedHashSet<>();
        private volatile int version;

        private Rules() {
        }

        @Override
        public WorldRule<Boolean> registerBoolean(Identifier id, boolean defaultValue) {
            return this.add(new BooleanRule(Objects.requireNonNull(id, "id"), defaultValue));
        }

        @Override
        public WorldRule<Integer> registerInt(Identifier id, int defaultValue, int min, int max) {
            Objects.requireNonNull(id, "id");
            if (min > max || defaultValue < min || defaultValue > max) {
                throw new IllegalArgumentException(id + ": Standard " + defaultValue + " liegt nicht in " + min + "…" + max);
            }
            return this.add(new IntRule(id, defaultValue, min, max));
        }

        private synchronized <T> WorldRule<T> add(WorldRule<T> rule) {
            NamespaceRules.requireForeign(rule.id().toString(), "world rule");
            if (this.rules.containsKey(rule.id())) {
                throw new IllegalArgumentException("Welt-Regel doppelt angemeldet: " + rule.id());
            }
            AddonWorldRules.register(rule);
            this.rules.put(rule.id(), rule);
            this.version++;
            return rule;
        }

        @Override
        public synchronized <T> void presetValue(WorldRule<T> rule, String presetId, T value) {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(presetId, "presetId");
            Objects.requireNonNull(value, "value");
            if (this.rules.get(rule.id()) != rule) {
                throw new IllegalArgumentException("Welt-Regel " + rule.id() + " ist nicht über PropwashApi.worldRules() angemeldet");
            }
            if (!PRESETS.contains(presetId)) {
                throw new IllegalArgumentException("Unbekannte Voreinstellung '" + presetId + "' für " + rule.id() + " (bekannt: "
                        + String.join(", ", PRESETS) + ")");
            }
            if (!valid(rule, value)) {
                throw new IllegalArgumentException("Wert " + value + " passt nicht zur Welt-Regel " + rule.id());
            }
            this.presets.computeIfAbsent(rule.id(), key -> new LinkedHashMap<>()).put(presetId, value);
        }

        private static boolean valid(WorldRule<?> rule, Object value) {
            return switch (rule.type()) {
                case BOOLEAN -> value instanceof Boolean;
                case INTEGER -> value instanceof Integer number && number >= rule.min() && number <= rule.max();
            };
        }

        @Override
        public synchronized void raceNeutral(WorldRule<?> rule) {
            Objects.requireNonNull(rule, "rule");
            if (this.rules.get(rule.id()) != rule) {
                throw new IllegalArgumentException("Welt-Regel " + rule.id() + " ist nicht über PropwashApi.worldRules() angemeldet");
            }
            if (this.neutral.add(rule.id())) {
                this.version++;
            }
        }

        @Override
        public synchronized boolean raceRelevant(WorldRule<?> rule) {
            return rule != null && !this.neutral.contains(rule.id());
        }

        public int version() {
            return this.version;
        }

        public synchronized Map<String, Object> presetValues(Identifier id) {
            Map<String, Object> values = this.presets.get(id);
            return values == null ? Map.of() : Map.copyOf(values);
        }

        @Override
        public synchronized List<WorldRule<?>> registered() {
            return List.copyOf(this.rules.values());
        }

        @Override
        public synchronized Optional<WorldRule<?>> find(Identifier id) {
            return Optional.ofNullable(this.rules.get(id));
        }
    }

    private record BooleanRule(Identifier id, Boolean defaultValue) implements WorldRule<Boolean> {
        @Override
        public Type type() {
            return Type.BOOLEAN;
        }

        @Override
        public int min() {
            return 0;
        }

        @Override
        public int max() {
            return 1;
        }

        @Override
        public Boolean get(Level level) {
            return AddonWorldRules.value(this, level);
        }
    }

    private record IntRule(Identifier id, Integer defaultValue, int min, int max) implements WorldRule<Integer> {
        @Override
        public Type type() {
            return Type.INTEGER;
        }

        @Override
        public Integer get(Level level) {
            return AddonWorldRules.value(this, level);
        }
    }

    public static final class Data implements DroneDataAccess {
        private final Map<Identifier, DroneDataKey<?>> keys = new LinkedHashMap<>();

        private Data() {
        }

        @Override
        public synchronized <T> DroneDataKey<T> register(Identifier id, Codec<T> codec, T defaultValue, DroneDataKey.Sync sync) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(codec, "codec");
            Objects.requireNonNull(defaultValue, "defaultValue");
            Objects.requireNonNull(sync, "sync");
            NamespaceRules.requireForeign(id.toString(), "drone data");
            if (this.keys.containsKey(id)) {
                throw new IllegalArgumentException("Drohnen-Daten doppelt angemeldet: " + id);
            }
            DroneDataKey<T> key = new DataKey<>(id, codec, defaultValue, sync);
            this.keys.put(id, key);
            return key;
        }

        @Override
        public <T> T get(Entity drone, DroneDataKey<T> key) {
            Objects.requireNonNull(drone, "drone");
            return DroneDataStore.get(drone, this.known(key));
        }

        @Override
        public <T> T get(ItemStack droneItem, DroneDataKey<T> key) {
            Objects.requireNonNull(droneItem, "droneItem");
            return DroneDataStore.get(droneItem, this.known(key));
        }

        @Override
        public <T> void set(Entity drone, DroneDataKey<T> key, T value) {
            Objects.requireNonNull(drone, "drone");
            Objects.requireNonNull(value, "value");
            this.known(key);
            if (drone.level().isClientSide()) {
                throw new IllegalStateException("Drohnen-Daten " + key.id() + " nur am Server schreiben");
            }
            DroneDataStore.set(drone, key, value);
        }

        @Override
        public void remove(Entity drone, DroneDataKey<?> key) {
            Objects.requireNonNull(drone, "drone");
            DroneDataStore.remove(drone, this.known(key));
        }

        @Override
        public synchronized List<DroneDataKey<?>> keys() {
            return List.copyOf(this.keys.values());
        }

        @Override
        public synchronized Optional<DroneDataKey<?>> find(Identifier id) {
            return Optional.ofNullable(this.keys.get(id));
        }

        private synchronized <T> DroneDataKey<T> known(DroneDataKey<T> key) {
            Objects.requireNonNull(key, "key");
            if (this.keys.get(key.id()) != key) {
                throw new IllegalArgumentException("Drohnen-Daten " + key.id() + " sind nicht über PropwashApi.droneData() angemeldet");
            }
            return key;
        }
    }

    private record DataKey<T>(Identifier id, Codec<T> codec, T defaultValue, Sync sync) implements DroneDataKey<T> {
    }

    public static final class Autonomy implements AutonomyAccess {
        private final Map<Identifier, ExitHandler> exitHandlers = new LinkedHashMap<>();

        private Autonomy() {
        }

        @Override
        public AutonomyResult start(Entity drone, ServerPlayer owner, FlightAssist assist, AutonomyOptions options) {
            NamespaceRules.requireForeign(Objects.requireNonNull(options, "options").id().toString(), "autonomous flight");
            Objects.requireNonNull(drone, "drone");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(assist, "assist");
            return AddonAutonomy.start(drone, owner, assist, options);
        }

        @Override
        public boolean stop(Entity drone) {
            Objects.requireNonNull(drone, "drone");
            return AddonAutonomy.stop(drone);
        }

        @Override
        public Optional<Identifier> active(Entity drone) {
            Objects.requireNonNull(drone, "drone");
            return AddonAutonomy.active(drone);
        }

        @Override
        public Optional<AutonomyNotReady> notReadyReason(Entity drone, AutonomyOptions options) {
            Objects.requireNonNull(drone, "drone");
            Objects.requireNonNull(options, "options");
            return AddonAutonomy.notReadyReason(drone, options);
        }

        @Override
        public synchronized void registerExitHandler(Identifier id, ExitHandler handler) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(handler, "handler");
            NamespaceRules.requireForeign(id.toString(), "exit handler");
            if (this.exitHandlers.putIfAbsent(id, handler) != null) {
                throw new IllegalArgumentException("Übergabe beim FPV-Ende doppelt angemeldet: " + id);
            }
        }

        public synchronized List<Registered<ExitHandler>> exitHandlers() {
            return entries(this.exitHandlers);
        }
    }

    public static final class Operators implements OperatorAccess {
        private Operators() {
        }

        @Override
        public boolean bind(Entity drone, ServerPlayer operator) {
            Objects.requireNonNull(drone, "drone");
            Objects.requireNonNull(operator, "operator");
            return OperatorServer.bind(drone, operator);
        }

        @Override
        public boolean unbind(Entity drone) {
            Objects.requireNonNull(drone, "drone");
            return OperatorServer.unbind(drone);
        }

        @Override
        public Optional<ServerPlayer> operator(Entity drone) {
            Objects.requireNonNull(drone, "drone");
            return OperatorServer.operator(drone);
        }
    }

    public static final class Share implements ShareCodes {
        private Share() {
        }

        @Override
        public ShareResult decode(String text) {
            return ShareResult.failed(text == null || text.isBlank() ? ShareResult.Status.EMPTY : ShareResult.Status.UNSUPPORTED_VERSION);
        }

        @Override
        public String encode(DroneData data, ShareOptions options) {
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(options, "options");
            return "";
        }

        @Override
        public ShareResult convert(String legacyCode) {
            return ShareResult.failed(legacyCode == null || legacyCode.isBlank() ? ShareResult.Status.EMPTY : ShareResult.Status.NOT_CONVERTIBLE);
        }
    }

    public static final class Client {
        public static final RecordingControl RECORDING = RecordingControlImpl.INSTANCE;

        private static final Map<Identifier, OsdElementSpec> OSD_ELEMENTS = new LinkedHashMap<>();
        private static final Map<Identifier, OsdWarning> OSD_WARNINGS = new LinkedHashMap<>();
        private static final Map<Identifier, KeyMapping> SWITCH_KEYS = new LinkedHashMap<>();
        private static final Map<Identifier, AxisKeys> AXIS_KEYS = new LinkedHashMap<>();
        private static final Map<Identifier, ServiceExtension> SERVICE_EXTENSIONS = new LinkedHashMap<>();

        private Client() {
        }

        public record AxisKeys(KeyMapping increase, KeyMapping decrease) {
            public AxisKeys {
                Objects.requireNonNull(increase, "increase");
                Objects.requireNonNull(decrease, "decrease");
            }
        }

        public static synchronized void registerOsdElement(OsdElementSpec spec) {
            Objects.requireNonNull(spec, "spec");
            NamespaceRules.requireForeign(spec.id().toString(), "osd element");
            if (OSD_ELEMENTS.putIfAbsent(spec.id(), spec) != null) {
                throw new IllegalArgumentException("OSD-Element doppelt angemeldet: " + spec.id());
            }
        }

        public static synchronized void registerOsdWarning(Identifier id, OsdWarning warning) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(warning, "warning");
            NamespaceRules.requireForeign(id.toString(), "osd warning");
            if (OSD_WARNINGS.putIfAbsent(id, warning) != null) {
                throw new IllegalArgumentException("OSD-Warnung doppelt angemeldet: " + id);
            }
        }

        public static synchronized void bindSwitchKey(Identifier id, KeyMapping key) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(key, "key");
            NamespaceRules.requireForeign(id.toString(), "switch key");
            SWITCH_KEYS.put(id, key);
        }

        public static synchronized void bindAxisKeys(Identifier id, KeyMapping increase, KeyMapping decrease) {
            Objects.requireNonNull(id, "id");
            NamespaceRules.requireForeign(id.toString(), "axis keys");
            AXIS_KEYS.put(id, new AxisKeys(increase, decrease));
        }

        public static synchronized void registerServiceExtension(ServiceExtension extension) {
            Objects.requireNonNull(extension, "extension");
            Identifier id = Objects.requireNonNull(extension.id(), "id");
            NamespaceRules.requireForeign(id.toString(), "service extension");
            if (SERVICE_EXTENSIONS.putIfAbsent(id, extension) != null) {
                throw new IllegalArgumentException("Wartungs-Erweiterung doppelt angemeldet: " + id);
            }
        }

        public static synchronized List<OsdElementSpec> osdElements() {
            return List.copyOf(OSD_ELEMENTS.values());
        }

        public static synchronized List<Registered<OsdWarning>> osdWarnings() {
            return entries(OSD_WARNINGS);
        }

        public static synchronized Optional<KeyMapping> switchKey(Identifier id) {
            return Optional.ofNullable(SWITCH_KEYS.get(id));
        }

        public static synchronized Optional<AxisKeys> axisKeys(Identifier id) {
            return Optional.ofNullable(AXIS_KEYS.get(id));
        }

        public static synchronized List<ServiceExtension> serviceExtensions() {
            return List.copyOf(SERVICE_EXTENSIONS.values());
        }

        public static boolean spectate(Entity drone) {
            Objects.requireNonNull(drone, "drone");
            return OperatorView.spectate(drone);
        }
    }
}
