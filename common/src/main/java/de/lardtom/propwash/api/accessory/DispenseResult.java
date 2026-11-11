package de.lardtom.propwash.api.accessory;

import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;

public record DispenseResult(Status status, Optional<Entity> entity, double recoilNs) {
    public enum Status {
        FIRED,
        PLACED,
        USED,
        EMPTY,
        REFUSED,
        BLOCKED,
        DENIED;

        public boolean success() {
            return this == FIRED || this == PLACED || this == USED;
        }
    }

    public DispenseResult {
        Objects.requireNonNull(status, "status");
        entity = Objects.requireNonNullElse(entity, Optional.empty());
        recoilNs = Double.isFinite(recoilNs) ? Math.max(0.0, recoilNs) : 0.0;
    }

    public static DispenseResult of(Status status) {
        return new DispenseResult(status, Optional.empty(), 0.0);
    }

    public static DispenseResult of(Status status, Entity entity, double recoilNs) {
        return new DispenseResult(status, Optional.of(Objects.requireNonNull(entity, "entity")), recoilNs);
    }

    public boolean success() {
        return this.status.success();
    }
}
