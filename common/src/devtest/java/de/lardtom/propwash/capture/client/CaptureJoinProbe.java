package de.lardtom.propwash.capture.client;

import de.lardtom.propwash.api.client.CameraChunkRequests;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.CameraChunkResult;
import de.lardtom.propwash.capture.CameraArea;
import de.lardtom.propwash.dev.CaptureJoinCheck;
import de.lardtom.propwash.race.client.RaceDataChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CaptureJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 120;
    public static final int REQUEST_DELAY = 100;
    private static final String START = "capture_join_start";
    private static final String PHASE = "capture_join_phase";
    private static final String SINCE = "capture_join_since";
    private static final String RESULT = "capture_join_result";
    private static final String SPOT = "capture_join_spot";
    private static final String OPENED = "capture_join_opened";
    private static final String SIDE = "capture_join_side";
    private static final String SEEN = "capture_join_seen";

    private CaptureJoinProbe() {
    }

    private record Spot(int droneId, int markerId, CameraArea area, int blocks, int nearId, int nearRadius) {
    }

    private static @Nullable String next(Map<String, Object> state, int tick, int phase) {
        state.put(PHASE, phase);
        state.put(SINCE, tick);
        return null;
    }

    private static String finish(Map<String, Object> state, String line) {
        String result = line + "\n";
        state.put(RESULT, result);
        return result;
    }

    private static @Nullable Spot spot(Minecraft mc) {
        Optional<String> line = RaceDataChat.line(mc, CaptureJoinCheck.SPAWNED);
        if (line.isEmpty()) {
            return null;
        }
        String[] parts = line.get().substring(CaptureJoinCheck.SPAWNED.length()).trim().split(" ");
        if (parts.length < 8) {
            return null;
        }
        try {
            int radius = Integer.parseInt(parts[4]);
            return new Spot(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    new CameraArea(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), radius), Integer.parseInt(parts[5]),
                    Integer.parseInt(parts[6]), Integer.parseInt(parts[7]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        if (state.containsKey(RESULT)) {
            return (String) state.get(RESULT);
        }
        int start = (int) state.computeIfAbsent(START, key -> tick);
        int phase = (int) state.getOrDefault(PHASE, -1);
        if (tick - start > TIMEOUT_TICKS) {
            return finish(state, "capture timeout phase=" + phase);
        }
        int since = tick - (int) state.getOrDefault(SINCE, tick);
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return null;
        }
        CameraChunkRequests requests = PropwashClientApi.cameraChunks();
        switch (phase) {
            case -1 -> {
                Spot spot = spot(mc);
                if (spot == null) {
                    return null;
                }
                if (!requests.supported()) {
                    return finish(state, "capture server without camera chunks");
                }
                int seen = (int) state.computeIfAbsent(SEEN, key -> tick);
                if (tick - seen < REQUEST_DELAY) {
                    return null;
                }
                state.put(SPOT, spot);
                requests.request(spot.droneId(), spot.area().radius());
                requests.request(spot.nearId(), spot.nearRadius());
                return next(state, tick, 0);
            }
            case 0 -> {
                Spot spot = (Spot) state.get(SPOT);
                Optional<CameraChunkResult.Status> far = requests.answer(spot.droneId());
                Optional<CameraChunkResult.Status> near = requests.answer(spot.nearId());
                if (far.isEmpty() || near.isEmpty()) {
                    return since > 200 ? finish(state, "capture no answer far=" + far + " near=" + near) : null;
                }
                if (far.get() != CameraChunkResult.Status.OPENED || near.get() != CameraChunkResult.Status.OPENED) {
                    return finish(state, "capture refused far=" + far.get() + " near=" + near.get());
                }
                return next(state, tick, 1);
            }
            case 1 -> {
                Spot spot = (Spot) state.get(SPOT);
                int missing = CaptureSelfTests.missing(level, spot.area());
                Entity drone = level.getEntity(spot.droneId());
                Entity marker = level.getEntity(spot.markerId());
                if (missing > 0 || drone == null || marker == null) {
                    return since > 1600 ? finish(state, "capture incomplete missing=" + missing + " drone=" + (drone != null) + " marker=" + (marker != null)) : null;
                }
                int render = mc.options.getEffectiveRenderDistance();
                int gap = spot.area().distanceFrom(mc.player.chunkPosition().x(), mc.player.chunkPosition().z()) - spot.area().radius() - 1;
                boolean ownView = mc.getCameraEntity() == mc.player
                        && level.getChunkSource().hasChunk(mc.player.chunkPosition().x(), mc.player.chunkPosition().z());
                boolean drawn = mc.levelRenderer.isSectionCompiledAndVisible(drone.blockPosition(), 0L);
                if (!ownView || gap <= render || drawn) {
                    return finish(state, "capture view changed own=" + ownView + " gap=" + gap + " render=" + render + " drawn=" + drawn);
                }
                state.put(OPENED, since);
                state.put(SIDE, CaptureSelfTests.storageSide(level));
                requests.release(spot.droneId());
                return next(state, tick, 2);
            }
            default -> {
                Spot spot = (Spot) state.get(SPOT);
                int present = CaptureSelfTests.present(level, spot.area());
                boolean drone = level.getEntity(spot.droneId()) != null;
                boolean marker = level.getEntity(spot.markerId()) != null;
                int side = CaptureSelfTests.storageSide(level);
                int opened = (int) state.get(SIDE);
                if (present > 0 || drone || marker || side >= opened) {
                    return since > 600 ? finish(state, "capture not freed chunks=" + present + " drone=" + drone + " marker=" + marker + " storage=" + side) : null;
                }
                return finish(state, String.format(Locale.ROOT, "capture ok far=%d chunks=%d loaded=%d freed=%d storage=%d->%d vanilla=%d near=%d",
                        spot.blocks(), spot.area().chunks(), (int) state.get(OPENED), since, opened, side, CaptureSelfTests.vanillaSide(level),
                        requests.radius(spot.nearId())));
            }
        }
    }
}
