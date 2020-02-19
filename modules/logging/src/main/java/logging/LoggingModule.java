package logging;

import com.typesafe.config.Config;
import io.honeycomb.beeline.tracing.Beeline;
import io.honeycomb.libhoney.HoneyClient;
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
            bindClass(HoneyClient.class).toProvider(HoneyClientProvider.class).eagerly(),
            bindClass(Beeline.class).toProvider(BeelineProvider.class).eagerly()
        );
    }
}
