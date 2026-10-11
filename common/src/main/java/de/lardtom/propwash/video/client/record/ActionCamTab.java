package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.Blaze3D;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.compat.flashback.client.FlashbackBridge;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.FormLayout;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.ui.client.widget.TextField;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.record.CamPower;
import de.lardtom.propwash.video.record.ClipInfo;
import de.lardtom.propwash.video.record.EncoderChoice;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingQuality;
import de.lardtom.propwash.video.record.Stabilizer;
import de.lardtom.propwash.video.record.external.FfmpegLocator;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import de.lardtom.propwash.video.record.path.KeyframeDensity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ActionCamTab extends UiTab {
    public static final String GUIDE_URL = "https://propwashfpv.com/docs/guide/#video-ffmpeg";
    private static final String KEY = "propwash.video.record.tab.";

    private RecordingConfig working = RecordingConfig.get().copy();
    private String probePath = FfmpegLocator.clean(this.working.ffmpegPath);
    private boolean edited;
    private boolean searched;
    private int left;
    private int textWidth;
    private int builtTop;
    private int flowTop;
    private int contentHeight;
    private @Nullable FormLayout form;
    private @Nullable TextField pathField;
    private HardwareEncoders.@Nullable Status preview;

    public record Line(Component text, int color) {
    }

    @Override
    public String id() {
        return "action_cam";
    }

    @Override
    public Component title() {
        return Component.translatable(KEY + "title");
    }

    @Override
    public int order() {
        return 530;
    }

    @Override
    protected void build() {
        RecordingConfig config = this.working;
        ScrollPanel panel = this.beginScroll();
        this.builtTop = this.top();
        FormLayout form = new FormLayout(this.x, this.builtTop, panel.innerWidth());
        this.form = form;
        this.left = form.left();
        this.textWidth = form.width();
        int columnWidth = form.columnWidth();
        form.section(Component.translatable(KEY + (this.flashbackDrone() ? "section.recording.video_only" : "section.recording")));

        CycleButton<Integer> resolution = CycleButton.<Integer>builder(value -> Component.literal(value + "p"), config.height)
                .withValues(RecordingFormat.HEIGHTS)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "resolution.tooltip")))
                .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "resolution"), (button, value) -> {
                    this.working.height = value;
                    this.markEdited();
                    this.host.rebuild();
                });
        this.add(form.place(resolution));

        CycleButton<Integer> rate = CycleButton.<Integer>builder(value -> Component.translatable(KEY + "fps.value", value), config.fps)
                .withValues(RecordingFormat.RATES)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "fps.tooltip")))
                .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "fps"), (button, value) -> {
                    this.working.fps = value;
                    this.markEdited();
                    this.host.rebuild();
                });
        this.add(form.place(rate));

        RecordingFormat planned = RecordingFormat.of(config.height, config.fps);
        CycleButton<RecordingQuality> quality = CycleButton.<RecordingQuality>builder(value -> qualityLabel(value, planned), config.quality)
                .withValues(List.of(RecordingQuality.values()))
                .withTooltip(value -> Tooltip.create(qualityTooltip(value, planned)))
                .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "quality"), (button, value) -> {
                    this.working.quality = value;
                    this.markEdited();
                });
        this.add(form.placeWide(quality));

        CycleButton<EncoderChoice> encoder = CycleButton.<EncoderChoice>builder(value -> Component.translatable(value.translationKey()),
                        config.encoder)
                .withValues(List.of(EncoderChoice.values()))
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "encoder.tooltip." + value.key())))
                .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "encoder"), (button, value) -> {
                    this.working.encoder = value;
                    this.markEdited();
                });
        this.add(form.place(encoder));

        CycleButton<Stabilizer.Mode> stabilization = CycleButton.<Stabilizer.Mode>builder(value -> Component.translatable(value.translationKey()),
                        config.stabilization)
                .withValues(List.of(Stabilizer.Mode.values()))
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "stabilization.tooltip." + value.key())))
                .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "stabilization"), (button, value) -> {
                    this.working.stabilization = value;
                    this.markEdited();
                    this.host.rebuild();
                });
        this.add(form.place(stabilization));

        StepSlider strength = new StepSlider(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, 0, 100, 10, config.stabilizationStrength,
                value -> strengthLabel((int) Math.round(value), this.working.stabilization), value -> {
                    this.working.stabilizationStrength = (int) Math.round(value);
                    this.markEdited();
                });
        strength.percentRange();
        strength.setTooltip(Tooltip.create(Component.translatable(KEY + "strength.tooltip")));
        this.add(form.place(strength));

        CycleButton<Boolean> audio = CycleButton.onOffBuilder(config.audio)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "audio.tooltip")))
                .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "audio"), (button, value) -> {
                    this.working.audio = value;
                    this.markEdited();
                });
        this.add(form.place(audio));

        CycleButton<Boolean> auto = CycleButton.onOffBuilder(config.autoRecord)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "auto.tooltip")))
                .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "auto"), (button, value) -> {
                    this.working.autoRecord = value;
                    this.markEdited();
                });
        this.add(form.place(auto));

        CycleButton<Boolean> indicator = CycleButton.onOffBuilder(config.showIndicator)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "indicator.tooltip")))
                .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "indicator"), (button, value) -> {
                    this.working.showIndicator = value;
                    this.markEdited();
                });
        this.add(form.place(indicator));

        CycleButton<Boolean> grain = CycleButton.onOffBuilder(config.grain)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "grain.tooltip")))
                .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "grain"), (button, value) -> {
                    this.working.grain = value;
                    this.markEdited();
                });
        this.add(form.place(grain));

        if (FlashbackBridge.installed()) {
            form.section(Component.translatable(KEY + "section.flashback"));
            CycleButton<Boolean> flashbackAuto = CycleButton.onOffBuilder(config.flashbackAutoRecord)
                    .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "flashback_auto.tooltip")))
                    .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "flashback_auto"), (button, value) -> {
                        this.working.flashbackAutoRecord = value;
                        this.markEdited();
                    });
            this.add(form.place(flashbackAuto));
            CycleButton<KeyframeDensity> density = CycleButton.<KeyframeDensity>builder(value -> Component.translatable(value.translationKey()),
                            config.flashbackDensity)
                    .withValues(List.of(KeyframeDensity.EVERY_TICK, KeyframeDensity.REDUCED))
                    .withTooltip(value -> Tooltip.create(Component.translatable(KEY + "flashback_density.tooltip." + value.key())))
                    .create(this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT, Component.translatable(KEY + "flashback_density"),
                            (button, value) -> {
                                this.working.flashbackDensity = value;
                                this.markEdited();
                            });
            this.add(form.place(density));
        }

        form.section(Component.translatable(KEY + "section.library"));
        Button recordings = Button.builder(Component.translatable(KEY + "recordings"), button -> this.openRecordings())
                .size(columnWidth, Metrics.CONTROL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(KEY + "recordings.tooltip")))
                .build();
        this.add(form.place(recordings));
        Button folder = Button.builder(Component.translatable(KEY + "open_folder"), button -> this.openFolder())
                .size(columnWidth, Metrics.CONTROL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(KEY + "open_folder.tooltip")))
                .build();
        this.add(form.place(folder));
        form.section(Component.translatable(KEY + "section.ffmpeg"));
        int labelWidth = Metrics.labelWidth(columnWidth);
        TextField path = new TextField(this.host.font(), this.left, this.y, columnWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable(KEY + "ffmpeg.path"));
        path.setMaxLength(RecordingConfig.MAX_PATH_LENGTH);
        path.setValue(config.ffmpegPath);
        path.hint(Component.translatable(KEY + "ffmpeg.path.hint"));
        path.setTooltip(Tooltip.create(Component.translatable(KEY + "ffmpeg.path.tooltip")));
        path.setResponder(text -> {
            if (!text.equals(this.working.ffmpegPath)) {
                this.working.ffmpegPath = text;
                this.markEdited();
            }
        });
        this.add(form.place(path));
        path.setRectangle(columnWidth - labelWidth, Metrics.CONTROL_HEIGHT, path.getX() + labelWidth, path.getY());
        this.pathField = path;
        Button test = Button.builder(Component.translatable(KEY + "ffmpeg.test"), button -> this.test())
                .size(columnWidth, Metrics.CONTROL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(KEY + "ffmpeg.test.tooltip")))
                .build();
        this.add(form.place(test));
        int half = (columnWidth - Metrics.GAP) / 2;
        Button guide = Button.builder(Component.translatable(KEY + "ffmpeg.guide"), button -> this.openGuide())
                .bounds(test.getX() + half + Metrics.GAP, test.getY(), columnWidth - half - Metrics.GAP, Metrics.CONTROL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(KEY + "ffmpeg.guide.tooltip", GUIDE_URL)))
                .build();
        test.setWidth(half);
        this.add(guide);
        form.endRow();
        this.flowTop = form.bottom() - this.builtTop + 2;
        this.textWidth = Math.max(form.width(), panel.innerWidth());
        if (!this.searched) {
            this.searched = true;
            if (this.ffmpegStatus().state() == HardwareEncoders.State.NO_FFMPEG) {
                HardwareEncoders.retest(this.probeSettings(), this.probeFormat());
            }
        }
        this.contentHeight = this.flow(null, this.host.font());
        this.endScroll(this.contentHeight);
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = this.host.font();
        if (this.form != null) {
            this.form.extract(graphics, font, this.top() - this.builtTop);
        }
        TextField path = this.pathField;
        if (path != null && path.visible) {
            ThemeText.text(graphics, font, ThemeText.fit(font, Component.translatable(KEY + "ffmpeg.path"),
                    Math.max(1, path.getX() - this.left - Metrics.LABEL_GAP)), this.left, Metrics.textY(path.getY(), Metrics.CONTROL_HEIGHT),
                    Palette.TEXT_MUTED);
        }
        int used = this.flow(graphics, font);
        if (this.scroll != null && used != this.contentHeight) {
            this.contentHeight = used;
            this.scroll.setContentHeight(used);
            this.scrollOffset = this.scroll.offset();
        }
        this.extractScrollbar(graphics);
    }

    private int flow(@Nullable GuiGraphicsExtractor graphics, Font font) {
        int top = this.top();
        int width = Math.max(40, this.textWidth);
        int y = top + this.flowTop;
        for (Line line : this.ffmpegLines()) {
            y += block(graphics, font, line.text(), this.left, y, width, line.color()) + 2;
        }
        y += Metrics.SECTION_GAP;
        if (graphics != null) {
            ThemeDraw.sectionTitle(graphics, font, Component.translatable(KEY + "section.status"), this.left, y, this.textWidth);
        }
        y += Metrics.SECTION_HEIGHT;
        for (Component line : this.statusLines()) {
            y += block(graphics, font, line, this.left, y, width, Palette.TEXT_MUTED) + 2;
        }
        y += 4;
        y += block(graphics, font, this.hint(), this.left, y, width, Palette.TEXT_FAINT);
        return y - top + 4;
    }

    private static int block(@Nullable GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color) {
        if (graphics != null) {
            return ThemeText.wrapped(graphics, font, text, x, y, width, color);
        }
        return ThemeText.balanced(font, text, width).size() * Metrics.LINE_HEIGHT;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        TextField path = this.pathField;
        if (path != null && path.isFocused() && event.isConfirmation()) {
            this.test();
            return true;
        }
        return false;
    }

    @Override
    public boolean filesDropped(List<Path> files) {
        TextField path = this.pathField;
        if (path == null || files.isEmpty()) {
            return false;
        }
        path.setValue(files.getFirst().toAbsolutePath().toString());
        path.moveCursorToStart(false);
        this.test();
        return true;
    }

    public void test() {
        this.probePath = FfmpegLocator.clean(this.working.ffmpegPath);
        HardwareEncoders.retest(this.probeSettings(), this.probeFormat());
    }

    public HardwareEncoders.Status ffmpegStatus() {
        HardwareEncoders.Status shown = this.preview;
        return shown != null ? shown : HardwareEncoders.status(this.probeSettings(), this.probeFormat());
    }

    void preview(HardwareEncoders.@Nullable Status status) {
        this.preview = status;
    }

    public @Nullable TextField pathField() {
        return this.pathField;
    }

    private HardwareEncoders.Settings probeSettings() {
        return new HardwareEncoders.Settings(true, this.probePath, ActionCamRecorder.ffmpegFolders(this.host.minecraft()));
    }

    RecordingFormat probeFormat() {
        DroneContext context = DroneContext.resolve(this.host.minecraft());
        Optional<ActionCamSpec> spec = context.hasDrone() ? context.build().accessories().camera().flatMap(AccessoryType::camera) : Optional.empty();
        return spec.map(camera -> RecordingFormat.forCamera(camera, this.working.height, this.working.fps))
                .orElseGet(() -> RecordingFormat.of(this.working.height, this.working.fps));
    }

    public void openGuide() {
        ConfirmLinkScreen.confirmLinkNow(this.host.screen(), URI.create(GUIDE_URL), true);
    }

    public List<Line> ffmpegLines() {
        return ffmpegLines(this.ffmpegStatus(), this.probeFormat(), this.working.encoder,
                !FfmpegLocator.clean(this.working.ffmpegPath).equals(this.probePath));
    }

    static List<Line> ffmpegLines(HardwareEncoders.Status status, RecordingFormat format, EncoderChoice choice, boolean untested) {
        String key = KEY + "ffmpeg.status.";
        List<Line> lines = new ArrayList<>();
        String name = status.version().isEmpty() ? "ffmpeg" : "ffmpeg " + status.version();
        String path = status.ffmpeg() == null ? "" : status.ffmpeg().toString();
        switch (status.state()) {
            case OFF, CHECKING -> lines.add(new Line(Component.translatable(key + "checking"), Palette.TEXT_MUTED));
            case NO_FFMPEG -> lines.add(new Line(Component.translatable(key + "missing"), Palette.WARNING));
            case UNUSABLE -> lines.add(new Line(Component.translatable(key + "unusable", path, status.detail()), Palette.ERROR));
            case NO_HARDWARE -> lines.add(new Line(status.detail().isEmpty() ? Component.translatable(key + "no_hardware", name, path)
                    : Component.translatable(key + "no_hardware.detail", name, path, status.detail()), Palette.WARNING));
            case READY -> {
                lines.add(new Line(Component.translatable(key + "ready", name, path, workingText(status, format)), Palette.SUCCESS));
                if (choice == EncoderChoice.BUILTIN) {
                    lines.add(new Line(Component.translatable(key + "builtin_chosen"), Palette.WARNING));
                }
            }
            case BROKEN -> lines.add(new Line(Component.translatable(key + "broken", status.detail()), Palette.ERROR));
        }
        if (status.customMissing()) {
            String problem = status.custom() == FfmpegLocator.CustomPath.NOT_EXECUTABLE ? "custom_not_executable" : "custom_missing";
            lines.add(new Line(Component.translatable(key + problem + (status.ffmpeg() == null ? "" : ".fallback")), Palette.WARNING));
        }
        if (untested) {
            lines.add(new Line(Component.translatable(key + "untested"), Palette.TEXT_MUTED));
        }
        return lines;
    }

    static Component workingText(HardwareEncoders.Status status, RecordingFormat format) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (HardwareEncoders.Trial trial : status.working()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(Component.translatable(KEY + "ffmpeg.status.trial", trial.codec().label(), format.label(), Math.round(trial.fps())));
        }
        if (first) {
            out.append(status.codec() == null ? "?" : status.codec().label());
        }
        return out;
    }

    static Component strengthLabel(int percent, Stabilizer.Mode mode) {
        return Component.translatable(KEY + "strength.value", percent, zoomText(Stabilizer.zoom(mode, percent / 100.0F)));
    }

    static String zoomText(float zoom) {
        String text = String.format(Locale.ROOT, "%.2f", zoom);
        String language = Minecraft.getInstance().options.languageCode;
        return language != null && language.startsWith("de") ? text.replace('.', ',') : text;
    }

    static Component encoderStatus(HardwareEncoders.Status status, int threads) {
        return switch (status.state()) {
            case OFF -> Component.translatable(KEY + "status.encoder.builtin", threads);
            case CHECKING -> Component.translatable(KEY + "status.encoder.checking");
            case READY -> Component.translatable(KEY + "status.encoder.ready", status.codec() == null ? "?" : status.codec().label());
            case NO_FFMPEG -> Component.translatable(KEY + "status.encoder.no_ffmpeg", threads);
            case NO_HARDWARE, UNUSABLE -> Component.translatable(KEY + "status.encoder.no_hardware", threads);
            case BROKEN -> Component.translatable(KEY + "status.encoder.broken", status.detail());
        };
    }

    Component cropStatus(RecordingFormat format, Stabilizer.Mode mode) {
        float zoom = Stabilizer.zoom(mode, this.working.strength());
        int windowWidth = this.host.minecraft().getWindow().getWidth();
        int windowHeight = this.host.minecraft().getWindow().getHeight();
        double imageWidth = Math.min(windowWidth, windowHeight * 16.0 / 9.0);
        double ratio = imageWidth / zoom / format.width();
        if (ratio >= 0.999) {
            return Component.translatable(KEY + "status.crop.lossless", zoomText(zoom), windowWidth, windowHeight);
        }
        return Component.translatable(KEY + "status.crop.upscaled", zoomText(zoom), Math.round((1.0 / ratio - 1.0) * 100.0), windowWidth,
                windowHeight);
    }

    static Component qualityLabel(RecordingQuality quality, RecordingFormat format) {
        return Component.translatable(KEY + "quality.value", Component.translatable(quality.translationKey()),
                quality.typicalMegabytesPerMinute(format));
    }

    static Component qualityTooltip(RecordingQuality quality, RecordingFormat format) {
        return Component.translatable(KEY + "quality.tooltip." + quality.key(), format.label(), quality.typicalMegabytesPerMinute(format),
                quality.maxMegabytesPerMinute(format));
    }

    boolean flashbackDrone() {
        DroneContext context = DroneContext.resolve(this.host.minecraft());
        return context.hasDrone() && context.build().accessories().flashbackCam().isPresent();
    }

    Component hint() {
        Component key = RecordingKeys.RECORD.getTranslatedKeyMessage();
        Component function = Component.translatable(SwitchFunction.RECORD.translationKey());
        if (this.flashbackDrone()) {
            return Component.translatable(KEY + "hint.flashback_cam", key, function);
        }
        Component hint = Component.translatable(KEY + "hint", key, function);
        if (!FlashbackBridge.installed()) {
            return hint;
        }
        return Component.empty().append(hint).append(" ").append(Component.translatable(KEY + "hint.flashback"));
    }

    List<Component> statusLines() {
        Minecraft minecraft = this.host.minecraft();
        DroneContext context = DroneContext.resolve(minecraft);
        Optional<AccessoryType> camera = context.hasDrone() ? context.build().accessories().camera() : Optional.empty();
        Optional<AccessoryType> flashbackCam = context.hasDrone() ? context.build().accessories().flashbackCam() : Optional.empty();
        ArrayList<Component> lines = new ArrayList<>();
        if (!context.hasDrone()) {
            lines.add(Component.translatable(KEY + "status.no_drone"));
        } else if (flashbackCam.isPresent()) {
            if (FlashbackBridge.ready()) {
                double fov = FovMath.verticalFromHorizontal(VideoSettings.get().horizontalFov(null, context.build().video()), FlashbackCam.EXPORT_ASPECT);
                lines.add(Component.translatable(KEY + "status.flashback_cam", context.name(), Math.round(fov)));
            } else {
                lines.add(Component.translatable(KEY + "status.flashback_missing", context.name()));
            }
        } else if (camera.isEmpty() || camera.get().camera().isEmpty()) {
            lines.add(Component.translatable(KEY + "status.no_camera", context.name()));
        } else {
            ActionCamSpec spec = camera.get().camera().get();
            RecordingFormat format = RecordingFormat.forCamera(spec, this.working.height, this.working.fps);
            Stabilizer.Mode mode = spec.stabilization() ? this.working.stabilization : Stabilizer.Mode.OFF;
            lines.add(Component.translatable(KEY + "status.camera", Component.translatable(camera.get().translationKey()), format.label(),
                    Component.translatable(mode.translationKey())));
            if (format.height() < this.working.height || format.fps() < this.working.fps) {
                lines.add(Component.translatable(KEY + "status.capped", format.label()));
            }
            HardwareEncoders.Status shown = this.preview;
            HardwareEncoders.Settings settings = new HardwareEncoders.Settings(this.working.encoder == EncoderChoice.AUTO,
                    this.probePath, ActionCamRecorder.ffmpegFolders(minecraft));
            lines.add(encoderStatus(shown != null && settings.enabled() ? shown : HardwareEncoders.status(settings, format),
                    this.working.threads()));
            if (mode != Stabilizer.Mode.OFF) {
                lines.add(this.cropStatus(format, mode));
            }
            if (spec.ownBattery()) {
                QuadcopterEntity entity = context.entity();
                float charge = entity != null ? entity.cameraCharge() : cameraChargeOf(context);
                long minutes = Math.round(CamPower.secondsLeft(charge, spec, format.height()) / 60.0);
                lines.add(Component.translatable(KEY + "status.battery", Math.round(charge * 100.0F), minutes));
            } else {
                lines.add(Component.translatable(KEY + "status.flight_power", Math.round(spec.recordWatts(format.height()))));
            }
        }
        flashbackLines(lines);
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (recorder.recording()) {
            lines.add(Component.translatable(KEY + "status.recording", ClipInfo.duration(recorder.recordedNanos() / 1_000_000_000L)));
        }
        for (ActionCamRecorder.SavingJob job : recorder.savingJobs()) {
            lines.add(Component.translatable(KEY + "status.saving", Math.round(job.progress().fraction() * 100.0F)));
        }
        Path last = recorder.lastSaved();
        if (last != null) {
            lines.add(Component.translatable(KEY + "status.last", last.getFileName().toString()));
        }
        String error = recorder.lastError();
        if (error != null && !error.isEmpty()) {
            lines.add(Component.translatable(KEY + "status.error", error));
        }
        return lines;
    }

    static void flashbackLines(List<Component> lines) {
        switch (FlashbackBridge.status()) {
            case ABSENT -> {
                return;
            }
            case READY -> lines.add(Component.translatable(KEY + "status.flashback_ready", FlashbackBridge.version().orElse("?")));
            case INCOMPATIBLE -> lines.add(Component.translatable(KEY + "status.flashback_incompatible", FlashbackBridge.version().orElse("?"),
                    String.valueOf(FlashbackBridge.problem())));
        }
        FlashbackCam cam = FlashbackCam.INSTANCE;
        if (cam.waiting()) {
            lines.add(Component.translatable(KEY + "status.flashback_waiting"));
        } else if (cam.recording()) {
            lines.add(Component.translatable(KEY + "status.flashback_recording", ClipInfo.duration(Math.round(cam.recordedSeconds()))));
        }
        FlashbackCam.Handover last = cam.lastHandover();
        if (last != null) {
            lines.add(Component.translatable(KEY + "status.flashback_last", last.scene(), last.keyframes()));
        }
    }

    private static float cameraChargeOf(DroneContext context) {
        Float stored = context.stack().get(CoreComponents.CAMERA_CHARGE.get());
        return stored == null ? CamPower.FULL : CamPower.clamp(stored);
    }

    private void openRecordings() {
        RecordingsScreen.open(this.host.screen());
    }

    private void openFolder() {
        Path dir = ActionCamRecorder.INSTANCE.library().directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        Blaze3D.openPath(dir);
    }

    @Override
    public void resetDefaults() {
        this.working = new RecordingConfig();
        this.probePath = "";
        this.markEdited();
        this.host.rebuild();
    }

    @Override
    public void save() {
        if (!this.edited) {
            return;
        }
        RecordingConfig saved = this.working.copy();
        saved.validate();
        RecordingConfig.CONFIG.set(saved);
        this.edited = false;
    }

    RecordingConfig working() {
        return this.working;
    }

    private void markEdited() {
        this.edited = true;
        this.host.changed();
    }
}
