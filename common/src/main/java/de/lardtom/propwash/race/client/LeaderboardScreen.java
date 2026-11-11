package de.lardtom.propwash.race.client;

import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.race.CourseArgument;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.BoardIndex;
import de.lardtom.propwash.race.CourseLaps;
import de.lardtom.propwash.race.GhostChoice;
import de.lardtom.propwash.race.GhostKey;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.ServerRulesBadge;
import de.lardtom.propwash.share.client.chat.ShareChat;
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
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private static final int ACTIONS = ROW + Metrics.CONTROL_HEIGHT + 8;
    private static final int CONFIRM_TICKS = 60;
    private static final int CHOSEN_GAP = 4;
    private static final int NAME_KEEP = 48;
    private static final int NAME_MIN = 20;
    private static final int MIN_ROWS = 12;
    private static final int ARROW = 7;
    private static final int MINE_GAP = 8;

    public record Selection(GhostKey key, boolean assisted) {
        public boolean matches(RacePayloads.BoardRow row) {
            return row.assisted() == this.assisted && row.key().equals(this.key);
        }
    }

    private record RowHit(int y, RacePayloads.BoardRow row) {
    }

    private record HeaderHit(int y, boolean assisted, int mineLeft, int mineRight, int rank) {
    }

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
    private boolean stacked;
    private @Nullable Selection selected;
    private final List<RowHit> rowHits = new ArrayList<>();
    private final List<HeaderHit> headerHits = new ArrayList<>();
    private @Nullable BoardIndex needClean;
    private @Nullable BoardIndex needAssisted;
    private int need;
    private int rowsDrawn;
    private int placeholdersDrawn;
    private int revealHeader = BoardLayout.NONE;
    private int jumpRank;
    private boolean jumpAssisted;
    private int tableTop;
    private int tableBottom;
    private int tableRight;
    private int confirmRemove;
    private @Nullable FlatButton copyButton;
    private @Nullable FlatButton importButton;
    private @Nullable FlatButton replayButton;
    private @Nullable FlatButton opponentButton;
    private @Nullable FlatButton removeButton;
    private int codeLeft;
    private int codeRight;
    private int codeTop;
    private boolean codeHovered;
    private int codeTooltips;
    private int rulesBadgeLeft = -1;
    private int rulesBadgeRight;
    private int rulesBadgesDrawn;
    private int creativeBadgeLeft = -1;
    private int creativeBadgeRight;
    private int rulesTooltips;
    private int creativeTooltips;

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
        return this.index(false).rows();
    }

    public List<RacePayloads.BoardRow> shownAssistedRows() {
        return this.index(true).rows();
    }

    public BoardIndex index(boolean assisted) {
        return ClientLeaderboards.index(this.courseId, this.filter, assisted);
    }

    public BoardLayout layout() {
        return new BoardLayout(this.index(false).total(), this.index(true).total(), BoardSections.collapsed(false), BoardSections.collapsed(true), ROW,
                ASSIST_GAP);
    }

    public boolean toggleSection(boolean assisted) {
        boolean collapsed = BoardSections.toggle(assisted);
        this.revealHeader = assisted ? BoardLayout.ASSISTED : BoardLayout.CLEAN;
        return collapsed;
    }

    public boolean jumpToMine(boolean assisted) {
        int rank = this.index(assisted).selfRank();
        if (rank <= 0) {
            return false;
        }
        BoardSections.set(assisted, false);
        this.jumpRank = rank;
        this.jumpAssisted = assisted;
        return true;
    }

    public int headerY(boolean assisted) {
        for (HeaderHit hit : this.headerHits) {
            if (hit.assisted() == assisted) {
                return hit.y();
            }
        }
        return Integer.MIN_VALUE;
    }

    public int mineX(boolean assisted) {
        for (HeaderHit hit : this.headerHits) {
            if (hit.assisted() == assisted && hit.rank() > 0) {
                return (hit.mineLeft() + hit.mineRight()) / 2;
            }
        }
        return Integer.MIN_VALUE;
    }

    public int rowsDrawn() {
        return this.rowsDrawn;
    }

    public int placeholdersDrawn() {
        return this.placeholdersDrawn;
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

    public @Nullable Selection selected() {
        return this.selected;
    }

    public RacePayloads.@Nullable BoardRow selectedRow() {
        Selection selection = this.selected;
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        if (selection == null || board == null) {
            return null;
        }
        for (RacePayloads.BoardRow row : board.rows()) {
            if (selection.matches(row)) {
                return row;
            }
        }
        return null;
    }

    public void select(RacePayloads.@Nullable BoardRow row) {
        Selection next = row == null ? null : new Selection(row.key(), row.assisted());
        if (!Objects.equals(next, this.selected)) {
            this.selected = next;
            this.confirmRemove = 0;
            if (this.minecraft != null) {
                this.rebuildWidgets();
            }
        }
    }

    public @Nullable FlatButton copyButton() {
        return this.copyButton;
    }

    public @Nullable FlatButton importButton() {
        return this.importButton;
    }

    public @Nullable FlatButton replayButton() {
        return this.replayButton;
    }

    public @Nullable FlatButton opponentButton() {
        return this.opponentButton;
    }

    public @Nullable FlatButton removeButton() {
        return this.removeButton;
    }

    public boolean removeArmed() {
        return this.confirmRemove > 0;
    }

    public int codeTooltips() {
        return this.codeTooltips;
    }

    public int codeCenterX() {
        return (this.codeLeft + this.codeRight) / 2;
    }

    public int codeCenterY() {
        return this.codeTop + ROW / 2;
    }

    public int rulesBadgeLeft() {
        return this.rulesBadgeLeft;
    }

    public int rulesBadgeRight() {
        return this.rulesBadgeRight;
    }

    public int rulesBadgeCenterX() {
        return (this.rulesBadgeLeft + this.rulesBadgeRight) / 2;
    }

    public int rulesTooltips() {
        return this.rulesTooltips;
    }

    public int rulesBadgeCenterY() {
        return this.windowTop + HEADER / 2;
    }

    public int rulesBadgesDrawn() {
        return this.rulesBadgesDrawn;
    }

    public int creativeBadgeLeft() {
        return this.creativeBadgeLeft;
    }

    public int creativeBadgeCenterX() {
        return (this.creativeBadgeLeft + this.creativeBadgeRight) / 2;
    }

    public int creativeBadgeRight() {
        return this.creativeBadgeRight;
    }

    public int creativeTooltips() {
        return this.creativeTooltips;
    }

    public int headerRight() {
        return this.windowRight - 8;
    }

    public boolean codeHovered() {
        return this.codeHovered;
    }

    public static boolean mayReplay(RacePayloads.LeaderboardS2C board, RacePayloads.BoardRow row) {
        return row.ghost() && (row.self() || board.mayReplayOthers());
    }

    public static boolean mayRace(RacePayloads.LeaderboardS2C board, RacePayloads.BoardRow row) {
        return row.ghost() && (row.self() || board.mayRaceOthers());
    }

    public void selectFilter(@Nullable String frame) {
        this.filter = frame;
        this.revealFilter = true;
        this.table.reset();
        this.rebuildWidgets();
    }

    public void selectCourse(int courseId) {
        this.courseId = courseId;
        this.selected = null;
        this.confirmRemove = 0;
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
        int h = Math.min(this.height - 12, HEADER + this.subHeight + TABLE_HEAD + Math.max(ROW * MIN_ROWS, this.layout().height()) + 2 * PAD + FOOTER
                + ACTIONS);
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
        this.stacked = this.footerTextRight - this.windowLeft - 8 < FOOTER_TEXT_MIN;
        this.initActions();
    }

    private int actionTop() {
        int footerTop = this.windowBottom - FOOTER - (this.stacked ? 2 * Metrics.LINE_HEIGHT : 0);
        return footerTop - ACTIONS;
    }

    private void initActions() {
        this.copyButton = null;
        this.importButton = null;
        this.replayButton = null;
        this.opponentButton = null;
        this.removeButton = null;
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        RacePayloads.BoardRow row = this.selectedRow();
        if (board == null || row == null) {
            if (this.selected != null && board != null) {
                this.selected = null;
            }
            return;
        }
        boolean remove = board.mayRemove();
        int count = remove ? 5 : 4;
        int left = this.windowLeft + PAD;
        int width = this.windowRight - PAD - left;
        int buttonWidth = Math.max(20, (width - (count - 1) * Metrics.GAP) / count);
        int y = this.actionTop() + ROW + 2;
        String code = row.code();
        boolean hasCode = !code.isEmpty();
        int x = left;
        FlatButton copy = new FlatButton(x, y, buttonWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.race.ghosts.copy"),
                FlatButton.Style.NORMAL, () -> ShareChat.copy(code));
        copy.active = hasCode;
        copy.setTooltip(Tooltip.create(Component.translatable(hasCode ? "propwash.race.ghosts.copy.tooltip" : "propwash.race.ghosts.no_code")));
        this.copyButton = this.addRenderableWidget(copy);
        x += buttonWidth + Metrics.GAP;
        FlatButton paste = new FlatButton(x, y, buttonWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.race.ghosts.import"),
                FlatButton.Style.NORMAL, () -> ShareChat.open(code, this));
        paste.active = hasCode;
        paste.setTooltip(Tooltip.create(Component.translatable(hasCode ? "propwash.race.ghosts.import.tooltip" : "propwash.race.ghosts.no_code")));
        this.importButton = this.addRenderableWidget(paste);
        x += buttonWidth + Metrics.GAP;
        FlatButton replay = new FlatButton(x, y, buttonWidth, Metrics.CONTROL_HEIGHT, Component.translatable("propwash.race.ghosts.replay"),
                FlatButton.Style.PRIMARY, () -> this.openReplay(row));
        String replayReason = this.replayBlocked(board, row);
        replay.active = replayReason == null;
        replay.setTooltip(Tooltip.create(Component.translatable(replayReason == null ? "propwash.race.ghosts.replay.tooltip" : replayReason)));
        this.replayButton = this.addRenderableWidget(replay);
        x += buttonWidth + Metrics.GAP;
        boolean chosen = GhostChoices.INSTANCE.chosen(this.minecraft, this.courseId, row.key());
        FlatButton opponent = new FlatButton(x, y, buttonWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable(chosen ? "propwash.race.ghosts.own" : "propwash.race.ghosts.opponent"), FlatButton.Style.NORMAL,
                () -> this.toggleOpponent(row));
        String raceReason = chosen ? null : !row.ghost() ? "propwash.race.ghosts.no_ghost" : mayRace(board, row) ? null : "propwash.race.ghosts.denied_opponent";
        opponent.active = raceReason == null;
        opponent.setTooltip(Tooltip.create(Component.translatable(raceReason != null ? raceReason
                : chosen ? "propwash.race.ghosts.own.tooltip" : "propwash.race.ghosts.opponent.tooltip")));
        this.opponentButton = this.addRenderableWidget(opponent);
        if (remove) {
            x += buttonWidth + Metrics.GAP;
            FlatButton delete = new FlatButton(x, y, buttonWidth, Metrics.CONTROL_HEIGHT,
                    Component.translatable(this.confirmRemove > 0 ? "propwash.race.ghosts.remove.confirm" : "propwash.race.ghosts.remove"),
                    FlatButton.Style.DANGER, () -> this.removeTime(row));
            delete.setTooltip(Tooltip.create(Component.translatable("propwash.race.ghosts.remove.tooltip", row.name())));
            this.removeButton = this.addRenderableWidget(delete);
        }
    }

    private @Nullable String replayBlocked(RacePayloads.LeaderboardS2C board, RacePayloads.BoardRow row) {
        if (!row.ghost()) {
            return "propwash.race.ghosts.no_ghost";
        }
        if (!mayReplay(board, row)) {
            return "propwash.race.ghosts.denied_replay";
        }
        if (FpvView.active()) {
            return "propwash.race.ghosts.replay.flying";
        }
        if (!GhostDownloads.INSTANCE.available() && GhostDownloads.INSTANCE.cached(this.courseId, row.key()) == null) {
            return "propwash.race.ghosts.replay.offline";
        }
        return null;
    }

    public void openReplay(RacePayloads.BoardRow row) {
        this.minecraft.gui.setScreen(new ReplayScreen(this, this.courseId, row));
    }

    public void toggleOpponent(RacePayloads.BoardRow row) {
        boolean chosen = GhostChoices.INSTANCE.chosen(this.minecraft, this.courseId, row.key());
        GhostChoices.INSTANCE.choose(this.minecraft, this.courseId, chosen ? GhostChoice.OWN : GhostChoice.entry(row.key(), row.name()));
        Component message = chosen ? Component.translatable("propwash.race.ghosts.own.chosen", this.courseLabel())
                : Component.translatable("propwash.race.ghosts.opponent.chosen", row.name(), RaceFormat.time(row.nanos()), this.courseLabel());
        this.minecraft.gui.hud.setOverlayMessage(ThemeText.colored(message, Palette.SUCCESS), false);
        this.rebuildWidgets();
    }

    public void removeTime(RacePayloads.BoardRow row) {
        if (this.confirmRemove <= 0) {
            this.confirmRemove = CONFIRM_TICKS;
            this.rebuildWidgets();
            return;
        }
        this.confirmRemove = 0;
        sendRemove(this.courseId, row);
        this.selected = null;
        this.rebuildWidgets();
    }

    static boolean sendRemove(int courseId, RacePayloads.BoardRow row) {
        if (!ClientNet.canSend(RacePayloads.TimeRemoveC2S.TYPE)) {
            return false;
        }
        GhostDownloads.INSTANCE.forget(courseId, row.key());
        return ClientNet.sendToServer(new RacePayloads.TimeRemoveC2S(courseId, row.player(), row.frame(), row.assisted()));
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
        if (this.confirmRemove > 0) {
            this.confirmRemove--;
            if (this.confirmRemove == 0) {
                this.rebuildWidgets();
                return;
            }
        }
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
        if (super.mouseClicked(event, doubleClick) || this.table.mouseClicked(event.x(), event.y())) {
            return true;
        }
        RacePayloads.BoardRow picked = this.selectedRow();
        if (picked != null && !picked.code().isEmpty() && event.x() >= this.codeLeft && event.x() < this.codeRight && event.y() >= this.codeTop
                && event.y() < this.codeTop + ROW) {
            ShareChat.copy(picked.code());
            return true;
        }
        HeaderHit header = this.headerAt(event.x(), event.y());
        if (header != null) {
            if (header.rank() > 0 && event.x() >= header.mineLeft() && event.x() < header.mineRight()) {
                this.jumpToMine(header.assisted());
            } else {
                this.toggleSection(header.assisted());
            }
            return true;
        }
        RacePayloads.BoardRow row = this.rowAt(event.x(), event.y());
        if (row == null) {
            return false;
        }
        Selection selection = this.selected;
        this.select(selection != null && selection.matches(row) ? null : row);
        return true;
    }

    private @Nullable HeaderHit headerAt(double mouseX, double mouseY) {
        if (mouseX < this.windowLeft + PAD || mouseX >= this.tableRight || mouseY < this.tableTop || mouseY >= this.tableBottom) {
            return null;
        }
        for (int i = this.headerHits.size() - 1; i >= 0; i--) {
            HeaderHit hit = this.headerHits.get(i);
            if (mouseY >= hit.y() && mouseY < hit.y() + ROW) {
                return hit;
            }
        }
        return null;
    }

    public RacePayloads.@Nullable BoardRow rowAt(double mouseX, double mouseY) {
        if (mouseX < this.windowLeft + PAD || mouseX >= this.tableRight || mouseY < this.tableTop || mouseY >= this.tableBottom
                || this.headerAt(mouseX, mouseY) != null) {
            return null;
        }
        for (RowHit hit : this.rowHits) {
            if (mouseY >= hit.y() && mouseY < hit.y() + ROW) {
                return hit.row();
            }
        }
        return null;
    }

    public int rowY(RacePayloads.BoardRow row) {
        for (RowHit hit : this.rowHits) {
            if (hit.row().assisted() == row.assisted() && hit.row().key().equals(row.key())) {
                return hit.y();
            }
        }
        return Integer.MIN_VALUE;
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
        int titleRight = titleX + this.font.width(this.title);
        List<ServerRulesBadge.Line> serverRules = ServerRulesText.board();
        HeaderBadges.Layout badges = HeaderBadges.place(titleRight, this.windowRight - 8, !serverRules.isEmpty(), ServerRulesText.width(this.font),
                ServerRulesText.shortWidth(this.font), ClientCreativeFc.legal(this.courseId), ClientCreativeFc.width(this.font));
        this.rulesBadgeLeft = -1;
        this.rulesBadgesDrawn = 0;
        if (badges.rules() != HeaderBadges.Form.NONE) {
            this.rulesBadgeLeft = titleRight + HeaderBadges.GAP;
            Component label = badges.rules() == HeaderBadges.Form.FULL ? ServerRulesText.label() : ServerRulesText.shortLabel();
            this.rulesBadgeRight = this.rulesBadgeLeft + ServerRulesText.draw(g, this.font, label, this.rulesBadgeLeft, this.windowTop + 6,
                    this.windowTop + HEADER - 6);
            titleRight = this.rulesBadgeRight;
        }
        this.creativeBadgeLeft = -1;
        if (badges.creative()) {
            this.creativeBadgeLeft = titleRight + HeaderBadges.GAP;
            this.creativeBadgeRight = this.creativeBadgeLeft + ClientCreativeFc.draw(g, this.font, this.creativeBadgeLeft, this.windowTop + 6,
                    this.windowTop + HEADER - 6);
            titleRight = this.creativeBadgeRight;
        }
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        if (board != null && board.gates() > 0) {
            Component brief = Component.translatable("propwash.race.board.info", board.gates(), board.layout());
            Component info = brief;
            CourseData shown = ClientCourses.get(this.courseId);
            if (shown != null) {
                int laps = CourseLaps.resolve(shown.laps(), RaceConfig.CONFIG.get().laps);
                Component format = shown.mode() == CourseMode.POINT_TO_POINT ? Component.translatable("propwash.race.p2p.mode")
                        : CourseEditScreen.lapsLabel(laps);
                info = Component.translatable("propwash.race.board.info_laps", format, brief);
            }
            int room = this.windowRight - 8 - (titleRight + 8);
            if (TextFit.mode(this.font.width(info), room) == TextFit.Mode.CUT) {
                info = brief;
            }
            if (TextFit.mode(this.font.width(info), room) != TextFit.Mode.CUT) {
                ThemeText.fittedRight(g, this.font, info, this.windowRight - 8, textY, room, Palette.TEXT_FAINT);
            }
        }
        int subTop = this.windowTop + HEADER;
        ThemeDraw.subHeaderBar(g, this.windowLeft, subTop, this.windowRight, this.subHeight);
        int labelWidth = this.courseLabelWidth();
        ThemeText.textCentered(g, this.font, ThemeText.fit(this.font, this.courseLabel(), labelWidth - 6),
                this.windowLeft + PAD + 14 + labelWidth / 2, Metrics.textY(subTop, SUBHEADER), Palette.TEXT);
        boolean stacked = this.stacked;
        int footerTop = this.windowBottom - FOOTER - (stacked ? 2 * Metrics.LINE_HEIGHT : 0);
        this.assistedDrawn = 0;
        this.assistedRowY = -1;
        this.lastAssistedRowY = -1;
        int actionTop = footerTop - ACTIONS;
        this.extractTable(g, board, subTop + this.subHeight + PAD / 2, actionTop - PAD / 2, mouseX, mouseY);
        this.extractActions(g, board, actionTop, mouseX, mouseY);
        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        if (board != null) {
            CourseData shown = ClientCourses.get(this.courseId);
            boolean point = shown != null && shown.mode() == CourseMode.POINT_TO_POINT;
            Component laps = Component.translatable(point ? "propwash.race.p2p.board.runs" : "propwash.race.board.laps", board.laps());
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
        if (this.codeHovered) {
            this.extractCodeTooltip(g, mouseX, mouseY);
        }
        if (this.rulesBadgeLeft >= 0 && mouseX >= this.rulesBadgeLeft && mouseX < this.rulesBadgeRight && mouseY >= this.windowTop
                && mouseY < this.windowTop + HEADER) {
            ThemeText.tooltipWrapped(g, this.font, ServerRulesText.boardTooltip(serverRules), TOOLTIP_WIDTH, mouseX, mouseY);
            this.rulesTooltips++;
        }
        if (this.creativeBadgeLeft >= 0 && mouseX >= this.creativeBadgeLeft && mouseX < this.creativeBadgeRight && mouseY >= this.windowTop
                && mouseY < this.windowTop + HEADER) {
            ThemeText.tooltipWrapped(g, this.font, ClientCreativeFc.tooltip(this.courseId), TOOLTIP_WIDTH, mouseX, mouseY);
            this.creativeTooltips++;
        }
    }

    private void extractActions(GuiGraphicsExtractor g, RacePayloads.@Nullable LeaderboardS2C board, int top, int mouseX, int mouseY) {
        int left = this.windowLeft + PAD;
        int right = this.windowRight - PAD;
        ThemeDraw.separator(g, left, right, top);
        this.codeHovered = false;
        RacePayloads.BoardRow row = board == null ? null : this.selectedRow();
        int textY = Metrics.textY(top + 1, ROW);
        if (row == null) {
            Component hint = Component.translatable(board == null || board.rows().isEmpty() ? "propwash.race.ghosts.hint_empty" : "propwash.race.ghosts.hint");
            ThemeText.fittedCentered(g, this.font, hint, (left + right) / 2, Metrics.textY(top, ACTIONS), right - left, Palette.TEXT_FAINT);
            return;
        }
        Component label = Component.translatable("propwash.race.ghosts.code", row.name());
        int labelWidth = Math.min(this.font.width(label), (right - left) / 2);
        ThemeText.fitted(g, this.font, label, left, textY, labelWidth, Palette.TEXT_MUTED);
        this.codeLeft = left + labelWidth + 6;
        this.codeRight = right;
        this.codeTop = top + 1;
        if (row.code().isEmpty()) {
            ThemeText.fitted(g, this.font, Component.translatable("propwash.race.ghosts.no_code"), this.codeLeft, textY, this.codeRight - this.codeLeft,
                    Palette.TEXT_FAINT);
            return;
        }
        boolean hovered = mouseX >= this.codeLeft && mouseX < this.codeRight && mouseY >= this.codeTop && mouseY < this.codeTop + ROW;
        ThemeText.fitted(g, this.font, row.code(), this.codeLeft, textY, this.codeRight - this.codeLeft, hovered ? Palette.ACCENT_HOVER : ShareChat.COLOR);
        this.codeHovered = hovered;
    }

    private void extractCodeTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        RacePayloads.BoardRow row = this.selectedRow();
        if (row == null || row.code().isEmpty()) {
            return;
        }
        Style style = Style.EMPTY.withClickEvent(new ClickEvent.Custom(ShareChat.COPY, Optional.of(StringTag.valueOf(row.code()))));
        List<ClientTooltipComponent> lines = ShareChat.tooltip(style, this.font, this.width);
        if (lines == null) {
            ThemeText.tooltip(g, this.font, ShareChat.invalidText(), mouseX, mouseY);
            return;
        }
        g.nextStratum();
        g.tooltip(this.font, lines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null, false);
        this.codeTooltips++;
    }

    private void extractTable(GuiGraphicsExtractor g, RacePayloads.@Nullable LeaderboardS2C board, int top, int bottom, int mouseX, int mouseY) {
        int left = this.windowLeft + PAD;
        int edge = this.windowRight - PAD;
        int bodyTop = top + TABLE_HEAD;
        BoardIndex clean = this.index(false);
        BoardIndex assisted = this.index(true);
        BoardLayout layout = this.layout();
        boolean empty = board == null || clean.total() == 0 && assisted.total() == 0;
        int right = !empty && layout.height() > bottom - bodyTop ? edge - ScrollArea.GUTTER : edge;
        this.rowHits.clear();
        this.headerHits.clear();
        this.rowsDrawn = 0;
        this.placeholdersDrawn = 0;
        this.tableTop = bodyTop;
        this.tableBottom = bottom;
        this.tableRight = right;
        Columns columns = this.columns(left, right, this.nameNeed(clean, assisted));
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
        if (empty) {
            this.table.begin(g);
            this.table.end(g, bodyTop);
            boolean archived = board != null && board.gates() != 0 && board.archived() > 0;
            Component message = board == null ? Component.translatable("propwash.race.board.loading")
                    : board.gates() == 0 ? Component.translatable("propwash.race.board.no_course")
                    : archived ? Component.translatable("propwash.race.board.empty_archived", board.layout())
                    : Component.translatable("propwash.race.board.empty");
            int center = (left + edge) / 2;
            int room = edge - left - 2 * EMPTY_MARGIN;
            int emptyY = bodyTop + 8;
            emptyY += ThemeText.wrappedCentered(g, this.font, message, center, emptyY, room, Palette.TEXT_MUTED);
            if (archived) {
                CourseData course = ClientCourses.get(board.courseId());
                String reference = CourseArgument.reference(board.courseId(), course == null ? null : course.key());
                ThemeText.wrappedCentered(g, this.font, Component.translatable("propwash.race.board.empty_archived.command", reference), center,
                        emptyY + EMPTY_GAP, room, Palette.TEXT_MUTED);
            }
            return;
        }
        this.applyScrollRequests(layout, bodyTop);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(Component.translatable("propwash.race.board.date_pattern").getString())
                .withZone(ZoneId.systemDefault());
        int offset = this.table.offset();
        int viewTop = offset - ROW;
        int viewBottom = offset + (bottom - bodyTop) + ROW;
        int hoverY = mouseX >= left && mouseX < right ? this.table.hoverY(mouseX, mouseY) : Integer.MIN_VALUE;
        RacePayloads.BoardRow hovered = null;
        int hoveredY = 0;
        long hoveredBest = 0L;
        int sticky = layout.sticky(offset);
        this.table.begin(g);
        if (sticky != BoardLayout.NONE) {
            g.enableScissor(left, bodyTop + ROW + offset, edge, bottom + offset);
        }
        if (sticky != BoardLayout.CLEAN) {
            this.extractHeader(g, layout, false, clean, left, right, bodyTop, offset, mouseX, mouseY);
        }
        if (layout.message() && layout.messageY() < viewBottom && layout.messageY() + ROW > viewTop) {
            ThemeText.fittedCentered(g, this.font, Component.translatable("propwash.race.board.assisted_only"), (left + right) / 2,
                    Metrics.textY(bodyTop + layout.messageY(), ROW), right - left - 2 * EMPTY_MARGIN, Palette.TEXT_MUTED);
        }
        for (boolean flagged : new boolean[]{false, true}) {
            if (!layout.has(flagged)) {
                continue;
            }
            BoardIndex index = flagged ? assisted : clean;
            if (flagged) {
                int separator = bodyTop + layout.headerY(true) - ASSIST_GAP / 2;
                if (separator >= bodyTop + viewTop && separator < bodyTop + viewBottom) {
                    ThemeDraw.separator(g, left, right, separator);
                }
                if (sticky != BoardLayout.ASSISTED) {
                    this.extractHeader(g, layout, true, assisted, left, right, bodyTop, offset, mouseX, mouseY);
                }
            }
            int[] visible = layout.visible(flagged, viewTop, viewBottom);
            if (visible.length == 0) {
                continue;
            }
            long best = index.best();
            for (int rank = visible[0]; rank <= visible[1]; rank++) {
                int y = bodyTop + layout.rowY(flagged, rank);
                RacePayloads.BoardRow row = index.row(rank);
                if (row == null) {
                    this.extractPlaceholder(g, rank, left, right, columns, y);
                    continue;
                }
                this.extractRow(g, row, rank, best, date, left, right, columns, y, rank - 1, flagged);
                if (hoverY >= y && hoverY < y + ROW) {
                    hovered = row;
                    hoveredY = y;
                    hoveredBest = best;
                }
                if (flagged) {
                    if (this.assistedDrawn == 0) {
                        this.assistedRowY = y - offset;
                    }
                    this.lastAssistedRowY = y - offset;
                    this.assistedDrawn++;
                }
            }
            ClientLeaderboards.ensure(this.courseId, this.filter, flagged, visible[0], visible[1]);
        }
        if (sticky != BoardLayout.NONE) {
            g.disableScissor();
        }
        this.table.end(g, bodyTop + layout.height());
        if (sticky != BoardLayout.NONE) {
            boolean flagged = sticky == BoardLayout.ASSISTED;
            this.extractHeaderAt(g, flagged, flagged ? assisted : clean, left, right, bodyTop, mouseX, mouseY, true);
        }
        if (this.headerAt(mouseX, mouseY) != null) {
            hovered = null;
        }
        if (hovered != null) {
            Component tip = this.rowTooltip(hovered, hoveredBest, columns, date);
            if (tip != null) {
                ThemeText.tooltipBelow(g, this.font, tip, TOOLTIP_WIDTH,
                        new ScreenRectangle(columns.nameX(), hoveredY - this.table.offset(), right - columns.nameX(), ROW));
            }
        } else {
            this.extractHeaderTooltip(g, mouseX, mouseY);
        }
    }

    private void applyScrollRequests(BoardLayout layout, int bodyTop) {
        if (this.jumpRank > 0) {
            int y = layout.rowY(this.jumpAssisted, this.jumpRank);
            this.jumpRank = 0;
            this.revealHeader = BoardLayout.NONE;
            if (y != Integer.MIN_VALUE) {
                this.table.reveal(bodyTop + y - 2 * ROW, 5 * ROW);
            }
            return;
        }
        if (this.revealHeader != BoardLayout.NONE) {
            boolean flagged = this.revealHeader == BoardLayout.ASSISTED;
            this.revealHeader = BoardLayout.NONE;
            if (layout.has(flagged) && this.table.offset() > layout.headerY(flagged)) {
                this.table.reveal(bodyTop + layout.headerY(flagged), ROW);
            }
        }
    }

    private void extractHeader(GuiGraphicsExtractor g, BoardLayout layout, boolean assisted, BoardIndex index, int left, int right, int bodyTop, int offset,
                               int mouseX, int mouseY) {
        int y = bodyTop + layout.headerY(assisted);
        if (y + ROW <= bodyTop + offset || y >= bodyTop + offset + (this.tableBottom - this.tableTop)) {
            return;
        }
        this.extractHeaderAt(g, assisted, index, left, right, y, mouseX, mouseY + offset, false);
        HeaderHit hit = this.headerHits.getLast();
        this.headerHits.set(this.headerHits.size() - 1, new HeaderHit(y - offset, hit.assisted(), hit.mineLeft(), hit.mineRight(), hit.rank()));
    }

    private void extractHeaderAt(GuiGraphicsExtractor g, boolean assisted, BoardIndex index, int left, int right, int y, int mouseX, int mouseY,
                                 boolean pinned) {
        g.fill(left, y, right, y + ROW, Palette.PANEL_HEADER);
        if (pinned) {
            g.fill(left, y + ROW - 1, right, y + ROW, Palette.BORDER);
        }
        boolean collapsed = BoardSections.collapsed(assisted);
        int color = assisted ? Palette.WARNING : Palette.TEXT;
        this.arrow(g, left + 4, y + (ROW - ARROW) / 2, collapsed, color);
        int textY = Metrics.textY(y, ROW);
        int rank = index.selfRank();
        int mineLeft = right;
        int mineRight = right;
        if (rank > 0) {
            Component mine = Component.translatable("propwash.race.board.mine", rank);
            int width = this.font.width(mine);
            mineRight = right - 4;
            mineLeft = Math.max(left + ARROW + 40, mineRight - width);
            boolean hovered = mouseX >= mineLeft && mouseX < mineRight && mouseY >= y && mouseY < y + ROW;
            ThemeText.fitted(g, this.font, mine, mineLeft, textY, mineRight - mineLeft, hovered ? Palette.ACCENT_HOVER : Palette.ACCENT);
        }
        Component label = Component.translatable("propwash.race.board.section_count",
                Component.translatable(assisted ? "propwash.race.board.assisted_section" : "propwash.race.board.records_section"), index.total());
        int labelX = left + 4 + ARROW + 5;
        ThemeText.fitted(g, this.font, label, labelX, textY, Math.max(10, mineLeft - MINE_GAP - labelX), color);
        this.headerHits.add(new HeaderHit(y, assisted, mineLeft, mineRight, rank));
    }

    private void arrow(GuiGraphicsExtractor g, int x, int y, boolean collapsed, int color) {
        for (int i = 0; i < ARROW / 2 + 1; i++) {
            if (collapsed) {
                g.fill(x + i, y + i, x + i + 1, y + ARROW - i, color);
            } else {
                g.fill(x + i, y + i + 1, x + ARROW - i, y + i + 2, color);
            }
        }
    }

    private void extractHeaderTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        HeaderHit hit = this.headerAt(mouseX, mouseY);
        if (hit == null) {
            return;
        }
        boolean mine = hit.rank() > 0 && mouseX >= hit.mineLeft() && mouseX < hit.mineRight();
        Component tip = mine ? Component.translatable("propwash.race.board.mine.tooltip", hit.rank())
                : Component.translatable(BoardSections.collapsed(hit.assisted()) ? "propwash.race.board.section.expand" : "propwash.race.board.section.collapse");
        ThemeText.tooltipBelow(g, this.font, tip, TOOLTIP_WIDTH, new ScreenRectangle(this.windowLeft + PAD, hit.y(), this.tableRight - this.windowLeft - PAD,
                ROW));
    }

    private void extractPlaceholder(GuiGraphicsExtractor g, int rank, int left, int right, Columns columns, int y) {
        if (rank % 2 == 0) {
            g.fill(left, y, right, y + ROW, Palette.ROW_STRIPE);
        }
        int textY = Metrics.textY(y, ROW);
        ThemeText.textRight(g, this.font, Integer.toString(rank), columns.nameX() - 6, textY, Palette.TEXT_DISABLED);
        ThemeText.text(g, this.font, "…", columns.nameX(), textY, Palette.TEXT_DISABLED);
        this.placeholdersDrawn++;
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

    private int nameNeed(BoardIndex clean, BoardIndex assisted) {
        if (clean == this.needClean && assisted == this.needAssisted) {
            return this.need;
        }
        int plain = 0;
        for (RacePayloads.BoardRow row : clean.rows()) {
            plain = Math.max(plain, this.font.width(row.name()));
        }
        int need = squeezed(plain);
        if (assisted.loaded() > 0) {
            int widest = 0;
            for (RacePayloads.BoardRow row : assisted.rows()) {
                widest = Math.max(widest, this.font.width(row.name()));
            }
            need = Math.max(need, this.badgeWidth() + 4 + squeezed(widest));
        }
        this.needClean = clean;
        this.needAssisted = assisted;
        this.need = need;
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
        List<ServerRulesBadge.Line> rules = ServerRulesText.entry(row.server());
        if (!rules.isEmpty()) {
            hidden = any ? hidden.append("\n").append(ServerRulesText.entryTooltip(rules)) : Component.empty().append(ServerRulesText.entryTooltip(rules));
            any = true;
        }
        if (!row.assisted()) {
            return any ? hidden : null;
        }
        MutableComponent tip = Component.translatable("propwash.race.board.assisted_tooltip", AssistText.storedReasons(row.reasons()));
        return any ? tip.append("\n").append(hidden) : tip;
    }

    private void extractRow(GuiGraphicsExtractor g, RacePayloads.BoardRow row, int rank, long best, DateTimeFormatter date, int left, int right,
                            Columns columns, int y, int index, boolean assisted) {
        this.rowHits.add(new RowHit(y - this.table.offset(), row));
        this.rowsDrawn++;
        Selection selection = this.selected;
        boolean picked = selection != null && selection.matches(row);
        if (row.self()) {
            ThemeDraw.selection(g, left, y, right, y + ROW, true, false);
        } else if (index % 2 == 1) {
            g.fill(left, y, right, y + ROW, Palette.ROW_STRIPE);
        }
        if (picked) {
            g.fill(left, y, right, y + 1, Palette.ACCENT);
            g.fill(left, y + ROW - 1, right, y + ROW, Palette.ACCENT);
            g.fill(left, y, left + 1, y + ROW, Palette.ACCENT);
            g.fill(right - 1, y, right, y + ROW, Palette.ACCENT);
        }
        int textY = Metrics.textY(y, ROW);
        int rankColor = assisted ? Palette.TEXT_MUTED : rank == 1 ? GOLD : rank == 2 ? SILVER : rank == 3 ? BRONZE : Palette.TEXT_MUTED;
        ThemeText.textRight(g, this.font, rank > 0 ? Integer.toString(rank) : "–", columns.nameX() - 6, textY, rankColor);
        int nameStart = columns.nameX();
        if (assisted) {
            nameStart += ThemeDraw.badge(g, this.font, Component.translatable("propwash.race.board.assisted_badge"), nameStart, y + 1, y + ROW - 1,
                    Palette.WARNING, Palette.BACKGROUND) + 4;
        }
        int nameRight = columns.nameRight();
        if (this.minecraft != null && row.ghost() && GhostChoices.INSTANCE.chosen(this.minecraft, this.courseId, row.key())) {
            Component mark = Component.translatable("propwash.race.ghosts.chosen_badge");
            int markWidth = this.font.width(mark) + 8;
            if (nameRight - nameStart > markWidth + 20) {
                nameRight -= markWidth + CHOSEN_GAP;
                ThemeDraw.badge(g, this.font, mark, nameRight + CHOSEN_GAP, y + 2, y + ROW - 2, Palette.ACCENT, Palette.BACKGROUND);
            }
        }
        if (!ServerRulesText.entry(row.server()).isEmpty()) {
            int nameNeed = Math.min(this.font.width(row.name()), NAME_KEEP);
            Component mark = ServerRulesText.label();
            if (nameRight - nameStart - this.font.width(mark) - 8 - CHOSEN_GAP < nameNeed) {
                mark = ServerRulesText.shortLabel();
            }
            int markWidth = this.font.width(mark) + 8;
            if (nameRight - nameStart - markWidth - CHOSEN_GAP >= Math.min(nameNeed, NAME_MIN)) {
                nameRight -= markWidth + CHOSEN_GAP;
                ServerRulesText.draw(g, this.font, mark, nameRight + CHOSEN_GAP, y + 2, y + ROW - 2);
                this.rulesBadgesDrawn++;
            }
        }
        ThemeText.fitted(g, this.font, row.name(), nameStart, textY, nameRight - nameStart, row.self() ? Palette.TEXT : Palette.TEXT_MUTED);
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
