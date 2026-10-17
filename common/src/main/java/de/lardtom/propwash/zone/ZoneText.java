package de.lardtom.propwash.zone;

import de.lardtom.propwash.server.WorldRuleText;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public final class ZoneText {
    public static final String KEY = "propwash.zone.";
    public static final String LIST_SEPARATOR = ", ";

    private ZoneText() {
    }

    public static String number(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    public static MutableComponent size(Zone zone) {
        ZoneBox box = zone.box();
        if (zone.shape() == ZoneShape.CYLINDER) {
            return Component.translatable(KEY + "size.cylinder", number(box.radius() * 2.0), number(box.sizeY()));
        }
        return Component.translatable(KEY + "size.box", number(box.sizeX()), number(box.sizeY()), number(box.sizeZ()));
    }

    public static MutableComponent shape(Zone zone) {
        return Component.translatable(zone.shape().translationKey());
    }

    public static MutableComponent position(Zone zone) {
        ZoneBox box = zone.box();
        return Component.translatable(KEY + "position", number(box.centerX()), number(box.minY()), number(box.centerZ()));
    }

    public static MutableComponent kind(Zone zone) {
        if (zone.raceLegal()) {
            return Component.translatable(KEY + "kind.race_legal");
        }
        return Component.translatable(zone.changesPhysics() ? KEY + "kind.assisted" : KEY + "kind.neutral");
    }

    public static MutableComponent value(RuleSpec spec, int value) {
        return Component.translatable(KEY + "rule_value", Component.translatable(spec.translationKey()), WorldRuleText.value(spec, value));
    }

    public static MutableComponent overrides(Zone zone) {
        MutableComponent out = Component.empty();
        int count = 0;
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (zone.overrides(spec)) {
                if (count++ > 0) {
                    out.append(LIST_SEPARATOR);
                }
                out.append(value(spec, zone.value(spec, spec.defaultValue())));
            }
        }
        return count == 0 ? Component.translatable(KEY + "overrides.none") : out;
    }

    public static MutableComponent transition(int tenths) {
        return tenths <= 0 ? Component.translatable(KEY + "transition.instant")
                : Component.translatable(KEY + "transition.seconds", number(tenths / 10.0));
    }

    public static MutableComponent summary(Zone zone) {
        return Component.translatable(KEY + "summary", zone.name(), shape(zone), size(zone), position(zone), zone.priority(), kind(zone),
                overrides(zone));
    }
}
