package de.lardtom.propwash.race;

public final class StandingStart {
    private StandingStart() {
    }

    public static boolean request(boolean armEdge, boolean countdownSetting, boolean lobbyOpenAtStart, boolean running, boolean lobbyMember,
                                  boolean nearStart) {
        return armEdge && (countdownSetting || lobbyOpenAtStart) && !running && !lobbyMember && nearStart;
    }
}
