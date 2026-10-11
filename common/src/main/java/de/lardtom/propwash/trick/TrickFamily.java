package de.lardtom.propwash.trick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public enum TrickFamily {
    FLIPS_ROLLS,
    AIR,
    LOOPS,
    MATTY_SPLIT,
    SPINS,
    DIVES,
    OBSTACLES,
    SBANG_DEX,
    THREE_D;

    private final String key = this.name().toLowerCase(Locale.ROOT);

    public String key() {
        return this.key;
    }

    public String translationKey() {
        return "propwash.trick.family." + this.key;
    }

    public List<TrickType> tricks() {
        List<TrickType> list = new ArrayList<>();
        for (TrickType type : TrickType.REPERTOIRE) {
            if (type.family() == this) {
                list.add(type);
            }
        }
        return Collections.unmodifiableList(list);
    }
}
