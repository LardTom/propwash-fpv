package de.lardtom.propwash.build;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class PartAvailability {
    private static final Set<String> ALWAYS_ACTIVE = Set.of(PartIndex.DEFAULT_NAMESPACE, Identifier.DEFAULT_NAMESPACE);

    private static volatile Set<String> inactive = Set.of();

    private PartAvailability() {
    }

    public static boolean filtering() {
        return !inactive.isEmpty();
    }

    public static Set<String> inactiveNamespaces() {
        return inactive;
    }

    public static boolean active(@Nullable String namespace) {
        Set<String> current = inactive;
        return current.isEmpty() || namespace == null || !current.contains(namespace);
    }

    public static boolean active(@Nullable Identifier id) {
        return id == null || active(id.getNamespace());
    }

    public static boolean activeKey(@Nullable String key) {
        if (!filtering() || key == null) {
            return true;
        }
        Identifier id = PartIndex.idOfKey(key);
        return id == null || active(id);
    }

    public static boolean activeNamespacedId(@Nullable String id) {
        if (!filtering() || id == null) {
            return true;
        }
        int colon = id.indexOf(':');
        return colon <= 0 || active(id.substring(0, colon));
    }

    public static synchronized void set(Collection<String> namespaces) {
        TreeSet<String> clean = new TreeSet<>();
        for (String namespace : namespaces) {
            if (namespace != null && !namespace.isEmpty() && !ALWAYS_ACTIVE.contains(namespace)) {
                clean.add(namespace);
            }
        }
        inactive = Set.copyOf(clean);
    }

    public static void clear() {
        set(List.of());
    }
}
