package handlers;

import com.tersesystems.logback.correlationid.CorrelationIdMarker;
import com.tersesystems.logback.tracing.SpanInfo;
import com.typesafe.config.Config;
import logging.LogEntry;
import logging.LogEntryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import play.Environment;
import play.api.OptionalSourceMapper;
import play.api.UsefulException;
import play.api.routing.Router;
import play.libs.concurrent.Futures;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import scala.Option;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
    private final Utils utils;
    private final boolean sentryEnabled;
    private final boolean honeycombEnabled;

    @Inject
    public ErrorHandler(Config config,
                        Environment environment,
                        OptionalSourceMapper sourceMapper,
                        Provider<Router> routes,
                        Futures futures,
                        SentryHandler sentryHandler,
                        HoneycombHandler honeycombHandler,
                        Utils utils,
                        LogEntryFinder logEntryFinder) {
        super(config, environment, sourceMapper, routes);
        this.sentryEnabled = config.getBoolean("sentry.enabled");
        this.honeycombEnabled = config.getBoolean("honeycomb.enabled");
        this.sentryHandler = sentryHandler;
        this.honeycombHandler = honeycombHandler;
        this.futures = futures;
        this.utils = utils;
        this.logEntryFinder = logEntryFinder;
    }

    @Override
    protected void logServerError(Http.RequestHeader request, UsefulException usefulException) {
        // any call to this logger will empty out the ring buffer to JDBC, and from there
        // we can query traces and assemble them into something we can send to Sentry and Honeycomb.
        bufferControl.error("Dump the ringbuffer to JDBC here!");
        SpanInfo rootSpan = utils.createRootSpan(request);
        handleBacktraces(rootSpan, request, usefulException);

        try {
            MDC.put("correlation_id", request.id().toString());

            // Log the error itself...
            Marker marker = utils.createMarker(rootSpan, request, 500);

            String msg = String.format("@%s - Internal server error, for (%s) [%s] ->\n", usefulException.id, request.method(), request.uri());
            logger.error(marker, msg, usefulException);
        } finally {
            MDC.clear();
        }
    }

    protected CompletionStage<Result> onProdServerError(
            Http.RequestHeader request, UsefulException exception) {
        return CompletableFuture.completedFuture(
                Results.internalServerError(
                        views.html.error.render(exception, request.asScala())));
    }
    protected CompletionStage<Result> onDevServerError(
            Http.RequestHeader request, UsefulException exception) {
        return CompletableFuture.completedFuture(
                Results.internalServerError(
                        views.html.devError.render(Option.empty(), exception, request.asScala())));
    }


    private void handleBacktraces(SpanInfo spanInfo, Http.RequestHeader request, UsefulException usefulException) {
        String cid = Long.toString(request.id());

        // Delay for a second so the queue can clear to the appender.
        futures.delayed(() ->
            logEntryFinder.findByCorrelation(cid).thenAcceptAsync(rows -> {
                logger.info("Writing out rows for request id " + cid);
                writeTracesToFile(cid, rows);
                if (isSentryEnabled()) {
                    sentryHandler.handle(rows, request, usefulException);
                }
                if (isHoneycombEnabled()) {
                    honeycombHandler.handle(spanInfo, rows, request, usefulException);
                }
            }
        ), Duration.ofSeconds(1));
    }

    private boolean isHoneycombEnabled() {
        return this.honeycombEnabled;
    }

    private boolean isSentryEnabled() {
        return this.sentryEnabled;
    }

    private void writeTracesToFile(String correlationId, List<LogEntry> rows) {
        try {
            Path cwd = FileSystems.getDefault().getPath("").toAbsolutePath();
            Path logs = Files.createDirectories(cwd.resolve("logs"));
            Path path = logs.resolve("backtrace_" + correlationId + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(path, UTF_8)) {
                for (LogEntry row : rows) {
                    writer.write(row.event());
                    writer.newLine();
                }
            } catch (IOException e) {
                logger.error("Cannot write file!", e);
            }
        } catch (IOException e) {
            logger.error("Cannot create directories!", e);
        }
    }

}
