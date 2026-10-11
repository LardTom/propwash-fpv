package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.ConfiguratorSafety;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class PilotGuard {
    private static final Object TOAST_TOKEN = new Object();

    private PilotGuard() {
    }

    public static ConfiguratorSafety.Decision evaluate() {
        PilotSession session = PilotSession.INSTANCE;
        boolean piloting = session.isActive();
        DroneSim sim = piloting ? session.sim() : null;
        QuadcopterEntity drone = piloting ? session.drone() : null;
        boolean armed;
        boolean grounded;
        if (sim != null) {
            Telemetry t = sim.telemetry();
            armed = t.armed;
            grounded = t.grounded || t.sleeping;
        } else {
            armed = drone != null && drone.isArmed();
            grounded = drone == null || drone.onGround();
        }
        return ConfiguratorSafety.decide(piloting, armed, grounded, sim != null);
    }

    public static boolean prepareOpen(Minecraft minecraft) {
        ConfiguratorSafety.Decision decision = evaluate();
        switch (decision) {
            case DISARM_THEN_OPEN -> {
                DroneSim sim = PilotSession.INSTANCE.sim();
                if (sim != null) {
                    sim.forceDisarm(DisarmReason.FORCED);
                }
            }
            case REFUSE_AIRBORNE -> notify(minecraft, "propwash.ui.guard.airborne");
            case REFUSE_ARMED -> notify(minecraft, "propwash.ui.guard.armed");
            default -> {
            }
        }
        return decision.opens();
    }

    public static boolean lockoutActive() {
        return PilotSession.INSTANCE.isActive();
    }

    public static boolean enforceLockout() {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.isActive() ? session.sim() : null;
        if (sim == null) {
            return false;
        }
        if (ConfiguratorSafety.lockoutDisarm(true, true, sim.telemetry().armed)) {
            sim.forceDisarm(DisarmReason.FORCED);
            return true;
        }
        return false;
    }

    private static void notify(Minecraft minecraft, String key) {
        if (minecraft.player != null) {
            ThemedToast.show(minecraft.gui.toastManager(), TOAST_TOKEN, Component.translatable(key), null, Palette.WARNING, 3000L);
        }
    }
}
