package de.lardtom.propwash.join.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.join.JoinReport.AddonLine;
import de.lardtom.propwash.join.JoinReport.PropwashLine;
import de.lardtom.propwash.join.JoinReport.Relevance;
import de.lardtom.propwash.join.JoinReport.UnknownParts;
import de.lardtom.propwash.join.JoinReport;
import de.lardtom.propwash.join.JoinText;
import de.lardtom.propwash.join.SafeText;
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
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class JoinMismatchScreen extends Screen implements ThemedScreen {
    private static final String KEY = JoinText.KEY + "screen.";
    private static final int MAX_WIDTH = 440;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int PAD = 10;
    private static final int CARD_PAD = 6;
    private static final int CARD_GAP = 6;
    private static final int SECTION_GAP = 8;
    private static final int MAX_REASON_LINES = 6;
    private static final int MAX_CARDS = 40;
    private static final int COPIED_MILLIS = 2000;

    private final Screen parent;
    private final JoinReport report;
    private final Component reason;
    private final boolean ours;
    private final List<Block> blocks = new ArrayList<>();
    private final List<LinkHit> links = new ArrayList<>();
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int contentHeight;
    private static final int BAR_GRAB = 3;
    private double scroll;
    private boolean draggingBar;
    private long copiedAt;
    private @Nullable FlatButton copyButton;

    public JoinMismatchScreen(Screen parent, JoinReport report, Component reason, boolean ours) {
        super(JoinText.title());
        this.parent = parent;
        this.report = report;
        this.reason = reason;
        this.ours = ours;
    }

    public JoinReport report() {
        return this.report;
    }

    public String detailsText() {
        return JoinText.details(this.report, this.reason);
    }

    public int linkCount() {
        int count = 0;
        for (Block block : this.blocks) {
            if (block instanceof Card card && card.link() != null) {
                count++;
            }
        }
        return count;
    }

    public boolean copyDetails() {
        this.minecraft.keyboardHandler.setClipboard(this.detailsText());
        this.copiedAt = Util.getMillis();
        return true;
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
        Component back = Component.translatableWithFallback(KEY + "back", "Back to server list");
        Component copy = Component.translatableWithFallback(KEY + "copy", "Copy details");
        int backWidth = Math.max(80, this.font.width(back) + 16);
        int copyWidth = Math.max(80, this.font.width(copy) + 16);
        this.addRenderableWidget(new FlatButton(this.windowRight - Metrics.PADDING - backWidth, buttonY, backWidth, Metrics.CONTROL_HEIGHT, back,
                FlatButton.Style.PRIMARY, this::onClose));
        FlatButton copyWidget = new FlatButton(this.windowLeft + Metrics.PADDING, buttonY, copyWidth, Metrics.CONTROL_HEIGHT, copy,
                FlatButton.Style.NORMAL, this::copyDetails);
        copyWidget.setTooltip(Tooltip.create(Component.translatableWithFallback(KEY + "copy.tooltip",
                "Copies this overview as text – for the server owner or a bug report.")));
        this.copyButton = this.addRenderableWidget(copyWidget);
    }

    private void buildBlocks() {
        this.blocks.clear();
        int width = this.textWidth();
        int cardText = width - 2 * CARD_PAD;
        this.blocks.add(new Paragraph(lines(JoinText.intro(this.ours || this.report.blocking() && !this.reasonShown()), width), Palette.TEXT_MUTED));
        if (this.reasonShown()) {
            List<FormattedCharSequence> reasonLines = lines(this.reason, width - 2 * CARD_PAD);
            if (reasonLines.size() > MAX_REASON_LINES) {
                ThemeStats.cut(this.reason.getString(), MAX_REASON_LINES + " von " + reasonLines.size() + " Zeilen");
                reasonLines = new ArrayList<>(reasonLines.subList(0, MAX_REASON_LINES));
                reasonLines.add(Component.literal("…").getVisualOrderText());
            }
            this.blocks.add(new Quote(Component.translatableWithFallback(KEY + "reason", "Original message"), reasonLines));
        }
        List<Card> actionable = new ArrayList<>();
        PropwashLine propwash = this.report.propwash();
        if (propwash != null) {
            actionable.add(card(Component.literal(JoinText.PROPWASH_NAME), JoinReport.PROPWASH_ID, JoinText.propwashStatus(propwash), Palette.ERROR,
                    Palette.ERROR_BG, JoinText.propwashVersions(propwash), JoinText.propwashAction(propwash), Palette.ACCENT, null, "", cardText));
        }
        for (AddonLine line : this.report.lines(true)) {
            actionable.add(this.addonCard(line, cardText));
        }
        List<Card> info = new ArrayList<>();
        for (UnknownParts group : this.report.unknown()) {
            MutableLines detail = new MutableLines();
            for (Component text : JoinText.unknownLines(group)) {
                detail.add(text);
            }
            if (group.blocking()) {
                actionable.add(card(JoinText.unknownTitle(group), "", Component.translatableWithFallback(KEY + "status.unknown", "Unknown parts"),
                        Palette.ERROR, Palette.ERROR_BG, detail.join(), JoinText.unknownAction(group), Palette.ACCENT, null, "", cardText));
            } else {
                info.add(card(JoinText.unknownTitle(group), "", Component.translatableWithFallback(KEY + "status.inactive", "Inactive here"),
                        Palette.TEXT_MUTED, Palette.PANEL_HEADER, detail.join(), JoinText.unknownAction(group), Palette.TEXT_FAINT, null, "", cardText));
            }
        }
        for (AddonLine line : this.report.lines(false)) {
            info.add(this.addonCard(line, cardText));
        }
        if (!actionable.isEmpty()) {
            this.blocks.add(new Section(Component.translatableWithFallback(KEY + "todo", "What to do")));
            this.blocks.addAll(limit(actionable));
        }
        if (!info.isEmpty()) {
            this.blocks.add(new Section(Component.translatableWithFallback(KEY + "other", "Also different (Propwash allows this)")));
            this.blocks.addAll(limit(info));
        }
        int height = 0;
        for (Block block : this.blocks) {
            height += block.height() + block.gapBefore();
        }
        this.contentHeight = height;
    }

    private boolean reasonShown() {
        return !this.ours && !this.reason.getString().isBlank();
    }

    private static List<Card> limit(List<Card> cards) {
        return cards.size() > MAX_CARDS ? cards.subList(0, MAX_CARDS) : cards;
    }

    private Card addonCard(AddonLine line, int width) {
        Relevance relevance = this.report.relevance(line);
        int color;
        int background;
        if (relevance == Relevance.INFO) {
            color = Palette.TEXT_MUTED;
            background = Palette.PANEL_HEADER;
        } else if (line.action() == JoinReport.Action.INSTALL || line.action() == JoinReport.Action.REMOVE || line.partsDiffer()) {
            color = Palette.ERROR;
            background = Palette.ERROR_BG;
        } else {
            color = Palette.WARNING;
            background = Palette.WARNING_BG;
        }
        return card(Component.literal(line.name()), line.modId(), JoinText.status(line, relevance), color, background, JoinText.versions(line),
                JoinText.action(line, relevance), relevance == Relevance.INFO ? Palette.TEXT_FAINT : Palette.ACCENT, JoinText.parts(line),
                line.homepage(), width);
    }

    private Card card(Component name, String id, Component status, int statusColor, int statusBackground, Component versions, Component action,
                      int actionColor, @Nullable Component parts, String homepage, int width) {
        int badgeWidth = this.font.width(status) + 8;
        List<FormattedCharSequence> versionLines = lines(versions, width);
        List<FormattedCharSequence> actionLines = lines(action, width);
        List<FormattedCharSequence> partLines = parts == null ? List.of() : lines(parts, width);
        String url = SafeText.url(homepage);
        Component link = url.isEmpty() ? null : Component.literal("↗ " + SafeText.shortUrl(url));
        int nameRoom = Math.max(20, width - badgeWidth - 6);
        int idWidth = id.isEmpty() ? 0 : this.font.width(id) + 5;
        boolean oneLine = this.font.width(name) + idWidth <= nameRoom;
        List<FormattedCharSequence> nameLines = oneLine ? List.of(name.getVisualOrderText()) : this.font.split(name, nameRoom);
        return new Card(name, id, status, statusColor, statusBackground, badgeWidth, versionLines, actionLines, actionColor, partLines,
                link, url, nameLines, !oneLine && !id.isEmpty());
    }

    private List<FormattedCharSequence> lines(Component text, int width) {
        return ThemeText.balanced(this.font, text, Math.max(20, width));
    }

    private int bodyTop() {
        return this.windowTop + HEADER;
    }

    private int bodyBottom() {
        return this.windowBottom - FOOTER;
    }

    private double maxScroll() {
        return Math.max(0, this.contentHeight + 2 * PAD - (this.bodyBottom() - this.bodyTop()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.maxScroll() > 0) {
            this.scroll = Math.clamp(this.scroll - scrollY * 3 * Metrics.LINE_HEIGHT, 0.0, this.maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public List<ScreenRectangle> linkAreas() {
        List<ScreenRectangle> out = new ArrayList<>();
        for (LinkHit hit : this.links) {
            out.add(new ScreenRectangle(hit.x0(), hit.y0(), hit.x1() - hit.x0(), hit.y1() - hit.y0()));
        }
        return out;
    }

    public double scrollOffset() {
        return this.scroll;
    }

    public double scrollLimit() {
        return this.maxScroll();
    }

    public ScreenRectangle scrollBarArea() {
        int left = this.windowRight - PAD / 2 - Metrics.SCROLLBAR_WIDTH;
        return new ScreenRectangle(left, this.bodyTop() + 2, Metrics.SCROLLBAR_WIDTH, this.scrollView() - 4);
    }

    private int scrollContent() {
        return this.contentHeight + 2 * PAD;
    }

    private int scrollView() {
        return this.bodyBottom() - this.bodyTop();
    }

    private void dragTo(double mouseY) {
        this.scroll = Math.clamp(ScrollMath.dragOffset(mouseY - (this.bodyTop() + 2), this.scrollContent(), this.scrollView() - 4), 0.0, this.maxScroll());
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
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.maxScroll() > 0 && event.x() >= this.windowRight - PAD / 2 - Metrics.SCROLLBAR_WIDTH - BAR_GRAB
                && event.x() < this.windowRight && event.y() >= this.bodyTop() && event.y() < this.bodyBottom()) {
            this.draggingBar = true;
            this.dragTo(event.y());
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (LinkHit hit : this.links) {
                if (event.x() >= hit.x0() && event.x() < hit.x1() && event.y() >= hit.y0() && event.y() < hit.y1()) {
                    this.openLink(hit.url());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    public boolean openLink(String url) {
        String safe = SafeText.url(url);
        if (safe.isEmpty()) {
            return false;
        }
        ConfirmLinkScreen.confirmLinkNow(this, URI.create(safe), false);
        return true;
    }

    @Override
    public void tick() {
        if (this.copyButton != null) {
            boolean copied = this.copiedAt > 0 && Util.getMillis() - this.copiedAt < COPIED_MILLIS;
            this.copyButton.setMessage(copied ? Component.translatableWithFallback(KEY + "copied", "Copied")
                    : Component.translatableWithFallback(KEY + "copy", "Copy details"));
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
        this.links.clear();
        g.enableScissor(this.windowLeft, top, this.windowRight, bottom);
        int y = top + PAD - (int) Math.round(this.scroll);
        for (Block block : this.blocks) {
            y += block.gapBefore();
            if (y + block.height() >= top && y <= bottom) {
                block.draw(this, g, x, y, right, mouseX, mouseY, top, bottom);
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

    private static void drawLines(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, List<FormattedCharSequence> lines, int x, int y, int color) {
        int row = y;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, x, row, color, false);
            row += Metrics.LINE_HEIGHT;
        }
    }

    private sealed interface Block permits Paragraph, Quote, Section, Card {
        int height();

        int gapBefore();

        void draw(JoinMismatchScreen screen, GuiGraphicsExtractor g, int x, int y, int right, int mouseX, int mouseY, int clipTop, int clipBottom);
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
        public void draw(JoinMismatchScreen screen, GuiGraphicsExtractor g, int x, int y, int right, int mouseX, int mouseY, int clipTop, int clipBottom) {
            drawLines(g, screen.font, this.lines, x, y, this.color);
        }
    }

    private record Quote(Component label, List<FormattedCharSequence> lines) implements Block {
        @Override
        public int height() {
            return 2 * CARD_PAD + Metrics.LINE_HEIGHT + this.lines.size() * Metrics.LINE_HEIGHT;
        }

        @Override
        public int gapBefore() {
            return SECTION_GAP;
        }

        @Override
        public void draw(JoinMismatchScreen screen, GuiGraphicsExtractor g, int x, int y, int right, int mouseX, int mouseY, int clipTop, int clipBottom) {
            ThemeDraw.framedBox(g, x, y, right, y + this.height(), Palette.FIELD, Palette.BORDER);
            ThemeText.text(g, screen.font, this.label, x + CARD_PAD, y + CARD_PAD, Palette.TEXT_FAINT);
            drawLines(g, screen.font, this.lines, x + CARD_PAD, y + CARD_PAD + Metrics.LINE_HEIGHT, Palette.TEXT_MUTED);
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
        public void draw(JoinMismatchScreen screen, GuiGraphicsExtractor g, int x, int y, int right, int mouseX, int mouseY, int clipTop, int clipBottom) {
            ThemeDraw.sectionTitle(g, screen.font, this.title, x, y, right - x);
        }
    }

    private record Card(Component name, String id, Component status, int statusColor, int statusBackground, int badgeWidth,
                        List<FormattedCharSequence> versions, List<FormattedCharSequence> action, int actionColor,
                        List<FormattedCharSequence> parts, @Nullable Component link, String url, List<FormattedCharSequence> nameLines,
                        boolean idBelow) implements Block {
        @Override
        public int height() {
            int rows = this.nameLines.size() + (this.idBelow ? 1 : 0) + this.versions.size() + this.action.size() + this.parts.size()
                    + (this.link == null ? 0 : 1);
            return 2 * CARD_PAD + rows * Metrics.LINE_HEIGHT + 2;
        }

        @Override
        public int gapBefore() {
            return CARD_GAP;
        }

        @Override
        public void draw(JoinMismatchScreen screen, GuiGraphicsExtractor g, int x, int y, int right, int mouseX, int mouseY, int clipTop, int clipBottom) {
            ThemeDraw.framedBox(g, x, y, right, y + this.height(), Palette.PANEL, Palette.BORDER);
            int tx = x + CARD_PAD;
            int row = y + CARD_PAD;
            int badgeX = right - CARD_PAD - this.badgeWidth;
            ThemeDraw.badge(g, screen.font, this.status, badgeX, row - 2, row + Metrics.TEXT_HEIGHT + 2, this.statusBackground, this.statusColor);
            if (this.nameLines.size() == 1 && !this.idBelow) {
                int room = badgeX - tx - 6;
                int idRoom = this.id.isEmpty() ? 0 : Math.min(screen.font.width(this.id) + 5, room / 2);
                ThemeText.fitted(g, screen.font, this.name, tx, row, room - idRoom, Palette.TEXT);
                int idX = tx + ThemeText.fittedWidth(screen.font, this.name, room - idRoom) + 5;
                if (!this.id.isEmpty() && idX < badgeX - 6) {
                    ThemeText.fitted(g, screen.font, this.id, idX, row, badgeX - 6 - idX, Palette.TEXT_FAINT);
                }
                row += Metrics.LINE_HEIGHT;
            } else {
                drawLines(g, screen.font, this.nameLines, tx, row, Palette.TEXT);
                row += this.nameLines.size() * Metrics.LINE_HEIGHT;
                if (this.idBelow) {
                    ThemeText.fitted(g, screen.font, this.id, tx, row, right - CARD_PAD - tx, Palette.TEXT_FAINT);
                    row += Metrics.LINE_HEIGHT;
                }
            }
            row += 2;
            drawLines(g, screen.font, this.versions, tx, row, Palette.TEXT_MUTED);
            row += this.versions.size() * Metrics.LINE_HEIGHT;
            drawLines(g, screen.font, this.action, tx, row, this.actionColor);
            row += this.action.size() * Metrics.LINE_HEIGHT;
            drawLines(g, screen.font, this.parts, tx, row, Palette.TEXT_FAINT);
            row += this.parts.size() * Metrics.LINE_HEIGHT;
            if (this.link != null) {
                int w = screen.font.width(this.link);
                boolean hover = mouseX >= tx && mouseX < tx + w && mouseY >= row - 1 && mouseY < row + Metrics.TEXT_HEIGHT + 1
                        && mouseY >= clipTop && mouseY < clipBottom;
                Component shown = hover ? this.link.copy().withStyle(Style.EMPTY.withUnderlined(true)) : this.link;
                ThemeText.text(g, screen.font, shown, tx, row, Palette.INFO);
                int y0 = Math.max(row - 1, clipTop);
                int y1 = Math.min(row + Metrics.TEXT_HEIGHT + 1, clipBottom);
                if (y1 > y0) {
                    screen.links.add(new LinkHit(tx, y0, tx + w, y1, this.url));
                }
            }
        }
    }

    private record LinkHit(int x0, int y0, int x1, int y1, String url) {
    }

    private static final class MutableLines {
        private final List<Component> parts = new ArrayList<>();

        void add(Component part) {
            this.parts.add(part);
        }

        Component join() {
            net.minecraft.network.chat.MutableComponent out = Component.empty();
            for (int i = 0; i < this.parts.size(); i++) {
                if (i > 0) {
                    out.append(" · ");
                }
                out.append(this.parts.get(i));
            }
            return out;
        }
    }
}
