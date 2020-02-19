package logging;

import com.typesafe.config.Config;
import io.honeycomb.libhoney.HoneyClient;
import io.honeycomb.libhoney.LibHoney;
import play.inject.ApplicationLifecycle;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class HoneyClientProvider implements Provider<HoneyClient> {
    private final HoneyClient client;

    @Inject
    public HoneyClientProvider(Config config, ApplicationLifecycle lifecycle) {
        String dataset = config.getString("beeline.dataset");
        String writeKey = config.getString("beeline.writeKey");
        Integer sampleRate = config.getInt("beeline.sampleRate");

        client = LibHoney.create(LibHoney.options()
                .setSampleRate(sampleRate)
                .setDataset(dataset)
                .setWriteKey(writeKey)
                .build());
        lifecycle.addStopHook(() -> CompletableFuture.runAsync(client::close));
    }

    @Override
    public HoneyClient get() {
        return client;
    }
}
