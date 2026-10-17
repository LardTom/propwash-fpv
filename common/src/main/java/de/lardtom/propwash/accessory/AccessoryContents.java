package de.lardtom.propwash.accessory;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.core.DroneBuildCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record AccessoryContents(Map<AccessoryMount, List<ItemStack>> byMount) {
    public static final int MAX_SLOTS = 9;
    public static final AccessoryContents EMPTY = new AccessoryContents(Map.of());
    public static final Codec<AccessoryContents> CODEC = Codec.unboundedMap(DroneBuildCodecs.ACCESSORY_MOUNT,
            ItemStack.OPTIONAL_CODEC.listOf(0, MAX_SLOTS)).xmap(AccessoryContents::new, AccessoryContents::byMount);
    public static final StreamCodec<RegistryFriendlyByteBuf, AccessoryContents> STREAM_CODEC = ByteBufCodecs.<RegistryFriendlyByteBuf,
                    AccessoryMount, List<ItemStack>, Map<AccessoryMount, List<ItemStack>>>map(HashMap::new, DroneBuildCodecs.ACCESSORY_MOUNT_STREAM,
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC, AccessoryMount.values().length)
            .map(AccessoryContents::new, AccessoryContents::byMount);

    public AccessoryContents {
        EnumMap<AccessoryMount, List<ItemStack>> copy = new EnumMap<>(AccessoryMount.class);
        for (Map.Entry<AccessoryMount, List<ItemStack>> entry : byMount.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            List<ItemStack> stacks = new ArrayList<>();
            boolean any = false;
            for (ItemStack stack : entry.getValue()) {
                if (stacks.size() >= MAX_SLOTS) {
                    break;
                }
                ItemStack safe = stack == null ? ItemStack.EMPTY : stack.copy();
                any |= !safe.isEmpty();
                stacks.add(safe);
            }
            if (any) {
                copy.put(entry.getKey(), Collections.unmodifiableList(stacks));
            }
        }
        byMount = Collections.unmodifiableMap(copy);
    }

    public List<ItemStack> at(AccessoryMount mount) {
        List<ItemStack> stacks = this.byMount.get(mount);
        return stacks == null ? List.of() : stacks;
    }

    public ItemStack get(AccessoryMount mount, int slot) {
        List<ItemStack> stacks = this.at(mount);
        return slot >= 0 && slot < stacks.size() ? stacks.get(slot) : ItemStack.EMPTY;
    }

    public AccessoryContents with(AccessoryMount mount, List<ItemStack> stacks) {
        EnumMap<AccessoryMount, List<ItemStack>> copy = new EnumMap<>(AccessoryMount.class);
        copy.putAll(this.byMount);
        copy.put(mount, stacks);
        return new AccessoryContents(copy);
    }

    public AccessoryContents set(AccessoryMount mount, int slot, ItemStack stack) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            throw new IndexOutOfBoundsException("Zubehör-Slot " + slot);
        }
        List<ItemStack> stacks = new ArrayList<>(this.at(mount));
        while (stacks.size() <= slot) {
            stacks.add(ItemStack.EMPTY);
        }
        stacks.set(slot, stack == null ? ItemStack.EMPTY : stack);
        return this.with(mount, stacks);
    }

    public boolean isEmpty() {
        return this.byMount.isEmpty();
    }

    public List<ItemStack> all() {
        List<ItemStack> out = new ArrayList<>();
        for (List<ItemStack> stacks : this.byMount.values()) {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AccessoryContents contents) || !contents.byMount.keySet().equals(this.byMount.keySet())) {
            return false;
        }
        for (Map.Entry<AccessoryMount, List<ItemStack>> entry : this.byMount.entrySet()) {
            if (!ItemStack.listMatches(entry.getValue(), contents.byMount.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (Map.Entry<AccessoryMount, List<ItemStack>> entry : this.byMount.entrySet()) {
            hash += entry.getKey().hashCode() ^ ItemStack.hashStackList(entry.getValue());
        }
        return hash;
    }

    @Override
    public String toString() {
        return "AccessoryContents" + this.byMount;
    }
}
