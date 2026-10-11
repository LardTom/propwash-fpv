package de.lardtom.propwash.bando;

import de.lardtom.propwash.bando.plan.BandoChecks;
import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPalettes;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.BandoViews;
import de.lardtom.propwash.bando.plan.Mat;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandoPlanTest {
    private static final int SEEDS = 60;
    private static final Map<BandoVariant, Integer> MIN_GAPS = new EnumMap<>(BandoVariant.class);

    static {
        for (BandoVariant variant : BandoVariant.values()) {
            MIN_GAPS.put(variant, 6);
        }
        MIN_GAPS.put(BandoVariant.PIER, 4);
    }

    private static long seed(BandoVariant variant, int i) {
        return 0x9E3779B97F4A7C15L * (i + 1) + variant.ordinal() * 1_000_003L;
    }

    @Test
    void sameSeedGivesSamePlan() {
        for (BandoVariant variant : BandoVariant.values()) {
            for (int i = 0; i < 5; i++) {
                BandoPlan a = BandoGenerator.generate(variant, seed(variant, i));
                BandoPlan b = BandoGenerator.generate(variant, seed(variant, i));
                assertEquals(a.fingerprint(), b.fingerprint(), variant + " nicht deterministisch");
                assertEquals(a.hasChest(), b.hasChest());
            }
        }
    }

    @Test
    void everyBandoIsDifferent() {
        for (BandoVariant variant : BandoVariant.values()) {
            Set<Long> prints = new HashSet<>();
            for (int i = 0; i < SEEDS; i++) {
                prints.add(BandoGenerator.generate(variant, seed(variant, i)).fingerprint());
            }
            assertTrue(prints.size() >= SEEDS * 9 / 10, variant + ": nur " + prints.size() + " verschiedene von " + SEEDS);
        }
    }

    @Test
    void plansStayInBoundsAndStanding() {
        for (BandoVariant variant : BandoVariant.values()) {
            for (int i = 0; i < SEEDS; i++) {
                BandoPlan plan = BandoGenerator.generate(variant, seed(variant, i));
                String where = variant + "/" + i;
                assertTrue(plan.sizeX() <= BandoGenerator.MAX_SIZE && plan.sizeZ() <= BandoGenerator.MAX_SIZE && plan.sizeY() <= BandoGenerator.MAX_SIZE,
                        where + " zu groß");
                assertTrue(plan.sizeX() >= 10 && plan.sizeZ() >= 10, where + " zu klein");
                assertEquals(0, BandoChecks.ungrounded(plan), where + ": schwebende Blöcke");
                assertTrue(plan.placedBlocks() >= 150, where + ": nur " + plan.placedBlocks() + " Blöcke");
                for (int y = 0; y < plan.sizeY(); y++) {
                    for (int z = 0; z < plan.sizeZ(); z++) {
                        for (int x = 0; x < plan.sizeX(); x++) {
                            Mat mat = plan.mat(x, y, z);
                            if (mat == Mat.WATER) {
                                assertEquals(BandoFamily.SWAMP, variant.family(), where + ": Wasser außerhalb des Sumpfs");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void bandosHaveGapsToFlyThrough() {
        for (BandoVariant variant : BandoVariant.values()) {
            int total = 0;
            int vertical = 0;
            for (int i = 0; i < SEEDS; i++) {
                BandoPlan plan = BandoGenerator.generate(variant, seed(variant, i));
                int gaps = BandoChecks.throughGaps(plan);
                total += gaps;
                vertical += BandoChecks.verticalGaps(plan) > 0 ? 1 : 0;
                assertTrue(gaps >= MIN_GAPS.get(variant), variant + "/" + i + ": nur " + gaps + " Durchflüge");
                BandoViews.View view = BandoViews.flyThrough(plan);
                assertTrue(view.run() >= BandoViews.MIN_RUN, variant + "/" + i + ": keine Fluglinie durch den Bando");
            }
            if (variant != BandoVariant.PIER) {
                assertTrue(vertical >= SEEDS / 2, variant + ": nur " + vertical + " von " + SEEDS + " mit Loch nach oben");
            }
            assertTrue(total >= SEEDS * 8, variant + ": im Mittel nur " + total / (double) SEEDS + " Durchflüge");
        }
    }

    @Test
    void chestsNowAndThenAndAlwaysOpenable() {
        int chests = 0;
        int plans = 0;
        for (BandoVariant variant : BandoVariant.values()) {
            int own = 0;
            for (int i = 0; i < SEEDS; i++) {
                BandoPlan plan = BandoGenerator.generate(variant, seed(variant, i));
                plans++;
                if (!plan.hasChest()) {
                    continue;
                }
                own++;
                assertEquals(1, plan.count(Mat.CHEST), variant + "/" + i + ": mehr als eine Truhe");
                assertTrue(BandoChecks.chestOpen(plan), variant + "/" + i + ": Truhe lässt sich nicht öffnen");
                assertTrue(BandoChecks.chestReachable(plan), variant + "/" + i + ": Truhe nicht erreichbar");
                Mat below = plan.mat(plan.chestX(), plan.chestY() - 1, plan.chestZ());
                assertTrue(below.holdsUp() || below == Mat.EMPTY && plan.chestY() - 1 <= plan.ground(), variant + "/" + i + ": Truhe schwebt");
            }
            assertTrue(own >= SEEDS / 4 && own <= SEEDS * 3 / 4, variant + ": " + own + " Truhen in " + SEEDS + " Bandos");
            chests += own;
        }
        double share = chests / (double) plans;
        assertTrue(share >= 0.35 && share <= 0.65, "Truhen-Anteil " + share);
    }

    @Test
    void palettesCoverEveryUsedRole() {
        Set<Mat> optional = EnumSet.of(Mat.VINE, Mat.MOSS, Mat.FOLIAGE, Mat.FLOAT);
        for (BandoVariant variant : BandoVariant.values()) {
            Set<Mat> used = EnumSet.noneOf(Mat.class);
            for (int i = 0; i < 20; i++) {
                BandoPlan plan = BandoGenerator.generate(variant, seed(variant, i));
                for (int y = 0; y < plan.sizeY(); y++) {
                    for (int z = 0; z < plan.sizeZ(); z++) {
                        for (int x = 0; x < plan.sizeX(); x++) {
                            used.add(plan.mat(x, y, z));
                        }
                        used.add(plan.fill(0, 0));
                    }
                }
                for (int z = 0; z < plan.sizeZ(); z++) {
                    for (int x = 0; x < plan.sizeX(); x++) {
                        used.add(plan.fill(x, z));
                    }
                }
            }
            used.remove(Mat.EMPTY);
            used.remove(Mat.AIR);
            for (BandoPalettes.Palette palette : BandoPalettes.of(variant)) {
                for (Mat mat : used) {
                    if (optional.contains(mat)) {
                        continue;
                    }
                    assertFalse(palette.entries(mat).isEmpty(), variant + "/" + palette.name() + ": kein Block für " + mat);
                }
            }
        }
    }

    @Test
    void familiesMatchVariants() {
        for (BandoFamily family : BandoFamily.values()) {
            assertFalse(BandoVariant.of(family).isEmpty(), family + " ohne Variante");
            for (int roll = 0; roll < 100; roll++) {
                assertEquals(family, BandoVariant.pick(family, roll).family());
            }
        }
    }
}
