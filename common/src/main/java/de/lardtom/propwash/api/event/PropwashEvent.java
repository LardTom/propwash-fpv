package de.lardtom.propwash.api.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class PropwashEvent<T> {
    private final String name;
    private final List<T> listeners = new CopyOnWriteArrayList<>();

    public PropwashEvent(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public void register(T listener) {
        this.listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public boolean hasListeners() {
        return !this.listeners.isEmpty();
    }

    public void fire(Consumer<? super T> action) {
        for (T listener : this.listeners) {
            action.accept(listener);
        }
    }

    public String name() {
        return this.name;
    }

    @Override
    public String toString() {
        return "PropwashEvent[" + this.name + ", " + this.listeners.size() + " Listener]";
    }
}
