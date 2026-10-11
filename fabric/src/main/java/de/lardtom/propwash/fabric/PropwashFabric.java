package de.lardtom.propwash.fabric;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.PropwashCommon;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.join.JoinCheck;
import de.lardtom.propwash.network.JoinInfoPayload;
import de.lardtom.propwash.network.PartsManifestPayload;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ProtocolVersionPayload;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.parts.AddonItems;
import de.lardtom.propwash.parts.PartsHandshake;
import de.lardtom.propwash.parts.PartsModule;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import java.util.function.Consumer;

public final class PropwashFabric implements ModInitializer {
    private static final Identifier JOIN_CHECK_PHASE = Constants.id("join_check");

    @Override
    public void onInitialize() {
        PropwashCommon.init();
        flushRegistries();
        AddonItems.INSTANCE.flush((key, item) -> Registry.register(BuiltInRegistries.ITEM, key, item), true);
        ExtensionTabs.INSTANCE.flush((key, tab) -> Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab), true);
        registerPayloads();
        registerServerEvents();
        FabricChestLoot.register();
        Constants.LOG.info("Propwash FPV geladen (Fabric)");
    }

    private static void flushRegistries() {
        for (PropwashRegistries.SerializerEntry entry : PropwashRegistries.flushEntityDataSerializers()) {
            FabricEntityDataRegistry.register(entry.id(), entry.serializer());
        }
        for (RegList<?> list : PropwashRegistries.inFlushOrder()) {
            flush(list);
        }
    }

    @SuppressWarnings("unchecked")
    private static <R> void flush(RegList<R> list) {
        Registry<?> raw = BuiltInRegistries.REGISTRY.getValue(list.registryKey().identifier());
        if (raw == null) {
            throw new IllegalStateException("Unbekanntes Register " + list.registryKey());
        }
        Registry<R> registry = (Registry<R>) raw;
        list.flush((key, value) -> Registry.register(registry, key, value));
    }

    private static void registerPayloads() {
        for (PropwashNet.C2S<?> c2s : PropwashNet.freezeC2S()) {
            registerC2S(c2s);
        }
        for (PropwashNet.S2C<?> s2c : PropwashNet.freezeS2C()) {
            registerS2C(s2c);
        }
        registerProtocolCheck();
    }

    private static void registerProtocolCheck() {
        PayloadTypeRegistry.clientboundConfiguration().register(ProtocolVersionPayload.TYPE, ProtocolVersionPayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(ProtocolVersionPayload.TYPE, ProtocolVersionPayload.CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(JoinInfoPayload.TYPE, JoinInfoPayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(JoinInfoPayload.TYPE, JoinInfoPayload.CODEC);
        PayloadTypeRegistry.clientboundConfiguration().register(PartsManifestPayload.TYPE, PartsManifestPayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(PartsManifestPayload.TYPE, PartsManifestPayload.CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(ProtocolVersionPayload.TYPE, (payload, context) -> {
            ProtocolVersionPayload server = ProtocolVersionPayload.local();
            if (server.compatibleWith(payload)) {
                context.packetListener().completeTask(ProtocolCheckTask.TYPE);
            } else {
                Constants.LOG.info("Propwash-Protokoll passt nicht: Server {} ({}), Client {} ({})",
                        server.modVersion(), server.protocol(), payload.modVersion(), payload.protocol());
                context.packetListener().disconnect(ProtocolVersionPayload.mismatchMessage(server, payload));
            }
        });
        ServerConfigurationNetworking.registerGlobalReceiver(JoinInfoPayload.TYPE, (payload, context) -> {
            Component reject = JoinCheck.onClientInfo(context.packetListener(), payload.info());
            if (reject == null) {
                context.packetListener().completeTask(JoinCheck.INFO_TASK);
            } else {
                context.packetListener().disconnect(reject);
            }
        });
        ServerConfigurationNetworking.registerGlobalReceiver(PartsManifestPayload.TYPE, (payload, context) -> {
            Component reject = JoinCheck.onClientParts(context.packetListener(), payload);
            if (reject == null) {
                context.packetListener().completeTask(PartsHandshake.TASK_TYPE);
            } else {
                context.packetListener().disconnect(reject);
            }
        });
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.addPhaseOrdering(JOIN_CHECK_PHASE, Event.DEFAULT_PHASE);
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register(JOIN_CHECK_PHASE, (listener, server) -> {
            if (ServerConfigurationNetworking.canSend(listener, JoinInfoPayload.TYPE)) {
                listener.addTask(new JoinInfoTask());
                addPartsCheck(listener);
            } else if (ServerConfigurationNetworking.canSend(listener, ProtocolVersionPayload.TYPE)) {
                listener.addTask(new ProtocolCheckTask());
                addPartsCheck(listener);
            } else if (!ServerConfigurationNetworking.getSendable(listener).isEmpty()) {
                listener.disconnect(JoinCheck.missingPropwash());
            }
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            if (!ServerConfigurationNetworking.canSend(listener, JoinInfoPayload.TYPE)
                    && !ServerConfigurationNetworking.canSend(listener, ProtocolVersionPayload.TYPE)) {
                listener.disconnect(JoinCheck.missingPropwash());
            }
        });
    }

    private static void addPartsCheck(ServerConfigurationPacketListenerImpl listener) {
        if (ServerConfigurationNetworking.canSend(listener, PartsManifestPayload.TYPE)) {
            listener.addTask(new PartsCheckTask());
        } else {
            listener.disconnect(JoinCheck.missingPropwash());
        }
    }

    private record JoinInfoTask() implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(JoinCheck.localPayload()));
        }

        @Override
        public ConfigurationTask.Type type() {
            return JoinCheck.INFO_TASK;
        }
    }

    private record PartsCheckTask() implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(PartsManifestPayload.local()));
        }

        @Override
        public ConfigurationTask.Type type() {
            return PartsHandshake.TASK_TYPE;
        }
    }

    private record ProtocolCheckTask() implements ConfigurationTask {
        static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(ProtocolVersionPayload.TYPE.id().toString());

        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(ProtocolVersionPayload.local()));
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }

    private static <T extends CustomPacketPayload> void registerC2S(PropwashNet.C2S<T> c2s) {
        PayloadTypeRegistry.serverboundPlay().register(c2s.type(), c2s.codec());
        ServerPlayNetworking.registerGlobalReceiver(c2s.type(), (payload, context) ->
                c2s.handler().handle(payload, new ServerPayloadContext(context.player())));
    }

    private static <T extends CustomPacketPayload> void registerS2C(PropwashNet.S2C<T> s2c) {
        PayloadTypeRegistry.clientboundPlay().register(s2c.type(), s2c.codec());
    }

    private static void registerServerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(server -> ServerBus.TICK_END.fire(l -> l.accept(server)));
        ServerPlayerEvents.JOIN.register(player -> ServerBus.PLAYER_JOIN.fire(l -> l.accept(player)));
        ServerPlayerEvents.LEAVE.register(player -> ServerBus.PLAYER_LEAVE.fire(l -> l.accept(player)));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> PartsModule.freeze());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ServerBus.STARTED.fire(l -> l.accept(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerBus.STOPPED.fire(l -> l.accept(server)));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                ServerBus.REGISTER_COMMANDS.fire(l -> l.register(dispatcher, context, selection)));
        ServerLevelEvents.UNLOAD.register((server, level) -> ServerBus.LEVEL_UNLOAD.fire(l -> l.accept(level)));
    }
}
