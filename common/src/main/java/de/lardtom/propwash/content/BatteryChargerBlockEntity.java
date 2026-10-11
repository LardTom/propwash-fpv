package de.lardtom.propwash.content;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.server.ServerRulesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class BatteryChargerBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOTS = 4;
    public static final int DATA_REDSTONE = SLOTS;
    public static final int DATA_COUNT = SLOTS + 1;
    public static final int NO_TIME = -1;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3};
    private static final Component DEFAULT_NAME = Component.translatable("container.propwash.battery_charger");

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final float[] pending = new float[SLOTS];
    private final int[] remaining = {NO_TIME, NO_TIME, NO_TIME, NO_TIME};
    private boolean powered;
    private int timer;
    private int sentPacks = -1;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int dataId) {
            if (dataId >= 0 && dataId < SLOTS) {
                return BatteryChargerBlockEntity.this.remaining[dataId];
            }
            return dataId == DATA_REDSTONE && BatteryChargerBlockEntity.this.powered ? 1 : 0;
        }

        @Override
        public void set(int dataId, int value) {
            if (dataId >= 0 && dataId < SLOTS) {
                BatteryChargerBlockEntity.this.remaining[dataId] = value;
            } else if (dataId == DATA_REDSTONE) {
                BatteryChargerBlockEntity.this.powered = value != 0;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public BatteryChargerBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(ContentBlocks.BATTERY_CHARGER_BE.get(), worldPosition, blockState);
    }

    public static ChargeMath.Cfg config() {
        ServerRulesConfig config = ServerRulesConfig.get();
        return new ChargeMath.Cfg(config.batteryChargeSeconds, config.batteryChargeCapacityExponent, config.batteryChargeMinSeconds,
                config.redstoneChargeMultiplier);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, BatteryChargerBlockEntity charger) {
        if (++charger.timer < ChargeMath.INTERVAL) {
            return;
        }
        charger.timer = 0;
        charger.powered = level.hasNeighborSignal(pos);
        ChargeMath.Cfg cfg = config();
        boolean changed = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = charger.items.get(slot);
            Optional<BatteryType> type = Batteries.typeOf(stack);
            if (type.isEmpty()) {
                charger.pending[slot] = 0.0F;
                charger.remaining[slot] = NO_TIME;
                continue;
            }
            float charge = Batteries.charge(stack);
            if (Batteries.isFull(charge)) {
                charger.pending[slot] = 0.0F;
                charger.remaining[slot] = NO_TIME;
                if (charge < Batteries.FULL) {
                    Batteries.storeCharge(stack, Batteries.FULL);
                    changed = true;
                }
                continue;
            }
            float exact = Batteries.clamp(charge + charger.pending[slot]);
            float next = ChargeMath.advance(exact, type.get(), ChargeMath.INTERVAL, charger.powered, cfg);
            float stored = Batteries.quantize(next);
            charger.pending[slot] = Math.max(0.0F, next - stored);
            if (stored != charge) {
                Batteries.storeCharge(stack, stored);
                changed = true;
            }
            charger.remaining[slot] = Batteries.isFull(stored) ? NO_TIME : ChargeMath.remainingSeconds(next, type.get(), charger.powered, cfg);
        }
        charger.refreshBlockState();
        if (changed) {
            setChanged(level, pos, level.getBlockState(pos));
        }
    }

    public BlockState displayState(BlockState state) {
        BlockState target = state.setValue(BatteryChargerBlock.STATE, this.chargerState());
        for (int slot = 0; slot < SLOTS; slot++) {
            target = target.setValue(BatteryChargerBlock.bay(slot), BayState.of(this.items.get(slot)));
        }
        return target;
    }

    private void refreshBlockState() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.isRemoved()) {
            return;
        }
        BlockState current = serverLevel.getBlockState(this.worldPosition);
        if (!current.is(this.getBlockState().getBlock())) {
            return;
        }
        BlockState target = this.displayState(current);
        if (target != current) {
            serverLevel.setBlock(this.worldPosition, target, Block.UPDATE_ALL);
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        this.refreshBlockState();
        this.syncPacks();
    }

    public int packSignature() {
        int signature = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            signature = signature * 31 + Batteries.typeOf(this.items.get(slot)).map(type -> type.id().hashCode() + 1).orElse(0);
        }
        return signature;
    }

    private void syncPacks() {
        int signature = this.packSignature();
        if (signature == this.sentPacks || !(this.level instanceof ServerLevel serverLevel) || this.isRemoved()) {
            return;
        }
        this.sentPacks = signature;
        BlockState state = this.getBlockState();
        serverLevel.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public ChargerState chargerState() {
        int batteries = 0;
        int full = 0;
        for (ItemStack stack : this.items) {
            if (Batteries.isBattery(stack)) {
                batteries++;
                if (Batteries.isFull(Batteries.charge(stack))) {
                    full++;
                }
            }
        }
        return ChargerState.of(batteries, full);
    }

    public int fullCount() {
        int full = 0;
        for (ItemStack stack : this.items) {
            if (Batteries.isBattery(stack) && Batteries.isFull(Batteries.charge(stack))) {
                full++;
            }
        }
        return full;
    }

    public int firstFreeSlot() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.items.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    public ContainerData data() {
        return this.data;
    }

    public boolean powered() {
        return this.powered;
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        super.setItem(slot, itemStack);
        if (slot >= 0 && slot < SLOTS) {
            this.pending[slot] = 0.0F;
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack itemStack) {
        return Batteries.isBattery(itemStack) && !PartCondition.broken(itemStack);
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack itemStack, @Nullable Direction direction) {
        return direction != Direction.DOWN && this.canPlaceItem(slot, itemStack) && this.items.get(slot).isEmpty();
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
        return direction == Direction.DOWN && Batteries.isBattery(itemStack) && Batteries.isFull(Batteries.charge(itemStack));
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new BatteryChargerMenu(containerId, inventory, this, this.data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        int before = this.packSignature();
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        if (this.level != null && this.level.isClientSide() && before != this.packSignature()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }
}
