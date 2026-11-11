package de.lardtom.propwash.server.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

public final class PersonalRulesDraft {
    public enum Status {
        RACE_LEGAL,
        NEUTRAL,
        ASSISTED
    }

    private PhysicsRules server;
    private RuleTiers tiers;
    private PersonalRules own;
    private final Map<String, Integer> desired = new TreeMap<>();
    private boolean touched;

    public PersonalRulesDraft(PhysicsRules server, RuleTiers tiers, PersonalRules own) {
        this.server = server;
        this.tiers = tiers;
        this.own = own;
        this.discard();
    }

    public void sync(PhysicsRules server, RuleTiers tiers, PersonalRules own) {
        RuleTiers before = this.tiers;
        this.server = server;
        this.tiers = tiers;
        this.own = own;
        if (!this.touched || this.desired().equals(this.synced())) {
            this.discard();
            return;
        }
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (before.players(spec) == tiers.players(spec)) {
                continue;
            }
            OptionalInt value = own.value(spec);
            if (tiers.players(spec) && value.isPresent()) {
                this.desired.put(spec.name(), value.getAsInt());
            } else {
                this.desired.remove(spec.name());
            }
        }
    }

    public void discard() {
        this.desired.clear();
        this.desired.putAll(this.synced());
        this.touched = false;
    }

    private Map<String, Integer> synced() {
        Map<String, Integer> out = new TreeMap<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            OptionalInt value = this.own.value(spec);
            if (value.isPresent() && this.tiers.players(spec) && value.getAsInt() != this.server.value(spec)) {
                out.put(spec.name(), value.getAsInt());
            }
        }
        return out;
    }

    public RuleTiers tiers() {
        return this.tiers;
    }

    public boolean editable(RuleSpec spec) {
        return this.tiers.players(spec);
    }

    public int server(RuleSpec spec) {
        return this.server.value(spec);
    }

    public int value(RuleSpec spec) {
        if (!this.editable(spec)) {
            return this.server(spec);
        }
        Integer value = this.desired.get(spec.name());
        return value == null ? this.server(spec) : value;
    }

    public OptionalInt stored(RuleSpec spec) {
        return this.own.value(spec);
    }

    public boolean deviates(RuleSpec spec) {
        return this.editable(spec) && this.value(spec) != this.server(spec);
    }

    public boolean set(RuleSpec spec, int value) {
        if (!RuleTiers.tierable(spec) || !this.editable(spec) || !spec.valid(value)) {
            return false;
        }
        if (value == this.server(spec)) {
            this.desired.remove(spec.name());
        } else {
            this.desired.put(spec.name(), value);
        }
        this.touched = true;
        return true;
    }

    public boolean reset(RuleSpec spec) {
        if (!RuleTiers.tierable(spec) || !this.editable(spec)) {
            return false;
        }
        this.desired.remove(spec.name());
        this.touched = true;
        return true;
    }

    public PersonalPresets.Applied apply(PersonalPresets.Preset preset) {
        PersonalPresets.Applied applied = PersonalPresets.apply(preset, this.tiers);
        this.desired.clear();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            Integer value = applied.values().get(spec.name());
            if (value != null && value != this.server(spec)) {
                this.desired.put(spec.name(), value);
            }
        }
        this.touched = true;
        return applied;
    }

    public Map<String, Integer> desired() {
        Map<String, Integer> out = new TreeMap<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (this.deviates(spec)) {
                out.put(spec.name(), this.value(spec));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    public Map<String, Integer> capture() {
        return this.desired();
    }

    public boolean changed() {
        return !this.desired().equals(this.synced());
    }

    public List<RuleSpec> deviations() {
        List<RuleSpec> out = new ArrayList<>();
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (this.deviates(spec)) {
                out.add(spec);
            }
        }
        return out;
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

    public Status status() {
        if (!this.assisting().isEmpty()) {
            return Status.ASSISTED;
        }
        return this.deviations().isEmpty() ? Status.RACE_LEGAL : Status.NEUTRAL;
    }
}
