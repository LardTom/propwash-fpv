package de.lardtom.propwash.share.client;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class ShareHarness {
    private ShareHarness() {
    }

    public static WorkbenchContext context(Optional<DroneData> result, boolean creative) {
        return new WorkbenchContext() {
            @Override
            public Screen screen() {
                return Minecraft.getInstance().gui.screen();
            }

            @Override
            public int containerId() {
                return -1;
            }

            @Override
            public boolean creative() {
                return creative;
            }

            @Override
            public Optional<Identifier> slot(PartKind kind, int position) {
                return Optional.empty();
            }

            @Override
            public Optional<DroneData> result() {
                return result;
            }

            @Override
            public void addButton(Component label, Component tooltip, Runnable onPress) {
            }

            @Override
            public void openChild(Function<Screen, Screen> factory) {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.gui.setScreen(factory.apply(minecraft.gui.screen()));
            }

            @Override
            public CompletableFuture<ImportResult> importIntoWorkbench(DroneData data, Set<ImportLayer> layers, Set<TuneGroup> tuneGroups) {
                return CompletableFuture.completedFuture(ImportResult.of(ImportStatus.NO_TARGET));
            }
        };
    }

    public static ShareScreen window(WorkbenchContext context, ShareScreen.Mode mode) {
        return new ShareScreen(null, context, mode);
    }
}
