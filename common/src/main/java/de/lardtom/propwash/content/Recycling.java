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
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartRef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class Recycling {
    public static final int FULL = 100;
    public static final int HALF = 50;
    public static final int QUARTER = 25;
    public static final int HALF_FROM = 60;

    private Recycling() {
    }

    public static int tier(float condition) {
        int percent = PartCondition.percent(condition);
        if (percent >= FULL) {
            return FULL;
        }
        return percent >= HALF_FROM ? HALF : QUARTER;
    }

    public static int tier(ItemStack stack) {
        return tier(PartCondition.of(stack));
    }

    public static boolean isPart(ItemStack stack) {
        return !stack.isEmpty() && stack.has(CoreComponents.PART.get());
    }

    public static Optional<Materials> fallback(PartInfo part) {
        if (part.missing()) {
            return Optional.empty();
        }
        Optional<RecycleYield> own = part.definition().basics().recycle();
        if (own.isPresent()) {
            RecycleYield yield = own.get();
            return yield.none() ? Optional.empty() : Optional.of(Materials.single(yield.item(), yield.count()));
        }
        return categoryDefault(part).map(material -> Materials.single(Constants.id(material.key()), 1));
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

    public static Plan plan(ItemStack stack, Function<Item, Optional<Materials>> recipes) {
        if (stack.isEmpty()) {
            return Plan.EMPTY;
        }
        PartRef ref = stack.get(CoreComponents.PART.get());
        if (ref == null) {
            return new Plan(Status.NOT_A_PART, Optional.empty());
        }
        Optional<PartInfo> part = ref.resolve();
        if (part.isEmpty() || part.get().missing()) {
            return new Plan(Status.MISSING, Optional.empty());
        }
        Optional<Materials> materials = recipes.apply(stack.getItem()).or(() -> fallback(part.get())).filter(Materials::registered);
        return materials.map(found -> new Plan(Status.READY, Optional.of(found))).orElseGet(() -> new Plan(Status.NOT_RECYCLABLE, Optional.empty()));
    }

    public static int piecesThatFit(Materials materials, int available, int tier, Container output) {
        for (int pieces = available; pieces > 0; pieces--) {
            if (fits(materials, pieces, tier, output)) {
                return pieces;
            }
        }
        return 0;
    }

    public static boolean fits(Materials materials, int pieces, int tier, Container output) {
        SimpleContainer copy = new SimpleContainer(output.getContainerSize());
        for (int slot = 0; slot < output.getContainerSize(); slot++) {
            copy.setItem(slot, output.getItem(slot).copy());
        }
        for (int i = 0; i < materials.entries().size(); i++) {
            int most = materials.most(i, pieces, tier);
            Optional<Item> item = materials.item(i);
            if (most > 0 && (item.isEmpty() || !insert(copy, new ItemStack(item.get(), most)).isEmpty())) {
                return false;
            }
        }
        return true;
    }

    public static List<ItemStack> roll(Materials materials, int pieces, int tier, RandomSource random) {
        int[] amounts = materials.roll(pieces, tier, random);
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            Optional<Item> item = materials.item(i);
            if (amounts[i] > 0 && item.isPresent()) {
                out.add(new ItemStack(item.get(), amounts[i]));
            }
        }
        return out;
    }

    public static ItemStack insert(Container container, ItemStack add) {
        ItemStack rest = add.copy();
        for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++) {
            ItemStack here = container.getItem(slot);
            if (!here.isEmpty() && ItemStack.isSameItemSameComponents(here, rest)) {
                int room = Math.min(here.getMaxStackSize(), container.getMaxStackSize(here)) - here.getCount();
                if (room > 0) {
                    int moved = Math.min(room, rest.getCount());
                    container.setItem(slot, here.copyWithCount(here.getCount() + moved));
                    rest.shrink(moved);
                }
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !rest.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                int moved = Math.min(Math.min(rest.getMaxStackSize(), container.getMaxStackSize(rest)), rest.getCount());
                container.setItem(slot, rest.copyWithCount(moved));
                rest.shrink(moved);
            }
        }
        return rest;
    }

    public enum Status {
        EMPTY,
        READY,
        NOT_A_PART,
        MISSING,
        NOT_RECYCLABLE
    }

    public record Plan(Status status, Optional<Materials> materials) {
        public static final Plan EMPTY = new Plan(Status.EMPTY, Optional.empty());
    }

    public record Material(Identifier item, int count) {
        public Material {
            if (count < 1) {
                throw new IllegalArgumentException("Recycling-Menge muss mindestens 1 sein: " + item + " " + count);
            }
        }
    }

    public record Materials(List<Material> entries, int perCraft) {
        public Materials {
            entries = List.copyOf(entries);
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("Recycling ohne Materialien");
            }
            if (perCraft < 1) {
                throw new IllegalArgumentException("Ergebnisanzahl muss mindestens 1 sein: " + perCraft);
            }
        }

        public static Materials of(List<Identifier> ingredients, int perCraft) {
            Map<Identifier, Integer> counts = new LinkedHashMap<>();
            for (Identifier ingredient : ingredients) {
                counts.merge(ingredient, 1, Integer::sum);
            }
            List<Material> entries = new ArrayList<>();
            counts.forEach((item, count) -> entries.add(new Material(item, count)));
            return new Materials(entries, perCraft);
        }

        public static Materials single(Identifier item, int count) {
            return new Materials(List.of(new Material(item, count)), 1);
        }

        public long numerator(int index, int pieces, int tier) {
            return (long) this.entries.get(index).count() * pieces * tier;
        }

        public long denominator() {
            return (long) FULL * this.perCraft;
        }

        public double expected(int index, int pieces, int tier) {
            return this.numerator(index, pieces, tier) / (double) this.denominator();
        }

        public int least(int index, int pieces, int tier) {
            return (int) (this.numerator(index, pieces, tier) / this.denominator());
        }

        public int most(int index, int pieces, int tier) {
            long denominator = this.denominator();
            return (int) ((this.numerator(index, pieces, tier) + denominator - 1) / denominator);
        }

        public boolean exact(int index, int pieces, int tier) {
            return this.numerator(index, pieces, tier) % this.denominator() == 0;
        }

        public int[] roll(int pieces, int tier, RandomSource random) {
            int[] amounts = new int[this.entries.size()];
            long denominator = this.denominator();
            for (int i = 0; i < amounts.length; i++) {
                long numerator = this.numerator(i, pieces, tier);
                long rest = numerator % denominator;
                amounts[i] = (int) (numerator / denominator) + (rest > 0 && random.nextInt((int) denominator) < rest ? 1 : 0);
            }
            return amounts;
        }

        public Optional<Item> item(int index) {
            return BuiltInRegistries.ITEM.getOptional(this.entries.get(index).item()).filter(item -> item != Items.AIR);
        }

        public boolean registered() {
            for (int i = 0; i < this.entries.size(); i++) {
                if (this.item(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }
}
