package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePoles;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.LapTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GateRenderer extends EntityRenderer<GateEntity, GateRenderState> {
    public static final int LIGHT_WHITE = 0xFFFFFFFF;
    public static final int LIGHT_GREEN = 0xFF33FF66;
    public static final int LIGHT_IDLE = 0xFF8A8A8A;
    public static final int LIGHT_RED = 0xFFFF3B30;
    public static final int LIGHT_AMBER = 0xFFFFA726;
    public static final int FRAME_COLOR = 0xFF3A3F47;
    public static final int EXIT_COLOR = 0xFFFF3B30;
    public static final int EXIT_TEXT_COLOR = 0xFF9AA0A8;
    public static final float BANNER_HEIGHT = 1.0F;
    public static final float BANNER_DEPTH = 0.125F;
    public static final float BANNER_CAP = 0.5F;
    public static final float PLATE_HEIGHT = 0.8F;
    public static final float PLATE_DEPTH = 0.12F;
    public static final int CHEVRONS = 3;
    private static final int PULSE_STEPS = 12;
    private static final float LIGHT_INSET = 0.3F;
    private static final float CORNER_HALF_DEPTH = 0.3125F;
    private static final float LIGHT_THICKNESS = 0.03125F;
    private static final float CHEVRON_FIRST = 1.0F;
    private static final float CHEVRON_STEP = 0.85F;
    private static final float CHEVRON_HALF_LENGTH = 0.3F;
    private static final float CHEVRON_THICKNESS = 0.2F;
    private static final float CHEVRON_LIFT = 0.035F;
    private static final float CHEVRON_WAVE = 0.05F;
    private static final float CHEVRON_MIN = 0.35F;
    private static final float HALO_WIDTH = 0.32F;
    private static final float HALO_ALPHA = 0.6F;
    private static final float RIM = 0.06F;
    private static final float EXIT_MARK = 0.11F;
    private static final float EXIT_ALPHA = 0.75F;
    private static final float NUMBER_HEIGHT = 0.5F;
    private static final float BANNER_TEXT_HEIGHT = 0.42F;
    private static final float EXIT_TEXT_SCALE = 0.6F;
    private static final float TEXT_MARGIN = 0.08F;
    private static final float GLYPH_HEIGHT = 7.0F;
    private static final float FACE_GAP = 0.012F;
    private static final double NAME_DROP = 0.35;
    private static final float PLATE_GAP = 0.0625F;
    private static final float NAME_LIFT = 0.3F;
    private static final double TILE_EPSILON = 1.0E-4;
    private static final double LEVEL_CHEVRONS = 0.6;
    private static final float EDITED_FILL = 0.55F;
    private static @Nullable GateRenderer current;

    private final ItemModelResolver items;
    private final boolean[] modelPresent = new boolean[GateSize.values().length];
    private final boolean[] startPresent = new boolean[GateSize.values().length];
    private final boolean[][] variantPresent = new boolean[GateSize.values().length][GateStacking.Base.values().length];
    private final ItemStackRenderState probe = new ItemStackRenderState();
    private final Fit[] fits = new Fit[GateSize.values().length];
    private final PartFit[] partFits = new PartFit[Part.values().length];
    private final boolean mountPartsPresent;
    private final boolean piecesPresent;
    private final CourseElementRenderer elements;
    private final Map<Long, ItemStack> stacks = new HashMap<>();
    private final Map<Long, ItemStack> pieceStacks = new HashMap<>();
    private final Map<Integer, FormattedCharSequence> numbers = new HashMap<>();
    private final GateRenderState edited = new GateRenderState();
    private static @Nullable RenderType glowType;
    private static @Nullable RenderType solidType;

    public enum Marking {
        NONE,
        NUMBER,
        START
    }

    public enum Piece {
        CORNER_LEFT("corner_left"),
        CORNER_RIGHT("corner_right"),
        TEE_LEFT("tee_left"),
        TEE_RIGHT("tee_right"),
        POST_LEFT("post_left"),
        POST_LEFT_HALF("post_left_half"),
        POST_RIGHT("post_right"),
        POST_RIGHT_HALF("post_right_half"),
        BAR_BOTTOM("bar_bottom"),
        BAR_BOTTOM_HALF("bar_bottom_half"),
        BAR_TOP("bar_top"),
        BAR_TOP_HALF("bar_top_half"),
        FOOT_LEFT("foot_left"),
        FOOT_RIGHT("foot_right"),
        LED_POST_LEFT("led_post_left"),
        LED_POST_LEFT_HALF("led_post_left_half"),
        LED_POST_RIGHT("led_post_right"),
        LED_POST_RIGHT_HALF("led_post_right_half"),
        LED_BOTTOM_OUTER("led_bottom_outer"),
        LED_BOTTOM_OUTER_HALF("led_bottom_outer_half"),
        LED_BOTTOM_INNER("led_bottom_inner"),
        LED_BOTTOM_INNER_HALF("led_bottom_inner_half"),
        LED_TOP("led_top"),
        LED_TOP_HALF("led_top_half"),
        BANNER_CAP_LEFT("banner_cap_left"),
        BANNER_CAP_RIGHT("banner_cap_right"),
        BANNER_PANEL("banner_panel"),
        BANNER_PANEL_HALF("banner_panel_half"),
        HANGER_CLAMP("hanger_clamp"),
        HANGER_STRAP("hanger_strap"),
        HANGER_CEILING_PLATE("hanger_ceiling_plate"),
        POLE_END("pole_end"),
        POLE_TEE("pole_tee"),
        POLE_FOOT("pole_foot");

        private final String key;

        Piece(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }

        public boolean lights() {
            return this.key.startsWith("led_");
        }

        public boolean frame() {
            return this.ordinal() <= FOOT_RIGHT.ordinal();
        }

        public boolean banner() {
            return this.key.startsWith("banner_");
        }

        public boolean hanger() {
            return this.key.startsWith("hanger_");
        }
    }

    public static Identifier pieceModelId(Piece piece, boolean start) {
        return Constants.id("race/gate_piece_" + (start && piece.frame() ? "start_" : "") + piece.key());
    }

    private record Fit(float scale, float x, float bottom, float z, boolean rotate) {
    }

    private record PartFit(float x, float bottom, float z) {
    }

    public GateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemModelResolver();
        this.shadowRadius = 0.0F;
        for (GateSize size : GateSize.values()) {
            Identifier model = Constants.id("models/gate/" + size.key() + "_frame.json");
            this.modelPresent[size.ordinal()] = context.getResourceManager().getResource(model).isPresent();
            Identifier start = Constants.id("models/gate/" + size.key() + "_start_frame.json");
            Identifier banner = Constants.id("models/gate/" + size.key() + "_banner.json");
            this.startPresent[size.ordinal()] = context.getResourceManager().getResource(start).isPresent()
                    && context.getResourceManager().getResource(banner).isPresent();
            for (GateStacking.Base base : GateStacking.Base.values()) {
                String suffix = variantSuffix(base);
                this.variantPresent[size.ordinal()][base.ordinal()] = suffix.isEmpty()
                        || (context.getResourceManager().getResource(Constants.id("models/gate/" + size.key() + "_frame" + suffix + ".json")).isPresent()
                        && context.getResourceManager().getResource(Constants.id("models/gate/" + size.key() + "_lights" + suffix + ".json")).isPresent()
                        && context.getResourceManager().getResource(Constants.id("models/gate/" + size.key() + "_start_frame" + suffix + ".json")).isPresent());
            }
        }
        this.mountPartsPresent = context.getResourceManager().getResource(Constants.id("models/gate/mount_plate.json")).isPresent()
                && context.getResourceManager().getResource(Constants.id("models/gate/hanger.json")).isPresent();
        boolean pieces = true;
        for (Piece piece : Piece.values()) {
            pieces &= context.getResourceManager().getResource(Constants.id("models/gate/piece/" + piece.key() + ".json")).isPresent();
        }
        this.piecesPresent = pieces;
        this.elements = new CourseElementRenderer(this.items, context.getResourceManager());
        current = this;
    }

    public static @Nullable GateRenderer current() {
        return current;
    }

    public static String variantSuffix(GateStacking.Base base) {
        return switch (base) {
            case BAR -> "";
            case LEGS -> "_legs";
            case STACKED -> "_stacked";
            case MOUNTED -> "_mount";
        };
    }

    public static Identifier frameModelId(GateSize size) {
        return frameModelId(size, GateStacking.Base.BAR);
    }

    public static Identifier frameModelId(GateSize size, GateStacking.Base base) {
        return Constants.id("race/gate_" + size.key() + "_frame" + variantSuffix(base));
    }

    public static Identifier lightsModelId(GateSize size) {
        return lightsModelId(size, GateStacking.Base.BAR);
    }

    public static Identifier lightsModelId(GateSize size, GateStacking.Base base) {
        return Constants.id("race/gate_" + size.key() + "_lights" + variantSuffix(base));
    }

    public static Identifier startFrameModelId(GateSize size) {
        return startFrameModelId(size, GateStacking.Base.BAR);
    }

    public static Identifier startFrameModelId(GateSize size, GateStacking.Base base) {
        return Constants.id("race/gate_" + size.key() + "_start_frame" + variantSuffix(base));
    }

    public static Identifier bannerModelId(GateSize size) {
        return Constants.id("race/gate_" + size.key() + "_banner");
    }

    public static Identifier mountPlateModelId() {
        return Constants.id("race/gate_mount_plate");
    }

    public static Identifier hangerModelId() {
        return Constants.id("race/gate_hanger");
    }

    public static void rotation(Quaternionf out, GateGeometry gate) {
        Matrix3f frame = new Matrix3f((float) gate.sideX(), (float) gate.sideY(), (float) gate.sideZ(), (float) gate.upX(), (float) gate.upY(),
                (float) gate.upZ(), (float) gate.normalX(), (float) gate.normalY(), (float) gate.normalZ());
        out.setFromNormalized(frame).normalize();
    }

    public static Marking marking(GateEntity gate) {
        if (!gate.inCourse()) {
            return Marking.NONE;
        }
        return gate.gateIndex() == 0 ? Marking.START : Marking.NUMBER;
    }

    @Override
    public GateRenderState createRenderState() {
        return new GateRenderState();
    }

    @Override
    public boolean shouldRender(GateEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
        return !GateEditor.replaces(entity) && super.shouldRender(entity, culler, camX, camY, camZ, partialTicks);
    }

    public void submitEdited(GateEntity entity, GateGeometry geometry, PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera,
                             float ageInTicks) {
        GateRenderState state = this.edited;
        Level level = entity.level();
        Marking marking = marking(entity);
        int number = entity.gateIndex() + 1;
        if (geometry.freeform()) {
            int light = entity.inCourse() ? lightColor(entity, ageInTicks) : FreeformGateRenderer.IDLE_COLOR;
            this.fill(state, geometry.at(0.0, 0.0, 0.0), null, marking, number, light, EDITED_FILL);
        } else {
            GateStacking.Base base = entity.kind() == CourseElement.GATE_POLE ? GatePoles.base(geometry.mount(), entity.supported())
                    : GateStacking.placedBase(geometry);
            this.fill(state, geometry.at(0.0, 0.0, 0.0), entity.size(), base, false, marking, number, lightColor(entity, ageInTicks), level);
        }
        BlockPos lightAt = BlockPos.containing(geometry.centerX(), geometry.centerY(), geometry.centerZ());
        state.lightCoords = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, lightAt), level.getBrightness(LightLayer.SKY, lightAt));
        state.outlineColor = 0;
        state.ageInTicks = ageInTicks;
        state.distanceToCameraSq = camera.distanceToSqr(geometry.centerX(), geometry.centerY(), geometry.centerZ());
        state.nameTag = null;
        poseStack.pushPose();
        poseStack.translate(geometry.x() - camera.x, geometry.y() - camera.y, geometry.z() - camera.z);
        if (geometry.freeform()) {
            FreeformGateRenderer.submit(this, state, poseStack, collector);
        } else {
            this.submitBody(state, poseStack, collector);
            if (state.kind == CourseElement.GATE_POLE) {
                poseStack.popPose();
                return;
            }
            if (state.kind != CourseElement.GATE) {
                this.elements.submitMarkings(this, state, poseStack, collector);
            } else if (state.marking != Marking.NONE) {
                this.submitMarkings(state, poseStack, collector);
            }
        }
        poseStack.popPose();
    }

    @Override
    public void extractRenderState(GateEntity entity, GateRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (entity.kind() == CourseElement.FREEFORM) {
            this.extractFreeform(entity, state, partialTicks);
            return;
        }
        this.fill(state, entity.geometry().at(0.0, 0.0, 0.0), entity.size(), entity.base(), entity.covered() || entity.carrying(), marking(entity),
                entity.gateIndex() + 1, lightColor(entity, state.ageInTicks), entity.level());
        if (state.covered && state.nameTag != null) {
            state.nameTagAttachment = new Vec3(0.0, state.height - state.size.frame() - NAME_DROP, 0.0);
        } else if (state.mount != GateMount.STANDING || !state.tilt.none()) {
            AABB box = entity.getBoundingBox();
            state.nameTagAttachment = new Vec3((box.minX + box.maxX) * 0.5 - entity.getX(), box.maxY - entity.getY() + NAME_LIFT,
                    (box.minZ + box.maxZ) * 0.5 - entity.getZ());
        }
    }

    private void extractFreeform(GateEntity entity, GateRenderState state, float partialTicks) {
        GateGeometry geometry = entity.geometry();
        Marking marking = marking(entity);
        int light = entity.inCourse() ? lightColor(entity, state.ageInTicks) : FreeformGateRenderer.IDLE_COLOR;
        Vec3 viewer = FreeformGateRenderer.viewer(partialTicks);
        float fill = viewer == null ? 0.0F : FreeformGateRenderer.approach(geometry, viewer);
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        if (entity.inCourse() && entity.courseId() == tracker.courseId() && tracker.tracker().racing() && entity.gateIndex() == tracker.nextGateIndex()) {
            fill = Math.max(fill, 0.85F);
        }
        this.fill(state, geometry.at(0.0, 0.0, 0.0), entity, marking, entity.gateIndex() + 1, light, fill);
        FreeformRegion region = geometry.region();
        if (region != null && state.nameTag != null) {
            double top = region.horizontal() ? 0.9 : region.height() * 0.5 + 0.4;
            state.nameTagAttachment = new Vec3(geometry.centerX() - entity.getX(), top, geometry.centerZ() - entity.getZ());
        }
    }

    public void fill(GateRenderState state, GateGeometry local, @Nullable GateEntity entity, Marking marking, int number, int lightColor, float fill) {
        state.kind = CourseElement.FREEFORM;
        state.variant = local.variant();
        state.marking = marking;
        state.number = number;
        state.offsetX = 0.0F;
        state.offsetY = 0.0F;
        state.offsetZ = 0.0F;
        state.tilt = GateTilt.NONE;
        state.mount = GateMount.STANDING;
        state.covered = false;
        state.modelled = false;
        state.startModelled = false;
        state.frame.clear();
        state.lights.clear();
        state.startFrame.clear();
        state.banner.clear();
        state.plate.clear();
        state.hanger.clear();
        for (ItemStackRenderState piece : state.pieces) {
            piece.clear();
        }
        for (ItemStackRenderState piece : state.elementPieces) {
            piece.clear();
        }
        state.straps = 0;
        state.levelChevrons = false;
        FreeformGateRenderer.fill(state, local, entity, lightColor, fill);
        state.label = switch (marking) {
            case START -> this.startShort();
            case NUMBER -> this.numbers.computeIfAbsent(number, n -> Component.literal(Integer.toString(n)).getVisualOrderText());
            case NONE -> null;
        };
        state.labelWidth = state.label == null ? 0 : this.getFont().width(state.label);
    }

    public void fill(GateRenderState state, GateSize size, float yaw, GateMount mount, boolean flipped, GateStacking.Base base, boolean covered,
                     Marking marking, int number, int lightColor, @Nullable Level level) {
        this.fill(state, GateGeometry.of(0.0, 0.0, 0.0, yaw, size, mount, flipped), size, base, covered, marking, number, lightColor, level);
    }

    public void fill(GateRenderState state, GateGeometry local, GateSize size, GateStacking.Base base, boolean covered, Marking marking, int number,
                     int lightColor, @Nullable Level level) {
        if (local.freeform()) {
            this.fill(state, local, null, marking, number, lightColor, 0.0F);
            return;
        }
        float yaw = local.yawDegrees();
        GateMount mount = local.mount();
        boolean flipped = local.flipped();
        state.width = (float) local.width();
        state.height = (float) local.height();
        state.tilt = local.tilt();
        state.offsetX = (float) local.originX();
        state.offsetY = (float) local.originY();
        state.offsetZ = (float) local.originZ();
        state.tiled = !size.standard(local.width(), local.height()) && this.piecesPresent;
        state.size = size;
        state.yaw = yaw;
        state.mount = mount;
        state.flipped = flipped;
        rotation(state.rotation, local);
        state.lightColor = lightColor;
        state.marking = marking;
        state.covered = covered;
        state.base = base;
        state.number = number;
        state.kind = local.kind();
        state.variant = local.variant();
        if (!local.gate()) {
            this.fillElement(state, local, lightColor, level);
            return;
        }
        state.modelled = this.modelPresent[size.ordinal()];
        state.startModelled = state.modelled && marking == Marking.START && this.startPresent[size.ordinal()];
        GateStacking.Base variant = this.variantPresent[size.ordinal()][base.ordinal()] ? base : GateStacking.Base.BAR;
        if (state.modelled) {
            Fit fit = this.fits[size.ordinal()];
            if (fit == null) {
                this.items.updateForTopItem(this.probe, this.stack(size, Part.FRAME, GateStacking.Base.BAR, 0), ItemDisplayContext.NONE, level, null, 0);
                fit = this.fit(size, this.probe);
                this.probe.clear();
            }
            this.items.updateForTopItem(state.frame, this.stack(size, Part.FRAME, variant, 0), ItemDisplayContext.NONE, level, null, 0);
            this.items.updateForTopItem(state.lights, this.stack(size, Part.LIGHTS, variant, lightColor), ItemDisplayContext.NONE, level, null, 0);
            if (fit == null) {
                state.modelled = false;
                state.startModelled = false;
            } else {
                state.fitScale = fit.scale;
                state.fitX = fit.x;
                state.fitBottom = fit.bottom;
                state.fitZ = fit.z;
                state.fitRotate = fit.rotate;
            }
        }
        if (state.startModelled) {
            this.items.updateForTopItem(state.startFrame, this.stack(size, Part.START_FRAME, variant, 0), ItemDisplayContext.NONE, level, null, 0);
            this.items.updateForTopItem(state.banner, this.stack(size, Part.BANNER, GateStacking.Base.BAR, 0), ItemDisplayContext.NONE, level, null, 0);
            state.startModelled = !state.startFrame.isEmpty() && !state.banner.isEmpty();
        }
        if (!state.startModelled) {
            state.startFrame.clear();
            state.banner.clear();
        }
        if (!state.modelled) {
            state.frame.clear();
            state.lights.clear();
        }
        state.plate.clear();
        state.hanger.clear();
        boolean tilted = !state.tilt.none();
        if (state.modelled && this.mountPartsPresent && !tilted) {
            if (mount == GateMount.WALL_SIDE) {
                this.updatePart(state.plate, Part.PLATE, level);
            } else if (mount == GateMount.HANGING) {
                this.updatePart(state.hanger, Part.HANGER, level);
            }
        }
        state.levelChevrons = false;
        if (local.tilted()) {
            double nx = local.normalX();
            double nz = local.normalZ();
            double horizontal = Math.sqrt(nx * nx + nz * nz);
            if (horizontal > LEVEL_CHEVRONS) {
                state.levelChevrons = true;
                float sx = (float) (nz / horizontal);
                float sz = (float) (-nx / horizontal);
                float fx = (float) (nx / horizontal);
                float fz = (float) (nz / horizontal);
                state.chevronRotation.setFromNormalized(new Matrix3f(sx, 0.0F, sz, 0.0F, 1.0F, 0.0F, fx, 0.0F, fz)).normalize();
                double[] lowest = {Double.POSITIVE_INFINITY};
                local.bounds((minX, minY, minZ, maxX, maxY, maxZ) -> lowest[0] = minY);
                state.chevronX = (float) local.centerX();
                state.chevronY = (float) lowest[0];
                state.chevronZ = (float) local.centerZ();
            }
        }
        state.straps = 0;
        if (mount == GateMount.HANGING && tilted) {
            local.straps((minX, minY, minZ, maxX, maxY, maxZ) -> {
                int i = state.straps;
                if (i < state.strapX.length) {
                    state.strapX[i] = (float) ((minX + maxX) * 0.5);
                    state.strapY[i] = (float) minY;
                    state.strapZ[i] = (float) ((minZ + maxZ) * 0.5);
                    state.strapLength[i] = (float) (maxY - minY);
                    state.straps = i + 1;
                }
            });
        }
        this.fillPieces(state, variant, lightColor, level);
        state.label = switch (marking) {
            case START -> covered ? this.startShort() : Component.translatable("propwash.race.gate.banner").getVisualOrderText();
            case NUMBER -> this.numbers.computeIfAbsent(number, n -> Component.literal(Integer.toString(n)).getVisualOrderText());
            case NONE -> null;
        };
        state.labelWidth = state.label == null ? 0 : this.getFont().width(state.label);
    }

    private void fillElement(GateRenderState state, GateGeometry local, int lightColor, @Nullable Level level) {
        GateSize size = GateSize.MEDIUM;
        Fit fit = this.fits[size.ordinal()];
        if (fit == null && this.modelPresent[size.ordinal()]) {
            this.items.updateForTopItem(this.probe, this.stack(size, Part.FRAME, GateStacking.Base.BAR, 0), ItemDisplayContext.NONE, level, null, 0);
            fit = this.fit(size, this.probe);
            this.probe.clear();
        }
        if (fit != null) {
            state.fitScale = fit.scale;
            state.fitX = fit.x;
            state.fitBottom = fit.bottom;
            state.fitZ = fit.z;
            state.fitRotate = false;
        }
        state.frame.clear();
        state.lights.clear();
        state.startFrame.clear();
        state.banner.clear();
        state.plate.clear();
        state.hanger.clear();
        for (ItemStackRenderState piece : state.pieces) {
            piece.clear();
        }
        state.straps = 0;
        state.levelChevrons = false;
        if (local.kind() == CourseElement.GATE_POLE) {
            GatePoleRenderer.fill(this, state, local, level, fit != null);
            state.label = null;
            state.labelWidth = 0;
            return;
        }
        this.elements.fill(state, local, lightColor, level, fit != null);
        state.label = state.marking == Marking.NONE ? null
                : this.numbers.computeIfAbsent(state.number, n -> Component.literal(Integer.toString(n)).getVisualOrderText());
        state.labelWidth = state.label == null ? 0 : this.getFont().width(state.label);
    }

    private void fillPieces(GateRenderState state, GateStacking.Base variant, int lightColor, @Nullable Level level) {
        boolean start = state.marking == Marking.START && this.startPresent[state.size.ordinal()];
        boolean hangers = state.straps > 0;
        for (Piece piece : Piece.values()) {
            ItemStackRenderState out = state.pieces[piece.ordinal()];
            boolean needed = piece.hanger() ? hangers && this.piecesPresent
                    : state.tiled && state.modelled && neededPiece(piece, variant, start && !state.covered);
            if (!needed) {
                out.clear();
                continue;
            }
            this.items.updateForTopItem(out, this.pieceStack(piece, start, piece.lights() ? lightColor : 0), ItemDisplayContext.NONE, level, null, 0);
        }
        if (state.tiled && state.modelled) {
            state.startModelled = start;
        }
    }

    private static boolean neededPiece(Piece piece, GateStacking.Base variant, boolean banner) {
        return switch (piece) {
            case TEE_LEFT, TEE_RIGHT -> variant == GateStacking.Base.STACKED;
            case FOOT_LEFT, FOOT_RIGHT -> variant.feet();
            case BAR_BOTTOM, BAR_BOTTOM_HALF, LED_BOTTOM_OUTER, LED_BOTTOM_OUTER_HALF -> variant.bottomBar();
            case LED_BOTTOM_INNER, LED_BOTTOM_INNER_HALF -> variant != GateStacking.Base.LEGS;
            case BANNER_CAP_LEFT, BANNER_CAP_RIGHT, BANNER_PANEL, BANNER_PANEL_HALF -> banner;
            case HANGER_CLAMP, HANGER_STRAP, HANGER_CEILING_PLATE, POLE_END, POLE_TEE, POLE_FOOT -> false;
            default -> true;
        };
    }

    private ItemStack pieceStack(Piece piece, boolean start, int color) {
        boolean lights = piece.lights();
        long rgb = color & 0xFFFFFF;
        boolean startTexture = start && piece.frame();
        long key = ((long) piece.ordinal() << 26) | (startTexture ? 1L << 25 : 0L) | (lights ? rgb : 0L);
        return this.pieceStacks.computeIfAbsent(key, k -> {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.ITEM_MODEL, pieceModelId(piece, startTexture));
            if (lights) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of((int) rgb)));
            }
            return stack;
        });
    }

    boolean piecesPresent() {
        return this.piecesPresent;
    }

    void updatePiece(GateRenderState state, Piece piece, int color, @Nullable Level level) {
        this.items.updateForTopItem(state.pieces[piece.ordinal()], this.pieceStack(piece, false, color), ItemDisplayContext.NONE, level, null, 0);
    }

    private void updatePart(ItemStackRenderState out, Part part, @Nullable Level level) {
        this.items.updateForTopItem(out, this.stack(GateSize.MEDIUM, part, GateStacking.Base.BAR, 0), ItemDisplayContext.NONE, level, null, 0);
        if (out.isEmpty()) {
            return;
        }
        if (this.partFits[part.ordinal()] == null) {
            AABB box = out.getModelBoundingBox();
            this.partFits[part.ordinal()] = new PartFit((float) ((box.minX + box.maxX) * 0.5), (float) box.minY, (float) ((box.minZ + box.maxZ) * 0.5));
        }
    }

    private FormattedCharSequence startShort() {
        return Component.translatable("propwash.race.gate.start_short").getVisualOrderText();
    }

    public static float plateCenterX(GateSize size, boolean covered) {
        return plateCenterX(size, size.width(), covered);
    }

    public static float plateCenterX(GateSize size, float width, boolean covered) {
        return covered ? -(width * 0.5F + PLATE_GAP + plateWidth(size) * 0.5F) : 0.0F;
    }

    public static float plateBottom(GateSize size, boolean covered) {
        return plateBottom(size.height(), covered);
    }

    public static float plateBottom(float height, boolean covered) {
        return covered ? height - PLATE_HEIGHT : height;
    }

    @Override
    public void submit(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.kind == CourseElement.FREEFORM) {
            FreeformGateRenderer.submit(this, state, poseStack, collector);
            super.submit(state, poseStack, collector, camera);
            return;
        }
        this.submitBody(state, poseStack, collector);
        if (state.kind == CourseElement.GATE_POLE) {
            super.submit(state, poseStack, collector, camera);
            return;
        }
        if (state.kind != CourseElement.GATE) {
            this.elements.submitMarkings(this, state, poseStack, collector);
        } else if (state.marking != Marking.NONE) {
            this.submitMarkings(state, poseStack, collector);
        }
        super.submit(state, poseStack, collector, camera);
    }

    private static boolean offset(GateRenderState state) {
        return state.offsetX != 0.0F || state.offsetY != 0.0F || state.offsetZ != 0.0F;
    }

    public static void toFrame(GateRenderState state, PoseStack poseStack) {
        if (offset(state)) {
            poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        }
        poseStack.rotate(state.rotation);
    }

    public void submitBody(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        if (state.kind == CourseElement.FREEFORM) {
            return;
        }
        if (state.kind == CourseElement.GATE_POLE) {
            GatePoleRenderer.submitBody(state, poseStack, collector);
            return;
        }
        if (state.kind != CourseElement.GATE) {
            CourseElementRenderer.submitBody(state, poseStack, collector);
            return;
        }
        if (!state.modelled) {
            submitFallback(state, poseStack, collector);
            return;
        }
        if (state.tiled) {
            this.submitTiled(state, poseStack, collector);
            this.submitMountParts(state, poseStack, collector);
            return;
        }
        poseStack.pushPose();
        toFrame(state, poseStack);
        if (state.fitRotate) {
            poseStack.rotateDegrees(Axis.YP, 90.0F);
        }
        poseStack.scale(state.fitScale, state.fitScale, state.fitScale);
        poseStack.translate(-state.fitX, -state.fitBottom, -state.fitZ);
        ItemStackRenderState frame = state.startModelled ? state.startFrame : state.frame;
        frame.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        state.lights.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        if (state.startModelled && !state.covered) {
            poseStack.pushPose();
            toFrame(state, poseStack);
            poseStack.translate(0.0F, state.height, 0.0F);
            if (state.fitRotate) {
                poseStack.rotateDegrees(Axis.YP, 90.0F);
            }
            poseStack.scale(state.fitScale, state.fitScale, state.fitScale);
            poseStack.translate(-state.fitX, -state.fitBottom, -state.fitZ);
            state.banner.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        this.submitMountParts(state, poseStack, collector);
    }

    private void submitTiled(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        float half = state.width * 0.5F;
        float frame = state.size.frame();
        float inner = half - frame;
        float height = state.height;
        GateStacking.Base variant = state.base;
        int light = state.lightCoords;
        int glow = LightCoordsUtil.FULL_BRIGHT;
        poseStack.pushPose();
        toFrame(state, poseStack);
        boolean stacked = variant == GateStacking.Base.STACKED;
        this.piece(state, stacked ? Piece.TEE_LEFT : Piece.CORNER_LEFT, -half, 0.0F, light, poseStack, collector);
        this.piece(state, stacked ? Piece.TEE_RIGHT : Piece.CORNER_RIGHT, half, 0.0F, light, poseStack, collector);
        this.piece(state, Piece.CORNER_LEFT, -half, height - frame, light, poseStack, collector);
        this.piece(state, Piece.CORNER_RIGHT, half, height - frame, light, poseStack, collector);
        for (float y = frame; y < height - frame - TILE_EPSILON; ) {
            boolean whole = height - frame - y >= 1.0F - TILE_EPSILON;
            this.piece(state, whole ? Piece.POST_LEFT : Piece.POST_LEFT_HALF, -half, y, light, poseStack, collector);
            this.piece(state, whole ? Piece.POST_RIGHT : Piece.POST_RIGHT_HALF, half, y, light, poseStack, collector);
            this.piece(state, whole ? Piece.LED_POST_LEFT : Piece.LED_POST_LEFT_HALF, -half, y, glow, poseStack, collector);
            this.piece(state, whole ? Piece.LED_POST_RIGHT : Piece.LED_POST_RIGHT_HALF, half, y, glow, poseStack, collector);
            y += whole ? 1.0F : 0.5F;
        }
        for (float x = -inner; x < inner - TILE_EPSILON; ) {
            boolean whole = inner - x >= 1.0F - TILE_EPSILON;
            if (variant.bottomBar()) {
                this.piece(state, whole ? Piece.BAR_BOTTOM : Piece.BAR_BOTTOM_HALF, x, 0.0F, light, poseStack, collector);
                this.piece(state, whole ? Piece.LED_BOTTOM_OUTER : Piece.LED_BOTTOM_OUTER_HALF, x, 0.0F, glow, poseStack, collector);
            }
            if (variant != GateStacking.Base.LEGS) {
                this.piece(state, whole ? Piece.LED_BOTTOM_INNER : Piece.LED_BOTTOM_INNER_HALF, x, 0.0F, glow, poseStack, collector);
            }
            this.piece(state, whole ? Piece.BAR_TOP : Piece.BAR_TOP_HALF, x, height, light, poseStack, collector);
            this.piece(state, whole ? Piece.LED_TOP : Piece.LED_TOP_HALF, x, height, glow, poseStack, collector);
            x += whole ? 1.0F : 0.5F;
        }
        if (variant.feet()) {
            this.piece(state, Piece.FOOT_LEFT, -half, 0.0F, light, poseStack, collector);
            this.piece(state, Piece.FOOT_RIGHT, half, 0.0F, light, poseStack, collector);
        }
        if (state.startModelled && !state.covered) {
            this.piece(state, Piece.BANNER_CAP_LEFT, -half, height, light, poseStack, collector);
            this.piece(state, Piece.BANNER_CAP_RIGHT, half, height, light, poseStack, collector);
            for (float x = -half + BANNER_CAP; x < half - BANNER_CAP - TILE_EPSILON; ) {
                boolean whole = half - BANNER_CAP - x >= 1.0F - TILE_EPSILON;
                this.piece(state, whole ? Piece.BANNER_PANEL : Piece.BANNER_PANEL_HALF, x, height, light, poseStack, collector);
                x += whole ? 1.0F : 0.5F;
            }
        }
        poseStack.popPose();
    }

    private void piece(GateRenderState state, Piece piece, float x, float y, int light, PoseStack poseStack, SubmitNodeCollector collector) {
        ItemStackRenderState out = state.pieces[piece.ordinal()];
        if (out.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0F);
        poseStack.scale(state.fitScale, state.fitScale, state.fitScale);
        poseStack.translate(-state.fitX, -state.fitBottom, -state.fitZ);
        out.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    private void submitMountParts(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        float half = state.width * 0.5F;
        float frame = state.size.frame();
        float height = state.height;
        PartFit hanger = this.partFits[Part.HANGER.ordinal()];
        if (state.mount == GateMount.HANGING && !state.hanger.isEmpty() && hanger != null) {
            for (int side = -1; side <= 1; side += 2) {
                poseStack.pushPose();
                toFrame(state, poseStack);
                poseStack.translate(side * (half - frame * 0.5F), height, 0.0F);
                poseStack.scale(state.fitScale, state.fitScale, state.fitScale);
                poseStack.translate(-hanger.x, -hanger.bottom, -hanger.z);
                state.hanger.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
                poseStack.popPose();
            }
        }
        if (state.straps > 0) {
            this.submitStraps(state, poseStack, collector);
        }
        PartFit plate = this.partFits[Part.PLATE.ordinal()];
        if (state.mount == GateMount.WALL_SIDE && !state.plate.isEmpty() && plate != null) {
            float sign = state.flipped ? -1.0F : 1.0F;
            for (int end = 0; end < 2; end++) {
                poseStack.pushPose();
                toFrame(state, poseStack);
                poseStack.translate(sign * half, end == 0 ? frame * 0.5F : height - frame * 0.5F, 0.0F);
                poseStack.rotateDegrees(Axis.ZP, 90.0F * sign);
                poseStack.scale(state.fitScale, state.fitScale, state.fitScale);
                poseStack.translate(-plate.x, -plate.bottom, -plate.z);
                state.plate.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
                poseStack.popPose();
            }
        }
    }

    private void submitStraps(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        ItemStackRenderState clamp = state.pieces[Piece.HANGER_CLAMP.ordinal()];
        ItemStackRenderState strap = state.pieces[Piece.HANGER_STRAP.ordinal()];
        ItemStackRenderState plate = state.pieces[Piece.HANGER_CEILING_PLATE.ordinal()];
        if (clamp.isEmpty() || strap.isEmpty() || plate.isEmpty()) {
            return;
        }
        float scale = state.fitScale;
        float clampHeight = (float) clamp.getModelBoundingBox().getYsize() * scale;
        float plateHeight = (float) plate.getModelBoundingBox().getYsize() * scale;
        float rodHeight = (float) strap.getModelBoundingBox().getYsize() * scale;
        for (int i = 0; i < state.straps; i++) {
            float length = state.strapLength[i];
            float rod = length - clampHeight - plateHeight;
            if (rod <= 0.0F || rodHeight <= 1.0E-4F) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(state.strapX[i], state.strapY[i], state.strapZ[i]);
            poseStack.rotateDegrees(Axis.YP, -state.yaw - state.tilt.yaw());
            this.strapPart(clamp, state, 0.0F, 1.0F, poseStack, collector);
            this.strapPart(strap, state, clampHeight, rod / rodHeight, poseStack, collector);
            this.strapPart(plate, state, length - plateHeight, 1.0F, poseStack, collector);
            poseStack.popPose();
        }
    }

    private void strapPart(ItemStackRenderState part, GateRenderState state, float y, float stretch, PoseStack poseStack,
                           SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.translate(0.0F, y, 0.0F);
        poseStack.scale(state.fitScale, state.fitScale * stretch, state.fitScale);
        poseStack.translate(-state.fitX, -state.fitBottom, -state.fitZ);
        part.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    static RenderType glowType() {
        if (glowType == null) {
            glowType = RenderTypes.eyes(BoxDraw.WHITE_TEXTURE);
        }
        return glowType;
    }

    static RenderType solidType() {
        if (solidType == null) {
            solidType = RenderTypes.entitySolid(BoxDraw.WHITE_TEXTURE);
        }
        return solidType;
    }

    private void submitMarkings(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        GateSize size = state.size;
        float width = state.width;
        float height = state.height;
        float frame = size.frame();
        float inner = width * 0.5F - frame;
        float halfDepth = size.depth() * 0.5F;
        int color = state.lightColor;
        float age = state.ageInTicks;
        boolean start = state.marking == Marking.START;
        boolean plate = !start || state.covered;
        float plateHalf = plateWidth(size) * 0.5F;
        float plateX = plateCenterX(size, width, state.covered);
        float plateY = plateBottom(height, state.covered);
        float floor = chevronFloor(size, state.base);
        boolean covered = state.covered;
        boolean level = state.levelChevrons;
        if (level) {
            poseStack.pushPose();
            poseStack.translate(state.chevronX, state.chevronY, state.chevronZ);
            poseStack.rotate(state.chevronRotation);
            collector.order(1).submitCustomGeometry(poseStack, glowType(), (pose, buffer) -> floorChevrons(pose, buffer, inner, 0.0F, color, age));
            poseStack.popPose();
        }
        poseStack.pushPose();
        toFrame(state, poseStack);
        collector.order(1).submitCustomGeometry(poseStack, glowType(), (pose, buffer) -> {
            if (!level) {
                floorChevrons(pose, buffer, inner, floor, color, age);
            }
            if (!covered) {
                exitMarks(pose, buffer, inner, frame, height);
            }
            if (start) {
                halo(pose, buffer, inner, frame, height, halfDepth + LIGHT_THICKNESS + FACE_GAP, color, age);
                halo(pose, buffer, inner, frame, height, -(halfDepth + LIGHT_THICKNESS + FACE_GAP), color, age);
            }
            if (plate) {
                plateRim(pose, buffer, plateX, plateHalf, plateY, -(PLATE_DEPTH * 0.5F + FACE_GAP), color);
                exitBar(pose, buffer, plateX, plateHalf, plateY, PLATE_DEPTH * 0.5F + FACE_GAP);
            }
        });
        int light = state.lightCoords;
        if (plate) {
            collector.submitCustomGeometry(poseStack, solidType(), (pose, buffer) -> BoxDraw.box(pose, buffer, plateX - plateHalf, plateY,
                    -PLATE_DEPTH * 0.5F, plateX + plateHalf, plateY + PLATE_HEIGHT, PLATE_DEPTH * 0.5F, FRAME_COLOR, light));
        } else if (!state.startModelled) {
            float half = width * 0.5F;
            collector.submitCustomGeometry(poseStack, solidType(), (pose, buffer) -> BoxDraw.box(pose, buffer, -half, height,
                    -BANNER_DEPTH * 0.5F, half, height + BANNER_HEIGHT, BANNER_DEPTH * 0.5F, FRAME_COLOR, light));
        }
        FormattedCharSequence label = state.label;
        if (label != null && state.labelWidth > 0) {
            float centerY;
            float face;
            float scale;
            int entryColor;
            float textX = 0.0F;
            if (!plate) {
                centerY = height + BANNER_HEIGHT * 0.5F;
                face = BANNER_DEPTH * 0.5F + FACE_GAP;
                float room = width - 2.0F * BANNER_CAP - 2.0F * TEXT_MARGIN;
                scale = Math.min(BANNER_TEXT_HEIGHT / GLYPH_HEIGHT, room / state.labelWidth);
                entryColor = LIGHT_WHITE;
            } else {
                centerY = plateY + PLATE_HEIGHT * 0.5F;
                textX = plateX;
                face = PLATE_DEPTH * 0.5F + FACE_GAP;
                scale = Math.min(NUMBER_HEIGHT / GLYPH_HEIGHT, (plateHalf * 2.0F - 2.0F * TEXT_MARGIN) / state.labelWidth);
                entryColor = ARGB.opaque(color);
            }
            boolean towardWall = state.mount == GateMount.WALL_DIVE;
            this.text(poseStack, collector, label, state.labelWidth, textX, centerY, -face, true, towardWall, scale, entryColor);
            this.text(poseStack, collector, label, state.labelWidth, textX, centerY, face, false, towardWall,
                    scale * (plate ? EXIT_TEXT_SCALE : 1.0F), EXIT_TEXT_COLOR);
        }
        poseStack.popPose();
    }

    public static float plateWidth(GateSize size) {
        return size == GateSize.SMALL ? 0.9F : 1.0F;
    }

    void text(PoseStack poseStack, SubmitNodeCollector collector, FormattedCharSequence label, int labelWidth, float x,
                      float centerY, float z, boolean entry, boolean upsideDown, float scale, int color) {
        poseStack.pushPose();
        poseStack.translate(x, centerY, z);
        if (upsideDown) {
            poseStack.rotateDegrees(Axis.ZP, 180.0F);
        }
        if (entry) {
            poseStack.rotateDegrees(Axis.YP, 180.0F);
        }
        poseStack.scale(scale, -scale, scale);
        collector.submitText(poseStack, -labelWidth * 0.5F, -GLYPH_HEIGHT * 0.5F, label, false, Font.DisplayMode.POLYGON_OFFSET,
                LightCoordsUtil.FULL_BRIGHT, color, 0, 0);
        poseStack.popPose();
    }

    public static float chevronBrightness(int fromFar, float ageInTicks) {
        float wave = ageInTicks * CHEVRON_WAVE + fromFar / (float) CHEVRONS;
        wave -= (float) Math.floor(wave);
        float fall = 1.0F - wave;
        return CHEVRON_MIN + (1.0F - CHEVRON_MIN) * fall * fall;
    }

    public static float chevronFloor(GateSize size, GateStacking.Base base) {
        return base == GateStacking.Base.STACKED ? size.frame() : 0.0F;
    }

    private static void floorChevrons(PoseStack.Pose pose, VertexConsumer buffer, float inner, float floor, int color, float age) {
        float lift = floor + CHEVRON_LIFT;
        float half = Math.min(inner * 0.55F, 1.1F);
        for (int i = 0; i < CHEVRONS; i++) {
            float center = -(CHEVRON_FIRST + CHEVRON_STEP * i);
            float tip = center + CHEVRON_HALF_LENGTH;
            float back = center - CHEVRON_HALF_LENGTH;
            int shade = ARGB.color(chevronBrightness(CHEVRONS - 1 - i, age), color);
            for (int side = -1; side <= 1; side += 2) {
                float x = half * side;
                quad(pose, buffer,
                        x, lift, back, shade,
                        x, lift, back + CHEVRON_THICKNESS, shade,
                        0.0F, lift, tip + CHEVRON_THICKNESS, shade,
                        0.0F, lift, tip, shade);
            }
        }
    }

    private static void exitMarks(PoseStack.Pose pose, VertexConsumer buffer, float inner, float frame, float height) {
        int red = ARGB.color(EXIT_ALPHA, EXIT_COLOR);
        float z = CORNER_HALF_DEPTH + FACE_GAP;
        float y = height - frame * 0.5F;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * (inner + frame * 0.5F);
            quad(pose, buffer,
                    x - EXIT_MARK, y - EXIT_MARK, z, red,
                    x + EXIT_MARK, y - EXIT_MARK, z, red,
                    x + EXIT_MARK, y + EXIT_MARK, z, red,
                    x - EXIT_MARK, y + EXIT_MARK, z, red);
        }
    }

    private static void exitBar(PoseStack.Pose pose, VertexConsumer buffer, float center, float plateHalf, float height, float z) {
        int red = ARGB.color(EXIT_ALPHA, EXIT_COLOR);
        float x = plateHalf - RIM;
        quad(pose, buffer,
                center - x, height + RIM, z, red,
                center + x, height + RIM, z, red,
                center + x, height + RIM * 2.0F, z, red,
                center - x, height + RIM * 2.0F, z, red);
    }

    private static void plateRim(PoseStack.Pose pose, VertexConsumer buffer, float center, float plateHalf, float height, float z, int color) {
        int rim = ARGB.opaque(color);
        float x0 = center - plateHalf;
        float x1 = center + plateHalf;
        float y0 = height;
        float y1 = height + PLATE_HEIGHT;
        frameBand(pose, buffer, x0, y0, x1, y1, RIM, z, rim, rim);
    }

    private static void halo(PoseStack.Pose pose, VertexConsumer buffer, float inner, float frame, float height, float z, int color,
                             float age) {
        float pulse = 0.8F + 0.2F * (float) Math.sin(age * 0.2F);
        int outer = ARGB.color(HALO_ALPHA * pulse, color);
        int fade = ARGB.color(0.0F, color);
        frameBand(pose, buffer, -inner, frame, inner, height - frame, HALO_WIDTH, z, outer, fade);
    }

    static void frameBand(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float band, float z,
                                  int outerColor, int innerColor) {
        float ix0 = x0 + band;
        float iy0 = y0 + band;
        float ix1 = x1 - band;
        float iy1 = y1 - band;
        quad(pose, buffer, x0, y1, z, outerColor, x1, y1, z, outerColor, ix1, iy1, z, innerColor, ix0, iy1, z, innerColor);
        quad(pose, buffer, ix0, iy0, z, innerColor, ix1, iy0, z, innerColor, x1, y0, z, outerColor, x0, y0, z, outerColor);
        quad(pose, buffer, x0, y0, z, outerColor, ix0, iy0, z, innerColor, ix0, iy1, z, innerColor, x0, y1, z, outerColor);
        quad(pose, buffer, ix1, iy0, z, innerColor, x1, y0, z, outerColor, x1, y1, z, outerColor, ix1, iy1, z, innerColor);
    }

    static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float ax, float ay, float az, int ac, float bx, float by, float bz, int bc,
                             float cx, float cy, float cz, int cc, float dx, float dy, float dz, int dc) {
        RaceLineRenderer.vertex(pose, buffer, ax, ay, az, ac);
        RaceLineRenderer.vertex(pose, buffer, bx, by, bz, bc);
        RaceLineRenderer.vertex(pose, buffer, cx, cy, cz, cc);
        RaceLineRenderer.vertex(pose, buffer, dx, dy, dz, dc);
        RaceLineRenderer.vertex(pose, buffer, dx, dy, dz, dc);
        RaceLineRenderer.vertex(pose, buffer, cx, cy, cz, cc);
        RaceLineRenderer.vertex(pose, buffer, bx, by, bz, bc);
        RaceLineRenderer.vertex(pose, buffer, ax, ay, az, ac);
    }

    private static void submitFallback(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        GateSize size = state.size;
        GateGeometry frame = new GateGeometry(0.0, 0.0, 0.0, 0.0F, state.width, state.height, size.frame(), size.depth());
        float inset = size.frame() * LIGHT_INSET;
        GateGeometry lights = new GateGeometry(0.0, inset, 0.0, 0.0F, state.width - 2.0F * inset, state.height - 2.0F * inset,
                size.frame() - 2.0F * inset, size.depth() + 0.03F);
        RenderType type = solidType();
        int frameLight = state.lightCoords;
        int lightColor = state.lightColor;
        GateStacking.Base base = state.base;
        poseStack.pushPose();
        toFrame(state, poseStack);
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            frame.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> BoxDraw.box(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, FRAME_COLOR, frameLight),
                    base);
            lights.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> BoxDraw.box(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, lightColor,
                    LightCoordsUtil.FULL_BRIGHT), base);
        });
        poseStack.popPose();
    }

    private @Nullable Fit fit(GateSize size, ItemStackRenderState frame) {
        Fit cached = this.fits[size.ordinal()];
        if (cached != null) {
            return cached;
        }
        if (frame.isEmpty()) {
            return null;
        }
        AABB box = frame.getModelBoundingBox();
        double sizeX = box.getXsize();
        double sizeY = box.getYsize();
        double sizeZ = box.getZsize();
        if (sizeY <= 1.0E-4 || Math.max(sizeX, sizeZ) <= 1.0E-4) {
            return null;
        }
        boolean rotate = sizeZ > sizeX * 1.5;
        double width = rotate ? sizeZ : sizeX;
        float scale = (float) Math.min(size.width() / width, size.height() / sizeY);
        Fit fit = new Fit(scale, (float) ((box.minX + box.maxX) * 0.5), (float) box.minY, (float) ((box.minZ + box.maxZ) * 0.5), rotate);
        this.fits[size.ordinal()] = fit;
        return fit;
    }

    private enum Part {
        FRAME,
        LIGHTS,
        START_FRAME,
        BANNER,
        PLATE,
        HANGER
    }

    private ItemStack stack(GateSize size, Part part, GateStacking.Base base, int color) {
        boolean lights = part == Part.LIGHTS;
        long rgb = color & 0xFFFFFF;
        long key = ((long) size.ordinal() << 34) | ((long) base.ordinal() << 30) | ((long) part.ordinal() << 26) | (lights ? rgb : 0L);
        return this.stacks.computeIfAbsent(key, k -> {
            ItemStack stack = new ItemStack(Items.PAPER);
            Identifier model = switch (part) {
                case FRAME -> frameModelId(size, base);
                case LIGHTS -> lightsModelId(size, base);
                case START_FRAME -> startFrameModelId(size, base);
                case BANNER -> bannerModelId(size);
                case PLATE -> mountPlateModelId();
                case HANGER -> hangerModelId();
            };
            stack.set(DataComponents.ITEM_MODEL, model);
            if (lights) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of((int) rgb)));
            }
            return stack;
        });
    }

    static int lightColor(GateEntity gate, float ageInTicks) {
        if (!gate.inCourse()) {
            return LIGHT_IDLE;
        }
        ClientRaceTracker tracker = ClientRaceTracker.INSTANCE;
        boolean ownCourse = gate.courseId() == tracker.courseId();
        if (ownCourse && tracker.tracker().racing()) {
            return gate.gateIndex() == tracker.nextGateIndex() ? LIGHT_GREEN : LIGHT_WHITE;
        }
        if (ownCourse && tracker.countdownActive()) {
            if (gate.gateIndex() != tracker.startGateIndex()) {
                return LIGHT_WHITE;
            }
            return tracker.tracker().falseStarted() ? LIGHT_AMBER : LIGHT_RED;
        }
        if (ownCourse && tracker.tracker().phase() == LapTracker.Phase.IDLE) {
            return gate.gateIndex() == tracker.startGateIndex() ? LIGHT_GREEN : LIGHT_WHITE;
        }
        if (FpvView.spectating()) {
            return gate.gateIndex() == 0 ? LIGHT_GREEN : LIGHT_WHITE;
        }
        if (gate.gateIndex() == 0 && RaceConfig.CONFIG.get().startHint) {
            return pulse(ageInTicks, 0.18F);
        }
        return LIGHT_WHITE;
    }

    private static int pulse(float ageInTicks, float speed) {
        float wave = 0.5F + 0.5F * (float) Math.sin(ageInTicks * speed);
        float step = Math.round(wave * PULSE_STEPS) / (float) PULSE_STEPS;
        return ARGB.srgbLerp(step, LIGHT_WHITE, LIGHT_GREEN);
    }

    private static boolean holdingCourseTool() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (CourseToolItem.isCourseTool(player.getMainHandItem()) || CourseToolItem.isCourseTool(player.getOffhandItem()));
    }

    private static int heldCourse() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return GateEntity.NO_COURSE;
        }
        ItemStack main = player.getMainHandItem();
        return CourseToolItem.isCourseTool(main) ? CourseToolItem.courseOf(main) : CourseToolItem.courseOf(player.getOffhandItem());
    }

    @Override
    protected boolean shouldShowName(GateEntity entity, double distanceToCameraSq) {
        return holdingCourseTool() || super.shouldShowName(entity, distanceToCameraSq);
    }

    @Override
    protected @Nullable Component getNameTag(GateEntity entity) {
        if (!holdingCourseTool()) {
            return super.getNameTag(entity);
        }
        if (entity.kind() == CourseElement.GATE_POLE) {
            return Component.translatable("propwash.race.pole.label", GateMounting.elementName(entity.kind()),
                    GateMounting.variantName(entity.geometry())).withStyle(ChatFormatting.GRAY);
        }
        if (!entity.inCourse()) {
            return entity.element() ? Component.translatable("propwash.race.element.label_none", GateMounting.elementName(entity.kind()),
                    GateMounting.variantName(entity.geometry())).withStyle(ChatFormatting.GRAY)
                    : Component.translatable("propwash.race.gate.label_none").withStyle(ChatFormatting.GRAY);
        }
        MutableComponent label = entity.element()
                ? Component.translatable("propwash.race.element.label", GateMounting.elementName(entity.kind()), entity.gateIndex() + 1,
                entity.courseId(), GateMounting.variantName(entity.geometry()))
                : Component.translatable("propwash.race.gate.label", entity.gateIndex() + 1, entity.courseId());
        return label.withStyle(entity.courseId() == heldCourse() ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }
}
