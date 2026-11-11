package de.lardtom.propwash.api.input;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public record AxisSpec(Identifier id, double center, double keyRateDps, boolean serverSync, String nameKey) {
    public static final int MAX_ID_LENGTH = SwitchSpec.MAX_ID_LENGTH;
    public static final double DEFAULT_KEY_RATE_DPS = 60.0;
    public static final double MAX_KEY_RATE_DPS = 3600.0;

    public AxisSpec {
        Objects.requireNonNull(id, "id");
        if (id.toString().length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Achsen-Id länger als " + MAX_ID_LENGTH + " Zeichen: " + id);
        }
        if (!(center >= -1.0 && center <= 1.0)) {
            throw new IllegalArgumentException(id + ": Mitte muss zwischen -1 und 1 liegen: " + center);
        }
        if (!(keyRateDps > 0.0 && keyRateDps <= MAX_KEY_RATE_DPS)) {
            throw new IllegalArgumentException(id + ": Tastenrate muss größer 0 und höchstens " + MAX_KEY_RATE_DPS + " °/s sein: " + keyRateDps);
        }
        nameKey = Objects.requireNonNullElse(nameKey, "").strip();
        if (nameKey.isEmpty()) {
            nameKey = defaultNameKey(id);
        }
    }

    public static String defaultNameKey(Identifier id) {
        return id.getNamespace() + ".axis." + id.getPath().replace('/', '.');
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private double center;
        private double keyRateDps = DEFAULT_KEY_RATE_DPS;
        private boolean serverSync;
        private String nameKey = "";

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder center(double value) {
            this.center = value;
            return this;
        }

        public Builder keyRateDps(double value) {
            this.keyRateDps = value;
            return this;
        }

        public Builder serverSync(boolean value) {
            this.serverSync = value;
            return this;
        }

        public Builder nameKey(String key) {
            this.nameKey = Objects.requireNonNull(key, "key");
            return this;
        }

        public AxisSpec build() {
            return new AxisSpec(this.id, this.center, this.keyRateDps, this.serverSync, this.nameKey);
        }
    }
}
