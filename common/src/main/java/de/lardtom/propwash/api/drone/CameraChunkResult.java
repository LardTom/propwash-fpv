package de.lardtom.propwash.api.drone;

public record CameraChunkResult(Status status, int radius) {
    public enum Status {
        OPENED,
        UPDATED,
        NOT_A_DRONE,
        NOT_OWN_DRONE,
        OTHER_DIMENSION,
        RULE_OFF,
        TOO_MANY,
        SERVER_BUSY,
        CLIENT_UNSUPPORTED,
        PLAYER_GONE
    }

    public static CameraChunkResult refused(Status status) {
        return new CameraChunkResult(status, 0);
    }

    public boolean accepted() {
        return this.status == Status.OPENED || this.status == Status.UPDATED;
    }
}
