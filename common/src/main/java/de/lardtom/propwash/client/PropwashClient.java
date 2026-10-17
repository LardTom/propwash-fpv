package de.lardtom.propwash.client;

import de.lardtom.propwash.PropwashCommon;
import de.lardtom.propwash.accessory.client.AccessoryClient;
import de.lardtom.propwash.apiimpl.client.ApiClient;
import de.lardtom.propwash.audio.client.AudioClient;
import de.lardtom.propwash.bando.client.BandoClient;
import de.lardtom.propwash.blackbox.client.BlackboxClient;
import de.lardtom.propwash.content.client.ContentClient;
import de.lardtom.propwash.core.DevExtensions;
import de.lardtom.propwash.core.client.CoreClient;
import de.lardtom.propwash.creative.client.CreativeClient;
import de.lardtom.propwash.drone.client.DroneClient;
import de.lardtom.propwash.guide.client.GuideClient;
import de.lardtom.propwash.input.client.InputClient;
import de.lardtom.propwash.interact.client.InteractClient;
import de.lardtom.propwash.join.client.JoinClient;
import de.lardtom.propwash.race.client.RaceClient;
import de.lardtom.propwash.relay.client.RelayClient;
import de.lardtom.propwash.rescue.client.RescueClient;
import de.lardtom.propwash.server.client.ServerClient;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.parts.client.PartsClient;
import de.lardtom.propwash.trick.client.TrickClient;
import de.lardtom.propwash.ui.client.UiClient;
import de.lardtom.propwash.video.client.VideoClient;
import de.lardtom.propwash.zone.client.ZoneClient;

public final class PropwashClient {
    private static boolean initialized;

    private PropwashClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashCommon.init();
        CoreClient.init();
        PartsClient.init();
        ContentClient.init();
        DroneClient.init();
        RaceClient.init();
        BandoClient.init();
        ServerClient.init();
        ZoneClient.init();
        RescueClient.init();
        InputClient.init();
        VideoClient.init();
        RelayClient.init();
        AudioClient.init();
        UiClient.init();
        TrickClient.init();
        BlackboxClient.init();
        InteractClient.init();
        AccessoryClient.init();
        CreativeClient.init();
        OsdClient.init();
        GuideClient.init();
        ApiClient.init();
        JoinClient.init();
        DevExtensions.client();
    }
}
