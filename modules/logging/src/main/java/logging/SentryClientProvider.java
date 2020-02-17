package logging;

import com.typesafe.config.Config;
import io.sentry.SentryClient;
import io.sentry.SentryClientFactory;
import play.inject.ApplicationLifecycle;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class SentryClientProvider implements Provider<SentryClient> {

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