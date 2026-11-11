package de.lardtom.propwash.rx;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public enum RxOutput implements StringRepresentable {
    FRONT,
    ALL;

    public int signal(Direction facing, @Nullable Direction towardBlock, int power) {
        if (this == ALL) {
            return power;
        }
        return towardBlock == facing.getOpposite() ? power : 0;
    }

    public int direct(Direction facing, @Nullable Direction towardBlock, int power) {
        return this == FRONT ? this.signal(facing, towardBlock, power) : 0;
    }

    public boolean connects(Direction facing, @Nullable Direction towardBlock) {
        if (towardBlock == null) {
            return false;
        }
        return this == ALL || towardBlock == facing.getOpposite();
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
