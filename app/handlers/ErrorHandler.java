package handlers;

import com.typesafe.config.Config;
import logging.LogEntry;
import logging.LogEntryFinder;
import org.apache.commons.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Environment;
import play.api.OptionalSourceMapper;
import play.api.UsefulException;
import play.api.routing.Router;
import play.libs.concurrent.Futures;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Error handler handles exceptions in a request which were not handled at a higher layer.
 */
@Singleton
public class ErrorHandler extends play.http.DefaultHttpErrorHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Logger bufferControl = LoggerFactory.getLogger("JDBC_RINGBUFFER_LOGGER");
    private final LogEntryFinder logEntryFinder;
    private final Futures futures;
    private final SentryHandler sentryHandler;
    private final HoneycombHandler honeycombHandler;

    @Inject
    public ErrorHandler(Config config,
                        Environment environment,
                        OptionalSourceMapper sourceMapper,
                        Provider<Router> routes,
                        Futures futures,
                        SentryHandler sentryHandler,
                        HoneycombHandler honeycombHandler,
                        LogEntryFinder logEntryFinder) {
        super(config, environment, sourceMapper, routes);

        this.sentryHandler = sentryHandler;
        this.honeycombHandler = honeycombHandler;
        this.futures = futures;
        this.logEntryFinder = logEntryFinder;
    }

    @Override
    protected void logServerError(Http.RequestHeader request, UsefulException usefulException) {
        // any call to this logger will empty out the ring buffer to JDBC, and from there
        // we can query traces and assemble them into something we can send to Sentry and Honeycomb.
        bufferControl.error("Dump the ringbuffer to JDBC here!");

        handleBacktraces(request, usefulException);
        super.logServerError(request, usefulException);
    }

    private void handleBacktraces(Http.RequestHeader request, UsefulException usefulException) {
        String cid = Long.toString(request.id());

        // Delay for a second so the queue can clear to the appender.
        futures.delayed(() ->
            logEntryFinder.findByCorrelation(cid).thenAcceptAsync(rows -> {
                writeTracesToFile(cid, rows);
                sentryHandler.handle(rows, request, usefulException);
                honeycombHandler.handle(rows, request, usefulException);
            }
        ), Duration.ofSeconds(1));
    }

    private void writeTracesToFile(String correlationId, List<LogEntry> rows) {
        Path path = Paths.get("logs","backtrace_" + correlationId + ".log");
        try (BufferedWriter writer = Files.newBufferedWriter(path, UTF_8)) {
            for (LogEntry row : rows) {
                writer.write(row.event());
                writer.newLine();
            }
        } catch (IOException e) {
            logger.error("Cannot write file!", e);
        }
    }

}
