package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.compat.iris.client.IrisCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class FpvEyeGuard implements EyeClearance.Solids, Shapes.DoubleLineConsumer {
    public static final FpvEyeGuard INSTANCE = new FpvEyeGuard();
    private static final int MAX_BLOCKS = 4096;

    private final EyeClearance clearance = new EyeClearance();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final Vector3d requested = new Vector3d();
    private final Vector3d placed = new Vector3d();
    private @Nullable BlockGetter level;
    private @Nullable EyeClearance sink;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private float near = (float) EyeClearance.VANILLA_NEAR;
    private boolean active;
    private boolean moved;
    private boolean bypassed;

    FpvEyeGuard() {
    }

    void bypass(boolean off) {
        this.bypassed = off;
    }

    public boolean apply(BlockGetter level, Vector3d eye, Vector3dc anchor, Vector3fc forwards, Vector3fc up, Vector3fc left, float verticalFovDeg,
                         float aspect) {
        double least = IrisCompat.shaderPackActive(Util.getNanos()) ? EyeClearance.VANILLA_NEAR : EyeClearance.MIN_NEAR;
        return this.apply(level, eye, anchor, forwards, up, left, verticalFovDeg, aspect, least);
    }

    boolean apply(BlockGetter level, Vector3d eye, Vector3dc anchor, Vector3fc forwards, Vector3fc up, Vector3fc left, float verticalFovDeg,
                  float aspect, double least) {
        this.requested.set(eye);
        if (this.bypassed) {
            this.placed.set(eye);
            this.idle();
            return false;
        }
        double tanY = Math.tan(Math.toRadians(Mth.clamp(verticalFovDeg, 1.0F, 179.0F)) / 2.0);
        double tanX = tanY * (aspect > 1.0E-3F && Float.isFinite(aspect) ? aspect : 16.0F / 9.0F);
        this.clearance.view(forwards.x(), forwards.y(), forwards.z(), up.x(), up.y(), up.z(), left.x(), left.y(), left.z(), tanX, tanY);
        this.level = level;
        try {
            this.clearance.resolve(eye.x, eye.y, eye.z, anchor.x(), anchor.y(), anchor.z(), EyeClearance.VANILLA_NEAR, least, this);
        } finally {
            this.level = null;
            this.sink = null;
        }
        this.near = (float) this.clearance.near();
        this.active = true;
        this.moved = this.clearance.moved();
        eye.set(this.clearance.x(), this.clearance.y(), this.clearance.z());
        this.placed.set(eye);
        return this.moved;
    }

    public void idle() {
        this.active = false;
        this.moved = false;
    }

    public float near(float vanilla) {
        return this.active ? this.near : vanilla;
    }

    public boolean active() {
        return this.active;
    }

    public boolean moved() {
        return this.active && this.moved;
    }

    public Vector3dc requested() {
        return this.requested;
    }

    public Vector3dc placed() {
        return this.placed;
    }

    @Override
    public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, EyeClearance target) {
        BlockGetter world = this.level;
        if (world == null) {
            return;
        }
        int x0 = Mth.floor(minX) - 1;
        int y0 = Math.max(Mth.floor(minY) - 1, world.getMinY());
        int z0 = Mth.floor(minZ) - 1;
        int x1 = Mth.floor(maxX);
        int y1 = Math.min(Mth.floor(maxY), world.getMaxY());
        int z1 = Mth.floor(maxZ);
        long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (volume <= 0L || volume > MAX_BLOCKS) {
            return;
        }
        this.sink = target;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockState state = world.getBlockState(this.cursor.set(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    VoxelShape shape = state.getVisualShape(world, this.cursor, CollisionContext.empty());
                    if (shape.isEmpty()) {
                        continue;
                    }
                    this.offsetX = x;
                    this.offsetY = y;
                    this.offsetZ = z;
                    shape.forAllBoxes(this);
                }
            }
        }
    }

    @Override
    public void consume(double x1, double y1, double z1, double x2, double y2, double z2) {
        EyeClearance target = this.sink;
        if (target != null) {
            target.addBox(x1 + this.offsetX, y1 + this.offsetY, z1 + this.offsetZ, x2 + this.offsetX, y2 + this.offsetY, z2 + this.offsetZ);
        }
    }
}
