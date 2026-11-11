package de.lardtom.propwash.webexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Problems {
    public enum Level {
        ERROR,
        WARNING
    }

    public record Entry(Level level, String where, String message) {
    }

    private final Set<Entry> entries = new LinkedHashSet<>();

    public void error(String where, String message) {
        this.entries.add(new Entry(Level.ERROR, where, message));
    }

    public void warning(String where, String message) {
        this.entries.add(new Entry(Level.WARNING, where, message));
    }

    public long errors() {
        return this.entries.stream().filter(e -> e.level() == Level.ERROR).count();
    }

    public long warnings() {
        return this.entries.stream().filter(e -> e.level() == Level.WARNING).count();
    }

    public List<Entry> firstErrors(int count) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : this.entries) {
            if (entry.level() == Level.ERROR && out.size() < count) {
                out.add(entry);
            }
        }
        return out;
    }

    public JsonArray toJson() {
        List<Entry> sorted = new ArrayList<>(this.entries);
        sorted.sort(Comparator.comparing((Entry e) -> e.level().ordinal()).thenComparing(Entry::where).thenComparing(Entry::message));
        JsonArray out = new JsonArray();
        for (Entry entry : sorted) {
            JsonObject object = new JsonObject();
            object.addProperty("level", entry.level() == Level.ERROR ? "error" : "warning");
            object.addProperty("where", entry.where());
            object.addProperty("message", entry.message());
            out.add(object);
        }
        return out;
    }
}
