package de.lardtom.propwash.server;

import de.lardtom.propwash.config.JsonConfig;

import java.util.function.IntPredicate;

public final class ServerRulesConfig {
    public static final double DEFAULT_MAX_PILOT_DISTANCE = 160.0;
    public static final double DEFAULT_MAX_DRONE_SPEED = 100.0;
    public static final int DEFAULT_DRONE_VIEW_DISTANCE = 12;
    public static final int DEFAULT_MAX_STREAMED_CHUNKS = 4096;
    public static final int DEFAULT_CHUNK_CHECKS_PER_TICK = 48;
    public static final int DEFAULT_GENERATE_CHUNKS_PERMISSION_LEVEL = 0;
    public static final int GENERATE_NOBODY = 5;
    public static final double DEFAULT_BATTERY_CHARGE_SECONDS = 90.0;
    public static final double DEFAULT_BATTERY_CHARGE_EXPONENT = 0.5;
    public static final double DEFAULT_BATTERY_CHARGE_MIN_SECONDS = 20.0;
    public static final double DEFAULT_REDSTONE_CHARGE_MULTIPLIER = 2.0;

    public static final JsonConfig<ServerRulesConfig> CONFIG = JsonConfig.create("server", ServerRulesConfig.class,
            ServerRulesConfig::new, ServerRulesConfig::validate);

    public int pilotPermissionLevel = 0;
    public boolean allowUnlimitedRange = true;
    public int unlimitedRangePermissionLevel = 0;
    public double maxPilotDistance = DEFAULT_MAX_PILOT_DISTANCE;
    public double maxDroneSpeed = DEFAULT_MAX_DRONE_SPEED;
    public int droneViewDistance = DEFAULT_DRONE_VIEW_DISTANCE;
    public int maxStreamedChunks = DEFAULT_MAX_STREAMED_CHUNKS;
    public int chunkChecksPerTick = DEFAULT_CHUNK_CHECKS_PER_TICK;
    public boolean onlyGeneratedChunks = true;
    public int generateChunksPermissionLevel = DEFAULT_GENERATE_CHUNKS_PERMISSION_LEVEL;
    public boolean hostGeneratesChunks = true;
    public boolean allowPilotingInSpawnProtection = true;
    public double batteryChargeSeconds = DEFAULT_BATTERY_CHARGE_SECONDS;
    public double batteryChargeCapacityExponent = DEFAULT_BATTERY_CHARGE_EXPONENT;
    public double batteryChargeMinSeconds = DEFAULT_BATTERY_CHARGE_MIN_SECONDS;
    public double redstoneChargeMultiplier = DEFAULT_REDSTONE_CHARGE_MULTIPLIER;
    public boolean worldCourses = true;
    public boolean allowSpectating = true;

    public static ServerRulesConfig get() {
        return CONFIG.get();
    }

    public static void validate(ServerRulesConfig config) {
        config.pilotPermissionLevel = clamp(config.pilotPermissionLevel, 0, 4);
        config.unlimitedRangePermissionLevel = clamp(config.unlimitedRangePermissionLevel, 0, 4);
        config.maxPilotDistance = clamp(config.maxPilotDistance, 16.0, 1_000_000.0, DEFAULT_MAX_PILOT_DISTANCE);
        config.maxDroneSpeed = clamp(config.maxDroneSpeed, 0.0, 10_000.0, DEFAULT_MAX_DRONE_SPEED);
        config.droneViewDistance = clamp(config.droneViewDistance, StreamBudget.MIN_VIEW_DISTANCE, StreamBudget.MAX_VIEW_DISTANCE);
        config.maxStreamedChunks = clamp(config.maxStreamedChunks, StreamBudget.cost(StreamBudget.MIN_VIEW_DISTANCE), 1_000_000);
        config.chunkChecksPerTick = clamp(config.chunkChecksPerTick, 1, 1024);
        config.generateChunksPermissionLevel = clamp(config.generateChunksPermissionLevel, 0, GENERATE_NOBODY);
        config.batteryChargeSeconds = clamp(config.batteryChargeSeconds, 10.0, 3600.0, DEFAULT_BATTERY_CHARGE_SECONDS);
        config.batteryChargeCapacityExponent = clamp(config.batteryChargeCapacityExponent, 0.0, 1.0, DEFAULT_BATTERY_CHARGE_EXPONENT);
        config.batteryChargeMinSeconds = clamp(config.batteryChargeMinSeconds, 1.0, 3600.0, DEFAULT_BATTERY_CHARGE_MIN_SECONDS);
        config.redstoneChargeMultiplier = clamp(config.redstoneChargeMultiplier, 1.0, 10.0, DEFAULT_REDSTONE_CHARGE_MULTIPLIER);
    }

    public boolean allowsGeneration(boolean host, IntPredicate hasPermissionLevel) {
        if (!this.onlyGeneratedChunks) {
            return true;
        }
        if (this.hostGeneratesChunks && host) {
            return true;
        }
        return this.generateChunksPermissionLevel < GENERATE_NOBODY && hasPermissionLevel.test(this.generateChunksPermissionLevel);
    }

    public ServerRulesConfig copy() {
        ServerRulesConfig copy = new ServerRulesConfig();
        copy.assign(this);
        return copy;
    }

    public void assign(ServerRulesConfig other) {
        this.pilotPermissionLevel = other.pilotPermissionLevel;
        this.allowUnlimitedRange = other.allowUnlimitedRange;
        this.unlimitedRangePermissionLevel = other.unlimitedRangePermissionLevel;
        this.maxPilotDistance = other.maxPilotDistance;
        this.maxDroneSpeed = other.maxDroneSpeed;
        this.droneViewDistance = other.droneViewDistance;
        this.maxStreamedChunks = other.maxStreamedChunks;
        this.chunkChecksPerTick = other.chunkChecksPerTick;
        this.onlyGeneratedChunks = other.onlyGeneratedChunks;
        this.generateChunksPermissionLevel = other.generateChunksPermissionLevel;
        this.hostGeneratesChunks = other.hostGeneratesChunks;
        this.allowPilotingInSpawnProtection = other.allowPilotingInSpawnProtection;
        this.batteryChargeSeconds = other.batteryChargeSeconds;
        this.batteryChargeCapacityExponent = other.batteryChargeCapacityExponent;
        this.batteryChargeMinSeconds = other.batteryChargeMinSeconds;
        this.redstoneChargeMultiplier = other.redstoneChargeMultiplier;
        this.worldCourses = other.worldCourses;
        this.allowSpectating = other.allowSpectating;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
