package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.ui.TuneEdits;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TemplateApplier {
    private static final Set<UUID> HANDLED = new HashSet<>();

    private TemplateApplier() {
    }

    public static void tick(Minecraft minecraft) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.isActive() ? session.drone() : null;
        if (drone == null || !HANDLED.add(drone.getUUID())) {
            return;
        }
        UiConfig config = UiConfig.get();
        Map<String, Float> stored = drone.tune().values();
        if (!config.applyTemplateToNewDrones || config.defaultTune.isEmpty() || TuneEdits.hasOwnTuning(stored)) {
            return;
        }
        if (TuneModel.applyNow(TuneEdits.templateFor(config.defaultTune, stored)) && minecraft.player != null) {
            ThemedToast.show(minecraft.gui.toastManager(), TemplateApplier.class, Component.translatable("propwash.ui.template.applied"), null,
                    Palette.SUCCESS, 3000L);
        }
    }

    public static void reset() {
        HANDLED.clear();
    }
}
