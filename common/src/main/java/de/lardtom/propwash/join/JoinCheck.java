package de.lardtom.propwash.join;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.network.JoinInfoPayload;
import de.lardtom.propwash.network.PartsManifestPayload;
import de.lardtom.propwash.parts.PartsHandshake;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class JoinCheck {
    public static final ConfigurationTask.Type INFO_TASK = new ConfigurationTask.Type(JoinInfoPayload.TYPE.id().toString());
    private static final Map<Object, JoinInfo> CLIENTS = Collections.synchronizedMap(new WeakHashMap<>());

    private JoinCheck() {
    }

    public static JoinInfoPayload localPayload() {
        return new JoinInfoPayload(LocalJoinInfo.get());
    }

    public static @Nullable Component onClientInfo(Object listener, JoinInfo client) {
        CLIENTS.put(listener, client);
        JoinInfo server = LocalJoinInfo.get();
        if (server.protocol() == client.protocol()) {
            return null;
        }
        JoinReport report = JoinReport.compute(server, client, null, null);
        logRejected(listener, report);
        return JoinText.disconnect(report);
    }

    public static @Nullable Component onClientParts(Object listener, PartsManifestPayload clientManifest) {
        PartsManifestPayload serverManifest = PartsManifestPayload.local();
        PartsHandshake.Diff diff = PartsHandshake.compare(serverManifest, clientManifest);
        JoinInfo client = CLIENTS.remove(listener);
        if (client == null) {
            if (diff.matches()) {
                return null;
            }
            PartsHandshake.logMismatch(diff);
            return PartsHandshake.message(diff);
        }
        JoinInfo server = LocalJoinInfo.get();
        JoinReport report = JoinReport.compute(server, client, parts(serverManifest), parts(clientManifest));
        if (!diff.matches() && !report.blocking() && diff.builtinsDiffer()) {
            report = report.withPropwash(new JoinReport.PropwashLine(server.propwashVersion(), server.protocol(), client.propwashVersion(),
                    client.protocol(), JoinReport.PropwashLine.Kind.BUILTINS));
        }
        if (report.blocking()) {
            logRejected(listener, report);
            return JoinText.disconnect(report);
        }
        if (report.hasDifferences()) {
            Constants.LOG.info("Propwash: {} hat andere Add-ons als der Server, Propwash lässt den Beitritt zu{}:\n{}", player(listener),
                    report.inactiveParts() > 0 ? " (" + report.inactiveParts() + " Teile nur beim Client, dort für diese Sitzung inaktiv)" : "",
                    JoinText.details(report, null).strip());
        }
        return null;
    }

    public static Component missingPropwash() {
        JoinInfo server = LocalJoinInfo.get();
        return JoinText.missingPropwash(server.propwashVersion(), server.protocol(), server.addons());
    }

    public static List<PartRef> parts(PartsManifestPayload manifest) {
        List<PartRef> out = new ArrayList<>(manifest.entries().size());
        for (PartsManifestPayload.Entry entry : manifest.entries()) {
            out.add(new PartRef(entry.kind(), entry.id().toString(), entry.hash()));
        }
        return out;
    }

    private static void logRejected(Object listener, JoinReport report) {
        Constants.LOG.info("Propwash: Beitritt von {} abgelehnt, Add-ons passen nicht zum Server (Übersicht wie beim Spieler):\n{}", player(listener),
                JoinText.details(report, null).strip());
    }

    public static String player(Object listener) {
        if (listener instanceof ServerConfigurationPacketListenerImpl config) {
            return config.getOwner().name();
        }
        return "?";
    }
}
