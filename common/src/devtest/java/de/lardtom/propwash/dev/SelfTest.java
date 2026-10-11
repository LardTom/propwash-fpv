package de.lardtom.propwash.dev;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class SelfTest {
    public static final String PROPERTY = "propwash.selftest";
    public static final String SMOKE_PROPERTY = "propwash.smoketest";
    public static final String ONLY_PROPERTY = "propwash.selftest.only";
    public static final String LIST_PROPERTY = "propwash.selftest.list";
    public static final String RESULT_FILE = "propwash-selftest-result.txt";
    public static final String STEPS_FILE = "propwash-selftest-steps.md";
    public static final String ONLY_ENV = "PROPWASH_SELFTEST_ONLY";

    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final boolean SMOKE = !ENABLED && Boolean.getBoolean(SMOKE_PROPERTY);
    private static final boolean LIST = ENABLED && Boolean.getBoolean(LIST_PROPERTY);
    private static final Set<String> HELPER_CLASSES = Set.of("SelfTest", "Screenshots");
    private static final List<Step> CLIENT_STEPS = new ArrayList<>();
    private static final List<Step> AFTER_LEAVE_STEPS = new ArrayList<>();
    private static final Set<String> NAMES = new HashSet<>();
    private static final Set<String> SETUP = new HashSet<>();
    private static final Set<String> BASE = new HashSet<>();
    private static final Map<String, String> SWITCHES = new HashMap<>();
    private static final Map<String, String> OWNERS = new HashMap<>();
    private static boolean started;

    private SelfTest() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static boolean smokeEnabled() {
        return SMOKE;
    }

    public static boolean listOnly() {
        return LIST;
    }

    public static void clientStep(String name, int maxTicks, ClientStep body) {
        add(CLIENT_STEPS, name, maxTicks, body);
    }

    public static void setupStep(String name, int maxTicks, ClientStep body) {
        add(CLIENT_STEPS, name, maxTicks, body);
        SETUP.add(name);
    }

    public static void baseStep(String name, int maxTicks, ClientStep body) {
        add(CLIENT_STEPS, name, maxTicks, body);
        BASE.add(name);
    }

    public static void switchStep(String name, String until, int maxTicks, ClientStep body) {
        add(CLIENT_STEPS, name, maxTicks, body);
        SWITCHES.put(name, until);
    }

    public static void afterLeaveStep(String name, int maxTicks, ClientStep body) {
        add(AFTER_LEAVE_STEPS, name, maxTicks, body);
    }

    private static void add(List<Step> target, String name, int maxTicks, ClientStep body) {
        Objects.requireNonNull(body, "body");
        if (maxTicks <= 0) {
            throw new IllegalArgumentException("maxTicks muss > 0 sein");
        }
        if (started) {
            throw new IllegalStateException("Selbsttest-Schritt '" + name + "' zu spät registriert");
        }
        if (!NAMES.add(name)) {
            throw new IllegalArgumentException("Doppelter Selbsttest-Schritt: " + name);
        }
        target.add(new Step(name, maxTicks, body));
        OWNERS.put(name, owner());
    }

    private static String owner() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .map(frame -> {
                    Class<?> type = frame.getDeclaringClass();
                    while (type.getEnclosingClass() != null) {
                        type = type.getEnclosingClass();
                    }
                    return type.getSimpleName();
                })
                .filter(name -> !HELPER_CLASSES.contains(name))
                .findFirst()
                .orElse("?"));
    }

    public static List<Step> startClientSteps() {
        started = true;
        return filtered(CLIENT_STEPS);
    }

    public static List<Step> startAfterLeaveSteps() {
        started = true;
        return filtered(AFTER_LEAVE_STEPS);
    }

    public static @Nullable String filter() {
        String only = System.getProperty(ONLY_PROPERTY);
        if (only == null || only.isBlank()) {
            only = System.getenv(ONLY_ENV);
        }
        return only == null || only.isBlank() ? null : only.trim();
    }

    public static int registeredCount() {
        return CLIENT_STEPS.size() + AFTER_LEAVE_STEPS.size();
    }

    public static int selectedCount() {
        Selection selection = Selection.of(filter());
        int count = 0;
        for (Step step : all()) {
            if (selection.keeps(step.name())) {
                count++;
            }
        }
        return count;
    }

    public static boolean filterMatchesNothing() {
        Selection selection = Selection.of(filter());
        return selection.active() && !selection.any();
    }

    private static List<Step> filtered(List<Step> steps) {
        Selection selection = Selection.of(filter());
        if (!selection.active()) {
            return Collections.unmodifiableList(steps);
        }
        List<Step> kept = new ArrayList<>();
        for (Step step : steps) {
            if (selection.keeps(step.name())) {
                kept.add(step);
            }
        }
        return Collections.unmodifiableList(kept);
    }

    private static List<Step> all() {
        List<Step> steps = new ArrayList<>(CLIENT_STEPS);
        steps.addAll(AFTER_LEAVE_STEPS);
        return steps;
    }

    public static String group(String name) {
        int colon = name.indexOf(':');
        return colon < 0 ? name.trim() : name.substring(0, colon).trim();
    }

    public static String describe(String platform) {
        Map<String, List<String>> owners = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (Step step : all()) {
            String group = group(step.name());
            counts.computeIfAbsent(group, key -> new int[2])[CLIENT_STEPS.contains(step) ? 0 : 1]++;
            List<String> classes = owners.computeIfAbsent(group, key -> new ArrayList<>());
            String owner = OWNERS.getOrDefault(step.name(), "?");
            if (!classes.contains(owner)) {
                classes.add(owner);
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("# Selbsttest-Schritte (").append(platform).append(", ").append(registeredCount()).append(" Schritte)\n\n");
        out.append("| Präfix | in der Welt | nach dem Verlassen | angemeldet in |\n");
        out.append("|---|---:|---:|---|\n");
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            out.append("| `").append(entry.getKey()).append(":` | ").append(entry.getValue()[0]).append(" | ")
                    .append(entry.getValue()[1]).append(" | ").append(String.join(", ", owners.get(entry.getKey()))).append(" |\n");
        }
        out.append("\n## Alle Schritte in Reihenfolge\n\n");
        appendSteps(out, "In der Welt", CLIENT_STEPS);
        appendSteps(out, "Nach dem Verlassen der Welt", AFTER_LEAVE_STEPS);
        return out.toString();
    }

    private static void appendSteps(StringBuilder out, String title, List<Step> steps) {
        out.append("### ").append(title).append("\n\n");
        for (Step step : steps) {
            out.append("- ").append(step.name());
            if (BASE.contains(step.name())) {
                out.append(" *(läuft immer)*");
            } else if (SETUP.contains(step.name())) {
                out.append(" *(läuft mit jeder Auswahl aus `").append(group(step.name())).append(":`)*");
            } else if (SWITCHES.containsKey(step.name())) {
                out.append(" *(läuft, wenn ein ausgewählter Schritt vor „").append(SWITCHES.get(step.name())).append("“ folgt, samt diesem)*");
            }
            out.append('\n');
        }
        out.append('\n');
    }

    private static boolean framing(String name) {
        return SETUP.contains(name) || BASE.contains(name) || SWITCHES.containsKey(name);
    }

    private record Selection(List<String> include, List<String> exclude, Set<String> groups, Set<String> switched, boolean any) {
        static Selection of(@Nullable String filter) {
            List<String> include = new ArrayList<>();
            List<String> exclude = new ArrayList<>();
            if (filter != null) {
                for (String token : filter.split("[,|]")) {
                    String prefix = token.trim();
                    if (prefix.startsWith("!")) {
                        prefix = prefix.substring(1).trim();
                        if (!prefix.isEmpty()) {
                            exclude.add(prefix);
                        }
                    } else if (!prefix.isEmpty()) {
                        include.add(prefix);
                    }
                }
            }
            Set<String> groups = new HashSet<>();
            boolean any = false;
            for (Step step : all()) {
                if (matches(include, exclude, step.name())) {
                    any = true;
                    if (!framing(step.name())) {
                        groups.add(group(step.name()));
                    }
                }
            }
            Set<String> switched = new HashSet<>();
            for (List<Step> steps : List.of(CLIENT_STEPS, AFTER_LEAVE_STEPS)) {
                for (int i = 0; i < steps.size(); i++) {
                    String name = steps.get(i).name();
                    String until = SWITCHES.get(name);
                    if (until == null) {
                        continue;
                    }
                    boolean keep = matches(include, exclude, name);
                    for (int j = i + 1; j < steps.size() && !keep; j++) {
                        String other = steps.get(j).name();
                        if (other.equals(until)) {
                            break;
                        }
                        keep = !framing(other) && matches(include, exclude, other);
                    }
                    if (keep) {
                        switched.add(name);
                        switched.add(until);
                    }
                }
            }
            return new Selection(include, exclude, groups, switched, any);
        }

        boolean active() {
            return !include.isEmpty() || !exclude.isEmpty();
        }

        boolean keeps(String name) {
            if (!active() || matches(include, exclude, name)) {
                return true;
            }
            if (exclude.stream().anyMatch(prefix -> startsWith(name, prefix))) {
                return false;
            }
            if (switched.contains(name)) {
                return true;
            }
            if (BASE.contains(name)) {
                return any;
            }
            return SETUP.contains(name) && groups.contains(group(name));
        }

        private static boolean matches(List<String> include, List<String> exclude, String name) {
            return (include.isEmpty() || include.stream().anyMatch(prefix -> startsWith(name, prefix)))
                    && exclude.stream().noneMatch(prefix -> startsWith(name, prefix));
        }

        private static boolean startsWith(String name, String prefix) {
            return name.regionMatches(true, 0, prefix, 0, prefix.length());
        }
    }

    public record Step(String name, int maxTicks, ClientStep body) {
    }

    @FunctionalInterface
    public interface ClientStep {
        StepResult tick(StepContext context);
    }

    public interface StepContext {
        int tick();

        @Nullable MinecraftServer server();

        CompletableFuture<Void> onServer(Consumer<MinecraftServer> action);

        Map<String, Object> state();

        void log(String message);
    }

    public record StepResult(Status status, String message) {
        public enum Status { PENDING, PASS, FAIL }

        private static final StepResult PENDING = new StepResult(Status.PENDING, "");
        private static final StepResult PASS = new StepResult(Status.PASS, "");

        public static StepResult pending() {
            return PENDING;
        }

        public static StepResult pass() {
            return PASS;
        }

        public static StepResult fail(String reason) {
            return new StepResult(Status.FAIL, reason);
        }

        public static StepResult check(boolean condition, String reason) {
            return condition ? PASS : fail(reason);
        }
    }

    public static Map<String, Object> newState() {
        return new HashMap<>();
    }
}
