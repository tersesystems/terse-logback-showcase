package logging;

import com.typesafe.config.Config;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import play.Environment;
import play.inject.ApplicationLifecycle;
import play.inject.Binding;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// https://www.playframework.com/documentation/2.8.x/JavaPlayModules
public class LoggingModule extends play.inject.Module  {
    public List<Binding<?>> bindings(Environment environment, Config config) {
        return Arrays.asList(
            bindClass(SentryClient.class).toProvider(SentryClientProvider.class).eagerly()
        );
    }
}

class SentryClientProvider implements Provider<SentryClient> {

    private final SentryClient client;

    @Inject
    public SentryClientProvider(Config config, ApplicationLifecycle lifecycle) {
        String dsn = config.getString("sentry.dsn");
        this.client = SentryClientFactory.sentryClient(dsn);
        lifecycle.addStopHook(() -> CompletableFuture.runAsync(client::closeConnection));
    }

    @Override
    public SentryClient get() {
        // https://docs.sentry.io/clients/java/config/
        return client;
    }
}