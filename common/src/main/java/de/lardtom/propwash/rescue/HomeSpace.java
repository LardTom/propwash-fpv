package de.lardtom.propwash.rescue;

import de.lardtom.propwash.drone.SoftGround;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class HomeSpace implements HomeResetRules.Space {
    private static final double SOFT_EDGE = 1.0E-7;

    private final Level level;
    private final Entity drone;
    private final AABB box;
    private final double fromX;
    private final double fromY;
    private final double fromZ;
    private final Predicate<BlockPos> allowed;

    public HomeSpace(Level level, Entity drone) {
        this(level, drone, pos -> true);
    }

    public HomeSpace(Level level, Entity drone, Predicate<BlockPos> allowed) {
        this.level = level;
        this.drone = drone;
        this.box = drone.getBoundingBox();
        this.fromX = drone.getX();
        this.fromY = drone.getY();
        this.fromZ = drone.getZ();
        this.allowed = allowed;
    }

    private AABB at(double x, double y, double z) {
        return this.box.move(x - this.fromX, y - this.fromY, z - this.fromZ);
    }

    private List<AABB> soft(AABB area) {
        List<AABB> out = new ArrayList<>();
        SoftGround.collect(this.level, area, out);
        return out;
    }

    @Override
    public boolean free(double x, double y, double z) {
        AABB at = this.at(x, y, z);
        return this.level.getWorldBorder().isWithinBounds(at) && this.allowed.test(BlockPos.containing(x, y, z))
                && this.level.noBlockCollision(this.drone, at) && this.soft(at.deflate(SOFT_EDGE)).isEmpty();
    }

    @Override
    public double fall(double x, double y, double z, double depth) {
        AABB at = this.at(x, y, z);
        AABB swept = at.expandTowards(0.0, -depth, 0.0);
        List<VoxelShape> shapes = new ArrayList<>();
        this.level.getBlockCollisions(this.drone, swept).forEach(shapes::add);
        for (AABB part : this.soft(swept)) {
            shapes.add(Shapes.create(part));
        }
        double moved = Shapes.collide(Direction.Axis.Y, at, shapes, -depth);
        return Math.max(0.0, -moved);
    }
}
