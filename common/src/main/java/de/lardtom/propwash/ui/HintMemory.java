package de.lardtom.propwash.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HintMemory {
    public static final int MAX_PLAYERS = 32;
    public static final int MAX_HINTS = 64;
    public static final int MAX_COUNT = 1_000_000;
    public static final int MAX_KEY_LENGTH = 64;
    public static final long PULSE_MILLIS = 1400L;

    public static final class Seen {
        public Map<String, Map<String, Integer>> players = new LinkedHashMap<>();
    }

    private HintMemory() {
    }

    public static void sanitize(Seen seen) {
        Map<String, Map<String, Integer>> clean = new LinkedHashMap<>();
        if (seen.players != null) {
            for (Map.Entry<String, Map<String, Integer>> player : seen.players.entrySet()) {
                if (!validKey(player.getKey()) || player.getValue() == null) {
                    continue;
                }
                Map<String, Integer> hints = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> hint : player.getValue().entrySet()) {
                    if (validKey(hint.getKey()) && hint.getValue() != null && hint.getValue() > 0 && hints.size() < MAX_HINTS) {
                        hints.put(hint.getKey(), Math.min(hint.getValue(), MAX_COUNT));
                    }
                }
                clean.put(player.getKey(), hints);
            }
        }
        List<String> order = new ArrayList<>(clean.keySet());
        for (int i = 0; i < order.size() - MAX_PLAYERS; i++) {
            clean.remove(order.get(i));
        }
        seen.players = clean;
    }

    private static boolean validKey(String key) {
        return key != null && !key.isEmpty() && key.length() <= MAX_KEY_LENGTH;
    }

    public static int count(Seen seen, String player, String hint) {
        Map<String, Integer> hints = seen.players.get(player);
        Integer count = hints == null ? null : hints.get(hint);
        return count == null ? 0 : Math.max(0, count);
    }

    public static boolean fresh(Seen seen, String player, String hint, int times) {
        return count(seen, player, hint) < times;
    }

    public static boolean shown(Seen seen, String player, String hint, int times) {
        int count = count(seen, player, hint);
        if (count >= times) {
            return false;
        }
        return put(seen, player, hint, count + 1);
    }

    public static boolean learned(Seen seen, String player, String hint, int times) {
        if (count(seen, player, hint) >= times) {
            return false;
        }
        return put(seen, player, hint, times);
    }

    public static boolean forget(Seen seen, String player, String hint) {
        Map<String, Integer> hints = seen.players.get(player);
        return hints != null && hints.remove(hint) != null;
    }

    private static boolean put(Seen seen, String player, String hint, int count) {
        if (!validKey(player) || !validKey(hint) || count <= 0) {
            return false;
        }
        Map<String, Integer> hints = seen.players.remove(player);
        if (hints == null) {
            hints = new LinkedHashMap<>();
        }
        hints.put(hint, Math.min(count, MAX_COUNT));
        seen.players.put(player, hints);
        sanitize(seen);
        return true;
    }

    public static float pulse(long millis) {
        double phase = Math.floorMod(millis, PULSE_MILLIS) / (double) PULSE_MILLIS;
        return (float) (0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI));
    }
}
