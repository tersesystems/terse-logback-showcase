package logging;

import com.typesafe.config.Config;
import io.sentry.SentryClient;
import play.Environment;
import play.inject.Binding;

import java.util.List;

import static java.util.Collections.singletonList;

// https://www.playframework.com/documentation/2.8.x/JavaPlayModules
public class LoggingModule extends play.inject.Module  {
    public List<Binding<?>> bindings(Environment environment, Config config) {
        return singletonList(
            bindClass(SentryClient.class).toProvider(SentryClientProvider.class).eagerly()
        );
    }
}
