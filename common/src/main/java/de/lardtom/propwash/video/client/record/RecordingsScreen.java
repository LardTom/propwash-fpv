package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.ui.ScrollMath;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.video.record.ClipInfo;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class RecordingsScreen extends Screen implements ThemedScreen {
    private static final String KEY = "propwash.video.recordings.";
    private static final int MAX_WIDTH = 500;
    private static final int MAX_HEIGHT = 420;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int PAD = 8;
    private static final int THUMB_WIDTH = 80;
    private static final int THUMB_HEIGHT = 45;
    private static final int ROW = THUMB_HEIGHT + 9;
    private static final int JOB_ROW = 26;
    private static final int LIVE_DOT_RADIUS = 3;
    private static final int REFRESH_TICKS = 40;
    private static final long DELETE_CONFIRM_MILLIS = 3000L;
    private static final long TOAST_MILLIS = 3500L;

    private final @Nullable Screen parent;
    private final Map<Path, Thumb> thumbs = new HashMap<>();
    private List<ClipInfo> clips = List.of();
    private @Nullable Path selected;
    private @Nullable Path armedDelete;
    private long armedUntil;
    private double scroll;
    private boolean draggingBar;
    private long seenRevision = -1L;
    private int ticks;
    private int lastSavingCount = -1;
    private @Nullable Path lastSaved;
    private long frames;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int listTop;
    private int listBottom;
    private @Nullable FlatButton playButton;
    private @Nullable FlatButton deleteButton;
    private @Nullable FlatButton stopButton;

    public RecordingsScreen(@Nullable Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    public static void open(@Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(new RecordingsScreen(parent));
    }

    public List<ClipInfo> clips() {
        return this.clips;
    }

    public @Nullable Path selected() {
        return this.selected;
    }

    public void select(@Nullable Path file) {
        this.selected = file;
        this.armedDelete = null;
        this.ensureVisible();
        this.updateButtons();
    }

    public long frames() {
        return this.frames;
    }

    public int loadedThumbnails() {
        int loaded = 0;
        for (Thumb thumb : this.thumbs.values()) {
            if (thumb.texture != null) {
                loaded++;
            }
        }
        return loaded;
    }

    public boolean deleteArmed() {
        return this.armedDelete != null;
    }

    private RecordingLibrary library() {
        return ActionCamRecorder.INSTANCE.library();
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, MAX_WIDTH);
        int h = Math.min(this.height - 16, MAX_HEIGHT);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        int buttonY = this.windowBottom - FOOTER + (FOOTER - Metrics.CONTROL_HEIGHT) / 2;
        Component folder = Component.translatable(KEY + "open_folder");
        Component done = Component.translatable(KEY + "done");
        Component play = Component.translatable(KEY + "play");
        Component delete = Component.translatable(KEY + "delete_confirm");
        int folderWidth = this.font.width(folder) + 16;
        int doneWidth = Math.max(48, this.font.width(done) + 16);
        int playWidth = Math.max(64, this.font.width(play) + 16);
        int deleteWidth = Math.max(56, Math.max(this.font.width(delete), this.font.width(Component.translatable(KEY + "delete"))) + 16);
        int room = w - 2 * Metrics.PADDING - 3 * Metrics.GAP;
        if (folderWidth + doneWidth + playWidth + deleteWidth > room) {
            folderWidth = this.font.width(folder) + 8;
            doneWidth = this.font.width(done) + 8;
            playWidth = this.font.width(play) + 8;
            deleteWidth = Math.max(this.font.width(delete), this.font.width(Component.translatable(KEY + "delete"))) + 8;
            int total = folderWidth + doneWidth + playWidth + deleteWidth;
            if (total > room) {
                folderWidth = folderWidth * room / total;
                doneWidth = doneWidth * room / total;
                playWidth = playWidth * room / total;
                deleteWidth = deleteWidth * room / total;
            }
        }
        int x = this.windowRight - Metrics.PADDING - doneWidth;
        this.addRenderableWidget(new FlatButton(x, buttonY, doneWidth, Metrics.CONTROL_HEIGHT, done, FlatButton.Style.NORMAL, this::onClose));
        x -= Metrics.GAP + playWidth;
        this.playButton = this.addRenderableWidget(new FlatButton(x, buttonY, playWidth, Metrics.CONTROL_HEIGHT, play, FlatButton.Style.PRIMARY,
                this::playSelected));
        x -= Metrics.GAP + deleteWidth;
        this.deleteButton = this.addRenderableWidget(new FlatButton(x, buttonY, deleteWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable(KEY + "delete"), FlatButton.Style.DANGER, this::deleteSelected));
        int folderRight = x - Metrics.GAP;
        int folderLeft = this.windowLeft + Metrics.PADDING;
        this.addRenderableWidget(new FlatButton(folderLeft, buttonY, Math.max(40, Math.min(folderWidth, folderRight - folderLeft)),
                Metrics.CONTROL_HEIGHT, folder, FlatButton.Style.NORMAL, this::openFolder));
        Component stop = Component.translatable(KEY + "stop");
        int stopWidth = Math.max(48, this.font.width(stop) + 16);
        this.stopButton = this.addRenderableWidget(new FlatButton(this.windowRight - PAD - stopWidth,
                this.windowTop + HEADER + 4 + (JOB_ROW - 4 - Metrics.CONTROL_HEIGHT) / 2, stopWidth, Metrics.CONTROL_HEIGHT, stop,
                FlatButton.Style.DANGER, this::stopRecording));
        this.library().refresh();
        this.pullClips();
        this.layoutList();
        this.updateButtons();
    }

    private void stopRecording() {
        ActionCamRecorder.INSTANCE.stop(Minecraft.getInstance(), ActionCamRecorder.StopReason.USER);
        this.updateButtons();
    }

    private void layoutList() {
        int jobs = this.jobCount();
        this.listTop = this.windowTop + HEADER + (jobs > 0 ? jobs * JOB_ROW + 6 : 0);
        this.listBottom = this.windowBottom - FOOTER;
        this.scroll = Math.clamp(this.scroll, 0.0, this.maxScroll());
    }

    private int jobCount() {
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        return (recorder.phase() == ActionCamRecorder.Phase.IDLE ? 0 : 1) + recorder.savingJobs().size();
    }

    private void pullClips() {
        RecordingLibrary library = this.library();
        if (library.revision() == this.seenRevision) {
            return;
        }
        this.seenRevision = library.revision();
        this.clips = library.clips();
        if (this.selected != null && this.indexOf(this.selected) < 0) {
            this.selected = null;
        }
        if (this.selected == null && !this.clips.isEmpty()) {
            this.selected = this.clips.getFirst().file();
        }
        this.releaseUnused();
        this.scroll = Math.clamp(this.scroll, 0.0, this.maxScroll());
        this.updateButtons();
    }

    private int indexOf(Path file) {
        for (int i = 0; i < this.clips.size(); i++) {
            if (this.clips.get(i).file().equals(file)) {
                return i;
            }
        }
        return -1;
    }

    private @Nullable ClipInfo selectedClip() {
        Path file = this.selected;
        if (file == null) {
            return null;
        }
        int index = this.indexOf(file);
        return index < 0 ? null : this.clips.get(index);
    }

    private void updateButtons() {
        if (this.stopButton != null) {
            boolean recording = ActionCamRecorder.INSTANCE.phase() == ActionCamRecorder.Phase.RECORDING;
            this.stopButton.visible = recording;
            this.stopButton.active = recording;
        }
        ClipInfo clip = this.selectedClip();
        if (this.playButton != null) {
            this.playButton.active = clip != null;
        }
        if (this.deleteButton != null) {
            this.deleteButton.active = clip != null;
            this.deleteButton.setMessage(Component.translatable(KEY + (this.armedDelete != null ? "delete_confirm" : "delete")));
        }
    }

    @Override
    public void tick() {
        this.ticks++;
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        int saving = recorder.savingJobs().size();
        Path saved = recorder.lastSaved();
        if (this.ticks % REFRESH_TICKS == 0 || saving != this.lastSavingCount || (saved != null && !saved.equals(this.lastSaved))) {
            this.lastSavingCount = saving;
            this.lastSaved = saved;
            this.library().refresh();
        }
        this.pullClips();
        this.layoutList();
        this.updateButtons();
        if (this.armedDelete != null && Util.getMillis() > this.armedUntil) {
            this.armedDelete = null;
            this.updateButtons();
        }
        this.pollThumbs();
    }

    private void pollThumbs() {
        for (Map.Entry<Path, Thumb> entry : this.thumbs.entrySet()) {
            Thumb thumb = entry.getValue();
            CompletableFuture<NativeImage> loading = thumb.loading;
            if (loading == null || !loading.isDone()) {
                continue;
            }
            thumb.loading = null;
            if (loading.isCompletedExceptionally() || loading.isCancelled()) {
                thumb.failed = true;
                continue;
            }
            NativeImage image = loading.join();
            try {
                thumb.texture = new DynamicTexture(() -> Constants.MOD_ID + " recording " + entry.getKey().getFileName(), image);
            } catch (RuntimeException e) {
                image.close();
                thumb.failed = true;
            }
        }
    }

    private Thumb thumbFor(ClipInfo clip) {
        Thumb thumb = this.thumbs.get(clip.file());
        if (thumb == null) {
            thumb = new Thumb();
            Path file = RecordingLibrary.thumbnailOf(clip.file());
            if (Files.isRegularFile(file)) {
                thumb.loading = RecordingThumbnails.read(file);
            } else {
                thumb.failed = true;
            }
            this.thumbs.put(clip.file(), thumb);
        }
        return thumb;
    }

    private void releaseUnused() {
        this.thumbs.entrySet().removeIf(entry -> {
            if (this.indexOf(entry.getKey()) >= 0) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
    }

    @Override
    public void removed() {
        for (Thumb thumb : this.thumbs.values()) {
            thumb.close();
        }
        this.thumbs.clear();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int contentHeight() {
        return this.clips.size() * ROW;
    }

    private double maxScroll() {
        return Math.max(0, this.contentHeight() - (this.listBottom - this.listTop));
    }

    private void ensureVisible() {
        Path file = this.selected;
        if (file == null) {
            return;
        }
        int index = this.indexOf(file);
        if (index < 0) {
            return;
        }
        int top = index * ROW;
        int view = this.listBottom - this.listTop;
        if (top < this.scroll) {
            this.scroll = top;
        } else if (top + ROW > this.scroll + view) {
            this.scroll = top + ROW - view;
        }
        this.scroll = Math.clamp(this.scroll, 0.0, this.maxScroll());
    }

    private void moveSelection(int delta) {
        if (this.clips.isEmpty()) {
            return;
        }
        int index = this.selected == null ? -1 : this.indexOf(this.selected);
        int next = Math.clamp(index + delta, 0, this.clips.size() - 1);
        this.select(this.clips.get(next).file());
    }

    public void playSelected() {
        ClipInfo clip = this.selectedClip();
        if (clip == null) {
            return;
        }
        if (!Files.isRegularFile(clip.file())) {
            this.library().refresh();
            return;
        }
        Blaze3D.openPath(clip.file());
    }

    public void openFolder() {
        Path dir = this.library().directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        Blaze3D.openPath(dir);
    }

    public void deleteSelected() {
        ClipInfo clip = this.selectedClip();
        if (clip == null) {
            return;
        }
        if (!clip.file().equals(this.armedDelete) || Util.getMillis() > this.armedUntil) {
            this.armedDelete = clip.file();
            this.armedUntil = Util.getMillis() + DELETE_CONFIRM_MILLIS;
            this.updateButtons();
            return;
        }
        this.armedDelete = null;
        int index = this.indexOf(clip.file());
        try {
            this.library().delete(clip.file());
            ThemedToast.show(this.minecraft.gui.toastManager(), ActionCamRecorder.TOAST_TOKEN, Component.translatable(KEY + "deleted"),
                    Component.literal(clip.fileName()), Palette.ACCENT, TOAST_MILLIS);
        } catch (IOException e) {
            ThemedToast.show(this.minecraft.gui.toastManager(), ActionCamRecorder.TOAST_TOKEN, Component.translatable(KEY + "delete_failed"),
                    Component.literal(e.getMessage() == null ? clip.fileName() : e.getMessage()), Palette.ERROR, TOAST_MILLIS);
        }
        Thumb thumb = this.thumbs.remove(clip.file());
        if (thumb != null) {
            thumb.close();
        }
        this.seenRevision = -1L;
        this.pullClips();
        if (!this.clips.isEmpty()) {
            this.select(this.clips.get(Math.clamp(index, 0, this.clips.size() - 1)).file());
        } else {
            this.select(null);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.getFocused() == null || !(this.getFocused() instanceof FlatButton)) {
            if (event.isUp()) {
                this.moveSelection(-1);
                return true;
            }
            if (event.isDown()) {
                this.moveSelection(1);
                return true;
            }
            if (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN) {
                int page = Math.max(1, (this.listBottom - this.listTop) / ROW - 1);
                this.moveSelection(event.key() == InputConstants.KEY_PAGEUP ? -page : page);
                return true;
            }
            if (event.key() == InputConstants.KEY_HOME) {
                this.moveSelection(-this.clips.size());
                return true;
            }
            if (event.key() == InputConstants.KEY_END) {
                this.moveSelection(this.clips.size());
                return true;
            }
            if (event.isConfirmation()) {
                this.playSelected();
                return true;
            }
            if (event.key() == InputConstants.KEY_DELETE) {
                this.deleteSelected();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void dragTo(double mouseY) {
        this.scroll = Math.clamp(ScrollMath.dragOffset(mouseY - (this.listTop + 2), this.contentHeight(), this.listBottom - this.listTop - 4),
                0.0, this.maxScroll());
    }

    private int barX() {
        return this.windowRight - 2 - Metrics.SCROLLBAR_WIDTH;
    }

    public ScreenRectangle scrollbarArea() {
        return new ScreenRectangle(this.barX(), this.listTop + 2, Metrics.SCROLLBAR_WIDTH, Math.max(0, this.listBottom - this.listTop - 4));
    }

    public double scrollOffset() {
        return this.scroll;
    }

    public double scrollMax() {
        return this.maxScroll();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingBar) {
            this.dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingBar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double x = event.x();
        double y = event.y();
        if (x < this.windowLeft || x >= this.windowRight || y < this.listTop || y >= this.listBottom) {
            return false;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.maxScroll() > 0
                && x >= this.barX() - 2) {
            this.draggingBar = true;
            this.dragTo(y);
            return true;
        }
        int index = (int) Math.floor((y - this.listTop + this.scroll) / ROW);
        if (index < 0 || index >= this.clips.size()) {
            return false;
        }
        Path file = this.clips.get(index).file();
        boolean same = file.equals(this.selected);
        this.select(file);
        this.setFocused(null);
        if (doubleClick && same) {
            this.playSelected();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= this.listTop && mouseY < this.listBottom && this.maxScroll() > 0) {
            this.scroll = Math.clamp(this.scroll - scrollY * ROW / 2.0, 0.0, this.maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.frames++;
        this.layoutList();
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, HEADER);
        int textY = Metrics.textY(this.windowTop, HEADER - 1);
        int titleX = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.windowTop + 5, this.windowTop + HEADER - 6);
        Component summary = this.summary();
        int summaryWidth = this.font.width(summary);
        ThemeText.textRight(g, this.font, summary, this.windowRight - 8, textY, Palette.TEXT_MUTED);
        ThemeText.fitted(g, this.font, this.title, titleX, textY, this.windowRight - titleX - 16 - summaryWidth, Palette.TEXT);
        this.extractJobs(g);
        this.extractList(g, mouseX, mouseY);
        int footerTop = this.windowBottom - FOOTER;
        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private Component summary() {
        long bytes = 0L;
        for (ClipInfo clip : this.clips) {
            bytes += clip.sizeBytes();
        }
        int count = this.clips.size();
        return Component.translatable(KEY + (count == 1 ? "count.one" : "count.many"), count, ClipInfo.size(bytes));
    }

    private void extractJobs(GuiGraphicsExtractor g) {
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        List<ActionCamRecorder.SavingJob> saving = recorder.savingJobs();
        boolean active = recorder.phase() != ActionCamRecorder.Phase.IDLE;
        if (!active && saving.isEmpty()) {
            return;
        }
        int x0 = this.windowLeft + 1;
        int x1 = this.windowRight - 1;
        int top = this.windowTop + HEADER;
        int bottom = this.listTop;
        ThemeDraw.subHeaderBar(g, x0, top, x1, bottom - top);
        int y = top + 4;
        int textX = this.windowLeft + PAD + 8;
        int barRight = this.windowRight - PAD;
        if (active) {
            boolean recording = recorder.phase() == ActionCamRecorder.Phase.RECORDING;
            int rowBottom = y + JOB_ROW - 4;
            g.fill(this.windowLeft + PAD, y, this.windowLeft + PAD + 2, rowBottom, ActionCamRecorder.REC_ACCENT);
            int tileHeight = rowBottom - y - 2;
            int tileWidth = tileHeight * 16 / 9;
            int tileX = textX;
            int tileY = y + 1;
            this.liveTile(g, tileX, tileY, tileWidth, tileHeight, recording, recorder.paused());
            long seconds = recorder.recordedNanos() / 1_000_000_000L;
            RecordingFormat format = recorder.format();
            Component line = recording
                    ? Component.translatable(KEY + (recorder.paused() ? "job.paused" : "job.recording"), ClipInfo.duration(seconds),
                    format == null ? "" : format.label())
                    : Component.translatable(KEY + "job.stopping");
            int lineX = tileX + tileWidth + PAD;
            int lineY = y + (rowBottom - y - Metrics.TEXT_HEIGHT) / 2;
            int lineRight = this.stopButton != null && this.stopButton.visible ? this.stopButton.getX() - PAD : barRight;
            ThemeText.fitted(g, this.font, line, lineX, lineY, lineRight - lineX, recording ? Palette.TEXT : Palette.TEXT_MUTED);
            y += JOB_ROW;
        }
        for (ActionCamRecorder.SavingJob job : saving) {
            g.fill(this.windowLeft + PAD, y + 1, this.windowLeft + PAD + 2, y + JOB_ROW - 5, Palette.ACCENT);
            int percent = Math.round(job.progress().fraction() * 100.0F);
            Component line = Component.translatable(KEY + "job.saving", job.file().getFileName().toString(), job.format().label(), percent);
            ThemeText.fitted(g, this.font, line, textX, y, barRight - textX, Palette.TEXT);
            ThemeDraw.valueBar(g, textX, y + Metrics.LINE_HEIGHT + 1, barRight, y + Metrics.LINE_HEIGHT + 5, job.progress().fraction(), Palette.ACCENT);
            y += JOB_ROW;
        }
    }

    private void liveTile(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean recording, boolean paused) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, recording && !paused ? ActionCamRecorder.REC_ACCENT : Palette.BORDER_STRONG);
        g.fill(x, y, x + width, y + height, 0xFF0E0E10);
        String label = Component.translatable(KEY + (paused ? "live.paused" : recording ? "live" : "live.stopping")).getString();
        int labelWidth = ThemeText.smallWidth(this.font, label);
        int dot = LIVE_DOT_RADIUS * 2 + 1;
        int total = dot + 3 + labelWidth;
        int left = x + (width - total) / 2;
        int cy = y + height / 2;
        if (paused) {
            g.fill(left, cy - LIVE_DOT_RADIUS, left + 2, cy + LIVE_DOT_RADIUS + 1, Palette.WARNING);
            g.fill(left + dot - 2, cy - LIVE_DOT_RADIUS, left + dot, cy + LIVE_DOT_RADIUS + 1, Palette.WARNING);
        } else {
            float pulse = recording ? 0.55F + 0.45F * (float) Math.sin(Util.getMillis() / 1000.0 * Math.PI * 2.0) : 0.35F;
            int color = Palette.withAlpha(ActionCamRecorder.REC_ACCENT, Math.round(90.0F + 165.0F * pulse));
            disc(g, left + LIVE_DOT_RADIUS, cy, LIVE_DOT_RADIUS, color);
        }
        int textColor = paused ? Palette.WARNING : recording ? 0xFFF2F2F2 : Palette.TEXT_MUTED;
        ThemeText.small(g, this.font, label, left + dot + 3, cy - ThemeText.smallLineHeight() / 2, textColor);
    }

    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy + 0.5));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private void extractList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = this.windowLeft + 1;
        int x1 = this.windowRight - 1;
        if (this.clips.isEmpty()) {
            this.extractEmpty(g);
            return;
        }
        boolean scrollable = this.maxScroll() > 0;
        int rowRight = scrollable ? x1 - Metrics.SCROLLBAR_WIDTH - 3 : x1;
        g.enableScissor(x0, this.listTop, x1, this.listBottom);
        int first = Math.max(0, (int) Math.floor(this.scroll / ROW));
        for (int i = first; i < this.clips.size(); i++) {
            int y0 = this.listTop + i * ROW - (int) Math.round(this.scroll);
            if (y0 >= this.listBottom) {
                break;
            }
            ClipInfo clip = this.clips.get(i);
            boolean selected = clip.file().equals(this.selected);
            boolean hovered = mouseX >= x0 && mouseX < rowRight && mouseY >= Math.max(y0, this.listTop) && mouseY < Math.min(y0 + ROW, this.listBottom);
            if (i % 2 == 1) {
                g.fill(x0, y0, rowRight, y0 + ROW, Palette.ROW_STRIPE);
            }
            ThemeDraw.selection(g, x0, y0, rowRight, y0 + ROW, selected, hovered);
            this.extractRow(g, clip, x0, y0, rowRight, selected);
        }
        g.disableScissor();
        if (scrollable) {
            ThemeDraw.scrollbar(g, this.barX(), this.listTop + 2, this.listBottom - this.listTop - 4, this.contentHeight(),
                    this.scroll, this.draggingBar);
        }
    }

    private void extractRow(GuiGraphicsExtractor g, ClipInfo clip, int x0, int y0, int x1, boolean selected) {
        int thumbX = x0 + PAD;
        int thumbY = y0 + (ROW - THUMB_HEIGHT) / 2;
        this.extractThumb(g, clip, thumbX, thumbY);
        int textX = thumbX + THUMB_WIDTH + PAD;
        int textWidth = x1 - textX - PAD;
        int lineY = thumbY + 2;
        ThemeText.fitted(g, this.font, this.dateLine(clip), textX, lineY, textWidth, Palette.TEXT);
        lineY += Metrics.LINE_HEIGHT + 3;
        if (clip.readable()) {
            Component details = Component.translatable(KEY + (clip.audio() ? "details.audio" : "details"), clip.duration(), clip.format(), clip.size());
            ThemeText.fitted(g, this.font, details, textX, lineY, textWidth, selected ? Palette.TEXT : Palette.TEXT_MUTED);
        } else {
            Component broken = Component.translatable(KEY + "unreadable", clip.size());
            ThemeText.fitted(g, this.font, broken, textX, lineY, textWidth, Palette.WARNING);
        }
        lineY += Metrics.LINE_HEIGHT + 3;
        ThemeText.fitted(g, this.font, clip.fileName(), textX, lineY, textWidth, selected ? Palette.TEXT_MUTED : Palette.TEXT_FAINT);
    }

    private void extractThumb(GuiGraphicsExtractor g, ClipInfo clip, int x, int y) {
        int x1 = x + THUMB_WIDTH;
        int y1 = y + THUMB_HEIGHT;
        g.fill(x - 1, y - 1, x1 + 1, y1 + 1, Palette.BORDER_STRONG);
        Thumb thumb = this.thumbFor(clip);
        if (thumb.texture != null) {
            g.blit(thumb.texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x1, y1, 0.0F, 1.0F, 0.0F, 1.0F);
        } else {
            g.fill(x, y, x1, y1, Palette.FIELD);
            ThemeDraw.previewStage(g, x, y, x1, y1);
            int cx = (x + x1) / 2;
            int cy = (y + y1) / 2;
            for (int i = 0; i < 5; i++) {
                g.fill(cx - 2 + i, cy - 5 + i, cx - 1 + i, cy + 6 - i, Palette.TEXT_FAINT);
            }
        }
        if (clip.readable()) {
            String duration = clip.duration();
            int w = this.font.width(duration) + 4;
            g.fill(x1 - w - 1, y1 - Metrics.TEXT_HEIGHT - 3, x1 - 1, y1 - 1, Palette.withAlpha(Palette.SHADOW, 0xC0));
            ThemeText.text(g, this.font, duration, x1 - w + 1, y1 - Metrics.TEXT_HEIGHT - 2, Palette.TEXT);
        }
    }

    private Component dateLine(ClipInfo clip) {
        LocalDateTime when = clip.when(ZoneId.systemDefault());
        return Component.translatable(KEY + "date", two(when.getDayOfMonth()), two(when.getMonthValue()), String.valueOf(when.getYear()),
                two(when.getHour()), two(when.getMinute()), two(when.getSecond()));
    }

    private static String two(int value) {
        return String.format(Locale.ROOT, "%02d", value);
    }

    private void extractEmpty(GuiGraphicsExtractor g) {
        int width = this.windowRight - this.windowLeft - 2 * PAD - 16;
        int centerX = (this.windowLeft + this.windowRight) / 2;
        Component title = Component.translatable(KEY + (this.library().scanned() ? "empty" : "loading"));
        Component hint = Component.translatable(KEY + "empty.hint", RecordingKeys.RECORD.getTranslatedKeyMessage());
        List<FormattedCharSequence> lines = ThemeText.balanced(this.font, hint, width);
        int height = Metrics.LINE_HEIGHT + 6 + lines.size() * Metrics.LINE_HEIGHT;
        int y = this.listTop + Math.max(PAD, (this.listBottom - this.listTop - height) / 2);
        ThemeText.textCentered(g, this.font, title, centerX, y, Palette.TEXT);
        y += Metrics.LINE_HEIGHT + 6;
        for (FormattedCharSequence line : lines) {
            g.text(this.font, line, centerX - this.font.width(line) / 2, y, Palette.TEXT_FAINT, false);
            y += Metrics.LINE_HEIGHT;
        }
    }

    private static final class Thumb {
        @Nullable CompletableFuture<NativeImage> loading;
        @Nullable DynamicTexture texture;
        boolean failed;

        void close() {
            if (this.texture != null) {
                this.texture.close();
                this.texture = null;
            }
            CompletableFuture<NativeImage> pending = this.loading;
            if (pending != null) {
                pending.thenAccept(NativeImage::close);
                this.loading = null;
            }
        }
    }
}
