package de.lardtom.propwash.content;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.CoreComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

public class FpvGogglesItem extends Item {
    private final VideoSystem system;

    public FpvGogglesItem(VideoSystem system, Item.Properties properties) {
        super(properties);
        this.system = system;
    }

    public VideoSystem system() {
        return this.system;
    }

    public String helpId() {
        return "goggles_" + this.system.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag tooltipFlag) {
        String key = "propwash.content.goggles." + this.system.name().toLowerCase(Locale.ROOT);
        ItemHelp.summary(ItemHelp.PREFIX + this.helpId() + ".summary")
                .what(ItemHelp.PREFIX + "goggles")
                .whatLine(Component.translatable(key + ".description").withStyle(ChatFormatting.DARK_GRAY))
                .use(ItemHelp.PREFIX + "goggles")
                .fits(this::fits)
                .specs(out -> out.accept(Component.translatable("propwash.content.goggles.receives", this.receivedSystems())
                        .withStyle(ChatFormatting.GRAY)))
                .appendTo(builder);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (itemStack.has(CoreComponents.BOUND_DRONE.get())) {
            itemStack.remove(CoreComponents.BOUND_DRONE.get());
        }
    }

    private void fits(Consumer<Component> out) {
        if (this.system == VideoSystem.CREATIVE) {
            out.accept(ItemHelp.line(ItemHelp.PREFIX + "goggles_creative.fits"));
            out.accept(Component.translatable("propwash.content.goggles.creative_only").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        for (VideoType video : VideoType.values()) {
            if (this.system.receives(video.system)) {
                out.accept(Component.translatable("propwash.content.goggles.compatible",
                        Component.translatable(video.translationKey()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private Component receivedSystems() {
        if (this.system == VideoSystem.CREATIVE) {
            return Component.translatable("propwash.content.video_system.all").withStyle(ChatFormatting.WHITE);
        }
        return PartTooltips.videoSystemName(this.system).copy().withStyle(ChatFormatting.WHITE);
    }
}
