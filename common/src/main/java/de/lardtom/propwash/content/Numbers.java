package de.lardtom.propwash.content;

import net.minecraft.network.chat.Component;

public final class Numbers {
    private static final String DECIMAL_KEY = "propwash.content.number.decimal";

    private Numbers() {
    }

    public static Component of(double value, int maxDecimals) {
        if (!Double.isFinite(value)) {
            return Component.translatable("propwash.content.number.none");
        }
        long scale = 1;
        for (int i = 0; i < maxDecimals; i++) {
            scale *= 10;
        }
        long scaled = Math.round(Math.abs(value) * scale);
        long whole = scaled / scale;
        long fraction = scaled % scale;
        String sign = value < 0 && scaled != 0 ? "-" : "";
        if (fraction == 0 || maxDecimals <= 0) {
            return Component.literal(sign + whole);
        }
        String digits = String.valueOf(fraction);
        while (digits.length() < maxDecimals) {
            digits = "0" + digits;
        }
        while (digits.endsWith("0")) {
            digits = digits.substring(0, digits.length() - 1);
        }
        return Component.translatable(DECIMAL_KEY, sign + whole, digits);
    }

    public static Component fixed(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return Component.translatable("propwash.content.number.none");
        }
        long scale = 1;
        for (int i = 0; i < decimals; i++) {
            scale *= 10;
        }
        long scaled = Math.round(Math.abs(value) * scale);
        long whole = scaled / scale;
        String sign = value < 0 && scaled != 0 ? "-" : "";
        if (decimals <= 0) {
            return Component.literal(sign + whole);
        }
        String digits = String.valueOf(scaled % scale);
        while (digits.length() < decimals) {
            digits = "0" + digits;
        }
        return Component.translatable(DECIMAL_KEY, sign + whole, digits);
    }

    public static Component minutes(double minutes) {
        if (!Double.isFinite(minutes) || minutes < 0) {
            return Component.translatable("propwash.content.number.none");
        }
        long totalSeconds = Math.round(minutes * 60.0);
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return Component.literal(m + ":" + (s < 10 ? "0" : "") + s);
    }
}
