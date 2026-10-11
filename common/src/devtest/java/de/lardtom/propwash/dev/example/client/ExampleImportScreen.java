package de.lardtom.propwash.dev.example.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ExampleImportScreen extends Screen implements ThemedScreen {
    private static final int MAX_WIDTH = 380;
    private static final int MAX_HEIGHT = 230;
    private static final int PAD = 8;

    private static @Nullable ExampleImportScreen active;

    private final Screen parent;
    private final WorkbenchContext context;
    private final DroneData data = ExampleAddon.xclass();
    private final BuildCheck check;
    private List<Identifier> missing = List.of();
    private @Nullable CompletableFuture<ImportResult> pending;
    private @Nullable ImportResult result;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private long frames;

    public ExampleImportScreen(Screen parent, WorkbenchContext context) {
        super(Component.translatable("propwash_example.import.title"));
        this.parent = parent;
        this.context = context;
        this.check = PropwashApi.builds().check(this.data.build());
    }

    public static @Nullable ExampleImportScreen active() {
        return active;
    }

    public DroneData data() {
        return this.data;
    }

    public BuildCheck check() {
        return this.check;
    }

    public List<Identifier> missing() {
        return this.missing;
    }

    public @Nullable ImportResult result() {
        return this.result;
    }

    public long frames() {
        return this.frames;
    }

    public CompletableFuture<ImportResult> importLayers(Set<ImportLayer> layers) {
        CompletableFuture<ImportResult> future = this.context.importIntoWorkbench(this.data, layers, TuneGroup.FLIGHT);
        this.pending = future;
        this.result = null;
        future.thenAccept(done -> {
            if (this.pending == future) {
                this.result = done;
            }
        });
        return future;
    }

    @Override
    protected void init() {
        active = this;
        int w = Math.min(this.width - 16, MAX_WIDTH);
        int h = Math.min(this.height - 12, MAX_HEIGHT);
        this.left = (this.width - w) / 2;
        this.right = this.left + w;
        this.top = (this.height - h) / 2;
        this.bottom = this.top + h;
        int buttonY = this.bottom - Metrics.FOOTER_HEIGHT + (Metrics.FOOTER_HEIGHT - Metrics.CONTROL_HEIGHT) / 2;
        Component back = Component.translatable("propwash_example.import.back");
        int backWidth = Math.max(60, this.font.width(back) + 16);
        this.addRenderableWidget(new FlatButton(this.right - PAD - backWidth, buttonY, backWidth, Metrics.CONTROL_HEIGHT, back,
                FlatButton.Style.NORMAL, this::onClose));
        Component all = Component.translatable("propwash_example.import.all");
        int allWidth = this.font.width(all) + 16;
        FlatButton whole = new FlatButton(this.left + PAD, buttonY, allWidth, Metrics.CONTROL_HEIGHT, all, FlatButton.Style.PRIMARY,
                () -> this.importLayers(EnumSet.allOf(ImportLayer.class)));
        whole.active = this.check.known();
        this.addRenderableWidget(whole);
        Component paint = Component.translatable("propwash_example.import.paint");
        int paintWidth = this.font.width(paint) + 16;
        FlatButton paintOnly = new FlatButton(this.left + PAD + allWidth + Metrics.GAP, buttonY, paintWidth, Metrics.CONTROL_HEIGHT, paint,
                FlatButton.Style.NORMAL, () -> this.importLayers(EnumSet.of(ImportLayer.PAINT)));
        paintOnly.active = this.check.known();
        this.addRenderableWidget(paintOnly);
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
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    static Component counted(String key, int count) {
        return switch (count) {
            case 0 -> Component.translatable(key + ".none");
            case 1 -> Component.translatable(key + ".one");
            default -> Component.translatable(key + ".many", count);
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        this.frames++;
        ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
        ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.right, Metrics.HEADER_HEIGHT, this.title, this.right - 8);
        int contentTop = this.top + Metrics.HEADER_HEIGHT + PAD;
        int footerTop = this.bottom - Metrics.FOOTER_HEIGHT;
        int previewRight = this.left + (this.right - this.left) / 2;
        ThemeDraw.previewStage(g, this.left + PAD, contentTop, previewRight, footerTop - PAD);
        PropwashClientApi.renderPreview(g, this.data, this.left + PAD, contentTop, previewRight, footerTop - PAD, 200.0F);
        int textX = previewRight + PAD;
        int textWidth = this.right - PAD - textX;
        int y = contentTop;
        ThemeText.text(g, this.font, Component.literal(this.data.name().orElse("")), textX, y, Palette.TEXT);
        y += Metrics.LINE_HEIGHT + 2;
        y += ThemeText.wrapped(g, this.font, Component.translatable("propwash_example.import.intro"), textX, y, textWidth, Palette.TEXT_MUTED) + 4;
        Component checkLine;
        int checkColor;
        if (!this.check.known()) {
            checkLine = counted("propwash_example.import.unknown", this.check.unknownParts().size());
            checkColor = Palette.ERROR;
        } else if (!this.check.buildable()) {
            checkLine = counted("propwash_example.import.problems", this.check.problems().size());
            checkColor = Palette.WARNING;
        } else {
            checkLine = counted("propwash_example.import.ready", this.check.warnings().size());
            checkColor = Palette.SUCCESS;
        }
        y += ThemeText.wrapped(g, this.font, checkLine, textX, y, textWidth, checkColor) + 4;
        if (this.check.known()) {
            this.missing = this.context.missingParts(this.data);
            Component missingLine = this.context.creative() ? Component.translatable("propwash_example.import.missing.creative")
                    : counted("propwash_example.import.missing", this.missing.size());
            y += ThemeText.wrapped(g, this.font, missingLine, textX, y, textWidth, this.missing.isEmpty() ? Palette.TEXT_MUTED : Palette.WARNING) + 4;
        }
        if (this.pending != null && this.result == null) {
            ThemeText.text(g, this.font, Component.translatable("propwash_example.import.sending"), textX, y, Palette.ACCENT);
        } else if (this.result != null) {
            int color = this.result.status().success() ? Palette.SUCCESS : Palette.ERROR;
            ThemeText.wrapped(g, this.font, Component.translatable(this.result.status().translationKey()), textX, y, textWidth, color);
        }
        ThemeDraw.footerBar(g, this.left, footerTop, this.right, this.bottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.left, this.top, this.right, this.bottom);
    }
}
