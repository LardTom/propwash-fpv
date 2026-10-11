package de.lardtom.propwash.guide.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.client.render.DroneAssembly;
import de.lardtom.propwash.drone.client.render.DroneLayout;
import de.lardtom.propwash.drone.client.render.DroneRenderConfig;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.drone.client.render.DroneVisualScale;
import de.lardtom.propwash.drone.client.render.DroneVisuals;
import de.lardtom.propwash.drone.client.render.FallbackShapes;
import de.lardtom.propwash.drone.client.render.PropDamage;
import de.lardtom.propwash.drone.client.render.Shape;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.DronePart;
import de.lardtom.propwash.guide.scene.ScenePos;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import static de.lardtom.propwash.drone.client.render.DroneVisualScale.MM_TO_UNITS;

final class SceneDroneVisual {
    private static @Nullable RenderType solidType;
    private static @Nullable RenderType translucentType;

    private final DroneRenderState state = new DroneRenderState();
    private @Nullable DroneBuild build;
    private int generation = Integer.MIN_VALUE;
    private float configScale = Float.NaN;
    private float lift;
    private float stageScale = 1.0F;
    private float fit = 1.0F;

    void prepare(DroneBuild build, float stageScale, ActorState actor, float timeTicks) {
        this.stageScale = stageScale;
        DroneRenderResources resources = DroneRenderResources.INSTANCE;
        DroneRenderConfig config = DroneRenderConfig.get();
        if (!build.equals(this.build) || resources.generation() != this.generation || config.visualScale != this.configScale) {
            DroneVisuals.fillPreview(this.state, build);
            this.build = build;
            this.generation = resources.generation();
            this.configScale = config.visualScale;
            this.lift = resources.assembly(build).hull().halfHeight(this.state.pivot) * DroneVisualScale.blocksPerMillimetre(this.state.visualScale);
            if (!Float.isFinite(this.lift) || this.lift < 0.0F) {
                this.lift = 0.08F;
            }
            this.fit = DroneVisuals.sceneFit(build);
            if (!Float.isFinite(this.fit) || this.fit <= 0.0F) {
                this.fit = 1.0F;
            }
        }
        DroneRenderState s = this.state;
        s.orientation.identity()
                .rotateY((float) Math.PI - (float) Math.toRadians(actor.yaw()))
                .rotateX((float) Math.toRadians(-actor.pitch()))
                .rotateZ((float) Math.toRadians(actor.roll()));
        s.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        s.outlineColor = 0;
        s.hidden = false;
        s.isInvisible = false;
        s.armed = actor.armed();
        s.batteryMissing = !actor.part(DronePart.BATTERY).attached();
        DroneAssembly assembly = resources.assembly(build);
        DroneLayout layout = resources.layout(build.frame());
        float signed = actor.armed() ? Math.clamp(actor.throttle(), -1.0F, 1.0F) : 0.0F;
        float throttle = Math.abs(signed);
        float seconds = timeTicks / 20.0F;
        boolean leds = config.armLeds;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            if (actor.armed()) {
                float rate = DroneVisuals.IDLE_SPIN_RADIANS_PER_SECOND
                        + (DroneVisuals.FULL_SPIN_RADIANS_PER_SECOND - DroneVisuals.IDLE_SPIN_RADIANS_PER_SECOND) * throttle;
                float direction = (layout.counterClockwise(slot) ? 1.0F : -1.0F) * (signed < 0.0F ? -1.0F : 1.0F);
                s.rotorAngle[slot] = (float) ((0.35F + slot * 0.7F + direction * rate * seconds) % (Math.PI * 2.0));
            } else {
                s.rotorAngle[slot] = 0.35F + slot * 0.7F;
            }
            s.motorOutput[slot] = throttle;
            PropDamage damage = PropDamage.of(actor.propCondition(slot));
            boolean blur = actor.armed() && throttle > config.propBlurThreshold && damage != PropDamage.BROKEN;
            s.propBlur[slot] = blur;
            s.props[slot].set(blur ? assembly.blur(slot) : assembly.prop(slot, damage));
            int color = leds && actor.armed() ? ledColor(slot, throttle) : 0;
            s.ledColor[slot] = color;
            s.motorGlowColor[slot] = color == 0 ? 0 : motorGlowColor(color, throttle);
        }
    }

    float lift() {
        return this.lift * this.stageScale * this.fit;
    }

    float visualRadius() {
        return this.state.visualRadius * this.fit;
    }

    void submit(ActorState actor, PoseStack ps, SubmitNodeCollector collector) {
        ScenePos pos = actor.position();
        float presence = actor.presence();
        float scale = presence * this.stageScale * this.fit;
        ps.translate(pos.x(), pos.y() + this.lift * scale, pos.z());
        ps.scale(scale, scale, scale);
        if (actor.fullyAssembled()) {
            DroneVisuals.submit(this.state, ps, collector);
            return;
        }
        this.submitExploded(actor, ps, collector);
    }

    private void submitExploded(ActorState actor, PoseStack ps, SubmitNodeCollector collector) {
        DroneRenderState s = this.state;
        int light = LightCoordsUtil.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        ps.pushPose();
        ps.translate(0.0F, s.originY, 0.0F);
        ps.rotate(s.orientation);
        for (DronePart part : DronePart.values()) {
            ActorState.PartState partState = actor.part(part);
            for (int slot = 0; slot < part.slots(); slot++) {
                if (!partState.slotVisible(slot)) {
                    continue;
                }
                ScenePos offset = partState.offset(slot);
                if (part.perSlot()) {
                    Vector3fc position = s.motors[slot].position;
                    offset = new ScenePos(offset.x() * Math.signum(position.x()), offset.y(), offset.z() * Math.signum(position.z()));
                }
                ps.pushPose();
                ps.translate(offset.x(), offset.y(), offset.z());
                ps.scale(s.poseScale, s.poseScale, s.poseScale);
                ps.translate(-s.pivot.x * MM_TO_UNITS, -s.pivot.y * MM_TO_UNITS, -s.pivot.z * MM_TO_UNITS);
                switch (part) {
                    case FRAME -> {
                        submitSlot(s.frame, ps, collector, light, overlay);
                        if (!s.leds.isEmpty()) {
                            Shape leds = s.leds;
                            collector.submitCustomGeometry(ps, solidType(), (pose, buffer) -> leds.emit(pose, buffer, light, overlay, -1));
                        }
                    }
                    case STACK -> submitSlot(s.stack, ps, collector, light, overlay);
                    case BATTERY -> submitSlot(s.battery, ps, collector, light, overlay);
                    case VIDEO -> {
                        submitSlot(s.transmitter, ps, collector, light, overlay);
                        ps.pushPose();
                        translate(ps, s.video.position);
                        ps.rotate(Axis.XP, (float) Math.toRadians(s.cameraTiltDegrees));
                        submitAtOrigin(s.video, ps, collector, light, overlay);
                        ps.popPose();
                    }
                    case MOTORS -> submitSlot(s.motors[slot], ps, collector, light, overlay);
                    case PROPS -> {
                        DroneRenderState.PartSlot prop = s.props[slot];
                        ps.pushPose();
                        translate(ps, prop.position);
                        ps.rotate(Axis.YP, s.rotorAngle[slot]);
                        submitAtOrigin(prop, ps, collector, light, overlay);
                        ps.popPose();
                    }
                }
                ps.popPose();
            }
        }
        ps.popPose();
    }

    private static void submitSlot(DroneRenderState.PartSlot slot, PoseStack ps, SubmitNodeCollector collector, int light, int overlay) {
        if (!slot.useItem && slot.solid.isEmpty() && slot.translucent.isEmpty()) {
            return;
        }
        ps.pushPose();
        translate(ps, slot.position);
        submitAtOrigin(slot, ps, collector, light, overlay);
        ps.popPose();
    }

    private static void translate(PoseStack ps, Vector3fc mm) {
        ps.translate(mm.x() * MM_TO_UNITS, mm.y() * MM_TO_UNITS, mm.z() * MM_TO_UNITS);
    }

    private static void submitAtOrigin(DroneRenderState.PartSlot slot, PoseStack ps, SubmitNodeCollector collector, int light, int overlay) {
        if (slot.useItem) {
            slot.item.submit(ps, collector, light, overlay, 0);
        }
        Shape solid = slot.solid;
        if (!solid.isEmpty()) {
            collector.submitCustomGeometry(ps, solidType(), (pose, buffer) -> solid.emit(pose, buffer, light, overlay, -1));
        }
        Shape translucent = slot.translucent;
        if (!translucent.isEmpty()) {
            collector.submitCustomGeometry(ps, translucentType(), (pose, buffer) -> translucent.emit(pose, buffer, light, overlay, -1));
        }
    }

    static int ledColor(int slot, float throttle) {
        int base = DroneLayout.isFront(slot) ? FallbackShapes.LED_FRONT : FallbackShapes.LED_REAR;
        float intensity = 0.6F + 0.4F * Math.clamp(throttle, 0.0F, 1.0F);
        return (Math.round(255.0F * intensity) << 24) | (base & 0x00FFFFFF);
    }

    static int motorGlowColor(int ledColor, float throttle) {
        float alpha = (ledColor >>> 24) / 255.0F * (0.3F + 0.55F * Math.clamp(throttle, 0.0F, 1.0F));
        return (Math.round(alpha * 255.0F) << 24) | (ledColor & 0x00FFFFFF);
    }

    private static RenderType solidType() {
        if (solidType == null) {
            solidType = RenderTypes.entitySolid(DroneRenderResources.WHITE_TEXTURE);
        }
        return solidType;
    }

    private static RenderType translucentType() {
        if (translucentType == null) {
            translucentType = RenderTypes.entityTranslucent(DroneRenderResources.WHITE_TEXTURE);
        }
        return translucentType;
    }
}
