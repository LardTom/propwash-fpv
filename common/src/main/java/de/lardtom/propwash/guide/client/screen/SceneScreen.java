package de.lardtom.propwash.guide.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.guide.client.GuideKeys;
import de.lardtom.propwash.guide.client.render.SceneFrame;
import de.lardtom.propwash.guide.client.render.SceneRenderState;
import de.lardtom.propwash.guide.client.render.SceneView;
import de.lardtom.propwash.guide.layout.CalloutLayout;
import de.lardtom.propwash.guide.scene.CameraSpec;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneFraming;
import de.lardtom.propwash.guide.scene.ScenePlayback;
import de.lardtom.propwash.guide.scene.SceneProjection;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.ui.client.theme.GuiLayerAccess;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class SceneScreen extends Screen implements ThemedScreen {
    private static final int MAX_WIDTH = 760;
    private static final int MAX_HEIGHT = 470;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int CAPTION_HEAD = 16;
    private static final int CAPTION_LINE = 9;
    private static final int CAPTION_LINES = 2;
    private static final int MIN_STAGE = 60;
    private static final int BUTTON_WIDTH = 20;
    private static final int TIMELINE_HEIGHT = 6;
    private static final float DRAG_YAW = 0.6F;
    private static final float DRAG_PITCH = 0.45F;
    private static final float ZOOM_STEP = 0.12F;
    private static final float MIN_USER_ZOOM = 0.4F;
    private static final float MAX_USER_ZOOM = 4.0F;
    private static final float SMOOTHING = 0.35F;
    private static final float JUMP_TICKS = 12.0F;

    private static @Nullable SceneScreen active;

    private final @Nullable Screen parent;
    private final Scene scene;
    private final ScenePlayback playback;
    private final SceneView view = new SceneView();
    private final SceneFraming framing;
    private long lastMillis = -1L;
    private float userYaw;
    private float userPitch;
    private float userZoom = 1.0F;
    private float shownYaw;
    private float shownPitch;
    private float shownZoom = 1.0F;
    private boolean dragging;
    private boolean scrubbing;
    private boolean interacted;
    private int textScale = 1;
    private int captionLines = CAPTION_LINES;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private ScreenRectangle viewport = new ScreenRectangle(0, 0, 1, 1);
    private int timelineLeft;
    private int timelineRight;
    private int timelineY;
    private long frames;
    private int lastCallouts;
    private int lastKeyHints;
    private boolean sceneSubmitted;
    private @Nullable SceneState lastState;
    private @Nullable SceneProjection lastProjection;
    private @Nullable SceneFrame lastFrame;
    private List<SceneOverlay.Placed> lastPlaced = List.of();
    private List<SceneOverlay.HintBox> lastHints = List.of();
    private final CalloutLayout calloutLayout = new CalloutLayout();
    private float lastSceneTime = Float.NaN;
    private float lastFrameTicks;
    private boolean lastReset;
    private @Nullable GuideButton resetViewButton;

    public SceneScreen(Scene scene, @Nullable Screen parent) {
        super(Component.translatable(scene.titleKey()));
        this.scene = scene;
        this.parent = parent;
        this.playback = new ScenePlayback(scene);
        this.framing = SceneFraming.of(scene);
    }

    public static @Nullable SceneScreen active() {
        return active;
    }

    public Scene scene() {
        return this.scene;
    }

    public ScenePlayback playback() {
        return this.playback;
    }

    public @Nullable Screen parent() {
        return this.parent;
    }

    public long frames() {
        return this.frames;
    }

    public int lastCallouts() {
        return this.lastCallouts;
    }

    public int lastKeyHints() {
        return this.lastKeyHints;
    }

    public boolean sceneSubmitted() {
        return this.sceneSubmitted;
    }

    public ScreenRectangle viewport() {
        return this.viewport;
    }

    public List<SceneOverlay.Placed> lastPlaced() {
        return this.lastPlaced;
    }

    public List<SceneOverlay.HintBox> lastHints() {
        return this.lastHints;
    }

    public CalloutLayout.Stats calloutStats() {
        return this.calloutLayout.stats();
    }

    public float lastFrameTicks() {
        return this.lastFrameTicks;
    }

    public boolean lastReset() {
        return this.lastReset;
    }

    public void turnView(float yaw, float pitch, float zoomFactor) {
        this.userYaw += yaw;
        this.userPitch += pitch;
        this.clampPitch();
        if (zoomFactor > 0.0F && Float.isFinite(zoomFactor)) {
            this.userZoom = Math.clamp(this.userZoom * zoomFactor, MIN_USER_ZOOM, MAX_USER_ZOOM);
        }
        this.interacted = true;
    }

    public @Nullable SceneProjection lastProjection() {
        return this.lastProjection;
    }

    public @Nullable SceneFrame lastFrame() {
        return this.lastFrame;
    }

    public void snapView(float yaw, float pitch, float zoom) {
        this.userYaw = yaw;
        this.userPitch = pitch;
        this.userZoom = Math.clamp(zoom, MIN_USER_ZOOM, MAX_USER_ZOOM);
        this.clampPitch();
        this.shownYaw = this.userYaw;
        this.shownPitch = this.userPitch;
        this.shownZoom = this.userZoom;
    }

    public @Nullable SceneState lastState() {
        return this.lastState;
    }

    @Override
    protected void init() {
        active = this;
        this.calloutLayout.reset();
        this.textScale = textScale(this.minecraft.getWindow().getGuiScale());
        int w = Math.min(this.width - 12, MAX_WIDTH * this.textScale);
        int h = Math.min(this.height - 12, MAX_HEIGHT * this.textScale);
        this.windowLeft = (this.width - w) / 2;
        this.windowTop = (this.height - h) / 2;
        this.windowRight = this.windowLeft + w;
        this.windowBottom = this.windowTop + h;
        int vx0 = this.windowLeft + Metrics.PADDING;
        int vy0 = this.windowTop + HEADER + Metrics.PADDING;
        int vx1 = this.windowRight - Metrics.PADDING;
        this.captionLines = this.captionLinesFor(w, this.windowBottom - FOOTER - 2 - vy0);
        int vy1 = this.windowBottom - FOOTER - (CAPTION_HEAD + this.captionLines * CAPTION_LINE + 2) * this.textScale - 2;
        this.viewport = new ScreenRectangle(vx0, vy0, Math.max(40, vx1 - vx0), Math.max(40, vy1 - vy0));

        int buttonHeight = Metrics.CONTROL_HEIGHT;
        int by = this.windowBottom - FOOTER + (FOOTER - buttonHeight) / 2;
        int x = this.windowLeft + Metrics.PADDING;
        this.addRenderableWidget(new GuideButton(x, by, BUTTON_WIDTH, buttonHeight, () -> GuideDraw.Icon.PREVIOUS,
                () -> Component.translatable("propwash.guide.player.previous"), false, this::previous));
        x += BUTTON_WIDTH + 3;
        this.addRenderableWidget(new GuideButton(x, by, BUTTON_WIDTH + 6, buttonHeight, this::playIcon, this::playLabel, true, this::togglePlay));
        x += BUTTON_WIDTH + 6 + 3;
        this.addRenderableWidget(new GuideButton(x, by, BUTTON_WIDTH, buttonHeight, () -> GuideDraw.Icon.NEXT,
                () -> Component.translatable("propwash.guide.player.next"), false, this::next));
        x += BUTTON_WIDTH + 3;
        this.addRenderableWidget(new GuideButton(x, by, BUTTON_WIDTH, buttonHeight, () -> GuideDraw.Icon.REPLAY,
                () -> Component.translatable("propwash.guide.player.replay"), false, this::replay));
        x += BUTTON_WIDTH + 10;
        int right = this.windowRight - Metrics.PADDING;
        Component viewCaption = Component.translatable("propwash.guide.player.view");
        int viewWidth = GuideButton.width(this.font, viewCaption);
        this.resetViewButton = this.addRenderableWidget(new GuideButton(right - viewWidth, by, viewWidth, buttonHeight, () -> GuideDraw.Icon.RESET_VIEW,
                () -> Component.translatable("propwash.guide.player.reset_view"), false, viewCaption, this::resetView));
        int timeWidth = this.font.width("00:00 / 00:00");
        this.timelineLeft = x;
        this.timelineRight = right - viewWidth - 8 - timeWidth - 8;
        this.timelineY = this.windowBottom - FOOTER + (FOOTER - TIMELINE_HEIGHT) / 2;
        int closeSize = Metrics.CONTROL_HEIGHT;
        this.addRenderableWidget(new GuideButton(this.windowRight - Metrics.PADDING - closeSize, this.windowTop + (HEADER - closeSize) / 2, closeSize,
                closeSize, () -> GuideDraw.Icon.CLOSE, () -> Component.translatable("propwash.guide.player.close"), false, this::onClose));
    }

    private GuideDraw.Icon playIcon() {
        if (this.playback.finished()) {
            return GuideDraw.Icon.REPLAY;
        }
        return this.playback.playing() ? GuideDraw.Icon.PAUSE : GuideDraw.Icon.PLAY;
    }

    private Component playLabel() {
        if (this.playback.finished()) {
            return Component.translatable("propwash.guide.player.replay");
        }
        return Component.translatable(this.playback.playing() ? "propwash.guide.player.pause" : "propwash.guide.player.play");
    }

    public void togglePlay() {
        this.playback.toggle();
    }

    public void previous() {
        this.playback.previous();
    }

    public void next() {
        this.playback.next();
    }

    public void replay() {
        this.playback.replay();
    }

    public void resetView() {
        this.userYaw = 0.0F;
        this.userPitch = 0.0F;
        this.userZoom = 1.0F;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        if (active == this) {
            active = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            this.onClose();
            return true;
        }
        if (GuideKeys.matchesKey(event.key())) {
            return true;
        }
        switch (event.key()) {
            case InputConstants.KEY_SPACE -> {
                this.togglePlay();
                return true;
            }
            case InputConstants.KEY_LEFT -> {
                this.previous();
                return true;
            }
            case InputConstants.KEY_RIGHT -> {
                this.next();
                return true;
            }
            case InputConstants.KEY_R -> {
                this.resetView();
                return true;
            }
            case InputConstants.KEY_HOME -> {
                this.replay();
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (this.onTimeline(event.x(), event.y())) {
            this.scrubbing = true;
            this.seekTo(event.x());
            return true;
        }
        if (this.viewport.containsPoint((int) event.x(), (int) event.y())) {
            if (doubleClick) {
                this.resetView();
            }
            this.dragging = true;
            this.interacted = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.scrubbing) {
            this.seekTo(event.x());
            return true;
        }
        if (this.dragging) {
            this.userYaw -= (float) dx * DRAG_YAW;
            this.userPitch += (float) dy * DRAG_PITCH;
            this.clampPitch();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = this.dragging || this.scrubbing;
        this.dragging = false;
        this.scrubbing = false;
        return super.mouseReleased(event) || handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.viewport.containsPoint((int) mouseX, (int) mouseY)) {
            this.userZoom = Math.clamp(this.userZoom * (float) Math.exp(scrollY * ZOOM_STEP), MIN_USER_ZOOM, MAX_USER_ZOOM);
            this.interacted = true;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampPitch() {
        float base = this.lastState != null ? this.lastState.camera().pitch() : this.scene.camera().pitch();
        this.userPitch = Math.clamp(this.userPitch, CameraSpec.MIN_PITCH - base, CameraSpec.MAX_PITCH - base);
    }

    private boolean onTimeline(double x, double y) {
        return x >= this.timelineLeft - 2 && x <= this.timelineRight + 2 && y >= this.timelineY - 5 && y <= this.timelineY + TIMELINE_HEIGHT + 5;
    }

    private void seekTo(double x) {
        float fraction = (float) ((x - this.timelineLeft) / Math.max(1.0, this.timelineRight - this.timelineLeft));
        this.playback.seek(Math.clamp(fraction, 0.0F, 1.0F) * this.scene.duration());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
        this.minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.frames++;
        long now = Util.getMillis();
        float dt = this.lastMillis < 0L ? 0.0F : (now - this.lastMillis) / 50.0F;
        this.lastMillis = now;
        this.lastFrameTicks = dt;
        boolean jumped = !(Math.abs(this.playback.time() - this.lastSceneTime) <= JUMP_TICKS);
        if (!this.scrubbing) {
            this.playback.advance(dt);
        }
        this.lastSceneTime = this.playback.time();
        this.lastReset = jumped;
        float blend = 1.0F - (float) Math.exp(-Math.max(0.0F, dt) * SMOOTHING * 3.0F);
        this.shownYaw += (this.userYaw - this.shownYaw) * blend;
        this.shownPitch += (this.userPitch - this.shownPitch) * blend;
        this.shownZoom += (this.userZoom - this.shownZoom) * blend;
        if (this.resetViewButton != null) {
            this.resetViewButton.active = this.userYaw != 0.0F || this.userPitch != 0.0F || this.userZoom != 1.0F;
        }

        SceneState state = this.playback.evaluate();
        this.lastState = state;
        CameraSpec camera = state.camera().withOffsets(this.shownYaw, this.shownPitch, 1.0F);
        ScreenRectangle vp = this.viewport;
        float base = SceneProjection.basePixelsPerBlock(vp.width(), vp.height(), this.scene.viewRadius());
        SceneProjection projection = this.framing.projection(camera, state.time(), base, vp.left(), vp.top(), vp.width(), vp.height(),
                this.shownZoom);
        this.lastProjection = projection;

        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        this.extractHeader(g);
        this.extractStage(g);
        this.sceneSubmitted = this.submitScene(g, state, projection);
        this.extractStageFade(g);
        g.enableScissor(vp.left(), vp.top(), vp.right(), vp.bottom());
        try {
            this.lastHints = SceneOverlay.layoutKeyHints(this.font, state, projection, vp, this.textScale);
            this.lastPlaced = SceneOverlay.callouts(g, this.font, state, projection, vp, this.view.screenBoxes(state, projection), this.lastHints,
                    this.calloutLayout, this.lastFrameTicks, this.lastReset, this.textScale);
            this.lastCallouts = this.lastPlaced.size();
            this.lastKeyHints = SceneOverlay.drawKeyHints(g, this.font, this.lastHints);
            this.extractViewportBadges(g);
        } finally {
            g.disableScissor();
        }
        this.extractCaption(g, state);
        this.extractFooter(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private boolean submitScene(GuiGraphicsExtractor g, SceneState state, SceneProjection projection) {
        ScreenRectangle vp = this.viewport;
        if (!(g instanceof GuiLayerAccess access)) {
            ThemeText.textCentered(g, this.font, Component.translatable("propwash.guide.player.no_3d"), vp.left() + vp.width() / 2,
                    vp.top() + vp.height() / 2 - 4, Palette.TEXT_FAINT);
            return false;
        }
        SceneFrame frame = this.view.build(state, projection);
        this.lastFrame = frame;
        access.propwash$renderState().addPicturesInPictureState(new SceneRenderState(frame, vp.left(), vp.top(), vp.right(), vp.bottom(),
                projection.pixelsPerBlock(), access.propwash$scissor()));
        return true;
    }

    private void extractStage(GuiGraphicsExtractor g) {
        ScreenRectangle vp = this.viewport;
        int x0 = vp.left();
        int y0 = vp.top();
        int x1 = vp.right();
        int y1 = vp.bottom();
        g.fillGradient(x0, y0, x1, y1, Palette.STAGE_TOP, Palette.STAGE_BOTTOM);
        int horizon = y0 + vp.height() * 3 / 4;
        g.fillGradient(x0, horizon, x1, y1, Palette.withAlpha(Palette.ACCENT, 0x00), Palette.withAlpha(Palette.ACCENT, 0x0C));
        g.fill(x0, y0, x1, y0 + 1, Palette.STAGE_EDGE_DARK);
        g.fill(x0, y0, x0 + 1, y1, Palette.STAGE_EDGE_DARK);
        g.fill(x0 + 1, y1 - 1, x1, y1, Palette.STAGE_EDGE_LIGHT);
        g.fill(x1 - 1, y0 + 1, x1, y1, Palette.STAGE_EDGE_LIGHT);
    }

    private void extractStageFade(GuiGraphicsExtractor g) {
        ScreenRectangle vp = this.viewport;
        int x0 = vp.left() + 1;
        int y0 = vp.top() + 1;
        int x1 = vp.right() - 1;
        int y1 = vp.bottom() - 1;
        int side = Math.max(8, vp.width() / 16);
        int band = Math.max(8, vp.height() / 7);
        int dark = Palette.STAGE_BOTTOM;
        g.fillGradient(x0, y1 - band, x1, y1, Palette.withAlpha(dark, 0x00), dark);
        g.fillGradient(x0, y0, x1, y0 + band / 2, Palette.withAlpha(dark, 0xE0), Palette.withAlpha(dark, 0x00));
        int steps = 12;
        for (int i = 0; i < steps; i++) {
            float edge = 1.0F - (i + 0.5F) / steps;
            int alpha = Math.round(0xF0 * edge * edge);
            int w0 = side * i / steps;
            int w1 = side * (i + 1) / steps;
            g.fill(x0 + w0, y0, x0 + w1, y1, Palette.withAlpha(dark, alpha));
            g.fill(x1 - w1, y0, x1 - w0, y1, Palette.withAlpha(dark, alpha));
        }
    }

    private void extractViewportBadges(GuiGraphicsExtractor g) {
        ScreenRectangle vp = this.viewport;
        Component badge = null;
        int background = Palette.BUTTON;
        int foreground = Palette.TEXT;
        if (this.playback.finished()) {
            badge = Component.translatable("propwash.guide.player.finished");
            background = Palette.SUCCESS_BG;
            foreground = Palette.SUCCESS;
        } else if (!this.playback.playing()) {
            badge = Component.translatable("propwash.guide.player.paused");
            background = Palette.WARNING_BG;
            foreground = Palette.WARNING;
        }
        int badgeWidth = badge == null ? 0 : this.font.width(badge) + 8;
        if (!this.interacted) {
            List<FormattedCharSequence> lines = ThemeText.balanced(this.font, Component.translatable("propwash.guide.player.controls"),
                    Math.max(40, vp.width() - 20 - (badge == null ? 0 : badgeWidth + 8)));
            int widest = 0;
            for (FormattedCharSequence line : lines) {
                widest = Math.max(widest, this.font.width(line));
            }
            int hx = vp.left() + 8;
            int hy = vp.top() + 8;
            ThemeDraw.framedBox(g, hx - 4, hy - 3, hx + widest + 4, hy + (lines.size() - 1) * Metrics.LINE_HEIGHT + Metrics.TEXT_HEIGHT + 3,
                    Palette.withAlpha(Palette.BACKGROUND, 0xE6), Palette.BORDER_STRONG);
            for (int i = 0; i < lines.size(); i++) {
                ThemeText.text(g, this.font, lines.get(i), hx, hy + i * Metrics.LINE_HEIGHT, Palette.TEXT_MUTED);
            }
        }
        if (badge != null) {
            ThemeDraw.badge(g, this.font, badge, vp.right() - 6 - badgeWidth, vp.top() + 5, vp.top() + 18, background, foreground);
        }
    }

    private void extractHeader(GuiGraphicsExtractor g) {
        int x0 = this.windowLeft;
        int y0 = this.windowTop;
        int x1 = this.windowRight;
        ThemeDraw.headerBar(g, x0, y0, x1, HEADER);
        int textY = Metrics.textY(y0, HEADER - 1);
        int left = ThemeDraw.brand(g, this.font, x0 + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, left - 5, y0 + 5, y0 + HEADER - 6);
        Component school = Component.translatable("propwash.guide.title");
        int schoolRight = left + this.font.width(school);
        int right = x1 - Metrics.PADDING - Metrics.CONTROL_HEIGHT - 8;
        int titleX = left;
        if (schoolRight + 10 + this.font.width(this.title) <= right) {
            ThemeText.text(g, this.font, school, left, textY, Palette.TEXT_MUTED);
            ThemeDraw.verticalSeparator(g, schoolRight + 5, y0 + 5, y0 + HEADER - 6);
            titleX = schoolRight + 10;
        }
        int limit = right - titleX;
        ThemeText.fitted(g, this.font, this.title, titleX, textY, limit, Palette.TEXT);
    }

    public static int textScale(int guiScale) {
        return guiScale <= 1 ? 2 : 1;
    }

    public int textScale() {
        return this.textScale;
    }

    private int captionWidth(int windowWidth) {
        return Math.max(40, (windowWidth - 2 * Metrics.PADDING) / this.textScale);
    }

    private int captionLinesFor(int windowWidth, int room) {
        int width = this.captionWidth(windowWidth);
        int lines = CAPTION_LINES;
        for (int step = 0; step < this.scene.stepCount(); step++) {
            lines = Math.max(lines, ThemeText.balanced(this.font, Component.translatable(this.scene.stepTextKey(step)), width).size());
        }
        int fit = (room - MIN_STAGE) / this.textScale - CAPTION_HEAD - 2;
        return Math.max(CAPTION_LINES, Math.min(lines, fit / CAPTION_LINE));
    }

    public int captionWidth() {
        return this.captionWidth(this.windowRight - this.windowLeft);
    }

    public int captionLines() {
        return this.captionLines;
    }

    private void extractCaption(GuiGraphicsExtractor g, SceneState state) {
        int k = this.textScale;
        g.pose().pushMatrix();
        g.pose().translate(this.windowLeft + Metrics.PADDING, this.viewport.bottom() + 3);
        g.pose().scale(k, k);
        try {
            this.extractCaptionText(g, state, this.captionWidth(this.windowRight - this.windowLeft));
        } finally {
            g.pose().popMatrix();
        }
    }

    private void extractCaptionText(GuiGraphicsExtractor g, SceneState state, int width) {
        int x0 = 0;
        int x1 = width;
        int y0 = 0;
        int step = state.step();
        Component counter = Component.translatable("propwash.guide.player.step", step + 1, this.scene.stepCount());
        int badgeWidth = ThemeDraw.badge(g, this.font, counter, x0, y0 + 1, y0 + 13, Palette.ACCENT, Palette.ACCENT_TEXT);
        Component title = Component.translatable(this.scene.stepTitleKey(step)).withStyle(ChatFormatting.BOLD);
        ThemeText.fitted(g, this.font, title, x0 + badgeWidth + 6, y0 + 3, x1 - x0 - badgeWidth - 8, Palette.ACCENT);
        Component text = Component.translatable(this.scene.stepTextKey(step));
        List<FormattedCharSequence> lines = ThemeText.balanced(this.font, text, Math.max(40, x1 - x0));
        int shown = ThemeText.shownLines(text, lines.size(), this.captionLines);
        int ty = y0 + CAPTION_HEAD;
        for (int i = 0; i < shown; i++) {
            ThemeText.text(g, this.font, lines.get(i), x0, ty, Palette.TEXT_MUTED);
            ty += CAPTION_LINE;
        }
    }

    private void extractFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y0 = this.windowBottom - FOOTER;
        ThemeDraw.footerBar(g, this.windowLeft, y0, this.windowRight, this.windowBottom);
        int left = this.timelineLeft;
        int right = this.timelineRight;
        if (right - left < 20) {
            return;
        }
        float total = this.scene.duration();
        float time = this.playback.time();
        int count = this.scene.stepCount();
        int gap = 2;
        int current = this.playback.step();
        int hovered = -1;
        for (int i = 0; i < count; i++) {
            int sx0 = left + Math.round((right - left) * this.scene.stepStart(i) / total) + (i > 0 ? gap / 2 : 0);
            int sx1 = left + Math.round((right - left) * this.scene.stepEnd(i) / total) - (i < count - 1 ? gap / 2 : 0);
            boolean hover = mouseX >= sx0 && mouseX < sx1 && mouseY >= this.timelineY - 5 && mouseY <= this.timelineY + TIMELINE_HEIGHT + 5;
            if (hover) {
                hovered = i;
            }
            int top = this.timelineY + (i == current ? -1 : 0);
            int bottom = this.timelineY + TIMELINE_HEIGHT + (i == current ? 1 : 0);
            g.fill(sx0, top, sx1, bottom, hover ? Palette.BUTTON_HOVER : Palette.FIELD);
            float fraction;
            if (i < current || this.playback.finished()) {
                fraction = 1.0F;
            } else if (i == current) {
                fraction = (time - this.scene.stepStart(i)) / Math.max(1.0E-3F, this.scene.stepEnd(i) - this.scene.stepStart(i));
            } else {
                fraction = 0.0F;
            }
            int fill = sx0 + Math.round((sx1 - sx0) * Math.clamp(fraction, 0.0F, 1.0F));
            if (fill > sx0) {
                g.fill(sx0, top, fill, bottom, i == current && !this.playback.finished() ? Palette.ACCENT : Palette.ACCENT_DIM);
            }
        }
        int head = left + Math.round((right - left) * Math.clamp(time / total, 0.0F, 1.0F));
        g.fill(head - 1, this.timelineY - 3, head + 1, this.timelineY + TIMELINE_HEIGHT + 3, Palette.TEXT);
        String clock = clock(time) + " / " + clock(total);
        ThemeText.text(g, this.font, clock, right + 8, Metrics.textY(y0, FOOTER), Palette.TEXT_MUTED);
        if (hovered >= 0 && !this.scrubbing) {
            ThemeText.tooltip(g, this.font, Component.translatable(this.scene.stepTitleKey(hovered)), mouseX, mouseY);
        }
    }

    static String clock(float ticks) {
        int seconds = Math.max(0, Math.round(ticks / 20.0F));
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
