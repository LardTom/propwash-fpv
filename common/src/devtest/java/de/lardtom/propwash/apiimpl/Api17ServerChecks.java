package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class Api17ServerChecks {
    private static final AtomicInteger WITH_NEW_PILOT = new AtomicInteger();
    private static final AtomicInteger WITH_OLD_PILOT = new AtomicInteger();
    private static final List<String> PROBLEMS = new CopyOnWriteArrayList<>();
    private static boolean registered;

    private Api17ServerChecks() {
    }

    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;
        PropwashEvents.PILOT_CHANGED.register((drone, previous, current) -> {
            if (!(drone.level() instanceof ServerLevel)) {
                PROBLEMS.add("PILOT_CHANGED nicht am Server");
            }
            if (previous.equals(current)) {
                PROBLEMS.add("PILOT_CHANGED ohne Wechsel: " + previous);
            }
            Optional<UUID> actual = PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
            if (!actual.equals(current)) {
                PROBLEMS.add("PILOT_CHANGED meldet " + current + ", die Drohne hat " + actual);
            }
            current.ifPresent(uuid -> WITH_NEW_PILOT.incrementAndGet());
            previous.ifPresent(uuid -> WITH_OLD_PILOT.incrementAndGet());
        });
    }

    public static @Nullable String check() {
        try {
            if (PropwashApi.switches().switches() == null || PropwashApi.worldRules().registered() == null || PropwashApi.droneData().keys() == null
                    || PropwashApi.shareCodes() == null || PropwashApi.drones().autonomy() == null || PropwashApi.drones().operators() == null) {
                return "Fassade der API 1.7 fehlt am dedizierten Server";
            }
        } catch (LinkageError e) {
            return "Fassade der API 1.7 lädt am dedizierten Server nicht: " + e;
        }
        if (!PROBLEMS.isEmpty()) {
            return String.join("; ", PROBLEMS.subList(0, Math.min(3, PROBLEMS.size())));
        }
        if (WITH_NEW_PILOT.get() == 0 || WITH_OLD_PILOT.get() == 0) {
            return "PILOT_CHANGED nicht in beiden Richtungen ausgelöst (" + summary() + ")";
        }
        return null;
    }

    public static String summary() {
        return "Fassaden erreichbar, PILOT_CHANGED " + WITH_NEW_PILOT.get() + "-mal mit neuem Piloten, " + WITH_OLD_PILOT.get()
                + "-mal mit bisherigem Piloten";
    }
}
