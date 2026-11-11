package de.lardtom.propwash.drone.data;

import com.mojang.serialization.DynamicOps;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

public final class DroneDataStore {
    private DroneDataStore() {
    }

    public static <T> T get(Entity drone, DroneDataKey<T> key) {
        if (!(drone instanceof QuadcopterEntity quad)) {
            return key.defaultValue();
        }
        if (drone.level().isClientSide() && key.sync() != DroneDataKey.Sync.TRACKING) {
            return key.defaultValue();
        }
        return quad.addonData().value(key, ops(drone));
    }

    public static <T> T get(ItemStack stack, DroneDataKey<T> key) {
        AddonData data = stack.isEmpty() ? null : stack.get(DroneDataComponents.ADDON_DATA.get());
        return data == null ? key.defaultValue() : data.value(key, NbtOps.INSTANCE);
    }

    public static <T> void set(Entity drone, DroneDataKey<T> key, T value) {
        QuadcopterEntity quad = writable(drone, key);
        quad.setAddonData(quad.addonData().with(key, value, ops(drone)));
    }

    public static void remove(Entity drone, DroneDataKey<?> key) {
        QuadcopterEntity quad = writable(drone, key);
        quad.setAddonData(quad.addonData().without(key.id()));
    }

    public static AddonData tracked(AddonData data) {
        if (data.isEmpty()) {
            return data;
        }
        return data.filter(id -> Api17Registries.DRONE_DATA.find(id).map(key -> key.sync() == DroneDataKey.Sync.TRACKING).orElse(false));
    }

    private static QuadcopterEntity writable(Entity drone, DroneDataKey<?> key) {
        if (drone.level().isClientSide()) {
            throw new IllegalStateException("Drohnen-Daten " + key.id() + " nur am Server schreiben");
        }
        if (!(drone instanceof QuadcopterEntity quad)) {
            throw new IllegalArgumentException("Drohnen-Daten " + key.id() + ": " + EntityType.getKey(drone.getType())
                    + " ist keine Propwash-Drohne");
        }
        return quad;
    }

    private static DynamicOps<Tag> ops(Entity drone) {
        return drone.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }
}
