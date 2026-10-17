package de.lardtom.propwash.accessory;

import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class DroneInventory implements AccessoryInventory {
    private final QuadcopterEntity drone;
    private final AccessoryMount mount;
    private final AccessoryType type;
    private final int size;

    DroneInventory(QuadcopterEntity drone, AccessoryMount mount, AccessoryType type, int size) {
        this.drone = drone;
        this.mount = mount;
        this.type = type;
        this.size = size;
    }

    @Override
    public int size() {
        return this.size;
    }

    private void check(int slot) {
        if (slot < 0 || slot >= this.size) {
            throw new IndexOutOfBoundsException("Zubehör-Slot " + slot + " von " + this.size);
        }
    }

    @Override
    public ItemStack get(int slot) {
        this.check(slot);
        return this.drone.accessoryContents().get(this.mount, slot).copy();
    }

    @Override
    public void set(int slot, ItemStack stack) {
        this.check(slot);
        ItemStack value = stack == null ? ItemStack.EMPTY : stack.copy();
        this.drone.setAccessoryContents(this.drone.accessoryContents().set(this.mount, slot, value));
    }

    @Override
    public ItemStack take(int slot, int count) {
        this.check(slot);
        ItemStack current = this.drone.accessoryContents().get(this.mount, slot);
        if (current.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack rest = current.copy();
        ItemStack taken = rest.split(count);
        this.set(slot, rest);
        return taken;
    }

    @Override
    public int firstFilled() {
        List<ItemStack> stacks = this.drone.accessoryContents().at(this.mount);
        for (int i = 0; i < Math.min(this.size, stacks.size()); i++) {
            if (!stacks.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isEmpty() {
        return this.firstFilled() < 0;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>(this.size);
        for (int i = 0; i < this.size; i++) {
            out.add(this.drone.accessoryContents().get(this.mount, i).copy());
        }
        return out;
    }

    @Override
    public double payloadGrams() {
        return AccessoryBehaviors.INSTANCE.payloadGrams(this.type.unladen(), this.drone.accessoryContents().at(this.mount));
    }
}
