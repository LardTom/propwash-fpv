package de.lardtom.propwash.audio;

import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.sounds.SoundEvent;

public final class AudioSounds {
    public static final RegHolder<SoundEvent> MOTOR = PropwashRegistries.sound("drone.motor");
    public static final RegHolder<SoundEvent> MOTOR_PILOT = PropwashRegistries.sound("drone.motor_pilot");
    public static final RegHolder<SoundEvent> MOTOR_SAMPLE = PropwashRegistries.sound("drone.motor_sample");
    public static final RegHolder<SoundEvent> LOCATOR = PropwashRegistries.sound("drone.locator");
    public static final RegHolder<SoundEvent> CRASH = PropwashRegistries.sound("drone.crash");
    public static final RegHolder<SoundEvent> BEEP_ARM = PropwashRegistries.sound("beep.arm");
    public static final RegHolder<SoundEvent> BEEP_DISARM = PropwashRegistries.sound("beep.disarm");
    public static final RegHolder<SoundEvent> BEEP_LOW_BATTERY = PropwashRegistries.sound("beep.low_battery");
    public static final RegHolder<SoundEvent> BEEP_GATE = PropwashRegistries.sound("beep.gate");
    public static final RegHolder<SoundEvent> BEEP_LAP = PropwashRegistries.sound("beep.lap");
    public static final RegHolder<SoundEvent> BEEP_COUNTDOWN = PropwashRegistries.sound("beep.countdown");
    public static final RegHolder<SoundEvent> BEEP_GO = PropwashRegistries.sound("beep.go");
    public static final RegHolder<SoundEvent> BEEP_FINISH = PropwashRegistries.sound("beep.finish");

    private AudioSounds() {
    }

    static void bootstrap() {
    }
}
