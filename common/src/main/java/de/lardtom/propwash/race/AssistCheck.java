package de.lardtom.propwash.race;

import de.lardtom.propwash.drone.DroneScale;
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
                        List<AssistDetail> zonePhysics, float scale) {
        public Setup {
            hold = hold == null ? HoldMode.OFF : hold;
            physics = physics == null ? PhysicsRules.DEFAULT : physics;
            partIssues = partIssues == null ? List.of() : List.copyOf(partIssues);
            zonePhysics = zonePhysics == null ? List.of() : List.copyOf(zonePhysics);
            scale = DroneScale.sanitize(scale);
        }

        public Setup(HoldMode hold, @Nullable String rescue, PhysicsRules physics, boolean physicsClaim, boolean creativeDrone,
                     List<String> partIssues, boolean creativeGoggles, boolean ownLatency, int gogglesLatencyMs, int transmitterLatencyMs,
                     List<AssistDetail> zonePhysics) {
            this(hold, rescue, physics, physicsClaim, creativeDrone, partIssues, creativeGoggles, ownLatency, gogglesLatencyMs, transmitterLatencyMs,
                    zonePhysics, DroneScale.NORMAL);
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
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.zonePhysics, this.scale);
        }

        public Setup withRescue(@Nullable String trigger) {
            return new Setup(this.hold, trigger, this.physics, this.physicsClaim, this.creativeDrone, this.partIssues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.zonePhysics, this.scale);
        }

        public Setup withPhysics(PhysicsRules rules, boolean claim) {
            return new Setup(this.hold, this.rescue, rules, claim, this.creativeDrone, this.partIssues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.zonePhysics, this.scale);
        }

        public Setup withZones(ZoneResolution resolution, boolean claim) {
            return this.withZones(resolution, List.of(), claim);
        }

        public Setup withZones(ZoneResolution resolution, List<AssistDetail> blend, boolean claim) {
            List<AssistDetail> zones = new ArrayList<>(resolution.zoneDetails());
            zones.addAll(blend);
            return new Setup(this.hold, this.rescue, resolution.worldPart(), claim, this.creativeDrone, this.partIssues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, zones, this.scale);
        }

        public Setup withDrone(boolean creative, List<String> issues) {
            return new Setup(this.hold, this.rescue, this.physics, this.physicsClaim, creative, issues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.zonePhysics, this.scale);
        }

        public Setup withScale(float droneScale) {
            return new Setup(this.hold, this.rescue, this.physics, this.physicsClaim, this.creativeDrone, this.partIssues, this.creativeGoggles,
                    this.ownLatency, this.gogglesLatencyMs, this.transmitterLatencyMs, this.zonePhysics, droneScale);
        }

        public Setup withGoggles(boolean creative, boolean ownLatency, int latencyMs, int transmitterMs) {
            return new Setup(this.hold, this.rescue, this.physics, this.physicsClaim, this.creativeDrone, this.partIssues, creative,
                    ownLatency, latencyMs, transmitterMs, this.zonePhysics, this.scale);
        }
    }

    public static AssistReasons evaluate(Setup setup) {
        List<AssistDetail> physics = new ArrayList<>(physics(setup.physics(), false).details());
        physics.addAll(relevant(setup.zonePhysics()));
        return evaluate(setup, physics);
    }

    public static AssistReasons evaluate(Setup setup, RaceRuleSet rules, RaceStandard standard) {
        return evaluate(setup, rules.mismatches(standard));
    }

    private static AssistReasons evaluate(Setup setup, List<AssistDetail> rulePhysics) {
        List<AssistDetail> details = new ArrayList<>();
        if (setup.hold() != HoldMode.OFF) {
            details.add(AssistDetail.hold(setup.hold()));
        }
        if (setup.rescue() != null) {
            details.add(AssistDetail.rescue(setup.rescue()));
        }
        List<AssistDetail> physics = new ArrayList<>(rulePhysics);
        if (physics.isEmpty() && setup.physicsClaim()) {
            physics.add(AssistDetail.clientPhysics());
        }
        details.addAll(physics);
        details.addAll(drone(setup.creativeDrone(), setup.partIssues(), setup.scale()).details());
        if (gogglesFaster(setup.creativeGoggles(), setup.ownLatency(), setup.gogglesLatencyMs(), setup.transmitterLatencyMs())) {
            details.add(AssistDetail.goggles(setup.gogglesLatencyMs(), setup.transmitterLatencyMs()));
        }
        return AssistReasons.of(details);
    }

    public static List<AssistDetail> relevant(List<AssistDetail> details) {
        List<AssistDetail> out = new ArrayList<>(details.size());
        for (AssistDetail detail : details) {
            if (!detail.neutral()) {
                out.add(detail);
            }
        }
        return out;
    }

    public static AssistReasons relevant(AssistReasons reasons) {
        if (reasons.none()) {
            return reasons;
        }
        int physicsBits = LapAssist.RULE_REASONS;
        List<AssistDetail> kept = relevant(reasons.details());
        if (kept.size() == reasons.details().size()) {
            return reasons;
        }
        int mask = reasons.mask() & ~physicsBits;
        for (AssistDetail detail : kept) {
            mask |= detail.reason().bit();
        }
        for (LapAssist physics : List.of(LapAssist.WORLD_PHYSICS, LapAssist.ZONE_PHYSICS, LapAssist.PERSONAL_RULES)) {
            if (reasons.has(physics) && reasons.details(physics).isEmpty()) {
                mask |= physics.bit();
            }
        }
        return mask == LapAssist.NONE ? AssistReasons.NONE : AssistReasons.of(mask, kept);
    }

    public static AssistReasons judge(Setup setup, RaceRuleSet rules, RaceStandard server) {
        return evaluate(setup, rules.personalMismatches(server));
    }

    public static AssistReasons physics(ZoneResolution resolution, boolean claim) {
        return physics(resolution, List.of(), claim);
    }

    public static AssistReasons physics(ZoneResolution resolution, List<AssistDetail> blend, boolean claim) {
        List<AssistDetail> details = new ArrayList<>(relevant(resolution.worldDetails()));
        details.addAll(relevant(resolution.zoneDetails()));
        details.addAll(relevant(blend));
        if (details.isEmpty() && claim) {
            details.add(AssistDetail.clientPhysics());
        }
        return AssistReasons.of(details);
    }

    public static AssistReasons physics(PhysicsRules rules, boolean claim) {
        List<AssistDetail> details = new ArrayList<>();
        for (RuleSpec spec : rules.changedSpecs()) {
            if (spec.raceRelevant()) {
                details.add(AssistDetail.rule(spec, rules.value(spec)));
            }
        }
        if (details.isEmpty() && claim) {
            details.add(AssistDetail.clientPhysics());
        }
        return AssistReasons.of(details);
    }

    public static AssistReasons drone(boolean creative, List<String> issues) {
        return drone(creative, issues, DroneScale.NORMAL);
    }

    public static AssistReasons drone(boolean creative, List<String> issues, float scale) {
        List<AssistDetail> details = new ArrayList<>();
        if (creative) {
            details.add(AssistDetail.creative());
        }
        for (String issue : issues) {
            details.add(AssistDetail.issue(issue));
        }
        if (DroneScale.scaled(scale)) {
            details.add(AssistDetail.scale(scale));
        }
        return AssistReasons.of(details);
    }

    public static AssistReasons lapVerdict(AssistTimeline server, long start, long end, AssistReasons fallback, AssistReasons client) {
        AssistReasons seen = server.covers(start, end) ? server.between(start, end) : fallback;
        server.forgetBefore(end);
        return relevant(seen.only(LapAssist.SERVER_REASONS).plus(client.only(LapAssist.CLIENT_REASONS)));
    }

    public record LapVerdict(AssistReasons fixed, RaceRuleSet rules, AssistReasons reasons, boolean creativeFc) {
        public LapVerdict(AssistReasons fixed, RaceRuleSet rules, AssistReasons reasons) {
            this(fixed, rules, reasons, false);
        }

        public boolean clean() {
            return this.reasons.none();
        }
    }

    public static AssistReasons creativeFc(AssistReasons reasons, boolean legal) {
        return legal && reasons.has(LapAssist.CREATIVE_DRONE) ? reasons.without(LapAssist.CREATIVE_DRONE.bit()) : reasons;
    }

    public static LapVerdict creativeFc(LapVerdict verdict, boolean legal) {
        if (!legal || !verdict.reasons().has(LapAssist.CREATIVE_DRONE) && !verdict.fixed().has(LapAssist.CREATIVE_DRONE)) {
            return verdict;
        }
        return new LapVerdict(creativeFc(verdict.fixed(), true), verdict.rules(), creativeFc(verdict.reasons(), true), true);
    }

    public static LapVerdict lapVerdict(AssistTimeline server, RuleTimeline timeline, long start, long end, AssistReasons fallback,
                                        RaceRuleSet fallbackRules, AssistReasons client, RaceStandard standard) {
        AssistReasons seen = server.covers(start, end) ? server.between(start, end) : fallback;
        RaceRuleSet rules = timeline.covers(start, end) ? timeline.between(start, end) : fallbackRules;
        server.forgetBefore(end);
        timeline.forgetBefore(end);
        RaceRuleSet.Split parts = RaceRuleSet.split(seen.only(LapAssist.SERVER_REASONS));
        if (!parts.rules().isDefault()) {
            rules = rules.union(parts.rules());
        }
        AssistReasons physics = rules.reasons(standard);
        AssistReasons fixed = parts.fixed().plus(client.only(LapAssist.CLIENT_REASONS));
        if (physics.any()) {
            fixed = withoutClaim(fixed);
        }
        return new LapVerdict(fixed, rules, fixed.plus(physics));
    }

    public static LapVerdict serverLapVerdict(AssistTimeline server, RuleTimeline timeline, long start, long end, AssistReasons fallback,
                                              RaceRuleSet fallbackRules, AssistReasons client, RaceStandard serverRules) {
        AssistReasons seen = server.covers(start, end) ? server.between(start, end) : fallback;
        boolean covered = timeline.covers(start, end);
        RaceRuleSet rules = covered ? timeline.between(start, end) : fallbackRules;
        AssistReasons physics = covered ? timeline.personalBetween(start, end) : fallbackRules.personalReasons(serverRules);
        server.forgetBefore(end);
        timeline.forgetBefore(end);
        RaceRuleSet.Split parts = RaceRuleSet.split(seen.only(LapAssist.SERVER_REASONS));
        if (!parts.rules().isDefault()) {
            rules = rules.union(parts.rules());
            physics = physics.plus(parts.rules().namedPersonalReasons(serverRules));
        }
        AssistReasons fixed = parts.fixed().plus(client.only(LapAssist.CLIENT_REASONS));
        if (physics.any()) {
            fixed = withoutClaim(fixed);
        }
        AssistReasons all = fixed.plus(physics);
        return new LapVerdict(all, rules, all);
    }

    public static AssistReasons withoutClaim(AssistReasons reasons) {
        if (!reasons.has(LapAssist.WORLD_PHYSICS)) {
            return reasons;
        }
        List<AssistDetail> kept = new ArrayList<>();
        boolean world = false;
        for (AssistDetail detail : reasons.details()) {
            if (detail.reason() == LapAssist.WORLD_PHYSICS && AssistDetail.CLIENT.equals(detail.subject())) {
                continue;
            }
            world |= detail.reason() == LapAssist.WORLD_PHYSICS;
            kept.add(detail);
        }
        int mask = world ? reasons.mask() : reasons.mask() & ~LapAssist.WORLD_PHYSICS.bit();
        return mask == LapAssist.NONE ? AssistReasons.NONE : AssistReasons.of(mask, kept);
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
