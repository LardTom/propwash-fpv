package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class CrossLoaderJoinCheck {
    public static final String LOG_PREFIX = "[Beitrittstest] gemischt: Drohne ";
    private static final int EVERY_TICKS = 10;
    private static final Map<UUID, String> SEEN = new HashMap<>();

    private CrossLoaderJoinCheck() {
    }

    static void tick(MinecraftServer server, int tick) {
        if (tick % EVERY_TICKS != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof QuadcopterEntity drone) {
                    String data = data(drone);
                    if (!data.equals(SEEN.put(drone.getUUID(), data))) {
                        Constants.LOG.info("{}{} Daten {} ({})", LOG_PREFIX, drone.getUUID(), digest(data), data);
                    }
                }
            }
        }
    }

    public static String digest(String data) {
        return Integer.toHexString(data.hashCode());
    }

    public static String data(QuadcopterEntity drone) {
        DroneBuild build = drone.build();
        StringBuilder text = new StringBuilder();
        text.append(build.frame().id()).append(' ').append(build.stack().id()).append(' ').append(build.motor().id()).append(' ')
                .append(build.prop().id()).append(' ').append(build.video().id()).append(' ').append(build.battery().id());
        new TreeMap<>(build.accessories().byMount()).forEach((mount, type) -> text.append(' ').append(mount).append('=').append(type.id()));
        text.append(" lack=").append(new TreeMap<>(drone.paint().colors()));
        text.append(" tune=").append(new TreeMap<>(drone.tune().values()));
        Map<AccessoryMount, List<ItemStack>> contents = new TreeMap<>(drone.accessoryContents().byMount());
        contents.forEach((mount, stacks) -> {
            text.append(" inhalt ").append(mount).append('=');
            stacks.forEach(stack -> text.append(stack.getCount()).append('x').append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append(','));
        });
        return text.toString();
    }
}
