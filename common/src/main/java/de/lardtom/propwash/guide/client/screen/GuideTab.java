package de.lardtom.propwash.guide.client.screen;

import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.GuideKeys;
import de.lardtom.propwash.guide.client.GuideOpener;
import de.lardtom.propwash.race.client.GatePlaceHint;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class GuideTab extends ConfigTab {
    public static final String ID = "flight_school";
    public static final int ORDER = 110;
    public static final String RESET_HINTS = "propwash.guide.hints.reset";

    private final SceneList list = new SceneList(scene -> GuideOpener.openScene(scene, this.host != null ? this.host.screen() : null));
    private int listTop;
    private @Nullable FlatButton resetHints;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.guide.title");
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public boolean hasDefaults() {
        return false;
    }

    public SceneList list() {
        return this.list;
    }

    @Override
    protected void build() {
        this.list.setScenes(GuideScenes.all());
        Component training = Component.translatable("propwash.guide.training.open");
        int trainingWidth = this.host.font().width(training) + 14;
        this.host.addWidget(new FlatButton(this.x + this.width - trainingWidth, this.y, trainingWidth, Metrics.CONTROL_HEIGHT, training,
                FlatButton.Style.NORMAL, () -> GuideOpener.openTraining(this.host.screen())));
        Component intro = Component.translatable("propwash.guide.tab.intro", GuideKeys.label());
        int introHeight = ThemeText.wrappedHeight(this.host.font(), intro, this.width);
        this.listTop = this.y + Metrics.SECTION_HEIGHT + Metrics.SECTION_GAP + introHeight + Metrics.GAP;
        this.listTop = this.addResetHints(trainingWidth, this.listTop);
        this.list.layout(this.x, this.listTop, this.width, Math.max(SceneList.CARD_HEIGHT, this.y + this.height - this.listTop));
        this.list.layout(this.x, this.listTop, this.width, Math.max(this.list.cardHeight(), this.y + this.height - this.listTop));
    }

    public @Nullable FlatButton resetHintsButton() {
        return this.resetHints;
    }

    private int addResetHints(int trainingWidth, int rowTop) {
        Component reset = Component.translatable(RESET_HINTS);
        int width = Math.min(this.host.font().width(reset) + 14, this.width);
        int titleRight = this.x + this.host.font().width(Component.translatable("propwash.guide.tab.section")) + Metrics.GAP;
        int x = this.x + this.width - trainingWidth - Metrics.GAP - width;
        int y = this.y;
        int below = rowTop;
        if (x < titleRight) {
            x = this.x + this.width - width;
            y = rowTop;
            below = rowTop + Metrics.CONTROL_HEIGHT + Metrics.GAP;
        }
        FlatButton button = new FlatButton(x, y, width, Metrics.CONTROL_HEIGHT, reset, FlatButton.Style.NORMAL, this::resetHints);
        button.setTooltip(Tooltip.create(Component.translatable(RESET_HINTS + ".tooltip")));
        this.resetHints = this.host.addWidget(button);
        return below;
    }

    private void resetHints() {
        boolean changed = GatePlaceHint.resetHints();
        if (this.host != null && this.host.screen() instanceof ConfiguratorScreen screen) {
            screen.flash(Component.translatable(RESET_HINTS + (changed ? ".done" : ".none")), changed ? Palette.SUCCESS : Palette.TEXT_MUTED);
        }
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ThemeDraw.sectionTitle(g, this.host.font(), Component.translatable("propwash.guide.tab.section"), this.x, this.y, this.width);
        Component intro = Component.translatable("propwash.guide.tab.intro", GuideKeys.label());
        ThemeText.hint(g, this.host.font(), intro, this.x, this.y + Metrics.SECTION_HEIGHT + Metrics.SECTION_GAP, this.width);
        this.list.extract(g, this.host.font(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return event.button() == 1 && (this.list.grab(event.x(), event.y()) || this.list.click(event.x(), event.y()));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return this.list.drag(event.y());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return this.list.release();
    }

    @Override
    public boolean scrollsItself() {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.list.scroll(mouseX, mouseY, scrollY);
    }
}
