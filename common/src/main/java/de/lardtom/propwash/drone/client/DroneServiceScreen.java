package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.build.BatteryFit;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.PartTooltips;
import de.lardtom.propwash.content.client.ThemedPanel;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.PropService;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.bind.BindAccess;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.sim.CameraOcclusion;
import de.lardtom.propwash.sim.FpvRig;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.theme.LabeledValue;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.NumberInput;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.client.FpvImage;
import de.lardtom.propwash.video.client.FpvLens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleConsumer;

public class DroneServiceScreen extends AbstractContainerScreen<DroneServiceMenu> implements ThemedScreen {
    public static final Identifier TEXTURE = Constants.id("textures/gui/drone_service.png");
    public static final int WIDTH = DroneServiceMenu.WIDTH;
    public static final int HEIGHT = DroneServiceMenu.HEIGHT;
    public static final int PROPS_X0 = 7;
    public static final int PROPS_Y0 = 18;
    public static final int PROPS_X1 = 121;
    public static final int PROPS_Y1 = 110;
    public static final int PREVIEW_X0 = 125;
    public static final int PREVIEW_Y0 = 18;
    public static final int PREVIEW_X1 = 229;
    public static final int PREVIEW_Y1 = 64;
    public static final int INFO_X0 = 154;
    public static final int INFO_Y0 = 68;
    public static final int INFO_X1 = 229;
    public static final int INFO_Y1 = 110;
    public static final int HINT_Y = 112;
    public static final int BODY_X0 = 52;
    public static final int BODY_Y0 = 44;
    public static final int BODY_X1 = 76;
    public static final int BODY_Y1 = 88;
    public static final int FRONT_Y = 21;
    public static final int CAMERA_X0 = 7;
    public static final int CAMERA_Y0 = 124;
    public static final int CAMERA_X1 = 229;
    public static final int CAMERA_Y1 = 150;
    public static final int GAUGE_X0 = 10;
    public static final int GAUGE_Y0 = 126;
    public static final int GAUGE_X1 = 34;
    public static final int GAUGE_Y1 = 148;
    public static final int CAMERA_TEXT_X = 38;
    public static final int CAMERA_LABEL_Y = 128;
    public static final int CAMERA_ROW_Y = 137;
    public static final int CAMERA_BUTTON = 12;
    public static final int CAMERA_RESET_WIDTH = 44;
    private static final long LOCAL_HOLD_MILLIS = 700L;
    private static final long REJECTED_MILLIS = 2500L;
    private static final int OCCLUSION_ROWS = 270;
    private static final String KEY = "propwash.drone.service.";
    private static final String[] POSITIONS = {"front_left", "front_right", "rear_left", "rear_right"};
    private static final int INFO_PAD = 5;
    private static final int INFO_TEXT_WIDTH = INFO_X1 - INFO_X0 - 2 * INFO_PAD;
    private static final int HOTBAR_Y = DroneServiceMenu.INVENTORY_Y + 58;
    private static final int BAR_OFFSET = 19;
    private static final int PERCENT_OFFSET = 23;
    private static final int HEADER_HEIGHT = 14;
    public static final int RX_X0 = 8;
    public static final int RX_Y0 = 167;
    public static final int RX_SIZE = 26;
    private static final long RX_ARM_MILLIS = 3000L;

    private float yaw = 210.0F;
    private long lastFrameMillis;
    private @Nullable StepSlider uptiltSlider;
    private float pendingUptilt = Float.NaN;
    private float sentUptilt = Float.NaN;
    private long localChangeMillis;
    private @Nullable DroneBuild occlusionBuild;
    private float occlusionTilt = Float.NaN;
    private double occlusionPercent;
    private double occlusionAspect = FpvRig.ASPECT;
    private int occlusionLens = -1;
    private @Nullable FlatButton bindButton;
    private @Nullable FlatButton bindConfirm;
    private long bindArmedUntil;
    private @Nullable BindAccess shownAccess;
    private boolean shownArmed;

    public DroneServiceScreen(DroneServiceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.inventoryLabelX = DroneServiceMenu.INVENTORY_X;
        this.inventoryLabelY = DroneServiceMenu.INVENTORY_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        ThemedPanel.forget();
        this.lastFrameMillis = Util.getMillis();
        this.uptiltSlider = null;
        QuadcopterEntity drone = this.menu.drone();
        if (drone == null) {
            return;
        }
        CameraMount.Range range = CameraMount.range(drone.build());
        int x = this.leftPos + CAMERA_TEXT_X;
        int y = this.topPos + CAMERA_ROW_Y;
        int right = this.leftPos + CAMERA_X1 - 3;
        Component resetLabel = Component.translatable(KEY + "camera.default");
        int resetWidth = Math.max(CAMERA_RESET_WIDTH, this.font.width(resetLabel) + 10);
        int sliderX = x + CAMERA_BUTTON + 2;
        int sliderWidth = right - resetWidth - 3 - CAMERA_BUTTON - 2 - sliderX;
        float shown = this.shownUptilt(drone);
        StepSlider slider = new UptiltSlider(sliderX, y, sliderWidth, CAMERA_BUTTON - 1, range, shown, this::changeUptilt);
        slider.setTooltip(ThemeText.tip(this.cameraTooltip(range)));
        this.uptiltSlider = this.addRenderableWidget(slider);
        FlatButton minus = new FlatButton(x, y, CAMERA_BUTTON, CAMERA_BUTTON - 1, Component.literal("−"), FlatButton.Style.NORMAL,
                () -> this.stepUptilt(-1.0F));
        minus.setTooltip(Tooltip.create(Component.translatable(KEY + "camera.minus")));
        this.addRenderableWidget(minus);
        FlatButton plus = new FlatButton(sliderX + sliderWidth + 2, y, CAMERA_BUTTON, CAMERA_BUTTON - 1, Component.literal("+"),
                FlatButton.Style.NORMAL, () -> this.stepUptilt(1.0F));
        plus.setTooltip(Tooltip.create(Component.translatable(KEY + "camera.plus")));
        this.addRenderableWidget(plus);
        FlatButton reset = new FlatButton(right - resetWidth, y, resetWidth, CAMERA_BUTTON - 1, resetLabel, FlatButton.Style.NORMAL,
                () -> this.changeUptilt(range.defaultDeg()));
        reset.setTooltip(Tooltip.create(Component.translatable(KEY + "camera.reset", Numbers.of(range.defaultDeg(), 0))));
        this.addRenderableWidget(reset);
        this.bindArmedUntil = 0L;
        this.shownAccess = null;
        this.bindButton = this.addRenderableWidget(new FlatButton(this.leftPos + RX_X0, this.topPos + RX_Y0, RX_SIZE, RX_SIZE, Component.empty(),
                FlatButton.Style.NORMAL, this::armBindReset));
        this.bindConfirm = this.addRenderableWidget(new FlatButton(this.leftPos + RX_X0, this.topPos + RX_Y0, RX_SIZE, RX_SIZE, Component.empty(),
                FlatButton.Style.DANGER, this::confirmBindReset));
        this.updateBindButtons();
        if (AccessoryBehaviors.INSTANCE.hasInventory(drone.build())) {
            Component label = Component.translatable("propwash.accessory.service.button");
            int width = this.font.width(label) + 12;
            FlatButton accessories = new FlatButton(this.leftPos + WIDTH - 3 - width, this.topPos + 3, width, HEADER_HEIGHT - 2, label,
                    FlatButton.Style.NORMAL, this::openAccessories);
            accessories.setTooltip(Tooltip.create(Component.translatable("propwash.accessory.service.tooltip")));
            this.addRenderableWidget(accessories);
        }
    }

    private void openAccessories() {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, DroneServiceMenu.ACCESSORY_BUTTON);
        }
    }

    private Component cameraTooltip(CameraMount.Range range) {
        QuadcopterEntity drone = this.menu.drone();
        double fov = FovMath.cameraHorizontalDegrees(drone == null ? null : drone.build().video());
        return Component.translatable(KEY + "camera.tooltip", Numbers.of(range.minDeg(), 0), Numbers.of(range.maxDeg(), 0),
                Numbers.of(range.defaultDeg(), 0), Numbers.of(fov, 0));
    }

    public float shownUptilt(QuadcopterEntity drone) {
        return Float.isFinite(this.pendingUptilt) ? this.pendingUptilt : CameraMount.uptilt(drone.build(), drone.tune().values());
    }

    public @Nullable StepSlider uptiltSlider() {
        return this.uptiltSlider;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && NumberInput.cancelEditing(this.getFocused())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        NumberInput.releaseOnClickAway(this, event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    private void stepUptilt(float delta) {
        QuadcopterEntity drone = this.menu.drone();
        if (drone == null) {
            return;
        }
        float step = this.minecraft != null && this.minecraft.hasShiftDown() ? 5.0F * delta : delta;
        this.changeUptilt(this.shownUptilt(drone) + step);
    }

    public void changeUptilt(double degrees) {
        QuadcopterEntity drone = this.menu.drone();
        if (drone == null) {
            return;
        }
        float value = CameraMount.range(drone.build()).clamp(Math.round((float) degrees));
        this.pendingUptilt = value;
        this.localChangeMillis = Util.getMillis();
        StepSlider slider = this.uptiltSlider;
        if (slider != null && slider.current() != value) {
            slider.setCurrent(value);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.updateBindButtons();
        QuadcopterEntity drone = this.menu.drone();
        if (drone == null || this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }
        float synced = CameraMount.uptilt(drone.build(), drone.tune().values());
        if (Float.isFinite(this.pendingUptilt) && this.pendingUptilt != this.sentUptilt) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, DroneServiceMenu.uptiltButton(this.pendingUptilt));
            this.sentUptilt = this.pendingUptilt;
        }
        long sinceChange = Util.getMillis() - this.localChangeMillis;
        boolean holding = sinceChange < LOCAL_HOLD_MILLIS;
        if (Float.isFinite(this.pendingUptilt) && !holding && (synced == this.pendingUptilt || sinceChange > REJECTED_MILLIS)) {
            this.pendingUptilt = Float.NaN;
            this.sentUptilt = Float.NaN;
        }
        StepSlider slider = this.uptiltSlider;
        if (!Float.isFinite(this.pendingUptilt) && !holding && slider != null && slider.current() != synced) {
            slider.setCurrent(synced);
        }
    }

    private double propsInView(DroneBuild build, float tilt) {
        double aspect = FpvImage.formatFor(build.video().system).boxed() ? ImageFormat.FOUR_THREE_ASPECT : FpvRig.ASPECT;
        int lens = FpvLens.INSTANCE.strengthPercent();
        if (build != this.occlusionBuild || tilt != this.occlusionTilt || aspect != this.occlusionAspect || lens != this.occlusionLens) {
            this.occlusionBuild = build;
            this.occlusionTilt = tilt;
            this.occlusionAspect = aspect;
            this.occlusionLens = lens;
            FpvRig rig = FpvRig.of(build);
            this.occlusionPercent = lens > 0
                    ? CameraOcclusion.fisheyePercent(rig, tilt, rig.horizontalFovDeg(), aspect, CameraOcclusion.FISHEYE_ROWS, lens / 100.0)
                    : CameraOcclusion.percent(rig, tilt, rig.horizontalFovDeg(), aspect, OCCLUSION_ROWS);
        }
        return this.occlusionPercent;
    }

    public static Component positionName(int position) {
        return Component.translatable(KEY + "position." + POSITIONS[position]);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        if (!ThemedPanel.blit(graphics, TEXTURE, x, y, WIDTH, HEIGHT)) {
            this.fallbackBackground(graphics, x, y);
        }
        ThemeDraw.brandedHeader(graphics, this.font, x + 2, y + 2, x + WIDTH - 2, HEADER_HEIGHT, this.title, x + WIDTH - 8);
        ThemeDraw.previewStage(graphics, x + PREVIEW_X0, y + PREVIEW_Y0, x + PREVIEW_X1, y + PREVIEW_Y1);
        QuadcopterEntity drone = this.menu.drone();
        if (drone != null) {
            this.frameIcon(graphics, x, y, drone.build());
            this.advanceYaw();
            float tilt = this.shownUptilt(drone);
            DronePreview.render(graphics, drone.build(), drone.wear(), drone.paint(), tilt, x + PREVIEW_X0 + 2, y + PREVIEW_Y0 + 2,
                    x + PREVIEW_X1 - 2, y + PREVIEW_Y1 - 2, this.yaw);
            this.propBars(graphics, x, y, drone);
            this.gauge(graphics, x, y, CameraMount.range(drone.build()), tilt);
        }
        ItemStack battery = this.menu.getSlot(DroneServiceMenu.BATTERY_SLOT).getItem();
        if (Batteries.isBattery(battery)) {
            float charge = Batteries.charge(battery);
            int bx = x + INFO_X0 + INFO_PAD;
            int by = y + INFO_Y0 + INFO_PAD + 22;
            ThemeDraw.valueBar(graphics, bx, by, bx + INFO_TEXT_WIDTH, by + 3, charge, 0xFF000000 | Batteries.barColor(charge));
        }
    }

    private void gauge(GuiGraphicsExtractor graphics, int x, int y, CameraMount.Range range, float tilt) {
        int px = x + GAUGE_X0 + 4;
        int py = y + GAUGE_Y1 - 4;
        int arc = GAUGE_X1 - GAUGE_X0 - 6;
        for (float a = range.minDeg(); a <= range.maxDeg(); a += 2.0F) {
            dot(graphics, px, py, a, arc, Palette.ACCENT_DIM);
        }
        dot(graphics, px, py, range.minDeg(), arc, ThemedPanel.MUTED);
        dot(graphics, px, py, range.maxDeg(), arc, ThemedPanel.MUTED);
        dot(graphics, px, py, range.defaultDeg(), arc + 2, ThemedPanel.TEXT);
        for (int i = 2; i <= arc - 4; i++) {
            dot(graphics, px, py, tilt, i, Palette.ACCENT);
        }
        graphics.fill(px - 2, py - 1, px + 2, py + 2, ThemedPanel.MUTED);
    }

    private static void dot(GuiGraphicsExtractor graphics, int px, int py, float degrees, int radius, int color) {
        double r = Math.toRadians(degrees);
        int lx = px + (int) Math.round(Math.cos(r) * radius);
        int ly = py - (int) Math.round(Math.sin(r) * radius);
        graphics.fill(lx, ly, lx + 1, ly + 1, color);
    }

    private void frameIcon(GuiGraphicsExtractor graphics, int x, int y, DroneBuild build) {
        int cx = x + (BODY_X0 + BODY_X1) / 2;
        int cy = y + (BODY_Y0 + BODY_Y1) / 2;
        graphics.item(ContentItems.stack(build.frame()), cx - 8, cy - 12);
        String label = Component.translatable(KEY + "frame_short").getString();
        ThemeText.small(graphics, this.font, label, cx - ThemeText.smallWidth(this.font, label) / 2, cy + 7, ThemedPanel.MUTED);
    }

    private void fallbackBackground(GuiGraphicsExtractor graphics, int x, int y) {
        ThemedPanel.panel(graphics, x, y, WIDTH, HEIGHT);
        graphics.fill(x + PROPS_X0, y + PROPS_Y0, x + PROPS_X1, y + PROPS_Y1, ThemedPanel.DARK);
        graphics.fill(x + BODY_X0, y + BODY_Y0, x + BODY_X1, y + BODY_Y1, ThemedPanel.SLOT_EDGE);
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            ThemedPanel.slot(graphics, x + DroneServiceMenu.PROP_X[position], y + DroneServiceMenu.PROP_Y[position]);
        }
        graphics.fill(x + INFO_X0, y + INFO_Y0, x + INFO_X1, y + INFO_Y1, ThemedPanel.DARK);
        graphics.fill(x + INFO_X0, y + INFO_Y0, x + INFO_X1, y + INFO_Y0 + 1, ThemedPanel.ACCENT);
        graphics.fill(x + CAMERA_X0, y + CAMERA_Y0, x + CAMERA_X1, y + CAMERA_Y1, ThemedPanel.DARK);
        graphics.fill(x + CAMERA_X0, y + CAMERA_Y0, x + CAMERA_X1, y + CAMERA_Y0 + 1, ThemedPanel.ACCENT);
        graphics.fill(x + GAUGE_X0, y + GAUGE_Y0, x + GAUGE_X1, y + GAUGE_Y1, ThemedPanel.SLOT_EDGE);
        graphics.outline(x + DroneServiceMenu.SLOT_X - 2, y + DroneServiceMenu.SLOT_Y - 2, 20, 20, ThemedPanel.ACCENT);
        ThemedPanel.slot(graphics, x + DroneServiceMenu.SLOT_X, y + DroneServiceMenu.SLOT_Y);
        ThemedPanel.inventory(graphics, x, y, DroneServiceMenu.INVENTORY_X, DroneServiceMenu.INVENTORY_Y, HOTBAR_Y);
    }

    private void propBars(GuiGraphicsExtractor graphics, int x, int y, QuadcopterEntity drone) {
        PartWear wear = drone.wear();
        boolean offered = this.carriedFit().filter(PropService::allowed).isPresent();
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            int sx = x + DroneServiceMenu.PROP_X[position];
            int sy = y + DroneServiceMenu.PROP_Y[position];
            float condition = wear.prop(position);
            int color = 0xFF000000 | PartCondition.barColor(condition);
            if (offered) {
                graphics.outline(sx - 2, sy - 2, 20, 20, Palette.ACCENT);
            } else if (PartWear.damaged(condition)) {
                graphics.outline(sx - 2, sy - 2, 20, 20, color);
            }
            ThemeDraw.valueBar(graphics, sx - 3, sy + BAR_OFFSET, sx + 19, sy + BAR_OFFSET + 2, PartWear.broken(condition) ? 0.0 : condition, color);
        }
    }

    private void advanceYaw() {
        long now = Util.getMillis();
        long delta = Math.max(0L, Math.min(100L, now - this.lastFrameMillis));
        this.lastFrameMillis = now;
        this.yaw = (this.yaw + delta * 0.03F) % 360.0F;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, ThemedPanel.MUTED, false);
        graphics.centeredText(this.font, Component.translatable(KEY + "front"), (PROPS_X0 + PROPS_X1) / 2, FRONT_Y, ThemedPanel.MUTED);
        QuadcopterEntity drone = this.menu.drone();
        if (drone != null) {
            PartWear wear = drone.wear();
            for (int position = 0; position < PartWear.POSITIONS; position++) {
                float condition = wear.prop(position);
                Component value = PartWear.broken(condition) ? Component.translatable("propwash.help.status.condition.broken")
                        : Component.translatable("propwash.help.status.condition.percent", PartCondition.percent(condition));
                int color = PartWear.broken(condition) ? ThemedPanel.BAD : 0xFF000000 | PartCondition.barColor(condition);
                graphics.text(this.font, value, this.percentX(position, this.font.width(value)), DroneServiceMenu.PROP_Y[position] + PERCENT_OFFSET, color,
                        false);
            }
        }
        this.batteryInfo(graphics);
        this.receiverChip(graphics);
        this.cameraInfo(graphics);
        Hint hint = this.hint();
        ThemeText.fitted(graphics, this.font, hint.text(), this.inventoryLabelX, HINT_Y, INFO_X1 - this.inventoryLabelX, hint.color());
    }

    private int percentX(int position, int textWidth) {
        int slotX = DroneServiceMenu.PROP_X[position];
        int center = slotX + 8 - textWidth / 2;
        int barX0 = slotX - 3;
        int barX1 = slotX + 19;
        if (textWidth <= barX1 - barX0) {
            return center;
        }
        return slotX < (PROPS_X0 + PROPS_X1) / 2 ? barX0 : barX1 - textWidth;
    }

    private void batteryInfo(GuiGraphicsExtractor graphics) {
        ItemStack battery = this.menu.getSlot(DroneServiceMenu.BATTERY_SLOT).getItem();
        Optional<BatteryType> type = Batteries.typeOf(battery);
        int tx = INFO_X0 + INFO_PAD;
        int ty = INFO_Y0 + INFO_PAD;
        if (type.isEmpty()) {
            graphics.text(this.font, Component.translatable(KEY + "battery_none"), tx, ty, ThemedPanel.BAD, false);
            Component hint = Component.translatable(KEY + "battery_none_hint");
            List<FormattedCharSequence> lines = this.font.split(hint, INFO_TEXT_WIDTH);
            int shown = ThemeText.shownLines(hint, lines.size(), 3);
            for (int i = 0; i < shown; i++) {
                graphics.text(this.font, lines.get(i), tx, ty + 10 + i * 9, ThemedPanel.MUTED, false);
            }
            return;
        }
        float charge = Batteries.charge(battery);
        Component name = Component.translatable(type.get().translationKey());
        graphics.text(this.font, this.wholeWords(name.getString(), INFO_TEXT_WIDTH), tx, ty, ThemedPanel.TEXT, false);
        Component info = Component.translatable(KEY + "battery_info", Batteries.percent(charge),
                Numbers.fixed(Batteries.openCircuitVoltage(type.get(), charge), 1));
        graphics.text(this.font, info, tx, ty + 11, ThemedPanel.MUTED, false);
    }

    public int receiverChipCenterX() {
        return this.leftPos + RX_X0 + RX_SIZE / 2;
    }

    public int receiverChipCenterY() {
        return this.topPos + RX_Y0 + RX_SIZE / 2;
    }

    public boolean bindResetArmed() {
        return Util.getMillis() < this.bindArmedUntil && this.menu.bindAccess().resettable();
    }

    public void armBindReset() {
        if (this.menu.bindAccess().resettable()) {
            this.bindArmedUntil = Util.getMillis() + RX_ARM_MILLIS;
            this.updateBindButtons();
        }
    }

    public void confirmBindReset() {
        if (!this.bindResetArmed() || this.minecraft == null || this.minecraft.gameMode == null) {
            return;
        }
        this.bindArmedUntil = 0L;
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, DroneServiceMenu.RESET_BIND_BUTTON);
        this.updateBindButtons();
    }

    private void updateBindButtons() {
        FlatButton button = this.bindButton;
        FlatButton confirm = this.bindConfirm;
        if (button == null || confirm == null) {
            return;
        }
        BindAccess access = this.menu.bindAccess();
        boolean armed = this.bindResetArmed();
        button.visible = !armed;
        confirm.visible = armed;
        button.active = access.resettable();
        if (access == this.shownAccess && armed == this.shownArmed) {
            return;
        }
        this.shownAccess = access;
        this.shownArmed = armed;
        MutableComponent tip = Component.translatable(KEY + "receiver." + access.key()).copy();
        if (access.resettable()) {
            tip.append("\n").append(Component.translatable(KEY + "receiver.click").withStyle(ChatFormatting.GRAY));
        }
        button.setTooltip(ThemeText.tip(tip));
        confirm.setTooltip(ThemeText.tip(Component.translatable(KEY + "receiver.confirm")));
    }

    private void receiverChip(GuiGraphicsExtractor graphics) {
        QuadcopterEntity drone = this.menu.drone();
        if (drone == null) {
            return;
        }
        BindAccess access = this.menu.bindAccess();
        boolean armed = this.bindResetArmed();
        int color = armed ? ThemedPanel.BAD : switch (access) {
            case PILOT -> Palette.SUCCESS;
            case OTHER -> Palette.WARNING;
            case UNBOUND, CLEARED -> ThemedPanel.MUTED;
        };
        int center = RX_X0 + RX_SIZE / 2;
        String title = Component.translatable(KEY + "receiver.short").getString();
        ThemeText.small(graphics, this.font, title, center - ThemeText.smallWidth(this.font, title) / 2, RX_Y0 - 9, ThemedPanel.MUTED);
        graphics.outline(RX_X0 - 1, RX_Y0 - 1, RX_SIZE + 2, RX_SIZE + 2, color);
        graphics.item(ContentItems.stack(drone.build().stack()), RX_X0 + (RX_SIZE - 16) / 2, RX_Y0 + (RX_SIZE - 16) / 2);
        String state = Component.translatable(KEY + "receiver.state." + (armed ? "armed" : access.key())).getString();
        String shown = ThemeText.smallWidth(this.font, state) <= RX_SIZE + 4 ? state : this.font.plainSubstrByWidth(state, RX_SIZE + 4);
        if (!shown.equals(state)) {
            ThemeStats.cut(state, shown);
        }
        ThemeText.small(graphics, this.font, shown, center - ThemeText.smallWidth(this.font, shown) / 2, RX_Y0 + RX_SIZE + 3, color);
    }

    private void cameraInfo(GuiGraphicsExtractor graphics) {
        QuadcopterEntity drone = this.menu.drone();
        if (drone == null) {
            return;
        }
        DroneBuild build = drone.build();
        float tilt = this.shownUptilt(drone);
        CameraMount.Range range = CameraMount.range(build);
        Component title = Component.translatable(KEY + "camera.title");
        graphics.text(this.font, title, CAMERA_TEXT_X, CAMERA_LABEL_Y, ThemedPanel.TEXT, false);
        String info = Component.translatable(KEY + "camera.info", Numbers.of(range.minDeg(), 0), Numbers.of(range.maxDeg(), 0),
                Numbers.of(this.propsInView(build, tilt), 1)).getString();
        int right = CAMERA_X1 - 4;
        int labelEnd = CAMERA_TEXT_X + this.font.width(title) + 6;
        String shown = ThemeText.smallWidth(this.font, info) <= right - labelEnd ? info
                : Component.translatable(KEY + "camera.props", Numbers.of(this.propsInView(build, tilt), 1)).getString();
        ThemeText.small(graphics, this.font, shown, right - ThemeText.smallWidth(this.font, shown), CAMERA_LABEL_Y + ThemeText.smallBaseline(),
                ThemedPanel.MUTED);
    }

    private String wholeWords(String text, int width) {
        String shown = text;
        while (this.font.width(shown) > width) {
            int space = shown.lastIndexOf(' ');
            if (space <= 0) {
                shown = this.font.plainSubstrByWidth(text, width);
                ThemeStats.cut(text, shown);
                return shown;
            }
            shown = shown.substring(0, space);
        }
        return shown;
    }

    private Optional<PropService.Fit> carriedFit() {
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty() || this.minecraft == null || this.minecraft.player == null) {
            return Optional.empty();
        }
        return this.menu.propFit(carried, this.minecraft.player);
    }

    private Hint hint() {
        QuadcopterEntity drone = this.menu.drone();
        ItemStack carried = this.menu.getCarried();
        if (drone == null) {
            return new Hint(Component.empty(), ThemedPanel.MUTED);
        }
        DroneBuild build = drone.build();
        Optional<BatteryType> battery = Batteries.typeOf(carried);
        if (battery.isPresent()) {
            if (BatteryFit.allowed(BatteryFit.check(build.frame(), battery.get()))) {
                return new Hint(Component.translatable(KEY + "hint_battery"), ThemedPanel.MUTED);
            }
            if (!ClientWorldRules.INSTANCE.partCompatibility().enforced()) {
                return new Hint(Component.translatable(KEY + "hint_battery_free", PartTooltips.batteryFitReason(build.frame(), battery.get())),
                        Palette.WARNING);
            }
            return new Hint(PartTooltips.batteryFitReason(build.frame(), battery.get()), ThemedPanel.BAD);
        }
        Optional<PropType> prop = DroneInteractions.propType(carried);
        Optional<PropService.Fit> fit = this.carriedFit();
        if (prop.isEmpty() || fit.isEmpty()) {
            return new Hint(Component.translatable(KEY + "hint"), ThemedPanel.MUTED);
        }
        return switch (fit.get()) {
            case SWAP_ONE -> new Hint(Component.translatable(KEY + "hint_swap_one"), Palette.ACCENT);
            case SWAP_ALL -> new Hint(Component.translatable(KEY + "hint_swap_all"), Palette.ACCENT);
            case NEED_FOUR -> new Hint(Component.translatable(KEY + "hint_need_four", PartWear.POSITIONS), Palette.WARNING);
            case TOO_LARGE -> new Hint(Component.translatable(KEY + "hint_too_large", build.frame().maxPropMm), ThemedPanel.BAD);
            case TOO_SMALL -> new Hint(Component.translatable(KEY + "hint_too_small", build.frame().minPropMm), ThemedPanel.BAD);
            case WRONG_MOUNT -> new Hint(Component.translatable(KEY + "hint_wrong_mount"), ThemedPanel.BAD);
        };
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack itemStack) {
        List<Component> lines = super.getTooltipFromContainerItem(itemStack);
        if (this.hoveredSlot instanceof DroneServiceMenu.PropSlot slot && !lines.isEmpty()) {
            List<Component> result = new ArrayList<>(lines.size() + 1);
            result.add(lines.getFirst());
            result.add(positionName(slot.position()).copy().withStyle(ChatFormatting.GOLD));
            result.addAll(lines.subList(1, lines.size()));
            return result;
        }
        return lines;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int rx = mouseX - this.leftPos;
        int ry = mouseY - this.topPos;
        if (rx >= INFO_X0 && rx < INFO_X1 && ry >= INFO_Y0 && ry < INFO_Y1) {
            ThemeText.tooltipLines(graphics, this.font, this.batteryTooltip(), mouseX, mouseY);
        } else if (rx >= CAMERA_X0 && rx < CAMERA_X1 && ry >= CAMERA_Y0 && ry < CAMERA_ROW_Y - 1 || rx >= GAUGE_X0 && rx < GAUGE_X1
                && ry >= GAUGE_Y0 && ry < GAUGE_Y1) {
            QuadcopterEntity drone = this.menu.drone();
            if (drone != null) {
                ThemeText.tooltipWrapped(graphics, this.font, this.cameraTooltip(CameraMount.range(drone.build())), 220, mouseX, mouseY);
            }
        } else if (rx >= BODY_X0 && rx < BODY_X1 && ry >= BODY_Y0 && ry < BODY_Y1
                || rx >= PROPS_X0 && rx < PREVIEW_X1 && ry >= HINT_Y - 1 && ry < HINT_Y + 9 && this.menu.getCarried().isEmpty()) {
            ThemeText.tooltipLines(graphics, this.font, this.propTooltip(), mouseX, mouseY);
        }
    }

    private List<Component> batteryTooltip() {
        List<Component> lines = new ArrayList<>();
        QuadcopterEntity drone = this.menu.drone();
        if (drone == null) {
            return lines;
        }
        DroneBuild build = drone.build();
        lines.add(Component.translatable(KEY + "battery_fits").withStyle(ChatFormatting.GOLD));
        for (BatteryType battery : BatteryFit.fitting(build.frame())) {
            MutableComponent line = Component.literal("- ").append(Component.translatable(battery.translationKey()));
            if (BatteryFit.check(build.frame(), battery) == BatteryFit.Result.WEAK) {
                line.append(" ").append(Component.translatable(KEY + "battery_weak").withStyle(ChatFormatting.YELLOW));
            }
            lines.add(line.withStyle(ChatFormatting.GRAY));
        }
        freeFit(lines);
        return lines;
    }

    private static void freeFit(List<Component> lines) {
        if (!ClientWorldRules.INSTANCE.partCompatibility().enforced()) {
            lines.add(Component.translatable(KEY + "free_fit").withStyle(ChatFormatting.YELLOW));
        }
    }

    private List<Component> propTooltip() {
        List<Component> lines = new ArrayList<>();
        QuadcopterEntity drone = this.menu.drone();
        if (drone == null) {
            return lines;
        }
        DroneBuild build = drone.build();
        lines.add(Component.translatable(KEY + "frame", Component.translatable(build.frame().translationKey())).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(KEY + "props_installed", Component.translatable(build.prop().translationKey())).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(KEY + "props_fits", build.frame().maxPropMm).withStyle(ChatFormatting.GRAY));
        for (PropType prop : PropType.values()) {
            if (PropService.fitsFrame(build.frame(), prop)) {
                lines.add(Component.literal("- ").append(Component.translatable(prop.translationKey())).withStyle(ChatFormatting.GRAY));
            }
        }
        lines.add(Component.translatable(KEY + "props_help_1").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(KEY + "props_help_2").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(KEY + "props_help_3").withStyle(ChatFormatting.DARK_GRAY));
        freeFit(lines);
        return lines;
    }

    private record Hint(Component text, int color) {
    }

    private static final class UptiltSlider extends StepSlider implements LabeledValue {
        private final float standard;

        UptiltSlider(int x, int y, int width, int height, CameraMount.Range range, float initial, DoubleConsumer onChange) {
            super(x, y, width, height, range.minDeg(), range.maxDeg(), 1.0, initial,
                    v -> Component.translatable(KEY + "camera.value", Numbers.of(v, 0)), onChange);
            this.standard = range.defaultDeg();
        }

        @Override
        public Component themeLabel() {
            return Component.empty();
        }

        @Override
        public Component themeValue() {
            return this.getMessage();
        }

        @Override
        public int themeLabelWidth(int width) {
            return 0;
        }

        @Override
        public boolean themeModified() {
            return this.current() != this.standard;
        }
    }
}
