package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.FreeformBlocks;
import de.lardtom.propwash.race.FreeformFill;
import de.lardtom.propwash.race.FreeformGateItem;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateMounting;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class FreeformPreview {
    public static final long REFRESH_TICKS = 10L;
    private static final int HINT_BOTTOM = 76;
    private static final int HINT_PAD = 6;
    private static final float SEED_INSET = 0.04F;
    private static final GateRenderState STATE = new GateRenderState();
    private static @Nullable Key lastKey;
    private static FreeformGateItem.@Nullable Plan lastPlan;

    private record Key(BlockPos clicked, Direction face, int axis, int variant, int limit, long bucket, ClientLevel level) {
    }

    private FreeformPreview() {
    }

    public static void forget() {
        lastKey = null;
        lastPlan = null;
    }

    public static boolean visible() {
        return plan(Minecraft.getInstance()) != null;
    }

    public static FreeformGateItem.@Nullable Plan plan(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.gui.hud.isHidden() || player.isSpectator() || PilotSession.INSTANCE.isActive()
                || GateEditor.active() || !GateMountKeys.holdingFreeform(player)) {
            return null;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        boolean reverse = FreeformGateItem.reversed(player, true);
        Vec3 look = player.getViewVector(1.0F);
        FreeformFill.Aim aim = FreeformBlocks.aim(level, hit.getBlockPos(), hit.getDirection(), look, reverse);
        Key key = new Key(hit.getBlockPos().immutable(), hit.getDirection(), aim.axis(), aim.variant(), FreeformBlocks.limit(level),
                level.getGameTime() / REFRESH_TICKS, level);
        if (!key.equals(lastKey) || lastPlan == null) {
            lastKey = key;
            lastPlan = FreeformGateItem.plan(level, hit.getBlockPos(), hit.getDirection(), look, reverse);
        }
        return lastPlan;
    }

    static void submit(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
        Minecraft minecraft = Minecraft.getInstance();
        FreeformGateItem.Plan plan = plan(minecraft);
        GateRenderer renderer = GateRenderer.current();
        Vec3 camera = levelState.cameraRenderState.pos;
        if (plan == null || renderer == null || camera == null || minecraft.level == null) {
            return;
        }
        GateGeometry geometry = plan.geometry();
        boolean ok = plan.ok() && minecraft.player != null && minecraft.player.mayBuild();
        if (geometry != null) {
            int color = ok ? GateRenderer.LIGHT_WHITE : GateRenderer.LIGHT_RED;
            renderer.fill(STATE, geometry.at(0.0, 0.0, 0.0), null, GateRenderer.Marking.NONE, 0, color, ok ? 0.55F : 0.8F);
            STATE.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            STATE.outlineColor = 0;
            STATE.ageInTicks = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            STATE.distanceToCameraSq = camera.distanceToSqr(geometry.x(), geometry.y(), geometry.z());
            poseStack.pushPose();
            poseStack.translate(geometry.x() - camera.x, geometry.y() - camera.y, geometry.z() - camera.z);
            FreeformGateRenderer.submit(renderer, STATE, poseStack, collector);
            poseStack.popPose();
            return;
        }
        FreeformFill.Aim aim = plan.aim();
        int red = ARGB.color(0.35F, GateRenderer.LIGHT_RED);
        poseStack.pushPose();
        poseStack.translate(aim.seedX() - camera.x, aim.seedY() - camera.y, aim.seedZ() - camera.z);
        collector.order(1).submitCustomGeometry(poseStack, GateRenderer.glowType(), (pose, buffer) -> cell(pose, buffer, red));
        poseStack.popPose();
    }

    private static void cell(PoseStack.Pose pose, VertexConsumer buffer, int color) {
        float a = SEED_INSET;
        float b = 1.0F - SEED_INSET;
        GateRenderer.quad(pose, buffer, a, a, a, color, b, a, a, color, b, b, a, color, a, b, a, color);
        GateRenderer.quad(pose, buffer, a, a, b, color, b, a, b, color, b, b, b, color, a, b, b, color);
        GateRenderer.quad(pose, buffer, a, a, a, color, a, a, b, color, a, b, b, color, a, b, a, color);
        GateRenderer.quad(pose, buffer, b, a, a, color, b, a, b, color, b, b, b, color, b, b, a, color);
        GateRenderer.quad(pose, buffer, a, a, a, color, b, a, a, color, b, a, b, color, a, a, b, color);
        GateRenderer.quad(pose, buffer, a, b, a, color, b, b, a, color, b, b, b, color, a, b, b, color);
    }

    static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        FreeformGateItem.Plan plan = plan(minecraft);
        if (plan == null || minecraft.player == null) {
            return;
        }
        Font font = minecraft.font;
        boolean allowed = minecraft.player.mayBuild();
        boolean ok = plan.ok() && allowed;
        GateGeometry geometry = plan.geometry();
        Component status;
        if (!allowed) {
            status = Component.translatable("propwash.race.mount.not_allowed");
        } else if (ok && geometry != null) {
            status = GateMounting.describe(geometry);
        } else {
            status = FreeformGateItem.problem(plan);
        }
        Component keys = Component.translatable("propwash.race.freeform.hint", GateMountKeys.keyName(),
                GateMounting.variantName(CourseElement.FREEFORM, GateMountKeys.preference().variant(CourseElement.FREEFORM)));
        int width = Math.max(font.width(status), font.width(keys)) + 2 * HINT_PAD + 2;
        int centerX = graphics.guiWidth() / 2;
        int bottom = graphics.guiHeight() - HINT_BOTTOM;
        int top = bottom - font.lineHeight * 2 - 8;
        int accent = ok ? Palette.ACCENT : 0xFFE53935;
        ThemeDraw.notice(graphics, centerX - width / 2, top, centerX + width / 2, bottom, accent, 1.0F);
        graphics.centeredText(font, status, centerX + 1, top + 4, ok ? Palette.TEXT : 0xFFFF8A80);
        graphics.centeredText(font, keys, centerX + 1, top + 5 + font.lineHeight, Palette.TEXT_MUTED);
    }
}
