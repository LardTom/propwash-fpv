package de.lardtom.propwash.accessory;

import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.QuadcopterEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AccessoryMenu extends AbstractContainerMenu {
    public static final Component TITLE = Component.translatable("propwash.accessory.menu.title");
    public static final int MOUNTS = AccessoryMount.values().length;
    public static final int LEFT = 8;
    public static final int SLOT_X = 34;
    public static final int FIRST_ROW_Y = 30;
    public static final int ROW_HEIGHT = 30;
    public static final int WIDTH = SLOT_X + AccessoryBehavior.MAX_SLOTS * 18 + 8;
    public static final StreamCodec<ByteBuf, Opening> OPENING = StreamCodec.of((buf, opening) -> {
        VarInt.write(buf, opening.droneId());
        for (int i = 0; i < MOUNTS; i++) {
            buf.writeByte(opening.sizes()[i]);
        }
    }, buf -> {
        int droneId = VarInt.read(buf);
        int[] sizes = new int[MOUNTS];
        for (int i = 0; i < MOUNTS; i++) {
            sizes[i] = Math.max(0, Math.min(AccessoryBehavior.MAX_SLOTS, buf.readUnsignedByte()));
        }
        return new Opening(droneId, sizes);
    });

    private final int droneId;
    private final Player player;
    private final Container container;
    private final int[] sizes;
    private final int[] firstIndex = new int[MOUNTS];
    private final List<AccessoryMount> rows = new ArrayList<>();
    private final int total;
    private final int inventoryY;

    public AccessoryMenu(int containerId, Inventory inventory, Opening opening) {
        this(containerId, inventory, opening.droneId(), opening.sizes().clone(), new SimpleContainer(sum(opening.sizes())));
    }

    public AccessoryMenu(int containerId, Inventory inventory, QuadcopterEntity drone) {
        this(containerId, inventory, drone.getId(), sizes(drone), new AccessoryContainer(drone, sizes(drone)));
    }

    private AccessoryMenu(int containerId, Inventory inventory, int droneId, int[] sizes, Container container) {
        super(AccessoryModule.ACCESSORY_MENU.get(), containerId);
        this.droneId = droneId;
        this.player = inventory.player;
        this.container = container;
        this.sizes = sizes;
        int index = 0;
        for (AccessoryMount mount : AccessoryMount.values()) {
            int size = sizes[mount.ordinal()];
            this.firstIndex[mount.ordinal()] = index;
            if (size <= 0) {
                continue;
            }
            int row = this.rows.size();
            this.rows.add(mount);
            for (int slot = 0; slot < size; slot++) {
                this.addSlot(new AccessorySlotView(container, index + slot, SLOT_X + slot * 18, FIRST_ROW_Y + row * ROW_HEIGHT, mount, slot));
            }
            index += size;
        }
        this.total = index;
        this.inventoryY = inventoryY(this.rows.size());
        this.addStandardInventorySlots(inventory, inventoryX(), this.inventoryY);
    }

    public static int inventoryX() {
        return (WIDTH - 162) / 2;
    }

    public static int inventoryY(int rows) {
        return FIRST_ROW_Y + Math.max(1, rows) * ROW_HEIGHT + 10;
    }

    public static int height(int rows) {
        return inventoryY(rows) + 58 + 18 + 8;
    }

    public static int[] sizes(QuadcopterEntity drone) {
        int[] sizes = new int[MOUNTS];
        for (AccessoryMount mount : AccessoryMount.values()) {
            sizes[mount.ordinal()] = drone.build().accessories().at(mount).map(type -> AccessoryBehaviors.INSTANCE.slots(type.unladen())).orElse(0);
        }
        return sizes;
    }

    private static int sum(int[] sizes) {
        int total = 0;
        for (int size : sizes) {
            total += size;
        }
        return total;
    }

    public static boolean storable(ItemStack stack) {
        return stack.getItem().canFitInsideContainerItems() && !stack.has(AccessoryComponents.CONTENTS.get());
    }

    public static boolean usable(@Nullable QuadcopterEntity drone, Player player) {
        return drone != null && !drone.isRemoved() && drone.level() == player.level()
                && player.position().distanceTo(drone.position()) <= DroneServiceMenu.MAX_DISTANCE;
    }

    public int droneId() {
        return this.droneId;
    }

    public List<AccessoryMount> rows() {
        return List.copyOf(this.rows);
    }

    public int size(AccessoryMount mount) {
        return this.sizes[mount.ordinal()];
    }

    public int inventoryTop() {
        return this.inventoryY;
    }

    public @Nullable QuadcopterEntity drone() {
        if (this.container instanceof AccessoryContainer accessories) {
            return accessories.drone();
        }
        Entity entity = this.player.level().getEntity(this.droneId);
        return entity instanceof QuadcopterEntity drone ? drone : null;
    }

    public @Nullable AccessoryType accessory(AccessoryMount mount) {
        QuadcopterEntity drone = this.drone();
        return drone == null ? null : drone.build().accessories().at(mount).map(AccessoryType::unladen).orElse(null);
    }

    @Override
    public void broadcastChanges() {
        if (this.container instanceof AccessoryContainer accessories) {
            accessories.refresh();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.container instanceof AccessoryContainer accessories) {
            return accessories.stillValid(player);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inventoryStart = this.total;
        int inventoryEnd = this.slots.size();
        if (slotIndex < this.total) {
            if (!this.moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, this.total, false)) {
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

    public record Opening(int droneId, int[] sizes) {
    }

    public final class AccessorySlotView extends Slot {
        private final AccessoryMount mount;
        private final int position;

        AccessorySlotView(Container container, int index, int x, int y, AccessoryMount mount, int position) {
            super(container, index, x, y);
            this.mount = mount;
            this.position = position;
        }

        public AccessoryMount mount() {
            return this.mount;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!storable(stack)) {
                return false;
            }
            AccessoryBehavior behavior = AccessoryBehaviors.INSTANCE.of(AccessoryMenu.this.accessory(this.mount));
            if (behavior == null) {
                return AccessoryMenu.this.container instanceof SimpleContainer;
            }
            try {
                return behavior.accepts(this.position, stack);
            } catch (RuntimeException e) {
                return false;
            }
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            AccessoryBehavior behavior = AccessoryBehaviors.INSTANCE.of(AccessoryMenu.this.accessory(this.mount));
            int vanilla = super.getMaxStackSize(stack);
            if (behavior == null) {
                return vanilla;
            }
            try {
                return Math.max(1, Math.min(vanilla, behavior.maxStackSize(this.position, stack)));
            } catch (RuntimeException e) {
                return vanilla;
            }
        }
    }
}
