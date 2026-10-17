package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum LapAssist {
    HOLD_MODE,
    RESCUE,
    WORLD_PHYSICS,
    CREATIVE_DRONE,
    PARTS_MISMATCH,
    GOGGLES_LATENCY,
    OTHER,
    ZONE_PHYSICS;

    public static final int NONE = 0;
    public static final int ALL = (1 << values().length) - 1;
    public static final int SERVER_REASONS = WORLD_PHYSICS.bit() | CREATIVE_DRONE.bit() | PARTS_MISMATCH.bit() | ZONE_PHYSICS.bit();
    public static final int CLIENT_REASONS = ALL & ~SERVER_REASONS;

    private final String key = this.name().toLowerCase(Locale.ROOT);

    public int bit() {
        return 1 << this.ordinal();
    }

    public boolean in(int mask) {
        return (mask & this.bit()) != 0;
    }

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.race.assist." + this.key;
    }

    public static int of(Flight flight) {
        int mask = NONE;
        if (flight.holdMode()) {
            mask |= HOLD_MODE.bit();
        }
        if (flight.rescue()) {
            mask |= RESCUE.bit();
        }
        if (flight.physicsModified()) {
            mask |= WORLD_PHYSICS.bit();
        }
        if (flight.creativeDrone()) {
            mask |= CREATIVE_DRONE.bit();
        }
        if (flight.partsMismatch()) {
            mask |= PARTS_MISMATCH.bit();
        }
        if (flight.creativeGoggles() && flight.gogglesOwnLatency() && flight.gogglesLatencyMs() < flight.transmitterLatencyMs()) {
            mask |= GOGGLES_LATENCY.bit();
        }
        return mask;
    }

    public static LapAssist byKey(String key) {
        for (LapAssist assist : values()) {
            if (assist.key.equals(key)) {
                return assist;
            }
        }
        return OTHER;
    }

    public boolean serverSide() {
        return this.in(SERVER_REASONS);
    }

    public static List<LapAssist> list(int mask) {
        List<LapAssist> out = new ArrayList<>();
        for (LapAssist assist : values()) {
            if (assist.in(mask)) {
                out.add(assist);
            }
        }
        return out;
    }

    public static @Nullable LapAssist first(int mask) {
        for (LapAssist assist : values()) {
            if (assist.in(mask)) {
                return assist;
            }
        }
        return null;
    }

    public record Flight(boolean holdMode, boolean rescue, boolean physicsModified, boolean creativeDrone, boolean partsMismatch,
                         boolean creativeGoggles, boolean gogglesOwnLatency, int gogglesLatencyMs, int transmitterLatencyMs,
                         boolean freeRange, boolean cleanVideo, int relays) {
        public static Flight plain() {
            return new Flight(false, false, false, false, false, false, false, 0, 0, false, false, 0);
        }

        public Flight withCreativeGoggles(boolean ownLatency, int latencyMs, int transmitterMs) {
            return new Flight(this.holdMode, this.rescue, this.physicsModified, this.creativeDrone, this.partsMismatch, true, ownLatency, latencyMs,
                    transmitterMs, true, true, this.relays);
        }

        public Flight withRelays(int count) {
            return new Flight(this.holdMode, this.rescue, this.physicsModified, this.creativeDrone, this.partsMismatch, this.creativeGoggles,
                    this.gogglesOwnLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.freeRange, this.cleanVideo, count);
        }
    }
}
