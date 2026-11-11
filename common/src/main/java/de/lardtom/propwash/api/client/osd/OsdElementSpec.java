package de.lardtom.propwash.api.client.osd;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public record OsdElementSpec(Identifier id, String nameKey, int column, int row, OsdAlign align, boolean enabledByDefault, OsdGroup group,
                             int maxChars, String preview, OsdContent content) {
    public static final int COLUMNS = 30;
    public static final int ROWS = 16;
    public static final int MAX_CHARS = 30;
    public static final int MAX_ID_LENGTH = 64;

    public OsdElementSpec {
        Objects.requireNonNull(id, "id");
        if (id.toString().length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("OSD-Element-Id länger als " + MAX_ID_LENGTH + " Zeichen: " + id);
        }
        nameKey = Objects.requireNonNullElse(nameKey, "").strip();
        if (nameKey.isEmpty()) {
            nameKey = defaultNameKey(id);
        }
        if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
            throw new IllegalArgumentException(id + ": Platz außerhalb des OSD-Rasters (Spalte 0–" + (COLUMNS - 1) + ", Zeile 0–" + (ROWS - 1)
                    + "): " + column + ", " + row);
        }
        Objects.requireNonNull(align, "align");
        Objects.requireNonNull(group, "group");
        if (maxChars < 1 || maxChars > MAX_CHARS) {
            throw new IllegalArgumentException(id + ": maxChars muss zwischen 1 und " + MAX_CHARS + " liegen: " + maxChars);
        }
        preview = Objects.requireNonNullElse(preview, "");
        Objects.requireNonNull(content, "content");
    }

    public static String defaultNameKey(Identifier id) {
        return id.getNamespace() + ".osd." + id.getPath().replace('/', '.');
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private String nameKey = "";
        private int column;
        private int row;
        private OsdAlign align = OsdAlign.LEFT;
        private boolean enabledByDefault;
        private OsdGroup group = OsdGroup.ADDON;
        private int maxChars = MAX_CHARS;
        private String preview = "";
        private OsdContent content;

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder nameKey(String key) {
            this.nameKey = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder position(int column, int row) {
            this.column = column;
            this.row = row;
            return this;
        }

        public Builder align(OsdAlign value) {
            this.align = Objects.requireNonNull(value, "align");
            return this;
        }

        public Builder enabledByDefault(boolean value) {
            this.enabledByDefault = value;
            return this;
        }

        public Builder group(OsdGroup value) {
            this.group = Objects.requireNonNull(value, "group");
            return this;
        }

        public Builder maxChars(int value) {
            this.maxChars = value;
            return this;
        }

        public Builder preview(String text) {
            this.preview = Objects.requireNonNull(text, "text");
            return this;
        }

        public Builder content(OsdContent value) {
            this.content = Objects.requireNonNull(value, "content");
            return this;
        }

        public OsdElementSpec build() {
            if (this.content == null) {
                throw new IllegalArgumentException(this.id + ": OSD-Element ohne content");
            }
            return new OsdElementSpec(this.id, this.nameKey, this.column, this.row, this.align, this.enabledByDefault, this.group,
                    this.maxChars, this.preview, this.content);
        }
    }
}
