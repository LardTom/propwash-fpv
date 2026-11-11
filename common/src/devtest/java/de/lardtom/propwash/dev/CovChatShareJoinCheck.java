package de.lardtom.propwash.dev;

public final class CovChatShareJoinCheck {
    private CovChatShareJoinCheck() {
    }

    static synchronized void init() {
        if (JoinTestEnv.covChatShare()) {
            CovShareJoinCheck.install();
        }
    }
}
