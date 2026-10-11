package de.lardtom.propwash.guide.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.screen.SceneIndexScreen;
import de.lardtom.propwash.guide.client.training.TrainingHud;
import de.lardtom.propwash.guide.client.training.TrainingRenderer;
import de.lardtom.propwash.guide.client.training.TrainingScreen;
import de.lardtom.propwash.guide.client.training.TrainingSession;
import de.lardtom.propwash.guide.client.training.TrainingStore;
import de.lardtom.propwash.guide.training.Chapter;
import de.lardtom.propwash.guide.training.Course;
import de.lardtom.propwash.guide.training.Curriculum;
import de.lardtom.propwash.guide.training.Goal;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.training.LessonRun;
import de.lardtom.propwash.guide.training.Placement;
import de.lardtom.propwash.guide.training.TrainingFrame;
import de.lardtom.propwash.guide.training.TrainingProgress;
import de.lardtom.propwash.guide.training.Zone;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class TrainingSelfTests {
    public static final int AREA_OFFSET = 320;
    public static final double STEP = 0.45;
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final String INDEX = "index";
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final AtomicReference<BlockPos> AREA = new AtomicReference<>();
    private static final AtomicReference<double[]> RETURN = new AtomicReference<>();
    private static final List<BlockPos> WALL = new ArrayList<>();
    private static final SimSnapshot SNAPSHOT = new SimSnapshot();

    private TrainingSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("guide: Flugtraining – Lehrplan, Texte und Freischaltung", 1, TrainingSelfTests::textsStep);
        SelfTest.clientStep("guide: Flugtraining – Fenster mit Kapiteln und Lektionen", 300, TrainingSelfTests::screenStep);
        SelfTest.clientStep("guide: Flugtraining – Drohne an einem freien Ort starten", 600, TrainingSelfTests::startStep);
        SelfTest.clientStep("guide: Flugtraining – Kurs erscheint an der Drohne", 800, ctx -> placeStep(ctx, "hover"));
        SelfTest.clientStep("guide: Flugtraining – Schweben im Kasten mit echter Drohne", 900, TrainingSelfTests::hoverStep);
        SelfTest.clientStep("guide: Flugtraining – Ringe geradeaus, Zwischenzeiten gespeichert", 1400, TrainingSelfTests::ringsStep);
        SelfTest.clientStep("guide: Flugtraining – Abschalten in der Luft beendet den Versuch", 900, TrainingSelfTests::disarmStep);
        TrainingCourseSelfTests.register();
        SelfTest.clientStep("guide: Flugtraining – kein Platz für den Kurs wird erkannt", 1200, TrainingSelfTests::blockedStep);
        SelfTest.clientStep("guide: Flugtraining – Taste M öffnet im Flug das Training", 200, TrainingSelfTests::keyStep);
        SelfTest.clientStep("guide: Flugtraining – Aufräumen", 400, TrainingSelfTests::cleanupStep);
        SelfTest.afterLeaveStep("guide: Flugtraining nach dem Verlassen zurückgesetzt", 40, ctx -> {
            TrainingSession session = TrainingSession.INSTANCE;
            return StepResult.check(session.lesson() == null && session.course() == null && session.phase() == TrainingSession.Phase.IDLE,
                    "Training nach dem Verlassen noch aktiv: " + session.phase());
        });
    }

    public static @Nullable BlockPos area() {
        return AREA.get();
    }

    public static int droneId() {
        return DRONE.get();
    }

    static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    static void phase(SelfTest.StepContext ctx, int next) {
        ctx.state().put(PHASE, next);
        ctx.state().put(MARK, ctx.tick());
    }

    static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    static StepResult awaitServer(SelfTest.StepContext ctx, String what) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return StepResult.fail(what + ": keine Server-Aktion");
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(what + ": " + future.exceptionNow());
        }
        return future.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult textsStep(SelfTest.StepContext ctx) {
        Language language = Language.getInstance();
        List<String> missing = new ArrayList<>();
        for (Chapter chapter : Curriculum.chapters()) {
            for (String key : List.of(chapter.titleKey(), chapter.descriptionKey())) {
                if (!language.has(key)) {
                    missing.add(key);
                }
            }
            for (Lesson lesson : chapter.lessons()) {
                for (String key : lesson.translationKeys()) {
                    if (!language.has(key)) {
                        missing.add(key);
                    }
                }
            }
        }
        for (String key : List.of("propwash.guide.training.title", "propwash.guide.training.open", "propwash.guide.training.fly",
                "propwash.guide.training.hud.ready", "propwash.guide.training.result.success", "propwash.guide.training.result.failed")) {
            if (!language.has(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            return StepResult.fail("Texte fehlen: " + missing);
        }
        TrainingStore.forget();
        TrainingProgress progress = TrainingStore.progress();
        if (!Curriculum.unlocked(Curriculum.lesson("arm"), progress) || Curriculum.unlocked(Curriculum.lesson("hover"), progress)) {
            return StepResult.fail("Freischaltung am Anfang falsch");
        }
        if (!TrainingStore.CONFIG.path().getFileName().toString().equals("flight_training.json")) {
            return StepResult.fail("Speicherort " + TrainingStore.CONFIG.path());
        }
        return StepResult.pass();
    }

    private static @Nullable FlatButton button(List<? extends GuiEventListener> children, String key) {
        String label = Component.translatable(key).getString();
        for (GuiEventListener child : children) {
            if (child instanceof FlatButton button && button.getMessage().getString().equals(label)) {
                return button;
            }
        }
        return null;
    }

    private static StepResult screenStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        switch (phase(ctx)) {
            case 0 -> {
                GuideOpener.openIndex(null, ItemStack.EMPTY);
                phase(ctx, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (!(mc.gui.screen() instanceof SceneIndexScreen index) || index.frames() < 2) {
                    return since(ctx) > 100 ? StepResult.fail("Flugschul-Übersicht nicht offen") : StepResult.pending();
                }
                FlatButton open = button(index.children(), "propwash.guide.training.open");
                if (open == null) {
                    return StepResult.fail("Übersicht ohne Knopf „Flugtraining“");
                }
                open.press();
                phase(ctx, 2);
                return StepResult.pending();
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof TrainingScreen screen) || screen.frames() < 3) {
                    return since(ctx) > 100 ? StepResult.fail("Training-Fenster nicht offen: " + mc.gui.screen()) : StepResult.pending();
                }
                if (!screen.selected().id().equals("arm")) {
                    return StepResult.fail("ohne Fortschritt nicht die erste Lektion gewählt: " + screen.selected().id());
                }
                FlatButton fly = button(screen.children(), "propwash.guide.training.fly");
                if (fly == null || !fly.active) {
                    return StepResult.fail("„Fliegen“ fehlt oder ist gesperrt");
                }
                Chapter second = Curriculum.chapters().get(1);
                ScreenRectangle chapterRow = screen.showRow(second);
                if (chapterRow == null) {
                    return StepResult.fail("Kapitel „" + second.id() + "“ steht nicht in der Liste");
                }
                MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
                screen.mouseClicked(new MouseButtonEvent(chapterRow.left() + chapterRow.width() / 2.0, chapterRow.top() + chapterRow.height() / 2.0, left),
                        false);
                if (screen.chapter() != second) {
                    return StepResult.fail("Linksklick auf das Kapitel „" + second.id() + "“ wählt es nicht (gewählt " + screen.chapter().id() + ")");
                }
                Chapter first = Curriculum.chapters().getFirst();
                ScreenRectangle firstRow = screen.showRow(first);
                if (firstRow == null) {
                    return StepResult.fail("erstes Kapitel steht nicht in der Liste");
                }
                screen.mouseClicked(new MouseButtonEvent(firstRow.left() + firstRow.width() / 2.0, firstRow.top() + firstRow.height() / 2.0, left), false);
                Lesson other = first.lessons().get(1);
                ScreenRectangle lessonRow = screen.showRow(other);
                if (screen.chapter() != first || lessonRow == null) {
                    return StepResult.fail("Lektion „" + other.id() + "“ steht nach dem Klick auf das erste Kapitel nicht in der Liste");
                }
                screen.mouseClicked(new MouseButtonEvent(lessonRow.left() + lessonRow.width() / 2.0, lessonRow.top() + lessonRow.height() / 2.0, left),
                        false);
                if (screen.selected() != other) {
                    return StepResult.fail("Linksklick auf die Lektion „" + other.id() + "“ wählt sie nicht (gewählt " + screen.selected().id() + ")");
                }
                MouseButtonInfo right = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0);
                ScreenRectangle again = screen.showRow(second);
                if (again == null) {
                    return StepResult.fail("Kapitel „" + second.id() + "“ steht nicht mehr in der Liste");
                }
                screen.mouseClicked(new MouseButtonEvent(again.left() + again.width() / 2.0, again.top() + again.height() / 2.0, right), false);
                if (screen.chapter() != first) {
                    return StepResult.fail("Rechtsklick wechselt das Kapitel");
                }
                screen.select(Curriculum.lesson("power_loop"));
                FlatButton locked = button(screen.children(), "propwash.guide.training.fly");
                if (locked == null || locked.active) {
                    return StepResult.fail("gesperrte Lektion lässt sich fliegen");
                }
                screen.onClose();
                phase(ctx, 3);
                return StepResult.pending();
            }
            default -> {
                if (!(mc.gui.screen() instanceof SceneIndexScreen)) {
                    return StepResult.fail("Schließen führt nicht zurück zur Übersicht: " + mc.gui.screen());
                }
                mc.gui.setScreen(null);
                return StepResult.pass();
            }
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    public static CompletableFuture<Void> startFlight(SelfTest.StepContext ctx, DroneBuild build, boolean fpv) {
        return ctx.onServer(server -> {
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            BlockPos area = AREA.get();
            if (area == null) {
                RETURN.set(new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()});
                int x = player.getBlockX() + AREA_OFFSET;
                int z = player.getBlockZ();
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                area = new BlockPos(x, y, z);
                AREA.set(area);
            }
            player.teleportTo(level, area.getX() + 0.5, area.getY(), area.getZ() + 4.5, Set.of(), 180.0F, 20.0F, true);
            discard(server);
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
            stack.set(CoreComponents.DRONE_BUILD.get(), build);
            drone.loadFromStack(stack);
            Vec3 pos = new Vec3(area.getX() + 0.5, area.getY(), area.getZ() + 0.5);
            drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
            level.addFreshEntity(drone);
            drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(-180.0) + (float) Math.PI));
            DRONE.set(drone.getId());
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.getInventory().setItem(0, remote);
            player.getInventory().setSelectedSlot(0);
            player.connection.send(new ClientboundSetHeldSlotPacket(0));
            if (fpv) {
                ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElse(ItemStack.EMPTY);
                goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                player.setItemSlot(EquipmentSlot.HEAD, goggles);
            } else {
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, fpv ? PilotMode.FPV : PilotMode.LINE_OF_SIGHT);
            if (result != DronePiloting.StartResult.STARTED) {
                throw new IllegalStateException("Start verweigert: " + result);
            }
        });
    }

    public static void returnPlayer(ServerPlayer player) {
        double[] back = RETURN.getAndSet(null);
        if (back != null) {
            player.teleportTo(player.level(), back[0], back[1], back[2], Set.of(), (float) back[3], (float) back[4], true);
        }
        AREA.set(null);
    }

    public static void discard(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
        }
    }

    private static StepResult startStep(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(FUTURE)) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(FUTURE, startFlight(ctx, DroneBuild.FREESTYLE, true));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Drohne starten");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isFpv() || session.sim() == null) {
            return ctx.tick() > 400 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
        }
        session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        return StepResult.pass();
    }

    public static StepResult placeStep(SelfTest.StepContext ctx, String lessonId) {
        TrainingSession training = TrainingSession.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        Lesson lesson = Curriculum.lesson(lessonId);
        if (lesson == null) {
            return StepResult.fail("Lektion fehlt: " + lessonId);
        }
        if (training.lesson() != lesson) {
            training.select(lesson);
            return StepResult.pending();
        }
        if (training.phase() != TrainingSession.Phase.ACTIVE || training.run() == null || training.course() == null) {
            Placement.Result placement = training.placement();
            return ctx.tick() > 700 ? StepResult.fail("Kurs nicht gesetzt: " + training.phase() + " " + (placement == null ? "-" : placement))
                    : StepResult.pending();
        }
        QuadcopterEntity drone = session.drone();
        Course course = training.course();
        if (drone == null) {
            return StepResult.fail("Drohne fehlt");
        }
        if (Math.hypot(course.frame().x() - drone.getX(), course.frame().z() - drone.getZ()) > 0.2) {
            return StepResult.fail("Kurs nicht an der Drohne: " + course.frame());
        }
        if (training.run().state() != LessonRun.State.READY) {
            return StepResult.fail("Versuch nicht bereit: " + training.run().state());
        }
        return StepResult.pass();
    }

    public static void teleport(double x, double footY, double z, double yaw, double vx, double vy, double vz) {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        if (sim == null || drone == null) {
            return;
        }
        double com = -sim.params().hitboxFootOffset;
        double half = -Math.toRadians(yaw) * 0.5;
        Quaternionf simQ = new Quaternionf(0.0F, (float) Math.sin(half), 0.0F, (float) Math.cos(half));
        sim.copyStateTo(SNAPSHOT);
        SNAPSHOT.px = x;
        SNAPSHOT.py = footY + com;
        SNAPSHOT.pz = z;
        SNAPSHOT.vx = vx;
        SNAPSHOT.vy = vy;
        SNAPSHOT.vz = vz;
        SNAPSHOT.lx = 0.0;
        SNAPSHOT.ly = 0.0;
        SNAPSHOT.lz = 0.0;
        SNAPSHOT.qw = simQ.w;
        SNAPSHOT.qx = simQ.x;
        SNAPSHOT.qy = simQ.y;
        SNAPSHOT.qz = simQ.z;
        SNAPSHOT.sleeping = false;
        sim.copyStateFrom(SNAPSHOT);
        Quaternionf body = new Quaternionf(simQ).rotateY((float) -Math.PI);
        drone.snapClientPose(new Vec3(x, footY, z), body);
    }

    static StepResult armAndWait(SelfTest.StepContext ctx, LessonRun run) {
        PilotSession session = PilotSession.INSTANCE;
        session.setTestInput(StickPositions.IDLE, session.armed() || since(ctx) >= 5 && ctx.tick() % 10 >= 3, StabMode.ANGLE);
        if (run.state() == LessonRun.State.RUNNING) {
            return StepResult.pass();
        }
        return since(ctx) > 200 ? StepResult.fail("Scharfschalten startet den Versuch nicht: " + run.state() + ", Sperren "
                + session.armBlockers()) : StepResult.pending();
    }

    static float hoverThrottle() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        double hover = sim == null ? 0.35 : sim.telemetry().hoverThrottleStick;
        return (float) (Double.isFinite(hover) && hover > 0.05 ? hover : 0.35);
    }

    private static StepResult hoverStep(SelfTest.StepContext ctx) {
        TrainingSession training = TrainingSession.INSTANCE;
        LessonRun run = training.run();
        Course course = training.course();
        if (run == null || course == null) {
            return StepResult.fail("kein Versuch");
        }
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult armed = armAndWait(ctx, run);
            if (armed.status() == StepResult.Status.PASS) {
                ctx.state().put("frames", TrainingRenderer.framesDrawn());
                phase(ctx, 1);
                return StepResult.pending();
            }
            return armed;
        }
        Zone zone = course.lesson().zones().getFirst();
        TrainingFrame frame = course.frame();
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hoverThrottle()), true, StabMode.ANGLE);
        if (run.state() == LessonRun.State.RUNNING) {
            teleport(frame.worldX(zone.centerX(), zone.centerZ()), frame.worldY(zone.centerY()) - 0.1, frame.worldZ(zone.centerX(), zone.centerZ()),
                    frame.yaw(), 0.0, 0.0, 0.0);
            return since(ctx) > 600 ? StepResult.fail("Schweben endet nicht: " + run.zoneTime() + " s im Kasten") : StepResult.pending();
        }
        if (run.state() != LessonRun.State.SUCCESS || run.result() == null) {
            return StepResult.fail("Schweben nicht geschafft: " + run.state() + " " + run.failure());
        }
        if (TrainingStore.progress().stars("hover") < 1) {
            return StepResult.fail("Sterne nicht gespeichert");
        }
        if ((long) ctx.state().get("frames") >= TrainingRenderer.framesDrawn()) {
            return StepResult.fail("Hologramme nicht gezeichnet");
        }
        String lines = String.join(" | ", TrainingHud.lastLines());
        if (!lines.contains(Component.translatable("propwash.guide.training.result.success").getString())) {
            return StepResult.fail("HUD zeigt kein Ergebnis: " + lines);
        }
        ctx.log("Schweben: " + run.result().stars() + " Sterne in " + run.result().time() + " s; HUD: " + lines);
        return StepResult.pass();
    }

    static List<double[]> worldPath(Course course) {
        List<double[]> points = new ArrayList<>();
        TrainingFrame frame = course.frame();
        for (double[] local : course.lesson().path()) {
            points.add(new double[]{frame.worldX(local[0], local[2]), frame.worldY(local[1]), frame.worldZ(local[0], local[2])});
        }
        return points;
    }

    private static StepResult ringsStep(SelfTest.StepContext ctx) {
        TrainingSession training = TrainingSession.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        int phase = phase(ctx);
        if (phase == 0) {
            QuadcopterEntity drone = session.drone();
            Course course = training.course();
            if (drone == null || course == null) {
                return StepResult.fail("keine Drohne oder kein Kurs");
            }
            teleport(course.frame().x(), course.frame().y(), course.frame().z(), course.frame().yaw(), 0.0, 0.0, 0.0);
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (phase == 1) {
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            if (since(ctx) < 10) {
                return StepResult.pending();
            }
            StepResult placed = placeStep(ctx, "straight");
            if (placed.status() == StepResult.Status.PASS) {
                phase(ctx, 2);
                return StepResult.pending();
            }
            return placed;
        }
        LessonRun run = training.run();
        Course course = training.course();
        if (run == null || course == null) {
            return StepResult.fail("kein Versuch");
        }
        if (phase == 2) {
            StepResult armed = armAndWait(ctx, run);
            if (armed.status() == StepResult.Status.PASS) {
                ctx.state().put(INDEX, 1);
                ctx.state().put("pos", new double[]{course.frame().x(), course.frame().y() + 0.5, course.frame().z()});
                phase(ctx, 3);
                return StepResult.pending();
            }
            return armed;
        }
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hoverThrottle()), true, StabMode.ANGLE);
        if (phase == 3) {
            List<double[]> path = worldPath(course);
            int index = (int) ctx.state().get(INDEX);
            double[] pos = (double[]) ctx.state().get("pos");
            if (run.state() == LessonRun.State.SUCCESS) {
                phase(ctx, 4);
                return StepResult.pending();
            }
            if (run.state() != LessonRun.State.RUNNING) {
                return StepResult.fail("Versuch abgebrochen: " + run.failure() + " bei Ring " + run.nextRing());
            }
            if (index >= path.size()) {
                return since(ctx) > 200 ? StepResult.fail("Pfad zu Ende, Ring " + run.nextRing() + " offen") : StepResult.pending();
            }
            double[] target = path.get(index);
            double dx = target[0] - pos[0];
            double dy = target[1] - 0.1 - pos[1];
            double dz = target[2] - pos[2];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double step = Math.min(STEP, length);
            if (length > 1.0E-6) {
                pos[0] += dx / length * step;
                pos[1] += dy / length * step;
                pos[2] += dz / length * step;
            }
            if (length <= STEP) {
                ctx.state().put(INDEX, index + 1);
            }
            double yaw = Math.hypot(dx, dz) > 0.05 ? TrainingFrame.yawOf(dx, dz) : course.frame().yaw();
            double speed = STEP * 20.0;
            teleport(pos[0], pos[1], pos[2], yaw, length > 1.0E-6 ? dx / length * speed : 0.0, length > 1.0E-6 ? dy / length * speed : 0.0,
                    length > 1.0E-6 ? dz / length * speed : 0.0);
            return since(ctx) > 1100 ? StepResult.fail("Ringe zu langsam") : StepResult.pending();
        }
        if (phase == 4) {
            LessonRun.Result result = run.result();
            if (result == null || result.splits().length != 5) {
                return StepResult.fail("Zwischenzeiten fehlen: " + (result == null ? "-" : result.splits().length));
            }
            TrainingProgress progress = TrainingStore.progress();
            double[] reference = progress.referenceSplits("straight", training.scale());
            if (reference.length != 5) {
                return StepResult.fail("Bestzeit-Zwischenzeiten nicht gespeichert: " + reference.length);
            }
            if (TrainingRenderer.ringsDrawn() <= 0) {
                return StepResult.fail("Ringe nicht gezeichnet");
            }
            ctx.state().put("runs", training.runs());
            teleport(course.frame().x(), course.frame().y(), course.frame().z(), course.frame().yaw(), 0.0, 0.0, 0.0);
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            phase(ctx, 5);
            return StepResult.pending();
        }
        session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (since(ctx) < 3) {
            teleport(course.frame().x(), course.frame().y(), course.frame().z(), course.frame().yaw(), 0.0, 0.0, 0.0);
        }
        if (training.runs() > (int) ctx.state().get("runs") && training.run() != null && training.run().state() == LessonRun.State.READY) {
            return StepResult.pass();
        }
        return since(ctx) > 300 ? StepResult.fail("kein neuer Versuch nach dem Landen am Start: " + training.phase()) : StepResult.pending();
    }

    private static StepResult disarmStep(SelfTest.StepContext ctx) {
        TrainingSession training = TrainingSession.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        LessonRun run = training.run();
        Course course = training.course();
        if (run == null || course == null) {
            return StepResult.fail("kein Versuch");
        }
        int phase = phase(ctx);
        if (phase == 0) {
            StepResult armed = armAndWait(ctx, run);
            if (armed.status() == StepResult.Status.PASS) {
                phase(ctx, 1);
                return StepResult.pending();
            }
            return armed;
        }
        TrainingFrame frame = course.frame();
        if (phase == 1) {
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, hoverThrottle()), true, StabMode.ANGLE);
            teleport(frame.worldX(0.0, 3.0), frame.worldY(3.0), frame.worldZ(0.0, 3.0), frame.yaw(), 0.0, 0.0, 0.0);
            if (since(ctx) > 10) {
                phase(ctx, 2);
            }
            return StepResult.pending();
        }
        if (phase == 2) {
            session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            teleport(frame.worldX(0.0, 3.0), frame.worldY(3.0), frame.worldZ(0.0, 3.0), frame.yaw(), 0.0, 0.0, 0.0);
            if (run.state() == LessonRun.State.FAILED) {
                if (run.failure() != LessonRun.Failure.DISARMED) {
                    return StepResult.fail("falscher Grund: " + run.failure());
                }
                TrainingSession.Banner banner = training.banner();
                if (banner == null || banner.success()) {
                    return StepResult.fail("kein Ergebnis-Banner für den Fehlversuch");
                }
                ctx.state().put("runs", training.runs());
                phase(ctx, 3);
                return StepResult.pending();
            }
            return since(ctx) > 200 ? StepResult.fail("Abschalten in der Luft nicht erkannt: " + run.state()) : StepResult.pending();
        }
        session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        if (since(ctx) < 3) {
            teleport(frame.x(), frame.y(), frame.z(), frame.yaw(), 0.0, 0.0, 0.0);
        }
        LessonRun next = training.run();
        if (next != null && training.runs() > (int) ctx.state().get("runs") && next.state() == LessonRun.State.READY) {
            return StepResult.pass();
        }
        return since(ctx) > 300 ? StepResult.fail("kein neuer Versuch nach dem Fehlversuch: " + training.phase() + ", Drohne "
                + describe(training)) : StepResult.pending();
    }

    private static StepResult blockedStep(SelfTest.StepContext ctx) {
        TrainingSession training = TrainingSession.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
        Map<String, Object> state = ctx.state();
        int phase = phase(ctx);
        if (phase == 0) {
            QuadcopterEntity drone = session.drone();
            if (drone == null) {
                return StepResult.fail("keine Drohne");
            }
            BlockPos at = drone.blockPosition();
            state.put(FUTURE, ctx.onServer(server -> {
                ServerLevel level = player(server).level();
                WALL.clear();
                for (int dx = -7; dx <= 7; dx++) {
                    for (int dz = -7; dz <= 7; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != 7) {
                            continue;
                        }
                        for (int dy = 0; dy < 9; dy++) {
                            BlockPos pos = at.offset(dx, dy, dz);
                            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                            WALL.add(pos);
                        }
                    }
                }
            }));
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (phase == 1) {
            StepResult server = awaitServer(ctx, "Mauer");
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.getBlockState(WALL.getLast()).is(Blocks.STONE)) {
                return since(ctx) > 200 ? StepResult.fail("Mauer kommt beim Client nicht an") : StepResult.pending();
            }
            training.select(Curriculum.lesson("straight"));
            phase(ctx, 2);
            return StepResult.pending();
        }
        if (phase == 2) {
            if (training.phase() == TrainingSession.Phase.BLOCKED) {
                Placement.Result placement = training.placement();
                if (placement == null || placement.status() != Placement.Status.BLOCKED || placement.blocked() <= 0) {
                    return StepResult.fail("Grund falsch: " + placement);
                }
                String lines = String.join(" | ", TrainingHud.lastLines());
                ctx.log("Kein Platz erkannt: " + placement + "; HUD: " + lines);
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerLevel level = player(server).level();
                    for (BlockPos pos : WALL) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }));
                phase(ctx, 3);
                return StepResult.pending();
            }
            if (training.phase() == TrainingSession.Phase.ACTIVE) {
                return StepResult.fail("Kurs trotz Mauer gesetzt");
            }
            return since(ctx) > 600 ? StepResult.fail("Platzprüfung meldet nichts: " + training.phase()) : StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Mauer abbauen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (training.phase() == TrainingSession.Phase.ACTIVE && training.course() != null) {
            WALL.clear();
            return StepResult.pass();
        }
        return since(ctx) > 600 ? StepResult.fail("Kurs nach dem Abbau nicht gesetzt: " + training.phase() + " " + training.placement())
                : StepResult.pending();
    }

    private static StepResult keyStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping mapping = GuideKeys.mapping();
        if (mapping == null) {
            return StepResult.fail("Taste fehlt");
        }
        if (phase(ctx) == 0) {
            mc.gui.setScreen(null);
            KeyMapping.click(InputConstants.getKey(mapping.saveString()));
            phase(ctx, 1);
            return StepResult.pending();
        }
        if (mc.gui.screen() instanceof TrainingScreen screen) {
            boolean straight = screen.selected().id().equals("straight") || screen.chapter().id().equals(Curriculum.FORWARD);
            mc.gui.setScreen(null);
            return StepResult.check(straight, "Training öffnet nicht die laufende Lektion: " + screen.selected().id());
        }
        return since(ctx) > 100 ? StepResult.fail("Taste M öffnet im Flug kein Training: " + mc.gui.screen()) : StepResult.pending();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            TrainingSession.INSTANCE.stop();
            PilotSession.INSTANCE.clearTestInput();
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                for (BlockPos pos : WALL) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
                WALL.clear();
                discard(server);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                returnPlayer(player);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx, "Aufräumen");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (PilotSession.INSTANCE.isActive()) {
            return ctx.tick() > 300 ? StepResult.fail("Sitzung endet nicht") : StepResult.pending();
        }
        TrainingStore.forget();
        return StepResult.check(TrainingSession.INSTANCE.lesson() == null, "Lektion noch aktiv");
    }

    static String describe(TrainingSession training) {
        de.lardtom.propwash.guide.training.TrainingSample s = training.sample();
        return String.format(java.util.Locale.ROOT, "scharf %s, am Boden %s, Höhe %.2f, Tempo %.2f, Lauf %s", s.armed, s.grounded,
                s.heightAboveGround, s.speed(), training.run() == null ? "-" : training.run().state());
    }

    public static boolean goalIs(Class<? extends Goal> type) {
        Goal goal = TrainingSession.INSTANCE.goal();
        return type.isInstance(goal);
    }
}
