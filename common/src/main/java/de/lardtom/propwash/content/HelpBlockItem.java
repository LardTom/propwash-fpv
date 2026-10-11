package de.lardtom.propwash.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class HelpBlockItem extends BlockItem {
    public HelpBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag tooltipFlag) {
        String id = BuiltInRegistries.ITEM.getKey(this).getPath();
        ItemHelp.builder(id)
                .fits(out -> out.accept(ItemHelp.line(ItemHelp.PREFIX + id + ".fits")))
                .appendTo(builder);
    }
}
