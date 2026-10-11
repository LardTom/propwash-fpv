package de.lardtom.propwash.content;

import de.lardtom.propwash.Constants;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BatteryChargerMenu extends AbstractContainerMenu {
    public static final int SLOTS = BatteryChargerBlockEntity.SLOTS;
    public static final Identifier EMPTY_SLOT_ICON = Constants.id("gui/slot/battery");
    public static final int[] SLOT_X = {17, 59, 101, 143};
    public static final int SLOT_Y = 30;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 125;
    public static final int INV_START = SLOTS;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int INV_END = HOTBAR_START + 9;

    private final Container charger;
    private final ContainerData data;

    public BatteryChargerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SLOTS), new SimpleContainerData(BatteryChargerBlockEntity.DATA_COUNT));
    }

    public BatteryChargerMenu(int containerId, Inventory inventory, Container charger, ContainerData data) {
        super(ContentBlocks.BATTERY_CHARGER_MENU.get(), containerId);
        checkContainerSize(charger, SLOTS);
        checkContainerDataCount(data, BatteryChargerBlockEntity.DATA_COUNT);
        this.charger = charger;
        this.data = data;
        charger.startOpen(inventory.player);
        for (int slot = 0; slot < SLOTS; slot++) {
            this.addSlot(new BatterySlot(charger, slot, SLOT_X[slot], SLOT_Y));
        }
        this.addDataSlots(data);
        this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
    }

    public int remainingSeconds(int slot) {
        return slot >= 0 && slot < SLOTS ? this.data.get(slot) : BatteryChargerBlockEntity.NO_TIME;
    }

    public boolean powered() {
        return this.data.get(BatteryChargerBlockEntity.DATA_REDSTONE) != 0;
    }

    public ItemStack battery(int slot) {
        return this.slots.get(slot).getItem();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.charger.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.charger.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex < INV_START) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (Batteries.isBattery(stack)) {
            if (!this.moveItemStackTo(stack, 0, SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < HOTBAR_START) {
            if (!this.moveItemStackTo(stack, HOTBAR_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INV_START, HOTBAR_START, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    private static final class BatterySlot extends Slot {
        BatterySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return Batteries.isBattery(itemStack) && !PartCondition.broken(itemStack);
        }

        @Override
        public Identifier getNoItemIcon() {
            return EMPTY_SLOT_ICON;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
