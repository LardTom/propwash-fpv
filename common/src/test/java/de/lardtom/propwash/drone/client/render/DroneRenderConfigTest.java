package de.lardtom.propwash.drone.client.render;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneRenderConfigTest {
    @Test
    void newInstallStartsAtRealSize() {
        DroneRenderConfig config = DroneRenderConfig.defaults();
        DroneRenderConfig.validate(config);
        assertEquals(1.0F, config.visualScale);
        assertEquals(DroneRenderConfig.VERSION, config.version);
        assertFalse(config.sizeNotice);
    }

    @Test
    void oldDefaultIsSwitchedOnceWithNotice() {
        DroneRenderConfig config = new Gson().fromJson("{\"visualScale\":1.75,\"armLeds\":false}", DroneRenderConfig.class);
        DroneRenderConfig.validate(config);
        assertEquals(1.0F, config.visualScale);
        assertTrue(config.sizeNotice);
        assertFalse(config.armLeds);
        assertEquals(DroneRenderConfig.VERSION, config.version);
        config.sizeNotice = false;
        config.visualScale = 1.75F;
        DroneRenderConfig.validate(config);
        assertEquals(1.75F, config.visualScale);
        assertFalse(config.sizeNotice);
    }

    @Test
    void ownSizeFromOldVersionStays() {
        for (float own : new float[] {1.0F, 1.5F, 2.0F, 3.0F}) {
            DroneRenderConfig config = new Gson().fromJson("{\"visualScale\":" + own + "}", DroneRenderConfig.class);
            DroneRenderConfig.validate(config);
            assertEquals(own, config.visualScale);
            assertFalse(config.sizeNotice);
        }
    }

    @Test
    void copyKeepsVersionSoDraftsAreNotMigrated() {
        DroneRenderConfig live = DroneRenderConfig.defaults();
        DroneRenderConfig.validate(live);
        DroneRenderConfig draft = live.copy();
        draft.visualScale = 1.75F;
        DroneRenderConfig.validate(draft);
        assertEquals(1.75F, draft.visualScale);
        assertFalse(draft.sizeNotice);
    }
}
