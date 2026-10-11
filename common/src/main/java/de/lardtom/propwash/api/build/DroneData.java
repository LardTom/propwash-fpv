package de.lardtom.propwash.api.build;

import java.util.Objects;
import java.util.Optional;

public record DroneData(BuildSpec build, PaintSpec paint, TuneSpec tune, Optional<String> name) {
    public static final int MAX_NAME_LENGTH = 48;

    public DroneData {
        Objects.requireNonNull(build, "build");
        paint = Objects.requireNonNullElse(paint, PaintSpec.DEFAULT);
        tune = Objects.requireNonNullElse(tune, TuneSpec.EMPTY);
        name = Objects.requireNonNullElse(name, Optional.<String>empty()).map(String::strip).filter(n -> !n.isEmpty());
        if (name.isPresent() && name.get().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Drohnenname höchstens " + MAX_NAME_LENGTH + " Zeichen");
        }
    }

    public static DroneData of(BuildSpec build) {
        return new DroneData(build, PaintSpec.DEFAULT, TuneSpec.EMPTY, Optional.empty());
    }

    public DroneData withBuild(BuildSpec value) {
        return new DroneData(value, this.paint, this.tune, this.name);
    }

    public DroneData withPaint(PaintSpec value) {
        return new DroneData(this.build, value, this.tune, this.name);
    }

    public DroneData withTune(TuneSpec value) {
        return new DroneData(this.build, this.paint, value, this.name);
    }

    public DroneData withName(String value) {
        return new DroneData(this.build, this.paint, this.tune, Optional.ofNullable(value));
    }
}
