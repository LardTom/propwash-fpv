package de.lardtom.nsprobe.fabric;

import de.lardtom.nsprobe.client.NsProbeClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public final class NsProbeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(NsProbeClient.keyMapping());
        NsProbeClient.init();
    }
}
