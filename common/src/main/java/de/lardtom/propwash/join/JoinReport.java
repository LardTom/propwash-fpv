package de.lardtom.propwash.join;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;

public record JoinReport(@Nullable PropwashLine propwash, List<AddonLine> addons, List<UnknownParts> unknown, boolean partsChecked) {
    public static final String PROPWASH_ID = "propwash";

    public JoinReport {
        addons = List.copyOf(addons);
        unknown = List.copyOf(unknown);
    }

    public boolean hasDifferences() {
        return this.propwash != null || !this.addons.isEmpty() || !this.unknown.isEmpty();
    }

    public boolean actionable() {
        return this.blocking() || !this.lines(true).isEmpty();
    }

    public boolean blocking() {
        if (this.propwash != null) {
            return true;
        }
        for (UnknownParts group : this.unknown) {
            if (group.blocking()) {
                return true;
            }
        }
        for (AddonLine line : this.addons) {
            if (line.partsDiffer()) {
                return true;
            }
        }
        return false;
    }

    public Relevance relevance(AddonLine line) {
        if (!this.partsChecked) {
            return line.action() == Action.REMOVE ? Relevance.INFO : Relevance.LIKELY;
        }
        if (line.partsDiffer()) {
            return Relevance.REQUIRED;
        }
        if (this.blocking() || line.action() == Action.REMOVE) {
            return Relevance.INFO;
        }
        return Relevance.LIKELY;
    }

    public List<AddonLine> lines(boolean actionable) {
        List<AddonLine> out = new ArrayList<>();
        for (AddonLine line : this.addons) {
            if ((this.relevance(line) != Relevance.INFO) == actionable) {
                out.add(line);
            }
        }
        return out;
    }

    public JoinReport withPropwash(PropwashLine line) {
        return new JoinReport(line, this.addons, this.unknown, this.partsChecked);
    }

    public int inactiveParts() {
        int count = 0;
        for (AddonLine line : this.addons) {
            count += line.inactive();
        }
        for (UnknownParts group : this.unknown) {
            count += group.inactive().size();
        }
        return count;
    }

    public static JoinReport compute(JoinInfo server, JoinInfo client, @Nullable List<PartRef> serverParts, @Nullable List<PartRef> clientParts) {
        PropwashLine propwash = null;
        if (server.protocol() != client.protocol()) {
            propwash = new PropwashLine(server.propwashVersion(), server.protocol(), client.propwashVersion(), client.protocol(), PropwashLine.Kind.PROTOCOL);
        } else if (!server.builtinFingerprint().isEmpty() && !client.builtinFingerprint().isEmpty()
                && !server.builtinFingerprint().equals(client.builtinFingerprint())) {
            propwash = new PropwashLine(server.propwashVersion(), server.protocol(), client.propwashVersion(), client.protocol(), PropwashLine.Kind.BUILTINS);
        }
        boolean partsChecked = serverParts != null && clientParts != null && propwash == null;
        Map<String, Counter> counters = new TreeMap<>();
        Map<String, UnknownBuilder> unknown = new TreeMap<>();
        if (partsChecked) {
            ClientOnlyAddons clientOnly = ClientOnlyAddons.compute(server, client, serverParts, clientParts);
            Map<String, PartRef> serverByKey = index(serverParts);
            Map<String, PartRef> clientByKey = index(clientParts);
            for (PartRef part : serverByKey.values()) {
                PartRef other = clientByKey.get(part.key());
                if (other == null) {
                    count(server, client, part, counters, unknown, Side.MISSING_ON_CLIENT);
                } else if (!other.hash().equals(part.hash())) {
                    count(server, client, part, counters, unknown, Side.DIFFERENT);
                }
            }
            for (PartRef part : clientByKey.values()) {
                if (!serverByKey.containsKey(part.key())) {
                    count(server, client, part, counters, unknown, clientOnly.covers(part) ? Side.INACTIVE : Side.MISSING_ON_SERVER);
                }
            }
        }
        TreeSet<String> ids = new TreeSet<>();
        for (AddonInfo addon : server.addons()) {
            ids.add(addon.modId());
        }
        for (AddonInfo addon : client.addons()) {
            ids.add(addon.modId());
        }
        ids.addAll(counters.keySet());
        ids.remove(PROPWASH_ID);
        List<AddonLine> lines = new ArrayList<>();
        for (String id : ids) {
            Optional<AddonInfo> onServer = server.addon(id);
            Optional<AddonInfo> onClient = client.addon(id);
            Counter counter = counters.getOrDefault(id, new Counter());
            AddonLine line = line(id, onServer.orElse(null), onClient.orElse(null), counter);
            if (line != null) {
                lines.add(line);
            }
        }
        List<UnknownParts> groups = new ArrayList<>();
        for (UnknownBuilder builder : unknown.values()) {
            groups.add(builder.build());
        }
        JoinReport base = new JoinReport(propwash, lines, groups, partsChecked);
        List<AddonLine> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator.comparingInt((AddonLine line) -> base.relevance(line).ordinal())
                .thenComparingInt(line -> line.action().ordinal())
                .thenComparing(line -> line.inactive() == 0)
                .thenComparing(line -> line.name().toLowerCase(Locale.ROOT)));
        return new JoinReport(propwash, ordered, groups, partsChecked);
    }

    private static @Nullable AddonLine line(String id, @Nullable AddonInfo server, @Nullable AddonInfo client, Counter counter) {
        AddonInfo named = server != null ? server : client;
        String name = named != null ? named.displayName() : id;
        String homepage = server != null && !server.homepage().isEmpty() ? server.homepage() : client != null ? client.homepage() : "";
        String serverVersion = server == null ? null : server.version();
        String clientVersion = client == null ? null : client.version();
        Action action;
        if (server != null && client == null) {
            action = Action.INSTALL;
        } else if (server == null && client != null) {
            action = Action.REMOVE;
        } else if (server != null) {
            if (!server.version().equals(client.version())) {
                OptionalInt order = VersionOrder.compare(client.version(), server.version());
                action = order.isPresent() && order.getAsInt() < 0 ? Action.UPDATE : Action.SWITCH;
            } else if (counter.any()) {
                action = Action.REINSTALL;
            } else {
                return null;
            }
        } else {
            action = (counter.missingOnServer > 0 || counter.inactive > 0) && counter.missingOnClient == 0 ? Action.REMOVE : Action.INSTALL;
        }
        boolean providesParts = server != null && !server.namespaces().isEmpty() || client != null && !client.namespaces().isEmpty();
        return new AddonLine(id, name, serverVersion, clientVersion, homepage, action, counter.missingOnClient, counter.missingOnServer,
                counter.different, counter.inactive, providesParts || counter.any() || counter.inactive > 0);
    }

    private static void count(JoinInfo server, JoinInfo client, PartRef part, Map<String, Counter> counters, Map<String, UnknownBuilder> unknown,
                              Side side) {
        String namespace = part.namespace();
        Optional<AddonInfo> owner = server.owner(namespace).or(() -> client.owner(namespace));
        if (owner.isEmpty()) {
            UnknownBuilder builder = unknown.computeIfAbsent(namespace, UnknownBuilder::new);
            switch (side) {
                case MISSING_ON_CLIENT -> builder.missingOnClient.add(part.id());
                case MISSING_ON_SERVER -> builder.missingOnServer.add(part.id());
                case DIFFERENT -> builder.different.add(part.id());
                case INACTIVE -> builder.inactive.add(part.id());
            }
            return;
        }
        Counter counter = counters.computeIfAbsent(owner.get().modId(), key -> new Counter());
        switch (side) {
            case MISSING_ON_CLIENT -> counter.missingOnClient++;
            case MISSING_ON_SERVER -> counter.missingOnServer++;
            case DIFFERENT -> counter.different++;
            case INACTIVE -> counter.inactive++;
        }
    }

    private static Map<String, PartRef> index(List<PartRef> parts) {
        Map<String, PartRef> out = new LinkedHashMap<>();
        for (PartRef part : parts) {
            out.put(part.key(), part);
        }
        return out;
    }

    private enum Side { MISSING_ON_CLIENT, MISSING_ON_SERVER, DIFFERENT, INACTIVE }

    private static final class Counter {
        int missingOnClient;
        int missingOnServer;
        int different;
        int inactive;

        boolean any() {
            return this.missingOnClient + this.missingOnServer + this.different > 0;
        }
    }

    private static final class UnknownBuilder {
        final String namespace;
        final List<String> missingOnClient = new ArrayList<>();
        final List<String> missingOnServer = new ArrayList<>();
        final List<String> different = new ArrayList<>();
        final List<String> inactive = new ArrayList<>();

        UnknownBuilder(String namespace) {
            this.namespace = namespace;
        }

        UnknownParts build() {
            return new UnknownParts(this.namespace, sorted(this.missingOnClient), sorted(this.missingOnServer), sorted(this.different),
                    sorted(this.inactive));
        }

        private static List<String> sorted(List<String> ids) {
            List<String> copy = new ArrayList<>(ids);
            copy.sort(String::compareTo);
            return copy;
        }
    }

    public enum Relevance { REQUIRED, LIKELY, INFO }

    public enum Action { INSTALL, UPDATE, SWITCH, REINSTALL, REMOVE }

    public record PropwashLine(String serverVersion, int serverProtocol, String clientVersion, int clientProtocol, Kind kind) {
        public enum Kind { PROTOCOL, BUILTINS }
    }

    public record AddonLine(String modId, String name, @Nullable String serverVersion, @Nullable String clientVersion, String homepage,
                            Action action, int missingOnClient, int missingOnServer, int different, int inactive, boolean providesParts) {
        public boolean partsDiffer() {
            return this.missingOnClient + this.missingOnServer + this.different > 0;
        }
    }

    public record UnknownParts(String namespace, List<String> missingOnClient, List<String> missingOnServer, List<String> different,
                               List<String> inactive) {
        public UnknownParts {
            missingOnClient = List.copyOf(missingOnClient);
            missingOnServer = List.copyOf(missingOnServer);
            different = List.copyOf(different);
            inactive = List.copyOf(inactive);
        }

        public int size() {
            return this.missingOnClient.size() + this.missingOnServer.size() + this.different.size() + this.inactive.size();
        }

        public boolean blocking() {
            return !this.missingOnClient.isEmpty() || !this.missingOnServer.isEmpty() || !this.different.isEmpty();
        }
    }
}
