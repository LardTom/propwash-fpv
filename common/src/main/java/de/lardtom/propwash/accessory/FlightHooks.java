package de.lardtom.propwash.accessory;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.flight.DroneProfile;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightExtensions;
import de.lardtom.propwash.api.flight.ForceProvider;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.DroneSim;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class FlightHooks implements FlightExtensions {
    public static final FlightHooks INSTANCE = new FlightHooks();
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private final Map<String, Function<DroneProfile, FlightAssist>> assists = new LinkedHashMap<>();
    private final Map<String, Function<DroneProfile, ForceProvider>> forces = new LinkedHashMap<>();

    private FlightHooks() {
    }

    private static String check(String id) {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches() || id.startsWith(Constants.MOD_ID + ":")) {
            throw new IllegalArgumentException("Id braucht einen eigenen Namensraum (z. B. meinmod:kino): " + id);
        }
        return id;
    }

    @Override
    public synchronized void registerAssist(String id, Function<DroneProfile, FlightAssist> factory) {
        Objects.requireNonNull(factory, "factory");
        if (this.assists.putIfAbsent(check(id), factory) != null) {
            throw new IllegalArgumentException("Flughilfe doppelt angemeldet: " + id);
        }
    }

    @Override
    public synchronized void registerForces(String id, Function<DroneProfile, ForceProvider> factory) {
        Objects.requireNonNull(factory, "factory");
        if (this.forces.putIfAbsent(check(id), factory) != null) {
            throw new IllegalArgumentException("Kräfte doppelt angemeldet: " + id);
        }
    }

    @Override
    public synchronized List<String> assistIds() {
        return List.copyOf(this.assists.keySet());
    }

    @Override
    public synchronized List<String> forceIds() {
        return List.copyOf(this.forces.keySet());
    }

    public synchronized boolean any() {
        return !this.assists.isEmpty() || !this.forces.isEmpty();
    }

    public static DroneProfile profile(DroneBuild build) {
        DroneBuild base = AccessoryPayload.unladen(build);
        Map<String, String> accessories = new LinkedHashMap<>();
        for (Map.Entry<AccessoryMount, AccessoryType> entry : base.accessories().byMount().entrySet()) {
            accessories.put(entry.getKey().key(), entry.getValue().id().toString());
        }
        return new DroneProfile(base.frame().id().toString(), base.stack().id().toString(), base.motor().id().toString(),
                base.prop().id().toString(), base.video().id().toString(), base.battery().id().toString(), accessories,
                AirframeSpec.of(build).massKg);
    }

    public record PickedAssist(String id, FlightAssist assist) {
    }

    public synchronized @Nullable FlightAssist assistFor(DroneBuild build) {
        PickedAssist picked = this.pickAssist(build);
        return picked == null ? null : picked.assist();
    }

    public synchronized @Nullable PickedAssist pickAssist(DroneBuild build) {
        if (this.assists.isEmpty()) {
            return null;
        }
        DroneProfile profile = profile(build);
        for (Map.Entry<String, Function<DroneProfile, FlightAssist>> entry : this.assists.entrySet()) {
            try {
                FlightAssist assist = entry.getValue().apply(profile);
                if (assist != null) {
                    return new PickedAssist(entry.getKey(), assist);
                }
            } catch (RuntimeException e) {
                Constants.LOG.warn("Flughilfe {}: Erzeugen fehlgeschlagen", entry.getKey(), e);
            }
        }
        return null;
    }

    public synchronized List<ForceProvider> forcesFor(DroneBuild build) {
        if (this.forces.isEmpty()) {
            return List.of();
        }
        DroneProfile profile = profile(build);
        List<ForceProvider> out = new ArrayList<>();
        for (Map.Entry<String, Function<DroneProfile, ForceProvider>> entry : this.forces.entrySet()) {
            try {
                ForceProvider provider = entry.getValue().apply(profile);
                if (provider != null) {
                    out.add(provider);
                }
            } catch (RuntimeException e) {
                Constants.LOG.warn("Kräfte {}: Erzeugen fehlgeschlagen", entry.getKey(), e);
            }
        }
        return out;
    }

    public void attachForces(DroneSim sim, DroneBuild build) {
        sim.setForceProviders(this.forcesFor(build));
    }

    public static void logFailure(DroneSim sim) {
        RuntimeException failure = sim.consumeExtensionFailure();
        if (failure != null) {
            Constants.LOG.warn("Add-on-Erweiterung im Flugmodell abgeschaltet", failure);
        }
    }
}
