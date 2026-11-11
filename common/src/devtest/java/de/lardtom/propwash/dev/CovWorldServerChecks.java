package de.lardtom.propwash.dev;

import de.lardtom.propwash.rx.CovRxServerChecks;
import de.lardtom.propwash.server.CovClearServerChecks;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

public final class CovWorldServerChecks {
    public static final int LIMIT_TICKS = 1600;
    private static int part;
    private static @Nullable String failure;
    private static String summary = "";

    private CovWorldServerChecks() {
    }

    public static boolean enabled() {
        return JoinTestEnv.holdSeconds() == 0;
    }

    public static @Nullable String failure() {
        return failure;
    }

    public static String summary() {
        return summary;
    }

    public static boolean tick(MinecraftServer server, int tick) {
        if (part == 0) {
            if (!CovRxServerChecks.tick(server)) {
                if (tick < LIMIT_TICKS) {
                    return false;
                }
                failure = "RX-Block mit zwei Spielern: nach " + tick + " Ticks nicht fertig";
                return true;
            }
            if (CovRxServerChecks.failure() != null) {
                failure = "RX-Block mit zwei Spielern: " + CovRxServerChecks.failure();
                return true;
            }
            part = 1;
            return false;
        }
        if (!CovClearServerChecks.tick(server)) {
            if (tick < LIMIT_TICKS) {
                return false;
            }
            failure = "/propwash clear: nach " + tick + " Ticks nicht fertig";
            return true;
        }
        if (CovClearServerChecks.failure() != null) {
            failure = "/propwash clear: " + CovClearServerChecks.failure();
            return true;
        }
        summary = "RX-Block mit zwei Spielern: " + CovRxServerChecks.summary() + " | /propwash clear: " + CovClearServerChecks.summary();
        return true;
    }
}
