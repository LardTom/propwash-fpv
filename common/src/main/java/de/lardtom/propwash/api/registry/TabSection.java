package de.lardtom.propwash.api.registry;

import de.lardtom.propwash.api.part.PartKind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public enum TabSection {
    PRESETS(null),
    FRAMES(PartKind.FRAME),
    STACKS(PartKind.STACK),
    MOTORS(PartKind.MOTOR),
    PROPS(PartKind.PROP),
    VIDEO(PartKind.VIDEO),
    BATTERIES(PartKind.BATTERY),
    ACCESSORIES(PartKind.ACCESSORY),
    ENTRIES(null);

    private final @Nullable PartKind kind;

    TabSection(@Nullable PartKind kind) {
        this.kind = kind;
    }

    public Optional<PartKind> kind() {
        return Optional.ofNullable(this.kind);
    }

    public static Optional<TabSection> of(PartKind kind) {
        for (TabSection section : values()) {
            if (section.kind != null && section.kind == kind) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }

    public static List<TabSection> defaults() {
        return List.of(values());
    }

    public static List<TabSection> complete(List<TabSection> wanted) {
        Set<TabSection> order = new LinkedHashSet<>();
        for (TabSection section : wanted) {
            order.add(Objects.requireNonNull(section, "section"));
        }
        order.addAll(Arrays.asList(values()));
        return List.copyOf(new ArrayList<>(order));
    }
}
