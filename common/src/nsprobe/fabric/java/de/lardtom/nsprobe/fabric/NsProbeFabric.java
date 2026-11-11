package de.lardtom.nsprobe.fabric;

import de.lardtom.nsprobe.NsProbe;
import de.lardtom.nsprobe.NsProbePing;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class NsProbeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.ITEM, NsProbe.ITEM_ID, NsProbe.item());
        PayloadTypeRegistry.serverboundPlay().register(NsProbePing.TYPE, NsProbePing.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NsProbePing.TYPE, (payload, context) -> NsProbe.ping(payload));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> NsProbe.commands(dispatcher));
        NsProbe.LOG.info("nsprobe: Fabric probe active");
    }
}
