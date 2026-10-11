package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.api.registry.TabSection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionTabsTest {
    private static final Identifier MAIN = Identifier.fromNamespaceAndPath("propwash", "main");
    private static final List<String> ADDONS = List.of("justmoreparts", "propwash_example", "aaa_parts", "zzz.more-parts", "propwash_extra");
    private static final List<Identifier> FOREIGN = List.of(Identifier.fromNamespaceAndPath("aaa", "main"),
            Identifier.fromNamespaceAndPath("create", "base"), Identifier.fromNamespaceAndPath("propwash_addon", "tab"),
            Identifier.fromNamespaceAndPath("quark", "main_tab"), Identifier.fromNamespaceAndPath("zzz", "items"),
            Identifier.fromNamespaceAndPath("mekanism", "main.tab"), Identifier.fromNamespaceAndPath("sophisticatedbackpacks", "a"));

    @Test
    void tabIdsLiveInThePropwashNamespaceRightBehindMain() {
        assertEquals(Identifier.fromNamespaceAndPath("propwash", "main-justmoreparts"), ExtensionTabs.tabId("justmoreparts"));
        assertEquals(MAIN, ExtensionTabs.MAIN_TAB);
        assertEquals("itemGroup.propwash.extension", ExtensionTab.TITLE_KEY);
        for (String namespace : ADDONS) {
            Identifier id = ExtensionTabs.tabId(namespace);
            assertEquals("propwash", id.getNamespace());
            assertTrue(MAIN.getPath().compareTo(id.getPath()) < 0, id.toString());
        }
    }

    @Test
    void fabricOrdersAddonTabsDirectlyAfterPropwash() {
        List<Identifier> tabs = new ArrayList<>(FOREIGN);
        tabs.add(MAIN);
        List<Identifier> own = ownTabs();
        tabs.addAll(own);
        tabs.sort(Comparator.comparing(Identifier::getNamespace).thenComparing(Identifier::getPath));
        int main = tabs.indexOf(MAIN);
        assertEquals(own, tabs.subList(main + 1, main + 1 + own.size()));
    }

    @Test
    void neoForgeOrdersAddonTabsDirectlyAfterPropwash() {
        Identifier lastVanilla = Identifier.withDefaultNamespace("spawn_eggs");
        Map<Identifier, Set<Identifier>> edges = new HashMap<>();
        List<Identifier> nodes = new ArrayList<>(FOREIGN);
        nodes.add(MAIN);
        for (Identifier id : nodes) {
            edge(edges, lastVanilla, id);
        }
        List<Identifier> own = ownTabs();
        for (Identifier id : own) {
            edge(edges, MAIN, id);
        }
        nodes.addAll(own);
        nodes.add(lastVanilla);
        List<Identifier> sorted = topologicalSort(nodes, edges);
        int main = sorted.indexOf(MAIN);
        assertEquals(own, sorted.subList(main + 1, main + 1 + own.size()));
    }

    @Test
    void sectionsKeepTheWantedOrderAndAppendTheRest() {
        List<TabSection> wanted = List.of(TabSection.PRESETS, TabSection.FRAMES, TabSection.MOTORS, TabSection.PROPS, TabSection.STACKS,
                TabSection.BATTERIES, TabSection.VIDEO);
        List<TabSection> complete = TabSection.complete(wanted);
        assertEquals(wanted, complete.subList(0, wanted.size()));
        assertEquals(List.of(TabSection.ACCESSORIES, TabSection.ENTRIES), complete.subList(wanted.size(), complete.size()));
        List<TabSection> twice = TabSection.complete(List.of(TabSection.MOTORS, TabSection.MOTORS, TabSection.ENTRIES));
        assertEquals(TabSection.values().length, twice.size());
        assertEquals(List.of(TabSection.MOTORS, TabSection.ENTRIES, TabSection.PRESETS), twice.subList(0, 3));
        assertEquals(TabSection.PRESETS, TabSection.defaults().getFirst());
        assertEquals(TabSection.ENTRIES, TabSection.defaults().getLast());
        for (PartKind kind : PartKind.values()) {
            TabSection section = TabSection.of(kind).orElseThrow(() -> new AssertionError("kein Abschnitt für " + kind));
            assertEquals(kind, section.kind().orElseThrow());
        }
        assertTrue(TabSection.PRESETS.kind().isEmpty());
        assertTrue(TabSection.ENTRIES.kind().isEmpty());
        assertThrows(NullPointerException.class, () -> TabSection.complete(java.util.Arrays.asList(TabSection.FRAMES, null)));
    }

    @Test
    void namespacesOfPropwashMinecraftAndInvalidOnesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ExtensionTabs.checkNamespace("propwash"));
        assertThrows(IllegalArgumentException.class, () -> ExtensionTabs.checkNamespace("minecraft"));
        assertThrows(IllegalArgumentException.class, () -> ExtensionTabs.checkNamespace("Just More Parts"));
        assertThrows(IllegalArgumentException.class, () -> ExtensionTabs.checkNamespace(""));
        assertThrows(NullPointerException.class, () -> ExtensionTabs.checkNamespace(null));
        assertEquals("zzz.more-parts", ExtensionTabs.checkNamespace("zzz.more-parts"));
    }

    @Test
    void tabsAreQueuedFlushedAndThenRegisteredDirectlyLikeOnFabric() {
        List<String> built = new ArrayList<>();
        List<Identifier> sink = new ArrayList<>();
        ExtensionTabs tabs = new ExtensionTabs(tab -> {
            built.add(tab.namespace());
            return null;
        });
        ExtensionTab jmp = tabs.tab("justmoreparts");
        tabs.touch("zeta");
        tabs.touch("propwash");
        tabs.touch("minecraft");
        tabs.touch("Bad Namespace");
        tabs.touch(null);
        assertSame(jmp, tabs.tab("justmoreparts"));
        assertEquals(List.of("justmoreparts", "zeta"), tabs.all().stream().map(ExtensionTab::namespace).toList());
        assertFalse(jmp.registered());
        assertFalse(tabs.hasTab("justmoreparts"));
        assertTrue(built.isEmpty());

        tabs.flush((key, tab) -> sink.add(key.identifier()), true);
        assertEquals(List.of(ExtensionTabs.tabId("justmoreparts"), ExtensionTabs.tabId("zeta")), sink);
        assertEquals(List.of("justmoreparts", "zeta"), built);
        assertTrue(jmp.registered());
        assertTrue(tabs.hasTab("zeta"));

        ExtensionTab later = tabs.tab("later_addon");
        assertTrue(later.registered());
        tabs.touch("zeta");
        tabs.touch("touched_late");
        assertEquals(4, sink.size());
        assertTrue(tabs.hasTab("touched_late"));
        assertThrows(IllegalStateException.class, () -> tabs.flush((key, tab) -> sink.add(key.identifier()), true));

        tabs.close();
        assertTrue(tabs.isClosed());
        assertThrows(IllegalStateException.class, () -> tabs.tab("too_late"));
        tabs.touch("ignored");
        assertTrue(tabs.find("ignored").isEmpty());
        assertSame(jmp, tabs.tab("justmoreparts"));
        assertEquals(4, sink.size());
    }

    @Test
    void neoForgeFlushClosesAndFailedTabsStayInTheBaseTab() {
        List<Identifier> sink = new ArrayList<>();
        ExtensionTabs tabs = new ExtensionTabs(tab -> null);
        tabs.tab("justmoreparts");
        tabs.tab("broken");
        tabs.flush((key, tab) -> {
            if (key.identifier().getPath().endsWith("broken")) {
                throw new IllegalStateException("Register eingefroren");
            }
            sink.add(key.identifier());
        }, false);
        assertEquals(List.of(ExtensionTabs.tabId("justmoreparts")), sink);
        assertTrue(tabs.hasTab("justmoreparts"));
        assertFalse(tabs.hasTab("broken"));
        assertFalse(tabs.find("broken").orElseThrow().registered());
        assertTrue(tabs.isClosed());
        assertThrows(IllegalStateException.class, () -> tabs.tab("after_register_event"));
    }

    @Test
    void configurationIsFluentAndLazy() {
        ExtensionTabs tabs = new ExtensionTabs(tab -> null);
        ExtensionTab tab = tabs.tab("justmoreparts");
        Supplier<ItemStack> neverCalled = () -> {
            throw new AssertionError("Symbol darf erst beim Zeichnen erzeugt werden");
        };
        assertSame(tab, tab.icon(neverCalled));
        assertSame(tab, tab.icon(Identifier.fromNamespaceAndPath("justmoreparts", "deadcat_5")));
        assertSame(tab, tab.order(TabSection.PRESETS, TabSection.FRAMES));
        assertEquals(List.of(TabSection.PRESETS, TabSection.FRAMES, TabSection.STACKS), tab.sections().subList(0, 3));
        assertEquals(TabSection.values().length, tab.sections().size());
        assertEquals(TabSection.defaults(), tabs.tab("other").sections());
        assertEquals("itemGroup.justmoreparts", tab.nameKey());
        assertEquals("justmoreparts", tab.namespace());
        tab.add(List::of);
        tab.add(() -> {
            throw new IllegalStateException("kaputter Lieferant");
        });
        assertTrue(tab.collect().isEmpty());
        assertThrows(NullPointerException.class, () -> tab.add(null));
    }

    @Test
    void modNamesBecomeTheTabNameWithoutADoublePrefix() {
        assertEquals("Just More Parts", ExtensionTabs.displayName("Propwash: Just More Parts", "justmoreparts"));
        assertEquals("Extra Frames", ExtensionTabs.displayName("Propwash FPV: Extra Frames", "extraframes"));
        assertEquals("Other Mod", ExtensionTabs.displayName("  Other Mod ", "other"));
        assertEquals("fallback", ExtensionTabs.displayName("", "fallback"));
        assertEquals("fallback", ExtensionTabs.displayName(null, "fallback"));
        assertEquals("fallback", ExtensionTabs.displayName("Propwash:", "fallback"));
    }

    private static List<Identifier> ownTabs() {
        return ADDONS.stream().sorted().map(ExtensionTabs::tabId).toList();
    }

    private static void edge(Map<Identifier, Set<Identifier>> edges, Identifier from, Identifier to) {
        edges.computeIfAbsent(from, key -> new HashSet<>()).add(to);
    }

    private static List<Identifier> topologicalSort(List<Identifier> nodes, Map<Identifier, Set<Identifier>> edges) {
        Map<Identifier, Integer> incoming = new HashMap<>();
        for (Identifier node : nodes) {
            incoming.putIfAbsent(node, 0);
        }
        for (Set<Identifier> targets : edges.values()) {
            for (Identifier target : targets) {
                incoming.merge(target, 1, Integer::sum);
            }
        }
        PriorityQueue<Identifier> ready = new PriorityQueue<>(Comparator.naturalOrder());
        for (Identifier node : nodes) {
            if (incoming.get(node) == 0) {
                ready.add(node);
            }
        }
        List<Identifier> sorted = new ArrayList<>();
        while (!ready.isEmpty()) {
            Identifier node = ready.poll();
            sorted.add(node);
            for (Identifier target : edges.getOrDefault(node, Set.of())) {
                if (incoming.merge(target, -1, Integer::sum) == 0) {
                    ready.add(target);
                }
            }
        }
        assertEquals(nodes.size(), sorted.size());
        return sorted;
    }
}
