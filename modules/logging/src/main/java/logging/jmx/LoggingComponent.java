package logging.jmx;

import com.tersesystems.jmxbuilder.AttributeInfo;
import com.tersesystems.jmxbuilder.DescriptorSupport;
import com.tersesystems.jmxbuilder.DynamicBean;
import logging.ChangeLogLevel;

import java.util.Set;

public interface LoggingComponent {
    void setLoggingLevel(String level);

    String getLoggingLevel();

    default String getLoggingLevel(org.slf4j.Logger logger) {
        return ((ch.qos.logback.classic.Logger) logger).getEffectiveLevel().toString();
    }

    default void setLoggingLevel(org.slf4j.Logger logger, String level) {
        new ChangeLogLevel().changeLogLevel(logger, level);
    }

    static DynamicBean.Builder jmxBuilder(LoggingComponent lc) {
        Set<String> levelsSet = new java.util.HashSet<>();
        java.util.Collections.addAll(levelsSet, "OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE");
        javax.management.modelmbean.DescriptorSupport descriptor = DescriptorSupport
                .builder()
                .withDisplayName("level")
                .withLegalValues(levelsSet)
                .build();

        AttributeInfo<String> attributeInfo = AttributeInfo
                .builder(String.class)
                .withName("loggingLevel")
                .withBeanProperty(lc, "loggingLevel")
                .withDescriptor(descriptor)
                .build();
        return DynamicBean.builder().withAttribute(attributeInfo);
    }
}
