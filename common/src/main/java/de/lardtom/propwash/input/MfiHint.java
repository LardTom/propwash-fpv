package de.lardtom.propwash.input;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class MfiHint {
    public static final String NAME = "SDL_JOYSTICK_MFI";
    public static final String OFF = "0";

    public enum Source {
        NOT_MAC,
        ENVIRONMENT,
        OPTION_OFF,
        OPTION_ON
    }

    public record Plan(Source source, @Nullable String environment) {
        public boolean setsOff() {
            return this.source == Source.OPTION_OFF;
        }

        public boolean resets() {
            return this.source == Source.OPTION_ON;
        }

        public boolean appleFramework() {
            return switch (this.source) {
                case NOT_MAC, OPTION_ON -> true;
                case OPTION_OFF -> false;
                case ENVIRONMENT -> enabled(this.environment);
            };
        }

        public boolean optionLocked() {
            return this.source == Source.ENVIRONMENT;
        }
    }

    private MfiHint() {
    }

    public static Plan plan(boolean mac, @Nullable String environment, boolean useAppleFramework) {
        if (!mac) {
            return new Plan(Source.NOT_MAC, null);
        }
        if (environment != null) {
            return new Plan(Source.ENVIRONMENT, environment);
        }
        return new Plan(useAppleFramework ? Source.OPTION_ON : Source.OPTION_OFF, null);
    }

    public static boolean restartNeeded(Plan applied, Plan wanted) {
        return (applied.setsOff() || applied.resets()) && (wanted.setsOff() || wanted.resets()) && applied.source() != wanted.source();
    }

    public static boolean enabled(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return !(value.charAt(0) == '0' || value.toLowerCase(Locale.ROOT).equals("false"));
    }
}
