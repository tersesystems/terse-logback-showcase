package controllers;

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import logging.LogEntry;
import logging.LogEntryFinder;
import org.slf4j.LoggerFactory;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import scala.collection.JavaConverters;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class LogController extends Controller {

    private final LogEntryFinder finder;
    private final String dataSet;
    private final String team;
    private final boolean honeycombEnabled;
    private final org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public LogController(Config config, LogEntryFinder finder) {
        this.finder = finder;
        this.dataSet = config.getString("honeycomb.dataSet");
        this.team = config.getString("honeycomb.team");
        this.honeycombEnabled = config.getBoolean("honeycomb.enabled");
    }

    public CompletionStage<Result> show(String id) {
        return finder.findById(id).thenApply(maybeEntry ->
                maybeEntry.map(entry -> {
                    String honeycombLink = null;
                    if (isHoneycombEnabled()) {
                        JsonNode json = Json.parse(entry.event());
                        String traceId = json.findPath("trace.trace_id").asText();
                        logger.info("traceId = {}", traceId);
                        if (!traceId.equals("")) {
                            long startTSE = entry.timestamp().toEpochMilli();
                            long endTSE = entry.timestamp().plusSeconds(60).toEpochMilli();
                            honeycombLink = views.html.log.honeycomb.render(team, dataSet, traceId, startTSE, endTSE).body();
                        }
                    }
                    logger.info("honeycombLink {}", honeycombLink);
                    return ok(views.html.log.show.render(entry, scala.Option.apply(honeycombLink)));
                }).orElse(notFound()));
    }

    private boolean isHoneycombEnabled() {
        return this.honeycombEnabled;
    }

    public CompletionStage<Result> list(Integer page) {
        int mult = Math.max(page, 1) - 1;
        Integer offset = 50 * mult;
        return finder.list(offset).thenApply(list -> {
            scala.collection.mutable.Seq<LogEntry> scalaList = JavaConverters.asScalaBuffer(list);
            return ok(views.html.log.list.render(scalaList.toSeq(), page + 1));
        });
    }

    public CompletionStage<Result> correlation(String correlationId, Integer page) {
        return finder.findByCorrelation(correlationId).thenApply(list -> {
            scala.collection.mutable.Seq<LogEntry> scalaList = JavaConverters.asScalaBuffer(list);
            return ok(views.html.log.list.render(scalaList.toSeq(), page + 1));
        });
    }
}
