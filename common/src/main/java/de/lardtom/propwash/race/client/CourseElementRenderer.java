package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.race.GateTilt;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CourseElementRenderer {
    public static final int FORBIDDEN_COLOR = 0xFFFF3B30;
    public static final float PLATE_WIDTH = 0.7F;
    public static final float PLATE_HEIGHT = 0.5F;
    public static final float PLATE_DEPTH = 0.08F;
    public static final float PLATE_LIFT = 0.25F;
    public static final float NUMBER_HEIGHT = 0.36F;
    public static final int ARC_CHEVRONS = 6;
    private static final float CHEVRON_HALF = 0.42F;
    private static final float CHEVRON_LENGTH = 0.28F;
    private static final float CHEVRON_THICKNESS = 0.18F;
    private static final float LIFT = 0.035F;
    private static final float WAVE = 0.05F;
    private static final float MIN_GLOW = 0.35F;
    private static final float CROSS = 0.32F;
    private static final float CROSS_WIDTH = 0.07F;
    private static final float HALO = 0.22F;
    private static final float GAP = 0.012F;
    private static final double EPSILON = 1.0E-4;
    private static final float GLYPH_HEIGHT = 7.0F;

    private final ItemModelResolver items;
    private final boolean present;
    private final Map<Long, ItemStack> stacks = new HashMap<>();

    public enum Piece {
        SLALOM_TUBE("slalom_tube", false),
        SLALOM_TUBE_HALF("slalom_tube_half", false),
        FLAG_TUBE("flag_tube", false),
        FLAG_TUBE_HALF("flag_tube_half", false),
        POLE_BASE("pole_base", false),
        POLE_LIGHT("pole_light", true),
        FLAG_CLOTH_LEFT("flag_cloth_left", false),
        FLAG_CLOTH_RIGHT("flag_cloth_right", false),
        CUBE_BAR("cube_bar", false),
        CUBE_BAR_HALF("cube_bar_half", false),
        CUBE_POST("cube_post", false),
        CUBE_POST_HALF("cube_post_half", false),
        CUBE_CORNER("cube_corner", false),
        CUBE_LED("cube_led", true),
        CUBE_LED_HALF("cube_led_half", true);

        private final String key;
        private final boolean tinted;

        Piece(String key, boolean tinted) {
            this.key = key;
            this.tinted = tinted;
        }

        public String key() {
            return this.key;
        }

        public boolean tinted() {
            return this.tinted;
        }

        public Identifier modelId() {
            return Constants.id("race/course_" + this.key);
        }

        boolean needed(CourseElement kind, int variant) {
            return switch (kind) {
                case POLE -> this == SLALOM_TUBE || this == SLALOM_TUBE_HALF || this == POLE_BASE || this == POLE_LIGHT;
                case FLAG -> this == FLAG_TUBE || this == FLAG_TUBE_HALF || this == POLE_BASE || this == POLE_LIGHT
                        || this == (variant == CourseElement.LEFT ? FLAG_CLOTH_LEFT : FLAG_CLOTH_RIGHT);
                case CUBE -> this.key.startsWith("cube_");
                case GATE, FREEFORM, GATE_POLE -> false;
            };
        }
    }

    CourseElementRenderer(ItemModelResolver items, ResourceManager resources) {
        this.items = items;
        boolean all = true;
        for (Piece piece : Piece.values()) {
            all &= resources.getResource(Constants.id("models/course/" + piece.key() + ".json")).isPresent();
        }
        this.present = all;
    }

    void fill(GateRenderState state, GateGeometry local, int lightColor, @Nullable Level level, boolean fitted) {
        state.kind = local.kind();
        state.variant = local.variant();
        state.elementFrame = (float) local.frame();
        state.bannerHeight = (float) local.bannerHeight();
        state.tiled = false;
        state.startModelled = false;
        state.modelled = this.present && fitted;
        for (Piece piece : Piece.values()) {
            ItemStackRenderState out = state.elementPieces[piece.ordinal()];
            if (!state.modelled || !piece.needed(state.kind, state.variant)) {
                out.clear();
                continue;
            }
            this.items.updateForTopItem(out, this.stack(piece, piece.tinted() ? lightColor : 0), ItemDisplayContext.NONE, level, null, 0);
        }
    }

    private ItemStack stack(Piece piece, int color) {
        long rgb = color & 0xFFFFFF;
        long key = ((long) piece.ordinal() << 26) | (piece.tinted() ? rgb : 0L);
        return this.stacks.computeIfAbsent(key, k -> {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.ITEM_MODEL, piece.modelId());
            if (piece.tinted()) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of((int) rgb)));
            }
            return stack;
        });
    }

    static void submitBody(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        GateRenderer.toFrame(state, poseStack);
        if (!state.modelled) {
            fallback(state, poseStack, collector);
        } else if (state.kind == CourseElement.CUBE) {
            cube(state, poseStack, collector);
        } else {
            pole(state, poseStack, collector);
        }
        poseStack.popPose();
    }

    private static void piece(GateRenderState state, Piece piece, float x, float y, float z, float yaw, float stretch, int light, PoseStack poseStack,
                              SubmitNodeCollector collector) {
        ItemStackRenderState out = state.elementPieces[piece.ordinal()];
        if (out.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        if (yaw != 0.0F) {
            poseStack.rotateDegrees(Axis.YP, yaw);
        }
        if (stretch != 1.0F) {
            poseStack.scale(1.0F, stretch, 1.0F);
        }
        poseStack.scale(state.fitScale, state.fitScale, state.fitScale);
        poseStack.translate(-state.fitX, -state.fitBottom, -state.fitZ);
        out.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    private static void pole(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        boolean flag = state.kind == CourseElement.FLAG;
        Piece whole = flag ? Piece.FLAG_TUBE : Piece.SLALOM_TUBE;
        Piece half = flag ? Piece.FLAG_TUBE_HALF : Piece.SLALOM_TUBE_HALF;
        int light = state.lightCoords;
        float height = state.height;
        for (float y = 0.0F; y < height - EPSILON; ) {
            boolean full = height - y >= 1.0F - EPSILON;
            piece(state, full ? whole : half, 0.0F, y, 0.0F, 0.0F, 1.0F, light, poseStack, collector);
            y += full ? 1.0F : 0.5F;
        }
        piece(state, Piece.POLE_BASE, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, light, poseStack, collector);
        piece(state, Piece.POLE_LIGHT, 0.0F, height, 0.0F, 0.0F, 1.0F, LightCoordsUtil.FULL_BRIGHT, poseStack, collector);
        if (flag) {
            float top = height - (float) CourseElement.BANNER_TOP_GAP;
            float bottom = top - state.bannerHeight;
            Piece cloth = state.variant == CourseElement.LEFT ? Piece.FLAG_CLOTH_LEFT : Piece.FLAG_CLOTH_RIGHT;
            piece(state, cloth, 0.0F, bottom, 0.0F, 0.0F, state.bannerHeight / 2.0F, light, poseStack, collector);
        }
    }

    private static void cube(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        int light = state.lightCoords;
        int glow = LightCoordsUtil.FULL_BRIGHT;
        float half = state.width * 0.5F;
        float bar = state.elementFrame;
        float height = state.height;
        float edge = half - bar * 0.5F;
        float inner = half - bar;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                piece(state, Piece.CUBE_CORNER, sx * edge, bar * 0.5F, sz * edge, 0.0F, 1.0F, light, poseStack, collector);
                piece(state, Piece.CUBE_CORNER, sx * edge, height - bar * 0.5F, sz * edge, 0.0F, 1.0F, light, poseStack, collector);
                for (float y = bar; y < height - bar - EPSILON; ) {
                    boolean full = height - bar - y >= 1.0F - EPSILON;
                    piece(state, full ? Piece.CUBE_POST : Piece.CUBE_POST_HALF, sx * edge, y, sz * edge, 0.0F, 1.0F, light, poseStack, collector);
                    y += full ? 1.0F : 0.5F;
                }
            }
        }
        for (float level : new float[]{bar * 0.5F, height - bar * 0.5F}) {
            boolean top = level > height * 0.5F;
            for (int s = -1; s <= 1; s += 2) {
                for (float a = -inner; a < inner - EPSILON; ) {
                    boolean full = inner - a >= 1.0F - EPSILON;
                    piece(state, full ? Piece.CUBE_BAR : Piece.CUBE_BAR_HALF, a, level, s * edge, 0.0F, 1.0F, light, poseStack, collector);
                    piece(state, full ? Piece.CUBE_BAR : Piece.CUBE_BAR_HALF, s * edge, level, a, -90.0F, 1.0F, light, poseStack, collector);
                    if (top) {
                        piece(state, full ? Piece.CUBE_LED : Piece.CUBE_LED_HALF, a, level, s * edge, 0.0F, 1.0F, glow, poseStack, collector);
                        piece(state, full ? Piece.CUBE_LED : Piece.CUBE_LED_HALF, s * edge, level, a, -90.0F, 1.0F, glow, poseStack, collector);
                    }
                    a += full ? 1.0F : 0.5F;
                }
            }
        }
    }

    private static void fallback(GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        GateGeometry local = GateGeometry.element(0.0, 0.0, 0.0, 0.0F, state.kind, state.variant, state.width, state.height, GateTilt.NONE);
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, GateRenderer.solidType(), (pose, buffer) -> local.boxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                BoxDraw.box(pose, buffer, minX, minY, minZ, maxX, maxY, maxZ, GateRenderer.FRAME_COLOR, light), GateStacking.Base.MOUNTED));
    }

    static float exitYaw(int exit) {
        return switch (exit) {
            case CourseElement.EXIT_LEFT -> 90.0F;
            case CourseElement.EXIT_BACK -> 180.0F;
            case CourseElement.EXIT_RIGHT -> -90.0F;
            default -> 0.0F;
        };
    }

    static void arrowFrame(GateRenderState state, PoseStack poseStack) {
        float sign = state.variant == CourseElement.LEFT ? 1.0F : -1.0F;
        switch (state.kind) {
            case CUBE -> poseStack.rotateDegrees(Axis.YP, exitYaw(state.variant));
            case POLE -> poseStack.translate(sign * lineRadius(state), 0.0F, 0.0F);
            case FLAG -> poseStack.translate(-sign * lineRadius(state), 0.0F, 0.0F);
            case GATE, FREEFORM -> {
            }
        }
    }

    static float lineRadius(GateRenderState state) {
        float t = state.elementFrame * 0.5F;
        if (state.kind == CourseElement.FLAG) {
            return t + Math.max(1.0F, Math.min(2.2F, state.width * 0.35F));
        }
        return t + Math.max(0.8F, Math.min(2.0F, state.width * 0.35F));
    }

    void submitMarkings(GateRenderer renderer, GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        int color = state.lightColor;
        float age = state.ageInTicks;
        poseStack.pushPose();
        GateRenderer.toFrame(state, poseStack);
        switch (state.kind) {
            case POLE -> {
                float sign = state.variant == CourseElement.LEFT ? 1.0F : -1.0F;
                float side = sign * lineRadius(state);
                collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> {
                    for (int i = 0; i < GateRenderer.CHEVRONS; i++) {
                        float z = -(0.9F + 0.85F * i);
                        chevron(pose, buffer, side, LIFT, z, 0.0F, 1.0F, ARGB.color(brightness(GateRenderer.CHEVRONS - 1 - i, age), color));
                    }
                    cross(pose, buffer, -side, LIFT, 0.0F);
                });
            }
            case FLAG -> {
                float sign = state.variant == CourseElement.LEFT ? 1.0F : -1.0F;
                float radius = lineRadius(state);
                collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> {
                    for (int i = 0; i < ARC_CHEVRONS; i++) {
                        double u = (i + 0.5) / ARC_CHEVRONS;
                        double phi = Math.PI * (1.0 - u);
                        float x = (float) (sign * radius * Math.cos(phi));
                        float z = (float) (radius * Math.sin(phi));
                        float dx = (float) (sign * Math.sin(phi));
                        float dz = (float) (-Math.cos(phi));
                        chevron(pose, buffer, x, LIFT, z, dx, dz, ARGB.color(brightness(ARC_CHEVRONS - 1 - i, age), color));
                    }
                    chevron(pose, buffer, -sign * radius, LIFT, -0.9F, 0.0F, 1.0F, ARGB.color(brightness(ARC_CHEVRONS, age), color));
                    cross(pose, buffer, sign * radius, LIFT, -0.9F);
                });
            }
            case CUBE -> {
                float half = state.width * 0.5F;
                float bar = state.elementFrame;
                float height = state.height;
                float inner = half - bar;
                poseStack.pushPose();
                poseStack.rotateDegrees(Axis.YP, exitYaw(state.variant));
                int outer = ARGB.color(0.65F, color);
                int fade = ARGB.color(0.0F, color);
                collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> {
                    float face = half + GAP;
                    GateRenderer.frameBand(pose, buffer, -inner, bar, inner, height - bar, HALO, face, outer, fade);
                    GateRenderer.frameBand(pose, buffer, -inner, bar, inner, height - bar, HALO, face - bar - 2.0F * GAP, outer, fade);
                    for (int i = 0; i < GateRenderer.CHEVRONS; i++) {
                        float z = half + 0.7F + 0.85F * i;
                        chevron(pose, buffer, 0.0F, LIFT, z, 0.0F, 1.0F, ARGB.color(brightness(GateRenderer.CHEVRONS - 1 - i, age), color));
                    }
                });
                poseStack.popPose();
                collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> {
                    for (int i = 0; i < 2; i++) {
                        float y = height + 1.3F - 0.45F * i;
                        int shade = ARGB.color(brightness(1 - i, age), color);
                        dive(pose, buffer, y, shade, false);
                        dive(pose, buffer, y, shade, true);
                    }
                });
            }
            case GATE, FREEFORM -> {
            }
        }
        if (state.marking != GateRenderer.Marking.NONE && state.label != null && state.labelWidth > 0) {
            this.plate(renderer, state, poseStack, collector);
        }
        poseStack.popPose();
    }

    private void plate(GateRenderer renderer, GateRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        float centerX = 0.0F;
        float bottom;
        float centerZ = 0.0F;
        if (state.kind == CourseElement.CUBE) {
            bottom = state.height;
            centerZ = -(state.width * 0.5F - state.elementFrame * 0.5F);
        } else {
            bottom = state.height + PLATE_LIFT;
        }
        float plateHalf = PLATE_WIDTH * 0.5F;
        float top = bottom + PLATE_HEIGHT;
        int light = state.lightCoords;
        int color = state.lightColor;
        float z = centerZ;
        collector.submitCustomGeometry(poseStack, GateRenderer.solidType(), (pose, buffer) -> BoxDraw.box(pose, buffer, centerX - plateHalf, bottom,
                z - PLATE_DEPTH * 0.5F, centerX + plateHalf, top, z + PLATE_DEPTH * 0.5F, GateRenderer.FRAME_COLOR, light));
        int rim = ARGB.opaque(color);
        collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> GateRenderer.frameBand(pose, buffer,
                centerX - plateHalf, bottom, centerX + plateHalf, top, 0.05F, z - PLATE_DEPTH * 0.5F - GAP, rim, rim));
        float scale = Math.min(NUMBER_HEIGHT / GLYPH_HEIGHT, (PLATE_WIDTH - 0.12F) / state.labelWidth);
        float middle = bottom + PLATE_HEIGHT * 0.5F;
        renderer.text(poseStack, collector, state.label, state.labelWidth, centerX, middle, z - PLATE_DEPTH * 0.5F - GAP, true, false, scale,
                ARGB.opaque(color));
        renderer.text(poseStack, collector, state.label, state.labelWidth, centerX, middle, z + PLATE_DEPTH * 0.5F + GAP, false, false, scale * 0.7F,
                GateRenderer.EXIT_TEXT_COLOR);
    }

    static float brightness(int fromFar, float age) {
        float wave = age * WAVE + fromFar / (float) GateRenderer.CHEVRONS;
        wave -= (float) Math.floor(wave);
        float fall = 1.0F - wave;
        return MIN_GLOW + (1.0F - MIN_GLOW) * fall * fall;
    }

    static void chevron(PoseStack.Pose pose, VertexConsumer buffer, float cx, float y, float cz, float dx, float dz, int color) {
        chevron(pose, buffer, cx, y, cz, dx, dz, color, CHEVRON_HALF);
    }

    static void chevron(PoseStack.Pose pose, VertexConsumer buffer, float cx, float y, float cz, float dx, float dz, int color, float halfWidth) {
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-6F) {
            return;
        }
        float fx = dx / length;
        float fz = dz / length;
        float sx = fz;
        float sz = -fx;
        float tipX = cx + fx * CHEVRON_LENGTH;
        float tipZ = cz + fz * CHEVRON_LENGTH;
        for (int side = -1; side <= 1; side += 2) {
            float bx = cx - fx * CHEVRON_LENGTH + sx * halfWidth * side;
            float bz = cz - fz * CHEVRON_LENGTH + sz * halfWidth * side;
            GateRenderer.quad(pose, buffer,
                    bx, y, bz, color,
                    bx + fx * CHEVRON_THICKNESS, y, bz + fz * CHEVRON_THICKNESS, color,
                    tipX + fx * CHEVRON_THICKNESS, y, tipZ + fz * CHEVRON_THICKNESS, color,
                    tipX, y, tipZ, color);
        }
    }

    static void cross(PoseStack.Pose pose, VertexConsumer buffer, float cx, float y, float cz) {
        int red = ARGB.color(0.8F, FORBIDDEN_COLOR);
        for (int side = -1; side <= 1; side += 2) {
            float ax = cx - CROSS;
            float az = cz - CROSS * side;
            float bx = cx + CROSS;
            float bz = cz + CROSS * side;
            float nx = CROSS_WIDTH * side;
            float nz = -CROSS_WIDTH;
            GateRenderer.quad(pose, buffer,
                    ax - nx, y, az - nz, red,
                    ax + nx, y, az + nz, red,
                    bx + nx, y, bz + nz, red,
                    bx - nx, y, bz - nz, red);
        }
    }

    private static void dive(PoseStack.Pose pose, VertexConsumer buffer, float y, int color, boolean alongZ) {
        float half = 0.45F;
        float drop = 0.35F;
        float thick = 0.14F;
        for (int side = -1; side <= 1; side += 2) {
            float ax = alongZ ? 0.0F : half * side;
            float az = alongZ ? half * side : 0.0F;
            GateRenderer.quad(pose, buffer,
                    ax, y, az, color,
                    ax, y - thick, az, color,
                    0.0F, y - drop - thick, 0.0F, color,
                    0.0F, y - drop, 0.0F, color);
        }
    }
}
