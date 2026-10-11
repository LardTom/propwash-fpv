package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.client.TuneAccess;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.ui.TuneDefaults;
import de.lardtom.propwash.ui.TuneEdits;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.TuneParam;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class TuneModel {
    private static final int APPLY_INTERVAL_TICKS = 3;

    public enum Scope {
        LIVE,
        TEMPLATE
    }

    private final Scope target;
    private final DroneBuild build;
    private final Map<String, Float> original;
    private final Map<String, Float> provided;
    private Map<String, Float> working;
    private Map<String, Float> defaults;
    private boolean dirty;
    private boolean pendingApply;
    private boolean appliedAny;
    private boolean liveLost;
    private int ticksSinceApply = APPLY_INTERVAL_TICKS;
    private int revision;
    private @Nullable FlightControllerConfig config;
    private int configRevision = -1;

    private TuneModel(Scope target, DroneBuild build, Map<String, Float> original, Map<String, Float> provided) {
        this.target = target;
        this.build = build;
        this.provided = Map.copyOf(provided);
        this.defaults = TuneDefaults.merge(TuneDefaults.compute(build, original), this.provided);
        this.original = TuneEdits.clean(original, this.defaults);
        this.working = new TreeMap<>(this.original);
        this.defaults = TuneDefaults.merge(TuneDefaults.compute(build, this.working), this.provided);
    }

    public static TuneModel open(DroneContext context) {
        TuneAccess access = TuneAccess.INSTANCE;
        if (access.available()) {
            return new TuneModel(Scope.LIVE, context.build(), access.current().values(), access.defaults().values());
        }
        return new TuneModel(Scope.TEMPLATE, context.build(), UiConfig.get().defaultTune, Map.of());
    }

    public static TuneModel detached(DroneBuild build, Map<String, Float> tune) {
        return new TuneModel(Scope.TEMPLATE, build, tune, Map.of());
    }

    public Scope target() {
        return this.target;
    }

    public boolean live() {
        return this.target == Scope.LIVE;
    }

    public DroneBuild build() {
        return this.build;
    }

    public Map<String, Float> working() {
        return this.working;
    }

    public Map<String, Float> defaults() {
        return this.defaults;
    }

    public int revision() {
        return this.revision;
    }

    public boolean dirty() {
        return this.dirty;
    }

    public boolean liveLost() {
        return this.liveLost;
    }

    public FlightControllerConfig config() {
        if (this.config == null || this.configRevision != this.revision) {
            this.config = TuneDefaults.config(this.build, this.working);
            this.configRevision = this.revision;
        }
        return this.config;
    }

    public double value(String key) {
        return TuneEdits.value(this.working, this.defaults, key);
    }

    public double defaultValue(String key) {
        return TuneEdits.defaultValue(this.defaults, key);
    }

    public boolean modified(String key) {
        return TuneEdits.isModified(this.working, this.defaults, key);
    }

    public boolean manualPid() {
        return TuneEdits.manualPid(this.working, this.defaults);
    }

    public boolean flag(String key) {
        return this.value(key) >= 0.5;
    }

    public int choice(String key) {
        return (int) Math.rint(this.value(key));
    }

    public void set(String key, double value) {
        this.update(t -> TuneEdits.set(t, this.defaults, key, value), TuneDefaults.affectsDefaults(key));
    }

    public void step(String key, int steps) {
        this.update(t -> TuneEdits.step(t, this.defaults, key, steps), TuneDefaults.affectsDefaults(key));
    }

    public void reset(String key) {
        this.update(t -> TuneEdits.reset(t, this.defaults, key), TuneDefaults.affectsDefaults(key));
    }

    public void resetPidToDerived() {
        this.update(TuneEdits::resetPidToDerived, false);
    }

    public void resetWhere(Predicate<String> keys) {
        this.update(t -> TuneEdits.resetWhere(t, keys), true);
    }

    public void replaceAll(Map<String, Float> values) {
        this.update(t -> TuneEdits.clean(values, this.defaults), true);
    }

    public void update(UnaryOperator<Map<String, Float>> edit, boolean refreshDefaults) {
        Map<String, Float> next = edit.apply(this.working);
        if (next == null || next.equals(this.working)) {
            return;
        }
        if (next.size() > FlightTune.MAX_ENTRIES) {
            Constants.LOG.warn("Tuning mit {} Einträgen verworfen", next.size());
            return;
        }
        this.working = new TreeMap<>(next);
        if (refreshDefaults) {
            this.defaults = TuneDefaults.merge(TuneDefaults.compute(this.build, this.working), this.provided);
        }
        this.dirty = true;
        this.pendingApply = this.live();
        this.revision++;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void tick() {
        this.ticksSinceApply++;
        if (this.pendingApply && this.ticksSinceApply >= APPLY_INTERVAL_TICKS) {
            this.flush();
        }
    }

    private void flush() {
        this.pendingApply = false;
        this.ticksSinceApply = 0;
        if (!this.live()) {
            return;
        }
        boolean ok = applyNow(this.working);
        this.appliedAny |= ok;
        this.liveLost = !ok;
    }

    public boolean save() {
        if (this.live()) {
            if (!this.dirty && !this.pendingApply && !this.liveLost) {
                return true;
            }
            this.flush();
            this.dirty = this.liveLost;
            return !this.liveLost;
        }
        UiConfig config = UiConfig.get();
        config.defaultTune = templateSafe(this.working);
        UiConfig.CONFIG.save();
        this.dirty = false;
        return true;
    }

    public void discard() {
        this.pendingApply = false;
        if (this.live() && this.appliedAny) {
            applyNow(this.original);
        }
        this.working = new TreeMap<>(this.original);
        this.dirty = false;
    }

    public static TreeMap<String, Float> templateSafe(Map<String, Float> values) {
        return new TreeMap<>(TuneEdits.resetPidToDerived(values));
    }

    public static boolean applyNow(Map<String, Float> values) {
        try {
            return TuneAccess.INSTANCE.apply(new FlightTune(values));
        } catch (IllegalArgumentException e) {
            Constants.LOG.warn("Tuning konnte nicht übernommen werden: {}", e.getMessage());
            return false;
        }
    }

    public static TuneParam param(String key) {
        TuneParam p = TuneKeys.param(key);
        if (p == null) {
            throw new IllegalArgumentException("Unbekannter Tuning-Schlüssel: " + key);
        }
        return p;
    }
}
