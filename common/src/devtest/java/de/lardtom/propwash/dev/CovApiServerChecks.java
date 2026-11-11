package de.lardtom.propwash.dev;

import de.lardtom.propwash.guard.CovGuardServerChecks;
import de.lardtom.propwash.server.CovAddonRulesServerChecks;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

public final class CovApiServerChecks {
    private static String summary = "";

    private CovApiServerChecks() {
    }

    public static @Nullable String check(MinecraftServer server) {
        String rules = CovAddonRulesServerChecks.check(server);
        if (rules != null) {
            return "Rechte an Add-on-Regeln: " + rules;
        }
        String guard = CovGuardServerChecks.check(server);
        if (guard != null) {
            return "Absturzbericht: " + guard;
        }
        summary = "Rechte an Add-on-Regeln: " + CovAddonRulesServerChecks.summary() + "; Absturzbericht: " + CovGuardServerChecks.summary();
        return null;
    }

    public static String summary() {
        return summary;
    }
}
