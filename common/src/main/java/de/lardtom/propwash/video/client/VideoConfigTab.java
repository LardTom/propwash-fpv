package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.sim.FpvRig;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.InfoField;
import de.lardtom.propwash.video.CameraShake;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.ImageFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public final class VideoConfigTab extends ConfigTab {
    private static final int ROW_HEIGHT = Metrics.CONTROL_HEIGHT;
    private static final int ROW_STEP = Metrics.ROW_HEIGHT;
    private static final int COLUMN_GAP = Metrics.GAP;
    private static final int MAX_COLUMN_WIDTH = Metrics.COLUMN_MAX;

    private VideoConfig draft = VideoSettings.get().copy();
    private @Nullable VideoSystem profile = wornGoggles();
    private int columnWidth;
    private int columns;
    private int row;
    private int left;
    private int statusY;
    private int cameraY;
    private int formatY;
    private @Nullable VideoType camera;
    private boolean retryShown;

    @Override
    public String id() {
        return "video";
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.video.config.title");
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    protected void build() {
        this.columns = this.width >= 2 * Metrics.COLUMN_MIN + COLUMN_GAP ? 2 : 1;
        this.columnWidth = Math.min(MAX_COLUMN_WIDTH, (this.width - (this.columns - 1) * COLUMN_GAP) / this.columns);
        this.left = this.x;
        this.row = 0;
        this.profile = wornGoggles();
        this.camera = contextCamera();
        this.retryShown = shaderFailed();
        if (!VideoTabRules.showsSettings(this.profile)) {
            return;
        }
        if (VideoTabRules.showsCreative(this.profile)) {
            this.place(CycleButton.<Boolean>builder(value -> Component.translatable(value ? "propwash.video.config.fov_source.module"
                            : "propwash.video.config.fov_source.own"), this.draft.creativeFovFromModule)
                    .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                    .withTooltip(value -> Tooltip.create(Component.translatable("propwash.video.config.fov_source.tooltip")))
                    .create(0, 0, this.columnWidth, ROW_HEIGHT, Component.translatable("propwash.video.config.fov_source"), (button, value) -> {
                        this.draft.creativeFovFromModule = value;
                        this.host.rebuild();
                        this.host.changed();
                    }));
            StepSlider fov = this.place(this.intSlider(this.draft.creativeFovFromModule ? moduleFov(this.camera) : this.draft.fovCreative,
                    FovMath.MIN_HORIZONTAL_DEGREES, FovMath.MAX_HORIZONTAL_DEGREES, 1,
                    value -> Component.translatable("propwash.video.config.fov", Component.translatable("propwash.video.unit.degrees", value)),
                    value -> this.draft.fovCreative = value));
            fov.setTooltip(Tooltip.create(Component.translatable("propwash.video.config.fov.tooltip")));
            fov.active = !this.draft.creativeFovFromModule;
        } else {
            InfoField fov = this.place(new InfoField(0, 0, this.columnWidth, ROW_HEIGHT, Component.translatable("propwash.video.config.fov_module"),
                    fovValue(this.camera), Component.translatable("propwash.video.config.fov_module.source")));
            fov.setTooltip(Tooltip.create(Component.translatable("propwash.video.config.fov_module.tooltip")));
        }
        this.place(CycleButton.<ImageFormat>builder(this::formatName, this.draft.imageFormat)
                .withValues(List.of(ImageFormat.values()))
                .withTooltip(value -> Tooltip.create(Component.translatable("propwash.video.config.format.tooltip")))
                .create(0, 0, this.columnWidth, ROW_HEIGHT, Component.translatable("propwash.video.config.format"), (button, value) -> {
                    this.draft.imageFormat = value;
                    FpvImage.draft(value);
                    this.host.rebuild();
                    this.host.changed();
                }));
        this.place(this.intSlider(this.draft.lensStrength, 0, FisheyeLens.MAX_PERCENT, FisheyeLens.PERCENT_STEP, VideoConfigTab::lensLabel,
                value -> this.draft.lensStrength = value))
                .setTooltip(Tooltip.create(Component.translatable("propwash.video.config.lens.tooltip", FisheyeLens.DEFAULT_PERCENT)));
        this.place(this.intSlider(Math.round(this.draft.effectStrength * 100.0F), 0, 100, 5,
                value -> Component.translatable("propwash.video.config.strength", Component.translatable("propwash.video.unit.percent", value)),
                value -> this.draft.effectStrength = value / 100.0F))
                .setTooltip(Tooltip.create(Component.translatable("propwash.video.config.strength.tooltip")));
        this.place(this.intSlider(Math.round(this.draft.cameraShake * 100.0F), 0, Math.round(CameraShake.MAX_INTENSITY * 100.0F), 10,
                value -> Component.translatable("propwash.video.config.camera_shake", value == 0
                        ? Component.translatable("propwash.video.config.camera_shake.off")
                        : Component.translatable("propwash.video.unit.percent", value)),
                value -> this.draft.cameraShake = value / 100.0F))
                .setTooltip(Tooltip.create(Component.translatable("propwash.video.config.camera_shake.tooltip")));
        this.place(CycleButton.onOffBuilder(this.draft.reduceFlicker)
                .withTooltip(value -> Tooltip.create(Component.translatable("propwash.video.config.reduce_flicker.tooltip")))
                .create(0, 0, this.columnWidth, ROW_HEIGHT, Component.translatable("propwash.video.config.reduce_flicker"), (button, value) -> {
                    this.draft.reduceFlicker = value;
                    this.host.changed();
                }));
        this.place(CycleButton.<VideoConfig.EffectRenderer>builder(value -> Component.translatable(value.translationKey()), this.draft.renderer)
                .withValues(List.of(VideoConfig.EffectRenderer.values()))
                .withTooltip(value -> Tooltip.create(Component.translatable("propwash.video.config.renderer.tooltip")))
                .create(0, 0, this.columnWidth, ROW_HEIGHT, Component.translatable("propwash.video.config.renderer"), (button, value) -> {
                    this.draft.renderer = value;
                    this.host.changed();
                }));
        this.place(CycleButton.onOffBuilder(this.draft.ledLighting)
                .withTooltip(value -> Tooltip.create(Component.translatable("propwash.video.config.led_lighting.tooltip")))
                .create(0, 0, this.columnWidth, ROW_HEIGHT, Component.translatable("propwash.video.config.led_lighting"), (button, value) -> {
                    this.draft.ledLighting = value;
                    this.host.changed();
                }));
        if (this.retryShown) {
            this.place(Button.builder(Component.translatable("propwash.video.config.retry"), button -> {
                        VideoPass.INSTANCE.retry();
                        HeadlightPass.INSTANCE.retryAll();
                        this.host.rebuild();
                    })
                    .size(this.columnWidth, ROW_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("propwash.video.config.retry.tooltip")))
                    .build());
        }

        if (VideoTabRules.showsCreative(this.profile)) {
            this.place(CycleButton.<Boolean>builder(value -> Component.translatable(value ? "propwash.video.config.latency_source.module"
                            : "propwash.video.config.latency_source.own"), this.draft.creativeLatencyFromModule)
                    .withValues(List.of(Boolean.TRUE, Boolean.FALSE))
                    .withTooltip(value -> Tooltip.create(Component.translatable("propwash.video.config.latency_source.tooltip")))
                    .create(0, 0, this.columnWidth, ROW_HEIGHT, Component.translatable("propwash.video.config.latency_source"), (button, value) -> {
                        this.draft.creativeLatencyFromModule = value;
                        if (!value) {
                            this.draft.creativeLatencyMs = VideoConfig.ownLatencyStart(this.draft.creativeLatencyMs, moduleLatency(this.camera));
                        }
                        this.host.rebuild();
                        this.host.changed();
                    }));
            StepSlider latency = this.place(this.intSlider(this.draft.creativeLatencyFromModule ? moduleLatency(this.camera) : this.draft.creativeLatencyMs,
                    0, VideoConfig.MAX_CREATIVE_LATENCY_MS, VideoConfig.CREATIVE_LATENCY_STEP_MS,
                    value -> Component.translatable("propwash.video.config.creative.latency", Component.translatable("propwash.video.unit.ms", value)),
                    value -> this.draft.creativeLatencyMs = value));
            latency.setTooltip(Tooltip.create(Component.translatable("propwash.video.config.creative.latency.tooltip")));
            latency.active = !this.draft.creativeLatencyFromModule;
            this.place(this.intSlider(this.draft.creativeRangeBlocks, 0, VideoConfig.MAX_CREATIVE_RANGE_BLOCKS, 25,
                    value -> Component.translatable("propwash.video.config.creative.range", value == 0
                            ? Component.translatable("propwash.video.config.creative.range.unlimited")
                            : Component.translatable("propwash.video.unit.blocks", value)),
                    value -> this.draft.creativeRangeBlocks = value))
                    .setTooltip(Tooltip.create(Component.translatable("propwash.video.config.creative.range.tooltip")));
            this.place(this.intSlider(Math.round(this.draft.creativeNoise * 100.0F), 0, 100, 5,
                    value -> Component.translatable("propwash.video.config.creative.noise", Component.translatable("propwash.video.unit.percent", value)),
                    value -> this.draft.creativeNoise = value / 100.0F))
                    .setTooltip(Tooltip.create(Component.translatable("propwash.video.config.creative.noise.tooltip")));
            this.place(CycleButton.onOffBuilder(this.draft.creativeObstruction)
                    .withTooltip(value -> Tooltip.create(Component.translatable("propwash.video.config.creative.obstruction.tooltip")))
                    .create(0, 0, this.columnWidth, ROW_HEIGHT, Component.translatable("propwash.video.config.creative.obstruction"), (button, value) -> {
                        this.draft.creativeObstruction = value;
                        this.host.changed();
                    }));
        }
        int usedRows = (this.row + this.columns - 1) / this.columns;
        this.cameraY = this.y + Metrics.SECTION_HEIGHT + usedRows * ROW_STEP;
        int total = this.columns * this.columnWidth + (this.columns - 1) * COLUMN_GAP;
        this.formatY = this.cameraY + ThemeText.wrappedHeight(this.host.font(), this.cameraLine(), total) + 2;
        Component format = this.formatLine(FisheyeLens.DEFAULT_PERCENT);
        Component flat = this.formatLine(0);
        int formatHeight = format == null ? 0 : Math.max(ThemeText.wrappedHeight(this.host.font(), format, total),
                flat == null ? 0 : ThemeText.wrappedHeight(this.host.font(), flat, total)) + 2;
        this.statusY = this.formatY + formatHeight + 2 * Metrics.GAP - 2;
    }

    private @Nullable VideoSystem tabSystem() {
        if (this.profile != VideoSystem.CREATIVE) {
            return this.profile;
        }
        return this.camera == null ? null : this.camera.system;
    }

    private ImageFormat shownFormat() {
        return this.draft.imageFormat.resolve(this.tabSystem());
    }

    private Component formatName(ImageFormat format) {
        if (format == ImageFormat.AUTO) {
            return Component.translatable("propwash.video.format.auto_resolved",
                    Component.translatable(format.resolve(this.tabSystem()).translationKey()));
        }
        return Component.translatable(format.translationKey());
    }

    static Component lensLabel(int percent) {
        Component value = percent <= 0 ? Component.translatable("propwash.video.config.lens.flat")
                : percent == FisheyeLens.DEFAULT_PERCENT
                ? Component.translatable("propwash.video.config.lens.default", Component.translatable("propwash.video.unit.percent", percent))
                : Component.translatable("propwash.video.unit.percent", percent);
        return Component.translatable("propwash.video.config.lens", value);
    }

    private @Nullable Component formatLine() {
        return this.formatLine(this.draft.lensStrength);
    }

    private @Nullable Component formatLine(int lensStrength) {
        double horizontal;
        if (this.profile == VideoSystem.CREATIVE && !this.draft.creativeFovFromModule) {
            horizontal = this.draft.fovCreative;
        } else if (this.camera != null) {
            horizontal = FovMath.cameraHorizontalDegrees(this.camera);
        } else {
            return null;
        }
        boolean boxed = this.shownFormat().boxed();
        double aspect = boxed ? ImageFormat.FOUR_THREE_ASPECT : FpvRig.ASPECT;
        if (lensStrength > 0) {
            double strength = lensStrength / 100.0;
            return Component.translatable(boxed ? "propwash.video.config.format_line.fisheye.boxed" : "propwash.video.config.format_line.fisheye.wide",
                    Math.round(horizontal), Math.round(FisheyeLens.verticalDeg(horizontal, aspect, strength)),
                    Math.round(FisheyeLens.diagonalDeg(horizontal, aspect, strength)));
        }
        long vertical = Math.round(FovMath.verticalFromHorizontal(horizontal, aspect));
        return Component.translatable(boxed ? "propwash.video.config.format_line.boxed" : "propwash.video.config.format_line.wide",
                Math.round(horizontal), vertical);
    }

    private Component cameraLine() {
        if (this.profile == VideoSystem.CREATIVE && !this.draft.creativeFovFromModule) {
            return Component.translatable("propwash.video.config.fov_line.own", this.draft.fovCreative);
        }
        if (this.camera == null) {
            return Component.translatable("propwash.video.config.fov_line.none");
        }
        Component name = Component.translatable(this.camera.translationKey());
        if (!FovMath.fromModule(this.camera)) {
            return Component.translatable("propwash.video.config.fov_line.fallback", name, moduleFov(this.camera));
        }
        return Component.translatable("propwash.video.config.fov_line.module", name, moduleFov(this.camera));
    }

    private static @Nullable VideoType contextCamera() {
        VideoType live = VideoState.INSTANCE.camera();
        if (live != null) {
            return live;
        }
        DroneContext context = DroneContext.resolve(Minecraft.getInstance());
        return context.hasDrone() ? context.build().video() : null;
    }

    private static int moduleLatency(@Nullable VideoType camera) {
        return camera == null ? 0 : camera.latencyMs;
    }

    private static int moduleFov(@Nullable VideoType camera) {
        return (int) Math.round(FovMath.cameraHorizontalDegrees(camera));
    }

    private static Component fovValue(@Nullable VideoType camera) {
        if (camera == null) {
            return Component.literal("—");
        }
        return Component.translatable("propwash.video.unit.degrees", moduleFov(camera));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.host != null && (shaderFailed() != this.retryShown || contextCamera() != this.camera || wornGoggles() != this.profile)) {
            this.host.rebuild();
        }
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = this.host.font();
        int total = this.columns * this.columnWidth + (this.columns - 1) * COLUMN_GAP;
        if (this.profile == null) {
            ThemeDraw.sectionTitle(graphics, font, Component.translatable("propwash.video.config.section"), this.left, this.y, total);
            ThemeText.wrapped(graphics, font, Component.translatable("propwash.video.config.no_goggles"), this.left,
                    this.y + Metrics.SECTION_HEIGHT + Metrics.GAP, total, Palette.TEXT_MUTED);
            return;
        }
        ThemeDraw.sectionTitle(graphics, font, Component.translatable("propwash.video.config.section.worn", systemName(this.profile)), this.left,
                this.y, total);
        ThemeText.wrapped(graphics, font, this.cameraLine(), this.left, this.cameraY + Metrics.GAP, total, Palette.TEXT_MUTED);
        Component format = this.formatLine();
        if (format != null) {
            ThemeText.wrapped(graphics, font, format, this.left, this.formatY + Metrics.GAP, total, Palette.TEXT_MUTED);
        }
        int y = this.statusY;
        ThemeDraw.sectionTitle(graphics, font, Component.translatable("propwash.video.config.preview"), this.left, y, total);
        y += Metrics.SECTION_HEIGHT;
        y = BreakupPreview.extract(graphics, font, this.profile, this.draft.effectStrength, this.shownFormat(), this.left, y, total) + Metrics.GAP;
        Component warning = fallbackText();
        if (warning != null) {
            y += ThemeText.wrapped(graphics, font, warning, this.left, y, total, Palette.WARNING) + 2;
        }
        Component headlight = headlightText();
        if (headlight != null) {
            y += ThemeText.wrapped(graphics, font, headlight, this.left, y, total, Palette.WARNING) + 2;
        }
        Component live = liveLine();
        if (live != null) {
            y += ThemeText.wrapped(graphics, font, live, this.left, y, total, Palette.TEXT_MUTED);
        }
        this.reportBottom(y);
    }

    @Override
    public void resetDefaults() {
        if (!VideoTabRules.showsSettings(this.profile)) {
            return;
        }
        VideoConfig defaults = VideoTabRules.defaults(this.draft, this.profile);
        this.draft = defaults;
        FpvImage.draft(defaults.imageFormat);
        this.host.rebuild();
        this.host.changed();
    }

    @Override
    public void save() {
        VideoSettings.CONFIG.set(VideoTabRules.saved(VideoSettings.get(), this.draft, this.profile));
        FpvImage.draft(null);
    }

    @Override
    public void discard() {
        FpvImage.draft(null);
    }

    private <T extends AbstractWidget> T place(T widget) {
        int column = this.row % this.columns;
        int rowInColumn = this.row / this.columns;
        widget.setX(this.left + column * (this.columnWidth + COLUMN_GAP));
        widget.setY(this.y + Metrics.SECTION_HEIGHT + rowInColumn * ROW_STEP);
        widget.setWidth(this.columnWidth);
        this.row++;
        return this.host.addWidget(widget);
    }

    private static Component systemName(VideoSystem system) {
        return Component.translatable("propwash.video.system." + system.name().toLowerCase(Locale.ROOT));
    }

    private static @Nullable VideoSystem wornGoggles() {
        return VideoState.INSTANCE.goggles(Minecraft.getInstance().player);
    }

    private static @Nullable Component fallbackText() {
        VideoPass pass = VideoPass.INSTANCE;
        VideoPass.Failure failure = pass.failure();
        if (failure == null) {
            failure = pass.overlayReason(Util.getNanos());
        }
        if (failure == null) {
            return null;
        }
        return Component.translatable("propwash.video.status.fallback", Component.translatable(failure.translationKey()));
    }

    private static boolean shaderFailed() {
        return VideoPass.INSTANCE.failure() != null || VideoPass.INSTANCE.lensFailure() != null || HeadlightPass.INSTANCE.failure() != null;
    }

    private static @Nullable Component headlightText() {
        VideoPass.Failure failure = HeadlightPass.INSTANCE.failure();
        if (failure == null) {
            return null;
        }
        return Component.translatable("propwash.video.status.headlight_fallback", Component.translatable(failure.translationKey()));
    }

    private static @Nullable Component liveLine() {
        VideoState state = VideoState.INSTANCE;
        if (!state.active()) {
            return null;
        }
        int signal = Math.round(state.signalQuality() * 100.0F);
        long latencyMs = Math.round(state.latencyNanos() / 1_000_000.0);
        long distance = Math.round(state.distanceBlocks());
        return Component.translatable("propwash.video.status.live", signal, latencyMs, distance);
    }

    private StepSlider intSlider(int initial, int min, int max, int step, IntFunction<Component> label, IntConsumer setter) {
        return new StepSlider(0, 0, this.columnWidth, ROW_HEIGHT, min, max, Math.max(1, step), initial, v -> label.apply((int) Math.round(v)), v -> {
            setter.accept((int) Math.round(v));
            this.host.changed();
        });
    }
}
