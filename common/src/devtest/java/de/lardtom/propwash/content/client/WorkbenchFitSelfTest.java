package de.lardtom.propwash.content.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.content.WorkbenchLayout;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkbenchFitSelfTest {
    private static final String KEY = "propwash.content.workbench.";

    private WorkbenchFitSelfTest() {
    }

    private static Component unit(String unit, Component value) {
        return Component.translatable(KEY + "unit." + unit, value);
    }

    static Map<String, List<Component>> widestValues() {
        Map<String, List<Component>> out = new LinkedHashMap<>();
        Component none = Component.translatable("propwash.content.number.none");
        out.put("dry_weight", List.of(unit("grams", Numbers.of(8888.0, 0)), none));
        out.put("takeoff_weight", List.of(unit("grams", Numbers.of(8888.0, 0)), none));
        out.put("thrust_to_weight", List.of(unit("ratio", Numbers.fixed(88.8, 1)), none));
        out.put("hover_throttle", List.of(unit("percent", Numbers.of(100.0, 0)), none));
        out.put("top_speed", List.of(unit("kmh", Numbers.of(888.0, 0)), none));
        out.put("flight_time", List.of(unit("minutes", Numbers.minutes(88.0 + 58.0 / 60.0)), none));
        return out;
    }

    public static StepResult check(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        List<String> tight = new ArrayList<>();
        int rows = 0;
        for (Map.Entry<String, List<Component>> row : widestValues().entrySet()) {
            Component label = Component.translatable(KEY + "stat." + row.getKey());
            for (Component value : row.getValue()) {
                rows++;
                if (font.width(label) > WorkbenchLayout.statLabelWidth(font.width(value))) {
                    tight.add("„" + label.getString() + "“ neben „" + value.getString() + "“");
                }
            }
        }
        for (DroneBuild build : DroneBuild.PRESETS) {
            for (WorkbenchReport.Row row : WorkbenchReport.of(BuildSlots.check(BuildSlots.layout(build)), true).rows()) {
                rows++;
                if (font.width(row.label()) > WorkbenchLayout.statLabelWidth(font.width(row.value()))) {
                    tight.add(build.frame().key() + ": „" + row.label().getString() + "“ neben „" + row.value().getString() + "“");
                }
            }
        }
        Component header = Component.translatable(KEY + "lifted_header");
        rows++;
        if (font.width(header) > WorkbenchLayout.issueTextWidth()) {
            tight.add("Überschrift „" + header.getString() + "“ " + font.width(header) + " px, Liste " + WorkbenchLayout.issueTextWidth() + " px");
        }
        if (!tight.isEmpty()) {
            return StepResult.fail("Kennzahlen überlappen (" + mc.getLanguageManager().getSelected() + "): " + tight);
        }
        ctx.log(rows + " Werkbank-Zeilen passen (" + mc.getLanguageManager().getSelected() + ", "
                + Component.translatable(KEY + "stat.flight_time").getString() + " " + font.width(Component.translatable(KEY + "stat.flight_time"))
                + " px, Platz neben dem breitesten Wert " + WorkbenchLayout.statLabelWidth(font.width(unit("minutes", Numbers.minutes(88.0 + 58.0 / 60.0))))
                + " px)");
        return StepResult.pass();
    }
}
