package de.lardtom.propwash.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrientedBoxTest {
    private static final double S = Math.sqrt(0.5);

    static OrientedBox turned(double cx, double cy, double cz, double yawRadians, double hu, double hv, double hw) {
        double c = Math.cos(yawRadians);
        double s = Math.sin(yawRadians);
        return new OrientedBox(cx, cy, cz, c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c, hu, hv, hw);
    }

    @Test
    void boundsOfDiagonalBoxCoverAllCorners() {
        OrientedBox box = turned(0.0, 0.0, 0.0, Math.PI / 4.0, 2.0, 1.0, 0.25);
        assertEquals((2.0 + 0.25) * S, box.extentX(), 1.0E-12);
        assertEquals(1.0, box.extentY(), 1.0E-12);
        assertEquals((2.0 + 0.25) * S, box.extentZ(), 1.0E-12);
        assertFalse(box.aligned());
        assertTrue(OrientedBox.ofBounds(0, 0, 0, 1, 2, 3).aligned());
    }

    @Test
    void separatingAxisFindsTheGapOfADiagonalBar() {
        OrientedBox bar = turned(0.0, 0.0, 0.0, Math.PI / 4.0, 3.0, 0.25, 0.25);
        assertTrue(bar.intersects(1.0, -0.1, 1.0, 1.2, 0.1, 1.2));
        assertFalse(bar.intersects(1.0, -0.1, -1.6, 1.4, 0.1, -1.2), "Box liegt in der Hülle, aber neben dem schrägen Balken");
        OrientedBox hull = OrientedBox.ofBounds(bar.minX(), bar.minY(), bar.minZ(), bar.maxX(), bar.maxY(), bar.maxZ());
        assertTrue(hull.intersects(1.0, -0.1, -1.6, 1.4, 0.1, -1.2));
        assertFalse(bar.intersects(0.0, 0.25, 0.0, 1.0, 1.0, 1.0), "berührende Flächen zählen nicht als Überschneidung");
    }

    @Test
    void orientedBoxesCrossedLikeAnXIntersect() {
        OrientedBox a = turned(0.0, 0.0, 0.0, Math.PI / 4.0, 2.0, 0.2, 0.2);
        OrientedBox b = turned(0.0, 0.0, 0.0, -Math.PI / 4.0, 2.0, 0.2, 0.2);
        assertTrue(a.intersects(b));
        assertFalse(a.intersects(b.moved(0.0, 0.5, 0.0)));
        assertFalse(a.intersects(turned(0.0, 0.0, 1.5, Math.PI / 4.0, 2.0, 0.2, 0.2)));
    }

    @Test
    void rayClipEntersDiagonalFaceAtTheRightPoint() {
        OrientedBox wall = turned(0.0, 0.0, 0.0, Math.PI / 4.0, 3.0, 2.0, 0.25);
        double t = wall.clip(-2.0, 0.0, 2.0, 2.0, 0.0, -2.0);
        assertTrue(t > 0.0 && t < 0.5);
        double x = -2.0 + 4.0 * t;
        double z = 2.0 - 4.0 * t;
        double depth = -S * x + S * z;
        assertEquals(0.25, depth, 1.0E-9);
        assertEquals(OrientedBox.NO_HIT, wall.clip(3.0, 0.0, 0.0, 4.0, 0.0, 1.0));
        assertEquals(0.0, wall.clip(0.0, 0.0, 0.0, 1.0, 0.0, 0.0));
    }

    @Test
    void sweepIntoDiagonalWallStopsOnTheFaceAndSlides() {
        OrientedBox wall = turned(0.0, 0.0, 0.0, Math.PI / 4.0, 5.0, 3.0, 0.25);
        double h = 0.2;
        double startX = -2.0;
        double startZ = 0.0;
        OrientedSweep.Result out = new OrientedSweep.Result();
        OrientedSweep.clip(startX - h, -h, startZ - h, startX + h, h, startZ + h, 3.0, 0.0, 0.0, List.of(wall), out);
        assertTrue(out.hit);
        assertEquals(-S, out.nx, 1.0E-9);
        assertEquals(0.0, out.ny, 1.0E-9);
        assertEquals(S, out.nz, 1.0E-9);
        double cx = startX + out.dx;
        double cz = startZ + out.dz;
        double gap = (cx * -S + cz * S) - 0.25 - h * (S + S);
        assertTrue(gap >= 0.0 && gap < 1.0E-3, "Abstand zur Wand " + gap);
        assertTrue(out.dz > 0.1, "rutscht an der Wand entlang statt zu kleben: dz=" + out.dz);
        OrientedBox moved = OrientedBox.ofBounds(cx - h, -h, cz - h, cx + h, h, cz + h);
        assertFalse(moved.intersects(wall));
    }

    @Test
    void fastMotionNeverTunnelsThroughAThinPost() {
        Random random = new Random(7L);
        for (int i = 0; i < 500; i++) {
            double yaw = random.nextDouble() * Math.PI;
            OrientedBox post = turned(0.0, 0.0, 0.0, yaw, 0.25, 2.0, 0.25);
            double angle = random.nextDouble() * Math.PI * 2.0;
            double sx = Math.cos(angle) * 4.0;
            double sz = Math.sin(angle) * 4.0;
            double h = 0.1;
            OrientedSweep.Result out = new OrientedSweep.Result();
            OrientedSweep.clip(sx - h, -h, sz - h, sx + h, h, sz + h, -2.0 * sx, 0.0, -2.0 * sz, List.of(post), out);
            assertTrue(out.hit, "Pfosten bei Lauf " + i + " durchflogen");
            double ex = sx + out.dx;
            double ez = sz + out.dz;
            assertFalse(post.intersects(ex - h, -h, ez - h, ex + h, h, ez + h), "Lauf " + i + " steckt im Pfosten");
        }
    }

    @Test
    void touchingAndMovingAlongTheFaceIsFree() {
        OrientedBox wall = OrientedBox.ofBounds(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        OrientedSweep.Result out = new OrientedSweep.Result();
        OrientedSweep.clip(1.0, 0.2, 0.2, 1.3, 0.5, 0.5, 0.0, 0.0, 2.0, List.of(wall), out);
        assertFalse(out.hit);
        assertEquals(2.0, out.dz, 1.0E-12);
        OrientedSweep.clip(1.0, 0.2, 0.2, 1.3, 0.5, 0.5, -0.5, 0.0, 0.0, List.of(wall), out);
        assertTrue(out.hit);
        assertEquals(0.0, out.dx, 1.0E-4);
    }

    @Test
    void stuckBoxCanLeaveButNotGoDeeper() {
        OrientedBox wall = turned(0.0, 0.0, 0.0, 0.3, 1.0, 1.0, 1.0);
        OrientedSweep.Result out = new OrientedSweep.Result();
        OrientedSweep.clip(0.8, -0.1, -0.1, 1.2, 0.1, 0.1, 1.0, 0.0, 0.0, List.of(wall), out);
        assertEquals(1.0, out.dx, 1.0E-9);
        OrientedSweep.clip(0.8, -0.1, -0.1, 1.2, 0.1, 0.1, -1.0, 0.0, 0.0, List.of(wall), out);
        assertTrue(out.hit);
        double deeper = out.dx * wall.ux() + out.dy * wall.uy() + out.dz * wall.uz();
        assertTrue(deeper > -1.0E-6, "darf nicht tiefer eindringen: " + deeper);
    }

    @Test
    void alignedObbBehavesLikeTheAxisSweep() {
        OrientedBox block = OrientedBox.ofBounds(2.0, 0.0, -1.0, 3.0, 1.0, 1.0);
        OrientedSweep.Result out = new OrientedSweep.Result();
        OrientedSweep.clip(0.0, 0.2, -0.2, 0.4, 0.6, 0.2, 5.0, 0.0, 0.0, List.of(block), out);
        assertTrue(out.hit);
        assertEquals(-1.0, out.nx, 1.0E-12);
        assertEquals(1.6, out.dx, 1.0E-4);
        assertTrue(out.dx <= 1.6);
    }
}
