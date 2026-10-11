package de.lardtom.propwash.compat.flashback.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class FlashbackProbe {
    private static final String ROOT = "com.moulberry.flashback.";

    private FlashbackProbe() {
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(ROOT + name, false, FlashbackProbe.class.getClassLoader());
    }

    public static void quicksave(boolean on) throws ReflectiveOperationException {
        Object config = type("Flashback").getMethod("getConfig").invoke(null);
        Object controls = config.getClass().getField("recordingControls").get(config);
        controls.getClass().getField("quicksave").setBoolean(controls, on);
    }

    public static Path editorState(UUID replay) {
        return FlashbackBridge.dataDirectory().resolve("editor_states").resolve(replay + ".json");
    }

    public static Path replayFolder() {
        return FlashbackBridge.dataDirectory().resolve("replays");
    }

    public static @Nullable Path replayWith(UUID replay) throws IOException {
        Path folder = replayFolder();
        if (!Files.isDirectory(folder)) {
            return null;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path zip : files.filter(p -> p.toString().endsWith(".zip")).toList()) {
                if (replay.equals(zipUuid(zip))) {
                    return zip;
                }
            }
        }
        return null;
    }

    public static @Nullable UUID zipUuid(Path zip) {
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

    public static @Nullable Editor load(Path file) throws ReflectiveOperationException {
        Object state = type("state.EditorState").getMethod("load", Path.class).invoke(null, file);
        return state == null ? null : new Editor(state);
    }

    public record Camera(double x, double y, double z, double yaw, double pitch, double roll) {
    }

    public static final class Editor {
        private final Object state;
        private final List<Object> scenes;

        @SuppressWarnings("unchecked")
        private Editor(Object state) throws ReflectiveOperationException {
            this.state = state;
            Class<?> type = state.getClass();
            long stamp = (long) type.getMethod("acquireRead").invoke(state);
            try {
                this.scenes = new ArrayList<>((List<Object>) type.getMethod("getScenes", long.class).invoke(state, stamp));
            } finally {
                type.getMethod("release", long.class).invoke(state, stamp);
            }
        }

        public int scenes() {
            return this.scenes.size();
        }

        public int sceneIndex() throws ReflectiveOperationException {
            return (int) this.state.getClass().getMethod("getSceneIndex").invoke(this.state);
        }

        public String name(int scene) throws ReflectiveOperationException {
            Object s = this.scenes.get(scene);
            return (String) s.getClass().getField("name").get(s);
        }

        public int[] exportRange(int scene) throws ReflectiveOperationException {
            Object s = this.scenes.get(scene);
            return new int[]{s.getClass().getField("exportStartTicks").getInt(s), s.getClass().getField("exportEndTicks").getInt(s)};
        }

        private List<?> tracks(int scene) throws ReflectiveOperationException {
            Object s = this.scenes.get(scene);
            return (List<?>) s.getClass().getField("keyframeTracks").get(s);
        }

        public int trackCount(int scene) throws ReflectiveOperationException {
            return this.tracks(scene).size();
        }

        public String trackName(int scene, int track) throws ReflectiveOperationException {
            Object t = this.tracks(scene).get(track);
            return (String) t.getClass().getField("customName").get(t);
        }

        public String trackType(int scene, int track) throws ReflectiveOperationException {
            Object t = this.tracks(scene).get(track);
            return t.getClass().getField("keyframeType").get(t).getClass().getSimpleName();
        }

        public int keyframes(int scene, int track) throws ReflectiveOperationException {
            Object t = this.tracks(scene).get(track);
            return ((Map<?, ?>) t.getClass().getField("keyframesByTick").get(t)).size();
        }

        public @Nullable Camera camera(int scene, int track, float tick) throws ReflectiveOperationException {
            Object change = this.change(scene, track, tick);
            if (change == null) {
                return null;
            }
            Class<?> type = change.getClass();
            Vector3d position = (Vector3d) type.getMethod("position").invoke(change);
            return new Camera(position.x, position.y, position.z, (double) type.getMethod("yaw").invoke(change),
                    (double) type.getMethod("pitch").invoke(change), (double) type.getMethod("roll").invoke(change));
        }

        public float fov(int scene, int track, float tick) throws ReflectiveOperationException {
            Object change = this.change(scene, track, tick);
            return change == null ? Float.NaN : (float) change.getClass().getMethod("fov").invoke(change);
        }

        private @Nullable Object change(int scene, int track, float tick) throws ReflectiveOperationException {
            Object t = this.tracks(scene).get(track);
            Method create = t.getClass().getMethod("createKeyframeChange", float.class, type("state.RealTimeMapping"));
            return create.invoke(t, tick, null);
        }
    }

    public static void openReplay(Path zip) throws ReflectiveOperationException {
        type("Flashback").getMethod("openReplayWorld", Path.class).invoke(null, zip);
    }

    public static @Nullable Object replayServer() throws ReflectiveOperationException {
        return type("Flashback").getMethod("getReplayServer").invoke(null);
    }

    public static boolean replayReady() throws ReflectiveOperationException {
        Object server = replayServer();
        return server != null && (boolean) server.getClass().getMethod("isReady").invoke(server);
    }

    public static void goToTick(int tick) throws ReflectiveOperationException {
        Object server = replayServer();
        if (server != null) {
            server.getClass().getMethod("goToReplayTick", int.class).invoke(server, tick);
        }
    }

    public static int replayTick() throws ReflectiveOperationException {
        Object server = replayServer();
        return server == null ? -1 : (int) server.getClass().getMethod("getReplayTick").invoke(server);
    }

    public static double partialReplayTick() throws ReflectiveOperationException {
        Object server = replayServer();
        return server == null ? -1.0 : (double) server.getClass().getMethod("getPartialReplayTick").invoke(server);
    }

    public static void playing(boolean playing) throws ReflectiveOperationException {
        Object server = replayServer();
        if (server != null) {
            server.getClass().getField("replayPaused").setBoolean(server, !playing);
        }
    }

    public static @Nullable Object sizing(@Nullable Object value) throws ReflectiveOperationException {
        Object state = type("state.EditorStateManager").getMethod("getCurrent").invoke(null);
        if (state == null) {
            return null;
        }
        Object visuals = state.getClass().getField("replayVisuals").get(state);
        Field field = visuals.getClass().getField("sizing");
        Object previous = field.get(visuals);
        if (value != null) {
            field.set(visuals, value);
        }
        return previous;
    }

    public static boolean overrideFov(boolean on) throws ReflectiveOperationException {
        Object state = type("state.EditorStateManager").getMethod("getCurrent").invoke(null);
        if (state == null) {
            return false;
        }
        Object visuals = state.getClass().getField("replayVisuals").get(state);
        Field field = visuals.getClass().getField("overrideFov");
        boolean previous = field.getBoolean(visuals);
        field.setBoolean(visuals, on);
        return previous;
    }

    public static Object underlay() throws ReflectiveOperationException {
        return type("combo_options.Sizing").getMethod("valueOf", String.class).invoke(null, "UNDERLAY");
    }

    public static int writtenTicks() {
        Object recorder = FlashbackBridge.recorder();
        return recorder == null ? -1 : FlashbackBridge.writtenTicks(recorder);
    }
}
