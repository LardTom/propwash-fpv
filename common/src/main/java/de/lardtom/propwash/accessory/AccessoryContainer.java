package de.lardtom.propwash.accessory;

import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class AccessoryContainer implements Container {
    private final QuadcopterEntity drone;
    private final AccessoryMount[] mountOf;
    private final int[] slotOf;
    private final int[] sizes;
    private final NonNullList<ItemStack> items;
    private AccessoryContents written;

    public AccessoryContainer(QuadcopterEntity drone, int[] sizes) {
        this.drone = drone;
        this.sizes = sizes.clone();
        int total = 0;
        for (int size : sizes) {
            total += size;
        }
        this.mountOf = new AccessoryMount[total];
        this.slotOf = new int[total];
        int index = 0;
        for (AccessoryMount mount : AccessoryMount.values()) {
            for (int slot = 0; slot < sizes[mount.ordinal()]; slot++) {
                this.mountOf[index] = mount;
                this.slotOf[index] = slot;
                index++;
            }
        }
        this.items = NonNullList.withSize(total, ItemStack.EMPTY);
        this.load(drone.accessoryContents());
    }

    private void load(AccessoryContents contents) {
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, contents.get(this.mountOf[i], this.slotOf[i]).copy());
        }
        this.written = contents;
    }

    public void refresh() {
        AccessoryContents now = this.drone.accessoryContents();
        if (!now.equals(this.written)) {
            this.load(now);
        }
    }

    public QuadcopterEntity drone() {
        return this.drone;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        this.refresh();
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        this.refresh();
        return index >= 0 && index < this.items.size() ? this.items.get(index) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        this.refresh();
        ItemStack taken = ContainerHelper.removeItem(this.items, index, count);
        if (!taken.isEmpty()) {
            this.setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        this.refresh();
        ItemStack taken = ContainerHelper.takeItem(this.items, index);
        this.setChanged();
        return taken;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= this.items.size()) {
            return;
        }
        this.refresh();
        this.items.set(index, stack == null ? ItemStack.EMPTY : stack);
        this.setChanged();
    }

    @Override
    public void setChanged() {
        AccessoryContents current = this.drone.accessoryContents();
        Map<AccessoryMount, List<ItemStack>> byMount = new EnumMap<>(AccessoryMount.class);
        for (AccessoryMount mount : AccessoryMount.values()) {
            List<ItemStack> stored = current.at(mount);
            int size = Math.max(this.sizes[mount.ordinal()], stored.size());
            List<ItemStack> stacks = new ArrayList<>(size);
            for (int slot = 0; slot < size; slot++) {
                stacks.add(slot < stored.size() ? stored.get(slot) : ItemStack.EMPTY);
            }
            byMount.put(mount, stacks);
        }
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack stack = this.items.get(i);
            if (!ItemStack.matches(stack, this.written.get(this.mountOf[i], this.slotOf[i]))) {
                byMount.get(this.mountOf[i]).set(this.slotOf[i], stack.copy());
            }
        }
        AccessoryContents next = new AccessoryContents(byMount);
        this.drone.setAccessoryContents(next);
        this.written = next;
    }

    @Override
    public boolean stillValid(Player player) {
        return AccessoryMenu.usable(this.drone, player);
    }

    @Override
    public void clearContent() {
        this.refresh();
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, ItemStack.EMPTY);
        }
        this.setChanged();
    }
}
