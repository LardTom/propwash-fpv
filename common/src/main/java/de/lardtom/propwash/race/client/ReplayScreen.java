package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.race.GhostAccess;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.ReplayExport;
import de.lardtom.propwash.race.ReplayMarks;
import de.lardtom.propwash.race.ServerRulesBadge;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.record.ClipInfo;
import de.lardtom.propwash.video.record.RecordingJob;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class ReplayScreen extends Screen implements ThemedScreen {
    private static final int PAD = 6;
    private static final int BAR_HEIGHT = 44;
    private static final int TIMELINE_HEIGHT = 10;
    private static final int INFO_WIDTH = 220;
    private static final int INFO_HEIGHT = 42;
    private boolean rulesBadgeShown;
    private static final int CONFIRM_TICKS = 60;
    private static final int SHADE = 0xB0101010;
    private static final int TRACK = 0xFF3A3A3A;
    private static final int PLAYED = 0xFFFFBB00;
    private static final int MARK = 0xFFE8E8E8;
    private static final int LAP_MARK = 0xFF5BC85B;

    private static @Nullable ReplayScreen active;

    private final @Nullable Screen parent;
    private final int courseId;
    private final RacePayloads.BoardRow row;
    private int request;
    private boolean requested;
    private GhostDownloads.@Nullable Status failure;
    private boolean keepSession;
    private boolean initializing;
    private boolean dragging;
    private int confirmRemove;
    private int timelineLeft;
    private int timelineRight;
    private int timelineTop;
    private long frames;
    private @Nullable FlatButton playButton;
    private @Nullable FlatButton speedButton;
    private @Nullable FlatButton viewButton;
    private @Nullable FlatButton removeButton;
    private @Nullable FlatButton videoButton;
    private @Nullable FlatButton cancelButton;
    private ReplayExport.@Nullable Block shownBlock;
    private ReplayVideo.Stage shownStage = ReplayVideo.Stage.IDLE;
    private int cornerWidth;

    public ReplayScreen(@Nullable Screen parent, int courseId, RacePayloads.BoardRow row) {
        super(Component.translatable("propwash.race.replay.title"));
        this.parent = parent;
        this.courseId = courseId;
        this.row = row;
    }

    public static @Nullable ReplayScreen active() {
        return active;
    }

    public RacePayloads.BoardRow row() {
        return this.row;
    }

    public int courseId() {
        return this.courseId;
    }

    public GhostDownloads.@Nullable Status failure() {
        return this.failure;
    }

    public boolean loaded() {
        return ReplaySession.INSTANCE.active() && ReplaySession.INSTANCE.row() == this.row;
    }

    public long frames() {
        return this.frames;
    }

    public @Nullable FlatButton playButton() {
        return this.playButton;
    }

    public @Nullable FlatButton speedButton() {
        return this.speedButton;
    }

    public @Nullable FlatButton viewButton() {
        return this.viewButton;
    }

    public @Nullable FlatButton removeButton() {
        return this.removeButton;
    }

    public @Nullable FlatButton videoButton() {
        return this.videoButton;
    }

    public @Nullable FlatButton cancelButton() {
        return this.cancelButton;
    }

    public ReplayExport.@Nullable Block videoBlock() {
        return this.shownBlock;
    }

    public boolean removeArmed() {
        return this.confirmRemove > 0;
    }

    private boolean mayRemove() {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        return board != null && board.mayRemove();
    }

    @Override
    protected void init() {
        active = this;
        this.keepSession = false;
        if (!this.requested) {
            this.requested = true;
            this.initializing = true;
            try {
                int sent = GhostDownloads.INSTANCE.fetch(this.courseId, this.row.key(), GhostAccess.Purpose.REPLAY, this::accept);
                if (!this.loaded() && this.failure == null) {
                    this.request = sent;
                }
            } finally {
                this.initializing = false;
            }
        }
        int bottom = this.height - PAD;
        int buttonY = bottom - Metrics.CONTROL_HEIGHT;
        this.timelineLeft = PAD + 4;
        this.timelineRight = this.width - PAD - 4;
        this.timelineTop = buttonY - PAD - TIMELINE_HEIGHT;
        this.shownStage = ReplayVideo.INSTANCE.stage();
        this.playButton = null;
        this.speedButton = null;
        this.viewButton = null;
        this.removeButton = null;
        this.videoButton = null;
        this.cancelButton = null;
        this.cornerWidth = 0;
        if (this.shownStage == ReplayVideo.Stage.RECORDING) {
            Component cancel = Component.translatable("propwash.race.replay.video.cancel");
            int cancelWidth = this.font.width(cancel) + 16;
            this.cancelButton = this.addRenderableWidget(new FlatButton(this.width - PAD - cancelWidth, buttonY, cancelWidth, Metrics.CONTROL_HEIGHT,
                    cancel, FlatButton.Style.NORMAL, this::cancelVideo));
            return;
        }
        int x = PAD;
        boolean ready = this.loaded();
        ReplaySession session = ReplaySession.INSTANCE;
        FlatButton back = new FlatButton(x, buttonY, 26, Metrics.CONTROL_HEIGHT, Component.literal("«"), FlatButton.Style.NORMAL,
                () -> this.skip(-ReplaySession.SKIP_NANOS));
        back.setTooltip(Tooltip.create(Component.translatable("propwash.race.replay.back5")));
        back.active = ready;
        this.addRenderableWidget(back);
        x += 26 + Metrics.GAP;
        Component play = Component.translatable(ready && session.playing() ? "propwash.race.replay.pause" : "propwash.race.replay.play");
        int playWidth = Math.max(this.font.width(Component.translatable("propwash.race.replay.pause")),
                this.font.width(Component.translatable("propwash.race.replay.play"))) + 16;
        FlatButton playButton = new FlatButton(x, buttonY, playWidth, Metrics.CONTROL_HEIGHT, play, FlatButton.Style.PRIMARY, this::togglePlay);
        playButton.active = ready;
        this.playButton = this.addRenderableWidget(playButton);
        x += playWidth + Metrics.GAP;
        FlatButton forward = new FlatButton(x, buttonY, 26, Metrics.CONTROL_HEIGHT, Component.literal("»"), FlatButton.Style.NORMAL,
                () -> this.skip(ReplaySession.SKIP_NANOS));
        forward.setTooltip(Tooltip.create(Component.translatable("propwash.race.replay.forward5")));
        forward.active = ready;
        this.addRenderableWidget(forward);
        x += 26 + Metrics.GAP;
        Component close = Component.translatable("propwash.race.replay.close");
        int closeWidth = this.font.width(close) + 16;
        int right = this.width - PAD;
        this.addRenderableWidget(new FlatButton(right - closeWidth, buttonY, closeWidth, Metrics.CONTROL_HEIGHT, close, FlatButton.Style.NORMAL,
                this::onClose));
        right -= closeWidth + Metrics.GAP;
        int speedWidth = this.font.width(Component.translatable("propwash.race.replay.speed", "0.25×")) + 16;
        int viewWidth = Math.max(this.font.width(Component.translatable("propwash.race.replay.view.fpv")),
                this.font.width(Component.translatable("propwash.race.replay.view.chase"))) + 16;
        int room = right - x;
        boolean compact = speedWidth + Metrics.GAP + viewWidth > room;
        if (compact) {
            speedWidth = this.font.width("0.25×") + 12;
            viewWidth = Math.max(24, room - speedWidth - Metrics.GAP);
        }
        Component speed = compact ? Component.literal(speedText(session.speed()))
                : Component.translatable("propwash.race.replay.speed", speedText(session.speed()));
        FlatButton speedButton = new FlatButton(x, buttonY, speedWidth, Metrics.CONTROL_HEIGHT, speed, FlatButton.Style.NORMAL, this::cycleSpeed);
        speedButton.setTooltip(Tooltip.create(Component.translatable("propwash.race.replay.speed.tooltip")));
        speedButton.active = ready;
        this.speedButton = this.addRenderableWidget(speedButton);
        x += speedWidth + Metrics.GAP;
        Component view = Component.translatable(session.view() == ReplaySession.View.FPV ? "propwash.race.replay.view.fpv" : "propwash.race.replay.view.chase");
        FlatButton viewButton = new FlatButton(x, buttonY, viewWidth, Metrics.CONTROL_HEIGHT, view, FlatButton.Style.NORMAL, this::toggleView);
        viewButton.setTooltip(Tooltip.create(Component.translatable("propwash.race.replay.view.tooltip")));
        viewButton.active = ready;
        this.viewButton = this.addRenderableWidget(viewButton);
        int cornerY = PAD;
        if (this.mayRemove()) {
            Component remove = Component.translatable(this.confirmRemove > 0 ? "propwash.race.ghosts.remove.confirm" : "propwash.race.ghosts.remove");
            int removeWidth = this.font.width(Component.translatable("propwash.race.ghosts.remove.confirm")) + 16;
            FlatButton removeButton = new FlatButton(this.width - PAD - removeWidth, PAD, removeWidth, Metrics.CONTROL_HEIGHT, remove,
                    FlatButton.Style.DANGER, this::removeTime);
            removeButton.setTooltip(Tooltip.create(Component.translatable("propwash.race.ghosts.remove.tooltip", this.row.name())));
            this.removeButton = this.addRenderableWidget(removeButton);
            this.cornerWidth = removeWidth;
            cornerY += Metrics.CONTROL_HEIGHT + Metrics.GAP;
        }
        Component video = Component.translatable("propwash.race.replay.video");
        int videoWidth = this.font.width(video) + 16;
        this.videoButton = this.addRenderableWidget(new FlatButton(this.width - PAD - videoWidth, cornerY, videoWidth, Metrics.CONTROL_HEIGHT, video,
                FlatButton.Style.NORMAL, this::startVideo));
        this.cornerWidth = Math.max(this.cornerWidth, videoWidth);
        this.shownBlock = null;
        this.refreshVideoButton();
    }

    private void refreshVideoButton() {
        FlatButton button = this.videoButton;
        if (button == null) {
            return;
        }
        ReplayExport.Block block = this.loaded() ? ReplayVideo.INSTANCE.block(this.minecraft) : ReplayExport.Block.LOADING;
        button.active = block == ReplayExport.Block.NONE;
        if (block != this.shownBlock) {
            this.shownBlock = block;
            button.setTooltip(Tooltip.create(Component.translatable(block.translationKey())));
        }
    }

    public void startVideo() {
        if (this.loaded() && ReplayVideo.INSTANCE.start(this.minecraft)) {
            this.rebuildWidgets();
        }
    }

    public void cancelVideo() {
        ReplayVideo.INSTANCE.cancel();
        this.rebuildWidgets();
    }

    static String speedText(double speed) {
        if (speed == Math.rint(speed)) {
            return (int) speed + "×";
        }
        return String.format(Locale.ROOT, "%s×", Double.toString(speed));
    }

    private void accept(GhostDownloads.Result result) {
        this.request = 0;
        if (this.minecraft == null || this.minecraft.gui.screen() != this) {
            return;
        }
        if (!result.loaded() || result.ghost() == null || result.track() == null) {
            this.failure = result.status();
        } else {
            ReplaySession.INSTANCE.start(this.minecraft, this.courseId, this.row, result.ghost(), result.track());
            this.failure = null;
        }
        if (!this.initializing) {
            this.rebuildWidgets();
        }
    }

    public void togglePlay() {
        if (this.loaded() && !ReplayVideo.INSTANCE.recording()) {
            ReplaySession.INSTANCE.togglePlay();
            this.rebuildWidgets();
        }
    }

    public void skip(long nanos) {
        if (this.loaded() && !ReplayVideo.INSTANCE.recording()) {
            ReplaySession.INSTANCE.skip(nanos);
        }
    }

    public void cycleSpeed() {
        if (this.loaded() && !ReplayVideo.INSTANCE.recording()) {
            ReplaySession.INSTANCE.cycleSpeed();
            this.rebuildWidgets();
        }
    }

    public void toggleView() {
        if (this.loaded() && !ReplayVideo.INSTANCE.recording()) {
            ReplaySession.INSTANCE.setView(ReplaySession.INSTANCE.view().next());
            this.rebuildWidgets();
        }
    }

    public void removeTime() {
        if (this.confirmRemove <= 0) {
            this.confirmRemove = CONFIRM_TICKS;
            this.rebuildWidgets();
            return;
        }
        this.confirmRemove = 0;
        LeaderboardScreen.sendRemove(this.courseId, this.row);
        if (this.parent instanceof LeaderboardScreen board) {
            board.select(null);
        }
        this.onClose();
    }

    @Override
    public void tick() {
        if (ReplayVideo.INSTANCE.stage() != this.shownStage) {
            this.rebuildWidgets();
        }
        this.refreshVideoButton();
        if (this.confirmRemove > 0) {
            this.confirmRemove--;
            if (this.confirmRemove == 0) {
                this.rebuildWidgets();
            }
        }
        FlatButton play = this.playButton;
        if (play != null && this.loaded()) {
            String key = ReplaySession.INSTANCE.playing() ? "propwash.race.replay.pause" : "propwash.race.replay.play";
            if (!play.getMessage().equals(Component.translatable(key))) {
                play.setMessage(Component.translatable(key));
            }
        }
    }

    @Override
    public void onClose() {
        if (ReplayVideo.INSTANCE.recording()) {
            this.cancelVideo();
            return;
        }
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void removed() {
        if (active == this) {
            active = null;
        }
        ReplayVideo.INSTANCE.forget();
        if (!this.keepSession) {
            if (this.request != 0) {
                GhostDownloads.INSTANCE.cancel(this.request);
                this.request = 0;
            }
            ReplaySession.INSTANCE.stop();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (ReplayVideo.INSTANCE.recording()) {
            if (key == InputConstants.KEY_ESCAPE) {
                this.cancelVideo();
            }
            return true;
        }
        if (this.loaded()) {
            if (key == InputConstants.KEY_SPACE) {
                this.togglePlay();
                return true;
            }
            if (key == InputConstants.KEY_LEFT) {
                this.skip(-ReplaySession.SKIP_NANOS);
                return true;
            }
            if (key == InputConstants.KEY_RIGHT) {
                this.skip(ReplaySession.SKIP_NANOS);
                return true;
            }
            if (key == InputConstants.KEY_V) {
                this.toggleView();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (this.loaded() && !ReplayVideo.INSTANCE.recording() && this.onTimeline(event.x(), event.y())) {
            this.dragging = true;
            this.seekTo(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging) {
            this.seekTo(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    private boolean onTimeline(double x, double y) {
        return x >= this.timelineLeft - 2 && x <= this.timelineRight + 2 && y >= this.timelineTop - 3 && y <= this.timelineTop + TIMELINE_HEIGHT + 3;
    }

    public void seekTo(double x) {
        if (ReplayVideo.INSTANCE.recording()) {
            return;
        }
        ReplaySession.INSTANCE.seek(this.timeAt(x));
    }

    private long timeAt(double x) {
        ReplaySession session = ReplaySession.INSTANCE;
        int width = Math.max(1, this.timelineRight - this.timelineLeft);
        double fraction = Math.max(0.0, Math.min(1.0, (x - this.timelineLeft) / width));
        return session.begin() + Math.round(session.duration() * fraction);
    }

    public int timelineX(long nanos) {
        ReplaySession session = ReplaySession.INSTANCE;
        long duration = Math.max(1L, session.duration());
        long at = Math.max(0L, Math.min(nanos - session.begin(), duration));
        return this.timelineLeft + (int) Math.round((this.timelineRight - this.timelineLeft) * (double) at / duration);
    }

    public String clock() {
        return RaceFormat.time(this.loaded() ? ReplaySession.INSTANCE.lapTime() : 0L);
    }

    public int timelineY() {
        return this.timelineTop + TIMELINE_HEIGHT / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        this.frames++;
        g.fill(0, this.height - BAR_HEIGHT - PAD, this.width, this.height, SHADE);
        this.extractInfo(g, mouseX, mouseY);
        if (this.loaded()) {
            this.extractTimeline(g, mouseX, mouseY);
        } else {
            this.extractStatus(g);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    public boolean rulesBadgeShown() {
        return this.rulesBadgeShown;
    }

    public @Nullable Component videoStatus() {
        ReplayVideo video = ReplayVideo.INSTANCE;
        ReplaySession session = ReplaySession.INSTANCE;
        return switch (video.stage()) {
            case IDLE -> null;
            case RECORDING -> Component.translatable("propwash.race.replay.video.recording",
                    Component.translatable(session.view() == ReplaySession.View.FPV ? "propwash.race.replay.view.fpv" : "propwash.race.replay.view.chase"),
                    ActionCamRecorder.INSTANCE.replayFormat().label());
            case SAVING -> {
                RecordingJob.Progress saving = video.saving();
                yield Component.translatable("propwash.race.replay.video.saving", saving == null ? 0 : Math.round(saving.fraction() * 100.0F));
            }
            case SAVED -> Component.translatable("propwash.race.replay.video.saved", video.savedName());
            case CANCELLED -> Component.translatable("propwash.race.replay.video.cancelled");
            case FAILED -> Component.translatable("propwash.race.replay.video.failed", video.error());
        };
    }

    private int videoStatusColor() {
        return switch (ReplayVideo.INSTANCE.stage()) {
            case RECORDING -> ActionCamRecorder.REC_ACCENT;
            case SAVED -> Palette.SUCCESS;
            case FAILED -> Palette.ERROR;
            default -> Palette.TEXT_MUTED;
        };
    }

    public @Nullable Component gatesLabel() {
        int gates = ReplaySession.INSTANCE.gates();
        return gates > 0 ? Component.translatable("propwash.race.replay.marks", gates) : null;
    }

    private void extractInfo(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int left = PAD;
        int top = PAD;
        int width = Math.min(INFO_WIDTH, this.width - 2 * PAD - (this.cornerWidth == 0 ? 0 : this.cornerWidth + Metrics.GAP));
        g.fill(left, top, left + width, top + INFO_HEIGHT, SHADE);
        Component head = Component.translatable("propwash.race.replay.head", this.row.name(), RaceFormat.time(this.row.nanos()),
                LeaderboardScreen.className(this.row.frame()));
        ThemeText.fitted(g, this.font, head, left + 4, top + 4, width - 8, Palette.TEXT_MUTED);
        ReplaySession session = ReplaySession.INSTANCE;
        String clock = this.clock();
        g.pose().pushMatrix();
        g.pose().translate(left + 4, top + 16);
        g.pose().scale(2.0F, 2.0F);
        ThemeText.text(g, this.font, clock, 0, 0, this.loaded() && session.time() >= this.row.nanos() ? Palette.SUCCESS : Palette.TEXT);
        g.pose().popMatrix();
        this.rulesBadgeShown = false;
        if (this.loaded()) {
            Component mode = Component.translatable(session.view() == ReplaySession.View.FPV ? "propwash.race.replay.view.fpv" : "propwash.race.replay.view.chase");
            ThemeText.textRight(g, this.font, mode, left + width - 4, top + 28, Palette.TEXT_FAINT);
            GhostTrack track = session.track();
            List<ServerRulesBadge.Line> rules = ServerRulesText.entry(track == null ? null : track.rules());
            int badgeLeft = left + width - 4 - ServerRulesText.width(this.font);
            if (!rules.isEmpty() && badgeLeft > left + 8 + 2 * this.font.width(clock)) {
                int badgeRight = badgeLeft + ServerRulesText.draw(g, this.font, badgeLeft, top + 15, top + 26);
                this.rulesBadgeShown = true;
                if (mouseX >= badgeLeft && mouseX < badgeRight && mouseY >= top + 15 && mouseY < top + 26) {
                    ThemeText.tooltipWrapped(g, this.font, ServerRulesText.entryTooltip(rules), 240, mouseX, mouseY);
                }
            }
        }
    }

    private void extractStatus(GuiGraphicsExtractor g) {
        Component text = this.failure == null ? Component.translatable("propwash.race.replay.loading")
                : Component.translatable("propwash.race.replay.failed." + this.failure.name().toLowerCase(Locale.ROOT));
        int center = this.width / 2;
        int y = this.timelineTop;
        ThemeText.fittedCentered(g, this.font, text, center, y, this.width - 2 * PAD, this.failure == null ? Palette.TEXT_MUTED : Palette.ERROR);
    }

    private void extractTimeline(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ReplaySession session = ReplaySession.INSTANCE;
        ReplayVideo video = ReplayVideo.INSTANCE;
        boolean recording = video.recording();
        int top = this.timelineTop;
        int mid = top + TIMELINE_HEIGHT / 2;
        g.fill(this.timelineLeft, mid - 2, this.timelineRight, mid + 2, TRACK);
        int head = this.timelineX(session.time());
        g.fill(this.timelineLeft, mid - 2, head, mid + 2, recording ? ActionCamRecorder.REC_ACCENT : PLAYED);
        GhostTrack shown = session.track();
        long lap = shown == null ? this.row.nanos() : shown.lapNanos();
        for (long mark : session.marks()) {
            int x = this.timelineX(mark);
            g.fill(x, top, x + 1, top + TIMELINE_HEIGHT, ReplayMarks.startOrFinish(mark, lap) ? LAP_MARK : MARK);
        }
        ThemeDraw.box(g, head - 2, top - 1, head + 3, top + TIMELINE_HEIGHT + 1, Palette.TEXT);
        String time = recording ? ClipInfo.duration((session.time() - session.begin()) / LapTracker.SECOND) + " / "
                + ClipInfo.duration(Math.round(session.duration() / (double) LapTracker.SECOND))
                : RaceFormat.time(session.lapTime()) + " / " + RaceFormat.time(this.row.nanos());
        ThemeText.textRight(g, this.font, time, this.timelineRight, top - Metrics.LINE_HEIGHT, Palette.TEXT_MUTED);
        Component status = this.videoStatus();
        if (status != null) {
            ThemeText.fitted(g, this.font, status, this.timelineLeft, top - Metrics.LINE_HEIGHT,
                    this.timelineRight - this.timelineLeft - this.font.width(time) - Metrics.GAP, this.videoStatusColor());
        } else {
            Component gates = this.gatesLabel();
            if (gates != null) {
                ThemeText.text(g, this.font, gates, this.timelineLeft, top - Metrics.LINE_HEIGHT, Palette.TEXT_FAINT);
            }
        }
        if (!recording && this.onTimeline(mouseX, mouseY) && !this.dragging) {
            long at = Math.max(0L, this.timeAt(mouseX));
            ThemeText.tooltip(g, this.font, Component.literal(RaceFormat.time(at)), mouseX, mouseY);
        }
    }
}
