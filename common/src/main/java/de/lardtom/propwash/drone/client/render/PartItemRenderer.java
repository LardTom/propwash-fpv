package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.client.ItemSizeRule;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartRef;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class PartItemRenderer implements SpecialModelRenderer<PartItemRenderer.Icon> {
    public static final Identifier TYPE = Constants.id("part");
    public static final Identifier BASE_MODEL = Constants.id("item/part_base");
    public static final float GUI_PITCH_DEGREES = 45.0F;
    public static final float GUI_YAW_DEGREES = 225.0F;
    public static final float TURNED_YAW_DEGREES = -20.0F;
    public static final float HAND_PITCH_DEGREES = 10.0F;
    public static final float HAND_YAW_DEGREES = 45.0F;
    public static final float GROUND_BASE_BLOCKS = 0.44F;
    public static final float GROUND_BOX_BULK = ItemSizeRule.GROUND_BULK / GROUND_BASE_BLOCKS;
    private static final int MAX_CACHED = 256;
    private static final Map<Icon, Fitted> FITTED = new HashMap<>();
    private static final Map<Icon, Fitted> WALL_FITTED = new HashMap<>();
    private static final Map<Icon, Fitted> BOX_FITTED = new HashMap<>();
    private static final Map<Icon, Fitted> GROUND_FITTED = new HashMap<>();
    private static final Map<Icon, Fitted> HAND_FITTED = new HashMap<>();
    private static int generation = Integer.MIN_VALUE;
    private static boolean warned;
    static @Nullable Icon lastSubmitted;
    static boolean lastWall;
    static boolean lastBox;
    static boolean lastGround;
    static boolean lastHand;
    static int submitted;

    private final Optional<Identifier> fixedPart;
    private final boolean wall;
    private final boolean box;
    private final boolean ground;
    private final boolean hand;

    public PartItemRenderer(Optional<Identifier> fixedPart, boolean wall, boolean box, boolean ground, boolean hand) {
        this.fixedPart = fixedPart;
        this.wall = wall;
        this.ground = ground && !wall;
        this.box = box && !wall && !this.ground;
        this.hand = hand && !wall && !this.ground && !this.box;
    }

    public record Icon(Identifier part, PropDamage damage) {
        public Icon {
            damage = damage == null ? PropDamage.NONE : damage;
        }
    }

    private enum Fit { GUI, WALL, BOX, GROUND, HAND }

    record Fitted(@Nullable ItemStackRenderState item, Shape solid, Shape translucent, Vector3fc pivot, float scale, float yawDegrees) {
        static final Fitted EMPTY = new Fitted(null, Shape.EMPTY, Shape.EMPTY, new Vector3f(), 1.0F, 0.0F);

        boolean isEmpty() {
            return this.item == null && this.solid.isEmpty() && this.translucent.isEmpty();
        }
    }

    @Override
    public void submit(@Nullable Icon argument, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
                       boolean hasFoil, int outlineColor) {
        if (argument == null) {
            return;
        }
        try {
            Fitted fitted = this.wall ? wallFitted(argument) : this.ground ? groundFitted(argument) : this.box ? boxFitted(argument)
                    : this.hand ? handFitted(argument) : fitted(argument);
            if (fitted.isEmpty()) {
                return;
            }
            poseStack.pushPose();
            try {
                poseStack.translate(0.5F, 0.5F, 0.5F);
                if (fitted.yawDegrees() != 0.0F) {
                    poseStack.rotateDegrees(Axis.YP, fitted.yawDegrees());
                }
                poseStack.scale(fitted.scale(), fitted.scale(), fitted.scale());
                poseStack.translate(-fitted.pivot().x(), -fitted.pivot().y(), -fitted.pivot().z());
                ItemStackRenderState item = fitted.item();
                if (item != null) {
                    item.submit(poseStack, collector, lightCoords, overlayCoords, outlineColor);
                }
                Shape solid = fitted.solid();
                if (!solid.isEmpty()) {
                    collector.submitCustomGeometry(poseStack, DroneVisuals.solidType(),
                            (pose, buffer) -> solid.emit(pose, buffer, lightCoords, overlayCoords, -1));
                }
                Shape translucent = fitted.translucent();
                if (!translucent.isEmpty()) {
                    collector.submitCustomGeometry(poseStack, DroneVisuals.translucentType(),
                            (pose, buffer) -> translucent.emit(pose, buffer, lightCoords, overlayCoords, -1));
                }
            } finally {
                poseStack.popPose();
            }
            lastSubmitted = argument;
            lastWall = this.wall;
            lastBox = this.box;
            lastGround = this.ground;
            lastHand = this.hand;
            submitted++;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                Constants.LOG.error("Bauteil-Symbol {} konnte nicht gezeichnet werden", argument.part(), e);
            }
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float half = this.wall ? DroneItemRenderer.WALL_DIAMETER * 0.5F : 0.5F;
        float y0 = this.wall ? 0.5F : 0.0F;
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new Vector3f((corner & 1) == 0 ? 0.5F - half : 0.5F + half, (corner & 2) == 0 ? y0 : 1.0F,
                    (corner & 4) == 0 ? 0.5F - half : 0.5F + half));
        }
    }

    @Override
    public @Nullable Icon extractArgument(ItemStack stack) {
        Identifier id = this.fixedPart.orElseGet(() -> partId(stack));
        if (id == null) {
            return null;
        }
        return new Icon(id, PropDamageProperty.stage(stack));
    }

    public static @Nullable Identifier partId(ItemStack stack) {
        PartRef ref = stack.get(CoreComponents.PART.get());
        if (ref != null) {
            Optional<PartInfo> part = ref.resolve();
            if (part.isPresent()) {
                return part.get().id();
            }
            Identifier byKey = PartIndex.idOfKey(ref.key());
            if (byKey != null) {
                return byKey;
            }
        }
        Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return PartIndex.registeredById(item).isPresent() ? item : null;
    }

    static synchronized Fitted fitted(Icon icon) {
        return cached(FITTED, icon, Fit.GUI);
    }

    static synchronized Fitted wallFitted(Icon icon) {
        return cached(WALL_FITTED, icon, Fit.WALL);
    }

    static synchronized Fitted boxFitted(Icon icon) {
        return cached(BOX_FITTED, icon, Fit.BOX);
    }

    static synchronized Fitted groundFitted(Icon icon) {
        return cached(GROUND_FITTED, icon, Fit.GROUND);
    }

    static synchronized Fitted handFitted(Icon icon) {
        return cached(HAND_FITTED, icon, Fit.HAND);
    }

    private static Fitted cached(Map<Icon, Fitted> cache, Icon icon, Fit mode) {
        DroneRenderResources res = DroneVisuals.resources();
        if (res.generation() != generation) {
            generation = res.generation();
            FITTED.clear();
            WALL_FITTED.clear();
            BOX_FITTED.clear();
            GROUND_FITTED.clear();
            HAND_FITTED.clear();
        }
        Fitted fitted = cache.get(icon);
        if (fitted == null) {
            fitted = PartIndex.registeredById(icon.part())
                    .map(part -> fit(part, icon.damage(), res, mode))
                    .orElse(Fitted.EMPTY);
            if (cache.size() >= MAX_CACHED) {
                cache.clear();
            }
            cache.put(icon, fitted);
        }
        return fitted;
    }

    static Fitted fit(PartInfo part, PropDamage damage, DroneRenderResources res) {
        return fit(part, damage, res, Fit.GUI);
    }

    static Fitted fitWall(PartInfo part, PropDamage damage, DroneRenderResources res) {
        return fit(part, damage, res, Fit.WALL);
    }

    private static Fitted fit(PartInfo part, PropDamage damage, DroneRenderResources res, Fit mode) {
        PartModel model = part instanceof PropType prop ? PartModel.prop(prop, false, damage) : PartModel.of(part);
        ItemStackRenderState item = res.item(model);
        if (item == null && part instanceof PropType prop) {
            item = res.item(PartModel.of(prop));
        }
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        Shape solid = Shape.EMPTY;
        if (item != null) {
            AABB box = item.getModelBoundingBox();
            min.set((float) box.minX, (float) box.minY, (float) box.minZ);
            max.set((float) box.maxX, (float) box.maxY, (float) box.maxZ);
        } else {
            solid = shapeOf(part, res);
            if (solid.isEmpty()) {
                return Fitted.EMPTY;
            }
            min.set(solid.min());
            max.set(solid.max());
        }
        Vector3f center = new Vector3f(min).add(max).mul(0.5F);
        if (mode == Fit.WALL) {
            float footprint = wallFootprint(min, max);
            float scale = footprint > 1.0E-4F && Float.isFinite(footprint) ? DroneItemRenderer.WALL_DIAMETER * 0.5F / footprint : 1.0F;
            return new Fitted(item, solid, Shape.EMPTY, new Vector3f(center.x, min.y, center.z), scale, 0.0F);
        }
        if (mode == Fit.BOX || mode == Fit.GROUND) {
            float edge = Math.max(max.x() - min.x(), Math.max(max.y() - min.y(), max.z() - min.z()));
            float scale = edge > 1.0E-4F && Float.isFinite(edge) ? 1.0F / edge : 1.0F;
            if (mode == Fit.GROUND) {
                float bulk = bulk(min, max);
                if (bulk > 1.0E-4F && Float.isFinite(bulk)) {
                    scale = Math.min(scale, GROUND_BOX_BULK / bulk);
                }
            }
            return new Fitted(item, solid, Shape.EMPTY, center, scale, 0.0F);
        }
        float yaw = yawOf(part);
        if (mode == Fit.HAND) {
            boolean round = part instanceof MotorType || part instanceof PropType;
            float hand = round ? roundViewExtent(min, max, HAND_PITCH_DEGREES) : Math.max(viewExtent(min, max, HAND_PITCH_DEGREES, HAND_YAW_DEGREES + yaw),
                    viewExtent(min, max, HAND_PITCH_DEGREES, -HAND_YAW_DEGREES + yaw));
            float scale = hand > 1.0E-4F && Float.isFinite(hand) ? ItemSizeRule.handFactor(lengthMm(min, max)) / hand : 1.0F;
            return new Fitted(item, solid, Shape.EMPTY, center, scale, yaw);
        }
        float extent = guiExtent(min, max, yaw);
        float scale = extent > 1.0E-4F && Float.isFinite(extent) ? 1.0F / extent : 1.0F;
        return new Fitted(item, solid, Shape.EMPTY, center, scale, yaw);
    }

    public static float lengthMm(Vector3fc min, Vector3fc max) {
        float edge = Math.max(max.x() - min.x(), Math.max(max.y() - min.y(), max.z() - min.z()));
        return edge * DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
    }

    public static float lengthMm(Identifier part) {
        Optional<PartInfo> info = PartIndex.registeredById(part);
        if (info.isEmpty()) {
            return 0.0F;
        }
        DroneRenderResources res = DroneVisuals.resources();
        PartModel model = info.get() instanceof PropType prop ? PartModel.prop(prop, false, PropDamage.NONE) : PartModel.of(info.get());
        ItemStackRenderState item = res.item(model);
        if (item != null) {
            AABB box = item.getModelBoundingBox();
            return lengthMm(new Vector3f((float) box.minX, (float) box.minY, (float) box.minZ),
                    new Vector3f((float) box.maxX, (float) box.maxY, (float) box.maxZ));
        }
        Shape solid = shapeOf(info.get(), res);
        return solid.isEmpty() ? 0.0F : lengthMm(solid.min(), solid.max());
    }

    public static float bulk(Vector3fc min, Vector3fc max) {
        double volume = (double) (max.x() - min.x()) * (max.y() - min.y()) * (max.z() - min.z());
        return (float) Math.cbrt(Math.max(0.0, volume));
    }

    public static float wallFootprint(Vector3fc min, Vector3fc max) {
        float width = max.x() - min.x();
        float length = max.z() - min.z();
        return 0.5F * (float) Math.sqrt(width * width + length * length);
    }

    static float yawOf(PartInfo part) {
        return part instanceof FrameType || part instanceof VideoType ? TURNED_YAW_DEGREES : 0.0F;
    }

    static Shape shapeOf(PartInfo part, DroneRenderResources res) {
        return switch (part) {
            case FrameType frame -> FallbackShapes.frameIcon(frame, res.layout(frame));
            case StackType stack -> FallbackShapes.stack(stack);
            case MotorType motor -> FallbackShapes.motor(motor);
            case PropType prop -> FallbackShapes.propIcon(prop);
            case VideoType video -> FallbackShapes.camera(video);
            case BatteryType battery -> FallbackShapes.battery(battery);
            case AccessoryType accessory -> FallbackShapes.accessory(accessory);
        };
    }

    public static float guiExtent(Vector3fc min, Vector3fc max) {
        return guiExtent(min, max, 0.0F);
    }

    public static float guiExtent(Vector3fc min, Vector3fc max, float yawDegrees) {
        return viewExtent(min, max, GUI_PITCH_DEGREES, GUI_YAW_DEGREES + yawDegrees);
    }

    public static float viewExtent(Vector3fc min, Vector3fc max, float pitchDegrees, float yawDegrees) {
        Quaternionf rotation = new Quaternionf().rotationXYZ((float) Math.toRadians(pitchDegrees), (float) Math.toRadians(yawDegrees), 0.0F);
        float cx = (min.x() + max.x()) * 0.5F;
        float cy = (min.y() + max.y()) * 0.5F;
        float cz = (min.z() + max.z()) * 0.5F;
        float x0 = Float.POSITIVE_INFINITY;
        float x1 = Float.NEGATIVE_INFINITY;
        float y0 = Float.POSITIVE_INFINITY;
        float y1 = Float.NEGATIVE_INFINITY;
        Vector3f corner = new Vector3f();
        for (int c = 0; c < 8; c++) {
            corner.set(((c & 1) == 0 ? min.x() : max.x()) - cx, ((c & 2) == 0 ? min.y() : max.y()) - cy,
                    ((c & 4) == 0 ? min.z() : max.z()) - cz);
            rotation.transform(corner);
            x0 = Math.min(x0, corner.x);
            x1 = Math.max(x1, corner.x);
            y0 = Math.min(y0, corner.y);
            y1 = Math.max(y1, corner.y);
        }
        return Math.max(x1 - x0, y1 - y0);
    }

    public static float roundViewExtent(Vector3fc min, Vector3fc max, float pitchDegrees) {
        float diameter = Math.max(max.x() - min.x(), max.z() - min.z());
        float height = max.y() - min.y();
        double pitch = Math.toRadians(pitchDegrees);
        float vertical = (float) (height * Math.abs(Math.cos(pitch)) + diameter * Math.abs(Math.sin(pitch)));
        return Math.max(diameter, vertical);
    }

    static synchronized int cachedIcons() {
        return FITTED.size();
    }

    public record Unbaked(Optional<Identifier> part, boolean wall, boolean box, boolean ground, boolean hand)
            implements SpecialModelRenderer.Unbaked<Icon> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.optionalFieldOf("part").forGetter(Unbaked::part),
                Codec.BOOL.optionalFieldOf("wall", false).forGetter(Unbaked::wall),
                Codec.BOOL.optionalFieldOf("box", false).forGetter(Unbaked::box),
                Codec.BOOL.optionalFieldOf("ground", false).forGetter(Unbaked::ground),
                Codec.BOOL.optionalFieldOf("hand", false).forGetter(Unbaked::hand)
        ).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<Icon> bake(SpecialModelRenderer.BakingContext context) {
            return new PartItemRenderer(this.part, this.wall, this.box, this.ground, this.hand);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
