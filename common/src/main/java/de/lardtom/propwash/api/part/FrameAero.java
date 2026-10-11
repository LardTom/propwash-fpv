package de.lardtom.propwash.api.part;

import java.util.Objects;
import java.util.OptionalDouble;

public record FrameAero(double frontalAreaM2, double dragCoefficient, OptionalDouble sideAreaM2, OptionalDouble topAreaM2) {
    public FrameAero {
        if (!(frontalAreaM2 > 0.0 && frontalAreaM2 <= 2.0)) {
            throw new IllegalArgumentException("Stirnfläche muss größer als 0 und höchstens 2 m² sein: " + frontalAreaM2);
        }
        if (!(dragCoefficient > 0.0 && dragCoefficient <= 3.0)) {
            throw new IllegalArgumentException("Widerstandsbeiwert muss größer als 0 und höchstens 3 sein: " + dragCoefficient);
        }
        sideAreaM2 = Objects.requireNonNullElse(sideAreaM2, OptionalDouble.empty());
        topAreaM2 = Objects.requireNonNullElse(topAreaM2, OptionalDouble.empty());
        positive(sideAreaM2, "Seitenfläche");
        positive(topAreaM2, "Draufsichtfläche");
    }

    public static FrameAero of(double frontalAreaM2, double dragCoefficient) {
        return new FrameAero(frontalAreaM2, dragCoefficient, OptionalDouble.empty(), OptionalDouble.empty());
    }

    private static void positive(OptionalDouble value, String name) {
        if (value.isPresent() && !(value.getAsDouble() > 0.0 && value.getAsDouble() <= 5.0)) {
            throw new IllegalArgumentException(name + " muss größer als 0 und höchstens 5 m² sein: " + value.getAsDouble());
        }
    }
}
