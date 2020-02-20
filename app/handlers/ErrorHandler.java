package handlers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tersesystems.logback.classic.LoggingEventFactory;
import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.tersesystems.logback.honeycomb.client.HoneycombRequest;
import com.tersesystems.logback.tracing.EventMarkerFactory;
import com.tersesystems.logback.tracing.SpanInfo;
import com.tersesystems.logback.tracing.SpanMarkerFactory;
import com.tersesystems.logback.uniqueid.RandomUUIDIdGenerator;
import com.typesafe.config.Config;
import filters.Attrs;
import io.sentry.SentryClient;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import logging.LogEntry;
import logging.LogEntryFinder;
import net.logstash.logback.encoder.LogstashEncoder;
import net.logstash.logback.marker.LogstashMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Environment;
import play.api.OptionalSourceMapper;
import play.api.UsefulException;
import play.api.routing.Router;
import play.libs.Json;
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
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Error handler handles exceptions in a request which were not handled at a higher layer.
 */
@Singleton
public class ErrorHandler extends play.http.DefaultHttpErrorHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());
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
        writeTracesToFile(request, usefulException);
        super.logServerError(request, usefulException);
    }

    private void writeTracesToFile(Http.RequestHeader request, UsefulException usefulException) {
        String correlationId = Long.toString(request.id());

        // Delay for a second so the queue can clear to the appender.
        futures.delayed(() -> logEntryFinder.findByCorrelation(correlationId)
            .thenApplyAsync(rows -> {
                Path path = Paths.get("logs","error_" + correlationId + ".log");
                try (BufferedWriter writer = Files.newBufferedWriter(path, UTF_8)) {
                    for (LogEntry row : rows) {
                        writer.write(row.event());
                        writer.newLine();
                    }
                    return rows;
                } catch (IOException e) {
                    logger.error("Cannot write file!", e);
                }
                return Collections.emptyList();
            })
            .thenAcceptAsync(rows -> {
                try {
                    sentryHandler.recordSentryEvent((List<LogEntry>) rows, request, usefulException);
                    honeycombHandler.recordHoneycombEvent((List<LogEntry>) rows, request, usefulException);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }), Duration.ofSeconds(1));
    }


}
