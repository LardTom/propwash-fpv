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
    public static final String OPEN_KEY = "propwash.zones_open.";
    public static final String NOTICE_SEPARATOR = " · ";

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
        return overrides(zone, ZoneRelease.DEFAULT);
    }

    public static MutableComponent overrides(Zone zone, ZoneRelease release) {
        MutableComponent out = Component.empty();
        int count = 0;
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (zone.sets(spec)) {
                if (count++ > 0) {
                    out.append(LIST_SEPARATOR);
                }
                MutableComponent value = zone.forcesWorld(spec) ? Component.translatable(OPEN_KEY + "rule.world_only", Component.translatable(spec.translationKey()))
                        : value(spec, zone.value(spec, spec.defaultValue()));
                out.append(release.released(spec) ? value : Component.translatable(OPEN_KEY + "blocked_value", value));
            }
        }
        return count == 0 ? Component.translatable(KEY + "overrides.none") : out;
    }

    public static MutableComponent worldValue(RuleSpec spec, int value) {
        return Component.translatable(OPEN_KEY + "rule.world", Component.translatable(spec.translationKey()), WorldRuleText.value(spec, value));
    }

    public static MutableComponent decided(ZoneResolution resolution, Zone zone) {
        MutableComponent out = Component.empty();
        int count = 0;
        for (RuleSpec spec : PhysicsRules.SPECS) {
            Zone decider = resolution.decider(spec);
            if (decider == null || decider.id() != zone.id()) {
                continue;
            }
            if (count++ > 0) {
                out.append(LIST_SEPARATOR);
            }
            int value = resolution.rules().value(spec);
            out.append(resolution.forcedBy(spec) != null ? worldValue(spec, value) : value(spec, value));
        }
        return count == 0 ? Component.empty() : out;
    }

    public static MutableComponent kind(ZoneResolution resolution) {
        Zone top = resolution.top();
        if (!resolution.zoneDetails().isEmpty()) {
            return Component.translatable(KEY + "kind.assisted");
        }
        if (top == null) {
            return Component.translatable(KEY + "kind.neutral");
        }
        for (RuleSpec spec : PhysicsRules.SPECS) {
            Zone source = resolution.source(spec);
            if (source != null && source.raceLegal() && resolution.rules().value(spec) != spec.defaultValue()) {
                return Component.translatable(KEY + "kind.race_legal");
            }
        }
        return top.raceLegal() ? Component.translatable(KEY + "kind.race_legal") : Component.translatable(KEY + "kind.neutral");
    }

    public static MutableComponent notice(ZoneResolution resolution) {
        Zone top = resolution.top();
        MutableComponent kind = kind(resolution);
        if (top == null) {
            return kind;
        }
        MutableComponent parts = Component.empty();
        int count = 0;
        for (Zone zone : resolution.inside()) {
            MutableComponent decided = decided(resolution, zone);
            if (decided.getSiblings().isEmpty()) {
                continue;
            }
            if (count++ > 0) {
                parts.append(NOTICE_SEPARATOR);
            }
            parts.append(zone.id() == top.id() ? decided : Component.translatable(OPEN_KEY + "from_zone", zone.name(), decided));
        }
        return count == 0 ? kind : Component.translatable(KEY + "hud.detail", parts, kind);
    }

    public static MutableComponent owner(Zone zone) {
        ZoneOwner owner = zone.owner();
        if (owner == null) {
            return Component.translatable(OPEN_KEY + "owner.admin");
        }
        return Component.translatable(OPEN_KEY + "owner.player", owner.name().isEmpty() ? owner.id().toString() : owner.name());
    }

    public static MutableComponent transition(int tenths) {
        return tenths <= 0 ? Component.translatable(KEY + "transition.instant")
                : Component.translatable(KEY + "transition.seconds", number(tenths / 10.0));
    }

    public static MutableComponent summary(Zone zone) {
        return Component.translatable(KEY + "summary", zone.name(), shape(zone), size(zone), position(zone), zone.priority(), kind(zone),
                overrides(zone));
    }

    public static MutableComponent summary(Zone stored, ZoneRelease release) {
        Zone effective = release.effective(stored);
        MutableComponent out = Component.translatable(KEY + "summary", stored.name(), shape(stored), size(stored), position(stored),
                stored.priority(), kind(effective), overrides(stored, release));
        if (stored.owned()) {
            out.append(" · ").append(owner(stored));
        }
        return out;
    }
}
