package de.lardtom.propwash.server.rules;

import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

public record PersonalRules(Map<String, Integer> values) {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_NAME = 64;
    public static final PersonalRules NONE = new PersonalRules(Map.of());
    public static final Codec<PersonalRules> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(PersonalRules::new, PersonalRules::values);

    public PersonalRules {
        Map<String, Integer> out = new TreeMap<>();
        if (values != null) {
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                Optional<RuleSpec> spec = entry.getKey() == null ? Optional.empty() : WorldRuleSpecs.byName(entry.getKey());
                Integer value = entry.getValue();
                if (spec.isPresent() && RuleTiers.tierable(spec.get()) && value != null && spec.get().valid(value)) {
                    out.put(spec.get().name(), value);
                }
            }
        }
        values = Collections.unmodifiableMap(out);
    }

    public OptionalInt value(RuleSpec spec) {
        Integer value = this.values.get(spec.name());
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public boolean has(RuleSpec spec) {
        return this.values.containsKey(spec.name());
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    public PersonalRules with(RuleSpec spec, int value) {
        if (!RuleTiers.tierable(spec) || !spec.valid(value)) {
            return this;
        }
        Map<String, Integer> copy = new LinkedHashMap<>(this.values);
        copy.put(spec.name(), value);
        return new PersonalRules(copy);
    }

    public PersonalRules without(RuleSpec spec) {
        if (!this.has(spec)) {
            return this;
        }
        Map<String, Integer> copy = new LinkedHashMap<>(this.values);
        copy.remove(spec.name());
        return new PersonalRules(copy);
    }

    public boolean active(RuleSpec spec, RuleTiers tiers) {
        return this.has(spec) && tiers.players(spec);
    }

    public PhysicsRules apply(PhysicsRules server, RuleTiers tiers) {
        if (this.values.isEmpty()) {
            return server;
        }
        Map<String, Integer> out = new HashMap<>();
        boolean changed = false;
        for (RuleSpec spec : PhysicsRules.SPECS) {
            int base = server.value(spec);
            OptionalInt own = this.value(spec);
            int value = own.isPresent() && tiers.players(spec) ? own.getAsInt() : base;
            changed |= value != base;
            out.put(spec.name(), value);
        }
        return changed ? PhysicsRules.of(new RuleValues(out)) : server;
    }

    public List<RuleSpec> deviations(PhysicsRules server, RuleTiers tiers) {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            OptionalInt own = this.value(spec);
            if (own.isPresent() && tiers.players(spec) && own.getAsInt() != server.value(spec)) {
                out.add(spec);
            }
        }
        return out;
    }

    public List<RuleSpec> locked(RuleTiers tiers) {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (this.has(spec) && !tiers.players(spec)) {
                out.add(spec);
            }
        }
        return out;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.values.size());
        for (Map.Entry<String, Integer> entry : this.values.entrySet()) {
            buf.writeUtf(entry.getKey(), MAX_NAME);
            buf.writeVarInt(entry.getValue());
        }
    }

    public static PersonalRules read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Persönliche Regeln mit " + count + " Einträgen");
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            out.put(buf.readUtf(MAX_NAME), buf.readVarInt());
        }
        return new PersonalRules(out);
    }
}
