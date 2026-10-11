package de.lardtom.propwash.neoforge;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.PropwashCommon;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.join.JoinCheck;
import de.lardtom.propwash.join.JoinClientState;
import de.lardtom.propwash.mixin.join.ServerConfigurationPacketListenerImplAccessor;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

@Mod(Constants.MOD_ID)
public final class PropwashNeoForge {
    public PropwashNeoForge(IEventBus modBus, ModContainer container) {
        PropwashCommon.init();
        modBus.addListener(RegisterEvent.class, PropwashNeoForge::onRegister);
        modBus.addListener(RegisterPayloadHandlersEvent.class, PropwashNeoForge::onRegisterPayloads);
        modBus.addListener(RegisterConfigurationTasksEvent.class, PropwashNeoForge::onConfigurationTasks);
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(PartsModule::freeze));
        registerServerEvents();
        Constants.LOG.info("Propwash FPV geladen (NeoForge)");
    }

    private static void onRegister(RegisterEvent event) {
        for (RegList<?> list : PropwashRegistries.inFlushOrder()) {
            flush(event, list);
        }
        event.register(Registries.ITEM, helper -> AddonItems.INSTANCE.flush(helper::register, false));
        event.register(Registries.CREATIVE_MODE_TAB, helper -> ExtensionTabs.INSTANCE.flush(helper::register, false));
        event.register(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, helper -> {
            for (PropwashRegistries.SerializerEntry entry : PropwashRegistries.flushEntityDataSerializers()) {
                helper.register(entry.id(), entry.serializer());
            }
        });
    }

    private static <R> void flush(RegisterEvent event, RegList<R> list) {
        event.register(list.registryKey(), helper -> list.flush((key, value) -> helper.register(key, value)));
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Integer.toString(ProtocolVersionPayload.localProtocol()));
        for (PropwashNet.C2S<?> c2s : PropwashNet.freezeC2S()) {
            registerC2S(registrar, c2s);
        }
        for (PropwashNet.S2C<?> s2c : PropwashNet.freezeS2C()) {
            registerS2C(registrar, s2c);
        }
        registrar.configurationBidirectional(JoinInfoPayload.TYPE, JoinInfoPayload.CODEC, (payload, context) -> {
            Component reject = JoinCheck.onClientInfo(context.listener(), payload.info());
            if (reject == null) {
                context.finishCurrentTask(JoinCheck.INFO_TASK);
            } else {
                context.disconnect(reject);
            }
        }, (payload, context) -> context.reply(JoinClientState.onServerInfo(payload)));
        registrar.configurationBidirectional(PartsManifestPayload.TYPE, PartsManifestPayload.CODEC, (payload, context) -> {
            Component reject = JoinCheck.onClientParts(context.listener(), payload);
            if (reject == null) {
                context.finishCurrentTask(PartsHandshake.TASK_TYPE);
            } else {
                context.disconnect(reject);
            }
        }, (payload, context) -> context.reply(JoinClientState.onServerParts(payload)));
    }

    private static void onConfigurationTasks(RegisterConfigurationTasksEvent event) {
        ServerConfigurationPacketListener listener = event.getListener();
        List<ConfigurationTask> early = new ArrayList<>();
        if (listener.hasChannel(JoinInfoPayload.TYPE)) {
            early.add(new JoinInfoTask());
        }
        if (listener.hasChannel(PartsManifestPayload.TYPE)) {
            early.add(new PartsCheckTask());
        }
        if (early.isEmpty()) {
            return;
        }
        if (listener instanceof ServerConfigurationPacketListenerImplAccessor accessor) {
            Queue<ConfigurationTask> queue = accessor.propwash$configurationTasks();
            List<ConfigurationTask> later = new ArrayList<>(queue);
            queue.clear();
            queue.addAll(early);
            queue.addAll(later);
        } else {
            early.forEach(event::register);
        }
    }

    private record JoinInfoTask() implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(JoinCheck.localPayload());
        }

        @Override
        public ConfigurationTask.Type type() {
            return JoinCheck.INFO_TASK;
        }
    }

    private record PartsCheckTask() implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(PartsManifestPayload.local());
        }

        @Override
        public ConfigurationTask.Type type() {
            return PartsHandshake.TASK_TYPE;
        }
    }

    private static <T extends CustomPacketPayload> void registerC2S(PayloadRegistrar registrar, PropwashNet.C2S<T> c2s) {
        registrar.playToServer(c2s.type(), c2s.codec(), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                c2s.handler().handle(payload, new ServerPayloadContext(player));
            }
        });
    }

    private static <T extends CustomPacketPayload> void registerS2C(PayloadRegistrar registrar, PropwashNet.S2C<T> s2c) {
        registrar.playToClient(s2c.type(), s2c.codec());
    }

    private static void registerServerEvents() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerTickEvent.Post.class, e -> ServerBus.TICK_END.fire(l -> l.accept(e.getServer())));
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ServerBus.PLAYER_JOIN.fire(l -> l.accept(player));
            }
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                ServerBus.PLAYER_LEAVE.fire(l -> l.accept(player));
            }
        });
        bus.addListener(ServerStartedEvent.class, e -> ServerBus.STARTED.fire(l -> l.accept(e.getServer())));
        bus.addListener(ServerStoppedEvent.class, e -> ServerBus.STOPPED.fire(l -> l.accept(e.getServer())));
        bus.addListener(RegisterCommandsEvent.class, e ->
                ServerBus.REGISTER_COMMANDS.fire(l -> l.register(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection())));
        bus.addListener(LevelEvent.Unload.class, e -> {
            if (e.getLevel() instanceof ServerLevel level) {
                ServerBus.LEVEL_UNLOAD.fire(l -> l.accept(level));
            }
        });
        NeoForgeLootInjections.register(bus);
    }
}
