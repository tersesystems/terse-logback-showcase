package logging;

import akka.actor.ActorSystem;
import play.libs.concurrent.CustomExecutionContext;

import javax.inject.Inject;

public class LoggingExecutionContext extends CustomExecutionContext {

    @Inject
    public LoggingExecutionContext(ActorSystem actorSystem) {
        super(actorSystem, "logging");
    }
}
