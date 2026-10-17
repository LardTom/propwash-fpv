package de.lardtom.propwash.api.accessory;

import net.minecraft.world.phys.Vec3;

public record MountPose(Vec3 anchor, Vec3 center, Vec3 front, Vec3 forward, Vec3 up, Vec3 right) {
    public Vec3 at(double rightMetres, double upMetres, double forwardMetres) {
        return this.anchor.add(this.right.scale(rightMetres)).add(this.up.scale(upMetres)).add(this.forward.scale(forwardMetres));
    }
}
