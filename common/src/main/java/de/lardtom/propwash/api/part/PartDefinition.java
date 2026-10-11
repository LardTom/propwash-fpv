package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

public sealed interface PartDefinition permits FrameDefinition, StackDefinition, MotorDefinition, PropDefinition, BatteryDefinition,
        VideoDefinition, AccessoryDefinition {
    Identifier id();

    PartKind kind();

    PartBasics basics();

    default double massGrams() {
        return this.basics().massGrams();
    }
}
