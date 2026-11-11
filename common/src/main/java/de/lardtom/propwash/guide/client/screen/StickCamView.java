package de.lardtom.propwash.guide.client.screen;

import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.client.render.RemoteGimbalModel;
import de.lardtom.propwash.drone.client.render.RemoteGimbalRenderer;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.input.client.StickModes;
import de.lardtom.propwash.input.client.screen.remote.RemoteDemoRenderState;
import de.lardtom.propwash.input.client.screen.remote.RemoteProjection;
import de.lardtom.propwash.ui.client.theme.GuiLayerAccess;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class StickCamView {
    public static final int MIN_WIDTH = 84;
    public static final int MIN_HEIGHT = 58;
    private static final int PADDING = 3;
    private static final int TRAIL = 10;

    private final ItemStackRenderState item = new ItemStackRenderState();
    private final float[][] trail = new float[TRAIL][4];
    private int trailSize;
    private int trailHead;
    private float lastTime = Float.NaN;
    private @Nullable ItemStack stack;
    private StickMode.@Nullable Sticks lastPlaced;
    private @Nullable StickMode lastMode;
    private long frames;
    private boolean submitted;

    public StickMode.@Nullable Sticks lastPlaced() {
        return this.lastPlaced;
    }

    public @Nullable StickMode lastMode() {
        return this.lastMode;
    }

    public long frames() {
        return this.frames;
    }

    public boolean submitted() {
        return this.submitted;
    }

    public void reset() {
        this.trailSize = 0;
        this.trailHead = 0;
        this.lastTime = Float.NaN;
    }

    public static boolean fits(int width, int height) {
        return width >= MIN_WIDTH && height >= MIN_HEIGHT;
    }

    public void extract(GuiGraphicsExtractor g, Font font, ActorState actor, float sceneTime, int x0, int y0, int x1, int y1) {
        this.submitted = false;
        if (!fits(x1 - x0, y1 - y0)) {
            return;
        }
        StickMode mode = StickModes.current();
        StickMode.Sticks placed = mode.fromMode2(actor.leftX(), actor.leftY(), actor.rightX(), actor.rightY());
        this.lastPlaced = placed;
        this.lastMode = mode;
        this.remember(placed, sceneTime);
        ThemeDraw.framedBox(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, Palette.withAlpha(Palette.BACKGROUND, 0xF0), Palette.BORDER_STRONG);
        ThemeDraw.previewStage(g, x0, y0, x1, y1);
        Component title = Component.translatable("propwash.guide.player.stick_cam.title", StickModes.name(mode));
        ThemeText.fitted(g, font, title, x0 + PADDING, y0 + PADDING, x1 - x0 - 2 * PADDING, Palette.TEXT_MUTED);
        int stageTop = y0 + PADDING + Metrics.LINE_HEIGHT;
        int stageBottom = y1 - PADDING;
        if (stageBottom - stageTop < 24) {
            return;
        }
        RemoteProjection projection = RemoteProjection.fit(RemoteProjection.Region.STICKS, x0 + PADDING, stageTop, x1 - PADDING, stageBottom)
                .centeredAt((x0 + x1) * 0.5F, (stageTop + stageBottom) * 0.5F);
        this.submitted = this.submit(g, placed, projection, x0 + 1, stageTop, x1 - 1, stageBottom);
        g.enableScissor(x0 + 1, stageTop, x1 - 1, stageBottom);
        try {
            this.overlay(g, projection, RemoteGimbalModel.LEFT_X, 0, Palette.ACCENT);
            this.overlay(g, projection, RemoteGimbalModel.RIGHT_X, 2, Palette.SUCCESS);
        } finally {
            g.disableScissor();
        }
        this.frames++;
    }

    private void remember(StickMode.Sticks placed, float time) {
        if (time == this.lastTime) {
            return;
        }
        if (!Float.isNaN(this.lastTime) && (time < this.lastTime || time - this.lastTime > 10.0F)) {
            this.reset();
        }
        this.lastTime = time;
        float[] slot = this.trail[this.trailHead];
        slot[0] = placed.leftX();
        slot[1] = placed.leftY();
        slot[2] = placed.rightX();
        slot[3] = placed.rightY();
        this.trailHead = (this.trailHead + 1) % TRAIL;
        this.trailSize = Math.min(TRAIL, this.trailSize + 1);
    }

    private boolean submit(GuiGraphicsExtractor g, StickMode.Sticks placed, RemoteProjection projection, int x0, int y0, int x1, int y1) {
        if (!(g instanceof GuiLayerAccess access)) {
            return false;
        }
        ItemStack remote = this.stack();
        if (remote.isEmpty()) {
            return false;
        }
        RemoteGimbalRenderer.Pose pose = new RemoteGimbalRenderer.Pose(placed.leftX(), placed.leftY(), placed.rightX(), placed.rightY());
        Minecraft minecraft = Minecraft.getInstance();
        RemoteGimbalRenderer.extractPosed(remote, pose,
                () -> minecraft.getItemModelResolver().updateForTopItem(this.item, remote, ItemDisplayContext.NONE, minecraft.level, null, 0));
        access.propwash$renderState().addPicturesInPictureState(new RemoteDemoRenderState(this.item, projection, x0, y0, x1, y1, access.propwash$scissor()));
        return true;
    }

    private ItemStack stack() {
        if (this.stack == null) {
            this.stack = DroneModule.REMOTE.isBound() ? new ItemStack(DroneModule.REMOTE.get()) : ItemStack.EMPTY;
        }
        return this.stack;
    }

    private void overlay(GuiGraphicsExtractor g, RemoteProjection p, float pivotX, int offset, int color) {
        for (int i = 0; i < this.trailSize; i++) {
            int index = Math.floorMod(this.trailHead - this.trailSize + i, TRAIL);
            float[] sample = this.trail[index];
            float[] tip = p.stickTip(pivotX, RemoteGimbalModel.PIVOT_Y, RemoteGimbalModel.PIVOT_Z, RemoteGimbalModel.STICK_LENGTH, sample[offset],
                    sample[offset + 1]);
            int alpha = 0x30 + 0xA0 * (i + 1) / Math.max(1, this.trailSize);
            int x = Math.round(tip[0]);
            int y = Math.round(tip[1]);
            boolean newest = i == this.trailSize - 1;
            if (newest) {
                g.fill(x - 2, y - 2, x + 3, y + 3, Palette.withAlpha(Palette.SHADOW, 0xC0));
                g.fill(x - 1, y - 1, x + 2, y + 2, color);
            } else {
                g.fill(x, y, x + 1, y + 1, Palette.withAlpha(color, alpha));
            }
        }
    }
}
