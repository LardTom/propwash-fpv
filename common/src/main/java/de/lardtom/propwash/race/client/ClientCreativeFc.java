package de.lardtom.propwash.race.client;

import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.AssistCheck;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CreativeFcRule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ClientCreativeFc {
    public static final String KEY = CreativeFcRule.KEY;

    private ClientCreativeFc() {
    }

    public static boolean world() {
        return ClientWorldRules.INSTANCE.on(WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL);
    }

    public static CreativeFcRule rule(int courseId) {
        CourseData course = ClientCourses.get(courseId);
        return course == null ? CreativeFcRule.WORLD : course.creativeFc();
    }

    public static boolean legal(int courseId) {
        return rule(courseId).legal(world());
    }

    public static AssistReasons apply(AssistReasons reasons, int courseId) {
        return AssistCheck.creativeFc(reasons, legal(courseId));
    }

    public static boolean canSend() {
        return ClientNet.canSend(RacePayloads.CourseCreativeFcC2S.TYPE);
    }

    public static boolean send(int courseId, CreativeFcRule rule) {
        return canSend() && ClientNet.sendToServer(new RacePayloads.CourseCreativeFcC2S(courseId, rule));
    }

    public static Component state(int courseId) {
        CreativeFcRule rule = rule(courseId);
        Component verdict = Component.translatable(KEY + (rule.legal(world()) ? "state.legal" : "state.assist"));
        if (rule != CreativeFcRule.WORLD) {
            return Component.translatable(KEY + "state.course", verdict);
        }
        return Component.translatable(KEY + "state.world", verdict, Component.translatable(KEY + (world() ? "world.on" : "world.off")));
    }

    public static Component label() {
        return Component.translatable(KEY + "badge.label");
    }

    public static int width(Font font) {
        return font.width(label()) + 8;
    }

    public static int draw(GuiGraphicsExtractor g, Font font, int x, int y0, int y1) {
        return ThemeDraw.badge(g, font, label(), x, y0, y1, Palette.INFO, Palette.BACKGROUND);
    }

    public static Component tooltip(int courseId) {
        String source = rule(courseId) == CreativeFcRule.WORLD ? "badge.world" : "badge.course";
        return Component.empty().append(ThemeText.accent(Component.translatable(KEY + "badge.title"))).append("\n")
                .append(Component.translatable(KEY + "badge.note")).append("\n").append(ThemeText.muted(Component.translatable(KEY + source)));
    }
}
