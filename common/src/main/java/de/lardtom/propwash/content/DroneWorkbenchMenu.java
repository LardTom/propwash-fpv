package de.lardtom.propwash.content;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintCost;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.content.paint.PaintService;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.server.WorldRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DroneWorkbenchMenu extends AbstractContainerMenu {
    public static final int PART_SLOTS = BuildSlots.COUNT;
    public static final int RESULT_SLOT = PART_SLOTS;
    public static final int SALVAGE_SLOT = RESULT_SLOT + 1;
    public static final int INV_START = SALVAGE_SLOT + 1;
    public static final int HOTBAR_START = INV_START + 27;
    public static final int INV_END = HOTBAR_START + 9;

    private final ContainerLevelAccess access;
    private final Player player;
    private final SimpleContainer parts = new SimpleContainer(PART_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            DroneWorkbenchMenu.this.slotsChanged(this);
        }
    };
    private final SimpleContainer salvage = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            DroneWorkbenchMenu.this.slotsChanged(this);
        }
    };
    private final ResultContainer result = new ResultContainer();
    private final Slot salvageSlot;
    private boolean updating;
    private @Nullable PartCompatibility appliedRule;
    private @Nullable PendingTune pendingTune;

    public DroneWorkbenchMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public DroneWorkbenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ContentBlocks.DRONE_WORKBENCH_MENU.get(), containerId);
        this.access = access;
        this.player = inventory.player;
        for (int slot = 0; slot < PART_SLOTS; slot++) {
            this.addSlot(new PartSlot(slot, WorkbenchLayout.partX(slot), WorkbenchLayout.partY(slot)));
        }
        this.addSlot(new ResultSlot(WorkbenchLayout.RESULT_X, WorkbenchLayout.RESULT_Y));
        this.salvageSlot = this.addSlot(new SalvageSlot(WorkbenchLayout.SALVAGE_X, WorkbenchLayout.SALVAGE_Y));
        this.addStandardInventorySlots(inventory, WorkbenchLayout.INVENTORY_X, WorkbenchLayout.INVENTORY_Y);
    }

    public PartCompatibility partCompatibility() {
        return WorldRules.partCompatibility(this.player.level());
    }

    public BuildSlots.Check check() {
        return DroneAssembly.check(this.parts);
    }

    public float[] conditions() {
        return DroneAssembly.conditions(this.parts);
    }

    public boolean partsEmpty() {
        for (int slot = 0; slot < PART_SLOTS; slot++) {
            if (!this.parts.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public ItemStack resultPreview() {
        return this.result.getItem(0);
    }

    public ItemStack frame() {
        return this.parts.getItem(BuildSlots.FRAME);
    }

    public DronePaint paint() {
        return PaintItems.paint(this.frame());
    }

    public PaintService.Result applyPaint(Player player, DronePaint paint) {
        DronePaint target = paint == null ? DronePaint.DEFAULT : paint;
        ItemStack frame = this.frame();
        PaintService.Result result = PaintService.check(player, frame, target);
        if (result != PaintService.Result.APPLIED) {
            return result;
        }
        if (!PaintService.pay(player, PaintCost.of(this.paint(), target))) {
            return PaintService.Result.MISSING_DYES;
        }
        ItemStack painted = frame.copy();
        PaintItems.store(painted, target);
        this.parts.setItem(BuildSlots.FRAME, painted);
        this.broadcastChanges();
        this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F));
        return PaintService.Result.APPLIED;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (this.updating) {
            return;
        }
        if (container == this.salvage) {
            this.disassembleSalvage();
        } else if (container == this.parts) {
            this.refreshResult();
        }
    }

    @Override
    public void broadcastChanges() {
        PartCompatibility applied = this.appliedRule;
        if (applied != null && applied != this.partCompatibility()) {
            this.refreshResult();
            return;
        }
        super.broadcastChanges();
    }

    void refreshResult() {
        PartCompatibility rule = this.partCompatibility();
        this.appliedRule = rule;
        ItemStack drone = DroneAssembly.assemble(this.parts, rule);
        PendingTune pending = this.pendingTune;
        if (pending != null && !drone.isEmpty()) {
            pending.applyTo(drone);
        }
        this.result.setItem(0, drone);
        this.broadcastChanges();
    }

    SimpleContainer partsContainer() {
        return this.parts;
    }

    public Optional<PendingTune> pendingTune() {
        return Optional.ofNullable(this.pendingTune);
    }

    public void setPendingTune(@Nullable PendingTune tune) {
        this.pendingTune = tune;
        this.refreshResult();
    }

    public void replaceParts(List<ItemStack> stacks) {
        if (stacks.size() != PART_SLOTS) {
            throw new IllegalArgumentException("Erwartet " + PART_SLOTS + " Teile");
        }
        this.updating = true;
        try {
            for (int slot = 0; slot < PART_SLOTS; slot++) {
                this.parts.setItem(slot, stacks.get(slot));
            }
        } finally {
            this.updating = false;
        }
        this.refreshResult();
    }

    private void disassembleSalvage() {
        ItemStack drone = this.salvage.getItem(0);
        if (Recycling.accepts(drone)) {
            this.recycleSalvage(drone);
            return;
        }
        if (drone.isEmpty() || !DroneAssembly.isDrone(drone) || !this.partsEmpty()) {
            return;
        }
        List<ItemStack> stacks = DroneAssembly.disassemble(drone);
        if (this.player instanceof ServerPlayer serverPlayer && PropwashEvents.DRONE_DISASSEMBLED.hasListeners()) {
            DroneData data = DroneDataReader.read(drone);
            fireSafely(() -> PropwashEvents.DRONE_DISASSEMBLED.fire(listener -> listener.onDroneDisassembled(serverPlayer, data)));
        }
        this.updating = true;
        try {
            this.salvage.setItem(0, ItemStack.EMPTY);
            for (int slot = 0; slot < PART_SLOTS; slot++) {
                this.parts.setItem(slot, stacks.get(slot));
            }
        } finally {
            this.updating = false;
        }
        this.refreshResult();
        this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6F, 1.3F));
    }

    private void recycleSalvage(ItemStack broken) {
        List<ItemStack> yields = Recycling.recycle(broken);
        this.updating = true;
        try {
            this.salvage.setItem(0, ItemStack.EMPTY);
        } finally {
            this.updating = false;
        }
        this.access.execute((level, pos) -> {
            for (ItemStack yield : yields) {
                this.player.getInventory().placeItemBackInInventory(yield.copy(), Prediction.SERVER_ONLY);
            }
            level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6F, 0.8F);
        });
        this.broadcastChanges();
    }

    private void consumeParts() {
        this.updating = true;
        try {
            for (int slot = 0; slot < PART_SLOTS; slot++) {
                this.parts.removeItem(slot, 1);
            }
        } finally {
            this.updating = false;
        }
        this.refreshResult();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex == RESULT_SLOT) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (slotIndex < INV_START) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (isBenchItem(stack)) {
            if (!this.moveIntoBench(stack)) {
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

    private static boolean isBenchItem(ItemStack stack) {
        return DroneAssembly.isDrone(stack) || DroneAssembly.category(stack).isPresent();
    }

    private boolean moveIntoBench(ItemStack stack) {
        if (DroneAssembly.isDrone(stack) || Recycling.accepts(stack)) {
            return this.salvageSlot.mayPlace(stack) && this.moveItemStackTo(stack, SALVAGE_SLOT, SALVAGE_SLOT + 1, false);
        }
        Optional<PartInfo> part = DroneAssembly.part(stack);
        if (part.isEmpty()) {
            return false;
        }
        boolean moved = false;
        for (int slot : BuildSlots.slotsFor(part.get())) {
            if (stack.isEmpty()) {
                break;
            }
            moved |= this.moveItemStackTo(stack, slot, slot + 1, false);
        }
        return moved;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != this.result && target.container != this.salvage && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.container != this.result && super.canDragTo(slot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> {
            this.clearContainer(player, this.salvage);
            this.clearContainer(player, this.parts);
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ContentBlocks.DRONE_WORKBENCH.block().get());
    }

    private static void fireSafely(Runnable event) {
        try {
            event.run();
        } catch (RuntimeException e) {
            Constants.LOG.error("Ein Listener eines Werkbank-Events ist fehlgeschlagen", e);
        }
    }

    public record PendingTune(TuneSpec tune, Set<TuneGroup> groups) {
        public PendingTune {
            tune = tune == null ? TuneSpec.EMPTY : tune;
            groups = Set.copyOf(groups);
        }

        void applyTo(ItemStack drone) {
            FlightTune stored = drone.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
            TuneSpec combined = new TuneSpec(stored.values()).overlay(this.tune, this.groups);
            FlightTune sanitized = DronePiloting.sanitize(stored, new FlightTune(combined.values()), DroneAssembly.buildOf(drone));
            if (sanitized.equals(FlightTune.EMPTY)) {
                drone.remove(CoreComponents.FLIGHT_TUNE.get());
            } else {
                drone.set(CoreComponents.FLIGHT_TUNE.get(), sanitized);
            }
        }
    }

    private final class PartSlot extends Slot {
        private final PartCategory category;
        private final int benchSlot;

        PartSlot(int slot, int x, int y) {
            super(DroneWorkbenchMenu.this.parts, slot, x, y);
            this.category = BuildSlots.categoryOf(slot);
            this.benchSlot = slot;
        }

        public PartCategory category() {
            return this.category;
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return DroneAssembly.fits(itemStack, this.category)
                    && BuildSlots.accepts(this.benchSlot, DroneAssembly.part(itemStack).orElse(null));
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private final class ResultSlot extends Slot {
        ResultSlot(int x, int y) {
            super(DroneWorkbenchMenu.this.result, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            DroneWorkbenchMenu.this.pendingTune = null;
            if (player instanceof ServerPlayer serverPlayer && PropwashEvents.DRONE_ASSEMBLED.hasListeners()) {
                DroneData data = DroneDataReader.read(carried);
                fireSafely(() -> PropwashEvents.DRONE_ASSEMBLED.fire(listener -> listener.onDroneAssembled(serverPlayer, carried, data)));
            }
            DroneWorkbenchMenu.this.consumeParts();
            DroneWorkbenchMenu.this.access.execute((level, pos) ->
                    level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.1F));
            super.onTake(player, carried);
        }
    }

    private final class SalvageSlot extends Slot {
        SalvageSlot(int x, int y) {
            super(DroneWorkbenchMenu.this.salvage, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return DroneAssembly.isDrone(itemStack) && DroneWorkbenchMenu.this.partsEmpty() || Recycling.accepts(itemStack);
        }

        @Override
        public int getMaxStackSize(ItemStack itemStack) {
            return DroneAssembly.isDrone(itemStack) ? 1 : itemStack.getMaxStackSize();
        }
    }
}
