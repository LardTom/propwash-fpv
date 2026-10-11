package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.interact.CargoMass;
import de.lardtom.propwash.interact.CargoReleaseReason;
import de.lardtom.propwash.interact.StrikeMath;
import de.lardtom.propwash.interact.StrikeRules;
import de.lardtom.propwash.interact.StrikeTargets;
import de.lardtom.propwash.interact.net.CargoReleaseC2S;
import de.lardtom.propwash.interact.net.DroneStrikeC2S;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.sim.CrashDamage;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.ImpactLog;
import de.lardtom.propwash.sim.ImpactSample;
import de.lardtom.propwash.core.client.ClientBus;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PilotInteraction {
    public static final PilotInteraction INSTANCE = new PilotInteraction();
    public static final double CONTACT_SLACK = 0.05;
    public static final int MAX_CONTACTS = 8;

    private static volatile FlightBuild lastFlightBuild;

    private final Vector3d velocity = new Vector3d();
    private final List<Contact> contacts = new ArrayList<>();
    private final List<Contact> confirmed = new ArrayList<>();
    private final Map<Integer, Long> lastStrike = new HashMap<>();
    private boolean crashed;
    private boolean dropRequested;
    private boolean switchHeld;
    private int strikesSent;

    private PilotInteraction() {
    }

    public static DroneBuild flightBuild(QuadcopterEntity drone) {
        DroneBuild base = drone.build();
        float grams = drone.cargoGrams();
        FlightBuild last = lastFlightBuild;
        if (last != null && last.base() == base && last.grams() == grams) {
            return last.flown();
        }
        DroneBuild flown = CargoMass.withPayload(base, grams);
        lastFlightBuild = new FlightBuild(base, grams, flown);
        return flown;
    }

    public static boolean sameAirframe(DroneBuild a, DroneBuild b) {
        return a != null && b != null && CargoMass.unladen(a).equals(CargoMass.unladen(b));
    }

    public void start() {
        this.reset();
        this.switchHeld = RadioState.INSTANCE.isActive(SwitchFunction.CARGO_DROP);
    }

    public void reset() {
        this.contacts.clear();
        this.confirmed.clear();
        this.lastStrike.clear();
        this.crashed = false;
        this.dropRequested = false;
        this.switchHeld = false;
        PilotBreaks.INSTANCE.reset();
    }

    public void requestDrop() {
        this.dropRequested = true;
    }

    public int strikesSent() {
        return this.strikesSent;
    }

    public Vec3 clip(QuadcopterEntity drone, DroneSim sim, Vec3 delta) {
        PilotBreaks.INSTANCE.beforeMove(drone, sim, delta);
        this.contacts.clear();
        if (!ClientInteractRules.dronesHitEntities() || delta.lengthSqr() <= 0.0 || drone.noPhysics) {
            return delta;
        }
        UUID pilot = drone.pilot().orElse(null);
        if (pilot == null) {
            return delta;
        }
        AABB box = drone.getBoundingBox();
        AABB swept = box.expandTowards(delta);
        List<Entity> found = drone.level().getEntities(drone, swept,
                entity -> StrikeTargets.solid(entity, pilot) && !entity.getBoundingBox().intersects(box));
        if (found.isEmpty()) {
            return delta;
        }
        sim.getVelocity(this.velocity);
        List<VoxelShape> shapes = new ArrayList<>(found.size());
        for (Entity entity : found) {
            shapes.add(Shapes.create(entity.getBoundingBox()));
        }
        Vec3 out = Vec3.ZERO;
        AABB moving = box;
        for (Direction.Axis axis : Direction.axisStepOrder(delta)) {
            double wanted = delta.get(axis);
            if (wanted == 0.0) {
                continue;
            }
            double allowed = wanted;
            Entity limiter = null;
            for (int i = 0; i < found.size(); i++) {
                double a = Shapes.collide(axis, moving, List.of(shapes.get(i)), wanted);
                if (Math.abs(a) < Math.abs(allowed)) {
                    allowed = a;
                    limiter = found.get(i);
                }
            }
            if (limiter != null && this.contacts.size() < MAX_CONTACTS) {
                this.contacts.add(new Contact(limiter, axis.ordinal(), wanted > 0.0, this.velocity.x, this.velocity.y, this.velocity.z));
            }
            out = out.with(axis, allowed);
            moving = moving.move(axis == Direction.Axis.X ? allowed : 0.0, axis == Direction.Axis.Y ? allowed : 0.0,
                    axis == Direction.Axis.Z ? allowed : 0.0);
        }
        return out;
    }

    public void afterMove(QuadcopterEntity drone) {
        if (this.contacts.isEmpty()) {
            return;
        }
        AABB box = drone.getBoundingBox();
        for (Contact contact : this.contacts) {
            AABB other = contact.entity().getBoundingBox();
            double gap = StrikeRules.gap(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
            if (gap <= CONTACT_SLACK && this.confirmed.size() < MAX_CONTACTS) {
                this.confirmed.add(contact);
            }
        }
        this.contacts.clear();
    }

    public void afterSlice(QuadcopterEntity drone, DroneSim sim) {
        if (this.crashed || drone.cargoGrams() <= 0.0F) {
            return;
        }
        ImpactLog impacts = sim.impacts();
        DroneBuild build = drone.build();
        for (int i = 0; i < impacts.size(); i++) {
            ImpactSample impact = impacts.get(i);
            if (CrashDamage.crash(build, impact)) {
                this.crashed = true;
                return;
            }
        }
    }

    public void afterFrame(QuadcopterEntity drone, int epoch) {
        PilotBreaks.INSTANCE.afterFrame(drone, epoch);
        boolean session = ClientBus.inSession();
        boolean held = RadioState.INSTANCE.isActive(SwitchFunction.CARGO_DROP);
        if (held && !this.switchHeld) {
            this.dropRequested = true;
        }
        this.switchHeld = held;
        long now = drone.level().getGameTime();
        for (Contact contact : this.confirmed) {
            Entity target = contact.entity();
            double closing = StrikeMath.closingSpeed(contact.vx(), contact.vy(), contact.vz(),
                    contact.axis() == 0 ? (contact.positive() ? 1.0 : -1.0) : 0.0,
                    contact.axis() == 1 ? (contact.positive() ? 1.0 : -1.0) : 0.0,
                    contact.axis() == 2 ? (contact.positive() ? 1.0 : -1.0) : 0.0);
            if (closing < StrikeMath.MIN_CLOSING_SPEED || target.isRemoved()) {
                continue;
            }
            Long last = this.lastStrike.get(target.getId());
            if (last != null && now - last < StrikeRules.TARGET_COOLDOWN_TICKS && now >= last) {
                continue;
            }
            this.lastStrike.put(target.getId(), now);
            if (session && ClientNet.sendToServer(new DroneStrikeC2S(drone.getId(), epoch, target.getId(), (float) contact.vx(),
                    (float) contact.vy(), (float) contact.vz(), contact.axis(), contact.positive()))) {
                this.strikesSent++;
            }
        }
        this.confirmed.clear();
        if (this.lastStrike.size() > 64) {
            this.lastStrike.values().removeIf(tick -> now - tick > StrikeRules.TARGET_COOLDOWN_TICKS);
        }
        boolean cargo = drone.cargoGrams() > 0.0F;
        if (this.crashed) {
            this.crashed = false;
            if (cargo && session) {
                ClientNet.sendToServer(new CargoReleaseC2S(drone.getId(), epoch, CargoReleaseReason.CRASH));
            }
            this.dropRequested = false;
            return;
        }
        if (this.dropRequested) {
            this.dropRequested = false;
            if (cargo && session) {
                ClientNet.sendToServer(new CargoReleaseC2S(drone.getId(), epoch, CargoReleaseReason.COMMAND));
            } else {
                CargoMessages.nothingToDrop(drone);
            }
        }
    }

    record Contact(Entity entity, int axis, boolean positive, double vx, double vy, double vz) {
    }

    private record FlightBuild(DroneBuild base, float grams, DroneBuild flown) {
    }
}
