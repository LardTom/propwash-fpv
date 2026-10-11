package de.lardtom.propwash.ui.client.search;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class SearchTags {
    private static final Map<AbstractWidget, Tag> TAGS = new WeakHashMap<>();

    public record Tag(@Nullable Component label, List<Component> keywords, boolean hidden) {
    }

    private SearchTags() {
    }

    public static <T extends AbstractWidget> T label(T widget, Component label, Component... keywords) {
        TAGS.put(widget, new Tag(label, List.of(keywords), false));
        return widget;
    }

    public static <T extends AbstractWidget> T hide(T widget) {
        TAGS.put(widget, new Tag(null, List.of(), true));
        return widget;
    }

    public static @Nullable Tag of(AbstractWidget widget) {
        return TAGS.get(widget);
    }
}
