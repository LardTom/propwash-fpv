package de.lardtom.propwash.api.part;

public record ActionCamDefinition(int maxHeight, int maxFps, boolean stabilization, boolean horizonLevelling, double ownBatteryWh,
                                  double recordWatts, double maxRecordWatts, double fovDegrees, double imageQuality,
                                  double lowLightNoise, double sensorInches) {
    public ActionCamDefinition {
        if (maxHeight != 720 && maxHeight != 1080 && maxHeight != 1440 && maxHeight != 2160) {
            throw new IllegalArgumentException("Höchstauflösung muss 720, 1080, 1440 oder 2160 sein: " + maxHeight);
        }
        if (maxFps != 30 && maxFps != 60) {
            throw new IllegalArgumentException("Bildrate muss 30 oder 60 sein: " + maxFps);
        }
        if (!(ownBatteryWh >= 0.0 && ownBatteryWh <= 100.0 && recordWatts > 0.0 && maxRecordWatts >= recordWatts && maxRecordWatts <= 50.0)) {
            throw new IllegalArgumentException("Energiewerte der Action-Cam ungültig");
        }
        if (!(fovDegrees >= 60.0 && fovDegrees <= 180.0 && imageQuality > 0.0 && imageQuality <= 1.0 && lowLightNoise >= 0.0
                && lowLightNoise <= 1.0 && sensorInches > 0.0 && sensorInches <= 2.0)) {
            throw new IllegalArgumentException("Bildwerte der Action-Cam ungültig");
        }
    }
}
