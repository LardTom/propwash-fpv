package de.lardtom.propwash.video.client;

import de.lardtom.propwash.api.client.osd.OsdAlign;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.osd.addon.AddonOsdKeys;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectateBannerPlacementTest {
    private static final float HEIGHT = 4 * 2 + 9 + 10 + 1;
    private static final int[][] GUIS = {{320, 240}, {427, 240}, {480, 270}, {569, 320}, {640, 360}, {683, 384}, {854, 480}, {960, 540}, {1280, 720}};
    private static final int[] WIDTHS = {96, 150, 196, 240, 296};
    private static final int[] USUAL_WIDTHS = {96, 150, 196};
    private static final OsdElementSpec WAYPOINT = spec("waypoint", 15, 4, OsdAlign.CENTER);
    private static final OsdElementSpec WAYPOINT_SIDE = spec("waypoint_side", 28, 2, OsdAlign.RIGHT);
    private static final OsdElementSpec GIMBAL = spec("gimbal", 1, 9, OsdAlign.LEFT);

    @Test
    void theBannerNeverCoversTheWarningLineInAnyPresetWindowOrTextWidth() {
        List<String> covered = new ArrayList<>();
        for (OsdPreset preset : OsdPreset.values()) {
            OsdConfig config = preset(preset);
            for (int[] gui : GUIS) {
                for (int width : WIDTHS) {
                    Placed placed = place(config, List.of(), gui, width);
                    int row = config.slot(OsdElement.WARNINGS).row;
                    if (placed.overlapsRow(row)) {
                        covered.add(preset + " " + gui[0] + "x" + gui[1] + " Breite " + width + ": Banner in Zeile " + placed.row + " über der Warnzeile "
                                + row);
                    }
                }
            }
        }
        assertTrue(covered.isEmpty(), String.join("\n", covered));
    }

    @Test
    void minimalMovesTheBannerAboveTheWarningLine() {
        OsdConfig config = preset(OsdPreset.MINIMAL);
        assertEquals(3, config.slot(OsdElement.WARNINGS).row);
        assertEquals(1, place(config, List.of(), new int[]{427, 240}, 196).row);
        assertEquals(1, place(config, List.of(), new int[]{640, 360}, 240).row);
        assertEquals(2, place(config, List.of(), new int[]{960, 540}, 240).row);
    }

    @Test
    void presetsWithAFreeRowThreeKeepTheBannerWhereItWas() {
        for (OsdPreset preset : List.of(OsdPreset.STANDARD, OsdPreset.FULL)) {
            OsdConfig config = preset(preset);
            for (int[] gui : GUIS) {
                for (int width : USUAL_WIDTHS) {
                    assertEquals(SpectateBannerPlacement.DEFAULT_ROW, place(config, List.of(), gui, width).row, preset + " " + gui[0] + "x" + gui[1]);
                }
            }
        }
    }

    @Test
    void inMinimalStandardAndRaceNoCentreElementIsCovered() {
        List<String> covered = new ArrayList<>();
        for (OsdPreset preset : List.of(OsdPreset.MINIMAL, OsdPreset.STANDARD, OsdPreset.RACE)) {
            OsdConfig config = preset(preset);
            for (int[] gui : GUIS) {
                for (int width : WIDTHS) {
                    Placed placed = place(config, List.of(), gui, width);
                    for (SpectateBannerPlacement.Occupant occupant : SpectateBannerPlacement.occupants(config, List.of())) {
                        if (placed.inSpan(occupant) && placed.overlapsRow(occupant.row())) {
                            covered.add(preset + " " + gui[0] + "x" + gui[1] + " Breite " + width + ": Zeile " + occupant.row() + " Spalte " + occupant.col());
                        }
                    }
                }
            }
        }
        assertTrue(covered.isEmpty(), String.join("\n", covered));
    }

    @Test
    void addonElementsInTheCentreAreAvoidedAsInTheCameraView() {
        OsdConfig config = preset(OsdPreset.MINIMAL);
        List<OsdElementSpec> addons = List.of(WAYPOINT, WAYPOINT_SIDE, GIMBAL);
        for (int[] gui : GUIS) {
            for (int width : WIDTHS) {
                Placed placed = place(config, addons, gui, width);
                assertFalse(placed.overlapsRow(3), "Warnzeile bei " + gui[0] + "x" + gui[1]);
                assertFalse(placed.overlapsRow(WAYPOINT.row()), "Wegpunkt-Element bei " + gui[0] + "x" + gui[1] + " Breite " + width);
            }
        }
        OsdConfig.Slot off = AddonOsdKeys.slot(config, WAYPOINT);
        off.enabled = false;
        assertTrue(SpectateBannerPlacement.occupants(config, addons).stream().noneMatch(o -> o.row() == WAYPOINT.row() && o.col() == WAYPOINT.column()));
    }

    @Test
    void aMovedWarningLineIsAvoidedWhereverItSits() {
        for (int row = 0; row <= 7; row++) {
            OsdConfig config = preset(OsdPreset.MINIMAL);
            config.move(OsdElement.WARNINGS, 15, row);
            for (int[] gui : GUIS) {
                for (int width : WIDTHS) {
                    Placed placed = place(config, List.of(), gui, width);
                    assertFalse(placed.overlapsRow(row), "Warnzeile " + row + " bei " + gui[0] + "x" + gui[1] + " in Zeile " + placed.row);
                }
            }
        }
    }

    @Test
    void aBiggerWarningTextReservesMoreThanItsRow() {
        OsdConfig config = preset(OsdPreset.STANDARD);
        config.move(OsdElement.WARNINGS, 15, 5);
        config.slot(OsdElement.WARNINGS).scale = OsdConfig.MAX_SCALE;
        assertTrue(SpectateBannerPlacement.occupants(config, List.of()).stream().anyMatch(o -> o.warnings() && o.scale() == OsdConfig.MAX_SCALE));
        OsdGrid grid = OsdGrid.screen(320, 240);
        float half = grid.cellHeight() * OsdGrid.TEXT_ROW_FILL * OsdConfig.MAX_SCALE * 0.5F;
        for (int width : WIDTHS) {
            Placed placed = place(config, List.of(), new int[]{320, 240}, width);
            float bottom = grid.cellTop(placed.row) + HEIGHT;
            assertTrue(bottom <= grid.rowCenterY(5) - half || grid.cellTop(placed.row) >= grid.rowCenterY(5) + half, "Zeile " + placed.row);
        }
    }

    @Test
    void readingAnAddonSlotDoesNotChangeTheLayout() {
        OsdConfig config = preset(OsdPreset.MINIMAL);
        int before = config.elements.size();
        SpectateBannerPlacement.occupants(config, List.of(WAYPOINT));
        assertEquals(before, config.elements.size());
    }

    private static OsdConfig preset(OsdPreset preset) {
        OsdConfig config = OsdConfig.defaults();
        config.applyPreset(preset);
        return config;
    }

    private static Placed place(OsdConfig config, List<OsdElementSpec> addons, int[] gui, int width) {
        OsdGrid grid = OsdGrid.screen(gui[0], gui[1]);
        float banner = Math.min(width, gui[0] - 24);
        float left = grid.centerX() - banner * 0.5F;
        float right = grid.centerX() + banner * 0.5F;
        int row = SpectateBannerPlacement.row(grid, SpectateBannerPlacement.occupants(config, addons), left, right, HEIGHT);
        return new Placed(grid, row, left, right);
    }

    private static OsdElementSpec spec(String path, int col, int row, OsdAlign align) {
        return OsdElementSpec.builder(Identifier.fromNamespaceAndPath("bannertest", path)).position(col, row).align(align).enabledByDefault(true)
                .maxChars(12).preview("WP 2/5").content(context -> OsdText.of("WP 2/5")).build();
    }

    private record Placed(OsdGrid grid, int row, float left, float right) {
        boolean overlapsRow(int other) {
            float top = this.grid.cellTop(this.row);
            float bottom = top + HEIGHT;
            return top < this.grid.cellTop(other + 1) && bottom > this.grid.cellTop(other);
        }

        boolean inSpan(SpectateBannerPlacement.Occupant occupant) {
            float anchor = this.grid.anchorX(occupant.col(), occupant.align());
            return anchor >= this.left && anchor <= this.right;
        }
    }
}
