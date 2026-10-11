package de.lardtom.propwash.guide.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.training.TrainingRenderer;
import de.lardtom.propwash.guide.client.training.TrainingSession;
import de.lardtom.propwash.guide.training.Course;
import de.lardtom.propwash.guide.training.Goal;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.training.LessonRun;
import de.lardtom.propwash.guide.training.TrainingFrame;
import de.lardtom.propwash.guide.training.Zone;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.StickMath;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class TrainingCourseSelfTests {
    private static final String PLAN = "course_plan";
    private static final String INDEX = "course_index";
    private static final String POS = "course_pos";
    private static final String INVERTED = "course_inverted";
    private static final String GOAL = "course_goal";
    private static final String RUNS = "course_runs";
    private static final double FLIP_RATE = 650.0;
    private static final float FLIP_THROTTLE = 0.3F;
    private static final int HOLD_TICKS = 10;
    private static final SimSnapshot SNAPSHOT = new SimSnapshot();

    private enum Action {
        FLY,
        FLIP,
        LAND
    }

    private record Waypoint(double x, double y, double z, Action action) {
    }

    private TrainingCourseSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("guide: Flugtraining – zwei Runden durch die Gates (3.3) mit Rundenzeiten", 2400,
                ctx -> flight(ctx, "short_course", TrainingCourseSelfTests::checkLaps));
        SelfTest.clientStep("guide: Flugtraining – echter Flip im Trick-Bereich (4.1)", 1600,
                ctx -> flight(ctx, "flip", TrainingCourseSelfTests::checkTrick));
        SelfTest.clientStep("guide: Flugtraining – liegendes Tauchtor (5.1)", 1600,
                ctx -> flight(ctx, "dive_gate", TrainingCourseSelfTests::checkRings));
        SelfTest.clientStep("guide: Flugtraining – kleine Präzisionsringe ohne Berührung (5.2)", 1600,
                ctx -> flight(ctx, "precision", TrainingCourseSelfTests::checkRings));
        SelfTest.clientStep("guide: Flugtraining – Abschlussprüfung (5.4) mit Flip und Landung auf dem Pad in 56 Blöcken", 2400,
                ctx -> flight(ctx, "final_exam", TrainingCourseSelfTests::checkExam));
    }

    private static @Nullable String checkLaps(LessonRun run) {
        if (run.lapTimes().size() != 2) {
            return "Rundenzeiten " + run.lapTimes();
        }
        return TrainingRenderer.ringsDrawn() == 6 ? null : "Gates gezeichnet: " + TrainingRenderer.ringsDrawn();
    }

    private static @Nullable String checkTrick(LessonRun run) {
        if (run.lastTrick() == null) {
            return "kein Trick gezählt";
        }
        return TrainingRenderer.trickZonesDrawn() == 1 ? null : "Trick-Bereich nicht gezeichnet: " + TrainingRenderer.trickZonesDrawn();
    }

    private static @Nullable String checkRings(LessonRun run) {
        LessonRun.Result result = run.result();
        int rings = run.course().rings().size();
        if (result == null || result.splits().length != rings) {
            return "Zwischenzeiten " + (result == null ? "-" : result.splits().length) + " statt " + rings;
        }
        return null;
    }

    private static @Nullable String checkExam(LessonRun run) {
        String rings = checkRings(run);
        if (rings != null) {
            return rings;
        }
        String trick = checkTrick(run);
        if (trick != null) {
            return trick;
        }
        Course course = run.course();
        if (TrainingRenderer.padsDrawn() != 2) {
            return "Pads gezeichnet: " + TrainingRenderer.padsDrawn();
        }
        double distance = Math.hypot(course.padX(1) - course.frame().x(), course.padZ(1) - course.frame().z());
        if (Math.abs(distance - 56.0 * course.scale().geometry()) > 0.5) {
            return String.format(Locale.ROOT, "Lande-Pad %.1f Blöcke vom Start", distance);
        }
        return Math.abs(course.padHeight(1) - course.frame().y()) <= 1.0 ? null
                : "Lande-Pad nicht auf dem Boden: " + course.padHeight(1) + " statt " + course.frame().y();
    }

    private static List<Waypoint> plan(Course course) {
        Lesson lesson = course.lesson();
        TrainingFrame frame = course.frame();
        List<double[]> path = new ArrayList<>(lesson.path());
        for (Goal goal : lesson.goals()) {
            if (goal instanceof Goal.Laps loop) {
                int first = 1 + 3 * (loop.from() + 1);
                for (int lap = 1; lap < loop.laps(); lap++) {
                    path.addAll(lesson.path().subList(first, lesson.path().size()));
                }
            }
        }
        boolean lands = lesson.goals().getLast() instanceof Goal.Land;
        List<Waypoint> plan = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            double[] local = path.get(i);
            Action action = Action.FLY;
            for (Zone zone : lesson.zones()) {
                if (zone.kind() == Zone.Kind.TRICK && local[0] == zone.centerX() && local[1] == zone.centerY() && local[2] == zone.centerZ()) {
                    action = Action.FLIP;
                }
            }
            if (lands && i == path.size() - 1) {
                action = Action.LAND;
            }
            plan.add(new Waypoint(frame.worldX(local[0], local[2]), frame.worldY(local[1]), frame.worldZ(local[0], local[2]), action));
        }
        return plan;
    }

    @SuppressWarnings("unchecked")
    private static StepResult flight(SelfTest.StepContext ctx, String lessonId, Function<LessonRun, @Nullable String> check) {
        TrainingSession training = TrainingSession.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        int phase = TrainingSelfTests.phase(ctx);
        if (phase == 0) {
            BlockStart start = BlockStart.of(training);
            if (start == null) {
                return StepResult.fail("kein Trainingsplatz");
            }
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            TrainingSelfTests.teleport(start.x, start.y, start.z, start.yaw, 0.0, 0.0, 0.0);
            if (TrainingSelfTests.since(ctx) >= HOLD_TICKS) {
                TrainingSelfTests.phase(ctx, 1);
            }
            return StepResult.pending();
        }
        if (phase == 1) {
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            StepResult placed = TrainingSelfTests.placeStep(ctx, lessonId);
            if (placed.status() != StepResult.Status.PASS) {
                return placed;
            }
            Course course = training.course();
            state.put(PLAN, plan(course));
            state.put(INDEX, 1);
            state.put(POS, new double[]{course.frame().x(), course.frame().y() + 0.5, course.frame().z()});
            TrainingSelfTests.phase(ctx, 2);
            return StepResult.pending();
        }
        LessonRun run = training.run();
        Course course = training.course();
        if (run == null || course == null) {
            return StepResult.fail("kein Versuch");
        }
        if (phase == 2) {
            StepResult armed = TrainingSelfTests.armAndWait(ctx, run);
            if (armed.status() == StepResult.Status.PASS) {
                TrainingSelfTests.phase(ctx, 3);
                return StepResult.pending();
            }
            return armed;
        }
        if (phase == 6) {
            return back(ctx, training, session, course);
        }
        if (run.state() == LessonRun.State.SUCCESS) {
            String problem = check.apply(run);
            if (problem != null) {
                return StepResult.fail(lessonId + ": " + problem);
            }
            LessonRun.Result result = run.result();
            ctx.log(String.format(Locale.ROOT, "%s im Gelände geflogen: %d Sterne in %.2f s, Ringe %d, Runden %s, Trick %s", lessonId,
                    result == null ? 0 : result.stars(), result == null ? 0.0 : result.time(), course.rings().size(), run.lapTimes(), run.lastTrick()));
            state.put(RUNS, training.runs());
            TrainingSelfTests.phase(ctx, 6);
            return StepResult.pending();
        }
        if (run.state() != LessonRun.State.RUNNING) {
            return StepResult.fail(lessonId + " abgebrochen: " + run.failure() + " bei Ziel " + run.goalIndex() + " (" + describeGoal(run) + ")");
        }
        List<Waypoint> plan = (List<Waypoint>) state.get(PLAN);
        int index = (int) state.get(INDEX);
        double[] pos = (double[]) state.get(POS);
        return switch (phase) {
            case 3 -> fly(ctx, session, course, run, plan, index, pos);
            case 4 -> flip(ctx, session, run, plan, index, pos);
            case 5 -> land(ctx, session, course, run);
            default -> StepResult.fail("unbekannte Phase " + phase);
        };
    }

    private static String describeGoal(LessonRun run) {
        Goal goal = run.goal();
        return goal == null ? "-" : goal.key() + ", Ring " + run.nextRing() + ", Runde " + run.lapsDone() + ", Tricks " + run.trickCount();
    }

    private static StepResult fly(SelfTest.StepContext ctx, PilotSession session, Course course, LessonRun run, List<Waypoint> plan, int index,
                                  double[] pos) {
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, TrainingSelfTests.hoverThrottle()), true, StabMode.ANGLE);
        if (index >= plan.size()) {
            return TrainingSelfTests.since(ctx) > 300 ? StepResult.fail("Weg zu Ende, Ziel offen: " + describeGoal(run)) : StepResult.pending();
        }
        Waypoint target = plan.get(index);
        double dx = target.x() - pos[0];
        double dy = target.y() - 0.1 - pos[1];
        double dz = target.z() - pos[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double step = Math.min(TrainingSelfTests.STEP, length);
        if (length > 1.0E-6) {
            pos[0] += dx / length * step;
            pos[1] += dy / length * step;
            pos[2] += dz / length * step;
        }
        double yaw = Math.hypot(dx, dz) > 0.05 ? TrainingFrame.yawOf(dx, dz) : course.frame().yaw();
        double speed = TrainingSelfTests.STEP * 20.0;
        boolean arrived = length <= TrainingSelfTests.STEP;
        boolean stop = arrived && target.action() != Action.FLY;
        TrainingSelfTests.teleport(pos[0], pos[1], pos[2], yaw, stop || length <= 1.0E-6 ? 0.0 : dx / length * speed,
                stop || length <= 1.0E-6 ? 0.0 : dy / length * speed, stop || length <= 1.0E-6 ? 0.0 : dz / length * speed);
        if (arrived) {
            if (target.action() == Action.FLIP) {
                ctx.state().put(GOAL, run.goalIndex());
                ctx.state().put(INVERTED, Boolean.FALSE);
                TrainingSelfTests.phase(ctx, 4);
                return StepResult.pending();
            }
            if (target.action() == Action.LAND) {
                TrainingSelfTests.phase(ctx, 5);
                return StepResult.pending();
            }
            ctx.state().put(INDEX, index + 1);
        }
        return TrainingSelfTests.since(ctx) > 2000 ? StepResult.fail("Flug zu langsam: " + describeGoal(run)) : StepResult.pending();
    }

    private static StepResult flip(SelfTest.StepContext ctx, PilotSession session, LessonRun run, List<Waypoint> plan, int index, double[] pos) {
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        if (sim == null || drone == null) {
            return StepResult.fail("Sitzung verloren");
        }
        int since = TrainingSelfTests.since(ctx);
        Waypoint target = plan.get(index);
        if (since < HOLD_TICKS) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, TrainingSelfTests.hoverThrottle()), true, StabMode.ANGLE);
            TrainingSelfTests.teleport(target.x(), target.y() - 0.1, target.z(), run.course().frame().yaw(), 0.0, 0.0, 0.0);
            return StepResult.pending();
        }
        sim.copyStateTo(SNAPSHOT);
        double upY = 1.0 - 2.0 * (SNAPSHOT.qx * SNAPSHOT.qx + SNAPSHOT.qz * SNAPSHOT.qz);
        boolean inverted = (boolean) ctx.state().get(INVERTED);
        if (upY < -0.5) {
            inverted = true;
            ctx.state().put(INVERTED, Boolean.TRUE);
        }
        boolean rotating = !(inverted && upY > 0.7);
        if (rotating && since < HOLD_TICKS + 60) {
            float pitch = (float) StickMath.rateToStick(FLIP_RATE, sim.config().rates, Axis.PITCH);
            session.setTestInput(new StickPositions(0.0F, pitch, 0.0F, FLIP_THROTTLE), true, StabMode.ACRO);
            return StepResult.pending();
        }
        if (rotating) {
            return StepResult.fail("Flip dreht nicht durch: oben " + String.format(Locale.ROOT, "%.2f", upY) + ", kopfüber gewesen " + inverted);
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, TrainingSelfTests.hoverThrottle()), true, StabMode.ANGLE);
        if (run.goalIndex() > (int) ctx.state().get(GOAL) || run.state() == LessonRun.State.SUCCESS) {
            pos[0] = drone.getX();
            pos[1] = drone.getY();
            pos[2] = drone.getZ();
            ctx.state().put(INDEX, index + 1);
            TrainingSelfTests.phase(ctx, 3);
            return StepResult.pending();
        }
        return since > HOLD_TICKS + 160 ? StepResult.fail("Flip im Trick-Bereich nicht gezählt: " + describeGoal(run) + ", letzter Trick "
                + run.lastTrick() + ", im Bereich " + run.inZone()) : StepResult.pending();
    }

    private static StepResult land(SelfTest.StepContext ctx, PilotSession session, Course course, LessonRun run) {
        Goal goal = run.goal();
        if (!(goal instanceof Goal.Land land)) {
            return StepResult.fail("Landen ohne Lande-Ziel: " + describeGoal(run));
        }
        int since = TrainingSelfTests.since(ctx);
        double x = course.padX(land.pad());
        double z = course.padZ(land.pad());
        double y = course.padHeight(land.pad());
        TrainingSelfTests.teleport(x, since < HOLD_TICKS ? y + 0.6 : y, z, course.frame().yaw(), 0.0, 0.0, 0.0);
        boolean armed = since < HOLD_TICKS * 2;
        session.setTestInput(armed ? new StickPositions(0.0F, 0.0F, 0.0F, 0.1F) : StickPositions.IDLE, armed, StabMode.ANGLE);
        return since > 300 ? StepResult.fail("Landung auf dem Pad nicht erkannt: " + describeGoal(run) + ", Abstand " + run.padDistance())
                : StepResult.pending();
    }

    private static StepResult back(SelfTest.StepContext ctx, TrainingSession training, PilotSession session, Course course) {
        session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        TrainingFrame frame = course.frame();
        if (TrainingSelfTests.since(ctx) < 3) {
            TrainingSelfTests.teleport(frame.x(), frame.y(), frame.z(), frame.yaw(), 0.0, 0.0, 0.0);
        }
        LessonRun next = training.run();
        if (training.runs() > (int) ctx.state().get(RUNS) && next != null && next.state() == LessonRun.State.READY) {
            return StepResult.pass();
        }
        return TrainingSelfTests.since(ctx) > 300 ? StepResult.fail("kein neuer Versuch am Start: " + training.phase()) : StepResult.pending();
    }

    private record BlockStart(double x, double y, double z, double yaw) {
        static @Nullable BlockStart of(TrainingSession training) {
            Course course = training.course();
            if (course != null) {
                return new BlockStart(course.frame().x(), course.frame().y(), course.frame().z(), course.frame().yaw());
            }
            BlockPos area = TrainingSelfTests.area();
            return area == null ? null : new BlockStart(area.getX() + 0.5, area.getY(), area.getZ() + 0.5, 180.0);
        }
    }
}
