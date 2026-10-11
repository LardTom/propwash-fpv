package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;

public record TickPose(int tick, double x, double y, double z, Quaterniond rotation, double fov) {
}
