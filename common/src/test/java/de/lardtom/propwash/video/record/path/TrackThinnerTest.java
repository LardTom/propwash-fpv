package de.lardtom.propwash.video.record.path;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackThinnerTest {
    @Test
    void liveCounterMatchesSavedTrackEveryTick() {
        CameraPathBuffer full = SyntheticFlight.fpv(40.0);
        LiveTrackCounter counter = new LiveTrackCounter(Simplification.OFF);
        CameraPathBuffer growing = new CameraPathBuffer();
        for (int i = 0; i < full.size(); i++) {
            growing.add(full.get(i));
            if (i % 3 == 0) {
                counter.update(growing);
            }
        }
        counter.update(growing);
        List<TickPose> poses = PathResampler.perTick(full);
        assertEquals(poses.size(), counter.ticks());
        CameraTrack saved = CameraTrack.build(poses, Simplification.OFF, Integer.MAX_VALUE).track();
        assertEquals(saved.camera().size(), counter.cameraKeyframes());
        assertEquals(saved.fov().size(), counter.fovKeyframes());
        for (int i = 0; i < poses.size(); i++) {
            assertEquals(poses.get(i).tick(), counter.frames().tick(i));
            assertEquals(poses.get(i).x(), counter.frames().pose(i).x(), 0.0);
            assertEquals(saved.camera().get(i), counter.frames().keyframe(i));
        }
    }

    @Test
    void liveCounterIsCloseWhenSimplified() {
        CameraPathBuffer full = SyntheticFlight.fpv(60.0);
        for (double slider : new double[]{Simplification.DEFAULT_SLIDER, 0.5, 1.0}) {
            Simplification tolerance = Simplification.ofSlider(slider);
            LiveTrackCounter counter = new LiveTrackCounter(tolerance);
            CameraPathBuffer growing = new CameraPathBuffer();
            for (int i = 0; i < full.size(); i++) {
                growing.add(full.get(i));
                counter.update(growing);
            }
            int saved = CameraTrack.build(PathResampler.perTick(full), tolerance, Integer.MAX_VALUE).track().camera().size();
            int live = counter.cameraKeyframes();
            assertTrue(Math.abs(saved - live) <= Math.max(5, saved / 20), "Regler " + slider + ": live " + live + ", gespeichert " + saved);
        }
    }

    @Test
    void savedPathStaysWithinToleranceOfFlashbackSpline() {
        PathFrames frames = PathFrames.of(SyntheticFlight.poses(45.0));
        for (double slider : new double[]{0.05, Simplification.DEFAULT_SLIDER, 0.4, 0.7, 1.0}) {
            Simplification tolerance = Simplification.ofSlider(slider);
            PathPreview preview = PathPreview.of(frames, tolerance, false, 2.0F);
            assertTrue(preview.maxSameTime() <= tolerance.position() * Simplification.REFINE_FACTOR + 1.0E-9,
                    "Regler " + slider + ": " + preview.maxSameTime());
            assertTrue(preview.maxAngle() <= tolerance.angle() * Simplification.REFINE_FACTOR + 1.0E-3,
                    "Regler " + slider + ": " + preview.maxAngle());
            assertTrue(preview.maxOffPath() <= tolerance.position() * Simplification.REFINE_FACTOR + 1.0E-9,
                    "Regler " + slider + ": neben der Bahn " + preview.maxOffPath() + ", zur gleichen Zeit " + preview.maxSameTime());
            assertTrue(preview.topGap().distance() <= preview.maxOffPath() + 1.0E-9);
            assertTrue(preview.keyframes() < frames.size());
        }
    }

    @Test
    void everyTickHasNoDeviation() {
        PathFrames frames = PathFrames.of(SyntheticFlight.poses(20.0));
        PathPreview preview = PathPreview.of(frames, Simplification.OFF, false, 2.0F);
        assertEquals(frames.size(), preview.keyframes());
        assertEquals(0.0, preview.maxSameTime(), 1.0E-9);
        assertEquals(0.0, preview.maxAngle(), 1.0E-3);
        assertEquals(0.0, preview.maxOffPath(), 1.0E-9);
    }

    @Test
    void coarserSliderNeedsFewerKeyframes() {
        PathFrames frames = PathFrames.of(SyntheticFlight.poses(60.0));
        int previous = Integer.MAX_VALUE;
        for (double slider : new double[]{0.0, Simplification.DEFAULT_SLIDER, 0.5, 1.0}) {
            int keys = CameraTrack.build(frames, Simplification.ofSlider(slider), Integer.MAX_VALUE).track().camera().size();
            assertTrue(keys < previous, "Regler " + slider + ": " + keys + " nach " + previous);
            previous = keys;
        }
        assertTrue(previous <= frames.size() / 4, "ganz rechts: " + previous);
    }

    @Test
    void capKeepsLongFlightsInsideTheBudget() {
        PathFrames frames = PathFrames.of(SyntheticFlight.poses(180.0));
        int max = FlashbackBudget.maxCameraKeyframes(2.0F, 2);
        Simplification wanted = Simplification.OFF;
        CameraTrack.Built built = CameraTrack.build(frames, wanted, max);
        CameraTrack track = built.track();
        assertTrue(built.capped());
        assertEquals(frames.size(), built.wantedKeyframes());
        assertTrue(track.camera().size() <= max, "Keyframes " + track.camera().size());
        assertTrue(track.camera().size() > max / 2, "zu stark ausgedünnt: " + track.camera().size());
        assertEquals(frames.firstTick(), track.camera().getFirst().tick());
        assertEquals(frames.lastTick(), track.camera().getLast().tick());
        assertTrue(built.used().position() > Simplification.MIN_POSITION);
        PathPreview preview = PathPreview.of(frames, built, track.fov().size(), 2.0F, max);
        assertTrue(preview.maxSameTime() <= built.used().position() * Simplification.REFINE_FACTOR + 1.0E-9);
        CameraTrack.Built free = CameraTrack.build(frames, wanted, Integer.MAX_VALUE);
        assertEquals(frames.size(), free.track().camera().size());
        assertTrue(!free.capped());
    }

    @Test
    void capHandlesFifteenMinutesAtLargeScale() {
        PathFrames frames = PathFrames.of(SyntheticFlight.poses(15.0 * 60.0));
        int max = FlashbackBudget.maxCameraKeyframes(4.0F, 2);
        long start = System.nanoTime();
        CameraTrack.Built built = CameraTrack.build(frames, Simplification.ofSlider(Simplification.DEFAULT_SLIDER), max);
        double seconds = (System.nanoTime() - start) / 1.0E9;
        assertTrue(built.track().camera().size() <= max, "Keyframes " + built.track().camera().size());
        assertTrue(built.track().usable());
        assertTrue(seconds < 30.0, "Dauer " + seconds);
    }

    @Test
    void capWithoutNeedChangesNothing() {
        PathFrames frames = PathFrames.of(SyntheticFlight.poses(30.0));
        Simplification wanted = Simplification.ofSlider(0.3);
        CameraTrack.Built free = CameraTrack.build(frames, wanted, Integer.MAX_VALUE);
        CameraTrack.Built limited = CameraTrack.build(frames, wanted, FlashbackBudget.maxCameraKeyframes(2.0F, 2));
        assertEquals(free.track(), limited.track());
        assertEquals(wanted, limited.used());
        assertTrue(!limited.capped());
    }
}
