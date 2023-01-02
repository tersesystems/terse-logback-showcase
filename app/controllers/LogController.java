package controllers;

import handlers.Utils;
import logging.LogEntry;
import logging.LogEntryFinder;
import play.mvc.Controller;
import play.mvc.Result;
import scala.collection.mutable.Seq;
import scala.jdk.javaapi.CollectionConverters;

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
        Integer offset = finder.pageSize * mult;
        return finder.list(offset).thenApply(list -> {
            Seq<LogEntry> scalaList = CollectionConverters.asScala(list);
            return ok(views.html.log.list.render(scalaList.toSeq(), scala.Option.empty(),page + 1));
        });
    }

    public CompletionStage<Result> request(String requestId, Integer page) {
        return finder.findByRequestId(requestId).thenApply(list -> {
            Seq<LogEntry> scalaList = CollectionConverters.asScala(list);
            return ok(views.html.log.list.render(scalaList.toSeq(), scala.Some.apply(requestId),page + 1));
        });
    }
}
