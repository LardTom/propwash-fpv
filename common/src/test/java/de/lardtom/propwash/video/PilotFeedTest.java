package de.lardtom.propwash.video;

import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdLayoutCodec;
import de.lardtom.propwash.osd.OsdPalette;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.rescue.RescuePhase;
import de.lardtom.propwash.sim.BatteryState;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.SpeedUnit;
import de.lardtom.propwash.video.net.PilotFeedC2S;
import de.lardtom.propwash.video.net.PilotFeedS2C;
import de.lardtom.propwash.video.net.PilotLookS2C;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotFeedTest {
    private static final long MS = 1_000_000L;

    private static OsdData pilotOsd() {
        OsdData d = OsdData.sample();
        d.stickRoll = -0.62F;
        d.stickPitch = 0.31F;
        d.stickYaw = 0.9F;
        d.stickThrottle = 0.77F;
        d.mode = StabMode.HORIZON;
        d.mode3d = true;
        d.holdMode = HoldMode.ALTHOLD;
        d.batteryState = BatteryState.LOW;
        d.warnCellVoltage = 3.5;
        d.criticalCellVoltage = 3.3;
        d.crashCount = 2;
        d.failsafeStage = 1;
        d.armBlockers = 5;
        d.worldEdge = true;
        d.rescueActive = true;
        d.rssiDbm = -71.5;
        d.flightSeconds = 95.5;
        d.armedSeconds = 41.25;
        d.trickTypeCount = 1;
        d.trickTypes[0] = d.trickType;
        d.assistLabel = "HOLD";
        return d;
    }

    @Test
    void spectatorSeesWhatThePilotOsdShows() {
        OsdData pilot = pilotOsd();
        PilotFeed feed = PilotFeed.capture(pilot).withRace(7, LapTracker.Phase.RACING, 3, 12, 10, false, LapTracker.NONE, 3_000 * MS, 20_000 * MS)
                .withRescuePhase(RescuePhase.CRUISE);
        OsdData seen = new OsdData();
        feed.apply(seen, 0L);
        assertEquals(pilot.stickRoll, seen.stickRoll);
        assertEquals(pilot.stickPitch, seen.stickPitch);
        assertEquals(pilot.stickYaw, seen.stickYaw);
        assertEquals(pilot.stickThrottle, seen.stickThrottle);
        assertEquals((float) pilot.throttle, (float) seen.throttle);
        assertTrue(seen.armed);
        assertTrue(seen.hasSim);
        assertEquals(StabMode.HORIZON, seen.mode);
        assertTrue(seen.mode3d);
        assertEquals(HoldMode.ALTHOLD, seen.holdMode);
        assertEquals(BatteryState.LOW, seen.batteryState);
        assertEquals((float) pilot.packVoltage, (float) seen.packVoltage);
        assertEquals((float) pilot.cellVoltage, (float) seen.cellVoltage);
        assertEquals(3.5F, (float) seen.warnCellVoltage);
        assertEquals((float) pilot.chargeFraction, (float) seen.chargeFraction);
        assertEquals((float) pilot.mAhUsed, (float) seen.mAhUsed);
        assertEquals((float) pilot.currentA, (float) seen.currentA);
        assertEquals(pilot.cells, seen.cells);
        assertEquals(pilot.capacityMah, seen.capacityMah);
        assertEquals(2, seen.crashCount);
        assertEquals(1, seen.failsafeStage);
        assertEquals(5, seen.armBlockers);
        assertTrue(seen.worldEdge);
        assertTrue(seen.rescueActive);
        assertEquals(-71.5F, (float) seen.rssiDbm);
        assertEquals((float) pilot.linkStrength, (float) seen.linkStrength);
        assertEquals(95.5, seen.flightSeconds, 1.0E-4);
        assertEquals(41.25, seen.armedSeconds, 1.0E-4);
        assertEquals("HOLD", seen.assistLabel);
        assertTrue(seen.hasHome);
        assertEquals(pilot.homeX, seen.homeX);
        assertEquals(pilot.homeZ, seen.homeZ);
        assertEquals(pilot.relayHops, seen.relayHops);
        assertEquals((float) pilot.relayHopQuality[1], (float) seen.relayHopQuality[1]);
        assertTrue(seen.racing);
        assertEquals(pilot.lap, seen.lap);
        assertEquals(pilot.nextGate, seen.nextGate);
        assertEquals(pilot.gateCount, seen.gateCount);
        assertEquals(pilot.currentLapNanos, seen.currentLapNanos);
        assertEquals(pilot.lastLapNanos, seen.lastLapNanos);
        assertEquals(pilot.bestLapNanos, seen.bestLapNanos);
        assertEquals(pilot.splitDeltaNanos, seen.splitDeltaNanos);
        assertTrue(seen.tricksOn);
        assertEquals(pilot.trickType, seen.trickType);
        assertEquals(pilot.trickScore, seen.trickScore);
        assertEquals(7, feed.courseId());
        assertEquals(12, feed.nextGateIndex());
        assertEquals(10, feed.startGateIndex());
        assertEquals(RescuePhase.CRUISE, feed.rescuePhase());
    }

    @Test
    void travelsThroughBothPayloadsUnchanged() {
        PilotFeed feed = PilotFeed.capture(pilotOsd()).withRace(3, LapTracker.Phase.COUNTDOWN, 0, -1, 4, true, 2_500 * MS, 3_000 * MS, Long.MAX_VALUE)
                .withRescuePhase(RescuePhase.HOLD);
        ByteBuf up = Unpooled.buffer();
        PilotFeedC2S.CODEC.encode(up, new PilotFeedC2S(41, feed));
        PilotFeedC2S fromPilot = PilotFeedC2S.CODEC.decode(up);
        assertEquals(0, up.readableBytes());
        assertEquals(41, fromPilot.entityId());
        assertEquals(feed, fromPilot.feed());
        ByteBuf down = Unpooled.buffer();
        PilotFeedS2C.CODEC.encode(down, new PilotFeedS2C(41, fromPilot.feed()));
        PilotFeedS2C toWatcher = PilotFeedS2C.CODEC.decode(down);
        assertEquals(0, down.readableBytes());
        assertEquals(feed, toWatcher.feed());
        assertTrue(toWatcher.feed().countdown());
        assertTrue(toWatcher.feed().falseStarted());
        assertEquals(RescuePhase.HOLD, toWatcher.feed().rescuePhase());
        assertTrue(up.writerIndex() < 400, "Feed je Tick klein halten: " + up.writerIndex() + " Bytes");
    }

    @Test
    void timersRunOnBetweenTwoFeeds() {
        OsdData pilot = pilotOsd();
        PilotFeed feed = PilotFeed.capture(pilot).withRace(2, LapTracker.Phase.RACING, 3, 5, 4, false, LapTracker.NONE, 3_000 * MS, 400 * MS);
        OsdData seen = new OsdData();
        feed.apply(seen, 50 * MS);
        assertEquals(pilot.currentLapNanos + 50 * MS, seen.currentLapNanos);
        assertEquals(95.5 + 0.05, seen.flightSeconds, 1.0E-4);
        assertEquals(41.25 + 0.05, seen.armedSeconds, 1.0E-4);
        assertEquals(450 * MS, feed.sinceGoNanos(50 * MS));
        PilotFeed countdown = PilotFeed.capture(pilot).withRace(2, LapTracker.Phase.COUNTDOWN, 3, -1, 4, false, 1_200 * MS, 3_000 * MS,
                Long.MAX_VALUE);
        assertEquals(1_150 * MS, countdown.countdownRemainingNanos(50 * MS));
        assertEquals(0L, countdown.countdownRemainingNanos(5_000 * MS));
        assertEquals(Long.MAX_VALUE, countdown.sinceGoNanos(50 * MS));
        assertEquals(LapTracker.NONE, feed.countdownRemainingNanos(0L));
    }

    @Test
    void unarmedPilotKeepsTheClocksStill() {
        OsdData pilot = pilotOsd();
        pilot.armed = false;
        pilot.armedSeconds = 0.0;
        OsdData seen = new OsdData();
        PilotFeed.capture(pilot).apply(seen, 500 * MS);
        assertFalse(seen.armed);
        assertEquals(95.5, seen.flightSeconds, 1.0E-4);
        assertEquals(0.0, seen.armedSeconds);
        assertEquals(pilot.racing, seen.racing);
    }

    @Test
    void spectatorSamplerKeepsThePilotClock() {
        PilotFeed feed = PilotFeed.capture(pilotOsd());
        OsdSampler.Source watched = new OsdSampler.Source() {
            @Override
            public boolean active() {
                return true;
            }

            @Override
            public void fill(OsdData data) {
                feed.apply(data, 0L);
            }

            @Override
            public boolean remoteClock() {
                return true;
            }
        };
        OsdSampler sampler = new OsdSampler(watched);
        long now = 5_000 * MS;
        for (int frame = 0; frame < 60; frame++) {
            now += 20 * MS;
            sampler.sample(now);
        }
        OsdData shown = sampler.data();
        assertEquals(95.5, shown.flightSeconds, 1.0E-4, "Flugzeit des Piloten, nicht seit Beginn des Zuschauens gezählt");
        assertEquals(41.25, shown.armedSeconds, 1.0E-4, "Zeit seit dem Scharfschalten des Piloten");
        assertEquals(-0.62F, shown.stickRoll);
    }

    @Test
    void pilotLayoutReplacesOnlyTheShownOsd() {
        OsdConfig own = OsdConfig.defaults();
        own.hideVanillaHud = false;
        own.analogBreakup = false;
        String ownBefore = OsdLayoutCodec.encode(own);
        OsdConfig pilot = OsdConfig.defaults();
        pilot.applyPreset(OsdPreset.RACE);
        pilot.color = OsdPalette.ORANGE.hex();
        pilot.slot(OsdElement.STICK_OVERLAY).enabled = true;
        pilot.move(OsdElement.LAP_TIME, 3, 9);
        PilotLook look = PilotLook.of(pilot, SpeedUnit.MPH, StickMode.MODE_1);
        ByteBuf buf = Unpooled.buffer();
        PilotLookS2C.CODEC.encode(buf, new PilotLookS2C(9, look));
        PilotLook received = PilotLookS2C.CODEC.decode(buf).look();
        assertEquals(look, received);
        assertEquals(SpeedUnit.MPH, received.unit());
        assertEquals(StickMode.MODE_1, received.stickMode());
        OsdConfig shown = received.config(own);
        assertEquals(OsdPreset.RACE, shown.preset);
        assertEquals(pilot.color, shown.color);
        assertTrue(shown.enabled(OsdElement.STICK_OVERLAY));
        assertEquals(3, shown.slot(OsdElement.LAP_TIME).col);
        assertEquals(9, shown.slot(OsdElement.LAP_TIME).row);
        assertFalse(shown.hideVanillaHud, "Vanilla-HUD bleibt Einstellung des Zuschauers");
        assertFalse(shown.analogBreakup, "Bildstörung bleibt Einstellung des Zuschauers");
        assertEquals(ownBefore, OsdLayoutCodec.encode(own), "eigenes OSD bleibt unberührt");
        assertNotEquals(OsdPreset.RACE, own.preset);
    }

    @Test
    void oversizedLayoutIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PilotLook("x".repeat(OsdLayoutCodec.MAX_LENGTH + 1), SpeedUnit.KMH, StickMode.MODE_2));
    }

    @Test
    void feedRateStopsFloods() {
        FeedRate rate = new FeedRate(PilotFeedServer.FEEDS_PER_SECOND, PilotFeedServer.FEED_BURST);
        int accepted = 0;
        for (int i = 0; i < 100; i++) {
            if (rate.accept(1_000L)) {
                accepted++;
            }
        }
        assertEquals(PilotFeedServer.FEED_BURST, accepted);
        assertEquals(100 - PilotFeedServer.FEED_BURST, rate.refused());
        accepted = 0;
        for (long t = 1_050L; t <= 2_000L; t += 50L) {
            if (rate.accept(t)) {
                accepted++;
            }
        }
        assertEquals(20, accepted, "ein Feed je Tick (20 Hz) kommt immer durch");
        accepted = 0;
        for (long t = 3_000L; t < 4_000L; t++) {
            if (rate.accept(t)) {
                accepted++;
            }
        }
        assertTrue(accepted <= PilotFeedServer.FEED_BURST + PilotFeedServer.FEEDS_PER_SECOND + 1, "Flut von 1000 Feeds je Sekunde: " + accepted);
    }

    @Test
    void blankFeedShowsNothingRacing() {
        OsdData seen = new OsdData();
        new PilotFeed().apply(seen, 0L);
        assertFalse(seen.racing);
        assertFalse(seen.armed);
        assertEquals(0.0F, seen.stickRoll);
        assertNull(seen.assistLabel);
        assertEquals(LapTracker.NO_COURSE, new PilotFeed().courseId());
    }
}
