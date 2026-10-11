package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.MotorSynth;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MotorSoundInstance extends AbstractTickableSoundInstance {
    private final Supplier<MotorSynth> synthFactory;
    private final boolean pilotView;
    private final int sourceId;
    private volatile @Nullable MotorStream stream;
    private volatile boolean opened;
    private boolean stopRequested;

    public MotorSoundInstance(SoundEvent event, Supplier<MotorSynth> synthFactory, boolean pilotView, double x, double y, double z) {
        this(event, synthFactory, pilotView, x, y, z, -1);
    }

    public MotorSoundInstance(SoundEvent event, Supplier<MotorSynth> synthFactory, boolean pilotView, double x, double y, double z,
                              int sourceId) {
        super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.sourceId = sourceId;
        this.synthFactory = synthFactory;
        this.pilotView = pilotView;
        this.looping = true;
        this.delay = 0;
        this.pitch = 1.0F;
        this.volume = 1.0F;
        this.relative = pilotView;
        this.attenuation = SoundInstance.Attenuation.NONE;
        if (pilotView) {
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
        } else {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public boolean pilotView() {
        return this.pilotView;
    }

    public boolean opened() {
        return this.opened;
    }

    public @Nullable MotorStream stream() {
        return this.stream;
    }

    public void moveTo(double x, double y, double z) {
        if (!this.pilotView) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public void setAudible(boolean audible) {
        this.volume = audible ? 1.0F : 0.0F;
    }

    public void requestStop() {
        this.stopRequested = true;
        MotorStream s = this.stream;
        if (s != null) {
            s.release();
        }
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

    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return this.open();
    }

    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library, Identifier id, boolean repeatInstantly) {
        return this.open();
    }

    private CompletableFuture<AudioStream> open() {
        MotorStream created = new MotorStream(this.synthFactory.get(), this.sourceId);
        if (this.stopRequested) {
            created.release();
        }
        this.stream = created;
        this.opened = true;
        return CompletableFuture.completedFuture(created);
    }
}
