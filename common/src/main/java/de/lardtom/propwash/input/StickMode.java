package de.lardtom.propwash.input;

public enum StickMode {
    MODE_1(1, false, true),
    MODE_2(2, true, true),
    MODE_3(3, false, false),
    MODE_4(4, true, false);

    public static final StickMode DEFAULT = MODE_2;

    private final int number;
    private final boolean throttleLeft;
    private final boolean yawLeft;

    StickMode(int number, boolean throttleLeft, boolean yawLeft) {
        this.number = number;
        this.throttleLeft = throttleLeft;
        this.yawLeft = yawLeft;
    }

    public int number() {
        return this.number;
    }

    public static StickMode byNumber(int number) {
        for (StickMode mode : values()) {
            if (mode.number == number) {
                return mode;
            }
        }
        return DEFAULT;
    }

    public static StickMode orDefault(StickMode mode) {
        return mode == null ? DEFAULT : mode;
    }

    public static boolean vertical(StickChannel channel) {
        return channel == StickChannel.THROTTLE || channel == StickChannel.PITCH;
    }

    public boolean left(StickChannel channel) {
        return switch (channel) {
            case THROTTLE -> this.throttleLeft;
            case PITCH -> !this.throttleLeft;
            case YAW -> this.yawLeft;
            case ROLL -> !this.yawLeft;
        };
    }

    public StickChannel channel(boolean left, boolean vertical) {
        if (vertical) {
            return left == this.throttleLeft ? StickChannel.THROTTLE : StickChannel.PITCH;
        }
        return left == this.yawLeft ? StickChannel.YAW : StickChannel.ROLL;
    }

    public boolean sameSide(StickChannel channel, StickMode other) {
        return this.left(channel) == other.left(channel);
    }

    public Sticks place(float roll, float pitch, float yaw, float throttle) {
        float leftX = this.yawLeft ? yaw : roll;
        float rightX = this.yawLeft ? roll : yaw;
        float leftY = this.throttleLeft ? throttle : pitch;
        float rightY = this.throttleLeft ? pitch : throttle;
        return new Sticks(leftX, leftY, rightX, rightY);
    }

    public Sticks fromMode2(float leftX, float leftY, float rightX, float rightY) {
        return this.place(rightX, rightY, leftX, leftY);
    }

    public String translationKey() {
        return "propwash.input.stick_mode.mode_" + this.number;
    }

    public String layoutKey() {
        return this.translationKey() + ".layout";
    }

    public String variantKey(String base) {
        return this == MODE_2 ? base : base + ".mode" + this.number;
    }

    public String sideKey(String base, StickChannel channel) {
        return base + (this.left(channel) ? ".left" : ".right");
    }

    public record Sticks(float leftX, float leftY, float rightX, float rightY) {
    }
}
