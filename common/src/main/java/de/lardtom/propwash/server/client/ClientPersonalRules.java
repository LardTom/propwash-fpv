package de.lardtom.propwash.server.client;

import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.RaceRules;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.server.PersonalRulesC2S;
import de.lardtom.propwash.server.PersonalRulesS2C;
import de.lardtom.propwash.server.RuleTierC2S;
import de.lardtom.propwash.server.rules.PersonalRuleEdit;
import de.lardtom.propwash.server.rules.PersonalRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleBase;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.RuleTiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ClientPersonalRules {
    public static final ClientPersonalRules INSTANCE = new ClientPersonalRules();

    private RuleTiers tiers = RuleTiers.DEFAULTS;
    private PersonalRules own = PersonalRules.NONE;
    private boolean editable;
    private boolean known;
    private int version;
    private int baseWorld = Integer.MIN_VALUE;
    private int baseOwn = Integer.MIN_VALUE;
    private RuleBase base = RuleBase.DEFAULT;
    private int standardWorld = Integer.MIN_VALUE;
    private RaceStandard standard = RaceStandard.DEFAULTS;

    private ClientPersonalRules() {
    }

    public boolean known() {
        return this.known;
    }

    public boolean editable() {
        return this.known && this.editable;
    }

    public int version() {
        return this.version;
    }

    public RuleTiers tiers() {
        return this.tiers;
    }

    public RuleTier tier(RuleSpec spec) {
        return this.tiers.tier(spec);
    }

    public PersonalRules own() {
        return this.own;
    }

    public synchronized RuleBase base() {
        int world = ClientWorldRules.INSTANCE.version();
        if (world != this.baseWorld || this.version != this.baseOwn) {
            this.base = RuleBase.of(ClientWorldRules.INSTANCE.physics(), this.tiers, this.own);
            this.baseWorld = world;
            this.baseOwn = this.version;
        }
        return this.base;
    }

    public synchronized RaceStandard standard() {
        int world = ClientWorldRules.INSTANCE.version();
        if (world != this.standardWorld) {
            this.standard = RaceStandard.adopt(RaceRules.values(ClientWorldRules.INSTANCE.values(), ClientWorldRules.INSTANCE.raw()));
            this.standardWorld = world;
        }
        return this.standard;
    }

    public List<RuleSpec> deviations() {
        return this.own.deviations(ClientWorldRules.INSTANCE.physics(), this.tiers);
    }

    public List<RuleSpec> assisting() {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec spec : this.deviations()) {
            if (spec.raceRelevant()) {
                out.add(spec);
            }
        }
        return out;
    }

    public List<RuleSpec> locked() {
        return this.own.locked(this.tiers);
    }

    public int serverValue(RuleSpec spec) {
        return ClientWorldRules.INSTANCE.physics().value(spec);
    }

    public int effective(RuleSpec spec) {
        return this.base().personal().value(spec);
    }

    public PhysicsRules physics() {
        return this.base().personal();
    }

    public synchronized void accept(PersonalRulesS2C payload) {
        boolean changed = !payload.tiers().equals(this.tiers) || !payload.own().equals(this.own);
        this.tiers = payload.tiers();
        this.own = payload.own();
        this.editable = payload.editable();
        this.known = true;
        if (changed) {
            this.version++;
        }
    }

    public synchronized void clear() {
        this.tiers = RuleTiers.DEFAULTS;
        this.own = PersonalRules.NONE;
        this.editable = false;
        this.known = false;
        this.version++;
    }

    public boolean setTier(RuleSpec spec, RuleTier tier) {
        return RuleTiers.tierable(spec) && ClientNet.sendToServer(new RuleTierC2S(spec.name(), tier));
    }

    public boolean set(RuleSpec spec, int value) {
        return ClientNet.sendToServer(new PersonalRulesC2S(PersonalRuleEdit.Op.SET, Map.of(spec.name(), value)));
    }

    public boolean reset(RuleSpec spec) {
        return ClientNet.sendToServer(new PersonalRulesC2S(PersonalRuleEdit.Op.RESET, Map.of(spec.name(), 0)));
    }

    public boolean apply(Map<String, Integer> values) {
        return ClientNet.sendToServer(new PersonalRulesC2S(PersonalRuleEdit.Op.APPLY, values));
    }
}
