package de.lardtom.propwash.api.accessory;

import de.lardtom.propwash.api.drone.DronePhysics;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public interface AccessoryContext {
    ServerLevel level();

    Entity drone();

    Identifier accessory();

    AccessorySlot slot();

    AccessoryInventory inventory();

    Optional<ServerPlayer> pilot();

    boolean flying();

    boolean armed();

    boolean actionHeld(int number);

    MountPose mount();

    Vec3 velocity();

    DronePhysics physics();
}
