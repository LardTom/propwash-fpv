package de.lardtom.propwash.race;

import de.lardtom.propwash.video.record.external.HardwareEncoders;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayExportTest {
    private static final long MS = 1_000_000L;
    private static final long BEGIN = -1_000L * MS;
    private static final long END = 20_000L * MS;

    @Test
    void withoutFfmpegTheButtonIsOff() {
        assertEquals(ReplayExport.Block.NO_FFMPEG, ReplayExport.ffmpeg(HardwareEncoders.State.NO_FFMPEG));
        assertEquals(ReplayExport.Block.FFMPEG_BROKEN, ReplayExport.ffmpeg(HardwareEncoders.State.UNUSABLE));
        assertEquals(ReplayExport.Block.CHECKING, ReplayExport.ffmpeg(HardwareEncoders.State.CHECKING));
    }

    @Test
    void withFfmpegTheRecorderChoosesTheEncoder() {
        assertEquals(ReplayExport.Block.NONE, ReplayExport.ffmpeg(HardwareEncoders.State.READY));
        assertEquals(ReplayExport.Block.NONE, ReplayExport.ffmpeg(HardwareEncoders.State.NO_HARDWARE));
        assertEquals(ReplayExport.Block.NONE, ReplayExport.ffmpeg(HardwareEncoders.State.BROKEN));
        assertEquals(ReplayExport.Block.NONE, ReplayExport.ffmpeg(HardwareEncoders.State.OFF));
    }

    @Test
    void playbackFollowsTheRecordingClockFromTheLeadOn() {
        assertEquals(BEGIN, ReplayExport.time(BEGIN, END, 0L));
        assertEquals(-500L * MS, ReplayExport.time(BEGIN, END, 500L * MS));
        assertEquals(0L, ReplayExport.time(BEGIN, END, 1_000L * MS));
        assertEquals(19_000L * MS, ReplayExport.time(BEGIN, END, 20_000L * MS));
    }

    @Test
    void playbackStopsAtTheEndHoweverLateTheFrameComes() {
        assertEquals(END, ReplayExport.time(BEGIN, END, 21_000L * MS));
        assertEquals(END, ReplayExport.time(BEGIN, END, 60_000L * MS));
    }

    @Test
    void progressRunsOverLeadAndLap() {
        assertEquals(0.0F, ReplayExport.fraction(BEGIN, BEGIN, END), 1.0E-6F);
        assertEquals(1.0F / 21.0F, ReplayExport.fraction(0L, BEGIN, END), 1.0E-6F);
        assertEquals(1.0F, ReplayExport.fraction(END, BEGIN, END), 1.0E-6F);
        assertEquals(1.0F, ReplayExport.fraction(END + MS, BEGIN, END), 1.0E-6F);
        assertEquals(0.0F, ReplayExport.fraction(BEGIN - MS, BEGIN, END), 1.0E-6F);
    }

    @Test
    void emptyTrackHasNoProgress() {
        assertEquals(0.0F, ReplayExport.fraction(0L, 0L, 0L), 1.0E-6F);
    }

    @Test
    void everyBlockNamesItsOwnText() {
        Set<String> keys = new HashSet<>();
        for (ReplayExport.Block block : ReplayExport.Block.values()) {
            assertTrue(keys.add(block.translationKey()), block.name());
        }
    }
}
