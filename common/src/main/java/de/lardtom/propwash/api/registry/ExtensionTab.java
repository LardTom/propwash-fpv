package de.lardtom.propwash.api.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

public interface ExtensionTab extends CreativeEntries {
    String TITLE_KEY = "itemGroup.propwash.extension";

    String namespace();

    Identifier tabId();

    default String nameKey() {
        return "itemGroup." + this.namespace();
    }

    ExtensionTab icon(Supplier<ItemStack> icon);

    ExtensionTab icon(Identifier itemId);

    ExtensionTab order(TabSection... sections);

    List<TabSection> sections();

    boolean registered();

    ItemStack iconStack();

    List<ItemStack> contents();
}
