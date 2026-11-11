package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.Constants;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public final class NamespaceRules {
    public static final String RESERVED = "propwash";
    public static final String UNKNOWN_MOD = "unknown mod";
    private static final String OWN_PACKAGE = "de.lardtom.propwash.";
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Set<Refusal> REFUSALS = new LinkedHashSet<>();
    private static final List<Consumer<Refusal>> LISTENERS = new ArrayList<>();
    private static volatile Function<Class<?>, Optional<String>> modResolver = type -> Optional.empty();

    private NamespaceRules() {
    }

    public record Refusal(String mod, String id, String what, boolean thrown) {
        public Refusal {
            Objects.requireNonNull(mod, "mod");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(what, "what");
        }

        public String message() {
            return NamespaceRules.message(this.mod, this.id, this.what);
        }
    }

    public static boolean isReserved(String namespace) {
        return RESERVED.equals(namespace);
    }

    public static boolean isReservedId(String id) {
        return id != null && isReserved(namespaceOf(id));
    }

    public static String namespaceOf(String id) {
        Objects.requireNonNull(id, "id");
        int colon = id.indexOf(':');
        return colon < 0 ? "" : id.substring(0, colon);
    }

    public static String message(String mod, String id, String what) {
        return "Mod '" + mod + "' tried to register '" + id + "' (" + what + "): the namespace " + RESERVED
                + " is reserved for Propwash. Use your mod id.";
    }

    public static String requireForeign(String id, String what) {
        return isReservedId(id) ? requireForeign(id, what, callerMod()) : Objects.requireNonNull(id, "id");
    }

    public static String requireForeign(String id, String what, String mod) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(what, "what");
        if (isReservedId(id)) {
            Refusal refusal = new Refusal(Objects.requireNonNullElse(mod, UNKNOWN_MOD), id, what, true);
            record(refusal);
            throw new IllegalArgumentException(refusal.message());
        }
        return id;
    }

    public static boolean refuse(String id, String what) {
        return isReservedId(id) && refuse(id, what, callerMod());
    }

    public static boolean refuse(String id, String what, String mod) {
        Objects.requireNonNull(what, "what");
        if (id == null || !isReservedId(id)) {
            return false;
        }
        Refusal refusal = new Refusal(Objects.requireNonNullElse(mod, UNKNOWN_MOD), id, what, false);
        if (record(refusal)) {
            Constants.LOG.error(refusal.message());
        }
        return true;
    }

    public static String callerMod() {
        Optional<Class<?>> caller = WALKER.walk(frames -> frames
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> !type.getName().startsWith(OWN_PACKAGE))
                .findFirst());
        if (caller.isEmpty()) {
            return UNKNOWN_MOD;
        }
        try {
            return modResolver.apply(caller.get()).filter(mod -> !mod.isBlank()).orElse(UNKNOWN_MOD);
        } catch (RuntimeException e) {
            return UNKNOWN_MOD;
        }
    }

    public static void setModResolver(Function<Class<?>, Optional<String>> resolver) {
        modResolver = Objects.requireNonNull(resolver, "resolver");
    }

    public static synchronized void onRefusal(Consumer<Refusal> listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    public static synchronized List<Refusal> refusals() {
        return List.copyOf(REFUSALS);
    }

    public static synchronized void reset() {
        REFUSALS.clear();
        LISTENERS.clear();
        modResolver = type -> Optional.empty();
    }

    private static boolean record(Refusal refusal) {
        List<Consumer<Refusal>> listeners;
        synchronized (NamespaceRules.class) {
            if (!REFUSALS.add(refusal)) {
                return false;
            }
            listeners = List.copyOf(LISTENERS);
        }
        for (Consumer<Refusal> listener : listeners) {
            try {
                listener.accept(refusal);
            } catch (RuntimeException e) {
                Constants.LOG.warn("Namespace check: listener failed", e);
            }
        }
        return true;
    }
}
