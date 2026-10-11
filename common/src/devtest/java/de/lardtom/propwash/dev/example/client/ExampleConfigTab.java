package de.lardtom.propwash.dev.example.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.client.OsdLayoutSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ExampleConfigTab extends ConfigTab {
    public static final String ID = ExampleAddon.NAMESPACE + ":api_demo";
    public static final int ORDER = 1010;
    private static final int LINES = 4;

    private @Nullable Component status;
    private int statusColor = Palette.TEXT_MUTED;
    private @Nullable TextField layoutField;
    private @Nullable FlatButton applyButton;
    private boolean layoutValid;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash_example.tab.title");
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public Storage storage() {
        return Storage.MIXED;
    }

    @Override
    public String mixedStorageKey() {
        return "propwash_example.tab.footer";
    }

    @Override
    public boolean hasDefaults() {
        return false;
    }

    public @Nullable Component status() {
        return this.status;
    }

    public boolean copyOsd() {
        OsdLayoutSpec layout = PropwashClientApi.osd().current();
        this.host.minecraft().keyboardHandler.setClipboard(layout.json());
        this.report(Component.translatable("propwash_example.tab.osd_copied", layout.json().length()), Palette.SUCCESS);
        return true;
    }

    public boolean pasteOsd() {
        return this.applyLayout(this.host.minecraft().keyboardHandler.getClipboard());
    }

    public static @Nullable OsdLayoutSpec layout(String text) {
        try {
            return new OsdLayoutSpec(OsdLayoutSpec.CURRENT_VERSION, text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean valid(String text) {
        OsdLayoutSpec layout = layout(text);
        return layout != null && PropwashClientApi.osd().validate(layout);
    }

    public boolean applyLayout(String text) {
        OsdLayoutSpec layout = layout(text);
        if (layout == null || !PropwashClientApi.osd().validate(layout)) {
            this.report(Component.translatable("propwash_example.tab.osd_rejected"), Palette.ERROR);
            return false;
        }
        boolean applied = PropwashClientApi.osd().apply(layout);
        this.report(Component.translatable(applied ? "propwash_example.tab.osd_applied" : "propwash_example.tab.osd_rejected"),
                applied ? Palette.SUCCESS : Palette.ERROR);
        return applied;
    }

    public @Nullable TextField layoutField() {
        return this.layoutField;
    }

    public boolean layoutValid() {
        return this.layoutValid;
    }

    private void onLayoutTyped(String text) {
        this.layoutValid = !text.isBlank() && valid(text);
        if (this.applyButton != null) {
            this.applyButton.active = this.layoutValid;
        }
    }

    public void sendTune() {
        ImportRequest request = new ImportRequest(ExampleAddon.xclass(), EnumSet.of(ImportLayer.TUNE), EnumSet.of(TuneGroup.RATES,
                TuneGroup.THROTTLE), ImportTarget.HELD_DRONE);
        this.report(Component.translatable("propwash_example.tab.sending"), Palette.ACCENT);
        PropwashClientApi.sendImport(request).thenAccept(this::showResult);
    }

    public CompletableFuture<ImportResult> createDrone() {
        this.report(Component.translatable("propwash_example.tab.sending"), Palette.ACCENT);
        CompletableFuture<ImportResult> future = PropwashClientApi.sendCreateDrone(ExampleAddon.xclass());
        future.thenAccept(this::showResult);
        return future;
    }

    private void showResult(ImportResult result) {
        this.report(Component.translatable(result.status().translationKey()), result.status().success() ? Palette.SUCCESS : Palette.ERROR);
    }

    private void report(Component text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    @Override
    protected void build() {
        int y = this.y + Metrics.SECTION_HEIGHT + Metrics.SECTION_GAP + LINES * Metrics.LINE_HEIGHT + Metrics.GAP;
        int width = Math.min(this.width, Metrics.COLUMN_MAX);
        this.host.addWidget(new FlatButton(this.x, y, width, Metrics.CONTROL_HEIGHT, Component.translatable("propwash_example.tab.osd_copy"),
                FlatButton.Style.NORMAL, this::copyOsd));
        y += Metrics.ROW_HEIGHT;
        this.host.addWidget(new FlatButton(this.x, y, width, Metrics.CONTROL_HEIGHT, Component.translatable("propwash_example.tab.osd_paste"),
                FlatButton.Style.NORMAL, this::pasteOsd));
        y += Metrics.ROW_HEIGHT;
        FlatButton tune = new FlatButton(this.x, y, width, Metrics.CONTROL_HEIGHT, Component.translatable("propwash_example.tab.tune"),
                FlatButton.Style.PRIMARY, this::sendTune);
        tune.active = PropwashClientApi.heldDrone().isPresent();
        this.host.addWidget(tune);
        y += Metrics.ROW_HEIGHT;
        this.host.addWidget(new FlatButton(this.x, y, width, Metrics.CONTROL_HEIGHT, Component.translatable("propwash_example.tab.create"),
                FlatButton.Style.NORMAL, this::createDrone));
        y += Metrics.ROW_HEIGHT + Metrics.GAP;
        Component applyLabel = Component.translatable("propwash_example.tab.osd_apply");
        int applyWidth = this.host.font().width(applyLabel) + 16;
        String previous = this.layoutField != null ? this.layoutField.getValue() : "";
        TextField field = new TextField(this.host.font(), this.x, y, width - applyWidth - Metrics.GAP, Metrics.CONTROL_HEIGHT,
                Component.translatable("propwash_example.tab.osd_field"));
        field.setMaxLength(OsdLayoutSpec.MAX_JSON_LENGTH);
        field.hint(Component.translatable("propwash_example.tab.osd_field"));
        field.setValue(previous);
        field.setResponder(this::onLayoutTyped);
        this.layoutField = field;
        this.host.addWidget(field);
        this.applyButton = new FlatButton(this.x + width - applyWidth, y, applyWidth, Metrics.CONTROL_HEIGHT, applyLabel, FlatButton.Style.NORMAL,
                () -> this.applyLayout(field.getValue()));
        this.host.addWidget(this.applyButton);
        this.onLayoutTyped(previous);
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ThemeDraw.sectionTitle(g, this.host.font(), Component.translatable("propwash_example.tab.section"), this.x, this.y, this.width);
        int y = this.y + Metrics.SECTION_HEIGHT + Metrics.SECTION_GAP;
        long parts = PropwashApi.parts().all().stream().map(PartDefinition::id)
                .filter(id -> ExampleAddon.NAMESPACE.equals(id.getNamespace())).count();
        ThemeText.text(g, this.host.font(), Component.translatable("propwash_example.tab.api", PropwashApi.API_VERSION), this.x, y, Palette.TEXT);
        y += Metrics.LINE_HEIGHT;
        ThemeText.text(g, this.host.font(), Component.translatable("propwash_example.tab.parts", parts), this.x, y, Palette.TEXT_MUTED);
        y += Metrics.LINE_HEIGHT;
        Optional<DroneData> held = PropwashClientApi.heldDrone();
        Component drone = held.map(data -> (Component) Component.translatable("propwash_example.tab.held",
                        data.name().orElse(data.build().frame().toString())))
                .orElse(Component.translatable("propwash_example.tab.no_drone"));
        ThemeText.text(g, this.host.font(), drone, this.x, y, Palette.TEXT_MUTED);
        y += Metrics.LINE_HEIGHT;
        if (this.status != null) {
            ThemeText.text(g, this.host.font(), this.status, this.x, y, this.statusColor);
        }
    }
}
