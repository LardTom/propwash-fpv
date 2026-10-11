package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.DuctSpec;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartWear;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameGeometryTest {
    private static DroneBuild with(FrameType frame, DroneBuild base) {
        return new DroneBuild(frame, base.stack(), base.motor(), base.prop(), base.video(), base.battery());
    }

    private static void close(double expected, double actual, String what) {
        double tolerance = 1e-9 * Math.max(1.0, Math.abs(expected));
        assertEquals(expected, actual, tolerance, what);
    }

    @Test
    void explicitTrueXArmsMatchTheLegacyModel() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            AirframeSpec legacy = AirframeSpec.of(preset);
            AirframeSpec explicit = AirframeSpec.of(with(AddonParts.trueXExplicit(preset.frame()), preset));
            String name = preset.frame().key();
            assertTrue(explicit.explicitArms, name);
            assertFalse(legacy.explicitArms, name);
            close(legacy.massKg, explicit.massKg, name + " Masse");
            close(legacy.comHeight, explicit.comHeight, name + " Schwerpunkt");
            close(legacy.inertiaXx, explicit.inertiaXx, name + " Ixx");
            close(legacy.inertiaYy, explicit.inertiaYy, name + " Iyy");
            close(legacy.inertiaZz, explicit.inertiaZz, name + " Izz");
            close(legacy.motorOffset, explicit.motorOffset, name + " Motorabstand");
            close(legacy.armLength, explicit.armLength, name + " Armlänge");
            close(legacy.motorSpacing, explicit.motorSpacing, name + " Motorabstand benachbart");
            close(legacy.rotationalDamping, explicit.rotationalDamping, name + " Rotationsdämpfung");
            close(legacy.authorityRoll, explicit.authorityRoll, name + " Roll");
            close(legacy.authorityPitch, explicit.authorityPitch, name + " Nick");
            close(legacy.authorityYawSteady, explicit.authorityYawSteady, name + " Gieren");
            close(legacy.hitboxWidth, explicit.hitboxWidth, name + " Hitbox");
            close(legacy.hitboxHeight, explicit.hitboxHeight, name + " Hitbox-Höhe");
            close(legacy.hitboxFootOffset, explicit.hitboxFootOffset, name + " Fußpunkt");
            AirframeSpec camera = AirframeSpec.of(with(AddonParts.trueXExplicit(preset.frame(), true), preset));
            close(legacy.lensRight, camera.lensRight, name + " Objektiv seitlich");
            close(legacy.lensForward, camera.lensForward, name + " Objektiv");
            close(legacy.lensUp, camera.lensUp, name + " Objektiv oben");
            assertTrue(explicit.uniformThrottle, name);
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                close(legacy.motorX(i), explicit.motorX(i), name + " x" + i);
                close(legacy.motorY(i), explicit.motorY(i), name + " y" + i);
                close(legacy.motorZ(i), explicit.motorZ(i), name + " z" + i);
                assertEquals(legacy.spin(i), explicit.spin(i), name + " Drehrichtung " + i);
                close(legacy.mixRoll(i), explicit.mixRoll(i), name + " Mixer Roll " + i);
                close(legacy.mixPitch(i), explicit.mixPitch(i), name + " Mixer Nick " + i);
                assertEquals(1.0, explicit.mixThrottle(i));
            }
            for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
                close(legacy.hullX(k), explicit.hullX(k), name + " Hülle x" + k);
                close(legacy.hullY(k), explicit.hullY(k), name + " Hülle y" + k);
                close(legacy.hullZ(k), explicit.hullZ(k), name + " Hülle z" + k);
            }
        }
    }

    @Test
    void armsMapToBetaflightMotorOrderAndSpin() {
        FrameType frame = AddonParts.stretched(70.0, 95.0);
        AirframeSpec p = AirframeSpec.of(with(frame, DroneBuild.FREESTYLE));
        List<MotorArm> arms = frame.definition().geometry().arms();
        for (int position = 0; position < MotorArm.COUNT; position++) {
            int motor = PartWear.simMotor(position);
            MotorArm arm = arms.get(position);
            assertEquals(-arm.position().x() / 1000.0, p.motorX(motor), 1e-12);
            assertEquals(-arm.position().z() / 1000.0, p.motorZ(motor), 1e-12);
            assertEquals(arm.spin() == MotorArm.Spin.CW ? -1 : 1, p.spin(motor));
        }
        assertTrue(p.motorZ(1) > 0.0 && p.motorX(1) < 0.0, "Motor 1 = vorne rechts");
        assertTrue(p.motorZ(0) < 0.0 && p.motorX(0) < 0.0, "Motor 0 = hinten rechts");
    }

    @Test
    void stretchedSquashedAndHFramesMixByTheirLeverArms() {
        AirframeSpec stretched = AirframeSpec.of(with(AddonParts.stretched(70.0, 95.0), DroneBuild.FREESTYLE));
        AirframeSpec squashed = AirframeSpec.of(with(AddonParts.stretched(95.0, 70.0), DroneBuild.FREESTYLE));
        AirframeSpec hFrame = AirframeSpec.of(with(AddonParts.part(AddonParts.fiveInch("h_frame").shape(FrameShape.H_FRAME)
                .arms(AddonParts.arms(110.0, 60.0, 110.0, 60.0)).build()), DroneBuild.FREESTYLE));
        for (AirframeSpec p : List.of(stretched, squashed, hFrame)) {
            assertTrue(p.uniformThrottle);
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                assertEquals(1.0, Math.abs(p.mixRoll(i)), 1e-12);
                assertEquals(1.0, Math.abs(p.mixPitch(i)), 1e-12);
                assertEquals(Math.signum(p.motorX(i)), Math.signum(p.mixRoll(i)));
                assertEquals(-Math.signum(p.motorZ(i)), Math.signum(p.mixPitch(i)));
            }
        }
        assertTrue(stretched.inertiaXx > stretched.inertiaZz, "gestreckt: Nickträgheit größer");
        assertTrue(squashed.inertiaZz > squashed.inertiaXx, "gestaucht: Rollträgheit größer");
        assertTrue(hFrame.inertiaZz > hFrame.inertiaXx);
        double stretchedRatio = stretched.authorityPitch / stretched.authorityRoll;
        double squashedRatio = squashed.authorityPitch / squashed.authorityRoll;
        assertTrue(stretchedRatio < 1.0 && squashedRatio > 1.0, stretchedRatio + " " + squashedRatio);
        assertEquals(stretched.motorX(1) * stretched.motorX(1) * 4.0 / Math.abs(stretched.motorX(1)),
                4.0 * Math.abs(stretched.motorX(1)), 1e-12);
    }

    @Test
    void deadcatThrottleWeightsBalanceTheCenterOfMass() {
        AirframeSpec p = AirframeSpec.of(with(AddonParts.deadcat(), DroneBuild.FREESTYLE));
        assertFalse(p.uniformThrottle);
        double mx = 0.0;
        double mz = 0.0;
        double max = 0.0;
        for (int i = 0; i < AirframeSpec.MOTORS; i++) {
            double w = p.mixThrottle(i);
            assertTrue(w > 0.2 && w <= 1.0, "Gewicht " + w);
            max = Math.max(max, w);
            mx += w * w * p.motorX(i);
            mz += w * w * p.motorZ(i);
        }
        assertEquals(1.0, max, 1e-12);
        assertEquals(0.0, mx, 1e-9);
        assertEquals(0.0, mz, 1e-9);
        assertTrue(p.comZ < 0.0, "Schwerpunkt hinter der Rahmenmitte: " + p.comZ);
        assertTrue(p.mixThrottle(1) > p.mixThrottle(0), "vorne mehr Gas");
    }

    @Test
    void frameCenterOfMassMovesTheWholeDrone() {
        AirframeSpec centered = AirframeSpec.of(with(AddonParts.part(AddonParts.fiveInch("centered")
                .arms(MotorArm.trueX(225, 0.0)).build()), DroneBuild.FREESTYLE));
        AirframeSpec shifted = AirframeSpec.of(with(AddonParts.part(AddonParts.fiveInch("shifted")
                .arms(MotorArm.trueX(225, 0.0)).centerOfMass(new Vec3Mm(10.0, 5.0, -20.0)).build()), DroneBuild.FREESTYLE));
        double frameShare = 0.118 / shifted.massKg;
        assertEquals(-0.010 * frameShare, shifted.comX - centered.comX, 1e-9);
        assertEquals(0.005 * frameShare, shifted.comHeight - centered.comHeight, 1e-9);
        assertEquals(0.020 * frameShare, shifted.comZ - centered.comZ, 1e-9);
        assertEquals(centered.motorZ(1) - (shifted.comZ - centered.comZ), shifted.motorZ(1), 1e-9);
    }

    @Test
    void cameraPositionSetsTheLensRelativeToTheCenterOfMass() {
        AirframeSpec p = AirframeSpec.of(with(AddonParts.deadcat(), DroneBuild.FREESTYLE));
        assertEquals(0.0 + p.comX, p.lensRight, 1e-12);
        assertEquals(0.014 - p.comHeight, p.lensUp, 1e-12);
        assertEquals(0.100 - p.comZ, p.lensForward, 1e-12);
        assertEquals(30.0, p.cameraTiltDeg);
    }

    @Test
    void hullAndHitboxEncloseEveryPropTip() {
        for (DroneBuild build : List.of(with(AddonParts.deadcat(), DroneBuild.FREESTYLE), AddonParts.xClassBuild(),
                with(AddonParts.stretched(60.0, 110.0), DroneBuild.FREESTYLE))) {
            AirframeSpec p = AirframeSpec.of(build);
            double half = p.hitboxWidth / 2.0;
            for (int i = 0; i < AirframeSpec.MOTORS; i++) {
                double reach = Math.max(Math.abs(p.motorX(i)), Math.abs(p.motorZ(i))) + p.propRadius;
                assertTrue(reach <= half + 1e-12, p.buildKey + " Motor " + i);
                assertTrue(Math.abs(p.hullX(i)) <= half + 1e-12 && Math.abs(p.hullZ(i)) <= half + 1e-12);
            }
            for (int k = 0; k < AirframeSpec.HULL_POINTS; k++) {
                assertTrue(Double.isFinite(p.hullX(k) + p.hullY(k) + p.hullZ(k)));
                assertTrue(p.hullY(k) >= p.hitboxFootOffset - 1e-12);
            }
            assertTrue(p.hitboxHeight > 0.0);
        }
    }

    @Test
    void ductOverridesReplaceTheDuctConstants() {
        DroneBuild base = DroneBuild.CINEWHOOP;
        AirframeSpec standard = AirframeSpec.of(with(AddonParts.ductedWith(DuctSpec.STANDARD), base));
        AirframeSpec custom = AirframeSpec.of(with(AddonParts.ductedWith(new DuctSpec(OptionalDouble.of(1.25), OptionalDouble.of(6.0),
                OptionalDouble.of(0.04), OptionalDouble.of(0.1), OptionalDouble.of(0.05), OptionalDouble.of(0.5), OptionalDouble.of(0.2))), base));
        assertEquals(ModelConstants.DUCT_GAIN, standard.ductGain);
        assertEquals(1.25, custom.ductGain);
        assertEquals(0.006, custom.ductClearance, 1e-12);
        assertEquals(0.04, custom.translationalLiftGain);
        assertEquals(0.1, custom.washLoss);
        assertEquals(0.05, custom.washNoise);
        assertEquals(0.5, custom.restitution);
        assertEquals(0.2, custom.friction);
        assertTrue(custom.hoverOmega < standard.hoverOmega, "mehr Duct-Schub, weniger Drehzahl");
        assertEquals(standard.hitboxWidth + 0.006, custom.hitboxWidth, 1e-12);
    }

    @Test
    void stiffPropsCutWashNoiseAndFlexLoss() {
        AirframeSpec pc = AirframeSpec.of(AddonParts.xClassBuild());
        assertTrue(pc.propStiffness > 1.0);
        assertTrue(pc.propFlex < 0.0);
        assertEquals(ModelConstants.WASH_NOISE_OPEN / Math.sqrt(pc.propStiffness), pc.washNoise, 1e-12);
        assertEquals(1.0, RotorAero.flexFactor(pc, pc.hoverOmega), 1e-12);
        assertTrue(RotorAero.flexFactor(pc, 2.0 * pc.hoverOmega) > 1.0);
        AirframeSpec builtin = AirframeSpec.of(DroneBuild.FREESTYLE);
        assertEquals(0.0, builtin.propFlex);
        assertEquals(1.0, RotorAero.flexFactor(builtin, 3.0 * builtin.hoverOmega));
    }
}
