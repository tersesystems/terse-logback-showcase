package logging;

import akka.actor.ActorSystem;
import play.libs.concurrent.CustomExecutionContext;

import javax.inject.Inject;

// A custom execution context ensures that DB queries don't impact rendering time.
public class LoggingExecutionContext extends CustomExecutionContext {

    @Inject
    public LoggingExecutionContext(ActorSystem actorSystem) {
        super(actorSystem, "logging");
    }
}
