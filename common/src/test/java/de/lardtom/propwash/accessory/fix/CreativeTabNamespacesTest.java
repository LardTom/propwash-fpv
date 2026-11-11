package de.lardtom.propwash.accessory.fix;

import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.content.CreativeTabNamespaces;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeTabNamespacesTest {
    private static final List<Identifier> VANILLA = List.of(Identifier.withDefaultNamespace("building_blocks"),
            Identifier.withDefaultNamespace("combat"), Identifier.withDefaultNamespace("search"),
            Identifier.withDefaultNamespace("spawn_eggs"), Identifier.withDefaultNamespace("op_blocks"));
    private static final Predicate<String> NO_EXTENSION = namespace -> false;

    private static List<Identifier> registry(Identifier... extra) {
        List<Identifier> tabs = new ArrayList<>(VANILLA);
        tabs.add(ExtensionTabs.MAIN_TAB);
        tabs.addAll(List.of(extra));
        return tabs;
    }

    @Test
    void addonWithRegisteredExtensionTabIsHiddenFromTheMainTab() {
        Predicate<String> ownTab = CreativeTabNamespaces.ownTab(registry(ExtensionTabs.tabId("guard_addon")), "guard_addon"::equals);
        assertTrue(ownTab.test("guard_addon"));
        Predicate<String> fromRegistryOnly = CreativeTabNamespaces.ownTab(registry(ExtensionTabs.tabId("guard_addon")), NO_EXTENSION);
        assertTrue(fromRegistryOnly.test("guard_addon"));
        Predicate<String> fromExtensionOnly = CreativeTabNamespaces.ownTab(registry(), "guard_addon"::equals);
        assertTrue(fromExtensionOnly.test("guard_addon"));
    }

    @Test
    void addonWithDirectlyRegisteredTabIsHiddenToo() {
        Predicate<String> ownTab = CreativeTabNamespaces.ownTab(registry(Identifier.fromNamespaceAndPath("direct_addon", "main"),
                Identifier.fromNamespaceAndPath("zzz.more-parts", "items")), NO_EXTENSION);
        assertTrue(ownTab.test("direct_addon"));
        assertTrue(ownTab.test("zzz.more-parts"));
        assertFalse(ownTab.test("other_addon"));
    }

    @Test
    void addonWithoutAnyOwnTabStaysInTheMainTab() {
        Predicate<String> ownTab = CreativeTabNamespaces.ownTab(registry(ExtensionTabs.tabId("listed")), "listed"::equals);
        assertFalse(ownTab.test("fallback_addon"));
        assertFalse(ownTab.test("late_addon"));
        assertFalse(ownTab.test(null));
        assertFalse(CreativeTabNamespaces.ownTab(registry(), NO_EXTENSION).test("unregistered_extension"));
    }

    @Test
    void propwashAndMinecraftNeverCountAsOwnTab() {
        Predicate<String> always = namespace -> true;
        Predicate<String> ownTab = CreativeTabNamespaces.ownTab(registry(ExtensionTabs.tabId("guard_addon")), always);
        assertFalse(ownTab.test("propwash"));
        assertFalse(ownTab.test("minecraft"));
        assertTrue(ownTab.test("guard_addon"));
        assertEquals(Set.of("guard_addon"), CreativeTabNamespaces.withOwnTab(registry(ExtensionTabs.tabId("guard_addon"))));
    }

    @Test
    void extensionTabIdsBelongToTheirAddonNamespace() {
        assertEquals("propwash", CreativeTabNamespaces.owner(ExtensionTabs.MAIN_TAB));
        assertEquals("justmoreparts", CreativeTabNamespaces.owner(ExtensionTabs.tabId("justmoreparts")));
        assertEquals("zzz.more-parts", CreativeTabNamespaces.owner(ExtensionTabs.tabId("zzz.more-parts")));
        assertEquals("propwash", CreativeTabNamespaces.owner(Identifier.fromNamespaceAndPath("propwash", "main-")));
        assertEquals("propwash", CreativeTabNamespaces.owner(Identifier.fromNamespaceAndPath("propwash", "mainline")));
        assertEquals("camera_addon", CreativeTabNamespaces.owner(Identifier.fromNamespaceAndPath("camera_addon", "main-propwash_extra")));
        assertEquals("minecraft", CreativeTabNamespaces.owner(Identifier.withDefaultNamespace("building_blocks")));
    }
}
