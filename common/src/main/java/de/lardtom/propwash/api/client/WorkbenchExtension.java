package de.lardtom.propwash.api.client;

import net.minecraft.resources.Identifier;

public interface WorkbenchExtension {
    Identifier id();

    default int order() {
        return 1000;
    }

    void init(WorkbenchContext context);
}
