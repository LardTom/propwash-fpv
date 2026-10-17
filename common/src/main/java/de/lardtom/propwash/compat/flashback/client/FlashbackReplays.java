package de.lardtom.propwash.compat.flashback.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.video.record.path.CameraAngles;
import de.lardtom.propwash.video.record.path.FlashbackBudget;
import de.lardtom.propwash.video.record.path.FlashbackSpline;
import de.lardtom.propwash.video.record.path.PathKeyframe;
import de.lardtom.propwash.video.record.path.TickPose;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FlashbackReplays {
    public static final String STATES = "editor_states";
    public static final String REPLAYS = "replays";
    public static final int MAX_BACKUPS = 20;

    private FlashbackReplays() {
    }

    public record Scene(UUID replay, Path state, String replayName, int sceneIndex, String sceneName, int trackIndex, int cameraKeyframes,
                        int holdKeyframes, boolean propwash, long modified) {
        public double fill(float scale) {
            return FlashbackBudget.fill(this.cameraKeyframes, this.holdKeyframes, scale);
        }

        public boolean fits(float scale) {
            return FlashbackBudget.fits(this.cameraKeyframes, this.holdKeyframes, scale);
        }
    }

    public static List<Scene> scan(Path dataDirectory, int propwashColour) {
        Map<UUID, String> names = names(dataDirectory.resolve(REPLAYS));
        Path states = dataDirectory.resolve(STATES);
        List<Scene> scenes = new ArrayList<>();
        if (!Files.isDirectory(states)) {
            return scenes;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(states)) {
            files = list.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
        } catch (IOException e) {
            return scenes;
        }
        for (Path file : files) {
            UUID replay = uuidOf(file);
            if (replay == null) {
                continue;
            }
            try {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                long modified = Files.getLastModifiedTime(file).toMillis();
                JsonArray list = root.has("scenes") ? root.getAsJsonArray("scenes") : new JsonArray();
                for (int s = 0; s < list.size(); s++) {
                    JsonObject scene = list.get(s).getAsJsonObject();
                    String sceneName = scene.has("name") ? scene.get("name").getAsString() : "#" + (s + 1);
                    JsonArray tracks = scene.has("keyframeTracks") ? scene.getAsJsonArray("keyframeTracks") : new JsonArray();
                    int camera = 0;
                    int hold = 0;
                    int propwashTrack = -1;
                    for (int t = 0; t < tracks.size(); t++) {
                        JsonObject track = tracks.get(t).getAsJsonObject();
                        int count = track.has("keyframesByTick") ? track.getAsJsonObject("keyframesByTick").size() : 0;
                        String type = track.has("keyframeType") ? track.get("keyframeType").getAsString() : "";
                        if ("CAMERA".equals(type) && propwashTrack < 0 && track.has("customColour")
                                && track.get("customColour").getAsInt() == propwashColour) {
                            propwashTrack = t;
                        }
                        if (holdOnly(track)) {
                            hold += count;
                        } else {
                            camera += count;
                        }
                    }
                    scenes.add(new Scene(replay, file, names.getOrDefault(replay, replay.toString().substring(0, 8)), s, sceneName, propwashTrack, camera,
                            hold, propwashTrack >= 0, modified));
                }
            } catch (IOException | RuntimeException e) {
                Constants.LOG.debug("Flashback-Editorzustand {} nicht lesbar: {}", file, e.toString());
            }
        }
        scenes.sort(Comparator.comparingLong(Scene::modified).reversed().thenComparingInt(Scene::sceneIndex));
        return scenes;
    }

    private static boolean holdOnly(JsonObject track) {
        if (!track.has("keyframesByTick")) {
            return false;
        }
        for (Entry<String, JsonElement> entry : track.getAsJsonObject("keyframesByTick").entrySet()) {
            JsonObject key = entry.getValue().getAsJsonObject();
            if (!key.has("interpolation_type") || !"HOLD".equals(key.get("interpolation_type").getAsString())) {
                return false;
            }
        }
        return true;
    }

    public static List<PathKeyframe> cameraKeyframes(Scene scene, double eyeHeight) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(scene.state(), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject track = root.getAsJsonArray("scenes").get(scene.sceneIndex()).getAsJsonObject().getAsJsonArray("keyframeTracks")
                .get(scene.trackIndex()).getAsJsonObject();
        List<PathKeyframe> keys = new ArrayList<>();
        for (Entry<String, JsonElement> entry : track.getAsJsonObject("keyframesByTick").entrySet()) {
            JsonObject key = entry.getValue().getAsJsonObject();
            JsonArray position = key.getAsJsonArray("position");
            keys.add(new PathKeyframe(Integer.parseInt(entry.getKey()), position.get(0).getAsDouble(), position.get(1).getAsDouble() + eyeHeight,
                    position.get(2).getAsDouble(), key.get("yaw").getAsFloat(), key.get("pitch").getAsFloat(), key.get("roll").getAsFloat()));
        }
        keys.sort(Comparator.comparingInt(PathKeyframe::tick));
        return keys;
    }

    public static List<TickPose> playback(List<PathKeyframe> keys, double fov) {
        List<TickPose> poses = new ArrayList<>();
        if (keys.size() < 2) {
            return poses;
        }
        for (int tick = keys.getFirst().tick(); tick <= keys.getLast().tick(); tick++) {
            FlashbackSpline.Pose pose = FlashbackSpline.at(keys, tick);
            poses.add(new TickPose(tick, pose.x(), pose.y(), pose.z(), new CameraAngles(pose.yaw(), pose.pitch(), pose.roll()).rotation(), fov));
        }
        return poses;
    }

    public static Path backup(Path state) throws IOException {
        for (int i = 1; i <= MAX_BACKUPS; i++) {
            Path target = state.resolveSibling(state.getFileName() + (i == 1 ? ".bak" : ".bak" + i));
            if (!Files.exists(target)) {
                Files.copy(state, target, StandardCopyOption.COPY_ATTRIBUTES);
                return target;
            }
        }
        throw new IOException("too many backups next to " + state.getFileName());
    }

    static Map<UUID, String> names(Path folder) {
        Map<UUID, String> names = new HashMap<>();
        if (!Files.isDirectory(folder)) {
            return names;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path zip : files.filter(p -> p.toString().endsWith(".zip")).toList()) {
                String file = zip.getFileName().toString();
                UUID id = zipUuid(zip);
                if (id != null) {
                    names.put(id, file.substring(0, file.length() - 4));
                }
            }
        } catch (IOException e) {
            return names;
        }
        return names;
    }

    static @Nullable UUID zipUuid(Path zip) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            ZipEntry entry = file.getEntry("metadata.json");
            if (entry == null) {
                return null;
            }
            try (InputStream in = file.getInputStream(entry)) {
                JsonObject meta = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                return meta.has("uuid") ? UUID.fromString(meta.get("uuid").getAsString()) : null;
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static @Nullable UUID uuidOf(Path file) {
        String name = file.getFileName().toString();
        try {
            return UUID.fromString(name.substring(0, name.length() - ".json".length()));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
