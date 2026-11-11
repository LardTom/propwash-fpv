package de.lardtom.propwash.guard.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.guard.GuardCategory;
import de.lardtom.propwash.guard.GuardReport;
import de.lardtom.propwash.guard.MixinScan;
import de.lardtom.propwash.guard.NamespaceGuard;
import de.lardtom.propwash.ui.ScrollMath;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

public final class NamespaceReportScreen extends Screen implements ThemedScreen {
    public static final String KEY = "propwash.guard.";
    public static final String RULES_URL = "https://propwashfpv.com/docs/api/#rules";
    private static final int MAX_WIDTH = 440;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int PAD = 10;
    private static final int CARD_PAD = 6;
    private static final int CARD_GAP = 6;
    private static final int SECTION_GAP = 8;
    private static final int BAR_GRAB = 3;
    private static final int COPIED_MILLIS = 2000;

    private final @Nullable Screen parent;
    private final GuardReport report;
    private final List<Block> blocks = new ArrayList<>();
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int contentHeight;
    private double scroll;
    private boolean draggingBar;
    private long copiedAt;
    private @Nullable FlatButton copyButton;
    private @Nullable FlatButton closeButton;

    public NamespaceReportScreen(@Nullable Screen parent) {
        this(parent, NamespaceGuard.report());
    }

    public NamespaceReportScreen(@Nullable Screen parent, GuardReport report) {
        super(Component.translatableWithFallback(KEY + "title", "Reserved namespace"));
        this.parent = parent;
        this.report = report;
    }

    public GuardReport report() {
        return this.report;
    }

    public String detailsText() {
        return this.report.text(NamespaceGuard.modNames(this.report));
    }

    public boolean copyDetails() {
        this.minecraft.keyboardHandler.setClipboard(this.detailsText());
        this.copiedAt = Util.getMillis();
        return true;
    }

    public @Nullable FlatButton copyButton() {
        return this.copyButton;
    }

    public @Nullable FlatButton closeButton() {
        return this.closeButton;
    }

    private int textWidth() {
        return this.windowRight - this.windowLeft - 2 * PAD - Metrics.SCROLLBAR_WIDTH - 2;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, MAX_WIDTH);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        this.buildBlocks();
        int body = this.contentHeight + 2 * PAD;
        int h = Math.min(this.height - 8, HEADER + body + FOOTER);
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        this.scroll = Math.clamp(this.scroll, 0.0, this.maxScroll());
        int buttonY = this.windowBottom - FOOTER + (FOOTER - Metrics.CONTROL_HEIGHT) / 2;
        Component close = Component.translatableWithFallback(KEY + "close", "Close");
        Component copy = Component.translatableWithFallback(KEY + "copy", "Copy");
        Component rules = Component.translatableWithFallback(KEY + "rules", "Add-on rules");
        int room = w - 2 * Metrics.PADDING - 2 * Metrics.GAP;
        int closeWidth = Math.min(Math.max(60, this.font.width(close) + 16), room / 3);
        int copyWidth = Math.min(Math.max(60, this.font.width(copy) + 16), room / 3);
        int rulesWidth = Math.min(Math.max(60, this.font.width(rules) + 16), room - closeWidth - copyWidth);
        this.closeButton = this.addRenderableWidget(new FlatButton(this.windowRight - Metrics.PADDING - closeWidth, buttonY, closeWidth,
                Metrics.CONTROL_HEIGHT, close, FlatButton.Style.PRIMARY, this::onClose));
        FlatButton copyWidget = new FlatButton(this.windowLeft + Metrics.PADDING, buttonY, copyWidth, Metrics.CONTROL_HEIGHT, copy,
                FlatButton.Style.NORMAL, this::copyDetails);
        copyWidget.setTooltip(Tooltip.create(Component.translatableWithFallback(KEY + "copy.tooltip",
                "Copies this list as text – for the add-on author or a bug report.")));
        this.copyButton = this.addRenderableWidget(copyWidget);
        FlatButton rulesWidget = new FlatButton(this.windowLeft + Metrics.PADDING + copyWidth + Metrics.GAP, buttonY, rulesWidth,
                Metrics.CONTROL_HEIGHT, rules, FlatButton.Style.NORMAL, this::openRules);
        rulesWidget.setTooltip(Tooltip.create(Component.literal(RULES_URL)));
        this.addRenderableWidget(rulesWidget);
    }

    private void buildBlocks() {
        this.blocks.clear();
        int width = this.textWidth();
        int inner = width - 2 * CARD_PAD;
        List<String> mods = this.report.mods();
        Component intro = mods.isEmpty()
                ? Component.translatableWithFallback(KEY + "none", "No add-on uses the reserved namespace propwash.")
                : Component.translatableWithFallback(KEY + "intro",
                "These add-ons use the namespace propwash, which is reserved for Propwash. The game keeps running, but they may break with the next update. Please tell their authors.");
        this.blocks.add(new Paragraph(this.lines(intro, width), Palette.TEXT_MUTED));
        for (String mod : mods) {
            List<Row> rows = new ArrayList<>();
            SortedMap<GuardCategory, List<String>> categories = this.report.categories(mod);
            for (Map.Entry<GuardCategory, List<String>> entry : categories.entrySet()) {
                Component label = Component.translatableWithFallback(entry.getKey().translationKey(), entry.getKey().logName())
                        .append(" (" + entry.getValue().size() + ")");
                rows.add(new Row(this.lines(label, inner), Palette.TEXT_FAINT));
                for (String id : entry.getValue()) {
                    rows.add(new Row(this.lines(Component.literal(id), inner - 6), Palette.TEXT));
                }
            }
            this.blocks.add(new Card(this.lines(Component.literal(NamespaceGuard.name(this.report, mod)), inner), mod, rows));
        }
        SortedMap<String, List<String>> mixins = this.report.mixins();
        if (!mixins.isEmpty()) {
            this.blocks.add(new Section(Component.translatableWithFallback(KEY + "mixins", "Mixins into Propwash internals (allowed, at own risk)")));
            for (Map.Entry<String, List<String>> entry : mixins.entrySet()) {
                List<Row> rows = new ArrayList<>();
                rows.add(new Row(this.lines(Component.translatableWithFallback(KEY + "mixins.count", "%s classes", entry.getValue().size()), inner),
                        Palette.TEXT_FAINT));
                for (String target : entry.getValue()) {
                    rows.add(new Row(this.lines(Component.literal(MixinScan.shortName(target)), inner - 6), Palette.TEXT_MUTED));
                }
                this.blocks.add(new Card(this.lines(Component.literal(NamespaceGuard.name(this.report, entry.getKey())), inner), entry.getKey(), rows));
            }
        }
        int height = 0;
        for (Block block : this.blocks) {
            height += block.height() + block.gapBefore();
        }
        this.contentHeight = height;
    }

    private List<FormattedCharSequence> lines(Component text, int width) {
        return ThemeText.balanced(this.font, text, Math.max(20, width));
    }

    public boolean openRules() {
        ConfirmLinkScreen.confirmLinkNow(this, URI.create(RULES_URL), false);
        return true;
    }

    private int bodyTop() {
        return this.windowTop + HEADER;
    }

    private int bodyBottom() {
        return this.windowBottom - FOOTER;
    }

    public double maxScroll() {
        return Math.max(0, this.contentHeight + 2 * PAD - (this.bodyBottom() - this.bodyTop()));
    }

    private int scrollContent() {
        return this.contentHeight + 2 * PAD;
    }

    private int scrollView() {
        return this.bodyBottom() - this.bodyTop();
    }

    private void dragTo(double mouseY) {
        this.scroll = Math.clamp(ScrollMath.dragOffset(mouseY - (this.bodyTop() + 2), this.scrollContent(), this.scrollView() - 4), 0.0,
                this.maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.maxScroll() > 0) {
            this.scroll = Math.clamp(this.scroll - scrollY * 3 * Metrics.LINE_HEIGHT, 0.0, this.maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.maxScroll() > 0) {
            double before = this.scroll;
            int key = event.key();
            if (key == InputConstants.KEY_PAGEDOWN) {
                this.scroll = ScrollMath.page(this.scroll, 1, this.scrollContent(), this.scrollView());
            } else if (key == InputConstants.KEY_PAGEUP) {
                this.scroll = ScrollMath.page(this.scroll, -1, this.scrollContent(), this.scrollView());
            } else if (key == InputConstants.KEY_END) {
                this.scroll = this.maxScroll();
            } else if (key == InputConstants.KEY_HOME) {
                this.scroll = 0.0;
            } else if (event.isDown() && !(this.getFocused() instanceof AbstractWidget)) {
                this.scroll = ScrollMath.wheel(this.scroll, -1.0, this.scrollContent(), this.scrollView());
            } else if (event.isUp() && !(this.getFocused() instanceof AbstractWidget)) {
                this.scroll = ScrollMath.wheel(this.scroll, 1.0, this.scrollContent(), this.scrollView());
            }
            this.scroll = Math.clamp(this.scroll, 0.0, this.maxScroll());
            if (this.scroll != before) {
                return true;
            }
        }
        return super.keyPressed(event);
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
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.maxScroll() > 0
                && event.x() >= this.windowRight - PAD / 2 - Metrics.SCROLLBAR_WIDTH - BAR_GRAB && event.x() < this.windowRight
                && event.y() >= this.bodyTop() && event.y() < this.bodyBottom()) {
            this.draggingBar = true;
            this.dragTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void tick() {
        if (this.copyButton != null) {
            boolean copied = this.copiedAt > 0 && Util.getMillis() - this.copiedAt < COPIED_MILLIS;
            this.copyButton.setMessage(copied ? Component.translatableWithFallback(KEY + "copied", "Copied")
                    : Component.translatableWithFallback(KEY + "copy", "Copy"));
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public Component getNarrationMessage() {
        return Component.literal(this.title.getString() + ". " + this.detailsText());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, HEADER);
        int textY = Metrics.textY(this.windowTop, HEADER - 1);
        int titleX = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.windowTop + 5, this.windowTop + HEADER - 6);
        ThemeText.fitted(g, this.font, this.title, titleX, textY, this.windowRight - titleX - 8, Palette.TEXT);

        int top = this.bodyTop();
        int bottom = this.bodyBottom();
        int x = this.windowLeft + PAD;
        int right = x + this.textWidth();
        g.enableScissor(this.windowLeft, top, this.windowRight, bottom);
        int y = top + PAD - (int) Math.round(this.scroll);
        for (Block block : this.blocks) {
            y += block.gapBefore();
            if (y + block.height() >= top && y <= bottom) {
                block.draw(this, g, x, y, right);
            }
            y += block.height();
        }
        g.disableScissor();
        if (this.maxScroll() > 0) {
            ThemeDraw.scrollbar(g, this.windowRight - PAD / 2 - Metrics.SCROLLBAR_WIDTH, top + 2, bottom - top - 4, this.contentHeight + 2 * PAD,
                    this.scroll, this.draggingBar);
        }

        ThemeDraw.footerBar(g, this.windowLeft, bottom, this.windowRight, this.windowBottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private static void drawLines(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, List<FormattedCharSequence> lines, int x, int y,
                                  int color) {
        int row = y;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, x, row, color, false);
            row += Metrics.LINE_HEIGHT;
        }
    }

    private sealed interface Block permits Paragraph, Section, Card {
        int height();

        int gapBefore();

        void draw(NamespaceReportScreen screen, GuiGraphicsExtractor g, int x, int y, int right);
    }

    private record Paragraph(List<FormattedCharSequence> lines, int color) implements Block {
        @Override
        public int height() {
            return this.lines.size() * Metrics.LINE_HEIGHT;
        }

        @Override
        public int gapBefore() {
            return 0;
        }

        @Override
        public void draw(NamespaceReportScreen screen, GuiGraphicsExtractor g, int x, int y, int right) {
            drawLines(g, screen.font, this.lines, x, y, this.color);
        }
    }

    private record Section(Component title) implements Block {
        @Override
        public int height() {
            return Metrics.SECTION_HEIGHT;
        }

        @Override
        public int gapBefore() {
            return SECTION_GAP;
        }

        @Override
        public void draw(NamespaceReportScreen screen, GuiGraphicsExtractor g, int x, int y, int right) {
            ThemeDraw.sectionTitle(g, screen.font, this.title, x, y, right - x);
        }
    }

    private record Row(List<FormattedCharSequence> lines, int color) {
        int height() {
            return this.lines.size() * Metrics.LINE_HEIGHT;
        }
    }

    private record Card(List<FormattedCharSequence> name, String id, List<Row> rows) implements Block {
        @Override
        public int height() {
            int height = this.name.size() * Metrics.LINE_HEIGHT + 2;
            for (Row row : this.rows) {
                height += row.height();
            }
            return 2 * CARD_PAD + height;
        }

        @Override
        public int gapBefore() {
            return CARD_GAP;
        }

        @Override
        public void draw(NamespaceReportScreen screen, GuiGraphicsExtractor g, int x, int y, int right) {
            ThemeDraw.framedBox(g, x, y, right, y + this.height(), Palette.PANEL, Palette.BORDER);
            int tx = x + CARD_PAD;
            int row = y + CARD_PAD;
            drawLines(g, screen.font, this.name, tx, row, Palette.ACCENT);
            int idX = tx + screen.font.width(this.name.getLast()) + 5;
            if (this.name.size() == 1 && !this.id.isEmpty() && idX < right - CARD_PAD - 10) {
                ThemeText.fitted(g, screen.font, this.id, idX, row, right - CARD_PAD - idX, Palette.TEXT_FAINT);
            }
            row += this.name.size() * Metrics.LINE_HEIGHT + 2;
            for (Row line : this.rows) {
                boolean entry = line.color() != Palette.TEXT_FAINT;
                drawLines(g, screen.font, line.lines(), entry ? tx + 6 : tx, row, line.color());
                row += line.height();
            }
        }
    }
}
