package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.compat.flashback.client.FlashbackBridge;
import de.lardtom.propwash.compat.flashback.client.FlashbackReplays;
import de.lardtom.propwash.compat.flashback.client.FlashbackUiScale;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.video.record.path.CameraTrack;
import de.lardtom.propwash.video.record.path.FlashbackBudget;
import de.lardtom.propwash.video.record.path.PathFrames;
import de.lardtom.propwash.video.record.path.PathKeyframe;
import de.lardtom.propwash.video.record.path.PathPreview;
import de.lardtom.propwash.video.record.path.TickPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class FlashbackRescueScreen extends Screen implements ThemedScreen {
    private static final String KEY = "propwash.video.flashback.rescue.";
    private static final int MAX_WIDTH = 520;
    private static final int MAX_HEIGHT = 360;
    private static final int ROW = 24;
    private static final double FALLBACK_FOV = 70.0;

    private final @Nullable Screen parent;
    private final float scale;
    private @Nullable CompletableFuture<List<FlashbackReplays.Scene>> scanning;
    private List<FlashbackReplays.Scene> scenes = List.of();
    private int selected = -1;
    private double scroll;
    private @Nullable CompletableFuture<PathPreviewModel> opening;
    private @Nullable Component message;
    private int messageColor = Palette.TEXT_MUTED;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int listTop;
    private int listBottom;
    private @Nullable FlatButton thinButton;

    public FlashbackRescueScreen(@Nullable Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.scale = FlashbackUiScale.budgetScale(Minecraft.getInstance());
        this.rescan();
    }

    public List<FlashbackReplays.Scene> scenes() {
        return this.scenes;
    }

    public boolean scanning() {
        return this.scanning != null || this.opening != null;
    }

    public float scale() {
        return this.scale;
    }

    public void select(int index) {
        this.selected = index;
        this.updateButtons();
    }

    public @Nullable Component message() {
        return this.message;
    }

    public void rescan() {
        Path data = FlashbackBridge.dataDirectory();
        int colour = imguiColour(FlashbackCam.TRACK_COLOUR);
        this.scanning = CompletableFuture.supplyAsync(() -> FlashbackReplays.scan(data, colour), Util.backgroundExecutor());
    }

    static int imguiColour(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, MAX_WIDTH);
        int h = Math.min(this.height - 16, MAX_HEIGHT);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        this.listTop = this.windowTop + Metrics.HEADER_HEIGHT + Metrics.PADDING;
        this.listBottom = this.windowBottom - Metrics.FOOTER_HEIGHT - Metrics.PADDING - 2 * Metrics.LINE_HEIGHT;
        int footerTop = this.windowBottom - Metrics.FOOTER_HEIGHT;
        int buttonY = footerTop + (Metrics.FOOTER_HEIGHT - Metrics.CONTROL_HEIGHT) / 2;
        Component done = Component.translatable(SimplificationText.PREVIEW + "done");
        int doneWidth = Math.max(56, this.font.width(done) + 16);
        this.addRenderableWidget(new FlatButton(this.windowRight - Metrics.PADDING - doneWidth, buttonY, doneWidth, Metrics.CONTROL_HEIGHT, done,
                FlatButton.Style.NORMAL, this::onClose));
        Component thin = Component.translatable(KEY + "thin");
        int thinWidth = this.font.width(thin) + 16;
        this.thinButton = this.addRenderableWidget(new FlatButton(this.windowRight - Metrics.PADDING - doneWidth - Metrics.GAP - thinWidth, buttonY,
                thinWidth, Metrics.CONTROL_HEIGHT, thin, FlatButton.Style.PRIMARY, this::openSelected));
        this.thinButton.setTooltip(Tooltip.create(Component.translatable("propwash.video.record.tab.flashback_check.tooltip")));
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.thinButton != null) {
            this.thinButton.active = this.selectedScene() != null && this.opening == null;
        }
    }

    private FlashbackReplays.@Nullable Scene selectedScene() {
        if (this.selected < 0 || this.selected >= this.scenes.size()) {
            return null;
        }
        FlashbackReplays.Scene scene = this.scenes.get(this.selected);
        return scene.propwash() ? scene : null;
    }

    public void openSelected() {
        FlashbackReplays.Scene scene = this.selectedScene();
        if (scene == null || this.opening != null) {
            return;
        }
        Component blocked = blocked();
        if (blocked != null) {
            this.message = blocked;
            this.messageColor = Palette.WARNING;
            return;
        }
        float budgetScale = this.scale;
        this.message = Component.translatable(SimplificationText.PREVIEW + "computing");
        this.messageColor = Palette.TEXT_MUTED;
        this.opening = CompletableFuture.supplyAsync(() -> {
            try {
                List<PathKeyframe> keys = FlashbackReplays.cameraKeyframes(scene, Avatar.DEFAULT_EYE_HEIGHT);
                List<TickPose> poses = FlashbackReplays.playback(keys, FALLBACK_FOV);
                PathFrames frames = PathFrames.of(poses);
                int max = FlashbackBudget.maxCameraKeyframes(budgetScale, Math.max(2, scene.holdKeyframes()));
                double start = CameraTrack.smallestFittingSlider(frames, max);
                return PathPreviewModel.forScene(scene.sceneName(), poses, start, budgetScale);
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }, Util.backgroundExecutor());
        this.updateButtons();
    }

    static @Nullable Component blocked() {
        if (FlashbackBridge.inReplay()) {
            return Component.translatable(KEY + "in_replay");
        }
        if (FlashbackBridge.recording()) {
            return Component.translatable(KEY + "recording");
        }
        return null;
    }

    private Component apply(FlashbackReplays.Scene scene, PathPreview preview) {
        Component blocked = blocked();
        if (blocked != null) {
            return blocked;
        }
        try {
            List<PathKeyframe> keys = preview.built().track().camera();
            Path backup = FlashbackReplays.backup(scene.state());
            int written = FlashbackBridge.replaceCameraKeyframes(scene.replay(), scene.sceneIndex(), scene.trackIndex(), scene.cameraKeyframes(), keys,
                    Avatar.DEFAULT_EYE_HEIGHT);
            Constants.LOG.info("Action-Cam (Flashback): Szene \"{}\" in {} ausgedünnt ({} → {} Keyframes, Sicherung {})", scene.sceneName(),
                    scene.replay(), scene.cameraKeyframes(), written, backup.getFileName());
            this.rescan();
            return Component.translatable(KEY + "done", scene.sceneName(), scene.cameraKeyframes(), written, backup.getFileName().toString());
        } catch (IOException | FlashbackBridge.FlashbackException e) {
            Constants.LOG.warn("Action-Cam (Flashback): Ausdünnen fehlgeschlagen", e);
            return Component.translatable(KEY + "failed", String.valueOf(e.getMessage()));
        }
    }

    @Override
    public void tick() {
        CompletableFuture<List<FlashbackReplays.Scene>> scan = this.scanning;
        if (scan != null && scan.isDone()) {
            this.scanning = null;
            this.scenes = scan.isCompletedExceptionally() ? List.of() : scan.join();
            if (this.selected >= this.scenes.size()) {
                this.selected = -1;
            }
            this.updateButtons();
        }
        CompletableFuture<PathPreviewModel> open = this.opening;
        if (open != null && open.isDone()) {
            this.opening = null;
            this.updateButtons();
            FlashbackReplays.Scene scene = this.selectedScene();
            if (open.isCompletedExceptionally() || scene == null) {
                this.message = Component.translatable(KEY + "failed", open.isCompletedExceptionally() ? "?" : "-");
                this.messageColor = Palette.ERROR;
                return;
            }
            this.message = null;
            PathPreviewModel model = open.join();
            Minecraft.getInstance().gui.setScreen(new PathPreviewScreen(this, model, (slider, limit) -> {
            }, preview -> this.apply(scene, preview)));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.y() >= this.listTop && event.y() < this.listBottom
                && event.x() >= this.windowLeft && event.x() < this.windowRight) {
            int index = (int) ((event.y() - this.listTop + this.scroll) / ROW);
            if (index >= 0 && index < this.scenes.size()) {
                this.select(index);
                if (doubleClick) {
                    this.openSelected();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int room = this.listBottom - this.listTop;
        double max = Math.max(0, this.scenes.size() * ROW - room);
        this.scroll = Math.max(0.0, Math.min(max, this.scroll - scrollY * ROW));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, Metrics.HEADER_HEIGHT);
        int textY = Metrics.textY(this.windowTop, Metrics.HEADER_HEIGHT - 1);
        int titleX = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.windowTop + 5, this.windowTop + Metrics.HEADER_HEIGHT - 6);
        ThemeText.fitted(g, this.font, this.title, titleX, textY, this.windowRight - titleX - 16, Palette.TEXT);
        int left = this.windowLeft + Metrics.PADDING;
        int right = this.windowRight - Metrics.PADDING;
        ThemeDraw.framedBox(g, left, this.listTop, right, this.listBottom, Palette.FIELD, Palette.BORDER);
        if (this.scenes.isEmpty()) {
            Component text = Component.translatable(this.scanning != null ? SimplificationText.PREVIEW + "computing" : KEY + "empty");
            ThemeText.textCentered(g, this.font, text, (left + right) / 2, (this.listTop + this.listBottom) / 2 - 4, Palette.TEXT_MUTED);
        } else {
            g.enableScissor(left + 1, this.listTop + 1, right - 1, this.listBottom - 1);
            int y = this.listTop - (int) this.scroll;
            for (int i = 0; i < this.scenes.size(); i++, y += ROW) {
                if (y + ROW < this.listTop || y > this.listBottom) {
                    continue;
                }
                this.row(g, this.scenes.get(i), i, left + 1, y, right - 1, mouseX, mouseY);
            }
            g.disableScissor();
        }
        int y = this.listBottom + 3;
        boolean allFit = this.scanning == null && !this.scenes.isEmpty() && this.scenes.stream().allMatch(s -> !s.propwash() || s.fits(this.scale));
        if (allFit) {
            ThemeText.text(g, this.font, Component.translatable(KEY + "all_fit"), left, y, Palette.SUCCESS);
            y += Metrics.LINE_HEIGHT;
        }
        if (this.message != null) {
            ThemeText.text(g, this.font, ThemeText.fit(this.font, this.message, right - left), left, y, this.messageColor);
        }
        int footerTop = this.windowBottom - Metrics.FOOTER_HEIGHT;
        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private void row(GuiGraphicsExtractor g, FlashbackReplays.Scene scene, int index, int x0, int y, int x1, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= Math.max(y, this.listTop) && mouseY < Math.min(y + ROW, this.listBottom);
        if (index == this.selected) {
            g.fill(x0, y, x1, y + ROW, Palette.SELECTION);
        } else if (hovered) {
            g.fill(x0, y, x1, y + ROW, Palette.HOVER_SOFT);
        }
        boolean fits = scene.fits(this.scale);
        int color = scene.propwash() ? Palette.TEXT : Palette.TEXT_FAINT;
        Component title = Component.literal(scene.replayName());
        ThemeText.text(g, this.font, ThemeText.fit(this.font, title, x1 - x0 - 90), x0 + 4, y + 3, color);
        Component status = Component.translatable(KEY + (fits ? "ok" : "over"));
        ThemeText.textRight(g, this.font, status, x1 - 4, y + 3, fits ? Palette.SUCCESS : Palette.WARNING);
        Component detail = scene.propwash()
                ? Component.translatable(KEY + "row", scene.sceneName(), scene.cameraKeyframes(), Math.round(scene.fill(this.scale) * 100.0))
                : Component.translatable(KEY + "foreign");
        ThemeText.text(g, this.font, ThemeText.fit(this.font, detail, x1 - x0 - 8), x0 + 4, y + 3 + Metrics.LINE_HEIGHT, Palette.TEXT_MUTED);
        g.fill(x0, y + ROW - 1, x1, y + ROW, Palette.GRID);
    }
}
