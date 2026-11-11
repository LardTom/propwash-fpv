package de.lardtom.propwash.osd;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectatorCountTest {
    private static final String DRONE = "drone";
    private static final String OTHER = "other";
    private static final UUID PILOT = new UUID(0L, 1L);
    private static final UUID SPECTATE = new UUID(0L, 2L);
    private static final UUID GOGGLES = new UUID(0L, 3L);
    private static final UUID GIMBAL = new UUID(0L, 4L);
    private static final UUID ELSEWHERE = new UUID(0L, 5L);
    private static final BiPredicate<String, UUID> PILOT_AND_GIMBAL = (drone, player) -> drone.equals(DRONE)
            && (player.equals(PILOT) || player.equals(GIMBAL));

    @Test
    void countsSpectatorsAndGogglesOnTheChannelOfThisDrone() {
        Map<String, Integer> counts = new HashMap<>();
        SpectatorCount.add(counts, SPECTATE, DRONE, DRONE, PILOT_AND_GIMBAL);
        SpectatorCount.add(counts, GOGGLES, null, DRONE, PILOT_AND_GIMBAL);
        SpectatorCount.add(counts, ELSEWHERE, OTHER, OTHER, PILOT_AND_GIMBAL);
        assertEquals(2, SpectatorCount.of(counts, DRONE));
        assertEquals(1, SpectatorCount.of(counts, OTHER));
        assertEquals(0, SpectatorCount.of(counts, "nobody"));
    }

    @Test
    void pilotAndGimbalOperatorAreNotCounted() {
        Map<String, Integer> counts = new HashMap<>();
        SpectatorCount.add(counts, PILOT, DRONE, DRONE, PILOT_AND_GIMBAL);
        SpectatorCount.add(counts, GIMBAL, null, DRONE, PILOT_AND_GIMBAL);
        SpectatorCount.add(counts, GIMBAL, DRONE, DRONE, PILOT_AND_GIMBAL);
        assertEquals(0, SpectatorCount.of(counts, DRONE));
        SpectatorCount.add(counts, SPECTATE, DRONE, DRONE, PILOT_AND_GIMBAL);
        assertEquals(1, SpectatorCount.of(counts, DRONE));
    }

    @Test
    void aPlayerWithoutPictureCountsNowhere() {
        Map<String, Integer> counts = new HashMap<>();
        SpectatorCount.add(counts, ELSEWHERE, null, null, PILOT_AND_GIMBAL);
        assertTrue(counts.isEmpty());
    }

    @Test
    void aPlayerCountsOnceEvenWhenBothWaysShowTheSameDrone() {
        Map<String, Integer> counts = new HashMap<>();
        SpectatorCount.add(counts, SPECTATE, DRONE, DRONE, PILOT_AND_GIMBAL);
        assertEquals(1, SpectatorCount.of(counts, DRONE));
    }

    @Test
    void theShownDroneIsTheFlownOneFirstThenTheWatchedOne() {
        assertSame(DRONE, SpectatorCount.shown(DRONE, OTHER, OTHER));
        assertSame(OTHER, SpectatorCount.shown(null, OTHER, DRONE));
        assertSame(DRONE, SpectatorCount.shown(null, null, DRONE));
        assertNull(SpectatorCount.shown(null, null, null));
    }

    @Test
    void outboxSendsOnlyWhenTheNumberChanges() {
        SpectatorCount.Outbox outbox = new SpectatorCount.Outbox();
        int sent = 0;
        for (int tick = 0; tick < 200; tick++) {
            int count = tick < 50 ? 0 : tick < 120 ? 1 : 2;
            if (outbox.offer(PILOT, 42, count)) {
                sent++;
            }
        }
        assertEquals(2, sent);
        assertFalse(outbox.offer(PILOT, 42, 2));
        assertTrue(outbox.offer(PILOT, 42, 1));
        assertTrue(outbox.offer(PILOT, 42, 0));
        assertFalse(outbox.offer(PILOT, 42, 0));
        assertEquals(1, outbox.size());
    }

    @Test
    void zeroIsOnlySentToReplaceAShownNumber() {
        SpectatorCount.Outbox outbox = new SpectatorCount.Outbox();
        assertFalse(outbox.offer(SPECTATE, 7, 0));
        assertEquals(0, outbox.size());
        assertTrue(outbox.offer(SPECTATE, 7, 3));
        assertFalse(outbox.offer(SPECTATE, 8, 0));
        assertTrue(outbox.offer(SPECTATE, 7, 0));
        assertTrue(outbox.offer(SPECTATE, 8, 1));
        assertTrue(outbox.offer(SPECTATE, 7, 1));
    }

    @Test
    void forgettingAPlayerSendsTheNumberAgain() {
        SpectatorCount.Outbox outbox = new SpectatorCount.Outbox();
        assertTrue(outbox.offer(PILOT, 42, 2));
        assertTrue(outbox.offer(GOGGLES, 42, 2));
        outbox.forget(PILOT);
        assertTrue(outbox.offer(PILOT, 42, 2));
        assertFalse(outbox.offer(GOGGLES, 42, 2));
        outbox.clear();
        assertEquals(0, outbox.size());
        assertTrue(outbox.offer(GOGGLES, 42, 2));
    }

    @Test
    void numbersAreClampedForTheOsd() {
        assertEquals(0, SpectatorCount.clamp(-3));
        assertEquals(12, SpectatorCount.clamp(12));
        assertEquals(SpectatorCount.MAX, SpectatorCount.clamp(SpectatorCount.MAX + 5));
    }

    @Test
    void payloadRoundTripsThroughTheCodec() {
        SpectatorCountS2C payload = new SpectatorCountS2C(1234, 3);
        ByteBuf buffer = Unpooled.buffer();
        SpectatorCountS2C.CODEC.encode(buffer, payload);
        assertEquals(payload, SpectatorCountS2C.CODEC.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        assertEquals("propwash:spectator_count", SpectatorCountS2C.TYPE.id().toString());
    }

    @Test
    void elementIsOnInStandardAndFullAndOffInMinimalAndRace() {
        OsdElement element = OsdElement.byKey("spectators");
        assertSame(OsdElement.SPECTATORS, element);
        assertTrue(OsdPreset.STANDARD.enabled(element));
        assertTrue(OsdPreset.FULL.enabled(element));
        assertFalse(OsdPreset.MINIMAL.enabled(element));
        assertFalse(OsdPreset.RACE.enabled(element));
        assertTrue(element.movable());
        assertSame(OsdElement.Group.STATUS, element.group());
        assertSame(OsdElement.Align.RIGHT, element.align());
        assertEquals(OsdElement.values().length - 1, element.ordinal());
    }

    @Test
    void existingLayoutsGainTheElementFromTheirPreset() {
        OsdConfig standard = OsdConfig.defaults();
        standard.applyPreset(OsdPreset.STANDARD);
        standard.elements.remove(OsdElement.SPECTATORS.key());
        OsdConfig.sanitize(standard);
        assertTrue(standard.enabled(OsdElement.SPECTATORS));
        assertTrue(OsdPreset.STANDARD.matches(standard));
        OsdConfig minimal = OsdConfig.defaults();
        minimal.elements.remove(OsdElement.SPECTATORS.key());
        OsdConfig.sanitize(minimal);
        assertFalse(minimal.enabled(OsdElement.SPECTATORS));
        assertTrue(OsdPreset.MINIMAL.matches(minimal));
    }

    @Test
    void sampleShowsViewersAndCopiesTheNumber() {
        OsdData sample = OsdData.sample();
        assertTrue(sample.spectators > 0);
        OsdData copy = new OsdData();
        sample.copyTo(copy);
        assertEquals(sample.spectators, copy.spectators);
        copy.reset();
        assertEquals(0, copy.spectators);
    }
}
