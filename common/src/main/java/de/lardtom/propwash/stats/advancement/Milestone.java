package de.lardtom.propwash.stats.advancement;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum Milestone implements StringRepresentable {
    DRONE_BUILT("drone_built"),
    TAKEOFF("takeoff"),
    FPV_FLIGHT("fpv_flight"),
    BATTERY_SWAPPED("battery_swapped"),
    BATTERY_CHARGED("battery_charged"),
    LAP("lap"),
    PERSONAL_BEST("personal_best"),
    GHOST_BEATEN("ghost_beaten"),
    OWN_COURSE("own_course"),
    DRONE_PAINTED("drone_painted"),
    SHARECODE_LOADED("sharecode_loaded"),
    SPECTATED("spectated");

    public static final Codec<Milestone> CODEC = StringRepresentable.fromEnum(Milestone::values);

    private final String name;

    Milestone(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
