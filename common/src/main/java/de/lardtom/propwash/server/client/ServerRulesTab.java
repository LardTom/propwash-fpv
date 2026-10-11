package de.lardtom.propwash.server.client;

import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.server.RangeStatusS2C;
import de.lardtom.propwash.server.ServerRulesS2C;
import de.lardtom.propwash.server.ServerRulesText;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class ServerRulesTab extends ConfigTab {
    private static final int LABEL_COLOR = Palette.TEXT_MUTED;
    private static final int VALUE_COLOR = Palette.TEXT;
    private static final int NOTE_COLOR = Palette.TEXT_FAINT;
    private static final int ROW_HEIGHT = Metrics.TABLE_ROW;
    private static final String MAX_DISTANCE_KEY = ServerRulesText.DISTANCE_LIMIT_KEY;

    @Override
    public String id() {
        return "server";
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.server.tab.title");
    }

    @Override
    public int order() {
        return 9000;
    }

    @Override
    public boolean hasDefaults() {
        return false;
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    protected void build() {
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Font font = this.host.font();
        int left = this.x;
        int right = this.x + this.width - Metrics.SCROLLBAR_WIDTH;
        int textWidth = Math.max(40, right - left);
        int y = this.y;
        ThemeDraw.sectionTitle(graphics, font, Component.translatable("propwash.server.tab.header"), left, y, right - left);
        y += Metrics.SECTION_HEIGHT;
        ServerRulesS2C rules = ClientServerRules.INSTANCE.rules();
        if (rules == null) {
            this.reportBottom(this.paragraph(graphics, font, Component.translatable("propwash.server.tab.unknown"), left, y, textWidth, LABEL_COLOR));
            return;
        }
        boolean shade = false;
        for (ServerRulesText.Row row : rowsWithRadio(rules)) {
            int pairWidth = right - left - 2 * Metrics.LABEL_GAP;
            int rowHeight = ROW_HEIGHT - Metrics.LINE_HEIGHT + ThemeText.pairHeight(font, row.label(), row.value(), pairWidth, 2 * Metrics.LABEL_GAP);
            if (shade) {
                graphics.fill(left, y, right, y + rowHeight, Palette.ROW_STRIPE);
            }
            shade = !shade;
            ThemeText.pair(graphics, font, row.label(), row.value(), left + Metrics.LABEL_GAP, Metrics.textY(y, ROW_HEIGHT), pairWidth,
                    2 * Metrics.LABEL_GAP, LABEL_COLOR, VALUE_COLOR);
            y += rowHeight;
        }
        y += Metrics.GAP;
        y = this.paragraph(graphics, font, statusText(ClientServerRules.INSTANCE.rangeStatus()), left, y, textWidth, VALUE_COLOR);
        y += Metrics.LABEL_GAP;
        this.reportBottom(this.paragraph(graphics, font, Component.translatable("propwash.server.tab.readonly"), left, y, textWidth, NOTE_COLOR));
    }

    private static List<ServerRulesText.Row> rowsWithRadio(ServerRulesS2C rules) {
        List<ServerRulesText.Row> rows = new ArrayList<>();
        for (ServerRulesText.Row row : ServerRulesText.rows(rules)) {
            rows.add(row);
            if (row.label().getContents() instanceof TranslatableContents label && MAX_DISTANCE_KEY.equals(label.getKey())) {
                rows.add(new ServerRulesText.Row(Component.translatable("propwash.server.rule.radio_range"), radioRange()));
            }
        }
        return rows;
    }

    private static Component radioRange() {
        if (!RadioLink.LOCAL_FREE_RANGE.getAsBoolean()) {
            return Component.translatable("propwash.server.value.radio_about", RadioLink.displayRange());
        }
        double limit = RadioLink.SERVER_LIMIT.getAsDouble();
        return Double.isFinite(limit)
                ? Component.translatable("propwash.server.value.radio_server", ServerRulesText.blocks(limit))
                : Component.translatable("propwash.server.value.radio_free");
    }

    private int paragraph(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
        List<FormattedCharSequence> lines = ThemeText.balanced(font, text, maxWidth);
        for (FormattedCharSequence line : lines) {
            ThemeText.text(graphics, font, line, x, y, color);
            y += font.lineHeight + 1;
        }
        return y;
    }

    private static Component statusText(RangeStatusS2C status) {
        if (!status.piloting()) {
            return Component.translatable("propwash.server.tab.status.idle");
        }
        if (status.anchored()) {
            return Component.translatable("propwash.server.tab.status.anchored", status.streamRadius());
        }
        return Component.translatable("propwash.server.tab.status.limited", ServerRulesText.blocks(status.maxDistance()));
    }
}
