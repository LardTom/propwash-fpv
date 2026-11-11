package de.lardtom.propwash.rx.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.rx.RxAccess;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxConfigC2S;
import de.lardtom.propwash.rx.RxLearn;
import de.lardtom.propwash.rx.RxLink;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
import de.lardtom.propwash.rx.RxText;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.WidgetViews;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.ContentScroll;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RxScreen extends Screen implements ThemedScreen, WidgetViews {
    public static final int WIDTH = 300;
    public static final double CLOSE_BEYOND_REACH = 2.0;
    private static final int GAP = 4;
    private static final int BAR_HEIGHT = 10;
    private static final int LED_SIZE = 7;
    private static final int DEFAULT_AXES = 8;
    private static final int RANGE_SEND_TICKS = 5;
    private static final int NOTE_TICKS = 80;

    private final BlockPos pos;
    private final @Nullable View preview;
    private final boolean unlimited;
    private final RxLearn learn = new RxLearn();
    private final List<AbstractWidget> content = new ArrayList<>();
    private final List<RxSource> choices = new ArrayList<>();
    private boolean confirm;
    private boolean learning;
    private int learnTicks;
    private @Nullable Component learnNote;
    private int noteAge;
    private int range = RxLink.DEFAULT_RANGE;
    private boolean rangeDirty;
    private int rangeWait;
    private @Nullable ContentScroll scroll;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int textX;
    private int textWidth;
    private int introY;
    private int ownerY;
    private int barY;
    private int endpointsY;
    private int rangeY;
    private int statusY;
    private int contentTop;
    private int contentBottom;
    private @Nullable FlatButton learnButton;
    private @Nullable FlatButton lowButton;
    private @Nullable FlatButton highButton;

    public record View(RxSettings settings, String ownerName, int power, RxLinkState link) {
    }

    public RxScreen(BlockPos pos, boolean confirm) {
        this(pos, confirm, null, unlimitedAt(pos));
    }

    private RxScreen(BlockPos pos, boolean confirm, @Nullable View preview, boolean unlimited) {
        super(Component.translatable((unlimited ? RxModule.CREATIVE_RX_BLOCK : RxModule.RX_BLOCK).block().get().getDescriptionId()));
        this.pos = pos.immutable();
        this.confirm = confirm;
        this.preview = preview;
        this.unlimited = unlimited;
        View view = this.view();
        if (view != null) {
            this.range = view.settings().range();
        }
    }

    public static RxScreen preview(View view, boolean confirm) {
        return preview(view, confirm, false);
    }

    public static RxScreen preview(View view, boolean confirm, boolean unlimited) {
        return new RxScreen(BlockPos.ZERO, confirm, view, unlimited);
    }

    private static boolean unlimitedAt(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && RxBlock.unlimited(minecraft.level.getBlockState(pos));
    }

    public boolean unlimited() {
        return this.unlimited;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public boolean confirming() {
        return this.confirm;
    }

    public boolean learning() {
        return this.learning;
    }

    public void startLearning() {
        if (!this.learning) {
            this.toggleLearn();
        }
    }

    public void takeLow() {
        this.endpoint(true);
    }

    public void takeHigh() {
        this.endpoint(false);
    }

    public void askUnbind() {
        this.setConfirm(true);
    }

    public void confirmUnbind() {
        if (this.confirm) {
            this.unbind();
        }
    }

    public List<AbstractWidget> contentWidgets() {
        return List.copyOf(this.content);
    }

    public @Nullable ContentScroll contentScroll() {
        return this.scroll;
    }

    public @Nullable View view() {
        if (this.preview != null) {
            return this.preview;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RxBlockEntity entity = RxClient.blockEntity(this.pos);
        if (entity == null || minecraft.level == null) {
            return null;
        }
        BlockState state = minecraft.level.getBlockState(this.pos);
        if (!(state.getBlock() instanceof RxBlock)) {
            return null;
        }
        return new View(entity.settings(), entity.ownerName(), state.getValue(RxBlock.POWER), state.getValue(RxBlock.LINK));
    }

    @Override
    protected void init() {
        double offset = this.scroll == null ? 0.0 : this.scroll.offset();
        this.content.clear();
        this.scroll = null;
        this.learnButton = null;
        this.lowButton = null;
        this.highButton = null;
        View view = this.view();
        RxSettings settings = view == null ? RxSettings.EMPTY : view.settings();
        int width = Math.min(WIDTH, this.width - 8);
        this.left = (this.width - width) / 2;
        this.right = this.left + width;
        int x = this.left + Metrics.PADDING;
        int w = width - 2 * Metrics.PADDING;
        int innerWidth = w - Metrics.SCROLLBAR_WIDTH - 2;
        this.textX = x;
        this.textWidth = innerWidth;
        int contentHeight = this.measure(innerWidth, settings);
        int footer = Metrics.CONTROL_HEIGHT + 2 * Metrics.PADDING;
        int chrome = Metrics.HEADER_HEIGHT + Metrics.PADDING + footer;
        int height = Math.min(chrome + contentHeight + ContentScroll.BOTTOM_PADDING, this.height - 8);
        this.top = Math.max(4, (this.height - height) / 2);
        this.bottom = this.top + height;
        this.contentTop = this.top + Metrics.HEADER_HEIGHT + Metrics.PADDING;
        this.contentBottom = this.bottom - footer;
        int y = this.contentTop;
        if (this.confirm) {
            this.introY = y;
            y += ThemeText.wrappedHeight(this.font, Component.translatable(RxText.UNBIND_CONFIRM), innerWidth) + GAP;
        } else {
            y = this.layoutContent(x, y, innerWidth, settings);
        }
        int footerY = this.contentBottom + Metrics.PADDING;
        int half = (w - GAP) / 2;
        if (this.confirm) {
            this.addRenderableWidget(new FlatButton(x, footerY, half, Metrics.CONTROL_HEIGHT, Component.translatable(RxText.UNBIND),
                    FlatButton.Style.DANGER, this::unbind));
            this.addRenderableWidget(new FlatButton(x + half + GAP, footerY, w - half - GAP, Metrics.CONTROL_HEIGHT,
                    Component.translatable("gui.cancel"), FlatButton.Style.NORMAL, () -> this.setConfirm(false)));
        } else {
            FlatButton unbind = this.addRenderableWidget(new FlatButton(x, footerY, half, Metrics.CONTROL_HEIGHT,
                    Component.translatable(RxText.UNBIND), FlatButton.Style.NORMAL, () -> this.setConfirm(true)));
            unbind.active = settings.bound() && this.mayChange(settings);
            this.addRenderableWidget(new FlatButton(x + half + GAP, footerY, w - half - GAP, Metrics.CONTROL_HEIGHT,
                    Component.translatable("gui.done"), FlatButton.Style.PRIMARY, this::onClose));
        }
        ContentScroll scroll = new ContentScroll(new ScreenRectangle(this.left, this.contentTop, width - Metrics.PADDING + 2,
                Math.max(0, this.contentBottom - this.contentTop)), offset);
        scroll.adopt(this.content);
        scroll.contentBottom(y - GAP);
        this.scroll = scroll;
        this.refreshButtons();
    }

    private int measure(int width, RxSettings settings) {
        if (this.confirm) {
            return ThemeText.wrappedHeight(this.font, Component.translatable(RxText.UNBIND_CONFIRM), width);
        }
        return this.layoutContent(-10000, 0, width, settings);
    }

    private int layoutContent(int x, int y0, int width, RxSettings settings) {
        boolean place = x > -10000;
        int y = y0;
        if (place) {
            this.introY = y;
        }
        y += ThemeText.wrappedHeight(this.font, Component.translatable(RxText.TOOLTIP), width) + GAP;
        if (place) {
            this.ownerY = y;
        }
        y += Metrics.LINE_HEIGHT + GAP;
        if (place) {
            this.choices.clear();
            this.choices.addAll(this.sourceChoices(settings.source()));
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i <= this.choices.size(); i++) {
                values.add(i);
            }
            Component label = Component.translatable(RxText.CHANNEL);
            int labelWidth = Metrics.labelWidth(width, this.font.width(label));
            this.content.add(this.addWidget(new ChoiceButton<>(x, y, width, Metrics.CONTROL_HEIGHT, label, labelWidth, values, this::choiceIndex,
                    this::choose, this::choiceName, null)));
        }
        y += Metrics.CONTROL_HEIGHT + GAP;
        if (place) {
            this.learnButton = this.addWidget(new FlatButton(x, y, width, Metrics.CONTROL_HEIGHT, Component.translatable(RxText.LEARN),
                    FlatButton.Style.NORMAL, this::toggleLearn));
            this.content.add(this.learnButton);
        }
        y += Metrics.CONTROL_HEIGHT + GAP;
        if (place) {
            this.barY = y;
        }
        y += Metrics.LINE_HEIGHT + BAR_HEIGHT + GAP;
        Component low = Component.translatable(RxText.SET_LOW);
        Component high = Component.translatable(RxText.SET_HIGH);
        int half = (width - GAP) / 2;
        boolean sideBySide = this.font.width(low) + 8 <= half && this.font.width(high) + 8 <= half;
        if (place) {
            this.lowButton = this.addWidget(new FlatButton(x, y, sideBySide ? half : width, Metrics.CONTROL_HEIGHT, low, FlatButton.Style.NORMAL,
                    () -> this.endpoint(true)));
            int highX = sideBySide ? x + half + GAP : x;
            int highY = sideBySide ? y : y + Metrics.CONTROL_HEIGHT + GAP;
            this.highButton = this.addWidget(new FlatButton(highX, highY, sideBySide ? width - half - GAP : width, Metrics.CONTROL_HEIGHT, high,
                    FlatButton.Style.NORMAL, () -> this.endpoint(false)));
            this.content.add(this.lowButton);
            this.content.add(this.highButton);
        }
        y += (sideBySide ? 1 : 2) * (Metrics.CONTROL_HEIGHT + GAP) - GAP + 2;
        if (place) {
            this.endpointsY = y;
        }
        y += ThemeText.wrappedHeight(this.font, RxText.endpoints(new RxSettings(null, RxSource.GAS, -1.0F, -1.0F, 1)), width) + GAP;
        if (place) {
            this.rangeY = y;
        }
        if (place && !this.unlimited) {
            Component label = Component.translatable(RxText.RANGE_LABEL);
            int labelWidth = Metrics.labelWidth(width, this.font.width(label));
            ValueSlider slider = new ValueSlider(x, y, width, Metrics.CONTROL_HEIGHT, label, labelWidth, RxLink.MIN_RANGE, RxLink.MAX_RANGE, 1.0,
                    () -> this.range, value -> this.setRange((int) Math.round(value)), () -> RxLink.DEFAULT_RANGE,
                    value -> Component.translatable(RxText.BLOCKS, (int) Math.round(value)).getString());
            slider.typed(value -> value, RxLink.MIN_RANGE, RxLink.MAX_RANGE, false);
            slider.active = this.mayChange(settings);
            this.content.add(this.addWidget(slider));
        }
        y += Metrics.CONTROL_HEIGHT + GAP;
        if (place) {
            this.statusY = y;
        }
        int status = 0;
        for (Component text : List.of(RxText.link(RxLinkState.LINKED, 15), RxText.link(RxLinkState.LOST, 15), RxText.link(RxLinkState.NONE, 0),
                Component.translatable(RxText.LEARN_PROMPT), Component.translatable(RxText.LEARN_NONE), Component.translatable(RxText.NO_RADIO),
                learnedNote(RxSource.axis(RxSource.AXES - 1)))) {
            status = Math.max(status, ThemeText.wrappedHeight(this.font, text, width));
        }
        y += status + GAP;
        return place ? y : y - y0;
    }

    private List<RxSource> sourceChoices(@Nullable RxSource current) {
        RxRadio.Frame frame = RxRadio.current();
        int axes = frame == null ? DEFAULT_AXES : Math.min(RxSource.AXES, frame.axes().length);
        int buttons = frame == null ? 0 : Math.min(RxSource.BUTTONS, frame.buttons().length);
        if (current != null && current.kind() == RxSource.Kind.AXIS) {
            axes = Math.max(axes, current.index() + 1);
        }
        if (current != null && current.kind() == RxSource.Kind.BUTTON) {
            buttons = Math.max(buttons, current.index() + 1);
        }
        return RxSource.choices(axes, buttons);
    }

    private Integer choiceIndex() {
        View view = this.view();
        RxSource source = view == null ? null : view.settings().source();
        int index = source == null ? -1 : this.choices.indexOf(source);
        return index < 0 ? 0 : index + 1;
    }

    private Component choiceName(Integer index) {
        return index == null || index <= 0 || index > this.choices.size() ? Component.translatable(RxText.NO_CHANNEL)
                : RxText.source(this.choices.get(index - 1));
    }

    private void choose(Integer index) {
        if (index == null || index <= 0 || index > this.choices.size()) {
            return;
        }
        this.stopLearning(null);
        this.send(RxConfigC2S.source(this.pos, this.choices.get(index - 1)));
    }

    private boolean mayChange(RxSettings settings) {
        if (this.preview != null) {
            return true;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        RxAccess access = RxClient.access(player, settings);
        return access.mayChange() || access == RxAccess.UNBOUND;
    }

    private void setRange(int value) {
        int clamped = RxLink.clampRange(value);
        if (clamped != this.range) {
            this.range = clamped;
            this.rangeDirty = true;
            this.rangeWait = 0;
        }
    }

    private void flushRange() {
        if (this.rangeDirty) {
            this.rangeDirty = false;
            this.send(RxConfigC2S.range(this.pos, this.range));
        }
    }

    private void toggleLearn() {
        if (this.learning) {
            this.stopLearning(null);
            return;
        }
        this.learn.reset();
        this.learning = true;
        this.learnTicks = 0;
        this.learnNote = Component.translatable(RxText.LEARN_PROMPT);
        this.refreshButtons();
    }

    private void stopLearning(@Nullable Component note) {
        this.learning = false;
        this.learnNote = note;
        this.noteAge = 0;
        this.refreshButtons();
    }

    private static Component learnedNote(RxSource source) {
        return Component.translatable(RxText.CHANNEL).append(": ").append(RxText.source(source));
    }

    public void learned(RxSource source) {
        this.stopLearning(learnedNote(source));
        this.send(RxConfigC2S.source(this.pos, source));
    }

    private void endpoint(boolean low) {
        View view = this.view();
        RxRadio.Frame frame = RxRadio.current();
        if (view == null || frame == null || view.settings().source() == null) {
            return;
        }
        float value = frame.value(view.settings().source());
        if (!Float.isFinite(value)) {
            return;
        }
        float clamped = Math.clamp(value, -1.0F, 1.0F);
        this.send(low ? RxConfigC2S.low(this.pos, clamped) : RxConfigC2S.high(this.pos, clamped));
    }

    private void unbind() {
        this.send(RxConfigC2S.unbind(this.pos));
        this.onClose();
    }

    private void setConfirm(boolean confirm) {
        this.confirm = confirm;
        this.stopLearning(null);
        this.rebuildWidgets();
    }

    private void send(RxConfigC2S payload) {
        if (this.preview == null) {
            ClientNet.sendToServer(payload);
        }
    }

    private void refreshButtons() {
        View view = this.view();
        boolean radio = RxRadio.current() != null || this.preview != null;
        boolean may = view != null && this.mayChange(view.settings());
        boolean source = view != null && view.settings().source() != null;
        if (this.learnButton != null) {
            this.learnButton.setMessage(Component.translatable(this.learning ? "gui.cancel" : RxText.LEARN));
            this.learnButton.active = may && (radio || this.learning);
        }
        if (this.lowButton != null) {
            this.lowButton.active = may && radio && source && !this.learning;
        }
        if (this.highButton != null) {
            this.highButton.active = may && radio && source && !this.learning;
        }
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        View view = this.view();
        if (this.preview == null) {
            LocalPlayer player = minecraft.player;
            if (view == null || player == null
                    || !player.isWithinBlockInteractionRange(this.pos, CLOSE_BEYOND_REACH)
                    || RxClient.access(player, view.settings()) == RxAccess.FOREIGN) {
                minecraft.gui.setScreen(null);
                return;
            }
        }
        if (this.learning) {
            this.learnTicks++;
            RxRadio.Frame frame = RxRadio.current();
            if (frame != null) {
                RxSource found = this.learn.sample(frame.sample(this.learnTicks / 20.0));
                if (found != null) {
                    this.learned(found);
                } else if (this.learn.timedOut()) {
                    this.stopLearning(Component.translatable(RxText.LEARN_NONE));
                }
            } else if (this.learnTicks > RxLearn.TIMEOUT_SECONDS * 20.0) {
                this.stopLearning(Component.translatable(RxText.LEARN_NONE));
            }
        }
        if (!this.learning && this.learnNote != null && ++this.noteAge > NOTE_TICKS) {
            this.learnNote = null;
        }
        if (this.rangeDirty && ++this.rangeWait >= RANGE_SEND_TICKS) {
            this.flushRange();
        }
        if (!this.rangeDirty && view != null) {
            this.range = view.settings().range();
        }
        this.refreshButtons();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
        ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
        View view = this.view();
        RxLinkState link = view == null ? RxLinkState.NONE : view.link();
        int ledX = this.right - Metrics.PADDING - LED_SIZE - 2;
        ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.right, Metrics.HEADER_HEIGHT, this.title, ledX - Metrics.PADDING);
        led(g, ledX, Metrics.textY(this.top, Metrics.HEADER_HEIGHT - 1), link);
        ThemeDraw.separator(g, this.left + 1, this.right - 1, this.contentBottom);
    }

    private static void led(GuiGraphicsExtractor g, int x, int y, RxLinkState link) {
        int core = switch (link) {
            case LINKED -> Palette.SUCCESS;
            case LOST -> Palette.ERROR;
            case NONE -> Palette.LED_OFF;
        };
        int top = y + (Metrics.TEXT_HEIGHT - LED_SIZE) / 2;
        if (link != RxLinkState.NONE) {
            g.fill(x - 1, top - 1, x + LED_SIZE + 1, top + LED_SIZE + 1, Palette.withAlpha(core, 0x40));
        }
        g.fill(x, top, x + LED_SIZE, top + LED_SIZE, Palette.LED_BEZEL);
        g.fill(x + 1, top + 1, x + LED_SIZE - 1, top + LED_SIZE - 1, core);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        ContentScroll scroll = this.scroll;
        if (scroll == null) {
            return;
        }
        ScreenRectangle view = scroll.view();
        boolean inside = view.containsPoint(mouseX, mouseY);
        int shift = scroll.offset();
        g.enableScissor(view.left(), view.top(), view.right(), view.bottom());
        this.extractContentText(g, shift);
        for (AbstractWidget widget : this.content) {
            widget.extractRenderState(g, inside ? mouseX : -1, inside ? mouseY : -1, a);
        }
        scroll.extractScrollbar(g);
        g.disableScissor();
        for (AbstractWidget widget : this.content) {
            if (inside && widget.isHovered() && widget instanceof FlatButton button && !button.active && button == this.learnButton) {
                ThemeText.tooltip(g, this.font, Component.translatable(RxText.NO_RADIO), mouseX, mouseY);
            }
        }
    }

    private void extractContentText(GuiGraphicsExtractor g, int shift) {
        if (this.confirm) {
            ThemeText.wrapped(g, this.font, Component.translatable(RxText.UNBIND_CONFIRM), this.textX, this.introY - shift, this.textWidth, Palette.TEXT);
            return;
        }
        View view = this.view();
        RxSettings settings = view == null ? RxSettings.EMPTY : view.settings();
        int power = view == null ? 0 : view.power();
        RxLinkState link = view == null ? RxLinkState.NONE : view.link();
        ThemeText.wrapped(g, this.font, Component.translatable(RxText.TOOLTIP), this.textX, this.introY - shift, this.textWidth, Palette.TEXT_MUTED);
        UUID owner = settings.owner();
        ThemeText.fitted(g, this.font, RxText.boundTo(view == null ? "" : view.ownerName(), owner), this.textX, this.ownerY - shift, this.textWidth,
                owner == null ? Palette.TEXT_FAINT : Palette.TEXT);
        int barTop = this.barY - shift;
        ThemeText.fitted(g, this.font, Component.translatable(RxText.OUTPUT, power), this.textX, barTop, this.textWidth, Palette.TEXT);
        int y0 = barTop + Metrics.LINE_HEIGHT;
        int x1 = this.textX + this.textWidth;
        ThemeDraw.valueBar(g, this.textX, y0, x1, y0 + BAR_HEIGHT, power / 15.0, link == RxLinkState.LOST ? Palette.WARNING : Palette.ACCENT);
        for (int step = 1; step < 15; step++) {
            int tick = this.textX + Math.round(this.textWidth * step / 15.0F);
            g.fill(tick, y0 + BAR_HEIGHT - 2, tick + 1, y0 + BAR_HEIGHT, Palette.BORDER);
        }
        if (settings.source() != null) {
            ThemeText.wrapped(g, this.font, RxText.endpoints(settings), this.textX, this.endpointsY - shift, this.textWidth, Palette.TEXT_FAINT);
        }
        if (this.unlimited) {
            ThemeText.fitted(g, this.font, Component.translatable(RxText.RANGE_UNLIMITED), this.textX,
                    Metrics.textY(this.rangeY - shift, Metrics.CONTROL_HEIGHT), this.textWidth, Palette.TEXT);
        }
        Component note = this.learnNote;
        if (note == null && RxRadio.current() == null && this.preview == null) {
            note = Component.translatable(RxText.NO_RADIO);
        }
        if (note != null) {
            ThemeText.wrapped(g, this.font, note, this.textX, this.statusY - shift, this.textWidth, this.learning ? Palette.ACCENT : Palette.TEXT_MUTED);
            return;
        }
        int color = switch (link) {
            case LINKED -> Palette.SUCCESS;
            case LOST -> Palette.WARNING;
            case NONE -> Palette.TEXT_FAINT;
        };
        ThemeText.wrapped(g, this.font, RxText.link(link, power), this.textX, this.statusY - shift, this.textWidth, color);
    }

    @Override
    public @Nullable ScreenRectangle widgetView(AbstractWidget widget) {
        return this.scroll != null && this.content.contains(widget) ? this.scroll.view() : null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ContentScroll scroll = this.scroll;
        if (scroll != null && scroll.view().containsPoint((int) mouseX, (int) mouseY)) {
            for (AbstractWidget widget : this.content) {
                if (widget.isMouseOver(mouseX, mouseY) && widget.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                    return true;
                }
            }
        }
        return scroll != null && scroll.wheel(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ContentScroll scroll = this.scroll;
        if (scroll != null && scroll.mouseClicked(event.x(), event.y())) {
            return true;
        }
        if (scroll != null && !scroll.view().containsPoint((int) event.x(), (int) event.y())) {
            for (AbstractWidget widget : this.content) {
                if (widget.isMouseOver(event.x(), event.y())) {
                    return false;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
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
        if (super.keyPressed(event)) {
            return true;
        }
        ContentScroll scroll = this.scroll;
        if (scroll == null) {
            return false;
        }
        return switch (event.key()) {
            case InputConstants.KEY_PAGEDOWN -> scroll.page(1);
            case InputConstants.KEY_PAGEUP -> scroll.page(-1);
            case InputConstants.KEY_END -> scroll.edge(true);
            case InputConstants.KEY_HOME -> scroll.edge(false);
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
    public void removed() {
        this.flushRange();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
