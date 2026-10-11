package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.PartDefinition;
import net.minecraft.resources.Identifier;

public sealed interface PartInfo permits FrameType, StackType, MotorType, PropType, BatteryType, VideoType, AccessoryType {
    Identifier id();

    String key();

    String name();

    double massGrams();

    PartDefinition definition();

    boolean missing();

    default boolean builtin() {
        return PartIndex.isBuiltin(this.id());
    }

    default String translationKey() {
        if (this.missing()) {
            return PartNames.MISSING_NAME;
        }
        return "part." + this.id().getNamespace() + "." + this.id().getPath();
    }
}
