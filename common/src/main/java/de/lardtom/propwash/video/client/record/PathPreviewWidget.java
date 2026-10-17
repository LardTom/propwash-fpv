package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.LineBatch;
import de.lardtom.propwash.ui.client.widget.PanelWidget;
import de.lardtom.propwash.video.record.path.PathPreview;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class PathPreviewWidget extends PanelWidget {
    public static final int DRAW_HEIGHT = 112;
    public static final int TEXT_LINES = 6;
    public static final int HEIGHT = DRAW_HEIGHT + 4 + TEXT_LINES * Metrics.LINE_HEIGHT;
    public static final int MAGNIFIER_TICKS = 60;
    private static final double MAIN_SHARE = 0.62;

    private final Supplier<PathPreviewModel> model;
    private final Runnable open;
    private final LineBatch batch = new LineBatch();
    private PathPreviewDraw.View view = PathPreviewDraw.View.TOP;
    private PathPreviewDraw.@Nullable Projection projection;
    private @Nullable PathPreview projected;
    private PathPreviewDraw.@Nullable View projectedView;

    public PathPreviewWidget(int x, int y, int width, Supplier<PathPreviewModel> model, Runnable open) {
        super(x, y, width, HEIGHT, Component.translatable(SimplificationText.PREVIEW + "title"));
        this.model = model;
        this.open = open;
    }

    public PathPreviewDraw.View view() {
        return this.view;
    }

    public void view(PathPreviewDraw.View view) {
        this.view = view;
    }

    private PathPreviewDraw.@Nullable Projection projection(@Nullable PathPreview preview) {
        if (preview == null) {
            return null;
        }
        if (this.projection == null || this.projected != preview || this.projectedView != this.view) {
            this.projection = PathPreviewDraw.Projection.of(preview, this.view);
            this.projected = preview;
            this.projectedView = this.view;
        }
        return this.projection;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.open.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        PathPreviewModel current = this.model.get();
        current.tick();
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        int y1 = y0 + DRAW_HEIGHT;
        Font font = this.font();
        ThemeDraw.framedBox(g, x0, y0, x1, y1, Palette.FIELD, this.isHovered() ? Palette.BORDER_STRONG : Palette.BORDER);
        PathPreview preview = current.result();
        this.lines(g, font, current, preview, x0, y1 + 4, this.width, y0 + this.height);
        PathPreviewDraw.Projection projection = this.projection(preview);
        if (preview == null || projection == null || projection.ticks() < 2) {
            Component text = Component.translatable(SimplificationText.PREVIEW + (current.empty() ? "unavailable" : "computing"));
            ThemeText.text(g, font, text, x0 + (this.width - font.width(text)) / 2, y0 + (DRAW_HEIGHT - Metrics.LINE_HEIGHT) / 2, Palette.TEXT_MUTED);
            return;
        }
        int split = x0 + (int) Math.round(this.width * MAIN_SHARE);
        int mainX0 = x0 + 1;
        int mainX1 = split - 1;
        int magX0 = split + 1;
        int magX1 = x1 - 1;
        int innerY0 = y0 + 1;
        int innerY1 = y1 - 1;
        PathPreviewDraw.Frame main = PathPreviewDraw.fit(projection, 0, projection.ticks() - 1, mainX1 - mainX0, innerY1 - innerY0 - 12, 1.0);
        main = main.moved(0.0, -6.0 / main.scale());
        PathPreviewDraw.draw(g, this.batch, projection, main, mainX0, innerY0, mainX1, innerY1, 0, projection.ticks() - 1, false, true, -1);
        int worst = PathPreviewDraw.gapIndex(projection);
        PathPreviewDraw.Frame zoom = PathPreviewDraw.aroundGap(projection, magX1 - magX0, innerY1 - innerY0);
        PathPreviewDraw.draw(g, this.batch, projection, zoom, magX0, innerY0, magX1, innerY1, worst - MAGNIFIER_TICKS, worst + MAGNIFIER_TICKS, true,
                true, -1);
        g.fill(split, innerY0, split + 1, innerY1, Palette.BORDER);
        this.legend(g, font, mainX0 + 4, innerY0 + 3, mainX1 - mainX0 - 8);
        long factor = Math.max(1L, Math.round(zoom.scale() / Math.max(1.0E-6, main.scale())));
        Component label = Component.translatable(SimplificationText.PREVIEW + "magnifier", Numbers.of(factor, 0));
        int room = magX1 - magX0 - 8;
        Component shortLabel = Component.literal("×").append(Numbers.of(factor, 0));
        Component shown = font.width(label) <= room ? label : font.width(shortLabel) <= room ? shortLabel : null;
        if (shown != null) {
            ThemeText.text(g, font, shown, magX0 + 4, innerY0 + 3, Palette.TEXT_MUTED);
        }
        if (current.busy()) {
            Component busy = Component.literal("…");
            ThemeText.text(g, font, busy, x1 - 6 - font.width(busy), y1 - 3 - Metrics.LINE_HEIGHT, Palette.TEXT_FAINT);
        }
    }

    private void lines(GuiGraphicsExtractor g, Font font, PathPreviewModel current, @Nullable PathPreview preview, int x, int y, int width,
                       int bottom) {
        List<Component> texts = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (preview != null) {
            texts.add(SimplificationText.metrics(preview));
            colors.add(Palette.TEXT);
            Component cap = SimplificationText.cap(preview);
            if (cap != null) {
                texts.add(cap);
                colors.add(preview.built().capped() ? Palette.ACCENT : Palette.WARNING);
            }
            texts.add(SimplificationText.fits(preview, current.limit()));
            colors.add(Palette.TEXT_MUTED);
        }
        texts.add(current.sourceLine());
        colors.add(Palette.TEXT_FAINT);
        int line = y;
        for (int i = 0; i < texts.size(); i++) {
            for (FormattedCharSequence row : font.split(texts.get(i), Math.max(20, width))) {
                if (line + Metrics.LINE_HEIGHT > bottom + 1) {
                    return;
                }
                ThemeText.text(g, font, row, x, line, colors.get(i));
                line += Metrics.LINE_HEIGHT;
            }
        }
    }

    private void legend(GuiGraphicsExtractor g, Font font, int x, int y, int room) {
        Component actual = Component.translatable(SimplificationText.PREVIEW + "actual");
        Component saved = Component.translatable(SimplificationText.PREVIEW + "saved");
        int first = 13 + font.width(actual);
        int second = 13 + font.width(saved);
        boolean stacked = first + 8 + second > room;
        int width = stacked ? Math.max(first, second) : first + 8 + second;
        int height = stacked ? 2 * Metrics.LINE_HEIGHT : Metrics.LINE_HEIGHT;
        g.fill(x - 3, y - 2, x + Math.min(width, room) + 3, y + height, 0xC0121212);
        int lineY = y + Metrics.LINE_HEIGHT / 2 - 1;
        g.fill(x, lineY, x + 10, lineY + 1, PathPreviewDraw.ACTUAL);
        ThemeText.text(g, font, ThemeText.fit(font, actual, Math.max(10, room - 13)), x + 13, y, Palette.TEXT_MUTED);
        int x2 = stacked ? x : x + first + 8;
        int y2 = stacked ? y + Metrics.LINE_HEIGHT : y;
        int lineY2 = y2 + Metrics.LINE_HEIGHT / 2 - 1;
        g.fill(x2, lineY2, x2 + 10, lineY2 + 2, PathPreviewDraw.SAVED);
        ThemeText.text(g, font, ThemeText.fit(font, saved, Math.max(10, room - 13)), x2 + 13, y2, Palette.TEXT_MUTED);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
