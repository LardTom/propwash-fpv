package de.lardtom.propwash.drone.client.dust;

import de.lardtom.propwash.drone.dust.DustModel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;

import java.util.Optional;

public final class WashParticle extends SingleQuadParticle {
    public static final ParticleLimit LIMIT = new ParticleLimit(DustModel.ENGINE_LIMIT);
    public static final int SPRITE_COUNT = 8;
    private static final SpriteId[] SPRITES = new SpriteId[SPRITE_COUNT];
    private static final Optional<ParticleLimit> LIMITED = Optional.of(LIMIT);

    static {
        for (int i = 0; i < SPRITE_COUNT; i++) {
            SPRITES[i] = new SpriteId(TextureAtlas.LOCATION_PARTICLES, Identifier.withDefaultNamespace("generic_" + i));
        }
    }

    public enum Style { DUST, SPRAY }

    private final Style style;
    private final float peakAlpha;
    private final int startSprite;
    private final long spawnTick;
    private int spriteIndex;

    WashParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, Style style, int rgb,
                 float peakAlpha, float size, int lifetime, int startSprite, long spawnTick) {
        super(level, x, y, z, sprite(startSprite));
        this.style = style;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = Math.max(2, lifetime);
        this.quadSize = size;
        this.peakAlpha = peakAlpha;
        this.startSprite = clampSprite(startSprite);
        this.spriteIndex = this.startSprite;
        this.spawnTick = spawnTick;
        this.hasPhysics = true;
        this.setSize(0.04F, 0.04F);
        if (style == Style.SPRAY) {
            this.gravity = 0.35F;
            this.friction = 0.92F;
        } else {
            this.gravity = -0.02F;
            this.friction = 0.88F;
        }
        this.setColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
        this.alpha = 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        if (this.style == Style.SPRAY && this.yd < 0.0 && this.belowWaterSurface()) {
            this.remove();
            return;
        }
        int index = DustModel.spriteFor(this.startSprite, (double) this.age / this.lifetime, SPRITE_COUNT);
        if (index != this.spriteIndex) {
            this.spriteIndex = index;
            this.setSprite(sprite(index));
        }
    }

    @Override
    public void extract(QuadParticleRenderState particleTypeRenderState, Camera camera, float partialTickTime) {
        this.alpha = DustModel.alpha((this.age + partialTickTime) / this.lifetime, this.peakAlpha);
        if (this.alpha < 0.004F) {
            return;
        }
        super.extract(particleTypeRenderState, camera, partialTickTime);
    }

    @Override
    public float getQuadSize(float partialTickTime) {
        return this.quadSize * DustModel.growth((this.age + partialTickTime) / this.lifetime);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public Optional<ParticleLimit> getParticleLimit() {
        return LIMITED;
    }

    public Style style() {
        return this.style;
    }

    public long spawnTick() {
        return this.spawnTick;
    }

    public boolean textured() {
        return !MissingTextureAtlasSprite.getLocation().equals(this.sprite.contents().name());
    }

    private boolean belowWaterSurface() {
        BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
        FluidState fluid = this.level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && this.y < pos.getY() + fluid.getHeight(this.level, pos);
    }

    private static int clampSprite(int index) {
        return Math.clamp(index, 1, SPRITE_COUNT - 1);
    }

    private static TextureAtlasSprite sprite(int index) {
        return Minecraft.getInstance().getAtlasManager().get(SPRITES[clampSprite(index)]);
    }
}
