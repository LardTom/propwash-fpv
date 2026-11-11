package de.lardtom.propwash.guard;

import de.lardtom.propwash.apiimpl.NamespaceRules;
import de.lardtom.propwash.network.PropwashNet;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class NetworkScan {
    private NetworkScan() {
    }

    public static void run(GuardReport report, boolean client) {
        Set<String> own = own();
        for (Map.Entry<String, Optional<String>> entry : NamespaceGuard.origins().networkChannels(client).entrySet()) {
            String id = entry.getKey();
            if (!NamespaceRules.isReservedId(id) || own.contains(id) || NamespaceGuard.own(entry.getValue())) {
                continue;
            }
            Optional<String> mod = entry.getValue().filter(found -> !NamespaceGuard.origins().platform(found));
            report.add(NamespaceGuard.modOrUnknown(mod), GuardCategory.NETWORK, id);
        }
    }

    private static Set<String> own() {
        Set<String> out = new HashSet<>();
        for (PropwashNet.C2S<?> c2s : PropwashNet.freezeC2S()) {
            out.add(c2s.type().id().toString());
        }
        for (PropwashNet.S2C<?> s2c : PropwashNet.freezeS2C()) {
            out.add(s2c.type().id().toString());
        }
        return out;
    }
}
