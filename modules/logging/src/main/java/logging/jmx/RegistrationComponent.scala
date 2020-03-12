package logging.jmx

import com.tersesystems.jmxbuilder.DynamicBean
import javax.management.ObjectName


trait RegistrationComponent {
  self: LoggingComponent =>

  protected def decorateBean(builder: DynamicBean.Builder): DynamicBean.Builder = builder

  protected def registerWithJMX(objectName: ObjectName, jmxServer: JMXServer): Unit = {
    val bean: DynamicBean = decorateBean(LoggingComponent.jmxBuilder(this)).build
    jmxServer.registerBean(objectName, bean)
  }

}
