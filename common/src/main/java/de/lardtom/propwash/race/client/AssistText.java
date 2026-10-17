package de.lardtom.propwash.race.client;

import de.lardtom.propwash.accessory.client.AddonFlightClient;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.race.AssistCheck;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.rescue.RescueSupervisor;
import de.lardtom.propwash.rescue.client.RescueController;
import de.lardtom.propwash.rescue.client.RescueState;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.video.client.VideoConfig;
import de.lardtom.propwash.video.client.VideoSettings;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class AssistText {
    private AssistText() {
    }

    public static AssistReasons evaluate(PilotSession session, QuadcopterEntity drone) {
        VideoState video = VideoState.INSTANCE;
        VideoConfig config = VideoSettings.get();
        DroneBuild build = drone.build();
        AssistReasons reasons = AssistCheck.evaluate(new AssistCheck.Setup(session.holdMode(), rescueTrigger(), session.appliedPhysics(),
                session.physicsModified(), build.isCreative(), RaceServer.issues(build), video.creative(), !config.creativeLatencyFromModule,
                config.creativeLatencyMs, build.video().latencyMs).withZones(session.zoneResolution(), session.zoneBlendDetails(), session.physicsModified()));
        DroneSim sim = session.sim();
        String addon = AddonFlightClient.INSTANCE.engagedAssist(sim);
        return AssistCheck.withAddon(reasons, addon, sim != null && addon != null && sim.assistOwnsHold());
    }

    public static AssistReasons preflight(@Nullable DroneBuild build, @Nullable VideoSystem goggles, HoldMode requestedHold) {
        VideoConfig config = VideoSettings.get();
        PhysicsRules physics = ClientWorldRules.INSTANCE.physics();
        AssistCheck.Setup setup = AssistCheck.Setup.clean().withHold(requestedHold).withRescue(rescueTrigger()).withPhysics(physics, false);
        if (build != null) {
            setup = setup.withDrone(build.isCreative(), RaceServer.issues(build))
                    .withGoggles(goggles == VideoSystem.CREATIVE, !config.creativeLatencyFromModule, config.creativeLatencyMs, build.video().latencyMs);
        }
        return AssistCheck.evaluate(setup);
    }

    public static @Nullable String rescueTrigger() {
        if (!RescueState.INSTANCE.active()) {
            return null;
        }
        RescueSupervisor.Trigger trigger = RescueController.INSTANCE.trigger();
        return trigger == null ? "" : trigger.name().toLowerCase(Locale.ROOT);
    }

    public static Component reasons(AssistReasons reasons) {
        return AssistWords.describe(reasons);
    }

    public static Component reasons(int mask) {
        return AssistWords.describe(AssistReasons.of(mask));
    }

    public static Component storedReasons(int mask) {
        return AssistWords.describe(AssistReasons.of(mask));
    }

    public static Component storedReasons(AssistReasons reasons) {
        return AssistWords.describe(reasons);
    }

    public static Component reason(LapAssist assist) {
        return AssistWords.reason(assist, List.of());
    }
}
