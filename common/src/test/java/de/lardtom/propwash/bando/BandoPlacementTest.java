package de.lardtom.propwash.bando;

import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoSite;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.LeafDistance;
import de.lardtom.propwash.bando.plan.Mat;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.function.LongUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandoPlacementTest {
    static final long[] SEEDS = {77031L, 31337L, 123456789L, 2718281L, 4242L, 555L, 900001L, 1618033L};
    private static final int FILL_DEPTH = 64;
    private static final int BELOW = FILL_DEPTH + 8;
    private static final int SPAN = 2048;

    private static final long[][] FINGERPRINTS = {
            {4483576905765950185L, 6464503206555586342L, 3973512106156138599L, 906244729654902759L, 2322118738186715909L, 7401808619838367100L,
                    7012545432487904600L, 370482380140711854L},
            {-7424387962022543726L, -7881783415185403786L, 2559865645193681761L, 3011233601602589748L, -1603517468992189822L, -8165930451902824441L,
                    9208559518930274631L, 2522542386650217336L},
            {-5994618698353000184L, -2613192153334466597L, 1242690088574567363L, -7327206773906939213L, -2836959725880116164L, -73712380272995925L,
                    5563221271755336275L, 2030328987808833645L},
            {223984118352486043L, 5563159605774945400L, 3631785555113712867L, 4741063880003029540L, 8972316285628734078L, -2888262781387099264L,
                    -5354689414726947608L, 6203662992133331876L},
            {-3635483683203391188L, 3866693486837288033L, 4674385598597210025L, -2556971813702042819L, -2111413151831953958L, -976171047774439083L,
                    -2230121057547815583L, 6842174993618837152L},
            {1483056612942396754L, -6025363935820763594L, -3707952147213635477L, 6651370987995206980L, 3703734166085199898L, 4329486287421425660L,
                    3372537822243951382L, -2817007518325610348L},
            {-301668866153624503L, -8493307959183918085L, 9010935671750085752L, 1577047399872368645L, 4978559505960066505L, 6391448090930786371L,
                    2360434522749513608L, 1173578916371296801L},
            {-311766589443602743L, 8210828138447836312L, -2854045497923823262L, -2542169388762972186L, -4337850104380289368L, -8686013617299048428L,
                    -6019937142832863496L, 1907662981712386891L},
            {-502483172581795996L, 7226920653048141249L, -6012432914178526202L, 4920946308185558933L, -5673560963686090687L, -2473570471189364619L,
                    -2264036656289501728L, -482360405289373562L},
            {-7384667375528169274L, -8271474933702978076L, 4790585461038441231L, 333202559723127733L, -3524061931305373653L, -865953007177931391L,
                    2368183184831980861L, 3259156433799218622L},
            {-3707791899508421176L, 6540238466454722677L, -4631230297747143344L, -7462393049889269803L, -8246493008846510383L, 2797215242218544535L,
                    -5843260103650037249L, -6151495584895153599L}};

    private enum Cell {
        AIR,
        TERRAIN,
        PLACED
    }

    private record Column(Cell[] cells, int base) {
        Cell at(int y) {
            int i = y - this.base;
            return i < 0 ? Cell.TERRAIN : i >= this.cells.length ? Cell.AIR : this.cells[i];
        }

        void set(int y, Cell cell) {
            this.cells[y - this.base] = cell;
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x + SPAN) * SPAN + (y + SPAN)) * SPAN + (z + SPAN);
    }

    private static final LongUnaryOperator[] SIDES = {
            at -> at + (long) SPAN * SPAN, at -> at - (long) SPAN * SPAN, at -> at + SPAN, at -> at - SPAN, at -> at + 1, at -> at - 1};

    private static Column place(BandoPlan plan, int x, int z, int floor, int terrainTop) {
        int g = plan.ground();
        int origin = floor - g;
        int base = origin - BELOW;
        Cell[] cells = new Cell[BELOW + plan.sizeY() + 1];
        Column column = new Column(cells, base);
        for (int y = base; y < base + cells.length; y++) {
            column.set(y, y <= terrainTop ? Cell.TERRAIN : Cell.AIR);
        }
        boolean[] site = BandoSite.siteColumns(plan);
        if (site[z * plan.sizeX() + x] && BandoSite.underpinned(plan, x, z) && column.at(floor) == Cell.AIR) {
            int y = floor - 1;
            for (int depth = 0; y > base && column.at(y) == Cell.AIR && depth < FILL_DEPTH; depth++, y--) {
                column.set(y, Cell.PLACED);
            }
            column.set(floor, Cell.PLACED);
        }
        for (int y = 0; y < plan.sizeY(); y++) {
            Mat mat = plan.mat(x, y, z);
            if (mat == Mat.EMPTY || mat == Mat.CHEST || mat == Mat.AIR || mat == Mat.WATER || !mat.holdsUp()) {
                continue;
            }
            int wy = origin + y;
            if (mat == Mat.YARD && column.at(wy) == Cell.AIR) {
                continue;
            }
            column.set(wy, Cell.PLACED);
        }
        if (plan.fill(x, z) != Mat.EMPTY) {
            int y = origin + Math.max(BandoSite.bottom(plan, x, z), 0) - 1;
            for (int depth = 0; y > base && column.at(y) == Cell.AIR && depth < FILL_DEPTH; depth++, y--) {
                column.set(y, Cell.PLACED);
            }
        }
        return column;
    }

    private static List<String> unsupported(BandoPlan plan, IntBinaryOperator terrain, int floor) {
        List<String> problems = new ArrayList<>();
        int g = plan.ground();
        int origin = floor - g;
        for (int z = 0; z < plan.sizeZ(); z++) {
            for (int x = 0; x < plan.sizeX(); x++) {
                int top = terrain.applyAsInt(x, z);
                Column column = place(plan, x, z, floor, top);
                Mat ground = plan.mat(x, g, z);
                boolean carries = ground == Mat.YARD || ground.kind() == Mat.Kind.STRUCTURE;
                int bottom = BandoSite.bottom(plan, x, z);
                if (bottom >= 0 && bottom <= g + 1 && plan.mat(x, bottom, z).kind() == Mat.Kind.STRUCTURE) {
                    carries = true;
                }
                if (!carries) {
                    continue;
                }
                if (ground == Mat.YARD && column.at(floor) != Cell.PLACED) {
                    problems.add("Hof fehlt bei " + x + "," + z + " (Gelände " + (top - floor) + ", Fundament " + plan.fill(x, z) + ")");
                    continue;
                }
                int lowest = Integer.MAX_VALUE;
                for (int y = 0; y <= g + 1 && y < plan.sizeY(); y++) {
                    if (column.at(origin + y) == Cell.PLACED && plan.mat(x, y, z) != Mat.EMPTY) {
                        lowest = origin + y;
                        break;
                    }
                }
                if (lowest == Integer.MAX_VALUE) {
                    continue;
                }
                int y = lowest - 1;
                while (column.at(y) == Cell.PLACED) {
                    y--;
                }
                if (column.at(y) != Cell.TERRAIN) {
                    problems.add("Spalte " + x + "," + z + " hängt über " + (lowest - y) + " Blöcken Luft (Gelände " + (top - floor) + ")");
                }
            }
        }
        return problems;
    }

    private static List<BandoPlan> landPlans() {
        List<BandoPlan> plans = new ArrayList<>();
        for (BandoVariant variant : BandoVariant.values()) {
            if (variant.family().terrain() == BandoFamily.Terrain.SHORE) {
                continue;
            }
            for (long seed : SEEDS) {
                plans.add(BandoGenerator.generate(variant, seed));
            }
        }
        return plans;
    }

    @Test
    void everyFamilyIsCoveredWithEightSeeds() {
        Set<BandoFamily> families = new HashSet<>();
        for (BandoVariant variant : BandoVariant.values()) {
            families.add(variant.family());
        }
        assertEquals(BandoFamily.values().length, families.size());
        assertEquals(8, SEEDS.length);
    }

    @Test
    void groundPartsRestOnTerrainWhenItIsFlush() {
        for (BandoPlan plan : landPlans()) {
            int floor = 70;
            List<String> problems = unsupported(plan, (x, z) -> floor, floor);
            assertTrue(problems.isEmpty(), plan.variant() + "/" + plan.seed() + ": " + problems);
        }
    }

    @Test
    void groundPartsReachTheGroundOverACarvedChannel() {
        for (BandoPlan plan : landPlans()) {
            int floor = 63;
            for (int depth : new int[]{1, 2, 7, 30}) {
                for (int offset = 0; offset < 12; offset += 3) {
                    int shift = offset;
                    IntBinaryOperator channel = (x, z) -> Math.floorMod(x + z + shift, 12) < 6 ? floor - depth : floor;
                    List<String> problems = unsupported(plan, channel, floor);
                    assertTrue(problems.isEmpty(), plan.variant() + "/" + plan.seed() + " Rinne " + depth + " tief: " + problems);
                }
            }
        }
    }

    @Test
    void groundPartsReachTheGroundOverAPit() {
        for (BandoPlan plan : landPlans()) {
            int floor = 90;
            List<String> problems = unsupported(plan, (x, z) -> floor - 12, floor);
            assertTrue(problems.isEmpty(), plan.variant() + "/" + plan.seed() + ": " + problems);
        }
    }

    @Test
    void yardOverOwnFillIsUnderpinnedAndStandingPartsKeepTheirFoundation() {
        int yardWithFill = 0;
        for (BandoPlan plan : landPlans()) {
            int g = plan.ground();
            for (int z = 0; z < plan.sizeZ(); z++) {
                for (int x = 0; x < plan.sizeX(); x++) {
                    int bottom = BandoSite.bottom(plan, x, z);
                    boolean stands = bottom >= 0 && bottom <= g + 1;
                    if (plan.mat(x, g, z) == Mat.YARD && plan.fill(x, z) != Mat.EMPTY && !stands) {
                        yardWithFill++;
                        assertTrue(BandoSite.underpinned(plan, x, z), plan.variant() + "/" + plan.seed() + " " + x + "," + z);
                    }
                    if (stands && plan.fill(x, z) != Mat.EMPTY) {
                        assertFalse(BandoSite.underpinned(plan, x, z), plan.variant() + "/" + plan.seed() + " " + x + "," + z + ": eigenes Fundament");
                    }
                }
            }
        }
        assertTrue(yardWithFill > 0, "kein Hof über einem eigenen Fundament in den Plänen (Silo, Wasserturm)");
    }

    @Test
    void shoreBandosAreNeverUnderpinned() {
        for (BandoVariant variant : new BandoVariant[]{BandoVariant.PIER, BandoVariant.PLATFORM}) {
            for (long seed : SEEDS) {
                BandoPlan plan = BandoGenerator.generate(variant, seed);
                for (int z = 0; z < plan.sizeZ(); z++) {
                    for (int x = 0; x < plan.sizeX(); x++) {
                        assertFalse(BandoSite.underpinned(plan, x, z), variant + "/" + seed);
                    }
                }
            }
        }
    }

    @Test
    void leavesKeptAliveOnlyByTheBandoAreOrphans() {
        Set<Long> leaves = new HashSet<>();
        for (int x = 1; x <= 9; x++) {
            leaves.add(pack(x, 10, 0));
        }
        Set<Long> own = Set.of(pack(5, 9, 0));
        long trunk = pack(0, 10, 0);
        Set<Long> orphans = LeafDistance.orphans(leaves, SIDES, at -> at == trunk);
        for (int x = 1; x <= 9; x++) {
            assertEquals(x >= LeafDistance.DECAY, orphans.contains(pack(x, 10, 0)), "Blatt " + x + " Schritte vom Stamm");
        }
        Set<Long> cut = new HashSet<>(leaves);
        cut.remove(pack(4, 10, 0));
        Set<Long> afterCut = LeafDistance.orphans(cut, SIDES, at -> at == trunk);
        assertTrue(afterCut.contains(pack(5, 10, 0)), "Blatt über dem eigenen Balken hängt an keinem Stamm");
        assertFalse(afterCut.contains(pack(3, 10, 0)));
        Set<Long> ownCounts = LeafDistance.orphans(cut, SIDES, at -> at == trunk || own.contains(at));
        assertFalse(ownCounts.contains(pack(5, 10, 0)), "so hielt der Balken das Blatt bisher am Leben (Abstand 1)");
    }

    @Test
    void cutCrownsLeaveNoLeafWithoutTrunkInTheAreaAndMargin() {
        for (BandoPlan plan : landPlans()) {
            BandoTransform t = BandoTransform.centered(plan, 0, 0, 70 - plan.ground(), 0);
            int[] a = BandoSite.leafArea(plan, t);
            BoundingBox area = new BoundingBox(a[0], a[1], a[2], a[3], a[4], a[5]);
            assertEquals(t.originX() - BandoSite.LEAF_MARGIN, area.minX());
            assertEquals(t.originZ() + t.widthZ() - 1 + BandoSite.LEAF_MARGIN, area.maxZ());
            assertEquals(t.worldY(plan.sizeY() - 1) + BandoSite.LEAF_MARGIN, area.maxY());
            assertEquals(t.worldY(0) - BandoSite.LEAF_MARGIN, area.minY(), "Rand reicht bis unter die Fundamente des Plans");
            Set<Long> leaves = new HashSet<>();
            Set<Long> own = new HashSet<>();
            Set<Long> trunks = new HashSet<>();
            int roof = 0;
            for (int y = 0; y < plan.sizeY(); y++) {
                for (int z = 0; z < plan.sizeZ(); z++) {
                    for (int x = 0; x < plan.sizeX(); x++) {
                        if (plan.mat(x, y, z).kind() == Mat.Kind.STRUCTURE) {
                            roof = y;
                        }
                    }
                }
            }
            int top = t.worldY(roof) + 1;
            for (int x = area.minX(); x <= area.maxX(); x++) {
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    int lx = t.localX(x, z);
                    int lz = t.localZ(x, z);
                    for (int y = top - 3; y <= top + 2; y++) {
                        int ly = y - t.originY();
                        Mat mat = plan.inside(lx, ly, lz) ? plan.mat(lx, ly, lz) : Mat.EMPTY;
                        if (mat.kind() == Mat.Kind.STRUCTURE) {
                            own.add(pack(x, y, z));
                        } else {
                            leaves.add(pack(x, y, z));
                        }
                    }
                }
            }
            int trunkX = area.maxX() + 3;
            int trunkZ = (area.minZ() + area.maxZ()) / 2;
            for (int y = top - 3; y <= top + 2; y++) {
                trunks.add(pack(trunkX, y, trunkZ));
            }
            for (int x = area.maxX() + 1; x < trunkX; x++) {
                for (int y = top - 3; y <= top + 2; y++) {
                    leaves.add(pack(x, y, trunkZ));
                }
            }
            Set<Long> orphans = LeafDistance.orphans(leaves, SIDES, trunks::contains);
            Set<Long> kept = new HashSet<>(leaves);
            kept.removeAll(orphans);
            Set<Long> still = LeafDistance.orphans(kept, SIDES, trunks::contains);
            assertTrue(still.isEmpty(), plan.variant() + "/" + plan.seed() + ": nach dem Entfernen bleiben " + still.size() + " Blätter ohne Stamm");
            for (long leaf : kept) {
                int d = distance(leaf, kept, trunks);
                assertTrue(d < LeafDistance.DECAY, plan.variant() + "/" + plan.seed() + ": Blatt mit Abstand " + d);
            }
            assertFalse(orphans.isEmpty(), plan.variant() + "/" + plan.seed() + ": Krone reicht weiter als 6 Blöcke vom Stamm");
            assertFalse(own.isEmpty());
        }
    }

    private static int distance(long leaf, Set<Long> leaves, Set<Long> trunks) {
        Set<Long> seen = new HashSet<>();
        List<Long> frontier = List.of(leaf);
        seen.add(leaf);
        for (int d = 1; d <= LeafDistance.DECAY; d++) {
            List<Long> next = new ArrayList<>();
            for (long at : frontier) {
                for (LongUnaryOperator side : SIDES) {
                    long n = side.applyAsLong(at);
                    if (trunks.contains(n)) {
                        return d;
                    }
                    if (leaves.contains(n) && seen.add(n)) {
                        next.add(n);
                    }
                }
            }
            frontier = next;
        }
        return LeafDistance.DECAY;
    }

    @Test
    void plansOfTheEightSeedsStayByteForByteTheSame() {
        BandoVariant[] variants = BandoVariant.values();
        assertEquals(FINGERPRINTS.length, variants.length);
        for (int v = 0; v < variants.length; v++) {
            long[] prints = new long[SEEDS.length];
            for (int i = 0; i < SEEDS.length; i++) {
                prints[i] = BandoGenerator.generate(variants[v], SEEDS[i]).fingerprint();
            }
            assertArrayEquals(FINGERPRINTS[v], prints, variants[v] + ": Bauplan geändert");
        }
    }
}
