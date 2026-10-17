package de.lardtom.propwash.rescue;

public final class HomeSync {
    private int seq;
    private int sessionPush;

    public HomeSync() {
    }

    public void beginSession() {
        this.sessionPush = 0;
    }

    public int push() {
        this.seq = this.seq == Integer.MAX_VALUE ? 1 : this.seq + 1;
        this.sessionPush = this.seq;
        return this.seq;
    }

    public boolean accepts(int ack) {
        return ack >= this.sessionPush;
    }

    public static Start onStart(boolean ownHere, boolean storedHere, boolean storedIsOwn) {
        if (ownHere) {
            return storedIsOwn ? Start.KEEP : Start.PUSH_OWN;
        }
        return storedHere ? Start.ADOPT_STORED : Start.KEEP;
    }

    public enum Start {
        PUSH_OWN,
        ADOPT_STORED,
        KEEP
    }
}
