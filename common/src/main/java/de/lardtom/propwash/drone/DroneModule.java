package de.lardtom.propwash.drone;

import de.lardtom.propwash.drone.bind.ReceiverBinds;
import de.lardtom.propwash.drone.coating.CoatingModule;
import de.lardtom.propwash.drone.coating.CoatingRules;
import de.lardtom.propwash.drone.coating.WaterproofSprayItem;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;

public final class DroneModule {
    public static final RegHolder<EntityType<QuadcopterEntity>> DRONE = PropwashRegistries.entity("drone",
            () -> EntityType.Builder.<QuadcopterEntity>of(QuadcopterEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.15F)
                    .clientTrackingRange(16)
                    .updateInterval(1));

    public static final RegHolder<QuadcopterItem> DRONE_ITEM = PropwashRegistries.item("drone", QuadcopterItem::new, p -> p.stacksTo(1));

    public static final RegHolder<RemoteItem> REMOTE = PropwashRegistries.item("remote", RemoteItem::new, p -> p.stacksTo(1));

    public static final RegHolder<WaterproofSprayItem> WATERPROOF_SPRAY = PropwashRegistries.item("waterproof_spray", WaterproofSprayItem::new,
            p -> p.durability(CoatingRules.DURABILITY));

    public static final RegHolder<MenuType<DroneServiceMenu>> DRONE_SERVICE_MENU = PropwashRegistries.extendedMenu("drone_service",
            DroneServiceMenu::new, ByteBufCodecs.VAR_INT);

    private static boolean initialized;

    private DroneModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        DronePiloting.init();
        DroneWear.init();
        ReceiverBinds.init();
        CoatingModule.init();
    }
}
