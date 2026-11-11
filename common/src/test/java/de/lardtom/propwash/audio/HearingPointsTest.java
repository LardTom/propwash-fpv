package de.lardtom.propwash.audio;

import de.lardtom.propwash.audio.HearingPoints.Listener;
import de.lardtom.propwash.audio.HearingPoints.Point;
import de.lardtom.propwash.audio.HearingPoints.View;
import de.lardtom.propwash.drone.PilotMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearingPointsTest {
    private static final double SOUND_RANGE = 16.0;
    private static final double LEVEL_EVENT_RANGE = 64.0;
    private static final Point SOUND = new Point(200.0, 65.0, 0.0);
    private static final Point HOME = new Point(0.0, 64.0, 0.0);
    private static final Point DRONE = new Point(203.0, 66.0, 1.0);

    private static List<String> heard(List<Listener<String>> listeners, String except, double range) {
        return HearingPoints.recipients(listeners, except, SOUND.x(), SOUND.y(), SOUND.z(), range);
    }

    private static List<String> heard(List<Listener<String>> listeners, double range) {
        return heard(listeners, null, range);
    }

    @Test
    void fpvPilotWithBodyFarAwayHearsAtTheDrone() {
        Listener<String> pilot = new Listener<>("pilot", HOME, View.FPV_PILOT, DRONE);
        assertEquals(List.of("pilot"), heard(List.of(pilot), SOUND_RANGE));
        assertEquals(List.of("pilot"), heard(List.of(pilot), LEVEL_EVENT_RANGE));
        assertSame(DRONE, pilot.hearing(SOUND.x(), SOUND.y(), SOUND.z()));
    }

    @Test
    void withoutTheDroneTheFarBodyHearsNothing() {
        Listener<String> walker = new Listener<>("walker", HOME, View.BODY, DRONE);
        assertEquals(List.of(), heard(List.of(walker), SOUND_RANGE));
        assertEquals(List.of(), heard(List.of(walker), LEVEL_EVENT_RANGE));
        Listener<String> sight = new Listener<>("sight", HOME, View.LINE_OF_SIGHT_PILOT, DRONE);
        assertEquals(List.of(), heard(List.of(sight), LEVEL_EVENT_RANGE));
    }

    @Test
    void bodyAndDroneBothNearGetTheSoundExactlyOnce() {
        Point body = new Point(196.0, 65.0, -2.0);
        Listener<String> pilot = new Listener<>("pilot", body, View.FPV_PILOT, DRONE);
        Listener<String> other = new Listener<>("other", new Point(201.0, 65.0, 2.0), View.BODY, null);
        List<String> got = heard(List.of(pilot, other), SOUND_RANGE);
        assertEquals(List.of("pilot", "other"), got);
        assertEquals(1, got.stream().filter("pilot"::equals).count());
    }

    @Test
    void spectatorHearsAtTheWatchedDrone() {
        Listener<String> spectator = new Listener<>("spectator", new Point(-500.0, 70.0, 300.0), View.SPECTATOR, DRONE);
        assertEquals(List.of("spectator"), heard(List.of(spectator), SOUND_RANGE));
    }

    @Test
    void excludedPlayerStaysExcludedEvenWithTheDroneNear() {
        Listener<String> pilot = new Listener<>("pilot", HOME, View.FPV_PILOT, DRONE);
        Listener<String> spectator = new Listener<>("spectator", HOME, View.SPECTATOR, DRONE);
        assertEquals(List.of("spectator"), heard(List.of(pilot, spectator), "pilot", SOUND_RANGE));
        Listener<String> near = new Listener<>("near", SOUND, View.FPV_PILOT, DRONE);
        assertEquals(List.of(), heard(List.of(near), "near", LEVEL_EVENT_RANGE));
    }

    @Test
    void reachFollowsTheVolumeLikeVanilla() {
        Point drone = new Point(SOUND.x() + 20.0, SOUND.y(), SOUND.z());
        Listener<String> pilot = new Listener<>("pilot", HOME, View.FPV_PILOT, drone);
        assertEquals(List.of(), heard(List.of(pilot), SOUND_RANGE));
        assertEquals(List.of("pilot"), heard(List.of(pilot), SOUND_RANGE * 2.0));
        assertEquals(List.of("pilot"), heard(List.of(pilot), LEVEL_EVENT_RANGE));
        Point edge = new Point(SOUND.x() + SOUND_RANGE, SOUND.y(), SOUND.z());
        assertFalse(HearingPoints.inRange(edge, SOUND.x(), SOUND.y(), SOUND.z(), SOUND_RANGE));
        assertTrue(HearingPoints.inRange(new Point(edge.x() - 0.01, edge.y(), edge.z()), SOUND.x(), SOUND.y(), SOUND.z(), SOUND_RANGE));
    }

    @Test
    void theNearerPointWins() {
        assertTrue(HearingPoints.droneCloser(0, 0, 0, 10, 0, 0, 8, 0, 0));
        assertFalse(HearingPoints.droneCloser(0, 0, 0, 10, 0, 0, 2, 0, 0));
        assertFalse(HearingPoints.droneCloser(0, 0, 0, 10, 0, 0, 5, 0, 0));
        Listener<String> pilot = new Listener<>("pilot", HOME, View.FPV_PILOT, DRONE);
        assertSame(HOME, pilot.hearing(HOME.x() + 1.0, HOME.y(), HOME.z()));
        Listener<String> unknown = new Listener<>("pilot", HOME, View.FPV_PILOT, null);
        assertSame(HOME, unknown.hearing(SOUND.x(), SOUND.y(), SOUND.z()));
    }

    @Test
    void viewFollowsWhatThePlayerLooksThrough() {
        assertEquals(View.BODY, HearingPoints.view(false, PilotMode.NONE));
        assertEquals(View.LINE_OF_SIGHT_PILOT, HearingPoints.view(false, PilotMode.LINE_OF_SIGHT));
        assertEquals(View.FPV_PILOT, HearingPoints.view(false, PilotMode.FPV));
        assertEquals(View.SPECTATOR, HearingPoints.view(true, PilotMode.NONE));
        assertEquals(View.SPECTATOR, HearingPoints.view(true, PilotMode.LINE_OF_SIGHT));
        assertTrue(View.FPV_PILOT.atDrone());
        assertTrue(View.SPECTATOR.atDrone());
        assertFalse(View.BODY.atDrone());
        assertFalse(View.LINE_OF_SIGHT_PILOT.atDrone());
    }
}
