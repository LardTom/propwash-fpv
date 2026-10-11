package de.lardtom.propwash.osd;

import de.lardtom.propwash.sim.BatteryState;
import org.jspecify.annotations.Nullable;

import java.time.LocalTime;

public final class OsdSampler {
    public static final double STATS_MIN_FLIGHT_SECONDS = 2.0;
    public static final double STATS_MAX_SECONDS = 30.0;
    public static final double STATS_STICK_DISMISS_AFTER = 1.5;
    public static final float STATS_STICK_THRESHOLD = 0.6F;
    private static final double FRAME_CAP_SECONDS = 0.25;
    private static final double CLOCK_GAP_SECONDS = 2.0;
    private static final double NANOS = 1.0E9;

    private final OsdData data = new OsdData();
    private final Source live;
    private boolean wasActive;
    private boolean wasArmed;
    private long lastNanos;
    private long sessionStartNanos;
    private long armedAtNanos;
    private long statsShownAtNanos;
    private long lastSplitDelta;
    private long splitChangedNanos;
    private double referenceY = Double.NaN;
    private @Nullable Source override;

    public OsdSampler(Source live) {
        this.live = live;
    }

    public OsdData data() {
        return this.data;
    }

    public void setOverride(@Nullable Source source) {
        this.override = source;
        this.reset();
    }

    public boolean hasOverride() {
        return this.override != null;
    }

    public void reset() {
        this.data.reset();
        this.wasActive = false;
        this.wasArmed = false;
        this.lastNanos = 0L;
        this.sessionStartNanos = 0L;
        this.armedAtNanos = 0L;
        this.statsShownAtNanos = 0L;
        this.lastSplitDelta = 0L;
        this.splitChangedNanos = 0L;
        this.referenceY = Double.NaN;
    }

    public void hold(long nowNanos) {
        if (this.wasActive) {
            this.lastNanos = nowNanos;
        }
    }

    public OsdData sample(long nowNanos) {
        Source source = this.override != null ? this.override : this.live;
        boolean active = source.active();
        if (!active) {
            if (this.wasActive) {
                this.reset();
            }
            this.data.active = false;
            return this.data;
        }
        if (!this.wasActive) {
            this.data.reset();
            this.sessionStartNanos = nowNanos;
            this.lastNanos = nowNanos;
            this.wasArmed = false;
            this.referenceY = Double.NaN;
        }
        this.wasActive = true;
        double elapsed = (nowNanos - this.lastNanos) / NANOS;
        double dt = Math.clamp(elapsed, 0.0, FRAME_CAP_SECONDS);
        double clockDt = Math.clamp(elapsed, 0.0, CLOCK_GAP_SECONDS);
        this.lastNanos = nowNanos;
        OsdData d = this.data;
        d.active = true;
        this.clearLiveValues(d);
        source.fill(d);
        d.onSeconds = (nowNanos - this.sessionStartNanos) / NANOS;
        d.blinkSeconds = d.onSeconds;
        LocalTime time = LocalTime.now();
        d.clockHour = time.getHour();
        d.clockMinute = time.getMinute();
        this.updateAltitude(d);
        this.updateSplit(d, nowNanos);
        this.updateArming(d, nowNanos, dt, clockDt);
        return d;
    }

    private void updateAltitude(OsdData d) {
        if (!d.hasPosition || !Double.isFinite(d.y)) {
            return;
        }
        if (!Double.isFinite(this.referenceY) || (d.armed && !this.wasArmed)) {
            this.referenceY = d.y;
        }
        if (!Double.isFinite(d.altitude)) {
            d.altitude = d.y - (d.hasHome && Double.isFinite(d.homeY) ? d.homeY : this.referenceY);
        }
    }

    private void clearLiveValues(OsdData d) {
        d.hasSim = false;
        d.recording = OsdData.REC_OFF;
        d.recordingSeconds = 0.0;
        d.recordingPercent = 0;
        d.ledsOff = false;
        d.vtxChannel = "";
        d.spectating = false;
        d.clearTricks();
        d.armed = false;
        d.throttle = Double.NaN;
        d.packVoltage = Double.NaN;
        d.cellVoltage = Double.NaN;
        d.chargeFraction = Double.NaN;
        d.mAhUsed = Double.NaN;
        d.currentA = Double.NaN;
        d.powerW = Double.NaN;
        d.speed = Double.NaN;
        d.altitude = Double.NaN;
        d.verticalSpeed = Double.NaN;
        d.gLoad = Double.NaN;
        d.rollDeg = Double.NaN;
        d.pitchDeg = Double.NaN;
        d.compassDeg = Double.NaN;
        d.armBlockers = 0;
        d.failsafeStage = 0;
        d.batteryState = BatteryState.OK;
        d.hasPosition = false;
        d.hasHome = false;
        d.videoSystem = null;
        d.videoShift = 0.0;
        d.videoLost = false;
        d.videoNoise = 0.0;
    }

    private void updateSplit(OsdData d, long nowNanos) {
        if (!d.racing || d.splitDeltaNanos == 0L) {
            this.lastSplitDelta = d.splitDeltaNanos;
            d.splitAgeSeconds = OsdData.NO_SPLIT;
            return;
        }
        if (d.splitDeltaNanos != this.lastSplitDelta) {
            this.lastSplitDelta = d.splitDeltaNanos;
            this.splitChangedNanos = nowNanos;
        }
        d.splitAgeSeconds = (nowNanos - this.splitChangedNanos) / NANOS;
    }

    private void updateArming(OsdData d, long nowNanos, double dt, double clockDt) {
        if (d.armed && !this.wasArmed) {
            this.armedAtNanos = nowNanos;
            d.stats.reset();
            d.showStats = false;
        } else if (!d.armed && this.wasArmed) {
            if (d.stats.flightSeconds >= STATS_MIN_FLIGHT_SECONDS) {
                d.showStats = true;
                this.statsShownAtNanos = nowNanos;
            }
        }
        this.wasArmed = d.armed;
        d.stats.trackCrashes(d);
        if (d.armed) {
            d.flightSeconds += clockDt;
            d.armedSeconds = (nowNanos - this.armedAtNanos) / NANOS;
            d.stats.accumulate(d, dt);
        } else {
            d.armedSeconds = 0.0;
        }
        if (d.showStats) {
            double shown = (nowNanos - this.statsShownAtNanos) / NANOS;
            boolean stickMoved = Math.abs(d.stickRoll) > STATS_STICK_THRESHOLD || Math.abs(d.stickPitch) > STATS_STICK_THRESHOLD;
            if (d.armed || shown > STATS_MAX_SECONDS || (shown > STATS_STICK_DISMISS_AFTER && stickMoved)) {
                d.showStats = false;
            }
        }
    }

    public interface Source {
        boolean active();

        void fill(OsdData data);
    }

    public static BatteryState batteryStateFor(double chargeFraction) {
        if (!Double.isFinite(chargeFraction)) {
            return BatteryState.OK;
        }
        if (chargeFraction <= 0.0) {
            return BatteryState.EMPTY;
        }
        if (chargeFraction < 0.1) {
            return BatteryState.CRITICAL;
        }
        return chargeFraction < 0.2 ? BatteryState.LOW : BatteryState.OK;
    }

    public static double clamp01(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 0.0;
    }
}
