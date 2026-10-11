package de.lardtom.propwash.input.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.input.client.KeyNames;
import de.lardtom.propwash.platform.client.ClientServices;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.ui.TextFit;
import de.lardtom.propwash.ui.client.search.SearchTags;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class KeysTab extends UiTab {
    public static final String ID = "keys";

    private static final int KEY_WIDTH = 96;
    private static final int RESET_WIDTH = 46;
    private static final int SECTION_GAP = 6;

    enum Group {
        STICKS,
        DRONE,
        FUNCTIONS,
        COMMANDS
    }

    private boolean stacked;

    private record Row(KeyMapping mapping, Group group, int y, FlatButton button, FlatButton reset) {
    }

    private final Map<KeyMapping, InputConstants.Key> draft = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<Group, Integer> sectionY = new EnumMap<>(Group.class);
    private @Nullable KeyMapping listening;
    private int hintY;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.input.tab.keys");
    }

    @Override
    public int order() {
        return 420;
    }

    @Override
    public boolean capturesInput() {
        return this.listening != null;
    }

    public @Nullable KeyMapping listening() {
        return this.listening;
    }

    public static List<KeyMapping> mappings() {
        List<KeyMapping> out = new ArrayList<>();
        for (Group group : Group.values()) {
            out.addAll(mappings(group));
        }
        return out;
    }

    static List<KeyMapping> mappings(Group group) {
        List<KeyMapping> out = new ArrayList<>();
        for (KeyMapping mapping : ClientRegistrar.keyMappings()) {
            if (groupOf(mapping) == group) {
                out.add(mapping);
            }
        }
        return out;
    }

    static Group groupOf(KeyMapping mapping) {
        if (KeyDefaults.isCommand(mapping.getName())) {
            return Group.COMMANDS;
        }
        for (KeyDefaults.Entry entry : KeyDefaults.OWN) {
            if (entry.mappingName().equals(mapping.getName())) {
                return entry.flightStick() ? Group.STICKS : Group.DRONE;
            }
        }
        return Group.FUNCTIONS;
    }

    public InputConstants.Key key(KeyMapping mapping) {
        return this.draft.getOrDefault(mapping, InputConstants.getKey(mapping.saveString()));
    }

    public void assign(KeyMapping mapping, InputConstants.Key key) {
        if (key.equals(InputConstants.getKey(mapping.saveString()))) {
            this.draft.remove(mapping);
        } else {
            this.draft.put(mapping, key);
        }
        this.host.changed();
        this.refresh();
    }

    public void startListening(KeyMapping mapping) {
        this.listening = mapping;
        this.refresh();
    }

    @Override
    protected void build() {
        this.rows.clear();
        this.sectionY.clear();
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        int right = this.x + w;
        int cy = 0;
        int widest = 0;
        for (Group group : Group.values()) {
            for (KeyMapping mapping : mappings(group)) {
                widest = Math.max(widest, this.font().width(Component.translatable(mapping.getName())));
            }
        }
        this.stacked = TextFit.mode(widest, w - KEY_WIDTH - RESET_WIDTH - Metrics.GAP - 2 * Metrics.LABEL_GAP) == TextFit.Mode.CUT;
        int lead = this.stacked ? Metrics.LINE_HEIGHT : 0;
        for (Group group : Group.values()) {
            List<KeyMapping> mappings = mappings(group);
            if (mappings.isEmpty()) {
                continue;
            }
            this.sectionY.put(group, cy);
            panel.stop(cy);
            cy += Metrics.SECTION_HEIGHT;
            boolean first = true;
            for (KeyMapping mapping : mappings) {
                if (!first) {
                    panel.stop(cy - 1);
                }
                first = false;
                int rowY = this.top() + cy + lead;
                FlatButton button = this.add(new FlatButton(right - RESET_WIDTH - Metrics.GAP / 2 - KEY_WIDTH, rowY, KEY_WIDTH, FIELD,
                        Component.empty(), FlatButton.Style.NORMAL, () -> this.startListening(mapping)));
                FlatButton reset = this.add(new FlatButton(right - RESET_WIDTH, rowY, RESET_WIDTH, FIELD, Component.translatable("propwash.input.keys.reset_short"),
                        FlatButton.Style.NORMAL, () -> this.assign(mapping, mapping.getDefaultKey())).placeholderWhenInactive());
                SearchTags.label(button, Component.translatable(mapping.getName()));
                SearchTags.hide(reset);
                this.rows.add(new Row(mapping, group, cy, button, reset));
                cy += ROW + lead;
            }
            cy += SECTION_GAP;
        }
        this.hintY = cy;
        panel.stop(cy);
        cy += ThemeText.wrappedHeight(this.font(), Component.translatable("propwash.input.keys.hint"), w) + 4;
        this.endScroll(cy);
        this.refresh();
    }

    private void refresh() {
        for (Row row : this.rows) {
            KeyMapping mapping = row.mapping();
            InputConstants.Key key = this.key(mapping);
            List<Component> clashes = this.clashes(mapping, key);
            Component message;
            if (mapping == this.listening) {
                message = ThemeText.accent(Component.literal("> ").append(Component.translatable("propwash.input.keys.press")).append(" <"));
            } else {
                Component name = this.keyName(mapping, key);
                message = clashes.isEmpty() ? name : ThemeText.colored(name, Palette.WARNING);
            }
            row.button().setMessage(message);
            MutableComponent tip = Component.translatable("propwash.input.keys.click", Component.translatable(mapping.getName()));
            if (!clashes.isEmpty()) {
                tip.append("\n").append(ThemeText.colored(Component.translatable("propwash.input.keys.clash",
                        joined(clashes)), Palette.WARNING));
            }
            List<Component> partners = this.partners(mapping, key);
            if (!partners.isEmpty()) {
                tip.append("\n").append(ThemeText.colored(Component.translatable("propwash.input.keys.shared",
                        joined(partners)), Palette.TEXT_MUTED));
            }
            row.button().setTooltip(ThemeText.tip(tip));
            boolean changed = !key.equals(mapping.getDefaultKey());
            row.reset().active = changed;
            row.reset().setTooltip(changed ? Tooltip.create(Component.translatable("propwash.input.keys.reset",
                    this.keyName(mapping, mapping.getDefaultKey()))) : null);
        }
    }

    private static Component joined(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out;
    }

    private Component keyName(KeyMapping mapping, InputConstants.Key key) {
        if (key.equals(InputConstants.UNKNOWN)) {
            KeyMapping fallback = InputKeys.movementFallback(mapping);
            if (fallback != null) {
                return Component.translatable("propwash.input.keys.follows", KeyNames.of(fallback));
            }
            return ThemeText.muted(Component.translatable("propwash.input.key.unbound"));
        }
        return KeyNames.of(key);
    }

    public List<Component> clashes(KeyMapping mapping, InputConstants.Key key) {
        return this.sameKey(mapping, key, false);
    }

    public List<Component> partners(KeyMapping mapping, InputConstants.Key key) {
        return this.sameKey(mapping, key, true);
    }

    private List<Component> sameKey(KeyMapping mapping, InputConstants.Key key, boolean shared) {
        List<Component> out = new ArrayList<>();
        if (key.equals(InputConstants.UNKNOWN)) {
            return out;
        }
        Options options = Minecraft.getInstance().options;
        for (KeyMapping other : options.keyMappings) {
            boolean partner = KeyDefaults.sharesKey(mapping.getName(), other.getName()) && ClientServices.PLATFORM.sameKeyModifier(mapping, other);
            if (other == mapping || partner != shared) {
                continue;
            }
            InputConstants.Key otherKey = this.draft.containsKey(other) ? this.draft.get(other) : InputConstants.getKey(other.saveString());
            if (key.equals(otherKey)) {
                out.add(Component.translatable(other.getName()));
            }
        }
        return out;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        KeyMapping mapping = this.listening;
        if (mapping == null) {
            return false;
        }
        this.listening = null;
        this.assign(mapping, event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        KeyMapping mapping = this.listening;
        if (mapping == null) {
            return super.mouseClicked(event, doubleClick);
        }
        this.listening = null;
        this.assign(mapping, InputConstants.Type.MOUSE.getOrCreate(event.button()));
        return true;
    }

    @Override
    public boolean cancelAction() {
        if (this.listening == null) {
            return false;
        }
        this.listening = null;
        this.refresh();
        return true;
    }

    @Override
    public void tick() {
        this.refresh();
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Font font = this.font();
        int w = this.innerWidth();
        int top = this.top();
        for (Map.Entry<Group, Integer> section : this.sectionY.entrySet()) {
            this.section(g, Component.translatable("propwash.input.keys.group." + section.getKey().name().toLowerCase(java.util.Locale.ROOT)),
                    this.x, top + section.getValue(), w);
        }
        int labelWidth = w - KEY_WIDTH - RESET_WIDTH - Metrics.GAP;
        for (int i = 0; i < this.rows.size(); i++) {
            Row row = this.rows.get(i);
            int rowY = top + row.y();
            if (i % 2 == 1) {
                g.fill(this.x, rowY - 1, this.x + w, rowY + ROW - 1 + (this.stacked ? Metrics.LINE_HEIGHT : 0), Palette.ROW_STRIPE);
            }
            boolean active = row.mapping() == this.listening;
            ThemeText.fitted(g, font, Component.translatable(row.mapping().getName()), this.x + Metrics.LABEL_GAP,
                    this.stacked ? rowY + 1 : Metrics.textY(rowY, FIELD), (this.stacked ? w : labelWidth) - 2 * Metrics.LABEL_GAP,
                    active ? Palette.ACCENT : Palette.TEXT_MUTED);
        }
        ThemeText.wrapped(g, font, Component.translatable("propwash.input.keys.hint"), this.x, top + this.hintY, w, Palette.TEXT_FAINT);
        this.extractScrollbar(g);
    }

    @Override
    public void resetDefaults() {
        this.listening = null;
        for (KeyMapping mapping : mappings()) {
            this.assign(mapping, mapping.getDefaultKey());
        }
    }

    @Override
    public void save() {
        if (this.draft.isEmpty()) {
            return;
        }
        for (Map.Entry<KeyMapping, InputConstants.Key> entry : this.draft.entrySet()) {
            entry.getKey().setKey(entry.getValue());
        }
        this.draft.clear();
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    @Override
    public void discard() {
        this.draft.clear();
        this.listening = null;
    }
}
