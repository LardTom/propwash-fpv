package de.lardtom.propwash.video.client;

import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.addon.AddonOsdKeys;

import java.util.ArrayList;
import java.util.List;

public final class SpectateBannerPlacement {
    public static final int DEFAULT_ROW = 3;
    private static final int[] ROWS = {DEFAULT_ROW, 2, 1, 4, 5, 0};
    private static final int CLEAR_ROW = 6;
    private static final float CENTER_REACH = 1.0F;
    private static final float SIDE_REACH = 3.0F;
    private static final float TEXT_MARGIN = 0.1F;

    private SpectateBannerPlacement() {
    }

    private enum Check {
        TEXT,
        ANCHOR,
        WARNINGS
    }

    public record Occupant(int col, int row, OsdElement.Align align, float scale, boolean warnings) {
    }

    public static List<Occupant> occupants(OsdConfig config, List<OsdElementSpec> addons) {
        List<Occupant> out = new ArrayList<>();
        for (OsdElement element : OsdElement.values()) {
            if (!element.movable() || !config.enabled(element)) {
                continue;
            }
            OsdConfig.Slot slot = config.slot(element);
            out.add(new Occupant(slot.col, slot.row, element.align(), config.elementScale(element), element == OsdElement.WARNINGS));
        }
        for (OsdElementSpec spec : addons) {
            OsdConfig.Slot slot = AddonOsdKeys.peek(config, spec);
            if (slot.enabled) {
                out.add(new Occupant(slot.col, slot.row, AddonOsdKeys.align(spec.align()), slot.hasScale() ? slot.scale : config.scale, false));
            }
        }
        return out;
    }

    public static int row(OsdGrid grid, List<Occupant> occupants, float left, float right, float height) {
        for (Check check : Check.values()) {
            int row = firstFree(grid, occupants, left, right, height, check);
            if (row >= 0) {
                return row;
            }
        }
        return DEFAULT_ROW;
    }

    private static int firstFree(OsdGrid grid, List<Occupant> occupants, float left, float right, float height, Check check) {
        for (int row : ROWS) {
            float top = grid.cellTop(row);
            float bottom = top + height;
            if (row > 0 && bottom > grid.cellTop(CLEAR_ROW)) {
                continue;
            }
            if (free(grid, occupants, left, right, top, bottom, check)) {
                return row;
            }
        }
        return -1;
    }

    private static boolean free(OsdGrid grid, List<Occupant> occupants, float left, float right, float top, float bottom, Check check) {
        for (Occupant occupant : occupants) {
            if (check == Check.WARNINGS && !occupant.warnings()) {
                continue;
            }
            float half = grid.cellHeight() * Math.max(0.5F, OsdGrid.TEXT_ROW_FILL * occupant.scale() * 0.5F + TEXT_MARGIN);
            float center = grid.rowCenterY(occupant.row());
            boolean text = check == Check.TEXT || occupant.warnings();
            if (top < center + half && bottom > center - half && reaches(grid, occupant, left, right, text)) {
                return false;
            }
        }
        return true;
    }

    private static boolean reaches(OsdGrid grid, Occupant occupant, float left, float right, boolean text) {
        float anchor = grid.anchorX(occupant.col(), occupant.align());
        float reach = text ? grid.cellWidth() * (occupant.align() == OsdElement.Align.CENTER ? CENTER_REACH : SIDE_REACH) : 0.0F;
        return switch (occupant.align()) {
            case LEFT -> anchor <= right && anchor + reach >= left;
            case RIGHT -> anchor >= left && anchor - reach <= right;
            case CENTER -> anchor + reach >= left && anchor - reach <= right;
        };
    }
}
