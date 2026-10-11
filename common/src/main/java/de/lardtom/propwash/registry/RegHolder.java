package de.lardtom.propwash.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class RegHolder<T> implements Supplier<T> {
    private final ResourceKey<?> key;
    private @Nullable T value;

    RegHolder(ResourceKey<?> key) {
        this.key = key;
    }

    public ResourceKey<?> key() {
        return this.key;
    }

    public Identifier id() {
        return this.key.identifier();
    }

    public boolean isBound() {
        return this.value != null;
    }

    @Override
    public T get() {
        T v = this.value;
        if (v == null) {
            throw new IllegalStateException("Registereintrag noch nicht registriert: " + this.key);
        }
        return v;
    }

    void bind(T value) {
        if (this.value != null) {
            throw new IllegalStateException("Registereintrag doppelt gebunden: " + this.key);
        }
        this.value = value;
    }

    @Override
    public String toString() {
        return "RegHolder[" + this.key + (this.value == null ? ", ungebunden]" : "]");
    }
}
