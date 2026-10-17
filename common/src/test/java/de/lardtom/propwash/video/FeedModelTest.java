package de.lardtom.propwash.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedModelTest {
    private static final long FRAME = 1_000_000_000L / 144L;
    private static final long START = 10_000_000_000L;
    private static final long MS = 1_000_000L;

    private static FeedInput input(FeedLook look, float quality, int latencyMs) {
        FeedInput input = new FeedInput().set(look, true, quality, latencyMs * MS);
        input.snowOnLoss = look == FeedLook.ANALOG;
        input.strength = 0.7F;
        input.reduceFlicker = true;
        return input;
    }

    private static long run(FeedModel model, FeedInput input, long from, int frames) {
        long now = from;
        for (int i = 0; i < frames; i++) {
            model.update(now, input);
            now += FRAME;
        }
        return now;
    }

    @Test
    void analogAtFullSignalIsCleanContinuousAndLowLatency() {
        FeedModel model = new FeedModel(1L);
        FeedInput in = input(FeedLook.ANALOG, 1.0F, 12);
        in.imageQuality = 0.65F;
        long now = run(model, in, START, 200);
        EffectParams p = model.params();
        assertEquals(0.0F, p.noSignal, 1.0E-6F);
        assertTrue(p.noise < 0.05F, "noise " + p.noise);
        assertEquals(0.0F, p.roll, 1.0E-6F);
        assertTrue(p.continuous && p.frameAdvanced);
        assertEquals(12 * MS, model.latencyAt(now));
    }

    @Test
    void analogNoiseGrowsGraduallyAndEndsInSnow() {
        float previous = -1.0F;
        for (float q = 1.0F; q >= 0.2F; q -= 0.1F) {
            FeedModel model = new FeedModel(2L);
            FeedInput in = input(FeedLook.ANALOG, q, 12);
            in.reduceFlicker = false;
            run(model, in, START, 30);
            float noise = model.params().noise;
            assertTrue(noise >= previous - 0.08F, "noise not rising at q=" + q);
            previous = noise;
        }
        FeedModel lost = new FeedModel(3L);
        run(lost, input(FeedLook.ANALOG, 0.0F, 12), START, 30);
        assertEquals(1.0F, lost.params().noSignal, 1.0E-6F);
        assertEquals(0.0F, lost.params().noSignalBlack, 1.0E-6F);
        assertTrue(lost.lost());
    }

    @Test
    void analogWeakSignalProducesRollsWithoutReduceFlicker() {
        FeedModel model = new FeedModel(4L);
        FeedInput in = input(FeedLook.ANALOG, 0.15F, 12);
        in.reduceFlicker = false;
        boolean rolled = false;
        long now = START;
        for (int i = 0; i < 144 * 20; i++) {
            model.update(now, in);
            rolled |= Math.abs(model.params().roll) > 0.3F;
            now += FRAME;
        }
        assertTrue(rolled, "no sync loss roll in 20 s at q=0.15");
    }

    @Test
    void reduceFlickerLimitsRollsShiftsAndFullFrameFlicker() {
        FeedModel model = new FeedModel(5L);
        FeedInput in = input(FeedLook.ANALOG, 0.1F, 12);
        long now = START;
        int events = 0;
        boolean rolling = false;
        for (int i = 0; i < 144 * 30; i++) {
            EffectParams p = model.update(now, in);
            assertTrue(Math.abs(p.roll) <= FeedModel.GENTLE_MAX_ROLL + 1.0E-6F, "roll " + p.roll);
            assertTrue(Math.abs(p.syncShift) <= FeedModel.GENTLE_MAX_SHIFT + 1.0E-6F, "shift " + p.syncShift);
            assertTrue(p.noiseContrast < 1.0F);
            boolean nowRolling = Math.abs(p.roll) > 0.05F;
            if (nowRolling && !rolling) {
                events++;
            }
            rolling = nowRolling;
            now += FRAME;
        }
        assertTrue(events <= 30 / 2.5 + 1, "too many sync events: " + events);
        FeedModel steady = new FeedModel(6L);
        float previous = steady.update(START, in).brightness;
        for (int i = 1; i < 144 * 10; i++) {
            float brightness = steady.update(START + i * FRAME, in).brightness;
            assertEquals(previous, brightness, 1.0E-3F, "brightness jumped at frame " + i);
            previous = brightness;
        }
    }

    @Test
    void noiseSeedUpdatesAtALimitedRate() {
        FeedModel model = new FeedModel(7L);
        FeedInput in = input(FeedLook.ANALOG, 0.5F, 12);
        long now = START;
        float seed = Float.NaN;
        int changes = 0;
        for (int i = 0; i < 144; i++) {
            float s = model.update(now, in).seed;
            if (s != seed) {
                changes++;
                seed = s;
            }
            now += FRAME;
        }
        assertTrue(changes <= 32, "seed changed " + changes + " times in 1 s with reduce flicker");
    }

    @Test
    void digitalAtFullSignalIsCleanAndContinuous() {
        FeedModel model = new FeedModel(8L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        long now = run(model, in, START, 144);
        EffectParams p = model.params();
        assertEquals(0.0F, p.noSignal, 1.0E-6F);
        assertEquals(0.0F, p.blockiness, 1.0E-6F);
        assertTrue(p.blurPx < 0.3F);
        assertTrue(p.visuallyClean());
        assertTrue(p.frameAdvanced && p.continuous);
        assertFalse(p.needsPass());
        assertEquals(30 * MS, model.latencyAt(now));
    }

    @Test
    void digitalStartsWithABriefSyncThenShowsPicture() {
        FeedModel model = new FeedModel(9L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        in.reduceFlicker = false;
        assertEquals(1.0F, model.update(START, in).noSignal, 1.0E-6F);
        run(model, in, START + FRAME, (int) (FeedModel.LOSS_HOLD_NANOS / FRAME) + 5);
        assertEquals(0.0F, model.params().noSignal, 1.0E-6F);
    }

    @Test
    void digitalWeakSignalStuttersFreezesAndGetsBlocky() {
        FeedModel model = new FeedModel(10L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        long now = run(model, in, START, 200);
        in.quality = 0.2F;
        int held = 0;
        int frames = 144 * 10;
        long maxLatency = 0L;
        for (int i = 0; i < frames; i++) {
            EffectParams p = model.update(now, in);
            if (!p.frameAdvanced) {
                held++;
            }
            maxLatency = Math.max(maxLatency, model.latencyAt(now));
            now += FRAME;
        }
        EffectParams p = model.params();
        assertTrue(held > frames / 5, "held frames " + held);
        assertTrue(p.blockiness > 0.1F);
        assertTrue(p.blurPx > 1.0F);
        assertTrue(maxLatency > 100 * MS, "max latency " + maxLatency / MS + " ms");
    }

    @Test
    void displayedTimeFollowsTheFrameClock() {
        FeedModel analog = new FeedModel(3L);
        long now = run(analog, input(FeedLook.ANALOG, 1.0F, 12), START, 200);
        assertEquals(now - 12 * MS, analog.displayedNanos(now));
        assertEquals(now + 5 * MS - 12 * MS, analog.displayedNanos(now + 5 * MS));
        FeedModel digital = new FeedModel(10L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        now = run(digital, in, START, 200);
        in.quality = 0.2F;
        int frozen = 0;
        for (int i = 0; i < 144 * 5; i++) {
            EffectParams p = digital.update(now, in);
            if (!p.continuous && !p.frameAdvanced) {
                long shown = digital.displayedNanos(now);
                assertEquals(p.displayedCaptureNanos, shown);
                assertEquals(shown, digital.displayedNanos(now + FRAME / 2));
                frozen++;
            }
            assertTrue(digital.displayedNanos(now) <= now - 30 * MS);
            now += FRAME;
        }
        assertTrue(frozen > 0, "kein eingefrorenes Bild");
    }

    @Test
    void digitalHoldsTheLastFrameBeforeItCutsAndRecoversWithHysteresis() {
        FeedModel model = new FeedModel(11L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        in.reduceFlicker = false;
        long now = run(model, in, START, 200);
        in.quality = 0.03F;
        assertEquals(0.0F, model.update(now, in).noSignal, 1.0E-6F, "no instant black on a sudden drop");
        now += FRAME;
        int grace = (int) (DigitalProfile.ADAPTIVE.lossGraceNanos() / FRAME) - 2;
        for (int i = 0; i < grace; i++) {
            EffectParams p = model.update(now, in);
            assertEquals(0.0F, p.noSignal, 1.0E-6F, "frame " + i + " of the hold");
            assertTrue(!p.frameAdvanced && !p.continuous, "last frame stays on screen");
            now += FRAME;
        }
        now = run(model, in, now, 10);
        EffectParams cut = model.params();
        assertEquals(1.0F, cut.noSignal, 1.0E-6F);
        assertTrue(cut.blackout());
        in.quality = 0.15F;
        now = run(model, in, now, 144);
        assertEquals(1.0F, model.params().noSignal, 1.0E-6F, "must stay lost between enter and exit threshold");
        in.quality = 0.5F;
        model.update(now, in);
        now += FRAME;
        assertEquals(0.0F, model.params().noSignal, 1.0E-6F);
        in.quality = 0.03F;
        model.update(now, in);
        now += FRAME;
        in.quality = 0.5F;
        now = run(model, in, now, 20);
        assertEquals(0.0F, model.params().noSignal, 1.0E-6F, "a single bad frame never blacks out");
        in.quality = 0.0F;
        now = run(model, in, now, (int) (DigitalProfile.ADAPTIVE.lossGraceNanos() / FRAME) + 10);
        assertEquals(1.0F, model.params().noSignal, 1.0E-6F);
        in.quality = 0.5F;
        model.update(now, in);
        assertEquals(1.0F, model.params().noSignal, 1.0E-6F, "minimum hold time must prevent fast toggling");
    }

    @Test
    void aLongStallAtTheEdgeComesBackWithoutAStrongerSignal() {
        FeedModel model = new FeedModel(18L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        in.reduceFlicker = false;
        long now = run(model, in, START, 200);
        in.quality = 0.1F;
        int black = 0;
        int frames = 144 * 60;
        boolean recovered = false;
        boolean wasBlack = false;
        for (int i = 0; i < frames; i++) {
            EffectParams p = model.update(now, in);
            boolean isBlack = p.noSignal >= 0.999F;
            if (isBlack) {
                black++;
            } else if (wasBlack) {
                recovered = true;
            }
            wasBlack = isBlack;
            now += FRAME;
        }
        assertTrue(black > 0, "a marginal link stalls into a loss now and then");
        assertTrue(recovered, "and comes back at the same quality");
        assertTrue(black < frames / 2, "black " + black + " of " + frames);
    }

    @Test
    void lostConnectionStillCutsAtOnce() {
        FeedModel model = new FeedModel(14L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        in.reduceFlicker = false;
        long now = run(model, in, START, 200);
        in.connected = false;
        assertEquals(1.0F, model.update(now, in).noSignal, 1.0E-6F);
    }

    @Test
    void fixedRateLinkCutsSoonerThanAnAdaptiveOne() {
        assertTrue(DigitalProfile.FIXED_RATE.lossGraceNanos() < DigitalProfile.ADAPTIVE.lossGraceNanos());
        FeedModel model = new FeedModel(15L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 14);
        in.digitalProfile = DigitalProfile.FIXED_RATE;
        in.reduceFlicker = false;
        long now = run(model, in, START, 200);
        in.quality = 0.0F;
        now = run(model, in, now, (int) (DigitalProfile.FIXED_RATE.lossGraceNanos() / FRAME) + 10);
        assertEquals(1.0F, model.params().noSignal, 1.0E-6F);
    }

    @Test
    void adaptiveLinkGetsLaggierWhileFixedRateKeepsItsLatency() {
        assertEquals(0L, DigitalProfile.ADAPTIVE.extraLatencyNanos(1.0));
        assertEquals(0L, DigitalProfile.ADAPTIVE.extraLatencyNanos(DigitalBreakup.CLEAN_ABOVE));
        assertTrue(DigitalProfile.ADAPTIVE.extraLatencyNanos(0.5) > 10 * MS);
        assertEquals(DigitalProfile.ADAPTIVE.maxExtraLatencyNanos(), DigitalProfile.ADAPTIVE.extraLatencyNanos(DigitalBreakup.LOSS_ENTER));
        long previous = -1L;
        for (double q = 1.0; q >= 0.0; q -= 0.01) {
            long extra = DigitalProfile.ADAPTIVE.extraLatencyNanos(q);
            assertTrue(extra >= previous);
            previous = extra;
            assertEquals(0L, DigitalProfile.FIXED_RATE.extraLatencyNanos(q));
        }
        double adaptive = meanLatency(DigitalProfile.ADAPTIVE, 0.45F);
        double fixed = meanLatency(DigitalProfile.FIXED_RATE, 0.45F);
        assertTrue(adaptive > fixed + 15 * MS, "adaptive " + adaptive / MS + " ms, fixed " + fixed / MS + " ms");
    }

    private static double meanLatency(DigitalProfile profile, float quality) {
        FeedModel model = new FeedModel(16L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 20);
        in.digitalProfile = profile;
        long now = run(model, in, START, 200);
        in.quality = quality;
        double sum = 0.0;
        int frames = 144 * 5;
        for (int i = 0; i < frames; i++) {
            model.update(now, in);
            sum += model.latencyAt(now);
            now += FRAME;
        }
        return sum / frames;
    }

    @Test
    void adaptiveLinkSoftensWhileFixedRateStaysSharpAndBreaksUp() {
        FeedModel adaptive = new FeedModel(17L);
        FeedInput in = input(FeedLook.DIGITAL, 0.5F, 30);
        long now = run(adaptive, in, START, 300);
        EffectParams a = adaptive.params();
        assertTrue(a.blurPx > 1.0F && a.resolution < 1.0F, "blur " + a.blurPx + " resolution " + a.resolution);
        FeedModel fixed = new FeedModel(17L);
        in.digitalProfile = DigitalProfile.FIXED_RATE;
        run(fixed, in, now, 300);
        EffectParams f = fixed.params();
        assertEquals(0.0F, f.blurPx, 1.0E-6F);
        assertEquals(1.0F, f.resolution, 1.0E-6F);
        assertTrue(f.blockiness > 0.1F, "breakup " + f.blockiness);
        assertEquals(DigitalProfile.FIXED_RATE, DigitalProfile.of(14));
        assertEquals(DigitalProfile.ADAPTIVE, DigitalProfile.of(26));
    }

    @Test
    void reduceFlickerFadesTheCutInsteadOfSwitching() {
        FeedModel model = new FeedModel(12L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        long now = run(model, in, START, 400);
        in.quality = 0.0F;
        now = run(model, in, now, (int) (DigitalProfile.ADAPTIVE.lossGraceNanos() / FRAME) - 2);
        assertEquals(0.0F, model.params().noSignal, 1.0E-6F);
        now = run(model, in, now, 5);
        float first = model.params().noSignal;
        assertTrue(first > 0.0F && first < 0.2F, "first frames of the fade " + first);
        now = run(model, in, now, 40);
        assertEquals(1.0F, model.params().noSignal, 1.0E-6F);
    }

    @Test
    void disconnectedLinkShowsTheReceiverStyle() {
        FeedModel analogGoggles = new FeedModel(13L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        in.connected = false;
        in.snowOnLoss = true;
        run(analogGoggles, in, START, 10);
        assertEquals(1.0F, analogGoggles.params().noSignal, 1.0E-6F);
        assertEquals(0.0F, analogGoggles.params().noSignalBlack, 1.0E-6F);
        FeedModel digitalGoggles = new FeedModel(14L);
        in.snowOnLoss = false;
        run(digitalGoggles, in, START, 10);
        assertTrue(digitalGoggles.params().blackout());
    }

    @Test
    void cleanLookOnlyAddsRequestedNoise() {
        FeedModel model = new FeedModel(15L);
        FeedInput in = input(FeedLook.CLEAN, 1.0F, 0);
        run(model, in, START, 10);
        assertTrue(model.params().visuallyClean());
        in.extraNoise = 0.5F;
        run(model, in, START + 10 * FRAME, 10);
        assertEquals(0.45F, model.params().noise, 1.0E-6F);
        assertEquals(0.0F, model.params().noSignal, 1.0E-6F);
        assertEquals(0L, model.latencyAt(START));
    }

    @Test
    void effectStrengthScalesCosmeticsButNotTheCut() {
        FeedModel strong = new FeedModel(16L);
        FeedModel weak = new FeedModel(16L);
        FeedInput in = input(FeedLook.ANALOG, 0.25F, 12);
        in.strength = 1.0F;
        run(strong, in, START, 20);
        FeedInput soft = input(FeedLook.ANALOG, 0.25F, 12);
        soft.strength = 0.0F;
        run(weak, soft, START, 20);
        assertTrue(weak.params().bleedPx < strong.params().bleedPx);
        assertTrue(weak.params().vignette < strong.params().vignette);
        assertTrue(weak.params().noise > 0.1F, "weak signal must stay visible at strength 0");
        FeedModel lost = new FeedModel(17L);
        soft.quality = 0.0F;
        run(lost, soft, START, 20);
        assertEquals(1.0F, lost.params().noSignal, 1.0E-6F);
    }

    @Test
    void resetStartsOver() {
        FeedModel model = new FeedModel(18L);
        FeedInput in = input(FeedLook.DIGITAL, 1.0F, 30);
        run(model, in, START, 200);
        model.reset();
        assertTrue(model.lost());
        assertEquals(1.0F, model.update(START + 300 * FRAME, in).noSignal, 1.0E-6F);
    }

    @Test
    void damagedCameraAddsArtifactsAndDeadCameraShowsBlack() {
        for (FeedLook look : new FeedLook[]{FeedLook.ANALOG, FeedLook.DIGITAL}) {
            FeedModel clean = new FeedModel(7L);
            run(clean, input(look, 1.0F, 20), START, 200);
            FeedModel scratched = new FeedModel(7L);
            FeedInput worn = input(look, 1.0F, 20);
            worn.cameraCondition = 0.3F;
            run(scratched, worn, START, 200);
            assertTrue(scratched.params().noise > clean.params().noise + 0.05F, look + " Rauschen " + scratched.params().noise);
            assertTrue(scratched.params().desaturation > clean.params().desaturation, look.name());
            assertEquals(0.0F, scratched.params().noSignal, 1.0E-6F);
            FeedModel dead = new FeedModel(7L);
            FeedInput broken = input(look, 1.0F, 20);
            broken.cameraCondition = 0.0F;
            run(dead, broken, START, 10);
            assertTrue(dead.params().blackout(), look + " kaputte Kamera zeigt noch ein Bild");
        }
        FeedModel slightly = new FeedModel(8L);
        FeedModel fresh = new FeedModel(8L);
        FeedInput used = input(FeedLook.ANALOG, 1.0F, 12);
        used.cameraCondition = 0.95F;
        run(slightly, used, START, 30);
        run(fresh, input(FeedLook.ANALOG, 1.0F, 12), START, 30);
        assertEquals(fresh.params().noise, slightly.params().noise, 1.0E-6F);
    }

    @Test
    void digitalNeverShowsAnalogSnowOrColourLoss() {
        for (float q = 1.0F; q >= 0.0F; q -= 0.05F) {
            FeedModel model = new FeedModel(19L);
            FeedInput in = input(FeedLook.DIGITAL, q, 30);
            in.reduceFlicker = false;
            run(model, in, START, 144);
            EffectParams p = model.params();
            assertEquals(0.0F, p.noise, 1.0E-6F, "snow at q=" + q);
            assertEquals(0.0F, p.desaturation, 1.0E-6F, "colour loss at q=" + q);
            assertEquals(0.0F, p.syncShift, 1.0E-6F);
            assertEquals(0.0F, p.roll, 1.0E-6F);
            assertEquals(0.0F, p.osdShift, 1.0E-6F, "digital OSD is goggle-side and never shifts");
        }
    }

    @Test
    void analogGlitchesAreIrregular() {
        FeedModel model = new FeedModel(20L);
        FeedInput in = input(FeedLook.ANALOG, 0.66F, 12);
        in.reduceFlicker = false;
        long now = START;
        boolean active = false;
        int last = -1;
        java.util.List<Integer> gaps = new java.util.ArrayList<>();
        for (int i = 0; i < 144 * 30; i++) {
            boolean glitch = model.update(now, in).glitch > 0.0F;
            if (glitch && !active) {
                if (last >= 0) {
                    gaps.add(i - last);
                }
                last = i;
            }
            active = glitch;
            now += FRAME;
        }
        assertTrue(gaps.size() > 10, "too few glitches: " + gaps.size());
        long distinct = gaps.stream().distinct().count();
        assertTrue(distinct > gaps.size() / 2, "glitch rhythm looks periodic: " + gaps);
    }

    @Test
    void analogStagesAppearInOrderAsTheLinkFalls() {
        float[] qualities = {0.9F, 0.68F, 0.47F, 0.25F, 0.0F};
        AnalogBreakup.Stage[] expected = AnalogBreakup.Stage.values();
        for (int i = 0; i < qualities.length; i++) {
            FeedModel model = new FeedModel(21L + i);
            FeedInput in = input(FeedLook.ANALOG, qualities[i], 12);
            run(model, in, START, 144);
            assertEquals(expected[i], model.analogStage(), "stage at q=" + qualities[i]);
        }
        FeedModel grey = new FeedModel(30L);
        run(grey, input(FeedLook.ANALOG, 0.3F, 12), START, 144);
        assertTrue(grey.params().desaturation > 0.95F, "black and white under the snow");
        assertTrue(grey.params().noise > 0.05F);
    }

    @Test
    void multipathCausesShortDropoutsAtMediumRange() {
        FeedModel model = new FeedModel(31L);
        FeedInput in = input(FeedLook.ANALOG, 0.75F, 12);
        in.reduceFlicker = false;
        in.multipath = 1.0F;
        long now = START;
        float lowest = 1.0F;
        for (int i = 0; i < 144 * 20; i++) {
            model.update(now, in);
            lowest = Math.min(lowest, model.link());
            now += FRAME;
        }
        assertTrue(lowest < 0.6F, "no multipath dropout, lowest link " + lowest);
        FeedModel open = new FeedModel(31L);
        in.multipath = 0.0F;
        now = START;
        float openLowest = 1.0F;
        for (int i = 0; i < 144 * 20; i++) {
            open.update(now, in);
            openLowest = Math.min(openLowest, open.link());
            now += FRAME;
        }
        assertTrue(openLowest > 0.68F, "open field link dipped to " + openLowest);
    }

    @Test
    void analogOsdShiftStaysSmall() {
        FeedModel model = new FeedModel(32L);
        FeedInput in = input(FeedLook.ANALOG, 0.2F, 12);
        in.reduceFlicker = false;
        long now = START;
        for (int i = 0; i < 144 * 20; i++) {
            float shift = model.update(now, in).osdShift;
            assertTrue(Math.abs(shift) <= 0.11F, "osd shift " + shift);
            now += FRAME;
        }
    }
}
