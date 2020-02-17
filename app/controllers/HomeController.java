package controllers;

import logging.LogEntry;
import logging.LogEntryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import scala.collection.JavaConverters;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final LogEntryFinder finder;

    @Inject
    public HomeController(LogEntryFinder finder) {
        this.finder = finder;
    }

    public Result index(Http.Request request) {
        MDC.put("correlation_id", Long.toString(request.id()));
        logger.debug("Hello world!");
        return ok(views.html.index.render());
    }

    public Result flaky(Http.Request request) {
        MDC.put("correlation_id", Long.toString(request.id()));
        logger.debug("Hello world!");
        if (isTSEEven()) {
            throw new IllegalStateException("Can't serve this request!");
        }
        return ok(views.html.index.render());
    }

    private boolean isTSEEven() {
        long time = System.currentTimeMillis();
        boolean result = time % 2 == 0;
        logger.trace("isTSEEven: time = " + time + ", result = " + result);
        return result;
    }


    public CompletionStage<Result> logs(Integer page) {
        int mult = Math.max(page, 1) - 1;
        Integer offset = 50 * mult;
        return finder.list(offset).thenApply(list -> {
            scala.collection.mutable.Seq<LogEntry> scalaList = JavaConverters.asScalaBuffer(list);
            return ok(views.html.logs.render(scalaList.toSeq(), page + 1));
        });
    }

    public CompletionStage<Result> correlation(String correlationId, Integer page) {
        return finder.findByCorrelation(correlationId).thenApply(list -> {
            scala.collection.mutable.Seq<LogEntry> scalaList = JavaConverters.asScalaBuffer(list);
            return ok(views.html.logs.render(scalaList.toSeq(), page + 1));
        });
    }
}
