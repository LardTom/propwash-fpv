package de.lardtom.propwash.race;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public record GhostChoice(Mode mode, @Nullable GhostKey key, String holder) {
    public static final GhostChoice OWN = new GhostChoice(Mode.OWN, null, "");
    public static final GhostChoice RECORD = new GhostChoice(Mode.BOARD, null, "");
    public static final GhostChoice NONE = new GhostChoice(Mode.OFF, null, "");
    public static final int MAX_COURSES = 512;

    public enum Mode {
        OWN,
        BOARD,
        OFF;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public static Mode byKey(@Nullable String key, Mode fallback) {
            if (key != null) {
                for (Mode mode : values()) {
                    if (mode.key().equalsIgnoreCase(key.strip())) {
                        return mode;
                    }
                }
            }
            return fallback;
        }

        public Mode next() {
            Mode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public GhostChoice {
        mode = mode == null ? Mode.OWN : mode;
        key = mode == Mode.BOARD ? key : null;
        holder = holder == null || key == null ? "" : holder.length() > Leaderboard.MAX_NAME ? holder.substring(0, Leaderboard.MAX_NAME) : holder;
    }

    public static GhostChoice of(Mode mode) {
        return switch (mode == null ? Mode.OWN : mode) {
            case OWN -> OWN;
            case BOARD -> RECORD;
            case OFF -> NONE;
        };
    }

    public static GhostChoice entry(GhostKey key, String holder) {
        return new GhostChoice(Mode.BOARD, key, holder);
    }

    public boolean record() {
        return this.mode == Mode.BOARD && this.key == null;
    }

    public boolean chosen() {
        return this.mode == Mode.BOARD && this.key != null;
    }

    public static Mode migrate(@Nullable Boolean ghost, @Nullable Boolean recordGhost) {
        boolean personal = ghost == null || ghost;
        boolean record = recordGhost == null || recordGhost;
        if (personal) {
            return Mode.OWN;
        }
        return record ? Mode.BOARD : Mode.OFF;
    }

    public static void migrateConfig(JsonObject json) {
        if (json.has("ghostMode") || !json.has("ghost") && !json.has("recordGhost")) {
            json.remove("ghost");
            json.remove("recordGhost");
            return;
        }
        Mode mode = migrate(bool(json.get("ghost")), bool(json.get("recordGhost")));
        json.remove("ghost");
        json.remove("recordGhost");
        json.addProperty("ghostMode", mode.name());
    }

    private static @Nullable Boolean bool(@Nullable JsonElement element) {
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }

    public JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("mode", this.mode.key());
        GhostKey chosen = this.key;
        if (chosen != null) {
            out.addProperty("player", chosen.player().toString());
            out.addProperty("frame", chosen.frame());
            out.addProperty("nanos", chosen.nanos());
            out.addProperty("date", chosen.dateMillis());
            out.addProperty("holder", this.holder);
        }
        return out;
    }

    public static @Nullable GhostChoice fromJson(@Nullable JsonElement element) {
        if (!(element instanceof JsonObject json) || !json.has("mode")) {
            return null;
        }
        try {
            Mode mode = Mode.byKey(json.get("mode").getAsString(), Mode.OWN);
            if (mode != Mode.BOARD || !json.has("player")) {
                return of(mode);
            }
            UUID player = UUID.fromString(json.get("player").getAsString());
            String frame = json.has("frame") ? json.get("frame").getAsString() : "";
            long nanos = json.get("nanos").getAsLong();
            long date = json.has("date") ? json.get("date").getAsLong() : 0L;
            String holder = json.has("holder") ? json.get("holder").getAsString() : "";
            if (nanos <= 0L) {
                return RECORD;
            }
            return entry(new GhostKey(player, frame, nanos, date), holder);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static JsonObject toJson(Map<Integer, GhostChoice> choices) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject courses = new JsonObject();
        for (Map.Entry<Integer, GhostChoice> entry : new TreeMap<>(choices).entrySet()) {
            courses.add(Integer.toString(entry.getKey()), entry.getValue().toJson());
        }
        root.add("courses", courses);
        return root;
    }

    public static Map<Integer, GhostChoice> fromJson(@Nullable JsonObject root) {
        Map<Integer, GhostChoice> out = new TreeMap<>();
        if (root == null || !(root.get("courses") instanceof JsonObject courses)) {
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : courses.entrySet()) {
            if (out.size() >= MAX_COURSES) {
                break;
            }
            try {
                int course = Integer.parseInt(entry.getKey());
                GhostChoice choice = fromJson(entry.getValue());
                if (course > 0 && choice != null) {
                    out.put(course, choice);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
