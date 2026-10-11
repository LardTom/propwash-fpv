package de.lardtom.propwash.drone.bind;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class BindTooltips {
    public static final String PREFIX = "propwash.drone.bind.tooltip.";
    public static volatile Supplier<@Nullable Viewer> VIEWER = () -> null;

    private BindTooltips() {
    }

    public static Optional<BindAccess> access(ItemStack stack) {
        ReceiverBind bind = BindComponents.of(stack);
        if (bind == null) {
            return Optional.empty();
        }
        Viewer viewer = VIEWER.get();
        if (viewer == null) {
            return Optional.of(bind.cleared() ? BindAccess.CLEARED : BindAccess.OTHER);
        }
        return Optional.of(BindAccess.of(bind, viewer.player()));
    }

    public static Optional<Component> line(ItemStack stack) {
        return access(stack).map(BindTooltips::line);
    }

    public static Component line(BindAccess access) {
        ChatFormatting color = switch (access) {
            case PILOT -> ChatFormatting.AQUA;
            case OTHER -> ChatFormatting.GOLD;
            case CLEARED, UNBOUND -> ChatFormatting.GRAY;
        };
        return Component.translatable(PREFIX + access.key()).withStyle(color);
    }

    public record Viewer(UUID player) {
    }
}
