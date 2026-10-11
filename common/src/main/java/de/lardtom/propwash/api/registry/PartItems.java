package de.lardtom.propwash.api.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.UnaryOperator;

public interface PartItems {
    int registerItems(String namespace);

    void registerItem(Identifier partId, UnaryOperator<Item.Properties> properties);

    Optional<Item> item(Identifier partId);

    Optional<Identifier> partOf(ItemStack stack);

    ItemStack stack(Identifier partId);

    default ItemStack stack(Identifier partId, float charge) {
        return this.stack(partId);
    }
}
