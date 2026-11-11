package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.ServerRulesBadge;
import de.lardtom.propwash.server.client.ClientPersonalRules;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ServerRulesText {
    public static final String KEY = ServerRulesBadge.KEY;

    private ServerRulesText() {
    }

    public static List<ServerRulesBadge.Line> board() {
        return ServerRulesBadge.board(ClientPersonalRules.INSTANCE.standard());
    }

    public static List<ServerRulesBadge.Line> entry(@Nullable RaceStandard flown) {
        return ServerRulesBadge.entry(flown, ClientPersonalRules.INSTANCE.standard());
    }

    public static Component label() {
        return Component.translatable(KEY + "label");
    }

    public static Component shortLabel() {
        return Component.translatable(KEY + "short");
    }

    public static int width(Font font) {
        return font.width(label()) + 8;
    }

    public static int shortWidth(Font font) {
        return font.width(shortLabel()) + 8;
    }

    public static int draw(GuiGraphicsExtractor g, Font font, int x, int y0, int y1) {
        return draw(g, font, label(), x, y0, y1);
    }

    public static int draw(GuiGraphicsExtractor g, Font font, Component label, int x, int y0, int y1) {
        return ThemeDraw.badge(g, font, label, x, y0, y1, Palette.INFO, Palette.BACKGROUND);
    }

    public static MutableComponent lines(List<ServerRulesBadge.Line> lines) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            ServerRulesBadge.Line line = lines.get(i);
            if (i > 0) {
                out.append("\n");
            }
            out.append(AssistWords.rule(line.rule(), line.value(), line.reference()));
        }
        return out;
    }

    public static Component boardTooltip(List<ServerRulesBadge.Line> lines) {
        return Component.empty().append(ThemeText.accent(Component.translatable(KEY + "board.title"))).append("\n").append(lines(lines))
                .append("\n").append(ThemeText.muted(Component.translatable(KEY + "board.note")));
    }

    public static Component entryTooltip(List<ServerRulesBadge.Line> lines) {
        return Component.empty().append(ThemeText.accent(Component.translatable(KEY + "entry.title"))).append("\n").append(lines(lines));
    }
}
