package de.lardtom.propwash.join.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.join.JoinClientState;
import de.lardtom.propwash.join.JoinReport;
import de.lardtom.propwash.join.JoinText;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import org.jspecify.annotations.Nullable;

public final class JoinCheckClient {
    private JoinCheckClient() {
    }

    public static @Nullable Screen disconnectScreen(Object configListener, DisconnectionDetails details, Screen parent) {
        if (!JoinClientState.current(configListener)) {
            return null;
        }
        try {
            JoinReport report = JoinClientState.report();
            if (report == null || !report.hasDifferences()) {
                return null;
            }
            boolean ours = JoinText.ours(details.reason());
            Constants.LOG.info("Propwash: Verbindung getrennt, Add-on-Unterschiede zum Server:\n{}", JoinText.details(report, details.reason()).strip());
            if (!ours && !report.actionable()) {
                Constants.LOG.info("Propwash: Trennung kam nicht von Propwash, die Unterschiede sind erlaubt – Meldung des Loaders bleibt");
                return null;
            }
            return new JoinMismatchScreen(parent, report, details.reason(), ours);
        } catch (RuntimeException e) {
            Constants.LOG.error("Propwash: Übersicht der Add-on-Unterschiede nicht erstellt", e);
            return null;
        }
    }
}
