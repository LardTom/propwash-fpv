package de.lardtom.propwash.barrier;

import de.lardtom.propwash.drone.QuadcopterEntity;
import org.jspecify.annotations.Nullable;

public enum Collider {
    DRONE,
    ENTITY,
    NONE,
    COURSE;

    public static Collider ofType(@Nullable Class<?> type) {
        if (type == null) {
            return NONE;
        }
        return QuadcopterEntity.class.isAssignableFrom(type) ? DRONE : ENTITY;
    }
}
