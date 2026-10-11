package de.lardtom.propwash.api.client;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.client.WorkbenchPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface WorkbenchContext {
    int BUTTON_PADDING = 14;
    int BUTTON_GAP = 3;
    int MAX_BUTTON_WIDTH = 80;
    int ICON_BUTTON_SIZE = 13;

    Screen screen();

    int containerId();

    boolean creative();

    Optional<Identifier> slot(PartKind kind, int position);

    Optional<DroneData> result();

    void addButton(Component label, Component tooltip, Runnable onPress);

    default void addButton(Component label, Component shortLabel, Component tooltip, Runnable onPress) {
        this.addButton(label, tooltip, onPress);
    }

    default void addIconButton(Identifier sprite, Component tooltip, Runnable onPress) {
        this.addButton(tooltip, tooltip, onPress);
    }

    default int headerSpace() {
        return 0;
    }

    default int buttonWidth(Component label) {
        return Math.min(Minecraft.getInstance().font.width(label) + BUTTON_PADDING, MAX_BUTTON_WIDTH);
    }

    default List<Identifier> missingParts(DroneData data) {
        return WorkbenchPlanner.missingParts(this, data);
    }

    void openChild(Function<Screen, Screen> factory);

    CompletableFuture<ImportResult> importIntoWorkbench(DroneData data, Set<ImportLayer> layers, Set<TuneGroup> tuneGroups);
}
