package controllers;

import com.tersesystems.jmxbuilder.DynamicBean;
import com.tersesystems.logback.tracing.SpanInfo;
import handlers.Utils;
import logging.jmx.JMXServer;
import logging.jmx.LoggingComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.concurrent.CompletionStage;

import com.tersesystems.logback.tracing.SpanInfo;
import handlers.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

public abstract class AbstractController extends Controller implements LoggingComponent {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public AbstractController(JMXServer jmxServer) throws MalformedObjectNameException {
        registerWithJMX(objectName(), jmxServer);
    }

    protected ObjectName objectName() throws MalformedObjectNameException {
        return new ObjectName(String.format("play:type=Controller,name=%s", getClass().getName()));
    }

    // Available for subclasses
    protected DynamicBean.Builder decorateBean(DynamicBean.Builder builder) {
        return builder;
    }

    protected void registerWithJMX(ObjectName objectName, JMXServer jmxServer) {
        DynamicBean bean = decorateBean(LoggingComponent.jmxBuilder(this)).build();
        jmxServer.registerBean(objectName, bean);
    }

    @Override
    public String getLoggingLevel() {
        return getLoggingLevel(logger);
    }

    @Override
    public void setLoggingLevel(String level) {
        setLoggingLevel(logger, level);
    }

    static class ContextAction extends play.mvc.Action.Simple {
        private final Logger logger = LoggerFactory.getLogger(getClass());

        @Inject
        Utils utils;

        public CompletionStage<Result> call(Http.Request request) {
            try {
                MDC.put("correlation_id", Long.toString(request.id()));
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
