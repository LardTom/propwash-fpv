package de.lardtom.propwash.blackbox;

import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.FcListener;
import de.lardtom.propwash.sim.FcSample;

import java.nio.file.Path;
import java.time.ZonedDateTime;

public final class BlackboxRecorder implements FcListener {
    public static final int RING_CAPACITY = 8192;
    public static final long SUBSTEP_MICROS = DroneSim.SUBSTEP_NANOS / 1000L;

    public enum State {
        OFF,
        READY,
        WAITING,
        LOGGING,
        PAUSED,
        FULL
    }

    private final BlackboxRing ring;
    private final BlackboxWriter writer;
    private final boolean threaded;
    private final int[] sample = new int[BlackboxSample.WIDTH];

    private boolean enabled;
    private int denominator = 1;
    private boolean gate = true;
    private boolean switchBound;
    private int rssi = 1023;
    private int extraModes;
    private boolean rescue;
    private String craftName = "";
    private long maxBytes = 512L << 20;

    private boolean logging;
    private boolean paused;
    private boolean resumeNext;
    private boolean armedSeen;
    private int logId;
    private int iteration;
    private int iInterval = 32;
    private int logDenominator = 1;
    private long timeBase;
    private int periodUs = 500;
    private int pushed;
    private long dropped;
    private String failure;

    public BlackboxRecorder(Path directory, boolean threaded) {
        this.ring = new BlackboxRing(RING_CAPACITY);
        this.writer = new BlackboxWriter(this.ring, new BlackboxLibrary(directory));
        this.threaded = threaded;
    }

    public BlackboxWriter writer() {
        return this.writer;
    }

    public BlackboxLibrary library() {
        return this.writer.library();
    }

    public void configure(boolean enabled, BlackboxRate rate, boolean switchBound, boolean gate, int rssi, int extraModes, boolean rescue,
                          String craftName, long maxBytes) {
        this.enabled = enabled;
        this.denominator = rate == null ? 1 : rate.denominator();
        this.switchBound = switchBound;
        this.gate = gate;
        this.rssi = rssi;
        this.extraModes = extraModes | (switchBound && gate ? BlackboxModes.BLACKBOX : 0);
        this.rescue = rescue;
        this.craftName = craftName == null ? "" : craftName;
        this.maxBytes = maxBytes;
    }

    @Override
    public void onFcSample(DroneSim sim, FcSample s) {
        try {
            this.sample(sim, s);
        } catch (RuntimeException e) {
            this.failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.enabled = false;
            if (this.logging) {
                this.stop();
            }
        }
    }

    private void sample(DroneSim sim, FcSample s) {
        if (!s.armed) {
            this.armedSeen = false;
            if (this.logging) {
                this.stop();
            }
            return;
        }
        this.armedSeen = true;
        boolean wanted = this.gate || s.failsafeStage > 0;
        if (!this.logging) {
            if (!this.enabled || !wanted || !this.start(sim, s)) {
                return;
            }
        }
        if (this.writer.closed(this.logId)) {
            return;
        }
        int n = this.iteration++;
        int phase = Integer.remainderUnsigned(n, this.iInterval);
        if (this.paused) {
            if (!wanted || phase != 0) {
                return;
            }
            this.paused = false;
            this.resumeNext = true;
        } else if (!wanted) {
            this.paused = true;
            return;
        }
        if (phase % this.logDenominator != 0) {
            return;
        }
        int time = (int) (this.timeBase + (long) n * this.periodUs);
        BlackboxSampler.fill(this.sample, n, time, s, this.rssi, this.extraModes, this.rescue);
        if (!this.ring.offerSample(this.resumeNext ? BlackboxRing.KIND_RESUME : BlackboxRing.KIND_MAIN, this.sample)) {
            this.paused = true;
            this.dropped++;
            this.writer.wake();
            return;
        }
        this.resumeNext = false;
        if ((++this.pushed & 31) == 0) {
            this.wake();
        }
    }

    private boolean start(DroneSim sim, FcSample s) {
        int divider = Math.max(1, s.loopDivider);
        String startedAt = startedAt(ZonedDateTime.now());
        BlackboxSetup setup = BlackboxSetup.of(this.craftName, startedAt, divider, this.denominator, s.voltage, sim.activePid(), sim.activeRates(),
                sim.activeFilters(), sim.params());
        int id = this.logId + 1;
        String fileName = BlackboxLibrary.fileName(startedAt, this.craftName);
        if (!this.ring.offerControl(BlackboxRing.KIND_START, new BlackboxWriter.Start(id, setup, fileName, this.maxBytes))) {
            return false;
        }
        this.logId = id;
        this.iInterval = setup.iInterval();
        this.logDenominator = Math.min(setup.pDenominator(), this.iInterval);
        this.periodUs = setup.loopPeriodUs();
        this.timeBase = s.substep * SUBSTEP_MICROS;
        this.iteration = 0;
        this.paused = false;
        this.resumeNext = false;
        this.logging = true;
        if (this.threaded) {
            this.writer.ensureStarted();
        }
        this.wake();
        return true;
    }

    private void stop() {
        this.logging = false;
        this.paused = false;
        this.ring.offerControl(BlackboxRing.KIND_STOP, this.logId);
        this.wake();
    }

    private void wake() {
        if (this.threaded) {
            this.writer.wake();
        } else {
            this.writer.drainAll();
        }
    }

    public void sessionEnded() {
        this.armedSeen = false;
        if (this.logging) {
            this.stop();
        }
    }

    public boolean flush(long timeoutNanos) {
        if (!this.threaded) {
            this.writer.drainAll();
            return true;
        }
        return this.writer.awaitIdle(timeoutNanos);
    }

    public State state() {
        if (this.logging) {
            if (this.writer.closed(this.logId)) {
                return State.FULL;
            }
            return this.paused ? State.PAUSED : State.LOGGING;
        }
        if (!this.enabled) {
            return State.OFF;
        }
        return this.armedSeen && !this.gate ? State.WAITING : State.READY;
    }

    public boolean logging() {
        return this.logging;
    }

    public long dropped() {
        return this.dropped;
    }

    public String failure() {
        return this.failure;
    }

    public int logId() {
        return this.logId;
    }

    static String startedAt(ZonedDateTime now) {
        return BlackboxSetup.formatDateTime(now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour(), now.getMinute(),
                now.getSecond(), now.getOffset().getTotalSeconds() / 60);
    }
}
