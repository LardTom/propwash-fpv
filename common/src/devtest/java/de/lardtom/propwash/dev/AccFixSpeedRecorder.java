package de.lardtom.propwash.dev;

import de.lardtom.propwash.accessory.DroneMotion;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AccFixSpeedRecorder {
    public static final double SPEED = 5.6;
    public static final int MEASURE_TICKS = 40;
    public static final double MIN_SHARE = 0.5;
    public static final double MEAN_TOLERANCE = 0.15;
    public static final int MIN_ACTIONS = 3;
    private static final double ZERO = 0.05;
    private static final AtomicBoolean HOOKED = new AtomicBoolean();
    private static final AtomicInteger DRONE = new AtomicInteger(-1);
    private static final List<Double> TICKS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Double> ACTIONS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Double> MOVED = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicReference<@Nullable Vec3> LAST = new AtomicReference<>();
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();
    private static volatile boolean passed;
    private static volatile boolean actionsExpected = true;

    private AccFixSpeedRecorder() {
    }

    public static void hook() {
        if (HOOKED.compareAndSet(false, true)) {
            QuadcopterEntity.SERVER_TICK.register(AccFixSpeedRecorder::tick);
            PropwashEvents.ACCESSORY_ACTION.register((player, drone, number, pressed) -> action(drone, number, pressed));
        }
    }

    public static void reset() {
        DRONE.set(-1);
        TICKS.clear();
        ACTIONS.clear();
        MOVED.clear();
        LAST.set(null);
        RESULT.set(null);
        passed = false;
    }

    public static void start(int droneId, boolean withActions) {
        reset();
        actionsExpected = withActions;
        DRONE.set(droneId);
    }

    public static boolean recording() {
        return DRONE.get() >= 0 && RESULT.get() == null;
    }

    public static @Nullable String result() {
        return RESULT.get();
    }

    public static boolean passed() {
        return passed;
    }

    public static String progress() {
        return TICKS.size() + " Ticks, " + ACTIONS.size() + " Aktionen, Drohne " + DRONE.get();
    }

    private static void tick(QuadcopterEntity drone) {
        if (drone.getId() != DRONE.get() || TICKS.size() >= MEASURE_TICKS || drone.level().isClientSide()) {
            return;
        }
        TICKS.add(DroneMotion.velocity(drone).horizontalDistance());
        Vec3 position = drone.position();
        Vec3 last = LAST.getAndSet(position);
        MOVED.add(last == null ? Double.NaN : position.subtract(last).horizontalDistance() * 20.0);
        if (TICKS.size() == MEASURE_TICKS) {
            finish();
        }
    }

    private static void action(Entity drone, int number, boolean pressed) {
        if (drone.getId() != DRONE.get() || !pressed || number != ExampleDispenser.FIRE || TICKS.isEmpty() || TICKS.size() >= MEASURE_TICKS) {
            return;
        }
        ACTIONS.add(ExampleDispenser.LAST_DRONE_VELOCITY.get().horizontalDistance());
    }

    private static void finish() {
        List<Double> ticks = List.copyOf(TICKS);
        List<Double> actions = List.copyOf(ACTIONS);
        double min = Double.POSITIVE_INFINITY;
        double max = 0.0;
        double sum = 0.0;
        int zero = 0;
        for (double speed : ticks) {
            min = Math.min(min, speed);
            max = Math.max(max, speed);
            sum += speed;
            if (speed < ZERO) {
                zero++;
            }
        }
        double mean = sum / ticks.size();
        int still = 0;
        double movedSum = 0.0;
        int movedCount = 0;
        for (double speed : List.copyOf(MOVED)) {
            if (speed == 0.0) {
                still++;
            }
            if (Double.isFinite(speed)) {
                movedSum += speed;
                movedCount++;
            }
        }
        double movedMean = movedCount == 0 ? Double.NaN : movedSum / movedCount;
        double actionMin = actions.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        int actionZero = (int) actions.stream().filter(speed -> speed < ZERO).count();
        boolean actionsOk = !actionsExpected || actions.size() >= MIN_ACTIONS && actionMin >= MIN_SHARE * SPEED;
        boolean ok = zero == 0 && min >= MIN_SHARE * SPEED && Math.abs(mean - SPEED) <= MEAN_TOLERANCE * SPEED && actionsOk;
        passed = ok;
        RESULT.set(String.format(Locale.ROOT, "Server über %d Ticks: Propwash-Tempo im Mittel %.2f m/s (%.2f bis %.2f), in %d Ticks 0; "
                        + "Entity-Position im Mittel %.2f m/s, in %d Ticks unbewegt (so oft gab die alte Schätzung 0); %d Aktionen zwischen den Ticks: kleinstes Tempo %.2f m/s, %d-mal 0%s",
                ticks.size(), mean, min, max, zero, movedMean, still, actions.size(), actionMin, actionZero,
                ok ? "" : " – Erwartung: nie 0, Mittel 5,6 m/s ± 15 %, mindestens 3 Aktionen mit Tempo"));
    }
}
