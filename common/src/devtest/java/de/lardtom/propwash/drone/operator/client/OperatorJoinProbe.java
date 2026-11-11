package de.lardtom.propwash.drone.operator.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.example.client.ExampleOperatorClient;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.video.client.SpectateSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class OperatorJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    private static final String START = "operator_start";
    private static final String PHASE = "operator_phase";
    private static final String MARK = "operator_mark";
    private static final String DRONE = "operator_drone";
    private static final String PAN = "operator_pan";
    private static final String SHOT = "operator_shot";
    private static final String LINE_A = "operator_line_a";
    private static final String NOTIFIED = "operator_notified";
    private static final String MAX_PAN = "operator_max_pan";
    private static final String MIN_PITCH = "operator_min_pitch";
    private static final String STATES = "operator_states";
    private static final String LAST_STATE = "operator_last_state";
    private static final float[] AXES = {1.0F, -0.6F, 0.0F, 0.5F};

    private OperatorJoinProbe() {
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        state.putIfAbsent(START, tick);
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (tick - (int) state.get(START) > TIMEOUT_TICKS) {
            OperatorClient.testInput(null, 0);
            return state.getOrDefault(LINE_A, "") + "operator timeout in phase " + phase + "\n";
        }
        int mark = (int) state.getOrDefault(MARK, tick);
        switch (phase) {
            case 0 -> {
                if (OperatorClient.operating()) {
                    int id = OperatorClient.operatorDrone();
                    QuadcopterEntity drone = drone(mc, id);
                    state.put(DRONE, id);
                    state.put(PAN, drone == null ? 0.0 : ExampleOperatorClient.PROVIDER.pan(drone));
                    OperatorClient.testInput(AXES, 0b0001);
                    Constants.LOG.info("[Beitrittstest] Operator a: Client ist Operator der Drohne {} (Ansicht {}), schickt Achsen {}", id,
                            SpectateSession.INSTANCE.phase(), Arrays.toString(AXES));
                    next(state, 1, tick);
                }
                return null;
            }
            case 1 -> {
                QuadcopterEntity drone = drone(mc, (int) state.get(DRONE));
                double pan = drone == null ? 0.0 : ExampleOperatorClient.PROVIDER.pan(drone) - (double) state.get(PAN);
                double pitch = drone == null ? Double.NaN : ExampleOperatorClient.PROVIDER.pitch(drone);
                if (tick - mark < 60 || pan < 20.0) {
                    if (tick - mark > 600) {
                        OperatorClient.testInput(null, 0);
                        return String.format(Locale.ROOT, "operator a: gimbal still, pan %.1f%n", pan);
                    }
                    return null;
                }
                OperatorClient.State relayed = drone == null ? null : OperatorClient.state(drone.getId());
                String line = String.format(Locale.ROOT, "operator a ok: operating, gimbal pan %.1f pitch %.1f, sent %d, relayed %.2f/%.2f%n", pan, pitch,
                        OperatorClient.sent(), relayed == null ? Double.NaN : relayed.axis(0), relayed == null ? Double.NaN : relayed.axis(1));
                state.put(LINE_A, line);
                Constants.LOG.info("[Beitrittstest] Operator a: Gimbal-Lage am Operator-Client: Schwenk {}°, Neigung {}°",
                        String.format(Locale.ROOT, "%.1f", pan), String.format(Locale.ROOT, "%.1f", pitch));
                state.put(SHOT, capture(JoinTestEnv.label() + "_operator"));
                next(state, 2, tick);
                return null;
            }
            case 2 -> {
                CompletableFuture<?> shot = (CompletableFuture<?>) state.get(SHOT);
                if (!shot.isDone()) {
                    return null;
                }
                Constants.LOG.info("[Beitrittstest] Operator a: Bildschirmfoto {}", shot.getNow(null));
                OperatorClient.testInput(null, 0);
                SpectateSession.INSTANCE.consumeEscape();
                next(state, 3, tick);
                return null;
            }
            case 3 -> {
                if (OperatorClient.bound() || SpectateSession.INSTANCE.operator()) {
                    if (tick - mark > 200) {
                        return state.get(LINE_A) + "operator a: escape did not hand the camera back\n";
                    }
                    return null;
                }
                next(state, 4, tick);
                return null;
            }
            case 4 -> {
                PilotSession session = PilotSession.INSTANCE;
                QuadcopterEntity drone = session.isFpv() ? session.drone() : null;
                if (drone == null) {
                    return null;
                }
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                if (OperatorClient.pilotDrone() == drone.getId()) {
                    state.put(NOTIFIED, OperatorClient.operatorName());
                }
                OperatorClient.State relayed = OperatorClient.state(drone.getId());
                if (relayed != null && !Long.valueOf(relayed.nanos()).equals(state.get(LAST_STATE))) {
                    state.put(LAST_STATE, relayed.nanos());
                    state.merge(STATES, 1, (a, b) -> (int) a + (int) b);
                }
                double pan = ExampleOperatorClient.PROVIDER.pan(drone);
                double pitch = ExampleOperatorClient.PROVIDER.pitch(drone);
                state.merge(MAX_PAN, pan, (a, b) -> Math.max((double) a, (double) b));
                state.merge(MIN_PITCH, pitch, (a, b) -> Math.min((double) a, (double) b));
                if ((tick - mark) % 20 == 0) {
                    Constants.LOG.info("[Beitrittstest] Operator b: Gimbal-Lage beim Piloten: Schwenk {}°, Neigung {}° (Operator-Achsen {} / {})",
                            String.format(Locale.ROOT, "%.1f", pan), String.format(Locale.ROOT, "%.1f", pitch),
                            String.format(Locale.ROOT, "%.2f", OperatorClient.axis(drone, 0)), String.format(Locale.ROOT, "%.2f", OperatorClient.axis(drone, 1)));
                }
                boolean released = state.containsKey(NOTIFIED) && OperatorClient.pilotDrone() < 0;
                if (!released) {
                    return null;
                }
                session.clearTestInput();
                session.requestStop();
                String line = String.format(Locale.ROOT, "operator b ok: pilot notified %s, states %d, gimbal pan max %.1f pitch min %.1f%n",
                        state.get(NOTIFIED), (int) state.getOrDefault(STATES, 0), (double) state.get(MAX_PAN), (double) state.get(MIN_PITCH));
                Constants.LOG.info("[Beitrittstest] {}", line.trim());
                return state.get(LINE_A) + line;
            }
            default -> {
                return null;
            }
        }
    }

    private static void next(Map<String, Object> state, int phase, int tick) {
        state.put(PHASE, phase);
        state.put(MARK, tick);
    }

    private static @Nullable QuadcopterEntity drone(Minecraft mc, int id) {
        return mc.level != null && mc.level.getEntity(id) instanceof QuadcopterEntity drone ? drone : null;
    }

    private static CompletableFuture<String> capture(String label) {
        CompletableFuture<String> done = new CompletableFuture<>();
        Path file = Services.PLATFORM.getGameDir().resolve("jointest").resolve(label + ".png");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.complete("kein Ordner: " + e);
            return done;
        }
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        Screenshot.takeScreenshot(target, 1, image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
                done.complete(file.toString());
            } catch (Exception e) {
                done.complete("Bildschirmfoto fehlgeschlagen: " + e);
            }
        }));
        return done;
    }
}
