package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;

public final class AirframeSpec {
    public static final int MOTORS = 4;
    public static final int HULL_POINTS = 12;
    public static final int GROUND_POINTS = HULL_POINTS + 8;
    public static final double LEGACY_LENS_UP = 0.015;
    public static final double LEGACY_LENS_FORWARD_FACTOR = 0.7;

    public final DroneBuild build;
    public final String buildKey;
    public final boolean buildable;
    public final boolean assemblable;
    public final double massKg;
    public final double armLength;
    public final double motorOffset;
    public final double motorSpacing;
    public final double propDiameter;
    public final double propRadius;
    public final double discArea;
    public final double propPitch;
    public final boolean ducted;
    public final double comHeight;
    public final double comX;
    public final double comZ;
    public final double rotorPlaneOffset;
    public final double inertiaXx;
    public final double inertiaYy;
    public final double inertiaZz;
    public final double kv;
    public final double kt;
    public final double motorResistance;
    public final double escResistance;
    public final double phaseResistance;
    public final double idleCurrent;
    public final double escCurrentLimit;
    public final double escInputLimit;
    public final double rotorInertia;
    public final double rotorInertiaEff;
    public final double omegaMax;
    public final double hoverOmega;
    public final double motorTauPhys;
    public final double motorTau;
    public final double kThrust;
    public final double kTorque;
    public final double ductGain;
    public final double ductClearance;
    public final double zeroThrustPerOmega;
    public final double inducedPerOmega;
    public final double translationalLiftGain;
    public final double hoverInducedVelocity;
    public final double washNoiseHz;
    public final int cells;
    public final double capacityMah;
    public final BatteryType.Chemistry chemistry;
    public final double packResistance;
    public final double polarizationResistance;
    public final double continuousCurrent;
    public final double fullPackVoltage;
    public final double auxPower;
    public final double accessoryPower;
    public final double accessoryMassKg;
    public final double brownoutVoltage;
    public final int escMinCells;
    public final int escMaxCells;
    public final boolean escOvervoltage;
    public final double areaX;
    public final double areaY;
    public final double areaZ;
    public final double dragCoefficient;
    public final double rotationalDamping;
    public final double impactTolerance;
    public final double restitution;
    public final double friction;
    public final double hitboxWidth;
    public final double hitboxHeight;
    public final double hitboxFootOffset;
    public final double authorityRoll;
    public final double authorityPitch;
    public final double authorityYawSteady;
    public final double authorityYawInstant;
    public final double hoverVoltage;
    public final double lensRight;
    public final double lensUp;
    public final double lensForward;
    public final double cameraTiltDeg;
    public final double cameraFovDeg;
    public final double motorHeatCapacity;
    public final double motorCoolingBase;
    public final double motorCoolingPerOmega;
    public final double motorCoolingScale;
    public final double motorDerateStartC;
    public final double motorMaxTempC;
    public final double motorHeatWearPerSecond;
    public final double propStiffness;
    public final double propFlex;
    public final double propBrittleness;
    public final double reverseThrust;
    public final double reverseTorque;
    public final double reversePitch;
    public final double avionicsPower;
    public final double driveLossHover;
    public final double propFactor;
    public final double propFullFactor;
    public final double fullLoadOmega;
    public final double usableFraction;
    public final double landingCellVoltage;
    public final FrameRole role;
    public final boolean explicitArms;
    public final boolean uniformThrottle;

    final double washLoss;
    final double washNoise;
    final double motorMassKg;
    final double stackMassKg;

    private final double[] motorX = new double[MOTORS];
    private final double[] motorY = new double[MOTORS];
    private final double[] motorZ = new double[MOTORS];
    private final int[] spin = new int[MOTORS];
    private final double[] mixRoll = new double[MOTORS];
    private final double[] mixPitch = new double[MOTORS];
    private final double[] mixThrottle = new double[MOTORS];
    private final double[] hullX = new double[HULL_POINTS];
    private final double[] hullY = new double[HULL_POINTS];
    private final double[] hullZ = new double[HULL_POINTS];
    private final int groundCount;
    private final int groundMask;
    private final double[] groundX = new double[GROUND_POINTS];
    private final double[] groundY = new double[GROUND_POINTS];
    private final double[] groundZ = new double[GROUND_POINTS];

    AirframeSpec(ParamDerivation.Draft d) {
        this.build = d.build;
        this.buildKey = d.buildKey;
        this.buildable = d.buildable;
        this.assemblable = d.assemblable;
        this.massKg = d.massKg;
        this.armLength = d.armLength;
        this.motorOffset = d.motorOffset;
        this.motorSpacing = d.motorSpacing;
        this.propDiameter = d.propDiameter;
        this.propRadius = d.propRadius;
        this.discArea = d.discArea;
        this.propPitch = d.propPitch;
        this.ducted = d.ducted;
        this.comHeight = d.comHeight;
        this.comX = d.comX;
        this.comZ = d.comZ;
        this.rotorPlaneOffset = d.rotorPlaneOffset;
        this.inertiaXx = d.inertiaXx;
        this.inertiaYy = d.inertiaYy;
        this.inertiaZz = d.inertiaZz;
        this.kv = d.kv;
        this.kt = d.kt;
        this.motorResistance = d.motorResistance;
        this.escResistance = d.escResistance;
        this.phaseResistance = d.motorResistance + d.escResistance;
        this.idleCurrent = d.idleCurrent;
        this.escCurrentLimit = d.escCurrentLimit;
        this.escInputLimit = d.escInputLimit;
        this.rotorInertia = d.rotorInertia;
        this.rotorInertiaEff = d.rotorInertiaEff;
        this.omegaMax = d.omegaMax;
        this.hoverOmega = d.hoverOmega;
        this.motorTauPhys = d.motorTauPhys;
        this.motorTau = d.motorTau;
        this.kThrust = d.kThrust;
        this.kTorque = d.kTorque;
        this.ductGain = d.ductGain;
        this.ductClearance = d.ductClearance;
        this.zeroThrustPerOmega = d.zeroThrustPerOmega;
        this.inducedPerOmega = RotorAero.inducedPerOmega(d.kThrust, d.discArea);
        this.translationalLiftGain = d.translationalLiftGain;
        this.hoverInducedVelocity = d.hoverInducedVelocity;
        this.washNoiseHz = d.washNoiseHz;
        this.cells = d.cells;
        this.capacityMah = d.capacityMah;
        this.chemistry = d.chemistry;
        this.packResistance = d.packResistance;
        this.polarizationResistance = d.polarizationResistance;
        this.continuousCurrent = d.continuousCurrent;
        this.fullPackVoltage = d.fullPackVoltage;
        this.auxPower = d.auxPower;
        this.accessoryPower = d.accessoryPower;
        this.accessoryMassKg = d.accessoryMassKg;
        this.brownoutVoltage = d.brownoutVoltage;
        this.escMinCells = d.escMinCells;
        this.escMaxCells = d.escMaxCells;
        this.escOvervoltage = d.cells > d.escMaxCells;
        this.areaX = d.areaX;
        this.areaY = d.areaY;
        this.areaZ = d.areaZ;
        this.dragCoefficient = d.dragCoefficient;
        this.rotationalDamping = d.rotationalDamping;
        this.impactTolerance = d.impactTolerance;
        this.restitution = d.restitution;
        this.friction = d.friction;
        this.hitboxWidth = d.hitboxWidth;
        this.hitboxHeight = d.hitboxHeight;
        this.hitboxFootOffset = d.hitboxFootOffset;
        this.authorityRoll = d.authorityRoll;
        this.authorityPitch = d.authorityPitch;
        this.authorityYawSteady = d.authorityYawSteady;
        this.authorityYawInstant = d.authorityYawInstant;
        this.hoverVoltage = d.hoverVoltage;
        this.lensRight = d.lensRight;
        this.lensUp = d.lensUp;
        this.lensForward = d.lensForward;
        this.cameraTiltDeg = d.cameraTiltDeg;
        this.cameraFovDeg = d.cameraFovDeg;
        this.motorHeatCapacity = d.motorHeatCapacity;
        this.motorCoolingBase = d.motorCoolingBase;
        this.motorCoolingPerOmega = d.motorCoolingPerOmega;
        this.motorCoolingScale = d.motorCoolingScale;
        this.motorDerateStartC = d.motorDerateStartC;
        this.motorMaxTempC = d.motorMaxTempC;
        this.motorHeatWearPerSecond = d.motorHeatWearPerSecond;
        this.propStiffness = d.propStiffness;
        this.propFlex = d.propFlex;
        this.propBrittleness = d.propBrittleness;
        this.reverseThrust = d.reverseThrust;
        this.reverseTorque = d.reverseTorque;
        this.reversePitch = d.reversePitch;
        this.avionicsPower = d.avionicsPower;
        this.driveLossHover = d.driveLossHover;
        this.propFactor = d.propFactor;
        this.propFullFactor = d.propFullFactor;
        this.fullLoadOmega = d.fullLoadOmega;
        this.usableFraction = d.usableFraction;
        this.landingCellVoltage = d.landingCellVoltage;
        this.role = d.role;
        this.explicitArms = d.explicitArms;
        this.uniformThrottle = d.uniformThrottle;
        this.washLoss = d.washLoss;
        this.washNoise = d.washNoise;
        this.motorMassKg = d.motorMassKg;
        this.stackMassKg = d.stackMassKg;
        System.arraycopy(d.motorX, 0, this.motorX, 0, MOTORS);
        System.arraycopy(d.motorY, 0, this.motorY, 0, MOTORS);
        System.arraycopy(d.motorZ, 0, this.motorZ, 0, MOTORS);
        System.arraycopy(d.spin, 0, this.spin, 0, MOTORS);
        System.arraycopy(d.mixRoll, 0, this.mixRoll, 0, MOTORS);
        System.arraycopy(d.mixPitch, 0, this.mixPitch, 0, MOTORS);
        System.arraycopy(d.mixThrottle, 0, this.mixThrottle, 0, MOTORS);
        System.arraycopy(d.hullX, 0, this.hullX, 0, HULL_POINTS);
        System.arraycopy(d.hullY, 0, this.hullY, 0, HULL_POINTS);
        System.arraycopy(d.hullZ, 0, this.hullZ, 0, HULL_POINTS);
        this.groundCount = d.groundCount;
        this.groundMask = d.groundMask;
        System.arraycopy(d.groundX, 0, this.groundX, 0, GROUND_POINTS);
        System.arraycopy(d.groundY, 0, this.groundY, 0, GROUND_POINTS);
        System.arraycopy(d.groundZ, 0, this.groundZ, 0, GROUND_POINTS);
    }

    public static AirframeSpec of(DroneBuild build) {
        return ParamDerivation.derive(build);
    }

    public static double defaultBrownoutVoltage(int escMinCells) {
        return escMinCells == 1 ? ModelConstants.BROWNOUT_1S : ModelConstants.BROWNOUT_MULTI;
    }

    public static double defaultSideAreaM2(double frontalAreaM2) {
        return ModelConstants.SIDE_AREA * frontalAreaM2;
    }

    public static double defaultTopAreaM2(double frontalAreaM2, boolean ducted) {
        return (ducted ? ModelConstants.TOP_AREA_DUCT : ModelConstants.TOP_AREA_OPEN) * frontalAreaM2;
    }

    public double motorX(int i) {
        return this.motorX[i];
    }

    public double motorY(int i) {
        return this.motorY[i];
    }

    public double motorZ(int i) {
        return this.motorZ[i];
    }

    public int spin(int i) {
        return this.spin[i];
    }

    public double mixRoll(int i) {
        return this.mixRoll[i];
    }

    public double mixPitch(int i) {
        return this.mixPitch[i];
    }

    public double mixThrottle(int i) {
        return this.mixThrottle[i];
    }

    public double mixYaw(int i) {
        return this.spin[i];
    }

    public int hullCount() {
        return HULL_POINTS;
    }

    public double hullX(int k) {
        return this.hullX[k];
    }

    public double hullY(int k) {
        return this.hullY[k];
    }

    public double hullZ(int k) {
        return this.hullZ[k];
    }

    public int groundCount() {
        return this.groundCount;
    }

    public boolean groundUsed(int k) {
        return (this.groundMask & (1 << k)) != 0;
    }

    public double groundX(int k) {
        return this.groundX[k];
    }

    public double groundY(int k) {
        return this.groundY[k];
    }

    public double groundZ(int k) {
        return this.groundZ[k];
    }

    public int hullMotor(int k) {
        return k < MOTORS ? k : -1;
    }

    public double hoverThrust() {
        return this.massKg * ModelConstants.G / MOTORS;
    }

    public double propwashNoiseFactor() {
        return this.propStiffness == 1.0 ? 1.0 : 1.0 / Math.sqrt(this.propStiffness);
    }
}
