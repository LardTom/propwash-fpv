package de.lardtom.propwash.core.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Event<T> {
    private final String name;
    private final List<T> listeners = new CopyOnWriteArrayList<>();

    public Event(String name) {
        this.name = name;
    }

    public void register(T listener) {
        this.listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void fire(Consumer<? super T> action) {
        for (T listener : this.listeners) {
            action.accept(listener);
        }
    }

    public boolean hasListeners() {
        return !this.listeners.isEmpty();
    }

    public String name() {
        return this.name;
    }

    @Override
    public String toString() {
        return "Event[" + this.name + ", " + this.listeners.size() + " Listener]";
    }
}
