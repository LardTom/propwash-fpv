package de.lardtom.propwash.compat.flashback.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.video.record.path.CameraTrack;
import de.lardtom.propwash.video.record.path.FovKeyframe;
import de.lardtom.propwash.video.record.path.PathKeyframe;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FlashbackBridge {
    public static final String MOD_ID = "flashback";
    private static final String ROOT = "com.moulberry.flashback.";

    private static @Nullable Status status;
    private static @Nullable String problem;
    private static @Nullable Handles handles;
    private static volatile boolean replaySeen;

    private FlashbackBridge() {
    }

    public static Status status() {
        Status current = status;
        if (current == null) {
            current = resolve();
            status = current;
        }
        return current;
    }

    public static boolean installed() {
        return status() != Status.ABSENT;
    }

    public static boolean ready() {
        return status() == Status.READY;
    }

    public static @Nullable String problem() {
        status();
        return problem;
    }

    public static Optional<String> version() {
        return installed() ? Services.PLATFORM.modVersion(MOD_ID) : Optional.empty();
    }

    private static Status resolve() {
        if (!Services.PLATFORM.isModLoaded(MOD_ID)) {
            problem = null;
            return Status.ABSENT;
        }
        try {
            handles = Handles.load();
            problem = null;
            Constants.LOG.info("Flashback {} erkannt: Action-Cam (Flashback) aktiv", Services.PLATFORM.modVersion(MOD_ID).orElse("?"));
            return Status.READY;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            problem = e.getClass().getSimpleName() + ": " + e.getMessage();
            Constants.LOG.warn("Action-Cam (Flashback): diese Flashback-Version wird nicht unterstützt ({})", problem);
            return Status.INCOMPATIBLE;
        }
    }

    private static Handles handles() {
        Handles h = handles;
        if (h == null || status() != Status.READY) {
            throw new IllegalStateException("Flashback not ready");
        }
        return h;
    }

    private static void broken(Throwable e) {
        problem = e.getClass().getSimpleName() + ": " + e.getMessage();
        status = Status.INCOMPATIBLE;
        Constants.LOG.warn("Action-Cam (Flashback): Zugriff auf Flashback fehlgeschlagen ({})", problem);
    }

    public static @Nullable Object recorder() {
        if (!ready()) {
            return null;
        }
        try {
            return handles().recorder.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return null;
        }
    }

    public static boolean recording() {
        return recorder() != null;
    }

    public static boolean paused() {
        Object recorder = recorder();
        if (recorder == null) {
            return false;
        }
        try {
            return (boolean) handles().isPaused.invoke(recorder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return false;
        }
    }

    public static boolean writing() {
        Object recorder = recorder();
        if (recorder == null) {
            return false;
        }
        try {
            return (boolean) handles().readyToWrite.invoke(recorder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return false;
        }
    }

    public static int writtenTicks(Object recorder) {
        try {
            return handles().writtenTicks.getInt(recorder);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return -1;
        }
    }

    public static @Nullable UUID replayId(Object recorder) {
        try {
            Object meta = handles().metadata.get(recorder);
            return meta == null ? null : (UUID) handles().replayIdentifier.get(meta);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return null;
        }
    }

    public static boolean inReplay() {
        if (!ready()) {
            return false;
        }
        try {
            boolean replay = (boolean) handles().isInReplay.invoke(null);
            if (replay) {
                replaySeen = true;
            }
            return replay;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return false;
        }
    }

    public static float editorZoom() {
        if (!replaySeen || !ready()) {
            return 1.0F;
        }
        try {
            float zoom = type("editor.ui.ReplayUI").getField("newGlobalScale").getFloat(null);
            return Float.isFinite(zoom) && zoom > 0.0F ? zoom : 1.0F;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 1.0F;
        }
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(ROOT + name, false, FlashbackBridge.class.getClassLoader());
    }

    public static boolean startRecording() {
        if (!ready()) {
            return false;
        }
        try {
            handles().startRecording.invoke(null);
            return recording();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return false;
        }
    }

    public static boolean finishRecording() {
        if (!recording()) {
            return false;
        }
        try {
            handles().finishRecording.invoke(null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            return false;
        }
    }

    public static boolean addMarker(int rgb, String description) {
        Object recorder = recorder();
        Handles h = handles;
        if (recorder == null || h == null || h.markerCtor == null || h.addMarker == null) {
            return false;
        }
        try {
            h.addMarker.invoke(recorder, h.markerCtor.newInstance(rgb & 0xFFFFFF, null, description));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Constants.LOG.debug("Action-Cam (Flashback): Marker nicht gesetzt ({})", e.toString());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static int writeScene(UUID replay, FlashbackScene scene) throws FlashbackException {
        Handles h;
        try {
            h = handles();
        } catch (IllegalStateException e) {
            throw new FlashbackException(e.getMessage(), e);
        }
        try {
            Object state = h.editorGet.invoke(null, replay);
            Object built = h.buildScene(scene);
            long stamp = (long) h.acquireWrite.invoke(state);
            int index;
            try {
                List<Object> scenes = (List<Object>) h.getScenes.invoke(state, stamp);
                boolean replaceDefault = scenes.size() == 1 && ((List<?>) h.sceneTracks.get(scenes.getFirst())).isEmpty();
                if (replaceDefault) {
                    scenes.set(0, built);
                    index = 0;
                    h.setSceneIndex.invoke(state, 0, stamp);
                } else {
                    scenes.add(built);
                    index = scenes.size() - 1;
                }
            } finally {
                h.release.invoke(state, stamp);
            }
            h.markDirty.invoke(state);
            h.editorReset.invoke(null);
            return index;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            throw new FlashbackException(e.toString(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static int replaceCameraKeyframes(UUID replay, int sceneIndex, int trackIndex, int expected, List<PathKeyframe> keys, double eyeHeight)
            throws FlashbackException {
        Handles h;
        try {
            h = handles();
        } catch (IllegalStateException e) {
            throw new FlashbackException(e.getMessage(), e);
        }
        try {
            Object state = h.editorGet.invoke(null, replay);
            long stamp = (long) h.acquireWrite.invoke(state);
            int written;
            try {
                List<Object> scenes = (List<Object>) h.getScenes.invoke(state, stamp);
                if (sceneIndex < 0 || sceneIndex >= scenes.size()) {
                    throw new FlashbackException("scene " + sceneIndex + " missing", null);
                }
                List<Object> tracks = (List<Object>) h.sceneTracks.get(scenes.get(sceneIndex));
                if (trackIndex < 0 || trackIndex >= tracks.size()) {
                    throw new FlashbackException("track " + trackIndex + " missing", null);
                }
                Object track = tracks.get(trackIndex);
                if (h.trackType.get(track) != h.cameraType) {
                    throw new FlashbackException("track " + trackIndex + " is not a camera track", null);
                }
                Map<Integer, Object> map = (Map<Integer, Object>) h.trackKeyframes.get(track);
                if (map.size() != expected) {
                    throw new FlashbackException("track changed: " + map.size() + " keyframes instead of " + expected, null);
                }
                map.clear();
                for (PathKeyframe key : keys) {
                    map.put(key.tick(), h.cameraKeyframe.newInstance(new Vector3d(key.x(), key.y() - eyeHeight, key.z()), key.yaw(), key.pitch(),
                            key.roll(), h.smooth));
                }
                written = map.size();
            } finally {
                h.release.invoke(state, stamp);
            }
            h.markDirty.invoke(state);
            h.editorReset.invoke(null);
            return written;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken(e);
            throw new FlashbackException(e.toString(), e);
        }
    }

    public static Path dataDirectory() {
        Handles h = handles;
        if (h != null && h.dataDirectory != null) {
            try {
                return (Path) h.dataDirectory.invoke(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return Services.PLATFORM.getGameDir().resolve(MOD_ID);
    }

    public enum Status {
        ABSENT,
        READY,
        INCOMPATIBLE
    }

    public static final class FlashbackException extends Exception {
        public FlashbackException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    private static final class Handles {
        Field recorder;
        Method startRecording;
        Method finishRecording;
        Method isInReplay;
        @Nullable Method dataDirectory;
        Method isPaused;
        Method readyToWrite;
        Field writtenTicks;
        Field metadata;
        Field replayIdentifier;
        @Nullable Method addMarker;
        @Nullable Constructor<?> markerCtor;
        Method editorGet;
        Method editorReset;
        Method acquireWrite;
        Method getScenes;
        Method release;
        Method markDirty;
        Method setSceneIndex;
        Constructor<?> sceneCtor;
        Field sceneTracks;
        Field sceneExportStart;
        Field sceneExportEnd;
        Constructor<?> trackCtor;
        Field trackKeyframes;
        Field trackType;
        Field trackName;
        Field trackColour;
        Object cameraType;
        Object fovType;
        Constructor<?> cameraKeyframe;
        Constructor<?> fovKeyframe;
        Object smooth;
        Object hold;

        static Handles load() throws ReflectiveOperationException {
            Handles h = new Handles();
            Class<?> flashback = type("Flashback");
            Class<?> recorderType = type("record.Recorder");
            Class<?> metaType = type("record.FlashbackMeta");
            Class<?> manager = type("state.EditorStateManager");
            Class<?> state = type("state.EditorState");
            Class<?> scene = type("state.EditorScene");
            Class<?> track = type("state.KeyframeTrack");
            Class<?> keyframeType = type("keyframe.KeyframeType");
            Class<?> interpolation = type("keyframe.interpolation.InterpolationType");
            h.recorder = flashback.getField("RECORDER");
            h.startRecording = flashback.getMethod("startRecordingReplay");
            h.finishRecording = flashback.getMethod("finishRecordingReplay");
            h.isInReplay = flashback.getMethod("isInReplay");
            try {
                h.dataDirectory = flashback.getMethod("getDataDirectory");
            } catch (NoSuchMethodException e) {
                h.dataDirectory = null;
            }
            h.isPaused = recorderType.getMethod("isPaused");
            h.readyToWrite = recorderType.getMethod("readyToWrite");
            h.writtenTicks = open(recorderType.getDeclaredField("writtenTicks"));
            h.metadata = open(recorderType.getDeclaredField("metadata"));
            h.replayIdentifier = metaType.getField("replayIdentifier");
            try {
                Class<?> marker = type("record.ReplayMarker");
                Class<?> markerPosition = type("record.ReplayMarker$MarkerPosition");
                h.markerCtor = marker.getConstructor(int.class, markerPosition, String.class);
                h.addMarker = recorderType.getMethod("addMarker", marker);
            } catch (ReflectiveOperationException e) {
                h.markerCtor = null;
                h.addMarker = null;
            }
            h.editorGet = manager.getMethod("get", UUID.class);
            h.editorReset = manager.getMethod("reset");
            h.acquireWrite = state.getMethod("acquireWrite");
            h.getScenes = state.getMethod("getScenes", long.class);
            h.release = state.getMethod("release", long.class);
            h.markDirty = state.getMethod("markDirty");
            h.setSceneIndex = state.getMethod("setSceneIndex", int.class, long.class);
            h.sceneCtor = scene.getConstructor(String.class);
            h.sceneTracks = scene.getField("keyframeTracks");
            h.sceneExportStart = scene.getField("exportStartTicks");
            h.sceneExportEnd = scene.getField("exportEndTicks");
            h.trackCtor = track.getConstructor(keyframeType);
            h.trackKeyframes = track.getField("keyframesByTick");
            h.trackType = track.getField("keyframeType");
            h.trackName = track.getField("customName");
            h.trackColour = track.getField("customColour");
            h.cameraType = type("keyframe.types.CameraKeyframeType").getField("INSTANCE").get(null);
            h.fovType = type("keyframe.types.FOVKeyframeType").getField("INSTANCE").get(null);
            h.cameraKeyframe = type("keyframe.impl.CameraKeyframe").getConstructor(Vector3d.class, float.class, float.class, float.class,
                    interpolation);
            h.fovKeyframe = type("keyframe.impl.FOVKeyframe").getConstructor(float.class, interpolation);
            h.smooth = interpolation.getMethod("valueOf", String.class).invoke(null, "SMOOTH");
            h.hold = interpolation.getMethod("valueOf", String.class).invoke(null, "HOLD");
            if (h.cameraType == null || h.fovType == null) {
                throw new NoSuchFieldException("keyframe types");
            }
            return h;
        }

        private static Class<?> type(String name) throws ClassNotFoundException {
            return Class.forName(ROOT + name, false, FlashbackBridge.class.getClassLoader());
        }

        private static Field open(Field field) {
            field.setAccessible(true);
            return field;
        }

        @SuppressWarnings("unchecked")
        Object buildScene(FlashbackScene scene) throws ReflectiveOperationException {
            CameraTrack track = scene.track();
            Object built = this.sceneCtor.newInstance(scene.name());
            List<Object> tracks = (List<Object>) this.sceneTracks.get(built);
            int colour = imguiColour(scene.colour());
            Object camera = this.trackCtor.newInstance(this.cameraType);
            this.trackName.set(camera, scene.cameraTrackName());
            this.trackColour.setInt(camera, colour);
            Map<Integer, Object> cameraKeys = (Map<Integer, Object>) this.trackKeyframes.get(camera);
            for (PathKeyframe key : track.camera()) {
                cameraKeys.put(key.tick(), this.cameraKeyframe.newInstance(new Vector3d(key.x(), key.y() - scene.eyeHeight(), key.z()),
                        key.yaw(), key.pitch(), key.roll(), this.smooth));
            }
            tracks.add(camera);
            Object fov = this.trackCtor.newInstance(this.fovType);
            this.trackName.set(fov, scene.fovTrackName());
            this.trackColour.setInt(fov, colour);
            Map<Integer, Object> fovKeys = (Map<Integer, Object>) this.trackKeyframes.get(fov);
            for (FovKeyframe key : track.fov()) {
                fovKeys.put(key.tick(), this.fovKeyframe.newInstance(key.fov(), this.hold));
            }
            tracks.add(fov);
            this.sceneExportStart.setInt(built, track.startTick());
            this.sceneExportEnd.setInt(built, track.endTick());
            return built;
        }

        static int imguiColour(int rgb) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return 0xFF000000 | (b << 16) | (g << 8) | r;
        }
    }
}
