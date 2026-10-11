package de.lardtom.propwash.content;

import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ContentMaterials {
    private static final Map<CraftingMaterial, RegHolder<MaterialItem>> ITEMS = new EnumMap<>(CraftingMaterial.class);

    static {
        for (CraftingMaterial material : CraftingMaterial.values()) {
            ITEMS.put(material, PropwashRegistries.item(material.key(), MaterialItem::new,
                    p -> material.rare() ? p.rarity(Rarity.UNCOMMON) : p));
        }
    }

    private ContentMaterials() {
    }

    static void bootstrap() {
    }

    public static Map<CraftingMaterial, RegHolder<MaterialItem>> items() {
        return Collections.unmodifiableMap(ITEMS);
    }

    public static RegHolder<MaterialItem> item(CraftingMaterial material) {
        return ITEMS.get(material);
    }

    public static ItemStack stack(CraftingMaterial material) {
        return new ItemStack(item(material).get());
    }
}
