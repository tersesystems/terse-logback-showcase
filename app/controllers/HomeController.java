package controllers;

import play.mvc.Result;
import play.mvc.With;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends AbstractController {

    public Result index() {
        return ok(views.html.index.render());
    }

    @With(ContextAction.class)
    public Result normal() {
        if (logger.isDebugEnabled()) {
            logger.debug("About to render /: this is a normal request...");
        }
        long timeMillis = -System.currentTimeMillis();
        if (logger.isTraceEnabled()) {
            logger.trace("Surely this is less than zero: timeMillis = " + timeMillis);
        }
        if (timeMillis > 0) {
            throw new IllegalStateException("Who could have foreseen this?");
        }
        return ok(views.html.index.render());
    }

    @With(ContextAction.class)
    public Result flaky() {
        if (logger.isDebugEnabled()) {
            logger.debug("About to render /flaky: this is a flaky request that throws an exception!");
        }
        long timeMillis = System.currentTimeMillis();
        if (logger.isTraceEnabled()) {
            logger.trace("Surely this is less than zero: timeMillis = " + timeMillis);
        }
        if (timeMillis > 0) {
            throw new IllegalStateException("Who could have foreseen this?");
        }

        return ok(views.html.index.render());
    }

}
