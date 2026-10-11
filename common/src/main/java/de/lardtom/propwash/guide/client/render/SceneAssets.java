package de.lardtom.propwash.guide.client.render;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.guide.scene.BlockSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SceneAssets {
    private static final Map<String, ItemStack> ITEMS = new HashMap<>();
    private static final Map<String, ItemStack> WORN_ITEMS = new HashMap<>();
    private static final Map<BlockSpec, Optional<BlockState>> BLOCKS = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private SceneAssets() {
    }

    public static ItemStack item(String id) {
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack cached = ITEMS.get(id);
        if (cached != null) {
            return cached;
        }
        ItemStack stack = ItemStack.EMPTY;
        Identifier key = Identifier.tryParse(id);
        Optional<Item> item = key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
        if (item.isPresent()) {
            stack = new ItemStack(item.get());
        } else {
            warn("Item " + id);
        }
        ITEMS.put(id, stack);
        return stack;
    }

    public static ItemStack item(String id, float condition) {
        ItemStack stack = item(id);
        int steps = PartWear.toSteps(condition);
        if (stack.isEmpty() || steps >= PartWear.STEPS || !stack.has(CoreComponents.PART.get())) {
            return stack;
        }
        return WORN_ITEMS.computeIfAbsent(id + "@" + steps, key -> PartCondition.with(stack.copy(), PartWear.fromSteps(steps)));
    }

    public static @Nullable BlockState block(BlockSpec spec) {
        return BLOCKS.computeIfAbsent(spec, SceneAssets::resolve).orElse(null);
    }

    private static Optional<BlockState> resolve(BlockSpec spec) {
        Identifier key = Identifier.tryParse(spec.id());
        Optional<Block> block = key == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(key);
        if (block.isEmpty()) {
            warn("Block " + spec.id());
            return Optional.empty();
        }
        BlockState state = block.get().defaultBlockState();
        for (Map.Entry<String, String> entry : spec.properties().entrySet()) {
            Property<?> property = block.get().getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                warn("Blockeigenschaft " + spec.id() + "[" + entry.getKey() + "]");
                continue;
            }
            state = with(state, property, entry.getValue(), spec.id());
        }
        return Optional.of(state);
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value, String id) {
        Optional<T> parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            warn("Blockwert " + id + "[" + property.getName() + "=" + value + "]");
            return state;
        }
        return state.setValue(property, parsed.get());
    }

    public static void clear() {
        ITEMS.clear();
        WORN_ITEMS.clear();
        BLOCKS.clear();
    }

    private static void warn(String what) {
        if (WARNED.add(what)) {
            Constants.LOG.warn("Flugschule: {} nicht gefunden", what);
        }
    }
}
