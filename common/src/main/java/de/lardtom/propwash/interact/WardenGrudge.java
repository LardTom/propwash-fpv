package de.lardtom.propwash.interact;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WardenGrudge {
    public static final int HEAR_ANGER = 35;
    public static final int MAX_ANGER = 150;
    public static final int AGITATED = 40;
    public static final int ANGRY = 80;
    public static final int DECAY_TICKS = 20;
    public static final int CHARGE_TICKS = 34;
    public static final int BOOM_TICKS = 60;
    public static final int COOLDOWN_TICKS = 40;
    public static final double RANGE_XZ = 15.0;
    public static final double RANGE_Y = 20.0;
    private static final long NONE = Long.MIN_VALUE;

    private final Map<UUID, long[]> anger = new HashMap<>();
    private long chargeStart = NONE;
    private UUID chargeTarget;
    private long readyAt = NONE;

    public int add(UUID drone, int amount, long now) {
        int value = Math.min(MAX_ANGER, this.anger(drone, now) + Math.max(0, amount));
        this.anger.put(drone, new long[]{value, now});
        return value;
    }

    public int anger(UUID drone, long now) {
        long[] entry = this.anger.get(drone);
        if (entry == null) {
            return 0;
        }
        long elapsed = Math.max(0L, now - entry[1]);
        return (int) Math.max(0L, entry[0] - elapsed / DECAY_TICKS);
    }

    public static boolean angry(int anger) {
        return anger >= ANGRY;
    }

    public Optional<UUID> target(long now) {
        UUID best = null;
        int most = 0;
        Iterator<Map.Entry<UUID, long[]>> it = this.anger.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, long[]> entry = it.next();
            int value = this.anger(entry.getKey(), now);
            if (value <= 0) {
                it.remove();
            } else if (angry(value) && value > most) {
                most = value;
                best = entry.getKey();
            }
        }
        return Optional.ofNullable(best);
    }

    public void forget(UUID drone) {
        this.anger.remove(drone);
        if (drone.equals(this.chargeTarget)) {
            this.chargeStart = NONE;
            this.chargeTarget = null;
        }
    }

    public boolean empty() {
        return this.anger.isEmpty() && this.chargeStart == NONE;
    }

    public boolean charging() {
        return this.chargeStart != NONE;
    }

    public UUID chargeTarget() {
        return this.chargeTarget;
    }

    public boolean mayCharge(long now) {
        return this.chargeStart == NONE && (this.readyAt == NONE || now >= this.readyAt);
    }

    public void startCharge(UUID drone, long now) {
        this.chargeStart = now;
        this.chargeTarget = drone;
    }

    public boolean boomDue(long now) {
        return this.chargeStart != NONE && now - this.chargeStart >= CHARGE_TICKS;
    }

    public void boomed(long now) {
        this.chargeStart = NONE;
        this.chargeTarget = null;
        this.readyAt = now + (BOOM_TICKS - CHARGE_TICKS) + COOLDOWN_TICKS;
    }

    public static boolean inRange(double dx, double dy, double dz) {
        return dx * dx + dz * dz <= RANGE_XZ * RANGE_XZ && Math.abs(dy) <= RANGE_Y;
    }
}
