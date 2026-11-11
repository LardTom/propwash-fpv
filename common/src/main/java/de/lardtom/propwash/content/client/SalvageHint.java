package de.lardtom.propwash.content.client;

import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.Recycling;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class SalvageHint {
    public static final String KEY = "propwash.content.workbench.";
    public static final String TITLE = KEY + "salvage_title";
    public static final String SALVAGE = KEY + "hint.salvage";
    public static final String RECYCLE_TAB = KEY + "hint.recycle";
    public static final String BLOCKED = KEY + "hint.salvage_blocked";
    public static final String REJECT = KEY + "salvage_reject.";
    public static final String REJECT_PART = REJECT + "part";
    public static final String REJECT_OTHER = REJECT + "other";

    private SalvageHint() {
    }

    public static List<Line> lines(ItemStack carried, boolean partsEmpty) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.title(TITLE));
        if (carried.isEmpty() || DroneAssembly.isDrone(carried)) {
            lines.add(Line.body(SALVAGE));
            lines.add(Line.body(RECYCLE_TAB));
            if (!partsEmpty) {
                lines.add(Line.warning(BLOCKED));
            }
            DroneWorkbenchScreen.missingWarning(carried).ifPresent(text -> lines.add(new Line(text, Kind.WARNING)));
            return lines;
        }
        lines.add(Line.warning(Recycling.isPart(carried) ? REJECT_PART : REJECT_OTHER));
        return lines;
    }

    public enum Kind {
        TITLE,
        BODY,
        WARNING
    }

    public record Line(Component text, Kind kind) {
        static Line title(String key) {
            return new Line(Component.translatable(key), Kind.TITLE);
        }

        static Line body(String key) {
            return new Line(Component.translatable(key), Kind.BODY);
        }

        static Line warning(String key) {
            return new Line(Component.translatable(key), Kind.WARNING);
        }
    }
}
