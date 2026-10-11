package de.lardtom.propwash.content;

import net.minecraft.util.StringRepresentable;

public enum ChargerState implements StringRepresentable {
    IDLE("idle", 0),
    CHARGING("charging", 6),
    DONE("done", 4);

    private final String name;
    private final int light;

    ChargerState(String name, int light) {
        this.name = name;
        this.light = light;
    }

    public int light() {
        return this.light;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public static ChargerState of(int batteries, int full) {
        if (batteries <= 0) {
            return IDLE;
        }
        return full >= batteries ? DONE : CHARGING;
    }
}
