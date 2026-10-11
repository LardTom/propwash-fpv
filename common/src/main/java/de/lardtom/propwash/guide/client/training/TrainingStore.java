package de.lardtom.propwash.guide.client.training;

import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.guide.training.TrainingProgress;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TrainingStore {
    public static final String NAME = "flight_training";
    public static final JsonConfig<TrainingFile> CONFIG = JsonConfig.create(NAME, TrainingFile.class, TrainingFile::new, TrainingStore::sanitize);

    public static final class TrainingFile {
        public int version = 1;
        public Map<String, TrainingProgress> players = new LinkedHashMap<>();
    }

    private TrainingStore() {
    }

    private static void sanitize(TrainingFile file) {
        if (file.players == null) {
            file.players = new LinkedHashMap<>();
        }
        file.players.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (TrainingProgress progress : file.players.values()) {
            progress.sanitize();
        }
        file.version = 1;
    }

    public static String playerKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            return minecraft.player.getUUID().toString();
        }
        UUID profile = minecraft.getUser().getProfileId();
        return profile == null ? "local" : profile.toString();
    }

    public static TrainingProgress progress() {
        if (!CONFIG.isLoaded()) {
            CONFIG.load();
        }
        TrainingFile file = CONFIG.get();
        return file.players.computeIfAbsent(playerKey(), key -> new TrainingProgress());
    }

    public static void save() {
        if (CONFIG.isLoaded()) {
            CONFIG.save();
        }
    }

    public static void forget() {
        if (!CONFIG.isLoaded()) {
            CONFIG.load();
        }
        CONFIG.get().players.remove(playerKey());
        CONFIG.save();
    }
}
