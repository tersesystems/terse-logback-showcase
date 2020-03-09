package handlers;

import com.tersesystems.logback.tracing.SpanInfo;
import com.typesafe.config.Config;
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
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
    private final FileHandler fileHandler;
    private final Utils utils;

    @Inject
    public ErrorHandler(Config config,
                        Environment environment,
                        OptionalSourceMapper sourceMapper,
                        Provider<Router> routes,
                        Futures futures,
                        SentryHandler sentryHandler,
                        HoneycombHandler honeycombHandler,
                        FileHandler fileHandler,
                        Utils utils,
                        LogEntryFinder logEntryFinder) {
        super(config, environment, sourceMapper, routes);
        this.sentryHandler = sentryHandler;
        this.honeycombHandler = honeycombHandler;
        this.fileHandler = fileHandler;
        this.futures = futures;
        this.utils = utils;
        this.logEntryFinder = logEntryFinder;
    }

    @Override
    protected void logServerError(Http.RequestHeader request, UsefulException usefulException) {
        try {
            MDC.put("correlation_id", request.id().toString());

            // Log the error itself...
            SpanInfo rootSpan = utils.createRootSpan(request);
            Marker marker = utils.createMarker(rootSpan, request, 500);

            String msg = String.format("@%s - Internal server error, for (%s) [%s] ->\n", usefulException.id, request.method(), request.uri());
            logger.error(marker, msg, usefulException);

            // any call to this logger will empty out the ring buffer to JDBC, and from there
            // we can query traces and assemble them into something we can send to Sentry and Honeycomb.
            bufferControl.error("Dump the ringbuffer to JDBC here!");
            handleBacktraces(rootSpan, request, usefulException);
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
        Duration spanDuration = spanInfo.duration(); // freeze this so it's not affected by delay

        // Delay for a second so the queue can clear to the appender.
        futures.delayed(() ->
            logEntryFinder.findByCorrelation(cid).thenAcceptAsync(rows -> {
                if (utils.isSentryEnabled()) {
                    sentryHandler.handle(rows, request, usefulException);
                }
                if (utils.isHoneycombEnabled()) {
                    honeycombHandler.handle(spanInfo, spanDuration, rows, request, usefulException);
                }
                if (utils.isFilesEnabled()) {
                    fileHandler.handle(cid, rows);
                }
            }
        ), Duration.ofSeconds(1));
    }

}
