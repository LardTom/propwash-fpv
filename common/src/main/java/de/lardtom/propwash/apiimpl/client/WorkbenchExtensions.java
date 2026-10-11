package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.Constants;

import de.lardtom.propwash.build.PartAvailability;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class WorkbenchExtensions {
    private static final List<WorkbenchExtension> EXTENSIONS = new ArrayList<>();

    private WorkbenchExtensions() {
    }

    public static synchronized void register(WorkbenchExtension extension) {
        Objects.requireNonNull(extension, "extension");
        for (WorkbenchExtension existing : EXTENSIONS) {
            if (existing.id().equals(extension.id())) {
                throw new IllegalArgumentException("Werkbank-Erweiterung doppelt angemeldet: " + extension.id());
            }
        }
        EXTENSIONS.add(extension);
        EXTENSIONS.sort(Comparator.comparingInt(WorkbenchExtension::order).thenComparing(e -> e.id().toString()));
    }

    public static synchronized List<WorkbenchExtension> all() {
        if (!PartAvailability.filtering()) {
            return List.copyOf(EXTENSIONS);
        }
        List<WorkbenchExtension> active = new ArrayList<>();
        for (WorkbenchExtension extension : EXTENSIONS) {
            if (PartAvailability.active(extension.id())) {
                active.add(extension);
            }
        }
        return List.copyOf(active);
    }

    public static void initAll(WorkbenchContext context) {
        for (WorkbenchExtension extension : all()) {
            try {
                extension.init(context);
            } catch (RuntimeException e) {
                Constants.LOG.error("Werkbank-Erweiterung {} ist beim Aufbau fehlgeschlagen", extension.id(), e);
            }
        }
    }
}
