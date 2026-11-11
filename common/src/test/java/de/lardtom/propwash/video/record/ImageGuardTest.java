package de.lardtom.propwash.video.record;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageGuardTest {
    private static final RecordingFormat FORMAT = RecordingFormat.of(720, 30);
    private static final int LUMA = FORMAT.width() * FORMAT.height();

    private static ByteBuffer frame(int y, int u, int v) {
        ByteBuffer frame = ByteBuffer.allocateDirect(FORMAT.frameBytes());
        for (int i = 0; i < FORMAT.frameBytes(); i++) {
            frame.put(i, (byte) (i < LUMA ? y : i < LUMA + LUMA / 4 ? u : v));
        }
        return frame;
    }

    @Test
    void zeroLumaPlaneIsBlankLikeTheDiscordVideo() {
        assertTrue(ImageGuard.blank(frame(0, 0, 0), FORMAT));
        assertTrue(ImageGuard.blank(frame(0, 128, 128), FORMAT), "Farbebenen zählen nicht");
    }

    @Test
    void blackPictureOfThePackShaderIsNotBlank() {
        assertFalse(ImageGuard.blank(frame(16, 128, 128), FORMAT));
    }

    @Test
    void oneLitPixelAnywhereInTheLumaPlaneIsAPicture() {
        ByteBuffer zero = frame(0, 0, 0);
        zero.put(LUMA - 1, (byte) 16);
        assertFalse(ImageGuard.blank(zero, FORMAT));
        ByteBuffer middle = frame(0, 0, 0);
        middle.put(LUMA / 2 + 3, (byte) 1);
        assertFalse(ImageGuard.blank(middle, FORMAT));
        ByteBuffer chromaOnly = frame(0, 0, 0);
        chromaOnly.put(LUMA, (byte) 128);
        assertTrue(ImageGuard.blank(chromaOnly, FORMAT));
    }

    @Test
    void zeroChecksExactlyTheGivenBytesFromTheStart() {
        ByteBuffer data = ByteBuffer.allocateDirect(64);
        assertTrue(ImageGuard.zero(data, 64));
        data.put(13, (byte) 255);
        assertFalse(ImageGuard.zero(data, 64));
        assertTrue(ImageGuard.zero(data, 13));
        data.position(20);
        assertFalse(ImageGuard.zero(data, 64), "Position des Puffers zählt nicht");
    }

    @Test
    void goodFramesStayOnTheGpuPath() {
        ImageGuard guard = new ImageGuard(true);
        for (int i = 0; i < 10; i++) {
            assertEquals(ImageGuard.Verdict.ACCEPT, guard.check(ImagePath.GPU, false));
        }
        assertEquals(ImagePath.GPU, guard.path());
        assertEquals(10L, guard.acceptedFrames());
        assertEquals(0L, guard.blankFrames());
        assertEquals(List.of(), guard.broken());
    }

    @Test
    void blankGpuFrameSwitchesToTheFramebufferReadAndLateGpuFramesAreDropped() {
        ImageGuard guard = new ImageGuard(true);
        assertEquals(ImageGuard.Verdict.SWITCH, guard.check(ImagePath.GPU, true));
        assertEquals(ImagePath.READ, guard.path());
        assertEquals(ImageGuard.Verdict.DROP, guard.check(ImagePath.GPU, true));
        assertEquals(ImageGuard.Verdict.DROP, guard.check(ImagePath.GPU, false));
        assertEquals(ImageGuard.Verdict.ACCEPT, guard.check(ImagePath.READ, false));
        assertEquals(ImagePath.READ, guard.path());
        assertEquals(1L, guard.acceptedFrames());
        assertEquals(1L, guard.blankFrames());
        assertEquals(2L, guard.droppedFrames());
        assertEquals(List.of(ImagePath.GPU), guard.broken());
    }

    @Test
    void blankFramebufferReadFallsBackToTheCpuConversion() {
        ImageGuard guard = new ImageGuard(true);
        guard.check(ImagePath.GPU, true);
        assertEquals(ImageGuard.Verdict.SWITCH, guard.check(ImagePath.READ, true));
        assertEquals(ImagePath.CPU, guard.path());
        assertEquals(ImageGuard.Verdict.ACCEPT, guard.check(ImagePath.CPU, false));
        assertEquals(List.of(ImagePath.GPU, ImagePath.READ), guard.broken());
    }

    @Test
    void nothingDeliversAPictureAbortsAfterTryingEveryPath() {
        ImageGuard guard = new ImageGuard(true);
        assertEquals(ImageGuard.Verdict.SWITCH, guard.check(ImagePath.GPU, true));
        assertEquals(ImageGuard.Verdict.SWITCH, guard.check(ImagePath.READ, true));
        assertEquals(ImageGuard.Verdict.ABORT, guard.check(ImagePath.CPU, true));
        assertEquals(ImagePath.NONE, guard.path());
        assertEquals(3L, guard.blankFrames());
        assertEquals(0L, guard.acceptedFrames());
        assertEquals(ImageGuard.Verdict.DROP, guard.check(ImagePath.CPU, false));
        assertEquals(List.of(ImagePath.GPU, ImagePath.READ, ImagePath.CPU), guard.broken());
    }

    @Test
    void withoutFramebufferReadTheFirstBlankFrameAborts() {
        ImageGuard guard = new ImageGuard(false);
        assertEquals(ImageGuard.Verdict.ABORT, guard.check(ImagePath.GPU, true));
        assertEquals(ImagePath.NONE, guard.path());
        assertEquals(ImageGuard.Verdict.DROP, guard.check(ImagePath.GPU, false));
    }

    @Test
    void aPathThatBreaksLaterStillSwitchesAndKeepsTheGoodFrames() {
        ImageGuard guard = new ImageGuard(true);
        guard.check(ImagePath.GPU, false);
        guard.check(ImagePath.GPU, false);
        assertEquals(ImageGuard.Verdict.SWITCH, guard.check(ImagePath.GPU, true));
        assertEquals(2L, guard.acceptedFrames());
        assertEquals(ImagePath.READ, guard.path());
    }

    @Test
    void pathKeysAndReplacement() {
        assertEquals("gpu", ImagePath.GPU.key());
        assertEquals("read", ImagePath.READ.key());
        assertEquals("cpu", ImagePath.CPU.key());
        assertEquals("none", ImagePath.NONE.key());
        assertFalse(ImagePath.GPU.replacement());
        assertTrue(ImagePath.READ.replacement());
        assertTrue(ImagePath.CPU.replacement());
        assertFalse(ImagePath.NONE.replacement());
    }
}
