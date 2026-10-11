package de.lardtom.propwash.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class MaterialItem extends Item {
    public static final String SECTION_INGREDIENT_FOR = ItemHelp.PREFIX + "section.ingredient_for";
    public static final String SHARED_PREFIX = ItemHelp.PREFIX + "material";

    public MaterialItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag tooltipFlag) {
        help(BuiltInRegistries.ITEM.getKey(this).getPath()).appendTo(builder);
    }

    public static ItemHelp.Builder help(String id) {
        String prefix = ItemHelp.PREFIX + id;
        return ItemHelp.summary(prefix + ".summary")
                .what(prefix)
                .use(SHARED_PREFIX)
                .use(prefix)
                .fitsHeader(SECTION_INGREDIENT_FOR)
                .fits(out -> ItemHelp.numbered(prefix, "fits").forEach(key -> out.accept(ItemHelp.line(key))));
    }
}
