package de.lardtom.propwash.guard;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.platform.Services;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GuardCrash {
    public static final String TITLE = "Propwash add-ons";
    private static final String INDENT = "\n\t\t";
    private static volatile boolean viaCallable;

    private GuardCrash() {
    }

    public static void install() {
        try {
            viaCallable = NamespaceGuard.origins().addCrashSection(TITLE, GuardCrash::section);
        } catch (RuntimeException | LinkageError e) {
            Constants.LOG.warn("Crash report section '{}' not registered: {}", TITLE, e.toString());
        }
    }

    public static boolean viaCallable() {
        return viaCallable;
    }

    public static String section() {
        StringBuilder out = new StringBuilder();
        out.append(INDENT).append("Add-ons: ").append(addons());
        out.append(INDENT).append("Mixins into Propwash internals: ").append(mixins());
        out.append(INDENT).append("Namespace check: ").append(findings());
        return out.toString();
    }

    private static String addons() {
        try {
            List<ModMeta> addons = Services.PLATFORM.dependentMods(Constants.MOD_ID);
            if (addons.isEmpty()) {
                return "none";
            }
            StringBuilder out = new StringBuilder();
            for (ModMeta addon : addons) {
                out.append(INDENT).append('\t').append(addon.name()).append(" (").append(addon.id()).append(") ").append(addon.version());
            }
            return out.toString();
        } catch (RuntimeException | LinkageError e) {
            return "unavailable (" + e + ")";
        }
    }

    private static String mixins() {
        try {
            Map<String, Set<String>> targets = MixinScan.foreignTargets();
            if (targets.isEmpty()) {
                return "none";
            }
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, Set<String>> entry : targets.entrySet()) {
                out.append(INDENT).append('\t').append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" classes");
            }
            return out.toString();
        } catch (RuntimeException | LinkageError e) {
            return "unavailable (" + e + ")";
        }
    }

    private static String findings() {
        Map<String, Integer> summary = NamespaceGuard.report().summary();
        if (NamespaceGuard.runs() == 0) {
            return "not run yet";
        }
        if (summary.isEmpty()) {
            return "no findings";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> entry : summary.entrySet()) {
            out.append(INDENT).append('\t').append(entry.getKey()).append(": ").append(entry.getValue()).append(" findings");
        }
        return out.toString();
    }
}
