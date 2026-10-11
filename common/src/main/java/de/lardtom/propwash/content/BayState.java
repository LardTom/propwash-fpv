package de.lardtom.propwash.content;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

public enum BayState implements StringRepresentable {
    EMPTY("empty"),
    CHARGING("charging"),
    FULL("full");

    private final String name;

    BayState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public static BayState of(ItemStack stack) {
        if (!Batteries.isBattery(stack)) {
            return EMPTY;
        }
        return Batteries.isFull(Batteries.charge(stack)) ? FULL : CHARGING;
    }
}
