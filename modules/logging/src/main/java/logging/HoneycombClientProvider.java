package logging;

import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.tersesystems.logback.honeycomb.playws.HoneycombPlayWSClient;
import com.typesafe.config.Config;
import play.inject.ApplicationLifecycle;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;

@Singleton
public class HoneycombClientProvider implements Provider<HoneycombClient> {
    private final HoneycombClient client;

    @Inject
    public HoneycombClientProvider(Config config, ApplicationLifecycle lifecycle) {
        String dataset = config.getString("beeline.dataset");
        String writeKey = config.getString("beeline.writeKey");
        Integer sampleRate = config.getInt("beeline.sampleRate");

        this.client = new HoneycombPlayWSClient();
        lifecycle.addStopHook(() -> CompletableFuture.runAsync(() -> {
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    @Override
    public HoneycombClient get() {
        return client;
    }
}
