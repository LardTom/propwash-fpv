package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.TuneAccess;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.LinkBudget;
import de.lardtom.propwash.video.VtxTables;
import de.lardtom.propwash.video.vtx.VtxHeat;
import de.lardtom.propwash.video.vtx.VtxPowerSettings;
import de.lardtom.propwash.video.vtx.VtxTable;
import de.lardtom.propwash.video.vtx.VtxUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public final class VtxRuntime {
    public static final VtxRuntime INSTANCE = new VtxRuntime();
    private static final Object TOAST_TOKEN = new Object();
    private static final long PENDING_NANOS = 3_000_000_000L;
    private static final long TOAST_MILLIS = 2500L;
    private static final int AMBIENT_REFRESH_TICKS = 20;

    private @Nullable QuadcopterEntity drone;
    private @Nullable VtxUnit unit;
    private @Nullable VideoType video;
    private @Nullable FlightTune seenTune;
    private @Nullable Map<String, Float> pending;
    private long pendingNanos;
    private boolean lastSwitch;
    private double ambientC = VtxHeat.DEFAULT_AMBIENT_C;
    private int ambientTicks;
    private double testTemperature = Double.NaN;

    private VtxRuntime() {
    }

    public double frame(Minecraft minecraft, QuadcopterEntity drone, DroneSim sim, double dtSeconds) {
        VtxUnit u = this.bind(minecraft, drone);
        this.syncSettings(drone, u);
        boolean pressed = RadioState.INSTANCE.isActive(SwitchFunction.VTX_POWER);
        if (pressed && !this.lastSwitch) {
            this.cycle(minecraft);
        }
        this.lastSwitch = pressed;
        while (VtxKeys.STEP.consumeClick()) {
            this.cycle(minecraft);
        }
        if (!Double.isNaN(this.testTemperature)) {
            u.setTemperature(this.testTemperature);
            this.testTemperature = Double.NaN;
        }
        Telemetry t = sim.telemetry();
        double motor = 0.0;
        for (double out : t.motorOutput) {
            motor += out;
        }
        double air = VtxHeat.airSpeed(t.armed, t.airspeed, motor / t.motorOutput.length);
        boolean limits = PilotSession.INSTANCE.appliedPhysics().hardwareLimits();
        u.update(dtSeconds, t.armed, air, this.ambientC, this.ratedMarginDb(minecraft, drone), limits);
        return u.extraW();
    }

    public void onTick(Minecraft minecraft) {
        QuadcopterEntity bound = this.drone;
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isActive() || bound == null || session.drone() != bound) {
            InputKeys.drain(VtxKeys.STEP);
            if (bound != null && session.drone() != bound) {
                this.clear();
            }
            return;
        }
        if (++this.ambientTicks >= AMBIENT_REFRESH_TICKS && minecraft.level != null) {
            this.ambientTicks = 0;
            this.ambientC = ambientAt(minecraft, bound);
        }
    }

    public void clear() {
        this.drone = null;
        this.unit = null;
        this.video = null;
        this.seenTune = null;
        this.pending = null;
        this.lastSwitch = false;
        this.testTemperature = Double.NaN;
    }

    public boolean boundTo(@Nullable QuadcopterEntity candidate) {
        return candidate != null && this.unit != null && this.drone == candidate && PilotSession.INSTANCE.drone() == candidate;
    }

    public @Nullable VtxUnit unit(@Nullable QuadcopterEntity candidate) {
        return this.boundTo(candidate) ? this.unit : null;
    }

    public double gainDb(QuadcopterEntity candidate) {
        VtxUnit u = this.unit(candidate);
        if (u != null) {
            return u.gainDb();
        }
        return VtxTables.configuredGainDb(candidate.tune().values(), candidate.build().video());
    }

    public Readout readout(QuadcopterEntity candidate) {
        VtxUnit u = this.unit(candidate);
        if (u != null) {
            return new Readout(u.effectiveMw(), u.auto(), u.throttled(), u.hot(), u.lowPowerActive(), u.tempC(), u.supplyW());
        }
        VideoType type = candidate.build().video();
        VtxTable table = VtxTables.of(type);
        Map<String, Float> tune = candidate.tune().values();
        VtxPowerSettings.Selection selection = VtxPowerSettings.selection(tune, table);
        return new Readout(table.levelMw(selection.index()), selection.auto(), false, false, false, Double.NaN, table.supplyW(selection.index()));
    }

    public void overrideTestTemperature(double tempC) {
        this.testTemperature = tempC;
    }

    private VtxUnit bind(Minecraft minecraft, QuadcopterEntity target) {
        VideoType type = target.build().video();
        VtxUnit current = this.unit;
        if (current != null && this.drone == target && Objects.equals(this.video, type)) {
            return current;
        }
        boolean sameDrone = this.drone == target;
        this.drone = target;
        this.video = type;
        this.ambientC = ambientAt(minecraft, target);
        this.ambientTicks = 0;
        VtxUnit created = new VtxUnit(VtxTables.of(type), this.ambientC);
        if (sameDrone && current != null) {
            created.setTemperature(current.tempC());
        }
        this.unit = created;
        this.seenTune = null;
        if (!sameDrone) {
            this.pending = null;
            this.lastSwitch = RadioState.INSTANCE.isActive(SwitchFunction.VTX_POWER);
        }
        return created;
    }

    private void syncSettings(QuadcopterEntity target, VtxUnit u) {
        FlightTune tune = target.tune();
        Map<String, Float> pend = this.pending;
        if (pend != null && (matches(pend, tune.values()) || System.nanoTime() - this.pendingNanos > PENDING_NANOS)) {
            this.pending = null;
            pend = null;
            this.seenTune = null;
        }
        if (pend != null) {
            return;
        }
        if (tune.equals(this.seenTune)) {
            return;
        }
        this.seenTune = tune;
        Map<String, Float> values = tune.values();
        u.configure(VtxPowerSettings.selection(values, u.table()), VtxPowerSettings.lowPower(values));
    }

    private void cycle(Minecraft minecraft) {
        VtxUnit u = this.unit;
        QuadcopterEntity target = this.drone;
        if (u == null || target == null) {
            return;
        }
        VtxTable table = u.table();
        if (table.count() < 2) {
            this.toast(minecraft, Component.translatable("propwash.video.vtx.toast.single", table.ratedMw()), Palette.WARNING);
            return;
        }
        Map<String, Float> base = this.pending != null ? this.pending : TuneAccess.INSTANCE.current().values();
        Map<String, Float> next = VtxPowerSettings.next(base, table);
        VtxPowerSettings.Selection selection = VtxPowerSettings.selection(next, table);
        u.configure(selection, VtxPowerSettings.lowPower(next));
        this.pending = next;
        this.pendingNanos = System.nanoTime();
        TuneAccess.INSTANCE.apply(new FlightTune(next));
        Component level = selection.auto() ? Component.translatable("propwash.video.vtx.auto")
                : Component.translatable("propwash.video.vtx.milliwatts", table.levelMw(selection.index()));
        this.toast(minecraft, Component.translatable("propwash.video.vtx.toast", level), Palette.INFO);
    }

    private void toast(Minecraft minecraft, Component text, int color) {
        if (minecraft.player != null) {
            ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, text, null, color, TOAST_MILLIS);
        }
    }

    private double ratedMarginDb(Minecraft minecraft, QuadcopterEntity target) {
        double fromVideo = VideoState.INSTANCE.ratedMarginDb(target);
        if (!Double.isNaN(fromVideo)) {
            return fromVideo;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return Double.NaN;
        }
        VideoType type = target.build().video();
        if (ClientWorldRules.INSTANCE.infiniteRange(type.system)) {
            return Double.POSITIVE_INFINITY;
        }
        return LinkBudget.marginDb(player.getEyePosition().distanceTo(target.position()), type.rangeBlocks)
                - ControlLinkScan.INSTANCE.videoBandLossDb(target);
    }

    private static boolean matches(Map<String, Float> wanted, Map<String, Float> actual) {
        for (String key : VtxPowerSettings.KEYS) {
            if (!Objects.equals(wanted.get(key), actual.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static double ambientAt(Minecraft minecraft, QuadcopterEntity target) {
        if (minecraft.level == null) {
            return VtxHeat.DEFAULT_AMBIENT_C;
        }
        return VtxHeat.ambientFromBiome(minecraft.level.getBiome(target.blockPosition()).value().getBaseTemperature());
    }

    public record Readout(int milliwatts, boolean auto, boolean throttled, boolean hot, boolean lowPower, double tempC, double supplyW) {
    }
}
