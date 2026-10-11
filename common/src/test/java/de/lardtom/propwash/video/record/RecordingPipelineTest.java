package de.lardtom.propwash.video.record;

import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.video.record.mp4.Mp4Info;
import de.lardtom.propwash.video.record.mp4.Mp4Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingPipelineTest {
    private static final long SECOND = 1_000_000_000L;

    @TempDir
    Path dir;

    private static void fill(ByteBuffer frame, int width, int height, int index) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                frame.put(y * width + x, (byte) ((x + y * 2 + index * 5) & 0xFF));
            }
        }
        for (int i = width * height; i < frame.limit(); i++) {
            frame.put(i, (byte) 128);
        }
    }

    private static void fillWith(ByteBuffer frame, byte value) {
        for (int i = 0; i < frame.limit(); i++) {
            frame.put(i, value);
        }
    }

    @Test
    void formatsFollowTheCameraLimits() {
        assertEquals(List.of(720, 1080), RecordingFormat.heightsFor(ActionCamSpec.LIGHT));
        assertEquals(List.of(720, 1080, 1440), RecordingFormat.heightsFor(ActionCamSpec.HEAVY));
        assertEquals(List.of(30, 60), RecordingFormat.ratesFor(ActionCamSpec.LIGHT));
        RecordingFormat capped = RecordingFormat.forCamera(ActionCamSpec.LIGHT, 1440, 60);
        assertEquals(1080, capped.height());
        assertEquals(1920, capped.width());
        assertEquals(60, capped.fps());
        RecordingFormat heavy = RecordingFormat.forCamera(ActionCamSpec.HEAVY, 1440, 60);
        assertEquals(2560, heavy.width());
        assertEquals(1280, RecordingFormat.of(720, 30).width());
        assertEquals(1920 * 1080 * 3 / 2, RecordingFormat.of(1080, 30).frameBytes());
        assertTrue(RecordingFormat.of(1080, 60).bitrate() > RecordingFormat.of(1080, 30).bitrate());
        assertTrue(RecordingFormat.of(1080, 60).bitrate() <= 50_000_000L);
    }

    @Test
    void frameClockSchedulesSlotsAndSkipsPausedTime() {
        FrameClock clock = new FrameClock(30);
        clock.start(0L);
        assertTrue(clock.due(0L));
        assertEquals(0L, clock.take(0L));
        assertFalse(clock.due(SECOND / 120));
        assertEquals(-1L, clock.take(SECOND / 120));
        assertEquals(1L, clock.take(SECOND / 30));
        assertEquals(4L, clock.take(SECOND * 4 / 30));
        clock.pause(SECOND);
        assertFalse(clock.due(2 * SECOND));
        assertEquals(SECOND, clock.elapsedNanos(2 * SECOND));
        clock.resume(3 * SECOND);
        assertEquals(SECOND + SECOND / 2, clock.elapsedNanos(3 * SECOND + SECOND / 2));
        long slot = clock.slotAt(3 * SECOND + SECOND / 2);
        assertTrue(slot >= 44L && slot <= 46L, "slot " + slot);
    }

    @Test
    void frameClockLocksOntoAMatchingRenderRateWithoutJudder() {
        java.util.Random random = new java.util.Random(3L);
        for (double phase : new double[]{0.0, 0.3, 0.49, 0.5, 0.97, 0.999}) {
            FrameClock clock = new FrameClock(60);
            clock.start(0L);
            long previous = -1L;
            int steps = 0;
            int irregular = 0;
            for (int frame = 0; frame < 1200; frame++) {
                long t = (long) ((frame + phase) * SECOND / 60.0 + (random.nextDouble() - 0.5) * 3_000_000.0);
                if (!clock.due(Math.max(0L, t))) {
                    continue;
                }
                long slot = clock.take(Math.max(0L, t));
                if (previous >= 0L && frame > 60) {
                    steps++;
                    if (slot - previous != 1L) {
                        irregular++;
                    }
                }
                previous = slot;
            }
            assertTrue(steps > 1000, "phase " + phase + ": " + steps);
            assertTrue(irregular <= 2, "phase " + phase + ": " + irregular + " Sprünge");
        }
    }

    @Test
    void frameClockTakesOneFramePerSlotAtHighRenderRatesAndRepeatsAtLowOnes() {
        FrameClock fast = new FrameClock(60);
        fast.start(0L);
        int taken = 0;
        long last = -1L;
        for (int frame = 0; frame < 144 * 5; frame++) {
            long t = frame * SECOND / 144;
            if (fast.due(t)) {
                long slot = fast.take(t);
                assertTrue(slot == last + 1L, "slot " + slot + " nach " + last);
                last = slot;
                taken++;
            }
        }
        assertEquals(300, taken, 2);
        FrameClock slow = new FrameClock(60);
        slow.start(0L);
        long before = -1L;
        int gaps = 0;
        for (int frame = 0; frame < 30 * 4; frame++) {
            long t = frame * SECOND / 30;
            long slot = slow.take(t);
            if (before >= 0L && slot - before == 2L) {
                gaps++;
            }
            before = slot;
        }
        assertTrue(gaps > 110, "gaps " + gaps);
    }

    @Test
    void heavyBatteryLastsAboutAnHourAndAHalfAtFullHd() {
        double seconds = CamPower.secondsLeft(1.0F, ActionCamSpec.HEAVY, 1080);
        assertEquals(90.0, seconds / 60.0, 1.0);
        assertTrue(CamPower.secondsLeft(1.0F, ActionCamSpec.HEAVY, 1440) < seconds);
        float after = CamPower.drain(1.0F, ActionCamSpec.HEAVY, 1080, 60.0);
        assertEquals(1.0F - 1.0F / 90.0F, after, 1.0E-3F);
        assertEquals(1.0F, CamPower.drain(1.0F, ActionCamSpec.LIGHT, 1080, 600.0));
        assertTrue(Double.isInfinite(CamPower.secondsLeft(0.5F, ActionCamSpec.LIGHT, 1080)));
        assertTrue(CamPower.empty(0.0F, ActionCamSpec.HEAVY));
        assertFalse(CamPower.empty(0.0F, ActionCamSpec.LIGHT));
    }

    @Test
    void heavyChargesOverUsbFromTheFlightPackButKeepsAReserve() {
        float[] step = CamPower.usbCharge(0.5F, 0.9F, ActionCamSpec.HEAVY, BatteryType.B6S_1300, 12.0);
        assertEquals(0.6F, step[0], 1.0E-4F);
        double costPerCharge = ActionCamSpec.HEAVY.ownBatteryWh() / CamPower.USB_EFFICIENCY / CamPower.packWattHours(BatteryType.B6S_1300);
        assertEquals(0.9F - 0.1F * costPerCharge, step[1], 1.0E-4F);
        assertEquals(0.26, costPerCharge, 0.01);
        float[] reserve = CamPower.usbCharge(0.0F, 0.16F, ActionCamSpec.HEAVY, BatteryType.B6S_1300, 600.0);
        assertEquals(CamPower.PACK_RESERVE, reserve[1], 1.0E-5F);
        assertTrue(reserve[0] > 0.0F && reserve[0] < 0.1F);
        float[] blocked = CamPower.usbCharge(0.2F, 0.1F, ActionCamSpec.HEAVY, BatteryType.B6S_1300, 60.0);
        assertEquals(0.2F, blocked[0]);
        assertEquals(0.1F, blocked[1]);
        float[] light = CamPower.usbCharge(0.2F, 0.9F, ActionCamSpec.LIGHT, BatteryType.B6S_1300, 60.0);
        assertEquals(0.2F, light[0]);
        assertEquals(0.9F, light[1]);
        float[] full = CamPower.usbCharge(0.99F, 0.9F, ActionCamSpec.HEAVY, BatteryType.B6S_1300, 60.0);
        assertEquals(1.0F, full[0]);
        assertTrue(full[1] > 0.89F);
    }

    @Test
    void queueSpillsToDiskAndKeepsOrder() throws Exception {
        int frameBytes = 4096;
        Path spool = this.dir.resolve("spool/test.frames");
        try (FrameQueue queue = new FrameQueue(frameBytes, frameBytes * 12L, 1L << 30, spool)) {
            int max = queue.maxBuffers();
            int submitted = 0;
            long deadline = System.currentTimeMillis() + 5000L;
            while (submitted < 40 && System.currentTimeMillis() < deadline) {
                ByteBuffer buffer = queue.acquire();
                if (buffer == null) {
                    Thread.sleep(5L);
                    continue;
                }
                fillWith(buffer, (byte) submitted);
                queue.submit(submitted, buffer);
                submitted++;
            }
            assertEquals(40, submitted);
            assertTrue(queue.spilledTotal() > 0L, "nothing spilled with " + max + " buffers");
            assertTrue(Files.exists(spool));
            for (int i = 0; i < 40; i++) {
                FrameQueue.Taken taken = queue.take(2000L);
                assertNotNull(taken, "frame " + i);
                assertEquals(i, taken.slot());
                assertEquals((byte) i, taken.data().get(0));
                assertEquals((byte) i, taken.data().get(frameBytes - 1));
                if (taken.pooled()) {
                    queue.release(taken.data());
                }
            }
            assertNull(queue.take(10L));
        }
        assertFalse(Files.exists(spool));
    }

    @Test
    void queueEnforcesItsMemoryCapWithoutSpool() {
        int frameBytes = 1024;
        try (FrameQueue queue = new FrameQueue(frameBytes, frameBytes * 10L, 0L, this.dir.resolve("none.frames"))) {
            int got = 0;
            for (int i = 0; i < 50; i++) {
                ByteBuffer buffer = queue.acquire();
                if (buffer != null) {
                    queue.submit(i, buffer);
                    got++;
                }
            }
            assertEquals(queue.maxBuffers(), got);
            assertEquals(50 - got, queue.dropped());
        }
    }

    @Test
    void jobWritesAMp4WithTheRecordedDurationAndRepeatsMissingFrames() throws Exception {
        RecordingFormat format = new RecordingFormat(320, 180, 30);
        Path file = this.dir.resolve("recordings/propwash_test.mp4");
        RecordingJob job = RecordingJob.start(new RecordingJob.Options(file, this.dir.resolve("recordings/.spool"), format, 2,
                format.frameBytes() * 16L, 1L << 28));
        for (int slot = 0; slot < 90; slot++) {
            if (slot % 7 == 3) {
                continue;
            }
            ByteBuffer buffer = null;
            for (int attempt = 0; attempt < 400 && buffer == null; attempt++) {
                buffer = job.acquire();
                if (buffer == null) {
                    Thread.sleep(5L);
                }
            }
            assertNotNull(buffer);
            fill(buffer, 320, 180, slot);
            job.submit(slot, buffer);
        }
        job.finish(90L);
        job.awaitDone(60_000L);
        RecordingJob.Progress progress = job.progress();
        assertEquals(RecordingJob.State.DONE, progress.state(), progress.error());
        assertEquals(file, progress.result());
        assertEquals(90L, progress.encodedSlots());
        assertEquals(1.0F, progress.fraction());
        assertTrue(progress.repeatedFrames() >= 12L);
        assertFalse(Files.exists(file.resolveSibling("propwash_test.mp4.part")));
        Mp4Info info = Mp4Reader.read(file);
        assertEquals(90, info.sampleCount());
        assertEquals(3.0, info.durationSeconds(), 1.0E-9);
        assertEquals(320, info.width());
        assertEquals(180, info.height());
    }

    @Test
    void jobStartsAtTheFirstFrameAndPadsToTheEnd() throws Exception {
        RecordingFormat format = new RecordingFormat(64, 64, 60);
        Path file = this.dir.resolve("late.mp4");
        RecordingJob job = RecordingJob.start(new RecordingJob.Options(file, this.dir.resolve(".spool"), format, 1,
                format.frameBytes() * 8L, 0L));
        for (int slot = 5; slot < 20; slot += 2) {
            ByteBuffer buffer = job.acquire();
            assertNotNull(buffer);
            fill(buffer, 64, 64, slot);
            job.submit(slot, buffer);
        }
        job.finish(125L);
        job.awaitDone(30_000L);
        assertEquals(RecordingJob.State.DONE, job.progress().state(), job.progress().error());
        Mp4Info info = Mp4Reader.read(file);
        assertEquals(120, info.sampleCount());
        assertEquals(2.0, info.durationSeconds(), 1.0E-9);
    }

    @Test
    void cancelledJobLeavesNoFile() throws Exception {
        RecordingFormat format = new RecordingFormat(64, 64, 30);
        Path file = this.dir.resolve("cancel.mp4");
        RecordingJob job = RecordingJob.start(new RecordingJob.Options(file, this.dir.resolve(".spool"), format, 1,
                format.frameBytes() * 8L, 0L));
        ByteBuffer buffer = job.acquire();
        fill(buffer, 64, 64, 0);
        job.submit(0L, buffer);
        job.cancel();
        job.awaitDone(10_000L);
        assertEquals(RecordingJob.State.CANCELLED, job.progress().state());
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(this.dir.resolve("cancel.mp4.part")));
    }

    @Test
    void existingFilesAreNotOverwritten() throws IOException {
        Path wanted = this.dir.resolve("clip.mp4");
        assertEquals(wanted, RecordingJob.uniqueTarget(wanted));
        Files.writeString(wanted, "x");
        assertEquals(this.dir.resolve("clip_2.mp4"), RecordingJob.uniqueTarget(wanted));
    }
}
