package de.lardtom.propwash.rx.client;

import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rx.RxLearn;
import de.lardtom.propwash.rx.RxSource;
import org.jspecify.annotations.Nullable;

public final class RxRadio {
    private RxRadio() {
    }

    public record Frame(float[] sticks, float[] axes, boolean[] buttons) {
        public float value(RxSource source) {
            return switch (source.kind()) {
                case STICK -> this.sticks[source.index()];
                case AXIS -> source.index() < this.axes.length ? this.axes[source.index()] : Float.NaN;
                case BUTTON -> source.index() < this.buttons.length ? (this.buttons[source.index()] ? 1.0F : 0.0F) : Float.NaN;
            };
        }

        public int pressed() {
            int bits = 0;
            for (int i = 0; i < Math.min(RxSource.BUTTONS, this.buttons.length); i++) {
                if (this.buttons[i]) {
                    bits |= 1 << i;
                }
            }
            return bits;
        }

        public RxLearn.Sample sample(double time) {
            return new RxLearn.Sample(time, this.sticks, this.axes, this.buttons);
        }
    }

    public static boolean flying() {
        return PilotSession.INSTANCE.isActive();
    }

    public static @Nullable Frame current() {
        RadioState radio = RadioState.INSTANCE;
        if (!radio.connected()) {
            return null;
        }
        if (radio.source() == RadioSource.KEYBOARD && !flying()) {
            return null;
        }
        StickPositions sticks = radio.sticks();
        float[] values = {sticks.roll(), sticks.pitch(), sticks.yaw(), throttle(sticks.throttle())};
        return new Frame(values, radio.rawAxes(), radio.rawButtons());
    }

    public static float throttle(float throttle) {
        return Math.clamp(throttle * 2.0F - 1.0F, -1.0F, 1.0F);
    }
}
