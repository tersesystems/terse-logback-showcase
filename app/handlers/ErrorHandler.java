package handlers;

import com.typesafe.config.Config;
import play.Environment;
import play.api.OptionalSourceMapper;
import play.api.UsefulException;
import play.api.routing.Router;
import play.mvc.Http;

import javax.inject.Inject;
import javax.inject.Provider;

public class ErrorHandler extends play.http.DefaultHttpErrorHandler {

    @Inject
    public ErrorHandler(Config config, Environment environment, OptionalSourceMapper sourceMapper, Provider<Router> routes) {
        super(config, environment, sourceMapper, routes);
    }

    @Override
    protected void logServerError(Http.RequestHeader request, UsefulException usefulException) {

        // Schedule a thread to give async processes a chance to log to the store to catch outliers.
        // In the thread:
        //   Query the log store to find every logging statement containing the correlation id.
        //   Synthesize a trace with child span events to set up conditions.
        //   Log the error.
        super.logServerError(request, usefulException);
    }
}
