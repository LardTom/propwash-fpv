package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.render.AfterWaterRenderTypes;
import de.lardtom.propwash.drone.client.render.DroneGhost;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.FreeformGateItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GatePoleItem;
import de.lardtom.propwash.race.GatePoleLinks;
import de.lardtom.propwash.race.GatePoles;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateStacking;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class GatePreview {
    public static final int FREE_TINT = 0x8CFFFFFF;
    public static final int BLOCKED_TINT = 0x8CFF4A40;
    public static final int FREE_ARROW = 0xE033FF66;
    public static final int BLOCKED_ARROW = 0xE0FF3B30;
    private static final float ARROW_HALF = 1.1F;
    private static final float SHAFT = 0.07F;
    private static final float HEAD = 0.32F;
    private static final float HEAD_LENGTH = 0.55F;
    private static final int HINT_BOTTOM = 76;
    private static final int HINT_PAD = 6;
    private static final GateRenderState STATE = new GateRenderState();

    private GatePreview() {
    }

    public record Target(GateGeometry geometry, GateItem item, boolean free, GateMount.Surface surface, boolean allowed, boolean seated) {
        public Target(GateGeometry geometry, GateItem item, boolean free, GateMount.Surface surface, boolean allowed) {
            this(geometry, item, free, surface, allowed, false);
        }
    }

    public static boolean visible() {
        return target(Minecraft.getInstance()) != null || FreeformPreview.visible();
    }

    public static GatePlacement.@Nullable Click click(LocalPlayer player, BlockHitResult hit) {
        InteractionHand hand = GateMountKeys.gateHand(player);
        if (hand == null) {
            return null;
        }
        BlockPlaceContext place = new BlockPlaceContext(player, hand, player.getItemInHand(hand), hit);
        return GateItem.click(place, hit.getBlockPos(), hit.getDirection(), hit.getLocation(), player.getEyePosition(), player.getYRot());
    }

    public static @Nullable Target target(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.gui.hud.isHidden() || player.isSpectator() || PilotSession.INSTANCE.isActive()
                || GateEditor.active()) {
            return null;
        }
        InteractionHand hand = GateMountKeys.gateHand(player);
        if (hand != null && player.getItemInHand(hand).getItem() instanceof GatePoleItem pole && minecraft.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof GateEntity gate) {
            return seated(player, level, pole, gate, entityHit.getLocation());
        }
        if (hand == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (!(player.getItemInHand(hand).getItem() instanceof GateItem item) || item instanceof FreeformGateItem) {
            return null;
        }
        GatePlacement.Click click = click(player, hit);
        if (click == null) {
            return null;
        }
        GateGeometry geometry = item.placement(click, GateMountKeys.preference());
        boolean allowed = player.mayBuild() && item.allowed(click);
        boolean free = allowed && GateItem.insideWorld(level, geometry) && GateItem.free(level, geometry, GateStacking.placedBase(geometry), false);
        return new Target(geometry, item, free, click.surface(), allowed);
    }

    private static @Nullable Target seated(LocalPlayer player, ClientLevel level, GatePoleItem item, GateEntity gate, Vec3 at) {
        GatePoleLinks.Target found = GatePoleLinks.target(gate, at.x, at.z);
        if (found == null) {
            return null;
        }
        GatePlacement.Preference preference = GateMountKeys.preference();
        GateGeometry geometry = GatePoles.onSeat(found.seat(), preference.variant(CourseElement.GATE_POLE), GatePoles.DEFAULT_LENGTH);
        boolean allowed = player.mayBuild();
        boolean free = allowed && found.refusal() == GatePoles.Refusal.NONE && GatePoleItem.fits(level, geometry);
        return new Target(geometry, item, free, GateMount.Surface.FLOOR, allowed, true);
    }

    static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        Minecraft minecraft = Minecraft.getInstance();
        Target target = target(minecraft);
        GateRenderer renderer = GateRenderer.current();
        if (target == null) {
            FreeformPreview.submit(collector, poseStack, levelState);
            return;
        }
        if (renderer == null) {
            return;
        }
        GateGeometry gate = target.geometry();
        if (target.seated()) {
            submitTinted(renderer, gate, target.item().size(), target.free() ? FREE_TINT : BLOCKED_TINT, 0, GateStacking.Base.STACKED, minecraft.level,
                    collector, poseStack, levelState.cameraRenderState.pos);
            return;
        }
        submitGhost(renderer, gate, target.item(), target.free(), minecraft.level, collector, poseStack, levelState.cameraRenderState.pos);
    }

    public static void submitGhost(GateRenderer renderer, GateGeometry gate, GateItem item, boolean free, @Nullable ClientLevel level,
                                   SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera) {
        submitGhost(renderer, gate, item.size(), free, level, collector, poseStack, camera);
    }

    public static void submitGhost(GateRenderer renderer, GateGeometry gate, GateSize size, boolean free, @Nullable ClientLevel level,
                                   SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera) {
        submitTinted(renderer, gate, size, free ? FREE_TINT : BLOCKED_TINT, free ? FREE_ARROW : BLOCKED_ARROW, level, collector, poseStack, camera);
    }

    public static void submitTinted(GateRenderer renderer, GateGeometry gate, GateSize size, int tint, int arrowColor, @Nullable ClientLevel level,
                                    SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera) {
        submitTinted(renderer, gate, size, tint, arrowColor, ghostBase(gate), level, collector, poseStack, camera);
    }

    public static void submitTinted(GateRenderer renderer, GateGeometry gate, GateSize size, int tint, int arrowColor, GateStacking.Base base,
                                    @Nullable ClientLevel level, SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera) {
        fillGhost(renderer, gate, size, base, level);
        poseStack.pushPose();
        poseStack.translate(gate.x() - camera.x, gate.y() - camera.y, gate.z() - camera.z);
        renderer.submitBody(STATE, poseStack, DroneGhost.tinted(collector, tint));
        if (arrowColor != 0 && gate.kind().courseable()) {
            arrowAt(gate, arrowColor, collector, poseStack);
        }
        poseStack.popPose();
    }

    public static void submitArrow(GateRenderer renderer, GateGeometry gate, GateSize size, int color, @Nullable ClientLevel level,
                                   SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera) {
        if (!gate.kind().courseable()) {
            return;
        }
        fillGhost(renderer, gate, size, ghostBase(gate), level);
        poseStack.pushPose();
        poseStack.translate(gate.x() - camera.x, gate.y() - camera.y, gate.z() - camera.z);
        arrowAt(gate, color, collector, poseStack);
        poseStack.popPose();
    }

    private static GateStacking.Base ghostBase(GateGeometry gate) {
        return GatePoles.pole(gate) ? GatePoles.base(gate.mount(), false) : GateStacking.placedBase(gate);
    }

    private static void fillGhost(GateRenderer renderer, GateGeometry gate, GateSize size, GateStacking.Base base, @Nullable ClientLevel level) {
        renderer.fill(STATE, gate.at(0.0, 0.0, 0.0), size, base, false, GateRenderer.Marking.NONE, 0,
                GateRenderer.LIGHT_WHITE, level);
        STATE.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        STATE.outlineColor = 0;
    }

    private static void arrowAt(GateGeometry gate, int color, SubmitNodeCollector collector, PoseStack poseStack) {
        poseStack.pushPose();
        GateRenderer.toFrame(STATE, poseStack);
        if (!gate.gate()) {
            CourseElementRenderer.arrowFrame(STATE, poseStack);
        }
        poseStack.translate(0.0F, (float) gate.height() * 0.5F, 0.0F);
        collector.submitCustomGeometry(poseStack, AfterWaterRenderTypes.ghost(BoxDraw.WHITE_TEXTURE), (pose, buffer) -> arrow(pose, buffer, color));
        poseStack.popPose();
    }

    private static void arrow(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        int tail = ARGB.color(0.35F, color);
        float tip = ARROW_HALF;
        float neck = tip - HEAD_LENGTH;
        for (int plane = 0; plane < 2; plane++) {
            boolean vertical = plane == 1;
            quad(pose, buffer, vertical, -SHAFT, -ARROW_HALF, tail, SHAFT, -ARROW_HALF, tail, SHAFT, neck, color, -SHAFT, neck, color);
            quad(pose, buffer, vertical, -HEAD, neck, color, HEAD, neck, color, 0.0F, tip, color, 0.0F, tip, color);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, boolean vertical, float a0, float z0, int c0, float a1, float z1, int c1,
                             float a2, float z2, int c2, float a3, float z3, int c3) {
        float[][] points = {{a0, z0}, {a1, z1}, {a2, z2}, {a3, z3}};
        int[] colors = {c0, c1, c2, c3};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                int k = pass == 0 ? i : 3 - i;
                float a = points[k][0];
                float z = points[k][1];
                if (vertical) {
                    RaceLineRenderer.vertex(pose, buffer, 0.0F, a, z, colors[k]);
                } else {
                    RaceLineRenderer.vertex(pose, buffer, a, 0.0F, z, colors[k]);
                }
            }
        }
    }

    static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Target target = target(minecraft);
        if (target == null) {
            FreeformPreview.extractHud(graphics, deltaTracker);
            return;
        }
        Font font = minecraft.font;
        GatePlacement.Preference preference = GateMountKeys.preference();
        GateMount.Surface surface = target.surface();
        boolean element = !target.geometry().gate();
        boolean pole = GatePoles.pole(target.geometry());
        Component pose = element ? GateMounting.describe(target.geometry())
                : GateMounting.describe(preference.mount(surface), preference.turn(surface), preference.fine(surface));
        String refusal = element && !pole && surface != GateMount.Surface.FLOOR ? "propwash.race.element.preview_floor_only"
                : target.allowed() ? "propwash.race.mount.preview_blocked" : "propwash.race.mount.preview_not_allowed";
        Component status = target.free() ? pose : Component.translatable(refusal, pose);
        String hint = pole ? (target.seated() ? "propwash.race.pole.hint_seated" : "propwash.race.pole.hint")
                : element ? "propwash.race.element.hint" : "propwash.race.mount.hint";
        Component keys = Component.translatable(hint, GateMountKeys.keyName(), GateMountKeys.keyName());
        int width = Math.max(font.width(status), font.width(keys)) + 2 * HINT_PAD + 2;
        int centerX = graphics.guiWidth() / 2;
        int bottom = graphics.guiHeight() - HINT_BOTTOM;
        int top = bottom - font.lineHeight * 2 - 8;
        int accent = target.free() ? Palette.ACCENT : 0xFFE53935;
        ThemeDraw.notice(graphics, centerX - width / 2, top, centerX + width / 2, bottom, accent, 1.0F);
        graphics.centeredText(font, status, centerX + 1, top + 4, target.free() ? Palette.TEXT : 0xFFFF8A80);
        graphics.centeredText(font, keys, centerX + 1, top + 5 + font.lineHeight, Palette.TEXT_MUTED);
    }
}
