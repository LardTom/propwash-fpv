package de.lardtom.propwash.drone;

import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class RemoteItem extends Item {
    public static final String HELP = "remote";

    public RemoteItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            DronePiloting.useRemote(serverPlayer, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag tooltipFlag) {
        String prefix = ItemHelp.PREFIX + HELP;
        ItemHelp.builder(HELP)
                .status(ItemHelp.boundStatus(itemStack))
                .args(prefix + ".use.4", Component.keybind("key.propwash.drone_exit"))
                .specs(out -> {
                    if (RadioLink.LOCAL_FREE_RANGE.getAsBoolean()) {
                        double limit = RadioLink.SERVER_LIMIT.getAsDouble();
                        out.accept(Double.isFinite(limit)
                                ? ItemHelp.line(prefix + ".range_free_server", (int) Math.round(limit))
                                : ItemHelp.line(prefix + ".range_free_now"));
                        out.accept(ItemHelp.line(prefix + ".range_normal", RadioLink.displayRange()));
                    } else {
                        out.accept(ItemHelp.line(prefix + ".range", RadioLink.displayRange()));
                        out.accept(ItemHelp.line(prefix + ".range_free"));
                    }
                })
                .appendTo(builder);
    }
}
