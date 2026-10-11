package de.lardtom.propwash.server;

import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Optional;

public final class WorldRuleText {
    private WorldRuleText() {
    }

    public static Optional<RuleSpec> spec(Identifier id) {
        return RuleSpec.NAMESPACE.equals(id.getNamespace()) ? WorldRuleSpecs.byName(id.getPath()) : Optional.empty();
    }

    public static String nameKey(Identifier id) {
        return Util.makeDescriptionId("gamerule", id);
    }

    public static MutableComponent name(Identifier id) {
        return Component.translatable(nameKey(id));
    }

    public static MutableComponent value(RuleSpec spec, int value) {
        if (spec.kind() == RuleSpec.Kind.BOOL) {
            return onOff(value != 0);
        }
        if (spec.labelled(value)) {
            return Component.translatable(spec.valueKey(value));
        }
        if (spec.display() == RuleSpec.Display.PERCENT) {
            return Component.translatable("propwash.server.world_rules.percent", value);
        }
        return Component.literal(Integer.toString(value));
    }

    public static MutableComponent value(Identifier id, String raw) {
        Optional<RuleSpec> spec = spec(id);
        if (spec.isPresent()) {
            try {
                return value(spec.get(), spec.get().parse(raw));
            } catch (IllegalArgumentException e) {
                return Component.literal(raw);
            }
        }
        if ("true".equals(raw) || "false".equals(raw)) {
            return onOff("true".equals(raw));
        }
        return Component.literal(raw);
    }

    public static MutableComponent onOff(boolean on) {
        return Component.translatable(on ? "propwash.server.world_rules.on" : "propwash.server.world_rules.off");
    }

    public static MutableComponent range(RuleSpec spec) {
        if (spec.kind() == RuleSpec.Kind.BOOL) {
            return Component.translatable("propwash.server.world_rules.range.switch");
        }
        if (spec.display() == RuleSpec.Display.LEVELS) {
            return Component.translatable("propwash.server.world_rules.range", value(spec, spec.min()), value(spec, spec.max()));
        }
        return Component.translatable("propwash.server.world_rules.range", plain(spec, spec.min()), plain(spec, spec.max()));
    }

    private static MutableComponent plain(RuleSpec spec, int value) {
        return spec.display() == RuleSpec.Display.PERCENT ? Component.translatable("propwash.server.world_rules.percent", value)
                : Component.literal(Integer.toString(value));
    }
}
