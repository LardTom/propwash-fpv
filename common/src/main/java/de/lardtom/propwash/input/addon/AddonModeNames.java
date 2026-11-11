package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.StabilizationMode;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AddonModeNames {
    public static final String CHANGED = "propwash.drone.mode.changed";
    public static final String CHANGED_SWITCH = "propwash.drone.mode.changed_switch";
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private AddonModeNames() {
    }

    public static String propwashKey(StabMode mode) {
        return "propwash.drone.mode." + mode.name().toLowerCase(Locale.ROOT);
    }

    public static Optional<String> nameKey(@Nullable FlightAssist assist, StabMode mode) {
        if (assist == null) {
            return Optional.empty();
        }
        try {
            Optional<String> key = assist.modeNameKey(apiMode(mode));
            return key == null ? Optional.empty() : key.map(String::strip).filter(text -> !text.isEmpty());
        } catch (RuntimeException e) {
            if (WARNED.add(assist.getClass().getName())) {
                Constants.LOG.warn("Flughilfe {}: modeNameKey({}) fehlgeschlagen", assist.getClass().getName(), mode, e);
            }
            return Optional.empty();
        }
    }

    public static Component name(@Nullable FlightAssist assist, StabMode mode) {
        return Component.translatable(nameKey(assist, mode).orElseGet(() -> propwashKey(mode)));
    }

    public static Component changed(@Nullable FlightAssist assist, StabMode mode, @Nullable StabMode fromSwitches) {
        return fromSwitches != null && fromSwitches != mode
                ? Component.translatable(CHANGED_SWITCH, name(assist, mode), name(assist, fromSwitches))
                : Component.translatable(CHANGED, name(assist, mode));
    }

    static StabilizationMode apiMode(StabMode mode) {
        return switch (mode) {
            case ACRO -> StabilizationMode.ACRO;
            case ANGLE -> StabilizationMode.ANGLE;
            case HORIZON -> StabilizationMode.HORIZON;
        };
    }
}
