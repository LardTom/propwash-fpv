package de.lardtom.propwash.rx;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum RxLinkState implements StringRepresentable {
    NONE,
    LINKED,
    LOST;

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
