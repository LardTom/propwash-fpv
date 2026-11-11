package de.lardtom.propwash.race.client;

public final class BoardSections {
    private static boolean cleanCollapsed;
    private static boolean assistedCollapsed;

    private BoardSections() {
    }

    public static boolean collapsed(boolean assisted) {
        return assisted ? assistedCollapsed : cleanCollapsed;
    }

    public static void set(boolean assisted, boolean collapsed) {
        if (assisted) {
            assistedCollapsed = collapsed;
        } else {
            cleanCollapsed = collapsed;
        }
    }

    public static boolean toggle(boolean assisted) {
        set(assisted, !collapsed(assisted));
        return collapsed(assisted);
    }

    public static void reset() {
        cleanCollapsed = false;
        assistedCollapsed = false;
    }
}
