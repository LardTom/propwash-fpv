package de.lardtom.propwash.api.build;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record ImportRequest(DroneData data, Set<ImportLayer> layers, Set<TuneGroup> tuneGroups, ImportTarget target) {
    public ImportRequest {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(target, "target");
        EnumSet<ImportLayer> layerCopy = EnumSet.noneOf(ImportLayer.class);
        layerCopy.addAll(Objects.requireNonNullElse(layers, Set.of()));
        if (layerCopy.isEmpty()) {
            throw new IllegalArgumentException("Mindestens eine Import-Ebene wählen");
        }
        if (target == ImportTarget.HELD_DRONE && layerCopy.contains(ImportLayer.PARTS)) {
            throw new IllegalArgumentException("Teile lassen sich nur in der Werkbank übernehmen");
        }
        if (target == ImportTarget.NEW_DRONE && !layerCopy.contains(ImportLayer.PARTS)) {
            throw new IllegalArgumentException("Eine neue Drohne braucht die Ebene PARTS");
        }
        layers = Collections.unmodifiableSet(layerCopy);
        EnumSet<TuneGroup> groupCopy = EnumSet.noneOf(TuneGroup.class);
        groupCopy.addAll(Objects.requireNonNullElse(tuneGroups, TuneGroup.FLIGHT));
        tuneGroups = Collections.unmodifiableSet(groupCopy);
    }

    public static ImportRequest wholeBuild(DroneData data) {
        return new ImportRequest(data, EnumSet.allOf(ImportLayer.class), TuneGroup.FLIGHT, ImportTarget.WORKBENCH);
    }

    public static ImportRequest tuneOnHeldDrone(DroneData data) {
        return new ImportRequest(data, EnumSet.of(ImportLayer.TUNE), TuneGroup.FLIGHT, ImportTarget.HELD_DRONE);
    }

    public static ImportRequest paintOnHeldDrone(DroneData data) {
        return new ImportRequest(data, EnumSet.of(ImportLayer.PAINT), TuneGroup.FLIGHT, ImportTarget.HELD_DRONE);
    }

    public static ImportRequest onHeldDrone(DroneData data, Set<ImportLayer> layers) {
        return new ImportRequest(data, layers, TuneGroup.FLIGHT, ImportTarget.HELD_DRONE);
    }

    public static ImportRequest createDrone(DroneData data) {
        return new ImportRequest(data, EnumSet.allOf(ImportLayer.class), EnumSet.allOf(TuneGroup.class), ImportTarget.NEW_DRONE);
    }
}
