package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.dust.DustConfig;
import de.lardtom.propwash.drone.client.trail.TrailClient;
import de.lardtom.propwash.drone.client.trail.TrailConfig;
import de.lardtom.propwash.drone.dust.DustModel;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class DroneRenderConfigTab extends ConfigTab {
    private static final int ROW = Metrics.ROW_HEIGHT;
    private static final int PREVIEW_MIN_WIDTH = 90;

    private DroneRenderConfig draft = DroneRenderConfig.get().copy();
    private DustConfig dustDraft = DustConfig.get().copy();
    private TrailConfig trailDraft = TrailConfig.get().copy();
    private boolean trailRule;
    private int trailSectionY;
    private int trailHintY = -1;
    private @Nullable CycleButton<Boolean> trailToggle;
    private StepSlider dustIntensity;
    private int dustSectionY;
    private int fpvSectionY;
    private int sectionWidth;
    private int previewX0;
    private int previewY0;
    private int previewX1;
    private int previewY1;

    @Override
    public String id() {
        return "drone_render";
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.drone_render.tab");
    }

    @Override
    public int order() {
        return 810;
    }

    @Override
    protected void build() {
        DroneRenderConfig config = this.draft;
        boolean withPreview = this.width >= 260;
        int controlWidth = withPreview ? Math.min(Metrics.COLUMN_MAX - 30, this.width - PREVIEW_MIN_WIDTH - 2 * Metrics.GAP)
                : Math.min(Metrics.COLUMN_MAX, this.width);
        int cx = this.x;
        int cy = this.y + Metrics.SECTION_HEIGHT;
        this.sectionWidth = controlWidth;
        this.host.addWidget(slider(cx, cy, controlWidth, "propwash.drone_render.visual_scale",
                DroneRenderConfig.MIN_VISUAL_SCALE, DroneRenderConfig.MAX_VISUAL_SCALE, 0.05, config.visualScale,
                v -> Numbers.fixed(v, 2).getString() + "×", v -> {
            config.visualScale = (float) v;
            this.host.changed();
        }));
        cy += ROW;
        this.host.addWidget(slider(cx, cy, controlWidth, "propwash.drone_render.prop_blur", 0.0, 1.0, 0.01, config.propBlurThreshold,
                v -> Component.translatable("propwash.drone_render.percent", Math.round(v * 100.0)).getString(), v -> {
            config.propBlurThreshold = (float) v;
            this.host.changed();
        }).percent());
        cy += ROW;
        this.host.addWidget(slider(cx, cy, controlWidth, "propwash.drone_render.render_distance", 16.0, 512.0, 16.0,
                config.renderDistance, v -> Math.round(v) + " m", v -> {
            config.renderDistance = (int) Math.round(v);
            this.host.changed();
        }));
        cy += ROW;
        this.host.addWidget(CycleButton.onOffBuilder(config.armLeds).create(cx, cy, controlWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash.drone_render.arm_leds"), (button, value) -> {
                    config.armLeds = value;
                    this.host.changed();
                }));
        cy += ROW;
        CycleButton<Boolean> shadowToggle = CycleButton.onOffBuilder(config.shadow).create(cx, cy, controlWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash.drone_render.shadow"), (button, value) -> {
                    config.shadow = value;
                    this.host.changed();
                });
        shadowToggle.setTooltip(Tooltip.create(Component.translatable("propwash.drone_render.shadow.tip")));
        this.host.addWidget(shadowToggle);
        cy += ROW + Metrics.SECTION_GAP;
        this.dustSectionY = cy;
        cy += Metrics.SECTION_HEIGHT;
        DustConfig dust = this.dustDraft;
        CycleButton<Boolean> dustToggle = CycleButton.onOffBuilder(dust.enabled).create(cx, cy, controlWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash.dust.enabled"), (button, value) -> {
                    dust.enabled = value;
                    if (this.dustIntensity != null) {
                        this.dustIntensity.active = value;
                    }
                    this.host.changed();
                });
        dustToggle.setTooltip(Tooltip.create(Component.translatable("propwash.dust.enabled.tip")));
        this.host.addWidget(dustToggle);
        cy += ROW;
        this.dustIntensity = this.host.addWidget(slider(cx, cy, controlWidth, "propwash.dust.intensity",
                DustModel.MIN_INTENSITY, DustModel.MAX_INTENSITY, 0.05, dust.intensity,
                v -> Component.translatable("propwash.dust.percent", Math.round(v * 100.0)).getString(), v -> {
            dust.intensity = (float) v;
            this.host.changed();
        }).percent());
        this.dustIntensity.setTooltip(Tooltip.create(Component.translatable("propwash.dust.intensity.tip")));
        this.dustIntensity.active = dust.enabled;
        cy += ROW + Metrics.SECTION_GAP;
        this.fpvSectionY = cy;
        cy += Metrics.SECTION_HEIGHT;
        CycleButton<Boolean> fpvProps = CycleButton.onOffBuilder(config.fpvProps).create(cx, cy, controlWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash.drone_render.fpv_props"), (button, value) -> {
                    config.fpvProps = value;
                    this.host.changed();
                });
        fpvProps.setTooltip(Tooltip.create(Component.translatable("propwash.drone_render.fpv_props.tip")));
        this.host.addWidget(fpvProps);
        cy += ROW;
        CycleButton<Boolean> fpvDucts = CycleButton.onOffBuilder(config.fpvDucts).create(cx, cy, controlWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash.drone_render.fpv_ducts"), (button, value) -> {
                    config.fpvDucts = value;
                    this.host.changed();
                });
        fpvDucts.setTooltip(Tooltip.create(Component.translatable("propwash.drone_render.fpv_ducts.tip")));
        this.host.addWidget(fpvDucts);
        cy += ROW + Metrics.SECTION_GAP;
        this.trailSectionY = cy;
        cy += Metrics.SECTION_HEIGHT;
        TrailConfig trails = this.trailDraft;
        this.trailRule = TrailClient.ruleOn();
        CycleButton<Boolean> trailToggle = CycleButton.onOffBuilder(trails.enabled).create(cx, cy, controlWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash.trails.enabled"), (button, value) -> {
                    trails.enabled = value;
                    this.host.changed();
                });
        trailToggle.setTooltip(Tooltip.create(Component.translatable(this.trailRule ? "propwash.trails.enabled.tip" : "propwash.trails.rule_off.tip")));
        trailToggle.active = this.trailRule;
        this.trailToggle = this.host.addWidget(trailToggle);
        this.trailHintY = this.trailRule ? -1 : cy + ROW;
        if (withPreview) {
            this.previewX0 = this.x + controlWidth + 2 * Metrics.GAP;
            this.previewY0 = this.y + Metrics.SECTION_HEIGHT;
            this.previewX1 = this.x + this.width;
            this.previewY1 = this.previewY0 + Math.min(this.height - Metrics.SECTION_HEIGHT, Math.max(ROW * 5, (this.previewX1 - this.previewX0) * 3 / 4));
        } else {
            this.previewX0 = this.previewX1 = 0;
        }
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ThemeDraw.sectionTitle(graphics, this.host.font(), Component.translatable("propwash.drone_render.section"), this.x, this.y, this.sectionWidth);
        ThemeDraw.sectionTitle(graphics, this.host.font(), Component.translatable("propwash.dust.section"), this.x, this.dustSectionY, this.sectionWidth);
        ThemeDraw.sectionTitle(graphics, this.host.font(), Component.translatable("propwash.drone_render.section.fpv"), this.x, this.fpvSectionY,
                this.sectionWidth);
        ThemeDraw.sectionTitle(graphics, this.host.font(), Component.translatable("propwash.trails.section"), this.x, this.trailSectionY,
                this.sectionWidth);
        if (this.trailHintY >= 0) {
            int hint = ThemeText.wrapped(graphics, this.host.font(), Component.translatable("propwash.trails.rule_off"), this.x, this.trailHintY + 3,
                    this.sectionWidth, Palette.TEXT_MUTED);
            this.reportBottom(this.trailHintY + 3 + hint);
        }
        if (this.previewX1 - this.previewX0 < 16 || this.previewY1 - this.previewY0 < 16) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, this.host.contentShift());
        ThemeDraw.sectionTitle(graphics, this.host.font(), Component.translatable("propwash.drone_render.section.preview"), this.previewX0, this.y,
                this.previewX1 - this.previewX0);
        ThemeDraw.previewStage(graphics, this.previewX0, this.previewY0, this.previewX1, this.previewY1);
        float yaw = (Util.getMillis() % 12000L) / 12000.0F * 360.0F;
        DronePreview.render(graphics, currentBuild(), this.previewX0, this.previewY0, this.previewX1, this.previewY1, yaw);
        graphics.pose().popMatrix();
    }

    @Override
    public void tick() {
        if (TrailClient.ruleOn() != this.trailRule) {
            this.host.rebuild();
        }
    }

    @Override
    public void resetDefaults() {
        this.draft = DroneRenderConfig.defaults();
        this.dustDraft = new DustConfig();
        this.trailDraft = new TrailConfig();
        this.host.changed();
        this.host.rebuild();
    }

    @Override
    public void save() {
        DroneRenderConfig.CONFIG.set(this.draft.copy());
        DustConfig.CONFIG.set(this.dustDraft.copy());
        TrailConfig.CONFIG.set(this.trailDraft.copy());
    }

    DroneRenderConfig draft() {
        return this.draft;
    }

    public DustConfig dustDraft() {
        return this.dustDraft;
    }

    public TrailConfig trailDraft() {
        return this.trailDraft;
    }

    public @Nullable CycleButton<Boolean> trailToggle() {
        return this.trailToggle;
    }

    public boolean trailRuleHintShown() {
        return this.trailHintY >= 0;
    }

    static DroneBuild currentBuild() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone != null) {
            return drone.build();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            for (ItemStack stack : new ItemStack[]{minecraft.player.getMainHandItem(), minecraft.player.getOffhandItem()}) {
                DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
                if (build != null) {
                    return build;
                }
            }
        }
        return DroneBuild.FREESTYLE;
    }

    private static StepSlider slider(int x, int y, int width, String key, double min, double max, double step, double initial,
                                     DoubleFunction<String> format, DoubleConsumer onChange) {
        return new StepSlider(x, y, width, Metrics.CONTROL_HEIGHT, min, max, step, initial, v -> Component.translatable(key, format.apply(v)), onChange);
    }
}
