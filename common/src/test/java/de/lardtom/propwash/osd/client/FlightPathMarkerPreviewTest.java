package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.osd.FlightPathMarker;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdProjection;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightPathMarkerPreviewTest {
    private static final List<OsdGrid> GRIDS = List.of(new OsdGrid(8.0F, 40.0F, 320.0F, 180.0F), new OsdGrid(8.0F, 40.0F, 240.0F, 180.0F),
            new OsdGrid(0.0F, 0.0F, 854.0F, 480.0F));
    private static final double SECONDS = 600.0;
    private static final double STEP = 0.05;

    private static FlightPathMarker.State marker(OsdData sample, OsdGrid grid, FlightPathMarker.State out) {
        Quaternionf rotation = OsdRenderer.previewRotation(sample, new Quaternionf());
        OsdProjection projection = new OsdProjection().setup(rotation, OsdRenderer.PREVIEW_FOV, grid.centerX(), grid.centerY(), grid.height());
        float margin = grid.cellHeight() * 1.5F;
        return new FlightPathMarker().compute(sample.velocityX, sample.velocityY, sample.velocityZ, projection, grid.left() + margin,
                grid.top() + margin, grid.left() + grid.width() - margin, grid.top() + grid.height() - margin, out);
    }

    @Test
    void editorPreviewMarkerStaysSlightlyBelowTheCenterWhileTheSampleMoves() {
        for (OsdGrid grid : GRIDS) {
            OsdData sample = OsdData.sample();
            FlightPathMarker.State state = new FlightPathMarker.State();
            List<String> off = new ArrayList<>();
            int frames = 0;
            int offFrames = 0;
            double minBelow = Double.MAX_VALUE;
            double maxBelow = -Double.MAX_VALUE;
            double maxSide = 0.0;
            for (double t = 0.0; t < SECONDS; t += STEP) {
                OsdConfigTab.animateSample(sample, t);
                marker(sample, grid, state);
                frames++;
                double below = (state.y - grid.centerY()) / grid.height();
                double side = Math.abs(state.x - grid.centerX()) / grid.width();
                boolean slightlyBelow = state.visible && state.inView && state.alpha >= 1.0F && below > 0.05 && below < 0.3 && side < 0.05;
                if (!slightlyBelow) {
                    offFrames++;
                    if (off.size() < 6) {
                        off.add(String.format(Locale.ROOT, "t %.2f s: Kompass %.0f°, Nick %.1f°, Roll %.1f° → %s %.3f H unter der Mitte, %.3f B seitlich", t,
                                sample.compassDeg, sample.pitchDeg, sample.rollDeg, state.visible ? (state.inView ? "im Bild" : "am Rand") : "unsichtbar", below,
                                side));
                    }
                }
                if (state.visible) {
                    minBelow = Math.min(minBelow, below);
                    maxBelow = Math.max(maxBelow, below);
                    maxSide = Math.max(maxSide, side);
                }
            }
            assertTrue(offFrames == 0, String.format(Locale.ROOT, "Raster %.0f × %.0f: Vorschau-Marker in %d von %d Bildern nicht leicht unter der Mitte: %s",
                    grid.width(), grid.height(), offFrames, frames, String.join("; ", off)));
            assertTrue(maxBelow - minBelow < 0.005 && maxSide < 0.005, String.format(Locale.ROOT,
                    "Raster %.0f × %.0f: Vorschau-Marker wandert (%.3f–%.3f H unter der Mitte, bis %.3f B seitlich)", grid.width(), grid.height(), minBelow,
                    maxBelow, maxSide));
        }
    }
}
