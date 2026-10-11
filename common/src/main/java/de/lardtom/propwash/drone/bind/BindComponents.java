package de.lardtom.propwash.drone.bind;

import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class BindComponents {
    public static final RegHolder<DataComponentType<ReceiverBind>> RECEIVER_BIND = PropwashRegistries.component("receiver_bind",
            b -> b.persistent(BindCodecs.CODEC).networkSynchronized(BindCodecs.STREAM_CODEC));

    private BindComponents() {
    }

    static void bootstrap() {
    }

    public static @Nullable ReceiverBind of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(RECEIVER_BIND.get());
    }

    public static void store(ItemStack stack, @Nullable ReceiverBind bind) {
        if (stack.isEmpty()) {
            return;
        }
        if (bind == null) {
            stack.remove(RECEIVER_BIND.get());
        } else {
            stack.set(RECEIVER_BIND.get(), bind);
        }
    }

    public static void copy(ItemStack from, ItemStack to) {
        ReceiverBind bind = of(from);
        if (bind != null) {
            store(to, bind);
        }
    }
}
