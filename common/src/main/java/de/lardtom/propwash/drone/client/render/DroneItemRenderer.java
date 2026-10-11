package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintChannel;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.client.ItemSizeRule;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;

import static de.lardtom.propwash.drone.client.render.DroneVisualScale.MM_TO_UNITS;

public final class DroneItemRenderer implements SpecialModelRenderer<DroneItemRenderer.Look> {
    public static final Identifier TYPE = Constants.id("drone_build");
    public static final DroneBuild REFERENCE = DroneBuild.FREESTYLE;
    public static final float MIN_FIT = 0.72F;
    public static final float MAX_FIT = 1.0F;
    public static final float FIT_EXPONENT = 0.3F;
    public static final float WALL_DIAMETER = 1.0F;
    public static final float GROUND_FULL_FOOTPRINT_MM = 600.0F;
    public static final float GROUND_EXPONENT = 0.2F;
    public static final float GUI_PITCH_DEGREES = 42.0F;
    public static final float GUI_YAW_DEGREES = 205.0F;
    private static final Quaternionf GUI_VIEW = new Quaternionf().rotationXYZ((float) Math.toRadians(GUI_PITCH_DEGREES),
            (float) Math.toRadians(GUI_YAW_DEGREES), 0.0F);
    public static final float ICON_SHAPES_BELOW = 0.5F;
    public static final int ICON_MIN_LUMA = 96;
    private static final int MAX_CACHED = 96;
    private static final Map<Look, Fitted> FITTED = new HashMap<>();
    private static final Map<Look, Fitted> WALL_FITTED = new HashMap<>();
    private static final Map<Look, Fitted> GROUND_FITTED = new HashMap<>();
    private static int generation = Integer.MIN_VALUE;
    private static @Nullable DroneRenderConfig config;
    private static @Nullable Reference reference;
    private static boolean warned;
    static @Nullable Look lastSubmitted;
    static boolean lastWall;
    static boolean lastGround;
    static int submitted;

    private final boolean wall;
    private final boolean ground;

    public DroneItemRenderer(boolean wall) {
        this(wall, false);
    }

    public DroneItemRenderer(boolean wall, boolean ground) {
        this.wall = wall;
        this.ground = ground && !wall;
    }

    public record Look(DroneBuild build, PartWear wear, DronePaint paint, boolean battery) {
        public static final Look DEFAULT = new Look(DroneBuild.FREESTYLE, PartWear.PRISTINE, DronePaint.DEFAULT, true);

        public Look {
            build = build == null ? DroneBuild.FREESTYLE : build;
            wear = DroneVisuals.visibleWear(wear);
            paint = paint == null ? DronePaint.DEFAULT : paint;
        }

        public static Look of(ItemStack stack) {
            DroneBuild build = stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
            PartWear wear = stack.getOrDefault(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE);
            boolean battery = stack.getOrDefault(CoreComponents.BATTERY_INSTALLED.get(), Boolean.TRUE);
            return new Look(build, wear, PaintItems.paint(stack), battery);
        }
    }

    record Reference(float x, float bottom, float z, float top, float radius, float footprint, float gui) {
    }

    record Fitted(DroneRenderState state, float scale, float fit) {
    }

    private enum Mode { SLOT, WALL, GROUND }

    public boolean wall() {
        return this.wall;
    }

    public boolean ground() {
        return this.ground;
    }

    @Override
    public void submit(@Nullable Look argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                       int overlayCoords, boolean hasFoil, int outlineColor) {
        Look look = argument != null ? argument : Look.DEFAULT;
        try {
            Fitted fitted = this.wall ? wallFitted(look) : this.ground ? groundFitted(look) : fitted(look);
            DroneRenderState state = fitted.state();
            state.lightCoords = lightCoords;
            state.outlineColor = outlineColor;
            poseStack.pushPose();
            if (this.wall || this.ground) {
                poseStack.translate(0.5F, 0.5F, 0.5F);
            } else {
                Reference ref = reference();
                poseStack.translate(0.5F + ref.x() * MM_TO_UNITS, 0.5F + ref.bottom() * MM_TO_UNITS, 0.5F + ref.z() * MM_TO_UNITS);
            }
            DroneVisuals.submit(state, poseStack, submitNodeCollector);
            poseStack.popPose();
            lastSubmitted = look;
            lastWall = this.wall;
            lastGround = this.ground;
            submitted++;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                Constants.LOG.error("Drohnen-Item konnte nicht gezeichnet werden", e);
            }
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float x0 = 0.0F;
        float y0 = 0.0F;
        float z0 = 0.0F;
        float x1 = 1.0F;
        float y1 = 1.0F;
        float z1 = 1.0F;
        try {
            Reference ref = reference();
            if (this.wall) {
                float half = WALL_DIAMETER * 0.5F;
                x0 = 0.5F - half;
                x1 = 0.5F + half;
                z0 = 0.5F - half;
                z1 = 0.5F + half;
                y0 = 0.5F;
                y1 = 0.5F + (ref.top() - ref.bottom()) * MM_TO_UNITS * wallScale(ref.footprint(), MAX_FIT);
            } else if (this.ground) {
                float half = ItemSizeRule.GROUND_MAX * 0.5F;
                x0 = 0.5F - half;
                x1 = 0.5F + half;
                z0 = 0.5F - half;
                z1 = 0.5F + half;
                y0 = 0.5F;
                y1 = 0.5F + half;
            } else {
                x0 = 0.5F + (ref.x() - ref.radius()) * MM_TO_UNITS;
                x1 = 0.5F + (ref.x() + ref.radius()) * MM_TO_UNITS;
                z0 = 0.5F + (ref.z() - ref.radius()) * MM_TO_UNITS;
                z1 = 0.5F + (ref.z() + ref.radius()) * MM_TO_UNITS;
                y0 = 0.5F + ref.bottom() * MM_TO_UNITS;
                y1 = 0.5F + ref.top() * MM_TO_UNITS;
            }
        } catch (RuntimeException e) {
            Constants.LOG.warn("Drohnen-Item: Maße nicht bestimmbar ({}), benutze einen Block", e.toString());
        }
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new Vector3f((corner & 1) == 0 ? x0 : x1, (corner & 2) == 0 ? y0 : y1, (corner & 4) == 0 ? z0 : z1));
        }
    }

    @Override
    public Look extractArgument(ItemStack stack) {
        return Look.of(stack);
    }

    public static float fitFactor(float radiusMm, float referenceRadiusMm) {
        if (!(radiusMm > 0.0F) || !(referenceRadiusMm > 0.0F)) {
            return MAX_FIT;
        }
        float ratio = radiusMm / referenceRadiusMm;
        return Math.clamp((float) Math.pow(ratio, FIT_EXPONENT), MIN_FIT, MAX_FIT);
    }

    public static float wallScale(float footprintMm, float fit) {
        if (!(footprintMm > 0.0F)) {
            return 1.0F;
        }
        return fit * WALL_DIAMETER * 0.5F / (footprintMm * MM_TO_UNITS);
    }

    public static float groundSize(float footprintMm) {
        if (!(footprintMm > 0.0F)) {
            return ItemSizeRule.GROUND_MAX;
        }
        float ratio = Math.min(1.0F, footprintMm / GROUND_FULL_FOOTPRINT_MM);
        return ItemSizeRule.GROUND_MAX * (float) Math.pow(ratio, GROUND_EXPONENT);
    }

    public static float groundScale(float footprintMm, float widthMm) {
        if (!(widthMm > 0.0F)) {
            return 1.0F;
        }
        return groundSize(footprintMm) / (widthMm * MM_TO_UNITS);
    }

    static synchronized Fitted fitted(Look look) {
        return cached(FITTED, look, Mode.SLOT);
    }

    static synchronized Fitted wallFitted(Look look) {
        return cached(WALL_FITTED, look, Mode.WALL);
    }

    static synchronized Fitted groundFitted(Look look) {
        return cached(GROUND_FITTED, look, Mode.GROUND);
    }

    private static Fitted cached(Map<Look, Fitted> cache, Look look, Mode mode) {
        DroneRenderResources res = DroneVisuals.resources();
        invalidateIfStale(res);
        Fitted fitted = cache.get(look);
        if (fitted == null) {
            fitted = switch (mode) {
                case SLOT -> fit(look, res);
                case WALL -> fitWall(look, res);
                case GROUND -> fitGround(look, res);
            };
            if (cache.size() >= MAX_CACHED) {
                cache.clear();
            }
            cache.put(look, fitted);
        }
        return fitted;
    }

    static synchronized Reference reference() {
        DroneRenderResources res = DroneVisuals.resources();
        invalidateIfStale(res);
        if (reference == null) {
            VisualHull hull = res.assembly(REFERENCE).hull();
            Vector3f center = hull.center(new Vector3f());
            reference = new Reference(center.x, hull.min().y(), center.z, hull.max().y(), Math.max(1.0F, hull.radius(center)),
                    Math.max(1.0F, hull.horizontalRadius(center)), hull.projectedExtent(GUI_VIEW, center));
        }
        return reference;
    }

    static synchronized int cachedLooks() {
        return FITTED.size();
    }

    private static void invalidateIfStale(DroneRenderResources res) {
        DroneRenderConfig current = DroneRenderConfig.get();
        if (res.generation() != generation || current != config) {
            generation = res.generation();
            config = current;
            reference = null;
            FITTED.clear();
            WALL_FITTED.clear();
            GROUND_FITTED.clear();
        }
    }

    private static DroneRenderState preview(Look look) {
        DroneRenderState state = new DroneRenderState();
        DroneVisuals.fillPreview(state, look.build(), look.wear(), look.paint());
        state.batteryMissing = !look.battery();
        state.orientation.identity();
        state.originY = 0.0F;
        state.hidden = false;
        state.isInvisible = false;
        return state;
    }

    private static Fitted fit(Look look, DroneRenderResources res) {
        Reference ref = reference();
        DroneRenderState state = preview(look);
        VisualHull hull = res.assembly(look.build()).hull();
        Vector3f center = hull.center(new Vector3f());
        float radius = Math.max(1.0F, hull.radius(center));
        float fit = fitFactor(radius, ref.radius());
        float scale = fit * ref.radius() / radius;
        float gui = hull.projectedExtent(GUI_VIEW, center);
        if (gui * scale > ref.gui() && gui > 0.0F) {
            scale = ref.gui() / gui;
        }
        if (scale < ICON_SHAPES_BELOW) {
            readableShapes(state, look.build(), res);
        }
        state.pivot.set(center.x, hull.min().y(), center.z);
        state.poseScale = scale;
        return new Fitted(state, scale, fit);
    }

    private static Fitted fitWall(Look look, DroneRenderResources res) {
        Reference ref = reference();
        DroneRenderState state = preview(look);
        VisualHull hull = res.assembly(look.build()).hull();
        Vector3f center = hull.center(new Vector3f());
        float footprint = Math.max(1.0F, hull.horizontalRadius(center));
        float fit = fitFactor(footprint, ref.footprint());
        float scale = wallScale(footprint, fit);
        state.pivot.set(center.x, hull.min().y(), center.z);
        state.poseScale = scale;
        return new Fitted(state, scale, fit);
    }

    private static Fitted fitGround(Look look, DroneRenderResources res) {
        DroneRenderState state = preview(look);
        VisualHull hull = res.assembly(look.build()).hull();
        Vector3f center = hull.center(new Vector3f());
        float footprint = Math.max(1.0F, hull.horizontalRadius(center));
        float width = Math.max(1.0F, Math.max(hull.max().x() - hull.min().x(), hull.max().z() - hull.min().z()));
        float scale = groundScale(footprint, width);
        state.pivot.set(center.x, hull.min().y(), center.z);
        state.poseScale = scale;
        return new Fitted(state, scale, groundSize(footprint) / ItemSizeRule.GROUND_MAX);
    }

    static void readableShapes(DroneRenderState state, DroneBuild build, DroneRenderResources res) {
        DroneLayout layout = res.layout(build.frame());
        if (state.frame.useItem || !state.frame.solid.isEmpty()) {
            iconSlot(state.frame, FallbackShapes.frameIcon(build.frame(), layout));
            paintIcon(state.frame, FallbackShapes.frameIconColor(build.frame()), state.paint, PaintChannel.FRAME, 0);
            if (build.frame().ducted && FallbackShapes.ductColor(build.frame()) != FallbackShapes.frameIconColor(build.frame())) {
                paintIcon(state.frame, FallbackShapes.ductColor(build.frame()), state.paint, PaintChannel.TPU, 0);
            }
        }
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            DroneRenderState.PartSlot prop = state.props[slot];
            if ((prop.useItem || !prop.solid.isEmpty()) && !state.propBlur[slot]) {
                iconSlot(prop, FallbackShapes.propWide(build.prop(), layout.counterClockwise(slot)));
                paintIcon(prop, FallbackShapes.propColor(build.prop()), state.paint, PaintChannel.PROP, slot);
            }
        }
    }

    private static void paintIcon(DroneRenderState.PartSlot slot, int baseRgb, DronePaint paint, PaintChannel channel, int position) {
        if (paint == null || paint.isDefault()) {
            return;
        }
        OptionalInt color = paint.color(channel, position);
        if (color.isEmpty()) {
            return;
        }
        int readable = readableIconColor(color.getAsInt());
        slot.solid = PaintedShapes.recolor(slot.solid, baseRgb, readable);
        slot.translucent = PaintedShapes.recolor(slot.translucent, baseRgb, readable);
    }

    public static int readableIconColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (int pass = 0; pass < 4; pass++) {
            int luma = Math.round(0.2126F * r + 0.7152F * g + 0.0722F * b);
            if (luma >= ICON_MIN_LUMA) {
                break;
            }
            int lift = ICON_MIN_LUMA - luma;
            r = Math.min(255, r + lift);
            g = Math.min(255, g + lift);
            b = Math.min(255, b + lift);
        }
        return r << 16 | g << 8 | b;
    }

    private static void iconSlot(DroneRenderState.PartSlot slot, Shape shape) {
        slot.useItem = false;
        slot.item = DroneRenderState.PartSlot.NO_ITEM;
        slot.solid = shape;
        slot.translucent = Shape.EMPTY;
    }

    public record Unbaked(boolean wall, boolean ground) implements SpecialModelRenderer.Unbaked<Look> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("wall", false).forGetter(Unbaked::wall),
                Codec.BOOL.optionalFieldOf("ground", false).forGetter(Unbaked::ground)
        ).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<Look> bake(SpecialModelRenderer.BakingContext context) {
            return new DroneItemRenderer(this.wall, this.ground);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
