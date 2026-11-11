package de.lardtom.propwash.sim;

import de.lardtom.propwash.build.DroneBuild;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovSurfaceResponseTest {
    private static final double DT = 1.0 / 144.0;
    private static final SurfaceResponse STONE = SurfaceResponse.normal(0.6);

    private static final class Ceiling {
        final FlatWorld w;
        final double ceilingY;
        final SurfaceResponse response;
        boolean hit;
        boolean crash;
        boolean hard;
        boolean bump;
        boolean strike;
        int impacts;
        double lowestVy = Double.POSITIVE_INFINITY;

        Ceiling(FlatWorld w, double ceilingY, SurfaceResponse response) {
            this.w = w;
            this.ceilingY = ceilingY;
            this.response = response;
        }

        double top() {
            return this.w.y + this.w.sim.params().hitboxFootOffset + this.w.sim.params().hitboxHeight;
        }

        void frame(double dt) {
            FlatWorld f = this.w;
            f.env.groundDistance = Double.POSITIVE_INFINITY;
            f.sim.step(Math.round(dt * 1e9), f.input, f.env, f.result);
            double dx = f.result.dx;
            double dy = f.result.dy;
            double dz = f.result.dz;
            double ay = dy;
            if (dy > 0.0) {
                ay = Math.min(dy, Math.max(0.0, this.ceilingY - this.top()));
            }
            f.y += ay;
            f.x += dx;
            f.z += dz;
            MoveReport r = f.report;
            r.clear();
            r.requestedX = dx;
            r.requestedY = dy;
            r.requestedZ = dz;
            r.actualX = dx;
            r.actualY = ay;
            r.actualZ = dz;
            r.hitY = Math.abs(ay - dy) > 1e-7;
            if (r.hitY) {
                this.hit = true;
                this.response.writeTo(r);
            }
            r.supportGap = FlatWorld.SUPPORT_PROBE;
            f.sim.afterMove(r);
            EventQueue q = f.sim.events();
            this.crash |= q.contains(SimEventType.CRASH);
            this.hard |= q.contains(SimEventType.HARD_IMPACT);
            this.bump |= q.contains(SimEventType.BUMP);
            this.strike |= q.contains(SimEventType.PROP_STRIKE);
            this.impacts += f.sim.impacts().size();
            if (this.hit) {
                this.lowestVy = Math.min(this.lowestVy, f.sim.body.vy);
            }
        }

        boolean quiet() {
            return !this.crash && !this.hard && !this.bump && !this.strike && this.impacts == 0;
        }
    }

    private static Ceiling upInto(SurfaceResponse response, double speed) {
        FlatWorld w = new FlatWorld(new DroneSim(DroneBuild.FREESTYLE, new FlightControllerConfig(), Tests.quietTuning(), 42L));
        w.place(0.0, 3.0, 0.0, 0.0);
        w.input.mode = StabMode.ANGLE;
        w.arm();
        AltitudeHold hold = new AltitudeHold(3.0);
        for (int f = 0; f < 288; f++) {
            hold.apply(w, DT);
            w.frame(DT);
        }
        w.input.throttle = w.sim.telemetry().hoverThrottleStick;
        double top = w.y + w.sim.params().hitboxFootOffset + w.sim.params().hitboxHeight;
        Ceiling placed = new Ceiling(w, top + 0.02, response);
        SimSnapshot snapshot = new SimSnapshot();
        w.sim.copyStateTo(snapshot);
        snapshot.vx = 0.0;
        snapshot.vy = speed;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        w.sim.copyStateFrom(snapshot);
        for (int f = 0; f < 200; f++) {
            placed.frame(0.001);
        }
        assertTrue(placed.hit, "Decke nicht erreicht");
        return placed;
    }

    @Test
    void slimeCeilingBouncesDown() {
        Ceiling slime = upInto(SurfaceResponse.BOUNCY, 8.0);
        assertTrue(slime.quiet(), "Ereignisse an der Slime-Decke");
        assertTrue(slime.w.sim.body.vy < 0.0, "vy nach der Slime-Decke " + slime.w.sim.body.vy);
        assertTrue(slime.lowestVy <= -4.0, "Rückprall nach unten " + slime.lowestVy);
        assertTrue(slime.w.sim.telemetry().armed, "bleibt scharf");
        assertEquals(0, slime.w.sim.telemetry().crashCount);
        assertEquals(0, slime.w.sim.telemetry().propStrikeCount);
    }

    @Test
    void stoneCeilingAtTheSameSpeedIsNotQuiet() {
        Ceiling stone = upInto(STONE, 8.0);
        assertTrue(!stone.quiet() || stone.w.sim.body.vy > -4.0, "Stein federt nicht wie Slime: vy " + stone.w.sim.body.vy);
    }
}
