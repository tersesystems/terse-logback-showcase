package controllers;

import handlers.Utils;
import logging.LogEntry;
import logging.LogEntryFinder;
import play.mvc.Controller;
import play.mvc.Result;
import scala.collection.JavaConverters;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class LogController extends Controller {

    private final LogEntryFinder finder;
    private final Utils utils;

    @Inject
    public LogController(Utils utils, LogEntryFinder finder) {
        this.finder = finder;
        this.utils = utils;
    }

    public CompletionStage<Result> show(String id) {
        return finder.findById(id).thenApply(maybeEntry ->
                maybeEntry.map(entry -> {
                    String honeycombLink =  utils.generateHoneycombLink(entry);
                    return ok(views.html.log.show.render(entry, scala.Option.apply(honeycombLink)));
                }).orElse(notFound()));
    }

    public CompletionStage<Result> list(Integer page) {
        int mult = Math.max(page, 1) - 1;
        Integer offset = 50 * mult;
        return finder.list(offset).thenApply(list -> {
            scala.collection.mutable.Seq<LogEntry> scalaList = JavaConverters.asScalaBuffer(list);
            return ok(views.html.log.list.render(scalaList.toSeq(), scala.Option.empty(),page + 1));
        });
    }

    public CompletionStage<Result> correlation(String correlationId, Integer page) {
        return finder.findByCorrelation(correlationId).thenApply(list -> {
            scala.collection.mutable.Seq<LogEntry> scalaList = JavaConverters.asScalaBuffer(list);
            return ok(views.html.log.list.render(scalaList.toSeq(), scala.Some.apply(correlationId),page + 1));
        });
    }
}
