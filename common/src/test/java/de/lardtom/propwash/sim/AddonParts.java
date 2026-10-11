package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.DuctSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.MotorThermal;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PackSize;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class AddonParts {
    static final String NAMESPACE = "propwash_simtest";
    private static final AtomicInteger SERIAL = new AtomicInteger();

    private AddonParts() {
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path + "_" + SERIAL.incrementAndGet());
    }

    @SuppressWarnings("unchecked")
    static <T extends PartInfo> T part(PartDefinition definition) {
        return (T) PartIndex.detached(definition);
    }

    static FrameDefinition.Builder fiveInch(String name) {
        return FrameDefinition.builder(id(name))
                .mass(118.0).role(FrameRole.FREESTYLE).wheelbase(225).maxProp(130)
                .motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30, Mounts.STACK_S20)
                .accessoryMount(AccessorySlot.TOP, AccessorySlot.BOTTOM)
                .battery(true, 30.0).electronics(12.0).cameraTilt(28.0)
                .aero(0.012, 0.9).impactTolerance(8.0)
                .batteryFit(new BatteryFitRule(4, 6, 260.0, 6, 0));
    }

    static List<MotorArm> arms(double frontX, double frontZ, double rearX, double rearZ) {
        return List.of(MotorArm.of(-frontX, 0.0, -frontZ, MotorArm.Spin.CW), MotorArm.of(frontX, 0.0, -frontZ, MotorArm.Spin.CCW),
                MotorArm.of(-rearX, 0.0, rearZ, MotorArm.Spin.CCW), MotorArm.of(rearX, 0.0, rearZ, MotorArm.Spin.CW));
    }

    static FrameType deadcat() {
        return part(fiveInch("deadcat_5").shape(FrameShape.DEADCAT).wheelbase(230)
                .arms(arms(95.0, 60.0, 80.0, 80.0))
                .camera(new Vec3Mm(0.0, 14.0, -100.0)).cameraTilt(30.0)
                .centerOfMass(new Vec3Mm(0.0, 0.0, 6.0))
                .build());
    }

    static FrameType trueXExplicit(FrameType legacy) {
        return trueXExplicit(legacy, false);
    }

    static FrameType trueXExplicit(FrameType legacy, boolean camera) {
        FrameDefinition d = legacy.definition();
        FrameDefinition.Builder b = FrameDefinition.builder(id((camera ? "explicit_camera_" : "explicit_") + legacy.key()))
                .basics(d.basics()).role(d.role()).shape(d.shape()).wheelbase(d.wheelbaseMm()).props(d.minPropMm(), d.maxPropMm())
                .arms(MotorArm.trueX(d.wheelbaseMm(), 0.0))
                .battery(d.geometry().batteryOnTop(), d.geometry().batteryPlateMm()).electronics(d.geometry().electronicsMm())
                .cameraTilt(d.geometry().cameraTiltDeg())
                .aero(d.aero().frontalAreaM2(), d.aero().dragCoefficient()).impactTolerance(d.impactTolerance()).batteryFit(d.batteryFit());
        d.motorMounts().forEach(b::motorMount);
        d.stackMounts().forEach(b::stackMount);
        d.accessoryMounts().forEach(b::accessoryMount);
        d.duct().ifPresent(b::duct);
        d.aero().sideAreaM2().ifPresent(b::sideArea);
        d.aero().topAreaM2().ifPresent(b::topArea);
        if (camera) {
            d.geometry().cameraMm().ifPresent(b::camera);
        }
        return part(b.build());
    }

    static FrameType stretched(double x, double z) {
        return part(fiveInch("stretched").shape(FrameShape.STRETCHED_X).arms(arms(x, z, x, z)).build());
    }

    static FrameType xClass13() {
        return part(FrameDefinition.builder(id("xclass_13"))
                .mass(1450.0).role(FrameRole.X_CLASS).wheelbase(820).props(250, 330)
                .motorMount(Mounts.MOTOR_M28).stackMount(Mounts.STACK_S30)
                .battery(true, 40.0).electronics(30.0).cameraTilt(25.0)
                .aero(0.08, 1.0).impactTolerance(6.0)
                .batteryFit(new BatteryFitRule(6, 8, 2500.0, 8, 2000))
                .build());
    }

    static MotorType motor4214() {
        return part(MotorDefinition.builder(id("m4214_400"))
                .mass(128.0).stator("4214").kv(400).mount(Mounts.MOTOR_M28).maxCurrent(60.0).resistance(0.045).cells(6, 12)
                .thermal(MotorThermal.limits(140.0, 160.0))
                .build());
    }

    static PropType prop13x7() {
        return part(PropDefinition.builder(id("p13_7_2b"))
                .mass(28.0).size(330, 7.0, 2).coefficients(0.095, 0.040).material(PropMaterial.CARBON_FIBER_NYLON)
                .build());
    }

    static StackType stack3to8() {
        return part(StackDefinition.builder(id("stack_3_8"))
                .mass(20.0).mount(Mounts.STACK_S30).escContinuous(60.0).cells(3, 8)
                .build());
    }

    static BatteryType lipo(int cells, int capacityMah, double massGrams, double cellResistance, int cRating) {
        return part(BatteryDefinition.builder(id("b" + cells + "s_" + capacityMah))
                .mass(massGrams).cells(cells).capacity(capacityMah).cellResistance(cellResistance).cRating(cRating)
                .chemistry(BatteryDefinition.LIPO)
                .build());
    }

    static BatteryType lion8s4500() {
        return part(BatteryDefinition.builder(id("b8s_4500_lion"))
                .mass(560.0).cells(8).capacity(4500).cellResistance(0.014).cRating(12).chemistry(BatteryDefinition.LION)
                .packSize(new PackSize(140.0, 45.0, 70.0)).connector("XT60")
                .build());
    }

    static DroneBuild xClassBuild() {
        return new DroneBuild(xClass13(), stack3to8(), motor4214(), prop13x7(), VideoType.DIGITAL_HD, lipo(8, 2200, 450.0, 0.008, 75));
    }

    static FrameType longRange8s() {
        return part(FrameDefinition.builder(id("lr7_8s"))
                .mass(175.0).role(FrameRole.LONG_RANGE).wheelbase(300).maxProp(180)
                .motorMount(Mounts.MOTOR_M28, Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30, Mounts.STACK_S20)
                .battery(true, 32.0).electronics(14.0).cameraTilt(20.0)
                .aero(0.017, 0.9).impactTolerance(8.0)
                .batteryFit(new BatteryFitRule(6, 8, 800.0, 8, 3000))
                .build());
    }

    static MotorType motor2807lowKv() {
        return part(MotorDefinition.builder(id("m2807_1050"))
                .mass(47.0).stator("2807").kv(1050).mount(Mounts.MOTOR_M28).maxCurrent(40.0).resistance(0.085).cells(6, 8)
                .build());
    }

    static DroneBuild longRange8sBuild() {
        return new DroneBuild(longRange8s(), stack3to8(), motor2807lowKv(), PropType.P7_2B, VideoType.DIGITAL_HD, lion8s4500());
    }

    static FrameType toothpick() {
        return part(FrameDefinition.builder(id("toothpick_2"))
                .mass(8.0).role(FrameRole.TOOTHPICK).wheelbase(90).maxProp(51)
                .motorMount(Mounts.MOTOR_M08).stackMount(Mounts.STACK_WHOOP_AIO)
                .battery(false, -3.0).electronics(5.0).cameraTilt(15.0)
                .aero(0.004, 1.0).impactTolerance(10.0)
                .batteryFit(new BatteryFitRule(1, 2, 30.0, 1, 0))
                .build());
    }

    static MotorType motor1103() {
        return part(MotorDefinition.builder(id("m1103_8000"))
                .mass(3.8).stator("1103").kv(8000).mount(Mounts.MOTOR_M08).maxCurrent(5.0).resistance(0.30).cells(1, 2)
                .build());
    }

    static PropType prop2in() {
        return part(PropDefinition.builder(id("p2_3b"))
                .mass(0.6).size(51, 1.5, 3).coefficients(0.095, 0.075)
                .build());
    }

    static DroneBuild toothpickBuild(BatteryType battery) {
        return new DroneBuild(toothpick(), StackType.AIO_WHOOP, motor1103(), prop2in(), VideoType.ANALOG_NANO, battery);
    }

    static FrameType ductedWith(DuctSpec duct) {
        return part(fiveInch("ducted").wheelbase(142).maxProp(76).motorMount(Mounts.MOTOR_M14).stackMount(Mounts.STACK_S20)
                .duct(duct).batteryFit(new BatteryFitRule(3, 4, 120.0, 3, 0)).build());
    }
}
