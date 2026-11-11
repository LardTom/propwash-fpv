package de.lardtom.propwash.content;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public final class CreativeTabNamespaces {
    private static final Set<String> SHARED = Set.of(Constants.MOD_ID, Identifier.DEFAULT_NAMESPACE);

    private CreativeTabNamespaces() {
    }

    public static Predicate<String> ownTab(Iterable<Identifier> registeredTabs, Predicate<String> extensionTab) {
        Set<String> registered = withOwnTab(registeredTabs);
        return namespace -> namespace != null && !SHARED.contains(namespace)
                && (registered.contains(namespace) || extensionTab.test(namespace));
    }

    public static Set<String> withOwnTab(Iterable<Identifier> registeredTabs) {
        Set<String> namespaces = new HashSet<>();
        for (Identifier tab : registeredTabs) {
            String namespace = owner(tab);
            if (!SHARED.contains(namespace)) {
                namespaces.add(namespace);
            }
        }
        return namespaces;
    }

    public static String owner(Identifier tab) {
        String path = tab.getPath();
        if (Constants.MOD_ID.equals(tab.getNamespace()) && path.startsWith(ExtensionTabs.PATH_PREFIX)
                && path.length() > ExtensionTabs.PATH_PREFIX.length()) {
            return path.substring(ExtensionTabs.PATH_PREFIX.length());
        }
        return tab.getNamespace();
    }
}
