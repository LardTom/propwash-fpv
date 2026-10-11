package de.lardtom.propwash.interact.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public final class ShardParticle extends TerrainParticle {
    public static final int TEXELS = 16;
    public static final int SPAN = 2;
    private final float u;
    private final float v;
    private final float spin;

    ShardParticle(ClientLevel level, double x, double y, double z, BlockState state, BlockPos pos, RandomSource random) {
        super(level, x, y, z, 0.0, 0.0, 0.0, state, pos);
        int along = random.nextInt(TEXELS - SPAN + 1);
        int edge = random.nextBoolean() ? 0 : TEXELS - SPAN;
        boolean upright = random.nextBoolean();
        this.u = (float) (upright ? edge : along) / TEXELS;
        this.v = (float) (upright ? along : edge) / TEXELS;
        this.rCol = Math.min(1.0F, this.rCol / 0.6F);
        this.gCol = Math.min(1.0F, this.gCol / 0.6F);
        this.bCol = Math.min(1.0F, this.bCol / 0.6F);
        this.roll = random.nextFloat() * (float) (2.0 * Math.PI);
        this.oRoll = this.roll;
        this.spin = (random.nextFloat() - 0.5F) * 1.2F;
    }

    void launch(double vx, double vy, double vz, double ticksAhead) {
        this.setParticleSpeed(vx, vy, vz);
        double nx = this.x + vx * ticksAhead;
        double ny = this.y + vy * ticksAhead;
        double nz = this.z + vz * ticksAhead;
        this.setPos(nx, ny, nz);
        this.xo = nx - vx;
        this.yo = ny - vy;
        this.zo = nz - vz;
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        if (!this.onGround) {
            this.roll += this.spin;
        }
    }

    @Override
    protected float getU0() {
        return this.sprite.getU(this.u + (float) SPAN / TEXELS);
    }

    @Override
    protected float getU1() {
        return this.sprite.getU(this.u);
    }

    @Override
    protected float getV0() {
        return this.sprite.getV(this.v);
    }

    @Override
    protected float getV1() {
        return this.sprite.getV(this.v + (float) SPAN / TEXELS);
    }
}
