package de.lardtom.propwash.video.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.freecam.client.FreecamSession;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.osd.client.LiveOsdSource;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.osd.client.SpectatorCountClient;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.client.ClientRaceTracker;
import de.lardtom.propwash.rescue.client.RescueState;
import de.lardtom.propwash.ui.client.UiConfig;
import de.lardtom.propwash.video.PilotFeed;
import de.lardtom.propwash.video.PilotLook;
import de.lardtom.propwash.video.net.PilotFeedC2S;
import de.lardtom.propwash.video.net.PilotFeedS2C;
import de.lardtom.propwash.video.net.PilotLookC2S;
import de.lardtom.propwash.video.net.PilotLookS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class PilotFeedClient {
    public static final int LOOK_CHECK_TICKS = 10;
    private static final OsdSampler SAMPLER = new OsdSampler(new FeedSource());
    private static boolean initialized;
    private static boolean watched;
    private static int lookTimer;
    private static @Nullable PilotLook sentLook;
    private static int sentLookEntity = -1;
    private static int sent;
    private static int looksSent;

    private PilotFeedClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientNet.handle(PilotFeedS2C.TYPE, (payload, context) -> WatchedPilot.onFeed(payload));
        ClientNet.handle(PilotLookS2C.TYPE, (payload, context) -> WatchedPilot.onLook(payload));
        ClientBus.TICK_END.register(PilotFeedClient::onClientTick);
        ClientBus.TICK_END.register(WatchedPilot::onClientTick);
        ClientBus.DISCONNECT.register(minecraft -> {
            reset();
            SAMPLER.reset();
            WatchedPilot.clear();
        });
    }

    public static boolean watched() {
        return watched;
    }

    public static int sent() {
        return sent;
    }

    public static int looksSent() {
        return looksSent;
    }

    static void onClientTick(Minecraft minecraft) {
        long now = Util.getNanos();
        if (minecraft.isPaused()) {
            SAMPLER.hold(now);
            return;
        }
        OsdData data = SAMPLER.sample(now);
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        if (drone == null || !data.active || SpectatorCountClient.count(drone) <= 0) {
            reset();
            return;
        }
        if (!ClientNet.canSend(PilotFeedC2S.TYPE)) {
            return;
        }
        if (ClientNet.sendToServer(new PilotFeedC2S(drone.getId(), capture(data)))) {
            sent++;
        }
        if (!watched || sentLookEntity != drone.getId() || --lookTimer <= 0) {
            lookTimer = LOOK_CHECK_TICKS;
            PilotLook look = PilotLook.of(OsdClient.CONFIG.get(), UiConfig.speedUnit(), InputConfig.get().stickMode());
            if ((!look.equals(sentLook) || sentLookEntity != drone.getId()) && ClientNet.sendToServer(new PilotLookC2S(drone.getId(), look))) {
                sentLook = look;
                sentLookEntity = drone.getId();
                looksSent++;
            }
        }
        watched = true;
    }

    public static PilotFeed capture(OsdData data) {
        ClientRaceTracker race = ClientRaceTracker.INSTANCE;
        LapTracker tracker = race.tracker();
        long now = race.now();
        RescueState rescue = RescueState.INSTANCE;
        return PilotFeed.capture(data)
                .withRace(tracker.courseId(), tracker.phase(), tracker.lapTarget(), race.nextGateIndex(), race.startGateIndex(), tracker.falseStarted(),
                        tracker.countdownRemainingNanos(now), race.countdownTotalNanos(), race.sinceGoNanos())
                .withRescuePhase(rescue.active() ? rescue.phase() : null);
    }

    private static void reset() {
        watched = false;
        sentLook = null;
        sentLookEntity = -1;
        lookTimer = 0;
    }

    private static final class FeedSource implements OsdSampler.Source {
        private final LiveOsdSource live = new LiveOsdSource();

        @Override
        public boolean active() {
            PilotSession session = PilotSession.INSTANCE;
            return session.isActive() && session.drone() != null && !FreecamSession.active();
        }

        @Override
        public void fill(OsdData data) {
            this.live.fill(data);
        }
    }
}
