package de.lardtom.propwash.content;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.PartInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class BatteryItem extends PartItem {
    public BatteryItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    protected List<Component> status(ItemStack stack, PartInfo part) {
        List<Component> status = super.status(stack, part);
        if (part instanceof BatteryType battery) {
            status.add(0, chargeLine(battery, Batteries.charge(stack)));
            if (PartCondition.critical(stack)) {
                status.add(Component.translatable("propwash.content.tooltip.puffed").withStyle(ChatFormatting.RED));
            }
        }
        return status;
    }

    public static Component chargeLine(BatteryType battery, float charge) {
        return Component.translatable(ItemHelp.PREFIX + "status.charge", Batteries.percent(charge),
                Numbers.fixed(Batteries.openCircuitVoltage(battery, charge), 1)).withStyle(chargeColor(charge));
    }

    public static ChatFormatting chargeColor(float charge) {
        float c = Batteries.clamp(charge);
        return c > 0.5F ? ChatFormatting.GREEN : c > 0.2F ? ChatFormatting.YELLOW : ChatFormatting.RED;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * Batteries.charge(stack)), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Batteries.barColor(Batteries.charge(stack));
    }
}
