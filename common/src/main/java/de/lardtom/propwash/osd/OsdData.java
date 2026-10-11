package de.lardtom.propwash.osd;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.sim.BatteryState;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.trick.TrickScore;
import de.lardtom.propwash.trick.TrickSession;
import de.lardtom.propwash.trick.TrickType;
import org.jspecify.annotations.Nullable;

public final class OsdData {
    public static final double NO_SPLIT = Double.POSITIVE_INFINITY;
    public static final int REC_OFF = 0;
    public static final int REC_ON = 1;
    public static final int REC_PAUSED = 2;
    public static final int REC_SAVING = 3;
    public static final int TRICK_NONE = 0;
    public static final int TRICK_DONE = 1;
    public static final int TRICK_COMBO = 2;
    public static final int TRICK_LOST = 3;
    public static final int MAX_RELAY_HOPS = 5;

    public boolean active;
    public boolean hasSim;
    public boolean armed;
    public double armedSeconds;
    public StabMode mode = StabMode.ACRO;
    public boolean mode3d;
    public HoldMode holdMode = HoldMode.OFF;
    public boolean posHoldDenied;
    public boolean worldEdge;
    public boolean gpsKnown;
    public int gpsSatellites;
    public boolean gpsFix;
    public int armBlockers;
    public int crashCount;
    public PartWear wear = PartWear.PRISTINE;
    public int failsafeStage;
    public BatteryState batteryState = BatteryState.OK;
    public boolean rescueActive;
    public String rescuePhase = "";
    public double throttle = Double.NaN;
    public double packVoltage = Double.NaN;
    public double cellVoltage = Double.NaN;
    public int cells;
    public double chargeFraction = Double.NaN;
    public double mAhUsed = Double.NaN;
    public int capacityMah;
    public double currentA = Double.NaN;
    public double powerW = Double.NaN;
    public double speed = Double.NaN;
    public double altitude = Double.NaN;
    public double verticalSpeed = Double.NaN;
    public double gLoad = Double.NaN;
    public double rollDeg = Double.NaN;
    public double pitchDeg = Double.NaN;
    public double compassDeg = Double.NaN;
    public double flightSeconds;
    public double onSeconds;
    public boolean radioConnected;
    public double linkStrength;
    public double videoSignal;
    public double videoShift;
    public boolean videoLost;
    public double videoNoise;
    public @Nullable VideoSystem videoSystem;
    public boolean hasPosition;
    public double x;
    public double y;
    public double z;
    public boolean hasHome;
    public double homeX;
    public double homeY;
    public double homeZ;
    public String craftName = "";
    public boolean racing;
    public int lap;
    public long currentLapNanos;
    public long lastLapNanos = -1L;
    public long bestLapNanos = -1L;
    public int nextGate = -1;
    public int gateCount;
    public long splitDeltaNanos;
    public double splitAgeSeconds = NO_SPLIT;
    public float stickRoll;
    public float stickPitch;
    public float stickYaw;
    public float stickThrottle;
    public int clockHour;
    public int clockMinute;
    public boolean showStats;
    public final FlightStats stats = new FlightStats();
    public double blinkSeconds;
    public int recording = REC_OFF;
    public double recordingSeconds;
    public int recordingPercent;
    public boolean ledsOff;
    public boolean motorDerating;
    public String vtxChannel = "";
    public int relayHops;
    public final double[] relayHopQuality = new double[MAX_RELAY_HOPS];
    public boolean spectating;
    public boolean tricksOn;
    public int trickPopup = TRICK_NONE;
    public @Nullable TrickType trickType;
    public final TrickType[] trickTypes = new TrickType[TrickScore.POPUP_TYPES];
    public int trickTypeCount;
    public int trickPoints;
    public int trickCombo;
    public double trickAgeSeconds = Double.POSITIVE_INFINITY;
    public int trickScore;
    public int trickBestCombo;
    public int trickBestComboPoints;

    public OsdData() {
    }

    public void reset() {
        OsdData blank = new OsdData();
        blank.copyTo(this);
        this.stats.reset();
    }

    public void applyTelemetry(Telemetry t) {
        this.hasSim = true;
        this.armed = t.armed;
        this.mode = t.mode == null ? StabMode.ACRO : t.mode;
        this.mode3d = t.mode3d;
        this.holdMode = t.holdMode == null ? HoldMode.OFF : t.holdMode;
        this.posHoldDenied = t.posHoldDenied;
        this.gpsKnown = true;
        this.gpsSatellites = t.gpsSatellites;
        this.gpsFix = t.gpsFix;
        this.armBlockers = t.armBlockers;
        this.crashCount = t.crashCount;
        this.failsafeStage = t.failsafeStage;
        this.batteryState = t.batteryState == null ? BatteryState.OK : t.batteryState;
        this.throttle = t.throttleOut;
        this.packVoltage = t.batteryVoltage;
        this.cellVoltage = t.batteryCellVoltage;
        this.chargeFraction = t.batteryCharge;
        this.mAhUsed = t.batteryMahUsed;
        this.currentA = t.batteryCurrentA;
        this.powerW = t.batteryPowerW;
        this.speed = t.speed;
        this.verticalSpeed = t.verticalSpeed;
        this.gLoad = t.gLoad;
        this.rollDeg = t.rollDeg;
        this.pitchDeg = t.pitchDeg;
        this.compassDeg = OsdFormat.compassFromSimHeading(t.headingDeg);
        this.motorDerating = t.motorDerating;
    }

    public void applySim(DroneSim sim) {
        this.applyTelemetry(sim.telemetry());
        DroneBuild build = sim.params().build;
        if (build != null && build.battery() != null) {
            this.cells = build.battery().cells;
            this.capacityMah = build.battery().capacityMah;
        }
    }

    public void applyTricks(TrickSession session) {
        TrickScore score = session.score();
        this.tricksOn = true;
        this.trickPopup = switch (score.popup()) {
            case NONE -> TRICK_NONE;
            case TRICK -> TRICK_DONE;
            case COMBO -> TRICK_COMBO;
            case LOST -> TRICK_LOST;
        };
        this.trickType = score.popupType();
        this.trickTypeCount = score.popup() == TrickScore.Popup.TRICK ? score.popupTypeCount() : 0;
        for (int i = 0; i < this.trickTypeCount; i++) {
            this.trickTypes[i] = score.popupType(i);
        }
        this.trickPoints = score.popupPoints();
        this.trickCombo = score.popupCombo();
        this.trickAgeSeconds = score.popupAge();
        this.trickScore = score.flightScore();
        this.trickBestCombo = score.bestCombo();
        this.trickBestComboPoints = score.bestComboPoints();
    }

    public void clearTricks() {
        this.tricksOn = false;
        this.trickPopup = TRICK_NONE;
        this.trickType = null;
        this.trickTypeCount = 0;
        this.trickPoints = 0;
        this.trickCombo = 0;
        this.trickAgeSeconds = Double.POSITIVE_INFINITY;
        this.trickScore = 0;
        this.trickBestCombo = 0;
        this.trickBestComboPoints = 0;
    }

    public double homeDistance() {
        if (!this.hasHome || !this.hasPosition) {
            return Double.NaN;
        }
        double dx = this.homeX - this.x;
        double dy = this.homeY - this.y;
        double dz = this.homeZ - this.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double homeBearing() {
        if (!this.hasHome || !this.hasPosition) {
            return Double.NaN;
        }
        return OsdFormat.bearing(this.homeX - this.x, this.homeZ - this.z);
    }

    public double homeRelativeBearing() {
        double bearing = this.homeBearing();
        if (!Double.isFinite(bearing) || !Double.isFinite(this.compassDeg)) {
            return Double.NaN;
        }
        return OsdFormat.wrap180(bearing - this.compassDeg);
    }

    public void copyTo(OsdData dst) {
        dst.active = this.active;
        dst.hasSim = this.hasSim;
        dst.armed = this.armed;
        dst.armedSeconds = this.armedSeconds;
        dst.mode = this.mode;
        dst.mode3d = this.mode3d;
        dst.holdMode = this.holdMode;
        dst.posHoldDenied = this.posHoldDenied;
        dst.worldEdge = this.worldEdge;
        dst.gpsKnown = this.gpsKnown;
        dst.gpsSatellites = this.gpsSatellites;
        dst.gpsFix = this.gpsFix;
        dst.armBlockers = this.armBlockers;
        dst.crashCount = this.crashCount;
        dst.wear = this.wear;
        dst.failsafeStage = this.failsafeStage;
        dst.batteryState = this.batteryState;
        dst.rescueActive = this.rescueActive;
        dst.rescuePhase = this.rescuePhase;
        dst.throttle = this.throttle;
        dst.packVoltage = this.packVoltage;
        dst.cellVoltage = this.cellVoltage;
        dst.cells = this.cells;
        dst.chargeFraction = this.chargeFraction;
        dst.mAhUsed = this.mAhUsed;
        dst.capacityMah = this.capacityMah;
        dst.currentA = this.currentA;
        dst.powerW = this.powerW;
        dst.speed = this.speed;
        dst.altitude = this.altitude;
        dst.verticalSpeed = this.verticalSpeed;
        dst.gLoad = this.gLoad;
        dst.rollDeg = this.rollDeg;
        dst.pitchDeg = this.pitchDeg;
        dst.compassDeg = this.compassDeg;
        dst.flightSeconds = this.flightSeconds;
        dst.onSeconds = this.onSeconds;
        dst.radioConnected = this.radioConnected;
        dst.linkStrength = this.linkStrength;
        dst.videoSignal = this.videoSignal;
        dst.videoShift = this.videoShift;
        dst.videoLost = this.videoLost;
        dst.videoNoise = this.videoNoise;
        dst.videoSystem = this.videoSystem;
        dst.hasPosition = this.hasPosition;
        dst.x = this.x;
        dst.y = this.y;
        dst.z = this.z;
        dst.hasHome = this.hasHome;
        dst.homeX = this.homeX;
        dst.homeY = this.homeY;
        dst.homeZ = this.homeZ;
        dst.craftName = this.craftName;
        dst.racing = this.racing;
        dst.lap = this.lap;
        dst.currentLapNanos = this.currentLapNanos;
        dst.lastLapNanos = this.lastLapNanos;
        dst.bestLapNanos = this.bestLapNanos;
        dst.nextGate = this.nextGate;
        dst.gateCount = this.gateCount;
        dst.splitDeltaNanos = this.splitDeltaNanos;
        dst.splitAgeSeconds = this.splitAgeSeconds;
        dst.stickRoll = this.stickRoll;
        dst.stickPitch = this.stickPitch;
        dst.stickYaw = this.stickYaw;
        dst.stickThrottle = this.stickThrottle;
        dst.clockHour = this.clockHour;
        dst.clockMinute = this.clockMinute;
        dst.showStats = this.showStats;
        this.stats.copyTo(dst.stats);
        dst.blinkSeconds = this.blinkSeconds;
        dst.recording = this.recording;
        dst.recordingSeconds = this.recordingSeconds;
        dst.recordingPercent = this.recordingPercent;
        dst.ledsOff = this.ledsOff;
        dst.motorDerating = this.motorDerating;
        dst.vtxChannel = this.vtxChannel;
        dst.relayHops = this.relayHops;
        System.arraycopy(this.relayHopQuality, 0, dst.relayHopQuality, 0, MAX_RELAY_HOPS);
        dst.spectating = this.spectating;
        dst.tricksOn = this.tricksOn;
        dst.trickPopup = this.trickPopup;
        dst.trickType = this.trickType;
        dst.trickTypeCount = this.trickTypeCount;
        System.arraycopy(this.trickTypes, 0, dst.trickTypes, 0, this.trickTypes.length);
        dst.trickPoints = this.trickPoints;
        dst.trickCombo = this.trickCombo;
        dst.trickAgeSeconds = this.trickAgeSeconds;
        dst.trickScore = this.trickScore;
        dst.trickBestCombo = this.trickBestCombo;
        dst.trickBestComboPoints = this.trickBestComboPoints;
    }

    public static OsdData sample() {
        OsdData d = new OsdData();
        d.active = true;
        d.hasSim = true;
        d.armed = true;
        d.armedSeconds = 83.0;
        d.mode = StabMode.ACRO;
        d.gpsKnown = true;
        d.gpsSatellites = 14;
        d.gpsFix = true;
        d.throttle = 0.46;
        d.cells = 6;
        d.packVoltage = 22.84;
        d.cellVoltage = 3.81;
        d.chargeFraction = 0.58;
        d.capacityMah = 1100;
        d.mAhUsed = 462.0;
        d.currentA = 23.6;
        d.powerW = 539.0;
        d.speed = 21.4;
        d.altitude = 12.0;
        d.verticalSpeed = 1.8;
        d.gLoad = 1.4;
        d.rollDeg = 12.0;
        d.pitchDeg = -8.0;
        d.compassDeg = 32.0;
        d.flightSeconds = 83.0;
        d.onSeconds = 214.0;
        d.radioConnected = true;
        d.linkStrength = 0.98;
        d.videoSignal = 0.86;
        d.videoSystem = VideoSystem.ANALOG;
        d.hasPosition = true;
        d.x = 128.4;
        d.y = 76.0;
        d.z = -342.8;
        d.hasHome = true;
        d.homeX = 90.0;
        d.homeY = 64.0;
        d.homeZ = -300.0;
        d.craftName = "PROPWASH";
        d.racing = true;
        d.lap = 2;
        d.currentLapNanos = 14_320_000_000L;
        d.lastLapNanos = 31_870_000_000L;
        d.bestLapNanos = 30_950_000_000L;
        d.nextGate = 4;
        d.gateCount = 9;
        d.splitDeltaNanos = -420_000_000L;
        d.splitAgeSeconds = 0.5;
        d.stickRoll = 0.2F;
        d.stickPitch = -0.1F;
        d.stickYaw = 0.05F;
        d.stickThrottle = 0.46F;
        d.clockHour = 14;
        d.clockMinute = 32;
        d.stats.flightSeconds = 83.0;
        d.stats.maxSpeed = 31.2;
        d.stats.maxAltitude = 24.5;
        d.stats.maxDistance = 118.0;
        d.stats.minCellVoltage = 3.62;
        d.stats.maxCurrent = 71.4;
        d.stats.mAhUsed = 462.0;
        d.stats.maxG = 3.8;
        d.stats.samples = 1;
        d.recording = REC_ON;
        d.recordingSeconds = 83.0;
        d.vtxChannel = "R1";
        d.relayHops = 2;
        d.relayHopQuality[0] = 0.97;
        d.relayHopQuality[1] = 0.91;
        d.relayHopQuality[2] = 0.88;
        d.tricksOn = true;
        d.trickPopup = TRICK_DONE;
        d.trickType = TrickType.POWER_LOOP;
        d.trickPoints = 300;
        d.trickCombo = 3;
        d.trickAgeSeconds = 0.5;
        d.trickScore = 2140;
        d.trickBestCombo = 5;
        d.trickBestComboPoints = 1320;
        d.stats.tricksOn = true;
        d.stats.trickScore = 2140;
        d.stats.bestCombo = 5;
        d.stats.bestComboPoints = 1320;
        return d;
    }
}
