package de.lardtom.propwash.join;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public record AddonInfo(String modId, String name, String version, String homepage, List<String> namespaces) {
    public static final int MAX_ID = 64;
    public static final int MAX_NAME = 96;
    public static final int MAX_VERSION = 64;
    public static final int MAX_NAMESPACES = 32;

    public AddonInfo {
        modId = SafeText.namespace(modId == null ? "" : modId.strip());
        name = SafeText.clean(name, MAX_NAME);
        version = SafeText.clean(version, MAX_VERSION);
        homepage = SafeText.url(homepage);
        TreeSet<String> clean = new TreeSet<>();
        if (namespaces != null) {
            for (String namespace : namespaces) {
                String value = SafeText.namespace(namespace);
                if (!value.isEmpty() && clean.size() < MAX_NAMESPACES) {
                    clean.add(value);
                }
            }
        }
        namespaces = List.copyOf(new ArrayList<>(clean));
    }

    public String displayName() {
        return this.name.isBlank() ? this.modId : this.name;
    }

    public boolean valid() {
        return !this.modId.isEmpty();
    }
}
