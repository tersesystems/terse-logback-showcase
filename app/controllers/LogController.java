package controllers;

import logging.LogEntry;
import logging.LogEntryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import scala.collection.JavaConverters;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class LogController extends Controller {

    private final LogEntryFinder finder;

    @Inject
    public LogController(LogEntryFinder finder) {
        this.finder = finder;
    }

    public CompletionStage<Result> show(String id) {
        return finder.findById(id).thenApply(maybeEntry ->
                maybeEntry.map(entry -> ok(views.html.log.show.render(entry)))
                          .orElse(notFound()));
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
