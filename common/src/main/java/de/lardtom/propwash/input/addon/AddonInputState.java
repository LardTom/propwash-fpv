package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AddonInputState {
    private static final Map<UUID, Held> SERVER = new ConcurrentHashMap<>();
    private static volatile Local local = Local.NONE;

    private AddonInputState() {
    }

    public interface Local {
        Local NONE = new Local() {
            @Override
            public boolean active(Entity drone, Identifier id) {
                return false;
            }

            @Override
            public double axis(Entity drone, Identifier id, double center) {
                return center;
            }
        };

        boolean active(Entity drone, Identifier id);

        double axis(Entity drone, Identifier id, double center);
    }

    private static final class Held {
        private final Set<Identifier> on = new LinkedHashSet<>();
        private final Map<Identifier, Double> axes = new HashMap<>();
    }

    public static void local(@Nullable Local view) {
        local = view == null ? Local.NONE : view;
    }

    public static boolean active(@Nullable Entity drone, @Nullable Identifier id) {
        if (drone == null || id == null) {
            return false;
        }
        if (drone.level().isClientSide()) {
            return local.active(drone, id);
        }
        Held held = SERVER.get(drone.getUUID());
        if (held == null) {
            return false;
        }
        synchronized (held) {
            return held.on.contains(id);
        }
    }

    public static boolean active(@Nullable Entity drone, @Nullable String id) {
        return active(drone, AddonIds.parse(id));
    }

    public static double axis(@Nullable Entity drone, @Nullable Identifier id) {
        double center = 0.0;
        if (id != null) {
            AxisSpec spec = AddonInputs.axisSpec(id);
            center = spec == null ? 0.0 : spec.center();
        }
        return axis(drone, id, center);
    }

    public static double axis(@Nullable Entity drone, @Nullable String id) {
        return axis(drone, AddonIds.parse(id));
    }

    public static double axis(@Nullable Entity drone, @Nullable Identifier id, double center) {
        if (drone == null || id == null) {
            return center;
        }
        if (drone.level().isClientSide()) {
            return local.axis(drone, id, center);
        }
        Held held = SERVER.get(drone.getUUID());
        if (held == null) {
            return center;
        }
        synchronized (held) {
            Double value = held.axes.get(id);
            return value == null ? center : value;
        }
    }

    public static void bind(Entity drone, DroneSim sim) {
        Objects.requireNonNull(drone, "drone");
        sim.setAddonInput(id -> active(drone, id), id -> axis(drone, id));
    }

    public static boolean setSwitch(Entity drone, Identifier id, boolean on) {
        Objects.requireNonNull(id, "id");
        Held held = SERVER.computeIfAbsent(drone.getUUID(), uuid -> new Held());
        synchronized (held) {
            return on ? held.on.add(id) : held.on.remove(id);
        }
    }

    public static boolean setAxis(Entity drone, Identifier id, double value) {
        Objects.requireNonNull(id, "id");
        Held held = SERVER.computeIfAbsent(drone.getUUID(), uuid -> new Held());
        synchronized (held) {
            Double before = held.axes.put(id, AddonInputMath.clamp(value));
            return before == null || before != AddonInputMath.clamp(value);
        }
    }

    public static Set<Identifier> activeOn(Entity drone) {
        Held held = SERVER.get(drone.getUUID());
        if (held == null) {
            return Set.of();
        }
        synchronized (held) {
            return Set.copyOf(held.on);
        }
    }

    public static void clear(Entity drone) {
        SERVER.remove(drone.getUUID());
    }

    public static void clearAll() {
        SERVER.clear();
    }
}
