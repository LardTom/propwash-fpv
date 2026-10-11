package de.lardtom.propwash.audio.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class MotorSampleSoundInstance extends AbstractTickableSoundInstance {
    public static final float REFERENCE_BLADE_PASS_HZ = 600.0F;

    private final boolean pilotView;
    private boolean stopRequested;

    public MotorSampleSoundInstance(SoundEvent event, boolean pilotView, double x, double y, double z) {
        super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.pilotView = pilotView;
        this.looping = true;
        this.delay = 0;
        this.pitch = 1.0F;
        this.volume = 0.0F;
        this.relative = pilotView;
        this.attenuation = SoundInstance.Attenuation.NONE;
        if (!pilotView) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public boolean pilotView() {
        return this.pilotView;
    }

    public void update(float volume, float pitch) {
        this.volume = Float.isFinite(volume) ? Math.max(0.0F, Math.min(1.0F, volume)) : 0.0F;
        this.pitch = Float.isFinite(pitch) ? Math.max(0.5F, Math.min(2.0F, pitch)) : 1.0F;
    }

    public void moveTo(double x, double y, double z) {
        if (!this.pilotView) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public void requestStop() {
        this.stopRequested = true;
    }

    public boolean stopRequested() {
        return this.stopRequested;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return !this.stopRequested;
    }

    @Override
    public void tick() {
        if (this.stopRequested) {
            this.stop();
        }
    }
}
