package de.lardtom.propwash.content;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.RecycleYield;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

public final class Recycling {
    private Recycling() {
    }

    public static Optional<Yield> yieldOf(PartInfo part) {
        if (part.missing()) {
            return Optional.empty();
        }
        Optional<RecycleYield> own = part.definition().basics().recycle();
        if (own.isPresent()) {
            RecycleYield yield = own.get();
            return yield.none() ? Optional.empty() : Optional.of(new Yield(yield.item(), yield.count()));
        }
        return categoryDefault(part).map(material -> new Yield(Constants.id(material.key()), 1));
    }

    public static Optional<CraftingMaterial> categoryDefault(PartInfo part) {
        return switch (part) {
            case FrameType frame -> Optional.of(CraftingMaterial.CARBON_PLATE);
            case StackType stack -> Optional.of(CraftingMaterial.CIRCUIT_BOARD);
            case MotorType motor -> Optional.of(CraftingMaterial.COPPER_COIL);
            case VideoType video -> Optional.of(CraftingMaterial.CIRCUIT_BOARD);
            case BatteryType battery -> Optional.of(CraftingMaterial.LIPO_CELL);
            case PropType prop -> Optional.empty();
            case AccessoryType accessory -> Optional.empty();
        };
    }

    public static boolean recyclable(PartInfo part) {
        return yieldOf(part).isPresent();
    }

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && PartCondition.broken(stack) && DroneAssembly.part(stack).map(Recycling::recyclable).orElse(false);
    }

    public static List<ItemStack> recycle(ItemStack stack) {
        if (!accepts(stack)) {
            return List.of();
        }
        Yield result = yieldOf(DroneAssembly.part(stack).orElseThrow()).orElseThrow();
        Optional<Item> item = result.item();
        if (item.isEmpty()) {
            return List.of();
        }
        ItemStack out = new ItemStack(item.get());
        out.setCount(result.count() * stack.getCount());
        return List.of(out);
    }

    public record Yield(Identifier itemId, int count) {
        public Optional<Item> item() {
            return BuiltInRegistries.ITEM.getOptional(this.itemId).filter(item -> item != Items.AIR);
        }
    }
}
