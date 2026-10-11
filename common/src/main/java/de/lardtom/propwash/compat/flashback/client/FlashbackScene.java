package de.lardtom.propwash.compat.flashback.client;

import de.lardtom.propwash.video.record.path.CameraTrack;

public record FlashbackScene(String name, String cameraTrackName, String fovTrackName, int colour, double eyeHeight, CameraTrack track) {
}
