package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.Constants;

import java.util.Locale;

public final class ModSearch {
    public static final int MIN_LENGTH = 4;

    private ModSearch() {
    }

    public static boolean wantsModItems(String text) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        return query.length() >= MIN_LENGTH && Constants.MOD_ID.startsWith(query);
    }
}
