package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.race.world.WorldCourses;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

import java.util.Optional;

public final class RaceModule {
    public static final RegHolder<EntityType<GateEntity>> GATE = PropwashRegistries.entity("gate",
            () -> EntityType.Builder.<GateEntity>of(GateEntity::new, MobCategory.MISC)
                    .sized(GateSize.MEDIUM.width(), GateSize.MEDIUM.height())
                    .clientTrackingRange(16)
                    .noUpdateInterval()
                    .dontTrackDeltas());

    public static final RegHolder<DataComponentType<Integer>> COURSE = PropwashRegistries.component("course",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final RegHolder<GateItem> GATE_SMALL = PropwashRegistries.item(ContentIds.GATE_SMALL.getPath(),
            p -> new GateItem(p, GateSize.SMALL), p -> p.stacksTo(16));
    public static final RegHolder<GateItem> GATE_MEDIUM = PropwashRegistries.item(ContentIds.GATE_MEDIUM.getPath(),
            p -> new GateItem(p, GateSize.MEDIUM), p -> p.stacksTo(16));
    public static final RegHolder<GateItem> GATE_LARGE = PropwashRegistries.item(ContentIds.GATE_LARGE.getPath(),
            p -> new GateItem(p, GateSize.LARGE), p -> p.stacksTo(16));
    public static final RegHolder<CourseToolItem> COURSE_TOOL = PropwashRegistries.item(ContentIds.COURSE_TOOL.getPath(),
            CourseToolItem::new, p -> p.stacksTo(1));
    public static final RegHolder<CourseElementItem> RACE_FLAG = PropwashRegistries.item(ContentIds.RACE_FLAG.getPath(),
            p -> new CourseElementItem(p, CourseElement.FLAG), p -> p.stacksTo(16));
    public static final RegHolder<CourseElementItem> SLALOM_POLE = PropwashRegistries.item(ContentIds.SLALOM_POLE.getPath(),
            p -> new CourseElementItem(p, CourseElement.POLE), p -> p.stacksTo(64));
    public static final RegHolder<CourseElementItem> DIVE_CUBE = PropwashRegistries.item(ContentIds.DIVE_CUBE.getPath(),
            p -> new CourseElementItem(p, CourseElement.CUBE), p -> p.stacksTo(16));
    public static final RegHolder<GatePoleItem> GATE_POLE = PropwashRegistries.item(ContentIds.GATE_POLE.getPath(),
            GatePoleItem::new, p -> p.stacksTo(16));
    public static final RegHolder<FreeformGateItem> FREEFORM_GATE = PropwashRegistries.item(ContentIds.FREEFORM_GATE.getPath(),
            FreeformGateItem::new, p -> p.stacksTo(16));

    private static boolean initialized;

    private RaceModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        WorldCourses.init();
        PropwashNet.registerS2C(RacePayloads.CourseSyncS2C.TYPE, RacePayloads.CourseSyncS2C.CODEC);
        PropwashNet.registerS2C(RacePayloads.RaceSettingsS2C.TYPE, RacePayloads.RaceSettingsS2C.CODEC);
        PropwashNet.registerC2S(RacePayloads.RaceReportC2S.TYPE, RacePayloads.RaceReportC2S.CODEC, RaceServer::onReport);
        PropwashNet.registerC2S(RacePayloads.LeaderboardRequestC2S.TYPE, RacePayloads.LeaderboardRequestC2S.CODEC, RaceServer::onLeaderboardRequest);
        PropwashNet.registerC2S(RacePayloads.GhostRequestC2S.TYPE, RacePayloads.GhostRequestC2S.CODEC, RaceServer::onGhostRequest);
        PropwashNet.registerC2S(RacePayloads.LobbyC2S.TYPE, RacePayloads.LobbyC2S.CODEC, RaceServer::onLobby);
        PropwashNet.registerC2S(RacePayloads.CourseEditC2S.TYPE, RacePayloads.CourseEditC2S.CODEC, CourseEdits::onEdit);
        PropwashNet.registerC2S(RacePayloads.CourseDefaultsC2S.TYPE, RacePayloads.CourseDefaultsC2S.CODEC, CourseEdits::onDefaults);
        PropwashNet.registerC2S(RacePayloads.GatePreferenceC2S.TYPE, RacePayloads.GatePreferenceC2S.CODEC, GateMounting::onPreference);
        PropwashNet.registerC2S(RacePayloads.GateRotateC2S.TYPE, RacePayloads.GateRotateC2S.CODEC, GateMounting::onRotate);
        PropwashNet.registerC2S(RacePayloads.GateEditC2S.TYPE, RacePayloads.GateEditC2S.CODEC, GateMounting::onEdit);
        PropwashNet.registerC2S(RacePayloads.GateSignalC2S.TYPE, RacePayloads.GateSignalC2S.CODEC, GateMounting::onSignal);
        PropwashNet.registerS2C(RacePayloads.LeaderboardS2C.TYPE, RacePayloads.LeaderboardS2C.CODEC);
        PropwashNet.registerS2C(RacePayloads.GhostS2C.TYPE, RacePayloads.GhostS2C.CODEC);
        PropwashNet.registerS2C(RacePayloads.LobbyS2C.TYPE, RacePayloads.LobbyS2C.CODEC);
        PropwashNet.registerS2C(RacePayloads.RaceStartS2C.TYPE, RacePayloads.RaceStartS2C.CODEC);
        ServerBus.STARTED.register(CourseRegistry::start);
        ServerBus.STARTED.register(server -> GateSignals.clear());
        ServerBus.STOPPED.register(server -> {
            RaceServer.clear();
            GateMounting.clear();
            GateSignals.clear();
            CourseEdits.clear();
            CourseRegistry.stop();
        });
        ServerBus.TICK_END.register(RaceServer::onServerTick);
        ServerBus.TICK_END.register(server -> GateSignals.tick());
        QuadcopterEntity.SERVER_MOVED.register(RaceServer::onDroneMoved);
        ServerBus.PLAYER_JOIN.register(player -> {
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                registry.sendAll(player);
            }
        });
        ServerBus.PLAYER_LEAVE.register(RaceServer::onLeave);
        ServerBus.PLAYER_LEAVE.register(GateMounting::forget);
        ServerBus.PLAYER_LEAVE.register(CourseEdits::forget);
        ServerBus.REGISTER_COMMANDS.register((dispatcher, context, selection) -> RaceCommands.register(dispatcher));
    }

    public static Optional<Item> item(CourseElement kind, GateSize size) {
        RegHolder<? extends Item> holder = switch (kind) {
            case GATE -> null;
            case FLAG -> RACE_FLAG;
            case POLE -> SLALOM_POLE;
            case CUBE -> DIVE_CUBE;
            case FREEFORM -> FREEFORM_GATE;
            case GATE_POLE -> GATE_POLE;
        };
        if (holder == null) {
            return gateItem(size);
        }
        try {
            return Optional.of(holder.get());
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    public static Optional<Item> gateItem(GateSize size) {
        RegHolder<GateItem> holder = switch (size) {
            case SMALL -> GATE_SMALL;
            case MEDIUM -> GATE_MEDIUM;
            case LARGE -> GATE_LARGE;
        };
        try {
            return Optional.of(holder.get());
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }
}
