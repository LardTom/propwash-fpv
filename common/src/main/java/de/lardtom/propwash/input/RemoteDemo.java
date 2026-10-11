package de.lardtom.propwash.input;

import java.util.Locale;

public final class RemoteDemo {
    public static final int SWITCHES = 4;
    public static final int BUTTONS = 3;
    public static final int ARM_SWITCH = 0;
    public static final int MODE_SWITCH = 1;
    public static final int MOMENTARY_BUTTON = 0;
    public static final int TOGGLE_BUTTON = 2;
    public static final float SWITCH_OFF = -1.0F;
    public static final float SWITCH_MID = 0.0F;
    public static final float SWITCH_ON = 1.0F;
    public static final float THROTTLE_HOLD_S = ThrottleDetector.HOLD_NANOS / 1.0E9F;
    private static final float FLIP_S = 0.15F;

    public enum Motion {
        THROTTLE(5.6F),
        ROLL(3.0F),
        PITCH(3.0F),
        YAW(3.0F),
        CHECK(8.4F),
        CENTER(4.4F),
        CENTER_3D(4.8F),
        EXTREMES(6.4F),
        REVIEW(6.0F),
        SWITCH(3.2F),
        SWITCH_MID(3.2F),
        BUTTON(2.6F),
        PAD_TOGGLE(2.6F),
        ARM_AND_MODE(8.0F),
        PAD_ARM_AND_MODE(6.0F);

        private final float period;

        Motion(float period) {
            this.period = period;
        }

        public float period() {
            return this.period;
        }
    }

    public enum Focus {
        NONE,
        LEFT_STICK,
        RIGHT_STICK,
        BOTH_STICKS,
        SWITCH,
        BUTTON
    }

    public enum Arrow {
        NONE,
        UP,
        DOWN,
        RIGHT,
        CIRCLE,
        FLIP,
        PRESS
    }

    public enum Cue {
        NONE,
        THROTTLE_UP,
        THROTTLE_DOWN,
        THROTTLE_HOLD,
        DETECTED,
        STICK_RIGHT,
        STICK_FORWARD,
        HOLD_BRIEFLY,
        LET_GO,
        CHECK_THROTTLE,
        CHECK_ROLL,
        CHECK_PITCH,
        CHECK_YAW,
        CENTER,
        CENTER_3D,
        CIRCLES,
        REVIEW_LOW,
        REVIEW_MID,
        REVIEW_HIGH,
        FLIP,
        FLIP_MID,
        KEEP,
        PRESS,
        PRESS_TOGGLE,
        ARM,
        ARM_PAD,
        MODE_ACRO,
        MODE_HORIZON,
        MODE_ANGLE,
        MODE_PAD;

        public String translationKey() {
            return "propwash.input.demo." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record Frame(float leftX, float leftY, float rightX, float rightY, float switch0, float switch1, float switch2, float switch3,
                        float button0, float button1, float button2, Focus focus, int target, Arrow arrow, Cue cue,
                        StickChannel channel, float hold) {
        public float throttle() {
            return (this.leftY + 1.0F) * 0.5F;
        }

        public float switchPosition(int index) {
            return switch (index) {
                case 0 -> this.switch0;
                case 1 -> this.switch1;
                case 2 -> this.switch2;
                case 3 -> this.switch3;
                default -> SWITCH_OFF;
            };
        }

        public float buttonPress(int index) {
            return switch (index) {
                case 0 -> this.button0;
                case 1 -> this.button1;
                case 2 -> this.button2;
                default -> 0.0F;
            };
        }

        public boolean holding() {
            return !Float.isNaN(this.hold);
        }
    }

    public record Target(Motion motion, int index) {
        public Target {
            index = Math.max(0, index);
        }
    }

    private RemoteDemo() {
    }

    public static Target forSwitch(SwitchFunction function, boolean gamepad) {
        if (gamepad) {
            return function.latchesOnGamepad() ? new Target(Motion.PAD_TOGGLE, TOGGLE_BUTTON) : new Target(Motion.BUTTON, MOMENTARY_BUTTON);
        }
        return switch (function) {
            case ARM -> new Target(Motion.SWITCH, ARM_SWITCH);
            case ANGLE -> new Target(Motion.SWITCH, MODE_SWITCH);
            case HORIZON, POS_HOLD -> new Target(Motion.SWITCH_MID, MODE_SWITCH);
            case BEEPER, RECORD, ALT_HOLD, BLACKBOX -> new Target(Motion.SWITCH, 2);
            case RESCUE, LEDS_OFF, MODE_3D -> new Target(Motion.SWITCH, 3);
            case FLIP_OVER, SET_HOME, RACE_RESTART, CARGO_DROP -> new Target(Motion.BUTTON, MOMENTARY_BUTTON);
            case COMMAND_1, COMMAND_2, COMMAND_3, COMMAND_4, COMMAND_5, COMMAND_6, COMMAND_7, COMMAND_8 -> new Target(Motion.SWITCH, 2);
        };
    }

    public static Motion forChannel(StickChannel channel) {
        return switch (channel) {
            case ROLL -> Motion.ROLL;
            case PITCH -> Motion.PITCH;
            case YAW -> Motion.YAW;
            case THROTTLE -> Motion.THROTTLE;
        };
    }

    public static Frame frame(Motion motion, float seconds) {
        return frame(new Target(motion, 0), seconds);
    }

    public static Frame frame(Target target, float seconds) {
        float period = target.motion().period();
        float t = seconds % period;
        if (t < 0.0F || !Float.isFinite(t)) {
            t = 0.0F;
        }
        Builder b = new Builder();
        switch (target.motion()) {
            case THROTTLE -> throttle(b, t);
            case ROLL -> stick(b, t, StickChannel.ROLL);
            case PITCH -> stick(b, t, StickChannel.PITCH);
            case YAW -> stick(b, t, StickChannel.YAW);
            case CHECK -> check(b, t);
            case CENTER -> center(b, t, false);
            case CENTER_3D -> center(b, t, true);
            case EXTREMES -> extremes(b, t, period);
            case REVIEW -> review(b, t);
            case SWITCH -> flip(b, t, target.index(), SWITCH_ON, Cue.FLIP);
            case SWITCH_MID -> flip(b, t, target.index(), SWITCH_MID, Cue.FLIP_MID);
            case BUTTON -> press(b, t, target.index(), 0.6F, 1.6F, Cue.PRESS);
            case PAD_TOGGLE -> press(b, t, target.index(), 0.6F, 0.85F, Cue.PRESS_TOGGLE);
            case ARM_AND_MODE -> armAndMode(b, t);
            case PAD_ARM_AND_MODE -> padArmAndMode(b, t);
        }
        return b.build();
    }

    static float progress(float t, float from, float to) {
        if (to <= from) {
            return t >= to ? 1.0F : 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (t - from) / (to - from)));
    }

    static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    static float ease(float t, float from, float to, float a, float b) {
        return a + (b - a) * smooth(progress(t, from, to));
    }

    private static void throttle(Builder b, float t) {
        b.focus = Focus.LEFT_STICK;
        b.channel = StickChannel.THROTTLE;
        float downEnd = 2.0F;
        float holdStart = 1.87F;
        float holdEnd = holdStart + THROTTLE_HOLD_S;
        if (t < 1.5F) {
            b.leftY = ease(t, 0.5F, 1.1F, -1.0F, 1.0F);
            b.arrow = Arrow.UP;
            b.cue = Cue.THROTTLE_UP;
        } else if (t < holdStart) {
            b.leftY = ease(t, 1.5F, downEnd, 1.0F, -1.0F);
            b.arrow = Arrow.DOWN;
            b.cue = Cue.THROTTLE_DOWN;
        } else if (t < holdEnd) {
            b.leftY = ease(t, 1.5F, downEnd, 1.0F, -1.0F);
            b.cue = Cue.THROTTLE_HOLD;
            b.hold = progress(t, holdStart, holdEnd);
        } else if (t < holdEnd + 1.2F) {
            b.leftY = -1.0F;
            b.cue = Cue.DETECTED;
        } else {
            b.leftY = -1.0F;
        }
    }

    private static void stick(Builder b, float t, StickChannel channel) {
        b.channel = channel;
        float value;
        if (t < 0.75F) {
            value = ease(t, 0.4F, 0.75F, 0.0F, 1.0F);
            b.arrow = channel == StickChannel.PITCH ? Arrow.UP : Arrow.RIGHT;
            b.cue = channel == StickChannel.PITCH ? Cue.STICK_FORWARD : Cue.STICK_RIGHT;
        } else if (t < 1.35F) {
            value = 1.0F;
            b.cue = Cue.HOLD_BRIEFLY;
            b.hold = progress(t, 0.75F, 1.35F);
        } else if (t < 1.85F) {
            value = 1.0F;
            b.cue = Cue.DETECTED;
        } else {
            value = ease(t, 1.85F, 2.2F, 1.0F, 0.0F);
            b.cue = t < 2.4F ? Cue.LET_GO : Cue.NONE;
        }
        b.leftY = -1.0F;
        switch (channel) {
            case ROLL -> {
                b.rightX = value;
                b.focus = Focus.RIGHT_STICK;
            }
            case PITCH -> {
                b.rightY = value;
                b.focus = Focus.RIGHT_STICK;
            }
            case YAW -> {
                b.leftX = value;
                b.focus = Focus.LEFT_STICK;
            }
            case THROTTLE -> b.leftY = value * 2.0F - 1.0F;
        }
    }

    private static void check(Builder b, float t) {
        b.leftY = -1.0F;
        if (t < 2.4F) {
            b.channel = StickChannel.THROTTLE;
            b.focus = Focus.LEFT_STICK;
            b.cue = Cue.CHECK_THROTTLE;
            b.leftY = t < 1.2F ? ease(t, 0.3F, 0.9F, -1.0F, 1.0F) : ease(t, 1.5F, 2.1F, 1.0F, -1.0F);
            b.arrow = t < 1.2F ? Arrow.UP : Arrow.DOWN;
        } else if (t < 4.0F) {
            b.channel = StickChannel.ROLL;
            b.focus = Focus.RIGHT_STICK;
            b.cue = Cue.CHECK_ROLL;
            b.arrow = Arrow.RIGHT;
            b.rightX = t < 3.2F ? ease(t, 2.6F, 2.9F, 0.0F, 1.0F) : ease(t, 3.5F, 3.8F, 1.0F, 0.0F);
        } else if (t < 5.6F) {
            b.channel = StickChannel.PITCH;
            b.focus = Focus.RIGHT_STICK;
            b.cue = Cue.CHECK_PITCH;
            b.arrow = Arrow.UP;
            b.rightY = t < 4.8F ? ease(t, 4.2F, 4.5F, 0.0F, 1.0F) : ease(t, 5.1F, 5.4F, 1.0F, 0.0F);
        } else if (t < 7.2F) {
            b.channel = StickChannel.YAW;
            b.focus = Focus.LEFT_STICK;
            b.cue = Cue.CHECK_YAW;
            b.arrow = Arrow.RIGHT;
            b.leftX = t < 6.4F ? ease(t, 5.8F, 6.1F, 0.0F, 1.0F) : ease(t, 6.7F, 7.0F, 1.0F, 0.0F);
        }
    }

    private static void center(Builder b, float t, boolean threeD) {
        float startRightX = 0.7F;
        float startRightY = -0.55F;
        float startYaw = -0.45F;
        float back = threeD ? 4.2F : 3.8F;
        float release = ease(t, 0.4F, 0.9F, 1.0F, 0.0F);
        float restore = progress(t, back, back + 0.5F);
        float off = Math.max(release, smooth(restore));
        b.rightX = startRightX * off;
        b.rightY = startRightY * off;
        b.leftX = startYaw * off;
        b.channel = StickChannel.THROTTLE;
        if (t < 0.9F) {
            b.leftY = -1.0F;
            b.focus = Focus.BOTH_STICKS;
            b.cue = Cue.LET_GO;
        } else if (t < back) {
            if (threeD) {
                if (t < 1.5F) {
                    b.leftY = ease(t, 0.9F, 1.5F, -1.0F, 0.25F);
                } else if (t < 1.9F) {
                    b.leftY = ease(t, 1.5F, 1.9F, 0.25F, -0.12F);
                } else {
                    b.leftY = ease(t, 1.9F, 2.3F, -0.12F, 0.0F);
                }
                b.cue = Cue.CENTER_3D;
            } else {
                b.leftY = ease(t, 0.9F, 1.6F, -1.0F, 0.0F);
                b.cue = Cue.CENTER;
            }
            b.focus = Focus.LEFT_STICK;
            b.arrow = t < 1.6F ? Arrow.UP : Arrow.NONE;
        } else {
            b.leftY = ease(t, back, back + 0.5F, 0.0F, -1.0F);
        }
    }

    private static void extremes(Builder b, float t, float period) {
        double angle = 2.0 * Math.PI * t / (period * 0.5F);
        b.leftX = (float) Math.sin(angle);
        b.leftY = (float) Math.cos(angle);
        b.rightX = (float) -Math.sin(angle);
        b.rightY = (float) Math.cos(angle);
        b.focus = Focus.BOTH_STICKS;
        b.arrow = Arrow.CIRCLE;
        b.cue = Cue.CIRCLES;
    }

    private static void review(Builder b, float t) {
        float value;
        if (t < 1.2F) {
            value = -1.0F;
            b.cue = Cue.REVIEW_LOW;
        } else if (t < 1.5F) {
            value = ease(t, 1.2F, 1.5F, -1.0F, 0.0F);
            b.cue = Cue.REVIEW_MID;
        } else if (t < 2.7F) {
            value = 0.0F;
            b.cue = Cue.REVIEW_MID;
        } else if (t < 3.0F) {
            value = ease(t, 2.7F, 3.0F, 0.0F, 1.0F);
            b.cue = Cue.REVIEW_HIGH;
        } else if (t < 4.2F) {
            value = 1.0F;
            b.cue = Cue.REVIEW_HIGH;
        } else {
            value = ease(t, 4.5F, 5.4F, 1.0F, -1.0F);
        }
        b.leftY = value;
        b.rightX = value;
        b.focus = Focus.BOTH_STICKS;
    }

    private static void flip(Builder b, float t, int index, float on, Cue cue) {
        b.leftY = -1.0F;
        b.focus = Focus.SWITCH;
        b.target = index;
        float position;
        if (t < 0.7F) {
            position = SWITCH_OFF;
            b.cue = cue;
        } else if (t < 0.7F + FLIP_S) {
            position = ease(t, 0.7F, 0.7F + FLIP_S, SWITCH_OFF, on);
            b.cue = cue;
            b.arrow = Arrow.FLIP;
        } else if (t < 2.3F) {
            position = on;
            b.cue = Cue.KEEP;
        } else {
            position = ease(t, 2.3F, 2.3F + FLIP_S, on, SWITCH_OFF);
            b.cue = Cue.NONE;
        }
        b.setSwitch(index, position);
    }

    private static void press(Builder b, float t, int index, float down, float up, Cue cue) {
        b.leftY = -1.0F;
        b.focus = Focus.BUTTON;
        b.target = index;
        b.cue = cue;
        float press = 0.0F;
        if (t >= down && t < up) {
            press = Math.min(1.0F, (t - down) / 0.08F);
            b.arrow = Arrow.PRESS;
        } else if (t >= up && t < up + 0.08F) {
            press = 1.0F - (t - up) / 0.08F;
        }
        b.setButton(index, press);
    }

    private static void armAndMode(Builder b, float t) {
        b.leftY = -1.0F;
        b.focus = Focus.SWITCH;
        if (t < 3.0F) {
            b.target = ARM_SWITCH;
            b.cue = Cue.ARM;
            float position = t < 0.6F ? SWITCH_OFF : t < 2.4F ? ease(t, 0.6F, 0.6F + FLIP_S, SWITCH_OFF, SWITCH_ON)
                    : ease(t, 2.4F, 2.4F + FLIP_S, SWITCH_ON, SWITCH_OFF);
            if (t >= 0.6F && t < 0.6F + FLIP_S) {
                b.arrow = Arrow.FLIP;
            }
            b.setSwitch(ARM_SWITCH, position);
            return;
        }
        b.target = MODE_SWITCH;
        float position;
        if (t < 3.6F) {
            position = SWITCH_OFF;
            b.cue = Cue.MODE_ACRO;
        } else if (t < 5.0F) {
            position = ease(t, 3.6F, 3.6F + FLIP_S, SWITCH_OFF, SWITCH_MID);
            b.cue = Cue.MODE_HORIZON;
        } else if (t < 6.4F) {
            position = ease(t, 5.0F, 5.0F + FLIP_S, SWITCH_MID, SWITCH_ON);
            b.cue = Cue.MODE_ANGLE;
        } else {
            position = ease(t, 6.4F, 6.4F + 2.0F * FLIP_S, SWITCH_ON, SWITCH_OFF);
            b.cue = Cue.MODE_ACRO;
        }
        if (t >= 3.6F && t < 3.6F + FLIP_S || t >= 5.0F && t < 5.0F + FLIP_S || t >= 6.4F && t < 6.4F + 2.0F * FLIP_S) {
            b.arrow = Arrow.FLIP;
        }
        b.setSwitch(MODE_SWITCH, position);
    }

    private static void padArmAndMode(Builder b, float t) {
        if (t < 3.0F) {
            press(b, t, TOGGLE_BUTTON, 0.6F, 0.85F, Cue.ARM_PAD);
            return;
        }
        press(b, t - 3.0F, 1, 0.6F, 0.85F, Cue.MODE_PAD);
    }

    private static final class Builder {
        float leftX;
        float leftY = -1.0F;
        float rightX;
        float rightY;
        final float[] switches = {SWITCH_OFF, SWITCH_OFF, SWITCH_OFF, SWITCH_OFF};
        final float[] buttons = new float[BUTTONS];
        Focus focus = Focus.NONE;
        int target;
        Arrow arrow = Arrow.NONE;
        Cue cue = Cue.NONE;
        StickChannel channel;
        float hold = Float.NaN;

        void setSwitch(int index, float value) {
            if (index >= 0 && index < SWITCHES) {
                this.switches[index] = value;
            }
        }

        void setButton(int index, float value) {
            if (index >= 0 && index < BUTTONS) {
                this.buttons[index] = Math.max(0.0F, Math.min(1.0F, value));
            }
        }

        Frame build() {
            return new Frame(clamp(this.leftX), clamp(this.leftY), clamp(this.rightX), clamp(this.rightY), this.switches[0], this.switches[1],
                    this.switches[2], this.switches[3], this.buttons[0], this.buttons[1], this.buttons[2], this.focus, this.target, this.arrow,
                    this.cue, this.channel, this.hold);
        }

        private static float clamp(float value) {
            return Math.max(-1.0F, Math.min(1.0F, value));
        }
    }
}
