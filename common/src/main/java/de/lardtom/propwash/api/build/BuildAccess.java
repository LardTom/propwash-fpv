package de.lardtom.propwash.api.build;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.Footprints;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public interface BuildAccess {
    boolean isDrone(ItemStack stack);

    Optional<DroneData> read(ItemStack stack);

    ItemStack createDrone(DroneData data);

    ItemStack withTune(ItemStack drone, TuneSpec tune);

    ItemStack withPaint(ItemStack drone, PaintSpec paint);

    BuildCheck check(BuildSpec spec);

    default boolean partCompatibility() {
        return true;
    }

    Optional<BuildStats> analyze(BuildSpec spec);

    default Optional<CameraView> cameraView(BuildSpec spec) {
        return Optional.empty();
    }

    default double footprintMm(BuildSpec spec) {
        return Footprints.fromRegistry(spec);
    }

    default Optional<CameraView> cameraView(BuildSpec spec, double uptiltDeg) {
        return Optional.empty();
    }

    default Optional<CameraView.UptiltRange> uptiltRange(Identifier frame) {
        return Optional.empty();
    }

    TuneSpec defaultTune(BuildSpec spec);

    List<DronePreset> presets();

    DronePreset registerPreset(DronePreset preset);

    ImportResult applyImport(ServerPlayer player, ImportRequest request);

    default OptionalDouble partCondition(ItemStack drone, PartKind kind) {
        return OptionalDouble.empty();
    }

    default Map<String, Integer> paintCost(ItemStack drone, PaintSpec target) {
        return Map.of();
    }
}
