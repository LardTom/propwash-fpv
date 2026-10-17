package de.lardtom.propwash.drone.coating;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;

public final class CoatingComponents {
    public static final RegHolder<DataComponentType<Integer>> COATING = PropwashRegistries.component("waterproof_coating",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT));

    private CoatingComponents() {
    }

    static void bootstrap() {
    }

    public static boolean coated(ItemStack drone) {
        if (drone.isEmpty()) {
            return false;
        }
        DroneBuild build = drone.get(CoreComponents.DRONE_BUILD.get());
        return build != null && CoatingRules.matches(drone.get(COATING.get()), build);
    }

    public static void coat(ItemStack drone, DroneBuild build, boolean coated) {
        if (drone.isEmpty()) {
            return;
        }
        if (coated && build != null) {
            drone.set(COATING.get(), CoatingRules.signature(build));
        } else {
            drone.remove(COATING.get());
        }
    }

    public static void toPart(ItemStack drone, ItemStack stackPart) {
        if (stackPart.isEmpty()) {
            return;
        }
        if (coated(drone)) {
            stackPart.set(COATING.get(), drone.get(COATING.get()));
        } else {
            stackPart.remove(COATING.get());
        }
    }

    public static void fromPart(ItemStack stackPart, ItemStack drone, DroneBuild build) {
        if (drone.isEmpty()) {
            return;
        }
        Integer coating = stackPart.isEmpty() ? null : stackPart.get(COATING.get());
        coat(drone, build, CoatingRules.matches(coating, build));
    }
}
