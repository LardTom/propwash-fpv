package de.lardtom.propwash.trick;

public final class TrickConfig {
    public TrickMode mode = TrickMode.AUTO;

    public TrickConfig() {
    }

    public static void sanitize(TrickConfig config) {
        if (config.mode == null) {
            config.mode = TrickMode.AUTO;
        }
    }

    public TrickConfig copy() {
        TrickConfig copy = new TrickConfig();
        copy.mode = this.mode;
        return copy;
    }
}
