package de.lardtom.propwash.api.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.Entity;

public interface HudContext {
    Entity drone();

    boolean fpv();

    boolean lineOfSight();

    boolean spectating();

    int imageX0();

    int imageY0();

    int imageX1();

    int imageY1();

    float partialTick();

    FlightTelemetry telemetry();

    Font font();

    default int imageWidth() {
        return this.imageX1() - this.imageX0();
    }

    default int imageHeight() {
        return this.imageY1() - this.imageY0();
    }
}
