package de.lardtom.propwash.race.client;

import de.lardtom.propwash.ui.HintMemory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class GateHintMemory {
    public static final String HINT = "gate_menu";
    public static final int NEVER = 1;
    public static final int MAX_PLAYERS = 32;
    public static final int MAX_WORLDS = 256;
    public static final int MAX_KEY_LENGTH = 96;
    public static final String UNKNOWN_WORLD = "unknown";

    public enum Outcome {
        SHOW,
        SEEN_HERE,
        NEVER,
        NO_PLAYER
    }

    public enum Choice {
        CHAPTER,
        OK
    }

    public static final class Worlds {
        public Map<String, List<String>> players = new LinkedHashMap<>();
    }

    private GateHintMemory() {
    }

    public static void sanitize(Worlds worlds) {
        Map<String, List<String>> clean = new LinkedHashMap<>();
        if (worlds.players != null) {
            for (Map.Entry<String, List<String>> player : worlds.players.entrySet()) {
                if (!validKey(player.getKey()) || player.getValue() == null) {
                    continue;
                }
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                for (String world : player.getValue()) {
                    if (validKey(world)) {
                        seen.remove(world);
                        seen.add(world);
                    }
                }
                List<String> list = new ArrayList<>(seen);
                clean.put(player.getKey(), new ArrayList<>(list.subList(Math.max(0, list.size() - MAX_WORLDS), list.size())));
            }
        }
        List<String> order = new ArrayList<>(clean.keySet());
        for (int i = 0; i < order.size() - MAX_PLAYERS; i++) {
            clean.remove(order.get(i));
        }
        worlds.players = clean;
    }

    public static String worldKey(@Nullable String key) {
        if (key == null || key.isBlank()) {
            return UNKNOWN_WORLD;
        }
        return key.length() <= MAX_KEY_LENGTH ? key : key.substring(0, MAX_KEY_LENGTH);
    }

    public static boolean never(HintMemory.Seen hints, String player) {
        return HintMemory.count(hints, player, HINT) >= NEVER;
    }

    public static boolean seen(Worlds worlds, String player, String world) {
        List<String> list = worlds.players.get(player);
        return list != null && list.contains(worldKey(world));
    }

    public static Outcome check(HintMemory.Seen hints, Worlds worlds, @Nullable String player, @Nullable String world) {
        if (player == null || !validKey(player)) {
            return Outcome.NO_PLAYER;
        }
        if (never(hints, player)) {
            return Outcome.NEVER;
        }
        return seen(worlds, player, worldKey(world)) ? Outcome.SEEN_HERE : Outcome.SHOW;
    }

    public static boolean shown(Worlds worlds, String player, @Nullable String world) {
        String key = worldKey(world);
        if (!validKey(player)) {
            return false;
        }
        List<String> list = worlds.players.remove(player);
        if (list == null) {
            list = new ArrayList<>();
        }
        boolean changed = !list.contains(key) || !list.getLast().equals(key);
        list.remove(key);
        list.add(key);
        worlds.players.put(player, list);
        sanitize(worlds);
        return changed;
    }

    public static boolean neverAgain(Choice choice, boolean checked) {
        return choice == Choice.CHAPTER || checked;
    }

    public static boolean close(HintMemory.Seen hints, String player, Choice choice, boolean checked) {
        return neverAgain(choice, checked) && HintMemory.learned(hints, player, HINT, NEVER);
    }

    public static boolean forgetWorlds(Worlds worlds, String player) {
        return worlds.players.remove(player) != null;
    }

    public static boolean forgetHints(HintMemory.Seen hints, String player) {
        Map<String, Integer> removed = hints.players.remove(player);
        return removed != null && !removed.isEmpty();
    }

    private static boolean validKey(@Nullable String key) {
        return key != null && !key.isEmpty() && key.length() <= MAX_KEY_LENGTH;
    }
}
