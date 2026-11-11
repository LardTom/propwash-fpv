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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToIntFunction;

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

    public void addTextOrIconButton(Component label, Component shortLabel, Component tooltip, Identifier sprite, Component iconTitle, Runnable onPress) {
        this.buttons.add(new Entry(Objects.requireNonNull(label, "label"), Objects.requireNonNull(shortLabel, "shortLabel"), null,
                Objects.requireNonNullElse(tooltip, Component.empty()), Objects.requireNonNull(onPress, "onPress"), Objects.requireNonNull(sprite, "sprite"),
                Objects.requireNonNull(iconTitle, "iconTitle")));
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

    public record Entry(Component label, @Nullable Component shortLabel, @Nullable Identifier icon, Component tooltip, Runnable onPress,
            @Nullable Identifier fallbackIcon, Component fallbackTitle) {
        public Entry(Component label, @Nullable Component shortLabel, @Nullable Identifier icon, Component tooltip, Runnable onPress) {
            this(label, shortLabel, icon, tooltip, onPress, null, Component.empty());
        }

        public boolean iconOnly() {
            return this.icon != null;
        }

        public int[] widths(ToIntFunction<Component> textWidth) {
            if (this.iconOnly()) {
                return new int[]{WorkbenchContext.ICON_BUTTON_SIZE};
            }
            int full = textWidth.applyAsInt(this.label);
            int[] widths = this.shortLabel == null ? new int[]{full} : new int[]{full, Math.min(full, textWidth.applyAsInt(this.shortLabel))};
            if (this.fallbackIcon == null) {
                return widths;
            }
            int[] withIcon = Arrays.copyOf(widths, widths.length + 1);
            withIcon[widths.length] = Math.min(widths[widths.length - 1], WorkbenchContext.ICON_BUTTON_SIZE);
            return withIcon;
        }

        public @Nullable Identifier iconAt(int variant) {
            if (this.iconOnly()) {
                return this.icon;
            }
            return this.fallbackIcon != null && variant == (this.shortLabel == null ? 1 : 2) ? this.fallbackIcon : null;
        }

        public Component textAt(int variant) {
            return variant == 0 || this.shortLabel == null ? this.label : this.shortLabel;
        }

        public Component titleAt(int variant) {
            return !this.iconOnly() && this.iconAt(variant) != null ? this.fallbackTitle : this.label;
        }
    }
}
