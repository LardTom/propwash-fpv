package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.ActionCamDefinition;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartVisual;
import de.lardtom.propwash.dev.example.client.ExampleOperatorClient;
import de.lardtom.propwash.platform.Services;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ExampleOperator {
    public static final String REQUIRED_API = "1.7";
    public static final Identifier GIMBAL_FRONT = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "gimbal_front");
    public static final int INVITE = 3;
    public static final double INVITE_RANGE = 16.0;
    public static final ActionCamDefinition CAMERA = new ActionCamDefinition(1080, 60, true, true, 0.0, 3.0, 4.5, 100.0, 0.9, 0.15, 0.5);
    public static final AtomicInteger BINDS = new AtomicInteger();
    public static final AtomicInteger UNBINDS = new AtomicInteger();
    public static final AtomicReference<String> LAST = new AtomicReference<>("");
    private static boolean initialized;

    private ExampleOperator() {
    }

    static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashApi.requireVersion(ExampleAddon.NAMESPACE, REQUIRED_API);
        PropwashApi.parts().register(AccessoryDefinition.builder(GIMBAL_FRONT)
                .basics(PartBasics.of(68.0).withCreativeListed(false).withVisual(PartVisual.accent(0x5A6068)))
                .slot(AccessorySlot.FRONT).size(40.0, 44.0, 40.0).flightBatteryWatts(4.0).frontalArea(0.0016)
                .actionCam(CAMERA).hangsStraight(true)
                .build());
        PropwashApi.partItems().registerItem(GIMBAL_FRONT, properties -> properties);
        PropwashEvents.ACCESSORY_ACTION.register((player, drone, number, pressed) -> {
            if (pressed && number == INVITE && carries(drone)) {
                toggle(drone, player);
            }
        });
        if (Services.PLATFORM.isClientDist()) {
            ExampleOperatorClient.init();
        }
    }

    public static boolean carries(Entity drone) {
        return PropwashApi.accessories().accessoryAt(drone, AccessorySlot.FRONT).equals(Optional.of(GIMBAL_FRONT));
    }

    public static boolean bind(Entity drone, ServerPlayer operator) {
        boolean bound = PropwashApi.drones().operators().bind(drone, operator);
        if (bound) {
            BINDS.incrementAndGet();
        }
        LAST.set((bound ? "bound " : "refused ") + operator.getPlainTextName());
        return bound;
    }

    public static boolean unbind(Entity drone) {
        boolean released = PropwashApi.drones().operators().unbind(drone);
        if (released) {
            UNBINDS.incrementAndGet();
            LAST.set("unbound");
        }
        return released;
    }

    static void toggle(Entity drone, ServerPlayer pilot) {
        if (PropwashApi.drones().operators().operator(drone).isPresent()) {
            unbind(drone);
            return;
        }
        ServerPlayer nearest = null;
        double best = INVITE_RANGE * INVITE_RANGE;
        for (ServerPlayer candidate : pilot.level().players()) {
            if (candidate == pilot) {
                continue;
            }
            double distance = candidate.distanceToSqr(pilot);
            if (distance <= best) {
                best = distance;
                nearest = candidate;
            }
        }
        if (nearest != null) {
            bind(drone, nearest);
        } else {
            LAST.set("nobody near " + pilot.getPlainTextName());
        }
    }
}
