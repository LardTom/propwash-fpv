package de.lardtom.propwash.drone.collision.client;

import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.CollisionFlags;
import de.lardtom.propwash.api.drone.CollisionQuery;
import de.lardtom.propwash.api.drone.CollisionRule;
import de.lardtom.propwash.apiimpl.Api17Registries.Registered;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.collision.CollisionEnvironment;
import de.lardtom.propwash.drone.collision.CollisionRules;
import de.lardtom.propwash.drone.collision.DroneCollisionQuery;
import de.lardtom.propwash.drone.collision.GhostLatch;
import de.lardtom.propwash.input.addon.client.AddonServerFeatures;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.SimEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ClientCollision {
    public static final ClientCollision INSTANCE = new ClientCollision();

    private final GhostLatch latch = new GhostLatch();
    private final Vector3d velocity = new Vector3d();
    private List<Registered<CollisionRule>> rules = List.of();
    private CollisionRules.Evaluation evaluation = CollisionRules.SOLID;
    private CollisionFlags flags = CollisionFlags.SOLID;
    private @Nullable Identifier lastRule;
    private @Nullable Level level;
    private int droneId = -1;
    private long tick = Long.MIN_VALUE;

    private ClientCollision() {
    }

    public CollisionFlags flags(QuadcopterEntity drone) {
        this.refresh(drone);
        return this.flags;
    }

    public boolean fluids(@Nullable QuadcopterEntity drone) {
        return drone == null || this.flags(drone).fluids();
    }

    public void environment(QuadcopterEntity drone, SimEnvironment env) {
        CollisionEnvironment.apply(this.flags(drone), env);
    }

    public void beforeStep(QuadcopterEntity drone, SimEnvironment env) {
        CollisionEnvironment.beforeStep(this.flags(drone), env);
    }

    public void afterSurface(QuadcopterEntity drone, @Nullable DroneSim sim, MoveReport report, boolean grounded) {
        if (sim == null) {
            return;
        }
        this.refresh(drone);
        if (this.rules.isEmpty()) {
            return;
        }
        sim.getVelocity(this.velocity);
        CollisionRules.afterSurface(this.rules, drone, report, grounded, this.velocity, sim.params().restitution);
    }

    public @Nullable String assistRule(QuadcopterEntity drone) {
        if (PilotSession.INSTANCE.drone() != drone) {
            return null;
        }
        this.refresh(drone);
        if (this.flags.solid()) {
            return null;
        }
        Identifier rule = this.evaluation.rule() != null ? this.evaluation.rule() : this.lastRule;
        return rule == null ? null : rule.toString();
    }

    public boolean ghost() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return drone != null && drone.getId() == this.droneId && drone.level() == this.level && this.latch.ghost();
    }

    public boolean insideBlock() {
        return this.ghost() && this.latch.inside();
    }

    public boolean cameraClip() {
        return PilotSession.INSTANCE.isFpv() && this.ghost();
    }

    public void reset() {
        this.latch.reset();
        this.rules = List.of();
        this.evaluation = CollisionRules.SOLID;
        this.flags = CollisionFlags.SOLID;
        this.lastRule = null;
        this.level = null;
        this.droneId = -1;
        this.tick = Long.MIN_VALUE;
    }

    private void refresh(QuadcopterEntity drone) {
        Level world = drone.level();
        long now = world.getGameTime();
        if (drone.getId() == this.droneId && world == this.level && now == this.tick) {
            return;
        }
        if (drone.getId() != this.droneId || world != this.level) {
            this.reset();
        }
        this.droneId = drone.getId();
        this.level = world;
        this.tick = now;
        this.rules = CollisionRules.known(CollisionRules.rules(), ClientCollision::serverKnows);
        if (this.rules.isEmpty() && !this.latch.ghost()) {
            this.evaluation = CollisionRules.SOLID;
            this.flags = CollisionFlags.SOLID;
            return;
        }
        ClientInput input = PropwashClientApi.input();
        CollisionRules.Evaluation result = CollisionRules.evaluate(this.rules, new DroneCollisionQuery(drone, Minecraft.getInstance().player,
                CollisionQuery.Side.CLIENT, false, input::switchActive, input::axis));
        boolean ghost = this.latch.update(!result.blocks(), () -> CollisionRules.hullBlocked(world, drone, drone.getBoundingBox()));
        if (result.rule() != null) {
            this.lastRule = result.rule();
        }
        this.evaluation = result;
        this.flags = result.blocks() == !ghost ? result.flags() : result.flags().withBlocks(!ghost);
    }

    private static boolean serverKnows(Identifier id) {
        return !AddonServerFeatures.inWorld() || AddonServerFeatures.knowsCollisionRule(id);
    }
}
