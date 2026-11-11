package de.lardtom.propwash.stats.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.freecam.client.FreecamSession;
import de.lardtom.propwash.guide.client.training.TrainingStore;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.stats.CrashWatch;
import de.lardtom.propwash.stats.advancement.TrainingTally;
import de.lardtom.propwash.stats.net.CrashC2S;
import de.lardtom.propwash.stats.net.GhostBeatenC2S;
import de.lardtom.propwash.stats.net.TrainingC2S;
import de.lardtom.propwash.stats.net.TrickC2S;
import de.lardtom.propwash.trick.Trick;
import de.lardtom.propwash.trick.TrickScore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class StatsClient {
    public static final int TRAINING_INTERVAL_TICKS = 40;
    public static final int MIN_COMBO = 2;
    private static final CrashWatch WATCH = new CrashWatch();
    private static final Set<String> TRICKS = new HashSet<>();
    private static boolean initialized;
    private static int sent;
    private static int combo;
    private static int ticks;
    private static int reports;
    private static @Nullable TrainingTally training;

    private StatsClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientBus.TICK_END.register(StatsClient::tick);
        ClientBus.DISCONNECT.register(minecraft -> {
            WATCH.reset();
            forget();
        });
        ClientBus.LOGIN.register(minecraft -> forget());
    }

    public static int sentCrashes() {
        return sent;
    }

    public static int reports() {
        return reports;
    }

    public static void forget() {
        TRICKS.clear();
        combo = 0;
        training = null;
        ticks = 0;
    }

    public static void tricks(QuadcopterEntity drone, TrickScore score) {
        if (FreecamSession.active()) {
            return;
        }
        for (Trick trick : score.awarded()) {
            if (TRICKS.add(trick.type().key())) {
                report(new TrickC2S(drone.getId(), trick.type().key(), 0));
            }
        }
        int best = Math.min(score.bestCombo(), TrickC2S.MAX_COMBO);
        if (best >= MIN_COMBO && best > combo) {
            combo = best;
            report(new TrickC2S(drone.getId(), "", best));
        }
    }

    public static void ghostBeaten(int courseId) {
        if (!FreecamSession.active()) {
            report(new GhostBeatenC2S(courseId));
        }
    }

    private static void report(CustomPacketPayload payload) {
        if (ClientNet.sendToServer(payload)) {
            reports++;
        }
    }

    private static void tick(Minecraft minecraft) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        if (!session.isActive() || drone == null) {
            WATCH.observe(false, CrashWatch.NONE, 0);
        } else {
            int delta = WATCH.observe(true, drone.getId(), session.crashCount());
            while (delta > 0) {
                int part = Math.min(delta, CrashC2S.MAX_CRASHES);
                if (ClientNet.sendToServer(new CrashC2S(drone.getId(), part))) {
                    sent += part;
                }
                delta -= part;
            }
        }
        if (minecraft.player != null && minecraft.getConnection() != null && ++ticks >= TRAINING_INTERVAL_TICKS) {
            ticks = 0;
            TrainingTally now = TrainingTally.of(TrainingStore.progress());
            if (!now.equals(training) && (now.chapters() > 0 || now.perfect())) {
                training = now;
                report(new TrainingC2S(now.chapters(), now.perfect()));
            }
        }
    }
}
