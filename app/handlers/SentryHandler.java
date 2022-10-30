package handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.sentry.*;
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

  private static final Logger logger = LoggerFactory.getLogger(SentryHandler.class);

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

      SentryEvent event = buildEvent(request, usefulException, breadcrumbs);
      Sentry.captureEvent(event);
    } catch (Exception e) {
      logger.error("handle: Cannot send to sentry!", e);
    }
  }

  private SentryEvent buildEvent(Http.RequestHeader request, UsefulException usefulException, List<Breadcrumb> breadcrumbs) {
    SentryEvent evt = new SentryEvent(usefulException);
    evt.setFingerprints(Collections.singletonList(usefulException.id));
    evt.setLogger(getClass().getName());
    evt.setTag("host", request.host());
    evt.setTag("uri", request.uri());
    evt.setBreadcrumbs(breadcrumbs);
    evt.setLevel(SentryLevel.ERROR);
    return evt;

  }

  private Breadcrumb buildBreadcrumb(LogEntry row) {
    JsonNode evt = Json.parse(row.event());
    String message = evt.findPath("message").textValue();
    final Breadcrumb bc = new Breadcrumb();
    bc.setMessage(message);
    bc.setLevel(mapToBreadcrumbLevel(row.level()));
    bc.setType("http");
    for (Iterator<String> fieldNames = evt.fieldNames(); fieldNames.hasNext(); ) {
      String name = fieldNames.next();
      String value = evt.findPath(name).toPrettyString();
      bc.setData(name, value);
    }
    return bc;
  }

  private SentryLevel mapToBreadcrumbLevel(Level level) {
    // Breadcrumb doesn't have a TRACE level
    String l = (level.toString().equals("TRACE")) ? "DEBUG" : level.toString();
    return SentryLevel.valueOf(l);
  }

}
