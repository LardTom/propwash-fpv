package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.input.KeyRepeatFilter;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class InputKeys {
    public static final KeyMapping THROTTLE_UP = register(KeyDefaults.THROTTLE_UP);
    public static final KeyMapping THROTTLE_DOWN = register(KeyDefaults.THROTTLE_DOWN);
    public static final KeyMapping YAW_LEFT = register(KeyDefaults.YAW_LEFT);
    public static final KeyMapping YAW_RIGHT = register(KeyDefaults.YAW_RIGHT);
    public static final KeyMapping PITCH_FORWARD = register(KeyDefaults.PITCH_FORWARD);
    public static final KeyMapping PITCH_BACK = register(KeyDefaults.PITCH_BACK);
    public static final KeyMapping ROLL_LEFT = register(KeyDefaults.ROLL_LEFT);
    public static final KeyMapping ROLL_RIGHT = register(KeyDefaults.ROLL_RIGHT);
    public static final KeyMapping MOUSE_CENTER = register(KeyDefaults.MOUSE_CENTER);
    public static final KeyMapping ARM = register(KeyDefaults.ARM);
    public static final KeyMapping FLIGHT_MODE = register(KeyDefaults.FLIGHT_MODE);
    public static final KeyMapping MODE_ACRO = register(KeyDefaults.MODE_ACRO);
    public static final KeyMapping MODE_ANGLE = register(KeyDefaults.MODE_ANGLE);
    public static final KeyMapping MODE_HORIZON = register(KeyDefaults.MODE_HORIZON);
    public static final KeyMapping ALT_HOLD = register(KeyDefaults.ALT_HOLD);
    public static final KeyMapping POS_HOLD = register(KeyDefaults.POS_HOLD);
    public static final KeyMapping MODE_3D = register(KeyDefaults.MODE_3D);
    public static final KeyMapping FLIP_UPRIGHT = register(KeyDefaults.FLIP_UPRIGHT);
    public static final KeyMapping TURTLE = register(KeyDefaults.TURTLE);
    public static final KeyMapping EXIT = register(KeyDefaults.EXIT);
    public static final KeyMapping BEEPER = register(KeyDefaults.BEEPER);
    public static final KeyMapping LEDS = register(KeyDefaults.LEDS);
    public static final KeyMapping RECEIVER_WIZARD = register(KeyDefaults.RECEIVER_WIZARD);
    public static final List<KeyMapping> COMMANDS = KeyDefaults.COMMANDS.stream().map(InputKeys::register).toList();

    private InputKeys() {
    }

    public static void bootstrap() {
    }

    public static List<KeyMapping> all() {
        return List.of(THROTTLE_UP, THROTTLE_DOWN, YAW_LEFT, YAW_RIGHT, PITCH_FORWARD, PITCH_BACK, ROLL_LEFT, ROLL_RIGHT, MOUSE_CENTER,
                ARM, FLIGHT_MODE, MODE_ACRO, MODE_ANGLE, MODE_HORIZON, ALT_HOLD, POS_HOLD, MODE_3D, FLIP_UPRIGHT, TURTLE, EXIT, BEEPER, LEDS,
                RECEIVER_WIZARD);
    }

    public static boolean anyFlightKeyDown() {
        return down(THROTTLE_UP) || down(THROTTLE_DOWN) || down(YAW_LEFT) || down(YAW_RIGHT)
                || down(PITCH_FORWARD) || down(PITCH_BACK) || down(ROLL_LEFT) || down(ROLL_RIGHT);
    }

    public static boolean down(KeyMapping key) {
        return effective(key).isDown();
    }

    public static Component label(KeyMapping key) {
        return KeyNames.of(effective(key));
    }

    public static KeyMapping effective(KeyMapping key) {
        if (!key.isUnbound()) {
            return key;
        }
        KeyMapping movement = movementFallback(key);
        return movement == null ? key : movement;
    }

    public static KeyMapping movementFallback(KeyMapping key) {
        Minecraft minecraft = Minecraft.getInstance();
        Options options = minecraft == null ? null : minecraft.options;
        if (options == null) {
            return null;
        }
        if (key == THROTTLE_UP) {
            return options.keyUp;
        }
        if (key == THROTTLE_DOWN) {
            return options.keyDown;
        }
        if (key == YAW_LEFT) {
            return options.keyLeft;
        }
        if (key == YAW_RIGHT) {
            return options.keyRight;
        }
        return null;
    }

    public static boolean pressed(KeyMapping key, KeyRepeatFilter filter) {
        int clicks = 0;
        while (key.consumeClick()) {
            clicks++;
        }
        return filter.pressed(clicks, key.isDown());
    }

    public static void drain(KeyMapping key) {
        boolean pending = key.consumeClick();
        while (pending) {
            pending = key.consumeClick();
        }
    }

    private static KeyMapping register(KeyDefaults.Entry entry) {
        return entry.mouse() ? ClientRegistrar.keyMapping(entry.name(), InputConstants.Type.MOUSE, entry.key())
                : ClientRegistrar.keyMapping(entry.name(), entry.key());
    }
}
