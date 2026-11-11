package de.lardtom.propwash.server.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record RuleBase(PhysicsRules server, PhysicsRules personal, RuleTiers tiers) {
    public static final RuleBase DEFAULT = of(PhysicsRules.DEFAULT);

    public RuleBase {
        Objects.requireNonNull(server, "server");
        personal = personal == null ? server : personal;
        tiers = tiers == null ? RuleTiers.DEFAULTS : tiers;
        if (!personal.equals(server)) {
            Map<String, Integer> out = new HashMap<>();
            for (RuleSpec spec : PhysicsRules.SPECS) {
                out.put(spec.name(), tiers.players(spec) ? personal.value(spec) : server.value(spec));
            }
            personal = PhysicsRules.of(new RuleValues(out));
        }
    }

    public static RuleBase of(PhysicsRules server) {
        return new RuleBase(server, server, RuleTiers.DEFAULTS);
    }

    public static RuleBase of(PhysicsRules server, RuleTiers tiers, PersonalRules own) {
        return new RuleBase(server, own.apply(server, tiers), tiers);
    }

    public boolean zones(RuleSpec spec) {
        return this.tiers.zones(spec);
    }

    public int serverValue(RuleSpec spec) {
        return this.server.value(spec);
    }

    public int baseValue(RuleSpec spec) {
        return this.personal.value(spec);
    }

    public boolean personalized() {
        return !this.personal.equals(this.server);
    }
}
