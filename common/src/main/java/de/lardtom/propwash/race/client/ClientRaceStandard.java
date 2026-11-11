package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.RaceStandards;

public final class ClientRaceStandard {
    public static final ClientRaceStandard INSTANCE = new ClientRaceStandard();

    private RaceStandard standard = RaceStandard.DEFAULTS;
    private boolean known;
    private int version;

    private ClientRaceStandard() {
    }

    public RaceStandard standard() {
        return this.standard;
    }

    public boolean known() {
        return this.known;
    }

    public int version() {
        return this.version;
    }

    public void accept(RaceStandards.SyncS2C payload) {
        this.standard = payload.standard();
        this.known = true;
        this.version++;
    }

    public void clear() {
        this.standard = RaceStandard.DEFAULTS;
        this.known = false;
        this.version++;
    }
}
