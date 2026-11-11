package de.lardtom.propwash.content;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryComponents;
import de.lardtom.propwash.accessory.AccessoryContents;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
    public static final int RECYCLE_INPUT = INV_END;
    public static final int RECYCLE_OUTPUT = RECYCLE_INPUT + 1;
    public static final int RECYCLE_OUTPUT_SLOTS = 9;
    public static final int RECYCLE_PREVIEW = RECYCLE_OUTPUT + RECYCLE_OUTPUT_SLOTS;
    public static final int RECYCLE_PREVIEW_SLOTS = 9;
    public static final int SLOT_COUNT = RECYCLE_PREVIEW + RECYCLE_PREVIEW_SLOTS;
    public static final int BUTTON_BUILD = 0;
    public static final int BUTTON_RECYCLE_TAB = 1;
    public static final int BUTTON_RECYCLE = 2;

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
    private final SimpleContainer recycleInput = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            DroneWorkbenchMenu.this.slotsChanged(this);
        }
    };
    private final SimpleContainer recycleOutput = new SimpleContainer(RECYCLE_OUTPUT_SLOTS);
    private final SimpleContainer recyclePreview = new SimpleContainer(RECYCLE_PREVIEW_SLOTS) {
        @Override
        public int getMaxStackSize() {
            return Item.ABSOLUTE_MAX_STACK_SIZE;
        }

        @Override
        public int getMaxStackSize(ItemStack itemStack) {
            return Item.ABSOLUTE_MAX_STACK_SIZE;
        }
    };
    private final ResultContainer result = new ResultContainer();
    private final Slot salvageSlot;
    private final DataSlot tab = DataSlot.standalone();
    private final DataSlot recycleStatus = DataSlot.standalone();
    private final DataSlot recyclePerCraft = DataSlot.standalone();
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
        this.addSlot(new RecycleInputSlot(WorkbenchLayout.RECYCLE_INPUT_X, WorkbenchLayout.RECYCLE_INPUT_Y));
        for (int slot = 0; slot < RECYCLE_OUTPUT_SLOTS; slot++) {
            this.addSlot(new RecycleOutputSlot(slot, WorkbenchLayout.recycleOutputX(slot), WorkbenchLayout.recycleOutputY(slot)));
        }
        for (int slot = 0; slot < RECYCLE_PREVIEW_SLOTS; slot++) {
            this.addSlot(new PreviewSlot(slot));
        }
        this.addDataSlot(this.tab);
        this.addDataSlot(this.recycleStatus);
        this.addDataSlot(this.recyclePerCraft);
    }

    public WorkbenchTab tab() {
        return WorkbenchTab.of(this.tab.get());
    }

    public static int tabButton(WorkbenchTab tab) {
        return tab == WorkbenchTab.RECYCLE ? BUTTON_RECYCLE_TAB : BUTTON_BUILD;
    }

    public void selectTab(WorkbenchTab tab) {
        this.tab.set(tab.ordinal());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        switch (buttonId) {
            case BUTTON_BUILD -> {
                this.selectTab(WorkbenchTab.BUILD);
                return true;
            }
            case BUTTON_RECYCLE_TAB -> {
                this.selectTab(WorkbenchTab.RECYCLE);
                return true;
            }
            case BUTTON_RECYCLE -> {
                return this.tab() == WorkbenchTab.RECYCLE && player.level() instanceof ServerLevel level && this.recycle(level.getRandom());
            }
            default -> {
                return false;
            }
        }
    }

    public ItemStack recycleInput() {
        return this.recycleInput.getItem(0);
    }

    public List<ItemStack> recycleOutput() {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < RECYCLE_OUTPUT_SLOTS; slot++) {
            out.add(this.recycleOutput.getItem(slot));
        }
        return out;
    }

    public Recycling.Status recycleStatus() {
        Recycling.Status[] values = Recycling.Status.values();
        int index = this.recycleStatus.get();
        return index >= 0 && index < values.length ? values[index] : Recycling.Status.EMPTY;
    }

    public Optional<Recycling.Materials> recycleMaterials() {
        if (this.recycleStatus() != Recycling.Status.READY || this.recycleInput().isEmpty()) {
            return Optional.empty();
        }
        List<Recycling.Material> entries = new ArrayList<>();
        for (int slot = 0; slot < RECYCLE_PREVIEW_SLOTS; slot++) {
            ItemStack stack = this.recyclePreview.getItem(slot);
            if (!stack.isEmpty()) {
                entries.add(new Recycling.Material(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount()));
            }
        }
        int perCraft = this.recyclePerCraft.get();
        return entries.isEmpty() || perCraft < 1 ? Optional.empty() : Optional.of(new Recycling.Materials(entries, perCraft));
    }

    public int recycleTier() {
        return Recycling.tier(this.recycleInput());
    }

    public int recyclePieces() {
        ItemStack input = this.recycleInput();
        return this.recycleMaterials().map(materials -> Recycling.piecesThatFit(materials, input.getCount(), Recycling.tier(input), this.recycleOutput))
                .orElse(0);
    }

    public boolean recycle(RandomSource random) {
        ItemStack input = this.recycleInput();
        Optional<Recycling.Materials> materials = this.recycleMaterials();
        if (input.isEmpty() || materials.isEmpty()) {
            return false;
        }
        int tier = Recycling.tier(input);
        int pieces = Recycling.piecesThatFit(materials.get(), input.getCount(), tier, this.recycleOutput);
        if (pieces < 1) {
            return false;
        }
        for (ItemStack yield : Recycling.roll(materials.get(), pieces, tier, random)) {
            ItemStack rest = Recycling.insert(this.recycleOutput, yield);
            if (!rest.isEmpty()) {
                Constants.LOG.warn("Recycling: {} passt nicht mehr in die Ausgabe", rest);
                this.player.getInventory().placeItemBackInInventory(rest, Prediction.SERVER_ONLY);
            }
        }
        this.recycleInput.setItem(0, input.copyWithCount(input.getCount() - pieces));
        this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6F, 0.8F));
        this.broadcastChanges();
        return true;
    }

    private void refreshRecyclePreview() {
        if (!(this.player.level() instanceof ServerLevel level)) {
            return;
        }
        Recycling.Plan plan = Recycling.plan(this.recycleInput(), item -> RecycleRecipes.find(level.recipeAccess(), item));
        List<Recycling.Material> entries = plan.materials().map(Recycling.Materials::entries).orElse(List.of());
        for (int slot = 0; slot < RECYCLE_PREVIEW_SLOTS; slot++) {
            ItemStack shown = ItemStack.EMPTY;
            if (slot < entries.size()) {
                Recycling.Material entry = entries.get(slot);
                shown = BuiltInRegistries.ITEM.getOptional(entry.item()).map(item -> new ItemStack(item, entry.count())).orElse(ItemStack.EMPTY);
            }
            this.recyclePreview.setItem(slot, shown);
        }
        this.recyclePerCraft.set(plan.materials().map(Recycling.Materials::perCraft).orElse(0));
        this.recycleStatus.set(entries.size() > RECYCLE_PREVIEW_SLOTS ? Recycling.Status.NOT_RECYCLABLE.ordinal() : plan.status().ordinal());
    }

    public PartCompatibility partCompatibility() {
        return WorldRules.partCompatibility(this.player.level());
    }

    public boolean fitsFrame(@Nullable PartInfo part) {
        return BuildSlots.fitsFrame(part, DroneAssembly.part(this.parts.getItem(BuildSlots.FRAME)).orElse(null), this.partCompatibility());
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
        } else if (container == this.recycleInput) {
            this.refreshRecyclePreview();
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
        if (drone.isEmpty() || !DroneAssembly.isDrone(drone) || !this.partsEmpty()) {
            return;
        }
        List<ItemStack> stacks = DroneAssembly.disassemble(drone);
        List<ItemStack> loaded = drone.getOrDefault(AccessoryComponents.CONTENTS.get(), AccessoryContents.EMPTY).all();
        if (!loaded.isEmpty()) {
            this.access.execute((level, pos) -> {
                for (ItemStack stack : loaded) {
                    this.player.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY);
                }
            });
        }
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
        if (slotIndex == RESULT_SLOT) {
            return this.quickMoveResult(player, slot);
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (slotIndex < INV_START || slotIndex >= INV_END) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.keepsDrone(stack)) {
            return ItemStack.EMPTY;
        } else if (this.tab() == WorkbenchTab.RECYCLE && Recycling.isPart(stack)) {
            Slot input = this.slots.get(RECYCLE_INPUT);
            if (!input.mayPlace(stack) || !this.moveItemStackTo(stack, RECYCLE_INPUT, RECYCLE_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.tab() == WorkbenchTab.BUILD && isBenchItem(stack)) {
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
        slot.onTake(player, original.copyWithCount(original.getCount() - stack.getCount()));
        return original;
    }

    private ItemStack quickMoveResult(Player player, Slot result) {
        ItemStack drone = result.getItem();
        for (int index = INV_END - 1; index >= INV_START; index--) {
            Slot target = this.slots.get(index);
            if (target.hasItem() || !target.mayPlace(drone) || target.getMaxStackSize(drone) < drone.getCount()) {
                continue;
            }
            ItemStack original = drone.copy();
            Optional<ItemStack> taken = result.tryRemove(drone.getCount(), Integer.MAX_VALUE, player);
            if (taken.isEmpty()) {
                return ItemStack.EMPTY;
            }
            target.setByPlayer(taken.get());
            result.onTake(player, taken.get());
            return original;
        }
        return ItemStack.EMPTY;
    }

    public boolean keepsDrone(ItemStack stack) {
        return this.tab() == WorkbenchTab.RECYCLE && DroneAssembly.isDrone(stack);
    }

    private static boolean isBenchItem(ItemStack stack) {
        return DroneAssembly.isDrone(stack) || DroneAssembly.category(stack).isPresent();
    }

    private boolean moveIntoBench(ItemStack stack) {
        if (DroneAssembly.isDrone(stack)) {
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
        return target.isActive() && target.container != this.result && target.container != this.salvage && target.container != this.recycleInput
                && target.container != this.recyclePreview && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.isActive() && slot.container != this.result && slot.container != this.recycleOutput && slot.container != this.recyclePreview
                && super.canDragTo(slot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> {
            this.clearContainer(player, this.salvage);
            this.clearContainer(player, this.parts);
            this.clearContainer(player, this.recycleInput);
            this.clearContainer(player, this.recycleOutput);
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

    private abstract class TabSlot extends Slot {
        private final WorkbenchTab owner;

        TabSlot(Container container, int slot, int x, int y, WorkbenchTab owner) {
            super(container, slot, x, y);
            this.owner = owner;
        }

        @Override
        public boolean isActive() {
            return DroneWorkbenchMenu.this.tab() == this.owner;
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.isActive() && super.mayPickup(player);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return this.isActive() && this.accepts(itemStack);
        }

        abstract boolean accepts(ItemStack itemStack);
    }

    private final class PartSlot extends TabSlot {
        private final PartCategory category;
        private final int benchSlot;

        PartSlot(int slot, int x, int y) {
            super(DroneWorkbenchMenu.this.parts, slot, x, y, WorkbenchTab.BUILD);
            this.category = BuildSlots.categoryOf(slot);
            this.benchSlot = slot;
        }

        public PartCategory category() {
            return this.category;
        }

        @Override
        boolean accepts(ItemStack itemStack) {
            PartInfo part = DroneAssembly.part(itemStack).orElse(null);
            return DroneAssembly.fits(itemStack, this.category) && BuildSlots.accepts(this.benchSlot, part)
                    && DroneWorkbenchMenu.this.fitsFrame(part);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private final class ResultSlot extends TabSlot {
        ResultSlot(int x, int y) {
            super(DroneWorkbenchMenu.this.result, 0, x, y, WorkbenchTab.BUILD);
        }

        @Override
        boolean accepts(ItemStack itemStack) {
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

    private final class SalvageSlot extends TabSlot {
        SalvageSlot(int x, int y) {
            super(DroneWorkbenchMenu.this.salvage, 0, x, y, WorkbenchTab.BUILD);
        }

        @Override
        boolean accepts(ItemStack itemStack) {
            return DroneAssembly.isDrone(itemStack) && DroneWorkbenchMenu.this.partsEmpty();
        }

        @Override
        public int getMaxStackSize(ItemStack itemStack) {
            return 1;
        }
    }

    private final class RecycleInputSlot extends TabSlot {
        RecycleInputSlot(int x, int y) {
            super(DroneWorkbenchMenu.this.recycleInput, 0, x, y, WorkbenchTab.RECYCLE);
        }

        @Override
        boolean accepts(ItemStack itemStack) {
            return Recycling.isPart(itemStack);
        }
    }

    private final class RecycleOutputSlot extends TabSlot {
        RecycleOutputSlot(int slot, int x, int y) {
            super(DroneWorkbenchMenu.this.recycleOutput, slot, x, y, WorkbenchTab.RECYCLE);
        }

        @Override
        boolean accepts(ItemStack itemStack) {
            return false;
        }
    }

    private final class PreviewSlot extends Slot {
        PreviewSlot(int slot) {
            super(DroneWorkbenchMenu.this.recyclePreview, slot, -10000, -10000);
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return false;
        }
    }
}
