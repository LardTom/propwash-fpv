package de.lardtom.propwash.rescue;

import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AutonomyBudget {
    public static final int SERVER_LIMIT = 8;
    public static final int OWNER_LIMIT = 2;
    public static final int HOME_SERVER_RESERVE = 2;
    public static final int HOME_OWNER_RESERVE = 1;
    public static final String FULL_OWNER_KEY = "propwash.drone.autonomy.no_budget.player";
    public static final String FULL_SERVER_KEY = "propwash.drone.autonomy.no_budget.server";
    public static final String OFF_KEY = "propwash.drone.autonomy.no_budget.off";
    public static final AutonomyBudget SERVER = new AutonomyBudget();

    public enum Admission {
        ADMITTED,
        SERVER_FULL,
        OWNER_FULL
    }

    public enum Kind {
        HOME,
        ADDON
    }

    private record Slot(UUID owner, Kind kind) {
    }

    private final int baseServerLimit;
    private final int baseOwnerLimit;
    private int serverLimit;
    private int ownerLimit;
    private int addonServerLimit;
    private int addonOwnerLimit;
    private final Map<Object, Slot> flights = new HashMap<>();

    public AutonomyBudget() {
        this(SERVER_LIMIT, OWNER_LIMIT);
    }

    public AutonomyBudget(int serverLimit, int ownerLimit) {
        this.baseServerLimit = Math.max(0, serverLimit);
        this.baseOwnerLimit = Math.max(0, ownerLimit);
        this.serverLimit = this.baseServerLimit;
        this.ownerLimit = this.baseOwnerLimit;
        this.addonServerLimit = Math.max(0, this.serverLimit - HOME_SERVER_RESERVE);
        this.addonOwnerLimit = Math.max(0, this.ownerLimit - HOME_OWNER_RESERVE);
    }

    public void addonLimits(int owner, int server) {
        this.addonOwnerLimit = Math.max(0, owner);
        this.addonServerLimit = Math.max(0, server);
        this.ownerLimit = Math.max(this.baseOwnerLimit, this.addonOwnerLimit + HOME_OWNER_RESERVE);
        this.serverLimit = Math.max(this.baseServerLimit, this.addonServerLimit + HOME_SERVER_RESERVE);
    }

    public int addonServerLimit() {
        return this.addonServerLimit;
    }

    public int addonOwnerLimit() {
        return this.addonOwnerLimit;
    }

    public int serverLimit() {
        return this.serverLimit;
    }

    public int ownerLimit() {
        return this.ownerLimit;
    }

    public MutableComponent refusal(Component drone, Admission admission) {
        if (this.addonOwnerLimit == 0) {
            return Component.translatable(OFF_KEY, drone, rule(WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER));
        }
        if (this.addonServerLimit == 0) {
            return Component.translatable(OFF_KEY, drone, rule(WorldRuleSpecs.AUTONOMOUS_FLIGHTS_TOTAL));
        }
        if (admission == Admission.SERVER_FULL) {
            return Component.translatable(FULL_SERVER_KEY, drone, this.addonServerLimit, rule(WorldRuleSpecs.AUTONOMOUS_FLIGHTS_TOTAL));
        }
        return Component.translatable(FULL_OWNER_KEY, drone, this.addonOwnerLimit, rule(WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER));
    }

    private static Component rule(RuleSpec spec) {
        return Component.translatable(spec.translationKey());
    }

    public Admission check(Object flight, UUID owner) {
        return this.check(flight, owner, Kind.HOME);
    }

    public Admission check(Object flight, UUID owner, Kind kind) {
        Slot held = this.flights.get(flight);
        if (held != null && held.owner().equals(owner) && held.kind() == kind) {
            return Admission.ADMITTED;
        }
        int total = 0;
        int addons = 0;
        int own = 0;
        int ownAddons = 0;
        for (Map.Entry<Object, Slot> entry : this.flights.entrySet()) {
            if (entry.getKey() == flight || entry.getKey().equals(flight)) {
                continue;
            }
            Slot slot = entry.getValue();
            boolean addon = slot.kind() == Kind.ADDON;
            boolean mine = slot.owner().equals(owner);
            total++;
            if (addon) {
                addons++;
            }
            if (mine) {
                own++;
                if (addon) {
                    ownAddons++;
                }
            }
        }
        if (total >= this.serverLimit || kind == Kind.ADDON && addons >= this.addonServerLimit) {
            return Admission.SERVER_FULL;
        }
        if (own >= this.ownerLimit || kind == Kind.ADDON && ownAddons >= this.addonOwnerLimit) {
            return Admission.OWNER_FULL;
        }
        return Admission.ADMITTED;
    }

    public Admission admit(Object flight, UUID owner) {
        return this.admit(flight, owner, Kind.HOME);
    }

    public Admission admit(Object flight, UUID owner, Kind kind) {
        Admission admission = this.check(flight, owner, kind);
        if (admission == Admission.ADMITTED) {
            this.flights.put(flight, new Slot(owner, kind));
        }
        return admission;
    }

    public void release(Object flight) {
        this.flights.remove(flight);
    }

    public boolean holds(Object flight) {
        return this.flights.containsKey(flight);
    }

    public int size() {
        return this.flights.size();
    }

    public int size(Kind kind) {
        int count = 0;
        for (Slot slot : this.flights.values()) {
            if (slot.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        this.flights.clear();
    }
}
