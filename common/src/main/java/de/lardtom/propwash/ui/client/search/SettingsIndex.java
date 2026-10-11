package de.lardtom.propwash.ui.client.search;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.TuneHost;
import de.lardtom.propwash.ui.client.TuneModel;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.LabeledValue;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationThunk;
import net.minecraft.client.gui.narration.NarrationTrigger;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SettingsIndex {
    public static final String WORLD_RULES_TAB = "world_rules";
    public static final String KEYS_TAB = "keys";
    private static final List<String> INDEX_LANGUAGES = List.of("en_us", "de_de");
    private static final Identifier SYNONYMS = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "search/synonyms.txt");
    private static final int MAX_LABEL = 64;

    private static @Nullable Language cachedFor;
    private static Map<String, Map<String, String>> languages = Map.of();
    private static SettingsSearch.Synonyms synonyms = SettingsSearch.Synonyms.NONE;

    public record Target(String tabId, int widgetIndex, String foldedLabel) {
    }

    private final List<SettingsSearch.Entry> entries;
    private final Map<String, Target> targets;
    private final List<String> failedTabs;
    private final long buildMillis;

    private SettingsIndex(List<SettingsSearch.Entry> entries, Map<String, Target> targets, List<String> failedTabs, long buildMillis) {
        this.entries = List.copyOf(entries);
        this.targets = Map.copyOf(targets);
        this.failedTabs = List.copyOf(failedTabs);
        this.buildMillis = buildMillis;
    }

    public List<String> failedTabs() {
        return this.failedTabs;
    }

    public List<SettingsSearch.Entry> entries() {
        return this.entries;
    }

    public @Nullable Target target(SettingsSearch.Entry entry) {
        return this.targets.get(entry.id());
    }

    public long buildMillis() {
        return this.buildMillis;
    }

    public SettingsSearch.Synonyms synonyms() {
        return synonyms;
    }

    public List<SettingsSearch.Hit> search(String query, int limit) {
        return SettingsSearch.search(query, this.entries, synonyms, limit);
    }

    public static SettingsIndex build(ConfiguratorScreen screen) {
        long start = System.nanoTime();
        refreshResources(screen.minecraft());
        List<SettingsSearch.Entry> entries = new ArrayList<>();
        Map<String, Target> targets = new HashMap<>();
        List<String> failed = new ArrayList<>();
        Map<String, ConfigTab> fresh = new LinkedHashMap<>();
        for (ConfigTab tab : ConfigTabs.create()) {
            String id = safeId(tab);
            if (id != null) {
                fresh.putIfAbsent(id, tab);
            }
        }
        ScreenRectangle content = screen.contentArea();
        for (ConfigTab live : screen.tabs()) {
            String tabId = safeId(live);
            if (tabId == null) {
                continue;
            }
            Component title;
            try {
                title = live.title();
            } catch (RuntimeException e) {
                continue;
            }
            String tabTitle = title.getString();
            List<String> tabAliases = aliases(title, tabTitle);
            entries.add(new SettingsSearch.Entry("tab:" + tabId, SettingsSearch.Kind.TAB, tabId, tabTitle, tabTitle, tabAliases, List.of(),
                    List.of()));
            ConfigTab probe = fresh.get(tabId);
            if (probe == null) {
                failed.add(tabId);
                continue;
            }
            ProbeHost host = new ProbeHost(screen);
            try {
                int width = probe instanceof UiTab ? content.width() : Math.max(40, content.width() - Metrics.CONTENT_INSET);
                probe.attach(host, content.left(), content.top(), width, content.height());
            } catch (RuntimeException e) {
                Constants.LOG.debug("Suche: Tab {} ließ sich nicht durchsuchen: {}", tabId, e.toString());
                failed.add(tabId);
                continue;
            }
            SettingsSearch.Kind kind = WORLD_RULES_TAB.equals(tabId) ? SettingsSearch.Kind.WORLD_RULE
                    : KEYS_TAB.equals(tabId) ? SettingsSearch.Kind.KEY : SettingsSearch.Kind.SETTING;
            List<String> kindWords = kindKeywords(kind);
            List<AbstractWidget> widgets = host.widgets;
            for (int i = 0; i < widgets.size(); i++) {
                AbstractWidget widget = widgets.get(i);
                SearchTags.Tag tag = SearchTags.of(widget);
                if (tag != null && tag.hidden()) {
                    continue;
                }
                Component label = label(widget, tag);
                if (label == null) {
                    continue;
                }
                String text = clean(label.getString());
                if (text.isEmpty() || text.length() > MAX_LABEL) {
                    continue;
                }
                List<String> keywords = new ArrayList<>(tabAliases);
                keywords.addAll(kindWords);
                if (tag != null) {
                    for (Component keyword : tag.keywords()) {
                        keywords.add(keyword.getString());
                        keywords.addAll(aliases(keyword, keyword.getString()));
                    }
                }
                List<String> details = new ArrayList<>();
                for (Component hint : hints(widget)) {
                    String shown = clean(hint.getString());
                    if (!shown.isEmpty()) {
                        details.add(shown);
                    }
                    details.addAll(aliases(hint, shown));
                }
                String id = tabId + "#" + i;
                entries.add(new SettingsSearch.Entry(id, kind, tabId, tabTitle, text, aliases(label, text), details, keywords));
                targets.put(id, new Target(tabId, i, SettingsSearch.fold(text).text()));
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        return new SettingsIndex(entries, targets, failed, millis);
    }

    public static @Nullable AbstractWidget locate(Target target, List<AbstractWidget> widgets) {
        if (target.widgetIndex() >= 0 && target.widgetIndex() < widgets.size()) {
            AbstractWidget candidate = widgets.get(target.widgetIndex());
            if (matches(candidate, target)) {
                return candidate;
            }
        }
        for (AbstractWidget widget : widgets) {
            if (matches(widget, target)) {
                return widget;
            }
        }
        return null;
    }

    private static boolean matches(AbstractWidget widget, Target target) {
        Component label = label(widget, SearchTags.of(widget));
        return label != null && SettingsSearch.fold(clean(label.getString())).text().equals(target.foldedLabel());
    }

    private static @Nullable String safeId(ConfigTab tab) {
        try {
            return tab.id();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static @Nullable Component label(AbstractWidget widget, SearchTags.@Nullable Tag tag) {
        if (tag != null && tag.label() != null) {
            return tag.label();
        }
        if (widget instanceof LabeledValue labeled) {
            return labeled.themeLabel();
        }
        Component message = widget.getMessage();
        ThemeText.Split split = ThemeText.splitLabel(message);
        if (split == null) {
            return message;
        }
        if (message.getContents() instanceof TranslatableContents translatable && translatable.getArgs().length > 0
                && translatable.getArgs()[0] instanceof Component first) {
            return first;
        }
        return split.label();
    }

    private static List<Component> hints(AbstractWidget widget) {
        List<Component> out = new ArrayList<>();
        try {
            widget.updateNarration(new HintCollector(out));
        } catch (RuntimeException ignored) {
            return out;
        }
        return out;
    }

    private static List<String> kindKeywords(SettingsSearch.Kind kind) {
        String key = switch (kind) {
            case WORLD_RULE -> "propwash.ui.search.kind.world_rule";
            case KEY -> "propwash.ui.search.kind.key";
            default -> null;
        };
        if (key == null) {
            return List.of();
        }
        Component component = Component.translatable(key);
        List<String> out = new ArrayList<>();
        out.add(component.getString());
        out.addAll(aliases(component, component.getString()));
        return out;
    }

    private static List<String> aliases(Component component, String shown) {
        Set<String> keys = new LinkedHashSet<>();
        collectKeys(component, keys, 0);
        Set<String> out = new LinkedHashSet<>();
        for (String key : keys) {
            for (Map<String, String> language : languages.values()) {
                String text = language.get(key);
                if (text != null) {
                    String cleaned = clean(text);
                    if (!cleaned.isEmpty() && !cleaned.equals(shown)) {
                        out.add(cleaned);
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    private static void collectKeys(Component component, Set<String> keys, int depth) {
        if (depth > 6) {
            return;
        }
        if (component.getContents() instanceof TranslatableContents translatable) {
            keys.add(translatable.getKey());
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component nested) {
                    collectKeys(nested, keys, depth + 1);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            collectKeys(sibling, keys, depth + 1);
        }
    }

    static String clean(String raw) {
        String text = raw.replaceAll("§.", "").replaceAll("%(\\d+\\$)?[sd]", " ").replace("%%", "%");
        return text.replace('\n', ' ').replaceAll("\\s+", " ").strip();
    }

    private static void refreshResources(Minecraft minecraft) {
        Language current = Language.getInstance();
        if (cachedFor == current) {
            return;
        }
        cachedFor = current;
        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
        for (String code : INDEX_LANGUAGES) {
            Map<String, String> values = new HashMap<>();
            Identifier file = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "lang/" + code + ".json");
            for (Resource resource : minecraft.getResourceManager().getResourceStack(file)) {
                try (InputStream in = resource.open()) {
                    Language.loadFromJson(in, values::put);
                } catch (IOException | RuntimeException e) {
                    Constants.LOG.debug("Suche: Sprachdatei {} nicht lesbar: {}", file, e.toString());
                }
            }
            loaded.put(code, values);
        }
        languages = loaded;
        SettingsSearch.Synonyms parsed = SettingsSearch.Synonyms.NONE;
        List<Resource> stack = minecraft.getResourceManager().getResourceStack(SYNONYMS);
        if (!stack.isEmpty()) {
            try (InputStream in = stack.getLast().open()) {
                parsed = SettingsSearch.Synonyms.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                Constants.LOG.debug("Suche: Synonyme nicht lesbar: {}", e.toString());
            }
        }
        synonyms = parsed;
    }

    private record HintCollector(List<Component> out) implements NarrationElementOutput {
        @Override
        public void add(NarratedElementType type, Component contents) {
            if (type == NarratedElementType.HINT) {
                this.out.add(contents);
            }
        }

        @Override
        public void add(NarratedElementType type, NarrationThunk<?> contents) {
        }

        @Override
        public NarrationElementOutput nest() {
            return this;
        }

        @Override
        public NarrationTrigger narrationTrigger() {
            return NarrationTrigger.SYSTEM;
        }
    }

    private static final class ProbeHost implements ConfigTabHost, TuneHost {
        private final ConfiguratorScreen screen;
        private final List<AbstractWidget> widgets = new ArrayList<>();

        private ProbeHost(ConfiguratorScreen screen) {
            this.screen = screen;
        }

        @Override
        public <T extends AbstractWidget> T addWidget(T widget) {
            this.widgets.add(widget);
            return widget;
        }

        @Override
        public Font font() {
            return this.screen.font();
        }

        @Override
        public Minecraft minecraft() {
            return this.screen.minecraft();
        }

        @Override
        public Screen screen() {
            return this.screen;
        }

        @Override
        public void rebuild() {
        }

        @Override
        public void changed() {
        }

        @Override
        public TuneModel tune() {
            return this.screen.tune();
        }

        @Override
        public DroneContext context() {
            return this.screen.context();
        }
    }
}
