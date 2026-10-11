package de.lardtom.propwash.server.client;

import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.server.WorldRuleText;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleEffects;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientWorldRules {
    public static final ClientWorldRules INSTANCE = new ClientWorldRules();

    private Map<Identifier, String> raw = Map.of();
    private RuleValues values = RuleValues.DEFAULTS;
    private PhysicsRules physics = PhysicsRules.DEFAULT;
    private boolean editable;
    private boolean known;
    private int version;
    private int updates;

    private ClientWorldRules() {
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

    public int updates() {
        return this.updates;
    }

    public RuleValues values() {
        return this.values;
    }

    public PhysicsRules physics() {
        return this.physics;
    }

    public Map<Identifier, String> raw() {
        return this.raw;
    }

    public Optional<String> raw(Identifier id) {
        return Optional.ofNullable(this.raw.get(id));
    }

    public int get(RuleSpec spec) {
        return this.values.get(spec);
    }

    public boolean on(RuleSpec spec) {
        return this.values.on(spec);
    }

    public boolean crashDamage() {
        return RuleEffects.crashDamage(this.values);
    }

    public float crashDamageStrength() {
        return RuleEffects.crashDamageStrength(this.values);
    }

    public PartCompatibility partCompatibility() {
        return RuleEffects.partCompatibility(this.values);
    }

    public boolean infiniteRange(VideoSystem transmitter) {
        return RuleEffects.infiniteRange(this.values, transmitter);
    }

    public void accept(WorldRulesS2C payload) {
        Map<Identifier, String> raw = new LinkedHashMap<>();
        Map<String, Integer> parsed = new HashMap<>();
        for (WorldRulesS2C.Entry entry : payload.entries()) {
            raw.put(entry.id(), entry.value());
            Optional<RuleSpec> spec = WorldRuleText.spec(entry.id());
            if (spec.isPresent()) {
                try {
                    parsed.put(spec.get().name(), spec.get().parse(entry.value()));
                } catch (IllegalArgumentException ignored) {
                    parsed.remove(spec.get().name());
                }
            }
        }
        RuleValues values = new RuleValues(parsed);
        boolean changed = !values.equals(this.values) || !raw.equals(this.raw);
        this.raw = Map.copyOf(raw);
        this.values = values;
        this.physics = PhysicsRules.of(values);
        this.editable = payload.editable();
        this.known = true;
        if (changed) {
            this.version++;
        }
        this.updates++;
    }

    public void clear() {
        this.raw = Map.of();
        this.values = RuleValues.DEFAULTS;
        this.physics = PhysicsRules.DEFAULT;
        this.editable = false;
        this.known = false;
        this.version++;
    }
}
