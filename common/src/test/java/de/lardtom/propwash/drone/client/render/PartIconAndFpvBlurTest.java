package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PropType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartIconAndFpvBlurTest {
    private static final float MM = DroneVisualScale.MM_TO_UNITS;
    private static final int ALPHA_CUTOUT = 26;

    @Test
    void worldFramesStayUnchangedAndIconsThickenLargeFrames() {
        for (FrameType frame : FrameType.values()) {
            DroneLayout layout = DroneLayout.fromDefinition(frame);
            Shape world = FallbackShapes.buildFrame(frame, layout);
            Shape plain = FallbackShapes.buildFrame(frame, layout, false);
            assertEquals(world.vertexCount(), plain.vertexCount(), frame.key());
            assertEquals(world.min(), plain.min(), frame.key());
            assertEquals(world.max(), plain.max(), frame.key());
        }
        FrameType frame = RenderTestParts.X_CLASS;
        DroneLayout layout = DroneLayout.fromDefinition(frame);
        Shape world = FallbackShapes.buildFrame(frame, layout);
        Shape icon = FallbackShapes.buildFrame(frame, layout, true);
        assertEquals(world.vertexCount(), icon.vertexCount());
        float worldHeight = world.max().y() - world.min().y();
        float iconHeight = icon.max().y() - icon.min().y();
        assertTrue(iconHeight >= worldHeight, "Symbol flacher als der Rahmen: " + iconHeight + " < " + worldHeight);
        assertTrue(FallbackShapes.ICON_ARM_RATIO * frame.wheelbaseMm > FallbackShapes.armWidth(frame), "X-Class-Arme im Symbol nicht breiter");
    }

    @Test
    void propIconBladesAreWiderThanTheWorldProp() {
        PropType prop = RenderTestParts.P13;
        Shape world = FallbackShapes.buildProp(prop, false);
        Shape icon = FallbackShapes.buildProp(prop, false, FallbackShapes.ICON_CHORD_SCALE);
        float worldChord = world.max().z() - world.min().z();
        float iconChord = icon.max().z() - icon.min().z();
        assertTrue(iconChord > worldChord, "Blatt im Symbol nicht breiter: " + iconChord + " <= " + worldChord);
        assertEquals(world.max().x(), icon.max().x(), 1.0E-4F);
    }

    @Test
    void turnedIconsKeepFittingTheGuiView() {
        assertEquals(PartItemRenderer.TURNED_YAW_DEGREES, PartItemRenderer.yawOf(RenderTestParts.X_CLASS), 0.0F);
        assertEquals(0.0F, PartItemRenderer.yawOf(RenderTestParts.P13), 0.0F);
        Vector3f min = new Vector3f(-1.0F, -0.01F, -1.0F);
        Vector3f max = new Vector3f(1.0F, 0.01F, 1.0F);
        float turned = PartItemRenderer.guiExtent(min, max, PartItemRenderer.TURNED_YAW_DEGREES);
        assertTrue(turned > 2.0F && turned <= 2.0F * (float) Math.sqrt(2.0) + 0.05F, "Ausdehnung " + turned);
    }

    @Test
    void fpvBlurIsSoftAndStaysAboveTheAlphaCutout() {
        for (PropType prop : new PropType[]{PropType.P51_3B, RenderTestParts.P13}) {
            Shape blur = FpvProps.buildBlur(prop, 0xA4E22B);
            float radius = prop.diameterMm * 0.5F;
            assertEquals(radius, blur.max().x() / MM, 0.5F, prop.key());
            Vector3f position = new Vector3f();
            int innerAlpha = -1;
            int rimAlpha = -1;
            for (int v = 0; v < blur.vertexCount(); v++) {
                int alpha = blur.color(v) >>> 24;
                assertTrue(alpha >= ALPHA_CUTOUT, prop.key() + ": Alpha " + alpha + " unter der Cutout-Schwelle");
                float r = blur.position(v, position).length() / MM;
                if (r < radius * 0.2F) {
                    innerAlpha = Math.max(innerAlpha, alpha);
                }
                if (r > radius * 0.99F) {
                    rimAlpha = Math.max(rimAlpha, alpha);
                }
            }
            assertTrue(innerAlpha > rimAlpha && rimAlpha > 0, prop.key() + ": kein weicher Rand " + innerAlpha + " → " + rimAlpha);
        }
    }
}
