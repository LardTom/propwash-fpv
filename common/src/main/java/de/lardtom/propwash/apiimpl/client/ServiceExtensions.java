package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.ServiceExtension;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceExtensions {
    public static final int GAP = 2;
    public static final int PAD = 10;
    public static final int MIN_WIDTH = 32;
    public static final int COMPACT_WIDTH = 14;
    public static final String COMPACT_LABEL = "…";
    public static final String KEY = "propwash.drone.service.extensions.";
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private ServiceExtensions() {
    }

    public static List<ServiceExtension> visible(Entity drone) {
        List<ServiceExtension> out = new ArrayList<>();
        for (ServiceExtension extension : Api17Registries.Client.serviceExtensions()) {
            try {
                if (extension.visible(drone)) {
                    out.add(extension);
                }
            } catch (RuntimeException e) {
                warn(extension, "visible", e);
            }
        }
        return out;
    }

    public static List<FlatButton> header(Screen parent, Font font, Entity drone, int left, int right, int y, int height) {
        List<ServiceExtension> extensions = visible(drone);
        if (extensions.isEmpty()) {
            return List.of();
        }
        Component more = Component.translatable(KEY + "more");
        int[] natural = new int[extensions.size()];
        for (int i = 0; i < natural.length; i++) {
            natural[i] = font.width(label(extensions.get(i))) + PAD;
        }
        Layout layout = Layout.of(natural, font.width(more) + PAD, right - left);
        if (layout.compact()) {
            more = Component.literal(COMPACT_LABEL);
        }
        List<FlatButton> buttons = new ArrayList<>();
        int x = right - layout.total();
        for (int i = 0; i < layout.widths().length; i++) {
            ServiceExtension extension = extensions.get(i);
            ExtensionButton button = new ExtensionButton(x, y, layout.widths()[i], height, extension, () -> open(extension, drone, parent));
            button.setTooltip(Tooltip.create(label(extension)));
            buttons.add(button);
            x += layout.widths()[i] + GAP;
        }
        if (layout.more() > 0) {
            List<ServiceExtension> rest = List.copyOf(extensions.subList(layout.widths().length, extensions.size()));
            MoreButton button = new MoreButton(x, y, layout.more(), height, more, rest, () -> openMore(rest, drone, parent));
            button.setTooltip(Tooltip.create(Component.translatable(KEY + "more.tooltip", rest.size())));
            buttons.add(button);
        }
        return buttons;
    }

    public static void open(ServiceExtension extension, Entity drone, Screen parent) {
        try {
            extension.open(drone, parent);
        } catch (RuntimeException e) {
            warn(extension, "open", e);
        }
    }

    public static void openMore(List<ServiceExtension> extensions, Entity drone, Screen parent) {
        Minecraft.getInstance().gui.setScreen(new MoreScreen(parent, drone, extensions));
    }

    public static Component label(ServiceExtension extension) {
        try {
            Component label = extension.label();
            return label == null ? Component.literal(extension.id().toString()) : label;
        } catch (RuntimeException e) {
            warn(extension, "label", e);
            return Component.literal(extension.id().toString());
        }
    }

    private static void warn(ServiceExtension extension, String method, RuntimeException e) {
        Identifier id = extension.id();
        if (WARNED.add(id + "#" + method)) {
            Constants.LOG.warn("Wartungs-Erweiterung {}: {} fehlgeschlagen", id, method, e);
        }
    }

    public record Layout(int[] widths, int more, boolean compact, int gap) {
        public static Layout of(int[] natural, int moreWidth, int room) {
            for (int moreSize : new int[]{moreWidth, Math.min(moreWidth, COMPACT_WIDTH)}) {
                boolean compact = moreSize < moreWidth;
                for (int shown = Math.min(natural.length, ServiceExtension.MAX_VISIBLE_BUTTONS); shown >= 0; shown--) {
                    boolean hasMore = shown < natural.length;
                    if (!hasMore && compact) {
                        continue;
                    }
                    int more = hasMore ? moreSize : 0;
                    int available = room - more - GAP * Math.max(0, shown + (hasMore ? 1 : 0) - 1);
                    int[] widths = fit(Arrays.copyOf(natural, shown), available);
                    if (widths != null) {
                        return new Layout(widths, more, hasMore && compact, GAP);
                    }
                }
            }
            return new Layout(new int[0], 0, false, GAP);
        }

        private static int @Nullable [] fit(int[] natural, int available) {
            if (natural.length == 0) {
                return available >= 0 ? natural : null;
            }
            if (available < MIN_WIDTH * natural.length) {
                return null;
            }
            Integer[] order = new Integer[natural.length];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            Arrays.sort(order, (a, b) -> Integer.compare(natural[a], natural[b]));
            int[] widths = new int[natural.length];
            int remaining = available;
            for (int i = 0; i < order.length; i++) {
                int share = remaining / (order.length - i);
                int width = Math.max(MIN_WIDTH, Math.min(natural[order[i]], share));
                widths[order[i]] = width;
                remaining -= width;
            }
            return widths;
        }

        public int total() {
            int total = 0;
            for (int width : this.widths) {
                total += width;
            }
            int buttons = this.widths.length + (this.more > 0 ? 1 : 0);
            return total + this.more + this.gap * Math.max(0, buttons - 1);
        }
    }

    public static class ExtensionButton extends FlatButton {
        private final ServiceExtension extension;

        public ExtensionButton(int x, int y, int width, int height, ServiceExtension extension, Runnable action) {
            super(x, y, width, height, label(extension), Style.NORMAL, action);
            this.extension = extension;
        }

        public ServiceExtension extension() {
            return this.extension;
        }
    }

    public static class MoreButton extends FlatButton {
        private final List<ServiceExtension> extensions;

        public MoreButton(int x, int y, int width, int height, Component label, List<ServiceExtension> extensions, Runnable action) {
            super(x, y, width, height, label, Style.NORMAL, action);
            this.extensions = extensions;
        }

        public List<ServiceExtension> extensions() {
            return this.extensions;
        }
    }

    public static final class MoreScreen extends Screen implements ThemedScreen {
        private static final int MAX_WIDTH = 220;
        private static final int ROW = Metrics.CONTROL_HEIGHT + 4;

        private final Screen parent;
        private final Entity drone;
        private final List<ServiceExtension> extensions;
        private final List<ExtensionButton> entries = new ArrayList<>();
        private int left;
        private int top;
        private int right;
        private int bottom;

        public MoreScreen(Screen parent, Entity drone, List<ServiceExtension> extensions) {
            super(Component.translatable(KEY + "title"));
            this.parent = parent;
            this.drone = drone;
            this.extensions = List.copyOf(extensions);
        }

        public Screen parent() {
            return this.parent;
        }

        public List<ExtensionButton> entries() {
            return List.copyOf(this.entries);
        }

        @Override
        protected void init() {
            this.entries.clear();
            int w = Math.min(this.width - 16, MAX_WIDTH);
            int listHeight = this.extensions.size() * ROW;
            int h = Math.min(this.height - 12, Metrics.HEADER_HEIGHT + Metrics.PADDING * 2 + listHeight + Metrics.FOOTER_HEIGHT);
            this.left = (this.width - w) / 2;
            this.right = this.left + w;
            this.top = (this.height - h) / 2;
            this.bottom = this.top + h;
            int y = this.top + Metrics.HEADER_HEIGHT + Metrics.PADDING;
            int limit = this.bottom - Metrics.FOOTER_HEIGHT - Metrics.CONTROL_HEIGHT;
            for (ServiceExtension extension : this.extensions) {
                if (y > limit) {
                    break;
                }
                ExtensionButton button = new ExtensionButton(this.left + Metrics.PADDING, y, w - 2 * Metrics.PADDING, Metrics.CONTROL_HEIGHT, extension,
                        () -> open(extension, this.drone, this.parent));
                button.setTooltip(Tooltip.create(label(extension)));
                this.entries.add(this.addRenderableWidget(button));
                y += ROW;
            }
            Component back = CommonComponents.GUI_BACK;
            int backWidth = Math.max(60, this.font.width(back) + 16);
            int buttonY = this.bottom - Metrics.FOOTER_HEIGHT + (Metrics.FOOTER_HEIGHT - Metrics.CONTROL_HEIGHT) / 2;
            this.addRenderableWidget(new FlatButton(this.right - Metrics.PADDING - backWidth, buttonY, backWidth, Metrics.CONTROL_HEIGHT, back,
                    FlatButton.Style.NORMAL, this::onClose));
        }

        @Override
        public void onClose() {
            this.minecraft.gui.setScreen(this.parent);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            ThemeDraw.backdrop(g, this.width, this.height);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            ThemeDraw.window(g, this.left, this.top, this.right, this.bottom);
            int titleEnd = ThemeDraw.brandedHeader(g, this.font, this.left, this.top, this.right, Metrics.HEADER_HEIGHT, this.title,
                    this.right - Metrics.PADDING);
            ThemeDraw.headerSubtitle(g, this.font, titleEnd, this.top, Metrics.HEADER_HEIGHT, this.drone.getDisplayName(), this.right - Metrics.PADDING);
            ThemeDraw.footerBar(g, this.left, this.bottom - Metrics.FOOTER_HEIGHT, this.right, this.bottom);
            if (this.entries.size() < this.extensions.size()) {
                ThemeText.fitted(g, this.font, Component.translatable(KEY + "hidden", this.extensions.size() - this.entries.size()),
                        this.left + Metrics.PADDING, Metrics.textY(this.bottom - Metrics.FOOTER_HEIGHT, Metrics.FOOTER_HEIGHT),
                        this.right - this.left - 2 * Metrics.PADDING - 70, Palette.TEXT_MUTED);
            }
            super.extractRenderState(g, mouseX, mouseY, a);
            ThemeDraw.windowBorder(g, this.left, this.top, this.right, this.bottom);
        }
    }
}
