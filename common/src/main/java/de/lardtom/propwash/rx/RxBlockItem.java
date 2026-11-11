package de.lardtom.propwash.rx;

import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RxBlockItem extends BlockItem {
    public RxBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag tooltipFlag) {
        boolean unlimited = this.getBlock() instanceof RxBlock rx && rx.unlimited();
        String help = ItemHelp.PREFIX + (unlimited ? RxModule.CREATIVE_ID : RxModule.ID).getPath();
        List<String> lines = RxText.tooltipLines(Language.getInstance().getOrDefault(unlimited ? RxText.CREATIVE_TOOLTIP : RxText.TOOLTIP));
        List<Component> more = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            more.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        ItemHelp.summary(RxText.TOOLTIP_LINE, lines.getFirst())
                .status(more)
                .what(help)
                .use(help)
                .fits(out -> out.accept(ItemHelp.line(help + ".fits")))
                .appendTo(builder);
    }
}
