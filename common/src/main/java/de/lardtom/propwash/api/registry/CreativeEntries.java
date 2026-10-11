package de.lardtom.propwash.api.registry;

import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public interface CreativeEntries {
    void add(Supplier<? extends Collection<ItemStack>> entries);

    List<ItemStack> collect();
}
