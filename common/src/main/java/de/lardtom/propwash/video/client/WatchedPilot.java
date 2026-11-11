package de.lardtom.propwash.video.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.race.LapTracker;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.client.RaceHud;
import de.lardtom.propwash.ui.SpeedUnit;
import de.lardtom.propwash.video.PilotFeed;
import de.lardtom.propwash.video.PilotLook;
import de.lardtom.propwash.video.net.PilotFeedS2C;
import de.lardtom.propwash.video.net.PilotLookS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class WatchedPilot {
    public static final long FRESH_NANOS = 1_000_000_000L;
    private static final long LAP_MESSAGE_NANOS = 4_000_000_000L;
    private static int feedEntity = -1;
    private static @Nullable PilotFeed feed;
    private static long feedNanos;
    private static int lookEntity = -1;
    private static @Nullable PilotLook look;
    private static @Nullable OsdConfig lookConfig;
    private static @Nullable OsdConfig lookBase;
    private static int feeds;
    private static int looks;

    private WatchedPilot() {
    }

    static void onFeed(PilotFeedS2C payload) {
        PilotFeed next = payload.feed();
        PilotFeed previous = payload.entityId() == feedEntity ? feed : null;
        feed = next;
        feedEntity = payload.entityId();
        feedNanos = Util.getNanos();
        feeds++;
        QuadcopterEntity drone = watchedDrone();
        if (previous != null && drone != null && drone.getId() == payload.entityId()) {
            announce(previous, next);
        }
    }

    static void onLook(PilotLookS2C payload) {
        look = payload.look();
        lookEntity = payload.entityId();
        lookConfig = null;
        lookBase = null;
        looks++;
    }

    public static @Nullable PilotFeed feed() {
        PilotFeed current = feed;
        QuadcopterEntity drone = watchedDrone();
        if (current == null || drone == null || drone.getId() != feedEntity || Util.getNanos() - feedNanos > FRESH_NANOS) {
            return null;
        }
        return current;
    }

    public static long ageNanos() {
        return Math.max(0L, Util.getNanos() - feedNanos);
    }

    public static @Nullable OsdConfig osdConfig() {
        PilotLook current = currentLook();
        if (current == null) {
            return null;
        }
        OsdConfig own = OsdClient.CONFIG.get();
        if (lookConfig == null || lookBase != own) {
            try {
                lookConfig = current.config(own);
                lookBase = own;
            } catch (IllegalArgumentException e) {
                Constants.LOG.debug("OSD-Layout des Piloten von Drohne {} nicht lesbar: {}", lookEntity, e.getMessage());
                look = null;
                lookConfig = null;
                return null;
            }
        }
        return lookConfig;
    }

    public static @Nullable SpeedUnit speedUnit() {
        PilotLook current = currentLook();
        return current == null ? null : current.unit();
    }

    public static @Nullable StickMode stickMode() {
        PilotLook current = currentLook();
        return current == null ? null : current.stickMode();
    }

    public static int feeds() {
        return feeds;
    }

    public static int looks() {
        return looks;
    }

    private static @Nullable PilotLook currentLook() {
        PilotLook current = look;
        QuadcopterEntity drone = watchedDrone();
        return current == null || drone == null || drone.getId() != lookEntity ? null : current;
    }

    private static @Nullable QuadcopterEntity watchedDrone() {
        SpectateSession session = SpectateSession.INSTANCE;
        return FpvView.spectating() && !session.operator() ? session.drone() : null;
    }

    private static void announce(PilotFeed previous, PilotFeed next) {
        LapTracker.Phase before = previous.phase();
        LapTracker.Phase after = next.phase();
        if (before == LapTracker.Phase.COUNTDOWN && after == LapTracker.Phase.RACING) {
            RaceHud.big(Component.translatable("propwash.race.msg.go"), RaceHud.GREEN, 900_000_000L);
        } else if (before == LapTracker.Phase.RACING && after == LapTracker.Phase.FINISHED) {
            String key = next.pointToPoint() ? "propwash.race.p2p.msg.finish_big" : "propwash.race.msg.finish_big";
            RaceHud.big(Component.translatable(key), RaceHud.GOLD, 2_500_000_000L);
        }
        if (before == LapTracker.Phase.RACING && after == LapTracker.Phase.RACING && !next.pointToPoint() && next.lap() > previous.lap()
                && next.lastLapNanos() > 0L) {
            RaceHud.message(Component.translatable("propwash.race.msg.lap", previous.lap(), RaceFormat.time(next.lastLapNanos(), 2)), RaceHud.WHITE,
                    LAP_MESSAGE_NANOS);
        }
    }

    static void onClientTick(Minecraft minecraft) {
        if (!SpectateSession.INSTANCE.active() && (feed != null || look != null)) {
            clear();
        }
    }

    static void clear() {
        feed = null;
        feedEntity = -1;
        feedNanos = 0L;
        look = null;
        lookEntity = -1;
        lookConfig = null;
        lookBase = null;
    }
}
