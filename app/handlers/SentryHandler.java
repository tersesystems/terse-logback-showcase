package handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.sentry.SentryClient;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;
import logging.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import play.api.UsefulException;
import play.libs.Json;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class SentryHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SentryClient sentryClient;

    @Inject
    public SentryHandler(SentryClient sentryClient) {
        this.sentryClient = sentryClient;
    }

    void handle(List<? extends LogEntry> rows, Http.RequestHeader request, UsefulException usefulException) {
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
            logger.error("handle: Cannot send to sentry!", e);
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

    private Breadcrumb.Level mapToBreadcrumbLevel(Level level) {
        // Breadcrumb doesn't have a TRACE level
        String l = (level.toString().equals("TRACE")) ? "DEBUG" : level.toString();
        return Breadcrumb.Level.valueOf(l);
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
