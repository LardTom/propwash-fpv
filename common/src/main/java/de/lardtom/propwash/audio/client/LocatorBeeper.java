package de.lardtom.propwash.audio.client;

import de.lardtom.propwash.audio.AudioSounds;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.net.BeeperOffC2S;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.network.client.ClientNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class LocatorBeeper {
    public static final int INTERVAL_TICKS = 24;

    private static final float VOLUME = 1.6F;
    private static long nextBeepTick;
    private static int beeps;
    private static boolean switchWasOn;

    private LocatorBeeper() {
    }

    static void tick(Minecraft minecraft, Iterable<DroneVoice> voices, long tick) {
        if (tick < nextBeepTick || minecraft.player == null || !AudioSounds.LOCATOR.isBound()) {
            return;
        }
        QuadcopterEntity piloted = PilotSession.INSTANCE.isActive() ? PilotSession.INSTANCE.drone() : null;
        boolean radioBeeper = RadioState.INSTANCE.isActive(SwitchFunction.BEEPER);
        float volume = (float) (AudioConfig.get().beepVolume * VOLUME);
        boolean played = false;
        for (DroneVoice voice : voices) {
            QuadcopterEntity drone = voice.drone;
            if (!sounds(drone, drone == piloted ? radioBeeper : drone.beeping())) {
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

    public static boolean sounds(QuadcopterEntity drone, boolean switchedOn) {
        return switchedOn && !drone.isArmed() && drone.hasBattery() && drone.batteryCharge() > 0.0F;
    }

    static void onTickEnd(Minecraft minecraft) {
        boolean on = RadioState.INSTANCE.isActive(SwitchFunction.BEEPER);
        boolean wasOn = switchWasOn;
        switchWasOn = on;
        if (wasOn && !on && !PilotSession.INSTANCE.isActive()) {
            switchOffOwn(minecraft);
        }
    }

    public static int switchOffOwn(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return 0;
        }
        UUID self = minecraft.player.getUUID();
        int sent = 0;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof QuadcopterEntity drone && drone.beeperBy().filter(self::equals).isPresent()) {
                ClientNet.sendToServer(new BeeperOffC2S(drone.getId()));
                sent++;
            }
        }
        return sent;
    }

    public static @Nullable QuadcopterEntity ownBeeping(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        UUID self = minecraft.player.getUUID();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof QuadcopterEntity drone && drone.beeperBy().filter(self::equals).isPresent()) {
                return drone;
            }
        }
        return null;
    }

    public static int beeps() {
        return beeps;
    }

    static void reset() {
        nextBeepTick = 0L;
    }
}
