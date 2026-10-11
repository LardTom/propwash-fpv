package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.StabMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LessonAutopilotTest {
    private static final Set<String> RING_LESSONS = Set.of("straight", "altitude", "gate_line", "low_flight");

    static SimFlight fly(DroneBuild build, Lesson lesson, double cruise) {
        SimFlight flight = new SimFlight(build, lesson);
        new Autopilot(flight, cruise).fly(lesson.timeLimit() * flight.scale.time() + 5.0);
        return flight;
    }

    private static String describe(SimFlight flight) {
        LessonRun run = flight.run;
        return String.format(Locale.ROOT, "%s: %s %s Ziel %d t=%.1f s Sterne %s Absturz %d", run.lesson().id(), run.state(), run.failure(),
                run.goalIndex(), run.elapsed(), run.result() == null ? "-" : run.result().stars(), flight.sample.crashCount);
    }

    private static List<String> failures(DroneBuild build, double cruise, boolean tricks) {
        List<String> failed = new ArrayList<>();
        for (Lesson lesson : Curriculum.lessons()) {
            if (!tricks && lesson.chapter().equals(Curriculum.FREESTYLE) && !lesson.id().equals("flip") && !lesson.id().equals("roll")) {
                continue;
            }
            SimFlight flight = fly(build, lesson, cruise);
            if (flight.run.state() != LessonRun.State.SUCCESS || flight.sample.crashCount > 0) {
                failed.add(build.frame().key() + " " + describe(flight));
            }
        }
        return failed;
    }

    @Test
    void everyLessonCanBeFlownWithTheRealFreestyleSim() {
        List<String> failed = failures(DroneBuild.FREESTYLE, 7.0, true);
        assertTrue(failed.isEmpty(), String.join("\n", failed));
    }

    @Test
    void smallDronesFlyTheScaledCourses() {
        List<String> failed = new ArrayList<>(failures(DroneBuild.WHOOP, 5.0, false));
        failed.addAll(failures(DroneBuild.CINEWHOOP, 5.0, false));
        assertTrue(failed.isEmpty(), String.join("\n", failed));
    }

    @Test
    void fasterPilotsEarnMoreStars() {
        for (String id : RING_LESSONS) {
            Lesson lesson = Curriculum.lesson(id);
            SimFlight slow = fly(DroneBuild.FREESTYLE, lesson, 4.0);
            SimFlight fast = fly(DroneBuild.FREESTYLE, lesson, 10.0);
            assertEquals(LessonRun.State.SUCCESS, slow.run.state(), describe(slow));
            assertEquals(LessonRun.State.SUCCESS, fast.run.state(), describe(fast));
            assertTrue(slow.run.result().stars() < 3, "langsam schon drei Sterne: " + describe(slow));
            assertEquals(3, fast.run.result().stars(), "schnell keine drei Sterne: " + describe(fast));
            assertTrue(fast.run.result().time() < slow.run.result().time());
        }
    }

    @Test
    void theNoseOfTheRealSimPointsAlongTheCourse() {
        for (float yaw : new float[]{0.0F, 90.0F, 180.0F, 233.0F}) {
            SimFlight flight = new SimFlight(DroneBuild.FREESTYLE, Curriculum.lesson("straight"), yaw);
            assertEquals(0.0, TrainingFrame.delta(yaw, flight.sample.yaw()), 0.5, "Nase nach dem Platzieren");
            flight.input.armSwitch = true;
            flight.input.mode = StabMode.ANGLE;
            for (int i = 0; i < 30; i++) {
                flight.rates(0.0, 0.0, 0.0, 0.0);
                flight.input.mode = StabMode.ANGLE;
                flight.step();
            }
            double x0 = flight.sample.x;
            double z0 = flight.sample.z;
            for (int i = 0; i < 180; i++) {
                flight.input.mode = StabMode.ANGLE;
                flight.input.roll = 0.0;
                flight.input.yaw = 0.0;
                flight.input.pitch = i < 20 ? 0.0 : 0.45;
                flight.input.throttle = Math.min(0.75, flight.hover() * 1.35);
                flight.step();
            }
            double moved = TrainingFrame.yawOf(flight.sample.x - x0, flight.sample.z - z0);
            assertTrue(Math.hypot(flight.sample.x - x0, flight.sample.z - z0) > 2.0, "kaum bewegt");
            assertEquals(0.0, TrainingFrame.delta(yaw, moved), 20.0, "Nick vorwärts fliegt nicht in Richtung der Nase (" + yaw + ")");
            TrainingFrame frame = flight.run.course().frame();
            assertTrue(frame.localZ(flight.sample.x, flight.sample.z) > 2.0, "Kurs liegt nicht vor der Drohne");
        }
    }

    @Test
    void landingPrecisionDecidesTheStars() {
        SimFlight flight = fly(DroneBuild.FREESTYLE, Curriculum.lesson("land"), 6.0);
        assertEquals(LessonRun.State.SUCCESS, flight.run.state(), describe(flight));
        assertTrue(flight.run.result().metric() < 0.4, "Abstand " + flight.run.result().metric());
        assertEquals(3, flight.run.result().stars());
    }
}
