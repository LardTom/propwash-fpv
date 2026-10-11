package de.lardtom.propwash.dev.example.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.client.PropwashClientApi;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExampleSizeScreen extends Screen implements ThemedScreen {
    public static final float FILL = 0.9F;
    public static final int BAR_MM = 200;
    private static final int MAX_WIDTH = 420;
    private static final int MAX_HEIGHT = 220;
    private static final int PAD = 8;
    private static final int GAP = 8;
    private static final float YAW = 205.0F;

    private static @Nullable ExampleSizeScreen active;

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private int left;
    private int top;
    private int right;
    private int bottom;
    private float pixelsPerMillimetre;
    private long frames;

    public record Entry(Component name, DroneData data, double footprintMm) {
    }

    public ExampleSizeScreen(Screen parent) {
        super(Component.translatable("propwash_example.size.title"));
        this.parent = parent;
        PropwashApi.builds().presets().stream().filter(p -> p.id().equals(Identifier.fromNamespaceAndPath("propwash", "freestyle"))).findFirst()
                .map(DronePreset::data).ifPresent(data -> this.add(Component.translatable("propwash_example.size.freestyle"), data));
        this.add(Component.translatable("propwash_example.frame_short.deadcat_5"), ExampleAddon.deadcat());
        this.add(Component.translatable("propwash_example.frame_short.race_stretch_5"), ExampleAddon.stretched());
        this.add(Component.translatable("propwash_example.frame_short.xclass_13"), ExampleAddon.xclass());
    }

    private void add(Component name, DroneData data) {
        try {
            this.entries.add(new Entry(name, data, PropwashApi.builds().footprintMm(data.build())));
        } catch (IllegalArgumentException e) {
            this.entries.add(new Entry(name, data, Double.NaN));
        }
    }

    public static @Nullable ExampleSizeScreen active() {
        return active;
    }

    public List<Entry> entries() {
        return List.copyOf(this.entries);
    }

    public float pixelsPerMillimetre() {
        return this.pixelsPerMillimetre;
    }

    public long frames() {
        return this.frames;
    }

    public static float pixelsPerMillimetre(List<Entry> entries, int width) {
        double total = 0.0;
        int count = 0;
        for (Entry entry : entries) {
            if (entry.footprintMm() > 0.0) {
                total += entry.footprintMm();
                count++;
            }
        }
        if (count == 0) {
            return 0.0F;
        }
        return (float) (Math.max(0, width - GAP * (count - 1)) * FILL / total);
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        this.frames++;
        ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
        int titleEnd = ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.right, Metrics.HEADER_HEIGHT, this.title, this.right - PAD);
        ThemeDraw.headerSubtitle(g, this.font, titleEnd, this.top, Metrics.HEADER_HEIGHT, Component.translatable("propwash_example.size.subtitle"),
                this.right - PAD);
        int footerTop = this.bottom - Metrics.FOOTER_HEIGHT;
        int stageLeft = this.left + PAD;
        int stageRight = this.right - PAD;
        int stageTop = this.top + Metrics.HEADER_HEIGHT + PAD;
        int stageBottom = footerTop - PAD - 2 * Metrics.LINE_HEIGHT;
        ThemeDraw.previewStage(g, stageLeft, stageTop, stageRight, stageBottom);
        int inner = stageRight - stageLeft - 2 * PAD;
        this.pixelsPerMillimetre = pixelsPerMillimetre(this.entries, inner);
        int x = stageLeft + PAD;
        for (Entry entry : this.entries) {
            if (!(entry.footprintMm() > 0.0)) {
                continue;
            }
            int w = Math.max(8, Math.round((float) entry.footprintMm() * this.pixelsPerMillimetre / FILL));
            int center = x + w / 2;
            ThemeText.textCentered(g, this.font, Component.literal(String.format(Locale.ROOT, "Ø %.0f mm", entry.footprintMm())), center,
                    stageTop + 4, Palette.TEXT_MUTED);
            PropwashClientApi.renderPreview(g, entry.data(), x, stageTop + 14, x + w, stageBottom - 4, YAW, this.pixelsPerMillimetre);
            ThemeText.textCentered(g, this.font, entry.name(), center, stageBottom + 4, Palette.TEXT);
            x += w + GAP;
        }
        int bar = Math.round(BAR_MM * this.pixelsPerMillimetre);
        int barY = footerTop - PAD - 4;
        g.fill(stageLeft, barY, stageLeft + bar, barY + 2, Palette.ACCENT);
        ThemeText.text(g, this.font, Component.literal(BAR_MM / 10 + " cm"), stageLeft + bar + 4, barY - 3, Palette.TEXT_MUTED);
        ThemeDraw.footerBar(g, this.left, footerTop, this.right, this.bottom);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, this.left, this.top, this.right, this.bottom);
    }
}
