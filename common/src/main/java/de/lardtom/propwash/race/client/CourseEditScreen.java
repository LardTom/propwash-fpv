package de.lardtom.propwash.race.client;

import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.CourseAccess;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseEdits;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseLaps;
import de.lardtom.propwash.race.CourseNames;
import de.lardtom.propwash.race.CourseOrder;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.RaceCommands;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.WidgetState;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.RowScroll;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class CourseEditScreen extends Screen implements ThemedScreen {
    private static final String KEY = "propwash.race.course_edit.";
    private static final int MAX_WIDTH = 440;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int PAD = 8;
    private static final int FORM_ROW = 18;
    private static final int ROW = Metrics.TABLE_ROW;
    private static final int LIST_HEAD = Metrics.SECTION_HEIGHT;
    private static final int HINT_LINES = 3;
    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 12;
    private static final int SMALL = 14;
    private static final int LABEL = 52;
    private static final int LAPS_VALUE = 132;
    private static final int LAPS_HINT_LEFT = LABEL + SMALL + LAPS_VALUE + SMALL + 8;
    private static final int NAME_IDLE_TICKS = 16;
    private static final int LAPS_IDLE_TICKS = 6;
    private static final int LAPS_REPLY_TICKS = 40;
    private static final int CONFIRM_TICKS = 80;
    private static final int NO_LAPS = -2;

    private final @Nullable Screen parent;
    private final int courseId;
    private @Nullable TextField nameField;
    private String nameText = "";
    private boolean nameDirty;
    private int nameIdle;
    private int pendingLaps = NO_LAPS;
    private int lapsIdle;
    private boolean lapsSent;
    private int firstRow;
    private int visibleRows = MIN_ROWS;
    private int lapsHintHeight;
    private int confirmDelete;
    private int seenVersion = -1;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int listTop;
    private long frames;
    private final RowScroll gateScroll = new RowScroll();

    public CourseEditScreen(@Nullable Screen parent, int courseId) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.courseId = courseId;
        CourseData course = ClientCourses.get(courseId);
        this.nameText = course == null ? "" : course.name();
    }

    public static void open(@Nullable Screen parent, int courseId) {
        Minecraft.getInstance().gui.setScreen(new CourseEditScreen(parent, courseId));
    }

    public static Component label(CourseData course) {
        return course.named() ? Component.literal(course.name()) : Component.translatable("propwash.race.course.number", course.id());
    }

    public static Component label(int courseId) {
        CourseData course = ClientCourses.get(courseId);
        return course == null ? Component.translatable("propwash.race.course.number", courseId) : label(course);
    }

    public static Component ref(int courseId) {
        CourseData course = ClientCourses.get(courseId);
        return Component.literal(course != null && course.named() ? course.name() : Integer.toString(courseId));
    }

    public static Component lapsLabel(int laps) {
        if (laps == CourseLaps.ENDLESS) {
            return Component.translatable(KEY + "laps.endless");
        }
        return Component.translatable(laps == 1 ? KEY + "laps.one" : KEY + "laps.value", laps);
    }

    public static CourseAccess access(Minecraft minecraft, CourseData course) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !ClientNet.canSend(RacePayloads.CourseEditC2S.TYPE)) {
            return CourseAccess.NO_BUILD;
        }
        boolean near = false;
        for (CourseData.Gate gate : course.gates()) {
            near |= CourseAccess.near(player.distanceToSqr(gate.x(), gate.y(), gate.z()), RaceCommands.NEAREST_COURSE_RANGE);
        }
        return CourseAccess.edit(build(player), sameDimension(minecraft, course), course.owner() == null, player.getUUID().equals(course.owner()),
                player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), near);
    }

    public static boolean canEdit(Minecraft minecraft, CourseData course) {
        return access(minecraft, course).allowed();
    }

    private static boolean build(LocalPlayer player) {
        return !player.isSpectator() && player.mayBuild();
    }

    private static boolean sameDimension(Minecraft minecraft, CourseData course) {
        return minecraft.level != null && course.dimension().equals(minecraft.level.dimension().identifier().toString());
    }

    public static boolean canDelete(Minecraft minecraft, CourseData course) {
        LocalPlayer player = minecraft.player;
        if (player == null || !ClientNet.canSend(RacePayloads.CourseEditC2S.TYPE)) {
            return false;
        }
        boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || minecraft.hasSingleplayerServer();
        return CourseAccess.delete(build(player), sameDimension(minecraft, course), player.getUUID().equals(course.owner()), operator, false).allowed();
    }

    public static Component denied(CourseAccess access, CourseData course) {
        Component owner = course.ownerName().isEmpty() ? Component.translatable("propwash.race.server.unknown_owner")
                : Component.literal(course.ownerName());
        return Component.translatable(KEY + "denied." + access.name().toLowerCase(Locale.ROOT), label(course), owner,
                (int) RaceCommands.NEAREST_COURSE_RANGE);
    }

    public int courseId() {
        return this.courseId;
    }

    public @Nullable CourseData course() {
        return ClientCourses.get(this.courseId);
    }

    public long frames() {
        return this.frames;
    }

    public int visibleRows() {
        return this.visibleRows;
    }

    public int firstRow() {
        return this.firstRow;
    }

    public boolean editable() {
        CourseData course = this.course();
        return course != null && canEdit(this.minecraft, course);
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
        this.nameText = CourseNames.clean(name);
        this.nameDirty = false;
        this.nameIdle = 0;
        this.send(RacePayloads.CourseEditC2S.rename(this.courseId, this.nameText));
    }

    public void stepLaps(int direction) {
        this.setLaps(CourseLaps.step(this.shownLaps(), direction));
    }

    public void setLaps(int laps) {
        this.pendingLaps = CourseLaps.clamp(laps);
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

    public void flush() {
        if (this.nameDirty && this.nameField != null) {
            this.rename(this.nameField.getValue());
        }
        this.sendLaps();
    }

    public void move(int from, int to) {
        this.send(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.MOVE, from, to));
    }

    public void makeStart(int index) {
        this.send(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.START, index, 0));
        this.firstRow = 0;
    }

    public void remove(int index) {
        this.send(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.REMOVE, index, 0));
    }

    public void bindTool() {
        this.flush();
        this.send(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.BIND_TOOL));
        this.minecraft.gui.setScreen(null);
    }

    public void delete() {
        if (this.confirmDelete <= 0) {
            this.confirmDelete = CONFIRM_TICKS;
            this.rebuildWidgets();
            return;
        }
        this.nameDirty = false;
        this.pendingLaps = NO_LAPS;
        this.send(new RacePayloads.CourseEditC2S(this.courseId, CourseEdits.Action.DELETE));
        this.minecraft.gui.setScreen(null);
    }

    public boolean deleteArmed() {
        return this.confirmDelete > 0;
    }

    private Component rowText(CourseData.Gate gate) {
        CourseElement kind = gate.kind();
        Component name = GateMounting.elementName(kind);
        if (kind == CourseElement.GATE) {
            return Component.translatable(KEY + "row.gate", name, blocks(gate.width()), blocks(gate.height()));
        }
        if (kind == CourseElement.FREEFORM) {
            return Component.translatable(KEY + "row.gate", name, blocks(gate.width()), blocks(gate.height()));
        }
        return Component.translatable(KEY + "row.element", name, GateMounting.variantName(kind, gate.variant()));
    }

    private static String blocks(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        return rounded == Math.rint(rounded) ? Integer.toString((int) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    private Component hint(@Nullable CourseData course) {
        if (course == null) {
            return Component.empty();
        }
        if (!access(this.minecraft, course).allowed()) {
            return denied(access(this.minecraft, course), course);
        }
        return Component.translatable(KEY + "hint.layout");
    }

    private int formTop() {
        return this.windowTop + HEADER + PAD;
    }

    @Override
    protected void init() {
        this.seenVersion = ClientCourses.version();
        CourseData course = this.course();
        int gates = course == null ? 0 : course.gates().size();
        int w = Math.min(this.width - 16, MAX_WIDTH);
        this.lapsHintHeight = 0;
        int hintLines = 0;
        for (String key : List.of(KEY + "laps.hint", KEY + "laps.hint.endless")) {
            Component hint = Component.translatable(key);
            if (this.font.width(hint) > w - 2 * PAD - LAPS_HINT_LEFT) {
                hintLines = Math.max(hintLines, this.font.split(hint, Math.max(20, w - 2 * PAD - LABEL)).size());
            }
        }
        if (hintLines > 0) {
            this.lapsHintHeight = hintLines * Metrics.LINE_HEIGHT + 2;
        }
        int helpLines = Math.max(HINT_LINES, this.font.split(this.hint(course), Math.max(20, w - 2 * PAD)).size());
        int fixed = HEADER + PAD + 2 * FORM_ROW + this.lapsHintHeight + 4 + LIST_HEAD + 4 + helpLines * Metrics.LINE_HEIGHT + PAD + FOOTER;
        int room = Math.max(MIN_ROWS * ROW, this.height - 12 - fixed);
        this.visibleRows = Math.max(MIN_ROWS, Math.min(Math.min(MAX_ROWS, room / ROW), gates));
        int h = Math.min(this.height - 4, fixed + this.visibleRows * ROW);
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        this.firstRow = Math.max(0, Math.min(this.firstRow, Math.max(0, gates - this.visibleRows)));
        boolean editable = course != null && canEdit(this.minecraft, course);
        int left = this.windowLeft + PAD;
        int right = this.windowRight - PAD;
        int y = this.formTop();
        TextField name = new TextField(this.font, left + LABEL, y + (FORM_ROW - Metrics.CONTROL_HEIGHT) / 2 - 1, right - left - LABEL,
                Metrics.CONTROL_HEIGHT + 2, Component.translatable(KEY + "name"));
        name.setMaxLength(CourseNames.MAX);
        name.hint(Component.translatable("propwash.race.course.number", this.courseId));
        name.setValue(this.nameDirty || course == null ? this.nameText : course.name());
        name.setEditable(editable);
        name.setResponder(value -> {
            CourseData current = this.course();
            this.nameDirty = current != null && !CourseNames.clean(value).equals(current.name());
            this.nameText = value;
            this.nameIdle = 0;
        });
        name.setTooltip(Tooltip.create(Component.translatable(KEY + "name.tooltip")));
        this.nameField = this.addRenderableWidget(name);
        y += FORM_ROW;
        int lapsY = y + (FORM_ROW - Metrics.CONTROL_HEIGHT) / 2;
        FlatButton minus = new FlatButton(left + LABEL, lapsY, SMALL, Metrics.CONTROL_HEIGHT, Component.literal("−"), FlatButton.Style.NORMAL,
                () -> this.stepLaps(-1));
        minus.active = editable;
        minus.setTooltip(Tooltip.create(Component.translatable(KEY + "laps.tooltip")));
        this.addRenderableWidget(minus);
        FlatButton plus = new FlatButton(left + LABEL + SMALL + LAPS_VALUE, lapsY, SMALL, Metrics.CONTROL_HEIGHT, Component.literal("+"),
                FlatButton.Style.NORMAL, () -> this.stepLaps(1));
        plus.active = editable;
        plus.setTooltip(Tooltip.create(Component.translatable(KEY + "laps.tooltip")));
        this.addRenderableWidget(plus);
        y += FORM_ROW + this.lapsHintHeight + 4 + LIST_HEAD;
        this.listTop = y;
        this.gateScroll.layout(right - Metrics.SCROLLBAR_WIDTH, this.listTop, ROW, gates, this.visibleRows);
        if (course != null) {
            List<CourseElement> kinds = course.kinds();
            Component startLabel = Component.translatable(KEY + "start");
            int startWidth = this.font.width(startLabel) + 10;
            int buttons = 3 * SMALL + startWidth + 3 * 2;
            int bx = right - Metrics.CONTENT_INSET - buttons;
            int last = Math.min(gates, this.firstRow + this.visibleRows);
            for (int i = this.firstRow; i < last; i++) {
                int index = i;
                int by = this.listTop + (i - this.firstRow) * ROW + (ROW - Metrics.CONTROL_HEIGHT) / 2;
                FlatButton up = new FlatButton(bx, by, SMALL, Metrics.CONTROL_HEIGHT, Component.literal("↑"), FlatButton.Style.NORMAL,
                        () -> this.move(index, index - 1));
                up.active = editable && CourseOrder.canMove(kinds, index, index - 1);
                up.setTooltip(Tooltip.create(Component.translatable(KEY + "up.tooltip")));
                this.addRenderableWidget(up);
                FlatButton down = new FlatButton(bx + SMALL + 2, by, SMALL, Metrics.CONTROL_HEIGHT, Component.literal("↓"), FlatButton.Style.NORMAL,
                        () -> this.move(index, index + 1));
                down.active = editable && CourseOrder.canMove(kinds, index, index + 1);
                down.setTooltip(Tooltip.create(Component.translatable(KEY + "down.tooltip")));
                this.addRenderableWidget(down);
                if (index > 0) {
                    FlatButton start = new FlatButton(bx + 2 * (SMALL + 2), by, startWidth, Metrics.CONTROL_HEIGHT, startLabel, FlatButton.Style.NORMAL,
                            () -> this.makeStart(index));
                    start.active = editable && CourseOrder.canStart(kinds, index);
                    start.setTooltip(Tooltip.create(Component.translatable(start.active || !editable ? KEY + "start.tooltip"
                            : KEY + "start.blocked.tooltip")));
                    this.addRenderableWidget(start);
                }
                FlatButton remove = new FlatButton(bx + 2 * (SMALL + 2) + startWidth + 2, by, SMALL, Metrics.CONTROL_HEIGHT, Component.literal("×"),
                        FlatButton.Style.DANGER, () -> this.remove(index));
                remove.active = editable && CourseOrder.canRemove(kinds, index);
                remove.setTooltip(Tooltip.create(Component.translatable(KEY + "remove.tooltip")));
                this.addRenderableWidget(remove);
            }
        }
        int buttonY = this.windowBottom - FOOTER + (FOOTER - Metrics.CONTROL_HEIGHT) / 2;
        Component done = Component.translatable(KEY + "done");
        int doneWidth = Math.max(64, this.font.width(done) + 16);
        this.addRenderableWidget(new FlatButton(right - doneWidth, buttonY, doneWidth, Metrics.CONTROL_HEIGHT, done, FlatButton.Style.PRIMARY,
                this::onClose));
        Component bind = Component.translatable(KEY + "bind");
        int bindWidth = this.font.width(bind) + 16;
        FlatButton bindButton = new FlatButton(right - doneWidth - Metrics.GAP - bindWidth, buttonY, bindWidth, Metrics.CONTROL_HEIGHT, bind,
                FlatButton.Style.NORMAL, this::bindTool);
        bindButton.active = editable;
        bindButton.setTooltip(Tooltip.create(Component.translatable(KEY + "bind.tooltip")));
        this.addRenderableWidget(bindButton);
        Component delete = Component.translatable(this.confirmDelete > 0 ? KEY + "delete.confirm" : KEY + "delete");
        int deleteWidth = Math.max(this.font.width(Component.translatable(KEY + "delete")), this.font.width(Component.translatable(KEY + "delete.confirm")))
                + 16;
        FlatButton deleteButton = new FlatButton(left, buttonY, deleteWidth, Metrics.CONTROL_HEIGHT, delete, FlatButton.Style.DANGER, this::delete);
        deleteButton.active = course != null && canDelete(this.minecraft, course);
        deleteButton.setTooltip(Tooltip.create(Component.translatable(KEY + "delete.tooltip")));
        this.addRenderableWidget(deleteButton);
    }

    @Override
    public void tick() {
        CourseData course = this.course();
        if (course == null) {
            this.nameDirty = false;
            this.pendingLaps = NO_LAPS;
            this.minecraft.gui.setScreen(null);
            return;
        }
        if (this.confirmDelete > 0 && --this.confirmDelete == 0) {
            this.rebuildWidgets();
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
            } else if (course.laps() == this.pendingLaps || this.lapsIdle >= LAPS_REPLY_TICKS) {
                this.pendingLaps = NO_LAPS;
            }
        }
        if (this.seenVersion != ClientCourses.version()) {
            this.seenVersion = ClientCourses.version();
            boolean focused = this.nameField != null && this.nameField.isFocused();
            this.rebuildWidgets();
            if (focused && this.nameField != null) {
                this.setFocused(this.nameField);
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && this.nameField != null && this.nameField.isFocused()) {
            this.rename(this.nameField.getValue());
            this.setFocused(null);
            return true;
        }
        return super.keyPressed(event) || this.scrollGates(this.gateScroll.key(event.key(), this.firstRow));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.scrollGates(this.gateScroll.grab(event.x(), event.y())) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.scrollGates(this.gateScroll.drag(event.y())) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.gateScroll.release();
        return super.mouseReleased(event);
    }

    private boolean scrollGates(int row) {
        if (row == RowScroll.NONE) {
            return false;
        }
        if (row != this.firstRow) {
            this.scrollTo(row);
        }
        return true;
    }

    public RowScroll gateScroll() {
        return this.gateScroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        CourseData course = this.course();
        int gates = course == null ? 0 : course.gates().size();
        int max = Math.max(0, gates - this.visibleRows);
        int next = Math.max(0, Math.min(max, this.firstRow - (int) Math.signum(scrollY)));
        if (next != this.firstRow && mouseY >= this.listTop && mouseY < this.listTop + this.visibleRows * ROW) {
            this.firstRow = next;
            this.rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void scrollTo(int row) {
        CourseData course = this.course();
        int gates = course == null ? 0 : course.gates().size();
        this.firstRow = Math.max(0, Math.min(Math.max(0, gates - this.visibleRows), row));
        this.rebuildWidgets();
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
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.frames++;
        CourseData course = this.course();
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, HEADER);
        int textY = Metrics.textY(this.windowTop, HEADER - 1);
        int titleX = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, titleX - 5, this.windowTop + 5, this.windowTop + HEADER - 6);
        ThemeText.text(g, this.font, this.title, titleX, textY, Palette.TEXT);
        int left = this.windowLeft + PAD;
        int right = this.windowRight - PAD;
        if (course != null) {
            Component owner = course.ownerName().isEmpty() ? Component.translatable("propwash.race.server.unknown_owner")
                    : Component.literal(course.ownerName());
            Component info = Component.translatable(KEY + "info", course.id(), course.gates().size(), owner);
            int infoWidth = this.windowRight - 8 - (titleX + this.font.width(this.title) + 12);
            if (TextFit.mode(this.font.width(info), infoWidth) == TextFit.Mode.CUT) {
                info = Component.translatable(KEY + "info.short", course.id(), course.gates().size());
            }
            if (TextFit.mode(this.font.width(info), infoWidth) != TextFit.Mode.CUT) {
                ThemeText.fittedRight(g, this.font, info, this.windowRight - 8, textY, infoWidth, Palette.TEXT_FAINT);
            }
        }
        int y = this.formTop();
        ThemeText.text(g, this.font, Component.translatable(KEY + "name"), left, Metrics.textY(y, FORM_ROW), Palette.TEXT_MUTED);
        y += FORM_ROW;
        ThemeText.text(g, this.font, Component.translatable(KEY + "laps"), left, Metrics.textY(y, FORM_ROW), Palette.TEXT_MUTED);
        Component laps = lapsLabel(this.shownLaps());
        if (this.personalLaps()) {
            laps = Component.translatable(KEY + "laps.personal", laps);
        }
        int valueLeft = left + LABEL + SMALL;
        ThemeDraw.field(g, valueLeft + 2, y + (FORM_ROW - Metrics.CONTROL_HEIGHT) / 2, valueLeft + LAPS_VALUE - 2,
                y + (FORM_ROW - Metrics.CONTROL_HEIGHT) / 2 + Metrics.CONTROL_HEIGHT, WidgetState.IDLE);
        ThemeText.textCentered(g, this.font, ThemeText.fit(this.font, laps, LAPS_VALUE - 10), valueLeft + LAPS_VALUE / 2, Metrics.textY(y, FORM_ROW),
                this.personalLaps() ? Palette.TEXT_MUTED : Palette.TEXT);
        Component lapsHint = Component.translatable(this.shownLaps() == CourseLaps.ENDLESS ? KEY + "laps.hint.endless" : KEY + "laps.hint");
        if (this.lapsHintHeight > 0) {
            ThemeText.wrapped(g, this.font, lapsHint, left + LABEL, y + FORM_ROW, Math.max(20, right - left - LABEL), Palette.TEXT_FAINT);
        } else {
            ThemeText.text(g, this.font, lapsHint, left + LAPS_HINT_LEFT, Metrics.textY(y, FORM_ROW), Palette.TEXT_FAINT);
        }
        y += FORM_ROW + this.lapsHintHeight + 4;
        int gates = course == null ? 0 : course.gates().size();
        ThemeDraw.sectionTitle(g, this.font, Component.translatable(KEY + "gates", gates), left, y, right - left);
        int listBottom = this.listTop + this.visibleRows * ROW;
        if (course != null) {
            this.extractRows(g, course, left, right, mouseX, mouseY);
        }
        this.gateScroll.extract(g, this.firstRow);
        ThemeDraw.separator(g, left, right, listBottom + 1);
        boolean denied = course != null && !access(this.minecraft, course).allowed();
        ThemeText.wrapped(g, this.font, this.hint(course), left, listBottom + 5, right - left, denied ? Palette.WARNING : Palette.TEXT_FAINT);
        int footerTop = this.windowBottom - FOOTER;
        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }

    private void extractRows(GuiGraphicsExtractor g, CourseData course, int left, int right, int mouseX, int mouseY) {
        List<CourseData.Gate> gates = course.gates();
        if (gates.isEmpty()) {
            ThemeText.text(g, this.font, Component.translatable(KEY + "hint.empty"), left + 4, this.listTop + 4, Palette.TEXT_FAINT);
            return;
        }
        Component startLabel = Component.translatable(KEY + "start");
        int buttons = 3 * SMALL + this.font.width(startLabel) + 10 + 3 * 2;
        int textRight = right - Metrics.CONTENT_INSET - buttons - 6;
        int positionWidth = this.font.width("-00000 000 -00000");
        int widestName = 0;
        for (CourseData.Gate gate : gates) {
            widestName = Math.max(widestName, this.font.width(this.rowText(gate)));
        }
        int badge = this.font.width(Component.translatable(KEY + "start_badge")) + 14;
        boolean showPosition = textRight - left > 250 && textRight - positionWidth - 8 - (left + 26 + badge) >= widestName;
        int last = Math.min(gates.size(), this.firstRow + this.visibleRows);
        for (int i = this.firstRow; i < last; i++) {
            CourseData.Gate gate = gates.get(i);
            int rowTop = this.listTop + (i - this.firstRow) * ROW;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= rowTop && mouseY < rowTop + ROW;
            if ((i & 1) == 1) {
                g.fill(left, rowTop, right - Metrics.CONTENT_INSET + 2, rowTop + ROW, Palette.ROW_STRIPE);
            }
            if (hovered) {
                g.fill(left, rowTop, right - Metrics.CONTENT_INSET + 2, rowTop + ROW, Palette.HOVER_SOFT);
            }
            int ty = Metrics.textY(rowTop, ROW);
            ThemeText.textRight(g, this.font, Integer.toString(i + 1), left + 18, ty, i == 0 ? Palette.ACCENT : Palette.TEXT_MUTED);
            int x = left + 26;
            if (i == 0) {
                x += ThemeDraw.badge(g, this.font, Component.translatable(KEY + "start_badge"), x, rowTop + 2, rowTop + ROW - 2, Palette.ACCENT_DIM,
                        Palette.TEXT) + 6;
            }
            int nameRight = showPosition ? textRight - positionWidth - 8 : textRight;
            ThemeText.fitted(g, this.font, this.rowText(gate), x, ty, Math.max(20, nameRight - x), Palette.TEXT);
            if (showPosition) {
                String position = Math.round(gate.x()) + " " + Math.round(gate.y()) + " " + Math.round(gate.z());
                ThemeText.textRight(g, this.font, position, textRight, ty, Palette.TEXT_FAINT);
            }
        }
    }
}
