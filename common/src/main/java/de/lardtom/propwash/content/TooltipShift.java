package de.lardtom.propwash.content;

import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class TooltipShift {
    public static volatile BooleanSupplier SHIFT = () -> false;
    public static volatile IntSupplier LINES = () -> Integer.MAX_VALUE;
    public static final int RESERVED_LINES = 3;
    private static volatile @Nullable Boolean override;

    private TooltipShift() {
    }

    public static boolean down() {
        Boolean forced = override;
        if (forced != null) {
            return forced;
        }
        return SHIFT.getAsBoolean();
    }

    public static int lineBudget() {
        if (override != null) {
            return Integer.MAX_VALUE;
        }
        return Math.max(8, LINES.getAsInt() - RESERVED_LINES);
    }

    public static void override(@Nullable Boolean value) {
        override = value;
    }
}
