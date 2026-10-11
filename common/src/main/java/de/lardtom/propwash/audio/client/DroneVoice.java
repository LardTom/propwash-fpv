package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.AudioSounds;
import de.lardtom.propwash.audio.MotorControl;
import de.lardtom.propwash.audio.MotorSynth;
import de.lardtom.propwash.audio.MotorTimbre;
import de.lardtom.propwash.audio.SoundReach;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class DroneVoice {
    public enum Kind { NONE, SYNTH, SAMPLE }

    private static final double TICK_SECONDS = 0.05;
    private static final float SPIN_UP_TAU = 0.06F;
    private static final float ARMED_SPIN_DOWN_TAU = 0.09F;
    private static final float FREEWHEEL_TAU = 0.3F;
    private static final float STOPPED_RPM = 60.0F;
    private static final float PILOT_GAIN = 0.85F;
    private static final float CLOSE_BRIGHTNESS_HZ = 14_000.0F;
    private static final int RESTART_WINDOW_TICKS = 200;
    private static final int MAX_RESTARTS = 4;
    private static final double SPEED_OF_SOUND = 343.0;
    private static final double FULL_AIRFLOW_SPEED = 45.0;
    private static final float UNDERWATER_BRIGHTNESS_HZ = 900.0F;
    private static final float UNDERWATER_GAIN = 0.45F;
    private static final double WASH_HORIZONTAL_SPEED = 8.0;
    private static final double KEEP_RANGE_FACTOR = 1.15;
    private static final double KEEP_RANGE_BLOCKS = 4.0;

    final int entityId;
    final QuadcopterEntity drone;
    final MotorControl control = new MotorControl();
    private final long seed;
    private MotorTimbre timbre = MotorTimbre.DEFAULT;
    private SoundReach reach = SoundReach.REFERENCE;
    private @Nullable DroneBuild build;

    Kind kind = Kind.NONE;
    private @Nullable MotorSoundInstance synth;
    private @Nullable MotorSampleSoundInstance sample;
    private @Nullable ChannelAccess.ChannelHandle handle;
    private boolean pilotView;
    private boolean releasing;
    private boolean silenced;
    long synthRetryTick;
    private long sampleRetryTick;
    private long restartWindowStart;
    private int restartCount;

    private final float[] spinRpm = new float[MotorControl.MOTORS];
    private final float[] outputs = new float[MotorControl.MOTORS];
    private boolean armed;
    private boolean underwater;
    private double x;
    private double y;
    private double z;
    private double velX;
    private double velY;
    private double velZ;
    private boolean hasPrevious;
    private float doppler = 1.0F;
    private double distance;
    private double range = SoundReach.MIN_RANGE_BLOCKS;
    private float baseGain;
    private float distanceGain = 1.0F;
    private double priority;
    private int seenCrashes = -1;
    private int seenStrikes = -1;

    DroneVoice(QuadcopterEntity drone) {
        this.drone = drone;
        this.entityId = drone.getId();
        this.seed = drone.getUUID().getLeastSignificantBits() ^ drone.getUUID().getMostSignificantBits();
        this.observe();
    }

    void observe() {
        DroneBuild current = this.drone.build();
        if (current != this.build) {
            this.build = current;
            this.timbre = MotorTimbre.of(current);
            this.reach = SoundReach.of(current);
        }
        double nx = this.drone.getX();
        double ny = this.drone.getY();
        double nz = this.drone.getZ();
        if (this.hasPrevious) {
            double vx = (nx - this.x) / TICK_SECONDS;
            double vy = (ny - this.y) / TICK_SECONDS;
            double vz = (nz - this.z) / TICK_SECONDS;
            if (vx * vx + vy * vy + vz * vz > 200.0 * 200.0) {
                vx = 0.0;
                vy = 0.0;
                vz = 0.0;
            }
            this.velX += 0.5 * (vx - this.velX);
            this.velY += 0.5 * (vy - this.velY);
            this.velZ += 0.5 * (vz - this.velZ);
        }
        this.x = nx;
        this.y = ny;
        this.z = nz;
        this.hasPrevious = true;
        this.armed = this.drone.isArmed();
        this.underwater = this.drone.isInWater();
        for (int m = 0; m < MotorControl.MOTORS; m++) {
            float output = this.drone.motorOutput(m);
            this.outputs[m] = Float.isFinite(output) ? output : 0.0F;
        }
    }

    MotorTimbre timbre() {
        return this.timbre;
    }

    boolean pilotView() {
        return this.pilotView;
    }

    double priority() {
        return this.priority;
    }

    double distance() {
        return this.distance;
    }

    double range() {
        return this.range;
    }

    float distanceGain() {
        return this.distanceGain;
    }

    SoundReach reach() {
        return this.reach;
    }

    boolean isStale(ClientLevel level) {
        return this.drone.isRemoved() || this.drone.level() != level || level.getEntity(this.entityId) != this.drone;
    }

    void updateTargets(boolean pilotView, @Nullable DroneSim sim, AudioConfig config, double listenerX, double listenerY,
                       double listenerZ, double listenerVx, double listenerVy, double listenerVz) {
        this.pilotView = pilotView;
        double dx = listenerX - this.x;
        double dy = listenerY - this.y;
        double dz = listenerZ - this.z;
        this.distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (pilotView && sim != null) {
            this.readTelemetry(sim.telemetry());
            this.control.setSmoothingSeconds(0.012);
        } else {
            this.spinModel();
            this.control.setAirflow(pilotView ? clamp(Math.sqrt(this.velX * this.velX + this.velY * this.velY + this.velZ * this.velZ)
                    / FULL_AIRFLOW_SPEED, 0.0, 1.0) : 0.0);
            this.control.setSmoothingSeconds(0.05);
        }
        if (pilotView || !config.doppler || this.distance < 0.5) {
            this.doppler += 0.5F * (1.0F - this.doppler);
        } else {
            double inv = 1.0 / this.distance;
            double nx = dx * inv;
            double ny = dy * inv;
            double nz = dz * inv;
            double sourceToward = clamp(this.velX * nx + this.velY * ny + this.velZ * nz, -250.0, 250.0);
            double listenerAway = clamp(listenerVx * nx + listenerVy * ny + listenerVz * nz, -250.0, 250.0);
            double factor = (SPEED_OF_SOUND - listenerAway) / (SPEED_OF_SOUND - sourceToward);
            this.doppler += 0.35F * ((float) clamp(factor, 0.7, 1.4) - this.doppler);
        }
        this.range = pilotView ? SoundReach.MAX_RANGE_BLOCKS : this.reach.rangeBlocks(this.amplitude());
        float brightness = pilotView ? CLOSE_BRIGHTNESS_HZ : (float) Math.min(CLOSE_BRIGHTNESS_HZ, SoundReach.airCutoffHz(this.distance));
        float gain = (float) (config.motorVolume * this.timbre.loudness() * (pilotView ? PILOT_GAIN : 1.0F));
        if (this.underwater) {
            brightness = Math.min(brightness, UNDERWATER_BRIGHTNESS_HZ);
            gain *= UNDERWATER_GAIN;
        }
        this.baseGain = gain;
        this.distanceGain = pilotView ? 1.0F : (float) SoundReach.attenuation(this.distance, this.range);
        this.control.setDoppler(this.doppler);
        this.control.setBrightnessHz(brightness);
        this.control.setGain(pilotView ? gain : gain * this.distanceGain);
        this.control.setOnboard(pilotView);
        if (this.synth != null) {
            this.synth.setAudible(pilotView || this.distanceGain > 0.0F);
        }
        this.control.setActive(!this.releasing);
        this.control.publish();
        this.priority = pilotView ? -1.0 : this.distance / this.range;
        if (this.kind == Kind.SYNTH) {
            this.priority *= 0.85;
        }
    }

    private double amplitude() {
        double sum = 0.0;
        for (int m = 0; m < MotorControl.MOTORS; m++) {
            sum += SoundReach.amplitude(this.control.rpm(m), this.timbre.maxRpm(), this.control.load(m));
        }
        return sum / MotorControl.MOTORS;
    }

    private void spinModel() {
        float sum = 0.0F;
        for (int m = 0; m < MotorControl.MOTORS; m++) {
            float output = Math.max(0.0F, Math.min(1.0F, this.outputs[m]));
            float target = this.armed ? (float) this.timbre.rpmForOutput(output) : 0.0F;
            float tau = target > this.spinRpm[m] ? SPIN_UP_TAU : (this.armed ? ARMED_SPIN_DOWN_TAU : FREEWHEEL_TAU);
            float k = 1.0F - (float) Math.exp(-TICK_SECONDS / tau);
            this.spinRpm[m] += k * (target - this.spinRpm[m]);
            if (!this.armed && this.spinRpm[m] < STOPPED_RPM) {
                this.spinRpm[m] = 0.0F;
            }
            float load = this.armed ? output : 0.0F;
            this.control.setMotor(m, this.spinRpm[m], load);
            sum += load;
        }
        double horizontal = Math.sqrt(this.velX * this.velX + this.velZ * this.velZ);
        double sink = clamp((-this.velY - 2.0) / 6.0, 0.0, 1.0) * clamp(1.0 - horizontal / WASH_HORIZONTAL_SPEED, 0.0, 1.0);
        this.control.setWash(sink * clamp(sum / MotorControl.MOTORS * 2.5, 0.0, 1.0));
    }

    void readTelemetry(Telemetry t) {
        double maxCurrent = this.timbre.maxCurrentA();
        for (int m = 0; m < MotorControl.MOTORS; m++) {
            double rpm = t.motorRpm[m];
            this.spinRpm[m] = (float) (Double.isFinite(rpm) ? Math.abs(rpm) : 0.0);
            this.control.setMotor(m, this.spinRpm[m], Math.abs(t.motorCurrentA[m]) / maxCurrent);
        }
        this.control.setWash(t.propWash);
        this.control.setAirflow(Double.isFinite(t.airspeed) ? clamp(t.airspeed / FULL_AIRFLOW_SPEED, 0.0, 1.0) : 0.0);
        this.armed = this.armed || t.armed;
        if (this.seenStrikes >= 0 && t.propStrikeCount != this.seenStrikes) {
            this.control.impulse(0.55);
        }
        if (this.seenCrashes >= 0 && t.crashCount != this.seenCrashes) {
            this.control.impulse(1.0);
        }
        this.seenStrikes = t.propStrikeCount;
        this.seenCrashes = t.crashCount;
    }

    void pushPilotTelemetry(Telemetry t) {
        this.readTelemetry(t);
        this.control.publish();
    }

    boolean wantsSound() {
        if (this.silenced) {
            if (this.armed || this.maxSpin() > STOPPED_RPM) {
                return false;
            }
            this.silenced = false;
        }
        if (this.pilotView) {
            return this.armed || this.maxSpin() > STOPPED_RPM;
        }
        if (!this.armed && this.maxSpin() <= STOPPED_RPM) {
            return false;
        }
        double keep = this.kind == Kind.NONE ? this.range : this.range * KEEP_RANGE_FACTOR + KEEP_RANGE_BLOCKS;
        return this.distance <= keep;
    }

    float maxSpin() {
        float max = 0.0F;
        for (float rpm : this.spinRpm) {
            max = Math.max(max, rpm);
        }
        return max;
    }

    void apply(Kind want, SoundManager soundManager, long tick) {
        if (want == Kind.NONE) {
            this.release();
            return;
        }
        if (this.releasing) {
            this.releasing = false;
            this.control.setActive(true);
            this.control.publish();
        }
        boolean viewChanged = (this.synth != null && this.synth.pilotView() != this.pilotView)
                || (this.sample != null && this.sample.pilotView() != this.pilotView);
        if (viewChanged) {
            this.stopInstances(soundManager);
        }
        if (want == Kind.SYNTH) {
            if (this.kind == Kind.SYNTH && this.synthHealthy(soundManager)) {
                return;
            }
            if (this.kind == Kind.SYNTH && !this.noteRestart(tick)) {
                this.synthRetryTick = tick + 600;
                this.stopInstances(soundManager);
                this.startSample(soundManager, tick);
                return;
            }
            MotorSampleSoundInstance runningSample = this.kind == Kind.SAMPLE && this.sample != null && soundManager.isActive(this.sample)
                    ? this.sample : null;
            if (runningSample == null) {
                this.stopInstances(soundManager);
            }
            if (this.startSynth(soundManager, tick)) {
                if (runningSample != null) {
                    runningSample.requestStop();
                    soundManager.stop(runningSample);
                    this.sample = null;
                }
                return;
            }
            if (runningSample == null) {
                this.startSample(soundManager, tick);
            }
            return;
        }
        if (this.kind == Kind.SAMPLE && this.sample != null && soundManager.isActive(this.sample)) {
            return;
        }
        this.stopInstances(soundManager);
        this.startSample(soundManager, tick);
    }

    private boolean noteRestart(long tick) {
        if (tick - this.restartWindowStart > RESTART_WINDOW_TICKS) {
            this.restartWindowStart = tick;
            this.restartCount = 0;
        }
        this.restartCount++;
        return this.restartCount <= MAX_RESTARTS;
    }

    private boolean synthHealthy(SoundManager soundManager) {
        MotorSoundInstance instance = this.synth;
        if (instance == null || !soundManager.isActive(instance)) {
            return false;
        }
        MotorStream stream = instance.stream();
        return stream != null && !stream.isClosed() && !stream.failed();
    }

    private boolean startSynth(SoundManager soundManager, long tick) {
        if (tick < this.synthRetryTick || !MotorVoices.synthAllowed(tick)) {
            return false;
        }
        MotorTimbre voiceTimbre = this.timbre;
        MotorControl voiceControl = this.control;
        long voiceSeed = this.seed;
        this.control.setActive(true);
        this.control.publish();
        MotorSoundInstance instance = new MotorSoundInstance(
                this.pilotView ? AudioSounds.MOTOR_PILOT.get() : AudioSounds.MOTOR.get(),
                () -> new MotorSynth(voiceTimbre, voiceControl, voiceSeed),
                this.pilotView, this.x, this.y, this.z, this.entityId);
        SoundEngine.PlayResult result = soundManager.play(instance);
        if (result == SoundEngine.PlayResult.NOT_STARTED) {
            instance.requestStop();
            this.synthRetryTick = tick + 100;
            return false;
        }
        if (!instance.opened()) {
            instance.requestStop();
            soundManager.stop(instance);
            MotorVoices.streamingUnavailable(tick);
            return false;
        }
        ChannelAccess.ChannelHandle channel = SoundEngineAccess.handle(soundManager, instance);
        MotorStream stream = instance.stream();
        if (channel == null || stream == null) {
            instance.requestStop();
            soundManager.stop(instance);
            this.synthRetryTick = tick + 400;
            AudioErrors.note("no-handle", "Motor-Stream ohne Kanal (Sound ersetzt?), nutze Sample-Ersatz");
            return false;
        }
        AudioPump.add(channel, stream);
        this.synth = instance;
        this.handle = channel;
        this.kind = Kind.SYNTH;
        return true;
    }

    private void startSample(SoundManager soundManager, long tick) {
        if (tick < this.sampleRetryTick) {
            this.kind = Kind.NONE;
            return;
        }
        MotorSampleSoundInstance instance = new MotorSampleSoundInstance(AudioSounds.MOTOR_SAMPLE.get(), this.pilotView, this.x, this.y, this.z);
        this.updateSample(instance);
        SoundEngine.PlayResult result = soundManager.play(instance);
        if (result == SoundEngine.PlayResult.NOT_STARTED) {
            instance.requestStop();
            this.sampleRetryTick = tick + 40;
            this.kind = Kind.NONE;
            return;
        }
        this.sample = instance;
        this.handle = SoundEngineAccess.handle(soundManager, instance);
        this.kind = Kind.SAMPLE;
    }

    void updateSample(MotorSampleSoundInstance instance) {
        double maxRpm = Math.max(1000.0, this.timbre.maxRpm());
        float sum = 0.0F;
        float gain = 0.0F;
        for (int m = 0; m < MotorControl.MOTORS; m++) {
            float rpm = this.control.rpm(m);
            sum += rpm;
            gain += (float) Math.pow(Math.min(1.0, rpm / maxRpm), 0.6) * (0.6F + 0.4F * this.control.load(m));
        }
        float bladeHz = (float) this.timbre.bladePassHz(sum / MotorControl.MOTORS) * this.doppler;
        float volume = this.releasing ? 0.0F : this.control.gain() * gain / MotorControl.MOTORS;
        instance.update(volume, bladeHz / MotorSampleSoundInstance.REFERENCE_BLADE_PASS_HZ);
    }

    void afterUpdate() {
        if (this.sample != null) {
            this.updateSample(this.sample);
        }
    }

    void frame(Vec3 position, Vec3 listener) {
        if (this.pilotView || this.kind == Kind.NONE) {
            return;
        }
        if (this.synth != null && !this.releasing) {
            float gain = (float) SoundReach.attenuation(position.distanceTo(listener), this.range);
            if (gain != this.distanceGain) {
                this.distanceGain = gain;
                this.control.setGain(this.baseGain * gain);
                this.control.publish();
            }
        }
        if (this.synth != null) {
            this.synth.moveTo(position.x, position.y, position.z);
        }
        if (this.sample != null) {
            this.sample.moveTo(position.x, position.y, position.z);
        }
        ChannelAccess.ChannelHandle channel = this.handle;
        if (channel != null && !channel.isStopped()) {
            channel.execute(ch -> ch.setSelfPosition(position));
        }
    }

    void release() {
        if (this.kind == Kind.NONE) {
            this.releasing = false;
            return;
        }
        if (!this.releasing) {
            this.releasing = true;
            this.control.setActive(false);
            this.control.publish();
            if (this.sample != null) {
                this.updateSample(this.sample);
            }
            return;
        }
        this.stopInstances(null);
        this.releasing = false;
    }

    void stopInstances(@Nullable SoundManager soundManager) {
        if (this.synth != null) {
            this.synth.requestStop();
            if (soundManager != null) {
                soundManager.stop(this.synth);
            }
        }
        if (this.sample != null) {
            this.sample.requestStop();
            if (soundManager != null) {
                soundManager.stop(this.sample);
            }
        }
        if (this.handle != null) {
            AudioPump.remove(this.handle);
        }
        this.synth = null;
        this.sample = null;
        this.handle = null;
        this.kind = Kind.NONE;
    }

    void onStopCommand(SoundManager soundManager, @Nullable Identifier sound, @Nullable SoundSource source) {
        if (source != null && source != SoundSource.PLAYERS) {
            return;
        }
        if (sound != null && !matches(this.synth, sound) && !matches(this.sample, sound)) {
            return;
        }
        if (this.kind == Kind.NONE && this.synth == null && this.sample == null) {
            return;
        }
        this.stopInstances(soundManager);
        this.releasing = false;
        this.silenced = true;
    }

    boolean silenced() {
        return this.silenced;
    }

    private static boolean matches(@Nullable SoundInstance instance, Identifier sound) {
        return instance != null && sound.equals(instance.getIdentifier());
    }

    @Nullable MotorSoundInstance synthInstance() {
        return this.synth;
    }

    @Nullable MotorSampleSoundInstance sampleInstance() {
        return this.sample;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
