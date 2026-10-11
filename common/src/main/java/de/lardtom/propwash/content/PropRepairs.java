package de.lardtom.propwash.content;

import de.lardtom.propwash.build.PropRepair;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.core.PartRef;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import java.util.Objects;

public final class PropRepairs {
    private PropRepairs() {
    }

    public static boolean isProp(ItemStack stack) {
        return stack != null && !stack.isEmpty() && DroneAssembly.category(stack).orElse(null) == PartCategory.PROP;
    }

    public static boolean repairable(ItemStack stack) {
        return isProp(stack) && PropRepair.repairable(PartCondition.of(stack));
    }

    public static boolean combinable(ItemStack first, ItemStack second) {
        if (!repairable(first) || !repairable(second) || !ItemStack.isSameItem(first, second)) {
            return false;
        }
        PartRef a = first.get(CoreComponents.PART.get());
        PartRef b = second.get(CoreComponents.PART.get());
        return Objects.equals(a, b);
    }

    public static ItemStack combine(ItemStack first, ItemStack second) {
        if (!combinable(first, second)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = first.copyWithCount(1);
        PartCondition.store(out, PropRepair.combine(PartCondition.of(first), PartCondition.of(second)));
        return out;
    }

    public static ItemStack combine(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return ItemStack.EMPTY;
        }
        ItemStack first = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = stack;
            } else {
                return combine(first, stack);
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack grindstone(ItemStack input, ItemStack additional) {
        if (input.getCount() != 1 || additional.getCount() != 1) {
            return ItemStack.EMPTY;
        }
        return combine(input, additional);
    }

    public static Slot grindstoneSlot(Slot original) {
        return new GrindstoneSlot(original);
    }

    private static final class GrindstoneSlot extends Slot {
        private final Slot original;

        private GrindstoneSlot(Slot original) {
            super(original.container, original.getContainerSlot(), original.x, original.y);
            this.original = original;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return repairable(stack) || this.original.mayPlace(stack);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return isProp(stack) ? 1 : super.getMaxStackSize(stack);
        }
    }
}
