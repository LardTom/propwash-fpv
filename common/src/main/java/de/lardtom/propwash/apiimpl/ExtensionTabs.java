package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.api.registry.TabSection;
import de.lardtom.propwash.content.ContentTab;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ExtensionTabs {
    public static final String MAIN_PATH = "main";
    public static final String PATH_PREFIX = MAIN_PATH + "-";
    public static final Identifier MAIN_TAB = Identifier.fromNamespaceAndPath(Constants.MOD_ID, MAIN_PATH);
    public static final ExtensionTabs INSTANCE = new ExtensionTabs(ContentTab::extensionTab);

    private static final Set<String> RESERVED = Set.of(Constants.MOD_ID, Identifier.DEFAULT_NAMESPACE);

    private final Object lock = new Object();
    private final Function<ExtensionTab, CreativeModeTab> factory;
    private final Map<String, Tab> tabs = new TreeMap<>();
    private @Nullable BiConsumer<ResourceKey<CreativeModeTab>, CreativeModeTab> directSink;
    private boolean flushed;
    private boolean closed;

    ExtensionTabs(Function<ExtensionTab, CreativeModeTab> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public static Identifier tabId(String namespace) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, PATH_PREFIX + checkNamespace(namespace));
    }

    public static String checkNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.isEmpty() || !Identifier.isValidNamespace(namespace)) {
            throw new IllegalArgumentException("Kreativ-Tab: ungültiger Namensraum '" + namespace + "' (nur [a-z0-9_.-])");
        }
        if (RESERVED.contains(namespace)) {
            throw new IllegalArgumentException("Kreativ-Tab: der Namensraum " + namespace + " hat schon seinen Tab");
        }
        return namespace;
    }

    public static String displayName(String modName, String namespace) {
        String name = modName == null ? "" : modName.strip();
        String lower = name.toLowerCase(Locale.ROOT);
        for (String prefix : List.of("propwash fpv:", "propwash:")) {
            if (lower.startsWith(prefix)) {
                name = name.substring(prefix.length()).strip();
                break;
            }
        }
        return name.isEmpty() ? namespace : name;
    }

    public ExtensionTab tab(String namespace) {
        checkNamespace(namespace);
        synchronized (this.lock) {
            Tab existing = this.tabs.get(namespace);
            if (existing != null) {
                return existing;
            }
            if (this.closed) {
                throw new IllegalStateException("Kreativ-Tab für " + namespace + " kommt zu spät: eigene Tabs nur während der"
                        + " Mod-Initialisierung anlegen (Fabric onInitialize, NeoForge Mod-Konstruktor)");
            }
            return this.create(namespace);
        }
    }

    public void touch(String namespace) {
        if (namespace == null || namespace.isEmpty() || RESERVED.contains(namespace) || !Identifier.isValidNamespace(namespace)) {
            return;
        }
        synchronized (this.lock) {
            if (!this.closed && !this.tabs.containsKey(namespace)) {
                this.create(namespace);
            }
        }
    }

    public Optional<ExtensionTab> find(String namespace) {
        synchronized (this.lock) {
            return Optional.ofNullable(this.tabs.get(namespace));
        }
    }

    public List<ExtensionTab> all() {
        synchronized (this.lock) {
            return List.copyOf(this.tabs.values());
        }
    }

    public boolean hasTab(String namespace) {
        synchronized (this.lock) {
            Tab tab = this.tabs.get(namespace);
            return tab != null && tab.registered;
        }
    }

    public void flush(BiConsumer<ResourceKey<CreativeModeTab>, CreativeModeTab> sink, boolean stayOpen) {
        Objects.requireNonNull(sink, "sink");
        synchronized (this.lock) {
            if (this.flushed) {
                throw new IllegalStateException("Addon-Kreativ-Tabs doppelt geflusht");
            }
            this.flushed = true;
            for (Tab tab : this.tabs.values()) {
                this.register(tab, sink);
            }
            if (stayOpen && !this.closed) {
                this.directSink = sink;
            } else {
                this.directSink = null;
                this.closed = true;
            }
        }
    }

    public void close() {
        synchronized (this.lock) {
            this.directSink = null;
            this.closed = true;
        }
    }

    public boolean isClosed() {
        synchronized (this.lock) {
            return this.closed;
        }
    }

    private Tab create(String namespace) {
        Tab tab = new Tab(namespace);
        this.tabs.put(namespace, tab);
        BiConsumer<ResourceKey<CreativeModeTab>, CreativeModeTab> sink = this.directSink;
        if (sink != null) {
            this.register(tab, sink);
        }
        return tab;
    }

    private void register(Tab tab, BiConsumer<ResourceKey<CreativeModeTab>, CreativeModeTab> sink) {
        if (tab.registered) {
            return;
        }
        try {
            sink.accept(ResourceKey.create(Registries.CREATIVE_MODE_TAB, tab.tabId), this.factory.apply(tab));
            tab.registered = true;
        } catch (RuntimeException e) {
            Constants.LOG.error("Kreativ-Tab für {} konnte nicht angelegt werden, seine Einträge bleiben im Propwash-Tab", tab.namespace, e);
        }
    }

    public static final class Tab implements ExtensionTab {
        private final String namespace;
        private final Identifier tabId;
        private final ApiExtensions.Creative entries = new ApiExtensions.Creative();
        private volatile @Nullable Supplier<ItemStack> icon;
        private volatile List<TabSection> sections = TabSection.defaults();
        private volatile boolean registered;

        private Tab(String namespace) {
            this.namespace = namespace;
            this.tabId = ExtensionTabs.tabId(namespace);
        }

        @Override
        public String namespace() {
            return this.namespace;
        }

        @Override
        public Identifier tabId() {
            return this.tabId;
        }

        @Override
        public ExtensionTab icon(Supplier<ItemStack> icon) {
            this.icon = Objects.requireNonNull(icon, "icon");
            return this;
        }

        @Override
        public ExtensionTab icon(Identifier itemId) {
            Objects.requireNonNull(itemId, "itemId");
            this.icon = () -> BuiltInRegistries.ITEM.getOptional(itemId).map(ItemStack::new).orElse(ItemStack.EMPTY);
            return this;
        }

        @Override
        public ExtensionTab order(TabSection... sections) {
            Objects.requireNonNull(sections, "sections");
            this.sections = TabSection.complete(List.of(sections));
            return this;
        }

        @Override
        public List<TabSection> sections() {
            return this.sections;
        }

        @Override
        public boolean registered() {
            return this.registered;
        }

        @Override
        public ItemStack iconStack() {
            return ContentTab.extensionIcon(this, this.icon);
        }

        @Override
        public List<ItemStack> contents() {
            return ContentTab.extensionContents(this);
        }

        @Override
        public void add(Supplier<? extends Collection<ItemStack>> entries) {
            this.entries.add(entries);
        }

        @Override
        public List<ItemStack> collect() {
            return this.entries.collect();
        }

        @Override
        public String toString() {
            return "ExtensionTab[" + this.tabId + (this.registered ? "" : ", nicht registriert") + "]";
        }
    }
}
