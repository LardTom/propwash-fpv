package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.compat.flashback.client.ReplayView;
import de.lardtom.propwash.compat.iris.client.IrisCompat;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.video.client.SpectateSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class QuadcopterRenderer extends EntityRenderer<QuadcopterEntity, DroneRenderState> {
    public static final float SHADOW_STRENGTH = 0.55F;

    private static int fpvShadowBodies;

    private final ItemModelResolver itemModelResolver;

    public QuadcopterRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.3F;
        this.shadowStrength = SHADOW_STRENGTH;
        try {
            DroneRenderResources.INSTANCE.onResourceManagerReload(context.getResourceManager());
        } catch (RuntimeException e) {
            Constants.LOG.error("Drohnen-Renderer: Laden der Render-Ressourcen fehlgeschlagen", e);
        }
    }

    @Override
    public DroneRenderState createRenderState() {
        return new DroneRenderState();
    }

    @Override
    public boolean shouldRender(QuadcopterEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
        boolean shadowPass = IrisCompat.renderingShadowPass();
        if (shadowPass && !DroneRenderConfig.get().shadow) {
            return false;
        }
        if (isOwnFpvDrone(entity)) {
            return shadowPass || FpvProps.enabled();
        }
        if (ReplayView.hides(entity, camX, camY, camZ, Minecraft.getInstance().gameRenderer.mainCamera().forwardVector())) {
            return false;
        }
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        double range = DroneRenderConfig.get().renderDistance * Entity.getViewScale();
        if (dx * dx + dy * dy + dz * dz > range * range) {
            return false;
        }
        AABB box = this.getBoundingBoxForCulling(entity, partialTicks).inflate(0.5);
        if (box.hasNaN() || box.getSize() == 0.0) {
            return true;
        }
        return culler.isVisible(box);
    }

    public static boolean isOwnFpvDrone(QuadcopterEntity entity) {
        PilotSession session = PilotSession.INSTANCE;
        return (session.isFpv() && session.drone() == entity) || (FpvView.spectating() && SpectateSession.INSTANCE.drone() == entity);
    }

    @Override
    protected AABB getBoundingBoxForCulling(QuadcopterEntity entity, float partialTicks) {
        AABB box = super.getBoundingBoxForCulling(entity, partialTicks);
        float radius = DroneVisuals.estimatedRadiusBlocks(entity.build());
        return box.inflate(radius, radius, radius);
    }

    @Override
    public void extractRenderState(QuadcopterEntity entity, DroneRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        DroneVisuals.fillFromEntity(state, entity, partialTicks);
        boolean own = isOwnFpvDrone(entity);
        state.shadowPass = IrisCompat.renderingShadowPass();
        state.hidden = own;
        state.fpvGeometry = null;
        state.fpvGuards = FpvGuardMesh.EMPTY;
        if (own && state.shadowPass && DroneVisuals.placeAtDisplayedPose(state, entity)) {
            state.hidden = false;
            fpvShadowBodies++;
        }
        ItemStack cargo = entity.cargoDisplay();
        state.hasCargo = !cargo.isEmpty() && !state.hidden;
        if (state.hasCargo) {
            this.itemModelResolver.updateForNonLiving(state.cargo, cargo, ItemDisplayContext.GROUND, entity);
        } else {
            state.cargo.clear();
        }
        if (own) {
            state.nameTag = null;
            state.shadowPieces.clear();
            state.leashStates = null;
            state.displayFireAnimation = false;
        }
        if (state.hidden) {
            FpvProps.prepare(state, entity);
        }
    }

    public static int fpvShadowBodies() {
        return fpvShadowBodies;
    }

    @Override
    protected float getShadowRadius(DroneRenderState state) {
        if (!DroneRenderConfig.get().shadow || state.hidden) {
            return 0.0F;
        }
        return Math.max(0.12F, Math.min(0.7F, state.visualRadius * 0.75F));
    }

    @Override
    public void submit(DroneRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.hidden) {
            if (!FpvPropPass.take(state)) {
                FpvProps.submit(state, poseStack, submitNodeCollector, camera);
            }
            return;
        }
        DroneVisuals.submit(state, poseStack, submitNodeCollector);
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
