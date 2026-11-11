package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class CovLogCapture implements AutoCloseable {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final Appender appender;
    private final LoggerContext context;
    private final LoggerConfig config;

    private CovLogCapture(String loggerName) {
        Logger logger = (Logger) LogManager.getLogger(loggerName);
        this.context = logger.getContext();
        this.config = logger.get();
        this.appender = new Appender("PropwashCovCapture" + COUNTER.incrementAndGet(), loggerName, this.lines);
        this.appender.start();
        this.config.addAppender(this.appender, null, null);
        this.context.updateLoggers();
    }

    public static CovLogCapture propwash() {
        return new CovLogCapture(Constants.LOG.getName());
    }

    public List<String> lines() {
        return new ArrayList<>(this.lines);
    }

    public List<String> matching(String prefix) {
        List<String> out = new ArrayList<>();
        for (String line : this.lines) {
            if (line.startsWith(prefix)) {
                out.add(line);
            }
        }
        return out;
    }

    @Override
    public void close() {
        this.config.removeAppender(this.appender.getName());
        this.context.updateLoggers();
        this.appender.stop();
    }

    private static final class Appender extends AbstractAppender {
        private final String loggerName;
        private final List<String> lines;

        Appender(String name, String loggerName, List<String> lines) {
            super(name, null, null, true, Property.EMPTY_ARRAY);
            this.loggerName = loggerName;
            this.lines = lines;
        }

        @Override
        public void append(LogEvent event) {
            if (this.loggerName.equals(event.getLoggerName())) {
                this.lines.add(event.getMessage().getFormattedMessage());
            }
        }
    }
}
