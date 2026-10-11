package de.lardtom.propwash.content;

import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.CraftNames;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

public final class DronePresets {
    public static final String CUSTOM = "custom";

    private DronePresets() {
    }

    public static Optional<String> key(DroneBuild build) {
        return presetKey(build.withoutAccessories());
    }

    private static Optional<String> presetKey(DroneBuild build) {
        if (build.equals(DroneBuild.WHOOP)) {
            return Optional.of("whoop");
        }
        if (build.equals(DroneBuild.CINEWHOOP)) {
            return Optional.of("cinewhoop");
        }
        if (build.equals(DroneBuild.FREESTYLE)) {
            return Optional.of("freestyle");
        }
        if (build.equals(DroneBuild.RACER)) {
            return Optional.of("racer");
        }
        if (build.equals(DroneBuild.LONG_RANGE)) {
            return Optional.of("long_range");
        }
        return Optional.empty();
    }

    public static String translationKey(DroneBuild build) {
        return "propwash.content.preset." + key(build).orElse(CUSTOM);
    }

    public static Optional<Component> label(DroneBuild build) {
        return label(build, ApiExtensions.registeredPresets());
    }

    static Optional<Component> label(DroneBuild build, List<DronePreset> addonPresets) {
        if (key(build).isPresent()) {
            return Optional.of(Component.translatable(translationKey(build)));
        }
        DroneBuild bare = build.withoutAccessories();
        for (DronePreset preset : addonPresets) {
            Optional<DroneBuild> presetBuild = Conversions.build(preset.data().build());
            if (presetBuild.isPresent() && presetBuild.get().withoutAccessories().equals(bare)) {
                String name = preset.data().name().map(CraftNames::clean).orElse("");
                return Optional.of(name.isEmpty() ? Component.translatable(preset.translationKey()) : Component.literal(name));
            }
        }
        return Optional.empty();
    }
}
