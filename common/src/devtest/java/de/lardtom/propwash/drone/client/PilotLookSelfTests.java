package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotLook;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class PilotLookSelfTests {
    private static final double RADIUS = 4.0;
    private static final double HEIGHT = 1.0;
    private static final int FPV_POINTS = 8;
    private static final int FPV_HOLD_TICKS = 15;
    private static final int FPV_FIRST_MOVE = 10;
    private static final int LOS_POINTS = 6;
    private static final int LOS_HOLD_TICKS = 20;
    private static final int LOS_SETTLE_TICKS = 12;
    private static final int SWITCH_HOLD_TICKS = 40;
    private static final float ROTATION_TOLERANCE = 1.0E-3F;
    private static final float HEAD_YAW_TOLERANCE = 0.5F;
    private static final float HEAD_PITCH_TOLERANCE = 1.0F;
    private static final float SERVER_TOLERANCE = 3.0F;
    private static final double MAX_LOOK_ERROR = 5.0;
    private static final double SWITCH_ANGLE = Math.PI * 1.1;
    private static final String VIEW = "view";
    private static final String SERVER_START = "serverStart";
    private static final String SERVER_END = "serverEnd";
    private static final String START_FUTURE = "startFuture";
    private static final String END_FUTURE = "endFuture";
    private static final String SETUP = "setup";
    private static final String MOVE = "move";
    private static final String FRAMES = "frames";
    private static final String POSED = "posed";
    private static final String HEAD_YAW = "headYaw";
    private static final String QUADRANTS = "quadrants";
    private static final String LAST_YAW = "lastYaw";
    private static final String SWEPT = "swept";
    private static final String WORST = "worst";
    private static final String SWITCHED = "switched";
    private static final String BLENDING = "blending";
    private static final AtomicReference<float[]> FROZEN_VIEW = new AtomicReference<>();

    private PilotLookSelfTests() {
    }

    private static ServerPlayer serverPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(ServerPlayer player, int id) {
        if (!(player.level().getEntity(id) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Drohne auf dem Server verschwunden");
        }
        return drone;
    }

    private static float[] rotation(LocalPlayer player) {
        return new float[]{player.getYRot(), player.getXRot(), player.getYHeadRot()};
    }

    private static String turned(float[] start, float[] now) {
        String[] names = {"Gieren", "Nicken", "Kopf"};
        for (int i = 0; i < start.length; i++) {
            if (Math.abs(Mth.wrapDegrees(now[i] - start[i])) > ROTATION_TOLERANCE) {
                return String.format(Locale.ROOT, "%s %.2f° → %.2f°", names[i], start[i], now[i]);
            }
        }
        return null;
    }

    private static CompletableFuture<Void> serverRotation(SelfTest.StepContext ctx, AtomicReference<float[]> out) {
        return ctx.onServer(server -> {
            ServerPlayer player = serverPlayer(server);
            out.set(new float[]{player.getYRot(), player.getXRot(), player.getYHeadRot()});
        });
    }

    private static CompletableFuture<Void> moveAround(SelfTest.StepContext ctx, int id, double angle) {
        return ctx.onServer(server -> {
            ServerPlayer player = serverPlayer(server);
            QuadcopterEntity drone = serverDrone(player, id);
            Vec3 target = player.position().add(Math.cos(angle) * RADIUS, HEIGHT, Math.sin(angle) * RADIUS);
            drone.resetTo(target, new Quaternionf().rotationY((float) (Math.PI / 2.0 - angle)));
        });
    }

    private static StepResult moveFailed(Map<String, Object> state) {
        Object move = state.get(MOVE);
        if (move instanceof CompletableFuture<?> future && future.isCompletedExceptionally()) {
            return StepResult.fail("Drohne nicht um den Piloten gesetzt: " + future.exceptionNow().getMessage());
        }
        return null;
    }

    private static void idle(PilotSession session) {
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), false, StabMode.ANGLE);
    }

    private static double lookError(LocalPlayer player, PilotSession session) {
        Vector3d target = session.comPosition(new Vector3d());
        Vec3 eye = player.getEyePosition();
        Vec3 wanted = new Vec3(target.x - eye.x, target.y - eye.y, target.z - eye.z).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, wanted.dot(player.getLookAngle())))));
    }

    @SuppressWarnings("unchecked")
    private static float[] value(Map<String, Object> state, String key) {
        return ((AtomicReference<float[]>) state.get(key)).get();
    }

    static StepResult fpvCircleStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        LocalPlayer player = mc.player;
        if (!session.isFpv() || drone == null || player == null) {
            return StepResult.fail("keine FPV-Sitzung");
        }
        idle(session);
        Map<String, Object> state = ctx.state();
        int tick = ctx.tick();
        if (tick == 0) {
            state.put(VIEW, rotation(player));
            AtomicReference<float[]> server = new AtomicReference<>();
            state.put(SERVER_START, server);
            state.put(START_FUTURE, serverRotation(ctx, server));
            state.put(FRAMES, PilotBodyPose.posedFrames());
            state.put(QUADRANTS, 0);
            state.put(POSED, 0);
            return StepResult.pending();
        }
        String turned = turned((float[]) state.get(VIEW), rotation(player));
        if (turned != null) {
            return StepResult.fail("Im FPV dreht sich der Pilot zur kreisenden Drohne: " + turned);
        }
        long frames = PilotBodyPose.posedFrames();
        if (frames != (long) state.get(FRAMES)) {
            state.put(FRAMES, frames);
            state.put(POSED, (int) state.get(POSED) + 1);
            float headYaw = PilotBodyPose.lastHeadYaw();
            Object first = state.get(HEAD_YAW);
            if (first == null) {
                state.put(HEAD_YAW, headYaw);
            } else if (Math.abs(Mth.wrapDegrees(headYaw - (float) first)) > HEAD_YAW_TOLERANCE) {
                return StepResult.fail(String.format(Locale.ROOT, "Kopf des Pilotenkörpers dreht sich zur Drohne: %.1f° → %.1f°",
                        (float) first, headYaw));
            }
        }
        StepResult failed = moveFailed(state);
        if (failed != null) {
            return failed;
        }
        int step = tick - FPV_FIRST_MOVE;
        if (step >= 0 && step % FPV_HOLD_TICKS == 0 && step / FPV_HOLD_TICKS < FPV_POINTS) {
            double angle = (step / FPV_HOLD_TICKS + 0.5) * Math.PI * 2.0 / FPV_POINTS;
            state.put(MOVE, moveAround(ctx, drone.getId(), angle));
        }
        Vector3d com = session.comPosition(new Vector3d());
        double around = Math.atan2(com.z - player.getZ(), com.x - player.getX());
        int quadrant = Math.floorMod((int) Math.floor(around / (Math.PI / 2.0)), 4);
        state.put(QUADRANTS, (int) state.get(QUADRANTS) | (1 << quadrant));
        int end = FPV_FIRST_MOVE + FPV_POINTS * FPV_HOLD_TICKS + 5;
        if (tick < end) {
            return StepResult.pending();
        }
        if (!state.containsKey(END_FUTURE)) {
            AtomicReference<float[]> server = new AtomicReference<>();
            state.put(SERVER_END, server);
            state.put(END_FUTURE, serverRotation(ctx, server));
            return StepResult.pending();
        }
        CompletableFuture<?> startFuture = (CompletableFuture<?>) state.get(START_FUTURE);
        CompletableFuture<?> endFuture = (CompletableFuture<?>) state.get(END_FUTURE);
        if (startFuture.isCompletedExceptionally() || endFuture.isCompletedExceptionally()) {
            return StepResult.fail("Blickrichtung auf dem Server nicht lesbar");
        }
        if (!startFuture.isDone() || !endFuture.isDone()) {
            return StepResult.pending();
        }
        if ((int) state.get(QUADRANTS) != 0b1111) {
            return StepResult.fail("Drohne hat den Piloten nicht umkreist (Viertel " + Integer.toBinaryString((int) state.get(QUADRANTS)) + ")");
        }
        float[] serverStart = value(state, SERVER_START);
        float[] serverEnd = value(state, SERVER_END);
        String serverTurned = turned(serverStart, serverEnd);
        if (serverTurned != null) {
            return StepResult.fail("Andere Spieler sehen den FPV-Piloten der Drohne nachschauen: " + serverTurned);
        }
        int posed = (int) state.get(POSED);
        if (posed == 0) {
            return StepResult.fail("Pilotenkörper während des Kreisens nie gezeichnet");
        }
        if (PilotHeads.weight(player.getUUID()) < 0.99F) {
            return StepResult.fail("Kopf des Pilotenkörpers nicht in der FPV-Haltung");
        }
        float pitch = PilotBodyPose.lastHeadPitch();
        if (Math.abs(pitch - PilotLook.FPV_HEAD_PITCH) > HEAD_PITCH_TOLERANCE) {
            return StepResult.fail(String.format(Locale.ROOT, "Kopf nicht leicht gesenkt: %.1f° statt %.1f°", pitch, PilotLook.FPV_HEAD_PITCH));
        }
        ctx.log(String.format(Locale.ROOT, "Drohne einmal um den Piloten, Blick %.1f°/%.1f° unverändert (auch auf dem Server), Kopf in %d Frames"
                + " fest bei %.1f° Gieren und %.1f° gesenkt", player.getYRot(), player.getXRot(), posed, (float) state.get(HEAD_YAW), pitch));
        return StepResult.pass();
    }

    static StepResult losCircleStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        LocalPlayer player = mc.player;
        if (!session.isLineOfSight() || drone == null || player == null) {
            return StepResult.fail("keine Sichtflug-Sitzung");
        }
        idle(session);
        Map<String, Object> state = ctx.state();
        int tick = ctx.tick();
        float yaw = player.getYRot();
        Object last = state.get(LAST_YAW);
        if (last != null) {
            state.put(SWEPT, (float) state.getOrDefault(SWEPT, 0.0F) + Math.abs(Mth.wrapDegrees(yaw - (float) last)));
        }
        state.put(LAST_YAW, yaw);
        StepResult failed = moveFailed(state);
        if (failed != null) {
            return failed;
        }
        if (tick % LOS_HOLD_TICKS == 0 && tick / LOS_HOLD_TICKS < LOS_POINTS) {
            double angle = (tick / LOS_HOLD_TICKS + 0.5) * Math.PI * 2.0 / LOS_POINTS;
            state.put(MOVE, moveAround(ctx, drone.getId(), angle));
        }
        if (tick % LOS_HOLD_TICKS >= LOS_SETTLE_TICKS) {
            double error = lookError(player, session);
            state.put(WORST, Math.max((double) state.getOrDefault(WORST, 0.0), error));
            if (error > MAX_LOOK_ERROR) {
                return StepResult.fail(String.format(Locale.ROOT, "Im Sichtflug verliert der Blick die kreisende Drohne: %.1f° nach %d Ticks",
                        error, tick));
            }
        }
        if (tick < LOS_POINTS * LOS_HOLD_TICKS) {
            return StepResult.pending();
        }
        float swept = (float) state.getOrDefault(SWEPT, 0.0F);
        if (swept < 180.0F) {
            return StepResult.fail(String.format(Locale.ROOT, "Blick dreht sich im Sichtflug nur um %.0f° mit der Drohne", swept));
        }
        if (!state.containsKey(END_FUTURE)) {
            AtomicReference<float[]> server = new AtomicReference<>();
            state.put(SERVER_END, server);
            state.put(END_FUTURE, serverRotation(ctx, server));
            return StepResult.pending();
        }
        CompletableFuture<?> endFuture = (CompletableFuture<?>) state.get(END_FUTURE);
        if (endFuture.isCompletedExceptionally()) {
            return StepResult.fail("Blickrichtung auf dem Server nicht lesbar");
        }
        if (!endFuture.isDone()) {
            return StepResult.pending();
        }
        float[] server = value(state, SERVER_END);
        float serverError = Math.abs(Mth.wrapDegrees(server[0] - player.getYRot()));
        if (serverError > SERVER_TOLERANCE) {
            return StepResult.fail(String.format(Locale.ROOT, "Andere Spieler sehen den Sichtflug-Piloten nicht zur Drohne schauen (%.1f° daneben)",
                    serverError));
        }
        ctx.log(String.format(Locale.ROOT, "Sichtflug: Blick dreht sich %.0f° mit der kreisenden Drohne, größte Abweichung %.1f°, Server %.1f° daneben",
                swept, (double) state.getOrDefault(WORST, 0.0), serverError));
        return StepResult.pass();
    }

    static StepResult gogglesOnStep(SelfTest.StepContext ctx, Function<QuadcopterEntity, ItemStack> goggles) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        LocalPlayer player = mc.player;
        if (drone == null || player == null) {
            return StepResult.fail("keine aktive Sitzung");
        }
        idle(session);
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            if (!session.isLineOfSight()) {
                return StepResult.fail("vor dem Aufsetzen kein Sichtflug");
            }
            int id = drone.getId();
            state.put(SETUP, ctx.onServer(server -> {
                ServerPlayer serverPlayer = serverPlayer(server);
                QuadcopterEntity serverDrone = serverDrone(serverPlayer, id);
                serverPlayer.setItemSlot(EquipmentSlot.HEAD, goggles.apply(serverDrone));
                if (DronePiloting.decideMode(serverPlayer, serverDrone).mode() != PilotMode.FPV) {
                    throw new IllegalStateException("aufgesetzte Brille erlaubt kein FPV");
                }
                serverDrone.setPilotMode(PilotMode.FPV);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> setup = (CompletableFuture<?>) state.get(SETUP);
        if (setup.isCompletedExceptionally()) {
            return StepResult.fail("Brille nicht aufgesetzt: " + setup.exceptionNow().getMessage());
        }
        StepResult failed = moveFailed(state);
        if (failed != null) {
            return failed;
        }
        if (!state.containsKey(SWITCHED)) {
            if (!session.isFpv() || mc.getCameraEntity() != drone) {
                return ctx.tick() > 60 ? StepResult.fail("Brille wieder auf: kein Wechsel ins FPV (Modus " + session.mode() + ")")
                        : StepResult.pending();
            }
            state.put(SWITCHED, ctx.tick());
            float[] view = rotation(player);
            state.put(VIEW, view);
            FROZEN_VIEW.set(view);
            state.put(MOVE, moveAround(ctx, drone.getId(), SWITCH_ANGLE));
            return StepResult.pending();
        }
        if (!session.isFpv()) {
            return StepResult.fail("FPV nach dem Aufsetzen wieder verlassen (Modus " + session.mode() + ")");
        }
        String turned = turned((float[]) state.get(VIEW), rotation(player));
        if (turned != null) {
            return StepResult.fail("Nach dem Wechsel ins FPV folgt der Blick weiter der Drohne: " + turned);
        }
        float weight = PilotHeads.weight(player.getUUID());
        if (weight > 0.0F && weight < 1.0F) {
            state.put(BLENDING, true);
        }
        if (ctx.tick() - (int) state.get(SWITCHED) < SWITCH_HOLD_TICKS) {
            return StepResult.pending();
        }
        if (!state.containsKey(BLENDING)) {
            return StepResult.fail("Kopf springt ohne Übergang in die FPV-Haltung");
        }
        if (weight < 0.99F) {
            return StepResult.fail(String.format(Locale.ROOT, "Kopf nach %d Ticks nicht in der FPV-Haltung (%.2f)", SWITCH_HOLD_TICKS, weight));
        }
        ctx.log("Sichtflug → FPV: Blick bleibt stehen, Kopf gleitet in die FPV-Haltung, Drohne versetzt ohne Kopfdrehung");
        return StepResult.pass();
    }

    static StepResult losResumeStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        LocalPlayer player = mc.player;
        if (!session.isLineOfSight() || player == null) {
            return StepResult.fail("keine Sichtflug-Sitzung");
        }
        idle(session);
        float[] frozen = FROZEN_VIEW.get();
        if (frozen == null) {
            return StepResult.fail("kein FPV-Blick zum Vergleichen");
        }
        double error = lookError(player, session);
        if (error > MAX_LOOK_ERROR) {
            return ctx.tick() > 40 ? StepResult.fail(String.format(Locale.ROOT, "Blick findet die Drohne nach dem Absetzen nicht (%.1f°)", error))
                    : StepResult.pending();
        }
        float turnedBy = Math.abs(Mth.wrapDegrees(player.getYRot() - frozen[0]));
        if (turnedBy < 30.0F) {
            return StepResult.fail(String.format(Locale.ROOT, "Drohne stand im FPV woanders, Blick hat sich aber nur um %.1f° gedreht", turnedBy));
        }
        ctx.log(String.format(Locale.ROOT, "Brille ab: Blick dreht sich sofort wieder zur Drohne (%.0f° weiter, %.1f° Abweichung) nach %d Ticks",
                turnedBy, error, ctx.tick()));
        return StepResult.pass();
    }
}
