package de.lardtom.propwash.guard;

import java.util.Set;

public final class KeyClassifier {
    public static final String NAMESPACE = "propwash";
    private static final int SEGMENTS = 3;

    public enum Result {
        OTHER,
        OVERRIDE,
        FINDING
    }

    private KeyClassifier() {
    }

    public static boolean reserved(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String[] segments = key.split("\\.", SEGMENTS + 1);
        for (int i = 0; i < Math.min(SEGMENTS, segments.length); i++) {
            if (NAMESPACE.equals(segments[i])) {
                return true;
            }
        }
        return false;
    }

    public static Result classify(String key, Set<String> own) {
        if (!reserved(key)) {
            return Result.OTHER;
        }
        return own.contains(key) ? Result.OVERRIDE : Result.FINDING;
    }
}
