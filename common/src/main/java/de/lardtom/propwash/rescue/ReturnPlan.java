package de.lardtom.propwash.rescue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.UUID;

public record ReturnPlan(UUID owner, double homeX, double homeY, double homeZ, boolean followOwner, float altitude, float speed,
                         float descentRate, boolean obstacleAvoidance, float clearance, float minDistance, boolean landNow, int ticks) {
    public static final Codec<ReturnPlan> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(ReturnPlan::owner),
            Codec.DOUBLE.fieldOf("home_x").forGetter(ReturnPlan::homeX),
            Codec.DOUBLE.fieldOf("home_y").forGetter(ReturnPlan::homeY),
            Codec.DOUBLE.fieldOf("home_z").forGetter(ReturnPlan::homeZ),
            Codec.BOOL.optionalFieldOf("follow_owner", false).forGetter(ReturnPlan::followOwner),
            Codec.FLOAT.optionalFieldOf("altitude", 15.0F).forGetter(ReturnPlan::altitude),
            Codec.FLOAT.optionalFieldOf("speed", 8.0F).forGetter(ReturnPlan::speed),
            Codec.FLOAT.optionalFieldOf("descent", 2.0F).forGetter(ReturnPlan::descentRate),
            Codec.BOOL.optionalFieldOf("obstacles", true).forGetter(ReturnPlan::obstacleAvoidance),
            Codec.FLOAT.optionalFieldOf("clearance", 1.0F).forGetter(ReturnPlan::clearance),
            Codec.FLOAT.optionalFieldOf("min_distance", 8.0F).forGetter(ReturnPlan::minDistance),
            Codec.BOOL.optionalFieldOf("land_now", false).forGetter(ReturnPlan::landNow),
            Codec.INT.optionalFieldOf("ticks", 0).forGetter(ReturnPlan::ticks)
    ).apply(i, ReturnPlan::new));

    public ReturnPlan {
        Objects.requireNonNull(owner, "owner");
    }

    public static ReturnPlan of(UUID owner, double homeX, double homeY, double homeZ, boolean followOwner, RescueSettings settings) {
        RescueSettings s = settings.copy();
        s.sanitize();
        return new ReturnPlan(owner, homeX, homeY, homeZ, followOwner, (float) s.altitude, (float) s.speed, (float) s.descentRate,
                s.obstacleAvoidance, (float) s.clearance, (float) s.minDistance, false, 0);
    }

    public RescueSettings settings() {
        RescueSettings s = new RescueSettings();
        s.altitude = this.altitude;
        s.speed = this.speed;
        s.descentRate = this.descentRate;
        s.obstacleAvoidance = this.obstacleAvoidance;
        s.clearance = this.clearance;
        s.minDistance = this.minDistance;
        s.autoLand = true;
        s.stickOverride = false;
        s.sanitize();
        return s;
    }

    public ReturnPlan withHome(double x, double y, double z) {
        return new ReturnPlan(this.owner, x, y, z, this.followOwner, this.altitude, this.speed, this.descentRate,
                this.obstacleAvoidance, this.clearance, this.minDistance, this.landNow, this.ticks);
    }

    public ReturnPlan withLandNow() {
        return new ReturnPlan(this.owner, this.homeX, this.homeY, this.homeZ, this.followOwner, this.altitude, this.speed,
                this.descentRate, this.obstacleAvoidance, this.clearance, this.minDistance, true, this.ticks);
    }

    public ReturnPlan withTicks(int ticks) {
        return new ReturnPlan(this.owner, this.homeX, this.homeY, this.homeZ, this.followOwner, this.altitude, this.speed,
                this.descentRate, this.obstacleAvoidance, this.clearance, this.minDistance, this.landNow, ticks);
    }

    public double horizontalDistance(double x, double z) {
        double dx = this.homeX - x;
        double dz = this.homeZ - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
