package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateTilesTest {
    private static final Set<GateRenderer.Piece> POST = EnumSet.of(GateRenderer.Piece.CORNER_LEFT, GateRenderer.Piece.CORNER_RIGHT,
            GateRenderer.Piece.TEE_LEFT, GateRenderer.Piece.TEE_RIGHT, GateRenderer.Piece.POST_LEFT, GateRenderer.Piece.POST_LEFT_HALF,
            GateRenderer.Piece.POST_RIGHT, GateRenderer.Piece.POST_RIGHT_HALF, GateRenderer.Piece.FOOT_LEFT, GateRenderer.Piece.FOOT_RIGHT,
            GateRenderer.Piece.LED_POST_LEFT, GateRenderer.Piece.LED_POST_LEFT_HALF, GateRenderer.Piece.LED_POST_RIGHT,
            GateRenderer.Piece.LED_POST_RIGHT_HALF);
    private static final Set<GateRenderer.Piece> POST_LIGHTS = EnumSet.of(GateRenderer.Piece.LED_POST_LEFT, GateRenderer.Piece.LED_POST_LEFT_HALF,
            GateRenderer.Piece.LED_POST_RIGHT, GateRenderer.Piece.LED_POST_RIGHT_HALF);
    private static final Set<GateRenderer.Piece> TOP_BAR = EnumSet.of(GateRenderer.Piece.BAR_TOP, GateRenderer.Piece.BAR_TOP_HALF,
            GateRenderer.Piece.LED_TOP, GateRenderer.Piece.LED_TOP_HALF);

    private record Tile(GateRenderer.Piece piece, float x, float y, boolean glow) {
    }

    private static List<Tile> tiles(float width, float height, float frame, GateStacking.Base base, int joints, boolean banner) {
        List<Tile> out = new ArrayList<>();
        GateTiles.layout(width, height, frame, base, joints, banner, (piece, x, y, glow) -> out.add(new Tile(piece, x, y, glow)));
        return out;
    }

    private static List<Tile> tiles(GateGeometry gate) {
        return tiles((float) gate.width(), (float) gate.height(), (float) gate.frame(), GateStacking.Base.BAR, gate.joints(), false);
    }

    private static GateGeometry gate(double x, double z, float heading) {
        return GatePlacement.turnedTo(GateGeometry.of(x, 64.0, z, 0.0F, GateSize.MEDIUM), heading);
    }

    private static List<GateGeometry> joined(List<GateGeometry> gates) {
        List<GateGeometry> out = new ArrayList<>();
        for (GateGeometry gate : gates) {
            List<GateGeometry> others = new ArrayList<>(gates);
            others.remove(gate);
            out.add(gate.withJoints(GateStacking.joints(gate, others).mask()));
        }
        return out;
    }

    private static String postKey(GateGeometry gate, float x) {
        double side = Math.signum(x) * gate.postOffset();
        double px = gate.x() + gate.sideX() * side;
        double pz = gate.z() + gate.sideZ() * side;
        return Math.round(px * 1000.0) + "/" + Math.round(pz * 1000.0);
    }

    private static Map<String, Set<Integer>> postsDrawn(List<GateGeometry> gates, Set<GateRenderer.Piece> pieces) {
        Map<String, Set<Integer>> drawn = new HashMap<>();
        for (int i = 0; i < gates.size(); i++) {
            GateGeometry gate = gates.get(i);
            for (Tile tile : tiles(gate)) {
                if (pieces.contains(tile.piece())) {
                    drawn.computeIfAbsent(postKey(gate, tile.x()), key -> new HashSet<>()).add(i);
                }
            }
        }
        return drawn;
    }

    private static List<GateGeometry> row(float heading) {
        GateGeometry first = gate(0.5, 0.5, heading);
        double step = 2.0 * first.postOffset();
        List<GateGeometry> row = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            row.add(gate(0.5 + first.sideX() * step * i, 0.5 + first.sideZ() * step * i, heading));
        }
        return joined(row);
    }

    private static List<GateGeometry> ring(float heading) {
        GateGeometry south = gate(0.5, 0.5, heading);
        double p = south.postOffset();
        double sx = south.sideX();
        double sz = south.sideZ();
        double nx = south.normalX();
        double nz = south.normalZ();
        GateGeometry east = gate(0.5 + sx * p + nx * p, 0.5 + sz * p + nz * p, heading + 90.0F);
        GateGeometry west = gate(0.5 - sx * p + nx * p, 0.5 - sz * p + nz * p, heading + 90.0F);
        GateGeometry north = gate(0.5 + nx * 2.0 * p, 0.5 + nz * 2.0 * p, heading);
        return List.of(south, east, west, north);
    }

    @Test
    void sidesWithAJointDrawNoPieceAtAll() {
        for (GateSize size : GateSize.values()) {
            for (GateStacking.Base base : GateStacking.Base.values()) {
                for (int joints = 0; joints <= GateGeometry.JOINT_MASK; joints++) {
                    for (boolean banner : new boolean[]{false, true}) {
                        float half = size.width() * 0.5F;
                        for (Tile tile : tiles(size.width(), size.height(), size.frame(), base, joints, banner)) {
                            if (tile.piece().banner()) {
                                continue;
                            }
                            String where = size + " " + base + " Fugen " + joints + ": " + tile;
                            if ((joints & GateGeometry.JOINT_LEFT) != 0) {
                                assertFalse(Math.abs(tile.x() + half) < 1.0E-4F, "links geteilt, trotzdem " + where);
                            }
                            if ((joints & GateGeometry.JOINT_RIGHT) != 0) {
                                assertFalse(Math.abs(tile.x() - half) < 1.0E-4F, "rechts geteilt, trotzdem " + where);
                            }
                            if ((joints & GateGeometry.JOINT_TOP) != 0) {
                                assertFalse(TOP_BAR.contains(tile.piece()), "oben unter dem Deckel, trotzdem " + where);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void withoutNeighboursEveryPieceStays() {
        List<Tile> plain = tiles(4.0F, 4.0F, 0.5F, GateStacking.Base.BAR, 0, true);
        assertEquals(2 + 2 + 3 * 4 + 3 * 5 + 2 + 2 + 3, plain.size());
        assertEquals(3 * 2 + 3 * 3, plain.stream().filter(Tile::glow).count());
        assertTrue(plain.stream().noneMatch(tile -> tile.piece().banner() && tile.glow()));
    }

    @Test
    void sharedPostsInARowAreDrawnByExactlyOneGate() {
        for (float heading : new float[]{0.0F, 90.0F, 30.0F, 45.0F, 200.0F}) {
            List<GateGeometry> row = row(heading);
            int dropped = 0;
            for (GateGeometry gate : row) {
                dropped += Integer.bitCount(gate.joints());
            }
            assertEquals(2, dropped, "zwei geteilte Pfosten bei " + heading + "°: " + row);
            Map<String, Set<Integer>> drawn = postsDrawn(row, POST);
            assertEquals(4, drawn.size(), "vier Pfosten bei " + heading + "°: " + drawn);
            for (Map.Entry<String, Set<Integer>> post : drawn.entrySet()) {
                assertEquals(1, post.getValue().size(), "Pfosten " + post.getKey() + " doppelt gezeichnet bei " + heading + "°: " + post.getValue());
            }
            Map<String, Set<Integer>> lights = postsDrawn(row, POST_LIGHTS);
            assertEquals(4, lights.size(), "jeder Pfosten behält seine Lichter bei " + heading + "°");
        }
    }

    @Test
    void cubeCornersAndTopBarsAreDrawnOnce() {
        for (float heading : new float[]{0.0F, 30.0F, 45.0F}) {
            List<GateGeometry> ring = ring(heading);
            GateGeometry south = ring.getFirst();
            GateStacking.Lid lid = GateStacking.lid(south, ring.subList(1, 4), GateSize.MEDIUM);
            assertEquals(GateStacking.LidRefusal.NONE, lid.refusal(), "Deckel bei " + heading + "°");
            List<GateGeometry> all = new ArrayList<>(ring);
            all.add(lid.geometry());
            List<GateGeometry> sides = joined(all).subList(0, 4);
            Map<String, Set<Integer>> drawn = postsDrawn(sides, POST);
            assertEquals(4, drawn.size(), "vier Eckpfosten bei " + heading + "°: " + drawn);
            for (Map.Entry<String, Set<Integer>> post : drawn.entrySet()) {
                assertEquals(1, post.getValue().size(), "Eckpfosten " + post.getKey() + " doppelt gezeichnet bei " + heading + "°");
            }
            for (GateGeometry side : sides) {
                assertTrue((side.joints() & GateGeometry.JOINT_TOP) != 0, "Seite unter dem Deckel bei " + heading + "°");
                assertTrue(tiles(side).stream().noneMatch(tile -> TOP_BAR.contains(tile.piece())),
                        "oberer Balken samt Lichtern gehört dem Deckel bei " + heading + "°");
            }
        }
    }
}
