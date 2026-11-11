package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.accessory.AccessoryPayload;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.collision.client.ClientCollision;
import de.lardtom.propwash.drone.led.DroneLeds;
import de.lardtom.propwash.freecam.FreecamPreset;
import de.lardtom.propwash.freecam.client.FreecamClient;
import de.lardtom.propwash.freecam.client.FreecamConfig;
import de.lardtom.propwash.freecam.client.FreecamSession;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.FlightPathMarker;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdFormat;
import de.lardtom.propwash.osd.OsdSampler;
import de.lardtom.propwash.race.client.RaceState;
import de.lardtom.propwash.rescue.RescuePhase;
import de.lardtom.propwash.rescue.client.AutonomyLabels;
import de.lardtom.propwash.rescue.client.RescueState;
import de.lardtom.propwash.sim.BatteryState;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.drone.client.EdgeGuard;
import de.lardtom.propwash.drone.client.FpvView;
import de.lardtom.propwash.video.PilotFeed;
import de.lardtom.propwash.video.VtxSettings;
import de.lardtom.propwash.video.client.SpectateConfig;
import de.lardtom.propwash.video.client.SpectateSession;
import de.lardtom.propwash.trick.client.TrickTracker;
import de.lardtom.propwash.video.client.VideoState;
import de.lardtom.propwash.video.client.VtxRuntime;
import de.lardtom.propwash.video.client.WatchedPilot;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.client.record.FlashbackCam;
import de.lardtom.propwash.video.record.RecordingJob;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class LiveOsdSource implements OsdSampler.Source {
    private final Quaternionf orientation = new Quaternionf();
    private final Vector3f forward = new Vector3f();
    private final FlightPathMarker.Velocity velocity = new FlightPathMarker.Velocity();
    private final Vector3d shownPosition = new Vector3d();
    private final Vector3d earlierPosition = new Vector3d();
    private final Quaternionf poseScratch = new Quaternionf();
    private @Nullable Language language;
    private @Nullable Component craftNameSource;
    private String craftName = "";
    private @Nullable Component rescuePhaseSource;
    private String rescuePhase = "";
    private @Nullable FreecamPreset freecamNamePreset;
    private String freecamName = "";
    private @Nullable RescuePhase watchedPhase;
    private String watchedPhaseText = "";
    private boolean remote;

    public LiveOsdSource() {
    }

    @Override
    public boolean active() {
        if (FreecamSession.active()) {
            return FreecamConfig.get().osd;
        }
        return PilotSession.INSTANCE.isFpv() || (FpvView.spectating() && SpectateConfig.get().showPilotOsd);
    }

    @Override
    public void fill(OsdData d) {
        Language current = Language.getInstance();
        if (current != this.language) {
            this.language = current;
            this.craftNameSource = null;
            this.craftName = "";
            this.rescuePhaseSource = null;
            this.rescuePhase = "";
            this.freecamNamePreset = null;
            this.watchedPhase = null;
            this.watchedPhaseText = "";
        }
        this.remote = false;
        d.spectators = 0;
        if (FreecamSession.active()) {
            this.fillFreecam(d);
            return;
        }
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isActive() && FpvView.spectating()) {
            this.fillSpectating(d);
            return;
        }
        DroneSim sim = session.sim();
        if (sim != null) {
            d.applySim(sim);
        }
        QuadcopterEntity drone = session.drone();
        if (drone != null) {
            this.fillFromEntity(d, drone, sim != null);
            d.spectators = SpectatorCountClient.count(drone);
        }
        RadioState radio = RadioState.INSTANCE;
        RescueState rescue = RescueState.INSTANCE;
        d.radioConnected = radio.connected();
        d.linkStrength = OsdSampler.clamp01(session.linkStrength());
        d.rssiDbm = session.rssiDbm();
        d.rescueActive = rescue.active();
        d.worldEdge = EdgeGuard.INSTANCE.warning();
        d.insideBlock = ClientCollision.INSTANCE.insideBlock();
        d.overloaded = drone != null && AccessoryPayload.overloaded(drone);
        Component phase = d.rescueActive ? rescue.phaseLabel() : null;
        if (phase != this.rescuePhaseSource) {
            this.rescuePhaseSource = phase;
            this.rescuePhase = phase == null ? "" : phase.getString();
        }
        d.rescuePhase = this.rescuePhase;
        StickPositions sticks = d.rescueActive ? rescue.commandedSticks() : radio.sticks();
        if (sticks == null) {
            sticks = StickPositions.IDLE;
        }
        d.stickRoll = sticks.roll();
        d.stickPitch = sticks.pitch();
        d.stickYaw = sticks.yaw();
        d.stickThrottle = !d.rescueActive && PilotSession.INSTANCE.mode3dActive() ? radio.throttle3d() : sticks.throttle();
        d.turtle = PilotSession.INSTANCE.turtleArmed() || !d.armed && PilotSession.INSTANCE.turtleSelected();
        if (!d.hasSim) {
            d.throttle = sticks.throttle();
        }
        this.fillVideo(d);
        this.fillVelocity(d);
        Vec3 home = rescue.home();
        if (home != null) {
            d.hasHome = true;
            d.homeX = home.x;
            d.homeY = home.y;
            d.homeZ = home.z;
        }
        RaceState race = RaceState.INSTANCE;
        d.racing = race.racing();
        d.lap = race.lap();
        d.pointToPoint = race.pointToPoint();
        d.currentLapNanos = race.currentLapNanos();
        d.lastLapNanos = race.lastLapNanos();
        d.bestLapNanos = race.bestLapNanos();
        d.nextGate = race.nextGate();
        d.gateCount = race.gateCount();
        d.splitDeltaNanos = race.splitDeltaNanos();
        TrickTracker tricks = TrickTracker.INSTANCE;
        if (tricks.active()) {
            d.applyTricks(tricks.session());
        }
        fillRecording(d);
    }

    private void fillFreecam(OsdData d) {
        FreecamSession freecam = FreecamSession.INSTANCE;
        DroneSim sim = freecam.sim();
        if (sim != null) {
            d.applySim(sim);
        }
        d.freecam = true;
        d.freecamEdge = freecam.edge();
        d.gpsKnown = false;
        d.gpsSatellites = 0;
        d.gpsFix = false;
        d.batteryState = BatteryState.OK;
        d.packVoltage = Double.NaN;
        d.cellVoltage = Double.NaN;
        d.warnCellVoltage = Double.NaN;
        d.criticalCellVoltage = Double.NaN;
        d.chargeFraction = Double.NaN;
        d.mAhUsed = Double.NaN;
        d.currentA = Double.NaN;
        d.powerW = Double.NaN;
        d.cells = 0;
        d.capacityMah = 0;
        d.motorHot = false;
        d.escDerating = false;
        freecam.position(this.shownPosition);
        d.hasPosition = true;
        d.x = this.shownPosition.x;
        d.y = this.shownPosition.y;
        d.z = this.shownPosition.z;
        if (freecam.preset() != this.freecamNamePreset) {
            this.freecamNamePreset = freecam.preset();
            this.freecamName = FreecamClient.osdName(freecam.preset()).getString();
        }
        d.craftName = this.freecamName;
        RadioState radio = RadioState.INSTANCE;
        d.radioConnected = radio.connected() || freecam.testInput();
        d.linkStrength = 1.0;
        d.rssiDbm = Double.NaN;
        StickPositions sticks = freecam.sticks();
        d.stickRoll = sticks.roll();
        d.stickPitch = sticks.pitch();
        d.stickYaw = sticks.yaw();
        d.stickThrottle = sticks.throttle();
        d.videoSystem = null;
        d.videoSignal = 0.0;
        d.videoLost = false;
        d.videoNoise = 0.0;
        d.relayHops = 0;
        freecam.velocity(this.earlierPosition);
        if (sim != null) {
            d.velocityX = this.earlierPosition.x;
            d.velocityY = this.earlierPosition.y;
            d.velocityZ = this.earlierPosition.z;
        } else {
            d.clearVelocity();
        }
    }

    private void fillVideo(OsdData d) {
        VideoState video = VideoState.INSTANCE;
        d.videoSystem = video.system();
        d.videoSignal = d.videoSystem == null ? 0.0 : OsdSampler.clamp01(video.signalQuality());
        d.videoShift = video.osdShift();
        d.videoLost = d.videoSystem != null && video.videoLost();
        d.videoNoise = d.videoSystem == null ? 0.0 : video.noiseLevel();
        d.relayHops = d.videoSystem == null ? 0 : video.relayHops();
        for (int hop = 0; hop < OsdData.MAX_RELAY_HOPS; hop++) {
            d.relayHopQuality[hop] = hop <= d.relayHops && d.relayHops > 0 ? OsdSampler.clamp01(video.relayHopQuality(hop)) : 0.0;
        }
    }

    @Override
    public boolean remoteClock() {
        return this.remote;
    }

    private void fillSpectating(OsdData d) {
        SpectateSession spectate = SpectateSession.INSTANCE;
        QuadcopterEntity drone = spectate.drone();
        PilotFeed feed = WatchedPilot.feed();
        d.spectating = true;
        d.assistLabel = AutonomyLabels.label(drone);
        if (drone != null) {
            this.fillFromEntity(d, drone, feed != null);
            d.spectators = SpectatorCountClient.count(drone);
            d.speed = spectate.speed();
            d.verticalSpeed = spectate.verticalSpeed();
        }
        d.radioConnected = true;
        d.linkStrength = 1.0;
        d.rssiDbm = Double.NaN;
        d.stickRoll = 0.0F;
        d.stickPitch = 0.0F;
        d.stickYaw = 0.0F;
        d.stickThrottle = 0.0F;
        d.throttle = drone != null ? OsdSampler.clamp01(drone.motorOutput()) : 0.0;
        this.fillVideo(d);
        this.fillVelocity(d);
        if (feed != null) {
            String autonomy = d.assistLabel;
            feed.apply(d, WatchedPilot.ageNanos());
            if (d.assistLabel == null) {
                d.assistLabel = autonomy;
            }
            d.rescuePhase = d.rescueActive ? this.watchedPhaseText(feed.rescuePhase()) : "";
            this.remote = true;
        }
    }

    private String watchedPhaseText(@Nullable RescuePhase phase) {
        if (phase == null) {
            return "";
        }
        if (phase != this.watchedPhase) {
            this.watchedPhase = phase;
            this.watchedPhaseText = Component.translatable(phase.translationKey()).getString();
        }
        return this.watchedPhaseText;
    }

    private void fillVelocity(OsdData d) {
        VideoState video = VideoState.INSTANCE;
        long shown = video.lastSampleNanos();
        if (video.sampleDisplayedAt(0L, this.shownPosition, this.poseScratch)
                && video.sampleDisplayedAt(FlightPathMarker.SPAN_NANOS, this.earlierPosition, this.poseScratch)
                && this.velocity.update(shown, this.shownPosition.x - this.earlierPosition.x, this.shownPosition.y - this.earlierPosition.y,
                this.shownPosition.z - this.earlierPosition.z, FlightPathMarker.SPAN_NANOS)) {
            d.velocityX = this.velocity.x();
            d.velocityY = this.velocity.y();
            d.velocityZ = this.velocity.z();
            return;
        }
        this.velocity.reset();
        d.clearVelocity();
    }

    private static void fillRecording(OsdData d) {
        FlashbackCam flashback = FlashbackCam.INSTANCE;
        FlashbackCam.Budget budget = flashback.budget();
        d.flashbackFill = budget == null ? Double.NaN : budget.fill();
        d.flashbackLimit = budget == null || budget.limit();
        if (flashback.recording()) {
            d.recording = flashback.paused() ? OsdData.REC_PAUSED : OsdData.REC_ON;
            d.recordingSeconds = flashback.recordedSeconds();
            return;
        }
        ActionCamRecorder recorder = ActionCamRecorder.INSTANCE;
        if (recorder.phase() == ActionCamRecorder.Phase.RECORDING) {
            d.recording = recorder.paused() ? OsdData.REC_PAUSED : OsdData.REC_ON;
            d.recordingSeconds = recorder.recordedNanos() / 1.0E9;
            return;
        }
        List<RecordingJob.Progress> saving = recorder.saving();
        if (recorder.phase() == ActionCamRecorder.Phase.STOPPING || !saving.isEmpty()) {
            d.recording = OsdData.REC_SAVING;
            d.recordingPercent = saving.isEmpty() ? 0 : Math.round(saving.getFirst().fraction() * 100.0F);
        }
    }

    private void fillFromEntity(OsdData d, QuadcopterEntity drone, boolean hasSim) {
        d.hasPosition = true;
        d.x = drone.getX();
        d.y = drone.getY();
        d.z = drone.getZ();
        Component custom = drone.getCustomName();
        if (custom != this.craftNameSource) {
            this.craftNameSource = custom;
            this.craftName = custom == null ? "" : custom.getString();
        }
        d.craftName = this.craftName;
        d.wear = drone.wear();
        d.ledsOff = DroneLeds.switchedOff(drone);
        DroneBuild build = drone.build();
        d.vtxChannel = build == null ? "" : VtxSettings.label(drone.tune().values(), build.video().system);
        if (build != null) {
            VtxRuntime.Readout vtx = VtxRuntime.INSTANCE.readout(drone);
            d.vtxPowerMw = vtx.milliwatts();
            d.vtxPowerAuto = vtx.auto();
            d.vtxThrottled = vtx.throttled();
            d.vtxHot = vtx.hot();
        } else {
            d.vtxPowerMw = 0;
            d.vtxPowerAuto = false;
            d.vtxThrottled = false;
            d.vtxHot = false;
        }
        if (!hasSim && build != null && build.battery() != null) {
            d.cells = build.battery().cells;
            d.capacityMah = build.battery().capacityMah;
        }
        if (hasSim) {
            return;
        }
        d.armed = drone.isArmed();
        d.chargeFraction = OsdSampler.clamp01(drone.batteryCharge());
        d.batteryState = OsdSampler.batteryStateFor(d.chargeFraction);
        if (build != null && build.battery() != null && drone.hasBattery() && build.battery().cells > 0) {
            d.packVoltage = Batteries.openCircuitVoltage(build.battery(), (float) d.chargeFraction);
            d.cellVoltage = d.packVoltage / build.battery().cells;
            d.mAhUsed = build.battery().capacityMah * (1.0 - d.chargeFraction);
        }
        Vec3 motion = drone.getDeltaMovement();
        d.speed = motion.length() * 20.0;
        d.verticalSpeed = motion.y * 20.0;
        this.orientation.set(drone.orientation());
        this.orientation.transform(this.forward.set(0.0F, 0.0F, -1.0F));
        if (this.forward.x * this.forward.x + this.forward.z * this.forward.z > 1.0E-6F) {
            d.compassDeg = OsdFormat.bearing(this.forward.x, this.forward.z);
        }
        d.pitchDeg = -Math.toDegrees(Math.asin(Math.clamp(this.forward.y, -1.0F, 1.0F)));
    }
}
