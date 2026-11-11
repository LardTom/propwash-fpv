package de.lardtom.propwash.race.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.race.GhostChoice;
import de.lardtom.propwash.race.GhostKey;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class GhostChoices {
    public static final GhostChoices INSTANCE = new GhostChoices();
    public static final String FILE = "choices.json";
    private static final int MAX_FILE_BYTES = 256 * 1024;

    private final Map<Integer, GhostChoice> choices = new TreeMap<>();
    private @Nullable String world;
    private boolean persist = true;
    private int version;

    private GhostChoices() {
    }

    public void setPersist(boolean persist) {
        this.persist = persist;
    }

    public int version() {
        return this.version;
    }

    public void clear() {
        this.choices.clear();
        this.world = null;
        this.version++;
    }

    public GhostChoice.Mode defaultMode() {
        return RaceConfig.CONFIG.get().ghostMode();
    }

    public GhostChoice get(Minecraft minecraft, int course) {
        this.load(minecraft);
        GhostChoice chosen = this.choices.get(course);
        return chosen != null ? chosen : GhostChoice.of(this.defaultMode());
    }

    public boolean chosen(Minecraft minecraft, int course, GhostKey key) {
        GhostChoice choice = this.get(minecraft, course);
        return choice.chosen() && key.equals(choice.key());
    }

    public void choose(Minecraft minecraft, int course, GhostChoice choice) {
        this.load(minecraft);
        GhostChoice before = this.choices.get(course);
        GhostChoice next = choice.mode() == this.defaultMode() && !choice.chosen() ? null : choice;
        if (Objects.equals(before, next)) {
            return;
        }
        if (next == null) {
            this.choices.remove(course);
        } else {
            this.choices.put(course, next);
        }
        this.version++;
        this.save(minecraft);
    }

    public void forget(Minecraft minecraft, int course) {
        this.load(minecraft);
        if (this.choices.remove(course) != null) {
            this.version++;
            this.save(minecraft);
        }
    }

    private void load(Minecraft minecraft) {
        String key = GhostStore.worldKey(minecraft);
        String wanted = key == null ? "" : key;
        if (wanted.equals(this.world)) {
            return;
        }
        this.world = wanted;
        this.choices.clear();
        this.version++;
        Path file = this.file(minecraft);
        if (file == null || !this.persist || !Files.isRegularFile(file)) {
            return;
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            this.choices.putAll(GhostChoice.fromJson(root));
        } catch (IOException | RuntimeException e) {
            Constants.LOG.warn("Geister-Wahl {} nicht lesbar: {}", file, e.getMessage());
        }
    }

    private @Nullable Path file(Minecraft minecraft) {
        String key = GhostStore.worldKey(minecraft);
        return key == null ? null : GhostStore.root(minecraft).resolve(key).resolve(FILE);
    }

    private void save(Minecraft minecraft) {
        Path file = this.file(minecraft);
        if (file == null || !this.persist) {
            return;
        }
        byte[] data = GhostChoice.toJson(this.choices).toString().getBytes(StandardCharsets.UTF_8);
        Util.ioPool().execute(() -> GhostStore.write(file, data));
    }

    public Map<Integer, GhostChoice> snapshot() {
        return Map.copyOf(this.choices);
    }
}
