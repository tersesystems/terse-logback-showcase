package controllers;

import com.tersesystems.jmxbuilder.DynamicBean;
import logging.jmx.JMXServer;
import logging.jmx.LoggingComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Controller;

import javax.inject.Inject;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

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
}
