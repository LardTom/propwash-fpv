package de.lardtom.propwash.trick.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.race.client.RaceState;
import de.lardtom.propwash.rescue.client.RescueState;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.trick.TrickSample;
import de.lardtom.propwash.trick.TrickSession;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

public final class TrickTracker {
    public static final TrickTracker INSTANCE = new TrickTracker();

    private final TrickSession session = new TrickSession();
    private final TrickSample sample = new TrickSample();
    private final ClearanceProbe probe = new ClearanceProbe();
    private final Vector3d position = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    private final Quaterniond attitude = new Quaterniond();
    private @Nullable DroneSim tracked;
    private boolean active;
    private long frames;

    private TrickTracker() {
    }

    public void onFrame(Minecraft minecraft, DeltaTracker deltaTracker) {
        PilotSession pilot = PilotSession.INSTANCE;
        DroneSim sim = pilot.sim();
        QuadcopterEntity drone = pilot.drone();
        if (sim == null || drone == null || minecraft.level == null
                || !TrickClient.CONFIG.get().mode.enabledFor(drone.build())) {
            this.stop();
            return;
        }
        if (sim != this.tracked) {
            this.session.reset();
            this.tracked = sim;
        }
        this.active = true;
        if (minecraft.isPaused()) {
            return;
        }
        this.fill(sim);
        this.sample.assisted = RescueState.INSTANCE.active() || RaceState.INSTANCE.racing();
        this.probe.probe(minecraft.level, this.sample);
        this.session.update(this.sample);
        this.frames++;
    }

    private void fill(DroneSim sim) {
        Telemetry t = sim.telemetry();
        sim.getPosition(this.position);
        sim.getVelocity(this.velocity);
        sim.getAttitude(this.attitude);
        TrickSample s = this.sample;
        s.time = t.time;
        s.x = this.position.x;
        s.y = this.position.y;
        s.z = this.position.z;
        s.vx = this.velocity.x;
        s.vy = this.velocity.y;
        s.vz = this.velocity.z;
        s.qx = this.attitude.x;
        s.qy = this.attitude.y;
        s.qz = this.attitude.z;
        s.qw = this.attitude.w;
        s.armed = t.armed;
        s.crashCount = t.crashCount;
        s.impactCount = t.impactCount;
        s.grounded = t.grounded;
        s.throttle = t.throttleOut;
        s.mode3d = t.mode3d;
    }

    private void stop() {
        if (this.active || this.tracked != null) {
            this.session.reset();
        }
        this.active = false;
        this.tracked = null;
    }

    public void onDisconnect(Minecraft minecraft) {
        this.stop();
    }

    public boolean active() {
        return this.active;
    }

    public TrickSession session() {
        return this.session;
    }

    public long frames() {
        return this.frames;
    }
}
