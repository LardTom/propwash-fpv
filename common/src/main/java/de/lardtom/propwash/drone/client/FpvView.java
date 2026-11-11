package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.drone.DroneScale;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DroneVisuals;
import de.lardtom.propwash.freecam.client.FreecamSession;
import de.lardtom.propwash.video.client.BossDarkness;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class FpvView {
    private static final Vector3d SAMPLE_POS = new Vector3d();
    private static final Quaternionf SAMPLE_ROT = new Quaternionf();
    private static final Vector3f LENS = new Vector3f();
    private static final Vector3d ANCHOR = new Vector3d();
    private static final Vector3d EYE_ANCHOR = new Vector3d();
    private static final Quaternionf BODY = new Quaternionf();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final double INSET = 1.0E-4;
    private static boolean bodyKnown;
    private static volatile @Nullable Object pilotBody;

    private FpvView() {
    }

    public static final double BODY_HIDE_DISTANCE = 2.0;

    public static boolean active() {
        return FreecamSession.active() || PilotSession.INSTANCE.isFpv() || spectating();
    }

    public static boolean spectating() {
        return !PilotSession.INSTANCE.isActive() && SpectateSession.INSTANCE.drone() != null;
    }

    public static boolean hidesBody(Entity entity, double camX, double camY, double camZ) {
        if (!active() || !isPilotBody(entity)) {
            return false;
        }
        Vec3 center = entity.getBoundingBox().getCenter();
        return center.distanceToSqr(camX, camY, camZ) < BODY_HIDE_DISTANCE * BODY_HIDE_DISTANCE
                || entity.getBoundingBox().contains(camX, camY, camZ);
    }

    public static boolean isPilotBody(Object entity) {
        Object body = pilotBody;
        return body != null && body == entity;
    }

    public static void clearPilotScreenEffects(PlayerRenderState state) {
        state.blockOverlay = null;
        state.waterOverlay = null;
        state.isEyeInWater = false;
        state.isOnFire = false;
        state.isUnderWater = false;
        state.portalEffectIntensity = 0.0F;
        state.nauseaEffectIntensity = 0.0F;
        state.spinningEffectAngle = 0.0F;
        state.itemActivation = null;
    }

    public static void coverInsideBlock(Camera camera, PlayerRenderState state) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || CameraClip.active()) {
            return;
        }
        Vec3 eye = camera.position();
        BlockState blocking = eyeBlocking(level, eye.x, eye.y, eye.z);
        if (blocking == null && bodyKnown) {
            blocking = viewBlocking(level, EYE_ANCHOR.x, EYE_ANCHOR.y, EYE_ANCHOR.z);
        }
        if (blocking == null) {
            return;
        }
        TextureAtlasSprite sprite = minecraft.getModelManager().getBlockStateModelSet().getParticleMaterial(blocking).sprite();
        state.blockOverlay = new PlayerRenderState.BlockOverlay(sprite.atlasLocation(), sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
    }

    private static @Nullable BlockState viewBlocking(Level level, double x, double y, double z) {
        CURSOR.set(x, y, z);
        BlockState state = level.getBlockState(CURSOR);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
            return null;
        }
        AABB probe = new AABB(x - INSET, y - INSET, z - INSET, x + INSET, y + INSET, z + INSET);
        return state.isViewBlocking(level, CURSOR, probe) ? state : null;
    }

    private static @Nullable BlockState eyeBlocking(Level level, double x, double y, double z) {
        BlockState blocking = viewBlocking(level, x, y, z);
        if (blocking != null) {
            return blocking;
        }
        BlockState state = level.getBlockState(CURSOR);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE || !state.canOcclude()) {
            return null;
        }
        VoxelShape shape = state.getVisualShape(level, CURSOR, CollisionContext.empty());
        return inside(shape, x - CURSOR.getX(), y - CURSOR.getY(), z - CURSOR.getZ()) ? state : null;
    }

    private static boolean inside(VoxelShape shape, double x, double y, double z) {
        for (AABB box : shape.toAabbs()) {
            if (x > box.minX + INSET && x < box.maxX - INSET && y > box.minY + INSET && y < box.maxY - INSET && z > box.minZ + INSET
                    && z < box.maxZ - INSET) {
                return true;
            }
        }
        return false;
    }

    public static void clearPilotLightEffects(LightmapRenderState state) {
        state.nightVisionEffectIntensity = 0.0F;
        state.darknessEffectScale = 0.0F;
        state.brightness = Minecraft.getInstance().options.gamma().get().floatValue();
        BossDarkness.INSTANCE.applyLight(state, state.brightness);
    }

    static void setPilotBody(@Nullable Object body) {
        pilotBody = body;
    }

    public static void holdBody(@Nullable Object body) {
        if (!PilotSession.INSTANCE.isActive()) {
            pilotBody = body;
        }
    }

    public static Vector3dc anchor() {
        return EYE_ANCHOR;
    }

    public static boolean displayedBody(Vector3d outCom, Quaternionf outRotation) {
        if (!bodyKnown || !active()) {
            return false;
        }
        outCom.set(ANCHOR);
        outRotation.set(BODY);
        return true;
    }

    public static boolean cameraPose(Vector3d outPosition, Quaternionf outRotation) {
        if (FreecamSession.active()) {
            bodyKnown = false;
            FreecamSession.INSTANCE.position(ANCHOR);
            EYE_ANCHOR.set(ANCHOR);
            return FreecamSession.INSTANCE.cameraPose(outPosition, outRotation);
        }
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isFpv()) {
            bodyKnown = spectatePose(outPosition, outRotation);
            return bodyKnown;
        }
        if (!VideoState.INSTANCE.sampleDisplayed(session.physicsFrameNanos(), SAMPLE_POS, SAMPLE_ROT)) {
            session.comPosition(SAMPLE_POS);
            session.bodyOrientation(SAMPLE_ROT);
        }
        ANCHOR.set(SAMPLE_POS);
        BODY.set(SAMPLE_ROT);
        bodyKnown = true;
        LENS.set((float) session.lensRightOffset(), (float) session.lensUpOffset(), (float) -session.lensForwardOffset());
        SAMPLE_ROT.transform(LENS);
        QuadcopterEntity flown = session.drone();
        DroneScale.eye(SAMPLE_POS, LENS, session.comOffset(), DroneScale.of(flown), outPosition);
        toDrawnCamera(flown, session.comOffset(), outPosition);
        outRotation.set(SAMPLE_ROT).rotateX((float) Math.toRadians(session.cameraUptiltDeg()));
        if (flown == null || !AddonCamera.apply(flown, SAMPLE_ROT, session.cameraUptiltDeg(), outRotation, false)) {
            FpvShake.INSTANCE.apply(VideoState.INSTANCE.lastSampleNanos(), outRotation);
        }
        return true;
    }

    private static void toDrawnCamera(@Nullable QuadcopterEntity drone, double comAboveFoot, Vector3d eye) {
        double lift = drone == null ? 0.0 : DroneVisuals.displayLift(drone, BODY, ANCHOR.y);
        eye.y += lift;
        EYE_ANCHOR.set(ANCHOR.x, ANCHOR.y + DroneScale.raise(comAboveFoot, DroneScale.of(drone)) + lift, ANCHOR.z);
    }

    private static boolean spectatePose(Vector3d outPosition, Quaternionf outRotation) {
        if (!spectating()) {
            return false;
        }
        SpectateSession spectate = SpectateSession.INSTANCE;
        QuadcopterEntity drone = spectate.drone();
        if (drone == null) {
            return false;
        }
        if (!VideoState.INSTANCE.sampleDisplayed(spectate.frameNanos(), SAMPLE_POS, SAMPLE_ROT)) {
            SAMPLE_POS.set(drone.getX(), drone.getY() + spectate.comOffset(drone), drone.getZ());
            SAMPLE_ROT.set(drone.orientation());
        }
        ANCHOR.set(SAMPLE_POS);
        BODY.set(SAMPLE_ROT);
        LENS.set((float) spectate.lensRightOffset(drone), (float) spectate.lensUpOffset(drone), (float) -spectate.lensForwardOffset(drone));
        SAMPLE_ROT.transform(LENS);
        DroneScale.eye(SAMPLE_POS, LENS, spectate.comOffset(drone), drone.scale(), outPosition);
        toDrawnCamera(drone, spectate.comOffset(drone), outPosition);
        outRotation.set(SAMPLE_ROT).rotateX((float) Math.toRadians(spectate.cameraUptiltDeg(drone)));
        AddonCamera.apply(drone, SAMPLE_ROT, (float) spectate.cameraUptiltDeg(drone), outRotation, true);
        return true;
    }
}
