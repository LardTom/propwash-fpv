package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.DuctSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PackSize;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartVisual;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import net.minecraft.resources.Identifier;

import java.util.List;

final class RenderTestParts {
    static final String NS = "rendertest";
    static final Identifier MOUNT_M42 = Mounts.MOTOR_M28;

    static final FrameType DEADCAT = frame(FrameDefinition.builder(id("deadcat_5")).mass(118.0).shape(FrameShape.DEADCAT).wheelbase(230)
            .maxProp(130).motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30).accessoryMount(AccessorySlot.TOP, AccessorySlot.BOTTOM)
            .arms(List.of(MotorArm.of(-105.0, 0.0, -30.0, MotorArm.Spin.CW), MotorArm.of(105.0, 0.0, -30.0, MotorArm.Spin.CCW),
                    MotorArm.of(-80.0, 0.0, 90.0, MotorArm.Spin.CCW), MotorArm.of(80.0, 0.0, 90.0, MotorArm.Spin.CW)))
            .battery(true, 30.0).electronics(12.0).camera(new Vec3Mm(0.0, 16.0, -92.0)).cameraTilt(30.0)
            .aero(0.012, 0.9).batteryFit(new BatteryFitRule(4, 6, 260.0, 6, 0)));
    static final FrameType CONTRACT_DEADCAT = frame(FrameDefinition.builder(id("deadcat_contract")).mass(118.0).shape(FrameShape.DEADCAT)
            .wheelbase(230).maxProp(130).motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30, Mounts.STACK_S20)
            .accessoryMount(AccessorySlot.TOP, AccessorySlot.BOTTOM)
            .arms(List.of(MotorArm.of(-95.0, 0.0, -60.0, MotorArm.Spin.CW), MotorArm.of(95.0, 0.0, -60.0, MotorArm.Spin.CCW),
                    MotorArm.of(-80.0, 0.0, 80.0, MotorArm.Spin.CCW), MotorArm.of(80.0, 0.0, 80.0, MotorArm.Spin.CW)))
            .battery(true, 30.0).electronics(12.0).camera(new Vec3Mm(0.0, 14.0, -58.0)).cameraTilt(30.0).centerOfMass(new Vec3Mm(0.0, 0.0, 6.0))
            .aero(0.012, 0.9).batteryFit(new BatteryFitRule(4, 6, 260.0, 6, 0)));
    static final FrameType STRETCHED = frame(FrameDefinition.builder(id("stretched_race")).mass(80.0).role(FrameRole.RACE)
            .shape(FrameShape.STRETCHED_X).wheelbase(220).maxProp(130).motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S20)
            .accessoryMount(AccessorySlot.BOTTOM)
            .arms(List.of(MotorArm.of(-62.0, 0.0, -90.0, MotorArm.Spin.CW), MotorArm.of(62.0, 0.0, -90.0, MotorArm.Spin.CCW),
                    MotorArm.of(-62.0, 0.0, 90.0, MotorArm.Spin.CCW), MotorArm.of(62.0, 0.0, 90.0, MotorArm.Spin.CW)))
            .cameraTilt(40.0).aero(0.009, 0.75).batteryFit(new BatteryFitRule(4, 6, 230.0, 6, 0)));
    static final FrameType SQUASHED = frame(FrameDefinition.builder(id("squashed_5")).mass(110.0).shape(FrameShape.SQUASHED_X).wheelbase(225)
            .maxProp(130).motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30)
            .arms(List.of(MotorArm.of(-92.0, 0.0, -65.0, MotorArm.Spin.CW), MotorArm.of(92.0, 0.0, -65.0, MotorArm.Spin.CCW),
                    MotorArm.of(-92.0, 0.0, 65.0, MotorArm.Spin.CCW), MotorArm.of(92.0, 0.0, 65.0, MotorArm.Spin.CW)))
            .aero(0.012, 0.9).batteryFit(new BatteryFitRule(4, 6, 230.0, 6, 0)));
    static final FrameType H_FRAME = frame(FrameDefinition.builder(id("h_frame_5")).mass(130.0).shape(FrameShape.H_FRAME).wheelbase(240)
            .maxProp(130).motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30)
            .arms(List.of(MotorArm.of(-75.0, 0.0, -95.0, MotorArm.Spin.CW), MotorArm.of(75.0, 0.0, -95.0, MotorArm.Spin.CCW),
                    MotorArm.of(-75.0, 0.0, 95.0, MotorArm.Spin.CCW), MotorArm.of(75.0, 0.0, 95.0, MotorArm.Spin.CW)))
            .aero(0.014, 1.0).batteryFit(new BatteryFitRule(4, 6, 230.0, 6, 0)));
    static final FrameType DUCTED = frame(FrameDefinition.builder(id("cinelifter_5")).mass(310.0).role(FrameRole.CINELIFTER).wheelbase(260)
            .maxProp(130).motorMount(Mounts.MOTOR_M22).stackMount(Mounts.STACK_S30).accessoryMount(AccessorySlot.TOP)
            .duct(DuctSpec.STANDARD).aero(0.03, 1.1).batteryFit(new BatteryFitRule(4, 6, 400.0, 6, 0)));
    static final FrameType X_CLASS = frame(FrameDefinition.builder(id("xclass_13")).mass(1450.0).role(FrameRole.X_CLASS).wheelbase(820)
            .props(250, 330).motorMount(MOUNT_M42).stackMount(Mounts.STACK_S30).accessoryMount(AccessorySlot.TOP, AccessorySlot.BOTTOM)
            .cameraTilt(15.0).aero(0.08, 1.0).impactTolerance(6.0).batteryFit(new BatteryFitRule(6, 8, 2500.0, 8, 4000)));
    static final MotorType M2806 = (MotorType) PartIndex.detached(MotorDefinition.builder(id("m2806_1300")).mass(52.0).stator("2806.5")
            .kv(1300).mount(Mounts.MOTOR_M28).maxCurrent(48.0).resistance(0.062).cells(6, 8).build());
    static final MotorType M4214 = (MotorType) PartIndex.detached(MotorDefinition.builder(id("m4214_400")).mass(128.0).stator("4214").kv(400)
            .mount(MOUNT_M42).maxCurrent(60.0).resistance(0.045).cells(6, 12)
            .basics(PartBasics.of(128.0).withVisual(PartVisual.accent(0x3A7BD5))).build());
    static final PropType P13 = (PropType) PartIndex.detached(PropDefinition.builder(id("p13_2b_carbon")).mass(22.0).size(330, 7.0, 2)
            .coefficients(0.09, 0.04).material(PropMaterial.CARBON).build());
    static final BatteryType B8S = (BatteryType) PartIndex.detached(BatteryDefinition.builder(id("b8s_4500_lion")).mass(560.0).cells(8)
            .capacity(4500).cellResistance(0.014).cRating(12).chemistry(BatteryDefinition.LION).packSize(new PackSize(140.0, 45.0, 70.0))
            .connector("XT60").basics(PartBasics.of(560.0).withVisual(PartVisual.accent(0x1A8F8B))).build());
    static final StackType STACK_8S = (StackType) PartIndex.detached(StackDefinition.builder(id("stack_30_8s")).mass(22.0)
            .mount(Mounts.STACK_S30).escContinuous(65.0).cells(3, 8).build());
    static final StackType AIO_25 = (StackType) PartIndex.detached(StackDefinition.builder(id("aio_25")).mass(6.0)
            .mount(Mounts.STACK_S20).escContinuous(20.0).cells(2, 4).allInOne(true).boardSize(25.0).build());
    static final VideoType HD_PRO = (VideoType) PartIndex.detached(VideoDefinition.builder(id("hd_pro")).mass(28.0).link(VideoLink.DIGITAL)
            .latency(22).range(700).imageQuality(1.0).cameraWidth(19.0).cameraFov(136.0).vtxPower(1000).build());

    static final DroneBuild X_CLASS_BUILD = new DroneBuild(X_CLASS, STACK_8S, M4214, P13, HD_PRO, B8S);
    static final DroneBuild DEADCAT_BUILD = new DroneBuild(DEADCAT, StackType.STACK_30, MotorType.M2207_1750, PropType.P51_3B,
            VideoType.DIGITAL_HD, BatteryType.B6S_1300);

    static final List<FrameType> FRAMES = List.of(DEADCAT, CONTRACT_DEADCAT, STRETCHED, SQUASHED, H_FRAME, DUCTED, X_CLASS);

    private RenderTestParts() {
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }

    private static FrameType frame(FrameDefinition.Builder builder) {
        return (FrameType) PartIndex.detached(builder.build());
    }
}
