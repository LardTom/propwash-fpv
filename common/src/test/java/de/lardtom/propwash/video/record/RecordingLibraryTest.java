package de.lardtom.propwash.video.record;

import de.lardtom.propwash.video.record.h264.EncodedFrame;
import de.lardtom.propwash.video.record.h264.EncoderSettings;
import de.lardtom.propwash.video.record.h264.H264Encoder;
import de.lardtom.propwash.video.record.mp4.Mp4Writer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingLibraryTest {
    @TempDir
    Path dir;

    private Path clip(String name, int frames, long modified) throws IOException {
        Path file = this.dir.resolve(name);
        byte[] image = new byte[64 * 36 * 3 / 2];
        Arrays.fill(image, 0, 64 * 36, (byte) 90);
        Arrays.fill(image, 64 * 36, image.length, (byte) 128);
        try (H264Encoder encoder = new H264Encoder(64, 36, 30, EncoderSettings.of(1, 200_000L, 30))) {
            Mp4Writer writer = new Mp4Writer(file, 64, 36, 30_000, encoder.sps(), encoder.pps(), "test");
            for (int i = 0; i < frames; i++) {
                EncodedFrame frame = i == 0 ? encoder.encode(image, 1) : encoder.repeat();
                writer.writeSample(frame.toSample(), 1000, frame.keyframe());
            }
            writer.finish();
        }
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified));
        return file;
    }

    @Test
    void listsClipsNewestFirstWithTheirMetadata() throws IOException {
        Path older = this.clip("propwash_2026-09-20_10-00-00.mp4", 30, 1_000_000L);
        Path newer = this.clip("propwash_2026-09-21_11-30-05.mp4", 90, 2_000_000L);
        Files.writeString(this.dir.resolve("notes.txt"), "not a clip");
        Files.writeString(this.dir.resolve("propwash_2026-09-22_12-00-00.mp4.part"), "unfinished");
        RecordingLibrary library = new RecordingLibrary(this.dir, Runnable::run);
        library.refresh();
        List<ClipInfo> clips = library.clips();
        assertTrue(library.scanned());
        assertEquals(List.of(newer, older), clips.stream().map(ClipInfo::file).toList());
        ClipInfo first = clips.getFirst();
        assertTrue(first.readable());
        assertEquals(3.0, first.seconds(), 0.001);
        assertEquals("36p30", first.format());
        assertEquals("0:03", first.duration());
        assertFalse(first.audio());
        assertEquals(LocalDateTime.of(2026, 9, 21, 11, 30, 5), first.recordedAt());
    }

    @Test
    void brokenFilesStayListedButUnreadable() throws IOException {
        Files.write(this.dir.resolve("propwash_2026-09-20_10-00-00.mp4"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9});
        RecordingLibrary library = new RecordingLibrary(this.dir, Runnable::run);
        List<ClipInfo> clips = library.scanNow();
        assertEquals(1, clips.size());
        assertFalse(clips.getFirst().readable());
    }

    @Test
    void deleteRemovesClipAndThumbnailButNothingElse() throws IOException {
        Path file = this.clip("propwash_2026-09-20_10-00-00.mp4", 10, 1_000_000L);
        Path thumbnail = RecordingLibrary.thumbnailOf(file);
        Files.createDirectories(thumbnail.getParent());
        Files.write(thumbnail, new byte[]{1});
        RecordingLibrary library = new RecordingLibrary(this.dir, Runnable::run);
        library.refresh();
        assertEquals(1, library.clips().size());
        assertTrue(library.delete(file));
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(thumbnail));
        assertTrue(library.clips().isEmpty());
        Path outside = this.dir.resolveSibling("elsewhere.mp4");
        assertThrows(IOException.class, () -> library.delete(outside));
        assertThrows(IOException.class, () -> library.delete(this.dir.resolve("notes.txt")));
    }

    @Test
    void thumbnailsLiveInAHiddenFolderAndOrphansArePruned() throws IOException {
        Path file = this.dir.resolve("propwash_2026-09-20_10-00-00.mp4");
        assertEquals(this.dir.resolve(".thumbs").resolve("propwash_2026-09-20_10-00-00.png"), RecordingLibrary.thumbnailOf(file));
        Path thumbs = this.dir.resolve(RecordingLibrary.THUMBNAILS);
        Files.createDirectories(thumbs);
        Files.write(thumbs.resolve("gone.png"), new byte[]{1});
        Files.write(thumbs.resolve("saving.png"), new byte[]{1});
        Files.write(this.dir.resolve("saving.mp4.part"), new byte[]{1});
        Files.write(thumbs.resolve("kept.png"), new byte[]{1});
        Files.write(this.dir.resolve("kept.mp4"), new byte[]{1});
        assertEquals(1, RecordingLibrary.pruneThumbnails(this.dir));
        assertFalse(Files.exists(thumbs.resolve("gone.png")));
        assertTrue(Files.exists(thumbs.resolve("saving.png")));
        assertTrue(Files.exists(thumbs.resolve("kept.png")));
    }

    @Test
    void formatsDurationsSizesAndNames() {
        assertEquals("0:00", ClipInfo.duration(0L));
        assertEquals("1:05", ClipInfo.duration(65L));
        assertEquals("1:01:01", ClipInfo.duration(3661L));
        assertEquals("1 KB", ClipInfo.size(1L));
        assertEquals("2.5 MB", ClipInfo.size(2_621_440L));
        assertEquals("1.50 GB", ClipInfo.size(1_610_612_736L));
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5), ClipInfo.parseRecordedAt("propwash_2026-01-02_03-04-05_2.mp4"));
        assertNull(ClipInfo.parseRecordedAt("holiday.mp4"));
        assertNull(ClipInfo.parseRecordedAt("propwash_2026-13-02_03-04-05.mp4"));
    }
}
