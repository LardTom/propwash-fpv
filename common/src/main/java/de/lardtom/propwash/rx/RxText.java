package de.lardtom.propwash.rx;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RxText {
    public static final String PREFIX = "propwash.rx.";
    public static final String TOOLTIP = PREFIX + "tooltip";
    public static final String TOOLTIP_LINE = TOOLTIP + ".line";
    public static final int TOOLTIP_WIDTH = 48;
    public static final String BOUND_TO = PREFIX + "bound_to";
    public static final String UNBOUND = PREFIX + "unbound";
    public static final String FOREIGN = PREFIX + "foreign";
    public static final String LEARN = PREFIX + "learn";
    public static final String LEARN_PROMPT = PREFIX + "learn.prompt";
    public static final String LEARN_NONE = PREFIX + "learn.none";
    public static final String CHANNEL = PREFIX + "channel";
    public static final String NO_CHANNEL = PREFIX + "no_channel";
    public static final String STICK = PREFIX + "stick.";
    public static final String AXIS = PREFIX + "axis";
    public static final String BUTTON = PREFIX + "button";
    public static final String SET_LOW = PREFIX + "set_low";
    public static final String SET_HIGH = PREFIX + "set_high";
    public static final String ENDPOINTS = PREFIX + "endpoints";
    public static final String RANGE = PREFIX + "range";
    public static final String RANGE_LABEL = PREFIX + "range.label";
    public static final String RANGE_UNLIMITED = PREFIX + "range.unlimited";
    public static final String CREATIVE_TOOLTIP = PREFIX + "creative.tooltip";
    public static final String BLOCKS = PREFIX + "blocks";
    public static final String OUTPUT = PREFIX + "output";
    public static final String LINK_OK = PREFIX + "link.ok";
    public static final String LINK_LOST = PREFIX + "link.lost";
    public static final String LINK_NONE = PREFIX + "link.none";
    public static final String UNBIND = PREFIX + "unbind";
    public static final String UNBIND_CONFIRM = PREFIX + "unbind.confirm";
    public static final String NO_RADIO = PREFIX + "no_radio";
    public static final String STATUS = PREFIX + "status";
    public static final String STATUS_UNBOUND = PREFIX + "status.unbound";
    public static final String UNKNOWN_OWNER = PREFIX + "unknown_owner";

    private RxText() {
    }

    public static Component source(@Nullable RxSource source) {
        if (source == null) {
            return Component.translatable(NO_CHANNEL);
        }
        return switch (source.kind()) {
            case STICK -> Component.translatable(STICK + source.index());
            case AXIS -> Component.translatable(AXIS, source.index() + 1);
            case BUTTON -> Component.translatable(BUTTON, source.index() + 1);
        };
    }

    public static Component owner(String name, @Nullable UUID owner) {
        if (!name.isEmpty()) {
            return Component.literal(name);
        }
        if (owner == null) {
            return Component.translatable(UNKNOWN_OWNER);
        }
        return Component.literal(owner.toString().substring(0, 8));
    }

    public static Component boundTo(String name, @Nullable UUID owner) {
        return owner == null ? Component.translatable(UNBOUND) : Component.translatable(BOUND_TO, owner(name, owner));
    }

    public static Component link(RxLinkState link, int power) {
        return switch (link) {
            case LINKED -> Component.translatable(LINK_OK);
            case LOST -> Component.translatable(LINK_LOST, power);
            case NONE -> Component.translatable(LINK_NONE);
        };
    }

    public static Component range(int range) {
        return Component.translatable(RANGE, range);
    }

    public static String endpoint(float value) {
        return String.format(Locale.ROOT, "%+.2f", value).replace('-', '−');
    }

    public static Component endpoints(RxSettings settings) {
        return Component.translatable(ENDPOINTS, endpoint(settings.low()), endpoint(settings.high()));
    }

    public static Component range(int range, boolean unlimited) {
        return unlimited ? Component.translatable(RANGE_UNLIMITED) : range(range);
    }

    public static Component status(RxSettings settings, int power, String ownerName) {
        return status(settings, power, ownerName, false);
    }

    public static Component status(RxSettings settings, int power, String ownerName, boolean unlimited) {
        if (!settings.bound()) {
            return Component.translatable(STATUS_UNBOUND);
        }
        return Component.translatable(STATUS, source(settings.source()), Component.translatable(OUTPUT, power),
                boundTo(ownerName, settings.owner()), range(settings.range(), unlimited));
    }

    public static List<String> tooltipLines(String text) {
        String[] words = text.trim().split(" +");
        int lines = wrap(words, TOOLTIP_WIDTH).size();
        int low = 1;
        for (String word : words) {
            low = Math.max(low, word.length());
        }
        int high = Math.max(low, text.length());
        while (low < high) {
            int mid = (low + high) / 2;
            if (wrap(words, mid).size() <= lines) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return wrap(words, low);
    }

    private static List<String> wrap(String[] words, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        lines.add(line.toString());
        return lines;
    }
}
