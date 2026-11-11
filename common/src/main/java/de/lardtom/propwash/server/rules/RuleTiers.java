package de.lardtom.propwash.server.rules;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

public record RuleTiers(Map<String, RuleTier> tiers) {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_NAME = 64;
    public static final RuleTiers DEFAULTS = new RuleTiers(Map.of());
    public static final Codec<RuleTiers> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(RuleTiers::parse, RuleTiers::names);

    public RuleTiers {
        Map<String, RuleTier> out = new TreeMap<>();
        if (tiers != null) {
            for (Map.Entry<String, RuleTier> entry : tiers.entrySet()) {
                Optional<RuleSpec> spec = entry.getKey() == null ? Optional.empty() : WorldRuleSpecs.byName(entry.getKey());
                if (spec.isPresent() && tierable(spec.get()) && entry.getValue() != null && entry.getValue() != RuleTier.DEFAULT) {
                    out.put(spec.get().name(), entry.getValue());
                }
            }
        }
        tiers = Collections.unmodifiableMap(out);
    }

    public static RuleTiers of(Predicate<RuleSpec> zonesReleased, Collection<String> players) {
        Map<String, RuleTier> out = new LinkedHashMap<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (!zonesReleased.test(spec)) {
                out.put(spec.name(), RuleTier.FIXED);
            } else if (players.contains(spec.name())) {
                out.put(spec.name(), RuleTier.PLAYERS);
            }
        }
        return new RuleTiers(out);
    }

    public List<String> players() {
        List<String> out = new ArrayList<>();
        this.tiers.forEach((name, tier) -> {
            if (tier == RuleTier.PLAYERS) {
                out.add(name);
            }
        });
        return out;
    }

    public static boolean tierable(RuleSpec spec) {
        return PhysicsRules.SPECS.contains(spec);
    }

    public RuleTier tier(RuleSpec spec) {
        if (!tierable(spec)) {
            return RuleTier.FIXED;
        }
        RuleTier tier = this.tiers.get(spec.name());
        return tier == null ? RuleTier.DEFAULT : tier;
    }

    public boolean zones(RuleSpec spec) {
        return this.tier(spec).zones();
    }

    public boolean players(RuleSpec spec) {
        return this.tier(spec).players();
    }

    public RuleTiers with(RuleSpec spec, RuleTier tier) {
        if (!tierable(spec)) {
            return this;
        }
        Map<String, RuleTier> copy = new LinkedHashMap<>(this.tiers);
        copy.put(spec.name(), tier);
        return new RuleTiers(copy);
    }

    public boolean isDefault() {
        return this.tiers.isEmpty();
    }

    private static RuleTiers parse(Map<String, String> names) {
        Map<String, RuleTier> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            RuleTier.byKey(entry.getValue()).ifPresent(tier -> out.put(entry.getKey(), tier));
        }
        return new RuleTiers(out);
    }

    private Map<String, String> names() {
        Map<String, String> out = new LinkedHashMap<>();
        this.tiers.forEach((name, tier) -> out.put(name, tier.key()));
        return out;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.tiers.size());
        for (Map.Entry<String, RuleTier> entry : this.tiers.entrySet()) {
            buf.writeUtf(entry.getKey(), MAX_NAME);
            buf.writeEnum(entry.getValue());
        }
    }

    public static RuleTiers read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Freigabestufen mit " + count + " Regeln");
        }
        Map<String, RuleTier> out = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            out.put(buf.readUtf(MAX_NAME), buf.readEnum(RuleTier.class));
        }
        return new RuleTiers(out);
    }
}
