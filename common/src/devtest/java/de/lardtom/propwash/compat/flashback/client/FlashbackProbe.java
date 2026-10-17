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

    public static int[] drawListVertices() throws ReflectiveOperationException {
        ClassLoader loader = FlashbackProbe.class.getClassLoader();
        Class<?> imgui = Class.forName("imgui.moulberry90.ImGui", false, loader);
        Class<?> contextType = Class.forName("imgui.moulberry90.internal.ImGuiContext", false, loader);
        Field own = type("editor.ui.ReplayUI").getDeclaredField("imGuiContext");
        own.setAccessible(true);
        Object flashbackContext = own.get(null);
        if (flashbackContext == null || contextType.getField("ptr").getLong(flashbackContext) == 0L) {
            return new int[0];
        }
        Method current = imgui.getMethod("getCurrentContext");
        Method setCurrent = imgui.getMethod("setCurrentContext", contextType);
        Field ptr = contextType.getField("ptr");
        long previous = ptr.getLong(current.invoke(null));
        setCurrent.invoke(null, flashbackContext);
        try {
            Object data = imgui.getMethod("getDrawData").invoke(null);
            if (data == null) {
                return new int[0];
            }
            int lists = (int) data.getClass().getMethod("getCmdListsCount").invoke(data);
            Method size = data.getClass().getMethod("getCmdListVtxBufferSize", int.class);
            int[] vertices = new int[Math.max(0, lists)];
            for (int i = 0; i < vertices.length; i++) {
                vertices[i] = (int) size.invoke(data, i);
            }
            return vertices;
        } finally {
            Object restore = current.invoke(null);
            ptr.setLong(restore, previous);
            setCurrent.invoke(null, restore);
        }
    }

    public static boolean editorVisible() throws ReflectiveOperationException {
        return (boolean) type("editor.ui.ReplayUI").getMethod("isActive").invoke(null);
    }

    public static float uiScale() throws ReflectiveOperationException {
        return (float) type("editor.ui.ReplayUI").getMethod("getUiScale").invoke(null);
    }

    public static void uiZoom(float zoom) throws ReflectiveOperationException {
        type("editor.ui.ReplayUI").getField("newGlobalScale").setFloat(null, zoom);
    }

    private static Object currentState() throws ReflectiveOperationException {
        Object state = type("state.EditorStateManager").getMethod("getCurrent").invoke(null);
        if (state == null) {
            throw new IllegalStateException("no editor state");
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    public static int addScene(String name, List<List<de.lardtom.propwash.video.record.path.PathKeyframe>> cameraTracks, int fovKeys, int imguiColour,
                               double eyeHeight) throws ReflectiveOperationException {
        Object state = currentState();
        Class<?> keyframeType = type("keyframe.KeyframeType");
        Class<?> interpolation = type("keyframe.interpolation.InterpolationType");
        Object smooth = interpolation.getMethod("valueOf", String.class).invoke(null, "SMOOTH");
        Object hold = interpolation.getMethod("valueOf", String.class).invoke(null, "HOLD");
        Object cameraType = type("keyframe.types.CameraKeyframeType").getField("INSTANCE").get(null);
        Object fovType = type("keyframe.types.FOVKeyframeType").getField("INSTANCE").get(null);
        java.lang.reflect.Constructor<?> camera = type("keyframe.impl.CameraKeyframe").getConstructor(Vector3d.class, float.class, float.class,
                float.class, interpolation);
        java.lang.reflect.Constructor<?> fov = type("keyframe.impl.FOVKeyframe").getConstructor(float.class, interpolation);
        Class<?> trackClass = type("state.KeyframeTrack");
        Object scene = type("state.EditorScene").getConstructor(String.class).newInstance(name);
        List<Object> tracks = (List<Object>) scene.getClass().getField("keyframeTracks").get(scene);
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        for (List<de.lardtom.propwash.video.record.path.PathKeyframe> keys : cameraTracks) {
            Object track = trackClass.getConstructor(keyframeType).newInstance(cameraType);
            trackClass.getField("customColour").setInt(track, imguiColour);
            Map<Integer, Object> map = (Map<Integer, Object>) trackClass.getField("keyframesByTick").get(track);
            for (de.lardtom.propwash.video.record.path.PathKeyframe key : keys) {
                map.put(key.tick(), camera.newInstance(new Vector3d(key.x(), key.y() - eyeHeight, key.z()), key.yaw(), key.pitch(), key.roll(), smooth));
                first = Math.min(first, key.tick());
                last = Math.max(last, key.tick());
            }
            tracks.add(track);
        }
        Object fovTrack = trackClass.getConstructor(keyframeType).newInstance(fovType);
        trackClass.getField("customColour").setInt(fovTrack, imguiColour);
        Map<Integer, Object> fovMap = (Map<Integer, Object>) trackClass.getField("keyframesByTick").get(fovTrack);
        for (int i = 0; i < fovKeys && first <= last; i++) {
            fovMap.put(first + (int) ((long) (last - first) * i / Math.max(1, fovKeys - 1)), fov.newInstance(70.0F, hold));
        }
        tracks.add(fovTrack);
        long stamp = (long) state.getClass().getMethod("acquireWrite").invoke(state);
        try {
            List<Object> scenes = (List<Object>) state.getClass().getMethod("getScenes", long.class).invoke(state, stamp);
            scenes.add(scene);
            return scenes.size() - 1;
        } finally {
            state.getClass().getMethod("release", long.class).invoke(state, stamp);
        }
    }

    @SuppressWarnings("unchecked")
    public static void removeScene(int index) throws ReflectiveOperationException {
        Object state = currentState();
        long stamp = (long) state.getClass().getMethod("acquireWrite").invoke(state);
        try {
            List<Object> scenes = (List<Object>) state.getClass().getMethod("getScenes", long.class).invoke(state, stamp);
            if (index >= 0 && index < scenes.size()) {
                scenes.remove(index);
            }
        } finally {
            state.getClass().getMethod("release", long.class).invoke(state, stamp);
        }
    }

    public static int activeScene() throws ReflectiveOperationException {
        Object state = currentState();
        return (int) state.getClass().getMethod("getSceneIndex").invoke(state);
    }

    public static void activeScene(int index) throws ReflectiveOperationException {
        Object state = currentState();
        long stamp = (long) state.getClass().getMethod("acquireWrite").invoke(state);
        try {
            state.getClass().getMethod("setSceneIndex", int.class, long.class).invoke(state, index, stamp);
        } finally {
            state.getClass().getMethod("release", long.class).invoke(state, stamp);
        }
    }

    public static Vector3d splinePosition(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, float time1, float time2, float time3, float amount)
            throws ReflectiveOperationException {
        return (Vector3d) type("spline.CatmullRom").getMethod("position", Vector3d.class, Vector3d.class, Vector3d.class, Vector3d.class, float.class,
                float.class, float.class, float.class).invoke(null, p0, p1, p2, p3, time1, time2, time3, amount);
    }

    public static float splineDegrees(float p0, float p1, float p2, float p3, float time1, float time2, float time3, float amount)
            throws ReflectiveOperationException {
        return (float) type("spline.CatmullRom").getMethod("degrees", float.class, float.class, float.class, float.class, float.class, float.class,
                float.class, float.class).invoke(null, p0, p1, p2, p3, time1, time2, time3, amount);
    }

    public static int writtenTicks() {
        Object recorder = FlashbackBridge.recorder();
        return recorder == null ? -1 : FlashbackBridge.writtenTicks(recorder);
    }
}
