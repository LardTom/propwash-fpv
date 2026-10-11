package de.lardtom.propwash.race.client;

import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseLaps;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.ui.ChipFlow;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ScrollArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LeaderboardScreen extends Screen implements ThemedScreen {
    private static final int MAX_WIDTH = 460;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int FOOTER_TEXT_MIN = 150;
    private static final int SUBHEADER = 22;
    private static final int ROW = Metrics.TABLE_ROW;
    private static final int TABLE_HEAD = Metrics.TABLE_HEADER + 4;
    private static final int PAD = 8;
    private static final int COL_RANK = 22;
    private static final int COL_TIME = 58;
    private static final int COL_GAP = 54;
    private static final int COL_CLASS = 78;
    private static final int COL_DATE = 62;
    private static final int GOLD = 0xFFFFC23D;
    private static final int SILVER = 0xFFD0D6DC;
    private static final int BRONZE = 0xFFD9955A;
    private static final int ASSIST_GAP = 6;
    private static final int EMPTY_MARGIN = 6;
    private static final int EMPTY_GAP = 2;
    private static final int TOOLTIP_WIDTH = 260;
    private static final int COURSE_LABEL_MAX = 150;
    private static final int CHIP_GAP = 2;
    private static final int CHIP_LINE = Metrics.CONTROL_HEIGHT + 2;
    private static final int CHIP_LINES = 2;
    private static final int NAME_GAP = 6;
    private static final String INFO_SEPARATOR = " · ";

    private record Columns(int nameX, int nameRight, int timeRight, int gapRight, int classX, int dateX, boolean gap, boolean frame, boolean date) {
    }

    private static @Nullable LeaderboardScreen active;

    private final @Nullable Screen parent;
    private int courseId;
    private @Nullable String filter;
    private int seenVersion = -1;
    private int seenCourses = -1;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private long frames;
    private int assistedDrawn;
    private int assistedRowY = -1;
    private int lastAssistedRowY = -1;
    private @Nullable FlatButton editButton;
    private @Nullable FlatButton moreButton;
    private int footerTextRight;
    private final ScrollArea table = new ScrollArea();
    private final List<FlatButton> classButtons = new ArrayList<>();
    private int chipStart;
    private boolean revealFilter = true;
    private int subHeight = SUBHEADER;

    public LeaderboardScreen(@Nullable Screen parent, int courseId) {
        super(Component.translatable("propwash.race.board.title"));
        this.parent = parent;
        this.courseId = courseId;
    }

    public static void open(@Nullable Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new LeaderboardScreen(parent, defaultCourse(minecraft)));
    }

    public static int defaultCourse(Minecraft minecraft) {
        int current = ClientRaceTracker.INSTANCE.courseId();
        if (current != LapTracker.NO_COURSE && ClientCourses.get(current) != null) {
            return current;
        }
        List<CourseData> courses = courses(minecraft);
        if (courses.isEmpty()) {
            return LapTracker.NO_COURSE;
        }
        if (minecraft.player == null) {
            return courses.getFirst().id();
        }
        CourseData best = courses.getFirst();
        double bestDistance = Double.MAX_VALUE;
        for (CourseData course : courses) {
            CourseData.Gate start = course.gates().getFirst();
            double distance = minecraft.player.distanceToSqr(start.x(), start.y(), start.z());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = course;
            }
        }
        return best.id();
    }

    static List<CourseData> courses(Minecraft minecraft) {
        String dimension = minecraft.level == null ? null : minecraft.level.dimension().identifier().toString();
        List<CourseData> list = new ArrayList<>();
        for (CourseData course : ClientCourses.all()) {
            if (!course.gates().isEmpty() && (dimension == null || course.dimension().equals(dimension))) {
                list.add(course);
            }
        }
        return list;
    }

    public static @Nullable LeaderboardScreen active() {
        return active;
    }

    public int courseId() {
        return this.courseId;
    }

    public @Nullable String filter() {
        return this.filter;
    }

    public long frames() {
        return this.frames;
    }

    public List<RacePayloads.BoardRow> shownRows() {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        if (board == null) {
            return List.of();
        }
        List<RacePayloads.BoardRow> ranking = ClientLeaderboards.ranking(board, this.filter);
        return ranking.subList(0, Math.min(ClientLeaderboards.TOP, ranking.size()));
    }

    public List<RacePayloads.BoardRow> shownAssistedRows() {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        if (board == null) {
            return List.of();
        }
        List<RacePayloads.BoardRow> ranking = ClientLeaderboards.assistedRanking(board, this.filter);
        return ranking.subList(0, Math.min(ClientLeaderboards.ASSISTED_TOP, ranking.size()));
    }

    public int assistedRowsDrawn() {
        return this.assistedDrawn;
    }

    public int firstAssistedRowY() {
        return this.assistedRowY;
    }

    public int lastAssistedRowY() {
        return this.lastAssistedRowY;
    }

    public int tableCenterX() {
        return (this.windowLeft + this.windowRight) / 2;
    }

    public ScrollArea table() {
        return this.table;
    }

    public List<FlatButton> classButtons() {
        return List.copyOf(this.classButtons);
    }

    public @Nullable FlatButton moreButton() {
        return this.moreButton;
    }

    private int assistedExtraHeight() {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        if (board == null) {
            return 0;
        }
        int count = Math.min(ClientLeaderboards.ASSISTED_TOP, ClientLeaderboards.assistedRanking(board, null).size());
        return count == 0 ? 0 : ASSIST_GAP + ROW * (count + 1);
    }

    public void selectFilter(@Nullable String frame) {
        this.filter = frame;
        this.revealFilter = true;
        this.table.reset();
        this.rebuildWidgets();
    }

    public void selectCourse(int courseId) {
        this.courseId = courseId;
        this.filter = null;
        this.chipStart = 0;
        this.table.reset();
        ClientLeaderboards.request(courseId);
        this.rebuildWidgets();
    }

    private void step(int direction) {
        List<CourseData> list = courses(this.minecraft);
        if (list.isEmpty()) {
            return;
        }
        int index = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == this.courseId) {
                index = i;
            }
        }
        index = Math.floorMod(index + direction, list.size());
        this.selectCourse(list.get(index).id());
    }

    private List<String> filters() {
        List<String> result = new ArrayList<>();
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        if (board == null) {
            return result;
        }
        List<String> present = ClientLeaderboards.classes(board);
        for (FrameType frame : FrameType.values()) {
            if (present.contains(frame.key())) {
                result.add(frame.key());
            }
        }
        for (String frame : present) {
            if (!result.contains(frame)) {
                result.add(frame);
            }
        }
        return result;
    }

    static Component className(String frame) {
        if (frame.isEmpty()) {
            return Component.translatable("propwash.race.class.unknown");
        }
        if (frame.indexOf(':') >= 0) {
            return PartNames.frameShortByKey(frame);
        }
        return Component.translatable("propwash.race.class." + frame);
    }

    @Override
    protected void init() {
        active = this;
        if (ClientLeaderboards.get(this.courseId) == null || this.seenVersion < 0 || this.seenCourses != ClientCourses.version()) {
            ClientLeaderboards.request(this.courseId);
        }
        this.seenVersion = ClientLeaderboards.version();
        this.seenCourses = ClientCourses.version();
        int w = Math.min(this.width - 16, MAX_WIDTH);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        int x = this.windowLeft + PAD;
        int labelWidth = this.courseLabelWidth();
        List<String> filters = this.filters();
        if (this.filter != null && !filters.contains(this.filter)) {
            this.filter = null;
        }
        Component all = Component.translatable("propwash.race.board.all");
        int allWidth = this.font.width(all) + 12;
        int allX = x + 14 + labelWidth + 14 + Metrics.GAP;
        int[] widths = new int[filters.size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = this.font.width(className(filters.get(i))) + 12;
        }
        int moreWidth = this.font.width("+" + filters.size()) + 10;
        int wanted = this.revealFilter && this.filter != null ? filters.indexOf(this.filter) : -1;
        this.revealFilter = false;
        ChipFlow.Page page = ChipFlow.pageShowing(widths, wanted, this.chipStart, allX + allWidth + CHIP_GAP, x, this.windowRight - PAD, CHIP_GAP,
                CHIP_LINES, moreWidth);
        this.chipStart = page.first();
        this.subHeight = SUBHEADER + (page.lines() - 1) * CHIP_LINE;
        int h = Math.min(this.height - 12, HEADER + this.subHeight + TABLE_HEAD + ROW * (ClientLeaderboards.TOP + 2) + 2 * PAD + FOOTER
                + this.assistedExtraHeight());
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        int subY = this.windowTop + HEADER + (SUBHEADER - Metrics.CONTROL_HEIGHT) / 2;
        List<CourseData> courses = courses(this.minecraft);
        boolean switchable = courses.size() > 1;
        FlatButton prev = new FlatButton(x, subY, 14, Metrics.CONTROL_HEIGHT, Component.literal("‹"), FlatButton.Style.NORMAL, () -> this.step(-1));
        prev.active = switchable;
        this.addRenderableWidget(prev);
        FlatButton next = new FlatButton(x + 14 + labelWidth, subY, 14, Metrics.CONTROL_HEIGHT, Component.literal("›"), FlatButton.Style.NORMAL,
                () -> this.step(1));
        next.active = switchable;
        this.addRenderableWidget(next);
        this.addRenderableWidget(new FlatButton(allX, subY, allWidth, Metrics.CONTROL_HEIGHT, all, FlatButton.Style.TAB,
                () -> this.filter == null, () -> this.selectFilter(null)));
        this.classButtons.clear();
        for (ChipFlow.Slot slot : page.slots()) {
            String frame = filters.get(slot.index());
            this.classButtons.add(this.addRenderableWidget(new FlatButton(slot.x(), subY + slot.line() * CHIP_LINE, slot.width(), Metrics.CONTROL_HEIGHT,
                    className(frame), FlatButton.Style.TAB, () -> Objects.equals(this.filter, frame), () -> this.selectFilter(frame))));
        }
        this.moreButton = null;
        if (page.paged()) {
            int nextStart = page.next();
            FlatButton more = new FlatButton(page.moreX(), subY + page.moreLine() * CHIP_LINE, moreWidth, Metrics.CONTROL_HEIGHT,
                    Component.literal("+" + page.hidden()), FlatButton.Style.NORMAL, () -> this.showClasses(nextStart));
            more.setTooltip(Tooltip.create(Component.translatable("propwash.race.board.more_classes")));
            this.moreButton = this.addRenderableWidget(more);
        }
        int buttonY = this.windowBottom - FOOTER + (FOOTER - Metrics.CONTROL_HEIGHT) / 2;
        Component close = Component.translatable("propwash.race.board.close");
        int closeWidth = Math.max(70, this.font.width(close) + 16);
        this.addRenderableWidget(new FlatButton(this.windowRight - PAD - closeWidth, buttonY, closeWidth, Metrics.CONTROL_HEIGHT, close,
                FlatButton.Style.PRIMARY, this::onClose));
        Component refresh = Component.translatable("propwash.race.board.refresh");
        int refreshWidth = this.font.width(refresh) + 16;
        this.addRenderableWidget(new FlatButton(this.windowRight - PAD - closeWidth - Metrics.GAP - refreshWidth, buttonY, refreshWidth,
                Metrics.CONTROL_HEIGHT, refresh, FlatButton.Style.NORMAL, () -> ClientLeaderboards.request(this.courseId)));
        this.footerTextRight = this.windowRight - PAD - closeWidth - Metrics.GAP - refreshWidth - Metrics.GAP;
        CourseData course = ClientCourses.get(this.courseId);
        if (course != null) {
            boolean editable = CourseEditScreen.canEdit(this.minecraft, course);
            Component edit = Component.translatable(editable ? "propwash.race.board.edit" : "propwash.race.board.view");
            int editWidth = this.font.width(edit) + 16;
            FlatButton editButton = new FlatButton(this.windowRight - PAD - closeWidth - Metrics.GAP - refreshWidth - Metrics.GAP - editWidth, buttonY,
                    editWidth, Metrics.CONTROL_HEIGHT, edit, FlatButton.Style.NORMAL, this::editCourse);
            editButton.setTooltip(Tooltip.create(Component.translatable("propwash.race.board.edit.tooltip")));
            this.editButton = this.addRenderableWidget(editButton);
            this.footerTextRight = editButton.getX() - Metrics.GAP;
        } else {
            this.editButton = null;
        }
    }

    private void showClasses(int start) {
        this.chipStart = start;
        this.rebuildWidgets();
    }

    public void editCourse() {
        if (ClientCourses.get(this.courseId) != null) {
            this.minecraft.gui.setScreen(new CourseEditScreen(this, this.courseId));
        }
    }

    public @Nullable FlatButton editButton() {
        return this.editButton;
    }

    private Component courseLabel() {
        return CourseEditScreen.label(this.courseId);
    }

    private int courseLabelWidth() {
        return Math.max(54, Math.min(COURSE_LABEL_MAX, this.font.width(this.courseLabel()) + 10));
    }

    @Override
    public void tick() {
        if (this.seenVersion != ClientLeaderboards.version() || this.seenCourses != ClientCourses.version()) {
            this.rebuildWidgets();
        }
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event) || this.table.key(event.key());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.table.wheel(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || this.table.mouseClicked(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.table.mouseDragged(event.y()) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.table.mouseReleased();
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
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, HEADER);
        int textY = Metrics.textY(this.windowTop, HEADER - 1);
        int titleX = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.windowTop + 5, this.windowTop + HEADER - 6);
        ThemeText.text(g, this.font, this.title, titleX, textY, Palette.TEXT);
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        if (board != null && board.gates() > 0) {
            Component brief = Component.translatable("propwash.race.board.info", board.gates(), board.layout());
            Component info = brief;
            CourseData shown = ClientCourses.get(this.courseId);
            if (shown != null) {
                int laps = CourseLaps.resolve(shown.laps(), RaceConfig.CONFIG.get().laps);
                info = Component.translatable("propwash.race.board.info_laps", CourseEditScreen.lapsLabel(laps), brief);
            }
            int room = this.windowRight - 8 - (titleX + this.font.width(this.title) + 8);
            if (TextFit.mode(this.font.width(info), room) == TextFit.Mode.CUT) {
                info = brief;
            }
            ThemeText.fittedRight(g, this.font, info, this.windowRight - 8, textY, Math.max(1, room), Palette.TEXT_FAINT);
        }
        int subTop = this.windowTop + HEADER;
        ThemeDraw.subHeaderBar(g, this.windowLeft, subTop, this.windowRight, this.subHeight);
        int labelWidth = this.courseLabelWidth();
        ThemeText.textCentered(g, this.font, ThemeText.fit(this.font, this.courseLabel(), labelWidth - 6),
                this.windowLeft + PAD + 14 + labelWidth / 2, Metrics.textY(subTop, SUBHEADER), Palette.TEXT);
        boolean stacked = this.footerTextRight - this.windowLeft - 8 < FOOTER_TEXT_MIN;
        int footerTop = this.windowBottom - FOOTER - (stacked ? 2 * Metrics.LINE_HEIGHT : 0);
        this.assistedDrawn = 0;
        this.assistedRowY = -1;
        this.lastAssistedRowY = -1;
        this.extractTable(g, board, subTop + this.subHeight + PAD / 2, footerTop - PAD / 2, mouseX, mouseY);
        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        if (board != null) {
            Component laps = Component.translatable("propwash.race.board.laps", board.laps());
            int footerRight = stacked ? this.windowRight - PAD : this.footerTextRight;
            int footerWidth = Math.max(20, footerRight - this.windowLeft - 8);
            if (board.hasRaceBest()) {
                Component race = Component.translatable("propwash.race.board.race_best", CourseEditScreen.lapsLabel(board.raceLaps()),
                        RaceFormat.time(board.raceBest()));
                ThemeText.fitted(g, this.font, laps, this.windowLeft + 8, footerTop + 3, footerWidth, Palette.TEXT_FAINT);
                ThemeText.fitted(g, this.font, race, this.windowLeft + 8, footerTop + 13, footerWidth, Palette.TEXT_MUTED);
            } else {
                ThemeText.fitted(g, this.font, laps, this.windowLeft + 8, stacked ? footerTop + 8 : Metrics.textY(footerTop, FOOTER), footerWidth,
                        Palette.TEXT_FAINT);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private void extractTable(GuiGraphicsExtractor g, RacePayloads.@Nullable LeaderboardS2C board, int top, int bottom, int mouseX, int mouseY) {
        int left = this.windowLeft + PAD;
        int edge = this.windowRight - PAD;
        int bodyTop = top + TABLE_HEAD;
        List<RacePayloads.BoardRow> ranking = board == null ? List.of() : ClientLeaderboards.ranking(board, this.filter);
        List<RacePayloads.BoardRow> assisted = board == null ? List.of() : ClientLeaderboards.assistedRanking(board, this.filter);
        int shown = Math.min(ClientLeaderboards.TOP, ranking.size());
        int assistedShown = Math.min(ClientLeaderboards.ASSISTED_TOP, assisted.size());
        RacePayloads.BoardRow self = null;
        for (int i = shown; i < ranking.size(); i++) {
            if (ranking.get(i).self()) {
                self = ranking.get(i);
                break;
            }
        }
        int content = shown * ROW + (self == null ? 0 : ROW + 4) + (assistedShown == 0 ? 0 : ASSIST_GAP + ROW * (assistedShown + 1))
                + (shown == 0 && assistedShown > 0 ? ROW : 0);
        int right = content > bottom - bodyTop ? edge - ScrollArea.GUTTER : edge;
        Columns columns = this.columns(left, right, this.nameNeed(ranking.subList(0, shown), self, assisted.subList(0, assistedShown)));
        int headY = top + 2;
        ThemeText.text(g, this.font, "#", left + 4, headY, Palette.TEXT_FAINT);
        ThemeText.text(g, this.font, Component.translatable("propwash.race.board.col.pilot"), columns.nameX(), headY, Palette.TEXT_FAINT);
        ThemeText.textRight(g, this.font, Component.translatable("propwash.race.board.col.time"), columns.timeRight(), headY, Palette.TEXT_FAINT);
        if (columns.gap()) {
            ThemeText.textRight(g, this.font, Component.translatable("propwash.race.board.col.gap"), columns.gapRight(), headY, Palette.TEXT_FAINT);
        }
        if (columns.frame()) {
            ThemeText.text(g, this.font, Component.translatable("propwash.race.board.col.class"), columns.classX(), headY, Palette.TEXT_FAINT);
        }
        if (columns.date()) {
            ThemeText.text(g, this.font, Component.translatable("propwash.race.board.col.date"), columns.dateX(), headY, Palette.TEXT_FAINT);
        }
        ThemeDraw.separator(g, left, edge, top + TABLE_HEAD - 2);
        this.table.view(left, bodyTop, edge, bottom);
        if (board == null || shown == 0 && assistedShown == 0) {
            this.table.begin(g);
            this.table.end(g, bodyTop);
            boolean archived = board != null && board.gates() != 0 && board.archived() > 0;
            Component empty = board == null ? Component.translatable("propwash.race.board.loading")
                    : board.gates() == 0 ? Component.translatable("propwash.race.board.no_course")
                    : archived ? Component.translatable("propwash.race.board.empty_archived", board.layout())
                    : Component.translatable("propwash.race.board.empty");
            int center = (left + edge) / 2;
            int room = edge - left - 2 * EMPTY_MARGIN;
            int emptyY = bodyTop + 8;
            emptyY += ThemeText.wrappedCentered(g, this.font, empty, center, emptyY, room, Palette.TEXT_MUTED);
            if (archived) {
                ThemeText.wrappedCentered(g, this.font, Component.translatable("propwash.race.board.empty_archived.command", board.courseId()), center,
                        emptyY + EMPTY_GAP, room, Palette.TEXT_MUTED);
            }
            return;
        }
        DateTimeFormatter date = DateTimeFormatter.ofPattern(Component.translatable("propwash.race.board.date_pattern").getString())
                .withZone(ZoneId.systemDefault());
        int hoverY = mouseX >= left && mouseX < right ? this.table.hoverY(mouseX, mouseY) : Integer.MIN_VALUE;
        RacePayloads.BoardRow hovered = null;
        int hoveredY = 0;
        long hoveredBest = 0L;
        this.table.begin(g);
        int y = bodyTop;
        long record = shown == 0 ? 0L : ranking.getFirst().nanos();
        for (int i = 0; i < shown; i++) {
            RacePayloads.BoardRow row = ranking.get(i);
            this.extractRow(g, row, ClientLeaderboards.rank(row, this.filter), record, date, left, right, columns, y, i, false);
            if (hoverY >= y && hoverY < y + ROW) {
                hovered = row;
                hoveredY = y;
                hoveredBest = record;
            }
            y += ROW;
        }
        if (self != null) {
            ThemeDraw.separator(g, left, right, y + 1);
            y += 4;
            this.extractRow(g, self, ClientLeaderboards.rank(self, this.filter), record, date, left, right, columns, y, -1, false);
            if (hoverY >= y && hoverY < y + ROW) {
                hovered = self;
                hoveredY = y;
                hoveredBest = record;
            }
            y += ROW;
        }
        if (assistedShown > 0) {
            if (shown == 0) {
                ThemeText.fittedCentered(g, this.font, Component.translatable("propwash.race.board.assisted_only"), (left + right) / 2,
                        Metrics.textY(y, ROW), right - left - 2 * EMPTY_MARGIN, Palette.TEXT_MUTED);
                y += ROW;
            }
            y += ASSIST_GAP / 2;
            ThemeDraw.separator(g, left, right, y);
            y += ASSIST_GAP / 2;
            ThemeText.text(g, this.font, Component.translatable("propwash.race.board.assisted_section"), left + 4, Metrics.textY(y, ROW), Palette.WARNING);
            y += ROW;
            long best = assisted.getFirst().nanos();
            for (int i = 0; i < assistedShown; i++) {
                RacePayloads.BoardRow row = assisted.get(i);
                this.extractRow(g, row, ClientLeaderboards.rank(row, this.filter), best, date, left, right, columns, y, i, true);
                if (hoverY >= y && hoverY < y + ROW) {
                    hovered = row;
                    hoveredY = y;
                    hoveredBest = best;
                }
                if (i == 0) {
                    this.assistedRowY = y - this.table.offset();
                }
                this.lastAssistedRowY = y - this.table.offset();
                this.assistedDrawn++;
                y += ROW;
            }
        }
        this.table.end(g, y);
        if (hovered != null) {
            Component tip = this.rowTooltip(hovered, hoveredBest, columns, date);
            if (tip != null) {
                ThemeText.tooltipBelow(g, this.font, tip, TOOLTIP_WIDTH,
                        new ScreenRectangle(columns.nameX(), hoveredY - this.table.offset(), right - columns.nameX(), ROW));
            }
        }
    }

    private Columns columns(int left, int right, int need) {
        boolean date = true;
        boolean frame = true;
        boolean gap = true;
        if (nameRoom(left, right, gap, frame, date) < need) {
            date = false;
        }
        if (nameRoom(left, right, gap, frame, date) < need) {
            frame = false;
        }
        if (nameRoom(left, right, gap, frame, date) < need) {
            gap = false;
        }
        int x = right;
        int dateX = x - COL_DATE;
        if (date) {
            x = dateX;
        }
        int classX = x - COL_CLASS;
        if (frame) {
            x = classX;
        }
        int gapRight = x - NAME_GAP;
        if (gap) {
            x -= COL_GAP;
        }
        int timeRight = x - NAME_GAP;
        x -= COL_TIME;
        return new Columns(left + COL_RANK, x - NAME_GAP, timeRight, gapRight, classX, dateX, gap, frame, date);
    }

    private static int nameRoom(int left, int right, boolean gap, boolean frame, boolean date) {
        return right - left - COL_RANK - COL_TIME - NAME_GAP - (gap ? COL_GAP : 0) - (frame ? COL_CLASS : 0) - (date ? COL_DATE : 0);
    }

    private int nameNeed(List<RacePayloads.BoardRow> ranked, RacePayloads.@Nullable BoardRow self, List<RacePayloads.BoardRow> assisted) {
        int plain = self == null ? 0 : this.font.width(self.name());
        for (RacePayloads.BoardRow row : ranked) {
            plain = Math.max(plain, this.font.width(row.name()));
        }
        int need = squeezed(plain);
        if (!assisted.isEmpty()) {
            int widest = 0;
            for (RacePayloads.BoardRow row : assisted) {
                widest = Math.max(widest, this.font.width(row.name()));
            }
            need = Math.max(need, this.badgeWidth() + 4 + squeezed(widest));
        }
        return need;
    }

    private static int squeezed(int width) {
        return (int) Math.ceil(width * TextFit.MIN_SQUEEZE);
    }

    private int badgeWidth() {
        return this.font.width(Component.translatable("propwash.race.board.assisted_badge")) + 8;
    }

    private @Nullable Component rowTooltip(RacePayloads.BoardRow row, long best, Columns columns, DateTimeFormatter date) {
        MutableComponent hidden = Component.empty();
        boolean any = false;
        if (!columns.gap() && row.nanos() != best) {
            hidden.append(RaceFormat.delta(row.nanos() - best));
            any = true;
        }
        if (!columns.frame()) {
            hidden.append(any ? INFO_SEPARATOR : "").append(className(row.frame()));
            any = true;
        }
        if (!columns.date() && row.dateMillis() > 0L) {
            hidden.append(any ? INFO_SEPARATOR : "").append(date.format(Instant.ofEpochMilli(row.dateMillis())));
            any = true;
        }
        if (!row.assisted()) {
            return any ? hidden : null;
        }
        MutableComponent tip = Component.translatable("propwash.race.board.assisted_tooltip", AssistText.storedReasons(row.assist()));
        return any ? tip.append("\n").append(hidden) : tip;
    }

    private void extractRow(GuiGraphicsExtractor g, RacePayloads.BoardRow row, int rank, long best, DateTimeFormatter date, int left, int right,
                            Columns columns, int y, int index, boolean assisted) {
        if (row.self()) {
            ThemeDraw.selection(g, left, y, right, y + ROW, true, false);
        } else if (index % 2 == 1) {
            g.fill(left, y, right, y + ROW, Palette.ROW_STRIPE);
        }
        int textY = Metrics.textY(y, ROW);
        int rankColor = assisted ? Palette.TEXT_MUTED : rank == 1 ? GOLD : rank == 2 ? SILVER : rank == 3 ? BRONZE : Palette.TEXT_MUTED;
        ThemeText.textRight(g, this.font, rank > 0 ? Integer.toString(rank) : "–", columns.nameX() - 6, textY, rankColor);
        int nameStart = columns.nameX();
        if (assisted) {
            nameStart += ThemeDraw.badge(g, this.font, Component.translatable("propwash.race.board.assisted_badge"), nameStart, y + 1, y + ROW - 1,
                    Palette.WARNING, Palette.BACKGROUND) + 4;
        }
        ThemeText.fitted(g, this.font, row.name(), nameStart, textY, columns.nameRight() - nameStart, row.self() ? Palette.TEXT : Palette.TEXT_MUTED);
        ThemeText.textRight(g, this.font, RaceFormat.time(row.nanos()), columns.timeRight(), textY, !assisted && rank == 1 ? GOLD : Palette.TEXT);
        if (columns.gap()) {
            String gap = row.nanos() == best ? "" : RaceFormat.delta(row.nanos() - best);
            ThemeText.textRight(g, this.font, gap, columns.gapRight(), textY, Palette.TEXT_FAINT);
        }
        if (columns.frame()) {
            int classRight = columns.date() ? columns.dateX() : right;
            ThemeText.fitted(g, this.font, className(row.frame()), columns.classX(), textY, classRight - columns.classX() - 4, Palette.TEXT_MUTED);
        }
        if (columns.date()) {
            String day = row.dateMillis() > 0L ? date.format(Instant.ofEpochMilli(row.dateMillis())) : "–";
            ThemeText.text(g, this.font, day, columns.dateX(), textY, Palette.TEXT_FAINT);
        }
    }
}
