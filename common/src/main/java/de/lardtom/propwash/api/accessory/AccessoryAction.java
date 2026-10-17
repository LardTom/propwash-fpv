package de.lardtom.propwash.api.accessory;

import net.minecraft.server.level.ServerPlayer;

public interface AccessoryAction extends AccessoryContext {
    int number();

    boolean pressed();

    ServerPlayer player();
}
