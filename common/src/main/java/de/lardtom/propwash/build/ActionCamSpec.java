package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.ActionCamDefinition;

import java.util.ArrayList;
import java.util.List;

public record ActionCamSpec(int maxHeight, int maxFps, boolean stabilization, boolean horizonLevelling, double ownBatteryWh,
                            double recordWatts, double maxRecordWatts, double fovDegrees, double imageQuality, double lowLightNoise,
                            double sensorInches) {
    public static final int FULL_HD = 1080;
    public static final List<Integer> HEIGHTS = List.of(720, 1080, 1440, 2160);

    public static final ActionCamSpec LIGHT = new ActionCamSpec(1080, 60, false, false, 0.0, 4.0, 4.0, 150.0, 0.80, 0.55, 1.0 / 2.8);
    public static final ActionCamSpec HEAVY = new ActionCamSpec(1440, 60, true, true, 6.6, 4.4, 6.6, 155.0, 0.95, 0.30, 1.0 / 1.9);

    public static ActionCamSpec of(ActionCamDefinition definition) {
        ActionCamSpec spec = new ActionCamSpec(definition.maxHeight(), definition.maxFps(), definition.stabilization(),
                definition.horizonLevelling(), definition.ownBatteryWh(), definition.recordWatts(), definition.maxRecordWatts(),
                definition.fovDegrees(), definition.imageQuality(), definition.lowLightNoise(), definition.sensorInches());
        if (spec.equals(LIGHT)) {
            return LIGHT;
        }
        if (spec.equals(HEAVY)) {
            return HEAVY;
        }
        return spec;
    }

    public boolean ownBattery() {
        return this.ownBatteryWh > 0.0;
    }

    public List<Integer> supportedHeights() {
        List<Integer> heights = new ArrayList<>();
        for (int height : HEIGHTS) {
            if (height <= this.maxHeight) {
                heights.add(height);
            }
        }
        return List.copyOf(heights);
    }

    public double recordWatts(int height) {
        if (height <= FULL_HD || this.maxHeight <= FULL_HD) {
            return this.recordWatts;
        }
        double t = Math.min(1.0, (height - FULL_HD) / (double) (this.maxHeight - FULL_HD));
        return this.recordWatts + (this.maxRecordWatts - this.recordWatts) * t;
    }

    public double recordMinutes(int height) {
        if (!this.ownBattery()) {
            return Double.POSITIVE_INFINITY;
        }
        return this.ownBatteryWh * 60.0 / this.recordWatts(height);
    }
}
