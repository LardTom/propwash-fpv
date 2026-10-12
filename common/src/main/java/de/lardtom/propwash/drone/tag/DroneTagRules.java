package de.lardtom.propwash.drone.tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class DroneTagRules {
    public static final String OWNER_PREFIX = "pw_owner_";
    public static final String PILOT_PREFIX = "pw_pilot_";
    public static final String FLYING = "pw_flying";
    public static final String AUTOPILOT = "pw_autopilot";
    public static final char REPLACEMENT = '_';

    private DroneTagRules() {
    }

    public static String ownerTag(String player) {
        return OWNER_PREFIX + safeName(player);
    }

    public static String pilotTag(String player) {
        return PILOT_PREFIX + safeName(player);
    }

    public static String safeName(String name) {
        if (name == null || name.isEmpty()) {
            return String.valueOf(REPLACEMENT);
        }
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            out.append(selectorSafe(c) ? c : REPLACEMENT);
        }
        return out.toString();
    }

    public static boolean selectorSafe(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
    }

    public static Change replace(Collection<String> tags, String prefix, String tag) {
        if (!tag.startsWith(prefix)) {
            throw new IllegalArgumentException(tag + " does not start with " + prefix);
        }
        List<String> remove = new ArrayList<>();
        for (String existing : tags) {
            if (existing.startsWith(prefix) && !existing.equals(tag)) {
                remove.add(existing);
            }
        }
        remove.sort(null);
        return new Change(remove, tags.contains(tag) ? List.of() : List.of(tag));
    }

    public static Optional<String> player(Collection<String> tags, String prefix) {
        return tags.stream()
                .filter(tag -> tag.startsWith(prefix) && tag.length() > prefix.length())
                .sorted()
                .findFirst()
                .map(tag -> tag.substring(prefix.length()));
    }

    public static boolean flying(boolean piloted, boolean pilotAlive) {
        return piloted && pilotAlive;
    }

    public static boolean autopilot(boolean piloted, boolean serverFlies) {
        return !piloted && serverFlies;
    }

    public static Change flightState(Collection<String> tags, boolean flying, boolean autopilot) {
        List<String> remove = new ArrayList<>(2);
        List<String> add = new ArrayList<>(2);
        mirror(tags, FLYING, flying, remove, add);
        mirror(tags, AUTOPILOT, autopilot, remove, add);
        return new Change(remove, add);
    }

    private static void mirror(Collection<String> tags, String tag, boolean present, List<String> remove, List<String> add) {
        boolean has = tags.contains(tag);
        if (present && !has) {
            add.add(tag);
        } else if (!present && has) {
            remove.add(tag);
        }
    }

    public record Change(List<String> remove, List<String> add) {
        public Change {
            remove = List.copyOf(remove);
            add = List.copyOf(add);
        }

        public boolean isEmpty() {
            return this.remove.isEmpty() && this.add.isEmpty();
        }
    }
}
