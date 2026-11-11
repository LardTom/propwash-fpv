package de.lardtom.propwash.dev.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.ExampleSwitches;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ExampleSwitchesClient {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "example"));
    private static KeyMapping ghostKey;
    private static KeyMapping tiltUp;
    private static KeyMapping tiltDown;

    private ExampleSwitchesClient() {
    }

    static void init() {
        ghostKey = key("ghost");
        tiltUp = key("gimbal_tilt_up");
        tiltDown = key("gimbal_tilt_down");
        PropwashClientApi.bindSwitchKey(ExampleSwitches.GHOST, ghostKey);
        PropwashClientApi.bindAxisKeys(ExampleSwitches.GIMBAL_TILT, tiltUp, tiltDown);
    }

    private static KeyMapping key(String name) {
        return new KeyMapping("key." + ExampleAddon.NAMESPACE + "." + name, InputConstants.UNKNOWN.getValue(), CATEGORY);
    }

    public static KeyMapping ghostKey() {
        return ghostKey;
    }

    public static KeyMapping tiltUp() {
        return tiltUp;
    }

    public static KeyMapping tiltDown() {
        return tiltDown;
    }
}
