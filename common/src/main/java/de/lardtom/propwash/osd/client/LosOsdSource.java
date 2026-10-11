package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.drone.client.PilotSession;

public final class LosOsdSource extends LiveOsdSource {
    @Override
    public boolean active() {
        return PilotSession.INSTANCE.isLineOfSight();
    }
}
