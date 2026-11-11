package de.lardtom.propwash.relay;

import de.lardtom.propwash.content.HelpBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class SignalRepeaterItem extends HelpBlockItem {
    public SignalRepeaterItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return RepeaterMeter.active();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.clamp(Math.round(Item.MAX_BAR_WIDTH * RepeaterMeter.quality()), 0, Item.MAX_BAR_WIDTH);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return RepeaterMeter.color(RepeaterMeter.bars(RepeaterMeter.quality())) & 0xFFFFFF;
    }
}
