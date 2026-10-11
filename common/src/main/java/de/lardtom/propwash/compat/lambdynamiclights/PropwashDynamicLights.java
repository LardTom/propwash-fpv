package de.lardtom.propwash.compat.lambdynamiclights;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.DroneModule;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;

public final class PropwashDynamicLights implements DynamicLightsInitializer {
    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        DroneLedLuminance.TYPE.id();
        context.entityLightSourceManager().onRegisterEvent().register(registration ->
                registration.register(DroneModule.DRONE.get(), DroneLedLuminance.INSTANCE));
        LambDynamicLightsCompat.markInitialized();
        Constants.LOG.info("LambDynamicLights erkannt: LED-Streifen der Drohnen sind echte Lichtquellen");
    }
}
