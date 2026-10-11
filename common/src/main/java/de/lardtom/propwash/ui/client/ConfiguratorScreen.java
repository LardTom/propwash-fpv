package de.lardtom.propwash.ui.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.ui.ChromeLayout;
import de.lardtom.propwash.ui.ScrollMath;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.TabErrorReport;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SearchBar;
import de.lardtom.propwash.ui.client.search.SettingsIndex;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.theme.WidgetViews;
import de.lardtom.propwash.ui.client.widget.ContentScroll;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.NumberInput;
import de.lardtom.propwash.ui.client.widget.PanelWidget;
import de.lardtom.propwash.ui.client.widget.RowScroll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ConfiguratorScreen extends Screen implements ConfigTabHost, ThemedScreen, TuneHost, WidgetViews {
    public static final int MIN_WIDTH = 427;
    public static final int MIN_HEIGHT = 240;
    static final int SIDEBAR_MIN_ROW = Metrics.TEXT_HEIGHT + 1;
    private static final int MAX_WIDTH = 720;
    private static final int MAX_HEIGHT = 440;
    private static final int FLASH_TICKS = 80;
    private static final int HIGHLIGHT_TICKS = 50;
    private static final int SEARCH_MIN = 104;
    private static final int SEARCH_MAX = 168;
    private static final int ERROR_PANEL_HEIGHT = 52;
    private static final int ERROR_TOOLTIP_WIDTH = 300;
    private static final double KEY_EDGE_NOTCHES = 4096.0;

    private static @Nullable ConfiguratorScreen active;

    private final @Nullable Screen parent;
    private final List<ConfigTab> tabs;
    private final List<AbstractWidget> tabWidgets = new ArrayList<>();
    private final Map<String, Double> autoOffsets = new HashMap<>();
    private final Map<String, Integer> autoBottoms = new HashMap<>();
    private @Nullable ContentScroll auto;
    private int headerHeight = ChromeLayout.HEADER_ROW;
    private int headerRows = 1;
    private int footerHeight = ChromeLayout.FOOTER_MIN;
    private int saveWidth;
    private int sideWidth;
    private int statusLimit;
    private boolean footerStacked;
    private int sidebarRow = Metrics.SIDEBAR_ROW;
    private final RowScroll sidebarScroll = new RowScroll();
    private int sidebarRows;
    private int sidebarFirst;
    private @Nullable String scrolledTab;
    private final Map<String, String> tabErrors = new LinkedHashMap<>();
    private final Map<String, String> tabReports = new HashMap<>();
    private final Map<String, Integer> extractedFrames = new HashMap<>();
    private final TuneModel tune;
    private DroneContext context;
    private int activeIndex;
    private boolean otherChanges;
    private boolean confirmDiscard;
    private boolean closing;
    private boolean rebuildRequested;
    private boolean lockoutFlashShown;
    private @Nullable Component flash;
    private int flashColor = Palette.TEXT;
    private int flashTicks;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private int sidebarWidth;
    private ScreenRectangle content = new ScreenRectangle(0, 0, 1, 1);
    private static final int EDGE_FADE = 8;
    private static final int EDGE_SOLID = 4;
    private @Nullable FlatButton defaultsButton;
    private @Nullable FlatButton discardButton;
    private @Nullable FlatButton saveButton;
    private final List<FlatButton> sidebarButtons = new ArrayList<>();
    private @Nullable SearchBar search;
    private String searchText = "";
    private @Nullable AbstractWidget highlight;
    private @Nullable FlatButton highlightTab;
    private int highlightTicks;

    private ConfiguratorScreen(@Nullable Screen parent, @Nullable String tabId) {
        super(Component.translatable("propwash.ui.configurator.title"));
        this.parent = parent;
        this.context = DroneContext.resolve(Minecraft.getInstance());
        this.tune = TuneModel.open(this.context);
        this.tabs = ConfigTabs.create();
        this.activeIndex = this.indexOf(tabId != null ? tabId : UiConfig.get().lastTab);
    }

    public static void open(@Nullable Screen parent, @Nullable String tabId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof ConfiguratorScreen current) {
            if (tabId != null) {
                current.selectTab(tabId);
            }
            return;
        }
        if (!PilotGuard.prepareOpen(minecraft)) {
            return;
        }
        minecraft.gui.setScreen(new ConfiguratorScreen(parent, tabId));
    }

    public static boolean openTab(@Nullable Screen parent, String tabId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof ConfiguratorScreen current) {
            int index = current.exactIndex(tabId);
            if (index < 0) {
                return false;
            }
            current.selectTab(index);
            return true;
        }
        ConfiguratorScreen screen = new ConfiguratorScreen(parent, tabId);
        if (screen.exactIndex(tabId) < 0 || !PilotGuard.prepareOpen(minecraft)) {
            return false;
        }
        minecraft.gui.setScreen(screen);
        return true;
    }

    private int exactIndex(@Nullable String tabId) {
        if (tabId != null) {
            for (int i = 0; i < this.tabs.size(); i++) {
                if (tabId.equals(this.safeId(this.tabs.get(i)))) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static Screen create(Screen parent, @Nullable String tabId) {
        if (!PilotGuard.prepareOpen(Minecraft.getInstance())) {
            return parent;
        }
        return new ConfiguratorScreen(parent, tabId);
    }

    public static @Nullable ConfiguratorScreen active() {
        return active;
    }

    @Override
    public TuneModel tune() {
        return this.tune;
    }

    @Override
    public DroneContext context() {
        return this.context;
    }

    public List<ConfigTab> tabs() {
        return List.copyOf(this.tabs);
    }

    public @Nullable ConfigTab activeTab() {
        return this.tabs.isEmpty() ? null : this.tabs.get(this.activeIndex);
    }

    public @Nullable SearchBar search() {
        return this.search;
    }

    public @Nullable AbstractWidget highlighted() {
        return this.highlightTicks > 0 ? this.highlight : null;
    }

    public Map<String, String> tabErrors() {
        return Map.copyOf(this.tabErrors);
    }

    public @Nullable String tabReport(String tabId) {
        return this.tabReports.get(tabId);
    }

    public int extractedFrames(String tabId) {
        return this.extractedFrames.getOrDefault(tabId, 0);
    }

    public boolean hasUnsavedChanges() {
        return this.tune.dirty() || this.otherChanges;
    }

    public void flash(@Nullable Component message, int color) {
        if (message == null) {
            return;
        }
        this.flash = message;
        this.flashColor = color;
        this.flashTicks = FLASH_TICKS;
    }

    private int indexOf(@Nullable String tabId) {
        if (tabId != null) {
            for (int i = 0; i < this.tabs.size(); i++) {
                if (tabId.equals(this.safeId(this.tabs.get(i)))) {
                    return i;
                }
            }
        }
        return 0;
    }

    public void selectTab(String tabId) {
        this.selectTab(this.indexOf(tabId));
    }

    public void selectTab(int index) {
        if (index < 0 || index >= this.tabs.size() || index == this.activeIndex) {
            return;
        }
        this.activeIndex = index;
        this.confirmDiscard = false;
        this.layoutSidebar(true);
        this.attachActive();
        this.updateFooterButtons();
    }

    @Override
    protected void init() {
        active = this;
        this.computeLayout();
        this.sidebarButtons.clear();
        for (int i = 0; i < this.tabs.size(); i++) {
            int index = i;
            ConfigTab tab = this.tabs.get(i);
            Component title = this.safeTitle(tab);
            this.sidebarButtons.add(this.addRenderableWidget(new FlatButton(this.windowLeft, this.sidebarTop(), this.sidebarWidth - 1, this.sidebarRow,
                    title, FlatButton.Style.TAB, () -> this.activeIndex == index, () -> this.selectTab(index))));
        }
        this.layoutSidebar(true);
        if (this.search != null) {
            this.searchText = this.search.text();
        }
        int searchWidth = this.searchWidth();
        int searchX = this.windowRight - 8 - searchWidth;
        this.search = new SearchBar(this, this.font, searchX, this.windowTop + (ChromeLayout.HEADER_ROW - Metrics.CONTROL_HEIGHT) / 2, searchWidth,
                Metrics.CONTROL_HEIGHT, this.searchText, this.windowBottom - this.footerHeight, this::revealSearchHit);
        this.addRenderableWidget(this.search.field());
        int buttonY = this.footerStacked
                ? ChromeLayout.stackedButtonTop(this.windowBottom - this.footerHeight, this.footerHeight, Metrics.CONTROL_HEIGHT)
                : this.windowBottom - this.footerHeight + (this.footerHeight - Metrics.CONTROL_HEIGHT) / 2;
        int saveX = this.windowRight - Metrics.PADDING - this.saveWidth;
        this.saveButton = this.addRenderableWidget(new FlatButton(saveX, buttonY, this.saveWidth, Metrics.CONTROL_HEIGHT, this.saveLabel(),
                FlatButton.Style.PRIMARY, this::primaryAction));
        int sideX = saveX - Metrics.GAP - this.sideWidth;
        this.defaultsButton = this.addRenderableWidget(new FlatButton(sideX, buttonY, this.sideWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash.ui.button.defaults"), FlatButton.Style.NORMAL, this::resetActiveTab));
        this.discardButton = this.addRenderableWidget(new FlatButton(sideX, buttonY, this.sideWidth, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash.ui.button.discard"), FlatButton.Style.DANGER, this::discardAndClose));
        this.updateFooterButtons();
        this.attachActive();
    }

    private int searchWidth() {
        return Math.clamp((this.windowRight - this.windowLeft) / 4, SEARCH_MIN, SEARCH_MAX);
    }

    private int sidebarTop() {
        return this.windowTop + this.headerHeight + 4;
    }

    private int sidebarSpace() {
        return this.windowBottom - this.footerHeight - this.windowTop - this.headerHeight - 8;
    }

    private void layoutSidebar(boolean reveal) {
        int count = this.sidebarButtons.size();
        this.sidebarFirst = ChromeLayout.sidebarFirstRow(this.sidebarFirst, reveal ? this.activeIndex : -1, count, this.sidebarRows);
        for (int i = 0; i < count; i++) {
            FlatButton button = this.sidebarButtons.get(i);
            button.setY(this.sidebarTop() + (i - this.sidebarFirst) * this.sidebarRow);
            button.visible = i >= this.sidebarFirst && i < this.sidebarFirst + this.sidebarRows;
        }
        this.sidebarScroll.layout(this.windowLeft + this.sidebarWidth - Metrics.SCROLLBAR_WIDTH - 1, this.sidebarTop(), this.sidebarRow, count,
                this.sidebarRows);
    }

    public boolean scrollSidebar(int row) {
        if (row == RowScroll.NONE) {
            return false;
        }
        if (row != this.sidebarFirst) {
            this.sidebarFirst = row;
            this.layoutSidebar(false);
        }
        return true;
    }

    public List<FlatButton> sidebarButtons() {
        return List.copyOf(this.sidebarButtons);
    }

    private boolean sidebarKey(KeyEvent event) {
        if (!this.sidebarScrollable()) {
            return false;
        }
        Window window = this.minecraft.getWindow();
        boolean focused = this.getFocused() instanceof FlatButton button && this.sidebarButtons.contains(button);
        if (!focused && !this.overSidebar(this.minecraft.mouseHandler.getScaledXPos(window), this.minecraft.mouseHandler.getScaledYPos(window))) {
            return false;
        }
        return this.scrollSidebar(this.sidebarScroll.key(event.key(), this.sidebarFirst));
    }

    public RowScroll sidebarScroll() {
        return this.sidebarScroll;
    }

    public int sidebarFirst() {
        return this.sidebarFirst;
    }

    private boolean sidebarScrollable() {
        return this.sidebarButtons.size() > this.sidebarRows;
    }

    private boolean overSidebar(double mouseX, double mouseY) {
        return mouseX >= this.windowLeft && mouseX < this.windowLeft + this.sidebarWidth && mouseY >= this.windowTop + this.headerHeight
                && mouseY < this.windowBottom - this.footerHeight;
    }

    private Component safeTitle(ConfigTab tab) {
        try {
            Component title = tab.title();
            if (title != null) {
                return title;
            }
        } catch (RuntimeException e) {
            this.recordError(tab, e);
        }
        return Component.literal(this.safeId(tab));
    }

    private void computeLayout() {
        int w = Math.min(this.width, MAX_WIDTH);
        int h = Math.min(this.height, MAX_HEIGHT);
        this.windowLeft = (this.width - w) / 2;
        this.windowTop = (this.height - h) / 2;
        this.windowRight = this.windowLeft + w;
        this.windowBottom = this.windowTop + h;
        int widestTitle = 0;
        for (ConfigTab tab : this.tabs) {
            widestTitle = Math.max(widestTitle, this.font.width(this.safeTitle(tab)));
        }
        this.sidebarWidth = ChromeLayout.sidebarWidth(w, widestTitle);
        this.saveWidth = Math.max(80, Math.max(this.font.width(Component.translatable("propwash.ui.button.save")),
                this.font.width(Component.translatable("propwash.ui.button.close"))) + 16);
        this.sideWidth = Math.max(64, Math.max(this.font.width(Component.translatable("propwash.ui.button.defaults")),
                this.font.width(Component.translatable("propwash.ui.button.discard"))) + 16);
        this.footerStacked = ChromeLayout.footerStacked(w, this.saveWidth, this.sideWidth, Metrics.PADDING, Metrics.GAP);
        this.statusLimit = this.footerStacked ? ChromeLayout.stackedStatusLimit(w)
                : ChromeLayout.statusLimit(w, this.saveWidth, this.sideWidth, Metrics.PADDING, Metrics.GAP);
        int statusLines = 1;
        for (Component candidate : this.footerCandidates()) {
            statusLines = Math.max(statusLines, this.font.split(candidate, this.statusLimit).size());
        }
        this.footerHeight = this.footerStacked ? ChromeLayout.stackedFooterHeight(statusLines, Metrics.CONTROL_HEIGHT)
                : ChromeLayout.footerHeight(statusLines);
        this.headerRows = this.headerRowsNeeded();
        this.headerHeight = ChromeLayout.headerHeight(this.headerRows);
        int space = this.sidebarSpace();
        this.sidebarRow = ChromeLayout.sidebarRow(space, this.tabs.size(), Metrics.SIDEBAR_ROW, SIDEBAR_MIN_ROW);
        this.sidebarRows = Math.min(this.tabs.size(), ChromeLayout.sidebarVisibleRows(space, this.sidebarRow));
        int cx = this.windowLeft + this.sidebarWidth + 8;
        int cy = this.windowTop + this.headerHeight + 6;
        this.content = new ScreenRectangle(cx, cy, Math.max(40, this.windowRight - 6 - cx),
                Math.max(40, this.windowBottom - this.footerHeight - 4 - cy));
    }

    private List<Component> footerCandidates() {
        List<Component> out = new ArrayList<>();
        for (String key : List.of("propwash.ui.footer.confirm_discard", "propwash.ui.footer.live_lost", "propwash.ui.footer.unsaved_live",
                "propwash.ui.footer.unsaved", "propwash.ui.footer.read_only", "propwash.ui.footer.mixed", "propwash.ui.footer.game",
                "propwash.ui.footer.template", "propwash.ui.footer.live", "propwash.ui.footer.held", "propwash.ui.footer.defaults_done")) {
            out.add(Component.translatable(key));
        }
        for (ConfigTab tab : this.tabs) {
            try {
                out.add(Component.translatable(tab.readOnlyKey()));
                out.add(Component.translatable(tab.mixedStorageKey()));
            } catch (RuntimeException e) {
                this.recordError(tab, e);
            }
        }
        return out;
    }

    private int headerRowsNeeded() {
        HeaderTexts texts = this.headerTexts();
        int badgeWidth = 0;
        for (String key : List.of("propwash.ui.header.live", "propwash.ui.header.held", "propwash.ui.header.template")) {
            badgeWidth = Math.max(badgeWidth, this.font.width(Component.translatable(key)) + 8);
        }
        return ChromeLayout.headerRows(this.windowRight - this.windowLeft, 8 + ThemeDraw.brandWidth(this.font), this.font.width(texts.name()),
                ChromeLayout.badgesWidth(badgeWidth, this.lockBadgeWidth()), this.font.width(texts.radio()), this.searchWidth());
    }

    private int lockBadgeWidth() {
        return PilotGuard.lockoutActive() ? this.font.width(Component.translatable("propwash.ui.header.lockout")) + 8 : 0;
    }

    private record HeaderTexts(Component name, String radio, boolean connected) {
    }

    private HeaderTexts headerTexts() {
        RadioState radio = RadioState.INSTANCE;
        boolean connected = radio.connected();
        String radioName = connected ? radio.deviceName() : Component.translatable("propwash.ui.header.no_radio").getString();
        if (connected && radioName.isBlank()) {
            radioName = Component.translatable("propwash.ui.header.radio").getString();
        }
        Component name = this.tune.live() || this.context.hasDrone() ? this.context.name()
                : Component.translatable("propwash.ui.header.template_name");
        return new HeaderTexts(name, radioName, connected);
    }

    public ScreenRectangle contentArea() {
        return this.content;
    }

    public ScreenRectangle windowArea() {
        return new ScreenRectangle(this.windowLeft, this.windowTop, this.windowRight - this.windowLeft, this.windowBottom - this.windowTop);
    }

    public ScreenRectangle contentView() {
        int clip = Math.min(this.content.bottom(), Math.max(this.content.top(), this.tabClipBottom()));
        return new ScreenRectangle(this.content.left(), this.content.top(), this.content.width(), clip - this.content.top());
    }

    @Override
    public @Nullable ScreenRectangle widgetView(AbstractWidget widget) {
        return this.tabWidgets.contains(widget) ? this.contentView() : null;
    }

    public boolean ownsTabWidget(AbstractWidget widget) {
        return this.tabWidgets.contains(widget);
    }

    private void attachActive() {
        for (AbstractWidget w : this.tabWidgets) {
            this.removeWidget(w);
        }
        this.tabWidgets.clear();
        this.rebuildRequested = false;
        this.rememberScroll();
        this.auto = null;
        ConfigTab tab = this.activeTab();
        if (tab == null) {
            return;
        }
        try {
            int width = tab instanceof UiTab ? this.content.width() : Math.max(40, this.content.width() - Metrics.CONTENT_INSET);
            tab.attach(this, this.content.left(), this.content.top(), width, this.content.height());
            String id = this.safeId(tab);
            this.scrolledTab = id;
            if (!tab.scrollsItself()) {
                ContentScroll scroll = new ContentScroll(this.content, this.autoOffsets.getOrDefault(id, 0.0));
                this.auto = scroll;
                scroll.adopt(this.tabWidgets);
                scroll.contentBottom(Math.max(Math.max(scroll.widgetBottom(), tab.reportedBottom()), this.autoBottoms.getOrDefault(id, 0)));
            }
        } catch (RuntimeException e) {
            this.recordError(tab, e);
        }
    }

    private void rememberScroll() {
        ContentScroll scroll = this.auto;
        if (scroll == null || this.scrolledTab == null) {
            return;
        }
        this.autoOffsets.put(this.scrolledTab, (double) scroll.offset());
        this.autoBottoms.put(this.scrolledTab, scroll.view().top() + scroll.contentHeight() - ContentScroll.BOTTOM_PADDING);
    }

    public @Nullable ContentScroll contentScroll() {
        return this.auto;
    }

    private boolean inContent(Predicate<ConfigTab> call) {
        ContentScroll scroll = this.auto;
        if (scroll == null) {
            return this.tabCall(call);
        }
        scroll.rest();
        try {
            return this.tabCall(call);
        } finally {
            if (this.auto == scroll) {
                scroll.adopt(this.tabWidgets);
            }
        }
    }

    @Override
    public int contentShift() {
        ContentScroll scroll = this.auto;
        return scroll == null ? 0 : scroll.offset();
    }

    private MouseButtonEvent shifted(MouseButtonEvent event) {
        int shift = this.contentShift();
        return shift == 0 ? event : new MouseButtonEvent(event.x(), event.y() + shift, event.buttonInfo());
    }

    private boolean overContent(double mouseX, double mouseY) {
        return this.contentView().containsPoint((int) mouseX, (int) mouseY);
    }

    private boolean contentAbove() {
        ContentScroll scroll = this.auto;
        return scroll != null && scroll.scrollable() ? scroll.above() : this.tabCall(ConfigTab::contentAbove);
    }

    private boolean contentBelow() {
        ContentScroll scroll = this.auto;
        return scroll != null && scroll.scrollable() ? scroll.below() : this.tabCall(ConfigTab::contentBelow);
    }

    @Override
    public Optional<GuiEventListener> getChildAt(double x, double y) {
        boolean inside = this.overContent(x, y);
        for (GuiEventListener child : this.children()) {
            if (!child.isMouseOver(x, y)) {
                continue;
            }
            if (inside || !(child instanceof AbstractWidget widget) || !this.tabWidgets.contains(widget)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private void recordError(ConfigTab tab, Throwable e) {
        String id = this.safeId(tab);
        if (!this.tabErrors.containsKey(id)) {
            Constants.LOG.error("Konfigurator-Tab {} ist fehlgeschlagen", id, e);
            this.tabReports.put(id, TabErrorReport.of(Services.PLATFORM.getModVersion(), Services.PLATFORM.getPlatformName(), id, e));
        }
        this.tabErrors.put(id, TabErrorReport.summary(e));
    }

    private boolean tabCall(Predicate<ConfigTab> call) {
        ConfigTab tab = this.activeTab();
        if (tab == null) {
            return false;
        }
        try {
            return call.test(tab);
        } catch (RuntimeException e) {
            this.recordError(tab, e);
            return false;
        }
    }

    @Override
    public <T extends AbstractWidget> T addWidget(T widget) {
        super.addWidget(widget);
        this.tabWidgets.add(widget);
        return widget;
    }

    @Override
    public Font font() {
        return this.font;
    }

    @Override
    public Minecraft minecraft() {
        return this.minecraft;
    }

    @Override
    public Screen screen() {
        return this;
    }

    @Override
    public void rebuild() {
        this.rebuildRequested = true;
    }

    @Override
    public void changed() {
        this.otherChanges = true;
        this.confirmDiscard = false;
        this.updateFooterButtons();
    }

    private Component saveLabel() {
        return Component.translatable(this.hasUnsavedChanges() ? "propwash.ui.button.save" : "propwash.ui.button.close");
    }

    private void primaryAction() {
        if (this.hasUnsavedChanges()) {
            this.saveAndClose();
        } else {
            this.discardAndClose();
        }
    }

    private void updateFooterButtons() {
        if (this.saveButton != null) {
            Component label = this.saveLabel();
            if (!label.equals(this.saveButton.getMessage())) {
                this.saveButton.setMessage(label);
            }
        }
        if (this.defaultsButton != null) {
            this.defaultsButton.visible = !this.confirmDiscard && !this.tabCall(ConfigTab::readOnly);
            this.defaultsButton.active = this.tabCall(ConfigTab::hasDefaults);
        }
        if (this.discardButton != null) {
            this.discardButton.visible = this.confirmDiscard;
        }
    }

    private void resetActiveTab() {
        ConfigTab tab = this.activeTab();
        if (tab == null || !this.tabCall(ConfigTab::hasDefaults)) {
            return;
        }
        if (this.tabCall(t -> {
            t.resetDefaults();
            return true;
        })) {
            if (!(tab instanceof UiTab)) {
                this.otherChanges = true;
            }
            this.flash(Component.translatable("propwash.ui.footer.defaults_done"), Palette.TEXT_MUTED);
        }
    }

    public void saveAndClose() {
        if (this.getFocused() instanceof NumberInput input) {
            input.commitPending();
        }
        boolean tuneSaved = this.tune.save();
        for (ConfigTab tab : this.tabs) {
            try {
                tab.save();
            } catch (RuntimeException e) {
                this.recordError(tab, e);
            }
        }
        if (!tuneSaved) {
            this.flash(Component.translatable("propwash.ui.footer.save_failed"), Palette.ERROR);
            return;
        }
        this.otherChanges = false;
        this.rememberTab();
        this.closeScreen();
    }

    public void discardAndClose() {
        this.tune.discard();
        for (ConfigTab tab : this.tabs) {
            try {
                tab.discard();
            } catch (RuntimeException e) {
                this.recordError(tab, e);
            }
        }
        this.otherChanges = false;
        this.rememberTab();
        this.closeScreen();
    }

    private void rememberTab() {
        ConfigTab tab = this.activeTab();
        if (tab != null) {
            UiConfig.get().lastTab = tab.id();
            UiConfig.CONFIG.save();
        }
    }

    private void closeScreen() {
        this.closing = true;
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.handleEscape();
    }

    public void revealSearchHit(SettingsSearch.Hit hit) {
        SettingsSearch.Entry entry = hit.entry();
        int index = this.exactIndex(entry.tabId());
        if (index < 0) {
            return;
        }
        if (index != this.activeIndex) {
            this.selectTab(index);
        } else if (this.rebuildRequested) {
            this.attachActive();
        }
        this.highlight = null;
        this.highlightTab = null;
        this.highlightTicks = HIGHLIGHT_TICKS;
        if (entry.kind() == SettingsSearch.Kind.TAB) {
            this.highlightTab = index < this.sidebarButtons.size() ? this.sidebarButtons.get(index) : null;
            this.setFocused(this.highlightTab);
            return;
        }
        SettingsIndex searchIndex = this.search != null ? this.search.index() : null;
        SettingsIndex.Target target = searchIndex != null ? searchIndex.target(entry) : null;
        AbstractWidget widget = target != null ? SettingsIndex.locate(target, this.tabWidgets) : null;
        if (widget == null) {
            this.highlightTab = index < this.sidebarButtons.size() ? this.sidebarButtons.get(index) : null;
            this.setFocused(null);
            return;
        }
        this.revealWidget(widget);
        this.highlight = widget;
        this.setFocused(widget.active && widget.visible ? widget : null);
    }

    private void revealWidget(AbstractWidget widget) {
        ContentScroll scroll = this.auto;
        if (scroll != null) {
            scroll.reveal(widget);
        } else {
            this.tabCall(tab -> tab.reveal(widget));
        }
    }

    private void revealFocus() {
        if (this.getFocused() instanceof AbstractWidget widget && this.tabWidgets.contains(widget)) {
            this.revealWidget(widget);
        }
    }

    private boolean scrollKey(KeyEvent event) {
        int key = event.key();
        boolean page = key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN;
        boolean edge = key == InputConstants.KEY_HOME || key == InputConstants.KEY_END;
        if (!page && !edge) {
            return false;
        }
        if (edge && this.getFocused() instanceof EditBox) {
            return false;
        }
        boolean down = key == InputConstants.KEY_PAGEDOWN || key == InputConstants.KEY_END;
        ContentScroll scroll = this.auto;
        if (scroll != null && scroll.scrollable()) {
            return page ? scroll.page(down ? 1 : -1) : scroll.edge(down);
        }
        double notches = page ? Math.max(1.0, (this.content.height() - ScrollMath.PAGE_OVERLAP) / (double) ScrollMath.LINE) : KEY_EDGE_NOTCHES;
        double x = this.content.left() + this.content.width() / 2.0;
        double y = this.content.top() + this.content.height() / 2.0;
        return this.tabCall(tab -> tab.mouseScrolled(x, y, 0.0, down ? -notches : notches));
    }

    private int highlightAlpha(float partialTick) {
        float t = this.highlightTicks - partialTick;
        float fade = Math.min(1.0F, t / 15.0F);
        float pulse = 0.65F + 0.35F * (float) Math.cos((HIGHLIGHT_TICKS - t) * 0.35F);
        return Math.max(0, Math.min(255, Math.round(255.0F * fade * pulse)));
    }

    private void extractWidgetHighlight(GuiGraphicsExtractor g, float partialTick) {
        AbstractWidget widget = this.highlight;
        if (widget == null || this.highlightTicks <= 0 || !widget.visible || !this.tabWidgets.contains(widget)) {
            return;
        }
        int alpha = this.highlightAlpha(partialTick);
        int x0 = widget.getX() - 2;
        int y0 = widget.getY() - 2;
        int x1 = widget.getX() + widget.getWidth() + 2;
        int y1 = widget.getY() + widget.getHeight() + 2;
        g.fill(x0, y0, x1, y1, Palette.withAlpha(Palette.ACCENT, alpha / 6));
        ThemeDraw.boxOutline(g, x0, y0, x1, y1, Palette.withAlpha(Palette.ACCENT, alpha));
    }

    private void extractTabHighlight(GuiGraphicsExtractor g, float partialTick) {
        FlatButton tab = this.highlightTab;
        if (tab == null || this.highlightTicks <= 0) {
            return;
        }
        int alpha = this.highlightAlpha(partialTick);
        ThemeDraw.boxOutline(g, tab.getX(), tab.getY(), tab.getX() + tab.getWidth(), tab.getY() + tab.getHeight(), Palette.withAlpha(Palette.ACCENT, alpha));
    }

    private void handleEscape() {
        if (this.search != null && this.search.escape()) {
            return;
        }
        GuiEventListener focused = this.getFocused();
        if (focused instanceof NumberInput input && input.isEditing()) {
            input.cancelEdit();
            return;
        }
        if (this.tabCall(ConfigTab::cancelAction)) {
            return;
        }
        if (this.confirmDiscard || !this.hasUnsavedChanges()) {
            this.discardAndClose();
            return;
        }
        this.confirmDiscard = true;
        this.updateFooterButtons();
    }

    @Override
    public void removed() {
        if (active == this) {
            active = null;
        }
    }

    @Override
    public void tick() {
        if (this.rebuildRequested) {
            this.attachActive();
        }
        DroneContext now = DroneContext.resolve(this.minecraft);
        this.context = now;
        this.tune.tick();
        if (this.headerRowsNeeded() != this.headerRows) {
            this.rebuildWidgets();
        }
        if (PilotGuard.enforceLockout() && !this.lockoutFlashShown) {
            this.lockoutFlashShown = true;
            this.flash(Component.translatable("propwash.ui.guard.lockout"), Palette.ERROR);
        }
        this.inContent(tab -> {
            tab.tick();
            return true;
        });
        if (this.flashTicks > 0 && --this.flashTicks == 0) {
            this.flash = null;
        }
        if (this.highlightTicks > 0 && --this.highlightTicks == 0) {
            this.highlight = null;
            this.highlightTab = null;
        }
        this.updateFooterButtons();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.tabCall(ConfigTab::capturesInput) && this.tabCall(tab -> tab.keyPressed(event))) {
            return true;
        }
        if (this.search != null && SearchBar.isShortcut(event)) {
            this.search.focus();
            return true;
        }
        if (this.search != null && !event.isEscape() && this.search.keyPressed(event)) {
            return true;
        }
        if (event.isEscape()) {
            this.handleEscape();
            return true;
        }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_S) {
            this.saveAndClose();
            return true;
        }
        if (!(this.search != null && this.search.focused()) && this.sidebarKey(event)) {
            return true;
        }
        boolean widgetFocused = this.getFocused() != null;
        if (!widgetFocused && this.inContent(tab -> tab.keyPressed(event))) {
            return true;
        }
        if (super.keyPressed(event)) {
            this.revealFocus();
            return true;
        }
        boolean searching = this.search != null && this.search.focused();
        if (widgetFocused && !searching && this.inContent(tab -> tab.keyPressed(event))) {
            return true;
        }
        return !searching && this.scrollKey(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.copyErrorReport(event.x(), event.y())) {
            return true;
        }
        MouseButtonEvent inTab = this.shifted(event);
        if (this.tabCall(ConfigTab::capturesInput) && this.inContent(tab -> tab.mouseClicked(inTab, doubleClick))) {
            return true;
        }
        if (this.search != null && this.search.mouseClicked(event.x(), event.y())) {
            return true;
        }
        if (this.scrollSidebar(this.sidebarScroll.grab(event.x(), event.y()))) {
            return true;
        }
        ContentScroll scroll = this.auto;
        if (scroll != null && scroll.mouseClicked(event.x(), event.y())) {
            return true;
        }
        GuiEventListener target = this.getChildAt(event.x(), event.y()).orElse(null);
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled) {
            this.setFocused(null);
            handled = this.overContent(event.x(), event.y()) && this.inContent(tab -> tab.mouseClicked(inTab, doubleClick));
        } else if (!this.closing) {
            GuiEventListener focused = this.getFocused();
            if (focused != null && (focused != target || !this.children().contains(focused))) {
                this.setFocused(null);
            }
        }
        if (!this.closing && this.confirmDiscard) {
            this.confirmDiscard = false;
            this.updateFooterButtons();
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ContentScroll scroll = this.auto;
        if (scroll != null) {
            scroll.mouseReleased();
        }
        this.sidebarScroll.release();
        MouseButtonEvent inTab = this.shifted(event);
        boolean tab = this.inContent(t -> t.mouseReleased(inTab));
        return super.mouseReleased(event) || tab;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.scrollSidebar(this.sidebarScroll.drag(event.y()))) {
            return true;
        }
        ContentScroll scroll = this.auto;
        if (scroll != null && scroll.mouseDragged(event.y())) {
            return true;
        }
        MouseButtonEvent inTab = this.shifted(event);
        if (this.inContent(tab -> tab.mouseDragged(inTab, dx, dy))) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (this.search != null) {
            this.search.mouseMoved(mouseX, mouseY);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (!files.isEmpty()) {
            this.tabCall(tab -> tab.filesDropped(files));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.search != null && this.search.overDropdown(mouseX, mouseY)) {
            this.search.select(this.search.selected() + (scrollY < 0 ? 1 : -1));
            return true;
        }
        if (this.sidebarScrollable() && this.overSidebar(mouseX, mouseY)) {
            this.sidebarFirst += scrollY < 0 ? 1 : -1;
            this.layoutSidebar(false);
            return true;
        }
        GuiEventListener focused = this.getFocused();
        boolean inside = this.overContent(mouseX, mouseY);
        for (GuiEventListener child : this.children()) {
            boolean wheelTarget = child instanceof PanelWidget || child == focused;
            boolean reachable = inside || !(child instanceof AbstractWidget widget) || !this.tabWidgets.contains(widget);
            if (wheelTarget && reachable && child.isMouseOver(mouseX, mouseY) && child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        int shift = this.contentShift();
        if (this.inContent(tab -> tab.mouseScrolled(mouseX, mouseY + shift, scrollX, scrollY))) {
            return true;
        }
        ContentScroll scroll = this.auto;
        return scroll != null && inside && scroll.wheel(scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
        this.minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (this.rebuildRequested) {
            this.attachActive();
        }
        ThemeStats.frame(this);
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.sidebar(g, this.windowLeft, this.windowTop + this.headerHeight, this.windowLeft + this.sidebarWidth,
                this.windowBottom - this.footerHeight);
        this.extractHeader(g);
        this.extractFooter(g);
        boolean overSearch = this.search != null && this.search.overDropdown(mouseX, mouseY);
        int contentMouseX = overSearch ? -1 : mouseX;
        int contentMouseY = overSearch ? -1 : mouseY;
        super.extractRenderState(g, contentMouseX, contentMouseY, a);
        this.extractTabHighlight(g, a);
        this.extractContent(g, contentMouseX, contentMouseY, a);
        this.extractStatus(g);
        this.sidebarScroll.extract(g, this.sidebarFirst);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        if (this.search != null) {
            this.search.extractIcon(g);
            this.search.extractDropdown(g, this.font, mouseX, mouseY);
        }
    }

    private void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ConfigTab tab = this.activeTab();
        if (tab == null) {
            ThemeText.text(g, this.font, Component.translatable("propwash.ui.no_tabs"), this.content.left(), this.content.top(), Palette.TEXT_MUTED);
            return;
        }
        String id = this.safeId(tab);
        String error = this.tabErrors.get(id);
        int clip = Math.min(this.content.bottom(), Math.max(this.content.top(), this.tabClipBottom()));
        g.enableScissor(this.content.left(), this.content.top(), this.content.right(), clip);
        try {
            if (error == null) {
                this.extractTab(g, tab, mouseX, mouseY, a);
                for (AbstractWidget widget : this.tabWidgets) {
                    widget.extractRenderState(g, mouseX, mouseY, a);
                }
                this.extractWidgetHighlight(g, a);
                this.extractEdgeFades(g);
                ContentScroll scroll = this.auto;
                if (scroll != null) {
                    scroll.extractScrollbar(g);
                }
                this.extractedFrames.merge(id, 1, Integer::sum);
            }
        } catch (RuntimeException e) {
            this.recordError(tab, e);
        } finally {
            g.disableScissor();
        }
        String after = this.tabErrors.get(id);
        if (after != null) {
            int x = this.content.left();
            int y = this.content.top();
            int textWidth = this.content.width() - 12;
            ThemeDraw.framedBox(g, x, y, this.content.right(), y + ERROR_PANEL_HEIGHT, Palette.ERROR_BG, Palette.ERROR);
            ThemeText.fitted(g, this.font, Component.translatable("propwash.ui.tab_error"), x + 6, y + 6, textWidth, Palette.ERROR);
            String shownError = ThemeText.fit(this.font, after, textWidth);
            ThemeText.text(g, this.font, shownError, x + 6, y + 20, Palette.TEXT_MUTED);
            ThemeText.fitted(g, this.font, Component.translatable("propwash.ui.tab_error.copy"), x + 6, y + 36, textWidth, Palette.TEXT_FAINT);
            if (!shownError.equals(after) && this.overErrorPanel(mouseX, mouseY)) {
                ThemeText.tooltipWrapped(g, this.font, Component.literal(after), ERROR_TOOLTIP_WIDTH, mouseX, mouseY);
            }
        }
    }

    private void extractTab(GuiGraphicsExtractor g, ConfigTab tab, int mouseX, int mouseY, float a) {
        ContentScroll scroll = this.auto;
        tab.frame(true);
        if (scroll == null) {
            try {
                tab.extract(g, mouseX, mouseY, a);
            } finally {
                tab.frame(false);
            }
            return;
        }
        int shift = scroll.offset();
        boolean inside = !scroll.scrollable() || this.overContent(mouseX, mouseY);
        scroll.rest();
        g.pose().pushMatrix();
        g.pose().translate(0.0F, -shift);
        try {
            tab.extract(g, inside ? mouseX : -1, inside ? mouseY + shift : -1, a);
        } finally {
            g.pose().popMatrix();
            tab.frame(false);
            if (this.auto == scroll) {
                scroll.adopt(this.tabWidgets);
                scroll.contentBottom(Math.max(scroll.widgetBottom(), tab.reportedBottom()));
            }
        }
    }

    private boolean overErrorPanel(double mouseX, double mouseY) {
        return mouseX >= this.content.left() && mouseX < this.content.right() && mouseY >= this.content.top()
                && mouseY < this.content.top() + ERROR_PANEL_HEIGHT;
    }

    private boolean copyErrorReport(double mouseX, double mouseY) {
        ConfigTab tab = this.activeTab();
        if (tab == null || !this.overErrorPanel(mouseX, mouseY)) {
            return false;
        }
        String id = this.safeId(tab);
        String error = this.tabErrors.get(id);
        if (error == null) {
            return false;
        }
        this.minecraft.keyboardHandler.setClipboard(this.tabReports.getOrDefault(id, error));
        this.flash(Component.translatable("propwash.ui.tab_error.copied"), Palette.TEXT_MUTED);
        return true;
    }

    private int tabClipBottom() {
        ConfigTab tab = this.activeTab();
        if (tab == null) {
            return this.content.bottom();
        }
        try {
            return tab.clipBottom();
        } catch (RuntimeException e) {
            this.recordError(tab, e);
            return this.content.bottom();
        }
    }

    private void extractEdgeFades(GuiGraphicsExtractor g) {
        int x0 = this.content.left();
        int x1 = this.content.right() - Metrics.SCROLLBAR_WIDTH;
        int clear = Palette.withAlpha(Palette.BACKGROUND, 0);
        int top = this.content.top();
        int bottom = this.content.bottom();
        if (this.tabCall(ConfigTab::rowSnapped)) {
            x1 -= 3;
            if (this.contentAbove()) {
                g.fill(x0, top, x1, top + 1, Palette.BORDER);
            }
            if (this.contentBelow()) {
                int clip = Math.min(bottom, this.tabClipBottom());
                g.disableScissor();
                g.fill(x0, clip, x1, clip + 1, Palette.BORDER);
                if (bottom - clip >= ThemeDraw.MORE_BELOW_HEIGHT + 2) {
                    ThemeDraw.moreBelow(g, this.font, x0 + 2, clip + 2, x1 - x0 - 4);
                }
                g.enableScissor(this.content.left(), this.content.top(), this.content.right(), clip);
            }
            return;
        }
        if (this.contentAbove()) {
            g.fill(x0, top, x1, top + EDGE_SOLID, Palette.BACKGROUND);
            g.fillGradient(x0, top + EDGE_SOLID, x1, top + EDGE_SOLID + EDGE_FADE, Palette.BACKGROUND, clear);
        }
        if (this.contentBelow()) {
            g.fillGradient(x0, bottom - EDGE_SOLID - EDGE_FADE, x1, bottom - EDGE_SOLID, clear, Palette.BACKGROUND);
            g.fill(x0, bottom - EDGE_SOLID, x1, bottom, Palette.BACKGROUND);
        }
    }

    private String safeId(ConfigTab tab) {
        try {
            String id = tab.id();
            return id != null ? id : tab.getClass().getName();
        } catch (RuntimeException e) {
            return tab.getClass().getName();
        }
    }

    private String tabKey(Function<ConfigTab, String> key, String fallback) {
        ConfigTab tab = this.activeTab();
        if (tab == null) {
            return fallback;
        }
        try {
            String value = key.apply(tab);
            return value != null && !value.isBlank() ? value : fallback;
        } catch (RuntimeException e) {
            this.recordError(tab, e);
            return fallback;
        }
    }

    private void extractHeader(GuiGraphicsExtractor g) {
        int x0 = this.windowLeft;
        int y0 = this.windowTop;
        int x1 = this.windowRight;
        boolean twoRows = this.headerRows > 1;
        boolean radioRow = this.headerRows > 2;
        ThemeDraw.headerBar(g, x0, y0, x1, this.headerHeight);
        int brandY = Metrics.textY(y0, ChromeLayout.HEADER_ROW - 1);
        int brandRight = ThemeDraw.brand(g, this.font, x0 + 8, brandY);
        int rowTop = twoRows ? y0 + ChromeLayout.HEADER_ROW - 4 : y0;
        int rowHeight = twoRows ? ChromeLayout.HEADER_SECOND_ROW + 3 : ChromeLayout.HEADER_ROW - 1;
        int textY = Metrics.textY(rowTop, rowHeight);
        int badgeTop = textY - 2;
        int badgeBottom = textY + Metrics.TEXT_HEIGHT + 2;
        int left = twoRows ? x0 + 8 : brandRight + 10;
        if (!twoRows) {
            ThemeDraw.verticalSeparator(g, left - 5, y0 + 5, y0 + ChromeLayout.HEADER_ROW - 6);
        }

        HeaderTexts texts = this.headerTexts();
        boolean connected = texts.connected();
        boolean flying = this.tune.live() && this.context.piloting();
        Component badge = Component.translatable(flying ? "propwash.ui.header.live"
                : this.tune.live() ? "propwash.ui.header.held" : "propwash.ui.header.template");
        int badgeWidth = this.font.width(badge) + 8;
        int lockWidth = this.lockBadgeWidth();
        int radioMax = radioRow ? ChromeLayout.radioRowRoom(x1 - x0)
                : ChromeLayout.radioRoom(x1 - x0, 8 + ThemeDraw.brandWidth(this.font), this.font.width(texts.name()),
                ChromeLayout.badgesWidth(badgeWidth, lockWidth), this.searchWidth(), twoRows);
        Component radioText = Component.literal(texts.radio());
        int radioRight = twoRows || this.search == null ? x1 - 8 : this.search.field().getX() - 18;
        int radioX = radioRight - ThemeText.fittedWidth(this.font, radioText, radioMax);
        int radioY = radioRow ? textY + ChromeLayout.HEADER_SECOND_ROW : textY;
        ThemeText.fitted(g, this.font, radioText, radioX, radioY, radioMax, connected ? Palette.TEXT : Palette.TEXT_FAINT);
        ThemeDraw.dot(g, radioX - 6, radioY + 4, connected ? Palette.SUCCESS : Palette.ERROR);
        int right = radioRow ? x1 - 8 : radioX - 14;

        if (lockWidth > 0) {
            int lockLeft = Math.max(left + badgeWidth + 6, right - lockWidth);
            ThemeDraw.badge(g, this.font, Component.translatable("propwash.ui.header.lockout"), lockLeft, badgeTop, badgeBottom, Palette.ERROR_BG, Palette.ERROR);
            right = lockLeft - 6;
        }

        boolean lost = this.tune.live() && this.tune.liveLost();
        int badgeColor = lost ? Palette.ERROR : flying ? Palette.SUCCESS : this.tune.live() ? Palette.ACCENT_DIM : Palette.BUTTON;
        int badgeText = lost || flying ? Palette.ACCENT_TEXT : this.tune.live() ? Palette.TEXT : Palette.TEXT_MUTED;
        int nameWidth = right - left - badgeWidth - 6;
        String name = ThemeText.fit(this.font, texts.name(), Math.max(0, nameWidth));
        ThemeText.text(g, this.font, name, left, textY, Palette.TEXT);
        int badgeX = left + this.font.width(name) + 6;
        if (badgeX + badgeWidth <= right) {
            ThemeDraw.badge(g, this.font, badge, badgeX, badgeTop, badgeBottom, badgeColor, badgeText);
        }
    }

    private void extractFooter(GuiGraphicsExtractor g) {
        ThemeDraw.footerBar(g, this.windowLeft, this.windowBottom - this.footerHeight, this.windowRight, this.windowBottom);
    }

    private record Status(Component text, int color) {
    }

    private Status status() {
        if (this.confirmDiscard) {
            return new Status(Component.translatable("propwash.ui.footer.confirm_discard"), Palette.WARNING);
        }
        if (this.flash != null) {
            return new Status(this.flash, this.flashColor);
        }
        if (this.tune.live() && this.tune.liveLost()) {
            return new Status(Component.translatable("propwash.ui.footer.live_lost"), Palette.ERROR);
        }
        if (this.hasUnsavedChanges()) {
            return new Status(Component.translatable(this.tune.live() && this.tune.dirty() ? "propwash.ui.footer.unsaved_live"
                    : "propwash.ui.footer.unsaved"), Palette.ACCENT);
        }
        if (this.tabCall(ConfigTab::readOnly)) {
            return new Status(Component.translatable(this.tabKey(ConfigTab::readOnlyKey, "propwash.ui.footer.read_only")), Palette.TEXT_FAINT);
        }
        if (this.tabCall(tab -> tab.storage() == ConfigTab.Storage.MIXED)) {
            return new Status(Component.translatable(this.tabKey(ConfigTab::mixedStorageKey, "propwash.ui.footer.mixed")), Palette.TEXT_FAINT);
        }
        if (!this.tabCall(tab -> tab.storage() == ConfigTab.Storage.DRONE)) {
            return new Status(Component.translatable("propwash.ui.footer.game"), Palette.TEXT_FAINT);
        }
        return new Status(Component.translatable(!this.tune.live() ? "propwash.ui.footer.template"
                : this.context.piloting() ? "propwash.ui.footer.live" : "propwash.ui.footer.held"), Palette.TEXT_FAINT);
    }

    public List<String> statusLines() {
        List<String> out = new ArrayList<>();
        for (FormattedCharSequence line : this.font.split(this.status().text(), this.currentStatusLimit())) {
            StringBuilder text = new StringBuilder();
            line.accept((position, style, codepoint) -> {
                text.appendCodePoint(codepoint);
                return true;
            });
            out.add(text.toString());
        }
        return out;
    }

    private int currentStatusLimit() {
        boolean sideShown = (this.defaultsButton != null && this.defaultsButton.visible) || (this.discardButton != null && this.discardButton.visible);
        return sideShown || this.footerStacked ? this.statusLimit : this.statusLimit + this.sideWidth + Metrics.GAP;
    }

    private void extractStatus(GuiGraphicsExtractor g) {
        Status status = this.status();
        int x0 = this.windowLeft;
        int y0 = this.windowBottom - this.footerHeight;
        int limit = this.currentStatusLimit();
        List<FormattedCharSequence> lines = ThemeText.balanced(this.font, status.text(), limit);
        int top = this.footerStacked ? ChromeLayout.stackedStatusTop(y0, this.footerHeight, Metrics.CONTROL_HEIGHT, lines.size())
                : ChromeLayout.statusTop(y0, this.footerHeight, lines.size());
        if (top < y0 + ChromeLayout.FOOTER_MARGIN) {
            ThemeDraw.footerBar(g, x0, top - ChromeLayout.FOOTER_MARGIN, x0 + limit + 16, y0 + 1);
        }
        for (int i = 0; i < lines.size(); i++) {
            ThemeText.text(g, this.font, lines.get(i), x0 + 8, top + i * ChromeLayout.LINE, status.color());
        }
    }
}
