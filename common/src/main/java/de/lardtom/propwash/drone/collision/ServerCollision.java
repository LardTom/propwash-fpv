package de.lardtom.propwash.drone.collision;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.CollisionQuery;
import de.lardtom.propwash.api.drone.CollisionRule;
import de.lardtom.propwash.api.input.AddonAxis;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.apiimpl.Api17Registries.Registered;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.MoveReport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ServerCollision {
    public static final String DENIED_KEY = "propwash.drone.collision.denied";
    public static final int DENIED_COOLDOWN_TICKS = 40;
    public static final double LATCH_REACH = 16.0;
    public static final long LATCH_TICKS = 400L;
    private static final Map<QuadcopterEntity, Latch> LATCHES = new WeakHashMap<>();
    private static final Map<UUID, Long> DENIED_AT = new HashMap<>();
    private static final AtomicInteger DENIED = new AtomicInteger();
    private static final AtomicInteger PASSED = new AtomicInteger();
    private static final Vector3d VELOCITY = new Vector3d();

    private ServerCollision() {
    }

    public static CollisionRules.Evaluation evaluate(QuadcopterEntity drone, @Nullable Player pilot, boolean autonomous) {
        List<Registered<CollisionRule>> rules = CollisionRules.rules();
        return rules.isEmpty() ? CollisionRules.SOLID : CollisionRules.evaluate(rules, query(drone, pilot, autonomous));
    }

    public static boolean passBlocks(ServerLevel level, QuadcopterEntity drone, ServerPlayer pilot, AABB target) {
        Latch latch = LATCHES.get(drone);
        if (latch != null && !latch.pilot.equals(pilot.getUUID())) {
            LATCHES.remove(drone);
            latch = null;
        }
        List<Registered<CollisionRule>> rules = CollisionRules.rules();
        if (rules.isEmpty() && latch == null) {
            return false;
        }
        boolean wanted = !CollisionRules.evaluate(rules, query(drone, pilot, false)).blocks();
        if (wanted) {
            if (latch == null) {
                latch = new Latch(pilot.getUUID());
                LATCHES.put(drone, latch);
            }
            latch.ghost.update(true, () -> false);
            latch.anchor = null;
            PASSED.incrementAndGet();
            return true;
        }
        if (latch == null) {
            return false;
        }
        long now = level.getGameTime();
        Vec3 center = target.getCenter();
        if (latch.anchor == null) {
            latch.anchor = drone.getBoundingBox().getCenter();
            latch.since = now;
        }
        boolean bounded = now - latch.since <= LATCH_TICKS && center.distanceTo(latch.anchor) <= LATCH_REACH;
        boolean ghost = bounded && latch.ghost.update(false, () -> CollisionRules.hullBlocked(level, drone, target));
        if (!ghost) {
            LATCHES.remove(drone);
        } else {
            PASSED.incrementAndGet();
        }
        return ghost;
    }

    public static boolean insideBlock(QuadcopterEntity drone) {
        Latch latch = LATCHES.get(drone);
        return latch != null && latch.ghost.inside();
    }

    public static void denied(ServerPlayer player, QuadcopterEntity drone, long now) {
        if (CollisionRules.rules().isEmpty()) {
            return;
        }
        Long last = DENIED_AT.get(player.getUUID());
        if (last != null && now - last >= 0L && now - last < DENIED_COOLDOWN_TICKS) {
            return;
        }
        DENIED_AT.put(player.getUUID(), now);
        DENIED.incrementAndGet();
        Constants.LOG.info("Kollision: Flug von {} durch Blöcke abgelehnt, Drohne {} zurückgesetzt ({})", player.getPlainTextName(),
                drone.getId(), DENIED_KEY);
        player.sendOverlayMessage(Component.translatable(DENIED_KEY).withStyle(ChatFormatting.RED));
    }

    public static void afterSurface(QuadcopterEntity drone, DroneSim sim, MoveReport report, boolean grounded) {
        List<Registered<CollisionRule>> rules = CollisionRules.rules();
        if (rules.isEmpty()) {
            return;
        }
        sim.getVelocity(VELOCITY);
        CollisionRules.afterSurface(rules, drone, report, grounded, VELOCITY, sim.params().restitution);
    }

    public static int deniedCount() {
        return DENIED.get();
    }

    public static int passedCount() {
        return PASSED.get();
    }

    private static final class Latch {
        final GhostLatch ghost = new GhostLatch();
        final UUID pilot;
        @Nullable Vec3 anchor;
        long since;

        Latch(UUID pilot) {
            this.pilot = pilot;
        }
    }

    private static CollisionQuery query(QuadcopterEntity drone, @Nullable Player pilot, boolean autonomous) {
        return new DroneCollisionQuery(drone, pilot, CollisionQuery.Side.SERVER, autonomous,
                id -> PropwashApi.switches().find(id).map(s -> active(s, drone)).orElse(false),
                id -> PropwashApi.switches().findAxis(id).map(a -> value(a, drone)).orElse(0.0));
    }

    private static boolean active(AddonSwitch handle, QuadcopterEntity drone) {
        return handle.active(drone);
    }

    private static double value(AddonAxis handle, QuadcopterEntity drone) {
        return handle.value(drone);
    }
}
