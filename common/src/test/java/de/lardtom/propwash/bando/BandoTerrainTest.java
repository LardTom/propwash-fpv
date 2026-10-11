package de.lardtom.propwash.bando;

import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoGenerator;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoSite;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.Mat;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandoTerrainTest {
    private static final float SQUEEZE_MAX = 0.5F - 1.0F / 24.0F;
    private static final int SEEDS = 12;

    @Test
    void beardBoxClearsTheWholeBoxAboveTheFloorAndKeepsTheFloorSolid() {
        for (BandoVariant variant : BandoVariant.values()) {
            if (variant.family() == BandoFamily.COAST) {
                continue;
            }
            BandoPlan plan = BandoGenerator.generate(variant, 11L);
            BandoTransform t = BandoTransform.centered(plan, 1, 100, 70 - plan.ground(), -40);
            BoundingBox box = new BoundingBox(t.originX(), t.worldY(BandoSite.boxBottom(plan)), t.originZ(), t.originX() + t.widthX() - 1,
                    t.worldY(plan.sizeY() - 1), t.originZ() + t.widthZ() - 1);
            Beardifier beard = new Beardifier(List.of(new Beardifier.Rigid(box, TerrainAdjustment.BEARD_BOX, 0)), List.of(), box.inflatedBy(24));
            int floor = t.worldY(plan.ground());
            assertEquals(floor + 1, box.minY(), variant + ": Box beginnt über der Bodenzeile");
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int y = floor + 1; y <= box.maxY(); y++) {
                        float value = beard.sampleValue(null, x, y, z);
                        assertTrue(value + SQUEEZE_MAX < 0.0F, variant + ": Gelände bleibt bei " + x + "," + y + "," + z + " stehen (" + value + ")");
                    }
                    float ground = beard.sampleValue(null, x, floor, z);
                    assertTrue(ground - SQUEEZE_MAX > 0.0F, variant + ": Bodenzeile bei " + x + "," + z + " nicht fest (" + ground + ")");
                }
            }
        }
    }

    @Test
    void yardClosesAroundTheBuildingAndSiteColumnsCoverIt() {
        int r = BandoGenerator.YARD;
        for (BandoVariant variant : BandoVariant.values()) {
            boolean shore = variant.family().terrain() == BandoFamily.Terrain.SHORE;
            for (long seed = 1; seed <= SEEDS; seed++) {
                BandoPlan plan = BandoGenerator.generate(variant, seed);
                assertEquals(plan.ground() + 1 + (shore ? BandoSite.SHORE_BANK : 0), BandoSite.clearFrom(plan), variant + ": räumt ab dieser Zeile");
                boolean[] site = BandoSite.siteColumns(plan);
                int g = plan.ground();
                int sx = plan.sizeX();
                int columns = 0;
                for (int z = 0; z < plan.sizeZ(); z++) {
                    for (int x = 0; x < sx; x++) {
                        boolean built = false;
                        for (int y = g; y < plan.sizeY(); y++) {
                            built |= plan.mat(x, y, z) != Mat.EMPTY;
                        }
                        assertEquals(built, site[z * sx + x], variant + "/" + seed + ": Spalte " + x + "," + z);
                        if (site[z * sx + x]) {
                            columns++;
                        }
                        if (shore || plan.mat(x, g, z).kind() != Mat.Kind.STRUCTURE && plan.mat(x, g + 1, z).kind() != Mat.Kind.STRUCTURE) {
                            continue;
                        }
                        for (int dz = -r; dz <= r; dz++) {
                            for (int dx = -r; dx <= r; dx++) {
                                int nx = x + dx;
                                int nz = z + dz;
                                if (nx < 0 || nz < 0 || nx >= sx || nz >= plan.sizeZ() || dx * dx + dz * dz > r * r + 1) {
                                    continue;
                                }
                                if (plan.mat(nx, g + 1, nz).kind() == Mat.Kind.STRUCTURE) {
                                    assertTrue(plan.mat(nx, g, nz) != Mat.YARD, variant + "/" + seed + ": Hof unter einem Bauteil bei " + nx + "," + nz);
                                } else {
                                    assertNotEquals(Mat.EMPTY, plan.mat(nx, g, nz), variant + "/" + seed + ": Lücke im Hof bei " + nx + "," + nz);
                                }
                                assertTrue(site[nz * sx + nx], variant + "/" + seed + ": Hof außerhalb der Grundriss-Spalten bei " + nx + "," + nz);
                            }
                        }
                    }
                }
                assertFalse(columns == 0, variant + "/" + seed + ": keine Grundriss-Spalten");
            }
        }
    }
}
