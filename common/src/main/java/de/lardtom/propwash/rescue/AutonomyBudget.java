package de.lardtom.propwash.rescue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AutonomyBudget {
    public static final int SERVER_LIMIT = 8;
    public static final int OWNER_LIMIT = 2;

    public enum Admission {
        ADMITTED,
        SERVER_FULL,
        OWNER_FULL
    }

    private final int serverLimit;
    private final int ownerLimit;
    private final Map<Object, UUID> flights = new HashMap<>();

    public AutonomyBudget() {
        this(SERVER_LIMIT, OWNER_LIMIT);
    }

    public AutonomyBudget(int serverLimit, int ownerLimit) {
        this.serverLimit = Math.max(0, serverLimit);
        this.ownerLimit = Math.max(0, ownerLimit);
    }

    public Admission check(Object flight, UUID owner) {
        if (this.flights.containsKey(flight)) {
            return Admission.ADMITTED;
        }
        if (this.flights.size() >= this.serverLimit) {
            return Admission.SERVER_FULL;
        }
        int own = 0;
        for (UUID other : this.flights.values()) {
            if (other.equals(owner)) {
                own++;
            }
        }
        return own >= this.ownerLimit ? Admission.OWNER_FULL : Admission.ADMITTED;
    }

    public Admission admit(Object flight, UUID owner) {
        Admission admission = this.check(flight, owner);
        if (admission == Admission.ADMITTED) {
            this.flights.put(flight, owner);
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

    public void clear() {
        this.flights.clear();
    }
}
