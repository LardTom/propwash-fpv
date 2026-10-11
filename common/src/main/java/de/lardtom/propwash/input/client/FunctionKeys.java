package de.lardtom.propwash.input.client;

import de.lardtom.propwash.input.SwitchFunction;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class FunctionKeys {
    private FunctionKeys() {
    }

    public static KeyDefaults.Entry entry(SwitchFunction function) {
        return switch (function) {
            case ARM -> KeyDefaults.ARM;
            case ANGLE -> KeyDefaults.MODE_ANGLE;
            case HORIZON -> KeyDefaults.MODE_HORIZON;
            case RESCUE -> KeyDefaults.RESCUE;
            case FLIP_OVER -> KeyDefaults.FLIP_UPRIGHT;
            case SET_HOME -> KeyDefaults.SET_HOME;
            case BEEPER -> KeyDefaults.BEEPER;
            case RACE_RESTART -> KeyDefaults.RACE_RESTART;
            case RECORD -> KeyDefaults.ACTION_CAM_RECORD;
            case LEDS_OFF -> KeyDefaults.LEDS;
            case ALT_HOLD -> KeyDefaults.ALT_HOLD;
            case POS_HOLD -> KeyDefaults.POS_HOLD;
            case CARGO_DROP -> KeyDefaults.CARGO_DROP;
            case MODE_3D -> KeyDefaults.MODE_3D;
            case BLACKBOX -> KeyDefaults.BLACKBOX;
            case COMMAND_1, COMMAND_2, COMMAND_3, COMMAND_4, COMMAND_5, COMMAND_6, COMMAND_7, COMMAND_8 -> KeyDefaults.command(function);
        };
    }

    public static @Nullable KeyMapping mapping(SwitchFunction function) {
        return KeyMapping.get(entry(function).mappingName());
    }

    public static Component keyLabel(SwitchFunction function) {
        KeyMapping mapping = mapping(function);
        if (mapping == null || mapping.isUnbound()) {
            return Component.translatable("propwash.input.key.unbound");
        }
        return KeyNames.of(mapping);
    }

    public static Component actionLabel(SwitchFunction function) {
        return Component.translatable(entry(function).mappingName());
    }
}
