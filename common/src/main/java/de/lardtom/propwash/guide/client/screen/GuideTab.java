package de.lardtom.propwash.guide.client.screen;

import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.GuideKeys;
import de.lardtom.propwash.guide.client.GuideOpener;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class GuideTab extends ConfigTab {
    public static final String ID = "flight_school";
    public static final int ORDER = 110;

    private final SceneList list = new SceneList(scene -> GuideOpener.openScene(scene, this.host != null ? this.host.screen() : null));
    private int listTop;

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
        this.list.layout(this.x, this.listTop, this.width, Math.max(SceneList.CARD_HEIGHT, this.y + this.height - this.listTop));
        this.list.layout(this.x, this.listTop, this.width, Math.max(this.list.cardHeight(), this.y + this.height - this.listTop));
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
