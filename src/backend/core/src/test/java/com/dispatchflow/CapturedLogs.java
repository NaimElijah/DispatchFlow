package com.dispatchflow;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

/**
 * The events a logger writes during each test, read from logback itself rather than from the
 * console, so a unit test's assertions hold whatever layout the console has, and whether or not an
 * earlier test booted Spring Boot's logging. Register it as an instance field with
 * {@code @RegisterExtension}: it attaches before each test and detaches after it. Integration tests
 * keep asserting on the console through {@code CapturedOutput}, because there the JSON line is the
 * point.
 *
 * <p>{@link #of} records its logger's events at INFO and above, the level the application runs
 * its own loggers at, and changes no level: logback's unconfigured default, DEBUG, would otherwise
 * record lines production never writes. {@link #ofEveryLogger} pins the root at INFO, so a
 * framework's DEBUG lines, which quote values the core doesn't control, stay off, and {@code
 * com.dispatchflow} at TRACE, so a check that a value leaks nowhere sees the application's own
 * lines at every level, those of a class another capture reads included; it puts both levels back
 * after each test. It changes shared logback state, so it is not for tests that run
 * concurrently.
 */
public final class CapturedLogs implements BeforeEachCallback, AfterEachCallback {

    private static final Level PRODUCTION_LEVEL = Level.INFO;
    private static final String APPLICATION_LOGGER = "com.dispatchflow";

    private final Logger logger;
    private final Map<Logger, Level> pinned;
    private final Map<Logger, @Nullable Level> levelsBefore = new LinkedHashMap<>();
    private final Recorder recorder;

    private CapturedLogs(Logger logger, Level lowest, Map<Logger, Level> pinned) {
        this.logger = logger;
        this.pinned = pinned;
        this.recorder = new Recorder(lowest);
    }

    /**
     * The events one class's logger writes, at INFO and above.
     *
     * @param source the class whose logger to read
     * @return the capture, attached once JUnit runs a test
     */
    public static CapturedLogs of(Class<?> source) {
        return new CapturedLogs((Logger) LoggerFactory.getLogger(source), PRODUCTION_LEVEL, Map.of());
    }

    /**
     * The events every logger writes, for a test that a value never reaches any line: the
     * application's at every level, the frameworks' at INFO and above.
     *
     * @return the capture, attached once JUnit runs a test
     */
    public static CapturedLogs ofEveryLogger() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Logger application = (Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        return new CapturedLogs(root, Level.TRACE, Map.of(root, PRODUCTION_LEVEL, application, Level.TRACE));
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        levelsBefore.clear();
        pinned.forEach((pinnedLogger, level) -> {
            levelsBefore.put(pinnedLogger, pinnedLogger.getLevel());
            pinnedLogger.setLevel(level);
        });
        recorder.list.clear();
        recorder.start();
        logger.addAppender(recorder);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        logger.detachAppender(recorder);
        recorder.stop();
        levelsBefore.forEach(Logger::setLevel);
    }

    /**
     * Every event written so far in this test, in order.
     *
     * @return the events
     */
    public List<ILoggingEvent> events() {
        return List.copyOf(recorder.list);
    }

    /**
     * The events written so far at one level.
     *
     * @param level the level
     * @return the events at it, in order
     */
    public List<ILoggingEvent> at(Level level) {
        return events().stream().filter(event -> event.getLevel() == level).toList();
    }

    /**
     * An event's key-value pairs, as the fluent API added them.
     *
     * @param event the event
     * @return its pairs, in the order added; empty when it has none
     */
    public static Map<String, Object> keyValues(ILoggingEvent event) {
        Map<String, Object> pairs = new LinkedHashMap<>();
        List<KeyValuePair> added = event.getKeyValuePairs();
        if (added != null) {
            added.forEach(pair -> pairs.put(pair.key, pair.value));
        }
        return pairs;
    }

    /**
     * Every text an event carries: its message, its key-value pairs, its MDC, and its throwable's
     * class names and messages with their causes', for a test that a value reaches none of them.
     *
     * @param event the event
     * @return the texts
     */
    public static List<String> textsOf(ILoggingEvent event) {
        List<String> texts = new ArrayList<>();
        texts.add(event.getFormattedMessage());
        keyValues(event).forEach((key, value) -> texts.add(key + "=" + value));
        event.getMDCPropertyMap().forEach((key, value) -> texts.add(key + "=" + value));
        for (IThrowableProxy thrown = event.getThrowableProxy(); thrown != null; thrown = thrown.getCause()) {
            texts.add(thrown.getClassName());
            texts.add(Objects.toString(thrown.getMessage()));
            Stream.of(thrown.getSuppressed())
                    .map(suppressed -> Objects.toString(suppressed.getMessage()))
                    .forEach(texts::add);
        }
        return texts;
    }

    /**
     * Keeps each event at or above its lowest level, whole: logback reads an event's MDC lazily,
     * and the request clears it.
     */
    private static final class Recorder extends ListAppender<ILoggingEvent> {

        private final Level lowest;

        Recorder(Level lowest) {
            this.lowest = lowest;
        }

        @Override
        protected void append(ILoggingEvent event) {
            if (event.getLevel().isGreaterOrEqual(lowest)) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        }
    }
}
