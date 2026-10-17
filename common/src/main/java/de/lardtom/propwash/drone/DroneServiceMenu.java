package de.lardtom.propwash.drone;

import de.lardtom.propwash.accessory.AccessoryModule;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BatteryChargerMenu;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.bind.BindAccess;
import de.lardtom.propwash.drone.bind.ReceiverBinds;
import de.lardtom.propwash.server.WorldRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class DroneServiceMenu extends AbstractContainerMenu {
    public static final Component TITLE = Component.translatable("container.propwash.drone_service");
    public static final double MAX_DISTANCE = 8.0;
    public static final int WIDTH = 236;
    public static final int HEIGHT = 244;
    public static final int BATTERY_SLOT = DroneServiceContainer.BATTERY;
    public static final int PROP_START = DroneServiceContainer.PROP_START;
    public static final int PROP_END = DroneServiceContainer.SIZE;
    public static final int SLOT_X = 131;
    public static final int SLOT_Y = 81;
    public static final int[] PROP_X = {17, 95, 17, 95};
    public static final int[] PROP_Y = {33, 33, 75, 75};
    public static final int INVENTORY_X = 38;
    public static final int INVENTORY_Y = 164;
    public static final int UPTILT_BUTTON = 1000;
    public static final int UPTILT_BUTTON_MAX = UPTILT_BUTTON + (int) TuneMapping.CAMERA_UPTILT_MAX;
    public static final int RESET_BIND_BUTTON = 2000;
    public static final int ACCESSORY_BUTTON = 3000;
    public static final int INV_START = PROP_END;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int INV_END = HOTBAR_START + 9;
    private static final int HOTBAR_SIZE = 9;
    private static final int OFFHAND = 40;

    private final int droneId;
    private final Player player;
    private final Container parts;
    private final DataSlot bindAccess;

    public DroneServiceMenu(int containerId, Inventory inventory, Integer droneId) {
        this(containerId, inventory, droneId, new SimpleContainer(DroneServiceContainer.SIZE));
    }

    public DroneServiceMenu(int containerId, Inventory inventory, QuadcopterEntity drone) {
        this(containerId, inventory, drone.getId(), new DroneServiceContainer(drone));
    }

    private DroneServiceMenu(int containerId, Inventory inventory, int droneId, Container parts) {
        super(DroneModule.DRONE_SERVICE_MENU.get(), containerId);
        this.droneId = droneId;
        this.player = inventory.player;
        this.parts = parts;
        this.addSlot(new BatterySlot(parts, SLOT_X, SLOT_Y));
        for (int position = 0; position < PartWear.POSITIONS; position++) {
            this.addSlot(new PropSlot(parts, PROP_START + position, PROP_X[position], PROP_Y[position]));
        }
        this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
        this.bindAccess = this.addDataSlot(parts instanceof DroneServiceContainer container && this.player instanceof ServerPlayer serverPlayer
                ? new BindAccessSlot(container, serverPlayer) : DataSlot.standalone());
    }

    public BindAccess bindAccess() {
        return BindAccess.byOrdinal(this.bindAccess.get());
    }

    public static boolean usable(@Nullable QuadcopterEntity drone, Player player) {
        return drone != null && !drone.isRemoved() && !drone.batteryLocked() && drone.level() == player.level()
                && player.position().distanceTo(drone.position()) <= MAX_DISTANCE;
    }

    public static boolean isPropSlot(int slotIndex) {
        return slotIndex >= PROP_START && slotIndex < PROP_END;
    }

    public int droneId() {
        return this.droneId;
    }

    public @Nullable QuadcopterEntity drone() {
        if (this.parts instanceof DroneServiceContainer container) {
            return container.drone();
        }
        Entity entity = this.player.level().getEntity(this.droneId);
        return entity instanceof QuadcopterEntity drone ? drone : null;
    }

    public boolean accepts(ItemStack stack) {
        QuadcopterEntity drone = this.drone();
        if (drone == null) {
            return false;
        }
        Optional<BatteryType> type = Batteries.typeOf(stack);
        return type.isPresent() && WorldRules.partCompatibility(this.player.level()).batteryFits(drone.build().frame(), type.get());
    }

    public Optional<PropService.Fit> propFit(ItemStack stack, Player player) {
        QuadcopterEntity drone = this.drone();
        Optional<PropType> type = DroneInteractions.propType(stack);
        if (drone == null || type.isEmpty()) {
            return Optional.empty();
        }
        DroneBuild build = drone.build();
        int available = player.hasInfiniteMaterials() ? PartWear.POSITIONS : stack.getCount();
        return Optional.of(PropService.fit(WorldRules.partCompatibility(player.level()), build, type.get(), available));
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.parts instanceof DroneServiceContainer container) {
            return container.stillValid(player);
        }
        return true;
    }

    public static int uptiltButton(float degrees) {
        return UPTILT_BUTTON + Math.round(TuneMapping.clampUptilt(degrees));
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == ACCESSORY_BUTTON) {
            QuadcopterEntity drone = this.drone();
            return drone != null && this.parts instanceof DroneServiceContainer && player instanceof ServerPlayer serverPlayer
                    && AccessoryModule.openMenu(serverPlayer, drone);
        }
        boolean reset = buttonId == RESET_BIND_BUTTON;
        if (!reset && (buttonId < UPTILT_BUTTON || buttonId > UPTILT_BUTTON_MAX)) {
            return false;
        }
        QuadcopterEntity drone = this.drone();
        if (!(this.parts instanceof DroneServiceContainer) || drone == null || player.level().isClientSide()) {
            return false;
        }
        if (reset) {
            return resetBind(drone, player);
        }
        return setUptilt(drone, buttonId - UPTILT_BUTTON);
    }

    public static boolean resetBind(QuadcopterEntity drone, Player player) {
        if (!ReceiverBinds.reset(drone)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("propwash.drone.bind.reset", DroneInteractions.droneName(drone))
                    .withStyle(ChatFormatting.GOLD));
        }
        return true;
    }

    public static boolean setUptilt(QuadcopterEntity drone, float degrees) {
        FlightTune before = drone.tune();
        FlightTune after = new FlightTune(CameraMount.withUptilt(before.values(), drone.build(), degrees));
        if (after.equals(before)) {
            return false;
        }
        drone.setTune(after);
        return true;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof PropSlot) && super.canDragTo(slot);
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
        if (!isPropSlot(slotIndex)) {
            super.clicked(slotIndex, buttonNum, containerInput, player);
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        int position = slotIndex - PROP_START;
        if (containerInput == ContainerInput.PICKUP) {
            this.swapProps(player, this.getCarried(), position);
        } else if (containerInput == ContainerInput.SWAP && (buttonNum >= 0 && buttonNum < HOTBAR_SIZE || buttonNum == OFFHAND)) {
            this.swapProps(player, player.getInventory().getItem(buttonNum), position);
        }
    }

    public boolean swapProps(Player player, ItemStack source, int position) {
        QuadcopterEntity drone = this.drone();
        if (!(this.parts instanceof DroneServiceContainer) || drone == null || drone.batteryLocked() || source.isEmpty()) {
            return false;
        }
        Optional<PropType> type = DroneInteractions.propType(source);
        Optional<PropService.Fit> fit = this.propFit(source, player);
        if (type.isEmpty() || fit.isEmpty() || !PropService.allowed(fit.get())) {
            return false;
        }
        boolean creative = player.hasInfiniteMaterials();
        float replacement = PartCondition.of(source);
        DroneBuild build = drone.build();
        PartWear wear = drone.wear();
        if (fit.get() == PropService.Fit.SWAP_ONE) {
            ItemStack old = DroneServiceContainer.propStack(drone, position);
            drone.setWear(PropService.swapOne(wear, position, replacement));
            if (!creative) {
                source.shrink(1);
            }
            this.giveBack(player, old);
        } else {
            for (int i = 0; i < PartWear.POSITIONS; i++) {
                this.giveBack(player, DroneServiceContainer.propStack(drone, i));
            }
            drone.setBuild(build.withProp(type.get()));
            drone.setWear(PropService.swapAll(wear, replacement));
            if (!creative) {
                source.shrink(PartWear.POSITIONS);
            }
        }
        player.level().playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.7F, 1.6F);
        return true;
    }

    private void giveBack(Player player, ItemStack stack) {
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
    }

    public int quickTarget(ItemStack stack, Player player) {
        QuadcopterEntity drone = this.drone();
        Optional<PropService.Fit> fit = this.propFit(stack, player);
        if (drone == null || fit.isEmpty()) {
            return PropService.NONE;
        }
        return switch (fit.get()) {
            case SWAP_ONE -> PropService.worstReplaceable(drone.wear(), PartCondition.of(stack));
            case SWAP_ALL -> 0;
            case NEED_FOUR, TOO_LARGE, TOO_SMALL, WRONG_MOUNT -> PropService.NONE;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem() || isPropSlot(slotIndex)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex == BATTERY_SLOT) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (this.accepts(stack) && !this.slots.get(BATTERY_SLOT).hasItem()) {
            if (!this.moveItemStackTo(stack, BATTERY_SLOT, BATTERY_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.quickTarget(stack, player) != PropService.NONE) {
            if (!player.level().isClientSide()) {
                this.quickSwapProps(player, slot, stack);
            }
            return ItemStack.EMPTY;
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

    private void quickSwapProps(Player player, Slot slot, ItemStack stack) {
        for (int i = 0; i <= PartWear.POSITIONS && !stack.isEmpty(); i++) {
            int target = this.quickTarget(stack, player);
            if (target == PropService.NONE || !this.swapProps(player, stack, target)) {
                break;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
    }

    private final class BatterySlot extends Slot {
        BatterySlot(Container container, int x, int y) {
            super(container, BATTERY_SLOT, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return DroneServiceMenu.this.accepts(itemStack);
        }

        @Override
        public Identifier getNoItemIcon() {
            return BatteryChargerMenu.EMPTY_SLOT_ICON;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPickup(Player player) {
            QuadcopterEntity drone = DroneServiceMenu.this.drone();
            return drone != null && !drone.batteryLocked();
        }
    }

    public static final class PropSlot extends Slot {
        private final int position;

        PropSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.position = slot - PROP_START;
        }

        public int position() {
            return this.position;
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static final class BindAccessSlot extends DataSlot {
        private final DroneServiceContainer container;
        private final ServerPlayer player;

        private BindAccessSlot(DroneServiceContainer container, ServerPlayer player) {
            this.container = container;
            this.player = player;
        }

        @Override
        public int get() {
            QuadcopterEntity drone = this.container.drone();
            return drone == null ? BindAccess.UNBOUND.ordinal() : ReceiverBinds.access(this.player, drone).ordinal();
        }

        @Override
        public void set(int value) {
        }
    }
}
