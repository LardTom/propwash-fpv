package de.lardtom.propwash.build;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

public record DronePaint(Map<PaintSlot, Integer> colors) {
    public static final DronePaint DEFAULT = new DronePaint(Map.of());
    public static final String CLIPBOARD_PREFIX = "propwash-paint:";
    public static final int MAX_TEXT_LENGTH = 512;

    public DronePaint {
        EnumMap<PaintSlot, Integer> copy = new EnumMap<>(PaintSlot.class);
        if (colors != null) {
            for (Map.Entry<PaintSlot, Integer> entry : colors.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), entry.getValue() & 0xFFFFFF);
                }
            }
        }
        colors = Collections.unmodifiableMap(copy);
    }

    public static DronePaint of(Map<PaintSlot, Integer> colors) {
        return colors == null || colors.isEmpty() ? DEFAULT : new DronePaint(colors);
    }

    public boolean isDefault() {
        return this.colors.isEmpty();
    }

    public boolean has(PaintSlot slot) {
        return this.colors.containsKey(slot);
    }

    public OptionalInt color(PaintSlot slot) {
        Integer color = this.colors.get(slot);
        return color == null ? OptionalInt.empty() : OptionalInt.of(color);
    }

    public OptionalInt color(PaintChannel channel, int propPosition) {
        return this.color(PaintSlot.of(channel, propPosition));
    }

    public DronePaint with(PaintSlot slot, int rgb) {
        EnumMap<PaintSlot, Integer> copy = this.mutable();
        copy.put(slot, rgb & 0xFFFFFF);
        return of(copy);
    }

    public DronePaint without(PaintSlot slot) {
        if (!this.has(slot)) {
            return this;
        }
        EnumMap<PaintSlot, Integer> copy = this.mutable();
        copy.remove(slot);
        return of(copy);
    }

    public DronePaint withAllProps(int rgb) {
        EnumMap<PaintSlot, Integer> copy = this.mutable();
        for (int i = 0; i < PaintSlot.PROPS; i++) {
            copy.put(PaintSlot.prop(i), rgb & 0xFFFFFF);
        }
        return of(copy);
    }

    public DronePaint withoutProps() {
        EnumMap<PaintSlot, Integer> copy = this.mutable();
        for (int i = 0; i < PaintSlot.PROPS; i++) {
            copy.remove(PaintSlot.prop(i));
        }
        return of(copy);
    }

    public int count() {
        return this.colors.size();
    }

    private EnumMap<PaintSlot, Integer> mutable() {
        EnumMap<PaintSlot, Integer> copy = new EnumMap<>(PaintSlot.class);
        copy.putAll(this.colors);
        return copy;
    }

    public String toText() {
        StringBuilder out = new StringBuilder(CLIPBOARD_PREFIX);
        boolean first = true;
        for (Map.Entry<PaintSlot, Integer> entry : this.colors.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(entry.getKey().key()).append('=').append(hex(entry.getValue()));
        }
        return out.toString();
    }

    public static Optional<DronePaint> fromText(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.strip();
        if (trimmed.length() > MAX_TEXT_LENGTH || !trimmed.startsWith(CLIPBOARD_PREFIX)) {
            return Optional.empty();
        }
        String body = trimmed.substring(CLIPBOARD_PREFIX.length()).strip();
        EnumMap<PaintSlot, Integer> colors = new EnumMap<>(PaintSlot.class);
        if (body.isEmpty()) {
            return Optional.of(DEFAULT);
        }
        for (String part : body.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                return Optional.empty();
            }
            Optional<PaintSlot> slot = PaintSlot.byKey(part.substring(0, eq).strip().toLowerCase(Locale.ROOT));
            OptionalInt rgb = parseHex(part.substring(eq + 1));
            if (slot.isEmpty() || rgb.isEmpty()) {
                return Optional.empty();
            }
            colors.put(slot.get(), rgb.getAsInt());
        }
        return Optional.of(of(colors));
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    public static OptionalInt parseHex(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        String value = text.strip();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() == 3) {
            StringBuilder expanded = new StringBuilder(6);
            for (char c : value.toCharArray()) {
                expanded.append(c).append(c);
            }
            value = expanded.toString();
        }
        if (value.length() != 6) {
            return OptionalInt.empty();
        }
        for (char c : value.toCharArray()) {
            if (Character.digit(c, 16) < 0) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(Integer.parseInt(value, 16));
    }
}
