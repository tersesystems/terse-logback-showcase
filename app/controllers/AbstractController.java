package controllers;

import com.tersesystems.logback.tracing.SpanInfo;
import handlers.Utils;
import logging.ID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

import static logging.Constants.REQUEST_ID;

// https://www.playframework.com/documentation/2.8.x/JavaActionsComposition
public abstract class AbstractController extends Controller {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    static class ContextAction extends play.mvc.Action.Simple {
        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Inject
        Utils utils;

        public CompletionStage<Result> call(Http.Request request) {
            try {
                // Always set request id in MDC for every action we want logging on
                MDC.put(REQUEST_ID, ID.get(request));
                return delegate.call(request).whenComplete((result, e) -> {
                    if (e != null) {
                        SpanInfo spanInfo = utils.createRootSpan(request);
                        int status = result == null ? 500 : result.status();
                        Marker markers = utils.createMarker(spanInfo, request, status);
                        logger.info(markers,
                          "{} {} failed with exception, sending span info",
                          request.method(),
                          request.uri());
                        // whenComplete will return the exception to the HTTP error handler,
                    }
                });
            } finally {
                MDC.clear();
            }
        }
    }
}
