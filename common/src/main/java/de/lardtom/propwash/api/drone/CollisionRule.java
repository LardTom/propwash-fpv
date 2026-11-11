package de.lardtom.propwash.api.drone;

@FunctionalInterface
public interface CollisionRule {
    CollisionFlags flags(CollisionQuery query);

    default void onBlockContact(BlockContact contact) {
    }
}
