package de.lardtom.propwash.bando;

import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class BandoMapItem extends MapItem {
    public static final String HELP = "bando_map";
    public static final String BLANK_KEY = ItemHelp.PREFIX + HELP + ".status.blank";

    public BandoMapItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag tooltipFlag) {
        ItemHelp.Builder help = ItemHelp.builder(HELP);
        if (!itemStack.has(DataComponents.MAP_ID)) {
            help.status(Component.translatable(BLANK_KEY).withStyle(ChatFormatting.DARK_GRAY));
        }
        help.appendTo(builder);
    }
}
