package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.AudioSounds;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.Arrays;

public final class BeepPlayer {
    private static final long MILLIS = 1_000_000L;
    private static final long[] LAST_PLAYED = new long[Beep.values().length];
    private static final int[] PLAYED = new int[Beep.values().length];

    private BeepPlayer() {
    }

    static boolean play(Beep beep) {
        return play(beep, AudioConfig.get().beepVolume);
    }

    static boolean play(Beep beep, double beepVolume) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        int index = beep.ordinal();
        long now = System.nanoTime();
        long last = LAST_PLAYED[index];
        if (last != 0L && now - last < mergeWindowNanos(beep)) {
            return false;
        }
        RegHolder<SoundEvent> holder = event(beep);
        if (!holder.isBound()) {
            return false;
        }
        float volume = (float) (beepVolume * baseVolume(beep));
        LAST_PLAYED[index] = now;
        PLAYED[index]++;
        if (volume <= 0.001F) {
            return true;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(holder.get(), 1.0F, Math.min(1.0F, volume)));
        return true;
    }

    public static int playedCount(Beep beep) {
        return PLAYED[beep.ordinal()];
    }

    static void resetMerging() {
        Arrays.fill(LAST_PLAYED, 0L);
    }

    public static RegHolder<SoundEvent> event(Beep beep) {
        return switch (beep) {
            case ARM -> AudioSounds.BEEP_ARM;
            case DISARM -> AudioSounds.BEEP_DISARM;
            case LOW_BATTERY -> AudioSounds.BEEP_LOW_BATTERY;
            case GATE -> AudioSounds.BEEP_GATE;
            case LAP -> AudioSounds.BEEP_LAP;
            case COUNTDOWN -> AudioSounds.BEEP_COUNTDOWN;
            case GO -> AudioSounds.BEEP_GO;
            case FINISH -> AudioSounds.BEEP_FINISH;
            case CRASH -> AudioSounds.CRASH;
        };
    }

    static long mergeWindowNanos(Beep beep) {
        return switch (beep) {
            case ARM, DISARM -> 250L * MILLIS;
            case LOW_BATTERY -> 400L * MILLIS;
            case GATE -> 120L * MILLIS;
            case LAP -> 350L * MILLIS;
            case COUNTDOWN, GO -> 400L * MILLIS;
            case FINISH -> 1_200L * MILLIS;
            case CRASH -> 300L * MILLIS;
        };
    }

    static double baseVolume(Beep beep) {
        return switch (beep) {
            case ARM, DISARM -> 0.7;
            case LOW_BATTERY -> 0.8;
            case GATE -> 0.65;
            case LAP, COUNTDOWN, GO -> 0.85;
            case FINISH -> 0.9;
            case CRASH -> 1.0;
        };
    }
}
