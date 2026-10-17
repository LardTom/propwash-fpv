package de.lardtom.propwash.drone.coating;

import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public final class WaterproofSprayItem extends Item {
    public static final String HELP = "waterproof_spray";

    public WaterproofSprayItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        int left = CoatingRules.usesLeft(stack.getDamageValue());
        ItemHelp.builder(HELP)
                .status(Component.translatable(ItemHelp.PREFIX + HELP + ".uses", left, CoatingRules.DURABILITY / CoatingRules.COST)
                        .withStyle(left > 0 ? ChatFormatting.AQUA : ChatFormatting.RED))
                .appendTo(builder);
    }
}
