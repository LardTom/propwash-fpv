package de.lardtom.propwash.rescue;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class ServerReturnFlight extends ServerFlight {
    static final double PILOT_HOME_OFFSET = 2.5;

    final RescueAutopilot autopilot = new RescueAutopilot();
    private RescueSettings cfg;
    private ReturnPlan plan;
    private @Nullable ServerPlayer owner;
    private boolean landNowApplied;
    boolean announcedStuck;

    private ServerReturnFlight(ServerRescueWorld world) {
        super(world);
    }

    static ServerReturnFlight start(QuadcopterEntity drone, ReturnPlan plan, ServerLevel level, ServerRescueWorld world) {
        ServerReturnFlight flight = new ServerReturnFlight(world);
        flight.cfg = plan.settings();
        flight.plan = plan;
        flight.rulesOf(plan.owner());
        flight.begin(drone, level, drone.getDeltaMovement().scale(20.0), true);
        flight.autopilot.setAutonomous(true);
        flight.autopilot.start(flight.sim, drone.getX(), drone.getY(), drone.getZ(), plan.homeX(), plan.homeY(), plan.homeZ(), world, flight.cfg);
        return flight;
    }

    Outcome tick(QuadcopterEntity drone, ServerLevel level, ReturnPlan plan, @Nullable ServerPlayer owner) {
        this.plan = plan;
        this.owner = owner;
        return this.fly(drone, level);
    }

    @Override
    void resynced(QuadcopterEntity drone) {
        this.autopilot.start(this.sim, drone.getX(), drone.getY(), drone.getZ(), this.plan.homeX(), this.plan.homeY(), this.plan.homeZ(), this.world,
                this.cfg);
    }

    @Override
    void beforeSlices(QuadcopterEntity drone, ServerLevel level) {
        ServerPlayer owner = this.owner;
        ReturnPlan plan = this.plan;
        Vec3 followed = plan.followOwner() && owner != null && owner.level() == level && !owner.isSpectator()
                ? pilotHome(owner.position(), drone.position(), owner.getYRot()) : null;
        if (followed != null && Math.hypot(followed.x - drone.getX(), followed.z - drone.getZ()) <= AutonomousReturns.MAX_HOME_DISTANCE) {
            this.autopilot.moveHome(followed.x, followed.y, followed.z);
        } else {
            this.autopilot.moveHome(plan.homeX(), plan.homeY(), plan.homeZ());
        }
        if (plan.landNow() && !this.landNowApplied) {
            this.landNowApplied = true;
            this.autopilot.landNow(this.world);
        }
    }

    @Override
    void steer(double dt, QuadcopterEntity drone) {
        this.autopilot.update(dt, this.sim, drone.getX(), drone.getY(), drone.getZ(), this.world, this.cfg);
        this.input.roll = clamp(this.autopilot.roll(), -1.0, 1.0);
        this.input.pitch = clamp(this.autopilot.pitch(), -1.0, 1.0);
        this.input.yaw = clamp(this.autopilot.yaw(), -1.0, 1.0);
        this.input.throttle = clamp(this.autopilot.throttle(), 0.0, 1.0);
        this.input.mode = StabMode.ANGLE;
        this.input.armSwitch = true;
    }

    @Override
    boolean wantsDisarm() {
        return this.autopilot.wantsDisarm();
    }

    @Override
    boolean landed() {
        return this.autopilot.phase() == RescuePhase.LANDED;
    }

    static Vec3 pilotHome(Vec3 feet, Vec3 drone, float yawDegrees) {
        double dx = drone.x - feet.x;
        double dz = drone.z - feet.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-3) {
            double yaw = Math.toRadians(yawDegrees);
            dx = -Math.sin(yaw);
            dz = Math.cos(yaw);
            d = 1.0;
        }
        return new Vec3(feet.x + dx / d * PILOT_HOME_OFFSET, feet.y, feet.z + dz / d * PILOT_HOME_OFFSET);
    }
}
