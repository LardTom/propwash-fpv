package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.DuctSpec;
import de.lardtom.propwash.api.part.FrameAero;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.MassSplit;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.MotorThermal;
import de.lardtom.propwash.api.part.PackSize;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.video.FovMath;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class ParamDerivation {
    private static final ConcurrentHashMap<DroneBuild, AirframeSpec> CACHE = new ConcurrentHashMap<>();
    private static final BoundedCache<DroneBuild, AirframeSpec> CREATIVE = new BoundedCache<>(128);
    private static final double SQRT2 = Math.sqrt(2.0);
    private static final double BATTERY_VOLUME_RATIO = ModelConstants.BATTERY_LENGTH * ModelConstants.BATTERY_WIDTH;
    static final double PLATE_FRACTION = 0.8;
    static final double MIN_THROTTLE_WEIGHT = 0.2;

    private ParamDerivation() {
    }

    static AirframeSpec derive(DroneBuild build) {
        if (build.edited()) {
            return CREATIVE.get(build, ParamDerivation::computeEdited);
        }
        return CACHE.computeIfAbsent(build, ParamDerivation::compute);
    }

    private static AirframeSpec computeEdited(DroneBuild build) {
        return compute(build, build.flight(), build.inertiaOverride());
    }

    static final class Draft {
        DroneBuild build;
        String buildKey;
        boolean buildable;
        boolean assemblable;
        double massKg;
        double armLength;
        double motorOffset;
        double motorSpacing;
        double propDiameter;
        double propRadius;
        double discArea;
        double propPitch;
        boolean ducted;
        double comHeight;
        double comX;
        double comZ;
        double rotorPlaneOffset;
        double inertiaXx;
        double inertiaYy;
        double inertiaZz;
        double kv;
        double kt;
        double motorResistance;
        double escResistance;
        double idleCurrent;
        double escCurrentLimit;
        double escInputLimit;
        double rotorInertia;
        double rotorInertiaEff;
        double omegaMax;
        double hoverOmega;
        double motorTauPhys;
        double motorTau;
        double kThrust;
        double kTorque;
        double ductGain;
        double ductClearance;
        double zeroThrustPerOmega;
        double translationalLiftGain;
        double hoverInducedVelocity;
        double washNoiseHz;
        int cells;
        double capacityMah;
        BatteryType.Chemistry chemistry;
        double packResistance;
        double polarizationResistance;
        double continuousCurrent;
        double fullPackVoltage;
        double auxPower;
        double accessoryPower;
        double accessoryMassKg;
        double brownoutVoltage;
        int escMinCells;
        int escMaxCells;
        double areaX;
        double areaY;
        double areaZ;
        double dragCoefficient;
        double rotationalDamping;
        double impactTolerance;
        double restitution;
        double friction;
        double hitboxWidth;
        double hitboxHeight;
        double hitboxFootOffset;
        double authorityRoll;
        double authorityPitch;
        double authorityYawSteady;
        double authorityYawInstant;
        double hoverVoltage;
        double lensRight;
        double lensUp;
        double lensForward;
        double cameraTiltDeg;
        double cameraFovDeg;
        double washLoss;
        double washNoise;
        double motorMassKg;
        double stackMassKg;
        double motorHeatCapacity;
        double motorCoolingBase;
        double motorCoolingPerOmega;
        double motorCoolingScale;
        double motorDerateStartC;
        double motorMaxTempC;
        double motorHeatWearPerSecond;
        double propStiffness;
        double propFlex;
        double propBrittleness;
        double reverseThrust;
        double reverseTorque;
        double reversePitch;
        double avionicsPower;
        double driveLossHover;
        double propFactor;
        double propFullFactor;
        double fullLoadOmega;
        double usableFraction;
        double landingCellVoltage;
        FrameRole role = FrameRole.FREESTYLE;
        boolean explicitArms;
        boolean uniformThrottle = true;
        final double[] motorX = new double[AirframeSpec.MOTORS];
        final double[] motorY = new double[AirframeSpec.MOTORS];
        final double[] motorZ = new double[AirframeSpec.MOTORS];
        final int[] spin = new int[AirframeSpec.MOTORS];
        final double[] mixRoll = new double[AirframeSpec.MOTORS];
        final double[] mixPitch = new double[AirframeSpec.MOTORS];
        final double[] mixThrottle = {1.0, 1.0, 1.0, 1.0};
        final double[] hullX = new double[AirframeSpec.HULL_POINTS];
        final double[] hullY = new double[AirframeSpec.HULL_POINTS];
        final double[] hullZ = new double[AirframeSpec.HULL_POINTS];
        int groundCount;
        int groundMask;
        final double[] groundX = new double[AirframeSpec.GROUND_POINTS];
        final double[] groundY = new double[AirframeSpec.GROUND_POINTS];
        final double[] groundZ = new double[AirframeSpec.GROUND_POINTS];
    }

    static double[] statorSize(MotorType motor) {
        MotorDefinition definition = motor.definition();
        return new double[]{definition.statorDiameterMm() / 1000.0, definition.statorHeightMm() / 1000.0};
    }

    static double batteryDensity(BatteryType.Chemistry chemistry) {
        return chemistry.definition().densityGramsPerCm3();
    }

    private static AirframeSpec compute(DroneBuild b) {
        return compute(b, b, null);
    }

    private static AirframeSpec compute(DroneBuild source, DroneBuild b, double @Nullable [] inertia) {
        Draft d = new Draft();
        FrameType frame = b.frame();
        FrameDefinition frameDef = frame.definition();
        AirframeGeometry geometry = frameDef.geometry();
        MotorType motor = b.motor();
        PropType prop = b.prop();
        BatteryType battery = b.battery();
        StackDefinition stackDef = b.stack().definition();
        d.build = source;
        Accessories accessories = b.accessories();
        d.buildKey = frame.key() + "/" + b.stack().key() + "/" + motor.key() + "/" + prop.key() + "/" + b.video().key() + "/"
                + battery.key() + (accessories.isEmpty() ? "" : "/" + accessories.key())
                + (source == b ? "" : "/creative/" + source.stack().key() + "/" + PartIndex.hashOf(source.creative()));
        d.buildable = source.buildable();
        d.assemblable = source.buildable(PartCompatibility.LIFTED);
        double m = b.totalMassGrams() / 1000.0;
        d.massKg = m;
        double diameter = prop.diameterMeters();
        double radius = diameter / 2.0;
        double area = Math.PI * radius * radius;
        d.propDiameter = diameter;
        d.propRadius = radius;
        d.discArea = area;
        d.propPitch = prop.pitchInch * 0.0254;
        d.ducted = frame.ducted;
        DuctSpec duct = frameDef.duct().orElse(DuctSpec.STANDARD);
        d.ductClearance = duct.clearanceMm().isPresent() ? duct.clearanceMm().getAsDouble() / 1000.0 : ModelConstants.DUCT_CLEARANCE;
        double[] stator = statorSize(motor);
        double statorDiameter = stator[0];
        double rotorY = stator[1] + ModelConstants.ROTOR_PLANE_EXTRA;

        FrameGeometry geo = FrameGeometry.of(frame);
        double batteryMass = battery.massGrams() / 1000.0;
        Optional<PackSize> pack = battery.definition().packSize();
        double bh;
        double bw;
        double bl;
        if (pack.isPresent()) {
            bh = pack.get().heightMm() / 1000.0;
            bw = pack.get().widthMm() / 1000.0;
            bl = pack.get().lengthMm() / 1000.0;
        } else {
            double density = batteryDensity(battery.chemistry);
            double volumeCm3 = battery.massGrams() / density;
            double heightCm = StrictMath.cbrt(volumeCm3 / BATTERY_VOLUME_RATIO);
            bh = heightCm / 100.0;
            bw = ModelConstants.BATTERY_WIDTH * bh;
            bl = ModelConstants.BATTERY_LENGTH * bh;
        }
        double batteryY = geo.batteryOnTop() ? geo.batteryPlateY() + bh / 2.0 : geo.batteryPlateY() - bh / 2.0;
        double electronicsMass = (b.stack().massGrams() + b.video().massGrams()) / 1000.0;
        double electronicsY = geo.electronicsY();
        double motorMass = motor.massGrams() / 1000.0;
        double propMass = prop.massGrams() / 1000.0;
        double frameMass = frame.massGrams() / 1000.0;
        d.motorMassKg = motorMass;
        d.stackMassKg = b.stack().massGrams() / 1000.0;

        int accessoryCount = accessories.size();
        double[] accessoryMass = new double[accessoryCount];
        double[][] accessoryCenter = new double[accessoryCount][];
        double[][] accessorySize = new double[accessoryCount][];
        double accessoryMoment = 0.0;
        double accessoryTop = Double.NEGATIVE_INFINITY;
        double accessoryTopX = 0.0;
        double accessoryTopZ = 0.0;
        int index = 0;
        for (AccessoryMount mount : accessories.mounts()) {
            AccessoryType type = accessories.at(mount).orElseThrow();
            AccessoryAnchor anchor = AccessoryAnchor.of(frame, mount);
            double[] center = anchor.centerOf(type);
            accessoryMass[index] = type.massGrams() / 1000.0;
            accessoryCenter[index] = new double[]{-center[0] / 1000.0, center[1] / 1000.0, -center[2] / 1000.0};
            accessorySize[index] = new double[]{type.widthMm * anchor.stretch(type) / 1000.0, type.heightMm / 1000.0, type.depthMm / 1000.0};
            accessoryMoment += accessoryMass[index] * accessoryCenter[index][1];
            if (mount == AccessoryMount.TOP) {
                for (double[] corner : anchor.corners(type)) {
                    if (corner[1] / 1000.0 > accessoryTop) {
                        accessoryTop = corner[1] / 1000.0;
                        accessoryTopX = -corner[0] / 1000.0;
                        accessoryTopZ = -corner[2] / 1000.0;
                    }
                }
            }
            index++;
        }
        d.accessoryMassKg = accessories.massGrams() / 1000.0;

        double kv = motor.kv * 2.0 * Math.PI / 60.0;
        double kt = 1.0 / kv;
        double escR = stackDef.escResistanceOhm().isPresent() ? stackDef.escResistanceOhm().getAsDouble()
                : ModelConstants.ESC_R_BASE + ModelConstants.ESC_R_PER_A / b.stack().escCurrentPerMotor;
        double rm = motor.resistanceOhm + escR;
        d.kv = kv;
        d.kt = kt;
        d.motorResistance = motor.resistanceOhm;
        d.escResistance = escR;
        d.idleCurrent = ModelConstants.I0_FRACTION * motor.maxCurrent;
        d.escCurrentLimit = stackDef.escBurstAmps().isPresent() ? stackDef.escBurstAmps().getAsDouble()
                : ModelConstants.ESC_BURST * b.stack().escCurrentPerMotor;
        d.escInputLimit = ModelConstants.ESC_INPUT_LIMIT * b.stack().escCurrentPerMotor;
        double bell = statorDiameter / 2.0 + ModelConstants.BELL_EXTRA;
        double j = ModelConstants.PROP_J * propMass * radius * radius + ModelConstants.BELL_J * motorMass * bell * bell;
        d.rotorInertia = j;
        d.omegaMax = ModelConstants.OMEGA_MAX_FACTOR * kv * battery.cells * battery.chemistry.fullCellVoltage;

        double kT = prop.thrustCoefficient * ModelConstants.RHO0 * StrictMath.pow(diameter, 4) / (4.0 * Math.PI * Math.PI);
        double kQ = prop.powerCoefficient * ModelConstants.RHO0 * StrictMath.pow(diameter, 5) / (8.0 * Math.PI * Math.PI * Math.PI);
        double ductGain = frame.ducted ? duct.thrustGain().orElse(ModelConstants.DUCT_GAIN) : 1.0;
        d.kThrust = kT;
        d.kTorque = kQ;
        d.ductGain = ductGain;
        double weight = m * ModelConstants.G;
        double omegaH = Math.sqrt(weight / (4.0 * kT * ductGain));
        d.hoverOmega = omegaH;
        double damping = kt * kt / rm + 2.0 * kQ * omegaH;
        double tauPhys = j / damping;
        double jEff = j * Math.min(1.0, ModelConstants.MOTOR_TAU_MAX / tauPhys);
        d.motorTauPhys = tauPhys;
        d.rotorInertiaEff = jEff;
        d.motorTau = jEff / damping;
        d.zeroThrustPerOmega = (d.propPitch + ModelConstants.ZERO_LIFT_PITCH * diameter) / (2.0 * Math.PI);
        d.hoverInducedVelocity = Math.sqrt(weight / (8.0 * ModelConstants.RHO0 * area * ductGain));
        d.washNoiseHz = 12.0 * Math.sqrt(0.127 / diameter);
        d.translationalLiftGain = frame.ducted ? duct.translationalLiftGain().orElse(ModelConstants.K_TL_DUCT) : ModelConstants.K_TL_OPEN;
        d.washLoss = frame.ducted ? duct.washLoss().orElse(ModelConstants.WASH_LOSS_DUCT) : ModelConstants.WASH_LOSS_OPEN;
        double washNoise = frame.ducted ? duct.washNoise().orElse(ModelConstants.WASH_NOISE_DUCT) : ModelConstants.WASH_NOISE_OPEN;
        double stiffness = prop.definition().stiffness();
        d.propStiffness = stiffness;
        d.washNoise = stiffness == 1.0 ? washNoise : washNoise / Math.sqrt(stiffness);
        d.propFlex = stiffness == 1.0 ? 0.0 : 1.0 / stiffness - 1.0;
        d.propBrittleness = PropFailure.of(prop.definition()).brittleness();
        double reverse = prop.definition().reverseThrust().orElse(ModelConstants.REVERSE_THRUST_STANDARD);
        d.reverseThrust = reverse;
        d.reverseTorque = ModelConstants.REVERSE_TORQUE_BASE + (1.0 - ModelConstants.REVERSE_TORQUE_BASE) * reverse;
        d.reversePitch = Math.sqrt(reverse);

        d.cells = battery.cells;
        d.capacityMah = battery.capacityMah;
        d.chemistry = battery.chemistry;
        d.packResistance = battery.cells * battery.cellResistanceOhm + ModelConstants.WIRING_R;
        d.polarizationResistance = ModelConstants.POL_FRACTION * d.packResistance;
        d.continuousCurrent = battery.maxContinuousCurrent();
        d.fullPackVoltage = battery.cells * battery.chemistry.fullCellVoltage;
        d.accessoryPower = accessories.flightBatteryWatts();
        d.auxPower = ModelConstants.AUX_BASE_W + ModelConstants.AUX_PER_GRAM_W * b.video().massGrams() + d.accessoryPower;
        d.avionicsPower = EnergyModel.AVIONICS_BASE_W + EnergyModel.videoPower(b.video()) + d.accessoryPower;
        d.driveLossHover = EnergyModel.driveLossAtHover(motor.massGrams(), prop.blades, diameter * 1000.0, kQ * omegaH * omegaH * omegaH);
        d.propFactor = EnergyModel.propFactor(diameter * 1000.0);
        d.propFullFactor = EnergyModel.propFullFactor(diameter * 1000.0);
        d.usableFraction = battery.chemistry.definition().effectiveUsableFraction();
        d.landingCellVoltage = battery.chemistry.definition().landingCellVoltage();
        d.role = frameDef.role();
        d.brownoutVoltage = stackDef.brownoutVoltage().isPresent() ? stackDef.brownoutVoltage().getAsDouble()
                : b.stack().minCells == 1 ? ModelConstants.BROWNOUT_1S : ModelConstants.BROWNOUT_MULTI;
        d.escMinCells = b.stack().minCells;
        d.escMaxCells = b.stack().maxCells;

        thermal(d, motor.definition());

        FrameAero aero = frameDef.aero();
        double accessoryArea = accessories.frontalAreaM2();
        double az = frame.frontalAreaM2 + accessoryArea;
        d.areaZ = az;
        double topFactor = frame.ducted ? ModelConstants.TOP_AREA_DUCT : ModelConstants.TOP_AREA_OPEN;
        d.areaX = aero.sideAreaM2().isPresent() ? aero.sideAreaM2().getAsDouble() + ModelConstants.SIDE_AREA * accessoryArea
                : ModelConstants.SIDE_AREA * az;
        d.areaY = aero.topAreaM2().isPresent() ? aero.topAreaM2().getAsDouble() + topFactor * accessoryArea : topFactor * az;
        d.dragCoefficient = frame.dragCoefficient;
        d.impactTolerance = frame.impactTolerance;
        d.restitution = frame.ducted ? duct.restitution().orElse(ModelConstants.RESTITUTION_DUCT) : ModelConstants.RESTITUTION_OPEN;
        d.friction = frame.ducted ? duct.friction().orElse(ModelConstants.FRICTION_DUCT) : ModelConstants.FRICTION_OPEN;
        d.cameraTiltDeg = geometry.cameraTiltDeg();
        d.cameraFovDeg = FovMath.cameraHorizontalDegrees(b.video());

        Body body = new Body(batteryMass, batteryY, bw, bh, bl, electronicsMass, electronicsY, motorMass, propMass, frameMass, rotorY,
                accessoryCount, accessoryMass, accessoryCenter, accessorySize, accessoryMoment, accessoryTop, accessoryTopX, accessoryTopZ,
                geo.batteryOnTop());
        if (geometry.hasExplicitArms()) {
            geometric(d, frameDef, body);
        } else {
            legacy(d, frame, body);
        }
        lens(d, geometry);
        if (inertia != null) {
            d.inertiaZz = Double.isNaN(inertia[0]) ? d.inertiaZz : inertia[0];
            d.inertiaXx = Double.isNaN(inertia[1]) ? d.inertiaXx : inertia[1];
            d.inertiaYy = Double.isNaN(inertia[2]) ? d.inertiaYy : inertia[2];
        }

        AirframeSpec preliminary = new AirframeSpec(d);
        OperatingPoints.Point hover = new OperatingPoints.Point();
        OperatingPoints.hover(preliminary, 0.5, true, 0.0, true, hover);
        double vh = hover.busVoltage;
        d.hoverVoltage = vh;
        OperatingPoints.Point full = new OperatingPoints.Point();
        OperatingPoints.uniformDuty(preliminary, 1.0, 1.0, false, 0.0, 0.0, full);
        d.fullLoadOmega = full.omega;
        double idle = ModelConstants.IDLE_REFERENCE;
        double dOmegaDDuty = (kt * vh / rm) / damping;
        double dThrustDOmega = 2.0 * kT * ductGain * omegaH;
        if (d.explicitArms) {
            double rollLever = lever(d.motorX, d.mixRoll);
            double pitchLever = lever(d.motorZ, d.mixPitch, -1.0);
            d.authorityRoll = rollLever * dThrustDOmega * dOmegaDDuty * (1.0 - idle) / d.inertiaZz;
            d.authorityPitch = pitchLever * dThrustDOmega * dOmegaDDuty * (1.0 - idle) / d.inertiaXx;
        } else {
            double s = d.motorOffset;
            d.authorityRoll = 4.0 * s * dThrustDOmega * dOmegaDDuty * (1.0 - idle) / d.inertiaZz;
            d.authorityPitch = 4.0 * s * dThrustDOmega * dOmegaDDuty * (1.0 - idle) / d.inertiaXx;
        }
        d.authorityYawSteady = 4.0 * (2.0 * kQ * omegaH) * dOmegaDDuty * (1.0 - idle) / d.inertiaYy;
        d.authorityYawInstant = 4.0 * kt * vh * (1.0 - idle) / (rm * d.inertiaYy);
        return new AirframeSpec(d);
    }

    private static double lever(double[] position, double[] mix) {
        return lever(position, mix, 1.0);
    }

    private static double lever(double[] position, double[] mix, double sign) {
        double sum = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            sum += sign * position[i] * mix[i];
        }
        return Math.max(sum, 1e-4);
    }

    private record Body(double batteryMass, double batteryY, double bw, double bh, double bl, double electronicsMass, double electronicsY,
                        double motorMass, double propMass, double frameMass, double rotorY, int accessoryCount, double[] accessoryMass,
                        double[][] accessoryCenter, double[][] accessorySize, double accessoryMoment, double accessoryTop,
                        double accessoryTopX, double accessoryTopZ, boolean batteryOnTop) {
    }

    static void thermal(Draft d, MotorDefinition motor) {
        MotorThermal thermal = motor.thermal();
        double motorGrams = d.motorMassKg * 1000.0;
        d.motorHeatCapacity = thermal.heatCapacityJPerK().isPresent() ? thermal.heatCapacityJPerK().getAsDouble()
                : Thermal.MOTOR_HEAT_CAPACITY * motorGrams;
        double sizeFactor = StrictMath.pow(motorGrams / 33.0, 2.0 / 3.0);
        boolean baseSet = thermal.coolingWPerK().isPresent();
        boolean omegaSet = thermal.coolingWPerKPerRadS().isPresent();
        if (!baseSet && !omegaSet) {
            d.motorCoolingBase = Thermal.MOTOR_CONDUCTANCE_BASE;
            d.motorCoolingPerOmega = Thermal.MOTOR_CONDUCTANCE_PER_OMEGA;
            d.motorCoolingScale = sizeFactor;
        } else {
            d.motorCoolingBase = baseSet ? thermal.coolingWPerK().getAsDouble() : Thermal.MOTOR_CONDUCTANCE_BASE * sizeFactor;
            d.motorCoolingPerOmega = omegaSet ? thermal.coolingWPerKPerRadS().getAsDouble() : Thermal.MOTOR_CONDUCTANCE_PER_OMEGA * sizeFactor;
            d.motorCoolingScale = 1.0;
        }
        d.motorDerateStartC = thermal.derateStartC();
        d.motorMaxTempC = thermal.maxTempC();
        d.motorHeatWearPerSecond = thermal.wearPerSecondAtMax() / motor.basics().durability();
    }

    private static void legacy(Draft d, FrameType frame, Body body) {
        double m = d.massKg;
        double armLength = frame.armLengthMeters();
        double s = armLength / SQRT2;
        d.armLength = armLength;
        d.motorOffset = s;
        d.motorSpacing = 2.0 * s;
        double radius = d.propRadius;
        double diameter = d.propDiameter;
        double rotorY = body.rotorY;
        double batteryMass = body.batteryMass;
        double batteryY = body.batteryY;
        double electronicsMass = body.electronicsMass;
        double electronicsY = body.electronicsY;
        double motorMass = body.motorMass;
        double propMass = body.propMass;
        double frameMass = body.frameMass;
        double bw = body.bw;
        double bh = body.bh;
        double bl = body.bl;

        double yCom = (batteryMass * batteryY + electronicsMass * electronicsY + 4.0 * motorMass * rotorY / 2.0
                + 4.0 * propMass * rotorY + body.accessoryMoment) / m;
        double ym = rotorY - yCom;
        d.comHeight = yCom;
        d.rotorPlaneOffset = ym;

        double ixx = 0.0;
        double iyy = 0.0;
        double yMot = rotorY / 2.0 - yCom;
        ixx += 4.0 * motorMass * (s * s + yMot * yMot);
        iyy += 8.0 * motorMass * s * s;
        ixx += 4.0 * propMass * (s * s + ym * ym);
        iyy += 8.0 * propMass * s * s;
        MassSplit split = frame.definition().geometry().massSplit();
        double ma = split.arms() * frameMass;
        double mp = split.plate() * frameMass;
        double mr = split.duct() * frameMass;
        double plate = PLATE_FRACTION * armLength;
        double yc2 = yCom * yCom;
        ixx += ma * (armLength * armLength / 6.0 + yc2);
        iyy += ma * armLength * armLength / 3.0;
        ixx += mp * (plate * plate / 12.0 + yc2);
        iyy += mp * plate * plate / 6.0;
        if (mr > 0.0) {
            double rd = radius + d.ductClearance;
            ixx += mr * (s * s + rd * rd / 2.0 + yc2);
            iyy += mr * (2.0 * s * s + rd * rd);
        }
        double izz = ixx;
        double yb = batteryY - yCom;
        ixx += batteryMass * ((bh * bh + bl * bl) / 12.0 + yb * yb);
        iyy += batteryMass * (bw * bw + bl * bl) / 12.0;
        izz += batteryMass * ((bw * bw + bh * bh) / 12.0 + yb * yb);
        double ye = electronicsY - yCom;
        ixx += electronicsMass * ye * ye;
        izz += electronicsMass * ye * ye;
        for (int k = 0; k < body.accessoryCount; k++) {
            double mk = body.accessoryMass[k];
            double xa = body.accessoryCenter[k][0];
            double ya = body.accessoryCenter[k][1] - yCom;
            double za = body.accessoryCenter[k][2];
            double wa = body.accessorySize[k][0];
            double ha = body.accessorySize[k][1];
            double da = body.accessorySize[k][2];
            ixx += mk * ((ha * ha + da * da) / 12.0 + ya * ya + za * za);
            iyy += mk * ((wa * wa + da * da) / 12.0 + xa * xa + za * za);
            izz += mk * ((wa * wa + ha * ha) / 12.0 + xa * xa + ya * ya);
        }
        d.inertiaXx = ixx;
        d.inertiaYy = iyy;
        d.inertiaZz = izz;
        d.rotationalDamping = 0.5 * ModelConstants.RHO0 * d.dragCoefficient * d.areaY * armLength * armLength * armLength / 4.0;

        double[] sx = {-s, -s, s, s};
        double[] sz = {-s, s, -s, s};
        int[] spin = {-1, 1, 1, -1};
        double tipRadius = radius + (frame.ducted ? d.ductClearance : 0.0);
        double tip = s + tipRadius / SQRT2;
        double bottom = -yCom - ModelConstants.HULL_MARGIN;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            d.motorX[i] = sx[i];
            d.motorY[i] = ym;
            d.motorZ[i] = sz[i];
            d.spin[i] = spin[i];
            d.mixRoll[i] = sx[i] / s;
            d.mixPitch[i] = -sz[i] / s;
            d.hullX[i] = Math.signum(sx[i]) * tip;
            d.hullY[i] = ym;
            d.hullZ[i] = Math.signum(sz[i]) * tip;
            d.hullX[4 + i] = sx[i];
            d.hullY[4 + i] = bottom;
            d.hullZ[4 + i] = sz[i];
        }
        double batteryFace = (body.batteryOnTop ? batteryY + bh / 2.0 : batteryY - bh / 2.0) - yCom;
        d.hullX[8] = 0.0;
        d.hullY[8] = batteryFace;
        d.hullZ[8] = bl / 2.0;
        d.hullX[9] = 0.0;
        d.hullY[9] = batteryFace;
        d.hullZ[9] = -bl / 2.0;
        if (body.batteryOnTop && body.accessoryTop - yCom > d.hullY[8]) {
            d.hullY[8] = body.accessoryTop - yCom;
            d.hullZ[8] = body.accessoryTopZ;
        }
        d.hullX[10] = 0.0;
        d.hullY[10] = electronicsY - yCom;
        d.hullZ[10] = 0.8 * s;
        d.hullX[11] = 0.0;
        d.hullY[11] = bottom;
        d.hullZ[11] = 0.0;
        groundPoints(d, 0.0, batteryFace, bw, bl, 0.0, frame.ducted, bottom);
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            minY = Math.min(minY, d.hullY[k]);
            maxY = Math.max(maxY, d.hullY[k]);
        }
        d.hitboxWidth = 2.0 * s + diameter + (frame.ducted ? 2.0 * d.ductClearance : 0.0);
        d.hitboxHeight = maxY - minY;
        d.hitboxFootOffset = minY;
    }

    static final class Arms {
        final double[] x = new double[AirframeSpec.MOTORS];
        final double[] y = new double[AirframeSpec.MOTORS];
        final double[] z = new double[AirframeSpec.MOTORS];
        final int[] spin = new int[AirframeSpec.MOTORS];

        static Arms of(List<MotorArm> arms) {
            Arms out = new Arms();
            for (int position = 0; position < MotorArm.COUNT; position++) {
                MotorArm arm = arms.get(position);
                int motor = PartWear.simMotor(position);
                out.x[motor] = -arm.position().x() / 1000.0;
                out.y[motor] = arm.position().y() / 1000.0;
                out.z[motor] = -arm.position().z() / 1000.0;
                out.spin[motor] = arm.spin() == MotorArm.Spin.CW ? -1 : 1;
            }
            return out;
        }
    }

    private static void geometric(Draft d, FrameDefinition frameDef, Body body) {
        d.explicitArms = true;
        AirframeGeometry geometry = frameDef.geometry();
        Arms arms = Arms.of(geometry.arms());
        double m = d.massKg;
        double rotorY = body.rotorY;
        Vec3Mm frameCom = geometry.frameCenterOfMassMm();
        double fx = -frameCom.x() / 1000.0;
        double fy = frameCom.y() / 1000.0;
        double fz = -frameCom.z() / 1000.0;

        double mx = body.frameMass * fx;
        double my = body.frameMass * fy + body.batteryMass * body.batteryY + body.electronicsMass * body.electronicsY;
        double mz = body.frameMass * fz;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            mx += (body.motorMass + body.propMass) * arms.x[i];
            my += body.motorMass * (arms.y[i] + rotorY / 2.0) + body.propMass * (arms.y[i] + rotorY);
            mz += (body.motorMass + body.propMass) * arms.z[i];
        }
        for (int k = 0; k < body.accessoryCount; k++) {
            mx += body.accessoryMass[k] * body.accessoryCenter[k][0];
            my += body.accessoryMass[k] * body.accessoryCenter[k][1];
            mz += body.accessoryMass[k] * body.accessoryCenter[k][2];
        }
        double cx = mx / m;
        double cy = my / m;
        double cz = mz / m;
        d.comX = cx;
        d.comZ = cz;
        d.comHeight = cy;

        double[] px = new double[AirframeSpec.MOTORS];
        double[] pz = new double[AirframeSpec.MOTORS];
        double maxX = 0.0;
        double maxZ = 0.0;
        double offset = 0.0;
        double reach = 0.0;
        double rotorPlane = 0.0;
        double armLow = Double.POSITIVE_INFINITY;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            px[i] = arms.x[i] - cx;
            pz[i] = arms.z[i] - cz;
            d.motorX[i] = px[i];
            d.motorY[i] = arms.y[i] + rotorY - cy;
            d.motorZ[i] = pz[i];
            d.spin[i] = arms.spin[i];
            maxX = Math.max(maxX, Math.abs(px[i]));
            maxZ = Math.max(maxZ, Math.abs(pz[i]));
            offset += 0.5 * (Math.abs(px[i]) + Math.abs(pz[i]));
            reach += Math.sqrt(px[i] * px[i] + pz[i] * pz[i]);
            rotorPlane += d.motorY[i];
            armLow = Math.min(armLow, arms.y[i]);
        }
        d.motorOffset = offset / AirframeSpec.MOTORS;
        d.armLength = reach / AirframeSpec.MOTORS;
        d.rotorPlaneOffset = rotorPlane / AirframeSpec.MOTORS;
        double spacing = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double nearest = Double.POSITIVE_INFINITY;
            for (int k = 0; k < AirframeSpec.MOTORS; k++) {
                if (k != i) {
                    double dx = px[i] - px[k];
                    double dz = pz[i] - pz[k];
                    nearest = Math.min(nearest, Math.sqrt(dx * dx + dz * dz));
                }
            }
            spacing += nearest;
        }
        d.motorSpacing = spacing / AirframeSpec.MOTORS;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            d.mixRoll[i] = maxX > 1e-6 ? px[i] / maxX : 0.0;
            d.mixPitch[i] = maxZ > 1e-6 ? -pz[i] / maxZ : 0.0;
        }
        throttleWeights(d, px, pz);

        Inertia inertia = new Inertia();
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            inertia.point(body.motorMass, px[i], arms.y[i] + rotorY / 2.0 - cy, pz[i]);
            inertia.point(body.propMass, px[i], arms.y[i] + rotorY - cy, pz[i]);
        }
        MassSplit split = geometry.massSplit();
        double ma = split.arms() * body.frameMass / AirframeSpec.MOTORS;
        double mp = split.plate() * body.frameMass;
        double mr = split.duct() * body.frameMass / AirframeSpec.MOTORS;
        double minX = Double.POSITIVE_INFINITY;
        double maxXf = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZf = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            inertia.rod(ma, -cx, -cy, -cz, px[i], arms.y[i] - cy, pz[i]);
            minX = Math.min(minX, arms.x[i]);
            maxXf = Math.max(maxXf, arms.x[i]);
            minZ = Math.min(minZ, arms.z[i]);
            maxZf = Math.max(maxZf, arms.z[i]);
        }
        double plateX = PLATE_FRACTION / SQRT2 * (maxXf - minX);
        double plateZ = PLATE_FRACTION / SQRT2 * (maxZf - minZ);
        inertia.box(mp, plateX, 0.0, plateZ, 0.5 * (minX + maxXf) - cx, -cy, 0.5 * (minZ + maxZf) - cz);
        double rd = d.propRadius + d.ductClearance;
        if (mr > 0.0) {
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                inertia.ring(mr, rd, px[i], arms.y[i] - cy, pz[i]);
            }
        }
        inertia.box(body.batteryMass, body.bw, body.bh, body.bl, -cx, body.batteryY - cy, -cz);
        inertia.point(body.electronicsMass, -cx, body.electronicsY - cy, -cz);
        for (int k = 0; k < body.accessoryCount; k++) {
            double[] c = body.accessoryCenter[k];
            double[] size = body.accessorySize[k];
            inertia.box(body.accessoryMass[k], size[0], size[1], size[2], c[0] - cx, c[1] - cy, c[2] - cz);
        }
        d.inertiaXx = inertia.xx;
        d.inertiaYy = inertia.yy;
        d.inertiaZz = inertia.zz;
        double l = d.armLength;
        d.rotationalDamping = 0.5 * ModelConstants.RHO0 * d.dragCoefficient * d.areaY * l * l * l / 4.0;

        double tipRadius = d.propRadius + (d.ducted ? d.ductClearance : 0.0);
        double bottom = armLow - cy - ModelConstants.HULL_MARGIN;
        double half = 0.0;
        double front = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double r = Math.sqrt(px[i] * px[i] + pz[i] * pz[i]);
            double ux = r > 1e-9 ? px[i] / r : 0.0;
            double uz = r > 1e-9 ? pz[i] / r : 1.0;
            d.hullX[i] = px[i] + tipRadius * ux;
            d.hullY[i] = d.motorY[i];
            d.hullZ[i] = pz[i] + tipRadius * uz;
            d.hullX[4 + i] = px[i];
            d.hullY[4 + i] = bottom;
            d.hullZ[4 + i] = pz[i];
            half = Math.max(half, Math.max(Math.abs(px[i]), Math.abs(pz[i])) + tipRadius);
            front = Math.max(front, pz[i]);
        }
        double batteryFace = (body.batteryOnTop ? body.batteryY + body.bh / 2.0 : body.batteryY - body.bh / 2.0) - cy;
        d.hullX[8] = -cx;
        d.hullY[8] = batteryFace;
        d.hullZ[8] = body.bl / 2.0 - cz;
        d.hullX[9] = -cx;
        d.hullY[9] = batteryFace;
        d.hullZ[9] = -body.bl / 2.0 - cz;
        if (body.batteryOnTop && body.accessoryTop - cy > d.hullY[8]) {
            d.hullX[8] = body.accessoryTopX - cx;
            d.hullY[8] = body.accessoryTop - cy;
            d.hullZ[8] = body.accessoryTopZ - cz;
        }
        Optional<Vec3Mm> camera = geometry.cameraMm();
        if (camera.isPresent()) {
            d.hullX[10] = -camera.get().x() / 1000.0 - cx;
            d.hullY[10] = camera.get().y() / 1000.0 - cy;
            d.hullZ[10] = -camera.get().z() / 1000.0 - cz;
        } else {
            d.hullX[10] = -cx;
            d.hullY[10] = body.electronicsY - cy;
            d.hullZ[10] = 0.8 * front;
        }
        d.hullX[11] = -cx;
        d.hullY[11] = bottom;
        d.hullZ[11] = -cz;
        groundPoints(d, -cx, batteryFace, body.bw, body.bl, -cz, d.ducted, bottom);
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
            minY = Math.min(minY, d.hullY[k]);
            maxY = Math.max(maxY, d.hullY[k]);
        }
        d.hitboxWidth = 2.0 * half;
        d.hitboxHeight = maxY - minY;
        d.hitboxFootOffset = minY;
    }

    static void groundPoints(Draft d, double x, double face, double width, double length, double z, boolean ducted, double bottom) {
        int n = AirframeSpec.HULL_POINTS;
        System.arraycopy(d.hullX, 0, d.groundX, 0, n);
        System.arraycopy(d.hullY, 0, d.groundY, 0, n);
        System.arraycopy(d.hullZ, 0, d.groundZ, 0, n);
        int mask = (1 << n) - 1;
        mask &= ~(1 << 11);
        for (int k = 8; k <= 9; k++) {
            if (d.hullY[k] == face) {
                mask &= ~(1 << k);
            }
        }
        for (int c = 0; c < 4; c++) {
            d.groundX[n] = x + ((c & 1) == 0 ? -0.5 : 0.5) * width;
            d.groundY[n] = face;
            d.groundZ[n] = z + ((c & 2) == 0 ? -0.5 : 0.5) * length;
            mask |= 1 << n;
            n++;
        }
        if (ducted) {
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                d.groundX[n] = d.hullX[i];
                d.groundY[n] = Math.min(bottom, d.hullY[i]);
                d.groundZ[n] = d.hullZ[i];
                mask |= 1 << n;
                if (d.groundY[n] <= d.hullY[4 + i]) {
                    mask &= ~(1 << (4 + i));
                }
                n++;
            }
        }
        d.groundCount = n;
        d.groundMask = mask;
    }

    static void throttleWeights(Draft d, double[] px, double[] pz) {
        double[][] a = {px, pz, {d.spin[0], d.spin[1], d.spin[2], d.spin[3]}};
        double[] residual = new double[3];
        double[][] gram = new double[3][3];
        for (int r = 0; r < 3; r++) {
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                residual[r] += a[r][i];
            }
            for (int c = 0; c < 3; c++) {
                for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                    gram[r][c] += a[r][i] * a[c][i];
                }
            }
        }
        double[] lambda = SimMath.solve3(gram, residual);
        if (lambda == null) {
            return;
        }
        double[] thrust = new double[AirframeSpec.MOTORS];
        double max = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double correction = 0.0;
            for (int r = 0; r < 3; r++) {
                correction += a[r][i] * lambda[r];
            }
            thrust[i] = 1.0 - correction;
            max = Math.max(max, thrust[i]);
        }
        if (!(max > 0.0) || !Double.isFinite(max)) {
            return;
        }
        boolean uniform = true;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double w = Math.sqrt(Math.max(MIN_THROTTLE_WEIGHT * MIN_THROTTLE_WEIGHT, thrust[i] / max));
            d.mixThrottle[i] = Math.min(1.0, w);
            uniform &= Math.abs(d.mixThrottle[i] - 1.0) < 1e-9;
        }
        if (uniform) {
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                d.mixThrottle[i] = 1.0;
            }
        }
        d.uniformThrottle = uniform;
    }

    private static final class Inertia {
        double xx;
        double yy;
        double zz;

        void point(double m, double x, double y, double z) {
            this.xx += m * (y * y + z * z);
            this.yy += m * (x * x + z * z);
            this.zz += m * (x * x + y * y);
        }

        void rod(double m, double ax, double ay, double az, double bx, double by, double bz) {
            double x2 = (ax * ax + ax * bx + bx * bx) / 3.0;
            double y2 = (ay * ay + ay * by + by * by) / 3.0;
            double z2 = (az * az + az * bz + bz * bz) / 3.0;
            this.xx += m * (y2 + z2);
            this.yy += m * (x2 + z2);
            this.zz += m * (x2 + y2);
        }

        void box(double m, double sx, double sy, double sz, double x, double y, double z) {
            this.xx += m * ((sy * sy + sz * sz) / 12.0 + y * y + z * z);
            this.yy += m * ((sx * sx + sz * sz) / 12.0 + x * x + z * z);
            this.zz += m * ((sx * sx + sy * sy) / 12.0 + x * x + y * y);
        }

        void ring(double m, double r, double x, double y, double z) {
            this.xx += m * (r * r / 2.0 + y * y + z * z);
            this.yy += m * (r * r + x * x + z * z);
            this.zz += m * (r * r / 2.0 + x * x + y * y);
        }
    }

    private static void lens(Draft d, AirframeGeometry geometry) {
        Optional<Vec3Mm> camera = geometry.cameraMm();
        if (camera.isPresent()) {
            Vec3Mm c = camera.get();
            d.lensRight = c.x() / 1000.0 + d.comX;
            d.lensUp = c.y() / 1000.0 - d.comHeight;
            d.lensForward = -c.z() / 1000.0 - d.comZ;
        } else if (d.explicitArms) {
            double front = 0.0;
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                front = Math.max(front, d.motorZ[i]);
            }
            d.lensRight = d.comX;
            d.lensUp = AirframeSpec.LEGACY_LENS_UP;
            d.lensForward = front * AirframeSpec.LEGACY_LENS_FORWARD_FACTOR;
        } else {
            d.lensRight = 0.0;
            d.lensUp = AirframeSpec.LEGACY_LENS_UP;
            d.lensForward = d.motorOffset * AirframeSpec.LEGACY_LENS_FORWARD_FACTOR;
        }
    }
}
