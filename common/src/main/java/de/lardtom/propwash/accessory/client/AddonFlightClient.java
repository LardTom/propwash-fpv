package de.lardtom.propwash.accessory.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.accessory.DroneQueries;
import de.lardtom.propwash.accessory.FlightHooks;
import de.lardtom.propwash.api.drone.ObstacleScan;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.PilotInput;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class AddonFlightClient {
    public static final AddonFlightClient INSTANCE = new AddonFlightClient();
    private static final Object TOAST = new Object();
    private static final long TOAST_MILLIS = 4000L;

    private @Nullable DroneSim attachedSim;
    private @Nullable DroneBuild attachedBuild;
    private @Nullable FlightAssist assist;
    private @Nullable String assistId;
    private @Nullable ObstacleScan lastScan;
    private int returnHomeRequests;

    private AddonFlightClient() {
    }

    public void beforeFrame(Minecraft minecraft, QuadcopterEntity drone, DroneSim sim, PilotInput input, DroneBuild flown) {
        input.actions = AccessoryActionInput.INSTANCE.bits();
        if (!FlightHooks.INSTANCE.any()) {
            return;
        }
        DroneBuild base = AccessoryPayload.unladen(flown);
        if (sim != this.attachedSim || !base.equals(this.attachedBuild)) {
            this.attachedSim = sim;
            this.attachedBuild = base;
            FlightHooks.PickedAssist picked = FlightHooks.INSTANCE.pickAssist(flown);
            this.assist = picked == null ? null : picked.assist();
            this.assistId = picked == null ? null : picked.id();
            sim.setAssist(null);
            FlightHooks.INSTANCE.attachForces(sim, flown);
        }
        boolean rescue = RescueController.INSTANCE.active();
        FlightAssist wanted = rescue ? null : this.assist;
        sim.setAssist(wanted);
        if (wanted != null) {
            double range = sim.assistSensorRange();
            if (range > 0.0) {
                ObstacleScan scan = DroneQueries.INSTANCE.scan(drone, range);
                this.lastScan = scan;
                for (SensorDirection direction : SensorDirection.values()) {
                    double distance = scan.distance(direction);
                    sim.setObstacleDistance(direction, distance >= range ? Double.POSITIVE_INFINITY : distance);
                }
            }
            if (sim.consumeReturnHomeRequest() && !RescueController.INSTANCE.active()) {
                this.returnHomeRequests++;
                RescueController.INSTANCE.requestToggle();
            }
        }
        RuntimeException failure = sim.consumeExtensionFailure();
        if (failure != null) {
            Constants.LOG.warn("Add-on-Erweiterung im Flugmodell abgeschaltet", failure);
            if (sim.assist() == null) {
                this.assist = null;
                this.assistId = null;
            }
            if (minecraft.player != null) {
                ThemedToast.show(minecraft.gui.toastManager(), TOAST, Component.translatable("propwash.accessory.assist.failed"), null,
                        Palette.WARNING, TOAST_MILLIS);
            }
        }
    }

    public @Nullable FlightAssist assist() {
        return this.assist;
    }

    public @Nullable String engagedAssist(@Nullable DroneSim sim) {
        return sim != null && sim == this.attachedSim && this.assistId != null && sim.assist() == this.assist && sim.assistEngaged() ? this.assistId
                : null;
    }

    public @Nullable ObstacleScan lastScan() {
        return this.lastScan;
    }

    public int returnHomeRequests() {
        return this.returnHomeRequests;
    }

    public void end() {
        this.attachedSim = null;
        this.attachedBuild = null;
        this.assist = null;
        this.assistId = null;
        this.lastScan = null;
    }
}
