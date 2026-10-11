package de.lardtom.propwash.guide.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.drone.client.render.RemoteGimbalRenderer;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.SceneKit;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.ui.client.theme.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SceneView {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private static final float HIGHLIGHT_PIXELS = 1.6F;
    private static final float ITEM_SPIN_DEGREES_PER_TICK = 4.0F;
    private static final float REMOTE_LIFT = 7.5F / 16.0F;
    public static final String BLOCK_PREFIX = "block@";

    private final Map<BlockSpec, BlockModelRenderState> blockModels = new HashMap<>();
    private final Map<String, SceneDroneVisual> drones = new HashMap<>();
    private final Map<String, ScenePilotVisual> pilots = new HashMap<>();
    private final Map<String, SceneGateVisual> gates = new HashMap<>();
    private final List<String> gateIds = new ArrayList<>();
    private final List<GateGeometry> gateGeometry = new ArrayList<>();
    private final Map<String, ItemStackRenderState> items = new HashMap<>();
    private @Nullable BlockModelResolver resolver;
    private int generation = Integer.MIN_VALUE;

    public SceneFrame build(SceneState state, SceneProjection projection) {
        this.checkReload();
        SceneFrame frame = new SceneFrame(projection);
        for (SceneState.BlockView block : state.blocks()) {
            BlockModelRenderState model = this.blockModel(block.block());
            if (model == null || block.scale() <= 0.0F) {
                continue;
            }
            float x = block.at().x();
            float y = block.at().y() + block.lift();
            float z = block.at().z();
            float scale = block.scale();
            frame.add((ps, collector) -> {
                ps.translate(x + 0.5F, y + 0.5F, z + 0.5F);
                ps.scale(scale, scale, scale);
                ps.translate(-0.5F, -0.5F, -0.5F);
                model.submit(ps, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            });
        }
        Set<String> seen = new HashSet<>();
        this.stackGates(state);
        for (ActorState actor : state.visibleActors()) {
            seen.add(actor.id());
            this.addActor(frame, actor, state.time());
        }
        this.drones.keySet().retainAll(seen);
        this.pilots.keySet().retainAll(seen);
        this.gates.keySet().retainAll(seen);
        this.items.keySet().retainAll(seen);
        float thickness = HIGHLIGHT_PIXELS / Math.max(1.0F, projection.pixelsPerBlock());
        for (SceneState.HighlightView highlight : state.highlights()) {
            float pulse = 0.75F + 0.25F * (float) Math.sin(highlight.age() * 0.25F);
            int alpha = Math.round(255.0F * highlight.alpha() * pulse);
            if (alpha <= 2) {
                continue;
            }
            int color = Palette.withAlpha(Palette.ACCENT, alpha);
            ScenePos min = highlight.min();
            ScenePos max = highlight.max();
            frame.add((ps, collector) -> HighlightGeometry.submit(ps, collector, min, max, thickness, color));
        }
        return frame;
    }

    private void stackGates(SceneState state) {
        this.gateIds.clear();
        this.gateGeometry.clear();
        for (ActorState actor : state.visibleActors()) {
            if (actor.spec() instanceof ActorSpec.Gate gate && actor.presence() > 0.999F) {
                ScenePos pos = actor.position();
                this.gateIds.add(actor.id());
                this.gateGeometry.add(SceneGateVisual.geometry(gate, actor, pos.x(), pos.y(), pos.z()));
            }
        }
    }

    private GateStacking.Base gateBase(String id) {
        int index = this.gateIds.indexOf(id);
        return index < 0 ? GateStacking.Base.BAR : GateStacking.base(this.gateGeometry, index);
    }

    private boolean gateCovered(String id) {
        int index = this.gateIds.indexOf(id);
        return index >= 0 && GateStacking.above(this.gateGeometry, index) >= 0;
    }

    private void addActor(SceneFrame frame, ActorState actor, float time) {
        switch (actor.spec()) {
            case ActorSpec.Drone drone -> {
                SceneDroneVisual visual = this.drones.computeIfAbsent(actor.id(), id -> new SceneDroneVisual());
                visual.prepare(drone.build(), drone.scale(), actor, time);
                frame.add((ps, collector) -> visual.submit(actor, ps, collector));
            }
            case ActorSpec.Pilot pilot -> {
                ScenePilotVisual visual = this.pilots.computeIfAbsent(actor.id(), id -> new ScenePilotVisual());
                visual.prepare(actor, time);
                frame.add((ps, collector) -> visual.submit(actor, ps, collector));
            }
            case ActorSpec.Gate gate -> {
                SceneGateVisual visual = this.gates.computeIfAbsent(actor.id(), id -> new SceneGateVisual());
                visual.prepare(gate, actor, this.gateBase(actor.id()), this.gateCovered(actor.id()), time);
                frame.add((ps, collector) -> visual.submit(actor, ps, collector));
            }
            case ActorSpec.Item item -> {
                ItemStack stack = SceneAssets.item(item.itemId(), actor.propCondition(0));
                if (stack.isEmpty()) {
                    return;
                }
                ItemStackRenderState render = this.items.computeIfAbsent(actor.id(), id -> new ItemStackRenderState());
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.getItemModelResolver().updateForTopItem(render, stack, ItemDisplayContext.GROUND, minecraft.level, null, 0);
                float yaw = actor.yaw() + (item.spin() ? time * ITEM_SPIN_DEGREES_PER_TICK : 0.0F);
                float scale = item.scale() * actor.presence();
                ScenePos pos = actor.position();
                float pitch = actor.pitch();
                float roll = actor.roll();
                frame.add((ps, collector) -> {
                    ps.translate(pos.x(), pos.y(), pos.z());
                    ps.rotate(Axis.YP, (float) Math.toRadians(yaw));
                    tilt(ps, pitch, roll);
                    ps.scale(scale, scale, scale);
                    render.submit(ps, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                });
            }
            case ActorSpec.Remote remote -> {
                ItemStack stack = SceneAssets.item(SceneKit.REMOTE);
                if (stack.isEmpty()) {
                    return;
                }
                ItemStackRenderState render = this.items.computeIfAbsent(actor.id(), id -> new ItemStackRenderState());
                Minecraft minecraft = Minecraft.getInstance();
                RemoteGimbalRenderer.Pose pose = new RemoteGimbalRenderer.Pose(actor.leftX(), actor.leftY(), actor.rightX(), actor.rightY());
                RemoteGimbalRenderer.extractPosed(stack, pose,
                        () -> minecraft.getItemModelResolver().updateForTopItem(render, stack, ItemDisplayContext.NONE, minecraft.level, null, 0));
                ScenePos pos = actor.position();
                float yaw = actor.yaw();
                float pitch = actor.pitch();
                float roll = actor.roll();
                float scale = remote.scale() * actor.presence();
                frame.add((ps, collector) -> {
                    ps.translate(pos.x(), pos.y(), pos.z());
                    ps.rotate(Axis.YP, (float) Math.toRadians(-yaw));
                    tilt(ps, pitch, roll);
                    ps.scale(scale, scale, scale);
                    ps.translate(0.0F, REMOTE_LIFT, 0.0F);
                    render.submit(ps, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                });
            }
            case ActorSpec.Block block -> {
                BlockModelRenderState model = this.blockModel(block.block());
                if (model == null) {
                    return;
                }
                ScenePos pos = actor.position();
                float yaw = actor.yaw();
                float pitch = actor.pitch();
                float roll = actor.roll();
                float scale = actor.presence();
                frame.add((ps, collector) -> {
                    ps.translate(pos.x(), pos.y(), pos.z());
                    ps.rotate(Axis.YP, (float) Math.toRadians(-yaw));
                    tilt(ps, pitch, roll);
                    ps.scale(scale, scale, scale);
                    ps.translate(-0.5F, 0.0F, -0.5F);
                    model.submit(ps, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                });
            }
        }
    }

    private static void tilt(PoseStack ps, float pitch, float roll) {
        if (pitch != 0.0F) {
            ps.rotate(Axis.XP, (float) Math.toRadians(-pitch));
        }
        if (roll != 0.0F) {
            ps.rotate(Axis.ZP, (float) Math.toRadians(-roll));
        }
    }

    public List<ScreenBox> screenBoxes(SceneState state, SceneProjection projection) {
        List<ScreenBox> boxes = new ArrayList<>();
        float[] point = new float[3];
        Vector3f corner = new Vector3f();
        for (ActorState actor : state.visibleActors()) {
            float presence = Math.max(0.0F, actor.presence());
            if (presence <= 0.05F) {
                continue;
            }
            ScenePos pos = actor.position();
            float cx = pos.x();
            float cz = pos.z();
            float yaw;
            float hx;
            float hz;
            float y0;
            float y1;
            switch (actor.spec()) {
                case ActorSpec.Drone drone -> {
                    SceneDroneVisual visual = this.drones.get(actor.id());
                    float scale = drone.scale() * presence;
                    float radius = (visual != null ? visual.visualRadius() : 0.25F) * scale;
                    float half = (visual != null ? visual.lift() / drone.scale() : 0.08F) * scale;
                    float tilt = (float) Math.toRadians(Math.min(90.0F, Math.max(Math.abs(actor.pitch()), Math.abs(actor.roll()))));
                    float vertical = Math.max(half * 1.2F, radius * (float) Math.sin(tilt));
                    yaw = 0.0F;
                    hx = radius;
                    hz = radius;
                    y0 = pos.y() + half - vertical;
                    y1 = pos.y() + half + vertical;
                }
                case ActorSpec.Gate gate when gate.isElement() || !gate.standing() || gate.customSize() || actor.pitch() != 0.0F
                        || actor.roll() != 0.0F -> {
                    float[] bounds = new float[6];
                    SceneGateVisual.geometry(gate, actor, pos.x(), pos.y(), pos.z())
                            .bounds((minX, minY, minZ, maxX, maxY, maxZ) -> {
                                bounds[0] = (float) minX;
                                bounds[1] = (float) minY;
                                bounds[2] = (float) minZ;
                                bounds[3] = (float) maxX;
                                bounds[4] = (float) maxY;
                                bounds[5] = (float) maxZ;
                            });
                    yaw = 0.0F;
                    cx = (bounds[0] + bounds[3]) * 0.5F;
                    cz = (bounds[2] + bounds[5]) * 0.5F;
                    hx = (bounds[3] - bounds[0]) * 0.5F * presence;
                    hz = (bounds[5] - bounds[2]) * 0.5F * presence;
                    y0 = bounds[1];
                    y1 = bounds[1] + (bounds[4] - bounds[1]) * presence;
                }
                case ActorSpec.Gate gate -> {
                    GateSize size = GateSize.byKey(gate.size());
                    yaw = -actor.yaw();
                    hx = size.width() * 0.5F * presence;
                    hz = size.depth() * 0.5F * presence;
                    y0 = pos.y();
                    y1 = pos.y() + size.height() * presence;
                }
                case ActorSpec.Pilot pilot -> {
                    yaw = 0.0F;
                    hx = 0.3F * presence;
                    hz = 0.3F * presence;
                    y0 = pos.y();
                    y1 = pos.y() + (actor.crouching() ? 1.5F : 1.8F) * presence;
                }
                case ActorSpec.Item item -> {
                    float half = 0.28F * item.scale() * presence;
                    yaw = 0.0F;
                    hx = half;
                    hz = half;
                    y0 = pos.y() + 0.12F * item.scale() * presence - half;
                    y1 = pos.y() + 0.12F * item.scale() * presence + half;
                }
                case ActorSpec.Remote remote -> {
                    float scale = remote.scale() * presence;
                    yaw = -actor.yaw();
                    hx = 0.42F * scale;
                    hz = 0.22F * scale;
                    y0 = pos.y();
                    y1 = pos.y() + 0.92F * scale;
                }
                case ActorSpec.Block block -> {
                    yaw = -actor.yaw();
                    hx = 0.5F * presence;
                    hz = 0.5F * presence;
                    y0 = pos.y();
                    y1 = pos.y() + presence;
                }
            }
            float cos = (float) Math.cos(Math.toRadians(yaw));
            float sin = (float) Math.sin(Math.toRadians(yaw));
            float sx0 = Float.MAX_VALUE;
            float sy0 = Float.MAX_VALUE;
            float sx1 = -Float.MAX_VALUE;
            float sy1 = -Float.MAX_VALUE;
            float wx0 = Float.MAX_VALUE;
            float wz0 = Float.MAX_VALUE;
            float wx1 = -Float.MAX_VALUE;
            float wz1 = -Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                float lx = (i & 1) == 0 ? -hx : hx;
                float lz = (i & 2) == 0 ? -hz : hz;
                float ly = (i & 4) == 0 ? y0 : y1;
                corner.set(cx + lx * cos + lz * sin, ly, cz - lx * sin + lz * cos);
                projection.project(corner.x, corner.y, corner.z, point);
                sx0 = Math.min(sx0, point[0]);
                sy0 = Math.min(sy0, point[1]);
                sx1 = Math.max(sx1, point[0]);
                sy1 = Math.max(sy1, point[1]);
                wx0 = Math.min(wx0, corner.x);
                wz0 = Math.min(wz0, corner.z);
                wx1 = Math.max(wx1, corner.x);
                wz1 = Math.max(wz1, corner.z);
            }
            boxes.add(new ScreenBox(actor.id(), sx0, sy0, sx1, sy1, new ScenePos(wx0, y0, wz0), new ScenePos(wx1, y1, wz1)));
        }
        for (SceneState.BlockView block : state.blocks()) {
            if (block.at().y() < 1 || block.scale() < 0.5F) {
                continue;
            }
            float bx = block.at().x();
            float by = block.at().y() + block.lift();
            float bz = block.at().z();
            float sx0 = Float.MAX_VALUE;
            float sy0 = Float.MAX_VALUE;
            float sx1 = -Float.MAX_VALUE;
            float sy1 = -Float.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                projection.project(bx + (i & 1), by + ((i >> 2) & 1), bz + ((i >> 1) & 1), point);
                sx0 = Math.min(sx0, point[0]);
                sy0 = Math.min(sy0, point[1]);
                sx1 = Math.max(sx1, point[0]);
                sy1 = Math.max(sy1, point[1]);
            }
            boxes.add(new ScreenBox(BLOCK_PREFIX + block.at().x() + "," + block.at().y() + "," + block.at().z(), sx0, sy0, sx1, sy1,
                    new ScenePos(bx, by, bz), new ScenePos(bx + 1.0F, by + 1.0F, bz + 1.0F)));
        }
        return boxes;
    }

    public float droneLift(String actorId) {
        SceneDroneVisual visual = this.drones.get(actorId);
        return visual != null ? visual.lift() : 0.0F;
    }

    private @Nullable BlockModelRenderState blockModel(BlockSpec spec) {
        BlockModelRenderState cached = this.blockModels.get(spec);
        if (cached != null) {
            return cached;
        }
        BlockState state = SceneAssets.block(spec);
        if (state == null) {
            return null;
        }
        if (this.resolver == null) {
            this.resolver = new BlockModelResolver(Minecraft.getInstance().getModelManager());
        }
        BlockModelRenderState model = new BlockModelRenderState();
        this.resolver.update(model, state, DISPLAY);
        this.blockModels.put(spec, model);
        return model;
    }

    private void checkReload() {
        int now = DroneRenderResources.INSTANCE.generation();
        if (now != this.generation) {
            this.generation = now;
            this.blockModels.clear();
            this.items.clear();
            this.drones.clear();
            this.gates.clear();
            this.resolver = null;
        }
    }
}
