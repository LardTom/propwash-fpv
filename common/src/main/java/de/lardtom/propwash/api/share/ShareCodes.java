package de.lardtom.propwash.api.share;

import de.lardtom.propwash.api.build.DroneData;

public interface ShareCodes {
    ShareResult decode(String text);

    String encode(DroneData data, ShareOptions options);

    ShareResult convert(String legacyCode);
}
