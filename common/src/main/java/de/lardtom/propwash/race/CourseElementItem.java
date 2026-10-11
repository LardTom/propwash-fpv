package de.lardtom.propwash.race;

import de.lardtom.propwash.content.ItemHelp;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

public class CourseElementItem extends GateItem {
    private final CourseElement kind;

    public CourseElementItem(Item.Properties properties, CourseElement kind) {
        super(properties, GateSize.MEDIUM);
        this.kind = kind;
    }

    public CourseElement kind() {
        return this.kind;
    }

    @Override
    public boolean allowed(GatePlacement.Click click) {
        return click.surface() == GateMount.Surface.FLOOR;
    }

    @Override
    public GateGeometry placement(GatePlacement.Click click, GatePlacement.Preference preference) {
        return GatePlacement.resolveElement(click, preference, this.kind);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!this.allowed(click(context))) {
            Player player = context.getPlayer();
            if (player != null && !context.getLevel().isClientSide()) {
                player.sendOverlayMessage(Component.translatable("propwash.race.element.floor_only"));
            }
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult stackOn(GateEntity target, Player player, ItemStack stack) {
        if (!target.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable("propwash.race.element.no_stack"));
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String id = ItemHelp.PREFIX + this.kind.itemId();
        ItemHelp.summary(id + ".summary")
                .what(id)
                .use(ItemHelp.PREFIX + "course_element")
                .args(ItemHelp.PREFIX + "course_element.use.2", Component.keybind(ROTATE_KEY), Component.keybind(ROTATE_KEY))
                .specs(out -> out.accept(Component.translatable("propwash.race.element.tooltip." + this.kind.key(),
                        GateMounting.degrees((float) this.kind.defaultWidth()), GateMounting.degrees((float) this.kind.defaultHeight()))
                        .withStyle(ChatFormatting.GRAY)))
                .appendTo(builder);
    }
}
