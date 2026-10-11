package de.lardtom.propwash.api.part;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.OptionalDouble;

public record VideoDefinition(Identifier id, PartBasics basics, VideoLink link, int latencyMs, int rangeBlocks, double imageQuality,
                              double cameraWidthMm, OptionalDouble cameraFovDeg, int vtxPowerMw, OptionalDouble powerW) implements PartDefinition {
    public VideoDefinition(Identifier id, PartBasics basics, VideoLink link, int latencyMs, int rangeBlocks, double imageQuality,
                           double cameraWidthMm, OptionalDouble cameraFovDeg, int vtxPowerMw) {
        this(id, basics, link, latencyMs, rangeBlocks, imageQuality, cameraWidthMm, cameraFovDeg, vtxPowerMw, OptionalDouble.empty());
    }

    public VideoDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(basics, "basics");
        Objects.requireNonNull(link, "link");
        cameraFovDeg = Objects.requireNonNullElse(cameraFovDeg, OptionalDouble.empty());
        if (latencyMs < 1 || latencyMs > 500) {
            throw new IllegalArgumentException(id + ": Latenz muss zwischen 1 und 500 ms liegen: " + latencyMs);
        }
        if (rangeBlocks < 16 || rangeBlocks > 20000) {
            throw new IllegalArgumentException(id + ": Reichweite muss zwischen 16 und 20000 Blöcken liegen: " + rangeBlocks);
        }
        if (!(imageQuality > 0.0 && imageQuality <= 1.0)) {
            throw new IllegalArgumentException(id + ": Bildqualität muss größer als 0 und höchstens 1 sein: " + imageQuality);
        }
        if (!(cameraWidthMm >= 8.0 && cameraWidthMm <= 40.0)) {
            throw new IllegalArgumentException(id + ": Kamerabreite muss zwischen 8 und 40 mm liegen: " + cameraWidthMm);
        }
        if (cameraFovDeg.isPresent() && !(cameraFovDeg.getAsDouble() >= 60.0 && cameraFovDeg.getAsDouble() <= 180.0)) {
            throw new IllegalArgumentException(id + ": Kamera-Sichtfeld muss zwischen 60 und 180 Grad liegen: " + cameraFovDeg.getAsDouble());
        }
        if (vtxPowerMw < 0 || vtxPowerMw > 10000) {
            throw new IllegalArgumentException(id + ": Sendeleistung ungültig: " + vtxPowerMw);
        }
        powerW = Objects.requireNonNullElse(powerW, OptionalDouble.empty());
        if (powerW.isPresent() && !(powerW.getAsDouble() >= 0.1 && powerW.getAsDouble() <= 60.0)) {
            throw new IllegalArgumentException(id + ": Leistungsaufnahme muss zwischen 0,1 und 60 W liegen: " + powerW.getAsDouble());
        }
    }

    @Override
    public PartKind kind() {
        return PartKind.VIDEO;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private PartBasics basics;
        private VideoLink link;
        private int latencyMs = -1;
        private int rangeBlocks = -1;
        private double imageQuality = -1.0;
        private double cameraWidthMm = 19.0;
        private OptionalDouble cameraFovDeg = OptionalDouble.empty();
        private int vtxPowerMw;
        private OptionalDouble powerW = OptionalDouble.empty();

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder basics(PartBasics value) {
            this.basics = value;
            return this;
        }

        public Builder mass(double grams) {
            this.basics = PartBasics.withMass(this.basics, grams);
            return this;
        }

        public Builder link(VideoLink value) {
            this.link = value;
            return this;
        }

        public Builder latency(int ms) {
            this.latencyMs = ms;
            return this;
        }

        public Builder range(int blocks) {
            this.rangeBlocks = blocks;
            return this;
        }

        public Builder imageQuality(double value) {
            this.imageQuality = value;
            return this;
        }

        public Builder cameraWidth(double mm) {
            this.cameraWidthMm = mm;
            return this;
        }

        public Builder cameraFov(double degrees) {
            this.cameraFovDeg = OptionalDouble.of(degrees);
            return this;
        }

        public Builder vtxPower(int milliwatts) {
            this.vtxPowerMw = milliwatts;
            return this;
        }

        public Builder powerWatts(double watts) {
            this.powerW = OptionalDouble.of(watts);
            return this;
        }

        public VideoDefinition build() {
            if (this.basics == null || this.link == null || this.latencyMs < 0 || this.rangeBlocks < 0 || this.imageQuality < 0.0) {
                throw new IllegalArgumentException(this.id + ": Masse, System, Latenz, Reichweite und Bildqualität sind Pflicht");
            }
            return new VideoDefinition(this.id, this.basics, this.link, this.latencyMs, this.rangeBlocks, this.imageQuality,
                    this.cameraWidthMm, this.cameraFovDeg, this.vtxPowerMw, this.powerW);
        }
    }
}
