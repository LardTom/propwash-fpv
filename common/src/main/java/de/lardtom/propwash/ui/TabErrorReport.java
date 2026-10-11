package de.lardtom.propwash.ui;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class TabErrorReport {
    public static final int MAX_FRAMES = 24;
    public static final int MAX_CAUSES = 4;

    private TabErrorReport() {
    }

    public static String summary(Throwable error) {
        String text = String.valueOf(error);
        return text.isBlank() ? error.getClass().getName() : text;
    }

    public static String of(String modVersion, String platform, String tabId, Throwable error) {
        StringBuilder out = new StringBuilder();
        out.append("Propwash FPV ").append(modVersion).append(" (").append(platform).append("), tab ").append(tabId).append('\n');
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        int causes = 0;
        while (current != null && seen.add(current) && causes <= MAX_CAUSES) {
            if (causes > 0) {
                out.append("Caused by: ");
            }
            out.append(summary(current)).append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            int shown = Math.min(frames.length, MAX_FRAMES);
            for (int i = 0; i < shown; i++) {
                out.append("\tat ").append(frames[i]).append('\n');
            }
            if (frames.length > shown) {
                out.append("\t... ").append(frames.length - shown).append(" more\n");
            }
            current = current.getCause();
            causes++;
        }
        return out.toString().stripTrailing();
    }
}
