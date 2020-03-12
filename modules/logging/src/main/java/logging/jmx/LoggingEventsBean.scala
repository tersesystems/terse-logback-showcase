package logging.jmx

import java.util

import ch.qos.logback.classic.{Logger, LoggerContext}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.CyclicBufferAppender
import com.tersesystems.jmxbuilder.{CompositeDataWriter, DynamicBean}
import com.tersesystems.jmxbuilder.TabularDataWriter
import javax.inject.{Inject, Singleton}
import javax.management.ObjectName

@Singleton
class LoggingEventsBean @Inject()(loggerContext: LoggerContext, jmxServer: JMXServer) {

  class CyclicAppenderIterator[E](appender: CyclicBufferAppender[E]) extends java.util.Iterator[E]() {
    private var index = 0
    override def hasNext: Boolean = index < appender.getLength
    override def next(): E = {
      val event = appender.get(index)
      if (event == null) {
        throw new NoSuchElementException("no event")
      } else {
        index = index + 1
      }
      event
    }
  }

  def findCyclicAppender: Option[CyclicBufferAppender[ILoggingEvent]] = {
    import scala.collection.JavaConverters._

    val rootLogger: Logger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    rootLogger.iteratorForAppenders().asScala.collectFirst {
      case cyclicBuffer: CyclicBufferAppender[ILoggingEvent] =>
        cyclicBuffer
    }
  }

  def iterator: util.Iterator[ILoggingEvent] = findCyclicAppender.map(new CyclicAppenderIterator(_)).getOrElse {
    java.util.Collections.emptyIterator()
  }

  val bean: DynamicBean = LoggingEventsBean(iterator)
  jmxServer.registerBean(new ObjectName(s"play:type=CyclicBuffer,name=${getClass.getName}"), bean)
}

object LoggingEventsBean {
  private val compositeEvents = CompositeDataWriter.builder(classOf[ILoggingEvent])
    .withTypeName("loggingEvent")
    .withTypeDescription("Logging Event")
    .withSimpleAttribute("timestamp", "Message", e => new java.util.Date(e.getTimeStamp))
    .withSimpleAttribute("name", "name", _.getLoggerName)
    .withSimpleAttribute("level", "Level", _.getLevel.toString)
    .withSimpleAttribute("message", "Message", _.getFormattedMessage)
    .build();

  private val tabularEventsWriter: TabularDataWriter[ILoggingEvent] = TabularDataWriter.builder(classOf[ILoggingEvent])
    .withTypeName("events")
    .withTypeDescription("Logging Events")
    .withIndexName("timestamp")
    .withCompositeDataWriter(compositeEvents).build

  def apply(iter: => java.util.Iterator[ILoggingEvent]): DynamicBean = {
    DynamicBean.builder().withTabularAttribute("events", () => new java.lang.Iterable[ILoggingEvent]() {
      override def iterator: java.util.Iterator[ILoggingEvent] = iter
    }, tabularEventsWriter).build()
  }
}
