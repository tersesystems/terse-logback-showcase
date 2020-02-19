package handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.beeline.tracing.Span;
import io.honeycomb.beeline.tracing.SpanBuilderFactory;
import io.honeycomb.beeline.tracing.Tracer;
import io.honeycomb.beeline.tracing.propagation.HttpHeaderV1PropagationCodec;
import io.honeycomb.beeline.tracing.propagation.Propagation;
import io.honeycomb.beeline.tracing.propagation.PropagationContext;
import io.sentry.SentryClient;
import io.sentry.context.Context;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import logging.LogEntry;
import logging.LogEntryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Environment;
import play.api.OptionalSourceMapper;
import play.api.UsefulException;
import play.api.routing.Router;
import play.i18n.Lang;
import play.libs.Json;
import play.libs.concurrent.Futures;
import play.mvc.Http;
import play.mvc.Result;

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
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Error handler handles exceptions in a request which were not handled at a higher layer.
 */
@Singleton
public class ErrorHandler extends play.http.DefaultHttpErrorHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SentryClient sentryClient;
    private final LogEntryFinder logEntryFinder;
    private final Futures futures;
    private final Beeline beeline;

    @Inject
    public ErrorHandler(Config config,
                        Environment environment,
                        OptionalSourceMapper sourceMapper,
                        Provider<Router> routes,
                        Futures futures,
                        Beeline beeline,
                        SentryClient sentryClient,
                        LogEntryFinder logEntryFinder) {
        super(config, environment, sourceMapper, routes);
        this.beeline = beeline;
        this.sentryClient = sentryClient;
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
                .thenAcceptAsync(rows ->
                    recordSentryEvent((List<LogEntry>) rows, request, usefulException)
                ), Duration.ofSeconds(1));
    }

    private void recordHoneycombEvent(List<LogEntry> rows, Http.RequestHeader request, UsefulException usefulException) {

        SpanBuilderFactory.SpanBuilder builder = beeline.getSpanBuilderFactory().createBuilder()
                .setSpanName("request")
                .setServiceName("terse-logback-showcase");

        Optional<String> headerValue = request.header(HttpHeaderV1PropagationCodec.HONEYCOMB_TRACE_HEADER);
        if (headerValue.isPresent()) {
            PropagationContext context = Propagation.honeycombHeaderV1().decode(headerValue.get());
            builder.setParentContext(context);
        }

        Span span = builder.build();

        Tracer tracer = beeline.getTracer();
        tracer.startTrace(span);

        span.addField("result", "OK");
        tracer.endTrace();
    }

    private void recordSentryEvent(List<LogEntry> rows, Http.RequestHeader request, UsefulException usefulException) {
        // Delay the query for a second so the async disruptor queue has a chance to clear.
        // Query for the records relating to this request.
        // Sentry usually has around 100 breadcrumbs handy.
        try {
            List<Breadcrumb> breadcrumbs = new ArrayList<>(rows.size());
            for (LogEntry row : rows) {
                Breadcrumb breadcrumb = buildBreadcrumb(row);
                breadcrumbs.add(breadcrumb);
            }

            Event event = buildEvent(request, usefulException, breadcrumbs);
            sentryClient.sendEvent(event);
        } catch (Exception e) {
            logger.error("Cannot send to sentry!", e);
        }
    }

    private Event buildEvent(Http.RequestHeader request, UsefulException usefulException, List<Breadcrumb> breadcrumbs) {
        return new EventBuilder()
                .withMessage(usefulException.description)
                .withFingerprint(usefulException.id)
                .withLogger(getClass().getName())
                .withTag("host", request.host())
                .withTag("uri", request.uri())
                .withBreadcrumbs(breadcrumbs)
                .withSentryInterface(new ExceptionInterface(usefulException))
                .withLevel(Event.Level.ERROR)
                .build();
    }

    private Breadcrumb buildBreadcrumb(LogEntry row) {
        JsonNode evt = Json.parse(row.event());
        String message = evt.findPath("message").textValue();
        Map<String, String> eventData = buildEventData(evt);
        return new BreadcrumbBuilder().setMessage(message)
                .setTimestamp(new Date(row.timestamp().toEpochMilli()))
                .setLevel(mapToBreadcrumbLevel(row.level()))
                .setType(Breadcrumb.Type.HTTP)
                .setData(eventData)
                .build();
    }

    private Breadcrumb.Level mapToBreadcrumbLevel(String rowLevel) {
        // Breadcrumb doesn't have a TRACE level
        String level = (rowLevel.equalsIgnoreCase("TRACE")) ? "DEBUG" : rowLevel;
        return Breadcrumb.Level.valueOf(level);
    }

    private Map<String, String> buildEventData(JsonNode evt) {
        Map<String, String> eventData = new HashMap<>();
        for (Iterator<String> fieldNames = evt.fieldNames(); fieldNames.hasNext(); ) {
            String name = fieldNames.next();
            String value = evt.findPath(name).toPrettyString();
            eventData.put(name, value);
        }
        return eventData;
    }

}
