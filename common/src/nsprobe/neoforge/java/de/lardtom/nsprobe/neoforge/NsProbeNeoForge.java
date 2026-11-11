package de.lardtom.nsprobe.neoforge;

import de.lardtom.nsprobe.NsProbe;
import de.lardtom.nsprobe.NsProbePing;
import de.lardtom.nsprobe.client.NsProbeClient;
import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(NsProbe.MOD_ID)
public final class NsProbeNeoForge {
    public NsProbeNeoForge(IEventBus modBus, Dist dist) {
        modBus.addListener(RegisterEvent.class, event -> event.register(Registries.ITEM, NsProbe.ITEM_ID, NsProbe::item));
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional()
                .playToServer(NsProbePing.TYPE, NsProbePing.CODEC, (payload, context) -> NsProbe.ping(payload)));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> NsProbe.commands(event.getDispatcher()));
        if (dist.isClient()) {
            Client.register(modBus);
        }
        NsProbe.LOG.info("nsprobe: NeoForge probe active");
    }

    private static final class Client {
        private Client() {
        }

        static void register(IEventBus modBus) {
            modBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(NsProbeClient.keyMapping()));
            modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(NsProbeClient::init));
        }
    }
}
