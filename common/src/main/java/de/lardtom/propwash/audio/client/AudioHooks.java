package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

public final class AudioHooks {
    private AudioHooks() {
    }

    public static void onDroneClientTick(QuadcopterEntity drone) {
        if (drone == null) {
            return;
        }
        try {
            MotorVoices.onDroneTick(drone);
        } catch (RuntimeException | LinkageError e) {
            AudioErrors.report("drone tick", e);
        }
    }

    public static void beep(Beep beep) {
        if (beep == null) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return;
            }
            if (!minecraft.isSameThread()) {
                minecraft.execute(() -> beep(beep));
                return;
            }
            BeepPlayer.play(beep);
        } catch (RuntimeException | LinkageError e) {
            AudioErrors.report("beep", e);
        }
    }

    public static void onStopCommand(@Nullable Identifier sound, @Nullable SoundSource source) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return;
            }
            if (!minecraft.isSameThread()) {
                minecraft.execute(() -> onStopCommand(sound, source));
                return;
            }
            MotorVoices.onStopCommand(minecraft.getSoundManager(), sound, source);
        } catch (RuntimeException | LinkageError e) {
            AudioErrors.report("stop command", e);
        }
    }

    static void preview(Beep beep, double beepVolume) {
        try {
            BeepPlayer.play(beep, beepVolume);
        } catch (RuntimeException | LinkageError e) {
            AudioErrors.report("beep", e);
        }
    }
}
