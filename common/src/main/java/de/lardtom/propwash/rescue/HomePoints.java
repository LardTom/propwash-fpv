package de.lardtom.propwash.rescue;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.rescue.net.HomePointC2S;
import de.lardtom.propwash.rescue.net.HomePointS2C;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class HomePoints {
    private static final String KEY = "propwash.rescue.home_point.";

    private HomePoints() {
    }

    static void init() {
        PropwashNet.registerC2S(HomePointC2S.TYPE, HomePointC2S.CODEC, HomePoints::onSet);
        PropwashNet.registerS2C(HomePointS2C.TYPE, HomePointS2C.CODEC);
        QuadcopterEntity.PILOT_CHANGED.register(HomePoints::onPilotChanged);
        QuadcopterEntity.HOME_EDITED.register(drone -> sendToPilot(drone, true));
    }

    public static void prepareEdit(QuadcopterEntity drone, CompoundTag tag) throws CommandSyntaxException {
        Tag raw = tag.get(HomePointCodecs.KEY);
        if (raw == null) {
            return;
        }
        Level level = drone.level();
        HomePoint current = drone.homePoint();
        Optional<HomePoint> parsed = HomePointCodecs.CODEC.parse(NbtOps.INSTANCE, raw).result();
        if (parsed.isEmpty()) {
            throw error("invalid", "HomePoint needs x, y and z as numbers; dimension, game_time and source are optional");
        }
        HomePoint edited = parsed.get();
        String dimension = HomePointCodecs.dimension(level);
        boolean inside = level.getWorldBorder().isWithinBounds(edited.x(), edited.z());
        HomePointRules.Edit edit = HomePointRules.edit(current, edited, dimension, level.getMinY(), level.getMaxY(), inside,
                level.getGameTime());
        HomePointRules.Problem problem = edit.problem();
        if (problem != null || edit.home() == null) {
            throw switch (problem == null ? HomePointRules.Problem.INVALID : problem) {
                case INVALID -> error("invalid", "HomePoint needs x, y and z as numbers; dimension, game_time and source are optional");
                case DIMENSION -> error("dimension", "HomePoint must be in the drone's dimension (%s)", dimension);
                case HEIGHT -> error("height", "HomePoint y must be between %s and %s",
                        number(HomePointRules.lowest(level.getMinY())), number(HomePointRules.highest(level.getMaxY())));
                case BORDER -> error("border", "HomePoint is outside the world border");
            };
        }
        tag.put(HomePointCodecs.KEY, HomePointCodecs.CODEC.encodeStart(NbtOps.INSTANCE, edit.home()).getOrThrow());
    }

    public static void onSet(HomePointC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        HomePoint.Source source = payload.source();
        if (source == null || !source.fromPilot() || !(level.getEntity(payload.entityId()) instanceof QuadcopterEntity drone)
                || drone.isRemoved() || !drone.isFlownBy(player.getUUID())) {
            return;
        }
        String dimension = HomePointCodecs.dimension(level);
        if (HomePointRules.check(payload.x(), payload.y(), payload.z(), dimension, dimension, level.getMinY(), level.getMaxY(),
                level.getWorldBorder().isWithinBounds(payload.x(), payload.z())) != null) {
            return;
        }
        drone.setHomePoint(new HomePoint(payload.x(), payload.y(), payload.z(), dimension, level.getGameTime(), source), payload.seq());
    }

    private static void onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous, Optional<UUID> current) {
        if (current.isPresent()) {
            sendToPilot(drone, false);
        }
    }

    private static void sendToPilot(QuadcopterEntity drone, boolean edited) {
        if (drone.pilotEntity() instanceof ServerPlayer player && player.connection != null && player.connection.isAcceptingMessages()) {
            PropwashNet.sendToPlayer(player, new HomePointS2C(drone.getId(), drone.homeAck(), edited, drone.homePoint()));
        }
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static CommandSyntaxException error(String key, String fallback, Object... args) {
        return new SimpleCommandExceptionType(Component.translatableWithFallback(KEY + key, fallback, args)).create();
    }
}
