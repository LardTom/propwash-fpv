package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.drone.PilotLook;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PilotHeads {
    private static final int STALE_TICKS = 3;
    private static final Map<UUID, Head> HEADS = new HashMap<>();
    private static long lastNanos;
    private static long clientTicks;

    private PilotHeads() {
    }

    private static final class Head {
        private boolean fpv;
        private long seenTick = Long.MIN_VALUE / 2;
        private float weight;
    }

    static void onDroneTick(QuadcopterEntity drone) {
        Optional<UUID> pilot = drone.pilot();
        if (pilot.isEmpty()) {
            return;
        }
        Head head = HEADS.computeIfAbsent(pilot.get(), uuid -> new Head());
        head.fpv = PilotSession.effectiveMode(drone.pilotMode()) == PilotMode.FPV;
        head.seenTick = clientTicks;
    }

    static void onClientTick(Minecraft mc) {
        clientTicks++;
    }

    static void onFrame(Minecraft mc, DeltaTracker deltaTracker) {
        long now = Util.getNanos();
        double dt = lastNanos == 0L ? 0.0 : (now - lastNanos) * 1.0E-9;
        lastNanos = now;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            HEADS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        UUID local = player.getUUID();
        boolean localFpv = PilotSession.INSTANCE.isFpv();
        if (localFpv) {
            HEADS.computeIfAbsent(local, uuid -> new Head());
        }
        if (HEADS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Head>> entries = HEADS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Head> entry = entries.next();
            Head head = entry.getValue();
            boolean fresh = clientTicks - head.seenTick <= STALE_TICKS;
            boolean fpv = entry.getKey().equals(local) ? localFpv : head.fpv && fresh;
            head.weight = PilotLook.headWeight(head.weight, fpv, dt);
            if (!fpv && head.weight <= 0.0F && !fresh) {
                entries.remove();
            }
        }
    }

    static void reset() {
        HEADS.clear();
        lastNanos = 0L;
    }

    public static void apply(Entity entity, LivingEntityRenderState state) {
        if (HEADS.isEmpty()) {
            return;
        }
        Head head = HEADS.get(entity.getUUID());
        if (head == null || head.weight <= 0.0F) {
            return;
        }
        state.xRot = PilotLook.headPitch(state.xRot, head.weight, state.isUpsideDown);
    }

    public static float weight(UUID player) {
        Head head = HEADS.get(player);
        return head == null ? 0.0F : head.weight;
    }
}
