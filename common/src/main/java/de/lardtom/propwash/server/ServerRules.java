package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.rules.RuleEffects;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.video.SpectateServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.Optional;

public final class ServerRules {
    private static final Permission[] LEVELS = {
            new Permission.HasCommandLevel(PermissionLevel.ALL),
            new Permission.HasCommandLevel(PermissionLevel.MODERATORS),
            new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS),
            new Permission.HasCommandLevel(PermissionLevel.ADMINS),
            new Permission.HasCommandLevel(PermissionLevel.OWNERS)
    };

    private ServerRules() {
    }

    public static boolean mayPilot(ServerPlayer player, QuadcopterEntity drone) {
        return pilotDenialReason(player, drone).isEmpty();
    }

    public static Optional<Component> pilotDenialReason(ServerPlayer player, QuadcopterEntity drone) {
        try {
            ServerRulesConfig config = ServerRulesConfig.get();
            if (!hasPermissionLevel(player, config.pilotPermissionLevel)) {
                return Optional.of(Component.translatable("propwash.server.deny.permission"));
            }
            if (player.level() != drone.level()) {
                return Optional.of(Component.translatable("propwash.server.deny.dimension"));
            }
            if (inForbiddenSpawnArea(player, drone)) {
                return Optional.of(Component.translatable("propwash.server.deny.spawn_protection"));
            }
            RuleValues rules = WorldRules.values(player.level().getServer());
            if (RuleEffects.channelsFull(rules, SpectateServer.transmittersExcept(drone, player.getUUID()))) {
                return Optional.of(Component.translatable("propwash.server.deny.channels_full", RuleEffects.maxVideoChannels(rules)));
            }
            if (refusesParts(RuleEffects.partCompatibility(rules), drone.build())) {
                return Optional.of(Component.translatable("propwash.server.deny.parts_incompatible"));
            }
            double limit = maxPilotDistance(player);
            if (player.position().distanceTo(drone.position()) > limit) {
                return Optional.of(Component.translatable("propwash.server.deny.too_far", Math.round(limit)));
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            Constants.LOG.error("ServerRules.pilotDenialReason fehlgeschlagen", e);
            return Optional.empty();
        }
    }

    public static boolean refusesParts(PartCompatibility rule, DroneBuild build) {
        return rule.enforced() && !build.buildable() && build.buildable(PartCompatibility.LIFTED);
    }

    public static double maxPilotDistance(ServerPlayer player) {
        try {
            return unlimitedRange(player) ? Double.POSITIVE_INFINITY : limitedRange(player);
        } catch (RuntimeException e) {
            Constants.LOG.error("ServerRules.maxPilotDistance fehlgeschlagen", e);
            return Double.POSITIVE_INFINITY;
        }
    }

    public static double maxDroneSpeed(ServerPlayer player) {
        double speed = ServerRulesConfig.get().maxDroneSpeed;
        return speed > 0.0 ? speed : Double.POSITIVE_INFINITY;
    }

    public static boolean unlimitedRange(ServerPlayer player) {
        ServerRulesConfig config = ServerRulesConfig.get();
        return config.allowUnlimitedRange && hasPermissionLevel(player, config.unlimitedRangePermissionLevel);
    }

    public static boolean mayGenerateChunks(ServerPlayer player) {
        try {
            return WorldRules.on(player.level().getServer(), WorldRuleSpecs.DRONES_GENERATE_CHUNKS)
                    && ServerRulesConfig.get().allowsGeneration(isHost(player), level -> hasPermissionLevel(player, level));
        } catch (RuntimeException e) {
            Constants.LOG.error("ServerRules.mayGenerateChunks fehlgeschlagen", e);
            return false;
        }
    }

    public static boolean isHost(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return server != null && !server.isDedicatedServer() && server.isSingleplayerOwner(player.nameAndId());
    }

    public static boolean isAnchored(ServerPlayer player) {
        return ViewAnchors.isAnchored(player);
    }

    public static double limitedRange(ServerPlayer player) {
        return AnchorRules.limitedRange(ServerRulesConfig.get().maxPilotDistance, playerViewDistance(player));
    }

    public static int playerViewDistance(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        int serverView = server != null ? server.getPlayerList().getViewDistance() : StreamBudget.MAX_VIEW_DISTANCE;
        return Math.max(StreamBudget.MIN_VIEW_DISTANCE, Math.min(player.requestedViewDistance(), Math.max(StreamBudget.MIN_VIEW_DISTANCE, serverView)));
    }

    public static int droneViewDistance(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        int serverView = server != null ? server.getPlayerList().getViewDistance() : StreamBudget.MAX_VIEW_DISTANCE;
        return StreamBudget.wantedViewDistance(player.requestedViewDistance(), serverView, ServerRulesConfig.get().droneViewDistance);
    }

    public static boolean hasPermissionLevel(ServerPlayer player, int level) {
        if (level <= 0) {
            return true;
        }
        return player.permissions().hasPermission(LEVELS[Math.min(level, LEVELS.length - 1)]);
    }

    public static boolean forbiddenSpawnArea(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (ServerRulesConfig.get().allowPilotingInSpawnProtection) {
            return false;
        }
        MinecraftServer server = level.getServer();
        return server != null && server.isUnderSpawnProtection(level, pos, player);
    }

    static boolean inForbiddenSpawnArea(ServerPlayer player, QuadcopterEntity drone) {
        if (ServerRulesConfig.get().allowPilotingInSpawnProtection || !(drone.level() instanceof ServerLevel level)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        return server != null && server.isUnderSpawnProtection(level, drone.blockPosition(), player);
    }

    public static ServerRulesS2C snapshotFor(ServerPlayer player) {
        ServerRulesConfig config = ServerRulesConfig.get();
        return new ServerRulesS2C(
                hasPermissionLevel(player, config.pilotPermissionLevel),
                config.allowUnlimitedRange,
                unlimitedRange(player),
                config.unlimitedRangePermissionLevel,
                limitedRange(player),
                config.maxDroneSpeed,
                droneViewDistance(player),
                config.maxStreamedChunks,
                config.onlyGeneratedChunks,
                config.generateChunksPermissionLevel,
                mayGenerateChunks(player),
                config.allowPilotingInSpawnProtection,
                config.allowSpectating);
    }

    public static boolean spectatingAllowed() {
        try {
            return ServerRulesConfig.get().allowSpectating;
        } catch (RuntimeException e) {
            Constants.LOG.error("ServerRules.spectatingAllowed fehlgeschlagen", e);
            return false;
        }
    }

    public static boolean worldCourses() {
        try {
            return ServerRulesConfig.get().worldCourses;
        } catch (RuntimeException e) {
            Constants.LOG.error("ServerRules.worldCourses fehlgeschlagen", e);
            return true;
        }
    }
}
