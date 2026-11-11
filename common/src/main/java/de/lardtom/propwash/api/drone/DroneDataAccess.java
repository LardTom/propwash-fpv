package de.lardtom.propwash.api.drone;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public interface DroneDataAccess {
    int MAX_BYTES_PER_KEY = 2048;
    int MAX_BYTES_PER_DRONE = 8192;

    <T> DroneDataKey<T> register(Identifier id, Codec<T> codec, T defaultValue, DroneDataKey.Sync sync);

    <T> T get(Entity drone, DroneDataKey<T> key);

    <T> T get(ItemStack droneItem, DroneDataKey<T> key);

    <T> void set(Entity drone, DroneDataKey<T> key, T value);

    void remove(Entity drone, DroneDataKey<?> key);

    List<DroneDataKey<?>> keys();

    Optional<DroneDataKey<?>> find(Identifier id);
}
