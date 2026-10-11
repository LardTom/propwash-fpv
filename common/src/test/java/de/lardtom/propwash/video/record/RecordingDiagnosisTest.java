package de.lardtom.propwash.video.record;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingDiagnosisTest {
    private static final RecordingJob.Backlog EMPTY = new RecordingJob.Backlog(0, 0, false, false, 0.0);

    private static PipelineStats flight(int fps, int seconds, double encodeMs, int gapEvery) {
        PipelineStats stats = new PipelineStats(fps);
        long frameNanos = 1_000_000_000L / fps;
        for (int i = 0; i < fps * seconds; i++) {
            stats.renderFrame(frameNanos);
            boolean gap = gapEvery > 0 && i % gapEvery == 0;
            stats.slot(gap ? 1L : 0L);
            stats.captured(200_000L);
            stats.readback(80_000L, 1L);
            stats.encoded((long) (encodeMs * 1.0E6), 0);
        }
        return stats;
    }

    @Test
    void healthyRecordingHasNoLimit() {
        RecordingDiagnosis d = RecordingDiagnosis.of(flight(60, 2, 10.0, 0).snapshot(), EMPTY, 0, RecordingDiagnosis.Tip.HARDWARE);
        assertEquals(RecordingDiagnosis.Limit.NONE, d.limit());
        assertFalse(d.losing());
        assertEquals(100.0, d.encoderFps(), 1.0E-6);
    }

    @Test
    void growingBacklogBlamesTheEncoderAndEstimatesTheExtraSavingTime() {
        RecordingJob.Backlog backlog = new RecordingJob.Backlog(240, 40, false, false, 0.01);
        RecordingDiagnosis d = RecordingDiagnosis.of(flight(60, 2, 25.0, 0).snapshot(), backlog, 180, RecordingDiagnosis.Tip.HARDWARE);
        assertEquals(RecordingDiagnosis.Limit.ENCODER, d.limit());
        assertEquals(RecordingDiagnosis.Tip.HARDWARE, d.tip());
        assertFalse(d.losing());
        assertEquals(40.0, d.encoderFps(), 1.0E-6);
        assertEquals(6.0, d.saveSeconds(), 1.0E-6);
        RecordingDiagnosis shrinking = RecordingDiagnosis.of(flight(60, 2, 25.0, 0).snapshot(), backlog, 300, RecordingDiagnosis.Tip.HARDWARE);
        assertEquals(RecordingDiagnosis.Limit.NONE, shrinking.limit());
    }

    @Test
    void slowGameIsReportedWithAFrameRateTip() {
        RecordingDiagnosis d = RecordingDiagnosis.of(flight(60, 2, 5.0, 4).snapshot(), EMPTY, 0, RecordingDiagnosis.Tip.LOWER);
        assertEquals(RecordingDiagnosis.Limit.GAME, d.limit());
        assertEquals(RecordingDiagnosis.Tip.FRAME_RATE, d.tip());
        RecordingDiagnosis thirty = RecordingDiagnosis.of(flight(30, 2, 5.0, 4).snapshot(), EMPTY, 0, RecordingDiagnosis.Tip.LOWER);
        assertEquals(RecordingDiagnosis.Tip.GRAPHICS, thirty.tip());
    }

    @Test
    void lostFramesNameTheStageThatLostThem() {
        PipelineStats readback = flight(60, 1, 5.0, 0);
        readback.ringFull();
        RecordingDiagnosis gpu = RecordingDiagnosis.of(readback.snapshot(), EMPTY, 0, RecordingDiagnosis.Tip.LOWER);
        assertEquals(RecordingDiagnosis.Limit.READBACK, gpu.limit());
        assertTrue(gpu.losing());
        assertEquals(1L, gpu.lostFrames());

        PipelineStats full = flight(60, 1, 5.0, 0);
        full.memoryFull();
        full.decimated();
        RecordingDiagnosis memory = RecordingDiagnosis.of(full.snapshot(), new RecordingJob.Backlog(300, 0, true, false, 1.0), 0,
                RecordingDiagnosis.Tip.LOWER);
        assertEquals(RecordingDiagnosis.Limit.MEMORY, memory.limit());
        assertEquals(RecordingDiagnosis.Tip.SPACE, memory.tip());
        assertEquals(2L, memory.lostFrames());

        PipelineStats slowDisk = flight(60, 1, 5.0, 0);
        slowDisk.memoryFull();
        for (int i = 0; i < 60; i++) {
            slowDisk.spoolWrite(20_000_000L);
        }
        RecordingDiagnosis disk = RecordingDiagnosis.of(slowDisk.snapshot(), new RecordingJob.Backlog(300, 200, false, false, 0.2), 0,
                RecordingDiagnosis.Tip.LOWER);
        assertEquals(RecordingDiagnosis.Limit.DISK, disk.limit());
        assertEquals(RecordingDiagnosis.Tip.DISK, disk.tip());
    }

    @Test
    void windowsOnlyCountWhatHappenedSince() {
        PipelineStats stats = flight(60, 1, 5.0, 0);
        stats.ringFull();
        PipelineStats.Snapshot earlier = stats.snapshot();
        for (int i = 0; i < 60; i++) {
            stats.renderFrame(16_000_000L);
            stats.slot(0L);
            stats.encoded(5_000_000L, 1);
        }
        PipelineStats.Snapshot window = stats.snapshot().since(earlier);
        assertEquals(0L, window.ringFullDrops());
        assertEquals(60L, window.encoded());
        assertEquals(60L, window.presets()[1]);
        assertEquals(RecordingDiagnosis.Limit.NONE, RecordingDiagnosis.of(window, EMPTY, 0, RecordingDiagnosis.Tip.LOWER).limit());
    }

    @Test
    void backpressureThinsOutEvenlyBeforeTheBufferOverflows() {
        assertEquals(1, Backpressure.stride(0.0, false));
        assertEquals(1, Backpressure.stride(0.59, false));
        assertEquals(2, Backpressure.stride(0.6, false));
        assertEquals(3, Backpressure.stride(0.85, false));
        assertEquals(4, Backpressure.stride(0.95, false));
        assertEquals(2, Backpressure.stride(0.5, true));
        assertEquals(1, Backpressure.stride(Double.NaN, true));
        int kept = 0;
        for (long slot = 0; slot < 60; slot++) {
            kept += Backpressure.keep(slot, 3) ? 1 : 0;
        }
        assertEquals(20, kept);
        assertTrue(Backpressure.keep(7L, 1));
    }

    @Test
    void resourcesScaleWithTheMachine() {
        assertEquals(1, RecordingResources.encoderThreads(2));
        assertEquals(1, RecordingResources.encoderThreads(4));
        assertEquals(3, RecordingResources.encoderThreads(8));
        assertEquals(5, RecordingResources.encoderThreads(12));
        assertEquals(8, RecordingResources.encoderThreads(16));
        assertEquals(12, RecordingResources.encoderThreads(32));
        long gb = 1024L * RecordingResources.MB;
        assertEquals(RecordingResources.MIN_BUFFER, RecordingResources.autoBuffer(2L * gb, 0L));
        assertEquals(gb, RecordingResources.autoBuffer(16L * gb, 0L));
        assertEquals(RecordingResources.MAX_AUTO_BUFFER, RecordingResources.autoBuffer(64L * gb, 0L));
        assertEquals(512L * RecordingResources.MB, RecordingResources.autoBuffer(0L, 2L * gb));
    }

    @Test
    void framesLiveOutsideTheHeapAndAreFreedAfterTheLastBorrowerReturns() throws Exception {
        int frameBytes = 4096;
        FrameQueue queue = new FrameQueue(frameBytes, frameBytes * 12L, 0L, Path.of("unused.frames"));
        ByteBuffer borrowed = queue.acquire();
        assertTrue(borrowed.isDirect());
        ByteBuffer submitted = queue.acquire();
        queue.submit(1L, submitted);
        queue.close();
        assertNull(queue.acquire());
        borrowed.put(0, (byte) 7);
        queue.submit(2L, borrowed);
        boolean freed = false;
        try {
            borrowed.get(0);
        } catch (IllegalStateException e) {
            freed = true;
        }
        assertTrue(freed, "frame memory still mapped after the last frame came back");
    }

    @Test
    void encoderTipOnlySuggestsALowerFormatThatExists() {
        RecordingFormat lowest = RecordingFormat.of(720, 30);
        RecordingFormat full = RecordingFormat.of(1080, 60);
        assertEquals(RecordingDiagnosis.Lower.NONE, RecordingDiagnosis.Lower.of(lowest));
        assertEquals(RecordingDiagnosis.Lower.BOTH, RecordingDiagnosis.Lower.of(full));
        assertEquals(RecordingDiagnosis.Lower.RESOLUTION, RecordingDiagnosis.Lower.of(RecordingFormat.of(1080, 30)));
        assertEquals(RecordingDiagnosis.Lower.FRAME_RATE, RecordingDiagnosis.Lower.of(RecordingFormat.of(720, 60)));
        assertEquals(1080, RecordingDiagnosis.Lower.lowerHeight(RecordingFormat.of(1440, 60)));
        assertEquals(720, RecordingDiagnosis.Lower.lowerHeight(full));
        assertEquals(RecordingDiagnosis.Tip.BUSY, RecordingDiagnosis.encoderTip(true, EncoderChoice.AUTO, false, lowest));
        assertEquals(RecordingDiagnosis.Tip.LOWER, RecordingDiagnosis.encoderTip(true, EncoderChoice.AUTO, false, full));
        assertEquals(RecordingDiagnosis.Tip.ENABLE_HARDWARE, RecordingDiagnosis.encoderTip(false, EncoderChoice.BUILTIN, false, lowest));
        assertEquals(RecordingDiagnosis.Tip.ENABLE_HARDWARE, RecordingDiagnosis.encoderTip(false, EncoderChoice.BUILTIN, true, full));
        assertEquals(RecordingDiagnosis.Tip.HARDWARE, RecordingDiagnosis.encoderTip(false, EncoderChoice.AUTO, true, lowest));
        assertEquals(RecordingDiagnosis.Tip.BUSY, RecordingDiagnosis.encoderTip(false, EncoderChoice.AUTO, false, lowest));
        assertEquals(RecordingDiagnosis.Tip.LOWER, RecordingDiagnosis.encoderTip(false, EncoderChoice.AUTO, false, RecordingFormat.of(720, 60)));
    }

    @Test
    void everyTipHasTextsInBothLanguages() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = videoLang(language);
            for (RecordingDiagnosis.Tip tip : RecordingDiagnosis.Tip.values()) {
                if (tip == RecordingDiagnosis.Tip.NONE) {
                    continue;
                }
                String key = "propwash.video.record.hud.tip." + tip.key();
                assertTrue(lang.has(key), language + ": " + key);
                if (tip.suggestsLower() && tip != RecordingDiagnosis.Tip.LOWER) {
                    assertTrue(lang.has(key + ".or_lower"), language + ": " + key + ".or_lower");
                    assertFalse(lang.get(key).getAsString().contains("%"), language + ": " + key + " ohne Format-Vorschlag");
                }
            }
            assertTrue(lang.get("propwash.video.record.hud.tip.lower").getAsString().contains("%s"), language);
            assertTrue(lang.has("propwash.video.record.hud.lower.both"), language);
        }
    }

    private static JsonObject videoLang(String language) throws IOException {
        for (Path dir : List.of(Path.of("common/src/lang/video"), Path.of("src/lang/video"))) {
            if (Files.isDirectory(dir)) {
                try (Reader reader = Files.newBufferedReader(dir.resolve(language + ".json"), StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        }
        throw new IllegalStateException("Sprachdateien des Videomoduls nicht gefunden");
    }
}
