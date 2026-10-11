package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.AudioSounds;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.UUID;

public final class LocatorBeeper {
    public static final int INTERVAL_TICKS = 24;

    private static final float VOLUME = 1.6F;
    private static long nextBeepTick;
    private static int beeps;

    private LocatorBeeper() {
    }

    static void tick(Minecraft minecraft, Iterable<DroneVoice> voices, long tick) {
        if (tick < nextBeepTick || minecraft.player == null || !AudioSounds.LOCATOR.isBound()) {
            return;
        }
        if (!RadioState.INSTANCE.isActive(SwitchFunction.BEEPER)) {
            return;
        }
        UUID self = minecraft.player.getUUID();
        QuadcopterEntity piloted = PilotSession.INSTANCE.isActive() ? PilotSession.INSTANCE.drone() : null;
        float volume = (float) (AudioConfig.get().beepVolume * VOLUME);
        boolean played = false;
        for (DroneVoice voice : voices) {
            QuadcopterEntity drone = voice.drone;
            if (drone.isArmed()) {
                continue;
            }
            boolean mine = drone == piloted || drone.pilot().map(self::equals).orElse(false);
            if (!mine) {
                continue;
            }
            if (volume > 0.001F) {
                minecraft.getSoundManager().play(new SimpleSoundInstance(AudioSounds.LOCATOR.get(), SoundSource.PLAYERS, volume, 1.0F,
                        RandomSource.create(), drone.getX(), drone.getY(), drone.getZ()));
            }
            played = true;
        }
        if (played) {
            beeps++;
            nextBeepTick = tick + INTERVAL_TICKS;
        }
    }

    public static int beeps() {
        return beeps;
    }

    static void reset() {
        nextBeepTick = 0L;
    }
}
