package de.lardtom.propwash.guard;

import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.apiimpl.NamespaceRules;
import de.lardtom.propwash.core.event.ServerBus;

public final class GuardModule {
    private static boolean initialized;
    private static boolean started;

    private GuardModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        listen();
        for (NamespaceRules.Refusal refusal : NamespaceRules.refusals()) {
            refusal(refusal);
        }
        GuardCrash.install();
        PropwashEvents.REGISTRY_FROZEN.register(registry -> startup());
        ServerBus.STARTED.register(GuardServer::started);
        ServerBus.PLAYER_JOIN.register(GuardServer::joined);
    }

    static void listen() {
        NamespaceRules.setModResolver(NamespaceGuard::callerMod);
        NamespaceRules.onRefusal(GuardModule::refusal);
    }

    static void refusal(NamespaceRules.Refusal refusal) {
        if (NamespaceGuard.own(refusal.mod())) {
            return;
        }
        NamespaceGuard.report().add(refusal.mod(), GuardCategory.REFUSED, refusal.id() + " (" + refusal.what() + ")");
    }

    public static synchronized void startup() {
        if (started) {
            return;
        }
        started = true;
        long start = System.nanoTime();
        GuardReport report = NamespaceGuard.report();
        boolean client = MixinScan.clientDist();
        NamespaceGuard.scan(GuardCategory.REGISTRY, () -> RegistryScan.run(report));
        NamespaceGuard.scan(GuardCategory.ENUM, () -> MixinScan.run(report, client));
        NamespaceGuard.scan(GuardCategory.DATA, () -> FileScan.mods(report, FileScan.DATA));
        NamespaceGuard.finish("start", start);
    }
}
