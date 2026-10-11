package de.lardtom.propwash.content.client;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.client.ClientApiImpl;
import de.lardtom.propwash.apiimpl.client.WorkbenchPlanner;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneDataReader;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.core.PartCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class WorkbenchHost implements WorkbenchContext {
    public static final int MAX_HEADER_BUTTONS = 2;

    private final DroneWorkbenchScreen screen;
    private final DroneWorkbenchMenu menu;
    private final int headerSpace;
    private final List<Entry> buttons = new ArrayList<>();

    WorkbenchHost(DroneWorkbenchScreen screen, DroneWorkbenchMenu menu, int headerSpace) {
        this.screen = screen;
        this.menu = menu;
        this.headerSpace = Math.max(0, headerSpace);
    }

    public List<Entry> buttons() {
        return List.copyOf(this.buttons);
    }

    @Override
    public Screen screen() {
        return this.screen;
    }

    @Override
    public int containerId() {
        return this.menu.containerId;
    }

    @Override
    public boolean creative() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.hasInfiniteMaterials();
    }

    @Override
    public Optional<Identifier> slot(PartKind kind, int position) {
        PartCategory category = PartCategory.of(kind);
        if (position < 0 || position >= category.slotCount()) {
            return Optional.empty();
        }
        ItemStack stack = this.menu.getSlot(BuildSlots.firstSlot(category) + position).getItem();
        return DroneAssembly.part(stack).map(part -> part.id());
    }

    @Override
    public Optional<DroneData> result() {
        ItemStack drone = this.menu.resultPreview();
        return drone.isEmpty() ? Optional.empty() : Optional.of(DroneDataReader.read(drone));
    }

    @Override
    public void addButton(Component label, Component tooltip, Runnable onPress) {
        this.buttons.add(new Entry(Objects.requireNonNull(label, "label"), null, null, Objects.requireNonNullElse(tooltip, Component.empty()),
                Objects.requireNonNull(onPress, "onPress")));
    }

    @Override
    public void addButton(Component label, Component shortLabel, Component tooltip, Runnable onPress) {
        this.buttons.add(new Entry(Objects.requireNonNull(label, "label"), Objects.requireNonNull(shortLabel, "shortLabel"), null,
                Objects.requireNonNullElse(tooltip, Component.empty()), Objects.requireNonNull(onPress, "onPress")));
    }

    @Override
    public void addIconButton(Identifier sprite, Component tooltip, Runnable onPress) {
        Component text = Objects.requireNonNull(tooltip, "tooltip");
        this.buttons.add(new Entry(text, null, Objects.requireNonNull(sprite, "sprite"), Component.empty(), Objects.requireNonNull(onPress, "onPress")));
    }

    @Override
    public int headerSpace() {
        return this.headerSpace;
    }

    @Override
    public List<Identifier> missingParts(DroneData data) {
        return WorkbenchPlanner.missingParts(this, data);
    }

    @Override
    public void openChild(Function<Screen, Screen> factory) {
        Screen child = factory.apply(this.screen);
        if (child == null) {
            return;
        }
        WorkbenchChildren.watch(child, this.screen, this.menu);
        Minecraft.getInstance().gui.setScreen(child);
    }

    @Override
    public CompletableFuture<ImportResult> importIntoWorkbench(DroneData data, Set<ImportLayer> layers, Set<TuneGroup> tuneGroups) {
        ImportRequest request = new ImportRequest(data, layers, tuneGroups, ImportTarget.WORKBENCH);
        return ClientApiImpl.sendWorkbenchImport(this.menu.containerId, request);
    }

    public record Entry(Component label, @Nullable Component shortLabel, @Nullable Identifier icon, Component tooltip, Runnable onPress) {
        public boolean iconOnly() {
            return this.icon != null;
        }
    }
}
