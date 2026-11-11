package de.lardtom.propwash.api.input;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public interface SwitchRegistry {
    int MAX_SWITCHES = 64;
    int MAX_AXES = 16;

    AddonSwitch register(SwitchSpec spec);

    AddonAxis registerAxis(AxisSpec spec);

    List<AddonSwitch> switches();

    List<AddonAxis> axes();

    Optional<AddonSwitch> find(Identifier id);

    Optional<AddonAxis> findAxis(Identifier id);
}
