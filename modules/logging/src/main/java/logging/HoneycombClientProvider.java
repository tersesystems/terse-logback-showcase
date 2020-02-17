package logging;

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import com.tersesystems.logback.honeycomb.client.HoneycombClient;
import com.tersesystems.logback.honeycomb.client.HoneycombRequest;
import com.tersesystems.logback.honeycomb.playws.HoneycombPlayWSClient;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.libs.ws.ahc.AhcWSClientConfig;
import play.inject.ApplicationLifecycle;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.ahc.AhcWSClientConfigFactory;
import play.libs.ws.ahc.StandaloneAhcWSClient;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Singleton
public class HoneycombClientProvider implements Provider<HoneycombClient> {
    private final HoneycombClient client;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public HoneycombClientProvider(Config config, ActorSystem actorSystem, ApplicationLifecycle lifecycle) {
        // Use Play's existing actor system so we don't have to create another one
        Materializer materializer = SystemMaterializer.get(actorSystem).materializer();
        AhcWSClientConfig clientConfig = AhcWSClientConfigFactory.forConfig(config, getClass().getClassLoader());
        StandaloneWSClient client = StandaloneAhcWSClient.create(clientConfig, materializer);

        String writeKey = config.getString("honeycomb.writeKey");
        String dataSet = config.getString("honeycomb.dataSet");
        Function<HoneycombRequest<Object>, byte[]> defaultEncodeFunction = r -> null; // don't use this
        boolean terminateOnClose = false; // don't terminate the actorsystem

        this.client = new HoneycombPlayWSClient(client,
                actorSystem,
                writeKey,
                dataSet,
                defaultEncodeFunction,
                terminateOnClose);
        lifecycle.addStopHook(() -> CompletableFuture.runAsync(() -> {
            try {
                client.close();
            } catch (Exception e) {
                logger.error("Cannot close client!", e);
            }
        }));
    }

    @Override
    public HoneycombClient get() {
        return client;
    }
}
