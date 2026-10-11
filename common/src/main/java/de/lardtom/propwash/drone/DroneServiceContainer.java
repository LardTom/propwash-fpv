package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.PartCondition;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class DroneServiceContainer implements Container {
    public static final int BATTERY = 0;
    public static final int PROP_START = 1;
    public static final int SIZE = PROP_START + PartWear.POSITIONS;

    private final QuadcopterEntity drone;

    public DroneServiceContainer(QuadcopterEntity drone) {
        this.drone = drone;
    }

    public QuadcopterEntity drone() {
        return this.drone;
    }

    public static boolean isProp(int slot) {
        return slot >= PROP_START && slot < SIZE;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == BATTERY) {
            return this.drone.batteryStack();
        }
        if (isProp(slot)) {
            return propStack(this.drone, slot - PROP_START);
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack propStack(QuadcopterEntity drone, int position) {
        return PartCondition.with(ContentItems.stack(drone.build().prop()), drone.wear().prop(position));
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot != BATTERY || count <= 0 || this.drone.batteryLocked()) {
            return ItemStack.EMPTY;
        }
        return this.drone.takeBattery();
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.removeItem(slot, 1);
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        if (slot != BATTERY || this.drone.batteryLocked()) {
            return;
        }
        Optional<BatteryType> type = Batteries.typeOf(itemStack);
        if (itemStack.isEmpty() || type.isEmpty()) {
            if (this.drone.hasBattery()) {
                this.drone.takeBattery();
            }
            return;
        }
        this.drone.insertBattery(type.get(), Batteries.charge(itemStack), PartCondition.of(itemStack));
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return DroneServiceMenu.usable(this.drone, player);
    }

    @Override
    public void clearContent() {
        this.setItem(BATTERY, ItemStack.EMPTY);
    }
}
