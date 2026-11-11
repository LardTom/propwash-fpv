package de.lardtom.propwash.api.input;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

public record SwitchSpec(Identifier id, boolean toggle, boolean latchesOnGamepad, boolean serverSync, String nameKey,
                         Optional<String> descriptionKey) {
    public static final int MAX_ID_LENGTH = 64;

    public SwitchSpec {
        Objects.requireNonNull(id, "id");
        if (id.toString().length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("Schalter-Id länger als " + MAX_ID_LENGTH + " Zeichen: " + id);
        }
        nameKey = Objects.requireNonNullElse(nameKey, "").strip();
        if (nameKey.isEmpty()) {
            nameKey = defaultNameKey(id);
        }
        descriptionKey = Objects.requireNonNullElse(descriptionKey, Optional.<String>empty()).map(String::strip).filter(key -> !key.isEmpty());
    }

    public static String defaultNameKey(Identifier id) {
        return id.getNamespace() + ".switch." + id.getPath().replace('/', '.');
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private boolean toggle = true;
        private boolean latchesOnGamepad;
        private boolean serverSync;
        private String nameKey = "";
        private Optional<String> descriptionKey = Optional.empty();

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder toggle(boolean value) {
            this.toggle = value;
            return this;
        }

        public Builder latchesOnGamepad(boolean value) {
            this.latchesOnGamepad = value;
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

        public Builder descriptionKey(String key) {
            this.descriptionKey = Optional.of(Objects.requireNonNull(key, "key"));
            return this;
        }

        public SwitchSpec build() {
            return new SwitchSpec(this.id, this.toggle, this.latchesOnGamepad, this.serverSync, this.nameKey, this.descriptionKey);
        }
    }
}
