package de.lardtom.propwash.api.build;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public record DronePreset(Identifier id, DroneData data, boolean creativeListed) {
    public DronePreset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(data, "data");
    }

    public static DronePreset of(Identifier id, DroneData data) {
        return new DronePreset(id, data, true);
    }

    public String translationKey() {
        if ("propwash".equals(this.id.getNamespace())) {
            return "propwash.content.preset." + this.id.getPath();
        }
        return this.id.getNamespace() + ".preset." + this.id.getPath();
    }
}
