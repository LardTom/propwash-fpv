package de.lardtom.propwash.share.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.share.ShareVectors;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class ShareSmokeCheck {
    private static final int DELAY_TICKS = 20;
    private static boolean initialized;
    private static boolean done;
    private static int inWorld;

    private ShareSmokeCheck() {
    }

    public static synchronized void init() {
        if (initialized || !SelfTest.smokeEnabled()) {
            return;
        }
        initialized = true;
        ClientBus.TICK_END.register(ShareSmokeCheck::tick);
    }

    private static void tick(Minecraft mc) {
        if (done || mc.player == null || mc.level == null) {
            return;
        }
        if (++inWorld < DELAY_TICKS) {
            return;
        }
        done = true;
        List<String> problems = new ArrayList<>();
        int buttons = 0;
        for (WorkbenchExtension extension : PropwashClientApi.workbenchExtensions()) {
            if (extension.id().getPath().contains("share") || extension.getClass().getSimpleName().contains("Share")) {
                buttons++;
            }
        }
        if (buttons != 1) {
            problems.add(buttons + " Sharecode-Knöpfe");
        }
        Optional<String> jmp = Services.PLATFORM.modVersion(ShareVectors.JMP);
        String export = "–";
        if (jmp.isPresent()) {
            export = ShareService.encode(DroneData.of(ShareVectors.jmpBuild()), EnumSet.of(ShareLayer.PARTS), true, Optional.empty());
            if (!ShareVectors.P2_JMP.equals(export)) {
                problems.add("PW3-Export der JMP-Drohne " + export + " statt " + ShareVectors.P2_JMP);
            }
        }
        String summary = "Just More Parts " + jmp.orElse("nicht geladen") + ", Sharecode-Knöpfe " + buttons + ", PW3-Export der JMP-Drohne "
                + export;
        if (problems.isEmpty()) {
            Constants.LOG.info("[Rauchtest] Sharecode OK: {}", summary);
        } else {
            Constants.LOG.error("[Rauchtest] Sharecode FEHLER: {} ({})", String.join("; ", problems), summary);
        }
    }
}
