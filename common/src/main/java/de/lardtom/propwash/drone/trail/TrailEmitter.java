package de.lardtom.propwash.drone.trail;

public final class TrailEmitter {
    private final TrailBuffer buffer;
    private double lastUpdate = Double.NaN;
    private double speed;
    private boolean emitting;

    public TrailEmitter(int capacity) {
        this.buffer = new TrailBuffer(capacity);
    }

    public TrailBuffer buffer() {
        return this.buffer;
    }

    public double speed() {
        return this.speed;
    }

    public boolean emitting() {
        return this.emitting;
    }

    public boolean isEmpty() {
        return this.buffer.isEmpty();
    }

    public void reset() {
        this.buffer.clear();
        this.lastUpdate = Double.NaN;
        this.speed = 0.0;
        this.emitting = false;
    }

    public void update(double now, double x, double y, double z, boolean emits) {
        if (!Double.isFinite(now) || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return;
        }
        if (!Double.isNaN(this.lastUpdate) && now < this.lastUpdate) {
            if (this.lastUpdate - now <= TrailModel.MAX_REWIND) {
                return;
            }
            this.reset();
        }
        if (now == this.lastUpdate) {
            return;
        }
        this.lastUpdate = now;
        this.buffer.expire(now);
        this.emitting = emits;
        if (!emits) {
            this.speed = 0.0;
            return;
        }
        if (this.buffer.isEmpty()) {
            this.buffer.push(now, x, y, z, TrailModel.life(this.speed));
            return;
        }
        int newest = this.buffer.newest();
        double dt = now - this.buffer.time(newest);
        if (dt < TrailModel.SAMPLE_INTERVAL) {
            return;
        }
        double dx = x - this.buffer.x(newest);
        double dy = y - this.buffer.y(newest);
        double dz = z - this.buffer.z(newest);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (TrailModel.isJump(distance, dt)) {
            this.buffer.clear();
            this.speed = 0.0;
            this.buffer.push(now, x, y, z, TrailModel.life(0.0));
            return;
        }
        this.speed = TrailModel.smoothSpeed(this.speed, distance / dt, dt);
        if (distance < TrailModel.MIN_SPACING) {
            return;
        }
        this.buffer.push(now, x, y, z, TrailModel.life(this.speed));
    }
}
