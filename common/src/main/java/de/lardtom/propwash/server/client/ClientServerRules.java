package de.lardtom.propwash.server.client;

import de.lardtom.propwash.server.RangeStatusS2C;
import de.lardtom.propwash.server.ServerRulesS2C;
import org.jspecify.annotations.Nullable;

public final class ClientServerRules {
    public static final ClientServerRules INSTANCE = new ClientServerRules();

    private @Nullable ServerRulesS2C rules;
    private RangeStatusS2C status = RangeStatusS2C.IDLE;
    private int updates;

    private ClientServerRules() {
    }

    public boolean known() {
        return this.rules != null;
    }

    public @Nullable ServerRulesS2C rules() {
        return this.rules;
    }

    public boolean mayPilot() {
        return this.rules == null || this.rules.mayPilot();
    }

    public boolean spectating() {
        return this.rules == null || this.rules.spectating();
    }

    public boolean unlimitedRange() {
        return this.rules != null && this.rules.unlimitedRange();
    }

    public double maxPilotDistance() {
        if (this.status.piloting()) {
            return this.status.maxDistance();
        }
        if (this.rules == null || this.rules.unlimitedRange()) {
            return Double.POSITIVE_INFINITY;
        }
        return this.rules.maxPilotDistance();
    }

    public double maxDroneSpeed() {
        return this.rules != null && this.rules.maxDroneSpeed() > 0.0 ? this.rules.maxDroneSpeed() : Double.POSITIVE_INFINITY;
    }

    public RangeStatusS2C rangeStatus() {
        return this.status;
    }

    public boolean viewAnchored() {
        return this.status.anchored();
    }

    public int updates() {
        return this.updates;
    }

    void accept(ServerRulesS2C rules) {
        this.rules = rules;
        this.updates++;
    }

    void accept(RangeStatusS2C status) {
        this.status = status;
        this.updates++;
    }

    void clear() {
        this.rules = null;
        this.status = RangeStatusS2C.IDLE;
    }
}
