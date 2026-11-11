package de.lardtom.propwash.guard;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.apiimpl.NamespaceRules;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.platform.services.ModOrigins;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class NamespaceGuard {
    public static final Set<String> OWN_MODS = Set.of(Constants.MOD_ID, Constants.MOD_ID + "_devtest");
    private static final String OWN_GUARD = "de.lardtom.propwash.guard.";
    private static final String OWN_MIXIN = "de.lardtom.propwash.mixin.guard.";
    private static final String OWN_PACKAGE = "de.lardtom.propwash.";
    private static final String API = "de.lardtom.propwash.api.";
    private static final String RULES = NamespaceRules.class.getName();
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final GuardReport REPORT = new GuardReport();
    private static final Set<GuardCategory> SKIPPED = EnumSet.noneOf(GuardCategory.class);
    private static final AtomicInteger RUNS = new AtomicInteger();
    private static volatile @Nullable ModOrigins origins;

    private NamespaceGuard() {
    }

    public static ModOrigins origins() {
        ModOrigins known = origins;
        if (known == null) {
            known = Services.load(ModOrigins.class);
            origins = known;
        }
        return known;
    }

    static void overrideOrigins(@Nullable ModOrigins value) {
        origins = value;
    }

    public static GuardReport report() {
        return REPORT;
    }

    public static int runs() {
        return RUNS.get();
    }

    public static boolean own(String mod) {
        return OWN_MODS.contains(mod);
    }

    public static boolean own(Optional<String> mod) {
        return mod.isPresent() && own(mod.get());
    }

    public static String modOrUnknown(Optional<String> mod) {
        return mod.filter(id -> !id.isBlank()).orElse(NamespaceRules.UNKNOWN_MOD);
    }

    public static Optional<String> registrant() {
        ModOrigins known = origins();
        Optional<String> found;
        try {
            found = WALKER.walk(frames -> frames
                    .map(StackWalker.StackFrame::getDeclaringClass)
                    .filter(type -> !type.getName().startsWith(OWN_GUARD) && !type.getName().startsWith(OWN_MIXIN))
                    .map(known::modOfClass)
                    .flatMap(Optional::stream)
                    .filter(mod -> !known.platform(mod))
                    .findFirst());
        } catch (RuntimeException | LinkageError e) {
            found = Optional.empty();
        }
        return found.isPresent() ? found : known.activeMod();
    }

    public static Optional<String> modOfClass(Class<?> type) {
        try {
            return origins().modOfClass(type);
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    public static Optional<String> callerMod(Class<?> caller) {
        Optional<String> mod = modOfClass(caller).filter(id -> !origins().platform(id));
        return mod.isPresent() || !ownCall() ? mod : Optional.of(Constants.MOD_ID);
    }

    public static boolean ownCall() {
        try {
            return WALKER.walk(NamespaceGuard::ownCall);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean ownCall(Stream<StackWalker.StackFrame> frames) {
        ModOrigins known = origins();
        List<StackWalker.StackFrame> below = frames
                .dropWhile(frame -> !frame.getClassName().startsWith(RULES))
                .dropWhile(frame -> frame.getClassName().startsWith(RULES))
                .toList();
        int run = 0;
        while (run < below.size() && below.get(run).getClassName().startsWith(OWN_PACKAGE)) {
            run++;
        }
        int entry = -1;
        for (int i = 0; i < run; i++) {
            if (api(below.get(i))) {
                entry = i;
            }
        }
        if (entry < 0 || entry + 1 >= run || !own(modOfClass(below.get(entry + 1).getDeclaringClass()))) {
            return false;
        }
        for (StackWalker.StackFrame frame : below) {
            Optional<String> mod = modOfClass(frame.getDeclaringClass());
            if (mod.isPresent() && !own(mod.get()) && !known.platform(mod.get())) {
                return false;
            }
        }
        return true;
    }

    private static boolean api(StackWalker.StackFrame frame) {
        Class<?> type = frame.getDeclaringClass();
        return type.getName().startsWith(API) || declaredByApi(type, frame.getMethodName(), new HashSet<>());
    }

    private static boolean declaredByApi(@Nullable Class<?> type, String method, Set<Class<?>> seen) {
        if (type == null || !seen.add(type)) {
            return false;
        }
        if (type.getName().startsWith(API)) {
            for (Method declared : type.getDeclaredMethods()) {
                if (declared.getName().equals(method)) {
                    return true;
                }
            }
        }
        for (Class<?> face : type.getInterfaces()) {
            if (declaredByApi(face, method, seen)) {
                return true;
            }
        }
        return declaredByApi(type.getSuperclass(), method, seen);
    }

    public static boolean attributedForeign(Optional<String> mod) {
        return mod.isPresent() && !own(mod.get()) && !origins().platform(mod.get());
    }

    public static synchronized void scan(GuardCategory category, Runnable scan) {
        if (SKIPPED.contains(category)) {
            return;
        }
        try {
            scan.run();
        } catch (RuntimeException | LinkageError e) {
            SKIPPED.add(category);
            Constants.LOG.warn("Namespace check: {} skipped ({})", category.logName(), e.toString());
        }
    }

    public static void finish(String phase, long startNanos) {
        RUNS.incrementAndGet();
        for (String line : REPORT.drainLogLines()) {
            Constants.LOG.error(line);
        }
        for (String line : REPORT.drainMixinLines()) {
            Constants.LOG.info(line);
        }
        long micros = (System.nanoTime() - startNanos) / 1000L;
        Constants.LOG.info("Namespace check ({}): {} findings in {} ms", phase, REPORT.count(), String.format(Locale.ROOT, "%.1f",
                micros / 1000.0));
    }

    public static Map<String, String> modNames(GuardReport report) {
        Map<String, String> out = new TreeMap<>();
        for (String mod : report.mods()) {
            out.put(mod, name(report, mod));
        }
        for (String mod : report.mixins().keySet()) {
            out.put(mod, name(report, mod));
        }
        return out;
    }

    public static String name(GuardReport report, String mod) {
        return report.name(mod).orElseGet(() -> name(mod));
    }

    public static String name(String mod) {
        try {
            return Services.PLATFORM.modMeta(mod).map(ModMeta::name).filter(name -> !name.isBlank()).orElse(mod);
        } catch (RuntimeException e) {
            return mod;
        }
    }
}
