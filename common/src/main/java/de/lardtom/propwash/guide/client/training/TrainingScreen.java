package de.lardtom.propwash.guide.client.training;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.GuideKeys;
import de.lardtom.propwash.guide.client.GuideOpener;
import de.lardtom.propwash.guide.client.render.SceneAssets;
import de.lardtom.propwash.guide.training.Chapter;
import de.lardtom.propwash.guide.training.Curriculum;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.training.StarRule;
import de.lardtom.propwash.guide.training.TrainingProgress;
import de.lardtom.propwash.guide.training.TrainingScale;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.ui.ChromeLayout;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ScrollArea;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TrainingScreen extends Screen implements ThemedScreen {
    private static final int MAX_WIDTH = 600;
    private static final int MAX_HEIGHT = 380;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int SUBHEADER = 20;
    private static final int CHAPTER_ROW = 30;
    private static final int LESSON_ROW = 22;
    private static final int ICON = 16;
    private static final int NARROW_WIDTH = 520;

    private static @Nullable TrainingScreen active;

    private final @Nullable Screen parent;
    private Chapter chapter;
    private Lesson selected;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int chaptersRight;
    private int lessonsRight;
    private int bodyTop;
    private int bodyBottom;
    private @Nullable FlatButton fly;
    private long frames;
    private int footer = FOOTER;
    private int subHeader = SUBHEADER;
    private int tipLimit;
    private int actionTop;
    private boolean narrow;
    private boolean lessonsStacked;
    private boolean starsInAction;
    private final ScrollArea chaptersArea = new ScrollArea();
    private final ScrollArea lessonsArea = new ScrollArea();
    private final ScrollArea detailArea = new ScrollArea();
    private final List<Entry> chapterEntries = new ArrayList<>();
    private final List<Entry> lessonEntries = new ArrayList<>();

    private record Entry(@Nullable Chapter chapter, @Nullable Lesson lesson, int x0, int x1, int top, int height, List<FormattedCharSequence> lines) {
        boolean hit(double x, double y) {
            return x >= this.x0 && x < this.x1 && y >= this.top && y < this.top + this.height;
        }
    }

    public TrainingScreen(@Nullable Screen parent) {
        this(parent, null);
    }

    public TrainingScreen(@Nullable Screen parent, @Nullable Lesson focus) {
        super(Component.translatable("propwash.guide.training.title"));
        this.parent = parent;
        TrainingProgress progress = TrainingStore.progress();
        Lesson start = focus;
        if (start == null) {
            Lesson current = TrainingSession.INSTANCE.lesson();
            if (current != null) {
                start = Curriculum.unlocked(current, progress) && progress.stars(current.id()) > 0
                        ? orElse(Curriculum.nextOpen(current, progress), current) : current;
            }
        }
        if (start == null) {
            start = orElse(Curriculum.firstOpen(progress), Curriculum.lessons().getFirst());
        }
        this.selected = start;
        this.chapter = orElse(Curriculum.chapter(start.chapter()), Curriculum.chapters().getFirst());
    }

    private static <T> T orElse(@Nullable T value, T fallback) {
        return value == null ? fallback : value;
    }

    public static @Nullable TrainingScreen active() {
        return active;
    }

    public long frames() {
        return this.frames;
    }

    public Lesson selected() {
        return this.selected;
    }

    public Chapter chapter() {
        return this.chapter;
    }

    public @Nullable ScreenRectangle showRow(Chapter wanted) {
        for (Entry entry : this.chapterEntries) {
            if (entry.chapter() == wanted) {
                return this.shown(this.chaptersArea, entry);
            }
        }
        return null;
    }

    public @Nullable ScreenRectangle showRow(Lesson wanted) {
        for (Entry entry : this.chapterEntries) {
            if (entry.lesson() == wanted) {
                return this.shown(this.chaptersArea, entry);
            }
        }
        for (Entry entry : this.lessonEntries) {
            if (entry.lesson() == wanted) {
                return this.shown(this.lessonsArea, entry);
            }
        }
        return null;
    }

    private ScreenRectangle shown(ScrollArea area, Entry entry) {
        area.reveal(entry.top(), entry.height());
        return new ScreenRectangle(entry.x0(), entry.top() - area.offset(), entry.x1() - entry.x0(), entry.height());
    }

    public void select(Lesson lesson) {
        this.selected = lesson;
        this.chapter = orElse(Curriculum.chapter(lesson.chapter()), this.chapter);
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        active = this;
        int w = Math.min(this.width - 12, MAX_WIDTH);
        int h = Math.min(this.height - 12, MAX_HEIGHT);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        Component closeLabel = Component.translatable("propwash.guide.training.close");
        int closeWidth = Math.max(70, this.font.width(closeLabel) + 16);
        this.tipLimit = Math.max(40, w - closeWidth - 2 * Metrics.PADDING - 16);
        this.footer = ChromeLayout.footerHeight(ThemeDraw.footerLines(this.font, this.tip(), this.tipLimit));
        Component scenes = Component.translatable("propwash.guide.training.scenes");
        int scenesWidth = this.font.width(scenes) + 14;
        boolean stacked = this.font.width(this.subTitle()) + 8 + Metrics.GAP + scenesWidth + Metrics.PADDING > w;
        this.subHeader = stacked ? SUBHEADER + Metrics.CONTROL_HEIGHT : SUBHEADER;
        this.bodyTop = this.windowTop + HEADER + this.subHeader + Metrics.PADDING;
        this.bodyBottom = this.windowBottom - this.footer - Metrics.PADDING;
        int inner = w - 2 * Metrics.PADDING;
        this.narrow = inner < NARROW_WIDTH;
        if (this.narrow) {
            this.chaptersRight = this.windowLeft + Metrics.PADDING + Math.round(inner * 0.46F);
            this.lessonsRight = this.chaptersRight;
        } else {
            this.chaptersRight = this.windowLeft + Metrics.PADDING + Math.round(inner * 0.27F);
            this.lessonsRight = this.chaptersRight + Metrics.GAP + Math.round(inner * 0.33F);
        }
        int buttonY = this.windowBottom - this.footer + (this.footer - Metrics.CONTROL_HEIGHT) / 2;
        this.addRenderableWidget(new FlatButton(this.windowRight - Metrics.PADDING - closeWidth, buttonY, closeWidth, Metrics.CONTROL_HEIGHT,
                closeLabel, FlatButton.Style.NORMAL, this::onClose));
        int subY = this.windowTop + HEADER + this.subHeader - SUBHEADER + (SUBHEADER - Metrics.CONTROL_HEIGHT) / 2 + 1;
        this.addRenderableWidget(new FlatButton(this.windowRight - Metrics.PADDING - scenesWidth, subY, scenesWidth, Metrics.CONTROL_HEIGHT,
                scenes, FlatButton.Style.NORMAL, () -> GuideOpener.openIndex(this.parent, ItemStack.EMPTY)));
        int detailLeft = this.lessonsRight + Metrics.GAP;
        int detailRight = this.windowRight - Metrics.PADDING;
        boolean unlocked = Curriculum.unlocked(this.selected, TrainingStore.progress());
        Lesson running = TrainingSession.INSTANCE.lesson();
        Component flyLabel = Component.translatable(running == this.selected ? "propwash.guide.training.fly_again" : "propwash.guide.training.fly");
        int room = detailRight - detailLeft - 8;
        int flyWidth = Math.min(room, Math.max(80, this.font.width(flyLabel) + 20));
        Component stop = Component.translatable("propwash.guide.training.stop");
        int stopWidth = running != null ? this.font.width(stop) + 16 : 0;
        boolean oneRow = stopWidth == 0 || flyWidth + Metrics.GAP + stopWidth <= room;
        int actionY = this.bodyBottom - Metrics.CONTROL_HEIGHT - 2;
        this.actionTop = (oneRow ? actionY : actionY - Metrics.ROW_HEIGHT) - 4;
        int starsWidth = this.font.width(TrainingHud.stars(3)) + 8;
        this.starsInAction = flyWidth + (oneRow && stopWidth > 0 ? Metrics.GAP + stopWidth : 0) + starsWidth <= room;
        this.fly = new FlatButton(detailRight - flyWidth, actionY, flyWidth, Metrics.CONTROL_HEIGHT, flyLabel, FlatButton.Style.PRIMARY, this::fly);
        this.fly.active = unlocked;
        this.addRenderableWidget(this.fly);
        if (running != null) {
            int stopX = oneRow ? detailRight - flyWidth - Metrics.GAP - stopWidth : detailRight - Math.min(room, stopWidth);
            this.addRenderableWidget(new FlatButton(stopX, oneRow ? actionY : actionY - Metrics.ROW_HEIGHT, Math.min(room, stopWidth),
                    Metrics.CONTROL_HEIGHT, stop, FlatButton.Style.DANGER, this::stop));
        }
        int listLeft = this.windowLeft + Metrics.PADDING;
        this.chaptersArea.view(listLeft, this.bodyTop, this.chaptersRight, this.bodyBottom);
        int lessonTop = this.bodyTop + this.lessonHeading().size() * Metrics.LINE_HEIGHT + 8;
        if (this.narrow) {
            this.lessonsArea.view(0, 0, 0, 0);
        } else {
            this.lessonsArea.view(this.chaptersRight + Metrics.GAP, lessonTop, this.lessonsRight, this.bodyBottom);
        }
        this.detailArea.view(detailLeft + 1, this.bodyTop + 1, detailRight - 1, this.actionTop);
        this.buildEntries();
    }

    private Component tip() {
        Lesson running = TrainingSession.INSTANCE.lesson();
        return running != null
                ? Component.translatable("propwash.guide.training.active_lesson", running.code(), Component.translatable(running.titleKey()))
                : Component.translatable("propwash.guide.training.tip", GuideKeys.label());
    }

    private Component subTitle() {
        return Component.translatable("propwash.guide.training.subtitle", Curriculum.lessons().size(), Curriculum.chapters().size(),
                Curriculum.totalStars(TrainingStore.progress()), Curriculum.maxStars());
    }

    private List<FormattedCharSequence> lessonHeading() {
        Component heading = Component.translatable("propwash.guide.training.chapter_label", this.chapter.number())
                .append(" · ").append(Component.translatable(this.chapter.titleKey()));
        return this.font.split(heading, Math.max(20, this.lessonsRight - this.chaptersRight - Metrics.GAP));
    }

    private void buildEntries() {
        this.fillChapters(0);
        if (bottomOf(this.chapterEntries) > this.chaptersArea.view().bottom()) {
            this.fillChapters(ScrollArea.GUTTER);
        }
        this.lessonEntries.clear();
        if (!this.narrow) {
            int left = this.lessonsArea.left();
            int right = this.lessonsArea.view().right();
            if (this.lessonRows(this.lessonEntries, left, right, this.lessonsArea.top()) > this.lessonsArea.view().bottom()) {
                this.lessonEntries.clear();
                this.lessonRows(this.lessonEntries, left, right - ScrollArea.GUTTER, this.lessonsArea.top());
            }
        }
    }

    private static int bottomOf(List<Entry> entries) {
        return entries.isEmpty() ? Integer.MIN_VALUE : entries.getLast().top() + entries.getLast().height();
    }

    private void fillChapters(int gutter) {
        this.chapterEntries.clear();
        int left = this.chaptersArea.left();
        int right = this.chaptersArea.view().right() - gutter;
        int y = this.chaptersArea.top();
        for (Chapter c : Curriculum.chapters()) {
            int textWidth = Math.max(20, right - 4 - (left + 6 + ICON + 7));
            List<FormattedCharSequence> lines = this.font.split(Component.literal(c.number() + " · ").append(Component.translatable(c.titleKey())), textWidth);
            int height = Math.max(CHAPTER_ROW, 5 + lines.size() * Metrics.LINE_HEIGHT + 2 + ThemeText.smallLineHeight() + 5);
            this.chapterEntries.add(new Entry(c, null, left, right, y, height, lines));
            y += height + 2;
            if (this.narrow && c == this.chapter) {
                y = this.lessonRows(this.chapterEntries, left + 8, right, y);
            }
        }
    }

    private int lessonRows(List<Entry> out, int left, int right, int top) {
        int y = top;
        Component locked = Component.translatable("propwash.guide.training.locked");
        int statusWidth = Math.max(this.font.width(TrainingHud.stars(3)), this.font.width(locked));
        int nameX = left + 7 + this.font.width("0.0") + 6;
        int nameWidth = Math.max(20, right - 8 - statusWidth - 6 - nameX);
        boolean stacked = false;
        for (Lesson lesson : this.chapter.lessons()) {
            stacked |= ThemeText.breaksWord(this.font, Component.translatable(lesson.titleKey()), nameWidth);
        }
        this.lessonsStacked = stacked;
        if (stacked) {
            nameWidth = Math.max(20, right - 7 - nameX);
        }
        for (Lesson lesson : this.chapter.lessons()) {
            List<FormattedCharSequence> lines = this.font.split(Component.translatable(lesson.titleKey()), nameWidth);
            int height = Math.max(LESSON_ROW, (lines.size() + (stacked ? 1 : 0)) * Metrics.LINE_HEIGHT + 12);
            out.add(new Entry(null, lesson, left, right, y, height, lines));
            y += height + 2;
        }
        return y;
    }

    private void fly() {
        if (!Curriculum.unlocked(this.selected, TrainingStore.progress())) {
            return;
        }
        TrainingSession.INSTANCE.select(this.selected);
        this.minecraft.gui.setScreen(null);
    }

    private void stop() {
        TrainingSession.INSTANCE.stop();
        this.rebuildWidgets();
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
    public boolean keyPressed(KeyEvent event) {
        if (GuideKeys.matchesKey(event.key())) {
            this.onClose();
            return true;
        }
        if (super.keyPressed(event)) {
            return true;
        }
        return this.detailArea.key(event.key()) || this.lessonsArea.key(event.key()) || this.chaptersArea.key(event.key());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        if (this.chaptersArea.mouseClicked(mx, my) || this.lessonsArea.mouseClicked(mx, my) || this.detailArea.mouseClicked(mx, my)) {
            return true;
        }
        Entry entry = this.entryAt(this.chaptersArea, this.chapterEntries, mx, my);
        if (entry == null) {
            entry = this.entryAt(this.lessonsArea, this.lessonEntries, mx, my);
        }
        if (entry == null) {
            return false;
        }
        if (entry.chapter() != null) {
            Chapter clicked = entry.chapter();
            if (clicked != this.chapter) {
                this.chapter = clicked;
                TrainingProgress progress = TrainingStore.progress();
                Lesson pick = clicked.lessons().getFirst();
                for (Lesson lesson : clicked.lessons()) {
                    if (Curriculum.unlocked(lesson, progress)) {
                        pick = lesson;
                        if (progress.stars(lesson.id()) <= 0) {
                            break;
                        }
                    }
                }
                this.selected = pick;
                this.lessonsArea.reset();
                this.detailArea.reset();
                this.rebuildWidgets();
            }
            return true;
        }
        Lesson clicked = entry.lesson();
        if (clicked != null && clicked != this.selected) {
            this.selected = clicked;
            this.detailArea.reset();
            this.rebuildWidgets();
        } else if (doubleClick) {
            this.fly();
        }
        return true;
    }

    private @Nullable Entry entryAt(ScrollArea area, List<Entry> entries, double mx, double my) {
        if (!area.contains(mx, my)) {
            return null;
        }
        int y = area.contentY(my);
        for (Entry entry : entries) {
            if (entry.hit(mx, y)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.chaptersArea.wheel(mouseX, mouseY, scrollY) || this.lessonsArea.wheel(mouseX, mouseY, scrollY)
                || this.detailArea.wheel(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.chaptersArea.mouseDragged(event.y()) || this.lessonsArea.mouseDragged(event.y()) || this.detailArea.mouseDragged(event.y())
                || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.chaptersArea.mouseReleased();
        this.lessonsArea.mouseReleased();
        this.detailArea.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.frames++;
        TrainingProgress progress = TrainingStore.progress();
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, HEADER);
        int textY = Metrics.textY(this.windowTop, HEADER - 1);
        int left = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, left - 5, this.windowTop + 5, this.windowTop + HEADER - 6);
        Component title = Component.translatable("propwash.guide.title");
        ThemeText.text(g, this.font, title, left, textY, Palette.TEXT_MUTED);
        int after = left + this.font.width(title) + 10;
        ThemeDraw.verticalSeparator(g, after - 5, this.windowTop + 5, this.windowTop + HEADER - 6);
        ThemeText.text(g, this.font, this.title, after, textY, Palette.TEXT);
        int subTop = this.windowTop + HEADER;
        ThemeDraw.subHeaderBar(g, this.windowLeft, subTop, this.windowRight, this.subHeader);
        boolean stacked = this.subHeader > SUBHEADER;
        ThemeText.fitted(g, this.font, this.subTitle(), this.windowLeft + 8, stacked ? subTop + 4 : Metrics.textY(subTop, SUBHEADER),
                stacked ? this.windowRight - this.windowLeft - 16 : this.windowRight - this.windowLeft - 120, Palette.TEXT_MUTED);
        this.chapters(g, progress, mouseX, mouseY);
        this.lessons(g, progress, mouseX, mouseY);
        this.details(g, progress);
        int footerTop = this.windowBottom - this.footer;
        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        ThemeDraw.footerText(g, this.font, this.tip(), this.windowLeft + 8, footerTop, this.footer, this.tipLimit,
                TrainingSession.INSTANCE.lesson() != null ? Palette.ACCENT : Palette.TEXT_FAINT);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private void chapters(GuiGraphicsExtractor g, TrainingProgress progress, int mouseX, int mouseY) {
        ScrollArea area = this.chaptersArea;
        int hoverY = area.hoverY(mouseX, mouseY);
        int bottom = area.top();
        area.begin(g);
        for (Entry entry : this.chapterEntries) {
            boolean hovered = hoverY != Integer.MIN_VALUE && entry.hit(mouseX, hoverY);
            if (entry.chapter() != null) {
                this.chapterRow(g, progress, entry, hovered);
            } else {
                this.lessonRow(g, progress, entry, hovered);
            }
            bottom = entry.top() + entry.height();
        }
        area.end(g, bottom);
    }

    private void chapterRow(GuiGraphicsExtractor g, TrainingProgress progress, Entry entry, boolean hovered) {
        Chapter c = entry.chapter();
        if (c == null) {
            return;
        }
        int left = entry.x0();
        int right = entry.x1();
        int top = entry.top();
        int bottom = top + entry.height();
        boolean open = Curriculum.chapterUnlocked(c, progress);
        ThemeDraw.framedBox(g, left, top, right, bottom, Palette.PANEL, Palette.BORDER);
        ThemeDraw.selection(g, left + 1, top + 1, right - 1, bottom - 1, c == this.chapter, hovered);
        int iconX = left + 6;
        int iconY = top + (CHAPTER_ROW - ICON) / 2;
        ThemeDraw.iconTile(g, iconX - 2, iconY - 2, iconX + ICON + 2, iconY + ICON + 2);
        g.item(SceneAssets.item(c.icon()), iconX, iconY);
        int tx = iconX + ICON + 7;
        int color = open ? (c == this.chapter ? Palette.ACCENT : Palette.TEXT) : Palette.TEXT_DISABLED;
        int ty = top + 5;
        for (FormattedCharSequence line : entry.lines()) {
            ThemeText.text(g, this.font, line, tx, ty, color);
            ty += Metrics.LINE_HEIGHT;
        }
        String stars = open ? TrainingHud.STAR + " " + Curriculum.chapterStars(c, progress) + "/" + c.maxStars()
                : Component.translatable("propwash.guide.training.locked").getString();
        ThemeText.small(g, this.font, stars, tx, ty + 2, open ? Palette.TEXT_MUTED : Palette.TEXT_DISABLED);
        if (open && Curriculum.chapterComplete(c, progress)) {
            ThemeDraw.lamp(g, right - 10, top + 5, 5, 5, Palette.SUCCESS);
        }
    }

    private void lessons(GuiGraphicsExtractor g, TrainingProgress progress, int mouseX, int mouseY) {
        if (this.narrow) {
            return;
        }
        int left = this.chaptersRight + Metrics.GAP;
        int headingY = this.bodyTop;
        for (FormattedCharSequence line : this.lessonHeading()) {
            ThemeText.text(g, this.font, line, left, headingY, Palette.ACCENT);
            headingY += Metrics.LINE_HEIGHT;
        }
        ScrollArea area = this.lessonsArea;
        int hoverY = area.hoverY(mouseX, mouseY);
        int bottom = area.top();
        area.begin(g);
        for (Entry entry : this.lessonEntries) {
            this.lessonRow(g, progress, entry, hoverY != Integer.MIN_VALUE && entry.hit(mouseX, hoverY));
            bottom = entry.top() + entry.height();
        }
        area.end(g, bottom);
    }

    private void lessonRow(GuiGraphicsExtractor g, TrainingProgress progress, Entry entry, boolean hovered) {
        Lesson lesson = entry.lesson();
        if (lesson == null) {
            return;
        }
        int left = entry.x0();
        int right = entry.x1();
        int top = entry.top();
        int bottom = top + entry.height();
        boolean open = Curriculum.unlocked(lesson, progress);
        ThemeDraw.framedBox(g, left, top, right, bottom, Palette.PANEL, Palette.BORDER);
        ThemeDraw.selection(g, left + 1, top + 1, right - 1, bottom - 1, lesson == this.selected, hovered);
        int rows = entry.lines().size() + (this.lessonsStacked ? 1 : 0);
        int textY = top + (entry.height() - rows * Metrics.LINE_HEIGHT + 2) / 2;
        ThemeText.text(g, this.font, lesson.code(), left + 7, textY, open ? Palette.TEXT_MUTED : Palette.TEXT_DISABLED);
        int nameX = left + 7 + this.font.width("0.0") + 6;
        int color = !open ? Palette.TEXT_DISABLED : lesson == this.selected ? Palette.ACCENT : Palette.TEXT;
        int ty = textY;
        for (FormattedCharSequence line : entry.lines()) {
            ThemeText.text(g, this.font, line, nameX, ty, color);
            ty += Metrics.LINE_HEIGHT;
        }
        int stars = progress.stars(lesson.id());
        ThemeText.textRight(g, this.font, open ? TrainingHud.stars(stars) : Component.translatable("propwash.guide.training.locked").getString(),
                right - 7, this.lessonsStacked ? ty : textY, !open ? Palette.TEXT_DISABLED : stars > 0 ? Palette.ACCENT : Palette.TEXT_FAINT);
        if (lesson == TrainingSession.INSTANCE.lesson()) {
            g.fill(right - 2, top + 3, right, bottom - 3, Palette.SUCCESS);
        }
    }

    private void details(GuiGraphicsExtractor g, TrainingProgress progress) {
        int left = this.lessonsRight + Metrics.GAP;
        int right = this.windowRight - Metrics.PADDING;
        int width = right - left;
        Lesson lesson = this.selected;
        ThemeDraw.panel(g, left, this.bodyTop, right, this.bodyBottom);
        int x = left + 7;
        int inner = Math.max(20, width - 14);
        int y = this.bodyTop + 6;
        this.detailArea.begin(g);
        Component title = Component.translatable("propwash.guide.training.lesson_label", lesson.code()).append(" · ")
                .append(Component.translatable(lesson.titleKey())).withStyle(ChatFormatting.BOLD);
        y += ThemeText.wrapped(g, this.font, title, x, y, inner, Palette.TEXT) + 4;
        y += ThemeText.wrapped(g, this.font, Component.translatable(lesson.descriptionKey()), x, y, inner, Palette.TEXT_MUTED) + 5;
        String mouseTip = InputConfig.get().mouseSelected() ? lesson.tipKey() + "_mouse" : "";
        boolean mouse = !mouseTip.isEmpty() && Language.getInstance().has(mouseTip);
        ThemeText.text(g, this.font, Component.translatable(mouse ? "propwash.guide.training.tip_label_mouse" : "propwash.guide.training.tip_label")
                .withStyle(ChatFormatting.BOLD), x, y, Palette.ACCENT);
        y += Metrics.LINE_HEIGHT;
        y += ThemeText.wrapped(g, this.font, Component.translatable(mouse ? mouseTip : lesson.tipKey()), x, y, inner, Palette.TEXT_MUTED) + 5;
        TrainingScale scale = this.scale();
        StarRule rule = lesson.stars();
        boolean distance = rule.metric() == StarRule.Metric.PAD_DISTANCE;
        String[] values = distance
                ? new String[]{TrainingHud.decimal(rule.threeFor(scale)), TrainingHud.decimal(rule.twoFor(scale)), ""}
                : new String[]{TrainingSession.time(rule.threeFor(scale)), TrainingSession.time(rule.twoFor(scale)),
                TrainingSession.time(lesson.timeLimit() * scale.time())};
        int starsWidth = this.font.width(TrainingHud.STAR.repeat(3)) + 6;
        for (int stars = 3; stars >= 1; stars--) {
            ThemeText.text(g, this.font, TrainingHud.STAR.repeat(stars), x, y, Palette.ACCENT);
            Component line = Component.translatable("propwash.guide.training.stars." + (distance ? "distance" : "time") + stars, values[3 - stars]);
            y += ThemeText.wrapped(g, this.font, line, x + starsWidth, y, Math.max(20, inner - starsWidth), Palette.TEXT);
        }
        y += 3;
        if (scale != TrainingScale.FULL) {
            y += ThemeText.wrapped(g, this.font, Component.translatable("propwash.guide.training.scale_note",
                    String.format(Locale.ROOT, "%.1f", scale.geometry()), String.format(Locale.ROOT, "%.2f", scale.time())), x, y, inner,
                    Palette.TEXT_FAINT) + 3;
        }
        TrainingProgress.LessonRecord record = progress.get(lesson.id());
        TrainingProgress.Best best = progress.best(lesson.id(), scale);
        List<Component> facts = new ArrayList<>();
        if (best != null && best.time >= 0.0) {
            facts.add(rule.metric() == StarRule.Metric.PAD_DISTANCE
                    ? Component.translatable("propwash.guide.training.best_distance", TrainingHud.decimal(best.metric))
                    : Component.translatable("propwash.guide.training.best_time", TrainingSession.time(best.time)));
        }
        if (record != null && record.attempts > 0) {
            facts.add(Component.translatable("propwash.guide.training.attempts", record.attempts, record.completions));
        } else if (best == null) {
            facts.add(Component.translatable("propwash.guide.training.not_flown"));
        }
        for (Component fact : facts) {
            y += ThemeText.wrapped(g, this.font, fact, x, y, inner, Palette.TEXT_FAINT);
        }
        Component status;
        int statusColor;
        if (!Curriculum.unlocked(lesson, progress)) {
            status = this.lockReason(lesson, progress);
            statusColor = Palette.WARNING;
        } else {
            status = Component.translatable("propwash.guide.training.how");
            statusColor = Palette.TEXT_FAINT;
        }
        int statusHeight = ThemeText.wrappedHeight(this.font, status, inner);
        int statusTop = Math.max(y + 4, this.actionTop - statusHeight - 4);
        ThemeText.wrapped(g, this.font, status, x, statusTop, inner, statusColor);
        this.detailArea.end(g, statusTop + statusHeight + 2);
        if (this.starsInAction) {
            int stars = progress.stars(lesson.id());
            ThemeText.text(g, this.font, TrainingHud.stars(stars), x, this.bodyBottom - Metrics.CONTROL_HEIGHT - 2,
                    stars > 0 ? Palette.ACCENT : Palette.TEXT_FAINT);
        }
    }

    private TrainingScale scale() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone != null) {
            return TrainingScale.of(drone.build().frame().wheelbaseMm);
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            DroneBuild build = this.minecraft.player.getMainHandItem().get(CoreComponents.DRONE_BUILD.get());
            if (build != null) {
                return TrainingScale.of(build.frame().wheelbaseMm);
            }
        }
        return TrainingSession.INSTANCE.scale();
    }

    private Component lockReason(Lesson lesson, TrainingProgress progress) {
        Chapter c = Curriculum.chapter(lesson.chapter());
        if (c != null && !Curriculum.chapterUnlocked(c, progress)) {
            List<String> missing = new ArrayList<>();
            for (String required : c.requires()) {
                Chapter before = Curriculum.chapter(required);
                if (before != null && !Curriculum.chapterComplete(before, progress)) {
                    missing.add(Integer.toString(before.number()));
                }
            }
            return missing.size() == 1 ? Component.translatable("propwash.guide.training.locked.chapter", missing.getFirst())
                    : Component.translatable("propwash.guide.training.locked.chapters", String.join(" + ", missing));
        }
        if (c != null) {
            int index = c.lessons().indexOf(lesson);
            if (index > 0) {
                return Component.translatable("propwash.guide.training.locked.lesson", Component.translatable(c.lessons().get(index - 1).titleKey()));
            }
        }
        return Component.translatable("propwash.guide.training.locked");
    }
}
