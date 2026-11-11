package de.lardtom.propwash.api.worldrule;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public interface WorldRule<T> {
    enum Type {
        BOOLEAN,
        INTEGER
    }

    Identifier id();

    Type type();

    T defaultValue();

    int min();

    int max();

    T get(Level level);

    default String nameKey() {
        return "gamerule." + this.id().getNamespace() + "." + this.id().getPath();
    }

    default String descriptionKey() {
        return this.nameKey() + ".description";
    }
}
