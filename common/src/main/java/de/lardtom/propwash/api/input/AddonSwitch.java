package de.lardtom.propwash.api.input;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public interface AddonSwitch {
    Identifier id();

    SwitchSpec spec();

    boolean active(Entity drone);
}
