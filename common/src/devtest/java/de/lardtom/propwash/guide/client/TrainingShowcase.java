package de.lardtom.propwash.guide.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.training.TrainingScreen;
import de.lardtom.propwash.guide.client.training.TrainingSession;
import de.lardtom.propwash.guide.client.training.TrainingStore;
import de.lardtom.propwash.guide.training.Course;
import de.lardtom.propwash.guide.training.Curriculum;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.training.LessonRun;
import de.lardtom.propwash.guide.training.TrainingFrame;
import de.lardtom.propwash.guide.training.TrainingProgress;
import de.lardtom.propwash.guide.training.TrainingScale;
import de.lardtom.propwash.guide.training.Zone;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TrainingShowcase {
    private static final String FUTURE = "future";
    private static final String STAGE = "training_stage";
    private static final String MARK = "training_mark";
    private static final String INDEX = "training_index";
    private static final String POS = "training_pos";
    private static final String LOOK = "training_look";
    private static final String PILOT = "training_pilot";

    private TrainingShowcase() {
    }

    public static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        Screenshots.step("bildschirmfotos: Flugtraining – Fenster mit Kapiteln und Lektionen", "training_screen", 20, new ScreenScene());
        Screenshots.step("bildschirmfotos: Flugtraining – Schweben im Kasten (FPV)", "training_hover_fpv", 8, new HoverScene());
        Screenshots.step("bildschirmfotos: Flugtraining – Säulen beim Gieren (FPV)", "training_yaw_fpv", 8, new YawScene());
        Screenshots.step("bildschirmfotos: Flugtraining – Ringe mit Ideallinie (FPV)", "training_rings_fpv", 8, new RingsScene());
        Screenshots.step("bildschirmfotos: Flugtraining – Ergebnis mit Sternen (FPV)", "training_result_fpv", 6, new ResultScene());
        Screenshots.step("bildschirmfotos: Flugtraining – Kurs der Acht im Sichtflug", "training_course_los", 10, new CourseScene());
        Screenshots.step("bildschirmfotos: Flugtraining – Landung auf dem Pad im Sichtflug", "training_landing_los", 10, new LandingScene());
        SelfTest.switchStep("bildschirmfotos: Flugtraining – Sprache Deutsch", "bildschirmfotos: Flugtraining – Sprache zurücksetzen", 1200,
                ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step("bildschirmfotos: Flugtraining – Fenster deutsch", "training_screen_de", 20, new ScreenScene());
        Screenshots.step("bildschirmfotos: Flugtraining – Schweben im Kasten deutsch", "training_hover_fpv_de", 8, new HoverScene());
        SelfTest.clientStep("bildschirmfotos: Flugtraining – Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        Screenshots.step("bildschirmfotos: Flugtraining – Runden durch die Gates (FPV)", "training_gates_fpv", 8, new GatesScene());
        Screenshots.step("bildschirmfotos: Flugtraining – Trick-Bereich (FPV)", "training_trick_zone_fpv", 8, new TrickZoneScene());
        Screenshots.step("bildschirmfotos: Flugtraining – liegendes Tauchtor im Sichtflug", "training_dive_gate_los", 10,
                new OverviewScene("dive_gate", new double[]{0.0, 11.0, 16.0}, new double[]{-8.0, 17.0, 6.0}, new double[]{0.0, 6.0, 20.0}));
        Screenshots.step("bildschirmfotos: Flugtraining – Präzisionsringe (FPV)", "training_precision_fpv", 8, new PrecisionScene());
        Screenshots.step("bildschirmfotos: Flugtraining – Abschlussprüfung im Sichtflug", "training_final_exam_los", 10,
                new OverviewScene("final_exam", new double[]{0.0, 3.0, 6.0}, new double[]{-12.0, 14.0, 4.0}, new double[]{0.0, 7.0, 32.0}));
        SelfTest.clientStep("bildschirmfotos: Flugtraining – Aufräumen", 400, TrainingShowcase::cleanupStep);
    }

    private static int stage(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(STAGE, 0);
    }

    private static void stage(SelfTest.StepContext ctx, int next) {
        ctx.state().put(STAGE, next);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static void progress() {
        TrainingStore.forget();
        TrainingProgress progress = TrainingStore.progress();
        int[] basics = {3, 2, 3, 2};
        List<Lesson> first = Curriculum.chapter(Curriculum.BASICS).lessons();
        for (int i = 0; i < first.size(); i++) {
            progress.record(first.get(i).id()).stars = basics[i];
            progress.record(first.get(i).id()).attempts = 2 + i;
            progress.record(first.get(i).id()).completions = 1 + i % 2;
        }
        progress.record("straight").stars = 3;
        progress.record("straight").attempts = 4;
        progress.record("straight").completions = 2;
        progress.record("altitude").stars = 1;
        progress.record("altitude").attempts = 3;
        progress.record("altitude").completions = 1;
        Lesson turns = Curriculum.lesson("turns");
        progress.record(turns.id()).attempts = 3;
        double time = 10.84;
        progress.complete(turns, TrainingScale.FULL, new LessonRun.Result(turns.stars().stars(time, TrainingScale.FULL, false), time, time, false,
                new double[]{1.9, 3.4, 4.9, 6.5, 8.1, 9.9}));
        TrainingStore.save();
    }

    private static final class ScreenScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof TrainingScreen screen)) {
                progress();
                mc.gui.setScreen(new TrainingScreen(null, Curriculum.lesson("turns")));
                return StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            return screen.frames() > 2 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private abstract static class FlightScene implements Screenshots.Scene {
        private final String lesson;
        private final boolean fpv;

        FlightScene(String lesson, boolean fpv) {
            this.lesson = lesson;
            this.fpv = fpv;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            PilotSession session = PilotSession.INSTANCE;
            TrainingSession training = TrainingSession.INSTANCE;
            Map<String, Object> state = ctx.state();
            switch (stage(ctx)) {
                case 0 -> {
                    mc.gui.setScreen(null);
                    session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                    boolean reuse = session.isActive() && session.isFpv() == this.fpv && session.drone() != null
                            && session.drone().getId() == TrainingSelfTests.droneId();
                    if (reuse) {
                        stage(ctx, 2);
                    } else {
                        state.put(FUTURE, TrainingSelfTests.startFlight(ctx, DroneBuild.FREESTYLE, this.fpv));
                        stage(ctx, 1);
                    }
                    return StepResult.pending();
                }
                case 1 -> {
                    StepResult server = TrainingSelfTests.awaitServer(ctx, "Drohne starten");
                    if (server.status() != StepResult.Status.PASS) {
                        return server;
                    }
                    if (!session.isActive() || session.isFpv() != this.fpv) {
                        return since(ctx) > 400 ? StepResult.fail("keine Sitzung im richtigen Modus") : StepResult.pending();
                    }
                    stage(ctx, 2);
                    return StepResult.pending();
                }
                case 2 -> {
                    session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                    BlockPos area = TrainingSelfTests.area();
                    if (area == null) {
                        return StepResult.fail("kein Trainingsplatz");
                    }
                    TrainingSelfTests.teleport(area.getX() + 0.5, area.getY(), area.getZ() + 0.5, 180.0, 0.0, 0.0, 0.0);
                    if (since(ctx) < 12) {
                        return StepResult.pending();
                    }
                    training.select(Curriculum.lesson(this.lesson));
                    stage(ctx, 3);
                    return StepResult.pending();
                }
                case 3 -> {
                    session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                    if (training.phase() != TrainingSession.Phase.ACTIVE || training.run() == null
                            || training.run().state() != LessonRun.State.READY) {
                        return since(ctx) > 700 ? StepResult.fail("Kurs nicht bereit: " + training.phase() + " " + training.placement())
                                : StepResult.pending();
                    }
                    stage(ctx, 4);
                    return StepResult.pending();
                }
                case 4 -> {
                    LessonRun run = training.run();
                    if (run == null) {
                        return StepResult.fail("kein Versuch");
                    }
                    StepResult armed = TrainingSelfTests.armAndWait(ctx, run);
                    if (armed.status() == StepResult.Status.PASS) {
                        state.put(INDEX, 1);
                        Course course = training.course();
                        state.put(POS, new double[]{course.frame().x(), course.frame().y() + 0.5, course.frame().z()});
                        stage(ctx, 5);
                        return StepResult.pending();
                    }
                    return armed;
                }
                default -> {
                    LessonRun run = training.run();
                    Course course = training.course();
                    if (run == null || course == null) {
                        return StepResult.fail("kein Versuch");
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, TrainingSelfTests.hoverThrottle()), true, StabMode.ANGLE);
                    Screenshots.clearOverlays(mc);
                    return this.script(ctx, run, course);
                }
            }
        }

        abstract StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course);

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Course course = TrainingSession.INSTANCE.course();
            double[] pose = (double[]) ctx.state().get("pose");
            if (course != null && pose != null) {
                PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, TrainingSelfTests.hoverThrottle()), true, StabMode.ANGLE);
                TrainingSelfTests.teleport(pose[0], pose[1], pose[2], pose[3], 0.0, 0.0, 0.0);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        }

        static void pose(SelfTest.StepContext ctx, TrainingFrame frame, double lx, double ly, double lz, double localYaw) {
            double[] pose = {frame.worldX(lx, lz), frame.worldY(ly), frame.worldZ(lx, lz), frame.worldYaw(localYaw)};
            ctx.state().put("pose", pose);
            TrainingSelfTests.teleport(pose[0], pose[1], pose[2], pose[3], 0.0, 0.0, 0.0);
        }

        static boolean fly(SelfTest.StepContext ctx, Course course, int stopIndex, double stopShort) {
            List<double[]> path = TrainingSelfTests.worldPath(course);
            int index = (int) ctx.state().get(INDEX);
            double[] pos = (double[]) ctx.state().get(POS);
            if (index >= Math.min(stopIndex, path.size())) {
                return true;
            }
            double[] target = path.get(index);
            double dx = target[0] - pos[0];
            double dy = target[1] - 0.1 - pos[1];
            double dz = target[2] - pos[2];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double limit = index + 1 >= stopIndex ? Math.max(0.0, length - stopShort) : length;
            double step = Math.min(TrainingSelfTests.STEP, limit);
            if (length > 1.0E-6) {
                pos[0] += dx / length * step;
                pos[1] += dy / length * step;
                pos[2] += dz / length * step;
            }
            if (limit <= TrainingSelfTests.STEP) {
                ctx.state().put(INDEX, index + 1);
            }
            double yaw = Math.hypot(dx, dz) > 0.05 ? TrainingFrame.yawOf(dx, dz) : course.frame().yaw();
            double speed = TrainingSelfTests.STEP * 20.0;
            TrainingSelfTests.teleport(pos[0], pos[1], pos[2], yaw, length > 1.0E-6 ? dx / length * speed : 0.0,
                    length > 1.0E-6 ? dy / length * speed : 0.0, length > 1.0E-6 ? dz / length * speed : 0.0);
            ctx.state().put("pose", new double[]{pos[0], pos[1], pos[2], yaw});
            return false;
        }
    }

    private static final class HoverScene extends FlightScene {
        HoverScene() {
            super("hover", true);
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            Zone zone = course.lesson().zones().getFirst();
            pose(ctx, course.frame(), zone.centerX() - 0.3, zone.centerY() - 0.2, zone.centerZ() - 0.7, 22.0);
            if (run.state() != LessonRun.State.RUNNING) {
                return StepResult.fail("Schweben läuft nicht: " + run.state() + " " + run.failure());
            }
            return run.zoneTime() > 3.0 ? StepResult.pass() : since(ctx) > 300 ? StepResult.fail("keine Zeit im Kasten") : StepResult.pending();
        }
    }

    private static final class YawScene extends FlightScene {
        YawScene() {
            super("yaw", true);
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            Zone zone = course.lesson().zones().getFirst();
            pose(ctx, course.frame(), zone.centerX(), zone.centerY(), zone.centerZ(), 48.0);
            if (run.state() != LessonRun.State.RUNNING) {
                return StepResult.fail("Gieren läuft nicht: " + run.state());
            }
            return since(ctx) > 30 ? StepResult.pass() : StepResult.pending();
        }
    }

    private static final class RingsScene extends FlightScene {
        RingsScene() {
            super("turns", true);
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            if (run.state() != LessonRun.State.RUNNING) {
                return StepResult.fail("Ringe abgebrochen: " + run.failure());
            }
            if (!fly(ctx, course, 5, 3.5)) {
                return since(ctx) > 800 ? StepResult.fail("Anflug zu langsam") : StepResult.pending();
            }
            return run.nextRing() == 1 ? StepResult.pass() : StepResult.fail("Ring 1 nicht gezählt: " + run.nextRing());
        }
    }

    private static final class ResultScene extends FlightScene {
        ResultScene() {
            super("straight", true);
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            if (run.state() == LessonRun.State.SUCCESS) {
                double[] pose = (double[]) ctx.state().get("pose");
                if (pose != null) {
                    pose[3] = TrainingFrame.wrap(course.frame().yaw() + 165.0);
                    pose[1] += 1.5;
                }
                return StepResult.pass();
            }
            if (run.state() != LessonRun.State.RUNNING) {
                return StepResult.fail("Lauf abgebrochen: " + run.failure());
            }
            fly(ctx, course, Integer.MAX_VALUE, 0.0);
            return since(ctx) > 900 ? StepResult.fail("Ziel nicht erreicht") : StepResult.pending();
        }
    }

    private static final class CourseScene extends FlightScene {
        CourseScene() {
            super("figure_eight", false);
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            pose(ctx, course.frame(), 0.0, 3.2, 4.0, 0.0);
            return since(ctx) > 30 ? StepResult.pass() : StepResult.pending();
        }
    }

    private static final class LandingScene extends FlightScene {
        LandingScene() {
            super("land", false);
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            pose(ctx, course.frame(), 0.3, 1.1, 9.4, 0.0);
            return since(ctx) > 30 ? StepResult.pass() : StepResult.pending();
        }
    }

    private static final class GatesScene extends FlightScene {
        GatesScene() {
            super("short_course", true);
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            if (run.state() != LessonRun.State.RUNNING) {
                return StepResult.fail("Runden abgebrochen: " + run.failure());
            }
            if (!fly(ctx, course, 5, 2.0)) {
                return since(ctx) > 900 ? StepResult.fail("Anflug zu langsam") : StepResult.pending();
            }
            return run.lapsStarted() ? StepResult.pass() : StepResult.fail("Runde nicht gestartet");
        }
    }

    private static final class TrickZoneScene extends FlightScene {
        TrickZoneScene() {
            super("flip", true);
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            pose(ctx, course.frame(), 1.5, 5.0, -3.0, -8.0);
            if (run.state() != LessonRun.State.RUNNING) {
                return StepResult.fail("Trick-Lektion läuft nicht: " + run.state());
            }
            return since(ctx) > 30 ? StepResult.pass() : StepResult.pending();
        }
    }

    private static final class PrecisionScene extends FlightScene {
        PrecisionScene() {
            super("precision", true);
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            pose(ctx, course.frame(), 0.0, 2.3, 1.5, 0.0);
            if (run.state() != LessonRun.State.RUNNING) {
                return StepResult.fail("Präzision läuft nicht: " + run.state());
            }
            return since(ctx) > 30 ? StepResult.pass() : StepResult.pending();
        }
    }

    private static final class OverviewScene extends FlightScene {
        private final double[] drone;
        private final double[] pilot;
        private final double[] target;

        OverviewScene(String lesson, double[] drone, double[] pilot, double[] target) {
            super(lesson, false);
            this.drone = drone.clone();
            this.pilot = pilot.clone();
            this.target = target.clone();
        }

        @Override
        StepResult script(SelfTest.StepContext ctx, LessonRun run, Course course) {
            TrainingFrame frame = course.frame();
            pose(ctx, frame, this.drone[0], this.drone[1], this.drone[2], 0.0);
            if (!ctx.state().containsKey(PILOT)) {
                double x = frame.worldX(this.pilot[0], this.pilot[2]);
                double y = frame.worldY(this.pilot[1]);
                double z = frame.worldZ(this.pilot[0], this.pilot[2]);
                ctx.state().put(PILOT, ctx.onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.level(), x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);
                }));
            }
            ctx.state().put(LOOK, this.target.clone());
            look(frame, this.target[0], this.target[1], this.target[2]);
            return since(ctx) > 40 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            super.hold(ctx);
            Course course = TrainingSession.INSTANCE.course();
            double[] look = (double[]) ctx.state().get(LOOK);
            if (course != null && look != null) {
                look(course.frame(), look[0], look[1], look[2]);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            super.finish(ctx);
            ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            });
        }
    }

    private static void look(TrainingFrame frame, double lx, double ly, double lz) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        double dx = frame.worldX(lx, lz) - eye.x;
        double dy = frame.worldY(ly) - eye.y;
        double dz = frame.worldZ(lx, lz) - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            TrainingSession.INSTANCE.stop();
            PilotSession.INSTANCE.clearTestInput();
            Minecraft.getInstance().gui.setScreen(null);
            state.put(FUTURE, ctx.onServer(server -> {
                TrainingSelfTests.discard(server);
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                TrainingSelfTests.returnPlayer(player);
            }));
            return StepResult.pending();
        }
        StepResult server = TrainingSelfTests.awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone != null) {
            return ctx.tick() > 300 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
        }
        TrainingStore.forget();
        return StepResult.pass();
    }
}
