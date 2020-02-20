package logging;

import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.typesafe.config.Config;
import io.sentry.SentryClient;
import play.Environment;
import play.inject.Binding;

import java.util.Arrays;
import java.util.List;

// https://www.playframework.com/documentation/2.8.x/JavaPlayModules
public class LoggingModule extends play.inject.Module  {
    public List<Binding<?>> bindings(Environment environment, Config config) {
        return Arrays.asList(
            bindClass(SentryClient.class).toProvider(SentryClientProvider.class).eagerly(),
            bindClass(HoneycombClient.class).toProvider(HoneycombClientProvider.class).eagerly()
        );
    }
}
