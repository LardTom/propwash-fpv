package de.lardtom.propwash.trick.tutorial;

import java.util.Objects;

public sealed interface TrickMove {
    String stage();

    TrickMove mirrored();

    default TrickMove backwards() {
        return this;
    }

    record Level(String stage, double seconds) implements TrickMove {
        public Level {
            Objects.requireNonNull(stage, "stage");
        }

        @Override
        public TrickMove mirrored() {
            return this;
        }
    }

    record Fly(String stage, double tilt, double bank, double turn, double throttle, double seconds) implements TrickMove {
        public Fly {
            Objects.requireNonNull(stage, "stage");
        }

        @Override
        public TrickMove mirrored() {
            return new Fly(this.stage, this.tilt, -this.bank, -this.turn, this.throttle, this.seconds);
        }
    }

    record Rotate(String stage, double roll, double pitch, double yaw, double throttle, double degrees) implements TrickMove {
        public Rotate {
            Objects.requireNonNull(stage, "stage");
        }

        @Override
        public TrickMove mirrored() {
            return new Rotate(this.stage, -this.roll, this.pitch, -this.yaw, this.throttle, this.degrees);
        }

        @Override
        public TrickMove backwards() {
            return new Rotate(this.stage, this.roll, -this.pitch, this.yaw, this.throttle, this.degrees);
        }
    }

    record Hold(String stage, double roll, double pitch, double yaw, double throttle, double seconds) implements TrickMove {
        public Hold {
            Objects.requireNonNull(stage, "stage");
        }

        @Override
        public TrickMove mirrored() {
            return new Hold(this.stage, -this.roll, this.pitch, -this.yaw, this.throttle, this.seconds);
        }

        @Override
        public TrickMove backwards() {
            return new Hold(this.stage, this.roll, -this.pitch, this.yaw, this.throttle, this.seconds);
        }
    }

    record InvertedHold(String stage, double seconds, double sink) implements TrickMove {
        public InvertedHold {
            Objects.requireNonNull(stage, "stage");
        }

        @Override
        public TrickMove mirrored() {
            return this;
        }
    }

    record InvertedHover(String stage, double seconds) implements TrickMove {
        public InvertedHover {
            Objects.requireNonNull(stage, "stage");
        }

        @Override
        public TrickMove mirrored() {
            return this;
        }
    }

    record Maverick(String stage, double radius, double speed) implements TrickMove {
        public Maverick {
            Objects.requireNonNull(stage, "stage");
        }

        @Override
        public TrickMove mirrored() {
            return this;
        }
    }

    record Orbit(String stage, double radius, double speed, double turns, double side) implements TrickMove {
        public Orbit {
            Objects.requireNonNull(stage, "stage");
        }

        @Override
        public TrickMove mirrored() {
            return new Orbit(this.stage, this.radius, this.speed, this.turns, -this.side);
        }
    }

    record Obstacle(double x0, double y0, double z0, double x1, double y1, double z1) implements TrickMove {
        @Override
        public String stage() {
            return "";
        }

        @Override
        public TrickMove mirrored() {
            return new Obstacle(-this.x1, this.y0, this.z0, -this.x0, this.y1, this.z1);
        }
    }
}
