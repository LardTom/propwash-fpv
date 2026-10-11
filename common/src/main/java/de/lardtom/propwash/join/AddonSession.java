package de.lardtom.propwash.join;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.PartAvailability;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AddonSession {
    private static final AtomicBoolean ANNOUNCED = new AtomicBoolean(true);
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile ClientOnlyAddons current = ClientOnlyAddons.NONE;

    private AddonSession() {
    }

    public static ClientOnlyAddons current() {
        return current;
    }

    public static boolean active(String namespace) {
        return PartAvailability.active(namespace);
    }

    public static void onChange(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static synchronized void activate(ClientOnlyAddons addons) {
        ClientOnlyAddons previous = current;
        current = addons;
        PartAvailability.set(addons.namespaces());
        ANNOUNCED.set(addons.isEmpty());
        if (!addons.isEmpty()) {
            Constants.LOG.info("Propwash: Add-ons nur bei dir, auf diesem Server für diese Sitzung inaktiv: {} (Namensräume {}, {} Teile)",
                    String.join(", ", addons.names()), addons.sortedNamespaces(), addons.parts());
        }
        if (!previous.equals(addons)) {
            fire();
        }
    }

    public static synchronized void clear() {
        ClientOnlyAddons previous = current;
        if (previous.isEmpty() && !PartAvailability.filtering()) {
            return;
        }
        current = ClientOnlyAddons.NONE;
        PartAvailability.clear();
        ANNOUNCED.set(true);
        Constants.LOG.info("Propwash: Verbindung beendet, alle Add-ons wieder aktiv ({})", String.join(", ", previous.names()));
        fire();
    }

    public static boolean takeAnnouncement() {
        return !ANNOUNCED.getAndSet(true) && !current.isEmpty();
    }

    private static void fire() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                Constants.LOG.error("Propwash: Reaktion auf geänderte Add-on-Sitzung fehlgeschlagen", e);
            }
        }
    }
}
