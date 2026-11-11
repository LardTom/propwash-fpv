package de.lardtom.propwash.input;

import org.jspecify.annotations.Nullable;

public final class DevExtraFunction {
    public static final String ENV = "PROPWASH_DEV_EXTRA_FUNCTION";
    public static final String NAME = "PROPWASH_DEV_EXTRA";
    public static final String LABEL = "Extra on/off";

    private DevExtraFunction() {
    }

    public static boolean requested() {
        return "1".equals(System.getenv(ENV));
    }

    public static boolean is(SwitchFunction function) {
        return NAME.equals(function.name());
    }

    public static @Nullable SwitchFunction present() {
        for (SwitchFunction function : SwitchFunction.values()) {
            if (is(function)) {
                return function;
            }
        }
        return null;
    }
}
