package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryAction;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.input.AddonAxis;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartVisual;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ExampleSwitches {
    public static final String REQUIRED_API = "1.7";
    public static final Identifier GHOST = id("ghost");
    public static final Identifier GIMBAL_TILT = id("gimbal_tilt");
    public static final Identifier SIGNAL_LIGHT = id("signal_light");
    public static final double TILT_KEY_RATE_DPS = 60.0;

    public static final AtomicInteger GHOST_CHANGES = new AtomicInteger();
    public static final AtomicReference<String> LAST_CHANGE = new AtomicReference<>("");
    public static final AtomicInteger SIGNALS = new AtomicInteger();

    private static AddonSwitch ghost;
    private static AddonAxis tilt;

    private ExampleSwitches() {
    }

    static void init() {
        PropwashApi.requireVersion(ExampleAddon.NAMESPACE, REQUIRED_API);
        ghost = PropwashApi.switches().register(SwitchSpec.builder(GHOST)
                .toggle(true).latchesOnGamepad(true).serverSync(true)
                .descriptionKey(ExampleAddon.NAMESPACE + ".switch.ghost.description")
                .build());
        tilt = PropwashApi.switches().registerAxis(AxisSpec.builder(GIMBAL_TILT)
                .center(0.0).keyRateDps(TILT_KEY_RATE_DPS).serverSync(true)
                .build());
        PropwashApi.parts().register(AccessoryDefinition.builder(SIGNAL_LIGHT)
                .basics(PartBasics.of(12.0).withCreativeListed(false).withVisual(PartVisual.accent(0xF2C14E)))
                .slot(AccessorySlot.TOP).size(20.0, 12.0, 20.0).flightBatteryWatts(0.5).frontalArea(0.0003)
                .build());
        PropwashApi.partItems().registerItem(SIGNAL_LIGHT, properties -> properties);
        PropwashApi.accessories().register(SIGNAL_LIGHT, new SignalLight());
        PropwashEvents.ADDON_SWITCH_CHANGED.register((player, drone, id, active) -> {
            if (GHOST.equals(id)) {
                GHOST_CHANGES.incrementAndGet();
                LAST_CHANGE.set(player.getPlainTextName() + " " + id + "=" + active);
            }
        });
    }

    public static AddonSwitch ghost() {
        return ghost;
    }

    public static AddonAxis tilt() {
        return tilt;
    }

    public static String actionKey(int number) {
        return ExampleAddon.NAMESPACE + ".accessory.signal_light.action." + number;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, path);
    }

    static final class SignalLight implements AccessoryBehavior {
        @Override
        public Optional<String> actionNameKey(int number) {
            return number == 1 || number == 2 ? Optional.of(actionKey(number)) : Optional.empty();
        }

        @Override
        public void onAction(AccessoryAction action) {
            if (action.pressed() && (action.number() == 1 || action.number() == 2)) {
                SIGNALS.incrementAndGet();
            }
        }
    }
}
