package de.lardtom.propwash;

import de.lardtom.propwash.accessory.AccessoryModule;
import de.lardtom.propwash.apiimpl.ApiModule;
import de.lardtom.propwash.audio.AudioModule;
import de.lardtom.propwash.bando.BandoModule;
import de.lardtom.propwash.blackbox.BlackboxModule;
import de.lardtom.propwash.content.ContentModule;
import de.lardtom.propwash.core.CoreModule;
import de.lardtom.propwash.creative.CreativeModule;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.guide.GuideModule;
import de.lardtom.propwash.input.InputModule;
import de.lardtom.propwash.interact.InteractModule;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.relay.RelayModule;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.rescue.RescueModule;
import de.lardtom.propwash.server.ServerModule;
import de.lardtom.propwash.osd.OsdModule;
import de.lardtom.propwash.parts.PartsModule;
import de.lardtom.propwash.trick.TrickModule;
import de.lardtom.propwash.ui.UiModule;
import de.lardtom.propwash.video.VideoModule;
import de.lardtom.propwash.zone.ZoneModule;

public final class PropwashCommon {
    private static boolean initialized;

    private PropwashCommon() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CoreModule.init();
        PartsModule.init();
        ContentModule.init();
        DroneModule.init();
        RaceModule.init();
        BandoModule.init();
        ServerModule.init();
        ZoneModule.init();
        RescueModule.init();
        InputModule.init();
        VideoModule.init();
        RelayModule.init();
        AudioModule.init();
        UiModule.init();
        TrickModule.init();
        BlackboxModule.init();
        InteractModule.init();
        AccessoryModule.init();
        CreativeModule.init();
        OsdModule.init();
        GuideModule.init();
        ApiModule.init();
        PropwashRegistries.closeAfterCommonInit();
        PropwashNet.closeAfterCommonInit();
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
