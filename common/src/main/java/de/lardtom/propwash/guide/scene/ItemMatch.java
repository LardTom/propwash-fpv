package de.lardtom.propwash.guide.scene;

import java.util.Set;

public record ItemMatch(Set<String> ids, boolean anyPart) {
    public static final ItemMatch NONE = new ItemMatch(Set.of(), false);

    public ItemMatch {
        ids = Set.copyOf(ids);
    }

    public boolean matches(String itemId, boolean isPart) {
        return this.ids.contains(itemId) || (this.anyPart && isPart);
    }
}
