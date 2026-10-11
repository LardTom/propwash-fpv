package de.lardtom.propwash.join;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record ClientOnlyAddons(List<AddonInfo> addons, Set<String> namespaces, int parts) {
    public static final ClientOnlyAddons NONE = new ClientOnlyAddons(List.of(), Set.of(), 0);

    public ClientOnlyAddons {
        addons = List.copyOf(addons);
        namespaces = Set.copyOf(namespaces);
    }

    public boolean isEmpty() {
        return this.namespaces.isEmpty();
    }

    public boolean covers(String namespace) {
        return this.namespaces.contains(namespace);
    }

    public boolean covers(PartRef part) {
        return this.namespaces.contains(part.namespace());
    }

    public List<String> sortedNamespaces() {
        return List.copyOf(new TreeSet<>(this.namespaces));
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        Set<String> named = new HashSet<>();
        for (AddonInfo addon : this.addons) {
            out.add(addon.displayName());
            named.add(addon.modId());
            named.addAll(addon.namespaces());
        }
        for (String namespace : this.sortedNamespaces()) {
            if (!named.contains(namespace)) {
                out.add(namespace);
            }
        }
        return List.copyOf(out);
    }

    public static ClientOnlyAddons compute(JoinInfo server, JoinInfo client, List<PartRef> serverParts, List<PartRef> clientParts) {
        Set<String> serverKeys = new HashSet<>();
        Set<String> serverNamespaces = new HashSet<>();
        for (PartRef part : serverParts) {
            serverKeys.add(part.key());
            serverNamespaces.add(part.namespace());
        }
        Map<String, Integer> extra = new TreeMap<>();
        for (PartRef part : clientParts) {
            if (!serverKeys.contains(part.key())) {
                extra.merge(part.namespace(), 1, Integer::sum);
            }
        }
        Map<String, AddonInfo> addons = new TreeMap<>();
        Set<String> namespaces = new TreeSet<>();
        int parts = 0;
        for (Map.Entry<String, Integer> entry : extra.entrySet()) {
            String namespace = entry.getKey();
            if (!unknownToServer(server, serverNamespaces, namespace)) {
                continue;
            }
            Optional<AddonInfo> owner = client.owner(namespace);
            if (owner.isPresent() && server.addon(owner.get().modId()).isPresent()) {
                continue;
            }
            namespaces.add(namespace);
            parts += entry.getValue();
            owner.ifPresent(addon -> addons.put(addon.modId(), addon));
        }
        for (AddonInfo addon : addons.values()) {
            if (unknownToServer(server, serverNamespaces, addon.modId())) {
                namespaces.add(addon.modId());
            }
            for (String namespace : addon.namespaces()) {
                if (unknownToServer(server, serverNamespaces, namespace)) {
                    namespaces.add(namespace);
                }
            }
        }
        if (namespaces.isEmpty()) {
            return NONE;
        }
        return new ClientOnlyAddons(new ArrayList<>(addons.values()), namespaces, parts);
    }

    private static boolean unknownToServer(JoinInfo server, Set<String> serverNamespaces, String namespace) {
        return !JoinReport.PROPWASH_ID.equals(namespace) && !"minecraft".equals(namespace) && !serverNamespaces.contains(namespace)
                && server.owner(namespace).isEmpty();
    }
}
