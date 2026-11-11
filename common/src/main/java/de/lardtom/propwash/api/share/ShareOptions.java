package de.lardtom.propwash.api.share;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ShareOptions(Set<Layer> layers, boolean addonInfo, Optional<String> osdJson) {
    public enum Layer {
        PARTS,
        PAINT,
        TUNE,
        OSD,
        NAME
    }

    public static final Set<Layer> DEFAULT_LAYERS = Collections.unmodifiableSet(EnumSet.of(Layer.PARTS, Layer.PAINT, Layer.TUNE, Layer.NAME));

    public ShareOptions {
        Objects.requireNonNull(layers, "layers");
        EnumSet<Layer> copy = EnumSet.noneOf(Layer.class);
        for (Layer layer : layers) {
            copy.add(Objects.requireNonNull(layer, "layer"));
        }
        layers = Collections.unmodifiableSet(copy);
        osdJson = Objects.requireNonNullElse(osdJson, Optional.<String>empty()).filter(json -> !json.isBlank());
    }

    public static ShareOptions defaults() {
        return new ShareOptions(DEFAULT_LAYERS, true, Optional.empty());
    }

    public ShareOptions withLayers(Set<Layer> value) {
        return new ShareOptions(value, this.addonInfo, this.osdJson);
    }

    public ShareOptions withAddonInfo(boolean value) {
        return new ShareOptions(this.layers, value, this.osdJson);
    }

    public ShareOptions withOsd(String json) {
        return new ShareOptions(this.layers, this.addonInfo, Optional.of(Objects.requireNonNull(json, "json")));
    }

    public boolean has(Layer layer) {
        return this.layers.contains(layer);
    }
}
