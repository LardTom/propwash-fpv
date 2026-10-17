package de.lardtom.propwash.accessory;

import de.lardtom.propwash.accessory.net.AccessoryActionC2S;
import de.lardtom.propwash.accessory.net.DronePushS2C;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;

public final class AccessoryModule {
    public static final RegHolder<MenuType<AccessoryMenu>> ACCESSORY_MENU = PropwashRegistries.extendedMenu("drone_accessories",
            AccessoryMenu::new, AccessoryMenu.OPENING);
    private static boolean initialized;

    private AccessoryModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        AccessoryComponents.bootstrap();
        PropwashNet.registerC2S(AccessoryActionC2S.TYPE, AccessoryActionC2S.CODEC, AccessoryHost::onAction);
        PropwashNet.registerS2C(DronePushS2C.TYPE, DronePushS2C.CODEC);
        QuadcopterEntity.SERVER_TICK.register(AccessoryHost::tick);
        QuadcopterEntity.PILOT_CHANGED.register((drone, previous, current) -> AccessoryHost.onPilotChanged(drone));
        QuadcopterEntity.setDamageFilter(AddonEvents::damage);
        ServerBus.STARTED.register(server -> {
            AccessoryHost.clear();
            DroneMotion.clear();
        });
        ServerBus.STOPPED.register(server -> {
            AccessoryHost.clear();
            DroneMotion.clear();
        });
        ServerBus.PLAYER_LEAVE.register(player -> AccessoryHost.GATE.forget(player.getUUID()));
    }

    public static boolean openMenu(ServerPlayer player, QuadcopterEntity drone) {
        int[] sizes = AccessoryMenu.sizes(drone);
        int total = 0;
        for (int size : sizes) {
            total += size;
        }
        if (total == 0 || !AccessoryMenu.usable(drone, player)) {
            return false;
        }
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((containerId, inventory, p) -> new AccessoryMenu(containerId, inventory, drone),
                AccessoryMenu.TITLE), AccessoryMenu.OPENING, new AccessoryMenu.Opening(drone.getId(), sizes));
        return true;
    }
}
