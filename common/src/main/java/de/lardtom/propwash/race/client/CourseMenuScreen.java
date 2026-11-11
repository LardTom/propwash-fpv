package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.CourseAccess;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseEdits;
import de.lardtom.propwash.race.CourseLaps;
import de.lardtom.propwash.race.CourseMenu;
import de.lardtom.propwash.race.CourseNames;
import de.lardtom.propwash.race.CreativeFcRule;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GhostChoice;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import de.lardtom.propwash.ui.client.theme.WidgetViews;
import de.lardtom.propwash.ui.client.widget.ContentScroll;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ScrollArea;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CourseMenuScreen extends Screen implements ThemedScreen, WidgetViews {
    public static final String KEY = "propwash.race.course_menu.";
    private static final String EDIT = "propwash.race.course_edit.";
    private static final String ID_KEY = "propwash.race.course_edit.id";
    private static final String P2P = "propwash.race.p2p.";
    private static final int MAX_WIDTH = 400;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int PAD = 8;
    private static final int ROW = 18;
    private static final int SECTION = Metrics.SECTION_HEIGHT + 2;
    private static final int SECTION_GAP = 6;
    private static final int WRAP_PAD = 4;
    private static final int SMALL = 14;
    private static final int LAPS_VALUE = 84;
    private static final int LABEL_MIN = 40;
    private static final int LABEL_GAP = 8;
    private static final int CHIP_PAD = 12;
    private static final int BUTTON_PAD = 16;
    private static final int CONFIRM_TICKS = 80;
    private static final int COPIED_TICKS = 40;
    private static final int NAME_IDLE_TICKS = 16;
    private static final int LAPS_IDLE_TICKS = 6;
    private static final int REPLY_TICKS = 40;
    private static final int NO_LAPS = -2;
    private static final int REFRESH_TICKS = 10;

    private enum Kind {
        SECTION,
        TEXT,
        WRAP,
        NOTE,
        FIELD,
        LAPS,
        LAPS_HINT,
        LAPS_HINT_WRAP
    }

    private record Item(Kind kind, int y, int height, Component label, Component value, int color, int x, int right) {
    }

    private final @Nullable Screen parent;
    private final int courseId;
    private final List<Item> items = new ArrayList<>();
    private final List<AbstractWidget> content = new ArrayList<>();
    private final List<CourseMenu.Section> sections = new ArrayList<>();
    private final List<CourseMenu.Row> rows = new ArrayList<>();
    private final Map<CourseMenu.Row, AbstractWidget> controls = new EnumMap<>(CourseMenu.Row.class);
    private final Map<CreativeFcRule, FlatButton> creativeButtons = new EnumMap<>(CreativeFcRule.class);
    private @Nullable ContentScroll scroll;
    private @Nullable TextField nameField;
    private @Nullable FlatButton copyButton;
    private @Nullable FlatButton lapsModeButton;
    private @Nullable FlatButton pointModeButton;
    private @Nullable FlatButton lapsMinus;
    private @Nullable FlatButton lapsPlus;
    private @Nullable FlatButton doneButton;
    private String nameText;
    private boolean nameDirty;
    private int nameIdle;
    private int pendingLaps = NO_LAPS;
    private boolean lapsSent;
    private int lapsIdle;
    private @Nullable CourseMode pendingMode;
    private int modeIdle;
    private @Nullable Boolean pendingRaceLine;
    private int raceLineIdle;
    private @Nullable FlatButton raceLineOnButton;
    private @Nullable FlatButton raceLineOffButton;
    private @Nullable CreativeFcRule pendingCreative;
    private int creativeIdle;
    private int confirmDelete;
    private int confirmReset;
    private int copiedTicks;
    private int refreshTicks;
    private int seenCourses = -1;
    private int seenBoards = -1;
    private int seenChoices = -1;
    private int seenRules = -1;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int labelWidth;
    private long frames;

    public CourseMenuScreen(@Nullable Screen parent, int courseId) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.courseId = courseId;
        CourseData course = ClientCourses.get(courseId);
        this.nameText = course == null ? "" : course.name();
    }

    public static void open(@Nullable Screen parent, int courseId) {
        Minecraft.getInstance().gui.setScreen(new CourseMenuScreen(parent, courseId));
    }

    public static CourseMenu.Rights rights(Minecraft minecraft, CourseData course) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return CourseMenu.Rights.VIEW;
        }
        CourseAccess edit = CourseEditScreen.access(minecraft, course);
        CourseAccess delete = CourseEditScreen.canDelete(minecraft, course) ? CourseAccess.ALLOWED : CourseAccess.NOT_OWNER;
        boolean owner = player.getUUID().equals(course.owner());
        boolean gamemaster = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        return CourseMenu.Rights.of(edit, delete, owner, gamemaster, ClientWorldRules.INSTANCE.editable());
    }

    public int courseId() {
        return this.courseId;
    }

    public @Nullable Screen parent() {
        return this.parent;
    }

    public @Nullable CourseData course() {
        return ClientCourses.get(this.courseId);
    }

    public CourseMenu.Rights rights() {
        CourseData course = this.course();
        return course == null || this.minecraft == null ? CourseMenu.Rights.VIEW : rights(this.minecraft, course);
    }

    public boolean readOnly() {
        return !this.rights().edit();
    }

    public List<CourseMenu.Section> sections() {
        return List.copyOf(this.sections);
    }

    public List<CourseMenu.Row> rows() {
        return List.copyOf(this.rows);
    }

    public @Nullable AbstractWidget control(CourseMenu.Row row) {
        return this.controls.get(row);
    }

    public List<AbstractWidget> contentWidgets() {
        return List.copyOf(this.content);
    }

    public @Nullable ContentScroll scroll() {
        return this.scroll;
    }

    public @Nullable TextField nameField() {
        return this.nameField;
    }

    public @Nullable FlatButton copyButton() {
        return this.copyButton;
    }

    public @Nullable FlatButton lapsModeButton() {
        return this.lapsModeButton;
    }

    public @Nullable FlatButton pointModeButton() {
        return this.pointModeButton;
    }

    public @Nullable FlatButton lapsPlus() {
        return this.lapsPlus;
    }

    public @Nullable FlatButton lapsMinus() {
        return this.lapsMinus;
    }

    public @Nullable FlatButton doneButton() {
        return this.doneButton;
    }

    public @Nullable FlatButton creativeButton(CreativeFcRule rule) {
        return this.creativeButtons.get(rule);
    }

    public boolean shownRaceLine() {
        if (this.pendingRaceLine != null) {
            return this.pendingRaceLine;
        }
        CourseData course = this.course();
        return course == null || course.raceLine();
    }

    public void setRaceLine(boolean shown) {
        if (shown == this.shownRaceLine() || !ClientNet.canSend(RacePayloads.CourseEditC2S.TYPE)) {
            return;
        }
        ClientNet.sendToServer(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.RACE_LINE, shown ? 1 : 0, 0));
        this.pendingRaceLine = shown;
        this.raceLineIdle = 0;
    }

    public @Nullable FlatButton raceLineButton(boolean on) {
        return on ? this.raceLineOnButton : this.raceLineOffButton;
    }

    public CreativeFcRule shownCreativeFc() {
        if (this.pendingCreative != null) {
            return this.pendingCreative;
        }
        CourseData course = this.course();
        return course == null ? CreativeFcRule.WORLD : course.creativeFc();
    }

    public boolean setCreativeFc(CreativeFcRule rule) {
        if (!this.rights().admin() || rule == this.shownCreativeFc() || !ClientCreativeFc.send(this.courseId, rule)) {
            return false;
        }
        this.pendingCreative = rule;
        this.creativeIdle = 0;
        this.rebuildWidgets();
        return true;
    }

    public long frames() {
        return this.frames;
    }

    public boolean deleteArmed() {
        return this.confirmDelete > 0;
    }

    public boolean resetArmed() {
        return this.confirmReset > 0;
    }

    public boolean copied() {
        return this.copiedTicks > 0;
    }

    public CourseMode shownMode() {
        if (this.pendingMode != null) {
            return this.pendingMode;
        }
        CourseData course = this.course();
        return course == null ? CourseMode.LAPS : course.mode();
    }

    private boolean shownPointToPoint() {
        return this.shownMode() == CourseMode.POINT_TO_POINT;
    }

    public int shownLaps() {
        if (this.pendingLaps != NO_LAPS) {
            return this.pendingLaps;
        }
        CourseData course = this.course();
        return CourseLaps.resolve(course == null ? CourseLaps.UNSET : course.laps(), RaceConfig.CONFIG.get().laps);
    }

    private boolean personalLaps() {
        CourseData course = this.course();
        return this.pendingLaps == NO_LAPS && course != null && !CourseLaps.set(course.laps());
    }

    private void send(RacePayloads.CourseEditC2S payload) {
        if (ClientNet.canSend(RacePayloads.CourseEditC2S.TYPE)) {
            ClientNet.sendToServer(payload);
        }
    }

    public void rename(String name) {
        if (!this.rights().edit()) {
            return;
        }
        this.nameText = CourseNames.clean(name);
        this.nameDirty = false;
        this.nameIdle = 0;
        this.send(RacePayloads.CourseEditC2S.rename(this.courseId, this.nameText));
    }

    public void stepLaps(int direction) {
        if (!this.rights().edit()) {
            return;
        }
        this.pendingLaps = CourseLaps.clamp(CourseLaps.step(this.shownLaps(), direction));
        this.lapsIdle = 0;
        this.lapsSent = false;
    }

    private void sendLaps() {
        if (this.pendingLaps != NO_LAPS && !this.lapsSent) {
            this.send(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.LAPS, this.pendingLaps, 0));
            this.lapsSent = true;
            this.lapsIdle = 0;
        }
    }

    public void setMode(CourseMode mode) {
        if (!this.rights().edit() || mode == this.shownMode()) {
            return;
        }
        this.flush();
        this.pendingMode = mode;
        this.modeIdle = 0;
        this.send(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.MODE, mode.ordinal(), 0));
        this.rebuildWidgets();
    }

    public void flush() {
        if (this.nameDirty && this.nameField != null) {
            this.rename(this.nameField.getValue());
        }
        this.sendLaps();
    }

    public @Nullable String courseKey() {
        CourseData course = this.course();
        return course == null || course.key() == null ? null : course.key().toString();
    }

    public boolean copyKey() {
        String key = this.courseKey();
        if (key == null) {
            return false;
        }
        this.minecraft.keyboardHandler.setClipboard(key);
        this.copiedTicks = COPIED_TICKS;
        if (this.copyButton != null) {
            this.copyButton.setMessage(Component.translatable(ID_KEY + ".copied"));
        }
        return true;
    }

    public void openGates() {
        this.flush();
        if (this.parent instanceof CourseEditScreen edit && edit.courseId() == this.courseId) {
            this.minecraft.gui.setScreen(edit);
            return;
        }
        this.minecraft.gui.setScreen(new CourseEditScreen(this, this.courseId));
    }

    public void openBoard() {
        this.flush();
        if (this.parent instanceof LeaderboardScreen board && board.courseId() == this.courseId) {
            this.minecraft.gui.setScreen(board);
            return;
        }
        this.minecraft.gui.setScreen(new LeaderboardScreen(this, this.courseId));
    }

    public void bindTool() {
        if (!this.rights().edit()) {
            return;
        }
        this.flush();
        this.send(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.BIND_TOOL));
        this.minecraft.gui.setScreen(null);
    }

    public boolean reset() {
        LocalPlayer player = this.minecraft.player;
        if (!this.rights().reset() || player == null) {
            return false;
        }
        if (this.confirmReset <= 0) {
            this.confirmReset = CONFIRM_TICKS;
            this.rebuildWidgets();
            return false;
        }
        this.confirmReset = 0;
        player.connection.sendCommand(CourseMenu.resetCommand(this.courseId));
        ClientLeaderboards.request(this.courseId);
        this.refreshTicks = REFRESH_TICKS;
        this.rebuildWidgets();
        return true;
    }

    public boolean delete() {
        if (!this.rights().delete()) {
            return false;
        }
        if (this.confirmDelete <= 0) {
            this.confirmDelete = CONFIRM_TICKS;
            this.rebuildWidgets();
            return false;
        }
        this.nameDirty = false;
        this.pendingLaps = NO_LAPS;
        this.send(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.DELETE));
        this.minecraft.gui.setScreen(null);
        return true;
    }

    private Component courseLabel(CourseData course) {
        return CourseEditScreen.label(course);
    }

    private Component ownerName(CourseData course) {
        if (course.owner() == null) {
            return Component.translatable(KEY + "owner.none");
        }
        return course.ownerName().isEmpty() ? Component.translatable("propwash.race.server.unknown_owner") : Component.literal(course.ownerName());
    }

    private Component roleText(CourseMenu.Rights rights) {
        return Component.translatable(KEY + "rights." + rights.role().name().toLowerCase(Locale.ROOT));
    }

    private Component boardText() {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        if (board == null) {
            return Component.translatable("propwash.race.board.loading");
        }
        List<RacePayloads.BoardRow> ranking = ClientLeaderboards.ranking(board, null);
        int times = CourseMenu.times(board);
        if (times == 0 || ranking.isEmpty()) {
            return Component.translatable(times == 0 ? KEY + "board.empty" : KEY + "board.count", times);
        }
        RacePayloads.BoardRow best = ranking.getFirst();
        return Component.translatable(KEY + "board.value", times, RaceFormat.time(best.nanos()), best.name());
    }

    private Component opponent() {
        GhostChoice choice = GhostChoices.INSTANCE.get(this.minecraft, this.courseId);
        return switch (choice.mode()) {
            case OWN -> Component.translatable(KEY + "ghosts.own");
            case OFF -> Component.translatable(KEY + "ghosts.off");
            case BOARD -> choice.key() == null || choice.holder().isEmpty() ? Component.translatable(KEY + "ghosts.record")
                    : Component.literal(choice.holder());
        };
    }

    private Component ghostText() {
        RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(this.courseId);
        if (board == null) {
            return Component.translatable(KEY + "ghosts.opponent", this.opponent());
        }
        return Component.translatable(KEY + "ghosts.value", this.opponent(), allowed(board.mayReplayOthers()), allowed(board.mayRaceOthers()));
    }

    private static Component allowed(boolean allowed) {
        return Component.translatable(KEY + (allowed ? "ghosts.allowed" : "ghosts.denied"));
    }

    private int buttonWidth(Component... labels) {
        int width = 0;
        for (Component label : labels) {
            width = Math.max(width, this.font.width(label));
        }
        return width + BUTTON_PAD;
    }

    private int wrapHeight(Component text, int width) {
        return ThemeText.balanced(this.font, text, Math.max(20, width)).size() * Metrics.LINE_HEIGHT;
    }

    private static List<Component> labels() {
        List<Component> out = new ArrayList<>();
        for (String key : List.of(EDIT + "name", ID_KEY, KEY + "type", EDIT + "laps", P2P + "edit.finish_label", KEY + "gates", KEY + "tool",
                KEY + "owner", KEY + "rights", KEY + "board", KEY + "ghosts", CreativeFcRule.KEY + "label", KEY + "reset", KEY + "delete")) {
            out.add(Component.translatable(key));
        }
        return out;
    }

    @Override
    protected void init() {
        this.seenCourses = ClientCourses.version();
        this.seenBoards = ClientLeaderboards.version();
        this.seenChoices = GhostChoices.INSTANCE.version();
        this.seenRules = ClientWorldRules.INSTANCE.version();
        if (ClientLeaderboards.get(this.courseId) == null) {
            ClientLeaderboards.request(this.courseId);
        }
        double offset = this.scroll == null ? 0.0 : this.scroll.offset();
        int w = Math.min(this.width - 16, MAX_WIDTH);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        int right = this.windowRight - PAD;
        int contentHeight = this.layout(0, right, false);
        int maxHeight = this.height - 8;
        boolean scrolls = HEADER + PAD + contentHeight + FOOTER > maxHeight;
        if (scrolls) {
            right = this.windowRight - PAD - ScrollArea.GUTTER;
            contentHeight = this.layout(0, right, false);
        }
        int h = Math.min(maxHeight, HEADER + PAD + contentHeight + FOOTER);
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        int viewTop = this.windowTop + HEADER;
        int viewBottom = this.windowBottom - FOOTER;
        int bottom = viewTop + PAD / 2 + this.layout(viewTop + PAD / 2, right, true);
        int buttonY = this.windowBottom - FOOTER + (FOOTER - Metrics.CONTROL_HEIGHT) / 2;
        Component done = Component.translatable(EDIT + "done");
        int doneWidth = Math.max(64, this.font.width(done) + BUTTON_PAD);
        this.doneButton = this.addRenderableWidget(new FlatButton(this.windowRight - PAD - doneWidth, buttonY, doneWidth, Metrics.CONTROL_HEIGHT, done,
                FlatButton.Style.PRIMARY, this::onClose));
        this.scroll = null;
        if (scrolls) {
            ContentScroll content = new ContentScroll(new ScreenRectangle(this.windowLeft + 1, viewTop, w - 2, Math.max(0, viewBottom - viewTop)), offset);
            content.adopt(this.content);
            content.contentBottom(bottom);
            this.scroll = content;
        }
    }

    private int layout(int top, int right, boolean place) {
        this.items.clear();
        this.content.clear();
        this.sections.clear();
        this.rows.clear();
        this.controls.clear();
        if (place) {
            this.nameField = null;
            this.copyButton = null;
            this.lapsModeButton = null;
            this.pointModeButton = null;
            this.lapsMinus = null;
            this.lapsPlus = null;
            this.creativeButtons.clear();
        }
        CourseData course = this.course();
        int left = this.windowLeft + PAD;
        int widest = 0;
        for (Component label : labels()) {
            widest = Math.max(widest, this.font.width(label));
        }
        this.labelWidth = Math.max(LABEL_MIN, Math.min(widest + LABEL_GAP, (right - left) / 3));
        int valueLeft = left + this.labelWidth;
        int y = top;
        if (course == null) {
            return 0;
        }
        CourseMenu.Rights rights = rights(this.minecraft, course);
        CourseAccess access = CourseEditScreen.access(this.minecraft, course);
        if (!rights.edit()) {
            Component note = Component.translatable(KEY + "read_only", CourseEditScreen.denied(access, course));
            int height = this.wrapHeight(note, right - left);
            this.items.add(new Item(Kind.NOTE, y, height + WRAP_PAD, Component.empty(), note, Palette.WARNING, left, right));
            y += height + WRAP_PAD + 2;
        }
        CourseMenu.Section current = null;
        for (CourseMenu.Row row : CourseMenu.rows(this.shownPointToPoint())) {
            if (row.section() != current) {
                if (current != null) {
                    y += SECTION_GAP;
                }
                current = row.section();
                this.sections.add(current);
                Component title = Component.translatable(KEY + "section." + current.name().toLowerCase(Locale.ROOT));
                this.items.add(new Item(Kind.SECTION, y, SECTION, title, Component.empty(), Palette.ACCENT, left, right));
                y += SECTION;
            }
            this.rows.add(row);
            y = this.row(row, course, rights, y, left, valueLeft, right, place);
        }
        return y - top;
    }

    private int control(int rowTop) {
        return rowTop + (ROW - Metrics.CONTROL_HEIGHT) / 2;
    }

    private <T extends AbstractWidget> T put(CourseMenu.Row row, T widget, boolean place) {
        if (place) {
            this.addWidget(widget);
            this.content.add(widget);
        }
        this.controls.putIfAbsent(row, widget);
        return widget;
    }

    private int row(CourseMenu.Row row, CourseData course, CourseMenu.Rights rights, int y, int left, int valueLeft, int right, boolean place) {
        boolean active = CourseMenu.active(row, rights);
        int valueWidth = right - valueLeft;
        switch (row) {
            case NAME -> {
                this.items.add(new Item(Kind.TEXT, y, ROW, Component.translatable(EDIT + "name"), Component.empty(), Palette.TEXT, valueLeft, valueLeft));
                TextField name = new TextField(this.font, valueLeft, this.control(y) - 1, valueWidth, Metrics.CONTROL_HEIGHT + 2,
                        Component.translatable(EDIT + "name"));
                name.setMaxLength(CourseNames.MAX);
                name.hint(Component.translatable("propwash.race.course.number", this.courseId));
                name.setValue(this.nameDirty ? this.nameText : course.name());
                name.setEditable(active);
                name.setResponder(value -> {
                    CourseData now = this.course();
                    this.nameDirty = now != null && !CourseNames.clean(value).equals(now.name());
                    this.nameText = value;
                    this.nameIdle = 0;
                });
                name.setTooltip(Tooltip.create(Component.translatable(EDIT + "name.tooltip")));
                TextField placed = this.put(row, name, place);
                if (place) {
                    this.nameField = placed;
                }
                return y + ROW;
            }
            case KEY -> {
                Component copy = Component.translatable(this.copiedTicks > 0 ? ID_KEY + ".copied" : ID_KEY + ".copy");
                int copyWidth = this.buttonWidth(Component.translatable(ID_KEY + ".copy"), Component.translatable(ID_KEY + ".copied"));
                String key = course.key() == null ? null : course.key().toString();
                Component value = key == null ? Component.translatable(ID_KEY + ".none") : Component.literal(key);
                int color = key == null ? Palette.TEXT_FAINT : Palette.TEXT;
                int fieldRight = right - copyWidth - Metrics.GAP;
                boolean below = this.font.width(value) + 10 > fieldRight - valueLeft;
                FlatButton button = new FlatButton(below ? valueLeft : right - copyWidth, this.control(y), copyWidth, Metrics.CONTROL_HEIGHT, copy,
                        FlatButton.Style.NORMAL, this::copyKey);
                button.active = course.key() != null;
                button.setTooltip(Tooltip.create(Component.translatable(ID_KEY + ".copy.tooltip")));
                FlatButton placed = this.put(row, button, place);
                if (place) {
                    this.copyButton = placed;
                }
                if (!below) {
                    this.items.add(new Item(Kind.FIELD, y, ROW, Component.translatable(ID_KEY), value, color, valueLeft, fieldRight));
                    return y + ROW;
                }
                this.items.add(new Item(Kind.TEXT, y, ROW, Component.translatable(ID_KEY), Component.empty(), color, valueLeft, valueLeft));
                this.items.add(new Item(Kind.FIELD, y + ROW, ROW, Component.empty(), value, color, left, right));
                return y + 2 * ROW;
            }
            case MODE -> {
                this.items.add(new Item(Kind.TEXT, y, ROW, Component.translatable(KEY + "type"), Component.empty(), Palette.TEXT, valueLeft, valueLeft));
                Component laps = Component.translatable(P2P + "mode.laps");
                Component point = Component.translatable(P2P + "mode");
                int lapsWidth = this.font.width(laps) + CHIP_PAD;
                int pointWidth = this.font.width(point) + CHIP_PAD;
                FlatButton lapsButton = new FlatButton(valueLeft, this.control(y), lapsWidth, Metrics.CONTROL_HEIGHT, laps, FlatButton.Style.TAB,
                        () -> !this.shownPointToPoint(), () -> this.setMode(CourseMode.LAPS));
                lapsButton.active = active;
                lapsButton.setTooltip(Tooltip.create(Component.translatable(P2P + "mode.laps.tooltip")));
                FlatButton pointButton = new FlatButton(valueLeft + lapsWidth + 2, this.control(y), pointWidth, Metrics.CONTROL_HEIGHT, point,
                        FlatButton.Style.TAB, this::shownPointToPoint, () -> this.setMode(CourseMode.POINT_TO_POINT));
                pointButton.active = active;
                pointButton.setTooltip(Tooltip.create(Component.translatable(P2P + "mode.tooltip")));
                FlatButton a = this.put(row, lapsButton, place);
                FlatButton b = this.put(row, pointButton, place);
                if (place) {
                    this.lapsModeButton = a;
                    this.pointModeButton = b;
                }
                return y + ROW;
            }
            case LAPS -> {
                int box = Math.min(LAPS_VALUE, Math.max(40, valueWidth - 2 * SMALL));
                this.items.add(new Item(Kind.LAPS, y, ROW, Component.translatable(EDIT + "laps"), Component.empty(), Palette.TEXT, valueLeft + SMALL,
                        valueLeft + SMALL + box));
                FlatButton minus = new FlatButton(valueLeft, this.control(y), SMALL, Metrics.CONTROL_HEIGHT, Component.literal("−"),
                        FlatButton.Style.NORMAL, () -> this.stepLaps(-1));
                minus.active = active;
                minus.setTooltip(Tooltip.create(Component.translatable(EDIT + "laps.tooltip")));
                FlatButton plus = new FlatButton(valueLeft + SMALL + box, this.control(y), SMALL, Metrics.CONTROL_HEIGHT, Component.literal("+"),
                        FlatButton.Style.NORMAL, () -> this.stepLaps(1));
                plus.active = active;
                plus.setTooltip(Tooltip.create(Component.translatable(EDIT + "laps.tooltip")));
                FlatButton a = this.put(row, plus, place);
                FlatButton b = this.put(row, minus, place);
                if (place) {
                    this.lapsPlus = a;
                    this.lapsMinus = b;
                }
                Component counted = Component.translatable(EDIT + "laps.hint");
                Component endless = Component.translatable(EDIT + "laps.hint.endless");
                int hintLeft = valueLeft + 2 * SMALL + box + LABEL_GAP;
                if (hintLeft + Math.max(this.font.width(counted), this.font.width(endless)) <= right) {
                    this.items.add(new Item(Kind.LAPS_HINT, y, ROW, Component.empty(), Component.empty(), Palette.TEXT_FAINT, hintLeft, right));
                    return y + ROW;
                }
                int height = Math.max(this.wrapHeight(counted, valueWidth), this.wrapHeight(endless, valueWidth));
                this.items.add(new Item(Kind.LAPS_HINT_WRAP, y + ROW, height, Component.empty(), Component.empty(), Palette.TEXT_FAINT, valueLeft, right));
                return y + ROW + height + 2;
            }
            case FINISH -> {
                int gates = course.gates().size();
                boolean ready = gates >= 2;
                Component value = ready ? Component.translatable(P2P + "edit.finish", gates, GateMounting.elementName(course.gates().getLast().kind()))
                        : Component.translatable(P2P + "edit.finish_missing");
                return this.textRow(y, Component.translatable(P2P + "edit.finish_label"), value, ready ? Palette.TEXT : Palette.WARNING, valueLeft, right);
            }
            case GATES -> {
                Component label = Component.translatable(KEY + "gates.open");
                int width = this.buttonWidth(label);
                FlatButton button = new FlatButton(right - width, this.control(y), width, Metrics.CONTROL_HEIGHT, label, FlatButton.Style.NORMAL,
                        this::openGates);
                button.setTooltip(Tooltip.create(Component.translatable(KEY + "gates.open.tooltip")));
                this.put(row, button, place);
                Component value = Component.translatable(KEY + "gates.value", course.gates().size());
                return this.textRow(y, Component.translatable(KEY + "gates"), value, Palette.TEXT, valueLeft, right - width - Metrics.GAP);
            }
            case RACE_LINE -> {
                this.items.add(new Item(Kind.TEXT, y, ROW, Component.translatable(KEY + "race_line"), Component.empty(), Palette.TEXT, valueLeft, valueLeft));
                Component on = Component.translatable(KEY + "race_line.on");
                Component off = Component.translatable(KEY + "race_line.off");
                int onWidth = this.font.width(on) + CHIP_PAD;
                int offWidth = this.font.width(off) + CHIP_PAD;
                FlatButton onButton = new FlatButton(valueLeft, this.control(y), onWidth, Metrics.CONTROL_HEIGHT, on, FlatButton.Style.TAB,
                        this::shownRaceLine, () -> this.setRaceLine(true));
                onButton.active = active;
                onButton.setTooltip(Tooltip.create(Component.translatable(KEY + "race_line.on.tooltip")));
                FlatButton offButton = new FlatButton(valueLeft + onWidth + 2, this.control(y), offWidth, Metrics.CONTROL_HEIGHT, off, FlatButton.Style.TAB,
                        () -> !this.shownRaceLine(), () -> this.setRaceLine(false));
                offButton.active = active;
                offButton.setTooltip(Tooltip.create(Component.translatable(KEY + "race_line.off.tooltip")));
                FlatButton a = this.put(row, onButton, place);
                FlatButton b = this.put(row, offButton, place);
                if (place) {
                    this.raceLineOnButton = a;
                    this.raceLineOffButton = b;
                }
                return y + ROW;
            }
            case BIND -> {
                Component label = Component.translatable(EDIT + "bind");
                int width = Math.min(valueWidth, this.buttonWidth(label));
                FlatButton button = new FlatButton(valueLeft, this.control(y), width, Metrics.CONTROL_HEIGHT, label, FlatButton.Style.NORMAL,
                        this::bindTool);
                button.active = active;
                button.setTooltip(Tooltip.create(Component.translatable(EDIT + "bind.tooltip")));
                this.put(row, button, place);
                this.items.add(new Item(Kind.TEXT, y, ROW, Component.translatable(KEY + "tool"), Component.empty(), Palette.TEXT, valueLeft, valueLeft));
                return y + ROW;
            }
            case OWNER -> {
                return this.textRow(y, Component.translatable(KEY + "owner"), this.ownerName(course), Palette.TEXT, valueLeft, right);
            }
            case RIGHTS -> {
                int next = this.textRow(y, Component.translatable(KEY + "rights"), this.roleText(rights),
                        rights.edit() ? Palette.SUCCESS : Palette.WARNING, valueLeft, right);
                Component rule = Component.translatable(KEY + "rights.rule");
                int height = this.wrapHeight(rule, valueWidth);
                this.items.add(new Item(Kind.WRAP, next, height, Component.empty(), rule, Palette.TEXT_FAINT, valueLeft, right));
                return next + height + 2;
            }
            case BOARD -> {
                Component label = Component.translatable(KEY + "board.open");
                int width = this.buttonWidth(label);
                FlatButton button = new FlatButton(right - width, this.control(y), width, Metrics.CONTROL_HEIGHT, label, FlatButton.Style.NORMAL,
                        this::openBoard);
                button.setTooltip(Tooltip.create(Component.translatable(KEY + "board.open.tooltip")));
                this.put(row, button, place);
                return this.textRow(y, Component.translatable(KEY + "board"), this.boardText(), Palette.TEXT, valueLeft, right - width - Metrics.GAP);
            }
            case GHOSTS -> {
                return this.textRow(y, Component.translatable(KEY + "ghosts"), this.ghostText(), Palette.TEXT, valueLeft, right);
            }
            case CREATIVE_FC -> {
                return this.creativeRow(y, left, valueLeft, right, active, place);
            }
            case RESET -> {
                Component label = Component.translatable(this.confirmReset > 0 ? KEY + "reset.confirm" : KEY + "reset.button");
                int width = Math.min(valueWidth, this.buttonWidth(Component.translatable(KEY + "reset.button"), Component.translatable(KEY + "reset.confirm")));
                FlatButton button = new FlatButton(valueLeft, this.control(y), width, Metrics.CONTROL_HEIGHT, label, FlatButton.Style.DANGER,
                        this::reset);
                button.active = active;
                button.setTooltip(Tooltip.create(Component.translatable(active ? KEY + "reset.tooltip" : KEY + "reset.denied", this.courseId)));
                this.put(row, button, place);
                this.items.add(new Item(Kind.TEXT, y, ROW, Component.translatable(KEY + "reset"), Component.empty(), Palette.TEXT, valueLeft, valueLeft));
                return y + ROW;
            }
            case DELETE -> {
                Component label = Component.translatable(this.confirmDelete > 0 ? EDIT + "delete.confirm" : EDIT + "delete");
                int width = Math.min(valueWidth, this.buttonWidth(Component.translatable(EDIT + "delete"), Component.translatable(EDIT + "delete.confirm")));
                FlatButton button = new FlatButton(valueLeft, this.control(y), width, Metrics.CONTROL_HEIGHT, label, FlatButton.Style.DANGER,
                        this::delete);
                button.active = active;
                button.setTooltip(Tooltip.create(Component.translatable(EDIT + "delete.tooltip")));
                this.put(row, button, place);
                this.items.add(new Item(Kind.TEXT, y, ROW, Component.translatable(KEY + "delete"), Component.empty(), Palette.TEXT, valueLeft, valueLeft));
                Component hint = Component.translatable(active ? KEY + "delete.hint" : KEY + "delete.denied");
                int height = this.wrapHeight(hint, valueWidth);
                this.items.add(new Item(Kind.WRAP, y + ROW, height, Component.empty(), hint, Palette.TEXT_FAINT, valueLeft, right));
                return y + ROW + height + 2;
            }
            default -> {
                return y;
            }
        }
    }

    private int creativeRow(int y, int left, int valueLeft, int right, boolean active, boolean place) {
        String key = CreativeFcRule.KEY;
        boolean sendable = ClientCreativeFc.canSend();
        int total = 0;
        for (CreativeFcRule rule : CreativeFcRule.values()) {
            total += this.font.width(Component.translatable(rule.translationKey())) + CHIP_PAD + 2;
        }
        boolean compact = total > right - left;
        if (compact) {
            total = 0;
            for (CreativeFcRule rule : CreativeFcRule.values()) {
                total += this.font.width(Component.translatable(rule.translationKey() + ".short")) + CHIP_PAD + 2;
            }
        }
        boolean below = total > right - valueLeft;
        this.items.add(new Item(Kind.TEXT, y, ROW, Component.translatable(key + "label"), Component.empty(), Palette.TEXT, valueLeft, valueLeft));
        int chipsY = below ? y + ROW : y;
        int x = below ? left : valueLeft;
        for (CreativeFcRule rule : CreativeFcRule.values()) {
            Component text = Component.translatable(compact ? rule.translationKey() + ".short" : rule.translationKey());
            int width = this.font.width(text) + CHIP_PAD;
            FlatButton chip = new FlatButton(x, this.control(chipsY), width, Metrics.CONTROL_HEIGHT, text, FlatButton.Style.TAB,
                    () -> this.shownCreativeFc() == rule, () -> this.setCreativeFc(rule));
            chip.active = active && sendable;
            Component tip = Component.translatable(rule.translationKey() + ".tooltip");
            if (!active) {
                tip = Component.empty().append(tip).append("\n").append(Component.translatable(key + "denied"));
            } else if (!sendable) {
                tip = Component.empty().append(tip).append("\n").append(Component.translatable(key + "unsupported"));
            }
            chip.setTooltip(Tooltip.create(tip));
            FlatButton placed = this.put(CourseMenu.Row.CREATIVE_FC, chip, place);
            if (place) {
                this.creativeButtons.put(rule, placed);
            }
            x += width + 2;
        }
        int next = chipsY + ROW;
        Component state = ClientCreativeFc.state(this.courseId);
        Component hint = active ? state : Component.translatable(key + "state.read_only", state);
        int hintLeft = below ? left : valueLeft;
        int height = this.wrapHeight(hint, right - hintLeft);
        this.items.add(new Item(Kind.WRAP, next, height, Component.empty(), hint, Palette.TEXT_FAINT, hintLeft, right));
        return next + height + 2;
    }

    private int textRow(int y, Component label, Component value, int color, int valueLeft, int right) {
        int width = Math.max(20, right - valueLeft);
        if (this.font.width(value) <= width) {
            this.items.add(new Item(Kind.TEXT, y, ROW, label, value, color, valueLeft, right));
            return y + ROW;
        }
        int height = Math.max(ROW, this.wrapHeight(value, width) + WRAP_PAD);
        this.items.add(new Item(Kind.WRAP, y, height, label, value, color, valueLeft, right));
        return y + height;
    }

    @Override
    public void tick() {
        CourseData course = this.course();
        if (course == null) {
            this.nameDirty = false;
            this.pendingLaps = NO_LAPS;
            this.pendingMode = null;
            this.pendingCreative = null;
            this.pendingRaceLine = null;
            this.minecraft.gui.setScreen(null);
            return;
        }
        boolean rebuild = false;
        if (this.confirmDelete > 0 && --this.confirmDelete == 0) {
            rebuild = true;
        }
        if (this.confirmReset > 0 && --this.confirmReset == 0) {
            rebuild = true;
        }
        if (this.refreshTicks > 0 && --this.refreshTicks == 0) {
            ClientLeaderboards.request(this.courseId);
        }
        if (this.copiedTicks > 0 && --this.copiedTicks == 0 && this.copyButton != null) {
            this.copyButton.setMessage(Component.translatable(ID_KEY + ".copy"));
        }
        if (this.nameDirty && ++this.nameIdle >= NAME_IDLE_TICKS && this.nameField != null) {
            this.rename(this.nameField.getValue());
        }
        if (this.pendingLaps != NO_LAPS) {
            this.lapsIdle++;
            if (!this.lapsSent) {
                if (this.lapsIdle >= LAPS_IDLE_TICKS) {
                    this.sendLaps();
                }
            } else if (course.laps() == this.pendingLaps || this.lapsIdle >= REPLY_TICKS) {
                this.pendingLaps = NO_LAPS;
                rebuild = true;
            }
        }
        if (this.pendingMode != null && (course.mode() == this.pendingMode || ++this.modeIdle >= REPLY_TICKS)) {
            rebuild |= course.mode() != this.pendingMode;
            this.pendingMode = null;
        }
        if (this.pendingRaceLine != null && (course.raceLine() == this.pendingRaceLine || ++this.raceLineIdle >= REPLY_TICKS)) {
            rebuild |= course.raceLine() != this.pendingRaceLine;
            this.pendingRaceLine = null;
        }
        if (this.pendingCreative != null && (course.creativeFc() == this.pendingCreative || ++this.creativeIdle >= REPLY_TICKS)) {
            rebuild |= course.creativeFc() != this.pendingCreative;
            this.pendingCreative = null;
        }
        if (this.seenCourses != ClientCourses.version() || this.seenBoards != ClientLeaderboards.version()
                || this.seenChoices != GhostChoices.INSTANCE.version() || this.seenRules != ClientWorldRules.INSTANCE.version()) {
            rebuild = true;
        }
        if (rebuild) {
            boolean focused = this.nameField != null && this.nameField.isFocused();
            int cursor = this.nameField == null ? 0 : this.nameField.getCursorPosition();
            this.rebuildWidgets();
            if (focused && this.nameField != null) {
                this.setFocused(this.nameField);
                this.nameField.moveCursorTo(cursor, false);
            }
        }
    }

    @Override
    public @Nullable ScreenRectangle widgetView(AbstractWidget widget) {
        return this.scroll != null && this.content.contains(widget) ? this.scroll.view() : null;
    }

    private boolean insideView(double x, double y) {
        ContentScroll content = this.scroll;
        return content == null || content.view().containsPoint((int) x, (int) y);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.scroll != null && this.scroll.wheel(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ContentScroll content = this.scroll;
        if (content != null && content.mouseClicked(event.x(), event.y())) {
            return true;
        }
        if (this.insideView(event.x(), event.y())) {
            return super.mouseClicked(event, doubleClick);
        }
        List<AbstractWidget> hidden = new ArrayList<>();
        for (AbstractWidget widget : this.content) {
            if (widget.visible) {
                widget.visible = false;
                hidden.add(widget);
            }
        }
        try {
            return super.mouseClicked(event, doubleClick);
        } finally {
            for (AbstractWidget widget : hidden) {
                widget.visible = true;
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.scroll != null && this.scroll.mouseDragged(event.y()) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.scroll != null) {
            this.scroll.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && this.nameField != null && this.nameField.isFocused()) {
            this.rename(this.nameField.getValue());
            this.setFocused(null);
            return true;
        }
        if (super.keyPressed(event)) {
            return true;
        }
        ContentScroll content = this.scroll;
        if (content == null) {
            return false;
        }
        return switch (event.key()) {
            case InputConstants.KEY_PAGEDOWN -> content.page(1);
            case InputConstants.KEY_PAGEUP -> content.page(-1);
            case InputConstants.KEY_END -> content.edge(true);
            case InputConstants.KEY_HOME -> content.edge(false);
            default -> false;
        };
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        super.setFocused(focused);
        if (this.scroll != null && focused instanceof AbstractWidget widget && this.content.contains(widget)) {
            this.scroll.reveal(widget);
        }
    }

    @Override
    public void onClose() {
        this.flush();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.footerBar(g, this.windowLeft, this.windowBottom - FOOTER, this.windowRight, this.windowBottom);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.frames++;
        CourseData course = this.course();
        int titleRight = ThemeDraw.brandedHeader(g, this.font, this.windowLeft, this.windowTop, this.windowRight, HEADER, this.title,
                this.windowRight - PAD);
        if (course != null) {
            Component label = this.courseLabel(course);
            int room = this.windowRight - PAD - titleRight - 10;
            if (TextFit.mode(this.font.width(label), room) == TextFit.Mode.PLAIN) {
                ThemeDraw.headerSubtitle(g, this.font, titleRight, this.windowTop, HEADER, label, this.windowRight - PAD);
            }
        }
        ContentScroll content = this.scroll;
        int shift = content == null ? 0 : content.offset();
        boolean inside = this.insideView(mouseX, mouseY);
        int viewTop = this.windowTop + HEADER;
        int viewBottom = this.windowBottom - FOOTER;
        g.enableScissor(this.windowLeft + 1, viewTop, this.windowRight - 1, viewBottom);
        for (Item item : this.items) {
            this.extractItem(g, item, item.y() - shift);
        }
        for (AbstractWidget widget : this.content) {
            widget.extractRenderState(g, inside ? mouseX : -1, inside ? mouseY : -1, a);
        }
        if (content != null) {
            content.extractScrollbar(g);
        }
        g.disableScissor();
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private Component lapsHint() {
        return Component.translatable(this.shownLaps() == CourseLaps.ENDLESS ? EDIT + "laps.hint.endless" : EDIT + "laps.hint");
    }

    private void label(GuiGraphicsExtractor g, Component label, int y) {
        if (ThemeText.shows(label)) {
            ThemeText.fitted(g, this.font, label, this.windowLeft + PAD, Metrics.textY(y, ROW), Math.max(20, this.labelWidth - 4), Palette.TEXT_MUTED);
        }
    }

    private void extractItem(GuiGraphicsExtractor g, Item item, int y) {
        int left = this.windowLeft + PAD;
        switch (item.kind()) {
            case SECTION -> ThemeDraw.sectionTitle(g, this.font, item.label(), left, y + 2, item.right() - left);
            case NOTE -> {
                g.fill(left - 2, y, item.right() + 2, y + item.height(), Palette.withAlpha(Palette.WARNING, 0x22));
                ThemeText.wrapped(g, this.font, item.value(), left, y + WRAP_PAD / 2, item.right() - left, item.color());
            }
            case TEXT -> {
                this.label(g, item.label(), y);
                if (ThemeText.shows(item.value())) {
                    ThemeText.fitted(g, this.font, item.value(), item.x(), Metrics.textY(y, ROW), Math.max(20, item.right() - item.x()), item.color());
                }
            }
            case WRAP -> {
                this.label(g, item.label(), y);
                int textTop = ThemeText.shows(item.label()) ? Metrics.textY(y, ROW) : y;
                ThemeText.wrapped(g, this.font, item.value(), item.x(), textTop, Math.max(20, item.right() - item.x()), item.color());
            }
            case FIELD -> {
                this.label(g, item.label(), y);
                int top = this.control(y);
                ThemeDraw.field(g, item.x(), top, item.right(), top + Metrics.CONTROL_HEIGHT, WidgetState.IDLE);
                ThemeText.fitted(g, this.font, item.value(), item.x() + 5, Metrics.textY(y, ROW), Math.max(20, item.right() - item.x() - 10), item.color());
            }
            case LAPS -> {
                this.label(g, item.label(), y);
                int top = this.control(y);
                ThemeDraw.field(g, item.x() + 2, top, item.right() - 2, top + Metrics.CONTROL_HEIGHT, WidgetState.IDLE);
                Component value = CourseEditScreen.lapsLabel(this.shownLaps());
                if (this.personalLaps()) {
                    value = Component.translatable(EDIT + "laps.personal", value);
                }
                int width = item.right() - item.x();
                ThemeText.textCentered(g, this.font, ThemeText.fit(this.font, value, width - 10), item.x() + width / 2, Metrics.textY(y, ROW),
                        this.personalLaps() ? Palette.TEXT_MUTED : Palette.TEXT);
            }
            case LAPS_HINT -> ThemeText.text(g, this.font, this.lapsHint(), item.x(), Metrics.textY(y, ROW), item.color());
            case LAPS_HINT_WRAP -> ThemeText.wrapped(g, this.font, this.lapsHint(), item.x(), y, Math.max(20, item.right() - item.x()), item.color());
        }
    }
}
