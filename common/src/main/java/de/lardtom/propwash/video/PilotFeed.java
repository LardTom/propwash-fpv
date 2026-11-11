package de.lardtom.propwash.video;

import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.rescue.RescuePhase;
import de.lardtom.propwash.sim.BatteryState;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.trick.TrickScore;
import de.lardtom.propwash.trick.TrickType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public final class PilotFeed {
    public static final int MAX_LABEL = 48;
    public static final long MAX_SINCE_GO_NANOS = 60_000_000_000L;
    public static final StreamCodec<ByteBuf, PilotFeed> CODEC = StreamCodec.of((buf, feed) -> feed.write(buf), PilotFeed::read);

    private static final int HAS_SIM = 1;
    private static final int ARMED = 1 << 1;
    private static final int MODE_3D = 1 << 2;
    private static final int TURTLE = 1 << 3;
    private static final int POS_HOLD_DENIED = 1 << 4;
    private static final int GPS_KNOWN = 1 << 5;
    private static final int GPS_FIX = 1 << 6;
    private static final int AUTO_ARM_PENDING = 1 << 7;
    private static final int RESCUE = 1 << 8;
    private static final int WORLD_EDGE = 1 << 9;
    private static final int INSIDE_BLOCK = 1 << 10;
    private static final int OVERLOADED = 1 << 11;
    private static final int MOTOR_HOT = 1 << 12;
    private static final int ESC_DERATING = 1 << 13;
    private static final int RADIO = 1 << 14;
    private static final int HOME = 1 << 15;
    private static final int POINT_TO_POINT = 1 << 16;
    private static final int FALSE_START = 1 << 17;
    private static final int TRICKS = 1 << 18;
    private static final int ALL_FLAGS = (1 << 19) - 1;

    private static final int F_THROTTLE = 0;
    private static final int F_PACK = 1;
    private static final int F_CELL = 2;
    private static final int F_WARN_CELL = 3;
    private static final int F_CRITICAL_CELL = 4;
    private static final int F_CHARGE = 5;
    private static final int F_MAH = 6;
    private static final int F_CURRENT = 7;
    private static final int F_POWER = 8;
    private static final int F_SPEED = 9;
    private static final int F_ALTITUDE = 10;
    private static final int F_VARIO = 11;
    private static final int F_G = 12;
    private static final int F_ROLL = 13;
    private static final int F_PITCH = 14;
    private static final int F_COMPASS = 15;
    private static final int F_FLIGHT = 16;
    private static final int F_ARMED_FOR = 17;
    private static final int F_LINK = 18;
    private static final int F_RSSI = 19;
    private static final int F_STICK_ROLL = 20;
    private static final int F_STICK_PITCH = 21;
    private static final int F_STICK_YAW = 22;
    private static final int F_STICK_THROTTLE = 23;
    private static final int F_TRICK_AGE = 24;
    private static final int F_RELAY = 25;
    private static final int FLOATS = F_RELAY + OsdData.MAX_RELAY_HOPS;

    private static final int I_ARM_BLOCKERS = 0;
    private static final int I_CRASHES = 1;
    private static final int I_FAILSAFE = 2;
    private static final int I_SATELLITES = 3;
    private static final int I_CELLS = 4;
    private static final int I_CAPACITY = 5;
    private static final int I_RELAY_HOPS = 6;
    private static final int I_COURSE = 7;
    private static final int I_LAP = 8;
    private static final int I_LAP_TARGET = 9;
    private static final int I_NEXT_GATE = 10;
    private static final int I_GATE_COUNT = 11;
    private static final int I_NEXT_INDEX = 12;
    private static final int I_START_INDEX = 13;
    private static final int I_TRICK_POINTS = 14;
    private static final int I_TRICK_COMBO = 15;
    private static final int I_TRICK_SCORE = 16;
    private static final int I_TRICK_BEST_COMBO = 17;
    private static final int I_TRICK_BEST_POINTS = 18;
    private static final int INTS = 19;

    private static final int L_CURRENT_LAP = 0;
    private static final int L_LAST_LAP = 1;
    private static final int L_BEST_LAP = 2;
    private static final int L_SPLIT = 3;
    private static final int L_COUNTDOWN = 4;
    private static final int L_COUNTDOWN_TOTAL = 5;
    private static final int L_SINCE_GO = 6;
    private static final int LONGS = 7;

    private static final StabMode[] MODES = StabMode.values();
    private static final HoldMode[] HOLDS = HoldMode.values();
    private static final BatteryState[] BATTERIES = BatteryState.values();
    private static final RescuePhase[] RESCUES = RescuePhase.values();
    private static final LapTracker.Phase[] PHASES = LapTracker.Phase.values();
    private static final TrickType[] TRICKS_ALL = TrickType.values();

    private int flags;
    private StabMode mode = StabMode.ACRO;
    private HoldMode holdMode = HoldMode.OFF;
    private BatteryState batteryState = BatteryState.OK;
    private @Nullable RescuePhase rescuePhase;
    private LapTracker.Phase phase = LapTracker.Phase.IDLE;
    private int trickPopup = OsdData.TRICK_NONE;
    private @Nullable TrickType trickType;
    private final TrickType[] trickTypes = new TrickType[TrickScore.POPUP_TYPES];
    private int trickTypeCount;
    private String assistLabel = "";
    private final float[] floats = new float[FLOATS];
    private final int[] ints = new int[INTS];
    private final long[] longs = new long[LONGS];
    private double homeX;
    private double homeY;
    private double homeZ;

    public PilotFeed() {
        Arrays.fill(this.floats, Float.NaN);
        this.ints[I_COURSE] = LapTracker.NO_COURSE;
        this.ints[I_NEXT_GATE] = -1;
        this.ints[I_NEXT_INDEX] = -1;
        this.ints[I_START_INDEX] = -1;
        this.longs[L_LAST_LAP] = -1L;
        this.longs[L_BEST_LAP] = -1L;
        this.longs[L_COUNTDOWN] = LapTracker.NONE;
        this.longs[L_SINCE_GO] = MAX_SINCE_GO_NANOS;
    }

    public static PilotFeed capture(OsdData d) {
        PilotFeed feed = new PilotFeed();
        feed.flags = bit(d.hasSim, HAS_SIM) | bit(d.armed, ARMED) | bit(d.mode3d, MODE_3D) | bit(d.turtle, TURTLE)
                | bit(d.posHoldDenied, POS_HOLD_DENIED) | bit(d.gpsKnown, GPS_KNOWN) | bit(d.gpsFix, GPS_FIX)
                | bit(d.autoArmPending, AUTO_ARM_PENDING) | bit(d.rescueActive, RESCUE) | bit(d.worldEdge, WORLD_EDGE)
                | bit(d.insideBlock, INSIDE_BLOCK) | bit(d.overloaded, OVERLOADED) | bit(d.motorHot, MOTOR_HOT)
                | bit(d.escDerating, ESC_DERATING) | bit(d.radioConnected, RADIO) | bit(d.hasHome, HOME)
                | bit(d.pointToPoint, POINT_TO_POINT) | bit(d.tricksOn, TRICKS);
        feed.mode = d.mode == null ? StabMode.ACRO : d.mode;
        feed.holdMode = d.holdMode == null ? HoldMode.OFF : d.holdMode;
        feed.batteryState = d.batteryState == null ? BatteryState.OK : d.batteryState;
        feed.assistLabel = label(d.assistLabel);
        float[] f = feed.floats;
        f[F_THROTTLE] = (float) d.throttle;
        f[F_PACK] = (float) d.packVoltage;
        f[F_CELL] = (float) d.cellVoltage;
        f[F_WARN_CELL] = (float) d.warnCellVoltage;
        f[F_CRITICAL_CELL] = (float) d.criticalCellVoltage;
        f[F_CHARGE] = (float) d.chargeFraction;
        f[F_MAH] = (float) d.mAhUsed;
        f[F_CURRENT] = (float) d.currentA;
        f[F_POWER] = (float) d.powerW;
        f[F_SPEED] = (float) d.speed;
        f[F_ALTITUDE] = (float) d.altitude;
        f[F_VARIO] = (float) d.verticalSpeed;
        f[F_G] = (float) d.gLoad;
        f[F_ROLL] = (float) d.rollDeg;
        f[F_PITCH] = (float) d.pitchDeg;
        f[F_COMPASS] = (float) d.compassDeg;
        f[F_FLIGHT] = (float) d.flightSeconds;
        f[F_ARMED_FOR] = (float) d.armedSeconds;
        f[F_LINK] = (float) d.linkStrength;
        f[F_RSSI] = (float) d.rssiDbm;
        f[F_STICK_ROLL] = d.stickRoll;
        f[F_STICK_PITCH] = d.stickPitch;
        f[F_STICK_YAW] = d.stickYaw;
        f[F_STICK_THROTTLE] = d.stickThrottle;
        f[F_TRICK_AGE] = (float) d.trickAgeSeconds;
        int hops = Math.clamp(d.relayHops, 0, OsdData.MAX_RELAY_HOPS);
        for (int hop = 0; hop < OsdData.MAX_RELAY_HOPS; hop++) {
            f[F_RELAY + hop] = (float) d.relayHopQuality[hop];
        }
        int[] i = feed.ints;
        i[I_ARM_BLOCKERS] = d.armBlockers;
        i[I_CRASHES] = d.crashCount;
        i[I_FAILSAFE] = d.failsafeStage;
        i[I_SATELLITES] = d.gpsSatellites;
        i[I_CELLS] = d.cells;
        i[I_CAPACITY] = d.capacityMah;
        i[I_RELAY_HOPS] = hops;
        i[I_LAP] = d.lap;
        i[I_NEXT_GATE] = d.nextGate;
        i[I_GATE_COUNT] = d.gateCount;
        i[I_TRICK_POINTS] = d.trickPoints;
        i[I_TRICK_COMBO] = d.trickCombo;
        i[I_TRICK_SCORE] = d.trickScore;
        i[I_TRICK_BEST_COMBO] = d.trickBestCombo;
        i[I_TRICK_BEST_POINTS] = d.trickBestComboPoints;
        long[] l = feed.longs;
        l[L_CURRENT_LAP] = d.currentLapNanos;
        l[L_LAST_LAP] = d.lastLapNanos;
        l[L_BEST_LAP] = d.bestLapNanos;
        l[L_SPLIT] = d.splitDeltaNanos;
        if (d.hasHome) {
            feed.homeX = d.homeX;
            feed.homeY = d.homeY;
            feed.homeZ = d.homeZ;
        }
        feed.phase = d.racing ? LapTracker.Phase.RACING : LapTracker.Phase.IDLE;
        if (d.tricksOn) {
            feed.trickPopup = Math.clamp(d.trickPopup, OsdData.TRICK_NONE, OsdData.TRICK_LOST);
            feed.trickType = d.trickType;
            feed.trickTypeCount = Math.clamp(d.trickTypeCount, 0, TrickScore.POPUP_TYPES);
            for (int k = 0; k < feed.trickTypeCount; k++) {
                feed.trickTypes[k] = d.trickTypes[k];
            }
        }
        return feed;
    }

    public PilotFeed withRace(int courseId, LapTracker.Phase phase, int lapTarget, int nextGateIndex, int startGateIndex, boolean falseStarted,
                              long countdownRemainingNanos, long countdownTotalNanos, long sinceGoNanos) {
        this.ints[I_COURSE] = courseId;
        this.phase = phase == null ? LapTracker.Phase.IDLE : phase;
        this.ints[I_LAP_TARGET] = lapTarget;
        this.ints[I_NEXT_INDEX] = nextGateIndex;
        this.ints[I_START_INDEX] = startGateIndex;
        this.flags = falseStarted ? this.flags | FALSE_START : this.flags & ~FALSE_START;
        this.longs[L_COUNTDOWN] = this.phase == LapTracker.Phase.COUNTDOWN ? Math.max(0L, countdownRemainingNanos) : LapTracker.NONE;
        this.longs[L_COUNTDOWN_TOTAL] = Math.max(0L, countdownTotalNanos);
        this.longs[L_SINCE_GO] = Math.clamp(sinceGoNanos, 0L, MAX_SINCE_GO_NANOS);
        return this;
    }

    public PilotFeed withRescuePhase(@Nullable RescuePhase phase) {
        this.rescuePhase = phase;
        return this;
    }

    public void apply(OsdData d, long ageNanos) {
        long age = Math.max(0L, ageNanos);
        double ageSeconds = age / 1.0E9;
        d.hasSim = this.has(HAS_SIM);
        d.armed = this.has(ARMED);
        d.mode = this.mode;
        d.mode3d = this.has(MODE_3D);
        d.turtle = this.has(TURTLE);
        d.holdMode = this.holdMode;
        d.posHoldDenied = this.has(POS_HOLD_DENIED);
        d.assistLabel = this.assistLabel.isEmpty() ? null : this.assistLabel;
        d.gpsKnown = this.has(GPS_KNOWN);
        d.gpsFix = this.has(GPS_FIX);
        d.autoArmPending = this.has(AUTO_ARM_PENDING);
        d.rescueActive = this.has(RESCUE);
        d.worldEdge = this.has(WORLD_EDGE);
        d.insideBlock = this.has(INSIDE_BLOCK);
        d.overloaded = this.has(OVERLOADED);
        d.motorHot = this.has(MOTOR_HOT);
        d.escDerating = this.has(ESC_DERATING);
        d.radioConnected = this.has(RADIO);
        d.batteryState = this.batteryState;
        float[] f = this.floats;
        d.throttle = f[F_THROTTLE];
        d.packVoltage = f[F_PACK];
        d.cellVoltage = f[F_CELL];
        d.warnCellVoltage = f[F_WARN_CELL];
        d.criticalCellVoltage = f[F_CRITICAL_CELL];
        d.chargeFraction = f[F_CHARGE];
        d.mAhUsed = f[F_MAH];
        d.currentA = f[F_CURRENT];
        d.powerW = f[F_POWER];
        d.speed = f[F_SPEED];
        d.altitude = f[F_ALTITUDE];
        d.verticalSpeed = f[F_VARIO];
        d.gLoad = f[F_G];
        d.rollDeg = f[F_ROLL];
        d.pitchDeg = f[F_PITCH];
        d.compassDeg = f[F_COMPASS];
        d.flightSeconds = finiteOr(f[F_FLIGHT]) + (d.armed ? ageSeconds : 0.0);
        d.armedSeconds = d.armed ? finiteOr(f[F_ARMED_FOR]) + ageSeconds : 0.0;
        d.linkStrength = finiteOr(f[F_LINK]);
        d.rssiDbm = f[F_RSSI];
        d.stickRoll = finiteOr(f[F_STICK_ROLL]);
        d.stickPitch = finiteOr(f[F_STICK_PITCH]);
        d.stickYaw = finiteOr(f[F_STICK_YAW]);
        d.stickThrottle = finiteOr(f[F_STICK_THROTTLE]);
        int[] i = this.ints;
        d.armBlockers = i[I_ARM_BLOCKERS];
        d.crashCount = i[I_CRASHES];
        d.failsafeStage = i[I_FAILSAFE];
        d.gpsSatellites = i[I_SATELLITES];
        d.cells = i[I_CELLS];
        d.capacityMah = i[I_CAPACITY];
        d.relayHops = Math.clamp(i[I_RELAY_HOPS], 0, OsdData.MAX_RELAY_HOPS);
        for (int hop = 0; hop < OsdData.MAX_RELAY_HOPS; hop++) {
            d.relayHopQuality[hop] = hop <= d.relayHops && d.relayHops > 0 ? finiteOr(f[F_RELAY + hop]) : 0.0;
        }
        d.hasHome = this.has(HOME);
        d.homeX = this.homeX;
        d.homeY = this.homeY;
        d.homeZ = this.homeZ;
        d.racing = this.phase == LapTracker.Phase.RACING;
        d.pointToPoint = this.has(POINT_TO_POINT);
        d.lap = i[I_LAP];
        d.nextGate = i[I_NEXT_GATE];
        d.gateCount = i[I_GATE_COUNT];
        long[] l = this.longs;
        d.currentLapNanos = d.racing ? plus(l[L_CURRENT_LAP], age) : l[L_CURRENT_LAP];
        d.lastLapNanos = l[L_LAST_LAP];
        d.bestLapNanos = l[L_BEST_LAP];
        d.splitDeltaNanos = l[L_SPLIT];
        if (!this.has(TRICKS)) {
            d.clearTricks();
            return;
        }
        d.tricksOn = true;
        d.trickPopup = this.trickPopup;
        d.trickType = this.trickType;
        d.trickTypeCount = this.trickTypeCount;
        for (int k = 0; k < this.trickTypeCount; k++) {
            d.trickTypes[k] = this.trickTypes[k];
        }
        d.trickPoints = i[I_TRICK_POINTS];
        d.trickCombo = i[I_TRICK_COMBO];
        d.trickAgeSeconds = f[F_TRICK_AGE] + ageSeconds;
        d.trickScore = i[I_TRICK_SCORE];
        d.trickBestCombo = i[I_TRICK_BEST_COMBO];
        d.trickBestComboPoints = i[I_TRICK_BEST_POINTS];
    }

    public int courseId() {
        return this.ints[I_COURSE];
    }

    public LapTracker.Phase phase() {
        return this.phase;
    }

    public boolean racing() {
        return this.phase == LapTracker.Phase.RACING;
    }

    public boolean countdown() {
        return this.phase == LapTracker.Phase.COUNTDOWN;
    }

    public int lap() {
        return this.ints[I_LAP];
    }

    public int lapTarget() {
        return this.ints[I_LAP_TARGET];
    }

    public int nextGateIndex() {
        return this.ints[I_NEXT_INDEX];
    }

    public int startGateIndex() {
        return this.ints[I_START_INDEX];
    }

    public boolean falseStarted() {
        return this.has(FALSE_START);
    }

    public boolean pointToPoint() {
        return this.has(POINT_TO_POINT);
    }

    public long lastLapNanos() {
        return this.longs[L_LAST_LAP];
    }

    public long countdownRemainingNanos(long ageNanos) {
        long left = this.longs[L_COUNTDOWN];
        return this.countdown() ? left <= 0L ? 0L : Math.max(0L, left - Math.max(0L, ageNanos)) : LapTracker.NONE;
    }

    public long countdownTotalNanos() {
        return this.longs[L_COUNTDOWN_TOTAL];
    }

    public long sinceGoNanos(long ageNanos) {
        long since = this.longs[L_SINCE_GO];
        return since >= MAX_SINCE_GO_NANOS ? Long.MAX_VALUE : plus(since, Math.max(0L, ageNanos));
    }

    public @Nullable RescuePhase rescuePhase() {
        return this.rescuePhase;
    }

    public float stickRoll() {
        return this.floats[F_STICK_ROLL];
    }

    public float stickPitch() {
        return this.floats[F_STICK_PITCH];
    }

    public float stickYaw() {
        return this.floats[F_STICK_YAW];
    }

    public float stickThrottle() {
        return this.floats[F_STICK_THROTTLE];
    }

    public boolean armed() {
        return this.has(ARMED);
    }

    public StabMode mode() {
        return this.mode;
    }

    public void write(ByteBuf buf) {
        ByteBufCodecs.VAR_INT.encode(buf, this.flags);
        buf.writeByte(this.mode.ordinal());
        buf.writeByte(this.holdMode.ordinal());
        buf.writeByte(this.batteryState.ordinal());
        buf.writeByte(this.rescuePhase == null ? 0 : this.rescuePhase.ordinal() + 1);
        buf.writeByte(this.phase.ordinal());
        buf.writeByte(this.trickPopup);
        buf.writeByte(this.trickType == null ? 0 : this.trickType.ordinal() + 1);
        buf.writeByte(this.trickTypeCount);
        for (int k = 0; k < this.trickTypeCount; k++) {
            TrickType type = this.trickTypes[k];
            buf.writeByte(type == null ? 0 : type.ordinal() + 1);
        }
        ByteBufCodecs.stringUtf8(MAX_LABEL).encode(buf, this.assistLabel);
        for (float value : this.floats) {
            buf.writeFloat(value);
        }
        for (int value : this.ints) {
            ByteBufCodecs.VAR_INT.encode(buf, value);
        }
        for (long value : this.longs) {
            ByteBufCodecs.VAR_LONG.encode(buf, value);
        }
        if (this.has(HOME)) {
            buf.writeDouble(this.homeX);
            buf.writeDouble(this.homeY);
            buf.writeDouble(this.homeZ);
        }
    }

    public static PilotFeed read(ByteBuf buf) {
        PilotFeed feed = new PilotFeed();
        feed.flags = ByteBufCodecs.VAR_INT.decode(buf) & ALL_FLAGS;
        feed.mode = pick(MODES, buf.readUnsignedByte(), StabMode.ACRO);
        feed.holdMode = pick(HOLDS, buf.readUnsignedByte(), HoldMode.OFF);
        feed.batteryState = pick(BATTERIES, buf.readUnsignedByte(), BatteryState.OK);
        int rescue = buf.readUnsignedByte();
        feed.rescuePhase = rescue == 0 ? null : pick(RESCUES, rescue - 1, null);
        feed.phase = pick(PHASES, buf.readUnsignedByte(), LapTracker.Phase.IDLE);
        feed.trickPopup = Math.clamp(buf.readUnsignedByte(), OsdData.TRICK_NONE, OsdData.TRICK_LOST);
        int type = buf.readUnsignedByte();
        feed.trickType = type == 0 ? null : pick(TRICKS_ALL, type - 1, null);
        int count = buf.readUnsignedByte();
        if (count > TrickScore.POPUP_TYPES) {
            throw new IllegalArgumentException("zu viele Figuren im Pilot-Feed: " + count);
        }
        feed.trickTypeCount = count;
        for (int k = 0; k < count; k++) {
            int each = buf.readUnsignedByte();
            feed.trickTypes[k] = each == 0 ? null : pick(TRICKS_ALL, each - 1, null);
        }
        feed.assistLabel = ByteBufCodecs.stringUtf8(MAX_LABEL).decode(buf);
        for (int k = 0; k < FLOATS; k++) {
            feed.floats[k] = buf.readFloat();
        }
        for (int k = 0; k < INTS; k++) {
            feed.ints[k] = ByteBufCodecs.VAR_INT.decode(buf);
        }
        for (int k = 0; k < LONGS; k++) {
            feed.longs[k] = ByteBufCodecs.VAR_LONG.decode(buf);
        }
        feed.ints[I_RELAY_HOPS] = Math.clamp(feed.ints[I_RELAY_HOPS], 0, OsdData.MAX_RELAY_HOPS);
        if (feed.has(HOME)) {
            feed.homeX = buf.readDouble();
            feed.homeY = buf.readDouble();
            feed.homeZ = buf.readDouble();
        }
        return feed;
    }

    private boolean has(int flag) {
        return (this.flags & flag) != 0;
    }

    private static int bit(boolean value, int flag) {
        return value ? flag : 0;
    }

    private static String label(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() > MAX_LABEL ? text.substring(0, MAX_LABEL) : text;
    }

    private static long plus(long value, long age) {
        return value > Long.MAX_VALUE - age ? Long.MAX_VALUE : value + age;
    }

    private static float finiteOr(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static <E> E pick(E[] values, int ordinal, E fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PilotFeed feed)) {
            return false;
        }
        return this.flags == feed.flags && this.mode == feed.mode && this.holdMode == feed.holdMode && this.batteryState == feed.batteryState
                && this.rescuePhase == feed.rescuePhase && this.phase == feed.phase && this.trickPopup == feed.trickPopup
                && this.trickType == feed.trickType && this.trickTypeCount == feed.trickTypeCount
                && Arrays.equals(Arrays.copyOf(this.trickTypes, this.trickTypeCount), Arrays.copyOf(feed.trickTypes, feed.trickTypeCount))
                && this.assistLabel.equals(feed.assistLabel) && Arrays.equals(this.floats, feed.floats) && Arrays.equals(this.ints, feed.ints)
                && Arrays.equals(this.longs, feed.longs) && (!this.has(HOME)
                || Double.compare(this.homeX, feed.homeX) == 0 && Double.compare(this.homeY, feed.homeY) == 0 && Double.compare(this.homeZ, feed.homeZ) == 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.flags, this.mode, this.phase, Arrays.hashCode(this.floats), Arrays.hashCode(this.ints), Arrays.hashCode(this.longs));
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "PilotFeed[%s %s, Sticks %.2f/%.2f/%.2f/%.2f, Kurs %d %s Runde %d Gate %d/%d]",
                this.armed() ? "scharf" : "unscharf", this.mode, this.stickRoll(), this.stickPitch(), this.stickYaw(), this.stickThrottle(),
                this.courseId(), this.phase, this.lap(), this.ints[I_NEXT_GATE], this.ints[I_GATE_COUNT]);
    }
}
