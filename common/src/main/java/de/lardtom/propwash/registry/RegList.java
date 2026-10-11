package de.lardtom.propwash.registry;

import de.lardtom.propwash.Constants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class RegList<R> {
    private final ResourceKey<? extends Registry<R>> registryKey;
    private final List<Entry<R, ?>> entries = new ArrayList<>();
    private final Set<String> paths = new HashSet<>();
    private boolean flushed;

    RegList(ResourceKey<? extends Registry<R>> registryKey) {
        this.registryKey = registryKey;
    }

    public ResourceKey<? extends Registry<R>> registryKey() {
        return this.registryKey;
    }

    public <T extends R> RegHolder<T> add(String path, Function<ResourceKey<R>, T> factory) {
        if (this.flushed) {
            throw new IllegalStateException("Register " + this.registryKey.identifier() + " ist bereits geschlossen, '" + path
                    + "' muss in der Common-init() des Moduls angemeldet werden");
        }
        if (PropwashRegistries.isClosedAfterCommonInit()) {
            throw new IllegalStateException(PropwashRegistries.lateRegistrationHint("'" + path + "' (" + this.registryKey.identifier() + ")"));
        }
        if (!this.paths.add(path)) {
            throw new IllegalArgumentException("Doppelter Registereintrag " + Constants.MOD_ID + ":" + path + " in " + this.registryKey.identifier());
        }
        ResourceKey<R> key = ResourceKey.create(this.registryKey, Constants.id(path));
        Entry<R, T> entry = new Entry<>(key, factory, new RegHolder<>(key));
        this.entries.add(entry);
        return entry.holder;
    }

    public void flush(BiConsumer<ResourceKey<R>, R> sink) {
        if (this.flushed) {
            throw new IllegalStateException("Register " + this.registryKey.identifier() + " doppelt geflusht");
        }
        this.flushed = true;
        for (Entry<R, ?> entry : this.entries) {
            entry.create(sink);
        }
    }

    public boolean isFlushed() {
        return this.flushed;
    }

    public int size() {
        return this.entries.size();
    }

    private record Entry<R, T extends R>(ResourceKey<R> key, Function<ResourceKey<R>, T> factory, RegHolder<T> holder) {
        void create(BiConsumer<ResourceKey<R>, R> sink) {
            T value = this.factory.apply(this.key);
            sink.accept(this.key, value);
            this.holder.bind(value);
        }
    }
}
