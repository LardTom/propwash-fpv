package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public record PartVisual(OptionalInt accentRgb, Optional<Identifier> renderModel) {
    public static final PartVisual NONE = new PartVisual(OptionalInt.empty(), Optional.empty());

    public PartVisual {
        accentRgb = Objects.requireNonNullElse(accentRgb, OptionalInt.empty());
        renderModel = Objects.requireNonNullElse(renderModel, Optional.empty());
        if (accentRgb.isPresent() && (accentRgb.getAsInt() & 0xFF000000) != 0) {
            throw new IllegalArgumentException("Akzentfarbe ist RGB ohne Alpha: " + Integer.toHexString(accentRgb.getAsInt()));
        }
    }

    public static PartVisual accent(int rgb) {
        return new PartVisual(OptionalInt.of(rgb), Optional.empty());
    }

    public PartVisual withRenderModel(Identifier model) {
        return new PartVisual(this.accentRgb, Optional.of(model));
    }
}
