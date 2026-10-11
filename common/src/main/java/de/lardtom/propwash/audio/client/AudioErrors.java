package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.Constants;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioErrors {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger COUNT = new AtomicInteger();

    private AudioErrors() {
    }

    public static void report(String where, Throwable error) {
        COUNT.incrementAndGet();
        String key = where + ":" + error.getClass().getName();
        if (REPORTED.add(key)) {
            Constants.LOG.warn("Propwash-Audio: Fehler in {} (weitere gleichartige werden unterdrückt)", where, error);
        }
    }

    public static void note(String key, String message) {
        if (REPORTED.add("note:" + key)) {
            Constants.LOG.info("Propwash-Audio: {}", message);
        }
    }

    public static int count() {
        return COUNT.get();
    }
}
