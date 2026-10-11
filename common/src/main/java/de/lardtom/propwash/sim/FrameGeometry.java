package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.build.FrameType;

record FrameGeometry(boolean batteryOnTop, double batteryPlateY, double electronicsY) {
    static FrameGeometry of(FrameType frame) {
        AirframeGeometry g = frame.definition().geometry();
        return new FrameGeometry(g.batteryOnTop(), g.batteryPlateMm() / 1000.0, g.electronicsMm() / 1000.0);
    }
}
