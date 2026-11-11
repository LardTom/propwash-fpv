package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.MigrationValues;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class MigrationLog {
    public static final String FILE = "log.txt";
    static final String SERVER_THREAD = "Server thread";
    static final Pattern ENVIRONMENT = Pattern.compile("Failed to (fetch|retrieve) (user properties|profile)|InvalidCredentialsException"
            + "|[Ss]tatus(=|: )401|Couldn.t (load|fetch) (profile|skin)|Unable to (load|fetch) (skin|profile)|realms|Realms|Telemetry|telemetry"
            + "|YggdrasilUserApiService|minecraftservices|sessionserver|api\\.mojang\\.com|Narrator|narrator|OpenAL|Unable to start the LAN");
    private static final List<String> OWN_RESULTS = List.of("[Selbsttest] FAIL ", "PROPWASH SELFTEST ");
    private static final String JMP_ID = MigrationValues.JMP + ":";
    private static final int SHOWN = 3;
    private static @Nullable Capture capture;

    private MigrationLog() {
    }

    public record Entry(String level, String logger, String thread, String message, @Nullable String thrown) {
        boolean error() {
            return "ERROR".equals(this.level) || "FATAL".equals(this.level) || this.thrown != null;
        }

        String line() {
            return "[" + this.thread + "/" + this.level + "] (" + this.logger + ") " + this.message + (this.thrown == null ? "" : " | " + this.thrown);
        }
    }

    public record Report(String since, List<Entry> unexpected, List<Entry> environment, List<Entry> withoutJmp, int events, int serverEvents,
                         int propwashEvents) {
        public String summary() {
            return String.format(Locale.ROOT, "Log seit „%s“: %d Zeilen (Server-Thread %d, Propwash %d), %d unerwartet, %d aus der Umgebung ohne Konto, "
                    + "%d ohne Just More Parts", this.since, this.events, this.serverEvents, this.propwashEvents, this.unexpected.size(),
                    this.environment.size(), this.withoutJmp.size());
        }

        public String report() {
            StringBuilder out = new StringBuilder(this.summary()).append("\n");
            section(out, "unerwartet", this.unexpected);
            section(out, "Umgebung ohne Konto", this.environment);
            section(out, "ohne Just More Parts", this.withoutJmp);
            return out.toString();
        }

        private static void section(StringBuilder out, String title, List<Entry> entries) {
            out.append("\n").append(title).append(" (").append(entries.size()).append("):\n");
            for (Entry entry : entries) {
                out.append(entry.line()).append("\n");
            }
        }
    }

    public static synchronized void begin(String since) {
        if (capture != null) {
            return;
        }
        capture = new Capture(since);
    }

    public static synchronized boolean running() {
        return capture != null;
    }

    public static synchronized @Nullable Report finish(boolean jmpMissing) {
        Capture running = capture;
        if (running == null) {
            return null;
        }
        capture = null;
        running.close();
        return check(running.since, new ArrayList<>(running.entries), jmpMissing, running.events.get(), running.serverEvents.get(),
                running.propwashEvents.get());
    }

    static Report check(List<Entry> entries, boolean jmpMissing, int events, int serverEvents, int propwashEvents) {
        return check("Test", entries, jmpMissing, events, serverEvents, propwashEvents);
    }

    static Report check(String since, List<Entry> entries, boolean jmpMissing, int events, int serverEvents, int propwashEvents) {
        List<Entry> unexpected = new ArrayList<>();
        List<Entry> environment = new ArrayList<>();
        List<Entry> withoutJmp = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.error() || ownResult(entry)) {
                continue;
            }
            String text = entry.message() + (entry.thrown() == null ? "" : " " + entry.thrown());
            if (ENVIRONMENT.matcher(text).find()) {
                environment.add(entry);
            } else if (jmpMissing && text.contains(JMP_ID)) {
                withoutJmp.add(entry);
            } else {
                unexpected.add(entry);
            }
        }
        return new Report(since, List.copyOf(unexpected), List.copyOf(environment), List.copyOf(withoutJmp), events, serverEvents, propwashEvents);
    }

    private static boolean ownResult(Entry entry) {
        if (!Constants.MOD_NAME.equals(entry.logger()) || entry.thrown() != null) {
            return false;
        }
        for (String prefix : OWN_RESULTS) {
            if (entry.message().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static StepResult result(@Nullable Report report) {
        if (report == null) {
            return StepResult.fail("kein Log-Abgriff (die Gruppe lief nicht ab „Testwelt im Test erzeugen“ oder „Welt-Kopie und Erwartungsdatei bereitstellen“)");
        }
        if (report.serverEvents() == 0 || report.propwashEvents() == 0) {
            return StepResult.fail("Log-Abgriff blind: " + report.serverEvents() + " Zeilen vom Server-Thread, " + report.propwashEvents()
                    + " von Propwash (" + report.events() + " insgesamt)");
        }
        if (!report.unexpected().isEmpty()) {
            List<String> first = new ArrayList<>();
            for (Entry entry : report.unexpected().subList(0, Math.min(SHOWN, report.unexpected().size()))) {
                first.add(entry.line());
            }
            return StepResult.fail(report.unexpected().size() + " unerwartete Fehlerzeilen, die ersten: " + String.join("; ", first) + " (migration/"
                    + FILE + ")");
        }
        return StepResult.pass();
    }

    private static final class Capture implements AutoCloseable {
        private static final AtomicInteger COUNTER = new AtomicInteger();
        final String since;
        final List<Entry> entries = new CopyOnWriteArrayList<>();
        final AtomicInteger events = new AtomicInteger();
        final AtomicInteger serverEvents = new AtomicInteger();
        final AtomicInteger propwashEvents = new AtomicInteger();
        private final Set<LoggerContext> contexts = new LinkedHashSet<>();
        private final Set<LoggerConfig> configs = new LinkedHashSet<>();
        private final Appender appender;

        Capture(String since) {
            this.since = since;
            this.appender = new Appender("PropwashMigrationLog" + COUNTER.incrementAndGet(), this);
            this.appender.start();
            this.attach(((Logger) LogManager.getLogger(Constants.MOD_NAME)).getContext());
        }

        private void attach(LoggerContext context) {
            if (!this.contexts.add(context)) {
                return;
            }
            Configuration configuration = context.getConfiguration();
            LoggerConfig root = configuration.getRootLogger();
            this.add(root);
            for (LoggerConfig config : configuration.getLoggers().values()) {
                if (config != root && !config.isAdditive()) {
                    this.add(config);
                }
            }
            context.updateLoggers();
        }

        private void add(LoggerConfig config) {
            if (this.configs.add(config)) {
                config.addAppender(this.appender, Level.INFO, null);
            }
        }

        void record(LogEvent event) {
            this.events.incrementAndGet();
            String thread = event.getThreadName() == null ? "?" : event.getThreadName();
            String logger = event.getLoggerName() == null ? "" : event.getLoggerName();
            if (SERVER_THREAD.equals(thread)) {
                this.serverEvents.incrementAndGet();
            }
            if (Constants.MOD_NAME.equals(logger)) {
                this.propwashEvents.incrementAndGet();
            }
            Throwable thrown = event.getThrown();
            if (event.getLevel().isLessSpecificThan(Level.WARN) && thrown == null) {
                return;
            }
            this.entries.add(new Entry(event.getLevel().name(), logger, thread, event.getMessage().getFormattedMessage(), thrown == null ? null
                    : thrown.getClass().getName() + ": " + thrown.getMessage()));
        }

        @Override
        public void close() {
            for (LoggerConfig config : this.configs) {
                config.removeAppender(this.appender.getName());
            }
            for (LoggerContext context : this.contexts) {
                context.updateLoggers();
            }
            this.appender.stop();
        }
    }

    private static final class Appender extends AbstractAppender {
        private final Capture capture;

        Appender(String name, Capture capture) {
            super(name, null, null, true, Property.EMPTY_ARRAY);
            this.capture = capture;
        }

        @Override
        public void append(LogEvent event) {
            this.capture.record(event);
        }
    }
}
