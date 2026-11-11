package de.lardtom.propwash.barrier;

public enum BarrierKind {
    DRONE_PASS("drone_pass_barrier"),
    DRONE_BLOCK("drone_block_barrier");

    private final String path;

    BarrierKind(String path) {
        this.path = path;
    }

    public String path() {
        return this.path;
    }

    public boolean solidFor(Collider collider) {
        if (collider == Collider.COURSE) {
            return true;
        }
        return switch (this) {
            case DRONE_PASS -> collider != Collider.DRONE;
            case DRONE_BLOCK -> collider == Collider.DRONE;
        };
    }
}
