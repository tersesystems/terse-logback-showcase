package logging.jmx;

import com.typesafe.config.Config;
import play.Environment;
import play.inject.Binding;
import play.inject.Module;

import java.util.Collections;
import java.util.List;

public class JMXModule extends Module {
    @Override
    public List<Binding<?>> bindings(Environment environment, Config config) {
        return Collections.singletonList(
                bindClass(JMXServer.class).toSelf()
        );
    }
}