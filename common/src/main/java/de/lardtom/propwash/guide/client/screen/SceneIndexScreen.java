package de.lardtom.propwash.guide.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.GuideKeys;
import de.lardtom.propwash.guide.client.GuideOpener;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.ui.ChromeLayout;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class SceneIndexScreen extends Screen implements ThemedScreen {
    private static final int MAX_WIDTH = 560;
    private static final int MAX_HEIGHT = 380;
    private static final int HEADER = Metrics.HEADER_HEIGHT;
    private static final int FOOTER = Metrics.FOOTER_HEIGHT;
    private static final int SUBHEADER = 20;

    private static @Nullable SceneIndexScreen active;

    private final @Nullable Screen parent;
    private final SceneList list = new SceneList(this::open);
    private ItemStack filter;
    private int windowLeft;
    private int windowTop;
    private int windowRight;
    private int windowBottom;
    private long frames;
    private int footer = FOOTER;
    private int subHeader = SUBHEADER;
    private int tipLimit;

    public SceneIndexScreen(@Nullable Screen parent, ItemStack filter) {
        super(Component.translatable("propwash.guide.title"));
        this.parent = parent;
        this.filter = filter == null ? ItemStack.EMPTY : filter.copy();
        this.refresh();
    }

    public static @Nullable SceneIndexScreen active() {
        return active;
    }

    public List<Scene> shownScenes() {
        return this.list.scenes();
    }

    public long frames() {
        return this.frames;
    }

    private void refresh() {
        List<Scene> scenes = this.filter.isEmpty() ? List.of() : GuideOpener.scenesFor(this.filter);
        if (scenes.isEmpty()) {
            this.filter = ItemStack.EMPTY;
            scenes = GuideScenes.all();
        }
        this.list.setScenes(scenes);
    }

    private void open(Scene scene) {
        GuideOpener.openScene(scene, this);
    }

    private void showAll() {
        this.filter = ItemStack.EMPTY;
        this.refresh();
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        active = this;
        int w = Math.min(this.width - 12, MAX_WIDTH);
        int h = Math.min(this.height - 12, MAX_HEIGHT);
        Component closeLabel = Component.translatable("propwash.guide.index.close");
        int closeWidth = Math.max(70, this.font.width(closeLabel) + 16);
        this.tipLimit = Math.max(40, w - closeWidth - 2 * Metrics.PADDING - 16);
        this.footer = ChromeLayout.footerHeight(ThemeDraw.footerLines(this.font, this.tip(), this.tipLimit));
        Component all = Component.translatable("propwash.guide.index.show_all");
        Component training = Component.translatable("propwash.guide.training.open");
        int buttons = this.font.width(training) + 14 + (this.filter.isEmpty() ? 0 : this.font.width(all) + 14 + Metrics.GAP);
        boolean stacked = this.font.width(this.subTitle()) + 8 + Metrics.GAP + buttons + Metrics.PADDING > w;
        this.subHeader = stacked ? SUBHEADER + Metrics.CONTROL_HEIGHT : SUBHEADER;
        int chrome = HEADER + this.subHeader + 2 * Metrics.PADDING + this.footer;
        this.windowLeft = (this.width - w) / 2;
        this.windowRight = this.windowLeft + w;
        this.list.layout(this.windowLeft + Metrics.PADDING + 2, 0, w - 2 * Metrics.PADDING - 2, h - chrome);
        h = Math.min(h, chrome + this.list.footprint());
        this.windowTop = (this.height - h) / 2;
        this.windowBottom = this.windowTop + h;
        int listTop = this.windowTop + HEADER + this.subHeader + Metrics.PADDING;
        this.list.layout(this.windowLeft + Metrics.PADDING + 2, listTop, w - 2 * Metrics.PADDING - 2,
                this.windowBottom - this.footer - Metrics.PADDING - listTop);
        int buttonY = this.windowBottom - this.footer + (this.footer - Metrics.CONTROL_HEIGHT) / 2;
        this.addRenderableWidget(new FlatButton(this.windowRight - Metrics.PADDING - closeWidth, buttonY, closeWidth, Metrics.CONTROL_HEIGHT, closeLabel,
                FlatButton.Style.PRIMARY, this::onClose));
        int subY = this.windowTop + HEADER + this.subHeader - SUBHEADER + (SUBHEADER - Metrics.CONTROL_HEIGHT) / 2 + 1;
        int subRight = this.windowRight - Metrics.PADDING;
        if (!this.filter.isEmpty()) {
            int allWidth = this.font.width(all) + 14;
            this.addRenderableWidget(new FlatButton(subRight - allWidth, subY, allWidth, Metrics.CONTROL_HEIGHT, all,
                    FlatButton.Style.NORMAL, this::showAll));
            subRight -= allWidth + Metrics.GAP;
        }
        int trainingWidth = this.font.width(training) + 14;
        this.addRenderableWidget(new FlatButton(subRight - trainingWidth, subY, trainingWidth, Metrics.CONTROL_HEIGHT, training,
                FlatButton.Style.NORMAL, () -> GuideOpener.openTraining(this)));
    }

    private Component tip() {
        return Component.translatable("propwash.guide.index.tip", GuideKeys.label());
    }

    private Component subTitle() {
        return this.filter.isEmpty()
                ? Component.translatable("propwash.guide.index.all", this.list.scenes().size())
                : Component.translatable("propwash.guide.index.for_item", this.filter.getHoverName());
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
            return true;
        }
        boolean scrolled = switch (event.key()) {
            case InputConstants.KEY_PAGEUP -> this.list.page(-1);
            case InputConstants.KEY_PAGEDOWN -> this.list.page(1);
            case InputConstants.KEY_HOME -> this.list.edge(false);
            case InputConstants.KEY_END -> this.list.edge(true);
            default -> false;
        };
        return scrolled || super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        return event.button() == 1 && (this.list.grab(event.x(), event.y()) || this.list.click(event.x(), event.y()));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return this.list.drag(event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean released = this.list.release();
        return super.mouseReleased(event) || released;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.list.scroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
        this.minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeStats.frame(this);
        this.frames++;
        ThemeDraw.window(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
        ThemeDraw.headerBar(g, this.windowLeft, this.windowTop, this.windowRight, HEADER);
        int textY = Metrics.textY(this.windowTop, HEADER - 1);
        int left = ThemeDraw.brand(g, this.font, this.windowLeft + 8, textY) + 10;
        ThemeDraw.verticalSeparator(g, left - 5, this.windowTop + 5, this.windowTop + HEADER - 6);
        ThemeText.text(g, this.font, this.title, left, textY, Palette.TEXT);
        int subTop = this.windowTop + HEADER;
        ThemeDraw.subHeaderBar(g, this.windowLeft, subTop, this.windowRight, this.subHeader);
        boolean stacked = this.subHeader > SUBHEADER;
        int subWidth = stacked ? this.windowRight - this.windowLeft - 16 : this.windowRight - this.windowLeft - 120;
        ThemeText.fitted(g, this.font, this.subTitle(), this.windowLeft + 8, stacked ? subTop + 4 : Metrics.textY(subTop, SUBHEADER), subWidth, Palette.TEXT_MUTED);
        this.list.extract(g, this.font, mouseX, mouseY);
        int footerTop = this.windowBottom - this.footer;
        ThemeDraw.footerBar(g, this.windowLeft, footerTop, this.windowRight, this.windowBottom);
        ThemeDraw.footerText(g, this.font, this.tip(), this.windowLeft + 8, footerTop, this.footer, this.tipLimit, Palette.TEXT_FAINT);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.windowLeft, this.windowTop, this.windowRight, this.windowBottom);
    }
}
