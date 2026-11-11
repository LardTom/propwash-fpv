package de.lardtom.propwash.input.addon.client;

import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.addon.AddonInputState;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.addon.AddonSwitchEvaluator;
import de.lardtom.propwash.input.net.AddonFeaturesS2C;
import de.lardtom.propwash.network.client.ClientNet;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class AddonSwitchClient {
    private static final AddonSwitchSender SENDER = new AddonSwitchSender();
    private static boolean initialized;

    private AddonSwitchClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RadioState.INSTANCE.addonStage(AddonRadioStage.INSTANCE);
        AddonInputState.local(new AddonInputState.Local() {
            @Override
            public boolean active(Entity drone, Identifier id) {
                return drone == pilotDrone() && switchActive(id);
            }

            @Override
            public double axis(Entity drone, Identifier id, double center) {
                return drone == pilotDrone() ? AddonSwitchClient.axis(id, center) : center;
            }
        });
        ClientNet.handle(AddonFeaturesS2C.TYPE, (payload, context) -> AddonServerFeatures.accept(payload));
        ClientBus.TICK_END.register(AddonSwitchClient::tick);
        ClientBus.DISCONNECT.register(minecraft -> {
            AddonServerFeatures.reset();
            SENDER.reset();
            AddonRadioStage.INSTANCE.reset();
        });
        AccessoryHintClient.init();
    }

    private static void tick(Minecraft minecraft) {
        SENDER.tick(pilotDrone());
        AccessoryHintClient.tick(minecraft);
    }

    public static @Nullable QuadcopterEntity pilotDrone() {
        PilotSession session = PilotSession.INSTANCE;
        return session.isActive() ? session.drone() : null;
    }

    public static boolean localActive(Identifier id) {
        int index = AddonInputs.switchIndex(id);
        return index >= 0 && AddonSwitchEvaluator.isSet(RadioState.INSTANCE.addonSwitches(), index);
    }

    public static double localAxis(Identifier id, double center) {
        int index = AddonInputs.axisIndex(id);
        float[] values = RadioState.INSTANCE.addonAxes();
        return index >= 0 && index < values.length ? values[index] : center;
    }

    public static boolean switchActive(Identifier id) {
        SwitchSpec spec = AddonInputs.switchSpec(id);
        if (spec == null || pilotDrone() == null || AddonServerFeatures.inactive(spec)) {
            return false;
        }
        return localActive(id);
    }

    public static double axis(Identifier id, double center) {
        AxisSpec spec = AddonInputs.axisSpec(id);
        if (spec == null || pilotDrone() == null || AddonServerFeatures.inactive(spec)) {
            return spec == null ? center : spec.center();
        }
        return localAxis(id, spec.center());
    }

    public static int switchPackets() {
        return SENDER.switchPackets();
    }

    public static int axisPackets() {
        return SENDER.axisPackets();
    }
}
