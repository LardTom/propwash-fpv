package de.lardtom.propwash.video.record.external;

import de.lardtom.propwash.video.record.FrameEncoder;
import de.lardtom.propwash.video.record.RecordingFormat;
import de.lardtom.propwash.video.record.RecordingQuality;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HardwareEncoders {
    public static final int WARMUP_FRAMES = 30;
    public static final int MEASURED_FRAMES = 60;
    public static final int PROBE_FRAMES = WARMUP_FRAMES + MEASURED_FRAMES;
    public static final double MIN_SPEED = 0.75;
    public static final long PROBE_STALL_MILLIS = 5_000L;
    public static final int MAX_VERSION = 28;
    private static final long LIST_TIMEOUT_SECONDS = 10L;
    private static final String VERSION_PREFIX = "ffmpeg version ";
    private static final Map<Key, Status> RESULTS = new ConcurrentHashMap<>();
    private static final ExecutorService PROBES = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Propwash ffmpeg probe");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final Object SHELL_LOCK = new Object();
    private static volatile String broken = "";
    private static volatile Optional<Path> shellResult;

    private HardwareEncoders() {
    }

    public static Status status(Settings settings, RecordingFormat format) {
        if (!settings.enabled()) {
            return Status.of(State.OFF, "");
        }
        String reason = broken;
        if (!reason.isEmpty()) {
            return Status.of(State.BROKEN, reason);
        }
        Key key = new Key(settings.ffmpegPath(), settings.extraDirs(), format);
        Status known = RESULTS.get(key);
        if (known != null) {
            return known;
        }
        Status checking = Status.of(State.CHECKING, "");
        if (RESULTS.putIfAbsent(key, checking) == null) {
            PROBES.execute(() -> RESULTS.put(key, probe(settings, format)));
        }
        return RESULTS.getOrDefault(key, checking);
    }

    public static Optional<FrameEncoder.Factory> factory(Settings settings, RecordingFormat format) {
        return factory(status(settings, format));
    }

    public static Optional<FrameEncoder.Factory> factory(Status status) {
        if (status.state() != State.READY || status.ffmpeg() == null || status.codec() == null) {
            return Optional.empty();
        }
        return Optional.of(FfmpegEncoder.factory(status.ffmpeg(), status.codec(), status.variant()));
    }

    public static void markBroken(String reason) {
        broken = reason == null || reason.isBlank() ? "failed" : reason;
    }

    public static void reset() {
        broken = "";
        shellResult = null;
        RESULTS.clear();
    }

    public static void retest(Settings settings, RecordingFormat format) {
        reset();
        Key key = new Key(settings.ffmpegPath(), settings.extraDirs(), format);
        RESULTS.put(key, Status.of(State.CHECKING, ""));
        PROBES.execute(() -> RESULTS.put(key, probe(settings, format, true)));
    }

    public static Status probe(Settings settings, RecordingFormat format) {
        return probe(settings, format, false);
    }

    public static Status probe(Settings settings, RecordingFormat format, boolean all) {
        return probe(settings, format, all, FfmpegCodec.Platform.current(), System.getenv(), HardwareEncoders::shell);
    }

    public static Status probe(Settings settings, RecordingFormat format, boolean all, FfmpegCodec.Platform platform, Map<String, String> env,
                               FfmpegLocator.ShellLookup shell) {
        FfmpegLocator.Search search = FfmpegLocator.search(settings.ffmpegPath(), settings.extraDirs(), platform, env, shell);
        if (search.found().isEmpty()) {
            return new Status(State.NO_FFMPEG, "", null, null, 0, 0.0, "", null, search.custom(), List.of());
        }
        Path ffmpeg = search.found().get().path();
        FfmpegLocator.Source source = search.found().get().source();
        String version;
        Set<String> available;
        try {
            version = version(ffmpeg);
            available = encoders(ffmpeg);
        } catch (IOException | RuntimeException e) {
            return new Status(State.UNUSABLE, shorten(e.getMessage()), ffmpeg, null, 0, 0.0, "", source, search.custom(), List.of());
        }
        List<Trial> trials = new ArrayList<>();
        Trial best = null;
        for (FfmpegCodec codec : FfmpegCodec.candidates(platform)) {
            if (!available.contains(codec.encoder())) {
                continue;
            }
            for (int variant = 0; variant < codec.variants(); variant++) {
                Trial trial;
                try {
                    double fps = testEncode(ffmpeg, codec, variant, format);
                    trial = new Trial(codec, variant, fps, fps >= format.fps() * MIN_SPEED ? "" : "slow");
                } catch (IOException | RuntimeException e) {
                    trial = new Trial(codec, variant, 0.0, shorten(e.getMessage()));
                }
                trials.add(trial);
                if (trial.ok()) {
                    if (best == null) {
                        best = trial;
                    }
                    break;
                }
            }
            if (best != null && !all) {
                break;
            }
        }
        if (best != null) {
            return new Status(State.READY, best.codec().label(), ffmpeg, best.codec(), best.variant(), best.fps(), version, source,
                    search.custom(), trials);
        }
        List<String> tried = new ArrayList<>();
        for (Trial trial : trials) {
            tried.add(trial.describe());
        }
        return new Status(State.NO_HARDWARE, String.join("; ", tried), ffmpeg, null, 0, 0.0, version, source, search.custom(), trials);
    }

    private static Optional<Path> shell(String program, Map<String, String> env) {
        Optional<Path> known = shellResult;
        if (known != null) {
            return known;
        }
        synchronized (SHELL_LOCK) {
            known = shellResult;
            if (known == null) {
                known = LoginShell.find(program, env);
                shellResult = known;
            }
            return known;
        }
    }

    static String version(Path ffmpeg) throws IOException {
        return parseVersion(run(ffmpeg, "-version"));
    }

    static String parseVersion(String text) {
        for (String line : text.split("\\R")) {
            String stripped = line.strip();
            if (stripped.regionMatches(true, 0, VERSION_PREFIX, 0, VERSION_PREFIX.length())) {
                String rest = stripped.substring(VERSION_PREFIX.length()).strip();
                int end = 0;
                while (end < rest.length() && !Character.isWhitespace(rest.charAt(end))) {
                    end++;
                }
                String version = rest.substring(0, end);
                return version.length() > MAX_VERSION ? version.substring(0, MAX_VERSION) + "…" : version;
            }
        }
        return "";
    }

    static Set<String> encoders(Path ffmpeg) throws IOException {
        Set<String> names = parseEncoders(run(ffmpeg, "-hide_banner", "-encoders"));
        if (names.isEmpty()) {
            throw new IOException("no encoder list");
        }
        return names;
    }

    static Set<String> parseEncoders(String text) {
        Set<String> names = new HashSet<>();
        for (String line : text.split("\\R")) {
            String[] parts = line.strip().split("\\s+");
            if (parts.length >= 2 && parts[0].length() == 6 && parts[0].charAt(0) == 'V') {
                names.add(parts[1]);
            }
        }
        return names;
    }

    private static String run(Path ffmpeg, String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        AtomicBoolean timedOut = new AtomicBoolean();
        CompletableFuture.delayedExecutor(LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (process.isAlive()) {
                timedOut.set(true);
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        });
        process.getOutputStream().close();
        String text;
        try (InputStream in = process.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String what = "ffmpeg " + arguments[arguments.length - 1];
        try {
            if (!process.waitFor(LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(what + " timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("interrupted", e);
        }
        if (timedOut.get()) {
            throw new IOException(what + " timed out");
        }
        if (process.exitValue() != 0 && text.isBlank()) {
            throw new IOException(what + ": exit " + process.exitValue());
        }
        return text;
    }

    static double testEncode(Path ffmpeg, FfmpegCodec codec, int variant, RecordingFormat format) throws IOException {
        int bytes = format.frameBytes();
        ByteBuffer[] frames = new ByteBuffer[3];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = ByteBuffer.allocate(bytes);
            pattern(frames[i], format, i);
        }
        List<Boolean> syncs = new ArrayList<>();
        long started;
        try (FfmpegEncoder encoder = FfmpegEncoder.start(ffmpeg, codec, variant, format, RecordingQuality.DEFAULT, PROBE_STALL_MILLIS)) {
            FrameEncoder.SampleSink sink = (sample, sync) -> {
                if (sample.length == 0) {
                    throw new IOException("empty sample");
                }
                syncs.add(sync);
            };
            for (int i = 0; i < WARMUP_FRAMES; i++) {
                encoder.encode(frames[i % frames.length], 0, sink);
            }
            started = System.nanoTime();
            int warm = syncs.size();
            for (int i = WARMUP_FRAMES; i < PROBE_FRAMES; i++) {
                encoder.encode(frames[i % frames.length], 0, sink);
            }
            encoder.finish(sink);
            double seconds = Math.max(1.0E-3, (System.nanoTime() - started) / 1.0E9);
            if (syncs.size() != PROBE_FRAMES) {
                throw new IOException(syncs.size() + " of " + PROBE_FRAMES + " frames");
            }
            if (!syncs.getFirst()) {
                throw new IOException("first frame is no keyframe");
            }
            byte[] sps = encoder.sps();
            if (sps.length < 4 || encoder.pps().length == 0) {
                throw new IOException("no SPS/PPS");
            }
            return (PROBE_FRAMES - warm) / seconds;
        }
    }

    static void pattern(ByteBuffer frame, RecordingFormat format, int index) {
        int width = format.width();
        int height = format.height();
        long seed = 0x9E3779B97F4A7C15L * (index + 1);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                seed ^= seed << 13;
                seed ^= seed >>> 7;
                seed ^= seed << 17;
                int value = 16 + ((x + y + index * 8) & 0x7F) + (int) (seed & 0x1F);
                frame.put(row + x, (byte) Math.min(235, value));
            }
        }
        int chroma = width * height / 4;
        for (int i = 0; i < chroma * 2; i++) {
            frame.put(width * height + i, (byte) (112 + ((i + index * 3) & 0x1F)));
        }
    }

    private static String shorten(String message) {
        if (message == null) {
            return "?";
        }
        return message.length() > 160 ? message.substring(0, 160) + "…" : message;
    }

    public enum State {
        OFF,
        CHECKING,
        NO_FFMPEG,
        UNUSABLE,
        NO_HARDWARE,
        READY,
        BROKEN
    }

    public record Settings(boolean enabled, String ffmpegPath, List<Path> extraDirs) {
        public Settings {
            ffmpegPath = ffmpegPath == null ? "" : ffmpegPath;
            extraDirs = List.copyOf(extraDirs);
        }
    }

    public record Status(State state, String detail, Path ffmpeg, FfmpegCodec codec, int variant, double fps, String version,
                         FfmpegLocator.Source source, FfmpegLocator.CustomPath custom, List<Trial> trials) {
        public Status {
            detail = detail == null ? "" : detail;
            version = version == null ? "" : version;
            custom = custom == null ? FfmpegLocator.CustomPath.NONE : custom;
            trials = List.copyOf(trials);
        }

        public Status(State state, String detail, Path ffmpeg, FfmpegCodec codec, int variant, double fps) {
            this(state, detail, ffmpeg, codec, variant, fps, "", null, FfmpegLocator.CustomPath.NONE, List.of());
        }

        public boolean customMissing() {
            return this.custom.rejected();
        }

        public static final Status NONE = new Status(State.OFF, "", null, null, 0, 0.0);

        static Status of(State state, String detail) {
            return new Status(state, detail, null, null, 0, 0.0);
        }

        public List<Trial> working() {
            List<Trial> out = new ArrayList<>();
            for (Trial trial : this.trials) {
                if (trial.ok()) {
                    out.add(trial);
                }
            }
            return out;
        }
    }

    public record Trial(FfmpegCodec codec, int variant, double fps, String error) {
        public Trial {
            error = error == null ? "" : error;
        }

        public boolean ok() {
            return this.error.isEmpty();
        }

        public String describe() {
            if (this.ok() || this.error.equals("slow")) {
                return this.codec.label() + " " + Math.round(this.fps) + " fps";
            }
            return this.codec.label() + ": " + this.error;
        }
    }

    private record Key(String ffmpegPath, List<Path> extraDirs, RecordingFormat format) {
    }
}
