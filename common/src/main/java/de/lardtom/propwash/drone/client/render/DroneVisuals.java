package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintChannel;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.drone.led.DroneLeds;
import de.lardtom.propwash.drone.led.LedAnimator;
import de.lardtom.propwash.drone.led.LedSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.WeakHashMap;

import static de.lardtom.propwash.drone.client.render.DroneVisualScale.MM_TO_UNITS;

public final class DroneVisuals {
    public static final float PREVIEW_TILT_RADIANS = (float) Math.toRadians(25.0);
    public static final float IDLE_SPIN_RADIANS_PER_SECOND = (float) (Math.PI * 2.0 * 4.0);
    public static final float FULL_SPIN_RADIANS_PER_SECOND = (float) (Math.PI * 2.0 * 21.0);
    public static final float MAX_SCENE_RATIO = 1.6F;
    public static final float CARGO_SIZE_MM = 220.0F;
    public static final float CARGO_DROP_MM = 30.0F;

    private static final Map<QuadcopterEntity, SpinState> SYNTHETIC_SPIN = new WeakHashMap<>();
    private static final Map<PreviewKey, DroneRenderState> PREVIEWS = new HashMap<>();
    private static final Map<DroneBuild, DroneRenderState> LED_PREVIEWS = new HashMap<>();
    private static final LedAnimator.Input PREVIEW_INPUT = new LedAnimator.Input(false, true, 1.0F, 0.0);
    static final float HALO_ALPHA = 0.6F;
    private static int previewGeneration = Integer.MIN_VALUE;
    private static @Nullable DroneRenderConfig previewConfig;
    private static @Nullable RenderType solidType;
    private static @Nullable RenderType translucentType;
    private static @Nullable RenderType glowType;

    private DroneVisuals() {
    }

    public static void fillFromEntity(DroneRenderState s, QuadcopterEntity entity, float partialTick) {
        entity.renderAttitude(partialTick, s.orientation);
        if (!Float.isFinite(s.orientation.x) || !Float.isFinite(s.orientation.w) || s.orientation.lengthSquared() < 1.0E-6F) {
            s.orientation.identity();
        }
        s.orientation.normalize();
        s.batteryCharge = entity.batteryCharge();
        s.batteryMissing = !entity.hasBattery();
        s.wear = entity.wear();
        s.paint = entity.paint();
        boolean armed = entity.isArmed();
        float[] angles = s.rotorAngle;
        float[] outputs = s.motorOutput;
        boolean anyAngle = false;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            int motor = DroneLayout.motorForSlot(slot);
            float angle = entity.rotorAngle(motor, partialTick);
            float output = entity.motorOutput(motor);
            angles[slot] = Float.isFinite(angle) ? angle : 0.0F;
            outputs[slot] = Float.isFinite(output) ? Math.max(0.0F, Math.min(1.0F, output)) : 0.0F;
            anyAngle |= angles[slot] != 0.0F;
        }
        DroneBuild build = entity.build();
        if (armed && !anyAngle) {
            syntheticAngles(entity, resources().layout(build.frame()), outputs, angles);
        } else if (!armed) {
            SYNTHETIC_SPIN.remove(entity);
        }
        fill(s, build, armed, false, s.ageInTicks);
        applyStripColors(s, DroneLeds.settings(entity), DroneLeds.input(entity, s.ageInTicks / 20.0));
        s.cameraTiltDegrees = CameraMount.uptilt(build, entity.tune().values());
        placeInWorld(s);
        if (!QuadcopterRenderer.isOwnFpvDrone(entity)) {
            restOnGround(s, entity, Mth.lerp(partialTick, entity.yo, entity.getY()));
        }
    }

    public static void fillPreview(DroneRenderState s, DroneBuild build) {
        fillPreview(s, build, PartWear.PRISTINE);
    }

    public static void fillPreview(DroneRenderState s, DroneBuild build, PartWear wear) {
        fillPreview(s, build, wear, DronePaint.DEFAULT);
    }

    public static void fillPreview(DroneRenderState s, DroneBuild build, PartWear wear, DronePaint paint) {
        s.wear = wear == null ? PartWear.PRISTINE : wear;
        s.paint = paint == null ? DronePaint.DEFAULT : paint;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            s.rotorAngle[slot] = 0.35F + slot * 0.7F;
            s.motorOutput[slot] = 0.0F;
        }
        s.orientation.identity();
        s.batteryCharge = 1.0F;
        s.batteryMissing = false;
        fill(s, build, false, true, 0.0F);
        applyStripColors(s, LedSettings.DEFAULT, PREVIEW_INPUT);
        VisualHull hull = resources().assembly(build).hull();
        hull.center(s.pivot);
        s.originY = 0.0F;
        s.visualRadius = hull.radius(s.pivot) * DroneVisualScale.blocksPerMillimetre(s.visualScale);
        s.shadowPieces.clear();
        s.shadowRadius = 0.0F;
        s.outlineColor = 0;
        s.nameTag = null;
        s.scoreText = null;
        s.leashStates = null;
        s.displayFireAnimation = false;
        s.isInvisible = false;
        s.entityType = DroneModule.DRONE.get();
    }

    public static synchronized @Nullable DroneRenderState previewState(DroneBuild build) {
        return previewState(build, PartWear.PRISTINE);
    }

    public static synchronized @Nullable DroneRenderState previewState(DroneBuild build, PartWear wear) {
        return previewState(build, wear, DronePaint.DEFAULT);
    }

    public static synchronized @Nullable DroneRenderState previewState(DroneBuild build, PartWear wear, DronePaint paint) {
        return previewState(build, wear, paint, Float.NaN);
    }

    public static synchronized @Nullable DroneRenderState previewState(DroneBuild build, PartWear wear, DronePaint paint, float cameraTiltDegrees) {
        PartWear shown = visibleWear(wear);
        DronePaint painted = paint == null ? DronePaint.DEFAULT : paint;
        int generation = resources().generation();
        DroneRenderConfig config = DroneRenderConfig.get();
        if (generation != previewGeneration || config != previewConfig) {
            previewGeneration = generation;
            previewConfig = config;
            PREVIEWS.clear();
            LED_PREVIEWS.clear();
        }
        float tilt = Float.isFinite(cameraTiltDegrees) ? cameraTiltDegrees : Float.NaN;
        PreviewKey key = new PreviewKey(build, shown, painted, tilt);
        DroneRenderState state = PREVIEWS.get(key);
        if (state == null) {
            state = new DroneRenderState();
            fillPreview(state, build, shown, painted);
            if (Float.isFinite(tilt)) {
                state.cameraTiltDegrees = tilt;
            }
            if (PREVIEWS.size() > 32) {
                PREVIEWS.clear();
            }
            PREVIEWS.put(key, state);
        }
        return state;
    }

    public static PartWear visibleWear(@Nullable PartWear wear) {
        if (wear == null || wear.isNew()) {
            return PartWear.PRISTINE;
        }
        float[] props = new float[PartWear.POSITIONS];
        for (int slot = 0; slot < PartWear.POSITIONS; slot++) {
            props[slot] = PropDamage.of(wear.prop(slot)).representative();
        }
        return PartWear.PRISTINE.withProps(props);
    }

    public static PropDamage propDamage(DroneRenderState s, int slot) {
        return PropDamage.of(s.wear.prop(slot));
    }

    public static synchronized @Nullable DroneRenderState ledPreviewState(DroneBuild build, LedSettings settings, boolean armed, double seconds) {
        DroneRenderState base = previewState(build);
        if (base == null) {
            return null;
        }
        DroneRenderState state = LED_PREVIEWS.get(build);
        if (state == null || state.stripGlow != base.stripGlow || state.visualScale != base.visualScale) {
            state = new DroneRenderState();
            fillPreview(state, build);
            if (LED_PREVIEWS.size() > 8) {
                LED_PREVIEWS.clear();
            }
            LED_PREVIEWS.put(build, state);
        }
        applyStripColors(state, settings, new LedAnimator.Input(armed, true, 1.0F, seconds));
        return state;
    }

    static void applyStripColors(DroneRenderState s, LedSettings settings, LedAnimator.Input input) {
        int count = s.stripLeds;
        for (int i = 0; i < DroneRenderState.MAX_STRIP_LEDS; i++) {
            s.stripColor[i] = i < count ? LedAnimator.color(settings, i, count, input) : 0;
        }
    }

    public static float previewScale(DroneRenderState s, int width, int height) {
        VisualHull hull = resources().assembly(s.build).hull();
        float toBlocks = DroneVisualScale.blocksPerMillimetre(s.visualScale);
        float radius = Math.max(1.0F, hull.horizontalRadius(s.pivot)) * toBlocks;
        float halfHeight = Math.max(1.0F, hull.halfHeight(s.pivot)) * toBlocks;
        float projectedHalfHeight = radius * (float) Math.sin(PREVIEW_TILT_RADIANS) + halfHeight * (float) Math.cos(PREVIEW_TILT_RADIANS);
        float fit = Math.min(width * 0.5F / radius, height * 0.5F / projectedHalfHeight);
        return Math.max(1.0F, fit * 0.9F);
    }

    public static float scaleForPixelsPerMillimetre(DroneRenderState s, float pixelsPerMillimetre) {
        return pixelsPerMillimetre / DroneVisualScale.blocksPerMillimetre(s.visualScale);
    }

    static void fill(DroneRenderState s, DroneBuild build, boolean armed, boolean preview, float[] angles, float[] outputs, float ageTicks) {
        System.arraycopy(angles, 0, s.rotorAngle, 0, DroneLayout.SLOTS);
        System.arraycopy(outputs, 0, s.motorOutput, 0, DroneLayout.SLOTS);
        fill(s, build, armed, preview, ageTicks);
    }

    static void fill(DroneRenderState s, DroneBuild build, boolean armed, boolean preview, float ageTicks) {
        DroneAssembly assembly = resources().assembly(build);
        DroneRenderConfig config = DroneRenderConfig.get();
        s.build = build;
        s.armed = armed;
        s.preview = preview;
        s.hidden = false;
        s.shadowPass = false;
        s.visualScale = config.visualScale;
        s.poseScale = DroneVisualScale.poseScale(config.visualScale);
        s.cameraTiltDegrees = assembly.cameraTiltDegrees();

        s.frame.set(assembly.frame());
        s.stack.set(assembly.stack());
        s.battery.set(assembly.battery());
        s.video.set(assembly.video());
        s.transmitter.set(assembly.transmitter());
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            s.motors[slot].set(assembly.motor(slot));
            PropDamage damage = propDamage(s, slot);
            boolean blur = armed && s.motorOutput[slot] > config.propBlurThreshold && damage != PropDamage.BROKEN;
            s.propBlur[slot] = blur;
            DroneAssembly.Part prop = blur ? assembly.blur(slot) : assembly.prop(slot, damage);
            s.props[slot].set(prop);
            paint(s.motors[slot], assembly.motor(slot), s.paint, slot);
            paint(s.props[slot], prop, s.paint, slot);
            paintShape(s.motors[slot], FallbackShapes.motorColor(build.motor()), s.paint, PaintChannel.MOTOR, slot);
            paintShape(s.props[slot], FallbackShapes.propColor(build.prop()), s.paint, PaintChannel.PROP, slot);
        }
        paint(s.frame, assembly.frame(), s.paint, 0);
        paint(s.stack, assembly.stack(), s.paint, 0);
        paint(s.battery, assembly.battery(), s.paint, 0);
        paint(s.video, assembly.video(), s.paint, 0);
        paintShape(s.frame, FallbackShapes.frameBodyColor(build.frame()), s.paint, PaintChannel.FRAME, 0);
        if (build.frame().ducted && FallbackShapes.ductColor(build.frame()) != FallbackShapes.frameBodyColor(build.frame())) {
            paintShape(s.frame, FallbackShapes.ductColor(build.frame()), s.paint, PaintChannel.TPU, 0);
        }
        paintShape(s.battery, FallbackShapes.batteryColor(build.battery()), s.paint, PaintChannel.BATTERY, 0);
        paintShape(s.video, FallbackShapes.CAMERA, s.paint, PaintChannel.CAMERA, 0);

        for (AccessoryMount mount : AccessoryMount.values()) {
            DroneAssembly.Accessory accessory = assembly.accessory(mount);
            int i = mount.ordinal();
            s.accessoryType[i] = accessory.type();
            s.accessories[i].set(accessory.body());
            s.accessoryBases[i].set(accessory.base());
            s.accessoryTilt[i] = accessory.tiltDegrees();
            s.accessoryStretch[i] = accessory.stretchX();
            paint(s.accessories[i], accessory.body(), s.paint, 0);
            paint(s.accessoryBases[i], accessory.base(), s.paint, 0);
        }

        s.stripMount = -1;
        s.stripLeds = 0;
        s.stripGlow = DroneRenderState.NO_SHAPES;
        s.stripHalo = DroneRenderState.NO_SHAPES;
        for (AccessoryMount mount : AccessoryMount.values()) {
            AccessoryType type = s.accessoryType[mount.ordinal()];
            if (type != null && type.isLight() && s.stripMount < 0) {
                s.stripMount = mount.ordinal();
                s.stripGlow = FallbackShapes.stripGlows(type);
                s.stripHalo = FallbackShapes.stripHalos(type);
                s.stripLeds = s.stripGlow.length;
            }
        }

        boolean leds = config.armLeds;
        s.leds = leds ? assembly.leds() : Shape.EMPTY;
        s.ledShiftY = assembly.ledShiftY();
        s.motorGlow = leds ? assembly.motorGlow() : Shape.EMPTY;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            s.ledGlow[slot] = leds ? assembly.ledGlow(slot) : Shape.EMPTY;
            s.ledHalo[slot] = leds ? assembly.ledHalo(slot) : Shape.EMPTY;
            int color = leds && armed ? ledColor(slot, s.motorOutput[slot], s.batteryCharge, ageTicks) : 0;
            s.ledColor[slot] = color;
            s.motorGlowColor[slot] = color == 0 ? 0 : motorGlowColor(color, s.motorOutput[slot]);
        }
    }

    static void paint(DroneRenderState.PartSlot slot, DroneAssembly.Part part, DronePaint paint, int propPosition) {
        if (paint == null || paint.isDefault() || !slot.useItem || part.model() == null) {
            return;
        }
        int[] tints = paintTints(resources().paintGroups(part.model()), paint, propPosition);
        if (tints == null) {
            return;
        }
        ItemStackRenderState item = resources().paintedItem(part.model(), tints);
        if (item != null) {
            slot.item = item;
        }
    }

    static void paintShape(DroneRenderState.PartSlot slot, int baseRgb, DronePaint paint, PaintChannel channel, int propPosition) {
        if (paint == null || paint.isDefault() || slot.useItem) {
            return;
        }
        OptionalInt color = paint.color(channel, propPosition);
        if (color.isEmpty()) {
            return;
        }
        slot.solid = PaintedShapes.recolor(slot.solid, baseRgb, color.getAsInt());
        slot.translucent = PaintedShapes.recolor(slot.translucent, baseRgb, color.getAsInt());
    }

    public static int @Nullable [] paintTints(List<DroneRenderResources.TintGroup> groups, DronePaint paint, int propPosition) {
        if (groups.isEmpty()) {
            return null;
        }
        int[] tints = new int[groups.size()];
        boolean painted = false;
        for (int i = 0; i < tints.length; i++) {
            DroneRenderResources.TintGroup group = groups.get(i);
            PaintChannel channel = group.channel();
            OptionalInt color = paint.color(channel, propPosition);
            painted |= color.isPresent();
            tints[i] = channel.tint(color.isPresent() ? color.getAsInt() : group.base());
        }
        return painted ? tints : null;
    }

    static int motorGlowColor(int ledColor, float output) {
        float alpha = (ledColor >>> 24) / 255.0F * (0.3F + 0.55F * Math.max(0.0F, Math.min(1.0F, output)));
        return (Math.round(alpha * 255.0F) << 24) | (ledColor & 0x00FFFFFF);
    }

    public static boolean placeAtDisplayedPose(DroneRenderState s, QuadcopterEntity entity) {
        Vector3d com = new Vector3d();
        Quaternionf body = new Quaternionf();
        if (!FpvView.displayedBody(com, body)) {
            return false;
        }
        s.orientation.set(body);
        s.visualScale = 1.0F;
        s.poseScale = DroneVisualScale.poseScale(1.0F);
        placeInWorld(s);
        s.x = com.x;
        s.y = com.y - DronePhysique.of(s.build).comAboveFoot();
        s.z = com.z;
        restOnGround(s, entity, s.y);
        return true;
    }

    static void restOnGround(DroneRenderState s, QuadcopterEntity entity, double renderY) {
        if (!GroundContact.enabled()) {
            return;
        }
        double rise = renderY - entity.getY();
        float reach = GroundContact.reach(s.lowestAboveFoot, rise);
        if (reach <= 0.0F) {
            return;
        }
        float offset = GroundContact.offset(s.lowestAboveFoot, rise, entity.groundGap(reach));
        s.groundOffset = offset;
        s.originY += offset;
    }

    public static int[] propVariants(DroneRenderState s) {
        int[] variants = s.propVariants;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            variants[slot] = s.propBlur[slot] ? VisualHull.BLUR : propDamage(s, slot).ordinal();
        }
        return variants;
    }

    static void placeInWorld(DroneRenderState s) {
        DronePhysique physique = DronePhysique.of(s.build);
        VisualHull hull = resources().assembly(s.build).hull();
        s.pivot.set(0.0F, physique.comHeightMm(), 0.0F);
        float toBlocks = DroneVisualScale.blocksPerMillimetre(s.visualScale);
        float visualLowest = hull.lowestPoint(s.orientation, s.pivot, propVariants(s)) * toBlocks;
        float physicalLowest = physique.hasHull() ? physique.lowestPoint(s.orientation) : visualLowest / s.visualScale;
        float origin = physique.comAboveFoot() + physicalLowest - visualLowest;
        boolean placed = Float.isFinite(origin);
        s.originY = placed ? origin : physique.comAboveFoot();
        s.lowestAboveFoot = placed ? GroundContact.lowest(physique.comAboveFoot(), physicalLowest) : 0.0F;
        s.groundOffset = 0.0F;
        s.visualRadius = hull.radius(s.pivot) * toBlocks;
    }

    static int ledColor(int slot, float output, float batteryCharge, float ageTicks) {
        boolean lowBattery = batteryCharge < 0.2F;
        int base;
        float intensity;
        if (lowBattery) {
            boolean on = ((int) Math.floor(ageTicks / 5.0F) & 1) == 0;
            base = FallbackShapes.LED_WARNING;
            intensity = on ? 1.0F : 0.15F;
        } else {
            base = DroneLayout.isFront(slot) ? FallbackShapes.LED_FRONT : FallbackShapes.LED_REAR;
            intensity = 0.6F + 0.4F * Math.max(0.0F, Math.min(1.0F, output));
        }
        int alpha = Math.round(255.0F * intensity);
        return (alpha << 24) | (base & 0x00FFFFFF);
    }

    static void syntheticAngles(QuadcopterEntity entity, DroneLayout layout, float[] outputs, float[] angles) {
        SpinState spin = SYNTHETIC_SPIN.computeIfAbsent(entity, e -> new SpinState());
        long now = Util.getNanos();
        float dt = spin.lastNanos == 0L ? 0.0F : Math.max(0.0F, Math.min(0.1F, (now - spin.lastNanos) / 1.0E9F));
        spin.lastNanos = now;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            float rate = IDLE_SPIN_RADIANS_PER_SECOND + (FULL_SPIN_RADIANS_PER_SECOND - IDLE_SPIN_RADIANS_PER_SECOND) * outputs[slot];
            float direction = layout.counterClockwise(slot) ? 1.0F : -1.0F;
            spin.angles[slot] = (float) ((spin.angles[slot] + direction * rate * dt) % (Math.PI * 2.0));
            angles[slot] = spin.angles[slot];
        }
    }

    public static void submit(DroneRenderState s, PoseStack ps, SubmitNodeCollector collector) {
        if (s.hidden || s.isInvisible) {
            return;
        }
        int light = s.lightCoords;
        int overlay = OverlayTexture.NO_OVERLAY;
        int outline = s.outlineColor;
        ps.pushPose();
        ps.translate(0.0F, s.originY, 0.0F);
        ps.rotate(s.orientation);
        ps.scale(s.poseScale, s.poseScale, s.poseScale);
        ps.translate(-s.pivot.x * MM_TO_UNITS, -s.pivot.y * MM_TO_UNITS, -s.pivot.z * MM_TO_UNITS);

        submitSlot(s.frame, ps, collector, light, overlay, outline);
        submitSlot(s.stack, ps, collector, light, overlay, outline);
        if (!s.batteryMissing) {
            submitSlot(s.battery, ps, collector, light, overlay, outline);
        }
        submitSlot(s.transmitter, ps, collector, light, overlay, outline);

        ps.pushPose();
        translate(ps, s.video.position);
        ps.rotate(Axis.XP, (float) Math.toRadians(s.cameraTiltDegrees));
        submitAtOrigin(s.video, ps, collector, light, overlay, outline);
        ps.popPose();

        submitAccessories(s, ps, collector, light, overlay, outline);

        SubmitNodeCollector blurItems = BlurItemCollector.wrap(collector);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            submitSlot(s.motors[slot], ps, collector, light, overlay, outline);
            DroneRenderState.PartSlot prop = s.props[slot];
            ps.pushPose();
            translate(ps, prop.position);
            ps.rotate(Axis.YP, s.rotorAngle[slot]);
            submitAtOrigin(prop, ps, collector, s.propBlur[slot] ? blurItems : collector, light, overlay, outline);
            ps.popPose();
        }

        if (!s.leds.isEmpty()) {
            Shape leds = s.leds;
            collector.submitCustomGeometry(ps, solidType(), (pose, buffer) -> leds.emit(pose, buffer, light, overlay, -1));
        }
        ps.pushPose();
        ps.translate(0.0F, s.ledShiftY * MM_TO_UNITS, 0.0F);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            int color = s.ledColor[slot];
            Shape glow = s.ledGlow[slot];
            if (color != 0 && !glow.isEmpty()) {
                int core = opaque(color);
                collector.order(1).submitCustomGeometry(ps, glowType(),
                        (pose, buffer) -> glow.emit(pose, buffer, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, core));
            }
            Shape halo = s.ledHalo[slot];
            int haloColor = color == 0 || s.shadowPass ? 0 : haloColor(color);
            if (haloColor != 0 && !halo.isEmpty()) {
                collector.order(2).submitCustomGeometry(ps, AfterWaterRenderTypes.halo(),
                        (pose, buffer) -> halo.emit(pose, buffer, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, haloColor));
            }
        }
        ps.popPose();
        Shape motorGlow = s.motorGlow;
        if (!motorGlow.isEmpty() && !s.shadowPass) {
            for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                int color = s.motorGlowColor[slot];
                if (color == 0) {
                    continue;
                }
                ps.pushPose();
                translate(ps, s.motors[slot].position);
                collector.order(1).submitCustomGeometry(ps, glowType(),
                        (pose, buffer) -> motorGlow.emit(pose, buffer, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color));
                ps.popPose();
            }
        }
        ps.popPose();
    }

    static void submitAccessories(DroneRenderState s, PoseStack ps, SubmitNodeCollector collector, int light, int overlay, int outline) {
        for (int i = 0; i < DroneAssembly.MOUNTS; i++) {
            if (s.accessoryType[i] == null) {
                continue;
            }
            submitSlot(s.accessoryBases[i], ps, collector, light, overlay, outline);
            DroneRenderState.PartSlot body = s.accessories[i];
            ps.pushPose();
            translate(ps, body.position);
            ps.rotate(Axis.XP, (float) Math.toRadians(s.accessoryTilt[i]));
            ps.scale(s.accessoryStretch[i], 1.0F, 1.0F);
            submitAtOrigin(body, ps, collector, light, overlay, outline);
            if (i == s.stripMount) {
                submitStripGlow(s, ps, collector);
            }
            if (s.hasCargo && s.accessoryType[i].isGripper()) {
                submitCargo(s, s.accessoryType[i], ps, collector, light, overlay, outline);
            }
            ps.popPose();
        }
    }

    static void submitCargo(DroneRenderState s, AccessoryType gripper, PoseStack ps, SubmitNodeCollector collector, int light, int overlay,
                            int outline) {
        ps.pushPose();
        ps.translate(0.0F, -(float) (gripper.heightMm + CARGO_DROP_MM) * MM_TO_UNITS, 0.0F);
        float size = CARGO_SIZE_MM * MM_TO_UNITS;
        ps.scale(size, size, size);
        s.cargo.submit(ps, collector, light, overlay, outline);
        ps.popPose();
    }

    static void submitStripGlow(DroneRenderState s, PoseStack ps, SubmitNodeCollector collector) {
        int count = Math.min(s.stripLeds, Math.min(s.stripGlow.length, s.stripHalo.length));
        for (int i = 0; i < count; i++) {
            int color = s.stripColor[i];
            if (color == 0) {
                continue;
            }
            Shape glow = s.stripGlow[i];
            Shape halo = s.stripHalo[i];
            int haloColor = s.shadowPass ? 0 : haloColor(color);
            collector.order(1).submitCustomGeometry(ps, glowType(),
                    (pose, buffer) -> glow.emit(pose, buffer, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color));
            if (haloColor != 0) {
                collector.order(2).submitCustomGeometry(ps, AfterWaterRenderTypes.halo(),
                        (pose, buffer) -> halo.emit(pose, buffer, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, haloColor));
            }
        }
    }

    static int opaque(int ledColor) {
        float alpha = (ledColor >>> 24) / 255.0F;
        int r = Math.round(((ledColor >>> 16) & 0xFF) * alpha);
        int g = Math.round(((ledColor >>> 8) & 0xFF) * alpha);
        int b = Math.round((ledColor & 0xFF) * alpha);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int haloColor(int ledColor) {
        int alpha = Math.round((ledColor >>> 24) * HALO_ALPHA);
        return alpha <= 0 ? 0 : (alpha << 24) | (ledColor & 0x00FFFFFF);
    }

    private static void submitSlot(DroneRenderState.PartSlot slot, PoseStack ps, SubmitNodeCollector collector, int light, int overlay, int outline) {
        if (!slot.useItem && slot.solid.isEmpty() && slot.translucent.isEmpty()) {
            return;
        }
        ps.pushPose();
        translate(ps, slot.position);
        submitAtOrigin(slot, ps, collector, light, overlay, outline);
        ps.popPose();
    }

    private static void translate(PoseStack ps, Vector3fc mm) {
        ps.translate(mm.x() * MM_TO_UNITS, mm.y() * MM_TO_UNITS, mm.z() * MM_TO_UNITS);
    }

    static void submitPart(DroneRenderState.PartSlot slot, PoseStack ps, SubmitNodeCollector collector, int light, int outline) {
        submitAtOrigin(slot, ps, collector, light, OverlayTexture.NO_OVERLAY, outline);
    }

    private static void submitAtOrigin(DroneRenderState.PartSlot slot, PoseStack ps, SubmitNodeCollector collector, int light, int overlay, int outline) {
        submitAtOrigin(slot, ps, collector, collector, light, overlay, outline);
    }

    private static void submitAtOrigin(DroneRenderState.PartSlot slot, PoseStack ps, SubmitNodeCollector collector, SubmitNodeCollector items,
                                       int light, int overlay, int outline) {
        if (slot.useItem) {
            slot.item.submit(ps, items, light, overlay, outline);
        }
        Shape solid = slot.solid;
        if (!solid.isEmpty()) {
            collector.submitCustomGeometry(ps, solidType(), (pose, buffer) -> solid.emit(pose, buffer, light, overlay, -1));
        }
        Shape translucent = slot.translucent;
        if (!translucent.isEmpty()) {
            collector.submitCustomGeometry(ps, AfterWaterRenderTypes.blur(), (pose, buffer) -> translucent.emit(pose, buffer, light, overlay, -1));
        }
    }

    public static float sceneFit(DroneBuild build) {
        DroneRenderResources res = resources();
        VisualHull reference = res.assembly(DroneItemRenderer.REFERENCE).hull();
        VisualHull hull = res.assembly(build).hull();
        float referenceRadius = reference.radius(reference.center(new Vector3f()));
        float radius = hull.radius(hull.center(new Vector3f()));
        if (!(radius > 0.0F) || !(referenceRadius > 0.0F)) {
            return 1.0F;
        }
        return Math.min(1.0F, MAX_SCENE_RATIO * referenceRadius / radius);
    }

    public static float estimatedRadiusBlocks(DroneBuild build) {
        DroneLayout layout = resources().layout(build.frame());
        float mm = layout.motorOffset() + build.prop().diameterMm * 0.5F + 20.0F;
        return mm * DroneVisualScale.blocksPerMillimetre(DroneRenderConfig.get().visualScale);
    }

    static DroneRenderResources resources() {
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        if (!res.loaded()) {
            res.onResourceManagerReload(Minecraft.getInstance().getResourceManager());
        }
        return res;
    }

    static RenderType solidType() {
        if (solidType == null) {
            solidType = RenderTypes.entitySolid(DroneRenderResources.WHITE_TEXTURE);
        }
        return solidType;
    }

    static RenderType translucentType() {
        if (translucentType == null) {
            translucentType = RenderTypes.entityTranslucent(DroneRenderResources.WHITE_TEXTURE);
        }
        return translucentType;
    }

    static RenderType glowType() {
        if (glowType == null) {
            glowType = RenderTypes.eyes(DroneRenderResources.WHITE_TEXTURE);
        }
        return glowType;
    }

    private record PreviewKey(DroneBuild build, PartWear wear, DronePaint paint, float cameraTilt) {
    }

    private static final class SpinState {
        final float[] angles = new float[DroneLayout.SLOTS];
        long lastNanos;
    }
}
