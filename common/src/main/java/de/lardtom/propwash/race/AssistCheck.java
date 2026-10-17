package de.lardtom.propwash.race;

import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.zone.ZoneResolution;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AssistCheck {
    private AssistCheck() {
    }

    public record Setup(HoldMode hold, @Nullable String rescue, PhysicsRules physics, boolean physicsClaim, boolean creativeDrone,
                        List<String> partIssues, boolean creativeGoggles, boolean ownLatency, int gogglesLatencyMs, int transmitterLatencyMs,
                        List<AssistDetail> zonePhysics) {
        public Setup {
            hold = hold == null ? HoldMode.OFF : hold;
            physics = physics == null ? PhysicsRules.DEFAULT : physics;
            partIssues = partIssues == null ? List.of() : List.copyOf(partIssues);
            zonePhysics = zonePhysics == null ? List.of() : List.copyOf(zonePhysics);
        }

        public Setup(HoldMode hold, @Nullable String rescue, PhysicsRules physics, boolean physicsClaim, boolean creativeDrone,
                     List<String> partIssues, boolean creativeGoggles, boolean ownLatency, int gogglesLatencyMs, int transmitterLatencyMs) {
            this(hold, rescue, physics, physicsClaim, creativeDrone, partIssues, creativeGoggles, ownLatency, gogglesLatencyMs, transmitterLatencyMs,
                    List.of());
        }

        public static Setup clean() {
            return new Setup(HoldMode.OFF, null, PhysicsRules.DEFAULT, false, false, List.of(), false, false, 0, 0);
        }

        public Setup withHold(HoldMode mode) {
            return new Setup(mode, this.rescue, this.physics, this.physicsClaim, this.creativeDrone, this.partIssues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.zonePhysics);
        }

        public Setup withRescue(@Nullable String trigger) {
            return new Setup(this.hold, trigger, this.physics, this.physicsClaim, this.creativeDrone, this.partIssues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.zonePhysics);
        }

        public Setup withPhysics(PhysicsRules rules, boolean claim) {
            return new Setup(this.hold, this.rescue, rules, claim, this.creativeDrone, this.partIssues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.zonePhysics);
        }

        public Setup withZones(ZoneResolution resolution, boolean claim) {
            return this.withZones(resolution, List.of(), claim);
        }

        public Setup withZones(ZoneResolution resolution, List<AssistDetail> blend, boolean claim) {
            List<AssistDetail> zones = new ArrayList<>(resolution.zoneDetails());
            zones.addAll(blend);
            return new Setup(this.hold, this.rescue, resolution.worldPart(), claim, this.creativeDrone, this.partIssues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, zones);
        }

        public Setup withDrone(boolean creative, List<String> issues) {
            return new Setup(this.hold, this.rescue, this.physics, this.physicsClaim, creative, issues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.zonePhysics);
        }

        public Setup withGoggles(boolean creative, boolean ownLatency, int latencyMs, int transmitterMs) {
            return new Setup(this.hold, this.rescue, this.physics, this.physicsClaim, this.creativeDrone, this.partIssues, creative,
                    ownLatency, latencyMs, transmitterMs, this.zonePhysics);
        }
    }

    public static AssistReasons evaluate(Setup setup) {
        List<AssistDetail> details = new ArrayList<>();
        if (setup.hold() != HoldMode.OFF) {
            details.add(AssistDetail.hold(setup.hold()));
        }
        if (setup.rescue() != null) {
            details.add(AssistDetail.rescue(setup.rescue()));
        }
        List<AssistDetail> physics = new ArrayList<>(physics(setup.physics(), false).details());
        physics.addAll(setup.zonePhysics());
        if (physics.isEmpty() && setup.physicsClaim()) {
            physics.add(AssistDetail.clientPhysics());
        }
        details.addAll(physics);
        details.addAll(drone(setup.creativeDrone(), setup.partIssues()).details());
        if (gogglesFaster(setup.creativeGoggles(), setup.ownLatency(), setup.gogglesLatencyMs(), setup.transmitterLatencyMs())) {
            details.add(AssistDetail.goggles(setup.gogglesLatencyMs(), setup.transmitterLatencyMs()));
        }
        return AssistReasons.of(details);
    }

    public static AssistReasons physics(ZoneResolution resolution, boolean claim) {
        return physics(resolution, List.of(), claim);
    }

    public static AssistReasons physics(ZoneResolution resolution, List<AssistDetail> blend, boolean claim) {
        List<AssistDetail> details = new ArrayList<>(resolution.worldDetails());
        details.addAll(resolution.zoneDetails());
        details.addAll(blend);
        if (details.isEmpty() && claim) {
            details.add(AssistDetail.clientPhysics());
        }
        return AssistReasons.of(details);
    }

    public static AssistReasons physics(PhysicsRules rules, boolean claim) {
        List<AssistDetail> details = new ArrayList<>();
        for (RuleSpec spec : rules.changedSpecs()) {
            details.add(AssistDetail.rule(spec, rules.value(spec)));
        }
        if (details.isEmpty() && claim) {
            details.add(AssistDetail.clientPhysics());
        }
        return AssistReasons.of(details);
    }

    public static AssistReasons drone(boolean creative, List<String> issues) {
        List<AssistDetail> details = new ArrayList<>();
        if (creative) {
            details.add(AssistDetail.creative());
        }
        for (String issue : issues) {
            details.add(AssistDetail.issue(issue));
        }
        return AssistReasons.of(details);
    }

    public static AssistReasons lapVerdict(AssistTimeline server, long start, long end, AssistReasons fallback, AssistReasons client) {
        AssistReasons seen = server.covers(start, end) ? server.between(start, end) : fallback;
        server.forgetBefore(end);
        return seen.only(LapAssist.SERVER_REASONS).plus(client.only(LapAssist.CLIENT_REASONS));
    }

    public static AssistReasons withAddon(AssistReasons reasons, @Nullable String assistId, boolean ownsHold) {
        if (assistId == null || assistId.isEmpty()) {
            return reasons;
        }
        AssistReasons base = ownsHold ? reasons.without(LapAssist.HOLD_MODE.bit()) : reasons;
        return base.plus(AssistReasons.of(List.of(AssistDetail.addon(assistId))));
    }

    public static boolean gogglesFaster(boolean creativeGoggles, boolean ownLatency, int latencyMs, int transmitterMs) {
        return creativeGoggles && ownLatency && latencyMs < transmitterMs;
    }
}
