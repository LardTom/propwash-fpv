package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.race.GateGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonRunTest {
    private static Lesson lesson(String id) {
        Lesson lesson = Curriculum.lesson(id);
        assertNotNull(lesson, id);
        return lesson;
    }

    @Test
    void armLessonNeedsTwoSecondsThenDisarmOnTheGround() {
        Flight early = Flight.of(lesson("arm"));
        assertEquals(LessonRun.State.READY, early.run.state());
        early.arm();
        assertEquals(LessonRun.State.RUNNING, early.run.state());
        early.hold(1.0).disarm();
        assertEquals(LessonRun.State.FAILED, early.run.state());
        assertEquals(LessonRun.Failure.TOO_EARLY, early.run.failure());

        Flight good = Flight.of(lesson("arm"));
        good.arm().hold(2.3).disarm();
        assertEquals(LessonRun.State.SUCCESS, good.run.state());
        LessonRun.Result result = good.run.result();
        assertNotNull(result);
        assertEquals(3, result.stars());
        assertTrue(result.time() > 2.0 && result.time() < 2.6, "Zeit " + result.time());
        assertEquals(1, good.count(LessonRun.EventType.SUCCESS));
    }

    @Test
    void runStartsOnlyWithAFreshArm() {
        Flight flight = Flight.of(lesson("arm"));
        flight.sample.armed = true;
        LessonRun run = new LessonRun(flight.run.course(), null);
        flight.sample.time += Flight.DT;
        run.update(flight.sample);
        flight.sample.time += Flight.DT;
        run.update(flight.sample);
        assertEquals(LessonRun.State.READY, run.state());
        flight.sample.armed = false;
        flight.sample.time += Flight.DT;
        run.update(flight.sample);
        flight.sample.armed = true;
        flight.sample.time += Flight.DT;
        run.update(flight.sample);
        assertEquals(LessonRun.State.RUNNING, run.state());
    }

    @Test
    void hoverCountsOnlyInsideTheBox() {
        Flight flight = Flight.of(lesson("hover"));
        flight.arm().moveLocal(0.0, 3.0, 0.0, 2.0).hold(3.0);
        assertEquals(LessonRun.State.RUNNING, flight.run.state());
        assertTrue(flight.run.inZone());
        double inside = flight.run.zoneTime();
        assertTrue(inside > 3.0 && inside < 4.0, "Zeit im Kasten " + inside);
        flight.moveLocal(0.0, 3.0, 5.0, 4.0).hold(3.0);
        assertFalse(flight.run.inZone());
        assertTrue(flight.run.zoneTime() < inside + 0.5, "außerhalb weitergezählt");
        flight.moveLocal(0.0, 3.0, 0.0, 4.0).hold(3.5);
        assertEquals(LessonRun.State.SUCCESS, flight.run.state());
        assertEquals(2, flight.run.result().stars());
    }

    @Test
    void landingNeedsThePadAndADisarmOnTheGround() {
        Flight good = Flight.of(lesson("land"));
        good.arm().moveLocal(0.0, 1.5, 0.0, 2.0).moveLocal(0.3, 1.5, 10.0, 6.0).moveLocal(0.3, 0.0, 10.0, 1.0).hold(0.3).disarm();
        assertEquals(LessonRun.State.SUCCESS, good.run.state(), String.valueOf(good.run.failure()));
        LessonRun.Result result = good.run.result();
        assertEquals(StarRule.Metric.PAD_DISTANCE, good.run.lesson().stars().metric());
        assertEquals(0.3, result.metric(), 0.02);
        assertEquals(3, result.stars());

        Flight off = Flight.of(lesson("land"));
        off.arm().moveLocal(0.0, 1.5, 0.0, 2.0).moveLocal(3.0, 1.5, 10.0, 6.0).moveLocal(3.0, 0.0, 10.0, 1.0).disarm();
        assertEquals(LessonRun.Failure.OUTSIDE_PAD, off.run.failure());

        Flight air = Flight.of(lesson("land"));
        air.arm().moveLocal(0.0, 1.5, 0.0, 2.0).moveLocal(0.0, 3.0, 10.0, 6.0).disarm();
        assertEquals(LessonRun.Failure.DISARMED, air.run.failure());
    }

    @Test
    void yawLessonTurnsToEachMarkerInOrder() {
        Lesson lesson = lesson("yaw");
        Flight flight = Flight.of(lesson);
        flight.arm().moveLocal(0.0, 3.0, 0.0, 2.0);
        TrainingFrame frame = flight.run.course().frame();
        flight.heading(frame.worldYaw(180.0)).hold(2.0);
        assertEquals(0, flight.run.markerStep(), "falscher Marker zählte");
        double[] headings = {90.0, 180.0, 270.0, 0.0};
        for (int i = 0; i < headings.length; i++) {
            flight.heading(frame.worldYaw(headings[i] + 5.0)).hold(1.2);
            if (i < headings.length - 1) {
                assertEquals(i + 1, flight.run.markerStep(), "Marker " + i);
            }
        }
        assertEquals(LessonRun.State.SUCCESS, flight.run.state(), String.valueOf(flight.run.failure()));
        assertEquals(4, flight.count(LessonRun.EventType.MARKER));
    }

    @Test
    void ringsCountInOrderWithMissedAndWrongWay() {
        Flight flight = Flight.of(lesson("straight"));
        flight.arm().moveLocal(0.0, 2.5, 0.0, 3.0).moveLocal(0.0, 2.5, 12.0, 10.0);
        assertEquals(1, flight.run.nextRing());
        flight.moveLocal(4.0, 2.5, 14.0, 10.0).moveLocal(4.0, 2.5, 18.0, 10.0).moveLocal(0.0, 2.5, 19.0, 10.0).moveLocal(0.0, 2.5, 26.0, 10.0);
        assertEquals(1, flight.run.nextRing());
        assertEquals(1, flight.count(LessonRun.EventType.MISSED));
        flight.moveLocal(0.0, 2.5, 14.0, 10.0);
        assertTrue(flight.count(LessonRun.EventType.WRONG_WAY) >= 1, "falsche Richtung nicht erkannt");
        flight.moveLocal(0.0, 2.5, 12.0, 10.0).moveLocal(0.0, 2.5, 42.0, 10.0);
        assertEquals(LessonRun.State.SUCCESS, flight.run.state(), String.valueOf(flight.run.failure()));
        assertEquals(5, flight.count(LessonRun.EventType.RING));
        assertEquals(5, flight.run.result().splits().length);
    }

    @Test
    void ringSplitsCompareWithTheReference() {
        Lesson lesson = lesson("straight");
        Course course = Course.flat(lesson, new TrainingFrame(10.0, 70.0, -5.0, 37.0F, 1.0), TrainingScale.FULL);
        double[] reference = {1.0, 1.5, 2.0, 2.5, 3.0};
        Flight flight = new Flight(new LessonRun(course, reference));
        flight.arm().followPath(10.0);
        assertEquals(LessonRun.State.SUCCESS, flight.run.state());
        List<LessonRun.Event> rings = flight.events.stream().filter(e -> e.type() == LessonRun.EventType.RING).toList();
        assertEquals(5, rings.size());
        for (int i = 0; i < rings.size(); i++) {
            assertTrue(rings.get(i).hasDelta(), "kein Abstand an Ring " + i);
            assertEquals(rings.get(i).value() - reference[i], rings.get(i).delta(), 1.0E-9);
        }
    }

    @Test
    void strictRingsFailOnAFrameTouch() {
        Flight flight = Flight.of(lesson("precision"));
        Ring first = flight.run.lesson().rings().getFirst();
        double edge = first.width() * 0.5 - Ring.FRAME * 0.5;
        flight.arm().moveLocal(edge, first.y(), 5.0, 3.0).moveLocal(edge, first.y(), 11.0, 8.0);
        assertEquals(LessonRun.State.FAILED, flight.run.state());
        assertEquals(LessonRun.Failure.TOUCHED, flight.run.failure());

        Flight loose = Flight.of(lesson("straight"));
        Ring ring = loose.run.lesson().rings().getFirst();
        double side = ring.width() * 0.5 - Ring.FRAME * 0.5;
        loose.arm().moveLocal(side, ring.y(), 5.0, 3.0).moveLocal(side, ring.y(), 11.0, 8.0);
        assertEquals(LessonRun.State.RUNNING, loose.run.state());
        assertTrue(loose.count(LessonRun.EventType.TOUCH) >= 1);
    }

    @Test
    void lapsUseTheRaceLapTracker() {
        Flight flight = Flight.of(lesson("short_course"));
        flight.arm().followPath(12.0);
        assertEquals(LessonRun.State.RUNNING, flight.run.state(), "nach einer Runde schon fertig");
        assertEquals(1, flight.run.lapsDone());
        List<double[]> path = flight.run.lesson().path();
        for (int i = 2; i < path.size(); i++) {
            double[] p = path.get(i);
            flight.moveLocal(p[0], p[1], p[2], 12.0);
        }
        assertEquals(LessonRun.State.SUCCESS, flight.run.state(), String.valueOf(flight.run.failure()));
        assertEquals(2, flight.count(LessonRun.EventType.LAP));
        assertEquals(2, flight.run.lapTimes().size());
        LessonRun.Event second = flight.events.stream().filter(e -> e.type() == LessonRun.EventType.LAP).toList().getLast();
        assertTrue(second.hasDelta(), "zweite Runde ohne Abstand zur ersten");
    }

    @Test
    void crashTimeoutAndLeavingFail() {
        Flight crash = Flight.of(lesson("straight"));
        crash.arm().moveLocal(0.0, 2.5, 4.0, 5.0).crash();
        assertEquals(LessonRun.Failure.CRASH, crash.run.failure());

        Flight slow = Flight.of(lesson("arm"));
        slow.arm().hold(61.0);
        assertEquals(LessonRun.Failure.TIMEOUT, slow.run.failure());

        Flight away = Flight.of(lesson("straight"));
        away.arm().moveLocal(0.0, 3.0, -40.0, 20.0);
        assertEquals(LessonRun.Failure.LEFT_AREA, away.run.failure());

        Flight disarm = Flight.of(lesson("straight"));
        disarm.arm().moveLocal(0.0, 2.5, 10.0, 8.0).disarm();
        assertEquals(LessonRun.Failure.DISARMED, disarm.run.failure());
    }

    @Test
    void flightAidsCapTheStars() {
        Flight flight = Flight.of(lesson("straight"));
        flight.arm().assisted(true).frame().assisted(false).followPath(14.0);
        assertEquals(LessonRun.State.SUCCESS, flight.run.state());
        assertTrue(flight.run.result().assisted());
        assertEquals(1, flight.run.result().stars());
    }

    @Test
    void endedRunFails() {
        Flight flight = Flight.of(lesson("hover"));
        flight.arm();
        flight.run.end();
        assertEquals(LessonRun.Failure.ENDED, flight.run.failure());
        Flight ready = Flight.of(lesson("hover"));
        ready.run.end();
        assertEquals(LessonRun.State.READY, ready.run.state());
        assertNull(ready.run.failure());
    }

    @Test
    void scaledCourseShrinksGeometryAndStretchesTime() {
        Lesson lesson = lesson("straight");
        TrainingFrame frame = new TrainingFrame(0.0, 64.0, 0.0, 90.0F, TrainingScale.SMALL.geometry());
        Course course = Course.flat(lesson, frame, TrainingScale.SMALL);
        GateGeometry first = course.rings().getFirst();
        assertEquals(Curriculum.RING * 0.6, first.width(), 1.0E-9);
        assertEquals(-8.0 * 0.6, first.centerX(), 1.0E-9);
        assertEquals(lesson.timeLimit() * TrainingScale.SMALL.time(), new LessonRun(course, null).timeLimit(), 1.0E-9);
        Flight flight = new Flight(new LessonRun(course, null));
        flight.arm().followPath(6.0);
        assertEquals(LessonRun.State.SUCCESS, flight.run.state(), String.valueOf(flight.run.failure()));
    }

    @Test
    void tricksCountOnlyInsideTheTrickArea() {
        Flight flight = Flight.of(lesson("flip"));
        flight.sample.throttle = 0.4;
        flight.arm().moveLocal(0.0, 2.0, -10.0, 4.0).hold(0.3).flip().hold(1.2);
        assertEquals(LessonRun.State.RUNNING, flight.run.state(), String.valueOf(flight.run.failure()));
        assertFalse(flight.run.inZone());
        assertEquals(0, flight.run.trickCount());
        assertEquals(1, flight.count(LessonRun.EventType.TRICK_OUTSIDE));
        assertEquals(0, flight.count(LessonRun.EventType.TRICK));
        flight.moveLocal(0.0, 9.0, 8.0, 6.0).hold(0.3);
        assertTrue(flight.run.inZone());
        flight.flip().hold(1.2);
        assertEquals(LessonRun.State.SUCCESS, flight.run.state(), String.valueOf(flight.run.failure()));
        assertEquals(1, flight.count(LessonRun.EventType.TRICK));
    }

    @Test
    void comboCountsOnlyTricksInsideTheTrickArea() {
        Flight flight = Flight.of(lesson("combo"));
        flight.sample.throttle = 0.4;
        flight.arm().moveLocal(0.0, 8.0, 10.0, 6.0).hold(0.3).flip().hold(0.8).flip().hold(0.8);
        flight.moveLocal(0.0, 8.0, -10.0, 30.0).flip().hold(1.0);
        assertEquals(LessonRun.State.RUNNING, flight.run.state(), String.valueOf(flight.run.failure()));
        assertEquals(2, flight.run.comboBest());
        assertEquals(0, flight.run.comboNow());
        flight.moveLocal(0.0, 8.0, 10.0, 30.0).hold(4.0);
        flight.flip().hold(0.8).flip().hold(0.8).flip().hold(1.0);
        assertEquals(LessonRun.State.SUCCESS, flight.run.state(), String.valueOf(flight.run.failure()));
        assertEquals(3, flight.run.comboBest());
    }

    @Test
    void finalExamChainsRingsTrickAndLanding() {
        Lesson exam = lesson("final_exam");
        assertEquals(List.of("rings", "tricks", "rings", "land"), exam.goals().stream().map(Goal::key).toList());
        Flight flight = Flight.of(exam);
        flight.arm().followPath(10.0);
        assertEquals(1, flight.run.goalIndex(), "Ringe vor dem Trick nicht erkannt");
        assertEquals(LessonRun.State.RUNNING, flight.run.state());
    }
}
