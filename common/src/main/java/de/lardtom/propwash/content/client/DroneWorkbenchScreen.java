package de.lardtom.propwash.content.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.apiimpl.client.WorkbenchExtensions;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.client.render.DronePreview;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DronePresets;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.Recycling;
import de.lardtom.propwash.content.WorkbenchLayout;
import de.lardtom.propwash.content.client.paint.PaintScreen;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DroneWorkbenchScreen extends AbstractContainerScreen<DroneWorkbenchMenu> implements ThemedScreen {
    private static final Identifier TEXTURE = Constants.id("textures/gui/drone_workbench.png");
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int HEADER_HEIGHT = 14;
    public static final int TITLE_GAP = 6;
    private static final int PAINT_PADDING = 14;
    private static final Identifier SALVAGE_ICON = Constants.id("gui/slot/drone");
    private static final String KEY = "propwash.content.workbench.";
    private static final int BULLET = WorkbenchLayout.ISSUE_BULLET;
    private static final String ELLIPSIS = "…";
    private static final int SPIN_LEGEND_X0 = 55;
    private static final int SPIN_LEGEND_X1 = 121;
    private static final int SPIN_LEGEND_Y = 114;
    private static final String[] SPIN_ICON = {"..XXXX....", ".X....X...", "X......X..", "X....XXXXX", "......XXX.", ".......X.."};
    private static final String[] POSITIONS = {"front_left", "front_right", "rear_left", "rear_right"};

    private static final int TEXT = WorkbenchReport.COLOR_VALUE;
    private static final int MUTED = WorkbenchReport.COLOR_LABEL;
    private static final int GHOST_VEIL = Palette.withAlpha(Palette.BACKGROUND, 0xB4);
    private static final int FILLED_WELL = Palette.BORDER_STRONG;
    private static final int TOOLTIP_TITLE = Palette.TEXT & 0xFFFFFF;
    private static final int TOOLTIP_BODY = Palette.TEXT_MUTED & 0xFFFFFF;
    private static final int TOOLTIP_WARNING = Palette.WARNING & 0xFFFFFF;

    private final Map<Integer, ItemStack> ghosts = new HashMap<>();
    private final Map<Identifier, Boolean> knownSprites = new HashMap<>();
    private final List<Component> overflowIssues = new ArrayList<>();
    private WorkbenchReport report;
    private @Nullable DroneBuild labelBuild;
    private Optional<Component> label = Optional.empty();
    private ItemStack droneGhost = ItemStack.EMPTY;
    private float yaw = 210.0F;
    private long lastFrameMillis;
    private long lastDragMillis;
    private boolean draggingPreview;
    private int issueOverflowTop = -1;
    private @Nullable FlatButton paintButton;
    private final List<FlatButton> extensionButtons = new ArrayList<>();
    private final Map<String, String> labelCuts = new HashMap<>();
    private final List<Component> extensionTitles = new ArrayList<>();
    private final List<Component> extensionTooltips = new ArrayList<>();
    private int headerSpace;

    public DroneWorkbenchScreen(DroneWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WorkbenchLayout.WIDTH, WorkbenchLayout.HEIGHT);
        this.inventoryLabelY = WorkbenchLayout.INVENTORY_Y - 11;
        this.report = WorkbenchReport.of(menu.check(), PartWear.fromSlots(menu.conditions()), menu.partCompatibility());
    }

    @Override
    protected void init() {
        super.init();
        this.lastFrameMillis = Util.getMillis();
        this.knownSprites.clear();
        this.droneGhost = DroneAssembly.preset(DroneBuild.FREESTYLE);
        Component paintLabel = Component.translatable("propwash.paint.open");
        int paintWidth = this.font.width(paintLabel) + PAINT_PADDING;
        this.paintButton = this.addRenderableWidget(new FlatButton(this.leftPos + WorkbenchLayout.PREVIEW_X1 - paintWidth, this.topPos + 2,
                paintWidth, 13, paintLabel, FlatButton.Style.NORMAL, this::openPaint));
        this.updatePaintButton();
        this.initExtensions();
    }

    private void initExtensions() {
        this.extensionButtons.clear();
        this.labelCuts.clear();
        this.extensionTitles.clear();
        this.extensionTooltips.clear();
        this.headerSpace = this.computeHeaderSpace();
        WorkbenchHost host = new WorkbenchHost(this, this.menu, this.headerSpace);
        WorkbenchExtensions.initAll(host);
        List<WorkbenchHost.Entry> entries = host.buttons();
        if (entries.isEmpty() || this.paintButton == null) {
            return;
        }
        Component moreLabel = Component.translatable("propwash.parts.workbench.more");
        int moreWidth = moreButtonWidth(this.font);
        List<int[]> widths = new ArrayList<>();
        for (WorkbenchHost.Entry entry : entries) {
            widths.add(this.variantWidths(entry));
        }
        HeaderLayout.Result layout = HeaderLayout.fit(this.headerSpace, WorkbenchContext.BUTTON_GAP, moreWidth, WorkbenchHost.MAX_HEADER_BUTTONS,
                widths);
        if (layout.width() > this.headerSpace) {
            Constants.LOG.warn("Werkbank-Kopf: kein Platz für Addon-Knöpfe neben dem Titel ({} px)", this.headerSpace);
            return;
        }
        int x = this.paintButton.getX();
        if (layout.more()) {
            x -= WorkbenchContext.BUTTON_GAP + moreWidth;
            List<WorkbenchHost.Entry> rest = entries.subList(layout.shown(), entries.size());
            FlatButton button = this.addRenderableWidget(new FlatButton(x, this.topPos + 2, moreWidth, 13, moreLabel, FlatButton.Style.NORMAL,
                    () -> this.minecraft.gui.setScreen(new WorkbenchMoreScreen(this, this.menu, rest))));
            this.extensionButtons.add(button);
            this.extensionTitles.add(moreLabel);
            this.extensionTooltips.add(Component.translatable("propwash.parts.workbench.more.tooltip"));
        }
        for (int i = layout.shown() - 1; i >= 0; i--) {
            WorkbenchHost.Entry entry = entries.get(i);
            int width = widths.get(i)[layout.variant(i)];
            x -= WorkbenchContext.BUTTON_GAP + width;
            Runnable press = () -> {
                try {
                    entry.onPress().run();
                } catch (RuntimeException e) {
                    Constants.LOG.error("Werkbank-Knopf eines Addons ist fehlgeschlagen", e);
                }
            };
            FlatButton button;
            if (entry.iconOnly()) {
                button = new FlatButton(x, this.topPos + 2, width, 13, entry.label(), FlatButton.Style.NORMAL, press).icon(entry.icon());
                this.extensionTitles.add(entry.label());
                this.extensionTooltips.add(Component.empty());
            } else {
                Component shown = layout.variant(i) == 0 || entry.shortLabel() == null ? entry.label() : entry.shortLabel();
                button = new FlatButton(x, this.topPos + 2, width, 13, this.fitLabel(shown, width), FlatButton.Style.NORMAL, press);
                this.extensionTitles.add(entry.label());
                this.extensionTooltips.add(entry.tooltip());
            }
            this.extensionButtons.add(this.addRenderableWidget(button));
        }
    }

    private int computeHeaderSpace() {
        if (this.paintButton == null) {
            return 0;
        }
        return this.paintButton.getX() - (this.titleEnd() + TITLE_GAP);
    }

    public static int headerSpace(Font font, Component title, Component paintLabel) {
        int paintX = WorkbenchLayout.PREVIEW_X1 - (font.width(paintLabel) + PAINT_PADDING);
        return paintX - (ThemeDraw.brandedTitleX(font, 2) + font.width(title) + TITLE_GAP);
    }

    public static int moreButtonWidth(Font font) {
        return font.width(Component.translatable("propwash.parts.workbench.more")) + 12;
    }

    public int headerSpace() {
        return this.headerSpace;
    }

    public int titleEnd() {
        return ThemeDraw.brandedTitleX(this.font, this.leftPos + 2) + this.font.width(this.title);
    }

    private int[] variantWidths(WorkbenchHost.Entry entry) {
        if (entry.iconOnly()) {
            return new int[]{WorkbenchContext.ICON_BUTTON_SIZE};
        }
        int full = this.extensionWidth(entry.label());
        if (entry.shortLabel() == null) {
            return new int[]{full};
        }
        return new int[]{full, Math.min(full, this.extensionWidth(entry.shortLabel()))};
    }

    private int extensionWidth(Component label) {
        return Math.min(this.font.width(label) + WorkbenchContext.BUTTON_PADDING, WorkbenchContext.MAX_BUTTON_WIDTH);
    }

    private Component fitLabel(Component label, int width) {
        String text = label.getString();
        if (this.font.width(text) + WorkbenchContext.BUTTON_PADDING <= width) {
            return label;
        }
        String shown = this.font.plainSubstrByWidth(text, width - WorkbenchContext.BUTTON_PADDING - this.font.width("…")) + "…";
        this.labelCuts.put(text, shown);
        return Component.literal(shown);
    }

    public List<FlatButton> extensionButtons() {
        return List.copyOf(this.extensionButtons);
    }

    public boolean canPaint() {
        return !this.menu.frame().isEmpty() && this.menu.getCarried().isEmpty();
    }

    public @Nullable PaintScreen openPaint() {
        if (!this.canPaint()) {
            return null;
        }
        PaintScreen screen = new PaintScreen(this, this.menu);
        this.minecraft.gui.setScreen(screen);
        return screen;
    }

    private void updatePaintButton() {
        if (this.paintButton != null) {
            this.paintButton.active = this.canPaint();
        }
    }

    public WorkbenchReport report() {
        return this.report;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(graphics, this.width, this.height);
        this.minecraft.gui.hud.extractDeferredSubtitles();
        this.report = WorkbenchReport.of(this.menu.check(), PartWear.fromSlots(this.menu.conditions()), this.menu.partCompatibility());
        this.advanceYaw();
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
        int titleRight = this.paintButton != null ? this.paintButton.getX() - TITLE_GAP : x + this.imageWidth - 8;
        for (FlatButton button : this.extensionButtons) {
            titleRight = Math.min(titleRight, button.getX() - TITLE_GAP);
        }
        ThemeDraw.brandedHeader(graphics, this.font, x + 2, y + 2, x + this.imageWidth - 2, HEADER_HEIGHT, this.title, titleRight);
        this.drawSlotOverlays(graphics, x, y);
        this.drawPreview(graphics, x, y);
        this.drawStatus(graphics, x, y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MUTED, false);
        int salvageX = WorkbenchLayout.SALVAGE_X + 21;
        int salvageWidth = WorkbenchLayout.PREVIEW_X1 - 3 - salvageX;
        ThemeText.fitted(graphics, this.font, Component.translatable(KEY + "salvage"), salvageX, WorkbenchLayout.SALVAGE_Y, salvageWidth, MUTED);
        String direction = Component.translatable(KEY + "salvage_direction").getString();
        ThemeText.small(graphics, this.font, direction, salvageX, WorkbenchLayout.SALVAGE_Y + 10, WorkbenchReport.COLOR_LABEL);
        this.drawSpinLegend(graphics);
        this.drawPreviewText(graphics);
        this.drawStats(graphics);
        this.drawIssues(graphics);
    }

    private void drawSpinLegend(GuiGraphicsExtractor g) {
        String text = Component.translatable(KEY + "spin_legend").getString();
        int iconWidth = SPIN_ICON[0].length();
        int available = SPIN_LEGEND_X1 - SPIN_LEGEND_X0;
        String shown = ThemeText.fit(this.font, text, available - iconWidth - 3);
        int width = iconWidth + 3 + this.font.width(shown);
        int x = SPIN_LEGEND_X0 + (available - width) / 2;
        int y = SPIN_LEGEND_Y;
        int color = Palette.ACCENT;
        for (int row = 0; row < SPIN_ICON.length; row++) {
            for (int column = 0; column < SPIN_ICON[row].length(); column++) {
                if (SPIN_ICON[row].charAt(column) == 'X') {
                    g.fill(x + column, y + row, x + column + 1, y + row + 1, color);
                }
            }
        }
        ThemeText.text(g, this.font, shown, x + iconWidth + 3, y - 1, WorkbenchReport.COLOR_LABEL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.labelCuts.forEach(ThemeStats::cut);
        this.updatePaintButton();
        super.extractRenderState(graphics, mouseX, mouseY, a);
        List<Component> hint = this.hintTooltip(mouseX, mouseY);
        ThemeText.tooltipLines(graphics, this.font, hint, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.menu.getCarried().isEmpty() && this.report.build() != null
                && this.inPreview(event.x(), event.y())) {
            this.draggingPreview = true;
            this.lastDragMillis = Util.getMillis();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingPreview) {
            this.yaw = wrapDegrees(this.yaw + (float) dx * 2.5F);
            this.lastDragMillis = Util.getMillis();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingPreview) {
            this.draggingPreview = false;
            this.lastDragMillis = Util.getMillis();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void advanceYaw() {
        long now = Util.getMillis();
        long delta = Math.max(0L, Math.min(100L, now - this.lastFrameMillis));
        this.lastFrameMillis = now;
        if (!this.draggingPreview && now - this.lastDragMillis > 2500L) {
            this.yaw = wrapDegrees(this.yaw + delta * 0.03F);
        }
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        return wrapped < 0 ? wrapped + 360.0F : wrapped;
    }

    private boolean inPreview(double mouseX, double mouseY) {
        double rx = mouseX - this.leftPos;
        double ry = mouseY - this.topPos;
        return rx >= WorkbenchLayout.PREVIEW_X0 && rx < WorkbenchLayout.PREVIEW_X1 && ry >= WorkbenchLayout.PREVIEW_Y0
                && ry < WorkbenchLayout.PREVIEW_Y1;
    }

    private void drawSlotOverlays(GuiGraphicsExtractor g, int x, int y) {
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            if (slot.index < DroneWorkbenchMenu.PART_SLOTS) {
                PartCategory category = BuildSlots.categoryOf(slot.index);
                int highlight = this.report.highlight(category);
                if (slot.hasItem()) {
                    g.fill(sx, sy, sx + 16, sy + 16, FILLED_WELL);
                }
                if (highlight != 0 && slot.hasItem()) {
                    g.outline(sx - 2, sy - 2, 20, 20, highlight);
                }
                if (!slot.hasItem()) {
                    this.drawEmptyIcon(g, slotIcon(slot.index), this.ghost(slot.index), sx, sy);
                }
            } else if (slot.index == DroneWorkbenchMenu.SALVAGE_SLOT && !slot.hasItem()) {
                this.drawEmptyIcon(g, SALVAGE_ICON, this.droneGhost, sx, sy);
            }
        }
    }

    private ItemStack ghost(int slot) {
        return this.ghosts.computeIfAbsent(slot, s -> {
            PartInfo part = ghostPart(s);
            return part == null ? ItemStack.EMPTY : ContentItems.stack(part);
        });
    }

    static @Nullable PartInfo ghostPart(int slot) {
        Optional<AccessoryMount> mount = BuildSlots.mountOf(slot);
        if (mount.isPresent()) {
            for (AccessoryType type : AccessoryType.values()) {
                if (type.fits(mount.get())) {
                    return type;
                }
            }
            return null;
        }
        return BuildSlots.categoryOf(slot).get(DroneBuild.FREESTYLE);
    }

    private void drawEmptyIcon(GuiGraphicsExtractor g, Identifier sprite, ItemStack fallback, int sx, int sy) {
        if (this.hasSprite(sprite)) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, sx, sy, 16, 16);
            return;
        }
        if (!fallback.isEmpty()) {
            g.fakeItem(fallback, sx, sy);
            g.fill(sx, sy, sx + 16, sy + 16, GHOST_VEIL);
        }
    }

    public static Identifier slotIcon(int slot) {
        Optional<AccessoryMount> mount = BuildSlots.mountOf(slot);
        if (mount.isPresent()) {
            return Constants.id("gui/slot/accessory_" + mount.get().key());
        }
        return Constants.id("gui/slot/" + BuildSlots.categoryOf(slot).key());
    }

    private boolean hasSprite(Identifier sprite) {
        return this.knownSprites.computeIfAbsent(sprite, id -> {
            try {
                TextureAtlas atlas = this.minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
                return atlas.getSprite(id) != atlas.missingSprite();
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    private void drawPreview(GuiGraphicsExtractor g, int x, int y) {
        ThemeDraw.previewStage(g, x + WorkbenchLayout.PREVIEW_X0, y + WorkbenchLayout.PREVIEW_Y0, x + WorkbenchLayout.PREVIEW_X1,
                y + WorkbenchLayout.PREVIEW_Y1);
        DroneBuild build = this.report.build();
        if (build != null) {
            DronePreview.render(g, build, this.report.wear(), this.menu.paint(), x + WorkbenchLayout.PREVIEW_X0 + 2,
                    y + WorkbenchLayout.PREVIEW_Y0 + 2, x + WorkbenchLayout.PREVIEW_X1 - 2, y + WorkbenchLayout.PREVIEW_Y1 - 2, this.yaw);
        }
    }

    private void drawStatus(GuiGraphicsExtractor g, int x, int y) {
        boolean hasResult = !this.menu.resultPreview().isEmpty();
        int ax = x + WorkbenchLayout.ARROW_X;
        int ay = y + WorkbenchLayout.ARROW_Y;
        if (hasResult) {
            arrow(g, ax, ay, WorkbenchReport.COLOR_ACCENT);
        } else if (this.report.check().complete()) {
            cross(g, ax + 12, ay + 8, 5, WorkbenchReport.COLOR_BAD);
        }
    }

    private static void arrow(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 2, y + 6, x + 15, y + 10, color);
        for (int row = 1; row < 15; row++) {
            g.fill(x + 15, y + row, x + 15 + Math.min(row, 15 - row), y + row + 1, color);
        }
    }

    private static void cross(GuiGraphicsExtractor g, int cx, int cy, int half, int color) {
        for (int i = -half; i < half; i++) {
            g.fill(cx + i, cy + i, cx + i + 2, cy + i + 1, color);
            g.fill(cx - i - 1, cy + i, cx - i + 1, cy + i + 1, color);
        }
    }

    private void drawPreviewText(GuiGraphicsExtractor g) {
        int x0 = WorkbenchLayout.PREVIEW_X0 + 5;
        int width = WorkbenchLayout.PREVIEW_X1 - WorkbenchLayout.PREVIEW_X0 - 10;
        BuildSlots.Check check = this.report.check();
        DroneBuild build = this.report.build();
        if (build != null) {
            if (build != this.labelBuild) {
                this.labelBuild = build;
                this.label = DronePresets.label(build);
            }
            this.label.ifPresent(label -> {
                if (this.font.width(label) <= width) {
                    g.text(this.font, label, x0, WorkbenchLayout.PREVIEW_Y0 + 4, MUTED, false);
                } else {
                    ThemeText.fitted(g, this.font, label, x0, WorkbenchLayout.PREVIEW_Y0 + 4, width, MUTED);
                }
            });
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (check.empty()) {
            lines.addAll(this.font.split(Component.translatable(KEY + "hint.empty"), width));
        } else if (!check.missing().isEmpty()) {
            lines.add(Component.translatable(KEY + "missing").withStyle(ChatFormatting.BOLD).getVisualOrderText());
            for (PartCategory category : PartCategory.values()) {
                if (check.missing().contains(category)) {
                    lines.addAll(this.font.split(Component.literal("• ").append(Component.translatable(category.translationKey())), width));
                }
            }
        } else {
            lines.addAll(this.font.split(Component.translatable(KEY + "hint.mixed"), width));
        }
        int maxLines = (WorkbenchLayout.PREVIEW_Y1 - WorkbenchLayout.PREVIEW_Y0 - 8) / 9;
        int shown = ThemeText.shownLines(Component.translatable(KEY + (check.empty() ? "hint.empty" : check.missing().isEmpty() ? "hint.mixed" : "missing")),
                lines.size(), maxLines);
        int top = WorkbenchLayout.PREVIEW_Y0 + (WorkbenchLayout.PREVIEW_Y1 - WorkbenchLayout.PREVIEW_Y0 - shown * 9) / 2 + 1;
        for (int i = 0; i < shown; i++) {
            FormattedCharSequence line = lines.get(i);
            int lx = check.empty() ? WorkbenchLayout.PREVIEW_X0 + (WorkbenchLayout.PREVIEW_X1 - WorkbenchLayout.PREVIEW_X0 - this.font.width(line)) / 2 : x0;
            g.text(this.font, line, lx, top + i * 9, i == 0 && !check.empty() ? TEXT : MUTED, false);
        }
    }

    private void drawStats(GuiGraphicsExtractor g) {
        List<WorkbenchReport.Row> rows = this.report.rows();
        int y = WorkbenchLayout.STATS_Y0;
        for (WorkbenchReport.Row row : rows) {
            int valueWidth = this.font.width(row.value());
            int labelWidth = WorkbenchLayout.statLabelWidth(valueWidth);
            int labelX = WorkbenchLayout.STATS_X0 + WorkbenchLayout.STATS_PAD;
            if (this.font.width(row.label()) <= labelWidth) {
                g.text(this.font, row.label(), labelX, y, WorkbenchReport.COLOR_LABEL, false);
            } else {
                ThemeText.fitted(g, this.font, row.label(), labelX, y, labelWidth, WorkbenchReport.COLOR_LABEL);
            }
            g.text(this.font, row.value(), WorkbenchLayout.STATS_X1 - WorkbenchLayout.STATS_PAD - valueWidth, y, row.color(), false);
            y += WorkbenchLayout.STATS_ROW;
        }
    }

    private int issuesTop() {
        return WorkbenchLayout.STATS_Y0 + 6 * WorkbenchLayout.STATS_ROW + 1;
    }

    private void drawIssues(GuiGraphicsExtractor g) {
        this.overflowIssues.clear();
        this.issueOverflowTop = -1;
        int x0 = WorkbenchLayout.STATS_X0 + WorkbenchLayout.STATS_PAD;
        int textX = x0 + BULLET + WorkbenchLayout.ISSUE_BULLET_GAP;
        int width = WorkbenchLayout.issueTextWidth();
        int y = this.issuesTop();
        List<WorkbenchReport.Line> issues = this.report.issues();
        if (issues.isEmpty()) {
            if (this.report.build() != null && !this.menu.resultPreview().isEmpty()) {
                g.fill(x0, y + 2, x0 + BULLET, y + 2 + BULLET, WorkbenchReport.COLOR_GOOD);
                g.text(this.font, Component.translatable(KEY + "ready"), textX, y, WorkbenchReport.COLOR_GOOD, false);
            }
            return;
        }
        List<IssueRow> rows = new ArrayList<>();
        for (WorkbenchReport.Line issue : issues) {
            List<FormattedCharSequence> wrapped = this.font.split(issue.text(), width);
            for (int i = 0; i < wrapped.size(); i++) {
                rows.add(new IssueRow(wrapped.get(i), issue.color(), i == 0 && !issue.heading(), i == wrapped.size() - 1));
            }
        }
        int capacity = Math.max(1, (WorkbenchLayout.ISSUES_Y1 - this.issuesTop()) / 9);
        boolean overflow = rows.size() > capacity;
        int shown = overflow ? capacity - 1 : rows.size();
        for (int i = 0; i < shown; i++) {
            IssueRow row = rows.get(i);
            if (row.bullet()) {
                g.fill(x0, y + 2, x0 + BULLET, y + 2 + BULLET, row.color());
            }
            if (overflow && i == shown - 1 && !row.last()) {
                g.text(this.font, this.cut(row.text(), width), textX, y, row.color(), false);
            } else {
                g.text(this.font, row.text(), textX, y, row.color(), false);
            }
            y += 9;
        }
        if (overflow) {
            int hidden = 0;
            for (int i = shown; i < rows.size(); i++) {
                if (rows.get(i).bullet()) {
                    hidden++;
                }
            }
            if (hidden == 0) {
                hidden = 1;
            }
            this.issueOverflowTop = y;
            g.text(this.font, Component.translatable(KEY + "more_issues", hidden), textX, y, MUTED, false);
            for (WorkbenchReport.Line issue : issues) {
                this.overflowIssues.add(issue.text().copy().withColor(issue.color() & 0xFFFFFF));
            }
        }
    }

    private String cut(FormattedCharSequence row, int width) {
        StringBuilder text = new StringBuilder();
        row.accept((index, style, codepoint) -> {
            text.appendCodePoint(codepoint);
            return true;
        });
        String full = text.toString().stripTrailing();
        String shown = this.font.plainSubstrByWidth(full, width - this.font.width(ELLIPSIS)).stripTrailing() + ELLIPSIS;
        ThemeStats.cut(full, shown);
        return shown;
    }

    private List<Component> hintTooltip(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        boolean carrying = !this.menu.getCarried().isEmpty();
        Slot slot = this.hoveredSlot;
        if (slot != null && !slot.hasItem()) {
            if (slot.index < DroneWorkbenchMenu.PART_SLOTS && !carrying) {
                lines.add(slotName(slot.index).copy().withColor(TOOLTIP_TITLE));
                lines.add(Component.translatable(slotHintKey(slot.index)).withColor(TOOLTIP_BODY));
                this.accessoryFitHint(slot.index, lines);
            } else if (slot.index == DroneWorkbenchMenu.SALVAGE_SLOT) {
                if (carrying && !DroneAssembly.isDrone(this.menu.getCarried()) && !Recycling.accepts(this.menu.getCarried())) {
                    return lines;
                }
                lines.add(Component.translatable(KEY + "salvage_title").withColor(TOOLTIP_TITLE));
                lines.add(Component.translatable(KEY + "hint.salvage").withColor(TOOLTIP_BODY));
                lines.add(Component.translatable(KEY + "hint.recycle").withColor(TOOLTIP_BODY));
                lines.add(Component.translatable(KEY + "hint.repair_props").withColor(TOOLTIP_BODY));
                if (!this.menu.partsEmpty()) {
                    lines.add(Component.translatable(KEY + "hint.salvage_blocked").withColor(TOOLTIP_WARNING));
                }
                missingWarning(this.menu.getCarried()).ifPresent(line -> lines.add(line.copy().withColor(TOOLTIP_WARNING)));
            } else if (slot.index == DroneWorkbenchMenu.RESULT_SLOT && !carrying) {
                lines.add(Component.translatable(KEY + "result_title").withColor(TOOLTIP_TITLE));
                lines.add(this.resultReason().copy().withColor(TOOLTIP_BODY));
            }
            return lines;
        }
        if (carrying || slot != null) {
            return lines;
        }
        for (int i = 0; i < this.extensionButtons.size(); i++) {
            FlatButton button = this.extensionButtons.get(i);
            if (mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() && mouseY >= button.getY()
                    && mouseY < button.getY() + button.getHeight()) {
                lines.add(this.extensionTitles.get(i).copy().withColor(TOOLTIP_TITLE));
                if (!this.extensionTooltips.get(i).getString().isEmpty()) {
                    lines.add(this.extensionTooltips.get(i).copy().withColor(TOOLTIP_BODY));
                }
                return lines;
            }
        }
        FlatButton paint = this.paintButton;
        if (paint != null && mouseX >= paint.getX() && mouseX < paint.getX() + paint.getWidth() && mouseY >= paint.getY()
                && mouseY < paint.getY() + paint.getHeight()) {
            lines.add(Component.translatable("propwash.paint.open").withColor(TOOLTIP_TITLE));
            lines.add(Component.translatable(this.menu.frame().isEmpty() ? "propwash.paint.open_hint_frame" : "propwash.paint.open_hint")
                    .withColor(TOOLTIP_BODY));
            return lines;
        }
        int rx = mouseX - this.leftPos;
        int ry = mouseY - this.topPos;
        if (rx >= WorkbenchLayout.STATS_X0 && rx < WorkbenchLayout.STATS_X1 && ry >= WorkbenchLayout.STATS_Y0
                && ry < this.issuesTop() - 1) {
            lines.addAll(this.report.details());
        } else if (this.issueOverflowTop >= 0 && rx >= WorkbenchLayout.STATS_X0 && rx < WorkbenchLayout.STATS_X1
                && ry >= this.issueOverflowTop && ry < this.issueOverflowTop + 9) {
            lines.addAll(this.overflowIssues);
        } else if (this.report.build() != null && this.inPreview(mouseX, mouseY)) {
            lines.add(Component.translatable(KEY + "hint.rotate").withColor(TOOLTIP_BODY));
        }
        return lines;
    }

    public static Optional<Component> missingWarning(ItemStack drone) {
        if (!DroneAssembly.isDrone(drone)) {
            return Optional.empty();
        }
        List<PartInfo> missing = DroneAssembly.buildOf(drone).missingParts();
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        List<String> ids = new ArrayList<>();
        for (PartInfo part : missing) {
            ids.add(part.id().toString());
        }
        return Optional.of(Component.translatable("propwash.parts.workbench.missing_warning", String.join(", ", ids)));
    }

    private Component resultReason() {
        BuildSlots.Check check = this.report.check();
        if (check.empty()) {
            return Component.translatable(KEY + "hint.empty");
        }
        if (!check.missing().isEmpty() || !check.mixed().isEmpty()) {
            return Component.translatable(KEY + "hint.incomplete");
        }
        if (!DroneAssembly.droneItemAvailable()) {
            return Component.translatable(KEY + "no_drone_item");
        }
        if (!this.report.buildable()) {
            boolean onlyCompatibility = check.build().map(build -> build.buildable(PartCompatibility.LIFTED)).orElse(false);
            return Component.translatable(KEY + (onlyCompatibility ? "hint.problems_rule" : "hint.problems"));
        }
        if (!this.report.liftedProblems().isEmpty()) {
            return Component.translatable(KEY + "hint.result_lifted");
        }
        return Component.translatable(KEY + "hint.result");
    }

    public static String slotHintKey(int slot) {
        Optional<AccessoryMount> mount = BuildSlots.mountOf(slot);
        if (mount.isPresent()) {
            return KEY + "slot_hint.accessory_" + mount.get().key();
        }
        return KEY + "slot_hint." + BuildSlots.categoryOf(slot).key();
    }

    private void accessoryFitHint(int slot, List<Component> lines) {
        Optional<AccessoryMount> mount = BuildSlots.mountOf(slot);
        if (mount.isEmpty()) {
            return;
        }
        Slot frameSlot = this.menu.getSlot(BuildSlots.FRAME);
        @Nullable PartInfo frame = frameSlot.hasItem() ? DroneAssembly.part(frameSlot.getItem()).orElse(null) : null;
        if (frame instanceof FrameType frameType && !frameType.accessoryMounts.contains(mount.get())) {
            boolean free = !this.report.rule().enforced();
            lines.add(Component.translatable(KEY + (free ? "slot_hint.accessory_unsupported_free" : "slot_hint.accessory_unsupported"))
                    .withColor(TOOLTIP_WARNING));
        } else {
            lines.add(Component.translatable(KEY + "slot_hint.accessory_optional").withColor(TOOLTIP_BODY));
        }
    }

    public static Component slotName(int slot) {
        PartCategory category = BuildSlots.categoryOf(slot);
        Optional<AccessoryMount> mount = BuildSlots.mountOf(slot);
        if (mount.isPresent()) {
            return Component.translatable(KEY + "slot.accessory_" + mount.get().key());
        }
        MutableComponent name = Component.translatable(KEY + "slot." + category.key());
        if (category.requiredCount() > 1) {
            return Component.translatable(KEY + "slot_position", name,
                    Component.translatable(KEY + "position." + POSITIONS[BuildSlots.positionInCategory(slot)]));
        }
        return name;
    }

    private record IssueRow(FormattedCharSequence text, int color, boolean bullet, boolean last) {
    }
}
