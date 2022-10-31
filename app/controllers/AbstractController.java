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
                return delegate.call(request).handle((result, e) -> {
                    SpanInfo spanInfo = utils.createRootSpan(request);
                    int status = e == null ? result.status() : 500;
                    Marker markers = utils.createMarker(spanInfo, request, result.status());
                    logger.info(markers,
                            "{} {} returned {}",
                            request.method(),
                            request.uri(),
                            status);
                    return result;
                });
            } finally {
                MDC.clear();
            }
        }
    }
}
