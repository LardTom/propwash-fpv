package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.drone.client.EyeClearance;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryLensNearTest {
    private static final float TAN_Y = FpvPropGeometry.tanHalfFromDegrees(101.0F);
    private static final float TAN_X = TAN_Y * 16.0F / 9.0F;
    private static final float NEAR_MM = (float) (EyeClearance.VANILLA_NEAR * DroneVisualScale.MILLIMETRES_PER_BLOCK);

    @Test
    void pointStraightAheadCountsWithItsDepth() {
        assertEquals(20.0F, AccessoryDisplayState.visibleDepthMm(0.0F, 0.0F, -20.0F, TAN_X, TAN_Y, 0.0F), 1.0E-5F);
    }

    @Test
    void pointsBehindTheLensOrOnItDoNotCount() {
        assertEquals(Float.POSITIVE_INFINITY, AccessoryDisplayState.visibleDepthMm(0.0F, 0.0F, 30.0F, TAN_X, TAN_Y, 10.0F));
        assertEquals(Float.POSITIVE_INFINITY, AccessoryDisplayState.visibleDepthMm(0.0F, 0.0F, -0.5F, TAN_X, TAN_Y, 10.0F));
    }

    @Test
    void pointOutsideTheImageCountsOnlyWithinOneTexel() {
        float depth = 10.0F;
        float edge = depth * TAN_Y;
        assertEquals(Float.POSITIVE_INFINITY, AccessoryDisplayState.visibleDepthMm(0.0F, -(edge + 8.0F), -depth, TAN_X, TAN_Y, 6.0F));
        assertEquals(depth, AccessoryDisplayState.visibleDepthMm(0.0F, -(edge + 4.0F), -depth, TAN_X, TAN_Y, 6.0F), 1.0E-5F);
        assertEquals(Float.POSITIVE_INFINITY, AccessoryDisplayState.visibleDepthMm(depth * TAN_X + 8.0F, 0.0F, -depth, TAN_X, TAN_Y, 6.0F));
    }

    @Test
    void nothingVisibleOrFarEnoughKeepsScaleOne() {
        assertEquals(1.0F, AccessoryDisplayState.nearScale(Float.POSITIVE_INFINITY));
        assertEquals(1.0F, AccessoryDisplayState.nearScale(FpvPropGeometry.NEAR_TARGET_MM));
        assertEquals(1.0F, AccessoryDisplayState.nearScale(300.0F));
    }

    @Test
    void scaleIsCappedLikeTheProps() {
        assertEquals(FpvPropGeometry.MAX_NEAR_SCALE, AccessoryDisplayState.nearScale(1.0F));
        assertEquals(2.0F, AccessoryDisplayState.nearScale(FpvPropGeometry.NEAR_TARGET_MM / 2.0F), 1.0E-5F);
    }

    @Test
    void spearShaftAtTheBottomOfTheImageLandsBehindTheNearPlane() {
        float[][] shaft = {{0.0F, -18.0F, -15.0F}, {0.0F, -16.0F, -25.0F}, {0.0F, -12.0F, -45.0F}, {0.0F, -8.0F, -88.0F}};
        float nearest = Float.POSITIVE_INFINITY;
        for (float[] p : shaft) {
            nearest = Math.min(nearest, AccessoryDisplayState.visibleDepthMm(p[0], p[1], p[2], TAN_X, TAN_Y, 6.0F));
        }
        assertEquals(15.0F, nearest, 1.0E-5F);
        float scale = AccessoryDisplayState.nearScale(nearest);
        for (float[] p : shaft) {
            assertTrue(-p[2] * scale > NEAR_MM, "Punkt " + -p[2] + " mm vor der Linse liegt nach dem Ausgleich bei " + -p[2] * scale + " mm");
            assertEquals(p[1] / p[2], p[1] * scale / (p[2] * scale), 1.0E-6F);
        }
    }

    @Test
    void pushedItemKeepsTheNormalsAndTheImageOfTheUnpushedItem() {
        float near = 11.1F;
        Quaternionf body = new Quaternionf().rotationXYZ(0.3F, -1.1F, 0.2F);
        Matrix4f[] items = {new Matrix4f().translate(0.1F, -0.2F, 0.3F).rotateY(0.7F).rotateZ(0.8F).scale(1.25F),
                new Matrix4f().translate(0.1F, -0.2F, 0.3F).rotateY(0.7F).rotateZ(0.8F)};
        for (Matrix4f item : items) {
            PoseStack.Pose unpushed = lens(body, 1.0F);
            PoseStack.Pose pushed = lens(body, near);
            PoseStack.Pose expected = new PoseStack.Pose();
            expected.set(unpushed);
            expected.mulPose(item);
            pushed.mulPose(item);
            AccessoryDisplayState.keepNormals(pushed, unpushed, item, new PoseStack.Pose());
            assertTrue(expected.normal().equals(pushed.normal(), 1.0E-5F), "Normalen wie ohne Ausgleich: " + pushed.normal() + " statt " + expected.normal());
            Vector3f point = new Vector3f(0.3F, -0.4F, 0.5F);
            Vector3f far = pushed.pose().transformPosition(point, new Vector3f());
            Vector3f close = expected.pose().transformPosition(point, new Vector3f());
            assertEquals(near * close.x, far.x, 1.0E-4F);
            assertEquals(near * close.y, far.y, 1.0E-4F);
            assertEquals(near * close.z, far.z, 1.0E-4F);
        }
    }

    private static PoseStack.Pose lens(Quaternionf body, float near) {
        PoseStack.Pose pose = new PoseStack.Pose();
        pose.scale(near, near, near);
        pose.rotate(body);
        pose.scale(0.128F, 0.128F, 0.128F);
        pose.translate(0.0F, -0.13F, 0.51F);
        return pose;
    }
}
