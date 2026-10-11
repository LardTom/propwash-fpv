package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.LapTracker;

public final class RaceState {
    public static final RaceState INSTANCE = new RaceState();

    private RaceState() {
    }

    private static LapTracker tracker() {
        return ClientRaceTracker.INSTANCE.tracker();
    }

    public boolean racing() {
        return tracker().racing();
    }

    public int lap() {
        return tracker().lap();
    }

    public long currentLapNanos() {
        return tracker().currentLapNanos(ClientRaceTracker.INSTANCE.now());
    }

    public long lastLapNanos() {
        return tracker().lastLapNanos();
    }

    public long bestLapNanos() {
        return tracker().bestLapNanos();
    }

    public int nextGate() {
        return tracker().nextGate();
    }

    public int gateCount() {
        return tracker().gateCount();
    }

    public long splitDeltaNanos() {
        return tracker().splitDeltaNanos();
    }

    public int lapTarget() {
        return tracker().lapTarget();
    }

    public int courseId() {
        return tracker().courseId();
    }

    public boolean finished() {
        return tracker().phase() == LapTracker.Phase.FINISHED;
    }

    public long raceNanos() {
        return tracker().raceNanos(ClientRaceTracker.INSTANCE.now());
    }

    public long totalNanos() {
        return tracker().totalNanos();
    }

    public long countdownRemainingNanos() {
        return tracker().countdownRemainingNanos(ClientRaceTracker.INSTANCE.now());
    }

    public long sessionBestLapNanos() {
        return tracker().sessionBestNanos();
    }

    public long penaltyNanos() {
        return tracker().penaltyNanos();
    }

    public boolean falseStarted() {
        return tracker().falseStarted();
    }

    public long personalBestNanos() {
        return ClientRaceTracker.INSTANCE.ghosts().personalLapNanos();
    }

    public long recordGhostNanos() {
        return ClientRaceTracker.INSTANCE.ghosts().recordLapNanos();
    }

    public boolean ghostReference() {
        return tracker().hasReference();
    }

    public boolean inLobby() {
        return ClientRaceTracker.INSTANCE.lobbyMember();
    }
}
